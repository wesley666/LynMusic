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
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
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
): String {
    return buildNavidromeRestUrl(
        baseUrl = baseUrl,
        username = username,
        password = password,
        endpoint = "stream",
        parameters = mapOf("id" to songId),
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
    val seenAlbumIds = linkedSetOf<String>()
    val seenSongIds = linkedSetOf<String>()
    artistIds.forEach { artistId ->
        requestNavidromeAlbumIds(httpClient, resolved, artistId).forEach { albumId ->
            if (!seenAlbumIds.add(albumId)) return@forEach
            requestNavidromeAlbumSongs(httpClient, resolved, sourceId, albumId).forEach { candidate ->
                val songId = parseNavidromeSongLocator(candidate.mediaLocator)?.second
                if (songId == null || seenSongIds.add(songId)) {
                    tracks += candidate
                }
            }
        }
    }
    logger.log(
        level = DiagnosticLogLevel.INFO,
        tag = "Navidrome",
        message = "scan-complete source=$sourceId baseUrl=$baseUrl artists=${artistIds.size} tracks=${tracks.size}",
    )
    return ImportScanReport(
        tracks = tracks,
        warnings = if (tracks.isEmpty()) listOf("当前 Navidrome 账号下没有可同步的歌曲。") else emptyList(),
    )
}

suspend fun requestNavidromeLyrics(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
    track: Track,
): LyricsDocument? {
    val payload = requestNavidromeLyricsPayload(
        httpClient = httpClient,
        source = source,
        title = track.title,
        artistName = track.artistName,
    ) ?: return null
    val lines = parseLrc(payload).ifEmpty { parsePlainText(payload) }
    if (lines.isEmpty()) return null
    return LyricsDocument(
        lines = lines,
        offsetMs = 0L,
        sourceId = NAVIDROME_LYRICS_SOURCE_ID,
        rawPayload = payload,
    )
}

suspend fun resolveNavidromeStreamUrl(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
): String? {
    val (_, songId) = parseNavidromeSongLocator(locator) ?: return null
    val source = resolveNavidromeSource(database, secureCredentialStore, locator) ?: return null
    return buildNavidromeStreamUrl(
        baseUrl = source.baseUrl,
        username = source.username,
        password = source.password,
        songId = songId,
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
    val entity = database.importSourceDao().getById(sourceId)?.takeIf { it.type == "NAVIDROME" } ?: return null
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
    sourceId: String,
    albumId: String,
): List<ImportedTrackCandidate> {
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
            ImportedTrackCandidate(
                title = title,
                artistName = artistName,
                albumTitle = song.string("album") ?: albumTitle,
                durationMs = (song.long("duration") ?: 0L) * 1_000L,
                trackNumber = song.int("track"),
                discNumber = song.int("discNumber"),
                mediaLocator = buildNavidromeSongLocator(sourceId, songId),
                relativePath = buildNavidromeRelativePath(
                    artistName = artistName,
                    albumTitle = song.string("album") ?: albumTitle,
                    title = title,
                    suffix = suffix,
                ),
                artworkLocator = coverArtId?.let { buildNavidromeCoverLocator(sourceId, it) },
                embeddedLyrics = null,
                sizeBytes = song.long("size") ?: 0L,
                modifiedAt = 0L,
            )
        }
}

private suspend fun requestNavidromeLyricsPayload(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
    title: String,
    artistName: String?,
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
    )
    return response["lyrics"].asObjectOrNull()
        ?.string("value")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private suspend fun requestNavidromeJson(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
    endpoint: String,
    parameters: Map<String, String> = emptyMap(),
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
    val response = httpClient.request(request).getOrElse { throwable ->
        throw IllegalStateException("Navidrome $endpoint 请求失败: ${throwable.message.orEmpty()}", throwable)
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

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()
