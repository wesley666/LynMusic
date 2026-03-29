package top.iwesley.lyn.music.core.model

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
    require(value.isNotBlank()) { "请填写 WebDAV 根 URL。" }
    require('?' !in value && '#' !in value) { "WebDAV 根 URL 不能包含 query 或 fragment。" }
    val parsed = parseUrl(value) ?: error("WebDAV 根 URL 无效。")
    require(parsed.protocol.name in setOf("http", "https")) { "WebDAV 根 URL 只支持 http 或 https。" }
    require(parsed.host.isNotBlank()) { "WebDAV 根 URL 缺少主机名。" }
    require(parsed.user == null && parsed.password == null) { "请不要在 WebDAV URL 中内嵌用户名或密码。" }

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

fun describeWebDavHttpFailure(
    operation: String,
    statusCode: Int,
    authSent: Boolean,
    serverDetail: String?,
): String {
    val normalizedServerDetail = serverDetail.orEmpty().trim()
    val detail = when {
        statusCode == 401 && !authSent && normalizedServerDetail.contains("Basic", ignoreCase = true) ->
            "WebDAV 服务端拒绝匿名访问，需要填写 Basic Auth 用户名。"

        statusCode == 401 && !authSent ->
            "WebDAV 服务端拒绝匿名访问，需要填写认证信息。"

        statusCode == 401 && authSent && normalizedServerDetail.contains("Basic", ignoreCase = true) ->
            "WebDAV Basic Auth 认证失败，请检查用户名和密码。"

        statusCode == 401 ->
            "WebDAV $operation 失败，请检查当前认证信息。"

        statusCode == 403 ->
            "WebDAV $operation 失败，当前账号没有访问权限。"

        else ->
            "WebDAV $operation 失败，HTTP $statusCode。"
    }
    return detail + normalizedServerDetail.takeIf { it.isNotBlank() }?.let { " 服务端信息: $it" }.orEmpty()
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
        pattern = """<(?:(?:[\w.-]+):)?$tagName\b[^>]*>(.*?)</(?:(?:[\w.-]+):)?$tagName>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
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
    pattern = """<(?:(?:[\w.-]+):)?response\b[^>]*>(.*?)</(?:(?:[\w.-]+):)?response>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val STATUS_TAG_REGEX = Regex(
    pattern = """<(?:(?:[\w.-]+):)?status\b[^>]*>(.*?)</(?:(?:[\w.-]+):)?status>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val COLLECTION_TAG_REGEX = Regex(
    pattern = """<(?:(?:[\w.-]+):)?collection\b""",
    options = setOf(RegexOption.IGNORE_CASE),
)
