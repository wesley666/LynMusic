package top.iwesley.lyn.music.platform

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.AndroidDiagnosticLogger
import top.iwesley.lyn.music.core.model.CompositePlaybackStatsReporter
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.withSecureInMemoryCache
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaylistTrackEntity
import top.iwesley.lyn.music.data.repository.DefaultPlaybackRepository
import top.iwesley.lyn.music.data.repository.LocalPlaybackStatsReporter
import top.iwesley.lyn.music.data.repository.NavidromePlaybackStatsReporter
import top.iwesley.lyn.music.data.repository.PlaybackRepository
import top.iwesley.lyn.music.data.repository.effectiveArtworkOverridesByTrackId
import top.iwesley.lyn.music.data.repository.toDomain

class LynAutomotiveMediaService : MediaBrowserServiceCompat() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var mediaSession: MediaSessionCompat
    private var playbackRepository: PlaybackRepository? = null
    private var database: LynMusicDatabase? = null
    private var initFailure: Throwable? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(AutomotiveSessionCallback())
        }
        sessionToken = mediaSession.sessionToken
        runCatching {
            val logger = AndroidDiagnosticLogger(
                enabled = applicationContext.isDebuggableApp(),
                label = "Android Automotive",
            )
            GlobalDiagnosticLogger.installStrategy(logger)
            val openedDatabase = openAndroidRuntimeDatabase(applicationContext)
            val secureStore = AndroidCredentialStore(applicationContext, logger).withSecureInMemoryCache()
            val preferencesStore = AndroidAppPreferencesStore(applicationContext)
            val networkConnectionTypeProvider = AndroidNetworkConnectionTypeProvider(applicationContext)
            val navidromeHttpClient = AndroidLyricsHttpClient()
            val sessionControls = AutomotiveMediaSessionControls(
                context = applicationContext,
                mediaSession = mediaSession,
            )
            database = openedDatabase
            playbackRepository = DefaultPlaybackRepository(
                database = openedDatabase,
                gateway = AndroidPlaybackGateway(
                    context = applicationContext,
                    database = openedDatabase,
                    secureCredentialStore = secureStore,
                    playbackPreferencesStore = preferencesStore,
                    navidromeAudioQualityPreferencesStore = preferencesStore,
                    networkConnectionTypeProvider = networkConnectionTypeProvider,
                    logger = logger,
                ),
                playbackPreferencesStore = preferencesStore,
                scope = serviceScope,
                systemPlaybackControlsPlatformService = sessionControls,
                logger = logger,
                playbackStatsReporter = CompositePlaybackStatsReporter(
                    reporters = listOf(
                        NavidromePlaybackStatsReporter(
                            database = openedDatabase,
                            secureCredentialStore = secureStore,
                            httpClient = navidromeHttpClient,
                            logger = logger,
                        ),
                        LocalPlaybackStatsReporter(
                            database = openedDatabase,
                        ),
                    ),
                    logger = logger,
                ),
            )
        }.onFailure { throwable ->
            initFailure = throwable
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot {
        return BrowserRoot(CarMediaIds.ROOT, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        val openedDatabase = database
        if (openedDatabase == null || initFailure != null) {
            result.sendResult(mutableListOf())
            return
        }
        result.detach()
        serviceScope.launch {
            val children = withContext(Dispatchers.IO) {
                loadChildren(openedDatabase, parentId)
            }
            result.sendResult(children.toMutableList())
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        val openedDatabase = database
        if (openedDatabase == null || initFailure != null) {
            result.sendResult(mutableListOf())
            return
        }
        result.detach()
        serviceScope.launch {
            val matches = withContext(Dispatchers.IO) {
                val normalizedQuery = query.trim().lowercase()
                if (normalizedQuery.isBlank()) {
                    emptyList()
                } else {
                    loadLibrary(openedDatabase).tracks
                        .filter { track ->
                            track.title.lowercase().contains(normalizedQuery) ||
                                track.artistName.orEmpty().lowercase().contains(normalizedQuery) ||
                                track.albumTitle.orEmpty().lowercase().contains(normalizedQuery)
                        }
                        .take(MAX_SEARCH_RESULTS)
                        .map { track -> track.toPlayableMediaItem(scope = CarMediaIds.SCOPE_SEARCH) }
                }
            }
            result.sendResult(matches.toMutableList())
        }
    }

    override fun onDestroy() {
        runBlocking {
            playbackRepository?.close()
        }
        playbackRepository = null
        database = null
        mediaSession.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class AutomotiveSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            val playable = CarMediaIds.parsePlayable(mediaId) ?: return
            val openedDatabase = database ?: return
            val repository = playbackRepository ?: return
            serviceScope.launch {
                val queue = withContext(Dispatchers.IO) {
                    loadQueueForScope(openedDatabase, playable.scope)
                }
                val startIndex = queue.indexOfFirst { it.id == playable.trackId }
                if (startIndex >= 0) {
                    repository.playTracks(queue, startIndex)
                }
            }
        }

        override fun onPlay() {
            val repository = playbackRepository ?: return
            serviceScope.launch { repository.togglePlayPause() }
        }

        override fun onPause() {
            val repository = playbackRepository ?: return
            serviceScope.launch { repository.pause() }
        }

        override fun onSkipToNext() {
            val repository = playbackRepository ?: return
            serviceScope.launch { repository.skipNext() }
        }

        override fun onSkipToPrevious() {
            val repository = playbackRepository ?: return
            serviceScope.launch { repository.skipPrevious() }
        }

        override fun onSeekTo(pos: Long) {
            val repository = playbackRepository ?: return
            serviceScope.launch { repository.seekTo(pos) }
        }
    }
}

private class AutomotiveMediaSessionControls(
    private val context: Context,
    private val mediaSession: MediaSessionCompat,
) : SystemPlaybackControlsPlatformService {
    private val artworkCacheStore = createAndroidArtworkCacheStore(context)
    private var callbacks = SystemPlaybackControlCallbacks()
    private var latestArtworkKey: String? = null
    private var latestArtworkBitmap: Bitmap? = null

    override fun bind(callbacks: SystemPlaybackControlCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun updateSnapshot(snapshot: PlaybackSnapshot) {
        mediaSession.isActive = snapshot.currentTrack != null
        mediaSession.setMetadata(snapshot.toMediaMetadata(resolveArtworkBitmap(snapshot.currentDisplayArtworkLocator)))
        mediaSession.setPlaybackState(snapshot.toPlaybackState())
    }

    override suspend fun close() {
        callbacks = SystemPlaybackControlCallbacks()
        mediaSession.isActive = false
        mediaSession.setMetadata(null)
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
                .build(),
        )
    }

    private suspend fun resolveArtworkBitmap(locator: String?): Bitmap? {
        val normalized = locator?.trim().orEmpty().ifBlank { null }
        if (normalized == null) {
            latestArtworkKey = null
            latestArtworkBitmap = null
            return null
        }
        if (normalized == latestArtworkKey && latestArtworkBitmap != null) return latestArtworkBitmap
        val resolvedBitmap = resolveAndroidNotificationArtworkBitmap(normalized, artworkCacheStore)
        if (resolvedBitmap == null) {
            latestArtworkKey = null
            latestArtworkBitmap = null
            return null
        }
        latestArtworkKey = normalized
        latestArtworkBitmap = resolvedBitmap
        return latestArtworkBitmap
    }
}

private suspend fun loadChildren(
    database: LynMusicDatabase,
    parentId: String,
): List<MediaBrowserCompat.MediaItem> {
    val library = loadLibrary(database)
    return when (val node = CarMediaIds.parseBrowsable(parentId)) {
        CarBrowsableNode.Root -> listOf(
            browsableItem(CarMediaIds.ALL, "全部歌曲", "${library.tracks.size} 首"),
            browsableItem(CarMediaIds.FAVORITES, "收藏", "${library.favoriteTrackIds.size} 首"),
            browsableItem(CarMediaIds.PLAYLISTS, "歌单", "${library.playlists.size} 个"),
            browsableItem(CarMediaIds.ALBUMS, "专辑", "${library.albums.size} 张"),
            browsableItem(CarMediaIds.ARTISTS, "艺术家", "${library.artists.size} 位"),
        )

        CarBrowsableNode.All -> library.tracks.map { it.toPlayableMediaItem(CarMediaIds.SCOPE_ALL) }
        CarBrowsableNode.Favorites -> library.favoriteTracks.map { it.toPlayableMediaItem(CarMediaIds.SCOPE_FAVORITES) }
        CarBrowsableNode.Playlists -> library.playlists.map { playlist ->
            browsableItem(
                mediaId = CarMediaIds.playlist(playlist.id),
                title = playlist.name,
                subtitle = "${playlist.trackIds.size} 首",
            )
        }

        is CarBrowsableNode.Playlist -> library.playlistTracks(node.playlistId)
            .map { it.toPlayableMediaItem(CarMediaIds.playlistScope(node.playlistId)) }

        CarBrowsableNode.Albums -> library.albums.map { album ->
            browsableItem(
                mediaId = CarMediaIds.album(album.key),
                title = album.title,
                subtitle = listOfNotNull(album.artistName, "${album.tracks.size} 首").joinToString(" · "),
            )
        }

        is CarBrowsableNode.Album -> library.albumTracks(node.albumKey)
            .map { it.toPlayableMediaItem(CarMediaIds.albumScope(node.albumKey)) }

        CarBrowsableNode.Artists -> library.artists.map { artist ->
            browsableItem(
                mediaId = CarMediaIds.artist(artist.name),
                title = artist.name,
                subtitle = "${artist.tracks.size} 首",
            )
        }

        is CarBrowsableNode.Artist -> library.artistTracks(node.artistName)
            .map { it.toPlayableMediaItem(CarMediaIds.artistScope(node.artistName)) }

        null -> emptyList()
    }
}

private suspend fun loadQueueForScope(
    database: LynMusicDatabase,
    scope: String,
): List<Track> {
    val library = loadLibrary(database)
    return when {
        scope == CarMediaIds.SCOPE_ALL -> library.tracks
        scope == CarMediaIds.SCOPE_FAVORITES -> library.favoriteTracks
        scope == CarMediaIds.SCOPE_SEARCH -> library.tracks
        scope.startsWith(CarMediaIds.SCOPE_PLAYLIST_PREFIX) ->
            library.playlistTracks(scope.removePrefix(CarMediaIds.SCOPE_PLAYLIST_PREFIX))

        scope.startsWith(CarMediaIds.SCOPE_ALBUM_PREFIX) ->
            library.albumTracks(scope.removePrefix(CarMediaIds.SCOPE_ALBUM_PREFIX))

        scope.startsWith(CarMediaIds.SCOPE_ARTIST_PREFIX) ->
            library.artistTracks(scope.removePrefix(CarMediaIds.SCOPE_ARTIST_PREFIX))

        else -> emptyList()
    }
}

private suspend fun loadLibrary(database: LynMusicDatabase): CarLibrary {
    val sources = database.importSourceDao().getAll().filter { it.enabled }
    val enabledSourceIds = sources.mapTo(linkedSetOf()) { it.id }
    val trackEntities = database.trackDao().getAll().filter { it.sourceId in enabledSourceIds }
    val artworkOverrides = effectiveArtworkOverridesByTrackId(
        database.lyricsCacheDao().getArtworkLocatorsByTrackIds(trackEntities.map { it.id }),
    )
    val tracks = trackEntities
        .map { entity -> entity.toDomain(artworkOverrides[entity.id]) }
        .sortedWith(trackComparator())
    val favoriteTrackIds = database.favoriteTrackDao()
        .observeAll()
        .first()
        .mapTo(linkedSetOf()) { it.trackId }
    val tracksById = tracks.associateBy { it.id }
    val favoriteTracks = favoriteTrackIds.mapNotNull(tracksById::get)
    val playlistRows = database.playlistDao().getAll()
    val playlistTrackRows = database.playlistTrackDao().getAll()
        .filter { it.sourceId in enabledSourceIds && it.trackId in tracksById }
    val playlistTrackIdsByPlaylistId = playlistTrackRows
        .groupBy { it.playlistId }
        .mapValues { (_, rows) ->
            rows.sortedWith(
                compareBy<PlaylistTrackEntity> { row -> if (row.localOrdinal != null) 0 else 1 }
                    .thenBy { row -> row.localOrdinal ?: Int.MAX_VALUE }
                    .thenBy { row -> row.remoteOrdinal ?: Int.MAX_VALUE }
                    .thenBy { row -> row.trackId },
            ).map { it.trackId }
        }
    val playlists = playlistRows.map { playlist ->
        CarPlaylist(
            id = playlist.id,
            name = playlist.name,
            trackIds = playlistTrackIdsByPlaylistId[playlist.id].orEmpty(),
        )
    }
    val albums = tracks
        .filter { !it.albumTitle.isNullOrBlank() }
        .groupBy { CarAlbumKey(title = it.albumTitle.orEmpty(), artistName = it.albumArtistKey()) }
        .map { (key, albumTracks) ->
            CarAlbum(
                key = key.encoded,
                title = key.title,
                artistName = key.artistName,
                tracks = albumTracks.sortedWith(albumTrackComparator()),
            )
        }
        .sortedWith(compareByDescending<CarAlbum> { it.tracks.size }.thenBy { it.title.lowercase() })
    val artists = tracks
        .filter { !it.artistName.isNullOrBlank() }
        .groupBy { it.artistName.orEmpty() }
        .map { (artistName, artistTracks) ->
            CarArtist(
                name = artistName,
                tracks = artistTracks.sortedWith(trackComparator()),
            )
        }
        .sortedWith(compareByDescending<CarArtist> { it.tracks.size }.thenBy { it.name.lowercase() })
    return CarLibrary(
        tracks = tracks,
        favoriteTrackIds = favoriteTrackIds,
        favoriteTracks = favoriteTracks,
        playlists = playlists,
        albums = albums,
        artists = artists,
    )
}

private fun browsableItem(
    mediaId: String,
    title: String,
    subtitle: String? = null,
): MediaBrowserCompat.MediaItem {
    val description = MediaDescriptionCompat.Builder()
        .setMediaId(mediaId)
        .setTitle(title)
        .setSubtitle(subtitle)
        .build()
    return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
}

private fun Track.toPlayableMediaItem(scope: String): MediaBrowserCompat.MediaItem {
    val description = MediaDescriptionCompat.Builder()
        .setMediaId(CarMediaIds.track(scope = scope, trackId = id))
        .setTitle(title)
        .setSubtitle(listOfNotNull(artistName, albumTitle).joinToString(" · "))
        .build()
    return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
}

private fun PlaybackSnapshot.toMediaMetadata(artworkBitmap: Bitmap?): MediaMetadataCompat? {
    val track = currentTrack ?: return null
    val builder = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, CarMediaIds.track(CarMediaIds.SCOPE_ALL, track.id))
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentDisplayTitle.ifBlank { track.title })
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentDisplayArtistName ?: track.artistName.orEmpty())
        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentDisplayAlbumTitle ?: track.albumTitle.orEmpty())
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs.coerceAtLeast(track.durationMs))
    artworkBitmap?.let { bitmap ->
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
    }
    return builder.build()
}

private fun PlaybackSnapshot.toPlaybackState(): PlaybackStateCompat {
    val state = when {
        currentTrack == null -> PlaybackStateCompat.STATE_NONE
        isPlaying -> PlaybackStateCompat.STATE_PLAYING
        else -> PlaybackStateCompat.STATE_PAUSED
    }
    return PlaybackStateCompat.Builder()
        .setActions(playbackActions())
        .setState(
            state,
            positionMs.coerceAtLeast(0L),
            if (isPlaying) 1f else 0f,
        )
        .build()
}

private fun PlaybackSnapshot.playbackActions(): Long {
    var actions = PlaybackStateCompat.ACTION_PLAY or
        PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    if (canSeek) {
        actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
    }
    return actions
}

private fun trackComparator(): Comparator<Track> {
    return compareBy<Track> { it.title.lowercase() }
        .thenBy { it.artistName.orEmpty().lowercase() }
        .thenBy { it.albumTitle.orEmpty().lowercase() }
        .thenBy { it.id }
}

private fun albumTrackComparator(): Comparator<Track> {
    return compareBy<Track> { it.discNumber ?: Int.MAX_VALUE }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy { it.title.lowercase() }
        .thenBy { it.id }
}

private fun Track.albumArtistKey(): String? {
    return artistName?.trim()?.takeIf { it.isNotBlank() }
}

private data class CarLibrary(
    val tracks: List<Track>,
    val favoriteTrackIds: Set<String>,
    val favoriteTracks: List<Track>,
    val playlists: List<CarPlaylist>,
    val albums: List<CarAlbum>,
    val artists: List<CarArtist>,
) {
    fun playlistTracks(playlistId: String): List<Track> {
        val tracksById = tracks.associateBy { it.id }
        return playlists.firstOrNull { it.id == playlistId }
            ?.trackIds
            ?.mapNotNull(tracksById::get)
            .orEmpty()
    }

    fun albumTracks(albumKey: String): List<Track> {
        return albums.firstOrNull { it.key == albumKey }?.tracks.orEmpty()
    }

    fun artistTracks(artistName: String): List<Track> {
        return artists.firstOrNull { it.name == artistName }?.tracks.orEmpty()
    }
}

private data class CarPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
)

private data class CarAlbum(
    val key: String,
    val title: String,
    val artistName: String?,
    val tracks: List<Track>,
)

private data class CarArtist(
    val name: String,
    val tracks: List<Track>,
)

private data class CarAlbumKey(
    val title: String,
    val artistName: String?,
) {
    val encoded: String
        get() = listOf(title, artistName.orEmpty()).joinToString(ALBUM_KEY_SEPARATOR)
}

internal sealed interface CarBrowsableNode {
    data object Root : CarBrowsableNode
    data object All : CarBrowsableNode
    data object Favorites : CarBrowsableNode
    data object Playlists : CarBrowsableNode
    data class Playlist(val playlistId: String) : CarBrowsableNode
    data object Albums : CarBrowsableNode
    data class Album(val albumKey: String) : CarBrowsableNode
    data object Artists : CarBrowsableNode
    data class Artist(val artistName: String) : CarBrowsableNode
}

internal object CarMediaIds {
    const val ROOT = "root"
    const val ALL = "all"
    const val FAVORITES = "favorites"
    const val PLAYLISTS = "playlists"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val SCOPE_ALL = "all"
    const val SCOPE_FAVORITES = "favorites"
    const val SCOPE_SEARCH = "search"
    const val SCOPE_PLAYLIST_PREFIX = "playlist:"
    const val SCOPE_ALBUM_PREFIX = "album:"
    const val SCOPE_ARTIST_PREFIX = "artist:"

    fun playlist(playlistId: String): String = "playlist|${playlistId.encodeMediaPart()}"
    fun album(albumKey: String): String = "album|${albumKey.encodeMediaPart()}"
    fun artist(artistName: String): String = "artist|${artistName.encodeMediaPart()}"
    fun playlistScope(playlistId: String): String = "$SCOPE_PLAYLIST_PREFIX$playlistId"
    fun albumScope(albumKey: String): String = "$SCOPE_ALBUM_PREFIX$albumKey"
    fun artistScope(artistName: String): String = "$SCOPE_ARTIST_PREFIX$artistName"
    fun track(scope: String, trackId: String): String {
        return "track|${scope.encodeMediaPart()}|${trackId.encodeMediaPart()}"
    }

    fun parseBrowsable(mediaId: String): CarBrowsableNode? {
        return when (mediaId) {
            ROOT -> CarBrowsableNode.Root
            ALL -> CarBrowsableNode.All
            FAVORITES -> CarBrowsableNode.Favorites
            PLAYLISTS -> CarBrowsableNode.Playlists
            ALBUMS -> CarBrowsableNode.Albums
            ARTISTS -> CarBrowsableNode.Artists
            else -> {
                val parts = mediaId.split('|')
                when (parts.firstOrNull()) {
                    "playlist" -> parts.getOrNull(1)?.decodeMediaPart()?.let(CarBrowsableNode::Playlist)
                    "album" -> parts.getOrNull(1)?.decodeMediaPart()?.let(CarBrowsableNode::Album)
                    "artist" -> parts.getOrNull(1)?.decodeMediaPart()?.let(CarBrowsableNode::Artist)
                    else -> null
                }
            }
        }
    }

    fun parsePlayable(mediaId: String): PlayableMediaId? {
        val parts = mediaId.split('|')
        if (parts.size != 3 || parts[0] != "track") return null
        return PlayableMediaId(
            scope = parts[1].decodeMediaPart(),
            trackId = parts[2].decodeMediaPart(),
        )
    }
}

internal data class PlayableMediaId(
    val scope: String,
    val trackId: String,
)

private fun String.encodeMediaPart(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

private fun String.decodeMediaPart(): String = java.net.URLDecoder.decode(this, Charsets.UTF_8.name())

private const val MEDIA_SESSION_TAG = "LynAutomotiveMediaService"
private const val MAX_SEARCH_RESULTS = 50
private const val ALBUM_KEY_SEPARATOR = "\u001F"
