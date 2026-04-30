package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.AutoPlayOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.AppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportIndexState
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagGateway
import top.iwesley.lyn.music.core.model.UnsupportedAppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedAutoPlayOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedCompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedNavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedSameNameLyricsFileGateway
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
import top.iwesley.lyn.music.domain.buildPresetOiapiQqMusicWorkflowJson
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
import top.iwesley.lyn.music.domain.DEFAULT_DIRECT_LYRICS_SELECTION
import top.iwesley.lyn.music.domain.AUTO_DIRECT_LYRICS_SYNCED_BONUS
import top.iwesley.lyn.music.domain.rankDirectLyricsCandidates
import top.iwesley.lyn.music.domain.rankWorkflowSongCandidates
import top.iwesley.lyn.music.domain.rewriteWorkflowLyricsSourceEnabled
import top.iwesley.lyn.music.domain.scoreDirectLyricsCandidate
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
    suspend fun importLocalFolder(): Result<ImportScanSummary?>
    suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit>
    suspend fun testUpdatedSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<Unit>
    suspend fun addSambaSource(draft: SambaSourceDraft): Result<ImportScanSummary>
    suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<ImportScanSummary>
    suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit>
    suspend fun testUpdatedWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<Unit>
    suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<ImportScanSummary>
    suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<ImportScanSummary>
    suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit>
    suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<Unit>
    suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary>
    suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<ImportScanSummary>
    suspend fun rescanSource(sourceId: String): Result<ImportScanSummary?>
    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): Result<Unit>
    suspend fun deleteSource(sourceId: String): Result<Unit>
}

interface PlaylistRepository {
    val playlists: Flow<List<PlaylistSummary>>

    fun observePlaylistDetail(playlistId: String): Flow<PlaylistDetail?>
    suspend fun createPlaylist(name: String): Result<PlaylistSummary>
    suspend fun deletePlaylist(playlistId: String): Result<Unit>
    suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit>
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit>
    suspend fun refreshNavidromePlaylists(): Result<Unit>
}

interface LyricsRepository {
    suspend fun getLyrics(track: Track): ResolvedLyricsResult?
    suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean = true): List<LyricsSearchCandidate>
    suspend fun applyLyricsCandidate(
        trackId: String,
        candidate: LyricsSearchCandidate,
        mode: LyricsSearchApplyMode = LyricsSearchApplyMode.FULL,
    ): AppliedLyricsResult
    suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate>
    suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult
    suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode = LyricsSearchApplyMode.FULL,
    ): AppliedLyricsResult
}

data class ResolvedLyricsResult(
    val document: LyricsDocument,
    val artworkLocator: String? = null,
)

data class AppliedLyricsResult(
    val document: LyricsDocument? = null,
    val artworkLocator: String? = null,
)

interface SettingsRepository {
    val lyricsSources: Flow<List<LyricsSourceDefinition>>
    val useSambaCache: StateFlow<Boolean>
    val showCompactPlayerLyrics: StateFlow<Boolean>
    val autoPlayOnStartup: StateFlow<Boolean>
    val appDisplayScalePreset: StateFlow<AppDisplayScalePreset>
    val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality>
    val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality>
    val selectedTheme: StateFlow<AppThemeId>
    val customThemeTokens: StateFlow<AppThemeTokens>
    val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences>
    val desktopVlcAutoDetectedPath: StateFlow<String?>
    val desktopVlcManualPath: StateFlow<String?>
    val desktopVlcEffectivePath: StateFlow<String?>

    suspend fun ensureDefaults()
    suspend fun setUseSambaCache(enabled: Boolean)
    suspend fun setShowCompactPlayerLyrics(enabled: Boolean)
    suspend fun setAutoPlayOnStartup(enabled: Boolean)
    suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset)
    suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality)
    suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality)
    suspend fun setSelectedTheme(themeId: AppThemeId)
    suspend fun setCustomThemeTokens(tokens: AppThemeTokens)
    suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette)
    suspend fun setDesktopVlcManualPath(path: String)
    suspend fun clearDesktopVlcManualPath()
    suspend fun saveLyricsSource(config: LyricsSourceConfig)
    suspend fun saveWorkflowLyricsSource(rawJson: String, editingId: String? = null): WorkflowLyricsSourceConfig
    suspend fun setLyricsSourceEnabled(sourceId: String, enabled: Boolean)
    suspend fun deleteLyricsSource(configId: String)
}

class RoomLibraryRepository(
    private val database: LynMusicDatabase,
) : LibraryRepository {
    override val tracks: Flow<List<Track>> = combine(
        database.trackDao().observeAll(),
        database.importSourceDao().observeAll(),
        database.lyricsCacheDao().observeArtworkLocators(),
    ) { entities, sources, artworkRows ->
        val enabledSourceIds = sources.asSequence()
            .filter { it.enabled }
            .map { it.id }
            .toSet()
        val artworkOverrides = effectiveArtworkOverridesByTrackId(artworkRows)
        entities
            .filter { it.sourceId in enabledSourceIds }
            .map { entity -> entity.toDomain(artworkOverrides[entity.id]) }
    }

    override val artists: Flow<List<Artist>> = database.artistDao()
        .observeAll()
        .map { entities -> entities.map { Artist(id = it.id, name = it.name, trackCount = it.trackCount) } }

    override val albums: Flow<List<Album>> = database.albumDao()
        .observeAll()
        .map { entities -> entities.map { Album(id = it.id, title = it.title, artistName = it.artistName, trackCount = it.trackCount) } }

    override suspend fun getTracksByIds(trackIds: List<String>): List<Track> {
        if (trackIds.isEmpty()) return emptyList()
        val items = database.trackDao().getByIds(trackIds)
        val artworkOverrides = effectiveArtworkOverridesByTrackId(database.lyricsCacheDao().getArtworkLocatorsByTrackIds(trackIds))
        val byId = items.associateBy { it.id }
        return trackIds.mapNotNull { trackId -> byId[trackId]?.toDomain(artworkOverrides[trackId]) }
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

    override suspend fun importLocalFolder(): Result<ImportScanSummary?> {
        return runCatching {
            val selection = gateway.pickLocalFolder() ?: return@runCatching null
            validateImportSourceCreation(
                label = selection.label,
                localFolderRootReference = selection.persistentReference,
            )
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

    override suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit> {
        return runCatching {
            val preparedDraft = prepareSambaDraft(draft)
            gateway.testSamba(preparedDraft)
        }
    }

    override suspend fun testUpdatedSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.SAMBA)
            val preparedDraft = prepareSambaDraft(draft)
            val password = resolveUpdatedPassword(
                existingCredentialKey = existing.credentialKey,
                password = preparedDraft.password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            gateway.testSamba(preparedDraft.copy(password = password))
        }
    }

    override suspend fun addSambaSource(draft: SambaSourceDraft): Result<ImportScanSummary> {
        return runCatching {
            val sourceId = newId("smb")
            val preparedDraft = prepareSambaDraft(draft)
            val newSource = createSambaSource(sourceId, preparedDraft).copy(
                credentialKey = credentialKeyForNewSource(preparedDraft.password, sourceId),
            )
            validateImportSourceCreation(label = newSource.label)
            newSource.credentialKey?.let { secureCredentialStore.put(it, preparedDraft.password) }
            database.importSourceDao().upsert(newSource.toEntity())
            runScan(newSource) {
                gateway.scanSamba(preparedDraft, sourceId)
            }
        }
    }

    override suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.SAMBA)
            val preparedDraft = prepareSambaDraft(draft)
            val updatedSource = createSambaSource(
                sourceId = existing.id,
                draft = preparedDraft,
                createdAt = existing.createdAt,
                enabled = existing.enabled,
            )
            assertUniqueImportSourceLabel(updatedSource.label, excludingSourceId = existing.id)
            val password = resolveUpdatedPassword(
                existingCredentialKey = existing.credentialKey,
                password = preparedDraft.password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            val report = gateway.scanSamba(preparedDraft.copy(password = password), sourceId)
            val credentialKey = resolveUpdatedCredentialKey(
                sourceId = sourceId,
                existingCredentialKey = existing.credentialKey,
                password = password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            persistUpdatedCredential(existing.credentialKey, credentialKey, password)
            persistScan(updatedSource.copy(credentialKey = credentialKey), report)
        }
    }

    override suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit> {
        return runCatching {
            gateway.testWebDav(prepareWebDavDraft(draft))
        }
    }

    override suspend fun testUpdatedWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.WEBDAV)
            val preparedDraft = prepareWebDavDraft(draft)
            val password = resolveUpdatedPassword(
                existingCredentialKey = existing.credentialKey,
                password = preparedDraft.password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            gateway.testWebDav(preparedDraft.copy(password = password))
        }
    }

    override suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<ImportScanSummary> {
        return runCatching {
            val sourceId = newId("dav")
            val preparedDraft = prepareWebDavDraft(draft)
            val source = createWebDavSource(sourceId, preparedDraft).copy(
                credentialKey = credentialKeyForNewSource(preparedDraft.password, sourceId),
            )
            validateImportSourceCreation(label = source.label)
            source.credentialKey?.let { secureCredentialStore.put(it, preparedDraft.password) }
            database.importSourceDao().upsert(source.toEntity())
            runScan(source) {
                gateway.scanWebDav(preparedDraft, sourceId)
            }
        }
    }

    override suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.WEBDAV)
            val preparedDraft = prepareWebDavDraft(draft)
            val updatedSource = createWebDavSource(
                sourceId = existing.id,
                draft = preparedDraft,
                createdAt = existing.createdAt,
                enabled = existing.enabled,
            )
            assertUniqueImportSourceLabel(updatedSource.label, excludingSourceId = existing.id)
            val password = resolveUpdatedPassword(
                existingCredentialKey = existing.credentialKey,
                password = preparedDraft.password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            val report = gateway.scanWebDav(preparedDraft.copy(password = password), sourceId)
            val credentialKey = resolveUpdatedCredentialKey(
                sourceId = sourceId,
                existingCredentialKey = existing.credentialKey,
                password = password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            persistUpdatedCredential(existing.credentialKey, credentialKey, password)
            persistScan(updatedSource.copy(credentialKey = credentialKey), report)
        }
    }

    override suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> {
        return runCatching {
            gateway.testNavidrome(prepareNavidromeDraft(draft))
        }
    }

    override suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.NAVIDROME)
            val preparedDraft = prepareNavidromeDraft(draft)
            val password = resolveUpdatedPassword(
                existingCredentialKey = existing.credentialKey,
                password = preparedDraft.password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            if (password.isBlank()) {
                error("Navidrome 来源缺少有效密码。")
            }
            gateway.testNavidrome(preparedDraft.copy(password = password))
        }
    }

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary> {
        return runCatching {
            val sourceId = newId("navidrome")
            val preparedDraft = prepareNavidromeDraft(draft)
            val source = createNavidromeSource(sourceId, preparedDraft).copy(credentialKey = "credential-$sourceId")
            validateImportSourceCreation(label = source.label)
            source.credentialKey?.let { secureCredentialStore.put(it, preparedDraft.password) }
            database.importSourceDao().upsert(source.toEntity())
            runScan(source) {
                gateway.scanNavidrome(preparedDraft, sourceId)
            }
        }
    }

    override suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.NAVIDROME)
            val preparedDraft = prepareNavidromeDraft(draft)
            val updatedSource = createNavidromeSource(
                sourceId = existing.id,
                draft = preparedDraft,
                createdAt = existing.createdAt,
                enabled = existing.enabled,
            )
            assertUniqueImportSourceLabel(updatedSource.label, excludingSourceId = existing.id)
            val password = resolveUpdatedPassword(
                existingCredentialKey = existing.credentialKey,
                password = preparedDraft.password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            if (password.isBlank()) {
                error("Navidrome 来源缺少有效密码。")
            }
            val report = gateway.scanNavidrome(preparedDraft.copy(password = password), sourceId)
            val credentialKey = resolveUpdatedCredentialKey(
                sourceId = sourceId,
                existingCredentialKey = existing.credentialKey,
                password = password,
                keepExistingCredentialWhenBlankPassword = true,
            )
            persistUpdatedCredential(existing.credentialKey, credentialKey, password)
            persistScan(updatedSource.copy(credentialKey = credentialKey), report)
        }
    }

    override suspend fun rescanSource(sourceId: String): Result<ImportScanSummary?> {
        return runCatching {
            val entity = database.importSourceDao().getById(sourceId)
                ?: error("Source $sourceId does not exist.")
            val source = entity.toDomain()
            if (!source.enabled) {
                error("来源已禁用，请先启用。")
            }
            val summary = runScan(source.copy(lastScannedAt = now())) {
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
            summary
        }
    }

    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): Result<Unit> {
        return runCatching {
            val source = database.importSourceDao().getById(sourceId)?.toDomain()
                ?: error("Source $sourceId does not exist.")
            database.importSourceDao().upsert(source.copy(enabled = enabled).toEntity())
            rebuildLibrarySummaries()
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

    private fun prepareSambaDraft(draft: SambaSourceDraft): SambaSourceDraft {
        val normalizedPath = normalizeSambaPath(draft.path)
        return draft.copy(
            label = draft.label.trim(),
            server = draft.server.trim(),
            path = normalizedPath,
            username = draft.username.trim(),
        )
    }

    private fun prepareWebDavDraft(draft: WebDavSourceDraft): WebDavSourceDraft {
        return draft.copy(
            label = draft.label.trim(),
            rootUrl = normalizeWebDavRootUrl(draft.rootUrl),
            username = draft.username.trim(),
        )
    }

    private fun prepareNavidromeDraft(draft: NavidromeSourceDraft): NavidromeSourceDraft {
        return draft.copy(
            label = draft.label.trim(),
            baseUrl = normalizeNavidromeBaseUrl(draft.baseUrl),
            username = draft.username.trim(),
        )
    }

    private fun createSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        createdAt: Long = now(),
        enabled: Boolean = true,
    ): ImportSource {
        val label = draft.label.ifBlank {
            formatSambaEndpoint(
                server = draft.server,
                port = draft.port,
                path = draft.path,
            )
        }
        return ImportSource(
            id = sourceId,
            type = ImportSourceType.SAMBA,
            label = label,
            rootReference = draft.path,
            server = draft.server,
            port = draft.port,
            path = draft.path,
            username = draft.username,
            createdAt = createdAt,
            enabled = enabled,
        )
    }

    private fun createWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        createdAt: Long = now(),
        enabled: Boolean = true,
    ): ImportSource {
        val label = draft.label.ifBlank { draft.rootUrl }
        return ImportSource(
            id = sourceId,
            type = ImportSourceType.WEBDAV,
            label = label,
            rootReference = draft.rootUrl,
            username = draft.username,
            allowInsecureTls = draft.allowInsecureTls,
            createdAt = createdAt,
            enabled = enabled,
        )
    }

    private fun createNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        createdAt: Long = now(),
        enabled: Boolean = true,
    ): ImportSource {
        val label = draft.label.ifBlank { draft.baseUrl }
        return ImportSource(
            id = sourceId,
            type = ImportSourceType.NAVIDROME,
            label = label,
            rootReference = draft.baseUrl,
            username = draft.username,
            createdAt = createdAt,
            enabled = enabled,
        )
    }

    private fun credentialKeyForNewSource(password: String, sourceId: String): String? {
        return if (password.isBlank()) null else "credential-$sourceId"
    }

    private suspend fun requireRemoteSource(sourceId: String, type: ImportSourceType): ImportSource {
        val source = database.importSourceDao().getById(sourceId)?.toDomain()
            ?: error("Source $sourceId does not exist.")
        require(source.type == type) { "仅支持编辑 ${type.name} 来源。" }
        return source
    }

    private suspend fun assertUniqueImportSourceLabel(
        label: String,
        excludingSourceId: String? = null,
    ) {
        val existing = database.importSourceDao().getAll()
            .filterNot { it.id == excludingSourceId }
        if (hasImportSourceNameConflict(name = label, existing = existing)) {
            error("音乐源名称已存在。")
        }
    }

    private suspend fun resolveUpdatedCredentialKey(
        sourceId: String,
        existingCredentialKey: String?,
        password: String,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): String? {
        if (password.isNotBlank()) {
            return existingCredentialKey ?: "credential-$sourceId"
        }
        return if (keepExistingCredentialWhenBlankPassword) existingCredentialKey else null
    }

    private suspend fun resolveUpdatedPassword(
        existingCredentialKey: String?,
        password: String,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): String {
        if (password.isNotBlank()) return password
        return if (keepExistingCredentialWhenBlankPassword) {
            existingCredentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        } else {
            ""
        }
    }

    private suspend fun persistUpdatedCredential(
        previousCredentialKey: String?,
        nextCredentialKey: String?,
        password: String,
    ) {
        when {
            nextCredentialKey == null && previousCredentialKey != null -> secureCredentialStore.remove(previousCredentialKey)
            nextCredentialKey != null && password.isNotBlank() -> secureCredentialStore.put(nextCredentialKey, password)
        }
    }

    private suspend fun persistScan(source: ImportSource, report: ImportScanReport): ImportScanSummary {
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
                bitDepth = candidate.bitDepth,
                samplingRate = candidate.samplingRate,
                bitRate = candidate.bitRate,
                channelCount = candidate.channelCount,
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
        return ImportScanSummary(
            sourceId = source.id,
            discoveredAudioFileCount = report.discoveredAudioFileCount,
            importedTrackCount = trackEntities.size,
            failures = report.failures,
        )
    }

    private suspend fun runScan(
        source: ImportSource,
        scan: suspend () -> ImportScanReport,
    ): ImportScanSummary {
        try {
            return persistScan(source, scan())
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

    private suspend fun validateImportSourceCreation(
        label: String,
        localFolderRootReference: String? = null,
    ) {
        val existing = database.importSourceDao().getAll()
        if (hasImportSourceNameConflict(name = label, existing = existing)) {
            error("音乐源名称已存在。")
        }
        if (localFolderRootReference != null && hasLocalFolderPathConflict(rootReference = localFolderRootReference, existing = existing)) {
            error("该本地文件夹已导入。")
        }
    }

    private suspend fun rebuildLibrarySummaries() {
        val enabledSourceIds = database.importSourceDao().getAll()
            .asSequence()
            .filter { it.enabled }
            .map { it.id }
            .toSet()
        val tracks = database.trackDao().getAll()
            .filter { it.sourceId in enabledSourceIds }
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
    private val themePreferencesStore: ThemePreferencesStore,
    private val desktopVlcPreferencesStore: DesktopVlcPreferencesStore,
    private val appDisplayPreferencesStore: AppDisplayPreferencesStore = UnsupportedAppDisplayPreferencesStore,
    private val compactPlayerLyricsPreferencesStore: CompactPlayerLyricsPreferencesStore =
        UnsupportedCompactPlayerLyricsPreferencesStore,
    private val autoPlayOnStartupPreferencesStore: AutoPlayOnStartupPreferencesStore =
        UnsupportedAutoPlayOnStartupPreferencesStore,
    private val navidromeAudioQualityPreferencesStore: NavidromeAudioQualityPreferencesStore =
        UnsupportedNavidromeAudioQualityPreferencesStore,
) : SettingsRepository {
    override val lyricsSources: Flow<List<LyricsSourceDefinition>> = combine(
        database.lyricsSourceConfigDao().observeAll(),
        database.workflowLyricsSourceConfigDao().observeAll(),
    ) { directConfigs, workflowConfigs ->
        (directConfigs.map { it.toDomain() } + workflowConfigs.mapNotNull { it.toDomainOrNull() })
            .sortedWith(compareByDescending<LyricsSourceDefinition> { it.priority }.thenBy { it.name.lowercase() })
    }
    override val useSambaCache: StateFlow<Boolean> = sambaCachePreferencesStore.useSambaCache
    override val showCompactPlayerLyrics: StateFlow<Boolean> =
        compactPlayerLyricsPreferencesStore.showCompactPlayerLyrics
    override val autoPlayOnStartup: StateFlow<Boolean> =
        autoPlayOnStartupPreferencesStore.autoPlayOnStartup
    override val appDisplayScalePreset: StateFlow<AppDisplayScalePreset> =
        appDisplayPreferencesStore.appDisplayScalePreset
    override val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality> =
        navidromeAudioQualityPreferencesStore.navidromeWifiAudioQuality
    override val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality> =
        navidromeAudioQualityPreferencesStore.navidromeMobileAudioQuality
    override val selectedTheme: StateFlow<AppThemeId> = themePreferencesStore.selectedTheme
    override val customThemeTokens: StateFlow<AppThemeTokens> = themePreferencesStore.customThemeTokens
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = themePreferencesStore.textPalettePreferences
    override val desktopVlcAutoDetectedPath: StateFlow<String?> = desktopVlcPreferencesStore.desktopVlcAutoDetectedPath
    override val desktopVlcManualPath: StateFlow<String?> = desktopVlcPreferencesStore.desktopVlcManualPath
    override val desktopVlcEffectivePath: StateFlow<String?> = desktopVlcPreferencesStore.desktopVlcEffectivePath

    override suspend fun ensureDefaults() {
        val existing = database.lyricsSourceConfigDao().getAll()
        if (existing.isEmpty()) {
            seedDefaultDirectLyricsSources()
        } else {
            migrateBuiltInLrclibConfig(existing)?.let { migrated ->
                database.lyricsSourceConfigDao().upsert(migrated)
                LEGACY_BUILT_IN_LRCLIB_SOURCE_IDS.forEach { legacyId ->
                    database.lyricsSourceConfigDao().delete(legacyId)
                }
            }

            database.lyricsSourceConfigDao().getAll()
                .mapNotNull(::sanitizeBuiltInLrclibConfig)
                .forEach { config ->
                    database.lyricsSourceConfigDao().upsert(config)
                }
        }
        seedDefaultWorkflowLyricsSources()
    }

    override suspend fun setUseSambaCache(enabled: Boolean) {
        sambaCachePreferencesStore.setUseSambaCache(enabled)
    }

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        compactPlayerLyricsPreferencesStore.setShowCompactPlayerLyrics(enabled)
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        autoPlayOnStartupPreferencesStore.setAutoPlayOnStartup(enabled)
    }

    override suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset) {
        appDisplayPreferencesStore.setAppDisplayScalePreset(preset)
    }

    override suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality) {
        navidromeAudioQualityPreferencesStore.setNavidromeWifiAudioQuality(quality)
    }

    override suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality) {
        navidromeAudioQualityPreferencesStore.setNavidromeMobileAudioQuality(quality)
    }

    override suspend fun setSelectedTheme(themeId: AppThemeId) {
        themePreferencesStore.setSelectedTheme(themeId)
    }

    override suspend fun setCustomThemeTokens(tokens: AppThemeTokens) {
        themePreferencesStore.setCustomThemeTokens(tokens)
    }

    override suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette) {
        themePreferencesStore.setTextPalette(themeId, palette)
    }

    override suspend fun setDesktopVlcManualPath(path: String) {
        desktopVlcPreferencesStore.setDesktopVlcManualPath(path)
    }

    override suspend fun clearDesktopVlcManualPath() {
        desktopVlcPreferencesStore.setDesktopVlcManualPath(null)
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
            database.workflowLyricsSourceConfigDao().upsert(
                workflow.copy(
                    enabled = enabled,
                    rawJson = rewriteWorkflowLyricsSourceEnabled(workflow.rawJson, enabled),
                ),
            )
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
            LRCLIB_SOURCE_ID,
            "lrclib-synced",
            "lrclib-plain",
            -> LRCLIB_JSON_MAP_EXTRACTOR
            else -> extractor
        }
    }

    private fun migrateBuiltInLrclibConfig(
        existing: List<LyricsSourceConfigEntity>,
    ): LyricsSourceConfigEntity? {
        val legacyConfigs = existing.filter { entity ->
            entity.id in LEGACY_BUILT_IN_LRCLIB_SOURCE_IDS && entity.urlTemplate.startsWith(LRCLIB_BASE_URL)
        }
        if (legacyConfigs.isEmpty()) return null
        val currentConfig = existing.firstOrNull { entity ->
            entity.id == LRCLIB_SOURCE_ID && entity.urlTemplate.startsWith(LRCLIB_BASE_URL)
        }
        val seed = currentConfig ?: legacyConfigs.maxByOrNull { it.priority } ?: return null
        return seed.copy(
            id = LRCLIB_SOURCE_ID,
            name = LRCLIB_SOURCE_NAME,
            urlTemplate = LRCLIB_SEARCH_URL,
            queryTemplate = sanitizeLrclibQueryTemplate(seed.queryTemplate),
            extractor = LRCLIB_JSON_MAP_EXTRACTOR,
            priority = maxOf(seed.priority, defaultLyricsSourceConfigs().first().priority),
            enabled = (listOfNotNull(currentConfig) + legacyConfigs).any { it.enabled },
        )
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

    private suspend fun seedDefaultDirectLyricsSources() {
        val workflowConfigs = database.workflowLyricsSourceConfigDao().getAll()
        val reservedIds = workflowConfigs.mapTo(mutableSetOf()) { it.id }
        val reservedNames = workflowConfigs.mapTo(mutableSetOf()) { normalizeLyricsSourceName(it.name) }
        defaultLyricsSourceConfigs().forEach { config ->
            val normalizedName = normalizeLyricsSourceName(config.name)
            if (config.id !in reservedIds && normalizedName !in reservedNames) {
                database.lyricsSourceConfigDao().upsert(config.toEntity())
                reservedIds += config.id
                reservedNames += normalizedName
            }
        }
    }

    private suspend fun seedDefaultWorkflowLyricsSources() {
        val directConfigs = database.lyricsSourceConfigDao().getAll()
        val reservedIds = directConfigs.mapTo(mutableSetOf()) { it.id }
        val reservedNames = directConfigs.mapTo(mutableSetOf()) { normalizeLyricsSourceName(it.name) }
        database.workflowLyricsSourceConfigDao().getAll().forEach { entity ->
            reservedIds += entity.id
            reservedNames += normalizeLyricsSourceName(entity.name)
        }
        defaultWorkflowLyricsSourceConfigs().forEach { config ->
            val normalizedName = normalizeLyricsSourceName(config.name)
            if (config.id in reservedIds || normalizedName in reservedNames) {
                return@forEach
            }
            database.workflowLyricsSourceConfigDao().upsert(config.toEntity())
            reservedIds += config.id
            reservedNames += normalizedName
        }
    }

    private companion object {
        val BUILT_IN_LRCLIB_SOURCE_IDS = setOf(LRCLIB_SOURCE_ID) + LEGACY_BUILT_IN_LRCLIB_SOURCE_IDS
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
    private val sameNameLyricsFileGateway: SameNameLyricsFileGateway = UnsupportedSameNameLyricsFileGateway,
    private val artworkCacheStore: ArtworkCacheStore = object : ArtworkCacheStore {
        override suspend fun cache(locator: String, cacheKey: String): String? = locator
    },
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) : LyricsRepository {
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? {
        val trackLabel = track.logIdentity()
        val cachedRows = database.lyricsCacheDao().getByTrack(track.id)
        val manualOverride = cachedRows.firstOrNull { it.sourceId == MANUAL_LYRICS_OVERRIDE_SOURCE_ID }
        val manualArtworkOverride = normalizeArtworkLocator(manualOverride?.artworkLocator)
        manualOverride
            ?.let(::resolveCachedLyrics)
            ?.let { resolved ->
                logCacheHit(trackLabel, resolved.document)
                return resolved.withArtworkOverride(manualArtworkOverride)
            }
        if (parseNavidromeSongLocator(track.mediaLocator) != null) {
            cachedRows
                .firstOrNull { it.sourceId == NAVIDROME_LYRICS_SOURCE_ID }
                ?.let(::resolveCachedLyrics)
                ?.let { resolved ->
                    logCacheHit(trackLabel, resolved.document)
                    return resolved.withArtworkOverride(manualArtworkOverride)
                }
            requestNavidromeLyricsDocument(track)?.let { navidromeLyrics ->
                storeLyricsDocument(track.id, navidromeLyrics)
                logger.info(LYRICS_LOG_TAG) {
                    "resolved track=$trackLabel source=${navidromeLyrics.sourceId} synced=${navidromeLyrics.isSynced} lines=${navidromeLyrics.lines.size}"
                }
                return ResolvedLyricsResult(document = navidromeLyrics)
                    .withArtworkOverride(manualArtworkOverride)
            }
            cachedRows
                .firstNotNullOfOrNull { cache ->
                    cache.takeUnless { it.sourceId == NAVIDROME_LYRICS_SOURCE_ID }
                        ?.let(::resolveCachedLyrics)
                }
                ?.let { resolved ->
                    logCacheHit(trackLabel, resolved.document)
                    return resolved.withArtworkOverride(manualArtworkOverride)
                }
        } else {
            resolveSameNameLyricsForPlayback(track, cachedRows)?.let { resolved ->
                return resolved.withArtworkOverride(manualArtworkOverride)
            }
            cachedRows
                .firstNotNullOfOrNull { cache ->
                    cache.takeUnless { it.sourceId == SAME_NAME_LRC_SOURCE_ID }
                        ?.let(::resolveCachedLyrics)
                }
                ?.let { resolved ->
                    logCacheHit(trackLabel, resolved.document)
                    return resolved.withArtworkOverride(manualArtworkOverride)
                }
            readEmbeddedTrackLyrics(track).document?.let { embeddedLyrics ->
                storeLyricsDocument(
                    track.id,
                    embeddedLyrics,
                    cacheSourceId = EMBEDDED_LYRICS_SOURCE_ID,
                )
                logger.info(LYRICS_LOG_TAG) {
                    "resolved track=$trackLabel source=${embeddedLyrics.sourceId} synced=${embeddedLyrics.isSynced} " +
                        "lines=${embeddedLyrics.lines.size}"
                }
                return ResolvedLyricsResult(document = embeddedLyrics)
                    .withArtworkOverride(manualArtworkOverride)
            }
        }

        val sources = enabledLyricsSources()
        if (sources.isEmpty()) {
            logger.warn(LYRICS_LOG_TAG) { "no-enabled-sources track=$trackLabel" }
            return null
        }

        for (source in sources) {
            val sourceResult = when (source) {
                is LyricsSourceConfig -> {
                    val rankedCandidates = rankDirectLyricsCandidates(
                        track = track,
                        candidates = requestDirectLyricsResults(
                            track = track,
                            config = source,
                            requestType = "auto",
                        ),
                        selection = DEFAULT_DIRECT_LYRICS_SELECTION,
                        syncedBonus = AUTO_DIRECT_LYRICS_SYNCED_BONUS,
                    )
                    val topCandidate = rankedCandidates.firstOrNull()
                    val matchedCandidate = topCandidate
                        ?.takeIf { it.score >= DEFAULT_DIRECT_LYRICS_SELECTION.minScore }
                        ?.candidate
                    logger.debug(LYRICS_LOG_TAG) {
                        "auto-direct-ranked track=$trackLabel source=${source.id} candidates=${rankedCandidates.size} " +
                            "topScore=${topCandidate?.score.logScore()} matched=${matchedCandidate != null} " +
                            "itemId=${matchedCandidate?.itemId.orEmpty()}"
                    }
                    matchedCandidate?.let { parsed ->
                        ResolvedLyricsResult(
                            document = parsed.document,
                            artworkLocator = normalizeArtworkLocator(parsed.artworkLocator),
                        )
                    }
                }

                is WorkflowLyricsSourceConfig -> requestWorkflowLyricsDocument(
                    track = track,
                    config = source,
                    requestType = "auto",
                )
            } ?: continue
            val result = sourceResult.withArtworkOverride(manualArtworkOverride)
            storeLyricsDocument(
                track.id,
                sourceResult.document,
                artworkLocator = sourceResult.artworkLocator,
            )
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
        val source = database.importSourceDao().getById(locator.first)
            ?.takeIf { it.type == ImportSourceType.NAVIDROME.name && it.enabled }
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
            logger = logger,
        )
    }

    override suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean): List<LyricsSearchCandidate> {
        val trackLabel = track.logIdentity()
        val baseTrack = database.trackDao().getByIds(listOf(track.id)).firstOrNull()?.toDomain() ?: track
        val trackProvidedCandidates = if (includeTrackProvidedCandidate) {
            buildTrackProvidedLyricsCandidates(baseTrack)
        } else {
            emptyList()
        }
        val configs = enabledDirectLyricsConfigs()
        if (configs.isEmpty()) {
            return if (trackProvidedCandidates.isNotEmpty()) {
                logger.debug(LYRICS_LOG_TAG) {
                    "manual-track-provided-only track=$trackLabel sources=${trackProvidedCandidates.joinToString(",") { it.sourceId }}"
                }
                trackProvidedCandidates
            } else {
                logger.warn(LYRICS_LOG_TAG) { "manual-no-enabled-direct-sources track=$trackLabel" }
                emptyList()
            }
        }
        var originalIndex = 0
        val rankedDirectCandidates = configs.flatMap { config ->
            requestDirectLyricsResults(
                track = track,
                config = config,
                requestType = "manual",
            ).map { parsed ->
                ScoredManualDirectLyricsCandidate(
                    candidate = LyricsSearchCandidate(
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
                    ),
                    score = scoreDirectLyricsCandidate(
                        track = track,
                        candidate = parsed,
                        selection = DEFAULT_DIRECT_LYRICS_SELECTION,
                    ),
                    originalIndex = originalIndex++,
                )
            }
        }
            .sortedWith(compareByDescending<ScoredManualDirectLyricsCandidate> { it.score }.thenBy { it.originalIndex })

        if (rankedDirectCandidates.isNotEmpty()) {
            logger.debug(LYRICS_LOG_TAG) {
                "manual-direct-ranked track=$trackLabel candidates=${rankedDirectCandidates.size} top=" +
                    rankedDirectCandidates.take(3).joinToString(" | ") { scored ->
                        "${scored.candidate.sourceId}:${scored.candidate.itemId.orEmpty()}:${scored.score.logScore()}"
                    }
            }
        }
        return buildList {
            addAll(trackProvidedCandidates)
            addAll(rankedDirectCandidates.map { it.candidate })
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

    override suspend fun applyLyricsCandidate(
        trackId: String,
        candidate: LyricsSearchCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        val appliedDocument = buildAppliedLyricsDocument(candidate)
        val existingOverride = manualOverrideRow(trackId)
        val existingManualPayload = existingOverride.manualPayloadOrNull()
        val existingManualArtwork = normalizeArtworkLocator(existingOverride?.artworkLocator)
        val trackArtwork = trackArtworkLocator(trackId)
        val sourceArtwork = normalizeArtworkLocator(candidate.artworkLocator)
        val artworkCandidateKey = candidate.itemId ?: candidate.title ?: "manual"
        val result = when (mode) {
            LyricsSearchApplyMode.FULL -> {
                val artworkLocator = if (candidate.isTrackProvided) {
                    persistManualOverride(trackId = trackId, rawPayload = null, artworkLocator = null)
                    storeLyricsDocument(trackId, appliedDocument)
                    sourceArtwork
                } else {
                    val normalizedArtworkLocator = cacheArtworkLocator(
                        trackId = trackId,
                        sourceKey = candidate.sourceId,
                        candidateKey = artworkCandidateKey,
                        sourceLocator = candidate.artworkLocator,
                    )
                    persistManualOverride(
                        trackId = trackId,
                        rawPayload = appliedDocument.rawPayload,
                        artworkLocator = normalizedArtworkLocator,
                    )
                    normalizedArtworkLocator
                }
                AppliedLyricsResult(
                    document = appliedDocument,
                    artworkLocator = artworkLocator,
                )
            }

            LyricsSearchApplyMode.LYRICS_ONLY -> {
                if (candidate.isTrackProvided) {
                    persistManualOverride(
                        trackId = trackId,
                        rawPayload = null,
                        artworkLocator = existingManualArtwork,
                    )
                    storeLyricsDocument(trackId, appliedDocument)
                } else {
                    persistManualOverride(
                        trackId = trackId,
                        rawPayload = appliedDocument.rawPayload,
                        artworkLocator = existingManualArtwork,
                    )
                }
                AppliedLyricsResult(
                    document = appliedDocument,
                    artworkLocator = existingManualArtwork ?: sourceArtwork ?: trackArtwork,
                )
            }

            LyricsSearchApplyMode.ARTWORK_ONLY -> {
                val artworkLocator = if (candidate.isTrackProvided) {
                    val sourceTrackArtwork = sourceArtwork ?: error("歌词结果没有可用封面。")
                    val artworkOverride = sourceTrackArtwork.takeUnless { it == trackArtwork }
                    persistManualOverride(
                        trackId = trackId,
                        rawPayload = existingManualPayload,
                        artworkLocator = artworkOverride,
                    )
                    sourceTrackArtwork
                } else {
                    val normalizedArtworkLocator = cacheArtworkLocator(
                        trackId = trackId,
                        sourceKey = candidate.sourceId,
                        candidateKey = artworkCandidateKey,
                        sourceLocator = candidate.artworkLocator ?: error("歌词结果没有可用封面。"),
                    ) ?: error("歌词结果没有可用封面。")
                    persistManualOverride(
                        trackId = trackId,
                        rawPayload = existingManualPayload,
                        artworkLocator = normalizedArtworkLocator,
                    )
                    normalizedArtworkLocator
                }
                AppliedLyricsResult(
                    document = null,
                    artworkLocator = artworkLocator,
                )
            }
        }
        logger.info(LYRICS_LOG_TAG) {
            "manual-apply track=$trackId source=${candidate.sourceId} mode=$mode synced=${candidate.document.isSynced} " +
                "lines=${candidate.document.lines.size} artworkLocator=${result.artworkLocator.orEmpty()}"
        }
        return result
    }

    private suspend fun buildTrackProvidedLyricsCandidates(track: Track): List<LyricsSearchCandidate> {
        return if (parseNavidromeSongLocator(track.mediaLocator) != null) {
            listOfNotNull(buildNavidromeTrackProvidedLyricsCandidate(track))
        } else {
            buildList {
                buildSameNameTrackProvidedLyricsCandidate(track)?.let(::add)
                buildEmbeddedTrackProvidedLyricsCandidate(track)?.let(::add)
            }
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

    private suspend fun resolveSameNameLyricsForPlayback(
        track: Track,
        cachedRows: List<LyricsCacheEntity>,
    ): ResolvedLyricsResult? {
        val trackLabel = track.logIdentity()
        return when (val lookup = readLiveSameNameLyricsDocument(track)) {
            is SameNameLyricsLookup.Found -> {
                storeLyricsDocument(
                    track.id,
                    lookup.document,
                    cacheSourceId = SAME_NAME_LRC_SOURCE_ID,
                )
                logger.info(LYRICS_LOG_TAG) {
                    "resolved track=$trackLabel source=$SAME_NAME_LRC_SOURCE_ID synced=${lookup.document.isSynced} " +
                        "lines=${lookup.document.lines.size}"
                }
                ResolvedLyricsResult(document = lookup.document)
            }

            SameNameLyricsLookup.Missing -> {
                database.lyricsCacheDao().deleteByTrackIdAndSourceId(track.id, SAME_NAME_LRC_SOURCE_ID)
                null
            }

            is SameNameLyricsLookup.Failed -> {
                logger.warn(LYRICS_LOG_TAG) {
                    "same-name-lrc-read-failed track=$trackLabel reason=${lookup.throwable.message.orEmpty()}"
                }
                cachedRows
                    .firstOrNull { it.sourceId == SAME_NAME_LRC_SOURCE_ID }
                    ?.let(::resolveCachedLyrics)
                    ?.also { logCacheHit(trackLabel, it.document) }
            }
        }
    }

    private suspend fun buildSameNameTrackProvidedLyricsCandidate(track: Track): LyricsSearchCandidate? {
        val document = when (val lookup = readLiveSameNameLyricsDocument(track)) {
            is SameNameLyricsLookup.Found -> {
                storeLyricsDocument(
                    track.id,
                    lookup.document,
                    cacheSourceId = SAME_NAME_LRC_SOURCE_ID,
                )
                lookup.document
            }

            SameNameLyricsLookup.Missing -> {
                database.lyricsCacheDao().deleteByTrackIdAndSourceId(track.id, SAME_NAME_LRC_SOURCE_ID)
                null
            }

            is SameNameLyricsLookup.Failed -> {
                logger.warn(LYRICS_LOG_TAG) {
                    "manual-track-provided-same-name-lrc-failed track=${track.logIdentity()} reason=${lookup.throwable.message.orEmpty()}"
                }
                database.lyricsCacheDao()
                    .getByTrackIdAndSourceId(track.id, SAME_NAME_LRC_SOURCE_ID)
                    ?.let { row -> parseCachedLyrics(row.sourceId, row.rawPayload) }
            }
        } ?: return null
        return LyricsSearchCandidate(
            sourceId = SAME_NAME_LRC_SOURCE_ID,
            sourceName = "同名歌词文件",
            document = document,
            title = track.title.takeIf { it.isNotBlank() },
            artistName = track.artistName?.takeIf { it.isNotBlank() },
            albumTitle = track.albumTitle?.takeIf { it.isNotBlank() },
            durationSeconds = track.durationSecondsOrNull(),
            artworkLocator = normalizeArtworkLocator(track.artworkLocator),
            isTrackProvided = true,
        )
    }

    private suspend fun readLiveSameNameLyricsDocument(track: Track): SameNameLyricsLookup {
        if (parseNavidromeSongLocator(track.mediaLocator) != null) return SameNameLyricsLookup.Missing
        val rawPayload = sameNameLyricsFileGateway.readSameNameLyrics(track).fold(
            onSuccess = { it?.trim()?.takeIf { value -> value.isNotBlank() } },
            onFailure = { throwable -> return SameNameLyricsLookup.Failed(throwable) },
        ) ?: return SameNameLyricsLookup.Missing
        val document = parseCachedLyrics(SAME_NAME_LRC_SOURCE_ID, rawPayload)
            ?: return SameNameLyricsLookup.Missing
        return SameNameLyricsLookup.Found(document)
    }

    private suspend fun readEmbeddedTrackLyrics(track: Track): LiveEmbeddedTrackLyricsResult {
        val isSambaTrack = parseSambaLocator(track.mediaLocator) != null
        val currentSnapshot = runCatching {
            if (isSambaTrack || audioTagGateway.canEdit(track)) {
                audioTagGateway.read(track).getOrThrow()
            } else {
                null
            }
        }.onFailure { throwable ->
            logger.warn(LYRICS_LOG_TAG) {
                "embedded-tag-read-failed track=${track.logIdentity()} reason=${throwable.message.orEmpty()}"
            }
        }.getOrNull()
        val document = parseCachedLyrics(
            EMBEDDED_LYRICS_SOURCE_ID,
            currentSnapshot?.embeddedLyrics?.trim().orEmpty(),
        )
        return LiveEmbeddedTrackLyricsResult(
            snapshot = currentSnapshot,
            document = document,
            isSambaTrack = isSambaTrack,
        )
    }

    private suspend fun buildEmbeddedTrackProvidedLyricsCandidate(track: Track): LyricsSearchCandidate? {
        val liveLyrics = readEmbeddedTrackLyrics(track)
        if (liveLyrics.snapshot != null && liveLyrics.document != null) {
            val currentSnapshot = liveLyrics.snapshot
            return LyricsSearchCandidate(
                sourceId = EMBEDDED_LYRICS_SOURCE_ID,
                sourceName = "歌曲标签",
                document = liveLyrics.document,
                title = currentSnapshot.title.takeIf { it.isNotBlank() },
                artistName = currentSnapshot.artistName?.takeIf { it.isNotBlank() },
                albumTitle = currentSnapshot.albumTitle?.takeIf { it.isNotBlank() },
                durationSeconds = track.durationSecondsOrNull(),
                artworkLocator = normalizeArtworkLocator(
                    if (liveLyrics.isSambaTrack) {
                        currentSnapshot.artworkLocator
                    } else {
                        currentSnapshot.artworkLocator ?: track.artworkLocator
                    },
                ),
                isTrackProvided = true,
            )
        }
        if (liveLyrics.snapshot != null || liveLyrics.isSambaTrack) return null

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

    private fun buildAppliedLyricsDocument(candidate: LyricsSearchCandidate): LyricsDocument {
        val rawPayload = preferredStoredLyricsPayload(candidate.document)
        return parseCachedLyrics(candidate.sourceId, rawPayload) ?: candidate.document.copy(
            sourceId = candidate.sourceId,
            rawPayload = rawPayload,
        )
    }

    private suspend fun fetchWorkflowLyricsForManualApply(
        trackId: String,
        candidate: WorkflowSongCandidate,
    ): LyricsDocument {
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
        return document.copy(rawPayload = preferredStoredLyricsPayload(document))
    }

    private suspend fun manualOverrideRow(trackId: String): LyricsCacheEntity? {
        return database.lyricsCacheDao().getByTrackIdAndSourceId(trackId, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
    }

    private suspend fun trackArtworkLocator(trackId: String): String? {
        return database.trackDao().getByIds(listOf(trackId))
            .firstOrNull()
            ?.artworkLocator
            ?.let(::normalizeArtworkLocator)
    }

    private suspend fun persistManualOverride(
        trackId: String,
        rawPayload: String?,
        artworkLocator: String?,
    ) {
        val normalizedPayload = rawPayload?.trim().orEmpty()
        val normalizedArtwork = normalizeArtworkLocator(artworkLocator)?.trim().orEmpty()
        if (normalizedPayload.isBlank() && normalizedArtwork.isBlank()) {
            database.lyricsCacheDao().deleteByTrackIdAndSourceId(trackId, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
            return
        }
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = trackId,
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = normalizedPayload,
                updatedAt = now(),
                artworkLocator = normalizedArtwork.ifBlank { null },
            ),
        )
    }

    private fun LyricsCacheEntity?.manualPayloadOrNull(): String? {
        return this?.rawPayload?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun ResolvedLyricsResult.withArtworkOverride(artworkLocator: String?): ResolvedLyricsResult {
        val normalizedArtwork = normalizeArtworkLocator(artworkLocator)
        return if (normalizedArtwork.isNullOrBlank()) {
            this
        } else {
            copy(artworkLocator = normalizedArtwork)
        }
    }

    override suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        val existingOverride = manualOverrideRow(trackId)
        val existingManualPayload = existingOverride.manualPayloadOrNull()
        val existingManualArtwork = normalizeArtworkLocator(existingOverride?.artworkLocator)
        val trackArtwork = trackArtworkLocator(trackId)
        val result = when (mode) {
            LyricsSearchApplyMode.FULL -> {
                val appliedDocument = fetchWorkflowLyricsForManualApply(trackId, candidate)
                val normalizedArtworkLocator = cacheArtworkLocator(
                    trackId = trackId,
                    sourceKey = candidate.sourceId,
                    candidateKey = candidate.id,
                    sourceLocator = candidate.imageUrl,
                )
                persistManualOverride(
                    trackId = trackId,
                    rawPayload = appliedDocument.rawPayload,
                    artworkLocator = normalizedArtworkLocator,
                )
                AppliedLyricsResult(
                    document = appliedDocument,
                    artworkLocator = normalizedArtworkLocator,
                )
            }

            LyricsSearchApplyMode.LYRICS_ONLY -> {
                val appliedDocument = fetchWorkflowLyricsForManualApply(trackId, candidate)
                persistManualOverride(
                    trackId = trackId,
                    rawPayload = appliedDocument.rawPayload,
                    artworkLocator = existingManualArtwork,
                )
                AppliedLyricsResult(
                    document = appliedDocument,
                    artworkLocator = existingManualArtwork ?: trackArtwork,
                )
            }

            LyricsSearchApplyMode.ARTWORK_ONLY -> {
                val normalizedArtworkLocator = cacheArtworkLocator(
                    trackId = trackId,
                    sourceKey = candidate.sourceId,
                    candidateKey = candidate.id,
                    sourceLocator = candidate.imageUrl ?: error("Workflow lyrics source ${candidate.sourceName} 没有可用封面。"),
                ) ?: error("Workflow lyrics source ${candidate.sourceName} 没有可用封面。")
                persistManualOverride(
                    trackId = trackId,
                    rawPayload = existingManualPayload,
                    artworkLocator = normalizedArtworkLocator,
                )
                AppliedLyricsResult(
                    document = null,
                    artworkLocator = normalizedArtworkLocator,
                )
            }
        }
        logger.info(LYRICS_LOG_TAG) {
            "manual-workflow-apply track=$trackId source=${candidate.sourceId} mode=$mode " +
                "coverUrl=${candidate.imageUrl.orEmpty()} artworkLocator=${result.artworkLocator.orEmpty()}"
        }
        return result
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
            document = document.copy(rawPayload = preferredStoredLyricsPayload(document)),
            artworkLocator = normalizeArtworkLocator(candidate.imageUrl),
        )
    }

    private suspend fun cacheArtworkLocator(
        trackId: String,
        sourceKey: String,
        candidateKey: String,
        sourceLocator: String?,
    ): String? {
        val normalizedLocator = normalizeArtworkLocator(sourceLocator)?.trim().orEmpty()
        if (normalizedLocator.isBlank()) return null
        val cacheKey = normalizedLocator
        val cachedLocator = runCatching {
            artworkCacheStore.cache(
                locator = normalizedLocator,
                cacheKey = cacheKey,
            )
        }.onFailure { throwable ->
            logger.error(LYRICS_LOG_TAG, throwable) {
                "artwork-cache-failed track=$trackId source=$sourceKey candidate=$candidateKey url=$normalizedLocator"
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (cachedLocator != null) {
            logger.debug(LYRICS_LOG_TAG) {
                "artwork-cache-hit track=$trackId source=$sourceKey candidate=$candidateKey locator=$cachedLocator"
            }
        }
        return normalizedLocator
    }

    private fun resolveCachedLyrics(row: LyricsCacheEntity): ResolvedLyricsResult? {
        return parseCachedLyrics(row.sourceId, row.rawPayload)?.let { cached ->
            ResolvedLyricsResult(
                document = cached,
                artworkLocator = normalizeArtworkLocator(row.artworkLocator),
            )
        }
    }

    private fun logCacheHit(trackLabel: String, document: LyricsDocument) {
        logger.debug(LYRICS_LOG_TAG) {
            "cache-hit track=$trackLabel source=${document.sourceId} synced=${document.isSynced} lines=${document.lines.size}"
        }
    }

    private suspend fun cacheWorkflowArtwork(trackId: String, candidate: WorkflowSongCandidate): String? {
        val sourceLocator = normalizeArtworkLocator(candidate.imageUrl)?.trim().orEmpty()
        if (sourceLocator.isBlank()) return null
        return cacheArtworkLocator(
            trackId = trackId,
            sourceKey = candidate.sourceId,
            candidateKey = candidate.id,
            sourceLocator = sourceLocator,
        )
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

    private suspend fun storeLyricsDocument(
        trackId: String,
        document: LyricsDocument,
        cacheSourceId: String = document.sourceId,
        artworkLocator: String? = null,
    ) {
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = trackId,
                sourceId = cacheSourceId,
                rawPayload = preferredStoredLyricsPayload(document),
                updatedAt = now(),
                artworkLocator = normalizeArtworkLocator(artworkLocator),
            ),
        )
    }
}

private fun preferredStoredLyricsPayload(document: LyricsDocument): String {
    return document.rawPayload.takeIf { it.isNotBlank() }
        ?: serializeLyricsDocument(document)
}

private data class ScoredManualDirectLyricsCandidate(
    val candidate: LyricsSearchCandidate,
    val score: Double,
    val originalIndex: Int,
)

private data class LiveEmbeddedTrackLyricsResult(
    val snapshot: AudioTagSnapshot?,
    val document: LyricsDocument?,
    val isSambaTrack: Boolean,
)

private sealed interface SameNameLyricsLookup {
    data class Found(val document: LyricsDocument) : SameNameLyricsLookup
    data class Failed(val throwable: Throwable) : SameNameLyricsLookup
    data object Missing : SameNameLyricsLookup
}

internal fun now(): Long = Clock.System.now().toEpochMilliseconds()

private const val LYRICS_LOG_TAG = "Lyrics"
const val MANUAL_LYRICS_OVERRIDE_SOURCE_ID = "manual-override"
const val SAME_NAME_LRC_SOURCE_ID = "same-name-lrc"
internal const val EMBEDDED_LYRICS_SOURCE_ID = "embedded-tag"

internal fun newId(prefix: String): String = "$prefix-${now()}-${Random.nextInt(1000, 9999)}"

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

private fun Double?.logScore(): String {
    if (this == null) return ""
    val rounded = (this * 1_000.0).toInt() / 1_000.0
    return rounded.toString()
}

fun effectiveArtworkOverridesByTrackId(rows: List<LyricsCacheEntity>): Map<String, String> {
    val manualOverrides = linkedMapOf<String, String>()
    val automaticOverrides = linkedMapOf<String, String>()
    rows.sortedWith(
        compareByDescending<LyricsCacheEntity> { it.updatedAt }
            .thenBy { it.trackId }
            .thenBy { it.sourceId },
    ).forEach { row ->
        val artworkLocator = normalizeArtworkLocator(row.artworkLocator)?.takeIf { it.isNotBlank() } ?: return@forEach
        if (row.sourceId == MANUAL_LYRICS_OVERRIDE_SOURCE_ID) {
            manualOverrides[row.trackId] = artworkLocator
        } else if (row.trackId !in automaticOverrides) {
            automaticOverrides[row.trackId] = artworkLocator
        }
    }
    return automaticOverrides.toMutableMap().apply {
        putAll(manualOverrides)
    }
}

fun manualArtworkOverridesByTrackId(rows: List<LyricsCacheEntity>): Map<String, String> {
    return effectiveArtworkOverridesByTrackId(rows.filter { it.sourceId == MANUAL_LYRICS_OVERRIDE_SOURCE_ID })
}

fun TrackEntity.toDomain(artworkOverrideLocator: String? = null): Track {
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
        artworkLocator = artworkOverrideLocator?.takeIf { it.isNotBlank() } ?: artworkLocator,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        bitDepth = bitDepth,
        samplingRate = samplingRate,
        bitRate = bitRate,
        channelCount = channelCount,
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
        enabled = enabled,
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
        enabled = enabled,
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
        val parsed = parseWorkflowLyricsSourceConfig(rawJson)
        val syncedRawJson = if (parsed.enabled == enabled) parsed.rawJson else rewriteWorkflowLyricsSourceEnabled(parsed.rawJson, enabled)
        parsed.copy(
            id = id,
            name = name,
            priority = priority,
            enabled = enabled,
            rawJson = syncedRawJson,
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

private fun normalizeImportSourceLabel(name: String): String {
    return name.trim().lowercase()
}

private fun hasImportSourceNameConflict(
    name: String,
    existing: List<ImportSourceEntity>,
    excludingId: String? = null,
): Boolean {
    val normalizedName = normalizeImportSourceLabel(name)
    return existing.any { entity ->
        entity.id != excludingId && normalizeImportSourceLabel(entity.label) == normalizedName
    }
}

private fun hasLocalFolderPathConflict(
    rootReference: String,
    existing: List<ImportSourceEntity>,
    excludingId: String? = null,
): Boolean {
    return existing.any { entity ->
        entity.id != excludingId &&
            entity.type == ImportSourceType.LOCAL_FOLDER.name &&
            entity.rootReference == rootReference
    }
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
            id = LRCLIB_SOURCE_ID,
            name = LRCLIB_SOURCE_NAME,
            method = RequestMethod.GET,
            urlTemplate = LRCLIB_SEARCH_URL,
            queryTemplate = LRCLIB_DEFAULT_QUERY_TEMPLATE,
            responseFormat = LyricsResponseFormat.JSON,
            extractor = LRCLIB_JSON_MAP_EXTRACTOR,
            priority = 50,
            enabled = true,
        ),
    )
}

fun defaultWorkflowLyricsSourceConfigs(): List<WorkflowLyricsSourceConfig> {
    return listOf(
        parseWorkflowLyricsSourceConfig(buildPresetOiapiQqMusicWorkflowJson()),
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
private const val LRCLIB_SOURCE_ID = "lrclib"
private const val LRCLIB_SOURCE_NAME = "LRCLIB"
private val LEGACY_BUILT_IN_LRCLIB_SOURCE_IDS = setOf("lrclib-synced", "lrclib-plain")
const val LRCLIB_JSON_MAP_EXTRACTOR = "json-map:lyrics=syncedLyrics|plainLyrics,title=trackName,artist=artistName,album=albumName,durationSeconds=duration,id=id"
private val LRCLIB_REMOVED_QUERY_KEYS = setOf("album_name", "duration")
