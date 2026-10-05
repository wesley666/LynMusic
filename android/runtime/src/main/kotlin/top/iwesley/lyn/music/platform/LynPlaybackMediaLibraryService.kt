package top.iwesley.lyn.music.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.AndroidDiagnosticLogger
import top.iwesley.lyn.music.core.model.CompositePlaybackStatsReporter
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.core.model.withSecureInMemoryCache
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaylistTrackEntity
import top.iwesley.lyn.music.data.repository.DefaultPlaybackRepository
import top.iwesley.lyn.music.data.repository.EmbyPlaybackStatsReporter
import top.iwesley.lyn.music.data.repository.FnMusicPlaybackStatsReporter
import top.iwesley.lyn.music.data.repository.LocalPlaybackStatsReporter
import top.iwesley.lyn.music.data.repository.NavidromePlaybackStatsReporter
import top.iwesley.lyn.music.data.repository.PlaybackRepository
import top.iwesley.lyn.music.data.repository.effectiveArtworkOverridesByTrackId
import top.iwesley.lyn.music.data.repository.toDomain

@OptIn(UnstableApi::class)
class LynPlaybackMediaLibraryService : MediaLibraryService() {
    private var runtime: LynPlaybackServiceRuntime? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(PLAYBACK_NOTIFICATION_ID)
                .setChannelId(PLAYBACK_NOTIFICATION_CHANNEL_ID)
                .build(),
        )
        runtime = LynPlaybackServiceRuntime.create(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return runtime?.session
    }

    override fun onDestroy() {
        runtime?.close()
        runtime = null
        super.onDestroy()
    }
}

@OptIn(UnstableApi::class)
private class LynPlaybackServiceRuntime private constructor(
    private val service: MediaLibraryService,
    private val database: LynMusicDatabase,
    val repository: PlaybackRepository,
    private val gateway: AndroidPlaybackGateway,
    val logger: DiagnosticLogger,
    private val artworkCacheStore: ArtworkCacheStore,
    private val serviceScope: CoroutineScope,
) {
    private val sessionPlayer = LynMediaSessionPlayer(
        player = gateway.currentPlayer,
        serviceScope = serviceScope,
        repository = repository,
        logger = logger,
        artworkCacheStore = artworkCacheStore,
        playFromMediaId = ::playFromMediaId,
    )

    val session: MediaLibrarySession = MediaLibrarySession.Builder(
        service,
        sessionPlayer,
        LynMediaLibrarySessionCallback(this),
    )
        .setId(PLAYBACK_SESSION_ID)
        .apply {
            buildSessionActivityPendingIntent(service)?.let { pendingIntent ->
                setSessionActivity(pendingIntent)
            }
        }
        .setBitmapLoader(
            LynMedia3ArtworkBitmapLoader(
                context = service.applicationContext,
                artworkCacheStore = artworkCacheStore,
                scope = serviceScope,
            ),
        )
        .build()

    init {
        gateway.onPlayerRecreated = { player ->
            sessionPlayer.replacePlayer(player)
        }
        AndroidPlaybackRuntimeRegistry.attach(repository)
    }

    fun close() {
        AndroidPlaybackRuntimeRegistry.detach(repository)
        session.release()
        runBlocking {
            repository.close()
        }
        serviceScope.cancel()
    }

    private fun buildSessionActivityPendingIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
            ?: return null
        intent.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            PLAYBACK_SESSION_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            runCatching { block() }
                .onSuccess(future::set)
                .onFailure(future::setException)
        }
        return future
    }

    suspend fun libraryRoot(): MediaItem = browsableMediaItem(
        mediaId = CarMediaIds.ROOT,
        title = "Lyn Music",
        subtitle = null,
    )

    suspend fun item(mediaId: String): MediaItem? {
        CarMediaIds.parseBrowsable(mediaId)?.let { node ->
            return browsableItemForNode(loadCarLibrary(database), node, mediaId)
        }
        val playable = CarMediaIds.parsePlayable(mediaId) ?: return null
        return loadCarLibrary(database).tracks
            .firstOrNull { track -> track.id == playable.trackId }
            ?.toMedia3PlayableMediaItem(playable.scope)
    }

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        return loadChildrenMediaItems(database, parentId).paged(page, pageSize)
    }

    suspend fun search(query: String, page: Int, pageSize: Int): List<MediaItem> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return emptyList()
        return loadCarLibrary(database).tracks
            .filter { track ->
                track.title.lowercase().contains(normalizedQuery) ||
                    track.artistName.orEmpty().lowercase().contains(normalizedQuery) ||
                    track.albumTitle.orEmpty().lowercase().contains(normalizedQuery)
            }
            .take(MAX_SEARCH_RESULTS)
            .map { track -> track.toMedia3PlayableMediaItem(scope = CarMediaIds.SCOPE_SEARCH) }
            .paged(page, pageSize)
    }

    private suspend fun playFromMediaId(mediaId: String) {
        val playable = CarMediaIds.parsePlayable(mediaId) ?: return
        CarMediaIds.parseQueueIndex(playable.scope)?.let { queueIndex ->
            val snapshot = repository.snapshot.value
            val startIndex = when {
                snapshot.queue.getOrNull(queueIndex)?.id == playable.trackId -> queueIndex
                else -> snapshot.queue.indexOfFirst { track -> track.id == playable.trackId }
            }
            if (startIndex >= 0) {
                logger.info(SERVICE_PLAYBACK_LOG_TAG) {
                    "service-play-from-media-id mediaId=$mediaId queueIndex=$startIndex " +
                        "queueSize=${snapshot.queue.size}"
                }
                repository.playQueueIndex(startIndex)
            }
            return
        }
        val queue = withContext(Dispatchers.IO) {
            loadQueueForScope(database, playable.scope)
        }
        val startIndex = queue.indexOfFirst { track -> track.id == playable.trackId }
        if (startIndex >= 0) {
            logger.info(SERVICE_PLAYBACK_LOG_TAG) {
                "service-play-from-media-id mediaId=$mediaId startIndex=$startIndex queueSize=${queue.size}"
            }
            repository.playTracks(queue, startIndex)
        }
    }

    companion object {
        fun create(service: MediaLibraryService): LynPlaybackServiceRuntime {
            val appContext = service.applicationContext
            val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val logger = AndroidDiagnosticLogger(
                enabled = true,
                label = "Android Playback Service",
            )
            GlobalDiagnosticLogger.installStrategy(logger)
            val database = openAndroidRuntimeDatabase(appContext)
            val secureStore = AndroidCredentialStore(appContext, logger).withSecureInMemoryCache()
            val preferencesStore = AndroidAppPreferencesStore(appContext)
            val networkConnectionTypeProvider = AndroidNetworkConnectionTypeProvider.get(appContext)
            val remoteSourceAddressSelector = AndroidRemoteSourceAddressSelector.get(appContext)
            val navidromeHttpClient = AndroidLyricsHttpClient()
            val artworkCacheStore = createAndroidArtworkCacheStore(appContext)
            val gateway = AndroidPlaybackGateway(
                context = appContext,
                database = database,
                secureCredentialStore = secureStore,
                playbackPreferencesStore = preferencesStore,
                equalizerPreferencesStore = preferencesStore,
                playbackDecoderPreferencesStore = preferencesStore,
                navidromeAudioQualityPreferencesStore = preferencesStore,
                networkConnectionTypeProvider = networkConnectionTypeProvider,
                addressSelector = remoteSourceAddressSelector,
                logger = logger,
                fnMusicHttpClient = navidromeHttpClient,
            )
            val repository = DefaultPlaybackRepository(
                database = database,
                gateway = gateway,
                playbackPreferencesStore = preferencesStore,
                scope = serviceScope,
                systemPlaybackControlsPlatformService = createAndroidAudioFocusPlaybackControlsPlatformService(appContext),
                logger = logger,
                playbackStatsReporter = CompositePlaybackStatsReporter(
                    reporters = listOf(
                        NavidromePlaybackStatsReporter(
                            database = database,
                            secureCredentialStore = secureStore,
                            httpClient = navidromeHttpClient,
                            addressSelector = remoteSourceAddressSelector,
                            logger = logger,
                        ),
                        EmbyPlaybackStatsReporter(
                            database = database,
                            secureCredentialStore = secureStore,
                            httpClient = navidromeHttpClient,
                            addressSelector = remoteSourceAddressSelector,
                            logger = logger,
                        ),
                        FnMusicPlaybackStatsReporter(
                            database = database,
                            secureCredentialStore = secureStore,
                            httpClient = navidromeHttpClient,
                            addressSelector = remoteSourceAddressSelector,
                            logger = logger,
                        ),
                        LocalPlaybackStatsReporter(database = database),
                    ),
                    logger = logger,
                ),
                hydrateImmediately = false,
            )
            return LynPlaybackServiceRuntime(
                service = service,
                database = database,
                repository = repository,
                gateway = gateway,
                logger = logger,
                artworkCacheStore = artworkCacheStore,
                serviceScope = serviceScope,
            )
        }
    }
}

@OptIn(UnstableApi::class)
private class LynMediaLibrarySessionCallback(
    private val runtime: LynPlaybackServiceRuntime,
) : MediaLibrarySession.Callback {
    @Deprecated("Deprecated in Media3")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Int {
        runtime.logger.info(SERVICE_PLAYBACK_LOG_TAG) {
            "session-player-command controller=${controller.packageName} uid=${controller.uid} command=$playerCommand"
        }
        return SessionResult.RESULT_SUCCESS
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return runtime.future {
            LibraryResult.ofItem(runtime.libraryRoot(), params)
        }
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return runtime.future {
            runtime.item(mediaId)?.let { item ->
                LibraryResult.ofItem(item, null)
            } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return runtime.future {
            LibraryResult.ofItemList(runtime.children(parentId, page, pageSize), params)
        }
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        return runtime.future {
            val count = runtime.search(query, page = 0, pageSize = MAX_SEARCH_RESULTS).size
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid(params)
        }
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return runtime.future {
            LibraryResult.ofItemList(runtime.search(query, page, pageSize), params)
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        return Futures.immediateFuture(mediaItems)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs),
        )
    }
}

@OptIn(UnstableApi::class)
private class LynMediaSessionPlayer(
    player: Player,
    private val serviceScope: CoroutineScope,
    private val repository: PlaybackRepository,
    private val logger: DiagnosticLogger,
    private val artworkCacheStore: ArtworkCacheStore,
    private val playFromMediaId: suspend (String) -> Unit,
) : ForwardingSimpleBasePlayer(player) {
    private var observedArtworkCacheKey: String? = null
    private var artworkVersion = 0L
    private var artworkVersionJob: Job? = null
    private var lastSessionStateKey: Media3SessionStateKey? = null

    init {
        serviceScope.launch {
            repository.snapshot.collect { snapshot ->
                updateArtworkVersionObserver(snapshot)
                val nextKey = snapshot.toMedia3SessionStateKey()
                if (nextKey != lastSessionStateKey) {
                    lastSessionStateKey = nextKey
                    invalidateState()
                }
            }
        }
    }

    fun replacePlayer(player: Player) {
        setPlayer(player)
    }

    override fun getState(): State {
        val state = super.getState()
        val commands = Player.Commands.Builder()
            .addAll(state.availableCommands)
            .remove(Player.COMMAND_STOP)
            .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .add(Player.COMMAND_SET_VOLUME)
            .build()
        val snapshot = repository.snapshot.value
        val metadata = snapshot.toMedia3SessionMetadata(artworkVersion)
        val playlist = snapshot.toMedia3SessionPlaylist(artworkVersion)
        return state.buildUpon()
            .setAvailableCommands(commands)
            .setPlayWhenReady(snapshot.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .apply {
                if (playlist.items.isNotEmpty()) {
                    setPlaylist(playlist.items)
                    setCurrentMediaItemIndex(playlist.currentMediaItemIndex)
                    setContentPositionMs(snapshot.coercedSessionPositionMs())
                    setPlaylistMetadata(metadata ?: MediaMetadata.EMPTY)
                    if (snapshot.isPlaying && state.playbackState == Player.STATE_IDLE && state.playerError == null) {
                        setPlaybackState(Player.STATE_BUFFERING)
                        setIsLoading(true)
                    }
                } else if (metadata != null && !state.timeline.isEmpty) {
                    setPlaylist(state.timeline, state.currentTracks, metadata)
                    setPlaylistMetadata(metadata)
                }
            }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val snapshot = repository.snapshot.value
        logger.warn(SERVICE_PLAYBACK_LOG_TAG) {
                "session-handle-set-play-when-ready requested=$playWhenReady " +
                "snapshotPlaying=${snapshot.isPlaying} track=${snapshot.currentTrack?.id.orEmpty()} " +
                "index=${snapshot.currentIndex} position=${snapshot.positionMs} " +
                "playerPlayWhenReady=${this.playWhenReady} playerState=$playbackState"
        }
        serviceScope.launch {
            if (playWhenReady) {
                if (!repository.snapshot.value.isPlaying) {
                    repository.togglePlayPause()
                }
            } else {
                repository.pause()
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        serviceScope.launch {
            repository.hydratePersistedQueueIfNeeded()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        val snapshot = repository.snapshot.value
        logger.warn(SERVICE_PLAYBACK_LOG_TAG) {
            "session-handle-stop-ignored snapshotPlaying=${snapshot.isPlaying} track=${snapshot.currentTrack?.id.orEmpty()} " +
                "position=${snapshot.positionMs}"
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val mediaItem = mediaItems.getOrNull(startIndex.takeIf { it >= 0 } ?: 0)
            ?: mediaItems.firstOrNull()
        val mediaId = mediaItem?.mediaId
        logger.info(SERVICE_PLAYBACK_LOG_TAG) {
            "session-handle-set-media-items size=${mediaItems.size} startIndex=$startIndex " +
                "startPositionMs=$startPositionMs mediaId=${mediaId.orEmpty()}"
        }
        if (mediaId != null && CarMediaIds.parsePlayable(mediaId) != null) {
            serviceScope.launch { playFromMediaId(mediaId) }
            return Futures.immediateVoidFuture()
        }
        return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        serviceScope.launch {
            when (seekCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                -> repository.skipNext()

                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                -> repository.skipPrevious()

                Player.COMMAND_SEEK_BACK -> repository.seekTo(
                    (repository.snapshot.value.positionMs - C.DEFAULT_SEEK_BACK_INCREMENT_MS).coerceAtLeast(0L),
                )

                Player.COMMAND_SEEK_FORWARD -> repository.seekTo(
                    repository.snapshot.value.positionMs + C.DEFAULT_SEEK_FORWARD_INCREMENT_MS,
                )

                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                -> repository.seekTo(positionMs.coerceAtLeast(0L))

                Player.COMMAND_SEEK_TO_MEDIA_ITEM -> {
                    val snapshot = repository.snapshot.value
                    val queueIndex = snapshot.sessionQueueIndexForMediaItemIndex(mediaItemIndex)
                    if (queueIndex != null && queueIndex != snapshot.currentIndex) {
                        repository.playQueueIndex(queueIndex)
                    } else {
                        repository.seekTo(positionMs.coerceAtLeast(0L))
                    }
                }
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
        if (volumeOperationType == C.VOLUME_OPERATION_TYPE_SET_VOLUME) {
            serviceScope.launch { repository.setVolume(volume) }
            return Futures.immediateVoidFuture()
        }
        return super.handleSetVolume(volume, volumeOperationType)
    }

    private fun updateArtworkVersionObserver(snapshot: PlaybackSnapshot) {
        val nextKey = AndroidNotificationArtworkLookup.from(snapshot)?.cacheKey
        if (nextKey == observedArtworkCacheKey) return
        artworkVersionJob?.cancel()
        artworkVersionJob = null
        observedArtworkCacheKey = nextKey
        artworkVersion = 0L
        if (nextKey == null) return
        artworkVersionJob = serviceScope.launch {
            artworkCacheStore.observeVersion(nextKey).collect { version ->
                if (artworkVersion != version) {
                    artworkVersion = version
                    invalidateState()
                }
            }
        }
    }
}

private suspend fun loadChildrenMediaItems(
    database: LynMusicDatabase,
    parentId: String,
): List<MediaItem> {
    val library = loadCarLibrary(database)
    return when (val node = CarMediaIds.parseBrowsable(parentId)) {
        CarBrowsableNode.Root -> listOf(
            browsableMediaItem(CarMediaIds.ALL, "全部歌曲", "${library.tracks.size} 首"),
            browsableMediaItem(CarMediaIds.FAVORITES, "收藏", "${library.favoriteTrackIds.size} 首"),
            browsableMediaItem(CarMediaIds.PLAYLISTS, "歌单", "${library.playlists.size} 个"),
            browsableMediaItem(CarMediaIds.ALBUMS, "专辑", "${library.albums.size} 张"),
            browsableMediaItem(CarMediaIds.ARTISTS, "艺术家", "${library.artists.size} 位"),
        )

        CarBrowsableNode.All -> library.tracks.map { it.toMedia3PlayableMediaItem(CarMediaIds.SCOPE_ALL) }
        CarBrowsableNode.Favorites -> library.favoriteTracks.map {
            it.toMedia3PlayableMediaItem(CarMediaIds.SCOPE_FAVORITES)
        }

        CarBrowsableNode.Playlists -> library.playlists.map { playlist ->
            browsableMediaItem(
                mediaId = CarMediaIds.playlist(playlist.id),
                title = playlist.name,
                subtitle = "${playlist.trackIds.size} 首",
            )
        }

        is CarBrowsableNode.Playlist -> library.playlistTracks(node.playlistId)
            .map { it.toMedia3PlayableMediaItem(CarMediaIds.playlistScope(node.playlistId)) }

        CarBrowsableNode.Albums -> library.albums.map { album ->
            browsableMediaItem(
                mediaId = CarMediaIds.album(album.key),
                title = album.title,
                subtitle = listOfNotNull(album.artistName, "${album.tracks.size} 首").joinToString(" · "),
            )
        }

        is CarBrowsableNode.Album -> library.albumTracks(node.albumKey)
            .map { it.toMedia3PlayableMediaItem(CarMediaIds.albumScope(node.albumKey)) }

        CarBrowsableNode.Artists -> library.artists.map { artist ->
            browsableMediaItem(
                mediaId = CarMediaIds.artist(artist.name),
                title = artist.name,
                subtitle = "${artist.tracks.size} 首",
            )
        }

        is CarBrowsableNode.Artist -> library.artistTracks(node.artistName)
            .map { it.toMedia3PlayableMediaItem(CarMediaIds.artistScope(node.artistName)) }

        null -> emptyList()
    }
}

private suspend fun loadQueueForScope(
    database: LynMusicDatabase,
    scope: String,
): List<Track> {
    val library = loadCarLibrary(database)
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

private suspend fun loadCarLibrary(database: LynMusicDatabase): CarLibrarySnapshot = withContext(Dispatchers.IO) {
    val sources = database.importSourceDao().getAll().filter { it.enabled }
    val enabledSourceIds = sources.mapTo(linkedSetOf()) { it.id }
    val trackEntities = database.trackDao().getAll().filter { it.sourceId in enabledSourceIds }
    val artworkOverrides = effectiveArtworkOverridesByTrackId(
        database.lyricsCacheDao().getArtworkLocatorsByTrackIds(trackEntities.map { it.id }),
    )
    val tracks = trackEntities
        .map { entity -> entity.toDomain(artworkOverrides[entity.id]) }
        .sortedWith(carTrackComparator())
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
        CarPlaylistSnapshot(
            id = playlist.id,
            name = playlist.name,
            trackIds = playlistTrackIdsByPlaylistId[playlist.id].orEmpty(),
        )
    }
    val albums = tracks
        .filter { !it.albumTitle.isNullOrBlank() }
        .groupBy { CarAlbumKeySnapshot(title = it.albumTitle.orEmpty(), artistName = it.albumArtistKey()) }
        .map { (key, albumTracks) ->
            CarAlbumSnapshot(
                key = key.encoded,
                title = key.title,
                artistName = key.artistName,
                tracks = albumTracks.sortedWith(carAlbumTrackComparator()),
            )
        }
        .sortedWith(compareByDescending<CarAlbumSnapshot> { it.tracks.size }.thenBy { it.title.lowercase() })
    val artists = tracks
        .filter { !it.artistName.isNullOrBlank() }
        .groupBy { it.artistName.orEmpty() }
        .map { (artistName, artistTracks) ->
            CarArtistSnapshot(
                name = artistName,
                tracks = artistTracks.sortedWith(carTrackComparator()),
            )
        }
        .sortedWith(compareByDescending<CarArtistSnapshot> { it.tracks.size }.thenBy { it.name.lowercase() })
    CarLibrarySnapshot(
        tracks = tracks,
        favoriteTrackIds = favoriteTrackIds,
        favoriteTracks = favoriteTracks,
        playlists = playlists,
        albums = albums,
        artists = artists,
    )
}

private fun browsableItemForNode(
    library: CarLibrarySnapshot,
    node: CarBrowsableNode,
    mediaId: String,
): MediaItem {
    return when (node) {
        CarBrowsableNode.Root -> browsableMediaItem(mediaId, "Lyn Music")
        CarBrowsableNode.All -> browsableMediaItem(mediaId, "全部歌曲", "${library.tracks.size} 首")
        CarBrowsableNode.Favorites -> browsableMediaItem(mediaId, "收藏", "${library.favoriteTrackIds.size} 首")
        CarBrowsableNode.Playlists -> browsableMediaItem(mediaId, "歌单", "${library.playlists.size} 个")
        is CarBrowsableNode.Playlist -> {
            val playlist = library.playlists.firstOrNull { it.id == node.playlistId }
            browsableMediaItem(mediaId, playlist?.name ?: "歌单", "${playlist?.trackIds?.size ?: 0} 首")
        }

        CarBrowsableNode.Albums -> browsableMediaItem(mediaId, "专辑", "${library.albums.size} 张")
        is CarBrowsableNode.Album -> {
            val album = library.albums.firstOrNull { it.key == node.albumKey }
            browsableMediaItem(mediaId, album?.title ?: "专辑", "${album?.tracks?.size ?: 0} 首")
        }

        CarBrowsableNode.Artists -> browsableMediaItem(mediaId, "艺术家", "${library.artists.size} 位")
        is CarBrowsableNode.Artist -> {
            val artist = library.artists.firstOrNull { it.name == node.artistName }
            browsableMediaItem(mediaId, artist?.name ?: "艺术家", "${artist?.tracks?.size ?: 0} 首")
        }
    }
}

private fun browsableMediaItem(
    mediaId: String,
    title: String,
    subtitle: String? = null,
): MediaItem {
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build(),
        )
        .build()
}

private fun Track.toMedia3PlayableMediaItem(scope: String): MediaItem {
    return toMedia3PlayableMediaItem(
        scope = scope,
        metadata = toMedia3TrackMetadata(artworkVersion = 0L),
    )
}

private fun Track.toMedia3PlayableMediaItem(
    scope: String,
    metadata: MediaMetadata,
): MediaItem {
    return MediaItem.Builder()
        .setMediaId(CarMediaIds.track(scope = scope, trackId = id))
        .setMediaMetadata(metadata)
        .build()
}

private fun Track.toMedia3TrackMetadata(artworkVersion: Long): MediaMetadata {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artistName)
        .setAlbumTitle(albumTitle)
        .setSubtitle(listOfNotNull(artistName, albumTitle).joinToString(" · "))
        .setIsBrowsable(false)
        .setIsPlayable(true)
    buildLynArtworkUri(
        locator = artworkLocator,
        cacheKey = trackArtworkCacheKey(this),
        version = artworkVersion,
    )?.let(metadata::setArtworkUri)
    return metadata.build()
}

private fun PlaybackSnapshot.toMedia3SessionMetadata(artworkVersion: Long): MediaMetadata? {
    val track = currentTrack ?: return null
    val metadata = MediaMetadata.Builder()
        .setTitle(currentDisplayTitle.ifBlank { track.title })
        .setArtist(currentDisplayArtistName ?: track.artistName)
        .setAlbumTitle(currentDisplayAlbumTitle ?: track.albumTitle)
        .setIsBrowsable(false)
        .setIsPlayable(true)
    buildLynArtworkUri(
        locator = currentDisplayArtworkLocator,
        cacheKey = trackArtworkCacheKey(track),
        version = artworkVersion,
    )?.let(metadata::setArtworkUri)
    return metadata.build()
}

@OptIn(UnstableApi::class)
private data class Media3SessionPlaylist(
    val items: ImmutableList<SimpleBasePlayer.MediaItemData>,
    val currentMediaItemIndex: Int,
)

private data class Media3SessionStateKey(
    val currentIndex: Int,
    val currentTrackId: String?,
    val isPlaying: Boolean,
    val durationMs: Long,
    val canSeek: Boolean,
    val displayTitle: String,
    val displayArtistName: String?,
    val displayAlbumTitle: String?,
    val displayArtworkLocator: String?,
    val windowItems: List<Media3SessionWindowItemKey>,
)

private data class Media3SessionWindowItemKey(
    val queueIndex: Int,
    val trackId: String,
    val title: String,
    val artistName: String?,
    val albumTitle: String?,
    val artworkLocator: String?,
    val durationMs: Long,
)

@OptIn(UnstableApi::class)
private fun PlaybackSnapshot.toMedia3SessionPlaylist(
    artworkVersion: Long,
): Media3SessionPlaylist {
    val playlist = ImmutableList.builder<SimpleBasePlayer.MediaItemData>()
    var currentMediaItemIndex = C.INDEX_UNSET
    sessionQueueWindowIndices().forEachIndexed { mediaItemIndex, queueIndex ->
        val track = queue.getOrNull(queueIndex) ?: return@forEachIndexed
        val isCurrent = queueIndex == currentIndex
        if (isCurrent) {
            currentMediaItemIndex = mediaItemIndex
        }
        val metadata = if (isCurrent) {
            toMedia3SessionMetadata(artworkVersion) ?: track.toMedia3TrackMetadata(artworkVersion = 0L)
        } else {
            track.toMedia3TrackMetadata(artworkVersion = 0L)
        }
        val durationMs = when {
            isCurrent && this.durationMs > 0L -> this.durationMs
            track.durationMs > 0L -> track.durationMs
            else -> 0L
        }
        playlist.add(
            SimpleBasePlayer.MediaItemData.Builder("queue|$queueIndex|${track.id}")
                .setMediaItem(
                    track.toMedia3PlayableMediaItem(
                        scope = CarMediaIds.queueScope(queueIndex),
                        metadata = metadata,
                    ),
                )
                .setMediaMetadata(metadata)
                .setDurationUs(durationMs.toMedia3DurationUs())
                .setIsSeekable(if (isCurrent) canSeek || durationMs > 0L else durationMs > 0L)
                .build(),
        )
    }
    return Media3SessionPlaylist(
        items = playlist.build(),
        currentMediaItemIndex = currentMediaItemIndex,
    )
}

private fun PlaybackSnapshot.toMedia3SessionStateKey(): Media3SessionStateKey {
    return Media3SessionStateKey(
        currentIndex = currentIndex,
        currentTrackId = currentTrack?.id,
        isPlaying = isPlaying,
        durationMs = durationMs,
        canSeek = canSeek,
        displayTitle = currentDisplayTitle,
        displayArtistName = currentDisplayArtistName,
        displayAlbumTitle = currentDisplayAlbumTitle,
        displayArtworkLocator = currentDisplayArtworkLocator,
        windowItems = sessionQueueWindowIndices().mapNotNull { queueIndex ->
            queue.getOrNull(queueIndex)?.let { track ->
                Media3SessionWindowItemKey(
                    queueIndex = queueIndex,
                    trackId = track.id,
                    title = track.title,
                    artistName = track.artistName,
                    albumTitle = track.albumTitle,
                    artworkLocator = track.artworkLocator,
                    durationMs = track.durationMs,
                )
            }
        },
    )
}

private fun PlaybackSnapshot.sessionQueueIndexForMediaItemIndex(mediaItemIndex: Int): Int? {
    if (mediaItemIndex < 0) return null
    return sessionQueueWindowIndices().getOrNull(mediaItemIndex)
}

private fun PlaybackSnapshot.sessionQueueWindowIndices(): List<Int> {
    val current = currentIndex.takeIf { index -> index in queue.indices } ?: return emptyList()
    return buildList {
        if (current > 0) add(current - 1)
        add(current)
        if (current < queue.lastIndex) add(current + 1)
    }
}

private fun PlaybackSnapshot.coercedSessionPositionMs(): Long {
    val position = positionMs.coerceAtLeast(0L)
    val duration = when {
        durationMs > 0L -> durationMs
        (currentTrack?.durationMs ?: 0L) > 0L -> currentTrack?.durationMs ?: 0L
        else -> 0L
    }
    return if (duration > 0L) position.coerceAtMost(duration) else position
}

private fun Long.toMedia3DurationUs(): Long {
    if (this <= 0L) return C.TIME_UNSET
    return coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
}

private fun buildLynArtworkUri(
    locator: String?,
    cacheKey: String?,
    version: Long,
): Uri? {
    val normalizedLocator = locator?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalizedCacheKey = cacheKey?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedLocator
    return Uri.Builder()
        .scheme(LYN_ARTWORK_URI_SCHEME)
        .authority(LYN_ARTWORK_URI_AUTHORITY)
        .appendPath(LYN_ARTWORK_URI_CURRENT_PATH)
        .appendQueryParameter(LYN_ARTWORK_URI_QUERY_LOCATOR, normalizedLocator)
        .appendQueryParameter(LYN_ARTWORK_URI_QUERY_CACHE_KEY, normalizedCacheKey)
        .appendQueryParameter(LYN_ARTWORK_URI_QUERY_VERSION, version.coerceAtLeast(0L).toString())
        .build()
}

@OptIn(UnstableApi::class)
private class LynMedia3ArtworkBitmapLoader(
    context: Context,
    private val artworkCacheStore: ArtworkCacheStore,
    private val scope: CoroutineScope,
) : BitmapLoader {
    private val delegate = DataSourceBitmapLoader.Builder(context).build()

    override fun supportsMimeType(mimeType: String): Boolean {
        return delegate.supportsMimeType(mimeType)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return delegate.decodeBitmap(data)
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (!uri.isLynArtworkUri()) {
            return delegate.loadBitmap(uri)
        }
        val locator = uri.getQueryParameter(LYN_ARTWORK_URI_QUERY_LOCATOR)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return Futures.immediateFailedFuture(IllegalArgumentException("Missing artwork locator"))
        val cacheKey = uri.getQueryParameter(LYN_ARTWORK_URI_QUERY_CACHE_KEY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: locator
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            runCatching {
                resolveAndroidNotificationArtworkBitmap(
                    locator = locator,
                    artworkCacheKey = cacheKey,
                    artworkCacheStore = artworkCacheStore,
                ) ?: error("Unable to resolve artwork bitmap")
            }.onSuccess(future::set)
                .onFailure(future::setException)
        }
        return future
    }
}

private fun Uri.isLynArtworkUri(): Boolean {
    return scheme == LYN_ARTWORK_URI_SCHEME &&
        authority == LYN_ARTWORK_URI_AUTHORITY &&
        path == "/$LYN_ARTWORK_URI_CURRENT_PATH"
}

private fun <T> List<T>.paged(page: Int, pageSize: Int): List<T> {
    if (pageSize <= 0) return this
    val fromIndex = (page.toLong() * pageSize.toLong()).coerceAtMost(size.toLong()).toInt()
    val toIndex = (fromIndex.toLong() + pageSize.toLong()).coerceAtMost(size.toLong()).toInt()
    return subList(fromIndex, toIndex)
}

private fun carTrackComparator(): Comparator<Track> {
    return compareBy<Track> { it.title.lowercase() }
        .thenBy { it.artistName.orEmpty().lowercase() }
        .thenBy { it.albumTitle.orEmpty().lowercase() }
        .thenBy { it.id }
}

private fun carAlbumTrackComparator(): Comparator<Track> {
    return compareBy<Track> { it.discNumber ?: Int.MAX_VALUE }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy { it.title.lowercase() }
        .thenBy { it.id }
}

private fun Track.albumArtistKey(): String? {
    return artistName?.trim()?.takeIf { it.isNotBlank() }
}

private data class CarLibrarySnapshot(
    val tracks: List<Track>,
    val favoriteTrackIds: Set<String>,
    val favoriteTracks: List<Track>,
    val playlists: List<CarPlaylistSnapshot>,
    val albums: List<CarAlbumSnapshot>,
    val artists: List<CarArtistSnapshot>,
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

private data class CarPlaylistSnapshot(
    val id: String,
    val name: String,
    val trackIds: List<String>,
)

private data class CarAlbumSnapshot(
    val key: String,
    val title: String,
    val artistName: String?,
    val tracks: List<Track>,
)

private data class CarArtistSnapshot(
    val name: String,
    val tracks: List<Track>,
)

private data class CarAlbumKeySnapshot(
    val title: String,
    val artistName: String?,
) {
    val encoded: String
        get() = listOf(title, artistName.orEmpty()).joinToString(ALBUM_KEY_SEPARATOR)
}

private const val PLAYBACK_SESSION_ID = "lynmusic-playback"
private const val SERVICE_PLAYBACK_LOG_TAG = "Playback"
private const val PLAYBACK_NOTIFICATION_ID = 3107
private const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "lynmusic.playback"
private const val PLAYBACK_SESSION_ACTIVITY_REQUEST_CODE = 3108
private const val MAX_SEARCH_RESULTS = 50
private const val ALBUM_KEY_SEPARATOR = "\u001F"
private const val LYN_ARTWORK_URI_SCHEME = "lynmusic-artwork"
private const val LYN_ARTWORK_URI_AUTHORITY = "playback"
private const val LYN_ARTWORK_URI_CURRENT_PATH = "current"
private const val LYN_ARTWORK_URI_QUERY_LOCATOR = "locator"
private const val LYN_ARTWORK_URI_QUERY_CACHE_KEY = "cacheKey"
private const val LYN_ARTWORK_URI_QUERY_VERSION = "version"
