package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.core.model.ArtworkWritePolicy
import top.iwesley.lyn.music.core.model.ArtworkCacheResult

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull
import top.iwesley.lyn.music.core.model.checkUi
import top.iwesley.lyn.music.core.model.requireUi
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import androidx.room.PooledConnection
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.AutoOpenPlayerOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.AutoPlayOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.AppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportIndexState
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportStreamingScanReport
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.ImportTrackBatchSink
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.LocalFolderPickerMode
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.EmbySourceDraft
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsLookupMetadata
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.MenuBarLyricsControlsPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeLibraryProbe
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.OfflineDownloadGateway
import top.iwesley.lyn.music.core.model.PlaybackDecoderPreferencesStore
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.PlayerArtworkStylePreferencesStore
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.core.model.SubsonicSourceDraft
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagGateway
import top.iwesley.lyn.music.core.model.UnsupportedOfflineDownloadGateway
import top.iwesley.lyn.music.core.model.UnsupportedAppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedAutoOpenPlayerOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedAutoPlayOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedCompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedDesktopLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedMenuBarLyricsControlsPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedNavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedSameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.UnsupportedPlaybackDecoderPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedPlayerArtworkStylePreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedWindowClosePreferencesStore
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.WindowClosePreferencesStore
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.RemoteDirectoryEntry
import top.iwesley.lyn.music.core.model.buildWebDavDirectoryUrl
import top.iwesley.lyn.music.core.model.displayWebDavRootUrl
import top.iwesley.lyn.music.core.model.isSameOrAncestorDirectory
import top.iwesley.lyn.music.core.model.normalizeSelectedDirectories
import top.iwesley.lyn.music.core.model.localFolderPersistentIdentity
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.parseEmbySongLocator
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.AlbumEntity
import top.iwesley.lyn.music.data.db.ArtistEntity
import top.iwesley.lyn.music.data.db.ImportIndexStateEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.platform.sambaPort
import top.iwesley.lyn.music.platform.sambaRootPath
import top.iwesley.lyn.music.data.db.decodeSelectedDirectories
import top.iwesley.lyn.music.data.db.encodeSelectedDirectories
import top.iwesley.lyn.music.data.db.ImportTrackStageEntity
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.WorkflowLyricsSourceConfigEntity
import top.iwesley.lyn.music.domain.DEFAULT_LRCAPI_URL
import top.iwesley.lyn.music.domain.EMBY_LYRICS_SOURCE_ID
import top.iwesley.lyn.music.domain.MANAGED_LRCAPI_SOURCE_ID
import top.iwesley.lyn.music.domain.buildLyricsRequest
import top.iwesley.lyn.music.domain.buildManagedLrcApiConfig
import top.iwesley.lyn.music.domain.NAVIDROME_LYRICS_SOURCE_ID
import top.iwesley.lyn.music.domain.SUBSONIC_LYRICS_SOURCE_ID
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.isSubsonicCompatibleSourceType
import top.iwesley.lyn.music.domain.lyricsSourceIdFor
import top.iwesley.lyn.music.domain.normalizeNavidromeBaseUrl
import top.iwesley.lyn.music.domain.normalizeSubsonicBaseUrl
import top.iwesley.lyn.music.domain.normalizeEmbyBaseUrl
import top.iwesley.lyn.music.domain.normalizeRemoteSourceBaseUrls
import top.iwesley.lyn.music.domain.testEachRemoteSourceAddress
import top.iwesley.lyn.music.domain.requestNavidromeLyrics
import top.iwesley.lyn.music.domain.requestEmbyLyricsDocument as requestEmbyServerLyricsDocument
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.resolveEmbyDeviceId
import top.iwesley.lyn.music.domain.resolveNavidromeCoverArtUrl
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl
import top.iwesley.lyn.music.domain.resolveEmbySource
import top.iwesley.lyn.music.domain.resolveSubsonicCompatibleCoverArtUrl
import top.iwesley.lyn.music.domain.resolveSubsonicCompatibleStreamUrl
import top.iwesley.lyn.music.domain.scanNavidromeLibrary
import top.iwesley.lyn.music.domain.scanSubsonicLibrary
import top.iwesley.lyn.music.domain.parseEmbyCredential
import top.iwesley.lyn.music.domain.serializeEmbyCredential
import top.iwesley.lyn.music.domain.toSubsonicAuthMode
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
import top.iwesley.lyn.music.domain.lyricsArtworkTieBreakScore
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

data class TrackPlaybackStat(
    val trackId: String,
    val playCount: Int,
    val lastPlayedAt: Long,
)

interface TrackPlaybackStatsRepository {
    val trackStats: Flow<Map<String, TrackPlaybackStat>>
}

interface ImportSourceRepository {
    fun observeSources(): Flow<List<SourceWithStatus>>
    suspend fun importLocalFolder(): Result<ImportScanSummary?>
    suspend fun importLocalFolder(progressSink: ImportScanProgressSink): Result<ImportScanSummary?> {
        return importLocalFolder()
    }
    suspend fun importLocalFolder(mode: LocalFolderPickerMode): Result<ImportScanSummary?> {
        return importLocalFolder()
    }
    suspend fun importLocalFolder(
        mode: LocalFolderPickerMode,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary?> {
        return importLocalFolder(mode)
    }
    suspend fun importSelectedLocalFolder(selection: LocalFolderSelection): Result<ImportScanSummary> {
        return Result.failure(UnsupportedOperationException("Importing a preselected local folder is not supported."))
    }
    suspend fun importSelectedLocalFolder(
        selection: LocalFolderSelection,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return importSelectedLocalFolder(selection)
    }
    suspend fun reauthorizeLocalFolder(sourceId: String): Result<ImportScanSummary?> {
        return Result.failure(UnsupportedOperationException("Reauthorizing a local folder is not supported."))
    }
    suspend fun reauthorizeLocalFolder(
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary?> {
        return reauthorizeLocalFolder(sourceId)
    }
    suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit>
    suspend fun listSambaDirectories(draft: SambaSourceDraft, relativePath: String): Result<List<RemoteDirectoryEntry>> =
        Result.failure(UnsupportedOperationException("Folder browsing is not supported."))
    /** Lists folders of an existing source, reusing its stored password when [draft] leaves it blank. */
    suspend fun listUpdatedSambaDirectories(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> = Result.failure(UnsupportedOperationException("Folder browsing is not supported."))
    suspend fun testUpdatedSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<Unit>
    suspend fun addSambaSource(draft: SambaSourceDraft): Result<ImportScanSummary>
    suspend fun addSambaSource(
        draft: SambaSourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return addSambaSource(draft)
    }
    suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<ImportScanSummary>
    suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return updateSambaSource(sourceId, draft, keepExistingCredentialWhenBlankPassword)
    }
    suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit>
    suspend fun listWebDavDirectories(draft: WebDavSourceDraft, relativePath: String): Result<List<RemoteDirectoryEntry>> =
        Result.failure(UnsupportedOperationException("Folder browsing is not supported."))
    /** Lists folders of an existing source, reusing its stored password when [draft] leaves it blank. */
    suspend fun listUpdatedWebDavDirectories(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> = Result.failure(UnsupportedOperationException("Folder browsing is not supported."))
    suspend fun testUpdatedWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<Unit>
    suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<ImportScanSummary>
    suspend fun addWebDavSource(
        draft: WebDavSourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return addWebDavSource(draft)
    }
    suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<ImportScanSummary>
    suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return updateWebDavSource(sourceId, draft, keepExistingCredentialWhenBlankPassword)
    }
    suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit>
    suspend fun probeNavidromeSource(draft: NavidromeSourceDraft): Result<NavidromeLibraryProbe>
    suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<Unit>
    suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary>
    suspend fun addNavidromeSource(
        draft: NavidromeSourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return addNavidromeSource(draft)
    }
    suspend fun addNavidromeSourceOnline(
        draft: NavidromeSourceDraft,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary>
    suspend fun probeExistingNavidromeSource(sourceId: String): Result<NavidromeLibraryProbe>
    suspend fun switchNavidromeSourceToOnline(
        sourceId: String,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary>
    suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<ImportScanSummary>
    suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return updateNavidromeSource(sourceId, draft, keepExistingCredentialWhenBlankPassword)
    }
    suspend fun testSubsonicSource(draft: SubsonicSourceDraft): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Subsonic import is not supported."))
    }
    suspend fun testUpdatedSubsonicSource(
        sourceId: String,
        draft: SubsonicSourceDraft,
        keepExistingCredentialWhenBlankCredential: Boolean = true,
    ): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Subsonic import is not supported."))
    }
    suspend fun addSubsonicSource(draft: SubsonicSourceDraft): Result<ImportScanSummary> {
        return Result.failure(UnsupportedOperationException("Subsonic import is not supported."))
    }
    suspend fun addSubsonicSource(
        draft: SubsonicSourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return addSubsonicSource(draft)
    }
    suspend fun updateSubsonicSource(
        sourceId: String,
        draft: SubsonicSourceDraft,
        keepExistingCredentialWhenBlankCredential: Boolean = true,
    ): Result<ImportScanSummary> {
        return Result.failure(UnsupportedOperationException("Subsonic import is not supported."))
    }
    suspend fun updateSubsonicSource(
        sourceId: String,
        draft: SubsonicSourceDraft,
        keepExistingCredentialWhenBlankCredential: Boolean = true,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return updateSubsonicSource(sourceId, draft, keepExistingCredentialWhenBlankCredential)
    }
    suspend fun testEmbySource(draft: EmbySourceDraft): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Emby import is not supported."))
    }
    suspend fun testUpdatedEmbySource(
        sourceId: String,
        draft: EmbySourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Emby import is not supported."))
    }
    suspend fun addEmbySource(draft: EmbySourceDraft): Result<ImportScanSummary> {
        return Result.failure(UnsupportedOperationException("Emby import is not supported."))
    }
    suspend fun addEmbySource(
        draft: EmbySourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return addEmbySource(draft)
    }
    suspend fun updateEmbySource(
        sourceId: String,
        draft: EmbySourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
    ): Result<ImportScanSummary> {
        return Result.failure(UnsupportedOperationException("Emby import is not supported."))
    }
    suspend fun updateEmbySource(
        sourceId: String,
        draft: EmbySourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean = true,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return updateEmbySource(sourceId, draft, keepExistingCredentialWhenBlankPassword)
    }
    suspend fun rescanSource(sourceId: String): Result<ImportScanSummary?>
    suspend fun rescanSource(
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary?> {
        return rescanSource(sourceId)
    }
    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): Result<Unit>
    suspend fun deleteSource(sourceId: String): Result<Unit>
}

interface PlaylistRepository {
    val playlists: Flow<List<PlaylistSummary>>

    fun observePlaylistDetail(playlistId: String): Flow<PlaylistDetail?>
    suspend fun createPlaylist(name: String): Result<PlaylistSummary>
    suspend fun renamePlaylist(playlistId: String, name: String): Result<PlaylistSummary>
    suspend fun deletePlaylist(playlistId: String): Result<Unit>
    suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit>
    suspend fun importPlaylistText(playlistId: String, text: String): Result<PlaylistImportReport>
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit>
    suspend fun refreshNavidromePlaylists(): Result<Unit>
}

data class PlaylistImportReport(
    val addedCount: Int = 0,
    val alreadyExistsCount: Int = 0,
    val duplicateInputCount: Int = 0,
    val malformedLines: List<PlaylistImportLineIssue> = emptyList(),
    val notMatchedLines: List<PlaylistImportLineIssue> = emptyList(),
    val ambiguousLines: List<PlaylistImportAmbiguousLineIssue> = emptyList(),
    val failedLines: List<PlaylistImportFailedLineIssue> = emptyList(),
) {
    val hasIssues: Boolean
        get() = duplicateInputCount > 0 ||
            malformedLines.isNotEmpty() ||
            notMatchedLines.isNotEmpty() ||
            ambiguousLines.isNotEmpty() ||
            failedLines.isNotEmpty()
}

data class PlaylistImportLineIssue(
    val lineNumber: Int,
    val rawText: String,
)

data class PlaylistImportAmbiguousLineIssue(
    val lineNumber: Int,
    val rawText: String,
    val matchCount: Int,
)

data class PlaylistImportFailedLineIssue(
    val lineNumber: Int,
    val rawText: String,
    val message: String,
    val messageText: UiText? = null,
)

fun PlaylistImportFailedLineIssue.messageUiText(): UiText = messageText ?: UiText.Raw(message)

internal fun playlistImportFailure(
    lineNumber: Int,
    rawText: String,
    throwable: Throwable,
): PlaylistImportFailedLineIssue = PlaylistImportFailedLineIssue(
    lineNumber = lineNumber,
    rawText = rawText,
    message = throwable.message.orEmpty().ifBlank { "加入失败。" },
    messageText = throwable.uiFailureTextOrNull()
        ?: throwable.message?.takeIf { it.isNotBlank() }?.let(UiText::Raw)
        ?: uiText(Res.string.playlist_add_track_failed),
)

interface LyricsRepository {
    suspend fun getLyrics(track: Track): ResolvedLyricsResult?
    suspend fun resolveNetworkLyrics(metadata: LyricsLookupMetadata): ResolvedLyricsResult? = null
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
    val appLanguage: StateFlow<AppLanguage> get() = AppLanguageRuntime.appLanguage
    suspend fun setAppLanguage(language: AppLanguage) { AppLanguageRuntime.select(language) }

    val lyricsSources: Flow<List<LyricsSourceDefinition>>
    val useSambaCache: StateFlow<Boolean>
    val showCompactPlayerLyrics: StateFlow<Boolean>
    val showDesktopLyrics: StateFlow<Boolean>
    val showMenuBarLyricsControls: StateFlow<Boolean>
    val autoPlayOnStartup: StateFlow<Boolean>
    val autoOpenPlayerOnStartup: StateFlow<Boolean>
    val minimizeWindowOnClose: StateFlow<Boolean>
    val appDisplayScalePreset: StateFlow<AppDisplayScalePreset>
    val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality>
    val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality>
    val useAndroidExtensionDecoder: StateFlow<Boolean>
    val playerArtworkStyle: StateFlow<PlayerArtworkStyle>
    val selectedTheme: StateFlow<AppThemeId>
    val customThemeTokens: StateFlow<AppThemeTokens>
    val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences>
    val desktopVlcAutoDetectedPath: StateFlow<String?>
    val desktopVlcManualPath: StateFlow<String?>
    val desktopVlcEffectivePath: StateFlow<String?>

    suspend fun ensureDefaults()
    suspend fun setUseSambaCache(enabled: Boolean)
    suspend fun setShowCompactPlayerLyrics(enabled: Boolean)
    suspend fun setShowDesktopLyrics(enabled: Boolean)
    suspend fun setShowMenuBarLyricsControls(enabled: Boolean)
    suspend fun setAutoPlayOnStartup(enabled: Boolean)
    suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean)
    suspend fun setMinimizeWindowOnClose(enabled: Boolean)
    suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset)
    suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality)
    suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality)
    suspend fun setUseAndroidExtensionDecoder(enabled: Boolean)
    suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle)
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
            .filter { it.isLocalIndexedEnabled() }
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

class RoomTrackPlaybackStatsRepository(
    database: LynMusicDatabase,
) : TrackPlaybackStatsRepository {
    override val trackStats: Flow<Map<String, TrackPlaybackStat>> = database.trackPlaybackStatsDao()
        .observeAllByRecent()
        .map { rows ->
            rows.associate { row ->
                row.trackId to TrackPlaybackStat(
                    trackId = row.trackId,
                    playCount = row.playCount,
                    lastPlayedAt = row.lastPlayedAt,
                )
            }
        }
}

class RoomImportSourceRepository(
    private val database: LynMusicDatabase,
    private val gateway: ImportSourceGateway,
    private val secureCredentialStore: SecureCredentialStore,
    private val offlineDownloadGateway: OfflineDownloadGateway = UnsupportedOfflineDownloadGateway,
    private val addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
) : ImportSourceRepository {
    private val navidromeScanLocks = mutableMapOf<String, Mutex>()
    private val navidromeScanLocksMutex = Mutex()

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
        return importLocalFolder(LocalFolderPickerMode.Automatic, ImportScanProgressSink.NoOp)
    }

    override suspend fun importLocalFolder(mode: LocalFolderPickerMode): Result<ImportScanSummary?> {
        return importLocalFolder(mode, ImportScanProgressSink.NoOp)
    }

    override suspend fun importLocalFolder(
        mode: LocalFolderPickerMode,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary?> {
        return runCatching {
            val selection = gateway.pickLocalFolder(mode) ?: return@runCatching null
            importSelectedLocalFolder(selection, progressSink).getOrThrow()
        }
    }

    override suspend fun importSelectedLocalFolder(selection: LocalFolderSelection): Result<ImportScanSummary> {
        return importSelectedLocalFolder(selection, ImportScanProgressSink.NoOp)
    }

    override suspend fun importSelectedLocalFolder(
        selection: LocalFolderSelection,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return runCatching {
            val sourceId = newId("local")
            val source = database.immediateWriteTransaction {
                val existing = database.importSourceDao().getAll()
                if (hasLocalFolderPathConflict(
                        rootReference = selection.persistentReference,
                        existing = existing,
                    )
                ) {
                    throw UiTextException(uiText(Res.string.source_local_folder_already_imported))
                }
                ImportSource(
                    id = sourceId,
                    type = ImportSourceType.LOCAL_FOLDER,
                    label = uniqueImportSourceLabel(selection.label, existing),
                    rootReference = selection.persistentReference,
                    createdAt = now(),
                ).also { database.importSourceDao().upsert(it.toEntity()) }
            }
            runScan(source, progressSink) {
                gateway.scanLocalFolder(selection, source.id, progressSink)
            }
        }
    }

    override suspend fun reauthorizeLocalFolder(sourceId: String): Result<ImportScanSummary?> {
        return reauthorizeLocalFolder(sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun reauthorizeLocalFolder(
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary?> {
        return runCatching {
            val existing = database.importSourceDao().getById(sourceId)?.toDomain()
                ?.takeIf { it.type == ImportSourceType.LOCAL_FOLDER }
                ?: throw UiTextException(uiText(Res.string.source_local_folder_not_found))
            val selection = gateway.pickLocalFolder(LocalFolderPickerMode.System) ?: return@runCatching null
            validateLocalFolderImportSourceCreation(
                rootReference = selection.persistentReference,
                excludingId = sourceId,
            )
            checkUi(
                localFolderPersistentIdentity(selection.persistentReference) ==
                    localFolderPersistentIdentity(existing.rootReference),
            ) { uiText(Res.string.source_local_folder_mismatch) }
            val updated = existing.copy(
                rootReference = selection.persistentReference,
            )
            runScan(updated, progressSink) {
                gateway.scanLocalFolder(selection, sourceId, progressSink)
            }
        }
    }

    override suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit> {
        return runCatching {
            val preparedDraft = prepareSambaDraft(draft)
            gateway.testSamba(preparedDraft)
        }
    }

    override suspend fun listSambaDirectories(
        draft: SambaSourceDraft,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> {
        return runCatching {
            gateway.listSambaDirectories(prepareSambaDraft(draft), relativePath)
        }
    }

    override suspend fun listUpdatedSambaDirectories(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.SAMBA)
            val preparedDraft = prepareSambaDraft(draft)
            val password = resolveUpdatedPassword(
                existingCredentialKey = existing.credentialKey,
                password = preparedDraft.password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            gateway.listSambaDirectories(preparedDraft.copy(password = password), relativePath)
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
        return addSambaSource(draft, ImportScanProgressSink.NoOp)
    }

    override suspend fun addSambaSource(
        draft: SambaSourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return runCatching {
            val sourceId = newId("smb")
            val preparedDraft = prepareSambaDraft(draft)
            val newSource = createSambaSource(sourceId, preparedDraft).copy(
                credentialKey = credentialKeyForNewSource(preparedDraft.password, sourceId),
            )
            validateImportSourceCreation(label = newSource.label)
            newSource.credentialKey?.let { secureCredentialStore.put(it, preparedDraft.password) }
            database.importSourceDao().upsert(newSource.toEntity())
            runScan(newSource, progressSink) {
                gateway.scanSamba(preparedDraft, sourceId, progressSink)
            }
        }
    }

    override suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        return updateSambaSource(
            sourceId = sourceId,
            draft = draft,
            keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            progressSink = ImportScanProgressSink.NoOp,
        )
    }

    override suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        progressSink: ImportScanProgressSink,
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
            val report = gateway.scanSamba(preparedDraft.copy(password = password), sourceId, progressSink)
            val credentialKey = resolveUpdatedCredentialKey(
                sourceId = sourceId,
                existingCredentialKey = existing.credentialKey,
                password = password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            persistUpdatedCredential(existing.credentialKey, credentialKey, password)
            persistScanWithProgress(updatedSource.copy(credentialKey = credentialKey), report, progressSink)
        }
    }

    override suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit> {
        return runCatching {
            gateway.testWebDav(prepareWebDavDraft(draft))
        }
    }

    override suspend fun listWebDavDirectories(
        draft: WebDavSourceDraft,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> {
        return runCatching {
            gateway.listWebDavDirectories(prepareWebDavDraft(draft), relativePath)
        }
    }

    override suspend fun listUpdatedWebDavDirectories(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.WEBDAV)
            val preparedDraft = prepareWebDavDraft(draft)
            val password = resolveUpdatedPassword(
                existingCredentialKey = existing.credentialKey,
                password = preparedDraft.password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            gateway.listWebDavDirectories(preparedDraft.copy(password = password), relativePath)
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
        return addWebDavSource(draft, ImportScanProgressSink.NoOp)
    }

    override suspend fun addWebDavSource(
        draft: WebDavSourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return runCatching {
            val sourceId = newId("dav")
            val preparedDraft = prepareWebDavDraft(draft)
            val source = createWebDavSource(sourceId, preparedDraft).copy(
                credentialKey = credentialKeyForNewSource(preparedDraft.password, sourceId),
            )
            validateImportSourceCreation(label = source.label)
            source.credentialKey?.let { secureCredentialStore.put(it, preparedDraft.password) }
            database.importSourceDao().upsert(source.toEntity())
            runScan(source, progressSink) {
                gateway.scanWebDav(preparedDraft, sourceId, progressSink)
            }
        }
    }

    override suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        return updateWebDavSource(
            sourceId = sourceId,
            draft = draft,
            keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            progressSink = ImportScanProgressSink.NoOp,
        )
    }

    override suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        progressSink: ImportScanProgressSink,
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
            val report = gateway.scanWebDav(preparedDraft.copy(password = password), sourceId, progressSink)
            val credentialKey = resolveUpdatedCredentialKey(
                sourceId = sourceId,
                existingCredentialKey = existing.credentialKey,
                password = password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            persistUpdatedCredential(existing.credentialKey, credentialKey, password)
            persistScanWithProgress(updatedSource.copy(credentialKey = credentialKey), report, progressSink)
        }
    }

    override suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> {
        return runCatching {
            val preparedDraft = prepareNavidromeDraft(draft)
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = preparedDraft.baseUrl,
                wanBaseUrl = preparedDraft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
            ) { candidate ->
                gateway.testNavidrome(preparedDraft.copy(baseUrl = candidate.value))
            }
        }
    }

    override suspend fun probeNavidromeSource(draft: NavidromeSourceDraft): Result<NavidromeLibraryProbe> {
        return runCatching {
            val preparedDraft = prepareNavidromeDraft(draft)
            addressSelector.invalidate("draft-navidrome")
            addressSelector.withAddressFallback(
                sourceId = "draft-navidrome",
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = preparedDraft.baseUrl,
                wanBaseUrl = preparedDraft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
            ) { candidate ->
                gateway.probeNavidrome(preparedDraft.copy(baseUrl = candidate.value))
            }
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
                throw UiTextException(uiText(Res.string.source_navidrome_password_missing))
            }
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = preparedDraft.baseUrl,
                wanBaseUrl = preparedDraft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
            ) { candidate ->
                gateway.testNavidrome(preparedDraft.copy(baseUrl = candidate.value, password = password))
            }
        }
    }

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary> {
        return addNavidromeSource(draft, ImportScanProgressSink.NoOp)
    }

    override suspend fun addNavidromeSource(
        draft: NavidromeSourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return runCatching {
            val sourceId = newId("navidrome")
            val preparedDraft = prepareNavidromeDraft(draft)
            val source = createNavidromeSource(sourceId, preparedDraft).copy(credentialKey = "credential-$sourceId")
            validateImportSourceCreation(label = source.label)
            source.credentialKey?.let { secureCredentialStore.put(it, preparedDraft.password) }
            database.importSourceDao().upsert(source.toEntity())
            runNavidromeStreamingScan(source, progressSink) { trackBatchSink, resetStage ->
                addressSelector.withAddressFallback(
                    sourceId = sourceId,
                    sourceType = ImportSourceType.NAVIDROME,
                    lanBaseUrl = preparedDraft.baseUrl,
                    wanBaseUrl = preparedDraft.wanBaseUrl,
                    normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
                ) { candidate ->
                    resetStage()
                    gateway.scanNavidromeStreaming(
                        preparedDraft.copy(baseUrl = candidate.value),
                        sourceId,
                        progressSink,
                        trackBatchSink,
                    )
                }
            }
        }
    }

    override suspend fun addNavidromeSourceOnline(
        draft: NavidromeSourceDraft,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> {
        return runCatching {
            val confirmedRemoteTrackCount = requireOnlineNavidromeRemoteTrackCount(remoteTrackCount)
            val sourceId = newId("navidrome")
            val preparedDraft = prepareNavidromeDraft(draft)
            val source = createNavidromeSource(
                sourceId = sourceId,
                draft = preparedDraft,
                indexMode = ImportSourceIndexMode.ONLINE,
            ).copy(credentialKey = "credential-$sourceId")
            validateImportSourceCreation(label = source.label)
            persistOnlineNavidromeSourceWithCredential(
                source = source,
                remoteTrackCount = confirmedRemoteTrackCount,
                credential = preparedDraft.password,
                shouldWriteCredential = true,
                previousCredentialKey = null,
            )
        }
    }

    override suspend fun probeExistingNavidromeSource(sourceId: String): Result<NavidromeLibraryProbe> {
        return runCatching {
            val source = requireRemoteSource(sourceId, ImportSourceType.NAVIDROME)
            requireUi(source.enabled) { uiText(Res.string.source_disabled) }
            probeExistingNavidromeSource(source)
        }
    }

    override suspend fun switchNavidromeSourceToOnline(
        sourceId: String,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> {
        return runCatching {
            val confirmedRemoteTrackCount = requireOnlineNavidromeRemoteTrackCount(remoteTrackCount)
            val source = requireRemoteSource(sourceId, ImportSourceType.NAVIDROME)
            requireUi(source.enabled) { uiText(Res.string.source_disabled) }
            persistOnlineNavidromeSource(
                source = source.copy(indexMode = ImportSourceIndexMode.ONLINE),
                remoteTrackCount = confirmedRemoteTrackCount,
            )
        }
    }

    override suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        return updateNavidromeSource(
            sourceId = sourceId,
            draft = draft,
            keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            progressSink = ImportScanProgressSink.NoOp,
        )
    }

    override suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.NAVIDROME)
            addressSelector.invalidate(sourceId)
            val preparedDraft = prepareNavidromeDraft(draft)
            val updatedSource = createNavidromeSource(
                sourceId = existing.id,
                draft = preparedDraft,
                createdAt = existing.createdAt,
                enabled = existing.enabled,
                indexMode = existing.indexMode,
            )
            assertUniqueImportSourceLabel(updatedSource.label, excludingSourceId = existing.id)
            val password = resolveUpdatedPassword(
                existingCredentialKey = existing.credentialKey,
                password = preparedDraft.password,
                keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            )
            if (password.isBlank()) {
                throw UiTextException(uiText(Res.string.source_navidrome_password_missing))
            }
            val submittedPassword = preparedDraft.password.isNotBlank()
            val credentialKey = if (existing.indexMode == ImportSourceIndexMode.ONLINE && submittedPassword && existing.credentialKey != null) {
                newId("credential-$sourceId")
            } else {
                resolveUpdatedCredentialKey(
                    sourceId = sourceId,
                    existingCredentialKey = existing.credentialKey,
                    password = password,
                    keepExistingCredentialWhenBlankPassword = true,
                )
            }
            if (existing.indexMode == ImportSourceIndexMode.ONLINE) {
                val probe = addressSelector.withAddressFallback(
                    sourceId = sourceId,
                    sourceType = ImportSourceType.NAVIDROME,
                    lanBaseUrl = preparedDraft.baseUrl,
                    wanBaseUrl = preparedDraft.wanBaseUrl,
                    normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
                ) { candidate ->
                    gateway.probeNavidrome(preparedDraft.copy(baseUrl = candidate.value, password = password))
                }
                requireOnlineNavidromePaging(probe)
                return@runCatching persistOnlineNavidromeSourceWithCredential(
                    source = updatedSource.copy(credentialKey = credentialKey),
                    remoteTrackCount = probe.totalTrackCount,
                    credential = password,
                    shouldWriteCredential = submittedPassword,
                    previousCredentialKey = existing.credentialKey,
                )
            }
            runNavidromeStreamingScan(updatedSource.copy(credentialKey = credentialKey), progressSink) { trackBatchSink, resetStage ->
                val report = addressSelector.withAddressFallback(
                    sourceId = sourceId,
                    sourceType = ImportSourceType.NAVIDROME,
                    lanBaseUrl = preparedDraft.baseUrl,
                    wanBaseUrl = preparedDraft.wanBaseUrl,
                    normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
                ) { candidate ->
                    resetStage()
                    gateway.scanNavidromeStreaming(
                        preparedDraft.copy(baseUrl = candidate.value, password = password),
                        sourceId,
                        progressSink,
                        trackBatchSink,
                    )
                }
                persistUpdatedCredential(existing.credentialKey, credentialKey, password)
                report
            }
        }
    }

    override suspend fun testSubsonicSource(draft: SubsonicSourceDraft): Result<Unit> {
        return runCatching {
            val preparedDraft = prepareSubsonicDraft(draft)
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.SUBSONIC,
                lanBaseUrl = preparedDraft.baseUrl,
                wanBaseUrl = preparedDraft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeSubsonicBaseUrl,
            ) { candidate ->
                gateway.testSubsonic(preparedDraft.copy(baseUrl = candidate.value))
            }
        }
    }

    override suspend fun testUpdatedSubsonicSource(
        sourceId: String,
        draft: SubsonicSourceDraft,
        keepExistingCredentialWhenBlankCredential: Boolean,
    ): Result<Unit> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.SUBSONIC)
            val preparedDraft = prepareSubsonicDraft(draft)
            val credential = resolveUpdatedSubsonicCredential(
                existing = existing,
                draft = preparedDraft,
                keepExistingCredentialWhenBlankCredential = keepExistingCredentialWhenBlankCredential,
            )
            if (credential.isBlank()) {
                throw UiTextException(uiText(Res.string.source_credentials_missing, "Subsonic"))
            }
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.SUBSONIC,
                lanBaseUrl = preparedDraft.baseUrl,
                wanBaseUrl = preparedDraft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeSubsonicBaseUrl,
            ) { candidate ->
                gateway.testSubsonic(preparedDraft.copy(baseUrl = candidate.value, credential = credential))
            }
        }
    }

    override suspend fun addSubsonicSource(draft: SubsonicSourceDraft): Result<ImportScanSummary> {
        return addSubsonicSource(draft, ImportScanProgressSink.NoOp)
    }

    override suspend fun addSubsonicSource(
        draft: SubsonicSourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return runCatching {
            val sourceId = newId("subsonic")
            val preparedDraft = prepareSubsonicDraft(draft)
            val source = createSubsonicSource(sourceId, preparedDraft).copy(credentialKey = "credential-$sourceId")
            validateImportSourceCreation(label = source.label)
            source.credentialKey?.let { secureCredentialStore.put(it, preparedDraft.credential) }
            database.importSourceDao().upsert(source.toEntity())
            runScan(source, progressSink) {
                addressSelector.withAddressFallback(
                    sourceId = sourceId,
                    sourceType = ImportSourceType.SUBSONIC,
                    lanBaseUrl = preparedDraft.baseUrl,
                    wanBaseUrl = preparedDraft.wanBaseUrl,
                    normalizeBaseUrl = ::normalizeSubsonicBaseUrl,
                ) { candidate ->
                    gateway.scanSubsonic(preparedDraft.copy(baseUrl = candidate.value), sourceId, progressSink)
                }
            }
        }
    }

    override suspend fun updateSubsonicSource(
        sourceId: String,
        draft: SubsonicSourceDraft,
        keepExistingCredentialWhenBlankCredential: Boolean,
    ): Result<ImportScanSummary> {
        return updateSubsonicSource(
            sourceId = sourceId,
            draft = draft,
            keepExistingCredentialWhenBlankCredential = keepExistingCredentialWhenBlankCredential,
            progressSink = ImportScanProgressSink.NoOp,
        )
    }

    override suspend fun updateSubsonicSource(
        sourceId: String,
        draft: SubsonicSourceDraft,
        keepExistingCredentialWhenBlankCredential: Boolean,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.SUBSONIC)
            addressSelector.invalidate(sourceId)
            val preparedDraft = prepareSubsonicDraft(draft)
            val updatedSource = createSubsonicSource(
                sourceId = existing.id,
                draft = preparedDraft,
                createdAt = existing.createdAt,
                enabled = existing.enabled,
            )
            assertUniqueImportSourceLabel(updatedSource.label, excludingSourceId = existing.id)
            val credential = resolveUpdatedSubsonicCredential(
                existing = existing,
                draft = preparedDraft,
                keepExistingCredentialWhenBlankCredential = keepExistingCredentialWhenBlankCredential,
            )
            if (credential.isBlank()) {
                throw UiTextException(uiText(Res.string.source_credentials_missing, "Subsonic"))
            }
            val report = addressSelector.withAddressFallback(
                sourceId = sourceId,
                sourceType = ImportSourceType.SUBSONIC,
                lanBaseUrl = preparedDraft.baseUrl,
                wanBaseUrl = preparedDraft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeSubsonicBaseUrl,
            ) { candidate ->
                gateway.scanSubsonic(
                    preparedDraft.copy(baseUrl = candidate.value, credential = credential),
                    sourceId,
                    progressSink,
                )
            }
            val credentialKey = resolveUpdatedCredentialKey(
                sourceId = sourceId,
                existingCredentialKey = existing.credentialKey,
                password = credential,
                keepExistingCredentialWhenBlankPassword = true,
            )
            persistUpdatedCredential(existing.credentialKey, credentialKey, credential)
            persistScanWithProgress(updatedSource.copy(credentialKey = credentialKey), report, progressSink)
        }
    }

    override suspend fun testEmbySource(draft: EmbySourceDraft): Result<Unit> {
        return runCatching {
            val preparedDraft = prepareEmbyDraft(draft)
            val deviceId = resolveEmbyDeviceId(secureCredentialStore)
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.EMBY,
                lanBaseUrl = preparedDraft.baseUrl,
                wanBaseUrl = preparedDraft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeEmbyBaseUrl,
            ) { candidate ->
                gateway.testEmby(
                    draft = preparedDraft.copy(baseUrl = candidate.value),
                    deviceId = deviceId,
                )
            }
        }.map { }
    }

    override suspend fun testUpdatedEmbySource(
        sourceId: String,
        draft: EmbySourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.EMBY)
            val preparedDraft = prepareEmbyDraft(draft)
            val deviceId = resolveEmbyDeviceId(secureCredentialStore)
            if (preparedDraft.password.isNotBlank()) {
                testEachRemoteSourceAddress(
                    sourceType = ImportSourceType.EMBY,
                    lanBaseUrl = preparedDraft.baseUrl,
                    wanBaseUrl = preparedDraft.wanBaseUrl,
                    normalizeBaseUrl = ::normalizeEmbyBaseUrl,
                ) { candidate ->
                    gateway.testEmby(preparedDraft.copy(baseUrl = candidate.value), deviceId)
                }
            } else if (keepExistingCredentialWhenBlankPassword) {
                val storedCredential = existing.credentialKey?.let { secureCredentialStore.get(it) }
                val credential = parseEmbyCredential(storedCredential) ?: throw UiTextException(uiText(Res.string.source_credentials_missing, "Emby"))
                testEachRemoteSourceAddress(
                    sourceType = ImportSourceType.EMBY,
                    lanBaseUrl = preparedDraft.baseUrl,
                    wanBaseUrl = preparedDraft.wanBaseUrl,
                    normalizeBaseUrl = ::normalizeEmbyBaseUrl,
                ) { candidate ->
                    gateway.testEmbyCredential(preparedDraft.copy(baseUrl = candidate.value), credential, deviceId)
                }
            } else {
                throw UiTextException(uiText(Res.string.source_credentials_missing, "Emby"))
            }
        }.map { }
    }

    override suspend fun addEmbySource(draft: EmbySourceDraft): Result<ImportScanSummary> {
        return addEmbySource(draft, ImportScanProgressSink.NoOp)
    }

    override suspend fun addEmbySource(
        draft: EmbySourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return runCatching {
            val sourceId = newId("emby")
            val preparedDraft = prepareEmbyDraft(draft)
            val deviceId = resolveEmbyDeviceId(secureCredentialStore)
            val credential = addressSelector.withAddressFallback(
                sourceId = sourceId,
                sourceType = ImportSourceType.EMBY,
                lanBaseUrl = preparedDraft.baseUrl,
                wanBaseUrl = preparedDraft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeEmbyBaseUrl,
            ) { candidate ->
                gateway.testEmby(preparedDraft.copy(baseUrl = candidate.value), deviceId)
            }
            val source = createEmbySource(sourceId, preparedDraft).copy(credentialKey = "credential-$sourceId")
            validateImportSourceCreation(label = source.label)
            source.credentialKey?.let { secureCredentialStore.put(it, serializeEmbyCredential(credential)) }
            database.importSourceDao().upsert(source.toEntity())
            runScan(source, progressSink) {
                addressSelector.withAddressFallback(
                    sourceId = sourceId,
                    sourceType = ImportSourceType.EMBY,
                    lanBaseUrl = preparedDraft.baseUrl,
                    wanBaseUrl = preparedDraft.wanBaseUrl,
                    normalizeBaseUrl = ::normalizeEmbyBaseUrl,
                ) { candidate ->
                    gateway.scanEmby(
                        preparedDraft.copy(baseUrl = candidate.value),
                        credential,
                        sourceId,
                        deviceId,
                        progressSink,
                    )
                }
            }
        }
    }

    override suspend fun updateEmbySource(
        sourceId: String,
        draft: EmbySourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        return updateEmbySource(
            sourceId = sourceId,
            draft = draft,
            keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankPassword,
            progressSink = ImportScanProgressSink.NoOp,
        )
    }

    override suspend fun updateEmbySource(
        sourceId: String,
        draft: EmbySourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        return runCatching {
            val existing = requireRemoteSource(sourceId, ImportSourceType.EMBY)
            addressSelector.invalidate(sourceId)
            val preparedDraft = prepareEmbyDraft(draft)
            val updatedSource = createEmbySource(
                sourceId = existing.id,
                draft = preparedDraft,
                createdAt = existing.createdAt,
                enabled = existing.enabled,
            )
            assertUniqueImportSourceLabel(updatedSource.label, excludingSourceId = existing.id)
            val deviceId = resolveEmbyDeviceId(secureCredentialStore)
            val credential = if (preparedDraft.password.isNotBlank()) {
                addressSelector.withAddressFallback(
                    sourceId = sourceId,
                    sourceType = ImportSourceType.EMBY,
                    lanBaseUrl = preparedDraft.baseUrl,
                    wanBaseUrl = preparedDraft.wanBaseUrl,
                    normalizeBaseUrl = ::normalizeEmbyBaseUrl,
                ) { candidate ->
                    gateway.testEmby(preparedDraft.copy(baseUrl = candidate.value), deviceId)
                }
            } else if (keepExistingCredentialWhenBlankPassword) {
                val existingCredential = parseEmbyCredential(existing.credentialKey?.let { secureCredentialStore.get(it) })
                    ?: throw UiTextException(uiText(Res.string.source_credentials_missing, "Emby"))
                addressSelector.withAddressFallback(
                    sourceId = sourceId,
                    sourceType = ImportSourceType.EMBY,
                    lanBaseUrl = preparedDraft.baseUrl,
                    wanBaseUrl = preparedDraft.wanBaseUrl,
                    normalizeBaseUrl = ::normalizeEmbyBaseUrl,
                ) { candidate ->
                    gateway.testEmbyCredential(preparedDraft.copy(baseUrl = candidate.value), existingCredential, deviceId)
                }
                existingCredential
            } else {
                throw UiTextException(uiText(Res.string.source_credentials_missing, "Emby"))
            }
            val report = addressSelector.withAddressFallback(
                sourceId = sourceId,
                sourceType = ImportSourceType.EMBY,
                lanBaseUrl = preparedDraft.baseUrl,
                wanBaseUrl = preparedDraft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeEmbyBaseUrl,
            ) { candidate ->
                gateway.scanEmby(
                    preparedDraft.copy(baseUrl = candidate.value),
                    credential,
                    sourceId,
                    deviceId,
                    progressSink,
                )
            }
            val credentialKey = existing.credentialKey ?: "credential-$sourceId"
            persistUpdatedCredential(
                previousCredentialKey = existing.credentialKey,
                nextCredentialKey = credentialKey,
                password = serializeEmbyCredential(credential),
            )
            persistScanWithProgress(updatedSource.copy(credentialKey = credentialKey), report, progressSink)
        }
    }

    override suspend fun rescanSource(sourceId: String): Result<ImportScanSummary?> {
        return rescanSource(sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun rescanSource(
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary?> {
        return runCatching {
            val entity = database.importSourceDao().getById(sourceId)
                ?: error("Source $sourceId does not exist.")
            val source = entity.toDomain()
            if (!source.enabled) {
                throw UiTextException(uiText(Res.string.source_disabled))
            }
            if (source.type == ImportSourceType.NAVIDROME) {
                if (source.indexMode == ImportSourceIndexMode.ONLINE) {
                    val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
                    if (password.isBlank()) {
                        throw UiTextException(uiText(Res.string.source_navidrome_password_missing))
                    }
                    val draft = NavidromeSourceDraft(
                        label = source.label,
                        baseUrl = source.rootReference,
                        wanBaseUrl = source.wanRootReference.orEmpty(),
                        username = source.username.orEmpty(),
                        password = password,
                    )
                    val probe = addressSelector.withAddressFallback(
                        sourceId = sourceId,
                        sourceType = ImportSourceType.NAVIDROME,
                        lanBaseUrl = source.rootReference,
                        wanBaseUrl = source.wanRootReference.orEmpty(),
                        normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
                    ) { candidate ->
                        gateway.probeNavidrome(draft.copy(baseUrl = candidate.value))
                    }
                    requireOnlineNavidromePaging(probe)
                    return@runCatching persistOnlineNavidromeSource(
                        source = source,
                        remoteTrackCount = probe.totalTrackCount,
                    )
                }
                return@runCatching rescanNavidromeSource(source, progressSink)
            }
            val summary = runScan(source.copy(lastScannedAt = now()), progressSink) {
                when (source.type) {
                    ImportSourceType.LOCAL_FOLDER -> gateway.scanLocalFolder(
                        selection = LocalFolderSelection(
                            label = source.label,
                            persistentReference = source.rootReference,
                        ),
                        sourceId = source.id,
                        progressSink = progressSink,
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
                                selectedDirectories = source.selectedDirectories,
                            ),
                            sourceId = source.id,
                            progressSink = progressSink,
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
                                selectedDirectories = source.selectedDirectories,
                            ),
                            sourceId = source.id,
                            progressSink = progressSink,
                        )
                    }

                    ImportSourceType.NAVIDROME -> {
                        error("Navidrome streaming scan should have been handled before generic rescan.")
                    }

                    ImportSourceType.SUBSONIC -> {
                        val credential = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
                        val draft = SubsonicSourceDraft(
                            label = source.label,
                            baseUrl = source.rootReference,
                            wanBaseUrl = source.wanRootReference.orEmpty(),
                            username = source.username.orEmpty(),
                            credential = credential,
                            authMode = source.subsonicAuthMode,
                        )
                        val prepared = prepareSubsonicDraft(draft)
                        addressSelector.withAddressFallback(
                            sourceId = source.id,
                            sourceType = ImportSourceType.SUBSONIC,
                            lanBaseUrl = prepared.baseUrl,
                            wanBaseUrl = prepared.wanBaseUrl,
                            normalizeBaseUrl = ::normalizeSubsonicBaseUrl,
                        ) { candidate ->
                            gateway.scanSubsonic(prepared.copy(baseUrl = candidate.value), source.id, progressSink)
                        }
                    }

                    ImportSourceType.EMBY -> {
                        val credential = parseEmbyCredential(source.credentialKey?.let { secureCredentialStore.get(it) })
                            ?: throw UiTextException(uiText(Res.string.source_credentials_missing, "Emby"))
                        val draft = EmbySourceDraft(
                            label = source.label,
                            baseUrl = source.rootReference,
                            wanBaseUrl = source.wanRootReference.orEmpty(),
                            username = source.username.orEmpty(),
                            password = "",
                        )
                        val prepared = prepareEmbyDraft(draft)
                        addressSelector.withAddressFallback(
                            sourceId = source.id,
                            sourceType = ImportSourceType.EMBY,
                            lanBaseUrl = prepared.baseUrl,
                            wanBaseUrl = prepared.wanBaseUrl,
                            normalizeBaseUrl = ::normalizeEmbyBaseUrl,
                        ) { candidate ->
                            gateway.scanEmby(
                                draft = prepared.copy(baseUrl = candidate.value),
                                credential = credential,
                                sourceId = source.id,
                                deviceId = resolveEmbyDeviceId(secureCredentialStore),
                                progressSink = progressSink,
                            )
                        }
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
            database.offlineDownloadDao().getBySourceId(source.id)
                .mapNotNull { it.localMediaLocator }
                .forEach { locator -> offlineDownloadGateway.delete(locator) }
            database.offlineDownloadDao().deleteBySourceId(source.id)
            source.credentialKey?.let { secureCredentialStore.remove(it) }
            database.favoriteTrackDao().deleteBySourceId(source.id)
            cleanupPlaylistsForDeletedSource(source.id)
            database.trackPlaybackStatsDao().deleteBySourceId(source.id)
            database.trackDao().deleteBySourceId(source.id)
            database.lyricsCacheDao().deleteByTrackIdPrefix(trackIdPrefix(source.id))
            database.importIndexStateDao().deleteBySourceId(source.id)
            database.importSourceDao().deleteById(source.id)
            addressSelector.invalidate(source.id)
            rebuildLibrarySummaries()
        }
    }

    private suspend fun cleanupPlaylistsForDeletedSource(sourceId: String) {
        val playlistIds = buildSet {
            database.playlistRemoteBindingDao().getBySourceId(sourceId).forEach { add(it.playlistId) }
            database.playlistTrackDao().getAll()
                .filter { it.sourceId == sourceId }
                .forEach { add(it.playlistId) }
        }
        playlistIds.forEach { playlistId ->
            database.playlistRemoteBindingDao().deleteByPlaylistIdAndSourceId(playlistId, sourceId)
            database.playlistTrackDao().deleteByPlaylistIdAndSourceId(playlistId, sourceId)
            cleanupDeletedSourcePlaylistIfNecessary(playlistId)
        }
    }

    private suspend fun cleanupDeletedSourcePlaylistIfNecessary(playlistId: String) {
        val playlist = database.playlistDao().getById(playlistId) ?: return
        val hasTracks = database.playlistTrackDao().getByPlaylistId(playlistId).isNotEmpty()
        val hasBindings = database.playlistRemoteBindingDao().getAll().any { it.playlistId == playlistId }
        if (!playlist.createdLocally && !hasTracks && !hasBindings) {
            database.playlistDao().deleteById(playlistId)
        }
    }

    private fun prepareSambaDraft(draft: SambaSourceDraft): SambaSourceDraft {
        val normalizedPath = normalizeSambaPath(draft.path)
        return draft.copy(
            label = draft.label.trim(),
            server = draft.server.trim(),
            path = normalizedPath,
            username = draft.username.trim(),
            selectedDirectories = normalizeSelectedDirectories(draft.selectedDirectories),
        )
    }

    private fun prepareWebDavDraft(draft: WebDavSourceDraft): WebDavSourceDraft {
        return draft.copy(
            label = draft.label.trim(),
            rootUrl = normalizeWebDavRootUrl(draft.rootUrl),
            username = draft.username.trim(),
            selectedDirectories = normalizeSelectedDirectories(draft.selectedDirectories),
        )
    }

    private fun prepareNavidromeDraft(draft: NavidromeSourceDraft): NavidromeSourceDraft {
        val (baseUrl, wanBaseUrl) = normalizeRemoteSourceBaseUrls(
            sourceType = ImportSourceType.NAVIDROME,
            lanBaseUrl = draft.baseUrl,
            wanBaseUrl = draft.wanBaseUrl,
            normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
        )
        return draft.copy(
            label = draft.label.trim(),
            baseUrl = baseUrl,
            wanBaseUrl = wanBaseUrl.orEmpty(),
            username = draft.username.trim(),
        )
    }

    private fun prepareSubsonicDraft(draft: SubsonicSourceDraft): SubsonicSourceDraft {
        val (baseUrl, wanBaseUrl) = normalizeRemoteSourceBaseUrls(
            sourceType = ImportSourceType.SUBSONIC,
            lanBaseUrl = draft.baseUrl,
            wanBaseUrl = draft.wanBaseUrl,
            normalizeBaseUrl = ::normalizeSubsonicBaseUrl,
        )
        val apiKeyMode = draft.authMode == SubsonicAuthMode.API_KEY
        val credential = if (apiKeyMode) {
            draft.credential.trim()
        } else {
            draft.credential
        }
        return draft.copy(
            label = draft.label.trim(),
            baseUrl = baseUrl,
            wanBaseUrl = wanBaseUrl.orEmpty(),
            username = if (apiKeyMode) "" else draft.username.trim(),
            credential = credential,
        )
    }

    private fun prepareEmbyDraft(draft: EmbySourceDraft): EmbySourceDraft {
        val (baseUrl, wanBaseUrl) = normalizeRemoteSourceBaseUrls(
            sourceType = ImportSourceType.EMBY,
            lanBaseUrl = draft.baseUrl,
            wanBaseUrl = draft.wanBaseUrl,
            normalizeBaseUrl = ::normalizeEmbyBaseUrl,
        )
        return draft.copy(
            label = draft.label.trim(),
            baseUrl = baseUrl,
            wanBaseUrl = wanBaseUrl.orEmpty(),
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
                path = joinSambaPath(draft.path, draft.selectedDirectories.singleOrNull().orEmpty()),
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
            selectedDirectories = draft.selectedDirectories,
        )
    }

    private fun createWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        createdAt: Long = now(),
        enabled: Boolean = true,
    ): ImportSource {
        val label = draft.label.ifBlank { displayWebDavRootUrl(buildWebDavDirectoryUrl(draft.rootUrl, draft.selectedDirectories.singleOrNull().orEmpty())) }
        return ImportSource(
            id = sourceId,
            type = ImportSourceType.WEBDAV,
            label = label,
            rootReference = draft.rootUrl,
            username = draft.username,
            allowInsecureTls = draft.allowInsecureTls,
            createdAt = createdAt,
            enabled = enabled,
            selectedDirectories = draft.selectedDirectories,
        )
    }

    private fun createNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        createdAt: Long = now(),
        enabled: Boolean = true,
        indexMode: ImportSourceIndexMode = ImportSourceIndexMode.LOCAL_INDEX,
    ): ImportSource {
        val label = draft.label.ifBlank { draft.baseUrl.ifBlank { draft.wanBaseUrl } }
        return ImportSource(
            id = sourceId,
            type = ImportSourceType.NAVIDROME,
            label = label,
            rootReference = draft.baseUrl,
            wanRootReference = draft.wanBaseUrl.takeIf { it.isNotBlank() },
            username = draft.username,
            createdAt = createdAt,
            enabled = enabled,
            indexMode = indexMode,
        )
    }

    private fun createSubsonicSource(
        sourceId: String,
        draft: SubsonicSourceDraft,
        createdAt: Long = now(),
        enabled: Boolean = true,
    ): ImportSource {
        val label = draft.label.ifBlank { draft.baseUrl.ifBlank { draft.wanBaseUrl } }
        return ImportSource(
            id = sourceId,
            type = ImportSourceType.SUBSONIC,
            label = label,
            rootReference = draft.baseUrl,
            wanRootReference = draft.wanBaseUrl.takeIf { it.isNotBlank() },
            username = draft.username.takeIf { draft.authMode == SubsonicAuthMode.PASSWORD },
            subsonicAuthMode = draft.authMode,
            createdAt = createdAt,
            enabled = enabled,
        )
    }

    private fun createEmbySource(
        sourceId: String,
        draft: EmbySourceDraft,
        createdAt: Long = now(),
        enabled: Boolean = true,
    ): ImportSource {
        val label = draft.label.ifBlank { draft.baseUrl.ifBlank { draft.wanBaseUrl } }
        return ImportSource(
            id = sourceId,
            type = ImportSourceType.EMBY,
            label = label,
            rootReference = draft.baseUrl,
            wanRootReference = draft.wanBaseUrl.takeIf { it.isNotBlank() },
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
            throw UiTextException(uiText(Res.string.source_name_already_exists))
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

    private suspend fun resolveUpdatedSubsonicCredential(
        existing: ImportSource,
        draft: SubsonicSourceDraft,
        keepExistingCredentialWhenBlankCredential: Boolean,
    ): String {
        if (draft.credential.isNotBlank()) return draft.credential
        if (existing.subsonicAuthMode != draft.authMode) {
            throw UiTextException(uiText(if (draft.authMode == SubsonicAuthMode.API_KEY) {
                Res.string.source_authentication_changed_api_key
            } else {
                Res.string.source_authentication_changed_password
            }))
        }
        return resolveUpdatedPassword(
            existingCredentialKey = existing.credentialKey,
            password = draft.credential,
            keepExistingCredentialWhenBlankPassword = keepExistingCredentialWhenBlankCredential,
        )
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

    private suspend fun persistOnlineNavidromeSourceWithCredential(
        source: ImportSource,
        remoteTrackCount: Int?,
        credential: String,
        shouldWriteCredential: Boolean,
        previousCredentialKey: String?,
    ): ImportScanSummary {
        val nextCredentialKey = source.credentialKey
        val wroteNewCredential = shouldWriteCredential &&
            nextCredentialKey != null &&
            nextCredentialKey != previousCredentialKey
        val writtenCredentialKey = if (wroteNewCredential) nextCredentialKey else null
        if (shouldWriteCredential && nextCredentialKey != null) {
            secureCredentialStore.put(nextCredentialKey, credential)
        }
        return try {
            val summary = persistOnlineNavidromeSource(source = source, remoteTrackCount = remoteTrackCount)
            if (previousCredentialKey != null && previousCredentialKey != nextCredentialKey) {
                runCatching { secureCredentialStore.remove(previousCredentialKey) }
            }
            summary
        } catch (throwable: Throwable) {
            if (writtenCredentialKey != null) {
                runCatching { secureCredentialStore.remove(writtenCredentialKey) }
            }
            throw throwable
        }
    }

    private suspend fun persistOnlineNavidromeSource(
        source: ImportSource,
        remoteTrackCount: Int?,
    ): ImportScanSummary {
        val savedAt = now()
        database.immediateWriteTransaction {
            val previousState = database.importIndexStateDao().getBySourceId(source.id)
            database.importSourceDao().upsert(
                source.copy(
                    lastScannedAt = savedAt,
                    indexMode = ImportSourceIndexMode.ONLINE,
                ).toEntity(),
            )
            database.importIndexStateDao().upsert(
                ImportIndexStateEntity(
                    sourceId = source.id,
                    trackCount = previousState?.trackCount ?: database.trackDao().getBySourceId(source.id).size,
                    remoteTrackCount = remoteTrackCount,
                    lastScannedAt = savedAt,
                    lastError = null,
                ),
            )
            rebuildLibrarySummaries()
        }
        return ImportScanSummary(
            sourceId = source.id,
            discoveredAudioFileCount = remoteTrackCount ?: 0,
            importedTrackCount = 0,
        )
    }

    private fun requireOnlineNavidromeRemoteTrackCount(remoteTrackCount: Int?): Int {
        return remoteTrackCount ?: throw UiTextException(uiText(Res.string.source_online_count_required))
    }

    private fun requireOnlineNavidromePaging(probe: NavidromeLibraryProbe) {
        if (!probe.supportsOnlineLibraryPaging) {
            throw UiTextException(uiText(Res.string.source_online_paging_required))
        }
    }

    private suspend fun probeExistingNavidromeSource(source: ImportSource): NavidromeLibraryProbe {
        val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        if (password.isBlank()) {
            throw UiTextException(uiText(Res.string.source_navidrome_password_missing))
        }
        val draft = NavidromeSourceDraft(
            label = source.label,
            baseUrl = source.rootReference,
            wanBaseUrl = source.wanRootReference.orEmpty(),
            username = source.username.orEmpty(),
            password = password,
        )
        addressSelector.invalidate(source.id)
        return addressSelector.withAddressFallback(
            sourceId = source.id,
            sourceType = ImportSourceType.NAVIDROME,
            lanBaseUrl = source.rootReference,
            wanBaseUrl = source.wanRootReference.orEmpty(),
            normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
        ) { candidate ->
            gateway.probeNavidrome(draft.copy(baseUrl = candidate.value))
        }
    }

    private suspend fun persistScan(source: ImportSource, report: ImportScanReport): ImportScanSummary {
        val scannedAt = now()
        val warningMessage = scanWarningsForStorage(report.warnings)
        val persistedSource = report.refreshedPersistentReference
            ?.takeIf { source.type == ImportSourceType.LOCAL_FOLDER && it.isNotBlank() }
            ?.also { refreshedReference ->
                checkUi(
                    localFolderPersistentIdentity(refreshedReference) ==
                        localFolderPersistentIdentity(source.rootReference),
                ) { uiText(Res.string.source_local_authorization_mismatch) }
            }
            ?.let { source.copy(rootReference = it) }
            ?: source
        return database.immediateWriteTransaction {
            val existingAddedAtByTrackId = database.trackDao()
                .getAddedAtBySourceId(source.id)
                .associate { it.id to it.addedAt }
            val scannedTrackEntities = report.tracks.map { candidate ->
                candidate.toTrackEntity(source.id, scannedAt, existingAddedAtByTrackId)
            }
            // Folders that couldn't be read this time keep their tracks (and so their favorites and embedded lyrics).
            val keptTracks = tracksInUnreadableFolders(source, report.unreadableFolders, scannedTrackEntities)
            val keptEmbeddedLyrics = keptTracks.map { it.id }.chunked(SQLITE_IN_CLAUSE_CHUNK_SIZE).flatMap { trackIds ->
                database.lyricsCacheDao().getByTrackIdsAndSourceId(trackIds, EMBEDDED_LYRICS_SOURCE_ID)
            }
            database.trackDao().deleteBySourceId(source.id)
            database.lyricsCacheDao().deleteByTrackIdPrefixAndSourceId(
                trackIdPrefix(source.id),
                EMBEDDED_LYRICS_SOURCE_ID,
            )
            val trackEntities = scannedTrackEntities + keptTracks

            if (trackEntities.isNotEmpty()) {
                database.trackDao().upsertAll(trackEntities)
            }
            keptEmbeddedLyrics.forEach { database.lyricsCacheDao().upsert(it) }
            report.tracks.zip(scannedTrackEntities).forEach { (candidate, entity) ->
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
            if (!isSubsonicCompatibleSourceType(source.type)) {
                database.favoriteTrackDao().deleteOrphansBySourceId(source.id)
            }
            rebuildLibrarySummaries()

            database.importSourceDao().upsert(persistedSource.copy(lastScannedAt = scannedAt).toEntity())
            database.importIndexStateDao().upsert(
                ImportIndexStateEntity(
                    sourceId = source.id,
                    trackCount = trackEntities.size,
                    remoteTrackCount = report.totalTrackCount,
                    lastScannedAt = scannedAt,
                    lastError = warningMessage,
                ),
            )
            ImportScanSummary(
                sourceId = source.id,
                discoveredAudioFileCount = report.discoveredAudioFileCount,
                importedTrackCount = scannedTrackEntities.size,
                failures = report.failures,
            )
        }
    }

    /**
     * Tracks already stored under [unreadableFolders] that this scan did not reach again. None are kept when the save moved
     * the source (another root URL, server, port or Samba root): the old paths would point somewhere else now.
     */
    private suspend fun tracksInUnreadableFolders(
        source: ImportSource,
        unreadableFolders: List<String>,
        scannedTracks: List<TrackEntity>,
    ): List<TrackEntity> {
        if (unreadableFolders.isEmpty()) return emptyList()
        val stored = database.importSourceDao().getById(source.id) ?: return emptyList()
        val updated = source.toEntity()
        // Compare what locates the files: Samba keeps its root in server/port/path columns, WebDAV in the root URL.
        val sameLocation = when (source.type) {
            // A blank port means the default one, so filling in 445 does not move the source.
            ImportSourceType.SAMBA -> stored.server == updated.server &&
                (stored.sambaPort() ?: DEFAULT_SAMBA_PORT) == (updated.sambaPort() ?: DEFAULT_SAMBA_PORT) &&
                stored.sambaRootPath() == updated.sambaRootPath()
            else -> stored.rootReference == updated.rootReference
        }
        if (!sameLocation) return emptyList()
        val scannedIds = scannedTracks.mapTo(mutableSetOf()) { it.id }
        return database.trackDao().getBySourceId(source.id).filter { track ->
            track.id !in scannedIds && unreadableFolders.any { folder -> isSameOrAncestorDirectory(folder, track.relativePath) }
        }
    }

    private suspend fun persistScanWithProgress(
        source: ImportSource,
        report: ImportScanReport,
        progressSink: ImportScanProgressSink,
    ): ImportScanSummary {
        progressSink.onProgress(
            ImportScanProgress(
                sourceId = source.id,
                phase = ImportScanPhase.Persisting,
                importedTrackCount = report.tracks.size,
                totalTrackCount = report.totalTrackCount,
            ),
        )
        return persistScan(source, report)
    }

    private suspend fun rescanNavidromeSource(
        source: ImportSource,
        progressSink: ImportScanProgressSink,
    ): ImportScanSummary {
        val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        val draft = NavidromeSourceDraft(
            label = source.label,
            baseUrl = source.rootReference,
            wanBaseUrl = source.wanRootReference.orEmpty(),
            username = source.username.orEmpty(),
            password = password,
        )
        val prepared = prepareNavidromeDraft(draft)
        return runNavidromeStreamingScan(source.copy(lastScannedAt = now()), progressSink) { trackBatchSink, resetStage ->
            addressSelector.withAddressFallback(
                sourceId = source.id,
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = prepared.baseUrl,
                wanBaseUrl = prepared.wanBaseUrl,
                normalizeBaseUrl = ::normalizeNavidromeBaseUrl,
            ) { candidate ->
                resetStage()
                gateway.scanNavidromeStreaming(
                    prepared.copy(baseUrl = candidate.value),
                    source.id,
                    progressSink,
                    trackBatchSink,
                )
            }
        }
    }

    private suspend fun runNavidromeStreamingScan(
        source: ImportSource,
        progressSink: ImportScanProgressSink,
        scan: suspend (ImportTrackBatchSink, suspend () -> Unit) -> ImportStreamingScanReport,
    ): ImportScanSummary {
        return withNavidromeSourceScanLock(source.id) {
            runNavidromeStreamingScanLocked(
                source = source,
                progressSink = progressSink,
                scan = scan,
            )
        }
    }

    private suspend fun runNavidromeStreamingScanLocked(
        source: ImportSource,
        progressSink: ImportScanProgressSink,
        scan: suspend (ImportTrackBatchSink, suspend () -> Unit) -> ImportStreamingScanReport,
    ): ImportScanSummary {
        val scannedAt = now()
        val scanId = newId("scan")
        val existingAddedAtByTrackId = database.trackDao()
            .getAddedAtBySourceId(source.id)
            .associate { it.id to it.addedAt }
        database.importTrackStageDao().deleteBySourceId(source.id)
        val batchSink = ImportTrackBatchSink { tracks ->
            if (tracks.isEmpty()) return@ImportTrackBatchSink
            database.importTrackStageDao().upsertAll(
                tracks.map { candidate ->
                    candidate.toTrackEntity(source.id, scannedAt, existingAddedAtByTrackId)
                        .toImportTrackStageEntity(scanId)
                },
            )
        }
        try {
            val report = scan(batchSink) {
                database.importTrackStageDao().deleteByScanId(scanId)
            }
            return persistStagedScanWithProgress(
                source = source.copy(lastScannedAt = scannedAt),
                report = report,
                scanId = scanId,
                progressSink = progressSink,
            )
        } catch (throwable: Throwable) {
            database.importTrackStageDao().deleteBySourceId(source.id)
            persistScanFailure(source.id, throwable)
            throw throwable
        }
    }

    private suspend fun <T> withNavidromeSourceScanLock(
        sourceId: String,
        block: suspend () -> T,
    ): T {
        val sourceLock = navidromeScanLocksMutex.withLock {
            navidromeScanLocks.getOrPut(sourceId) { Mutex() }
        }
        return sourceLock.withLock { block() }
    }

    private suspend fun persistStagedScanWithProgress(
        source: ImportSource,
        report: ImportStreamingScanReport,
        scanId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanSummary {
        val warningMessage = scanWarningsForStorage(report.warnings)
        progressSink.onProgress(
            ImportScanProgress(
                sourceId = source.id,
                phase = ImportScanPhase.Persisting,
                importedTrackCount = report.importedTrackCount,
                totalTrackCount = report.totalTrackCount,
            ),
        )
        replaceSourceTracksFromStage(source.id, scanId)
        rebuildLibrarySummaries()
        database.importSourceDao().upsert(source.toEntity())
        database.importIndexStateDao().upsert(
            ImportIndexStateEntity(
                sourceId = source.id,
                trackCount = report.importedTrackCount,
                remoteTrackCount = report.totalTrackCount,
                lastScannedAt = source.lastScannedAt,
                lastError = warningMessage,
            ),
        )
        return ImportScanSummary(
            sourceId = source.id,
            discoveredAudioFileCount = report.discoveredAudioFileCount,
            importedTrackCount = report.importedTrackCount,
            failures = report.failures,
        )
    }

    private suspend fun replaceSourceTracksFromStage(sourceId: String, scanId: String) {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                execSql("DELETE FROM track WHERE sourceId = ?", sourceId)
                execSql(
                    """
                    INSERT OR REPLACE INTO track (
                        id,
                        sourceId,
                        title,
                        artistId,
                        artistName,
                        albumId,
                        albumTitle,
                        durationMs,
                        trackNumber,
                        discNumber,
                        mediaLocator,
                        relativePath,
                        artworkLocator,
                        sizeBytes,
                        modifiedAt,
                        addedAt,
                        bitDepth,
                        samplingRate,
                        bitRate,
                        channelCount
                    )
                    SELECT
                        id,
                        sourceId,
                        title,
                        artistId,
                        artistName,
                        albumId,
                        albumTitle,
                        durationMs,
                        trackNumber,
                        discNumber,
                        mediaLocator,
                        relativePath,
                        artworkLocator,
                        sizeBytes,
                        modifiedAt,
                        addedAt,
                        bitDepth,
                        samplingRate,
                        bitRate,
                        channelCount
                    FROM import_track_stage
                    WHERE scanId = ? AND sourceId = ?
                    """.trimIndent(),
                    scanId,
                    sourceId,
                )
                execSql("DELETE FROM import_track_stage WHERE scanId = ?", scanId)
            }
        }
    }

    private suspend fun runScan(
        source: ImportSource,
        progressSink: ImportScanProgressSink,
        scan: suspend () -> ImportScanReport,
    ): ImportScanSummary {
        try {
            return persistScanWithProgress(source, scan(), progressSink)
        } catch (throwable: Throwable) {
            persistScanFailure(source.id, throwable)
            throw throwable
        }
    }

    private suspend fun persistScanFailure(sourceId: String, throwable: Throwable) {
        val errorMessage = scanErrorMessageForStorage(throwable)
        val previous = database.importIndexStateDao().getBySourceId(sourceId)
        database.importIndexStateDao().upsert(
            ImportIndexStateEntity(
                sourceId = sourceId,
                trackCount = previous?.trackCount ?: 0,
                remoteTrackCount = previous?.remoteTrackCount,
                lastScannedAt = previous?.lastScannedAt,
                lastError = errorMessage,
            ),
        )
    }

    private suspend fun validateImportSourceCreation(
        label: String,
    ) {
        val existing = database.importSourceDao().getAll()
        if (hasImportSourceNameConflict(name = label, existing = existing)) {
            throw UiTextException(uiText(Res.string.source_name_already_exists))
        }
    }

    private suspend fun validateLocalFolderImportSourceCreation(
        rootReference: String,
        excludingId: String? = null,
    ) {
        val existing = database.importSourceDao().getAll()
        if (hasLocalFolderPathConflict(
                rootReference = rootReference,
                existing = existing,
                excludingId = excludingId,
            )
        ) {
            throw UiTextException(uiText(Res.string.source_local_folder_already_imported))
        }
    }

    private suspend fun rebuildLibrarySummaries() {
        val enabledSourceIds = database.importSourceDao().getAll()
            .asSequence()
            .filter { it.isLocalIndexedEnabled() }
            .map { it.id }
            .toList()
        database.artistDao().deleteAll()
        database.albumDao().deleteAll()
        if (enabledSourceIds.isEmpty()) return

        val artistEntities = database.trackDao()
            .getArtistSummariesBySourceIds(enabledSourceIds)
            .map { row ->
                ArtistEntity(
                    id = artistIdFor(row.name),
                    name = row.name,
                    trackCount = row.trackCount,
                )
            }

        val albumEntities = database.trackDao()
            .getAlbumSummariesBySourceIds(enabledSourceIds)
            .map { row ->
                AlbumEntity(
                    id = row.id,
                    title = row.title,
                    artistName = row.artistName,
                    trackCount = row.trackCount,
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
    private val desktopLyricsPreferencesStore: DesktopLyricsPreferencesStore =
        UnsupportedDesktopLyricsPreferencesStore,
    private val menuBarLyricsControlsPreferencesStore: MenuBarLyricsControlsPreferencesStore =
        UnsupportedMenuBarLyricsControlsPreferencesStore,
    private val autoPlayOnStartupPreferencesStore: AutoPlayOnStartupPreferencesStore =
        UnsupportedAutoPlayOnStartupPreferencesStore,
    private val autoOpenPlayerOnStartupPreferencesStore: AutoOpenPlayerOnStartupPreferencesStore =
        UnsupportedAutoOpenPlayerOnStartupPreferencesStore,
    private val windowClosePreferencesStore: WindowClosePreferencesStore =
        UnsupportedWindowClosePreferencesStore,
    private val navidromeAudioQualityPreferencesStore: NavidromeAudioQualityPreferencesStore =
        UnsupportedNavidromeAudioQualityPreferencesStore,
    private val playbackDecoderPreferencesStore: PlaybackDecoderPreferencesStore =
        UnsupportedPlaybackDecoderPreferencesStore,
    private val playerArtworkStylePreferencesStore: PlayerArtworkStylePreferencesStore =
        UnsupportedPlayerArtworkStylePreferencesStore,
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
    override val showDesktopLyrics: StateFlow<Boolean> =
        desktopLyricsPreferencesStore.showDesktopLyrics
    override val showMenuBarLyricsControls: StateFlow<Boolean> =
        menuBarLyricsControlsPreferencesStore.showMenuBarLyricsControls
    override val autoPlayOnStartup: StateFlow<Boolean> =
        autoPlayOnStartupPreferencesStore.autoPlayOnStartup
    override val autoOpenPlayerOnStartup: StateFlow<Boolean> =
        autoOpenPlayerOnStartupPreferencesStore.autoOpenPlayerOnStartup
    override val minimizeWindowOnClose: StateFlow<Boolean> =
        windowClosePreferencesStore.minimizeWindowOnClose
    override val appDisplayScalePreset: StateFlow<AppDisplayScalePreset> =
        appDisplayPreferencesStore.appDisplayScalePreset
    override val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality> =
        navidromeAudioQualityPreferencesStore.navidromeWifiAudioQuality
    override val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality> =
        navidromeAudioQualityPreferencesStore.navidromeMobileAudioQuality
    override val useAndroidExtensionDecoder: StateFlow<Boolean> =
        playbackDecoderPreferencesStore.useAndroidExtensionDecoder
    override val playerArtworkStyle: StateFlow<PlayerArtworkStyle> =
        playerArtworkStylePreferencesStore.playerArtworkStyle
    override val selectedTheme: StateFlow<AppThemeId> = themePreferencesStore.selectedTheme
    override val customThemeTokens: StateFlow<AppThemeTokens> = themePreferencesStore.customThemeTokens
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = themePreferencesStore.textPalettePreferences
    override val desktopVlcAutoDetectedPath: StateFlow<String?> = desktopVlcPreferencesStore.desktopVlcAutoDetectedPath
    override val desktopVlcManualPath: StateFlow<String?> = desktopVlcPreferencesStore.desktopVlcManualPath
    override val desktopVlcEffectivePath: StateFlow<String?> = desktopVlcPreferencesStore.desktopVlcEffectivePath

    override suspend fun ensureDefaults() {
        val existing = database.lyricsSourceConfigDao().getAll()
        if (existing.isEmpty()) {
            seedDefaultDirectLyricsSources(defaultLyricsSourceConfigs())
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

            seedDefaultDirectLyricsSources(
                defaultLyricsSourceConfigs().filter { it.id == MANAGED_LRCAPI_SOURCE_ID },
            )
        }
        seedDefaultWorkflowLyricsSources()
    }

    override suspend fun setUseSambaCache(enabled: Boolean) {
        sambaCachePreferencesStore.setUseSambaCache(enabled)
    }

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        compactPlayerLyricsPreferencesStore.setShowCompactPlayerLyrics(enabled)
    }

    override suspend fun setShowDesktopLyrics(enabled: Boolean) {
        desktopLyricsPreferencesStore.setShowDesktopLyrics(enabled)
    }

    override suspend fun setShowMenuBarLyricsControls(enabled: Boolean) {
        menuBarLyricsControlsPreferencesStore.setShowMenuBarLyricsControls(enabled)
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        autoPlayOnStartupPreferencesStore.setAutoPlayOnStartup(enabled)
    }

    override suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean) {
        autoOpenPlayerOnStartupPreferencesStore.setAutoOpenPlayerOnStartup(enabled)
    }

    override suspend fun setMinimizeWindowOnClose(enabled: Boolean) {
        windowClosePreferencesStore.setMinimizeWindowOnClose(enabled)
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

    override suspend fun setUseAndroidExtensionDecoder(enabled: Boolean) {
        playbackDecoderPreferencesStore.setUseAndroidExtensionDecoder(enabled)
    }

    override suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle) {
        playerArtworkStylePreferencesStore.setPlayerArtworkStyle(style)
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
            throw UiTextException(uiText(Res.string.lyrics_workflow_id_immutable))
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
            throw UiTextException(uiText(Res.string.lyrics_source_name_required))
        }
        val directConflict = database.lyricsSourceConfigDao().getAll().any { entity ->
            entity.id != currentDirectId && normalizeLyricsSourceName(entity.name) == normalizedTarget
        }
        val workflowConflict = database.workflowLyricsSourceConfigDao().getAll().any { entity ->
            entity.id != currentWorkflowId && normalizeLyricsSourceName(entity.name) == normalizedTarget
        }
        if (directConflict || workflowConflict) {
            throw UiTextException(uiText(Res.string.lyrics_source_name_already_exists))
        }
    }

    private suspend fun assertSourceIdAvailable(
        sourceId: String,
        currentWorkflowId: String? = null,
    ) {
        val hasDirectConflict = database.lyricsSourceConfigDao().getAll().any { it.id == sourceId }
        val hasWorkflowConflict = database.workflowLyricsSourceConfigDao().getAll().any { it.id == sourceId && it.id != currentWorkflowId }
        if (hasDirectConflict || hasWorkflowConflict) {
            throw UiTextException(uiText(Res.string.lyrics_source_id_already_exists))
        }
    }

    private suspend fun seedDefaultDirectLyricsSources(configs: List<LyricsSourceConfig>) {
        val directConfigs = database.lyricsSourceConfigDao().getAll()
        val workflowConfigs = database.workflowLyricsSourceConfigDao().getAll()
        val reservedIds = directConfigs.mapTo(mutableSetOf()) { it.id }
        val reservedNames = directConfigs.mapTo(mutableSetOf()) { normalizeLyricsSourceName(it.name) }
        workflowConfigs.forEach { entity ->
            reservedIds += entity.id
            reservedNames += normalizeLyricsSourceName(entity.name)
        }
        configs.forEach { config ->
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
        override suspend fun cache(locator: String, cacheKey: String, policy: ArtworkWritePolicy): ArtworkCacheResult? = ArtworkCacheResult(locator, true)
    },
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    private val addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
) : LyricsRepository {
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? {
        val trackLabel = track.logIdentity()
        val cachedRows = database.lyricsCacheDao().getByTrack(track.id)
        logger.debug(LYRICS_LOG_TAG) {
            "cache-lookup track=$trackLabel rows=${cachedRows.size} sources=${cachedRows.joinToString { it.sourceId }}"
        }
        val manualOverride = cachedRows.firstOrNull { it.sourceId == MANUAL_LYRICS_OVERRIDE_SOURCE_ID }
        val manualArtworkOverride = normalizeArtworkLocator(manualOverride?.artworkLocator)
        val albumArtworkCacheState = trackArtworkCacheState(track)
        val hasRealLocalAlbumArtworkCache = albumArtworkCacheState.hasRealCachedArtwork
        manualOverride
            ?.let(::resolveCachedLyrics)
            ?.let { resolved ->
                logCacheHit(trackLabel, resolved.document)
                return resolved.withArtworkOverride(manualArtworkOverride)
            }
        val embyLocator = parseEmbySongLocator(track.mediaLocator)
        if (embyLocator != null) {
            cachedRows
                .firstOrNull { it.sourceId == EMBY_LYRICS_SOURCE_ID }
                ?.let { row ->
                    resolveCachedLyricsForTrack(
                        track = track,
                        row = row,
                        suppressArtwork = hasRealLocalAlbumArtworkCache,
                    )
                }
                ?.let { resolved ->
                    logCacheHit(trackLabel, resolved.document)
                    return resolved.withArtworkOverride(manualArtworkOverride)
                }

            cachedRows
                .firstNotNullOfOrNull { cache ->
                    cache.takeUnless { it.sourceId == EMBY_LYRICS_SOURCE_ID }
                        ?.let { row ->
                            resolveCachedLyricsForTrack(
                                track = track,
                                row = row,
                                suppressArtwork = hasRealLocalAlbumArtworkCache,
                            )
                        }
                }
                ?.let { resolved ->
                    logCacheHit(trackLabel, resolved.document)
                    return resolved.withArtworkOverride(manualArtworkOverride)
                }
            logger.debug(LYRICS_LOG_TAG) { "cache-miss track=$trackLabel fallback=emby-server" }
            requestEmbyLyricsDocumentForPlayback(track)?.let { embyLyrics ->
                storeLyricsDocument(track.id, embyLyrics)
                logger.info(LYRICS_LOG_TAG) {
                    "resolved track=$trackLabel source=${embyLyrics.sourceId} synced=${embyLyrics.isSynced} lines=${embyLyrics.lines.size}"
                }
                return ResolvedLyricsResult(document = embyLyrics)
                    .withArtworkOverride(manualArtworkOverride)
            }
        }
        val subsonicLocator = parseSubsonicCompatibleSongLocator(track.mediaLocator)
        val subsonicLyricsSourceId = subsonicLocator?.sourceType?.let(::lyricsSourceIdFor)
        if (embyLocator == null && subsonicLocator != null && subsonicLyricsSourceId != null) {
            cachedRows
                .firstOrNull { it.sourceId == subsonicLyricsSourceId }
                ?.let { row ->
                    resolveCachedLyricsForTrack(
                        track = track,
                        row = row,
                        suppressArtwork = hasRealLocalAlbumArtworkCache,
                    )
                }
                ?.let { resolved ->
                    logCacheHit(trackLabel, resolved.document)
                    return resolved.withArtworkOverride(manualArtworkOverride)
                }

            cachedRows
                .firstNotNullOfOrNull { cache ->
                    cache.takeUnless { it.sourceId == subsonicLyricsSourceId }
                        ?.let { row ->
                            resolveCachedLyricsForTrack(
                                track = track,
                                row = row,
                                suppressArtwork = hasRealLocalAlbumArtworkCache,
                            )
                        }
                }
                ?.let { resolved ->
                    logCacheHit(trackLabel, resolved.document)
                    return resolved.withArtworkOverride(manualArtworkOverride)
                }
            logger.debug(LYRICS_LOG_TAG) { "cache-miss track=$trackLabel fallback=subsonic-server" }
            requestNavidromeLyricsDocumentForPlayback(track)?.let { subsonicLyrics ->
                storeLyricsDocument(track.id, subsonicLyrics)
                logger.info(LYRICS_LOG_TAG) {
                    "resolved track=$trackLabel source=${subsonicLyrics.sourceId} synced=${subsonicLyrics.isSynced} lines=${subsonicLyrics.lines.size}"
                }
                return ResolvedLyricsResult(document = subsonicLyrics)
                    .withArtworkOverride(manualArtworkOverride)
            }
        } else {
            resolveSameNameLyricsForPlayback(track, cachedRows)?.let { resolved ->
                return resolved.withArtworkOverride(manualArtworkOverride)
            }
            cachedRows
                .firstNotNullOfOrNull { cache ->
                    cache.takeUnless { it.sourceId == SAME_NAME_LRC_SOURCE_ID }
                        ?.let { row ->
                            resolveCachedLyricsForTrack(
                                track = track,
                                row = row,
                                suppressArtwork = hasRealLocalAlbumArtworkCache,
                            )
                        }
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

        return resolveNetworkLyricsForTrack(
            track = track,
            requestType = "auto",
            manualArtworkOverride = manualArtworkOverride,
            suppressArtwork = hasRealLocalAlbumArtworkCache,
            storeResult = true,
        )
    }

    override suspend fun resolveNetworkLyrics(metadata: LyricsLookupMetadata): ResolvedLyricsResult? {
        val lookupTrack = metadata.toNetworkLyricsLookupTrack() ?: return null
        return resolveNetworkLyricsForTrack(
            track = lookupTrack,
            requestType = "cast-auto",
            manualArtworkOverride = null,
            suppressArtwork = false,
            storeResult = false,
        )
    }

    private suspend fun resolveNetworkLyricsForTrack(
        track: Track,
        requestType: String,
        manualArtworkOverride: String?,
        suppressArtwork: Boolean,
        storeResult: Boolean,
    ): ResolvedLyricsResult? {
        val trackLabel = track.logIdentity()
        val sources = enabledLyricsSources()
        val logPrefix = requestType.takeUnless { it == "auto" }?.let { "$it-" }.orEmpty()
        if (sources.isEmpty()) {
            logger.warn(LYRICS_LOG_TAG) { "${logPrefix}no-enabled-sources track=$trackLabel" }
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
                            requestType = requestType,
                        ),
                        selection = DEFAULT_DIRECT_LYRICS_SELECTION,
                        syncedBonus = AUTO_DIRECT_LYRICS_SYNCED_BONUS,
                    )
                    val topCandidate = rankedCandidates.firstOrNull()
                    val matchedCandidate = topCandidate
                        ?.takeIf { it.score >= DEFAULT_DIRECT_LYRICS_SELECTION.minScore }
                        ?.candidate
                    logger.debug(LYRICS_LOG_TAG) {
                        "$requestType-direct-ranked track=$trackLabel source=${source.id} candidates=${rankedCandidates.size} " +
                            "topScore=${topCandidate?.score.logScore()} matched=${matchedCandidate != null} " +
                            "itemId=${matchedCandidate?.itemId.orEmpty()}"
                    }
                    matchedCandidate?.let { parsed ->
                        val artworkLocator = normalizeArtworkLocator(parsed.artworkLocator)
                        ResolvedLyricsResult(
                            document = parsed.document,
                            artworkLocator = artworkLocator,
                        )
                    }
                }

                is WorkflowLyricsSourceConfig -> requestWorkflowLyricsDocument(
                    track = track,
                    config = source,
                    requestType = requestType,
                )
            } ?: continue
            val automaticArtworkLocator = sourceResult.artworkLocator.takeUnless { suppressArtwork }
            val result = sourceResult
                .copy(artworkLocator = automaticArtworkLocator)
                .withArtworkOverride(manualArtworkOverride)
            if (storeResult) {
                storeLyricsDocument(
                    track.id,
                    sourceResult.document,
                    artworkLocator = automaticArtworkLocator,
                )
            }
            logger.info(LYRICS_LOG_TAG) {
                "${logPrefix}resolved track=$trackLabel source=${source.id} synced=${result.document.isSynced} " +
                    "lines=${result.document.lines.size} artworkLocator=${result.artworkLocator.orEmpty()}"
            }
            return result
        }
        logger.warn(LYRICS_LOG_TAG) {
            "${logPrefix}miss track=$trackLabel attempted=${sources.joinToString(",") { it.id }}"
        }
        return null
    }

    private suspend fun requestNavidromeLyricsDocumentForPlayback(track: Track): LyricsDocument? {
        return runCatching { requestNavidromeLyricsDocument(track) }
            .onFailure { throwable ->
                throwable.throwIfCancellation()
                logger.warn(LYRICS_LOG_TAG) {
                    "playback-subsonic-lyrics-failed track=${track.logIdentity()} reason=${throwable.message.orEmpty()}"
                }
            }
            .getOrNull()
    }

    private suspend fun requestNavidromeLyricsDocument(track: Track): LyricsDocument? {
        val locator = parseSubsonicCompatibleSongLocator(track.mediaLocator) ?: return null
        val source = database.importSourceDao().getById(locator.sourceId)
            ?.takeIf { it.type == locator.sourceType.name && it.enabled }
            ?: return null
        val sourceType = runCatching { ImportSourceType.valueOf(source.type) }.getOrNull() ?: return null
        if (!isSubsonicCompatibleSourceType(sourceType)) return null
        val authMode = source.authMode.toSubsonicAuthMode()
        val username = source.username?.trim().orEmpty()
        val credential = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        if (authMode == SubsonicAuthMode.PASSWORD && (username.isBlank() || credential.isBlank())) return null
        if (authMode == SubsonicAuthMode.API_KEY && credential.isBlank()) return null
        return requestNavidromeLyrics(
            httpClient = httpClient,
            source = NavidromeResolvedSource(
                baseUrl = source.rootReference,
                wanBaseUrl = source.wanRootReference,
                sourceId = source.id,
                addressSelector = addressSelector,
                username = username,
                password = credential,
                authMode = authMode,
                sourceType = sourceType,
            ),
            track = track,
            logger = logger,
        )
    }

    private suspend fun requestEmbyLyricsDocument(track: Track): LyricsDocument? {
        val locator = parseEmbySongLocator(track.mediaLocator) ?: return null
        if (locator.first != track.sourceId) return null
        val source = resolveEmbySource(database, secureCredentialStore, locator.first, addressSelector) ?: return null
        return requestEmbyServerLyricsDocument(
            httpClient = httpClient,
            source = source,
            itemId = locator.second,
            logger = logger,
        )
    }

    private suspend fun requestEmbyLyricsDocumentForPlayback(track: Track): LyricsDocument? {
        return runCatching { requestEmbyLyricsDocument(track) }
            .onFailure { throwable ->
                throwable.throwIfCancellation()
                logger.warn(LYRICS_LOG_TAG) {
                    "playback-emby-lyrics-failed track=${track.logIdentity()} reason=${throwable.message.orEmpty()}"
                }
            }
            .getOrNull()
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
            .sortedWith(
                compareByDescending<ScoredManualDirectLyricsCandidate> { it.score }
                    .thenByDescending { lyricsArtworkTieBreakScore(it.candidate.artworkLocator) }
                    .thenBy { it.originalIndex },
            )

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
            rankWorkflowSongCandidates(
                track = track,
                candidates = searchWorkflowCandidates(track, config, requestType = "manual"),
                selection = config.selection,
                enforceMinScore = false,
            )
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
                    sourceArtwork?.let {
                        cacheArtworkLocator(
                            trackId = trackId,
                            sourceKey = candidate.sourceId,
                            candidateKey = artworkCandidateKey,
                            sourceLocator = it,
                            replaceExisting = true,
                        )
                    }
                    sourceArtwork
                } else {
                    val normalizedArtworkLocator = cacheArtworkLocator(
                        trackId = trackId,
                        sourceKey = candidate.sourceId,
                        candidateKey = artworkCandidateKey,
                        sourceLocator = candidate.artworkLocator,
                        replaceExisting = true,
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
                    val sourceTrackArtwork = sourceArtwork ?: throw UiTextException(uiText(Res.string.lyrics_artwork_unavailable))
                    val artworkOverride = sourceTrackArtwork.takeUnless { it == trackArtwork }
                    cacheArtworkLocator(
                        trackId = trackId,
                        sourceKey = candidate.sourceId,
                        candidateKey = artworkCandidateKey,
                        sourceLocator = sourceTrackArtwork,
                        replaceExisting = true,
                    )
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
                        sourceLocator = candidate.artworkLocator ?: throw UiTextException(uiText(Res.string.lyrics_artwork_unavailable)),
                        replaceExisting = true,
                    ) ?: throw UiTextException(uiText(Res.string.lyrics_artwork_unavailable))
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
        return if (parseSubsonicCompatibleSongLocator(track.mediaLocator) != null) {
            listOfNotNull(buildNavidromeTrackProvidedLyricsCandidate(track))
        } else if (parseEmbySongLocator(track.mediaLocator) != null) {
            listOfNotNull(buildEmbyTrackProvidedLyricsCandidate(track))
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
            sourceName = if (document.sourceId == SUBSONIC_LYRICS_SOURCE_ID) "Subsonic" else "Navidrome",
            document = document,
            title = track.title.takeIf { it.isNotBlank() },
            artistName = track.artistName?.takeIf { it.isNotBlank() },
            albumTitle = track.albumTitle?.takeIf { it.isNotBlank() },
            durationSeconds = track.durationSecondsOrNull(),
            artworkLocator = normalizeArtworkLocator(track.artworkLocator),
            isTrackProvided = true,
        )
    }

    private suspend fun buildEmbyTrackProvidedLyricsCandidate(track: Track): LyricsSearchCandidate? {
        val document = runCatching { requestEmbyLyricsDocument(track) }
            .onFailure { throwable ->
                logger.warn(LYRICS_LOG_TAG) {
                    "manual-track-provided-emby-failed track=${track.logIdentity()} reason=${throwable.message.orEmpty()}"
                }
            }
            .getOrNull()
            ?: return null
        return LyricsSearchCandidate(
            sourceId = document.sourceId,
            sourceName = "Emby",
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
            sourceNameText = uiText(Res.string.lyrics_source_same_name_file),
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
        if (parseSubsonicCompatibleSongLocator(track.mediaLocator) != null) return SameNameLyricsLookup.Missing
        if (parseEmbySongLocator(track.mediaLocator) != null) return SameNameLyricsLookup.Missing
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
                sourceNameText = uiText(Res.string.lyrics_source_song_tags),
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
            sourceNameText = uiText(Res.string.lyrics_source_song_tags),
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
            ?: throw UiTextException(uiText(Res.string.workflow_source_missing, candidate.sourceId))
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
        ) ?: throw UiTextException(uiText(Res.string.workflow_lyrics_unavailable, candidate.sourceName))
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
                    replaceExisting = true,
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
                    sourceLocator = candidate.imageUrl ?: throw UiTextException(uiText(Res.string.workflow_artwork_unavailable, candidate.sourceName)),
                    replaceExisting = true,
                ) ?: throw UiTextException(uiText(Res.string.workflow_artwork_unavailable, candidate.sourceName))
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
            ?: throw UiTextException(uiText(Res.string.workflow_source_missing, candidate.sourceId))
        val document = fetchWorkflowLyricsForCandidate(
            track = track,
            config = config,
            candidate = candidate,
            requestType = "tag-import",
        ) ?: throw UiTextException(uiText(Res.string.workflow_lyrics_unavailable, candidate.sourceName))
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
        replaceExisting: Boolean = false,
    ): String? {
        val normalizedLocator = normalizeArtworkLocator(sourceLocator)?.trim().orEmpty()
        if (normalizedLocator.isBlank()) return null
        val track = database.trackDao().getByIds(listOf(trackId)).firstOrNull()?.toDomain()
        val trackCacheKey = track?.let(::trackArtworkCacheKey)
        val fallbackToLocatorKey = trackCacheKey == null
        val cacheKey = trackCacheKey ?: normalizedLocator
        if (fallbackToLocatorKey) {
            logger.warn(LYRICS_LOG_TAG) {
                "artwork-cache-key-fallback track=$trackId source=$sourceKey candidate=$candidateKey replace=$replaceExisting " +
                    "trackFound=${track != null} key=$cacheKey locator=$normalizedLocator " +
                    "trackSource=${track?.sourceId.orEmpty()} albumId=${track?.albumId.orEmpty()} " +
                    "albumTitle=${track?.albumTitle.orEmpty()} artist=${track?.artistName.orEmpty()} " +
                    "trackArtwork=${track?.artworkLocator.orEmpty()}"
            }
        } else {
            logger.debug(LYRICS_LOG_TAG) {
                "artwork-cache-key-resolved track=$trackId source=$sourceKey candidate=$candidateKey replace=$replaceExisting " +
                    "key=$cacheKey locator=$normalizedLocator trackSource=${track.sourceId} " +
                    "albumId=${track.albumId.orEmpty()} albumTitle=${track.albumTitle.orEmpty()} " +
                    "artist=${track.artistName.orEmpty()} trackArtwork=${track.artworkLocator.orEmpty()}"
            }
        }
        val cachedLocator = runCatching {
            artworkCacheStore.cache(
                locator = normalizedLocator,
                cacheKey = cacheKey,
                policy = if (replaceExisting) ArtworkWritePolicy.Replace else ArtworkWritePolicy.KeepExisting,
            )?.locator
        }.onFailure { throwable ->
            throwable.throwIfCancellation()
            logger.error(LYRICS_LOG_TAG, throwable) {
                "artwork-cache-failed track=$trackId source=$sourceKey candidate=$candidateKey replace=$replaceExisting " +
                    "key=$cacheKey fallbackToLocator=$fallbackToLocatorKey url=$normalizedLocator"
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (cachedLocator != null) {
            logger.debug(LYRICS_LOG_TAG) {
                "artwork-cache-hit track=$trackId source=$sourceKey candidate=$candidateKey replace=$replaceExisting " +
                    "key=$cacheKey fallbackToLocator=$fallbackToLocatorKey locator=$cachedLocator"
            }
        }
        return normalizedLocator
    }

    private fun resolveCachedLyrics(
        row: LyricsCacheEntity,
        suppressArtwork: Boolean = false,
    ): ResolvedLyricsResult? {
        return parseCachedLyrics(row.sourceId, row.rawPayload)?.let { cached ->
            ResolvedLyricsResult(
                document = cached,
                artworkLocator = normalizeArtworkLocator(row.artworkLocator).takeUnless { suppressArtwork },
            )
        }
    }

    private suspend fun resolveCachedLyricsForTrack(
        track: Track,
        row: LyricsCacheEntity,
        suppressArtwork: Boolean = false,
    ): ResolvedLyricsResult? {
        val artworkLocator = normalizeArtworkLocator(row.artworkLocator)?.trim().orEmpty()
        val currentCacheState = artworkLocator
            .takeIf { it.isNotBlank() }
            ?.let { trackArtworkCacheState(track) }
        return resolveCachedLyrics(
            row = row,
            suppressArtwork = suppressArtwork || currentCacheState?.hasRealCachedArtwork == true,
        )
    }

    private data class TrackArtworkCacheState(
        val hasCached: Boolean = false,
        val hasReplaceableNavidromePlaceholder: Boolean = false,
    ) {
        val hasRealCachedArtwork: Boolean
            get() = hasCached && !hasReplaceableNavidromePlaceholder
    }

    private suspend fun trackArtworkCacheState(track: Track): TrackArtworkCacheState {
        val cacheKey = trackArtworkCacheKey(track)
        val isSubsonicCompatible = parseSubsonicCompatibleSongLocator(track.mediaLocator) != null
        if (cacheKey == null) {
            logger.warn(LYRICS_LOG_TAG) {
                "artwork-cache-state track=${track.id} key=<none> hasCached=false hasReplaceable=false " +
                    "hasReal=false isSubsonicCompatible=$isSubsonicCompatible source=${track.sourceId} albumId=${track.albumId.orEmpty()} " +
                    "albumTitle=${track.albumTitle.orEmpty()} artist=${track.artistName.orEmpty()} " +
                    "trackArtwork=${track.artworkLocator.orEmpty()}"
            }
            return TrackArtworkCacheState()
        }
        val hasCached = runCatching { artworkCacheStore.hasCached(cacheKey) }
            .onFailure { error ->
                error.throwIfCancellation()
                logger.warn(LYRICS_LOG_TAG) { "artwork-cache-check-failed track=${track.id} reason=${error.message.orEmpty()}" }
            }.getOrDefault(false)
        val hasReplaceablePlaceholder = hasCached &&
            isSubsonicCompatible &&
            runCatching {
                artworkCacheStore.hasReplaceableNavidromePlaceholderCached(cacheKey)
            }.onFailure { error ->
                error.throwIfCancellation()
                logger.warn(LYRICS_LOG_TAG) { "artwork-placeholder-check-failed track=${track.id} reason=${error.message.orEmpty()}" }
            }.getOrDefault(false)
        val state = TrackArtworkCacheState(
            hasCached = hasCached,
            hasReplaceableNavidromePlaceholder = hasReplaceablePlaceholder,
        )
        logger.debug(LYRICS_LOG_TAG) {
            "artwork-cache-state track=${track.id} key=$cacheKey hasCached=$hasCached " +
                "hasReplaceable=$hasReplaceablePlaceholder hasReal=${state.hasRealCachedArtwork} " +
                "isSubsonicCompatible=$isSubsonicCompatible source=${track.sourceId} albumId=${track.albumId.orEmpty()} " +
                "albumTitle=${track.albumTitle.orEmpty()} artist=${track.artistName.orEmpty()} " +
                "trackArtwork=${track.artworkLocator.orEmpty()}"
        }
        return state
    }

    private fun logCacheHit(trackLabel: String, document: LyricsDocument) {
        logger.debug(LYRICS_LOG_TAG) {
            "cache-hit track=$trackLabel source=${document.sourceId} synced=${document.isSynced} lines=${document.lines.size}"
        }
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
                throwable.throwIfCancellation()
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
        val rankedCandidates = rankWorkflowSongCandidates(
            track = track,
            candidates = candidates,
            selection = config.selection,
        )
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
            val artworkLocator = normalizeArtworkLocator(candidate.imageUrl)
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
                throwable.throwIfCancellation()
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
                    throwable.throwIfCancellation()
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
                    throwable.throwIfCancellation()
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

private fun Throwable.throwIfCancellation() {
    if (this is CancellationException) throw this
}

private const val LYRICS_LOG_TAG = "Lyrics"
private const val NETWORK_LYRICS_LOOKUP_SOURCE_ID = "network-lyrics"
const val MANUAL_LYRICS_OVERRIDE_SOURCE_ID = "manual-override"
const val SAME_NAME_LRC_SOURCE_ID = "same-name-lrc"
internal const val EMBEDDED_LYRICS_SOURCE_ID = "embedded-tag"

internal fun newId(prefix: String): String = "$prefix-${now()}-${Random.nextInt(1000, 9999)}"

private fun artistIdFor(name: String): String = "artist:${name.trim().lowercase()}"

private fun albumIdFor(artistName: String?, albumTitle: String): String {
    return "album:${artistName.orEmpty().trim().lowercase()}:${albumTitle.trim().lowercase()}"
}

private fun ImportedTrackCandidate.toTrackEntity(
    sourceId: String,
    scannedAt: Long,
    existingAddedAtByTrackId: Map<String, Long>,
): TrackEntity {
    val artistId = artistName?.takeIf { it.isNotBlank() }?.let(::artistIdFor)
    val albumId = albumTitle?.takeIf { it.isNotBlank() }?.let {
        albumIdFor(artistName, it)
    }
    val trackId = trackIdFor(sourceId, relativePath, mediaLocator)
    return TrackEntity(
        id = trackId,
        sourceId = sourceId,
        title = title,
        artistId = artistId,
        artistName = artistName,
        albumId = albumId,
        albumTitle = albumTitle,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        mediaLocator = mediaLocator,
        relativePath = relativePath,
        artworkLocator = artworkLocator,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        addedAt = existingAddedAtByTrackId[trackId] ?: scannedAt,
        bitDepth = bitDepth,
        samplingRate = samplingRate,
        bitRate = bitRate,
        channelCount = channelCount,
    )
}

private fun TrackEntity.toImportTrackStageEntity(scanId: String): ImportTrackStageEntity {
    return ImportTrackStageEntity(
        scanId = scanId,
        id = id,
        sourceId = sourceId,
        title = title,
        artistId = artistId,
        artistName = artistName,
        albumId = albumId,
        albumTitle = albumTitle,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        mediaLocator = mediaLocator,
        relativePath = relativePath,
        artworkLocator = artworkLocator,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        addedAt = addedAt,
        bitDepth = bitDepth,
        samplingRate = samplingRate,
        bitRate = bitRate,
        channelCount = channelCount,
    )
}

private suspend fun PooledConnection.execSql(sql: String, vararg args: Any?) {
    usePrepared(sql) { statement ->
        args.forEachIndexed { index, value ->
            statement.bindValue(index + 1, value)
        }
        statement.step()
    }
}

private fun SQLiteStatement.bindValue(index: Int, value: Any?) {
    when (value) {
        null -> bindNull(index)
        is String -> bindText(index, value)
        is Int -> bindLong(index, value.toLong())
        is Long -> bindLong(index, value)
        is Boolean -> bindBoolean(index, value)
        else -> error("Unsupported SQLite bind value: ${value::class.simpleName}")
    }
}

internal fun navidromeTrackIdFor(sourceId: String, songId: String): String {
    return "track:${sourceId}:navidrome:${songId.lowercase()}"
}

internal fun subsonicTrackIdFor(sourceId: String, songId: String): String {
    return "track:${sourceId}:subsonic:${songId.lowercase()}"
}

internal fun subsonicCompatibleTrackIdFor(
    sourceId: String,
    songId: String,
    sourceType: ImportSourceType,
): String {
    return if (sourceType == ImportSourceType.SUBSONIC) {
        subsonicTrackIdFor(sourceId, songId)
    } else {
        navidromeTrackIdFor(sourceId, songId)
    }
}

internal fun embyTrackIdFor(sourceId: String, itemId: String): String {
    return "track:${sourceId}:emby:${itemId.lowercase()}"
}

private fun trackIdFor(sourceId: String, relativePath: String, mediaLocator: String): String {
    val subsonicSong = parseSubsonicCompatibleSongLocator(mediaLocator)
    if (subsonicSong != null) {
        return subsonicCompatibleTrackIdFor(sourceId, subsonicSong.itemId, subsonicSong.sourceType)
    }
    val embySong = parseEmbySongLocator(mediaLocator)
    if (embySong != null) {
        return embyTrackIdFor(sourceId, embySong.second)
    }
    return "track:${sourceId}:${relativePath.lowercase()}"
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

private fun String.directArtworkCandidateFallbackKey(): String {
    return if (this == "auto") "auto-direct" else this
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
        addedAt = addedAt,
        bitDepth = bitDepth,
        samplingRate = samplingRate,
        bitRate = bitRate,
        channelCount = channelCount,
        albumId = albumId,
        artistId = artistId,
    )
}

private fun LyricsLookupMetadata.toNetworkLyricsLookupTrack(): Track? {
    val normalizedTitle = title.trim().takeIf { it.isNotEmpty() } ?: return null
    val normalizedArtist = artistName?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedAlbum = albumTitle?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedDurationMs = durationMs.coerceAtLeast(0L)
    val key = listOf(
        normalizedTitle,
        normalizedArtist.orEmpty(),
        normalizedAlbum.orEmpty(),
        normalizedDurationMs.toString(),
    ).joinToString("|")
    return Track(
        id = "network-lyrics:${key.hashCode()}",
        sourceId = NETWORK_LYRICS_LOOKUP_SOURCE_ID,
        title = normalizedTitle,
        artistName = normalizedArtist,
        albumTitle = normalizedAlbum,
        durationMs = normalizedDurationMs,
        mediaLocator = "",
        relativePath = "",
    )
}

private fun ImportSourceEntity.toDomain(): ImportSource {
    val parsedPort = sambaPort()
    val migratedPath = sambaRootPath()
    return ImportSource(
        id = id,
        type = type.toImportSourceType(),
        label = label,
        rootReference = rootReference,
        wanRootReference = wanRootReference,
        server = server,
        port = parsedPort,
        path = migratedPath.ifBlank { null }.takeIf { type.toImportSourceType() == ImportSourceType.SAMBA },
        username = username,
        credentialKey = credentialKey,
        subsonicAuthMode = authMode.toSubsonicAuthMode(),
        allowInsecureTls = allowInsecureTls,
        enabled = enabled,
        lastScannedAt = lastScannedAt,
        createdAt = createdAt,
        indexMode = indexMode.toImportSourceIndexMode(),
        selectedDirectories = decodeSelectedDirectories(selectedDirectories),
    )
}

private fun ImportSource.toEntity(): ImportSourceEntity {
    return ImportSourceEntity(
        id = id,
        type = type.name,
        label = label,
        rootReference = rootReference,
        wanRootReference = wanRootReference?.trim()?.takeIf { it.isNotBlank() },
        server = server,
        shareName = port?.toString().takeIf { type == ImportSourceType.SAMBA },
        directoryPath = normalizeSambaPath(path).ifBlank { null }.takeIf { type == ImportSourceType.SAMBA },
        username = username,
        credentialKey = credentialKey,
        allowInsecureTls = allowInsecureTls,
        enabled = enabled,
        lastScannedAt = lastScannedAt,
        createdAt = createdAt,
        authMode = subsonicAuthMode.name,
        indexMode = indexMode.name,
        selectedDirectories = encodeSelectedDirectories(selectedDirectories),
    )
}

private fun ImportIndexStateEntity.toDomain(): ImportIndexState {
    return ImportIndexState(
        sourceId = sourceId,
        trackCount = trackCount,
        remoteTrackCount = remoteTrackCount,
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

private fun uniqueImportSourceLabel(
    preferredLabel: String,
    existing: List<ImportSourceEntity>,
): String {
    val baseLabel = preferredLabel.trim().ifBlank { "本地音乐" }
    val normalizedExistingLabels = existing
        .mapTo(mutableSetOf()) { normalizeImportSourceLabel(it.label) }
    if (normalizeImportSourceLabel(baseLabel) !in normalizedExistingLabels) return baseLabel

    var suffix = 2
    while (true) {
        val candidate = "$baseLabel ($suffix)"
        if (normalizeImportSourceLabel(candidate) !in normalizedExistingLabels) return candidate
        suffix += 1
    }
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
    val identity = localFolderPersistentIdentity(rootReference)
    return existing.any { entity ->
        entity.id != excludingId &&
            entity.type == ImportSourceType.LOCAL_FOLDER.name &&
            localFolderPersistentIdentity(entity.rootReference) == identity
    }
}

private fun String.toImportSourceType(): ImportSourceType {
    return runCatching { ImportSourceType.valueOf(this) }.getOrDefault(ImportSourceType.LOCAL_FOLDER)
}

internal fun String.toImportSourceIndexMode(): ImportSourceIndexMode {
    return runCatching { ImportSourceIndexMode.valueOf(this) }.getOrDefault(ImportSourceIndexMode.LOCAL_INDEX)
}

internal fun ImportSourceEntity.isLocalIndexedEnabled(): Boolean {
    return enabled && indexMode.toImportSourceIndexMode() == ImportSourceIndexMode.LOCAL_INDEX
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
        buildManagedLrcApiConfig(DEFAULT_LRCAPI_URL),
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

/** Keeps `IN (...)` queries under SQLite's bound-parameter limit. */
private const val SQLITE_IN_CLAUSE_CHUNK_SIZE = 500
