package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.requireUi
import top.iwesley.lyn.music.core.model.uiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.iwesley.lyn.music.core.model.DiagnosticLogger
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
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.parseEmbySongLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaylistEntity
import top.iwesley.lyn.music.data.db.PlaylistRemoteBindingEntity
import top.iwesley.lyn.music.data.db.PlaylistTrackEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.domain.addEmbyPlaylistItem
import top.iwesley.lyn.music.domain.createEmbyPlaylist
import top.iwesley.lyn.music.domain.deleteEmbyPlaylist
import top.iwesley.lyn.music.domain.fetchEmbyPlaylistEntries
import top.iwesley.lyn.music.domain.fetchEmbyPlaylists
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.isSubsonicCompatibleSourceType
import top.iwesley.lyn.music.domain.normalizeSubsonicBaseUrl
import top.iwesley.lyn.music.domain.removeEmbyPlaylistEntries
import top.iwesley.lyn.music.domain.requestNavidromeJson
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.resolveEmbySource
import top.iwesley.lyn.music.domain.toSubsonicAuthMode
import top.iwesley.lyn.music.domain.updateEmbyPlaylistName

class RoomPlaylistRepository(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val httpClient: LyricsHttpClient,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    private val addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
) : PlaylistRepository {
    override val playlists: Flow<List<PlaylistSummary>> = combine(
        database.playlistDao().observeAll(),
        database.playlistTrackDao().observeAll(),
        database.trackDao().observeAll(),
        database.importSourceDao().observeAll(),
        combine(
            database.lyricsCacheDao().observeArtworkLocators(),
            database.playlistRemoteBindingDao().observeAll(),
        ) { artworkRows, remoteBindings -> artworkRows to remoteBindings },
    ) { playlists, playlistTracks, trackEntities, sources, artworkAndBindings ->
        val (artworkRows, remoteBindings) = artworkAndBindings
        val enabledSourceIds = sources.asSequence()
            .filter { it.isLocalIndexedEnabled() }
            .map { it.id }
            .toSet()
        val artworkOverrides = effectiveArtworkOverridesByTrackId(artworkRows)
        val trackById = trackEntities.associate { entity ->
            entity.id to entity.toDomain(artworkOverrides[entity.id])
        }
        playlists.map { playlist ->
            val visiblePlaylistTracks = playlistTracks.asSequence()
                .filter {
                    it.playlistId == playlist.id &&
                        it.sourceId in enabledSourceIds &&
                        trackById.containsKey(it.trackId)
                }
                .toList()
            val memberTrackIds = visiblePlaylistTracks.asSequence()
                .map { it.trackId }
                .toCollection(linkedSetOf())
            val artwork = visiblePlaylistTracks.latestPlaylistArtwork(trackById)
            if (!playlist.isVisibleInLocalPlaylistBrowser(visiblePlaylistTracks, remoteBindings, enabledSourceIds)) {
                return@map null
            }
            playlist.toSummary(
                memberTrackIds = memberTrackIds,
                artworkLocator = artwork?.locator,
                artworkCacheKey = artwork?.cacheKey,
            )
        }.filterNotNull()
    }

    override fun observePlaylistDetail(playlistId: String): Flow<PlaylistDetail?> {
        return combine(
            database.playlistDao().observeAll(),
            database.playlistTrackDao().observeAll(),
            database.trackDao().observeAll(),
            database.importSourceDao().observeAll(),
            combine(
                database.lyricsCacheDao().observeArtworkLocators(),
                database.playlistRemoteBindingDao().observeAll(),
            ) { artworkRows, remoteBindings -> artworkRows to remoteBindings },
        ) { playlists, playlistTracks, trackEntities, sources, artworkAndBindings ->
            val (artworkRows, remoteBindings) = artworkAndBindings
            val playlist = playlists.firstOrNull { it.id == playlistId } ?: return@combine null
            val artworkOverrides = effectiveArtworkOverridesByTrackId(artworkRows)
            val enabledSourceIds = sources.asSequence()
                .filter { it.isLocalIndexedEnabled() }
                .map { it.id }
                .toSet()
            val visiblePlaylistTracks = playlistTracks.filter { it.sourceId in enabledSourceIds }
            if (!playlist.isVisibleInLocalPlaylistBrowser(visiblePlaylistTracks, remoteBindings, enabledSourceIds)) {
                return@combine null
            }
            val trackById = trackEntities.associate { entity ->
                entity.id to entity.toDomain(artworkOverrides[entity.id])
            }
            val sourceLabelById = sources.associate { it.id to it.label }
            playlist.toDetail(
                tracks = visiblePlaylistTracks,
                trackById = trackById,
                sourceLabelById = sourceLabelById,
            )
        }
    }

    override suspend fun createPlaylist(name: String): Result<PlaylistSummary> {
        return runCatching {
            val displayName = name.trim()
            requireUi(displayName.isNotBlank()) { uiText(Res.string.playlist_name_required) }
            val normalizedName = normalizePlaylistName(displayName)
            requireUi(database.playlistDao().getByNormalizedName(normalizedName) == null) { uiText(Res.string.playlist_already_exists) }
            val entity = PlaylistEntity(
                id = newId("playlist"),
                name = displayName,
                normalizedName = normalizedName,
                createdLocally = true,
                createdAt = now(),
                updatedAt = now(),
            )
            database.playlistDao().upsert(entity)
            entity.toSummary()
        }
    }

    override suspend fun renamePlaylist(playlistId: String, name: String): Result<PlaylistSummary> {
        return runCatching {
            val playlist = database.playlistDao().getById(playlistId) ?: throw UiTextException(uiText(Res.string.playlist_not_found))
            val displayName = name.trim()
            requireUi(displayName.isNotBlank()) { uiText(Res.string.playlist_name_required) }
            val normalizedName = normalizePlaylistName(displayName)
            val duplicate = database.playlistDao().getByNormalizedName(normalizedName)
            requireUi(duplicate == null || duplicate.id == playlistId) { uiText(Res.string.playlist_already_exists) }

            val bindings = database.playlistRemoteBindingDao().getByPlaylistId(playlistId)
            val writableBindings = localPlaylistMutationRemoteBindings(playlist, bindings)
            writableBindings.forEach { binding ->
                if (database.importSourceDao().getById(binding.sourceId)?.isEmbySource() == true) {
                    val resolvedSource = resolveEmbySource(database, secureCredentialStore, binding.sourceId, addressSelector)
                        ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_rename, "Emby"))
                    updateEmbyPlaylistName(
                        httpClient = httpClient,
                        source = resolvedSource,
                        playlistId = binding.remotePlaylistId,
                        name = displayName,
                        logger = logger,
                    )
                } else {
                    val resolvedSource = resolveSubsonicCompatibleSource(
                        sourceId = binding.sourceId,
                        requireLocalIndex = false,
                    )
                        ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_rename, "Subsonic-compatible"))
                    requestNavidromeJson(
                        httpClient = httpClient,
                        source = resolvedSource,
                        endpoint = "updatePlaylist",
                        parameters = mapOf(
                            "playlistId" to binding.remotePlaylistId,
                            "name" to displayName,
                        ),
                        logger = logger,
                        logContext = "playlist=\"${playlist.name}\" rename remotePlaylist=${binding.remotePlaylistId}",
                    )
                }
            }

            val updatedAt = now()
            val updatedPlaylist = playlist.copy(
                name = displayName,
                normalizedName = normalizedName,
                updatedAt = updatedAt,
            )
            database.playlistDao().upsert(updatedPlaylist)
            writableBindings.forEach { binding ->
                database.playlistRemoteBindingDao().upsert(
                    binding.copy(
                        remoteName = displayName,
                        lastSyncedAt = updatedAt,
                    ),
                )
            }
            updatedPlaylist.toSummary(
                memberTrackIds = database.playlistTrackDao()
                    .getByPlaylistId(playlistId)
                    .mapTo(linkedSetOf()) { it.trackId },
            )
        }
    }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        return runCatching {
            val playlist = database.playlistDao().getById(playlistId) ?: throw UiTextException(uiText(Res.string.playlist_not_found))
            val bindings = localPlaylistMutationRemoteBindings(
                playlist = playlist,
                bindings = database.playlistRemoteBindingDao().getByPlaylistId(playlistId),
            )
            bindings.forEach { binding ->
                if (database.importSourceDao().getById(binding.sourceId)?.isEmbySource() == true) {
                    val resolvedSource = resolveEmbySource(database, secureCredentialStore, binding.sourceId, addressSelector)
                        ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_delete, "Emby"))
                    deleteEmbyPlaylist(
                        httpClient = httpClient,
                        source = resolvedSource,
                        playlistId = binding.remotePlaylistId,
                        logger = logger,
                    )
                } else {
                    val resolvedSource = resolveSubsonicCompatibleSource(
                        sourceId = binding.sourceId,
                        requireLocalIndex = false,
                    )
                        ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_delete, "Subsonic-compatible"))
                    requestNavidromeJson(
                        httpClient = httpClient,
                        source = resolvedSource,
                        endpoint = "deletePlaylist",
                        parameters = mapOf("id" to binding.remotePlaylistId),
                        logger = logger,
                        logContext = "playlist=\"${playlist.name}\" delete remotePlaylist=${binding.remotePlaylistId}",
                    )
                }
            }
            deleteLocalPlaylist(playlistId)
        }
    }

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> {
        return runCatching {
            val playlist = database.playlistDao().getById(playlistId) ?: throw UiTextException(uiText(Res.string.playlist_not_found))
            if (database.playlistTrackDao().getByPlaylistIdAndTrackId(playlistId, track.id) != null) {
                throw UiTextException(uiText(Res.string.playlist_track_already_present))
            }
            val subsonicSong = parseSubsonicCompatibleSongLocator(track.mediaLocator)
                ?.takeIf { it.sourceId == track.sourceId }
            if (subsonicSong != null) {
                addSubsonicCompatibleTrackToPlaylist(playlist, track, subsonicSong.itemId)
            } else {
                val embySong = parseEmbySongLocator(track.mediaLocator)
                    ?.takeIf { it.first == track.sourceId }
                if (embySong != null) {
                    addEmbyTrackToPlaylist(playlist, track, embySong.second)
                } else {
                    addLocalTrackToPlaylist(playlist, track)
                }
            }
        }
    }

    override suspend fun importPlaylistText(
        playlistId: String,
        text: String,
    ): Result<PlaylistImportReport> {
        return runCatching {
            database.playlistDao().getById(playlistId) ?: throw UiTextException(uiText(Res.string.playlist_not_found))
            val enabledSourceIds = database.importSourceDao().getAll()
                .filter { it.isLocalIndexedEnabled() }
                .mapTo(linkedSetOf()) { it.id }
            val malformedLines = mutableListOf<PlaylistImportLineIssue>()
            val parsedLines = mutableListOf<PlaylistTextImportLine>()
            val requestedKeys = linkedSetOf<PlaylistTextImportKey>()

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
                    requestedKeys += parsedLine.key
                }
            }

            val tracksByImportKey = if (enabledSourceIds.isEmpty() || requestedKeys.isEmpty()) {
                emptyMap()
            } else {
                getPlaylistTextImportCandidateTracks(
                    enabledSourceIds = enabledSourceIds.toList(),
                    requestedKeys = requestedKeys.toList(),
                )
                    .map { it.toDomain() }
                    .groupBy { track ->
                        PlaylistTextImportKey(
                            title = normalizePlaylistTextImportPart(track.title),
                            artist = normalizePlaylistTextImportPart(track.artistName.orEmpty()),
                        )
                    }
            }
            val currentMemberTrackIds = database.playlistTrackDao()
                .getByPlaylistId(playlistId)
                .mapTo(linkedSetOf()) { it.trackId }
            val seenInputTrackIds = linkedSetOf<String>()
            var addedCount = 0
            var alreadyExistsCount = 0
            var duplicateInputCount = 0
            val notMatchedLines = mutableListOf<PlaylistImportLineIssue>()
            val ambiguousLines = mutableListOf<PlaylistImportAmbiguousLineIssue>()
            val failedLines = mutableListOf<PlaylistImportFailedLineIssue>()

            parsedLines.forEach { parsedLine ->
                val lineNumber = parsedLine.lineNumber
                val rawLine = parsedLine.rawText
                val matches = tracksByImportKey[parsedLine.key].orEmpty()
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
                        val track = matches.single()
                        if (!seenInputTrackIds.add(track.id)) {
                            duplicateInputCount += 1
                            return@forEach
                        }
                        if (track.id in currentMemberTrackIds) {
                            alreadyExistsCount += 1
                            return@forEach
                        }
                        addTrackToPlaylist(playlistId, track)
                            .onSuccess {
                                currentMemberTrackIds += track.id
                                addedCount += 1
                            }
                            .onFailure { throwable ->
                                failedLines += playlistImportFailure(
                                    lineNumber = lineNumber,
                                    rawText = rawLine,
                                    throwable = throwable,
                                )
                            }
                    }
                }
            }

            PlaylistImportReport(
                addedCount = addedCount,
                alreadyExistsCount = alreadyExistsCount,
                duplicateInputCount = duplicateInputCount,
                malformedLines = malformedLines,
                notMatchedLines = notMatchedLines,
                ambiguousLines = ambiguousLines,
                failedLines = failedLines,
            )
        }
    }

    private suspend fun getPlaylistTextImportCandidateTracks(
        enabledSourceIds: List<String>,
        requestedKeys: List<PlaylistTextImportKey>,
    ): List<TrackEntity> {
        if (enabledSourceIds.isEmpty() || requestedKeys.isEmpty()) return emptyList()
        val candidatesById = linkedMapOf<String, TrackEntity>()
        enabledSourceIds.chunked(PlaylistTextImportSourceChunkSize).forEach { sourceChunk ->
            val keyChunkSize = maxOf(
                1,
                (PlaylistTextImportSqlBindLimit - sourceChunk.size) / PlaylistTextImportBindingsPerKey,
            )
            requestedKeys.chunked(keyChunkSize).forEach { keyChunk ->
                database.trackDao()
                    .getByNormalizedTitleAndArtistCandidates(
                        sourceIds = sourceChunk,
                        titles = keyChunk.map { it.title }.distinct(),
                        artists = keyChunk.map { it.artist }.distinct(),
                    )
                    .forEach { track ->
                        candidatesById.getOrPut(track.id) { track }
                    }
            }
        }
        return candidatesById.values.toList()
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> {
        return runCatching {
            val playlist = database.playlistDao().getById(playlistId) ?: throw UiTextException(uiText(Res.string.playlist_not_found))
            val row = database.playlistTrackDao().getByPlaylistIdAndTrackId(playlistId, trackId) ?: return@runCatching
            val binding = database.playlistRemoteBindingDao().getByPlaylistIdAndSourceId(playlistId, row.sourceId)
            if (binding != null && row.remoteOrdinal != null && isLocalIndexedSource(row.sourceId)) {
                if (database.importSourceDao().getById(row.sourceId)?.isEmbySource() == true) {
                    removeEmbyTrackFromPlaylist(
                        playlist = playlist,
                        binding = binding,
                        row = row,
                    )
                } else {
                    val resolvedSource = resolveSubsonicCompatibleSource(row.sourceId)
                        ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_update, "Subsonic-compatible"))
                    requestNavidromeJson(
                        httpClient = httpClient,
                        source = resolvedSource,
                        endpoint = "updatePlaylist",
                        parameters = mapOf(
                            "playlistId" to binding.remotePlaylistId,
                            "songIndexToRemove" to row.remoteOrdinal.toString(),
                        ),
                        logger = logger,
                        logContext = "playlist=\"${playlist.name}\" remove track=$trackId",
                    )
                    syncRemoteBinding(
                        playlist = playlist,
                        sourceId = row.sourceId,
                        remotePlaylistId = binding.remotePlaylistId,
                        remoteName = binding.remoteName,
                    )
                }
            } else {
                database.playlistTrackDao().deleteByPlaylistIdAndTrackId(playlistId, trackId)
                touchPlaylist(playlist)
                cleanupPlaylistIfNecessary(playlistId)
            }
        }
    }

    override suspend fun refreshNavidromePlaylists(): Result<Unit> {
        return runCatching {
            val remoteSources = database.importSourceDao().getAll()
                .filter { it.subsonicCompatibleSourceType() != null || it.isEmbySource() }
            cleanupRemovedRemoteSources(remoteSources.mapTo(linkedSetOf()) { it.id })
            val failures = mutableListOf<UiText>()
            remoteSources
                .filter { it.isLocalIndexedEnabled() }
                .forEach { source ->
                runCatching {
                    if (source.isEmbySource()) {
                        syncEmbySourcePlaylists(source)
                    } else {
                        syncSourcePlaylists(source)
                    }
                }
                    .onFailure { throwable ->
                        failures += uiText(Res.string.source_error_details, source.label, throwable.uiErrorDetail())
                    }
            }
            if (failures.isNotEmpty()) {
                throw UiTextException(UiText.Joined(failures, separator = "\n"))
            }
        }
    }

    private suspend fun addLocalTrackToPlaylist(
        playlist: PlaylistEntity,
        track: Track,
    ) {
        val nextOrdinal = database.playlistTrackDao().getByPlaylistId(playlist.id)
            .mapNotNull { it.localOrdinal }
            .maxOrNull()
            ?.plus(1)
            ?: 0
        database.playlistTrackDao().upsert(
            PlaylistTrackEntity(
                playlistId = playlist.id,
                trackId = track.id,
                sourceId = track.sourceId,
                addedAt = now(),
                localOrdinal = nextOrdinal,
                remoteOrdinal = null,
            ),
        )
        touchPlaylist(playlist)
    }

    private suspend fun addSubsonicCompatibleTrackToPlaylist(
        playlist: PlaylistEntity,
        track: Track,
        songId: String,
    ) {
        val resolvedSource = resolveSubsonicCompatibleSource(track.sourceId)
            ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_update, "Subsonic-compatible"))
        val binding = ensureRemoteBinding(
            playlist = playlist,
            sourceId = track.sourceId,
            resolvedSource = resolvedSource,
        )
        requestNavidromeJson(
            httpClient = httpClient,
            source = resolvedSource,
            endpoint = "updatePlaylist",
            parameters = mapOf(
                "playlistId" to binding.remotePlaylistId,
                "songIdToAdd" to songId,
            ),
            logger = logger,
            logContext = "playlist=\"${playlist.name}\" add track=${track.id}",
        )
        syncRemoteBinding(
            playlist = playlist,
            sourceId = track.sourceId,
            remotePlaylistId = binding.remotePlaylistId,
            remoteName = binding.remoteName,
        )
    }

    private suspend fun addEmbyTrackToPlaylist(
        playlist: PlaylistEntity,
        track: Track,
        itemId: String,
    ) {
        val resolvedSource = resolveEmbySource(database, secureCredentialStore, track.sourceId, addressSelector)
            ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_update, "Emby"))
        val binding = ensureEmbyRemoteBinding(
            playlist = playlist,
            sourceId = track.sourceId,
            resolvedSource = resolvedSource,
        )
        addEmbyPlaylistItem(
            httpClient = httpClient,
            source = resolvedSource,
            playlistId = binding.remotePlaylistId,
            itemId = itemId,
            logger = logger,
        )
        syncEmbyRemoteBinding(
            playlist = playlist,
            sourceId = track.sourceId,
            remotePlaylistId = binding.remotePlaylistId,
            remoteName = binding.remoteName,
        )
    }

    private suspend fun removeEmbyTrackFromPlaylist(
        playlist: PlaylistEntity,
        binding: PlaylistRemoteBindingEntity,
        row: PlaylistTrackEntity,
    ) {
        val resolvedSource = resolveEmbySource(database, secureCredentialStore, row.sourceId, addressSelector)
            ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_update, "Emby"))
        val entries = fetchEmbyPlaylistEntries(
            httpClient = httpClient,
            source = resolvedSource,
            playlistId = binding.remotePlaylistId,
            logger = logger,
        )
        val entry = entries.getOrNull(row.remoteOrdinal ?: -1)
            ?.takeIf { embyTrackIdFor(row.sourceId, it.itemId) == row.trackId }
            ?: entries.firstOrNull { embyTrackIdFor(row.sourceId, it.itemId) == row.trackId }
            ?: throw UiTextException(uiText(Res.string.playlist_remote_track_not_found))
        val entryId = entry.playlistItemId ?: throw UiTextException(uiText(Res.string.playlist_remote_entry_id_missing))
        removeEmbyPlaylistEntries(
            httpClient = httpClient,
            source = resolvedSource,
            playlistId = binding.remotePlaylistId,
            entryIds = listOf(entryId),
            logger = logger,
        )
        syncEmbyRemoteBinding(
            playlist = playlist,
            sourceId = row.sourceId,
            remotePlaylistId = binding.remotePlaylistId,
            remoteName = binding.remoteName,
        )
    }

    private suspend fun ensureRemoteBinding(
        playlist: PlaylistEntity,
        sourceId: String,
        resolvedSource: NavidromeResolvedSource,
    ): PlaylistRemoteBindingEntity {
        database.playlistRemoteBindingDao().getByPlaylistIdAndSourceId(playlist.id, sourceId)?.let { return it }

        val remotePlaylist = fetchSourcePlaylists(resolvedSource)
            .firstOrNull { normalizePlaylistName(it.name) == playlist.normalizedName }
            ?: run {
                requestNavidromeJson(
                    httpClient = httpClient,
                    source = resolvedSource,
                    endpoint = "createPlaylist",
                    parameters = mapOf("name" to playlist.name),
                    logger = logger,
                    logContext = "playlist=\"${playlist.name}\" create",
                )
                fetchSourcePlaylists(resolvedSource)
                    .firstOrNull { normalizePlaylistName(it.name) == playlist.normalizedName }
            }
            ?: throw UiTextException(uiText(Res.string.playlist_remote_creation_failed))

        val binding = PlaylistRemoteBindingEntity(
            playlistId = playlist.id,
            sourceId = sourceId,
            remotePlaylistId = remotePlaylist.id,
            remoteName = remotePlaylist.name,
            lastSyncedAt = null,
        )
        database.playlistRemoteBindingDao().upsert(binding)
        return binding
    }

    private suspend fun ensureEmbyRemoteBinding(
        playlist: PlaylistEntity,
        sourceId: String,
        resolvedSource: top.iwesley.lyn.music.domain.EmbyResolvedSource,
    ): PlaylistRemoteBindingEntity {
        database.playlistRemoteBindingDao().getByPlaylistIdAndSourceId(playlist.id, sourceId)?.let { return it }

        val remotePlaylist = fetchEmbyPlaylists(httpClient, resolvedSource, logger)
            .firstOrNull { normalizePlaylistName(it.name) == playlist.normalizedName }
            ?: createEmbyPlaylist(
                httpClient = httpClient,
                source = resolvedSource,
                name = playlist.name,
                logger = logger,
            )

        val binding = PlaylistRemoteBindingEntity(
            playlistId = playlist.id,
            sourceId = sourceId,
            remotePlaylistId = remotePlaylist.id,
            remoteName = remotePlaylist.name,
            lastSyncedAt = null,
        )
        database.playlistRemoteBindingDao().upsert(binding)
        return binding
    }

    private suspend fun syncSourcePlaylists(source: ImportSourceEntity) {
        val resolvedSource = source.toSubsonicCompatibleResolvedSource()
            ?: throw UiTextException(uiText(Res.string.playlist_source_credentials_missing, "Subsonic-compatible"))
        val remotePlaylists = fetchSourcePlaylists(resolvedSource)
        val remoteIds = remotePlaylists.mapTo(linkedSetOf()) { it.id }
        val existingBindingsByRemoteId = database.playlistRemoteBindingDao().getBySourceId(source.id)
            .associateBy { it.remotePlaylistId }
        val playlistsByNormalizedName = database.playlistDao().getAll()
            .associateBy { it.normalizedName }
            .toMutableMap()

        remotePlaylists.forEach { remotePlaylist ->
            val existingBinding = existingBindingsByRemoteId[remotePlaylist.id]
            val currentPlaylist = existingBinding?.let { database.playlistDao().getById(it.playlistId) }
                ?: playlistsByNormalizedName[normalizePlaylistName(remotePlaylist.name)]
            val playlist = currentPlaylist ?: PlaylistEntity(
                id = newId("playlist"),
                name = remotePlaylist.name,
                normalizedName = normalizePlaylistName(remotePlaylist.name),
                createdLocally = false,
                createdAt = now(),
                updatedAt = now(),
            )
            if (currentPlaylist == null) {
                database.playlistDao().upsert(playlist)
                playlistsByNormalizedName[playlist.normalizedName] = playlist
            }
            syncRemoteBinding(
                playlist = playlist,
                sourceId = source.id,
                remotePlaylistId = remotePlaylist.id,
                remoteName = remotePlaylist.name,
            )
        }

        existingBindingsByRemoteId.values
            .filter { it.remotePlaylistId !in remoteIds }
            .forEach { binding ->
                database.playlistRemoteBindingDao().deleteByPlaylistIdAndSourceId(binding.playlistId, binding.sourceId)
                database.playlistTrackDao().deleteByPlaylistIdAndSourceId(binding.playlistId, binding.sourceId)
                cleanupPlaylistIfNecessary(binding.playlistId)
        }
    }

    private suspend fun syncEmbySourcePlaylists(source: ImportSourceEntity) {
        val resolvedSource = resolveEmbySource(database, secureCredentialStore, source.id, addressSelector)
            ?: throw UiTextException(uiText(Res.string.playlist_source_credentials_missing, "Emby"))
        val remotePlaylists = fetchEmbyPlaylists(
            httpClient = httpClient,
            source = resolvedSource,
            logger = logger,
        )
        val remoteIds = remotePlaylists.mapTo(linkedSetOf()) { it.id }
        val existingBindingsByRemoteId = database.playlistRemoteBindingDao().getBySourceId(source.id)
            .associateBy { it.remotePlaylistId }
        val playlistsByNormalizedName = database.playlistDao().getAll()
            .associateBy { it.normalizedName }
            .toMutableMap()

        remotePlaylists.forEach { remotePlaylist ->
            val existingBinding = existingBindingsByRemoteId[remotePlaylist.id]
            val currentPlaylist = existingBinding?.let { database.playlistDao().getById(it.playlistId) }
                ?: playlistsByNormalizedName[normalizePlaylistName(remotePlaylist.name)]
            val playlist = currentPlaylist ?: PlaylistEntity(
                id = newId("playlist"),
                name = remotePlaylist.name,
                normalizedName = normalizePlaylistName(remotePlaylist.name),
                createdLocally = false,
                createdAt = now(),
                updatedAt = now(),
            )
            if (currentPlaylist == null) {
                database.playlistDao().upsert(playlist)
                playlistsByNormalizedName[playlist.normalizedName] = playlist
            }
            syncEmbyRemoteBinding(
                playlist = playlist,
                sourceId = source.id,
                remotePlaylistId = remotePlaylist.id,
                remoteName = remotePlaylist.name,
            )
        }

        existingBindingsByRemoteId.values
            .filter { it.remotePlaylistId !in remoteIds }
            .forEach { binding ->
                database.playlistRemoteBindingDao().deleteByPlaylistIdAndSourceId(binding.playlistId, binding.sourceId)
                database.playlistTrackDao().deleteByPlaylistIdAndSourceId(binding.playlistId, binding.sourceId)
                cleanupPlaylistIfNecessary(binding.playlistId)
            }
    }

    private suspend fun syncRemoteBinding(
        playlist: PlaylistEntity,
        sourceId: String,
        remotePlaylistId: String,
        remoteName: String,
    ) {
        val resolvedSource = resolveSubsonicCompatibleSource(sourceId)
            ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_sync, "Subsonic-compatible"))
        val remoteEntries = fetchRemotePlaylistEntries(
            resolvedSource = resolvedSource,
            remotePlaylistId = remotePlaylistId,
        )
        val nextRows = remoteEntries.mapIndexed { index, entry ->
            PlaylistTrackEntity(
                playlistId = playlist.id,
                trackId = subsonicCompatibleTrackIdFor(sourceId, entry.songId, resolvedSource.sourceType),
                sourceId = sourceId,
                addedAt = now(),
                localOrdinal = null,
                remoteOrdinal = index,
            )
        }
        database.immediateWriteTransaction {
            val currentRemoteTrackOrder = database.playlistTrackDao().getByPlaylistIdAndSourceId(playlist.id, sourceId)
                .sortedBy { it.remoteOrdinal ?: Int.MAX_VALUE }
                .map { RemotePlaylistTrackSnapshot(trackId = it.trackId, remoteOrdinal = it.remoteOrdinal ?: -1) }
            val nextRemoteTrackOrder = nextRows.map {
                RemotePlaylistTrackSnapshot(trackId = it.trackId, remoteOrdinal = it.remoteOrdinal ?: -1)
            }
            val tracksChanged = currentRemoteTrackOrder != nextRemoteTrackOrder
            if (tracksChanged) {
                database.playlistTrackDao().deleteByPlaylistIdAndSourceId(playlist.id, sourceId)
                if (nextRows.isNotEmpty()) {
                    database.playlistTrackDao().upsertAll(nextRows)
                }
            }
            database.playlistRemoteBindingDao().upsert(
                PlaylistRemoteBindingEntity(
                    playlistId = playlist.id,
                    sourceId = sourceId,
                    remotePlaylistId = remotePlaylistId,
                    remoteName = remoteName,
                    lastSyncedAt = now(),
                ),
            )
            if (tracksChanged) {
                touchPlaylist(playlist)
            }
        }
    }

    private suspend fun syncEmbyRemoteBinding(
        playlist: PlaylistEntity,
        sourceId: String,
        remotePlaylistId: String,
        remoteName: String,
    ) {
        val resolvedSource = resolveEmbySource(database, secureCredentialStore, sourceId, addressSelector)
            ?: throw UiTextException(uiText(Res.string.playlist_source_unavailable_sync, "Emby"))
        val remoteEntries = fetchEmbyPlaylistEntries(
            httpClient = httpClient,
            source = resolvedSource,
            playlistId = remotePlaylistId,
            logger = logger,
        )
        val nextRows = remoteEntries.mapIndexed { index, entry ->
            PlaylistTrackEntity(
                playlistId = playlist.id,
                trackId = embyTrackIdFor(sourceId, entry.itemId),
                sourceId = sourceId,
                addedAt = now(),
                localOrdinal = null,
                remoteOrdinal = index,
            )
        }
        database.immediateWriteTransaction {
            val currentRemoteTrackOrder = database.playlistTrackDao().getByPlaylistIdAndSourceId(playlist.id, sourceId)
                .sortedBy { it.remoteOrdinal ?: Int.MAX_VALUE }
                .map { RemotePlaylistTrackSnapshot(trackId = it.trackId, remoteOrdinal = it.remoteOrdinal ?: -1) }
            val nextRemoteTrackOrder = nextRows.map {
                RemotePlaylistTrackSnapshot(trackId = it.trackId, remoteOrdinal = it.remoteOrdinal ?: -1)
            }
            val tracksChanged = currentRemoteTrackOrder != nextRemoteTrackOrder
            if (tracksChanged) {
                database.playlistTrackDao().deleteByPlaylistIdAndSourceId(playlist.id, sourceId)
                if (nextRows.isNotEmpty()) {
                    database.playlistTrackDao().upsertAll(nextRows)
                }
            }
            database.playlistRemoteBindingDao().upsert(
                PlaylistRemoteBindingEntity(
                    playlistId = playlist.id,
                    sourceId = sourceId,
                    remotePlaylistId = remotePlaylistId,
                    remoteName = remoteName,
                    lastSyncedAt = now(),
                ),
            )
            if (tracksChanged) {
                touchPlaylist(playlist)
            }
        }
    }

    private suspend fun cleanupRemovedRemoteSources(activeSourceIds: Set<String>) {
        database.playlistRemoteBindingDao().getAll()
            .filter { it.sourceId !in activeSourceIds }
            .forEach { binding ->
                database.playlistRemoteBindingDao().deleteByPlaylistIdAndSourceId(binding.playlistId, binding.sourceId)
                database.playlistTrackDao().deleteByPlaylistIdAndSourceId(binding.playlistId, binding.sourceId)
                cleanupPlaylistIfNecessary(binding.playlistId)
            }
    }

    private suspend fun cleanupPlaylistIfNecessary(playlistId: String) {
        val playlist = database.playlistDao().getById(playlistId) ?: return
        val hasTracks = database.playlistTrackDao().getByPlaylistId(playlistId).isNotEmpty()
        val hasBindings = database.playlistRemoteBindingDao().getAll().any { it.playlistId == playlistId }
        if (!playlist.createdLocally && !hasTracks && !hasBindings) {
            database.playlistDao().deleteById(playlistId)
        }
    }

    private suspend fun deleteLocalPlaylist(playlistId: String) {
        database.playlistRemoteBindingDao().deleteByPlaylistId(playlistId)
        database.playlistTrackDao().deleteByPlaylistId(playlistId)
        database.playlistDao().deleteById(playlistId)
    }

    private suspend fun touchPlaylist(playlist: PlaylistEntity) {
        database.playlistDao().upsert(playlist.copy(updatedAt = now()))
    }

    private suspend fun fetchSourcePlaylists(
        resolvedSource: NavidromeResolvedSource,
    ): List<NavidromePlaylistSummaryPayload> {
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = resolvedSource,
            endpoint = "getPlaylists",
            logger = logger,
        )
        return payload["playlists"].asJsonObjectOrNull()
            ?.get("playlist")
            .asJsonObjectList()
            .mapNotNull { playlist ->
                val id = playlist.string("id") ?: return@mapNotNull null
                val name = playlist.string("name")?.trim().orEmpty().ifBlank { "未命名歌单" }
                NavidromePlaylistSummaryPayload(
                    id = id,
                    name = name,
                )
            }
    }

    private suspend fun fetchRemotePlaylistEntries(
        resolvedSource: NavidromeResolvedSource,
        remotePlaylistId: String,
    ): List<NavidromePlaylistEntryPayload> {
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = resolvedSource,
            endpoint = "getPlaylist",
            parameters = mapOf("id" to remotePlaylistId),
            logger = logger,
            logContext = "playlistId=$remotePlaylistId",
        )
        return payload["playlist"].asJsonObjectOrNull()
            ?.get("entry")
            .asJsonObjectList()
            .mapNotNull { entry ->
                val songId = entry.string("id") ?: return@mapNotNull null
                NavidromePlaylistEntryPayload(songId = songId)
            }
    }

    private suspend fun localPlaylistMutationRemoteBindings(
        playlist: PlaylistEntity,
        bindings: List<PlaylistRemoteBindingEntity>,
    ): List<PlaylistRemoteBindingEntity> {
        if (bindings.isEmpty()) return emptyList()
        val sourcesById = database.importSourceDao()
            .getAll()
            .associateBy { it.id }
        return bindings.filter { binding ->
            val source = sourcesById[binding.sourceId] ?: return@filter false
            source.isLocalIndexedEnabled() ||
                (playlist.createdLocally && source.enabled && source.supportsRemotePlaylistMutations())
        }
    }

    private suspend fun isLocalIndexedSource(sourceId: String): Boolean {
        return database.importSourceDao().getById(sourceId)?.isLocalIndexedEnabled() == true
    }

    private suspend fun resolveSubsonicCompatibleSource(
        sourceId: String,
        requireLocalIndex: Boolean = true,
    ): NavidromeResolvedSource? {
        val source = database.importSourceDao().getById(sourceId)
            ?.takeIf {
                it.subsonicCompatibleSourceType() != null &&
                    (!requireLocalIndex || it.isLocalIndexedEnabled())
            }
            ?: return null
        return source.toSubsonicCompatibleResolvedSource()
    }

    private suspend fun ImportSourceEntity.toSubsonicCompatibleResolvedSource(): NavidromeResolvedSource? {
        val sourceType = subsonicCompatibleSourceType() ?: return null
        val authMode = authMode.toSubsonicAuthMode()
        val username = username?.trim().orEmpty()
        val credential = credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        if (authMode == SubsonicAuthMode.PASSWORD && (username.isBlank() || credential.isBlank())) return null
        if (authMode == SubsonicAuthMode.API_KEY && credential.isBlank()) return null
        return NavidromeResolvedSource(
            baseUrl = rootReference,
            wanBaseUrl = wanRootReference,
            sourceId = id,
            addressSelector = addressSelector,
            username = username,
            password = credential,
            authMode = authMode,
            sourceType = sourceType,
        )
    }

    private fun ImportSourceEntity.subsonicCompatibleSourceType(): ImportSourceType? {
        val sourceType = runCatching { ImportSourceType.valueOf(type) }.getOrNull() ?: return null
        return sourceType.takeIf(::isSubsonicCompatibleSourceType)
    }

    private fun ImportSourceEntity.isEmbySource(): Boolean {
        return type == ImportSourceType.EMBY.name
    }

    private fun ImportSourceEntity.supportsRemotePlaylistMutations(): Boolean {
        return isEmbySource() || subsonicCompatibleSourceType() != null
    }
}

private fun PlaylistEntity.toSummary(
    memberTrackIds: Set<String> = emptySet(),
    artworkLocator: String? = null,
    artworkCacheKey: String? = null,
): PlaylistSummary {
    return PlaylistSummary(
        id = id,
        name = name,
        kind = PlaylistKind.USER,
        trackCount = memberTrackIds.size,
        updatedAt = updatedAt,
        memberTrackIds = memberTrackIds,
        artworkLocator = artworkLocator,
        artworkCacheKey = artworkCacheKey,
    )
}

private fun PlaylistEntity.isVisibleInLocalPlaylistBrowser(
    visiblePlaylistTracks: List<PlaylistTrackEntity>,
    remoteBindings: List<PlaylistRemoteBindingEntity>,
    localIndexedSourceIds: Set<String>,
): Boolean {
    if (createdLocally) return true
    if (visiblePlaylistTracks.any { it.playlistId == id }) return true
    return remoteBindings.any { binding ->
        binding.playlistId == id && binding.sourceId in localIndexedSourceIds
    }
}

private data class PlaylistSummaryArtwork(
    val locator: String,
    val cacheKey: String?,
)

private fun List<PlaylistTrackEntity>.latestPlaylistArtwork(trackById: Map<String, Track>): PlaylistSummaryArtwork? {
    var bestRow: PlaylistTrackEntity? = null
    var bestArtwork: PlaylistSummaryArtwork? = null
    for (row in this) {
        val track = trackById[row.trackId] ?: continue
        val locator = track.artworkLocator?.takeIf { it.isNotBlank() } ?: continue
        val currentBest = bestRow
        if (currentBest == null || row.isNewerPlaylistArtworkCandidateThan(currentBest)) {
            bestRow = row
            bestArtwork = PlaylistSummaryArtwork(
                locator = locator,
                cacheKey = trackArtworkCacheKey(track),
            )
        }
    }
    return bestArtwork
}

private fun PlaylistTrackEntity.isNewerPlaylistArtworkCandidateThan(other: PlaylistTrackEntity): Boolean {
    if (addedAt != other.addedAt) return addedAt > other.addedAt
    return playlistArtworkOrdinal() > other.playlistArtworkOrdinal()
}

private fun PlaylistTrackEntity.playlistArtworkOrdinal(): Int {
    return localOrdinal ?: remoteOrdinal ?: -1
}

private fun PlaylistEntity.toDetail(
    tracks: List<PlaylistTrackEntity>,
    trackById: Map<String, Track>,
    sourceLabelById: Map<String, String>,
): PlaylistDetail {
    val orderedTracks = tracks
        .filter { it.playlistId == id }
        .sortedWith(
            compareBy<PlaylistTrackEntity> { if (it.localOrdinal != null) 0 else 1 }
                .thenBy { it.localOrdinal ?: Int.MAX_VALUE }
                .thenBy { if (it.localOrdinal != null) "" else sourceLabelById[it.sourceId]?.lowercase().orEmpty() }
                .thenBy { it.remoteOrdinal ?: Int.MAX_VALUE }
                .thenBy { it.trackId },
        )
        .mapNotNull { row ->
            trackById[row.trackId]?.let { track ->
                PlaylistTrackEntry(
                    track = track,
                    sourceLabel = sourceLabelById[row.sourceId] ?: row.sourceId,
                )
            }
        }
    return PlaylistDetail(
        id = id,
        name = name,
        kind = PlaylistKind.USER,
        updatedAt = updatedAt,
        tracks = orderedTracks,
    )
}

private fun normalizePlaylistName(name: String): String = name.trim().lowercase()

private data class NavidromePlaylistSummaryPayload(
    val id: String,
    val name: String,
)

private data class NavidromePlaylistEntryPayload(
    val songId: String,
)

private data class RemotePlaylistTrackSnapshot(
    val trackId: String,
    val remoteOrdinal: Int,
)

private const val PlaylistTextImportSqlBindLimit = 900
private const val PlaylistTextImportSourceChunkSize = 100
private const val PlaylistTextImportBindingsPerKey = 2

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asJsonObjectList(): List<JsonObject> {
    return when (val element = this) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(element)
        else -> emptyList()
    }
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
