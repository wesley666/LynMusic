package top.iwesley.lyn.music.core.model

import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.StateFlow

enum class AppTab {
    Library,
    Sources,
    Settings,
}

enum class ImportSourceType {
    LOCAL_FOLDER,
    SAMBA,
    WEBDAV,
    NAVIDROME,
}

enum class PlaybackMode {
    ORDER,
    SHUFFLE,
    REPEAT_ONE,
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
    val allowInsecureTls: Boolean = false,
    val lastScannedAt: Long? = null,
    val createdAt: Long = 0L,
)

data class ImportIndexState(
    val sourceId: String,
    val trackCount: Int,
    val lastScannedAt: Long? = null,
    val lastError: String? = null,
)

data class SourceWithStatus(
    val source: ImportSource,
    val indexState: ImportIndexState? = null,
)

data class LocalFolderSelection(
    val label: String,
    val persistentReference: String,
)

data class SambaSourceDraft(
    val label: String,
    val server: String,
    val port: Int? = null,
    val path: String = "",
    val username: String,
    val password: String,
)

data class WebDavSourceDraft(
    val label: String,
    val rootUrl: String,
    val username: String,
    val password: String,
    val allowInsecureTls: Boolean = false,
)

data class NavidromeSourceDraft(
    val label: String,
    val baseUrl: String,
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
)

data class ImportScanReport(
    val tracks: List<ImportedTrackCandidate>,
    val warnings: List<String> = emptyList(),
)

data class PlaybackSnapshot(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val mode: PlaybackMode = PlaybackMode.ORDER,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val metadataTitle: String? = null,
    val metadataArtistName: String? = null,
    val metadataAlbumTitle: String? = null,
    val metadataArtworkLocator: String? = null,
    val errorMessage: String? = null,
) {
    val currentTrack: Track?
        get() = queue.getOrNull(currentIndex)

    val currentDisplayTitle: String
        get() = metadataTitle?.takeIf { it.isNotBlank() } ?: currentTrack?.title.orEmpty()

    val currentDisplayArtistName: String?
        get() = metadataArtistName?.takeIf { it.isNotBlank() } ?: currentTrack?.artistName

    val currentDisplayAlbumTitle: String?
        get() = metadataAlbumTitle?.takeIf { it.isNotBlank() } ?: currentTrack?.albumTitle

    val currentDisplayArtworkLocator: String?
        get() = metadataArtworkLocator?.takeIf { it.isNotBlank() } ?: currentTrack?.artworkLocator
}

data class PlaybackGatewayState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val metadataTitle: String? = null,
    val metadataArtistName: String? = null,
    val metadataAlbumTitle: String? = null,
    val completionCount: Long = 0L,
    val errorMessage: String? = null,
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

data class LyricsSearchCandidate(
    val sourceId: String,
    val sourceName: String,
    val document: LyricsDocument,
)

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
    val minScore: Double = 0.4,
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

data class LyricsRequest(
    val method: RequestMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class LyricsHttpResponse(
    val statusCode: Int,
    val body: String,
)

data class PlatformCapabilities(
    val supportsLocalFolderImport: Boolean,
    val supportsSambaImport: Boolean,
    val supportsWebDavImport: Boolean,
    val supportsNavidromeImport: Boolean,
    val supportsSystemMediaControls: Boolean,
)

data class PlatformDescriptor(
    val name: String,
    val capabilities: PlatformCapabilities,
)

interface ImportSourceGateway {
    suspend fun pickLocalFolder(): LocalFolderSelection?
    suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport
    suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport
    suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport
    suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport
}

interface PlaybackGateway {
    val state: StateFlow<PlaybackGatewayState>

    suspend fun load(track: Track, playWhenReady: Boolean, startPositionMs: Long = 0L)
    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Float)
    suspend fun release()
}

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

interface ArtworkCacheStore {
    suspend fun cache(locator: String, cacheKey: String): String?
}

interface NavidromeLocatorResolver {
    suspend fun resolveStreamUrl(locator: String): String?
    suspend fun resolveCoverArtUrl(locator: String): String?
}

object NavidromeLocatorRuntime {
    @Volatile
    private var resolver: NavidromeLocatorResolver? = null

    fun install(resolver: NavidromeLocatorResolver) {
        this.resolver = resolver
    }

    suspend fun resolveStreamUrl(locator: String): String? = resolver?.resolveStreamUrl(locator)

    suspend fun resolveCoverArtUrl(locator: String): String? = resolver?.resolveCoverArtUrl(locator)
}
