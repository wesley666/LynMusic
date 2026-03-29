package top.iwesley.lyn.music.core.model

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

data class LyricsSourceConfig(
    val id: String,
    val name: String,
    val method: RequestMethod = RequestMethod.GET,
    val urlTemplate: String,
    val headersTemplate: String = "",
    val queryTemplate: String = "",
    val bodyTemplate: String = "",
    val responseFormat: LyricsResponseFormat = LyricsResponseFormat.JSON,
    val extractor: String = "text",
    val priority: Int = 0,
    val enabled: Boolean = true,
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
