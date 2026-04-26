package top.iwesley.lyn.music.domain

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.decodeURLPart
import io.ktor.http.encodedPath
import io.ktor.http.parseUrl
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.classifyAudioExtensionForImport
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.unsupportedAudioImportFailure
import top.iwesley.lyn.music.data.db.LynMusicDatabase

const val NAVIDROME_LYRICS_SOURCE_ID = "navidrome-lyrics"

private const val NAVIDROME_CLIENT_NAME = "LynMusic"
private const val NAVIDROME_API_VERSION = "1.16.1"
private val navidromeJson = Json { ignoreUnknownKeys = true }

data class NavidromeResolvedSource(
    val baseUrl: String,
    val username: String,
    val password: String,
)

data class NavidromeSongCandidate(
    val songId: String,
    val title: String,
    val artistName: String?,
    val albumTitle: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val sizeBytes: Long,
    val suffix: String?,
    val coverArtId: String?,
    val bitDepth: Int?,
    val samplingRate: Int?,
    val bitRate: Int?,
    val channelCount: Int?,
)

fun normalizeNavidromeBaseUrl(rawUrl: String?): String {
    val value = rawUrl.orEmpty().trim()
    require(value.isNotBlank()) { "请填写 Navidrome 服务器地址。" }
    require('?' !in value && '#' !in value) { "Navidrome 地址不能包含 query 或 fragment。" }
    val parsed = parseUrl(value) ?: error("Navidrome 地址无效。")
    require(parsed.protocol.name in setOf("http", "https")) { "Navidrome 地址只支持 http 或 https。" }
    require(parsed.host.isNotBlank()) { "Navidrome 地址缺少主机名。" }
    require(parsed.user == null && parsed.password == null) { "请不要在 Navidrome URL 中内嵌用户名或密码。" }
    val pathSegments = parsed.encodedPath
        .split('/')
        .filter { it.isNotBlank() }
        .map { it.decodeURLPart() }
        .let { segments ->
            if (segments.lastOrNull()?.equals("rest", ignoreCase = true) == true) {
                segments.dropLast(1)
            } else {
                segments
            }
        }
    val normalizedPath = URLBuilder().apply {
        encodedPath = "/"
        if (pathSegments.isNotEmpty()) {
            appendPathSegments(pathSegments)
        }
    }.encodedPath.removeSuffix("/").ifBlank { "/" }
    return URLBuilder(parsed).apply {
        encodedUser = null
        encodedPassword = null
        encodedParameters.clear()
        encodedFragment = ""
        encodedPath = normalizedPath
        if (port == protocol.defaultPort) {
            port = DEFAULT_PORT
        }
    }.buildString().removeSuffix("/")
}

fun buildNavidromeStreamUrl(
    baseUrl: String,
    username: String,
    password: String,
    songId: String,
    audioQuality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
): String {
    return buildNavidromeRestUrl(
        baseUrl = baseUrl,
        username = username,
        password = password,
        endpoint = "stream",
        parameters = buildMap {
            put("id", songId)
            audioQuality.maxBitRateKbps?.let { maxBitRate ->
                put("maxBitRate", maxBitRate.toString())
                put("format", "mp3")
            }
        },
        includeJsonFormat = false,
    )
}

fun buildNavidromeCoverArtUrl(
    baseUrl: String,
    username: String,
    password: String,
    coverArtId: String,
): String {
    return buildNavidromeRestUrl(
        baseUrl = baseUrl,
        username = username,
        password = password,
        endpoint = "getCoverArt",
        parameters = mapOf("id" to coverArtId),
        includeJsonFormat = false,
    )
}

suspend fun scanNavidromeLibrary(
    draft: NavidromeSourceDraft,
    sourceId: String,
    httpClient: LyricsHttpClient,
    supportedImportExtensions: Set<String>,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): ImportScanReport {
    val baseUrl = normalizeNavidromeBaseUrl(draft.baseUrl)
    require(draft.username.isNotBlank()) { "请填写 Navidrome 用户名。" }
    require(draft.password.isNotBlank()) { "请填写 Navidrome 密码。" }
    val resolved = NavidromeResolvedSource(
        baseUrl = baseUrl,
        username = draft.username.trim(),
        password = draft.password,
    )
    val artistIds = requestNavidromeArtistIds(httpClient, resolved)
    val tracks = mutableListOf<ImportedTrackCandidate>()
    val failures = mutableListOf<top.iwesley.lyn.music.core.model.ImportScanFailure>()
    val seenAlbumIds = linkedSetOf<String>()
    val seenSongIds = linkedSetOf<String>()
    var discoveredAudioFileCount = 0
    artistIds.forEach { artistId ->
        requestNavidromeAlbumIds(httpClient, resolved, artistId).forEach { albumId ->
            if (!seenAlbumIds.add(albumId)) return@forEach
            requestNavidromeAlbumSongs(httpClient, resolved, albumId).forEach { candidate ->
                if (candidate.songId.isNotBlank() && !seenSongIds.add(candidate.songId)) {
                    return@forEach
                }
                discoveredAudioFileCount += 1
                when (classifyAudioExtensionForImport(candidate.suffix, supportedImportExtensions)) {
                    NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> {
                        tracks += candidate.toImportedTrackCandidate(sourceId)
                    }

                    NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED,
                    NonNavidromeAudioScanResult.NOT_AUDIO -> {
                        failures += unsupportedAudioImportFailure(candidate.relativePath())
                    }
                }
            }
        }
    }
    logger.log(
        level = DiagnosticLogLevel.INFO,
        tag = "Navidrome",
        message = "scan-complete source=$sourceId baseUrl=$baseUrl artists=${artistIds.size} discovered=$discoveredAudioFileCount imported=${tracks.size} failures=${failures.size}",
    )
    return ImportScanReport(
        tracks = tracks,
        warnings = if (discoveredAudioFileCount == 0) listOf("当前 Navidrome 账号下没有可同步的歌曲。") else emptyList(),
        discoveredAudioFileCount = discoveredAudioFileCount,
        failures = failures,
    )
}

suspend fun testNavidromeConnection(
    draft: NavidromeSourceDraft,
    httpClient: LyricsHttpClient,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    val resolved = NavidromeResolvedSource(
        baseUrl = normalizeNavidromeBaseUrl(draft.baseUrl),
        username = draft.username.trim(),
        password = draft.password,
    )
    requestNavidromeJson(
        httpClient = httpClient,
        source = resolved,
        endpoint = "ping",
        logger = logger,
    )
}

suspend fun requestNavidromeLyrics(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
    track: Track,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): LyricsDocument? {
    requestNavidromeStructuredLyrics(
        httpClient = httpClient,
        source = source,
        track = track,
        logger = logger,
    )?.let { return it }
    val payload = requestNavidromeLyricsPayload(
        httpClient = httpClient,
        source = source,
        title = track.title,
        artistName = track.artistName,
        logger = logger,
    ) ?: return null
    val lines = parseLrc(payload).ifEmpty { parsePlainText(payload) }
    if (lines.isEmpty()) return null
    val document = LyricsDocument(
        lines = lines,
        offsetMs = 0L,
        sourceId = NAVIDROME_LYRICS_SOURCE_ID,
        rawPayload = payload,
    )
    logger.log(
        level = DiagnosticLogLevel.INFO,
        tag = "Navidrome",
        message = buildString {
            append("lyrics-resolved ")
            append(formatNavidromeLyricsContext(track.title, track.artistName))
            append(" synced=")
            append(document.isSynced)
            append(" lines=")
            append(document.lines.size)
            append('\n')
            append("lyrics-payload:\n")
            append(payload.ifBlank { "<empty>" })
        },
    )
    return document
}

private suspend fun requestNavidromeStructuredLyrics(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
    track: Track,
    logger: DiagnosticLogger,
): LyricsDocument? {
    val (_, songId) = parseNavidromeSongLocator(track.mediaLocator) ?: return null
    val response = runCatching {
        requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getLyricsBySongId",
            parameters = mapOf(
                "id" to songId,
                "enhanced" to "true",
            ),
            logger = logger,
            logContext = buildString {
                append("songId=\"")
                append(songId)
                append("\" ")
                append(formatNavidromeLyricsContext(track.title, track.artistName))
            },
        )
    }.getOrElse { throwable ->
        logger.log(
            level = DiagnosticLogLevel.WARN,
            tag = "Navidrome",
            message = buildString {
                append("structured-lyrics-fallback ")
                append("songId=\"")
                append(songId)
                append("\" ")
                append(formatNavidromeLyricsContext(track.title, track.artistName))
                append(" cause=")
                append(throwable.message.orEmpty().ifBlank { throwable::class.simpleName.orEmpty() })
            },
            throwable = throwable,
        )
        return null
    }
    val document = parseNavidromeStructuredLyricsDocument(response)
        ?.takeIf { it.lines.isNotEmpty() }
        ?: return null
    logger.log(
        level = DiagnosticLogLevel.INFO,
        tag = "Navidrome",
        message = buildString {
            append("lyrics-resolved endpoint=getLyricsBySongId ")
            append("songId=\"")
            append(songId)
            append("\" ")
            append(formatNavidromeLyricsContext(track.title, track.artistName))
            append(" synced=")
            append(document.isSynced)
            append(" lines=")
            append(document.lines.size)
            append(" offsetMs=")
            append(document.offsetMs)
            append('\n')
            append("lyrics-payload:\n")
            append(document.rawPayload.ifBlank { "<empty>" })
        },
    )
    return document
}

suspend fun resolveNavidromeStreamUrl(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    audioQuality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
): String? {
    val (_, songId) = parseNavidromeSongLocator(locator) ?: return null
    val source = resolveNavidromeSource(database, secureCredentialStore, locator) ?: return null
    return buildNavidromeStreamUrl(
        baseUrl = source.baseUrl,
        username = source.username,
        password = source.password,
        songId = songId,
        audioQuality = audioQuality,
    )
}

suspend fun resolveNavidromeCoverArtUrl(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
): String? {
    val (_, coverArtId) = parseNavidromeCoverLocator(locator) ?: return null
    val source = resolveNavidromeSource(database, secureCredentialStore, locator) ?: return null
    return buildNavidromeCoverArtUrl(
        baseUrl = source.baseUrl,
        username = source.username,
        password = source.password,
        coverArtId = coverArtId,
    )
}

private suspend fun resolveNavidromeSource(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
): NavidromeResolvedSource? {
    val sourceId = parseNavidromeSongLocator(locator)?.first ?: parseNavidromeCoverLocator(locator)?.first ?: return null
    val entity = database.importSourceDao().getById(sourceId)?.takeIf { it.type == "NAVIDROME" && it.enabled } ?: return null
    val username = entity.username?.trim().orEmpty()
    val password = entity.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    if (username.isBlank() || password.isBlank()) return null
    return NavidromeResolvedSource(
        baseUrl = normalizeNavidromeBaseUrl(entity.rootReference),
        username = username,
        password = password,
    )
}

private suspend fun requestNavidromeArtistIds(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
): List<String> {
    val response = requestNavidromeJson(
        httpClient = httpClient,
        source = source,
        endpoint = "getArtists",
    )
    return response["artists"].asObject("artists")["index"].asObjectList()
        .flatMap { index -> index["artist"].asObjectList() }
        .mapNotNull { artist -> artist.string("id") }
}

private suspend fun requestNavidromeAlbumIds(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
    artistId: String,
): List<String> {
    val response = requestNavidromeJson(
        httpClient = httpClient,
        source = source,
        endpoint = "getArtist",
        parameters = mapOf("id" to artistId),
    )
    return response["artist"].asObject("artist")["album"].asObjectList()
        .mapNotNull { album -> album.string("id") }
}

private suspend fun requestNavidromeAlbumSongs(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
    albumId: String,
): List<NavidromeSongCandidate> {
    val response = requestNavidromeJson(
        httpClient = httpClient,
        source = source,
        endpoint = "getAlbum",
        parameters = mapOf("id" to albumId),
    )
    val album = response["album"].asObject("album")
    val albumTitle = album.string("name") ?: album.string("title") ?: album.string("album")
    val albumArtist = album.string("artist")
    val albumCoverArtId = album.string("coverArt")
    return album["song"].asObjectList()
        .map { song ->
            val songId = song.string("id").orEmpty()
            val suffix = song.string("suffix")
            val title = song.string("title").orEmpty().ifBlank { "未知曲目" }
            val artistName = song.string("artist") ?: albumArtist
            val coverArtId = song.string("coverArt") ?: albumCoverArtId
            NavidromeSongCandidate(
                songId = songId,
                title = title,
                artistName = artistName,
                albumTitle = song.string("album") ?: albumTitle,
                durationMs = (song.long("duration") ?: 0L) * 1_000L,
                trackNumber = song.int("track"),
                discNumber = song.int("discNumber"),
                sizeBytes = song.long("size") ?: 0L,
                suffix = suffix,
                coverArtId = coverArtId,
                bitDepth = song.int("bitDepth"),
                samplingRate = song.int("samplingRate"),
                bitRate = song.int("bitRate"),
                channelCount = song.int("channelCount"),
            )
        }
}

private suspend fun requestNavidromeLyricsPayload(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
    title: String,
    artistName: String?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): String? {
    if (title.isBlank()) return null
    val response = requestNavidromeJson(
        httpClient = httpClient,
        source = source,
        endpoint = "getLyrics",
        parameters = buildMap {
            put("title", title)
            artistName?.takeIf { it.isNotBlank() }?.let { put("artist", it) }
        },
        logger = logger,
        logContext = formatNavidromeLyricsContext(title, artistName),
    )
    return response["lyrics"].asObjectOrNull()
        ?.string("value")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

internal suspend fun requestNavidromeJson(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
    endpoint: String,
    parameters: Map<String, String> = emptyMap(),
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    logContext: String? = null,
): JsonObject {
    val request = LyricsRequest(
        method = RequestMethod.GET,
        url = buildNavidromeRestUrl(
            baseUrl = source.baseUrl,
            username = source.username,
            password = source.password,
            endpoint = endpoint,
            parameters = parameters,
            includeJsonFormat = true,
        ),
    )
    if (logger !== NoopDiagnosticLogger) {
        logger.log(
            level = DiagnosticLogLevel.INFO,
            tag = "Navidrome",
            message = buildString {
                append("request endpoint=")
                append(endpoint)
                logContext?.takeIf { it.isNotBlank() }?.let {
                    append(' ')
                    append(it)
                }
                append('\n')
                append("url: ")
                append(redactNavidromeUrlForLog(request.url))
            },
        )
    }
    val response = httpClient.request(request).getOrElse { throwable ->
        throw IllegalStateException("Navidrome $endpoint 请求失败: ${throwable.message.orEmpty()}", throwable)
    }
    if (logger !== NoopDiagnosticLogger) {
        logger.log(
            level = DiagnosticLogLevel.INFO,
            tag = "Navidrome",
            message = buildString {
                append("response endpoint=")
                append(endpoint)
                logContext?.takeIf { it.isNotBlank() }?.let {
                    append(' ')
                    append(it)
                }
                append(" status=")
                append(response.statusCode)
                append('\n')
                append("body:\n")
                append(response.body.ifBlank { "<empty>" })
            },
        )
    }
    require(response.statusCode in 200..299) { "Navidrome $endpoint 失败，HTTP ${response.statusCode}" }
    val root = navidromeJson.parseToJsonElement(response.body) as? JsonObject
        ?: error("Navidrome $endpoint 返回不是 JSON 对象。")
    val payload = root["subsonic-response"].asObject("subsonic-response")
    val status = payload.string("status").orEmpty()
    if (!status.equals("ok", ignoreCase = true)) {
        val error = payload["error"].asObjectOrNull()
        val message = error?.string("message").orEmpty().ifBlank { "Navidrome $endpoint 返回失败状态。" }
        error(message)
    }
    return payload
}

private fun formatNavidromeLyricsContext(
    title: String,
    artistName: String?,
): String {
    return buildString {
        append("title=\"")
        append(title)
        append('"')
        artistName?.takeIf { it.isNotBlank() }?.let {
            append(" artist=\"")
            append(it)
            append('"')
        }
    }
}

private fun redactNavidromeUrlForLog(url: String): String {
    return url
        .replace(Regex("([?&]t=)[^&]*"), "$1<redacted>")
        .replace(Regex("([?&]s=)[^&]*"), "$1<redacted>")
}

private fun parseNavidromeStructuredLyricsDocument(payload: JsonObject): LyricsDocument? {
    val parsed = parseNavidromeStructuredLyricsPayload(payload)
        ?: return null
    return LyricsDocument(
        lines = parsed.lines,
        offsetMs = parsed.offsetMs,
        sourceId = NAVIDROME_LYRICS_SOURCE_ID,
        rawPayload = payload.toString(),
    )
}

private fun buildNavidromeRestUrl(
    baseUrl: String,
    username: String,
    password: String,
    endpoint: String,
    parameters: Map<String, String>,
    includeJsonFormat: Boolean,
): String {
    val normalizedBaseUrl = normalizeNavidromeBaseUrl(baseUrl)
    val salt = randomNavidromeSalt()
    val token = md5Hex(password + salt)
    return URLBuilder(normalizedBaseUrl).apply {
        appendPathSegments("rest", endpoint)
        parameters.forEach { (key, value) ->
            if (value.isNotBlank()) {
                this.parameters.append(key, value)
            }
        }
        this.parameters.append("u", username)
        this.parameters.append("t", token)
        this.parameters.append("s", salt)
        this.parameters.append("v", NAVIDROME_API_VERSION)
        this.parameters.append("c", NAVIDROME_CLIENT_NAME)
        if (includeJsonFormat) {
            this.parameters.append("f", "json")
        }
    }.buildString()
}

private fun buildNavidromeRelativePath(
    artistName: String?,
    albumTitle: String?,
    title: String,
    suffix: String?,
): String {
    val fileName = buildString {
        append(normalizeNavidromePathSegment(title.ifBlank { "未知曲目" }))
        suffix?.trim()?.takeIf { it.isNotBlank() }?.let {
            append('.')
            append(it.lowercase())
        }
    }
    return listOf(
        normalizeNavidromePathSegment(artistName.orEmpty().ifBlank { "未知艺人" }),
        normalizeNavidromePathSegment(albumTitle.orEmpty().ifBlank { "未知专辑" }),
        fileName,
    ).joinToString("/")
}

private fun NavidromeSongCandidate.relativePath(): String {
    return buildNavidromeRelativePath(
        artistName = artistName,
        albumTitle = albumTitle,
        title = title,
        suffix = suffix,
    )
}

private fun NavidromeSongCandidate.toImportedTrackCandidate(sourceId: String): ImportedTrackCandidate {
    return ImportedTrackCandidate(
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        mediaLocator = buildNavidromeSongLocator(sourceId, songId),
        relativePath = relativePath(),
        artworkLocator = coverArtId?.let { buildNavidromeCoverLocator(sourceId, it) },
        embeddedLyrics = null,
        sizeBytes = sizeBytes,
        modifiedAt = 0L,
        bitDepth = bitDepth,
        samplingRate = samplingRate,
        bitRate = bitRate,
        channelCount = channelCount,
    )
}

private fun normalizeNavidromePathSegment(value: String): String {
    return value.trim()
        .replace('/', '／')
        .replace('\\', '／')
        .ifBlank { "未知" }
}

private fun randomNavidromeSalt(length: Int = 12): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return buildString(length) {
        repeat(length) {
            append(alphabet[Random.nextInt(alphabet.length)])
        }
    }
}

private fun JsonElement?.asObject(context: String): JsonObject {
    return this as? JsonObject ?: error("Navidrome $context 缺失或格式错误。")
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asObjectList(): List<JsonObject> {
    return when (val element = this) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(element)
        else -> emptyList()
    }
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.boolean(key: String): Boolean? {
    return string(key)?.lowercase()?.let { value ->
        when (value) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
}

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()
