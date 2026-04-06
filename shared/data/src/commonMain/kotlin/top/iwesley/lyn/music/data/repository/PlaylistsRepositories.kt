package top.iwesley.lyn.music.data.repository

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
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaylistEntity
import top.iwesley.lyn.music.data.db.PlaylistRemoteBindingEntity
import top.iwesley.lyn.music.data.db.PlaylistTrackEntity
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.normalizeNavidromeBaseUrl
import top.iwesley.lyn.music.domain.requestNavidromeJson

class RoomPlaylistRepository(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val httpClient: LyricsHttpClient,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) : PlaylistRepository {
    override val playlists: Flow<List<PlaylistSummary>> = combine(
        database.playlistDao().observeAll(),
        database.playlistTrackDao().observeAll(),
        database.trackDao().observeAll(),
        database.lyricsCacheDao().observeBySourceId(MANUAL_LYRICS_OVERRIDE_SOURCE_ID),
    ) { playlists, playlistTracks, trackEntities, overrides ->
        val trackById = trackEntities.associate { entity ->
            entity.id to entity.toDomain(manualArtworkOverridesByTrackId(overrides)[entity.id])
        }
        playlists.map { playlist ->
            val memberTrackIds = playlistTracks.asSequence()
                .filter { it.playlistId == playlist.id && trackById.containsKey(it.trackId) }
                .map { it.trackId }
                .toCollection(linkedSetOf())
            playlist.toSummary(memberTrackIds)
        }
    }

    override fun observePlaylistDetail(playlistId: String): Flow<PlaylistDetail?> {
        return combine(
            database.playlistDao().observeAll(),
            database.playlistTrackDao().observeAll(),
            database.trackDao().observeAll(),
            database.importSourceDao().observeAll(),
            database.lyricsCacheDao().observeBySourceId(MANUAL_LYRICS_OVERRIDE_SOURCE_ID),
        ) { playlists, playlistTracks, trackEntities, sources, overrides ->
            val playlist = playlists.firstOrNull { it.id == playlistId } ?: return@combine null
            val artworkOverrides = manualArtworkOverridesByTrackId(overrides)
            val trackById = trackEntities.associate { entity ->
                entity.id to entity.toDomain(artworkOverrides[entity.id])
            }
            val sourceLabelById = sources.associate { it.id to it.label }
            playlist.toDetail(
                tracks = playlistTracks,
                trackById = trackById,
                sourceLabelById = sourceLabelById,
            )
        }
    }

    override suspend fun createPlaylist(name: String): Result<PlaylistSummary> {
        return runCatching {
            val displayName = name.trim()
            require(displayName.isNotBlank()) { "歌单名称不能为空。" }
            val normalizedName = normalizePlaylistName(displayName)
            require(database.playlistDao().getByNormalizedName(normalizedName) == null) { "歌单已存在。" }
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

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> {
        return runCatching {
            val playlist = database.playlistDao().getById(playlistId) ?: error("歌单不存在。")
            if (database.playlistTrackDao().getByPlaylistIdAndTrackId(playlistId, track.id) != null) {
                error("歌曲已在歌单中。")
            }
            val navidromeSongId = parseNavidromeSongLocator(track.mediaLocator)
                ?.takeIf { it.first == track.sourceId }
                ?.second
            if (navidromeSongId != null) {
                addNavidromeTrackToPlaylist(playlist, track, navidromeSongId)
            } else {
                addLocalTrackToPlaylist(playlist, track)
            }
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> {
        return runCatching {
            val playlist = database.playlistDao().getById(playlistId) ?: error("歌单不存在。")
            val row = database.playlistTrackDao().getByPlaylistIdAndTrackId(playlistId, trackId) ?: return@runCatching
            val binding = database.playlistRemoteBindingDao().getByPlaylistIdAndSourceId(playlistId, row.sourceId)
            if (binding != null && row.remoteOrdinal != null) {
                val resolvedSource = resolveNavidromeSource(row.sourceId)
                    ?: error("Navidrome 来源不可用，无法更新歌单。")
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
            } else {
                database.playlistTrackDao().deleteByPlaylistIdAndTrackId(playlistId, trackId)
                touchPlaylist(playlist)
                cleanupPlaylistIfNecessary(playlistId)
            }
        }
    }

    override suspend fun refreshNavidromePlaylists(): Result<Unit> {
        return runCatching {
            val navidromeSources = database.importSourceDao().getAll()
                .filter { it.type == ImportSourceType.NAVIDROME.name }
            cleanupRemovedNavidromeSources(navidromeSources.mapTo(linkedSetOf()) { it.id })
            val failures = mutableListOf<String>()
            navidromeSources.forEach { source ->
                runCatching { syncSourcePlaylists(source) }
                    .onFailure { throwable ->
                        failures += "${source.label}: ${throwable.message.orEmpty()}"
                    }
            }
            if (failures.isNotEmpty()) {
                error(failures.joinToString("\n"))
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

    private suspend fun addNavidromeTrackToPlaylist(
        playlist: PlaylistEntity,
        track: Track,
        navidromeSongId: String,
    ) {
        val resolvedSource = resolveNavidromeSource(track.sourceId)
            ?: error("Navidrome 来源不可用，无法更新歌单。")
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
                "songIdToAdd" to navidromeSongId,
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
            ?: error("远端歌单创建失败。")

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
        val resolvedSource = source.toNavidromeResolvedSource()
            ?: error("Navidrome 来源缺少有效账号或密码，无法同步歌单。")
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

    private suspend fun syncRemoteBinding(
        playlist: PlaylistEntity,
        sourceId: String,
        remotePlaylistId: String,
        remoteName: String,
    ) {
        val resolvedSource = resolveNavidromeSource(sourceId)
            ?: error("Navidrome 来源不可用，无法同步歌单。")
        val remoteEntries = fetchRemotePlaylistEntries(
            resolvedSource = resolvedSource,
            remotePlaylistId = remotePlaylistId,
        )
        val currentRemoteTrackOrder = database.playlistTrackDao().getByPlaylistIdAndSourceId(playlist.id, sourceId)
            .sortedBy { it.remoteOrdinal ?: Int.MAX_VALUE }
            .map { RemotePlaylistTrackSnapshot(trackId = it.trackId, remoteOrdinal = it.remoteOrdinal ?: -1) }
        val nextRemoteTrackOrder = remoteEntries.mapIndexed { index, entry ->
            RemotePlaylistTrackSnapshot(
                trackId = navidromeTrackIdFor(sourceId, entry.songId),
                remoteOrdinal = index,
            )
        }
        val tracksChanged = currentRemoteTrackOrder != nextRemoteTrackOrder
        if (tracksChanged) {
            database.playlistTrackDao().deleteByPlaylistIdAndSourceId(playlist.id, sourceId)
            if (remoteEntries.isNotEmpty()) {
                database.playlistTrackDao().upsertAll(
                    remoteEntries.mapIndexed { index, entry ->
                        PlaylistTrackEntity(
                            playlistId = playlist.id,
                            trackId = navidromeTrackIdFor(sourceId, entry.songId),
                            sourceId = sourceId,
                            addedAt = now(),
                            localOrdinal = null,
                            remoteOrdinal = index,
                        )
                    },
                )
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

    private suspend fun cleanupRemovedNavidromeSources(activeSourceIds: Set<String>) {
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

    private suspend fun resolveNavidromeSource(sourceId: String): NavidromeResolvedSource? {
        val source = database.importSourceDao().getById(sourceId)?.takeIf { it.type == ImportSourceType.NAVIDROME.name }
            ?: return null
        return source.toNavidromeResolvedSource()
    }

    private suspend fun ImportSourceEntity.toNavidromeResolvedSource(): NavidromeResolvedSource? {
        val username = username?.trim().orEmpty()
        val password = credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        if (username.isBlank() || password.isBlank()) return null
        return NavidromeResolvedSource(
            baseUrl = normalizeNavidromeBaseUrl(rootReference),
            username = username,
            password = password,
        )
    }
}

private fun PlaylistEntity.toSummary(memberTrackIds: Set<String> = emptySet()): PlaylistSummary {
    return PlaylistSummary(
        id = id,
        name = name,
        kind = PlaylistKind.USER,
        trackCount = memberTrackIds.size,
        updatedAt = updatedAt,
        memberTrackIds = memberTrackIds,
    )
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
