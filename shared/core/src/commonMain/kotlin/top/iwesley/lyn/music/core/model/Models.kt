package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*

import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class AppTab {
    My,
    Library,
    Playlists,
    Favorites,
    Tags,
    Sources,
    Settings,
}

enum class PlaylistKind {
    USER,
    SYSTEM_LIKED,
}

const val SYSTEM_LIKED_PLAYLIST_ID = "system-liked"

enum class ImportSourceType {
    LOCAL_FOLDER,
    SAMBA,
    WEBDAV,
    NAVIDROME,
    SUBSONIC,
    EMBY,
}

enum class ImportSourceIndexMode {
    LOCAL_INDEX,
    ONLINE,
}

enum class SubsonicAuthMode {
    PASSWORD,
    API_KEY,
}

enum class LyricsResponseFormat {
    JSON,
    XML,
    LRC,
    TEXT,
}

enum class RequestMethod {
    GET,
    POST,
    DELETE,
}

data class Artist(
    val id: String,
    val name: String,
    val trackCount: Int = 0,
)

data class Album(
    val id: String,
    val title: String,
    val artistName: String? = null,
    val trackCount: Int = 0,
)

data class Track(
    val id: String,
    val sourceId: String,
    val title: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long = 0L,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val mediaLocator: String,
    val relativePath: String,
    val artworkLocator: String? = null,
    val sizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    val addedAt: Long = modifiedAt,
    val bitDepth: Int? = null,
    val samplingRate: Int? = null,
    val bitRate: Int? = null,
    val channelCount: Int? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val remoteFavoriteHint: Boolean? = null,
)

data class RecentTrack(
    val track: Track,
    val playCount: Int,
    val lastPlayedAt: Long,
)

data class RecentAlbum(
    val album: Album,
    val playCount: Int,
    val lastPlayedAt: Long,
    val artworkLocator: String? = null,
)

data class PlaylistTrackEntry(
    val track: Track,
    val sourceLabel: String? = null,
)

data class PlaylistSummary(
    val id: String,
    val name: String,
    val kind: PlaylistKind = PlaylistKind.USER,
    val trackCount: Int = 0,
    val updatedAt: Long = 0L,
    val memberTrackIds: Set<String> = emptySet(),
    val artworkLocator: String? = null,
    val artworkCacheKey: String? = null,
)

data class PlaylistDetail(
    val id: String,
    val name: String,
    val kind: PlaylistKind = PlaylistKind.USER,
    val updatedAt: Long = 0L,
    val tracks: List<PlaylistTrackEntry> = emptyList(),
)

data class PlaylistAddTarget(
    val id: String,
    val name: String,
    val kind: PlaylistKind,
    val updatedAt: Long = 0L,
    val alreadyContainsTrack: Boolean = false,
    val nameText: UiText? = null,
)

data class ImportSource(
    val id: String,
    val type: ImportSourceType,
    val label: String,
    val rootReference: String,
    val server: String? = null,
    val port: Int? = null,
    val path: String? = null,
    val username: String? = null,
    val credentialKey: String? = null,
    val subsonicAuthMode: SubsonicAuthMode = SubsonicAuthMode.PASSWORD,
    val allowInsecureTls: Boolean = false,
    val enabled: Boolean = true,
    val lastScannedAt: Long? = null,
    val createdAt: Long = 0L,
    val wanRootReference: String? = null,
    val indexMode: ImportSourceIndexMode = ImportSourceIndexMode.LOCAL_INDEX,
    /** Folders to scan, relative to the source root; empty means the whole root (sources created before folder selection). */
    val selectedDirectories: List<String> = emptyList(),
)

data class ImportIndexState(
    val sourceId: String,
    val trackCount: Int,
    val remoteTrackCount: Int? = null,
    val lastScannedAt: Long? = null,
    val lastError: String? = null,
)

fun ImportIndexState.lastErrorUiText(): UiText? =
    lastError?.takeIf { it.isNotBlank() }?.let(UiText::Raw)

data class NavidromeLibraryProbe(
    val totalTrackCount: Int?,
    val supportsOnlineLibraryPaging: Boolean = totalTrackCount != null,
)

data class SourceWithStatus(
    val source: ImportSource,
    val indexState: ImportIndexState? = null,
)

data class LocalFolderSelection(
    val label: String,
    val persistentReference: String,
)

enum class LocalFolderPickerMode {
    Automatic,
    System,
    BuiltIn,
}

data class SambaSourceDraft(
    val label: String,
    val server: String,
    val port: Int? = null,
    /** Source root as `share/sub/path`; blank for sources rooted at the server, whose folders start with the share name. */
    val path: String = "",
    val username: String,
    val password: String,
    val selectedDirectories: List<String> = emptyList(),
)

data class WebDavSourceDraft(
    val label: String,
    val rootUrl: String,
    val username: String,
    val password: String,
    val allowInsecureTls: Boolean = false,
    val selectedDirectories: List<String> = emptyList(),
)

/** A folder listed while browsing a remote source; [relativePath] is relative to the source root. */
data class RemoteDirectoryEntry(
    val name: String,
    val relativePath: String,
)

data class NavidromeSourceDraft(
    val label: String,
    val baseUrl: String,
    val wanBaseUrl: String = "",
    val username: String,
    val password: String,
)

data class SubsonicSourceDraft(
    val label: String,
    val baseUrl: String,
    val wanBaseUrl: String = "",
    val username: String = "",
    val credential: String,
    val authMode: SubsonicAuthMode = SubsonicAuthMode.PASSWORD,
)

data class EmbySourceDraft(
    val label: String,
    val baseUrl: String,
    val wanBaseUrl: String = "",
    val username: String,
    val password: String,
)

data class ImportedTrackCandidate(
    val title: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long = 0L,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val mediaLocator: String,
    val relativePath: String,
    val artworkLocator: String? = null,
    val embeddedLyrics: String? = null,
    val sizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    val bitDepth: Int? = null,
    val samplingRate: Int? = null,
    val bitRate: Int? = null,
    val channelCount: Int? = null,
)

data class ImportScanFailure(
    val relativePath: String,
    val reason: String,
    val reasonText: UiText? = null,
)

/** Transient scan data: the original fallback stays with its optional resource description. */
data class ImportScanWarning(
    val diagnostic: String,
    val text: UiText? = null,
)

data class ImportScanReport(
    val tracks: List<ImportedTrackCandidate>,
    val warnings: List<ImportScanWarning> = emptyList(),
    val discoveredAudioFileCount: Int = tracks.size,
    val failures: List<ImportScanFailure> = emptyList(),
    val totalTrackCount: Int? = null,
    val refreshedPersistentReference: String? = null,
    /** Selected folders that could not be read this time; their tracks already in the library are kept as they are. */
    val unreadableFolders: List<String> = emptyList(),
)

data class ImportStreamingScanReport(
    val discoveredAudioFileCount: Int,
    val importedTrackCount: Int,
    val warnings: List<ImportScanWarning> = emptyList(),
    val failures: List<ImportScanFailure> = emptyList(),
    val totalTrackCount: Int? = null,
)

enum class ImportScanPhase {
    Scanning,
    Persisting,
}

data class ImportScanProgress(
    val sourceId: String,
    val phase: ImportScanPhase,
    val importedTrackCount: Int,
    val totalTrackCount: Int? = null,
)

fun interface ImportScanProgressSink {
    fun onProgress(progress: ImportScanProgress)

    companion object {
        val NoOp: ImportScanProgressSink = ImportScanProgressSink {}
    }
}

fun interface ImportTrackBatchSink {
    suspend fun onBatch(tracks: List<ImportedTrackCandidate>)

    companion object {
        val NoOp: ImportTrackBatchSink = ImportTrackBatchSink {}
    }
}

data class ImportScanSummary(
    val sourceId: String,
    val discoveredAudioFileCount: Int,
    val importedTrackCount: Int,
    val failures: List<ImportScanFailure> = emptyList(),
) {
    val failedAudioFileCount: Int
        get() = maxOf(discoveredAudioFileCount - importedTrackCount, failures.size, 0)
}

data class AudioTagSnapshot(
    val title: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val comment: String? = null,
    val composer: String? = null,
    val isCompilation: Boolean = false,
    val tagLabel: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val embeddedLyrics: String? = null,
    val artworkLocator: String? = null,
    val tagLabelText: UiText? = null,
)

data class AudioTagPatch(
    val title: String? = null,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val comment: String? = null,
    val composer: String? = null,
    val isCompilation: Boolean? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val embeddedLyrics: String? = null,
    val artworkBytes: ByteArray? = null,
    val clearArtwork: Boolean = false,
)

data class LyricsLine(
    val timestampMs: Long?,
    val text: String,
)

data class LyricsDocument(
    val lines: List<LyricsLine>,
    val offsetMs: Long = 0L,
    val sourceId: String,
    val rawPayload: String,
) {
    val isSynced: Boolean = lines.any { it.timestampMs != null }
}

data class LyricsLookupMetadata(
    val title: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long = 0L,
)

data class LyricsSearchCandidate(
    val sourceId: String,
    val sourceName: String,
    val document: LyricsDocument,
    val itemId: String? = null,
    val title: String? = null,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val durationSeconds: Int? = null,
    val artworkLocator: String? = null,
    val isTrackProvided: Boolean = false,
    val sourceNameText: UiText? = null,
)

fun ImportScanFailure.reasonUiText(): UiText = reasonText ?: UiText.Raw(reason)

fun LyricsSearchCandidate.sourceNameUiText(): UiText = sourceNameText ?: UiText.Raw(sourceName)

sealed interface LyricsSourceDefinition {
    val id: String
    val name: String
    val priority: Int
    val enabled: Boolean
}

data class LyricsSourceConfig(
    override val id: String,
    override val name: String,
    val method: RequestMethod = RequestMethod.GET,
    val urlTemplate: String,
    val headersTemplate: String = "",
    val queryTemplate: String = "",
    val bodyTemplate: String = "",
    val responseFormat: LyricsResponseFormat = LyricsResponseFormat.JSON,
    val extractor: String = "text",
    override val priority: Int = 0,
    override val enabled: Boolean = true,
) : LyricsSourceDefinition

enum class WorkflowLyricsTransform {
    BASE64_DECODE,
    JSON_UNESCAPE,
    TRIM,
    JOIN_ARRAY_WITH_DELIMITER,
}

data class WorkflowRequestConfig(
    val method: RequestMethod = RequestMethod.GET,
    val url: String,
    val queryTemplate: String = "",
    val bodyTemplate: String = "",
    val headersTemplate: String = "",
    val responseFormat: LyricsResponseFormat = LyricsResponseFormat.JSON,
)

data class WorkflowSearchConfig(
    val request: WorkflowRequestConfig,
    val resultPath: String,
    val mapping: Map<String, String>,
)

data class WorkflowSelectionConfig(
    val titleWeight: Double = 0.7,
    val artistWeight: Double = 0.2,
    val albumWeight: Double = 0.05,
    val durationWeight: Double = 0.05,
    val durationToleranceSeconds: Int = 3,
    val minScore: Double = 0.9,
    val maxCandidates: Int = 10,
)

data class WorkflowCandidateEnrichmentStepConfig(
    val request: WorkflowRequestConfig,
    val capture: Map<String, String> = emptyMap(),
)

data class WorkflowCandidateEnrichmentConfig(
    val steps: List<WorkflowCandidateEnrichmentStepConfig> = emptyList(),
)

data class WorkflowLyricsStepConfig(
    val request: WorkflowRequestConfig,
    val capture: Map<String, String> = emptyMap(),
    val payloadPath: String? = null,
    val fallbackPayloadPath: String? = null,
    val format: LyricsResponseFormat = LyricsResponseFormat.LRC,
    val transforms: List<WorkflowLyricsTransform> = emptyList(),
    val extractor: String = "text",
)

data class WorkflowLyricsConfig(
    val steps: List<WorkflowLyricsStepConfig>,
)

data class WorkflowOptionalFields(
    val coverUrlField: String? = null,
)

data class WorkflowLyricsSourceConfig(
    override val id: String,
    override val name: String,
    override val priority: Int = 0,
    override val enabled: Boolean = true,
    val search: WorkflowSearchConfig,
    val selection: WorkflowSelectionConfig = WorkflowSelectionConfig(),
    val enrichment: WorkflowCandidateEnrichmentConfig = WorkflowCandidateEnrichmentConfig(),
    val lyrics: WorkflowLyricsConfig,
    val optionalFields: WorkflowOptionalFields = WorkflowOptionalFields(),
    val rawJson: String,
) : LyricsSourceDefinition

data class WorkflowSongCandidate(
    val sourceId: String,
    val sourceName: String,
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationSeconds: Int? = null,
    val imageUrl: String? = null,
    val extraFields: Map<String, String> = emptyMap(),
)

enum class LyricsSearchApplyMode {
    FULL,
    LYRICS_ONLY,
    ARTWORK_ONLY,
}

data class LyricsRequest(
    val method: RequestMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutMillis: Long? = null,
)

const val IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS = 60_000L

data class LyricsHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

data class AppReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String,
)

data class PlatformCapabilities(
    val supportsLocalFolderImport: Boolean,
    val supportsSambaImport: Boolean,
    val supportsWebDavImport: Boolean,
    val supportsNavidromeImport: Boolean,
    val supportsSystemMediaControls: Boolean,
    val supportsSubsonicImport: Boolean = supportsNavidromeImport,
    val supportsEmbyImport: Boolean = supportsNavidromeImport,
    val supportsAppDisplayScaleAdjustment: Boolean = false,
    val supportsAndroidExtensionDecoder: Boolean = false,
    val supportsDesktopLyrics: Boolean = false,
    val supportsMenuBarLyricsControls: Boolean = false,
    val supportsEqualizer: Boolean = false,
    val supportsPlaybackBackgroundArtworkBlur: Boolean = true,
    val supportsSystemLocalFolderPicker: Boolean = false,
    val supportsLocalFolderReauthorization: Boolean = false,
    val supportsMacOsWindowCloseBehavior: Boolean = false,
    val supportsCustomDataLocation: Boolean = false,
    val supportsPlaybackProgressFlowerParticles: Boolean = true,
)

data class PlatformDescriptor(
    val name: String,
    val capabilities: PlatformCapabilities,
)

interface ImportSourceGateway {
    suspend fun pickLocalFolder(): LocalFolderSelection?
    suspend fun pickLocalFolder(mode: LocalFolderPickerMode): LocalFolderSelection? {
        return pickLocalFolder()
    }
    suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport
    suspend fun scanLocalFolder(
        selection: LocalFolderSelection,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanLocalFolder(selection, sourceId)
    }
    suspend fun testSamba(draft: SambaSourceDraft)
    suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport
    suspend fun scanSamba(
        draft: SambaSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanSamba(draft, sourceId)
    }
    /** Lists the folders directly under [relativePath] (relative to the draft root); a blank server root lists shares. */
    suspend fun listSambaDirectories(draft: SambaSourceDraft, relativePath: String): List<RemoteDirectoryEntry> {
        throw UnsupportedOperationException("Samba folder browsing is not supported on this platform.")
    }
    suspend fun testWebDav(draft: WebDavSourceDraft)
    /** Lists the folders directly under [relativePath], relative to the draft root URL. */
    suspend fun listWebDavDirectories(draft: WebDavSourceDraft, relativePath: String): List<RemoteDirectoryEntry> {
        throw UnsupportedOperationException("WebDAV folder browsing is not supported on this platform.")
    }
    suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport
    suspend fun scanWebDav(
        draft: WebDavSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanWebDav(draft, sourceId)
    }
    suspend fun testNavidrome(draft: NavidromeSourceDraft)
    suspend fun probeNavidrome(draft: NavidromeSourceDraft): NavidromeLibraryProbe {
        testNavidrome(draft)
        return NavidromeLibraryProbe(totalTrackCount = null)
    }
    suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport
    suspend fun scanNavidrome(
        draft: NavidromeSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanNavidrome(draft, sourceId)
    }
    suspend fun scanNavidromeStreaming(
        draft: NavidromeSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
        trackBatchSink: ImportTrackBatchSink,
    ): ImportStreamingScanReport {
        val report = scanNavidrome(draft, sourceId, progressSink)
        trackBatchSink.onBatch(report.tracks)
        return ImportStreamingScanReport(
            discoveredAudioFileCount = report.discoveredAudioFileCount,
            importedTrackCount = report.tracks.size,
            warnings = report.warnings,
            failures = report.failures,
            totalTrackCount = report.totalTrackCount,
        )
    }
    suspend fun testSubsonic(draft: SubsonicSourceDraft) {
        throw UnsupportedOperationException("Subsonic import is not supported on this platform.")
    }
    suspend fun scanSubsonic(draft: SubsonicSourceDraft, sourceId: String): ImportScanReport {
        throw UnsupportedOperationException("Subsonic import is not supported on this platform.")
    }
    suspend fun scanSubsonic(
        draft: SubsonicSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanSubsonic(draft, sourceId)
    }
    suspend fun testEmby(draft: EmbySourceDraft, deviceId: String): EmbyCredential {
        throw UnsupportedOperationException("Emby import is not supported on this platform.")
    }
    suspend fun testEmbyCredential(draft: EmbySourceDraft, credential: EmbyCredential, deviceId: String) {
        throw UnsupportedOperationException("Emby import is not supported on this platform.")
    }
    suspend fun scanEmby(
        draft: EmbySourceDraft,
        credential: EmbyCredential,
        sourceId: String,
        deviceId: String,
    ): ImportScanReport {
        throw UnsupportedOperationException("Emby import is not supported on this platform.")
    }
    suspend fun scanEmby(
        draft: EmbySourceDraft,
        credential: EmbyCredential,
        sourceId: String,
        deviceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanEmby(draft, credential, sourceId, deviceId)
    }
}

data class EmbyCredential(
    val userId: String,
    val accessToken: String,
)

interface SecureCredentialStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
}

interface LyricsHttpClient {
    suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse>
}

interface ArtworkLoader {
    suspend fun resolve(track: Track): String?
}

enum class ArtworkWritePolicy {
    /** Return any existing valid image; write only when missing. */
    KeepExisting,
    /** Explicit user replacement, regardless of existing image. */
    Replace,
    /** For Subsonic-compatible tracks: fill missing artwork or replace a recognized placeholder. */
    MissingOrPlaceholder,
}

/** [changed] indicates that this call committed a new image, rather than returning an existing one. */
data class ArtworkCacheResult(val locator: String, val changed: Boolean)

interface ArtworkCacheStore {
    suspend fun cache(
        locator: String,
        cacheKey: String,
        policy: ArtworkWritePolicy = ArtworkWritePolicy.KeepExisting,
    ): ArtworkCacheResult?
    suspend fun hasCached(cacheKey: String): Boolean = false
    suspend fun hasReplaceableNavidromePlaceholderCached(cacheKey: String): Boolean = false
    fun observeVersion(cacheKey: String): Flow<Long> = flowOf(0L)
    fun peekCachedTarget(cacheKey: String): ArtworkCachedTarget? = null
}

interface AudioTagGateway {
    suspend fun canEdit(track: Track): Boolean
    suspend fun canWrite(track: Track): Boolean
    suspend fun read(track: Track): Result<AudioTagSnapshot>
    suspend fun write(track: Track, patch: AudioTagPatch): Result<AudioTagSnapshot>
}

object UnsupportedAudioTagGateway : AudioTagGateway {
    private val error = UiTextException(uiText(Res.string.audio_tags_platform_unsupported))

    override suspend fun canEdit(track: Track): Boolean = false
    override suspend fun canWrite(track: Track): Boolean = false

    override suspend fun read(track: Track): Result<AudioTagSnapshot> = Result.failure(error)

    override suspend fun write(track: Track, patch: AudioTagPatch): Result<AudioTagSnapshot> = Result.failure(error)
}

interface SameNameLyricsFileGateway {
    suspend fun readSameNameLyrics(track: Track): Result<String?>
}

object UnsupportedSameNameLyricsFileGateway : SameNameLyricsFileGateway {
    override suspend fun readSameNameLyrics(track: Track): Result<String?> = Result.success(null)
}

interface AudioTagEditorPlatformService {
    suspend fun pickArtworkBytes(): Result<ByteArray?>
    suspend fun loadArtworkBytes(locator: String): Result<ByteArray?>
}

object UnsupportedAudioTagEditorPlatformService : AudioTagEditorPlatformService {
    private val error = UiTextException(uiText(Res.string.artwork_picker_platform_unsupported))

    override suspend fun pickArtworkBytes(): Result<ByteArray?> = Result.failure(error)

    override suspend fun loadArtworkBytes(locator: String): Result<ByteArray?> = Result.failure(error)
}

interface VlcPathPickerPlatformService {
    suspend fun pickVlcDirectory(): Result<String?>
}

object UnsupportedVlcPathPickerPlatformService : VlcPathPickerPlatformService {
    private val error = UiTextException(uiText(Res.string.vlc_picker_platform_unsupported))

    override suspend fun pickVlcDirectory(): Result<String?> = Result.failure(error)
}

data class RemotePlaybackUrlCandidate(
    val sourceId: String = "",
    val addressKind: String = "",
    val value: String,
)

interface NavidromeLocatorResolver {
    suspend fun resolveStreamUrl(locator: String, audioQuality: NavidromeAudioQuality): String?
    suspend fun resolveStreamUrlCandidates(
        locator: String,
        audioQuality: NavidromeAudioQuality,
    ): List<RemotePlaybackUrlCandidate>? {
        return resolveStreamUrl(locator, audioQuality)
            ?.let { listOf(RemotePlaybackUrlCandidate(value = it)) }
    }

    suspend fun resolveCoverArtUrl(locator: String): String?
    suspend fun resolveCoverArtUrlCandidates(locator: String): List<RemotePlaybackUrlCandidate>? {
        return resolveCoverArtUrl(locator)
            ?.let { listOf(RemotePlaybackUrlCandidate(value = it)) }
    }

    fun markResolvedUrlSuccess(candidate: RemotePlaybackUrlCandidate) = Unit
}

object NavidromeLocatorRuntime {
    @Volatile
    private var resolver: NavidromeLocatorResolver? = null

    fun install(resolver: NavidromeLocatorResolver) {
        this.resolver = resolver
    }

    suspend fun resolveStreamUrl(
        locator: String,
        audioQuality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
    ): String? = resolver?.resolveStreamUrl(locator, audioQuality)

    suspend fun resolveStreamUrlCandidates(
        locator: String,
        audioQuality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
    ): List<RemotePlaybackUrlCandidate>? = resolver?.resolveStreamUrlCandidates(locator, audioQuality)

    suspend fun resolveCoverArtUrl(locator: String): String? = resolver?.resolveCoverArtUrl(locator)

    suspend fun resolveCoverArtUrlCandidates(locator: String): List<RemotePlaybackUrlCandidate>? =
        resolver?.resolveCoverArtUrlCandidates(locator)

    fun markResolvedUrlSuccess(candidate: RemotePlaybackUrlCandidate) {
        resolver?.markResolvedUrlSuccess(candidate)
    }
}
