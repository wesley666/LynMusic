package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodedPath
import io.ktor.http.parseUrl
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val WEBDAV_SCHEME = "lynmusic-webdav://"

data class WebDavPropfindEntry(
    val href: String,
    val isDirectory: Boolean,
    val lastModified: String?,
    val contentLength: Long?,
)

fun buildWebDavLocator(sourceId: String, relativePath: String): String {
    val normalizedPath = relativePath.trimStart('/')
    return WEBDAV_SCHEME + sourceId + "/" + normalizedPath.encodeURLParameter()
}

fun parseWebDavLocator(locator: String): Pair<String, String>? {
    if (!locator.startsWith(WEBDAV_SCHEME)) return null
    val payload = locator.removePrefix(WEBDAV_SCHEME)
    val dividerIndex = payload.indexOf('/')
    if (dividerIndex <= 0) return null
    val sourceId = payload.substring(0, dividerIndex)
    val relativePath = payload.substring(dividerIndex + 1).decodeURLPart()
    return sourceId to relativePath
}

fun normalizeWebDavRootUrl(rawUrl: String?): String {
    val value = rawUrl.orEmpty().trim()
    requireUi(value.isNotBlank()) { uiText(Res.string.webdav_root_required) }
    requireUi('?' !in value && '#' !in value) { uiText(Res.string.webdav_root_no_query) }
    val parsed = parseUrl(value) ?: throw UiTextException(uiText(Res.string.webdav_root_invalid))
    requireUi(parsed.protocol.name in setOf("http", "https")) { uiText(Res.string.webdav_root_http_required) }
    requireUi(parsed.host.isNotBlank()) { uiText(Res.string.webdav_root_host_required) }
    requireUi(parsed.user == null && parsed.password == null) { uiText(Res.string.server_address_no_credentials, "WebDAV") }

    val normalizedPath = URLBuilder().apply {
        encodedPath = "/"
        val decodedSegments = parsed.encodedPath
            .split('/')
            .filter { it.isNotBlank() }
            .map { it.decodeURLPart() }
        if (decodedSegments.isNotEmpty()) {
            appendPathSegments(decodedSegments)
        }
    }.encodedPath.ensureTrailingSlash()

    return URLBuilder(parsed).apply {
        encodedUser = null
        encodedPassword = null
        encodedPath = normalizedPath
        encodedParameters.clear()
        encodedFragment = ""
        if (port == protocol.defaultPort) {
            port = DEFAULT_PORT
        }
    }.buildString()
}

fun displayWebDavRootUrl(rootUrl: String): String {
    return runCatching {
        val parsed = parseUrl(rootUrl) ?: return rootUrl
        val host = parsed.host.takeIf { it.isNotBlank() } ?: return rootUrl
        val hostDisplay = if (':' in host && !host.startsWith("[")) "[$host]" else host
        val portDisplay = if (parsed.port != parsed.protocol.defaultPort) ":${parsed.port}" else ""
        val decodedPath = parsed.encodedPath
            .split('/')
            .joinToString("/") { segment -> segment.decodeURLPart() }
        "${parsed.protocol.name}://$hostDisplay$portDisplay$decodedPath"
    }.getOrElse { rootUrl }
}

/** URL of a folder under the root, with the trailing slash WebDAV servers expect for collections. */
fun buildWebDavDirectoryUrl(rootUrl: String, relativePath: String): String =
    normalizeWebDavRootUrl(buildWebDavTrackUrl(rootUrl, relativePath))

fun buildWebDavTrackUrl(rootUrl: String, relativePath: String): String {
    val normalizedRoot = normalizeWebDavRootUrl(rootUrl)
    val segments = relativePath.trim().split('/').filter { it.isNotBlank() }
    return URLBuilder(normalizedRoot).apply {
        appendPathSegments(segments)
    }.buildString()
}

fun resolveWebDavRelativePath(rootUrl: String, href: String): String? {
    val normalizedRoot = normalizeWebDavRootUrl(rootUrl)
    val root = Url(normalizedRoot)
    val resolved = resolveWebDavHref(root, href.trim())
        ?.takeIf {
            it.protocol.name == root.protocol.name &&
                it.host == root.host &&
                it.port == root.port
        }
        ?: return null

    val rootPath = root.encodedPath.ensureTrailingSlash()
    val candidatePath = resolved.encodedPath
    val normalizedCandidate = when {
        candidatePath == rootPath.removeSuffix("/") -> rootPath
        candidatePath.endsWith("/") -> candidatePath
        else -> candidatePath
    }

    if (normalizedCandidate == rootPath) return ""
    if (!normalizedCandidate.startsWith(rootPath)) return null

    return normalizedCandidate.removePrefix(rootPath)
        .trim('/')
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/") { it.decodeURLPart() }
}

fun parseWebDavMultistatus(payload: String): List<WebDavPropfindEntry> {
    return RESPONSE_BLOCK_REGEX.findAll(payload)
        .mapNotNull { match ->
            val block = match.groupValues[1]
            val href = extractFirstTag(block, "href") ?: return@mapNotNull null
            val statusCodes = STATUS_TAG_REGEX.findAll(block)
                .mapNotNull { extractStatusCode(it.groupValues[1]) }
                .toList()
            if (statusCodes.isNotEmpty() && statusCodes.none { it in 200..299 }) {
                return@mapNotNull null
            }
            WebDavPropfindEntry(
                href = decodeXmlText(href).trim(),
                isDirectory = COLLECTION_TAG_REGEX.containsMatchIn(block),
                lastModified = extractFirstTag(block, "getlastmodified")?.let(::decodeXmlText)?.trim(),
                contentLength = extractFirstTag(block, "getcontentlength")
                    ?.let(::decodeXmlText)
                    ?.trim()
                    ?.toLongOrNull(),
            )
        }
        .toList()
}

@OptIn(ExperimentalEncodingApi::class)
fun buildBasicAuthorizationHeader(username: String, password: String): String? {
    if (username.isBlank()) return null
    val encoded = Base64.encode("$username:$password".encodeToByteArray())
    return "Basic $encoded"
}

enum class WebDavOperation(private val resource: org.jetbrains.compose.resources.StringResource) {
    TestConnection(Res.string.webdav_operation_test_connection),
    Scan(Res.string.webdav_operation_scan),
    ReadLyrics(Res.string.webdav_operation_read_lyrics),
    ProbeMetadata(Res.string.webdav_operation_probe_metadata),
    Playback(Res.string.webdav_operation_playback),
    ListFolders(Res.string.webdav_operation_list_folders);

    val text: UiText get() = uiText(resource)
}

fun webDavHttpFailureText(
    operation: WebDavOperation,
    statusCode: Int,
    authSent: Boolean,
    serverDetail: String?,
): UiText = webDavHttpFailureText(operation.text, statusCode, authSent, serverDetail)

/** Diagnostic compatibility for callers that already store an operation label. */
fun describeWebDavHttpFailure(
    operation: String,
    statusCode: Int,
    authSent: Boolean,
    serverDetail: String?,
): String = (webDavHttpFailureText(UiText.Raw(operation), statusCode, authSent, serverDetail)).diagnosticMessage()

private fun webDavHttpFailureText(
    operation: UiText,
    statusCode: Int,
    authSent: Boolean,
    serverDetail: String?,
): UiText {
    val normalizedServerDetail = serverDetail.orEmpty().trim()
    val detail = when {
        statusCode == 401 && !authSent && normalizedServerDetail.contains("Basic", ignoreCase = true) ->
            uiText(Res.string.webdav_anonymous_basic_auth_required)

        statusCode == 401 && !authSent ->
            uiText(Res.string.webdav_anonymous_auth_required)

        statusCode == 401 && authSent && normalizedServerDetail.contains("Basic", ignoreCase = true) ->
            uiText(Res.string.webdav_basic_auth_failed)

        statusCode == 401 ->
            uiText(Res.string.webdav_operation_auth_failed, operation)

        statusCode == 403 ->
            uiText(Res.string.webdav_operation_access_denied, operation)

        else ->
            uiText(Res.string.webdav_operation_http_failed, operation, statusCode)
    }
    return if (normalizedServerDetail.isBlank()) detail else
        uiText(Res.string.webdav_failure_with_server_detail, detail, UiText.Raw(normalizedServerDetail))
}

private fun resolveWebDavHref(root: Url, href: String): Url? {
    if (href.isBlank()) return null
    return when {
        href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true) -> parseUrl(href)
        href.startsWith("/") -> URLBuilder(root).apply {
            encodedPath = href.substringBefore('?').substringBefore('#')
            encodedParameters.clear()
            encodedFragment = ""
        }.build()

        else -> URLBuilder(root).apply {
            appendPathSegments(
                href.substringBefore('?').substringBefore('#')
                    .split('/')
                    .filter { it.isNotBlank() }
                    .map { it.decodeURLPart() },
            )
            encodedParameters.clear()
            encodedFragment = ""
        }.build()
    }
}

private fun extractFirstTag(block: String, tagName: String): String? {
    val regex = Regex(
        pattern = """(?s)<(?:(?:[\w.-]+):)?$tagName\b[^>]*>(.*?)</(?:(?:[\w.-]+):)?$tagName>""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    return regex.find(block)?.groupValues?.get(1)
}

private fun extractStatusCode(statusLine: String): Int? {
    return Regex("""\b(\d{3})\b""").find(statusLine)?.groupValues?.get(1)?.toIntOrNull()
}

private fun decodeXmlText(value: String): String {
    return value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

private val RESPONSE_BLOCK_REGEX = Regex(
    pattern = """(?s)<(?:(?:[\w.-]+):)?response\b[^>]*>(.*?)</(?:(?:[\w.-]+):)?response>""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val STATUS_TAG_REGEX = Regex(
    pattern = """(?s)<(?:(?:[\w.-]+):)?status\b[^>]*>(.*?)</(?:(?:[\w.-]+):)?status>""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val COLLECTION_TAG_REGEX = Regex(
    pattern = """<(?:(?:[\w.-]+):)?collection\b""",
    options = setOf(RegexOption.IGNORE_CASE),
)
