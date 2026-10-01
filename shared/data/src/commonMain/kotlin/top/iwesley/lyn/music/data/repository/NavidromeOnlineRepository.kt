package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.checkUi
import top.iwesley.lyn.music.core.model.requireUi
import top.iwesley.lyn.music.core.model.uiText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.PlaylistTrackEntry
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildSubsonicCompatibleCoverLocator
import top.iwesley.lyn.music.core.model.buildSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.isSubsonicCompatibleSourceType
import top.iwesley.lyn.music.domain.normalizeSubsonicBaseUrl
import top.iwesley.lyn.music.domain.requestNavidromeJson
import top.iwesley.lyn.music.domain.requestNavidromeJsonWithRepeatedParameters
import top.iwesley.lyn.music.domain.requestNavidromeSongPage
import top.iwesley.lyn.music.domain.toSubsonicAuthMode

data class OnlinePage<T>(
    val items: List<T>,
    val totalCount: Int? = null,
    val offset: Int = 0,
    val limit: Int = items.size,
) {
    val hasMore: Boolean
        get() {
            val total = totalCount
            return if (total != null) {
                offset + items.size < total
            } else {
                items.size >= limit && items.isNotEmpty()
            }
        }
}

data class OnlineLibrarySearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<OnlineAlbumItem> = emptyList(),
    val artists: List<OnlineArtistItem> = emptyList(),
)

data class OnlineAlbumItem(
    val album: Album,
    val artworkLocator: String? = null,
)

data class OnlineArtistItem(
    val artist: Artist,
    val trackCount: Int? = null,
    val albumCount: Int? = null,
)

interface NavidromeOnlineDataSource {
    suspend fun tracks(sourceId: String, offset: Int, limit: Int): OnlinePage<Track>
    suspend fun albums(sourceId: String, offset: Int, limit: Int): OnlinePage<OnlineAlbumItem>
    suspend fun artists(sourceId: String, offset: Int, limit: Int): OnlinePage<OnlineArtistItem>
    suspend fun search(sourceId: String, query: String, offset: Int, limit: Int): OnlineLibrarySearchResult
    suspend fun searchTracks(sourceId: String, query: String, offset: Int, limit: Int): OnlinePage<Track>
    suspend fun searchAlbums(sourceId: String, query: String, offset: Int, limit: Int): OnlinePage<OnlineAlbumItem>
    suspend fun searchArtists(sourceId: String, query: String, offset: Int, limit: Int): OnlinePage<OnlineArtistItem>
    suspend fun artistAlbums(sourceId: String, artistId: String): List<OnlineAlbumItem>
    suspend fun albumTracks(sourceId: String, albumId: String): List<Track>
    suspend fun favoriteTracks(sourceId: String, offset: Int, limit: Int, query: String): OnlinePage<Track>
    suspend fun setFavorite(sourceId: String, track: Track, favorite: Boolean)
    suspend fun playlists(sourceId: String): List<PlaylistSummary>
    suspend fun playlistDetail(sourceId: String, playlistId: String): PlaylistDetail?
    suspend fun createPlaylist(sourceId: String, name: String): PlaylistSummary
    suspend fun renamePlaylist(sourceId: String, playlistId: String, name: String)
    suspend fun deletePlaylist(sourceId: String, playlistId: String)
    suspend fun addTrackToPlaylist(sourceId: String, playlistId: String, track: Track)
    suspend fun importPlaylistText(sourceId: String, playlistId: String, text: String): PlaylistImportReport
    suspend fun removeTrackFromPlaylist(sourceId: String, playlistId: String, index: Int)
}

class NavidromeOnlineRepository(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val httpClient: LyricsHttpClient,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    private val addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
) : NavidromeOnlineDataSource {
    private val artistsCacheBySourceId = mutableMapOf<String, OnlineArtistsCacheEntry>()

    override suspend fun tracks(
        sourceId: String,
        offset: Int,
        limit: Int,
    ): OnlinePage<Track> {
        val source = requireOnlineNavidromeSource(sourceId)
        val page = requestNavidromeSongPage(
            httpClient = httpClient,
            source = source,
            start = offset,
            pageSize = limit,
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        return OnlinePage(
            items = page.songs.map { it.toOnlineTrack(source) },
            totalCount = page.totalTrackCount,
            offset = offset,
            limit = limit,
        )
    }

    override suspend fun albums(
        sourceId: String,
        offset: Int,
        limit: Int,
    ): OnlinePage<OnlineAlbumItem> {
        val source = requireOnlineNavidromeSource(sourceId)
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getAlbumList2",
            parameters = mapOf(
                "type" to "alphabeticalByName",
                "size" to limit.coerceAtLeast(1).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            ),
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        val albums = payload["albumList2"].asObjectOrNull()
            ?.get("album")
            .asObjectList()
            .mapNotNull { it.toOnlineAlbum(source) }
        return OnlinePage(items = albums, offset = offset, limit = limit)
    }

    override suspend fun artists(
        sourceId: String,
        offset: Int,
        limit: Int,
    ): OnlinePage<OnlineArtistItem> {
        val source = requireOnlineNavidromeSource(sourceId)
        val start = offset.coerceAtLeast(0)
        val limitValue = limit.coerceAtLeast(1)
        val fingerprint = source.onlineArtistsCacheFingerprint()
        val allArtists = if (start == 0) {
            fetchAllOnlineArtists(source).also { artists ->
                artistsCacheBySourceId[sourceId] = OnlineArtistsCacheEntry(
                    fingerprint = fingerprint,
                    artists = artists,
                )
            }
        } else {
            artistsCacheBySourceId[sourceId]
                ?.takeIf { it.fingerprint == fingerprint }
                ?.artists
                ?: fetchAllOnlineArtists(source).also { artists ->
                    artistsCacheBySourceId[sourceId] = OnlineArtistsCacheEntry(
                        fingerprint = fingerprint,
                        artists = artists,
                    )
                }
        }
        return OnlinePage(
            items = allArtists.drop(start).take(limitValue),
            totalCount = allArtists.size,
            offset = start,
            limit = limitValue,
        )
    }

    private suspend fun fetchAllOnlineArtists(source: NavidromeResolvedSource): List<OnlineArtistItem> {
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getArtists",
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        return payload["artists"].asObjectOrNull()
            ?.get("index")
            .asObjectList()
            .flatMap { index -> index["artist"].asObjectList() }
            .mapNotNull { it.toOnlineArtist() }
            .orEmpty()
    }

    override suspend fun search(
        sourceId: String,
        query: String,
        offset: Int,
        limit: Int,
    ): OnlineLibrarySearchResult {
        val source = requireOnlineNavidromeSource(sourceId)
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return OnlineLibrarySearchResult()
        val count = limit.coerceAtLeast(1)
        val pageOffset = offset.coerceAtLeast(0)
        val result = requestSearch3Result(
            source = source,
            query = normalizedQuery,
            songOffset = pageOffset,
            songCount = count,
            albumOffset = pageOffset,
            albumCount = count,
            artistOffset = pageOffset,
            artistCount = count,
        )
        return OnlineLibrarySearchResult(
            tracks = result?.get("song").asObjectList().mapNotNull { it.toOnlineTrack(source) },
            albums = result?.get("album").asObjectList().mapNotNull { it.toOnlineAlbum(source) },
            artists = result?.get("artist").asObjectList().mapNotNull { it.toOnlineArtist() },
        )
    }

    override suspend fun searchTracks(
        sourceId: String,
        query: String,
        offset: Int,
        limit: Int,
    ): OnlinePage<Track> {
        val source = requireOnlineNavidromeSource(sourceId)
        val normalizedQuery = query.trim()
        val start = offset.coerceAtLeast(0)
        val limitValue = limit.coerceAtLeast(1)
        if (normalizedQuery.isBlank()) {
            return OnlinePage(items = emptyList(), offset = start, limit = limitValue)
        }
        val result = requestSearch3Result(
            source = source,
            query = normalizedQuery,
            songOffset = start,
            songCount = limitValue,
            albumCount = 0,
            artistCount = 0,
        )
        return OnlinePage(
            items = result?.get("song").asObjectList().mapNotNull { it.toOnlineTrack(source) },
            offset = start,
            limit = limitValue,
        )
    }

    override suspend fun searchAlbums(
        sourceId: String,
        query: String,
        offset: Int,
        limit: Int,
    ): OnlinePage<OnlineAlbumItem> {
        val source = requireOnlineNavidromeSource(sourceId)
        val normalizedQuery = query.trim()
        val start = offset.coerceAtLeast(0)
        val limitValue = limit.coerceAtLeast(1)
        if (normalizedQuery.isBlank()) {
            return OnlinePage(items = emptyList(), offset = start, limit = limitValue)
        }
        val result = requestSearch3Result(
            source = source,
            query = normalizedQuery,
            songCount = 0,
            albumOffset = start,
            albumCount = limitValue,
            artistCount = 0,
        )
        return OnlinePage(
            items = result?.get("album").asObjectList().mapNotNull { it.toOnlineAlbum(source) },
            offset = start,
            limit = limitValue,
        )
    }

    override suspend fun searchArtists(
        sourceId: String,
        query: String,
        offset: Int,
        limit: Int,
    ): OnlinePage<OnlineArtistItem> {
        val source = requireOnlineNavidromeSource(sourceId)
        val normalizedQuery = query.trim()
        val start = offset.coerceAtLeast(0)
        val limitValue = limit.coerceAtLeast(1)
        if (normalizedQuery.isBlank()) {
            return OnlinePage(items = emptyList(), offset = start, limit = limitValue)
        }
        val result = requestSearch3Result(
            source = source,
            query = normalizedQuery,
            songCount = 0,
            albumCount = 0,
            artistOffset = start,
            artistCount = limitValue,
        )
        return OnlinePage(
            items = result?.get("artist").asObjectList().mapNotNull { it.toOnlineArtist() },
            offset = start,
            limit = limitValue,
        )
    }

    override suspend fun artistAlbums(sourceId: String, artistId: String): List<OnlineAlbumItem> {
        val source = requireOnlineNavidromeSource(sourceId)
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getArtist",
            parameters = mapOf("id" to artistId),
            logger = logger,
            logContext = "artistId=$artistId",
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        return payload["artist"].asObjectOrNull()
            ?.get("album")
            .asObjectList()
            .mapNotNull { it.toOnlineAlbum(source) }
    }

    private suspend fun requestSearch3Result(
        source: NavidromeResolvedSource,
        query: String,
        songOffset: Int = 0,
        songCount: Int = 0,
        albumOffset: Int = 0,
        albumCount: Int = 0,
        artistOffset: Int = 0,
        artistCount: Int = 0,
    ): JsonObject? {
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "search3",
            parameters = mapOf(
                "query" to query.trim(),
                "songCount" to songCount.coerceAtLeast(0).toString(),
                "songOffset" to songOffset.coerceAtLeast(0).toString(),
                "albumCount" to albumCount.coerceAtLeast(0).toString(),
                "albumOffset" to albumOffset.coerceAtLeast(0).toString(),
                "artistCount" to artistCount.coerceAtLeast(0).toString(),
                "artistOffset" to artistOffset.coerceAtLeast(0).toString(),
            ),
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        return payload["searchResult3"].asObjectOrNull()
    }

    override suspend fun albumTracks(sourceId: String, albumId: String): List<Track> {
        val source = requireOnlineNavidromeSource(sourceId)
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getAlbum",
            parameters = mapOf("id" to albumId),
            logger = logger,
            logContext = "albumId=$albumId",
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        val album = payload["album"].asObjectOrNull()
        val albumTitle = album?.string("name") ?: album?.string("title") ?: album?.string("album")
        val albumArtist = album?.string("artist")
        val albumArtistId = album?.string("artistId")
        val albumCoverArt = album?.string("coverArt")
        return album?.get("song").asObjectList()
            .mapNotNull { song ->
                song.toOnlineTrack(
                    source = source,
                    fallbackAlbumId = albumId,
                    fallbackAlbumTitle = albumTitle,
                    fallbackArtistId = albumArtistId,
                    fallbackArtistName = albumArtist,
                    fallbackCoverArtId = albumCoverArt,
                )
            }
            .orEmpty()
    }

    override suspend fun favoriteTracks(
        sourceId: String,
        offset: Int,
        limit: Int,
        query: String,
    ): OnlinePage<Track> {
        val source = requireOnlineNavidromeSource(sourceId)
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getStarred2",
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        val allTracks = payload["starred2"].asObjectOrNull()
            ?.get("song")
            .asObjectList()
            .mapNotNull { it.toOnlineTrack(source, remoteFavoriteHint = true) }
        val normalizedQuery = query.trim().lowercase()
        val matchingTracks = if (normalizedQuery.isBlank()) {
            allTracks
        } else {
            allTracks.filter { track ->
                track.title.lowercase().contains(normalizedQuery) ||
                    track.artistName.orEmpty().lowercase().contains(normalizedQuery) ||
                    track.albumTitle.orEmpty().lowercase().contains(normalizedQuery)
            }
        }
        val start = offset.coerceAtLeast(0)
        val limitValue = limit.coerceAtLeast(1)
        return OnlinePage(
            items = matchingTracks.drop(start).take(limitValue),
            totalCount = matchingTracks.size,
            offset = start,
            limit = limitValue,
        )
    }

    override suspend fun setFavorite(sourceId: String, track: Track, favorite: Boolean) {
        val source = requireOnlineNavidromeSource(sourceId)
        val parsed = parseSubsonicCompatibleSongLocator(track.mediaLocator)
            ?.takeIf { it.sourceId == sourceId }
            ?: throw UiTextException(uiText(Res.string.online_track_remote_id_missing))
        requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = if (favorite) "star" else "unstar",
            parameters = mapOf("id" to parsed.itemId),
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun playlists(sourceId: String): List<PlaylistSummary> {
        val source = requireOnlineNavidromeSource(sourceId)
        return fetchRemotePlaylists(source)
    }

    override suspend fun playlistDetail(sourceId: String, playlistId: String): PlaylistDetail? {
        val source = requireOnlineNavidromeSource(sourceId)
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getPlaylist",
            parameters = mapOf("id" to playlistId),
            logger = logger,
            logContext = "playlistId=$playlistId",
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        val playlist = payload["playlist"].asObjectOrNull() ?: return null
        val name = playlist.string("name")?.trim().orEmpty().ifBlank { "未命名歌单" }
        val tracks = playlist["entry"].asObjectList()
            .mapNotNull { it.toOnlineTrack(source) }
            .map { PlaylistTrackEntry(track = it) }
        return PlaylistDetail(
            id = playlistId,
            name = name,
            kind = PlaylistKind.USER,
            updatedAt = now(),
            tracks = tracks,
        )
    }

    override suspend fun createPlaylist(sourceId: String, name: String): PlaylistSummary {
        val source = requireOnlineNavidromeSource(sourceId)
        val displayName = name.trim()
        requireUi(displayName.isNotBlank()) { uiText(Res.string.playlist_name_required) }
        requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "createPlaylist",
            parameters = mapOf("name" to displayName),
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        return fetchRemotePlaylists(source)
            .firstOrNull { it.name == displayName }
            ?: PlaylistSummary(id = displayName, name = displayName, kind = PlaylistKind.USER)
    }

    override suspend fun renamePlaylist(sourceId: String, playlistId: String, name: String) {
        val source = requireOnlineNavidromeSource(sourceId)
        val displayName = name.trim()
        requireUi(displayName.isNotBlank()) { uiText(Res.string.playlist_name_required) }
        requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "updatePlaylist",
            parameters = mapOf("playlistId" to playlistId, "name" to displayName),
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun deletePlaylist(sourceId: String, playlistId: String) {
        val source = requireOnlineNavidromeSource(sourceId)
        requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "deletePlaylist",
            parameters = mapOf("id" to playlistId),
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun addTrackToPlaylist(sourceId: String, playlistId: String, track: Track) {
        val source = requireOnlineNavidromeSource(sourceId)
        val songId = track.onlineSongIdOrNull(sourceId)
            ?: throw UiTextException(uiText(Res.string.online_track_remote_id_missing))
        checkUi(songId !in playlistSongIds(sourceId, playlistId)) { uiText(Res.string.playlist_track_already_present) }
        addSongIdToPlaylist(source, playlistId, songId)
    }

    override suspend fun importPlaylistText(
        sourceId: String,
        playlistId: String,
        text: String,
    ): PlaylistImportReport {
        val source = requireOnlineNavidromeSource(sourceId)
        val playlist = playlistDetail(sourceId, playlistId) ?: throw UiTextException(uiText(Res.string.playlist_not_found))
        val currentMemberSongIds = playlist.tracks
            .mapNotNull { it.track.onlineSongIdOrNull(sourceId) }
            .toMutableSet()
        val malformedLines = mutableListOf<PlaylistImportLineIssue>()
        val parsedLines = mutableListOf<PlaylistTextImportLine>()

        text.lineSequence().forEachIndexed { index, rawLine ->
            if (rawLine.isBlank()) return@forEachIndexed
            val lineNumber = index + 1
            val parsedLine = parsePlaylistTextImportLine(lineNumber, rawLine)
            if (parsedLine == null) {
                malformedLines += PlaylistImportLineIssue(
                    lineNumber = lineNumber,
                    rawText = rawLine,
                )
            } else {
                parsedLines += parsedLine
            }
        }

        val seenInputSongIds = linkedSetOf<String>()
        var addedCount = 0
        var alreadyExistsCount = 0
        var duplicateInputCount = 0
        val notMatchedLines = mutableListOf<PlaylistImportLineIssue>()
        val ambiguousLines = mutableListOf<PlaylistImportAmbiguousLineIssue>()
        val failedLines = mutableListOf<PlaylistImportFailedLineIssue>()
        val pendingAdditions = mutableListOf<OnlinePlaylistImportPendingAddition>()

        parsedLines.forEach { parsedLine ->
            val lineNumber = parsedLine.lineNumber
            val rawLine = parsedLine.rawText
            val matches = searchTracksForPlaylistImport(
                source = source,
                query = "${parsedLine.title} ${parsedLine.artist}",
            )
                .mapNotNull { track ->
                    val songId = track.onlineSongIdOrNull(sourceId) ?: return@mapNotNull null
                    val titleMatches = normalizePlaylistTextImportPart(track.title) == parsedLine.key.title
                    val artistMatches = normalizePlaylistTextImportPart(track.artistName.orEmpty()) == parsedLine.key.artist
                    if (titleMatches && artistMatches) {
                        OnlinePlaylistImportMatch(songId = songId, track = track)
                    } else {
                        null
                    }
                }
                .distinctBy { it.songId }

            when {
                matches.isEmpty() -> {
                    notMatchedLines += PlaylistImportLineIssue(
                        lineNumber = lineNumber,
                        rawText = rawLine,
                    )
                }

                matches.size > 1 -> {
                    ambiguousLines += PlaylistImportAmbiguousLineIssue(
                        lineNumber = lineNumber,
                        rawText = rawLine,
                        matchCount = matches.size,
                    )
                }

                else -> {
                    val match = matches.single()
                    if (!seenInputSongIds.add(match.songId)) {
                        duplicateInputCount += 1
                        return@forEach
                    }
                    if (match.songId in currentMemberSongIds) {
                        alreadyExistsCount += 1
                        return@forEach
                    }
                    pendingAdditions += OnlinePlaylistImportPendingAddition(
                        lineNumber = lineNumber,
                        rawText = rawLine,
                        songId = match.songId,
                    )
                }
            }
        }

        pendingAdditions.chunked(ONLINE_PLAYLIST_IMPORT_UPDATE_BATCH_SIZE).forEach { batch ->
            val batchSongIds = batch.map { it.songId }
            runCatching { addSongIdsToPlaylist(source, playlistId, batchSongIds) }
                .onSuccess {
                    currentMemberSongIds += batchSongIds
                    addedCount += batch.size
                }
                .onFailure {
                    val refreshedMemberSongIds = runCatching { playlistSongIds(sourceId, playlistId) }
                        .getOrElse { currentMemberSongIds.toSet() }
                    batch.forEach { pending ->
                        if (pending.songId in currentMemberSongIds || pending.songId in refreshedMemberSongIds) {
                            currentMemberSongIds += pending.songId
                            addedCount += 1
                            return@forEach
                        }
                        runCatching { addSongIdToPlaylist(source, playlistId, pending.songId) }
                            .onSuccess {
                                currentMemberSongIds += pending.songId
                                addedCount += 1
                            }
                            .onFailure { throwable ->
                                failedLines += playlistImportFailure(
                                    lineNumber = pending.lineNumber,
                                    rawText = pending.rawText,
                                    throwable = throwable,
                                )
                            }
                    }
                }
        }

        return PlaylistImportReport(
            addedCount = addedCount,
            alreadyExistsCount = alreadyExistsCount,
            duplicateInputCount = duplicateInputCount,
            malformedLines = malformedLines,
            notMatchedLines = notMatchedLines,
            ambiguousLines = ambiguousLines,
            failedLines = failedLines,
        )
    }

    private suspend fun playlistSongIds(sourceId: String, playlistId: String): Set<String> {
        return playlistDetail(sourceId, playlistId)
            ?.tracks
            .orEmpty()
            .mapNotNull { it.track.onlineSongIdOrNull(sourceId) }
            .toSet()
    }

    private suspend fun addSongIdToPlaylist(
        source: NavidromeResolvedSource,
        playlistId: String,
        songId: String,
    ) {
        requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "updatePlaylist",
            parameters = mapOf("playlistId" to playlistId, "songIdToAdd" to songId),
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    private suspend fun addSongIdsToPlaylist(
        source: NavidromeResolvedSource,
        playlistId: String,
        songIds: List<String>,
    ) {
        if (songIds.isEmpty()) return
        val parameters = buildList {
            add("playlistId" to playlistId)
            songIds.forEach { songId ->
                add("songIdToAdd" to songId)
            }
        }
        requestNavidromeJsonWithRepeatedParameters(
            httpClient = httpClient,
            source = source,
            endpoint = "updatePlaylist",
            parameters = parameters,
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    private suspend fun searchTracksForPlaylistImport(
        source: NavidromeResolvedSource,
        query: String,
    ): List<Track> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "search3",
            parameters = mapOf(
                "query" to normalizedQuery,
                "songCount" to ONLINE_PLAYLIST_IMPORT_SEARCH_LIMIT.toString(),
                "songOffset" to "0",
                "albumCount" to "0",
                "artistCount" to "0",
            ),
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        return payload["searchResult3"].asObjectOrNull()
            ?.get("song")
            .asObjectList()
            .mapNotNull { it.toOnlineTrack(source) }
    }

    override suspend fun removeTrackFromPlaylist(sourceId: String, playlistId: String, index: Int) {
        val source = requireOnlineNavidromeSource(sourceId)
        requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "updatePlaylist",
            parameters = mapOf("playlistId" to playlistId, "songIndexToRemove" to index.coerceAtLeast(0).toString()),
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    private suspend fun fetchRemotePlaylists(source: NavidromeResolvedSource): List<PlaylistSummary> {
        val sourceId = source.sourceId ?: return emptyList()
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getPlaylists",
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
        return payload["playlists"].asObjectOrNull()
            ?.get("playlist")
            .asObjectList()
            .mapNotNull { playlist ->
                val id = playlist.string("id") ?: return@mapNotNull null
                val name = playlist.string("name")?.trim().orEmpty().ifBlank { "未命名歌单" }
                val coverArtId = playlist.string("coverArt")?.trim()?.takeIf { it.isNotBlank() }
                PlaylistSummary(
                    id = id,
                    name = name,
                    kind = PlaylistKind.USER,
                    trackCount = playlist.int("songCount") ?: 0,
                    artworkLocator = coverArtId?.let {
                        buildSubsonicCompatibleCoverLocator(source.sourceType, sourceId, it)
                    },
                    updatedAt = now(),
                )
            }
    }

    private suspend fun requireOnlineNavidromeSource(sourceId: String): NavidromeResolvedSource {
        val entity = database.importSourceDao().getById(sourceId)
            ?: throw UiTextException(uiText(Res.string.source_not_found))
        requireUi(entity.enabled) { uiText(Res.string.source_disabled) }
        requireUi(entity.type == ImportSourceType.NAVIDROME.name) { uiText(Res.string.source_online_navidrome_only) }
        requireUi(entity.indexMode.toImportSourceIndexMode() == ImportSourceIndexMode.ONLINE) {
            uiText(Res.string.source_not_online)
        }
        return entity.toSubsonicCompatibleResolvedSource()
            ?: throw UiTextException(uiText(Res.string.source_credentials_missing, "Navidrome"))
    }

    private suspend fun ImportSourceEntity.toSubsonicCompatibleResolvedSource(): NavidromeResolvedSource? {
        val sourceType = runCatching { ImportSourceType.valueOf(type) }.getOrNull()
            ?.takeIf(::isSubsonicCompatibleSourceType)
            ?: return null
        val authMode = authMode.toSubsonicAuthMode()
        val username = username?.trim().orEmpty()
        val credential = credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        if (authMode == SubsonicAuthMode.PASSWORD && (username.isBlank() || credential.isBlank())) return null
        if (authMode == SubsonicAuthMode.API_KEY && credential.isBlank()) return null
        val serverLabel = if (sourceType == ImportSourceType.NAVIDROME) "Navidrome" else "Subsonic"
        return NavidromeResolvedSource(
            baseUrl = normalizeSubsonicBaseUrl(rootReference).takeIf { it.isNotBlank() } ?: rootReference,
            wanBaseUrl = wanRootReference?.let { normalizeSubsonicBaseUrl(it) },
            sourceId = id,
            addressSelector = addressSelector,
            username = username,
            password = credential,
            authMode = authMode,
            sourceType = sourceType,
        )
    }

    private fun JsonObject.toOnlineAlbum(source: NavidromeResolvedSource): OnlineAlbumItem? {
        val sourceId = source.sourceId ?: return null
        val id = string("id") ?: return null
        val title = string("name") ?: string("title") ?: string("album") ?: "未知专辑"
        val coverArtId = string("coverArt")
        return OnlineAlbumItem(
            album = Album(
                id = id,
                title = title,
                artistName = string("artist"),
                trackCount = int("songCount") ?: int("trackCount") ?: 0,
            ),
            artworkLocator = coverArtId?.let {
                buildSubsonicCompatibleCoverLocator(source.sourceType, sourceId, it)
            },
        )
    }

    private fun JsonObject.toOnlineArtist(): OnlineArtistItem? {
        val id = string("id") ?: return null
        val name = string("name")?.trim().orEmpty().ifBlank { "未知艺人" }
        val trackCount = int("songCount")
        val albumCount = int("albumCount")
        return OnlineArtistItem(
            artist = Artist(
                id = id,
                name = name,
                trackCount = trackCount ?: 0,
            ),
            trackCount = trackCount,
            albumCount = albumCount,
        )
    }

    private fun JsonObject.toOnlineTrack(
        source: NavidromeResolvedSource,
        fallbackAlbumId: String? = null,
        fallbackAlbumTitle: String? = null,
        fallbackArtistId: String? = null,
        fallbackArtistName: String? = null,
        fallbackCoverArtId: String? = null,
        remoteFavoriteHint: Boolean? = this.remoteFavoriteHint(),
    ): Track? {
        val sourceId = source.sourceId ?: return null
        val songId = string("id") ?: return null
        val title = string("title")?.trim().orEmpty().ifBlank { "未知曲目" }
        val coverArtId = string("coverArt") ?: fallbackCoverArtId
        return Track(
            id = subsonicCompatibleTrackIdFor(sourceId, songId, source.sourceType),
            sourceId = sourceId,
            title = title,
            artistName = string("artist") ?: fallbackArtistName,
            albumTitle = string("album") ?: string("albumName") ?: fallbackAlbumTitle,
            durationMs = (long("duration") ?: 0L) * 1_000L,
            trackNumber = int("track"),
            discNumber = int("discNumber"),
            mediaLocator = buildSubsonicCompatibleSongLocator(source.sourceType, sourceId, songId),
            relativePath = string("path") ?: songId,
            artworkLocator = coverArtId?.let { buildSubsonicCompatibleCoverLocator(source.sourceType, sourceId, it) },
            sizeBytes = long("size") ?: 0L,
            modifiedAt = 0L,
            bitDepth = int("bitDepth"),
            samplingRate = int("samplingRate"),
            bitRate = int("bitRate"),
            channelCount = int("channelCount"),
            albumId = string("albumId") ?: fallbackAlbumId,
            artistId = string("artistId") ?: fallbackArtistId,
            remoteFavoriteHint = remoteFavoriteHint,
        )
    }

    private fun top.iwesley.lyn.music.domain.NavidromeSongCandidate.toOnlineTrack(
        source: NavidromeResolvedSource,
    ): Track {
        val sourceId = source.sourceId.orEmpty()
        return Track(
            id = subsonicCompatibleTrackIdFor(sourceId, songId, source.sourceType),
            sourceId = sourceId,
            title = title,
            artistName = artistName,
            albumTitle = albumTitle,
            durationMs = durationMs,
            trackNumber = trackNumber,
            discNumber = discNumber,
            mediaLocator = buildSubsonicCompatibleSongLocator(source.sourceType, sourceId, songId),
            relativePath = path ?: songId,
            artworkLocator = coverArtId?.let { buildSubsonicCompatibleCoverLocator(source.sourceType, sourceId, it) },
            sizeBytes = sizeBytes,
            modifiedAt = 0L,
            bitDepth = bitDepth,
            samplingRate = samplingRate,
            bitRate = bitRate,
            channelCount = channelCount,
            albumId = albumId,
            artistId = artistId,
            remoteFavoriteHint = remoteFavoriteHint,
        )
    }
}

private data class OnlineArtistsCacheEntry(
    val fingerprint: OnlineArtistsSourceFingerprint,
    val artists: List<OnlineArtistItem>,
)

private data class OnlineArtistsSourceFingerprint(
    val baseUrl: String,
    val wanBaseUrl: String?,
    val username: String,
    val authMode: SubsonicAuthMode,
    val sourceType: ImportSourceType,
)

private fun NavidromeResolvedSource.onlineArtistsCacheFingerprint(): OnlineArtistsSourceFingerprint {
    return OnlineArtistsSourceFingerprint(
        baseUrl = baseUrl,
        wanBaseUrl = wanBaseUrl,
        username = username,
        authMode = authMode,
        sourceType = sourceType,
    )
}

private data class OnlinePlaylistImportMatch(
    val songId: String,
    val track: Track,
)

private data class OnlinePlaylistImportPendingAddition(
    val lineNumber: Int,
    val rawText: String,
    val songId: String,
)

private fun Track.onlineSongIdOrNull(sourceId: String): String? {
    return parseSubsonicCompatibleSongLocator(mediaLocator)
        ?.takeIf { it.sourceId == sourceId }
        ?.itemId
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asObjectList(): List<JsonObject> {
    return when (this) {
        is kotlinx.serialization.json.JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(this)
        else -> emptyList()
    }
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull
}

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

private fun JsonObject.remoteFavoriteHint(): Boolean? {
    val value = string("starred")?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return !(value.equals("false", ignoreCase = true) || value == "0")
}

private const val ONLINE_PLAYLIST_IMPORT_SEARCH_LIMIT = 10
private const val ONLINE_PLAYLIST_IMPORT_UPDATE_BATCH_SIZE = 50
