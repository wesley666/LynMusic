package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportIndexState
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagGateway
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.AlbumEntity
import top.iwesley.lyn.music.data.db.ArtistEntity
import top.iwesley.lyn.music.data.db.ImportIndexStateEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.WorkflowLyricsSourceConfigEntity
import top.iwesley.lyn.music.domain.buildLyricsRequest
import top.iwesley.lyn.music.domain.NAVIDROME_LYRICS_SOURCE_ID
import top.iwesley.lyn.music.domain.normalizeNavidromeBaseUrl
import top.iwesley.lyn.music.domain.requestNavidromeLyrics
import top.iwesley.lyn.music.domain.resolveNavidromeCoverArtUrl
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl
import top.iwesley.lyn.music.domain.scanNavidromeLibrary
import top.iwesley.lyn.music.domain.buildWorkflowRequest
import top.iwesley.lyn.music.domain.extractWorkflowEnrichmentStepCapture
import top.iwesley.lyn.music.domain.extractWorkflowLyricsPayload
import top.iwesley.lyn.music.domain.extractWorkflowSongCandidates
import top.iwesley.lyn.music.domain.extractWorkflowStepCapture
import top.iwesley.lyn.music.domain.parseWorkflowLyricsSourceConfig
import top.iwesley.lyn.music.domain.parseCachedLyrics
import top.iwesley.lyn.music.domain.ParsedLyricsPayload
import top.iwesley.lyn.music.domain.parseLyricsPayloadResults
import top.iwesley.lyn.music.domain.parseWorkflowLyricsDocument
import top.iwesley.lyn.music.domain.parsePlainText
import top.iwesley.lyn.music.domain.parseLrc
import top.iwesley.lyn.music.domain.rankWorkflowSongCandidates
import top.iwesley.lyn.music.domain.scoreWorkflowSongCandidate
import top.iwesley.lyn.music.domain.serializeLyricsDocument
import top.iwesley.lyn.music.domain.validateWorkflowLyricsSourceConfig
import top.iwesley.lyn.music.domain.mergeWorkflowCandidateCapture
import top.iwesley.lyn.music.domain.workflowCandidateVariables
import top.iwesley.lyn.music.domain.workflowTrackVariables

interface LibraryRepository {
    val tracks: Flow<List<Track>>
    val artists: Flow<List<Artist>>
    val albums: Flow<List<Album>>

    suspend fun getTracksByIds(trackIds: List<String>): List<Track>
}

interface ImportSourceRepository {
    fun observeSources(): Flow<List<SourceWithStatus>>
    suspend fun importLocalFolder(): Result<Unit>
    suspend fun addSambaSource(draft: SambaSourceDraft): Result<Unit>
    suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<Unit>
    suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<Unit>
    suspend fun rescanSource(sourceId: String): Result<Unit>
    suspend fun deleteSource(sourceId: String): Result<Unit>
}

interface LyricsRepository {
    suspend fun getLyrics(track: Track): ResolvedLyricsResult?
    suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean = true): List<LyricsSearchCandidate>
    suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate): LyricsDocument
    suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate>
    suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult
    suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate): AppliedWorkflowLyricsResult
}

data class ResolvedLyricsResult(
    val document: LyricsDocument,
    val artworkLocator: String? = null,
)

data class AppliedWorkflowLyricsResult(
    val document: LyricsDocument,
    val artworkLocator: String? = null,
)

interface SettingsRepository {
    val lyricsSources: Flow<List<LyricsSourceDefinition>>
    val useSambaCache: StateFlow<Boolean>

    suspend fun ensureDefaults()
    suspend fun setUseSambaCache(enabled: Boolean)
    suspend fun saveLyricsSource(config: LyricsSourceConfig)
    suspend fun saveWorkflowLyricsSource(rawJson: String, editingId: String? = null): WorkflowLyricsSourceConfig
    suspend fun setLyricsSourceEnabled(sourceId: String, enabled: Boolean)
    suspend fun deleteLyricsSource(configId: String)
}

class RoomLibraryRepository(
    private val database: LynMusicDatabase,
) : LibraryRepository {
    override val tracks: Flow<List<Track>> = database.trackDao()
        .observeAll()
        .map { entities -> entities.map { it.toDomain() } }

    override val artists: Flow<List<Artist>> = database.artistDao()
        .observeAll()
        .map { entities -> entities.map { Artist(id = it.id, name = it.name, trackCount = it.trackCount) } }

    override val albums: Flow<List<Album>> = database.albumDao()
        .observeAll()
        .map { entities -> entities.map { Album(id = it.id, title = it.title, artistName = it.artistName, trackCount = it.trackCount) } }

    override suspend fun getTracksByIds(trackIds: List<String>): List<Track> {
        if (trackIds.isEmpty()) return emptyList()
        val items = database.trackDao().getByIds(trackIds)
        val byId = items.associateBy { it.id }
        return trackIds.mapNotNull { byId[it]?.toDomain() }
    }
}

class RoomImportSourceRepository(
    private val database: LynMusicDatabase,
    private val gateway: ImportSourceGateway,
    private val secureCredentialStore: SecureCredentialStore,
) : ImportSourceRepository {
    override fun observeSources(): Flow<List<SourceWithStatus>> {
        return combine(
            database.importSourceDao().observeAll(),
            database.importIndexStateDao().observeAll(),
        ) { sources, states ->
            val stateBySource = states.associateBy { it.sourceId }
            sources.map { source ->
                SourceWithStatus(
                    source = source.toDomain(),
                    indexState = stateBySource[source.id]?.toDomain(),
                )
            }
        }
    }

    override suspend fun importLocalFolder(): Result<Unit> {
        return runCatching {
            val selection = gateway.pickLocalFolder() ?: return Result.success(Unit)
            val sourceId = newId("local")
            val source = ImportSource(
                id = sourceId,
                type = ImportSourceType.LOCAL_FOLDER,
                label = selection.label,
                rootReference = selection.persistentReference,
                createdAt = now(),
            )
            database.importSourceDao().upsert(source.toEntity())
            runScan(source) {
                gateway.scanLocalFolder(selection, sourceId)
            }
        }
    }

    override suspend fun addSambaSource(draft: SambaSourceDraft): Result<Unit> {
        return runCatching {
            val sourceId = newId("smb")
            val credentialKey = if (draft.password.isBlank()) null else "credential-$sourceId"
            if (credentialKey != null) {
                secureCredentialStore.put(credentialKey, draft.password)
            }
            val normalizedPath = normalizeSambaPath(draft.path)
            val source = ImportSource(
                id = sourceId,
                type = ImportSourceType.SAMBA,
                label = draft.label.ifBlank {
                    formatSambaEndpoint(
                        server = draft.server,
                        port = draft.port,
                        path = normalizedPath,
                    )
                },
                rootReference = normalizedPath,
                server = draft.server,
                port = draft.port,
                path = normalizedPath,
                username = draft.username,
                credentialKey = credentialKey,
                createdAt = now(),
            )
            database.importSourceDao().upsert(source.toEntity())
            runScan(source) {
                gateway.scanSamba(draft, sourceId)
            }
        }
    }

    override suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<Unit> {
        return runCatching {
            val sourceId = newId("dav")
            val credentialKey = if (draft.password.isBlank()) null else "credential-$sourceId"
            if (credentialKey != null) {
                secureCredentialStore.put(credentialKey, draft.password)
            }
            val normalizedRootUrl = normalizeWebDavRootUrl(draft.rootUrl)
            val source = ImportSource(
                id = sourceId,
                type = ImportSourceType.WEBDAV,
                label = draft.label.ifBlank { normalizedRootUrl },
                rootReference = normalizedRootUrl,
                username = draft.username,
                credentialKey = credentialKey,
                allowInsecureTls = draft.allowInsecureTls,
                createdAt = now(),
            )
            database.importSourceDao().upsert(source.toEntity())
            runScan(source) {
                gateway.scanWebDav(draft.copy(rootUrl = normalizedRootUrl), sourceId)
            }
        }
    }

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> {
        return runCatching {
            val sourceId = newId("navidrome")
            val credentialKey = "credential-$sourceId"
            secureCredentialStore.put(credentialKey, draft.password)
            val normalizedBaseUrl = normalizeNavidromeBaseUrl(draft.baseUrl)
            val source = ImportSource(
                id = sourceId,
                type = ImportSourceType.NAVIDROME,
                label = draft.label.ifBlank { normalizedBaseUrl },
                rootReference = normalizedBaseUrl,
                username = draft.username.trim(),
                credentialKey = credentialKey,
                createdAt = now(),
            )
            database.importSourceDao().upsert(source.toEntity())
            runScan(source) {
                gateway.scanNavidrome(
                    draft = draft.copy(
                        baseUrl = normalizedBaseUrl,
                        username = draft.username.trim(),
                    ),
                    sourceId = sourceId,
                )
            }
        }
    }

    override suspend fun rescanSource(sourceId: String): Result<Unit> {
        return runCatching {
            val entity = database.importSourceDao().getById(sourceId)
                ?: error("Source $sourceId does not exist.")
            val source = entity.toDomain()
            runScan(source.copy(lastScannedAt = now())) {
                when (source.type) {
                    ImportSourceType.LOCAL_FOLDER -> gateway.scanLocalFolder(
                        selection = LocalFolderSelection(
                            label = source.label,
                            persistentReference = source.rootReference,
                        ),
                        sourceId = source.id,
                    )

                    ImportSourceType.SAMBA -> {
                        val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
                        gateway.scanSamba(
                            draft = SambaSourceDraft(
                                label = source.label,
                                server = source.server.orEmpty(),
                                port = source.port,
                                path = source.path.orEmpty(),
                                username = source.username.orEmpty(),
                                password = password,
                            ),
                            sourceId = source.id,
                        )
                    }

                    ImportSourceType.WEBDAV -> {
                        val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
                        gateway.scanWebDav(
                            draft = WebDavSourceDraft(
                                label = source.label,
                                rootUrl = normalizeWebDavRootUrl(source.rootReference),
                                username = source.username.orEmpty(),
                                password = password,
                                allowInsecureTls = source.allowInsecureTls,
                            ),
                            sourceId = source.id,
                        )
                    }

                    ImportSourceType.NAVIDROME -> {
                        val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
                        gateway.scanNavidrome(
                            draft = NavidromeSourceDraft(
                                label = source.label,
                                baseUrl = normalizeNavidromeBaseUrl(source.rootReference),
                                username = source.username.orEmpty(),
                                password = password,
                            ),
                            sourceId = source.id,
                        )
                    }
                }
            }
        }
    }

    override suspend fun deleteSource(sourceId: String): Result<Unit> {
        return runCatching {
            val source = database.importSourceDao().getById(sourceId)?.toDomain()
                ?: error("Source $sourceId does not exist.")
            source.credentialKey?.let { secureCredentialStore.remove(it) }
            database.favoriteTrackDao().deleteBySourceId(source.id)
            database.trackDao().deleteBySourceId(source.id)
            database.lyricsCacheDao().deleteByTrackIdPrefix(trackIdPrefix(source.id))
            database.importIndexStateDao().deleteBySourceId(source.id)
            database.importSourceDao().deleteById(source.id)
            rebuildLibrarySummaries()
        }
    }

    private suspend fun persistScan(source: ImportSource, report: top.iwesley.lyn.music.core.model.ImportScanReport) {
        database.trackDao().deleteBySourceId(source.id)
        database.lyricsCacheDao().deleteByTrackIdPrefixAndSourceId(trackIdPrefix(source.id), EMBEDDED_LYRICS_SOURCE_ID)
        val trackEntities = report.tracks.map { candidate ->
            val artistId = candidate.artistName?.takeIf { it.isNotBlank() }?.let(::artistIdFor)
            val albumId = candidate.albumTitle?.takeIf { it.isNotBlank() }?.let {
                albumIdFor(candidate.artistName, it)
            }

            TrackEntity(
                id = trackIdFor(source.id, candidate.relativePath, candidate.mediaLocator),
                sourceId = source.id,
                title = candidate.title,
                artistId = artistId,
                artistName = candidate.artistName,
                albumId = albumId,
                albumTitle = candidate.albumTitle,
                durationMs = candidate.durationMs,
                trackNumber = candidate.trackNumber,
                discNumber = candidate.discNumber,
                mediaLocator = candidate.mediaLocator,
                relativePath = candidate.relativePath,
                artworkLocator = candidate.artworkLocator,
                sizeBytes = candidate.sizeBytes,
                modifiedAt = candidate.modifiedAt,
            )
        }

        if (trackEntities.isNotEmpty()) {
            database.trackDao().upsertAll(trackEntities)
        }
        val scannedAt = now()
        report.tracks.zip(trackEntities).forEach { (candidate, entity) ->
            candidate.embeddedLyrics
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { lyrics ->
                    database.lyricsCacheDao().upsert(
                        LyricsCacheEntity(
                            trackId = entity.id,
                            sourceId = EMBEDDED_LYRICS_SOURCE_ID,
                            rawPayload = lyrics,
                            updatedAt = scannedAt,
                        ),
                    )
                }
        }
        if (source.type != ImportSourceType.NAVIDROME) {
            database.favoriteTrackDao().deleteOrphansBySourceId(source.id)
        }
        rebuildLibrarySummaries()

        database.importSourceDao().upsert(source.copy(lastScannedAt = scannedAt).toEntity())
        database.importIndexStateDao().upsert(
            ImportIndexStateEntity(
                sourceId = source.id,
                trackCount = trackEntities.size,
                lastScannedAt = scannedAt,
                lastError = report.warnings.joinToString("\n").ifBlank { null },
            ),
        )
    }

    private suspend fun runScan(
        source: ImportSource,
        scan: suspend () -> top.iwesley.lyn.music.core.model.ImportScanReport,
    ) {
        try {
            persistScan(source, scan())
        } catch (throwable: Throwable) {
            persistScanFailure(source.id, throwable)
            throw throwable
        }
    }

    private suspend fun persistScanFailure(sourceId: String, throwable: Throwable) {
        val previous = database.importIndexStateDao().getBySourceId(sourceId)
        database.importIndexStateDao().upsert(
            ImportIndexStateEntity(
                sourceId = sourceId,
                trackCount = previous?.trackCount ?: 0,
                lastScannedAt = previous?.lastScannedAt,
                lastError = throwable.message ?: "扫描失败。",
            ),
        )
    }

    private suspend fun rebuildLibrarySummaries() {
        val tracks = database.trackDao().getAll()
        database.artistDao().deleteAll()
        database.albumDao().deleteAll()

        val artistEntities = tracks
            .mapNotNull { track ->
                track.artistName?.takeIf { it.isNotBlank() }
            }
            .groupingBy { it }
            .eachCount()
            .map { (artistName, trackCount) ->
                ArtistEntity(
                    id = artistIdFor(artistName),
                    name = artistName,
                    trackCount = trackCount,
                )
            }

        val albumEntities = tracks
            .mapNotNull { track ->
                track.albumTitle?.takeIf { it.isNotBlank() }?.let { albumTitle ->
                    Triple(
                        albumIdFor(track.artistName, albumTitle),
                        albumTitle,
                        track.artistName,
                    )
                }
            }
            .groupBy { it.first }
            .map { (albumId, items) ->
                val first = items.first()
                AlbumEntity(
                    id = albumId,
                    title = first.second,
                    artistName = first.third,
                    trackCount = items.size,
                )
            }

        if (artistEntities.isNotEmpty()) {
            database.artistDao().upsertAll(artistEntities)
        }
        if (albumEntities.isNotEmpty()) {
            database.albumDao().upsertAll(albumEntities)
        }
    }
}

class DefaultSettingsRepository(
    private val database: LynMusicDatabase,
    private val sambaCachePreferencesStore: SambaCachePreferencesStore,
) : SettingsRepository {
    override val lyricsSources: Flow<List<LyricsSourceDefinition>> = combine(
        database.lyricsSourceConfigDao().observeAll(),
        database.workflowLyricsSourceConfigDao().observeAll(),
    ) { directConfigs, workflowConfigs ->
        (directConfigs.map { it.toDomain() } + workflowConfigs.mapNotNull { it.toDomainOrNull() })
            .sortedWith(compareByDescending<LyricsSourceDefinition> { it.priority }.thenBy { it.name.lowercase() })
    }
    override val useSambaCache: StateFlow<Boolean> = sambaCachePreferencesStore.useSambaCache

    override suspend fun ensureDefaults() {
        val existing = database.lyricsSourceConfigDao().getAll()
        if (existing.isEmpty()) {
            val reservedNames = database.workflowLyricsSourceConfigDao().getAll()
                .mapTo(mutableSetOf()) { normalizeLyricsSourceName(it.name) }
            defaultLyricsSourceConfigs().forEach { config ->
                if (normalizeLyricsSourceName(config.name) !in reservedNames) {
                    database.lyricsSourceConfigDao().upsert(config.toEntity())
                }
            }
            return
        }

        existing
            .mapNotNull(::sanitizeBuiltInLrclibConfig)
            .forEach { config ->
                database.lyricsSourceConfigDao().upsert(config)
            }
    }

    override suspend fun setUseSambaCache(enabled: Boolean) {
        sambaCachePreferencesStore.setUseSambaCache(enabled)
    }

    override suspend fun saveLyricsSource(config: LyricsSourceConfig) {
        assertUniqueLyricsSourceName(
            name = config.name,
            currentDirectId = config.id,
        )
        database.lyricsSourceConfigDao().upsert(config.toEntity())
    }

    override suspend fun saveWorkflowLyricsSource(rawJson: String, editingId: String?): WorkflowLyricsSourceConfig {
        val config = parseWorkflowLyricsSourceConfig(rawJson)
        if (editingId != null && config.id != editingId) {
            error("Workflow 源 id 不支持修改。")
        }
        assertSourceIdAvailable(
            sourceId = config.id,
            currentWorkflowId = editingId,
        )
        assertUniqueLyricsSourceName(
            name = config.name,
            currentWorkflowId = config.id,
        )
        database.workflowLyricsSourceConfigDao().upsert(config.toEntity())
        return config
    }

    override suspend fun setLyricsSourceEnabled(sourceId: String, enabled: Boolean) {
        val direct = database.lyricsSourceConfigDao().getAll().firstOrNull { it.id == sourceId }
        if (direct != null) {
            database.lyricsSourceConfigDao().upsert(direct.copy(enabled = enabled))
            return
        }
        val workflow = database.workflowLyricsSourceConfigDao().getById(sourceId)
        if (workflow != null) {
            database.workflowLyricsSourceConfigDao().upsert(workflow.copy(enabled = enabled))
        }
    }

    override suspend fun deleteLyricsSource(configId: String) {
        database.lyricsSourceConfigDao().delete(configId)
        database.workflowLyricsSourceConfigDao().delete(configId)
    }

    private fun sanitizeBuiltInLrclibConfig(config: LyricsSourceConfigEntity): LyricsSourceConfigEntity? {
        if (!config.isBuiltInLrclib()) return null
        val sanitizedUrl = LRCLIB_SEARCH_URL
        val sanitizedQuery = sanitizeLrclibQueryTemplate(config.queryTemplate)
        val sanitizedExtractor = config.expectedBuiltInLrclibExtractor()
        return if (
            sanitizedUrl == config.urlTemplate &&
            sanitizedQuery == config.queryTemplate &&
            sanitizedExtractor == config.extractor
        ) {
            null
        } else {
            config.copy(
                urlTemplate = sanitizedUrl,
                queryTemplate = sanitizedQuery,
                extractor = sanitizedExtractor,
            )
        }
    }

    private fun LyricsSourceConfigEntity.isBuiltInLrclib(): Boolean {
        return id in BUILT_IN_LRCLIB_SOURCE_IDS && urlTemplate.startsWith(LRCLIB_BASE_URL)
    }

    private fun LyricsSourceConfigEntity.expectedBuiltInLrclibExtractor(): String {
        return when (id) {
            "lrclib-synced" -> LRCLIB_SYNCED_JSON_MAP_EXTRACTOR
            "lrclib-plain" -> LRCLIB_PLAIN_JSON_MAP_EXTRACTOR
            else -> extractor
        }
    }

    private suspend fun assertUniqueLyricsSourceName(
        name: String,
        currentDirectId: String? = null,
        currentWorkflowId: String? = null,
    ) {
        val normalizedTarget = normalizeLyricsSourceName(name)
        if (normalizedTarget.isBlank()) {
            error("歌词源名称不能为空。")
        }
        val directConflict = database.lyricsSourceConfigDao().getAll().any { entity ->
            entity.id != currentDirectId && normalizeLyricsSourceName(entity.name) == normalizedTarget
        }
        val workflowConflict = database.workflowLyricsSourceConfigDao().getAll().any { entity ->
            entity.id != currentWorkflowId && normalizeLyricsSourceName(entity.name) == normalizedTarget
        }
        if (directConflict || workflowConflict) {
            error("歌词源名称已存在。")
        }
    }

    private suspend fun assertSourceIdAvailable(
        sourceId: String,
        currentWorkflowId: String? = null,
    ) {
        val hasDirectConflict = database.lyricsSourceConfigDao().getAll().any { it.id == sourceId }
        val hasWorkflowConflict = database.workflowLyricsSourceConfigDao().getAll().any { it.id == sourceId && it.id != currentWorkflowId }
        if (hasDirectConflict || hasWorkflowConflict) {
            error("歌词源 id 已存在。")
        }
    }

    private companion object {
        val BUILT_IN_LRCLIB_SOURCE_IDS = setOf("lrclib-synced", "lrclib-plain")
    }
}

private fun normalizeLyricsSourceName(name: String): String {
    return name.trim().lowercase()
}

class DefaultLyricsRepository(
    private val database: LynMusicDatabase,
    private val httpClient: LyricsHttpClient,
    private val secureCredentialStore: SecureCredentialStore,
    private val audioTagGateway: AudioTagGateway = UnsupportedAudioTagGateway,
    private val artworkCacheStore: ArtworkCacheStore = object : ArtworkCacheStore {
        override suspend fun cache(locator: String, cacheKey: String): String? = locator
    },
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) : LyricsRepository {
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? {
        val trackLabel = track.logIdentity()
        val cachedRows = database.lyricsCacheDao().getByTrack(track.id)
        if (parseNavidromeSongLocator(track.mediaLocator) != null) {
            cachedRows
                .firstOrNull { it.sourceId == NAVIDROME_LYRICS_SOURCE_ID }
                ?.let { cache -> parseCachedLyrics(cache.sourceId, cache.rawPayload) }
                ?.let { cached ->
                    logger.debug(LYRICS_LOG_TAG) {
                        "cache-hit track=$trackLabel source=${cached.sourceId} synced=${cached.isSynced} lines=${cached.lines.size}"
                    }
                    return ResolvedLyricsResult(document = cached)
                }
            requestNavidromeLyricsDocument(track)?.let { navidromeLyrics ->
                storeLyricsDocument(track.id, navidromeLyrics)
                logger.info(LYRICS_LOG_TAG) {
                    "resolved track=$trackLabel source=${navidromeLyrics.sourceId} synced=${navidromeLyrics.isSynced} lines=${navidromeLyrics.lines.size}"
                }
                return ResolvedLyricsResult(document = navidromeLyrics)
            }
            cachedRows
                .firstNotNullOfOrNull { cache ->
                    cache.takeUnless { it.sourceId == NAVIDROME_LYRICS_SOURCE_ID }
                        ?.let { parseCachedLyrics(it.sourceId, it.rawPayload) }
                }
                ?.let { cached ->
                    logger.debug(LYRICS_LOG_TAG) {
                        "cache-hit track=$trackLabel source=${cached.sourceId} synced=${cached.isSynced} lines=${cached.lines.size}"
                    }
                    return ResolvedLyricsResult(document = cached)
                }
        } else {
            cachedRows
                .firstNotNullOfOrNull { cache -> parseCachedLyrics(cache.sourceId, cache.rawPayload) }
                ?.let { cached ->
                    logger.debug(LYRICS_LOG_TAG) {
                        "cache-hit track=$trackLabel source=${cached.sourceId} synced=${cached.isSynced} lines=${cached.lines.size}"
                    }
                    return ResolvedLyricsResult(document = cached)
                }
        }

        val sources = enabledLyricsSources()
        if (sources.isEmpty()) {
            logger.warn(LYRICS_LOG_TAG) { "no-enabled-sources track=$trackLabel" }
            return null
        }

        for (source in sources) {
            val result = when (source) {
                is LyricsSourceConfig -> requestDirectLyricsResults(
                    track = track,
                    config = source,
                    requestType = "auto",
                ).firstOrNull()?.let { parsed ->
                    ResolvedLyricsResult(
                        document = parsed.document,
                        artworkLocator = normalizeArtworkLocator(parsed.artworkLocator),
                    )
                }

                is WorkflowLyricsSourceConfig -> requestWorkflowLyricsDocument(
                    track = track,
                    config = source,
                    requestType = "auto",
                )
            } ?: continue
            storeLyricsDocument(track.id, result.document)
            logger.info(LYRICS_LOG_TAG) {
                "resolved track=$trackLabel source=${source.id} synced=${result.document.isSynced} " +
                    "lines=${result.document.lines.size} artworkLocator=${result.artworkLocator.orEmpty()}"
            }
            return result
        }
        logger.warn(LYRICS_LOG_TAG) {
            "miss track=$trackLabel attempted=${sources.joinToString(",") { it.id }}"
        }
        return null
    }

    private suspend fun requestNavidromeLyricsDocument(track: Track): LyricsDocument? {
        val locator = parseNavidromeSongLocator(track.mediaLocator) ?: return null
        val source = database.importSourceDao().getById(locator.first)?.takeIf { it.type == ImportSourceType.NAVIDROME.name }
            ?: return null
        val username = source.username?.trim().orEmpty()
        val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        if (username.isBlank() || password.isBlank()) return null
        return requestNavidromeLyrics(
            httpClient = httpClient,
            source = top.iwesley.lyn.music.domain.NavidromeResolvedSource(
                baseUrl = normalizeNavidromeBaseUrl(source.rootReference),
                username = username,
                password = password,
            ),
            track = track,
        )
    }

    override suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean): List<LyricsSearchCandidate> {
        val trackLabel = track.logIdentity()
        val baseTrack = database.trackDao().getByIds(listOf(track.id)).firstOrNull()?.toDomain() ?: track
        val trackProvidedCandidate = if (includeTrackProvidedCandidate) {
            buildTrackProvidedLyricsCandidate(baseTrack)
        } else {
            null
        }
        val configs = enabledDirectLyricsConfigs()
        if (configs.isEmpty()) {
            return if (trackProvidedCandidate != null) {
                logger.debug(LYRICS_LOG_TAG) { "manual-track-provided-only track=$trackLabel source=${trackProvidedCandidate.sourceId}" }
                listOf(trackProvidedCandidate)
            } else {
                logger.warn(LYRICS_LOG_TAG) { "manual-no-enabled-direct-sources track=$trackLabel" }
                emptyList()
            }
        }
        val directCandidates = configs.flatMap { config ->
            requestDirectLyricsResults(
                track = track,
                config = config,
                requestType = "manual",
            ).map { parsed ->
                LyricsSearchCandidate(
                    sourceId = config.id,
                    sourceName = config.name,
                    document = parsed.document,
                    itemId = parsed.itemId,
                    title = parsed.title,
                    artistName = parsed.artistName,
                    albumTitle = parsed.albumTitle,
                    durationSeconds = parsed.durationSeconds,
                    artworkLocator = normalizeArtworkLocator(parsed.artworkLocator),
                    isTrackProvided = false,
                )
            }
        }
        return buildList {
            trackProvidedCandidate?.let(::add)
            addAll(directCandidates)
        }
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> {
        val trackLabel = track.logIdentity()
        val configs = enabledWorkflowLyricsConfigs()
        if (configs.isEmpty()) {
            logger.warn(LYRICS_LOG_TAG) { "manual-no-enabled-workflow-sources track=$trackLabel" }
            return emptyList()
        }
        val candidates = configs.flatMap { config ->
            searchWorkflowCandidates(track, config, requestType = "manual")
        }
        if (candidates.isNotEmpty()) {
            logger.debug(LYRICS_LOG_TAG) {
                "manual-workflow-cover-candidates track=$trackLabel results=" +
                    candidates.joinToString(" | ") { candidate ->
                        "${candidate.sourceId}:${candidate.id}:${candidate.imageUrl.orEmpty()}"
                    }
            }
        }
        return candidates
    }

    override suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate): LyricsDocument {
        val cachedRawPayload = serializeLyricsDocument(candidate.document)
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = trackId,
                sourceId = candidate.sourceId,
                rawPayload = cachedRawPayload,
                updatedAt = now(),
            ),
        )
        if (candidate.isTrackProvided) {
            normalizeArtworkLocator(candidate.artworkLocator)
                ?.takeIf { it.isNotBlank() }
                ?.let { artworkLocator ->
                    database.trackDao().updateArtworkLocator(trackId, artworkLocator)
                }
        }
        logger.info(LYRICS_LOG_TAG) {
            "manual-apply track=$trackId source=${candidate.sourceId} synced=${candidate.document.isSynced} " +
                "lines=${candidate.document.lines.size}"
        }
        return parseCachedLyrics(candidate.sourceId, cachedRawPayload)
            ?: candidate.document.copy(
                sourceId = candidate.sourceId,
                rawPayload = cachedRawPayload,
            )
    }

    private suspend fun buildTrackProvidedLyricsCandidate(track: Track): LyricsSearchCandidate? {
        return if (parseNavidromeSongLocator(track.mediaLocator) != null) {
            buildNavidromeTrackProvidedLyricsCandidate(track)
        } else {
            buildEmbeddedTrackProvidedLyricsCandidate(track)
        }
    }

    private suspend fun buildNavidromeTrackProvidedLyricsCandidate(track: Track): LyricsSearchCandidate? {
        val document = runCatching { requestNavidromeLyricsDocument(track) }
            .onFailure { throwable ->
                logger.warn(LYRICS_LOG_TAG) {
                    "manual-track-provided-navidrome-failed track=${track.logIdentity()} reason=${throwable.message.orEmpty()}"
                }
            }
            .getOrNull()
            ?: return null
        return LyricsSearchCandidate(
            sourceId = document.sourceId,
            sourceName = "Navidrome",
            document = document,
            title = track.title.takeIf { it.isNotBlank() },
            artistName = track.artistName?.takeIf { it.isNotBlank() },
            albumTitle = track.albumTitle?.takeIf { it.isNotBlank() },
            durationSeconds = track.durationSecondsOrNull(),
            artworkLocator = normalizeArtworkLocator(track.artworkLocator),
            isTrackProvided = true,
        )
    }

    private suspend fun buildEmbeddedTrackProvidedLyricsCandidate(track: Track): LyricsSearchCandidate? {
        val isSambaTrack = parseSambaLocator(track.mediaLocator) != null
        val currentSnapshot = runCatching {
            if (isSambaTrack || audioTagGateway.canEdit(track)) {
                audioTagGateway.read(track).getOrThrow()
            } else {
                null
            }
        }.onFailure { throwable ->
            logger.warn(LYRICS_LOG_TAG) {
                "manual-track-provided-tag-read-failed track=${track.logIdentity()} reason=${throwable.message.orEmpty()}"
            }
        }.getOrNull()

        if (currentSnapshot != null) {
            val rawPayload = currentSnapshot.embeddedLyrics?.trim().orEmpty()
            val document = parseCachedLyrics(EMBEDDED_LYRICS_SOURCE_ID, rawPayload)
            if (document == null) return null
            return LyricsSearchCandidate(
                sourceId = EMBEDDED_LYRICS_SOURCE_ID,
                sourceName = "歌曲标签",
                document = document,
                title = currentSnapshot.title.takeIf { it.isNotBlank() },
                artistName = currentSnapshot.artistName?.takeIf { it.isNotBlank() },
                albumTitle = currentSnapshot.albumTitle?.takeIf { it.isNotBlank() },
                durationSeconds = track.durationSecondsOrNull(),
                artworkLocator = normalizeArtworkLocator(
                    if (isSambaTrack) currentSnapshot.artworkLocator else (currentSnapshot.artworkLocator ?: track.artworkLocator),
                ),
                isTrackProvided = true,
            )
        }
        if (isSambaTrack) return null

        val cached = database.lyricsCacheDao().getByTrack(track.id)
            .firstOrNull { it.sourceId == EMBEDDED_LYRICS_SOURCE_ID }
            ?.let { row -> parseCachedLyrics(row.sourceId, row.rawPayload) }
            ?: return null
        return LyricsSearchCandidate(
            sourceId = EMBEDDED_LYRICS_SOURCE_ID,
            sourceName = "歌曲标签",
            document = cached,
            title = track.title.takeIf { it.isNotBlank() },
            artistName = track.artistName?.takeIf { it.isNotBlank() },
            albumTitle = track.albumTitle?.takeIf { it.isNotBlank() },
            durationSeconds = track.durationSecondsOrNull(),
            artworkLocator = normalizeArtworkLocator(track.artworkLocator),
            isTrackProvided = true,
        )
    }

    override suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate): AppliedWorkflowLyricsResult {
        val config = database.workflowLyricsSourceConfigDao().getById(candidate.sourceId)?.toDomainOrNull()
            ?: error("Workflow lyrics source ${candidate.sourceId} does not exist.")
        val document = fetchWorkflowLyricsForCandidate(
            track = database.trackDao().getByIds(listOf(trackId)).firstOrNull()?.toDomain()
                ?: Track(
                    id = trackId,
                    sourceId = "",
                    title = candidate.title,
                    artistName = candidate.artists.joinToString(" / ").ifBlank { null },
                    albumTitle = candidate.album,
                    durationMs = (candidate.durationSeconds?.toLong() ?: 0L) * 1_000L,
                    mediaLocator = "",
                    relativePath = "",
                ),
            config = config,
            candidate = candidate,
            requestType = "manual",
        ) ?: error("Workflow lyrics source ${candidate.sourceName} 没有返回可解析歌词。")
        storeLyricsDocument(trackId, document)
        val cachedArtworkLocator = cacheWorkflowArtwork(trackId, candidate)
        logger.info(LYRICS_LOG_TAG) {
            "manual-workflow-apply track=$trackId source=${candidate.sourceId} synced=${document.isSynced} " +
                "lines=${document.lines.size} coverUrl=${candidate.imageUrl.orEmpty()} " +
                "artworkLocator=${cachedArtworkLocator.orEmpty()}"
        }
        return AppliedWorkflowLyricsResult(
            document = document,
            artworkLocator = cachedArtworkLocator,
        )
    }

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        val config = database.workflowLyricsSourceConfigDao().getById(candidate.sourceId)?.toDomainOrNull()
            ?: error("Workflow lyrics source ${candidate.sourceId} does not exist.")
        val document = fetchWorkflowLyricsForCandidate(
            track = track,
            config = config,
            candidate = candidate,
            requestType = "tag-import",
        ) ?: error("Workflow lyrics source ${candidate.sourceName} 没有返回可解析歌词。")
        return ResolvedLyricsResult(
            document = document.copy(rawPayload = serializeLyricsDocument(document)),
            artworkLocator = normalizeArtworkLocator(candidate.imageUrl),
        )
    }

    private suspend fun cacheWorkflowArtwork(trackId: String, candidate: WorkflowSongCandidate): String? {
        val sourceLocator = normalizeArtworkLocator(candidate.imageUrl)?.trim().orEmpty()
        if (sourceLocator.isBlank()) return null
        val cachedLocator = runCatching {
            artworkCacheStore.cache(
                locator = sourceLocator,
                cacheKey = "${trackId}_${candidate.sourceId}_${candidate.id}",
            )
        }.onFailure { throwable ->
            logger.error(LYRICS_LOG_TAG, throwable) {
                "workflow-artwork-cache-failed track=$trackId source=${candidate.sourceId} candidate=${candidate.id} url=$sourceLocator"
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (cachedLocator != null) {
            database.trackDao().updateArtworkLocator(trackId, cachedLocator)
            logger.debug(LYRICS_LOG_TAG) {
                "workflow-artwork-cache-hit track=$trackId source=${candidate.sourceId} candidate=${candidate.id} locator=$cachedLocator"
            }
        }
        return cachedLocator
    }

    private suspend fun enabledLyricsSources(): List<LyricsSourceDefinition> {
        return (enabledDirectLyricsConfigs() + enabledWorkflowLyricsConfigs())
            .sortedWith(compareByDescending<LyricsSourceDefinition> { it.priority }.thenBy { it.name.lowercase() })
    }

    private suspend fun enabledDirectLyricsConfigs(): List<LyricsSourceConfig> {
        return database.lyricsSourceConfigDao().getEnabled().map { it.toDomain() }
    }

    private suspend fun enabledWorkflowLyricsConfigs(): List<WorkflowLyricsSourceConfig> {
        return database.workflowLyricsSourceConfigDao().getEnabled().mapNotNull { it.toDomainOrNull() }
    }

    private suspend fun requestDirectLyricsResults(
        track: Track,
        config: LyricsSourceConfig,
        requestType: String,
    ): List<ParsedLyricsPayload> {
        val trackLabel = track.logIdentity()
        val request = buildLyricsRequest(config, track)
        logger.logLyricsHttpRequest(
            context = "$requestType-request track=$trackLabel source=${config.id}",
            request = request,
        )
        val startedAt = now()
        val response = httpClient.request(request).fold(
            onSuccess = { it },
            onFailure = { throwable ->
                logger.error(LYRICS_LOG_TAG, throwable) {
                    "$requestType-request-failed track=$trackLabel source=${config.id} " +
                        "elapsedMs=${now() - startedAt} method=${request.method.name} url=${request.url}"
                }
                null
            },
        ) ?: return emptyList()
        logger.logLyricsHttpResponse(
            context = "$requestType-response track=$trackLabel source=${config.id}",
            response = response,
            elapsedMs = now() - startedAt,
        )
        val parsed = parseLyricsPayloadResults(config, response.body)
        if (parsed.isEmpty()) {
            logger.warn(LYRICS_LOG_TAG) {
                "$requestType-parse-miss track=$trackLabel source=${config.id} status=${response.statusCode} " +
                    "extractor=${config.extractor}"
            }
        }
        return parsed
    }

    private suspend fun requestWorkflowLyricsDocument(
        track: Track,
        config: WorkflowLyricsSourceConfig,
        requestType: String,
    ): ResolvedLyricsResult? {
        val candidates = searchWorkflowCandidates(track, config, requestType)
        val rankedCandidates = rankWorkflowSongCandidates(track, candidates, config.selection)
        if (rankedCandidates.isEmpty()) {
            logger.warn(LYRICS_LOG_TAG) {
                "$requestType-workflow-select-miss track=${track.logIdentity()} source=${config.id} candidates=${candidates.size}"
            }
            return null
        }
        for (candidate in rankedCandidates) {
            logger.debug(LYRICS_LOG_TAG) {
                "$requestType-workflow-select-hit track=${track.logIdentity()} source=${config.id} candidate=${candidate.id} coverUrl=${candidate.imageUrl.orEmpty()}"
            }
            val document = fetchWorkflowLyricsForCandidate(track, config, candidate, requestType)
            if (document == null) {
                logger.warn(LYRICS_LOG_TAG) {
                    "$requestType-workflow-candidate-miss track=${track.logIdentity()} source=${config.id} candidate=${candidate.id}"
                }
                continue
            }
            val artworkLocator = cacheWorkflowArtwork(track.id, candidate)
            return ResolvedLyricsResult(
                document = document,
                artworkLocator = artworkLocator,
            )
        }
        logger.warn(LYRICS_LOG_TAG) {
            "$requestType-workflow-lyrics-miss track=${track.logIdentity()} source=${config.id} tried=${rankedCandidates.joinToString(",") { it.id }}"
        }
        return null
    }

    private suspend fun searchWorkflowCandidates(
        track: Track,
        config: WorkflowLyricsSourceConfig,
        requestType: String,
    ): List<WorkflowSongCandidate> {
        val variables = workflowTrackVariables(track)
        val request = buildWorkflowRequest(config.search.request, variables)
        val trackLabel = track.logIdentity()
        logger.logLyricsHttpRequest(
            context = "$requestType-workflow-search track=$trackLabel source=${config.id}",
            request = request,
        )
        val startedAt = now()
        val response = httpClient.request(request).fold(
            onSuccess = { it },
            onFailure = { throwable ->
                logger.error(LYRICS_LOG_TAG, throwable) {
                    "$requestType-workflow-search-failed track=$trackLabel source=${config.id} elapsedMs=${now() - startedAt} url=${request.url}"
                }
                null
            },
        ) ?: return emptyList()
        logger.logLyricsHttpResponse(
            context = "$requestType-workflow-search-response track=$trackLabel source=${config.id}",
            response = response,
            elapsedMs = now() - startedAt,
        )
        val candidates = runCatching {
            extractWorkflowSongCandidates(config, response.body)
        }.getOrElse { throwable ->
            logger.error(LYRICS_LOG_TAG, throwable) {
                "$requestType-workflow-parse-failed track=$trackLabel source=${config.id} status=${response.statusCode}"
            }
            emptyList()
        }
        logger.debug(LYRICS_LOG_TAG) {
            "$requestType-workflow-search-candidates track=$trackLabel source=${config.id} candidates=${candidates.size}"
        }
        val limitedCandidates = candidates.take(config.selection.maxCandidates.coerceAtLeast(1))
        if (config.enrichment.steps.isEmpty()) return limitedCandidates
        val enrichedCandidates = limitedCandidates.map { candidate ->
            enrichWorkflowCandidate(track, config, candidate, requestType)
        }
        logger.debug(LYRICS_LOG_TAG) {
            "$requestType-workflow-cover-results track=$trackLabel source=${config.id} candidates=" +
                enrichedCandidates.joinToString(" | ") { candidate ->
                    "${candidate.id}:${candidate.imageUrl.orEmpty()}"
                }
        }
        return enrichedCandidates
    }

    private suspend fun enrichWorkflowCandidate(
        track: Track,
        config: WorkflowLyricsSourceConfig,
        candidate: WorkflowSongCandidate,
        requestType: String,
    ): WorkflowSongCandidate {
        val trackLabel = track.logIdentity()
        var enrichedCandidate = candidate
        config.enrichment.steps.forEachIndexed { index, step ->
            val requestVariables = workflowTrackVariables(track) + workflowCandidateVariables(enrichedCandidate)
            val request = buildWorkflowRequest(step.request, requestVariables)
            logger.logLyricsHttpRequest(
                context = "$requestType-workflow-enrichment track=$trackLabel source=${config.id} step=$index candidate=${candidate.id}",
                request = request,
            )
            val startedAt = now()
            val response = httpClient.request(request).fold(
                onSuccess = { it },
                onFailure = { throwable ->
                    logger.log(DiagnosticLogLevel.WARN, LYRICS_LOG_TAG,
                        "$requestType-workflow-enrichment-failed track=$trackLabel source=${config.id} step=$index candidate=${candidate.id} elapsedMs=${now() - startedAt} url=${request.url}"
                    , throwable)
                    null
                },
            ) ?: return@forEachIndexed
            logger.logLyricsHttpResponse(
                context = "$requestType-workflow-enrichment-response track=$trackLabel source=${config.id} step=$index candidate=${candidate.id}",
                response = response,
                elapsedMs = now() - startedAt,
            )
            val capture = runCatching {
                extractWorkflowEnrichmentStepCapture(step, response.body)
            }.getOrElse { throwable ->
                logger.log(DiagnosticLogLevel.WARN, LYRICS_LOG_TAG,
                    "$requestType-workflow-enrichment-capture-failed track=$trackLabel source=${config.id} step=$index candidate=${candidate.id} status=${response.statusCode}"
                , throwable)
                emptyMap()
            }
            if (capture.isNotEmpty()) {
                enrichedCandidate = mergeWorkflowCandidateCapture(enrichedCandidate, capture)
                logger.debug(LYRICS_LOG_TAG) {
                    "$requestType-workflow-enrichment-response track=$trackLabel source=${config.id} step=$index candidate=${candidate.id} " +
                        "elapsedMs=${now() - startedAt} captured=${capture.keys.joinToString(",")} imageUrl=${enrichedCandidate.imageUrl.orEmpty()}"
                }
            }
        }
        return enrichedCandidate
    }

    private suspend fun fetchWorkflowLyricsForCandidate(
        track: Track,
        config: WorkflowLyricsSourceConfig,
        candidate: WorkflowSongCandidate,
        requestType: String,
    ): LyricsDocument? {
        val trackLabel = track.logIdentity()
        val baseVariables = workflowTrackVariables(track) + workflowCandidateVariables(candidate)
        val stepOutputs = mutableMapOf<String, Map<String, String>>()
        var finalPayload: String? = null
        config.lyrics.steps.forEachIndexed { index, step ->
            val requestVariables = buildMap {
                putAll(baseVariables)
                stepOutputs.forEach { (stepName, values) ->
                    values.forEach { (key, value) -> put("$stepName.$key", value) }
                }
            }
            val request = buildWorkflowRequest(step.request, requestVariables)
            logger.logLyricsHttpRequest(
                context = "$requestType-workflow-step track=$trackLabel source=${config.id} step=$index candidate=${candidate.id}",
                request = request,
            )
            val startedAt = now()
            val response = httpClient.request(request).fold(
                onSuccess = { it },
                onFailure = { throwable ->
                    logger.error(LYRICS_LOG_TAG, throwable) {
                        "$requestType-workflow-step-failed track=$trackLabel source=${config.id} step=$index elapsedMs=${now() - startedAt} url=${request.url}"
                    }
                    null
                },
            ) ?: return null
            logger.logLyricsHttpResponse(
                context = "$requestType-workflow-step-response track=$trackLabel source=${config.id} step=$index candidate=${candidate.id}",
                response = response,
                elapsedMs = now() - startedAt,
            )
            val capture = runCatching {
                extractWorkflowStepCapture(step, response.body)
            }.getOrElse { throwable ->
                logger.error(LYRICS_LOG_TAG, throwable) {
                    "$requestType-workflow-step-capture-failed track=$trackLabel source=${config.id} step=$index status=${response.statusCode}"
                }
                return null
            }
            if (capture.isNotEmpty()) {
                stepOutputs["step$index"] = capture
            }
            if (index == config.lyrics.steps.lastIndex) {
                finalPayload = runCatching {
                    extractWorkflowLyricsPayload(step, response.body)
                }.getOrElse { throwable ->
                    logger.error(LYRICS_LOG_TAG, throwable) {
                        "$requestType-workflow-step-payload-failed track=$trackLabel source=${config.id} step=$index status=${response.statusCode}"
                    }
                    null
                }
            }
        }
        val payload = finalPayload?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        return parseWorkflowLyricsDocument(
            sourceId = config.id,
            sourceName = config.name,
            step = config.lyrics.steps.last(),
            payload = payload,
        )
    }

    private suspend fun storeLyricsDocument(trackId: String, document: LyricsDocument) {
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = trackId,
                sourceId = document.sourceId,
                rawPayload = serializeLyricsDocument(document),
                updatedAt = now(),
            ),
        )
    }
}

private fun now(): Long = Clock.System.now().toEpochMilliseconds()

private const val LYRICS_LOG_TAG = "Lyrics"
private const val EMBEDDED_LYRICS_SOURCE_ID = "embedded-tag"

private fun newId(prefix: String): String = "$prefix-${now()}-${Random.nextInt(1000, 9999)}"

private fun artistIdFor(name: String): String = "artist:${name.trim().lowercase()}"

private fun albumIdFor(artistName: String?, albumTitle: String): String {
    return "album:${artistName.orEmpty().trim().lowercase()}:${albumTitle.trim().lowercase()}"
}

internal fun navidromeTrackIdFor(sourceId: String, songId: String): String {
    return "track:${sourceId}:navidrome:${songId.lowercase()}"
}

private fun trackIdFor(sourceId: String, relativePath: String, mediaLocator: String): String {
    val navidromeSongId = parseNavidromeSongLocator(mediaLocator)?.second
    return if (navidromeSongId != null) {
        navidromeTrackIdFor(sourceId, navidromeSongId)
    } else {
        "track:${sourceId}:${relativePath.lowercase()}"
    }
}

private fun trackIdPrefix(sourceId: String): String = "track:${sourceId}:%"

private fun Track.logIdentity(): String {
    val artist = artistName?.takeIf { it.isNotBlank() } ?: "Unknown"
    return "\"$title\" by $artist (#$id)"
}

private fun Track.durationSecondsOrNull(): Int? {
    return (durationMs / 1_000L).takeIf { it > 0L }?.toInt()
}

private fun DiagnosticLogger.logLyricsHttpRequest(
    context: String,
    request: top.iwesley.lyn.music.core.model.LyricsRequest,
) {
    info(LYRICS_LOG_TAG) {
        buildString {
            append(context)
            append(" method=")
            append(request.method.name)
            append('\n')
            append("url: ")
            append(request.url)
            append('\n')
            append("headers:\n")
            append(request.headers.formatHeaderBlock())
            append('\n')
            append("body:\n")
            append(request.body?.ifBlank { "<empty>" } ?: "<empty>")
        }
    }
}

private fun DiagnosticLogger.logLyricsHttpResponse(
    context: String,
    response: top.iwesley.lyn.music.core.model.LyricsHttpResponse,
    elapsedMs: Long,
) {
    info(LYRICS_LOG_TAG) {
        buildString {
            append(context)
            append(" status=")
            append(response.statusCode)
            append(" elapsedMs=")
            append(elapsedMs)
            append('\n')
            append("body:\n")
            append(response.body.ifBlank { "<empty>" })
        }
    }
}

private fun Map<String, String>.formatHeaderBlock(): String {
    return if (isEmpty()) {
        "<empty>"
    } else {
        entries
            .sortedBy { it.key.lowercase() }
            .joinToString("\n") { (key, value) -> "$key: $value" }
    }
}

fun TrackEntity.toDomain(): Track {
    return Track(
        id = id,
        sourceId = sourceId,
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        mediaLocator = mediaLocator,
        relativePath = relativePath,
        artworkLocator = artworkLocator,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
    )
}

private fun ImportSourceEntity.toDomain(): ImportSource {
    val parsedPort = shareName?.toIntOrNull()
    val migratedPath = when {
        parsedPort != null -> normalizeSambaPath(directoryPath)
        shareName.isNullOrBlank() -> normalizeSambaPath(directoryPath)
        else -> normalizeSambaPath(joinSambaPath(shareName, directoryPath.orEmpty()))
    }
    return ImportSource(
        id = id,
        type = type.toImportSourceType(),
        label = label,
        rootReference = rootReference,
        server = server,
        port = parsedPort,
        path = migratedPath.ifBlank { null }.takeIf { type.toImportSourceType() == ImportSourceType.SAMBA },
        username = username,
        credentialKey = credentialKey,
        allowInsecureTls = allowInsecureTls,
        lastScannedAt = lastScannedAt,
        createdAt = createdAt,
    )
}

private fun ImportSource.toEntity(): ImportSourceEntity {
    return ImportSourceEntity(
        id = id,
        type = type.name,
        label = label,
        rootReference = rootReference,
        server = server,
        shareName = port?.toString().takeIf { type == ImportSourceType.SAMBA },
        directoryPath = normalizeSambaPath(path).ifBlank { null }.takeIf { type == ImportSourceType.SAMBA },
        username = username,
        credentialKey = credentialKey,
        allowInsecureTls = allowInsecureTls,
        lastScannedAt = lastScannedAt,
        createdAt = createdAt,
    )
}

private fun ImportIndexStateEntity.toDomain(): ImportIndexState {
    return ImportIndexState(
        sourceId = sourceId,
        trackCount = trackCount,
        lastScannedAt = lastScannedAt,
        lastError = lastError,
    )
}

private fun LyricsSourceConfigEntity.toDomain(): LyricsSourceConfig {
    return LyricsSourceConfig(
        id = id,
        name = name,
        method = method.toRequestMethod(),
        urlTemplate = urlTemplate,
        headersTemplate = headersTemplate,
        queryTemplate = queryTemplate,
        bodyTemplate = bodyTemplate,
        responseFormat = responseFormat.toLyricsFormat(),
        extractor = extractor,
        priority = priority,
        enabled = enabled,
    )
}

private fun LyricsSourceConfig.toEntity(): LyricsSourceConfigEntity {
    return LyricsSourceConfigEntity(
        id = id,
        name = name,
        method = method.name,
        urlTemplate = urlTemplate,
        headersTemplate = headersTemplate,
        queryTemplate = queryTemplate,
        bodyTemplate = bodyTemplate,
        responseFormat = responseFormat.name,
        extractor = extractor,
        priority = priority,
        enabled = enabled,
    )
}

private fun WorkflowLyricsSourceConfigEntity.toDomainOrNull(): WorkflowLyricsSourceConfig? {
    return runCatching {
        parseWorkflowLyricsSourceConfig(rawJson).copy(
            id = id,
            name = name,
            priority = priority,
            enabled = enabled,
            rawJson = rawJson,
        )
    }.getOrNull()
}

private fun WorkflowLyricsSourceConfig.toEntity(): WorkflowLyricsSourceConfigEntity {
    validateWorkflowLyricsSourceConfig(this)
    return WorkflowLyricsSourceConfigEntity(
        id = id,
        name = name,
        priority = priority,
        enabled = enabled,
        rawJson = rawJson,
    )
}

private fun String.toImportSourceType(): ImportSourceType {
    return runCatching { ImportSourceType.valueOf(this) }.getOrDefault(ImportSourceType.LOCAL_FOLDER)
}

private fun String.toRequestMethod(): RequestMethod {
    return runCatching { RequestMethod.valueOf(this) }.getOrDefault(RequestMethod.GET)
}

private fun String.toLyricsFormat(): LyricsResponseFormat {
    return runCatching { LyricsResponseFormat.valueOf(this) }.getOrDefault(LyricsResponseFormat.JSON)
}

fun defaultLyricsSourceConfigs(): List<LyricsSourceConfig> {
    return listOf(
        LyricsSourceConfig(
            id = "lrclib-synced",
            name = "LRCLIB Synced",
            method = RequestMethod.GET,
            urlTemplate = LRCLIB_SEARCH_URL,
            queryTemplate = LRCLIB_DEFAULT_QUERY_TEMPLATE,
            responseFormat = LyricsResponseFormat.JSON,
            extractor = LRCLIB_SYNCED_JSON_MAP_EXTRACTOR,
            priority = 100,
            enabled = true,
        ),
        LyricsSourceConfig(
            id = "lrclib-plain",
            name = "LRCLIB Plain",
            method = RequestMethod.GET,
            urlTemplate = LRCLIB_SEARCH_URL,
            queryTemplate = LRCLIB_DEFAULT_QUERY_TEMPLATE,
            responseFormat = LyricsResponseFormat.JSON,
            extractor = LRCLIB_PLAIN_JSON_MAP_EXTRACTOR,
            priority = 90,
            enabled = true,
        ),
    )
}

fun sanitizeLrclibQueryTemplate(template: String): String {
    return template.split("&")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { segment ->
            segment.substringBefore("=").trim() in LRCLIB_REMOVED_QUERY_KEYS
        }
        .joinToString("&")
}

private const val LRCLIB_BASE_URL = "https://lrclib.net/"
private const val LRCLIB_SEARCH_URL = "${LRCLIB_BASE_URL}api/search"
private const val LRCLIB_DEFAULT_QUERY_TEMPLATE = "track_name={title}&artist_name={artist}"
const val LRCLIB_SYNCED_JSON_MAP_EXTRACTOR = "json-map:lyrics=syncedLyrics,title=trackName,artist=artistName,album=albumName,durationSeconds=duration,id=id"
const val LRCLIB_PLAIN_JSON_MAP_EXTRACTOR = "json-map:lyrics=plainLyrics,title=trackName,artist=artistName,album=albumName,durationSeconds=duration,id=id"
private val LRCLIB_REMOVED_QUERY_KEYS = setOf("album_name", "duration")
