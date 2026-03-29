package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.Album
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
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.AlbumEntity
import top.iwesley.lyn.music.data.db.ArtistEntity
import top.iwesley.lyn.music.data.db.ImportIndexStateEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.WorkflowLyricsSourceConfigEntity
import top.iwesley.lyn.music.domain.buildLyricsRequest
import top.iwesley.lyn.music.domain.buildWorkflowRequest
import top.iwesley.lyn.music.domain.extractWorkflowLyricsPayload
import top.iwesley.lyn.music.domain.extractWorkflowSongCandidates
import top.iwesley.lyn.music.domain.extractWorkflowStepCapture
import top.iwesley.lyn.music.domain.parseWorkflowLyricsSourceConfig
import top.iwesley.lyn.music.domain.parseCachedLyrics
import top.iwesley.lyn.music.domain.parseLyricsPayload
import top.iwesley.lyn.music.domain.parsePlainText
import top.iwesley.lyn.music.domain.parseLrc
import top.iwesley.lyn.music.domain.scoreWorkflowSongCandidate
import top.iwesley.lyn.music.domain.serializeLyricsDocument
import top.iwesley.lyn.music.domain.selectBestWorkflowSongCandidate
import top.iwesley.lyn.music.domain.validateWorkflowLyricsSourceConfig
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
    suspend fun rescanSource(sourceId: String): Result<Unit>
    suspend fun deleteSource(sourceId: String): Result<Unit>
}

interface PlaybackRepository {
    val snapshot: StateFlow<PlaybackSnapshot>

    suspend fun playTracks(tracks: List<Track>, startIndex: Int)
    suspend fun togglePlayPause()
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Float)
    suspend fun cycleMode()
    suspend fun close()
}

interface LyricsRepository {
    suspend fun getLyrics(track: Track): LyricsDocument?
    suspend fun searchLyricsCandidates(track: Track): List<LyricsSearchCandidate>
    suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate): LyricsDocument
    suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate>
    suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate): LyricsDocument
}

interface SettingsRepository {
    val lyricsSources: Flow<List<LyricsSourceDefinition>>
    val useSambaCache: StateFlow<Boolean>

    suspend fun ensureDefaults()
    suspend fun setUseSambaCache(enabled: Boolean)
    suspend fun saveLyricsSource(config: LyricsSourceConfig)
    suspend fun importWorkflowLyricsSource(rawJson: String): WorkflowLyricsSourceConfig
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
                }
            }
        }
    }

    override suspend fun deleteSource(sourceId: String): Result<Unit> {
        return runCatching {
            val source = database.importSourceDao().getById(sourceId)?.toDomain()
                ?: error("Source $sourceId does not exist.")
            source.credentialKey?.let { secureCredentialStore.remove(it) }
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
                id = trackIdFor(source.id, candidate.relativePath),
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
    private val playbackPreferencesStore: PlaybackPreferencesStore,
) : SettingsRepository {
    override val lyricsSources: Flow<List<LyricsSourceDefinition>> = combine(
        database.lyricsSourceConfigDao().observeAll(),
        database.workflowLyricsSourceConfigDao().observeAll(),
    ) { directConfigs, workflowConfigs ->
        (directConfigs.map { it.toDomain() } + workflowConfigs.mapNotNull { it.toDomainOrNull() })
            .sortedWith(compareByDescending<LyricsSourceDefinition> { it.priority }.thenBy { it.name.lowercase() })
    }
    override val useSambaCache: StateFlow<Boolean> = playbackPreferencesStore.useSambaCache

    override suspend fun ensureDefaults() {
        val existing = database.lyricsSourceConfigDao().getAll()
        if (existing.isEmpty()) {
            defaultLyricsSourceConfigs().forEach { config ->
                database.lyricsSourceConfigDao().upsert(config.toEntity())
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
        playbackPreferencesStore.setUseSambaCache(enabled)
    }

    override suspend fun saveLyricsSource(config: LyricsSourceConfig) {
        database.lyricsSourceConfigDao().upsert(config.toEntity())
    }

    override suspend fun importWorkflowLyricsSource(rawJson: String): WorkflowLyricsSourceConfig {
        val config = parseWorkflowLyricsSourceConfig(rawJson)
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
        val sanitizedQuery = sanitizeLrclibQueryTemplate(config.queryTemplate)
        return if (sanitizedQuery == config.queryTemplate) null else config.copy(queryTemplate = sanitizedQuery)
    }

    private fun LyricsSourceConfigEntity.isBuiltInLrclib(): Boolean {
        return id in BUILT_IN_LRCLIB_SOURCE_IDS && urlTemplate.startsWith(LRCLIB_BASE_URL)
    }

    private companion object {
        val BUILT_IN_LRCLIB_SOURCE_IDS = setOf("lrclib-synced", "lrclib-plain")
    }
}

class DefaultLyricsRepository(
    private val database: LynMusicDatabase,
    private val httpClient: LyricsHttpClient,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) : LyricsRepository {
    override suspend fun getLyrics(track: Track): LyricsDocument? {
        val trackLabel = track.logIdentity()
        database.lyricsCacheDao().getByTrack(track.id)
            .firstNotNullOfOrNull { cache -> parseCachedLyrics(cache.sourceId, cache.rawPayload) }
            ?.let { cached ->
                logger.debug(LYRICS_LOG_TAG) {
                    "cache-hit track=$trackLabel source=${cached.sourceId} synced=${cached.isSynced} lines=${cached.lines.size}"
                }
                return cached
            }

        val sources = enabledLyricsSources()
        if (sources.isEmpty()) {
            logger.warn(LYRICS_LOG_TAG) { "no-enabled-sources track=$trackLabel" }
            return null
        }

        for (source in sources) {
            val document = when (source) {
                is LyricsSourceConfig -> requestDirectLyricsDocument(
                    track = track,
                    config = source,
                    requestType = "auto",
                )

                is WorkflowLyricsSourceConfig -> requestWorkflowLyricsDocument(
                    track = track,
                    config = source,
                    requestType = "auto",
                )
            } ?: continue
            storeLyricsDocument(track.id, document)
            logger.info(LYRICS_LOG_TAG) {
                "resolved track=$trackLabel source=${source.id} synced=${document.isSynced} " +
                    "lines=${document.lines.size}"
            }
            return document
        }
        logger.warn(LYRICS_LOG_TAG) {
            "miss track=$trackLabel attempted=${sources.joinToString(",") { it.id }}"
        }
        return null
    }

    override suspend fun searchLyricsCandidates(track: Track): List<LyricsSearchCandidate> {
        val trackLabel = track.logIdentity()
        val configs = enabledDirectLyricsConfigs()
        if (configs.isEmpty()) {
            logger.warn(LYRICS_LOG_TAG) { "manual-no-enabled-direct-sources track=$trackLabel" }
            return emptyList()
        }
        return configs.mapNotNull { config ->
            requestDirectLyricsDocument(
                track = track,
                config = config,
                requestType = "manual",
            )?.let { document ->
                LyricsSearchCandidate(
                    sourceId = config.id,
                    sourceName = config.name,
                    document = document,
                )
            }
        }
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> {
        val trackLabel = track.logIdentity()
        val configs = enabledWorkflowLyricsConfigs()
        if (configs.isEmpty()) {
            logger.warn(LYRICS_LOG_TAG) { "manual-no-enabled-workflow-sources track=$trackLabel" }
            return emptyList()
        }
        return configs.flatMap { config ->
            searchWorkflowCandidates(track, config, requestType = "manual")
        }
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

    override suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate): LyricsDocument {
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
        logger.info(LYRICS_LOG_TAG) {
            "manual-workflow-apply track=$trackId source=${candidate.sourceId} synced=${document.isSynced} lines=${document.lines.size}"
        }
        return document
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

    private suspend fun requestDirectLyricsDocument(
        track: Track,
        config: LyricsSourceConfig,
        requestType: String,
    ): LyricsDocument? {
        val trackLabel = track.logIdentity()
        val request = buildLyricsRequest(config, track)
        logger.debug(LYRICS_LOG_TAG) {
            "$requestType-request track=$trackLabel source=${config.id} method=${request.method.name} " +
                "url=${request.url} headers=${request.headers.headerNames()} bodyLength=${request.body?.length ?: 0}"
        }
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
        ) ?: return null
        logger.debug(LYRICS_LOG_TAG) {
            "$requestType-response track=$trackLabel source=${config.id} status=${response.statusCode} " +
                "elapsedMs=${now() - startedAt} bodyPreview=${response.body.logPreview()}"
        }
        val document = parseLyricsPayload(config, response.body)
        if (document == null) {
            logger.warn(LYRICS_LOG_TAG) {
                "$requestType-parse-miss track=$trackLabel source=${config.id} status=${response.statusCode} " +
                    "extractor=${config.extractor}"
            }
        }
        return document
    }

    private suspend fun requestWorkflowLyricsDocument(
        track: Track,
        config: WorkflowLyricsSourceConfig,
        requestType: String,
    ): LyricsDocument? {
        val candidates = searchWorkflowCandidates(track, config, requestType)
        val candidate = selectBestWorkflowSongCandidate(track, candidates, config.selection)
        if (candidate == null) {
            logger.warn(LYRICS_LOG_TAG) {
                "$requestType-workflow-select-miss track=${track.logIdentity()} source=${config.id} candidates=${candidates.size}"
            }
            return null
        }
        return fetchWorkflowLyricsForCandidate(track, config, candidate, requestType)
    }

    private suspend fun searchWorkflowCandidates(
        track: Track,
        config: WorkflowLyricsSourceConfig,
        requestType: String,
    ): List<WorkflowSongCandidate> {
        val variables = workflowTrackVariables(track)
        val request = buildWorkflowRequest(config.search.request, variables)
        val trackLabel = track.logIdentity()
        logger.debug(LYRICS_LOG_TAG) {
            "$requestType-workflow-search track=$trackLabel source=${config.id} method=${request.method.name} " +
                "url=${request.url} headers=${request.headers.headerNames()} bodyLength=${request.body?.length ?: 0}"
        }
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
        val candidates = runCatching {
            extractWorkflowSongCandidates(config, response.body)
        }.getOrElse { throwable ->
            logger.error(LYRICS_LOG_TAG, throwable) {
                "$requestType-workflow-parse-failed track=$trackLabel source=${config.id} status=${response.statusCode}"
            }
            emptyList()
        }
        logger.debug(LYRICS_LOG_TAG) {
            "$requestType-workflow-search-response track=$trackLabel source=${config.id} status=${response.statusCode} " +
                "elapsedMs=${now() - startedAt} candidates=${candidates.size}"
        }
        return candidates.take(config.selection.maxCandidates.coerceAtLeast(1))
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
            logger.debug(LYRICS_LOG_TAG) {
                "$requestType-workflow-step track=$trackLabel source=${config.id} step=$index method=${request.method.name} " +
                    "url=${request.url} headers=${request.headers.headerNames()} bodyLength=${request.body?.length ?: 0}"
            }
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
        val lines = when (config.lyrics.steps.last().format) {
            LyricsResponseFormat.LRC -> parseLrc(payload)
            LyricsResponseFormat.TEXT -> parsePlainText(payload)
            else -> parseLrc(payload).ifEmpty { parsePlainText(payload) }
        }
        if (lines.isEmpty()) return null
        return LyricsDocument(
            lines = lines,
            offsetMs = 0L,
            sourceId = config.id,
            rawPayload = payload,
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

class DefaultPlaybackRepository(
    private val database: LynMusicDatabase,
    private val gateway: PlaybackGateway,
    private val scope: CoroutineScope,
) : PlaybackRepository {
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot())
    private var observedCompletionCount = 0L

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    init {
        scope.launch {
            restoreQueue()
        }
        scope.launch {
            gateway.state.collect { gatewayState ->
                val completionChanged = gatewayState.completionCount > observedCompletionCount
                observedCompletionCount = gatewayState.completionCount
                mutableSnapshot.update {
                    it.copy(
                        isPlaying = gatewayState.isPlaying,
                        positionMs = gatewayState.positionMs,
                        durationMs = gatewayState.durationMs,
                        volume = gatewayState.volume,
                        metadataTitle = gatewayState.metadataTitle,
                        metadataArtistName = gatewayState.metadataArtistName,
                        metadataAlbumTitle = gatewayState.metadataAlbumTitle,
                        errorMessage = gatewayState.errorMessage,
                    )
                }
                if (completionChanged) {
                    advance(autoTriggered = true)
                }
            }
        }
    }

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        mutableSnapshot.value = PlaybackSnapshot(
            queue = tracks,
            currentIndex = index,
            mode = mutableSnapshot.value.mode,
            isPlaying = true,
            positionMs = 0L,
            durationMs = tracks[index].durationMs,
            volume = mutableSnapshot.value.volume,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
        )
        gateway.load(tracks[index], playWhenReady = true, startPositionMs = 0L)
        persistSnapshot()
    }

    override suspend fun togglePlayPause() {
        if (mutableSnapshot.value.currentTrack == null) return
        if (mutableSnapshot.value.isPlaying) {
            gateway.pause()
        } else {
            gateway.play()
        }
    }

    override suspend fun skipNext() {
        advance(autoTriggered = false)
    }

    override suspend fun skipPrevious() {
        val snapshot = mutableSnapshot.value
        if (snapshot.queue.isEmpty()) return
        if (snapshot.positionMs > 5_000) {
            gateway.seekTo(0L)
            mutableSnapshot.update { it.copy(positionMs = 0L) }
            persistSnapshot()
            return
        }
        val previousIndex = when {
            snapshot.mode == PlaybackMode.SHUFFLE && snapshot.queue.size > 1 -> randomIndex(snapshot.queue.lastIndex, snapshot.currentIndex)
            snapshot.currentIndex > 0 -> snapshot.currentIndex - 1
            else -> 0
        }
        loadIndex(previousIndex, playWhenReady = true)
    }

    override suspend fun seekTo(positionMs: Long) {
        gateway.seekTo(positionMs)
        mutableSnapshot.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
        persistSnapshot()
    }

    override suspend fun setVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        gateway.setVolume(normalized)
        mutableSnapshot.update { it.copy(volume = normalized) }
    }

    override suspend fun cycleMode() {
        val nextMode = when (mutableSnapshot.value.mode) {
            PlaybackMode.ORDER -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.ORDER
        }
        mutableSnapshot.update { it.copy(mode = nextMode) }
        persistSnapshot()
    }

    override suspend fun close() {
        gateway.release()
    }

    private suspend fun advance(autoTriggered: Boolean) {
        val snapshot = mutableSnapshot.value
        if (snapshot.queue.isEmpty()) return
        val nextIndex = when (snapshot.mode) {
            PlaybackMode.REPEAT_ONE -> snapshot.currentIndex
            PlaybackMode.SHUFFLE -> randomIndex(snapshot.queue.lastIndex, snapshot.currentIndex)
            PlaybackMode.ORDER -> {
                if (snapshot.currentIndex + 1 <= snapshot.queue.lastIndex) {
                    snapshot.currentIndex + 1
                } else if (autoTriggered) {
                    gateway.pause()
                    mutableSnapshot.update { it.copy(isPlaying = false, positionMs = 0L) }
                    persistSnapshot()
                    return
                } else {
                    0
                }
            }
        }
        loadIndex(nextIndex, playWhenReady = true)
    }

    private suspend fun loadIndex(index: Int, playWhenReady: Boolean) {
        val queue = mutableSnapshot.value.queue
        val target = queue.getOrNull(index) ?: return
        mutableSnapshot.update {
            it.copy(
                currentIndex = index,
                isPlaying = playWhenReady,
                positionMs = 0L,
                durationMs = target.durationMs,
                metadataTitle = null,
                metadataArtistName = null,
                metadataAlbumTitle = null,
                errorMessage = null,
            )
        }
        gateway.load(target, playWhenReady = playWhenReady, startPositionMs = 0L)
        persistSnapshot()
    }

    private suspend fun restoreQueue() {
        val persisted = database.playbackQueueSnapshotDao().get() ?: return
        val ids = persisted.queueTrackIds.split(',').filter { it.isNotBlank() }
        if (ids.isEmpty()) return
        val tracks = database.trackDao().getByIds(ids)
            .associateBy { it.id }
            .let { indexed -> ids.mapNotNull { indexed[it]?.toDomain() } }
        if (tracks.isEmpty()) return
        val index = persisted.currentIndex.coerceIn(0, tracks.lastIndex)
        val mode = persisted.mode.toPlaybackMode()
        mutableSnapshot.value = PlaybackSnapshot(
            queue = tracks,
            currentIndex = index,
            mode = mode,
            isPlaying = false,
            positionMs = persisted.positionMs,
            durationMs = tracks[index].durationMs,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
        )
        gateway.load(tracks[index], playWhenReady = false, startPositionMs = persisted.positionMs)
    }

    private suspend fun persistSnapshot() {
        val snapshot = mutableSnapshot.value
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = snapshot.queue.joinToString(",") { it.id },
                currentIndex = snapshot.currentIndex,
                positionMs = snapshot.positionMs,
                mode = snapshot.mode.name,
                updatedAt = now(),
            ),
        )
    }

    private fun randomIndex(lastIndex: Int, currentIndex: Int): Int {
        if (lastIndex <= 0) return currentIndex.coerceAtLeast(0)
        var next = currentIndex
        while (next == currentIndex) {
            next = Random.nextInt(0, lastIndex + 1)
        }
        return next
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

private fun trackIdFor(sourceId: String, relativePath: String): String {
    return "track:${sourceId}:${relativePath.lowercase()}"
}

private fun trackIdPrefix(sourceId: String): String = "track:${sourceId}:%"

private fun Track.logIdentity(): String {
    val artist = artistName?.takeIf { it.isNotBlank() } ?: "Unknown"
    return "\"$title\" by $artist (#$id)"
}

private fun Map<String, String>.headerNames(): String {
    return keys.sorted().joinToString(",").ifBlank { "-" }
}

private fun String.logPreview(limit: Int = 240): String {
    val sanitized = replace("\n", "\\n")
    return if (sanitized.length <= limit) sanitized else sanitized.take(limit) + "..."
}

private fun TrackEntity.toDomain(): Track {
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

private fun String.toPlaybackMode(): PlaybackMode {
    return runCatching { PlaybackMode.valueOf(this) }.getOrDefault(PlaybackMode.ORDER)
}

fun defaultLyricsSourceConfigs(): List<LyricsSourceConfig> {
    return listOf(
        LyricsSourceConfig(
            id = "lrclib-synced",
            name = "LRCLIB Synced",
            method = RequestMethod.GET,
            urlTemplate = LRCLIB_GET_URL,
            queryTemplate = LRCLIB_DEFAULT_QUERY_TEMPLATE,
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json:syncedLyrics",
            priority = 100,
            enabled = true,
        ),
        LyricsSourceConfig(
            id = "lrclib-plain",
            name = "LRCLIB Plain",
            method = RequestMethod.GET,
            urlTemplate = LRCLIB_GET_URL,
            queryTemplate = LRCLIB_DEFAULT_QUERY_TEMPLATE,
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json:plainLyrics",
            priority = 90,
            enabled = true,
        ),
    )
}

internal fun sanitizeLrclibQueryTemplate(template: String): String {
    return template.split("&")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { segment ->
            segment.substringBefore("=").trim() in LRCLIB_REMOVED_QUERY_KEYS
        }
        .joinToString("&")
}

private const val LRCLIB_BASE_URL = "https://lrclib.net/"
private const val LRCLIB_GET_URL = "${LRCLIB_BASE_URL}api/get"
private const val LRCLIB_DEFAULT_QUERY_TEMPLATE = "track_name={title}&artist_name={artist}"
private val LRCLIB_REMOVED_QUERY_KEYS = setOf("album_name", "duration")
