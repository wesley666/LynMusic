package top.iwesley.lyn.music.domain

import top.iwesley.lyn.music.resources.*

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodedPath
import io.ktor.http.parseUrl
import io.ktor.util.encodeBase64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.FnMusicConnectionMode
import top.iwesley.lyn.music.core.model.FnMusicSourceDraft
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportScanWarning
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SignedRemoteStreamHooks
import top.iwesley.lyn.music.core.model.RemoteRequestSigner
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.UiTextArgumentException
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.buildFnMusicCoverLocator
import top.iwesley.lyn.music.core.model.buildFnMusicSongLocator
import top.iwesley.lyn.music.core.model.classifyAudioExtensionForImport
import top.iwesley.lyn.music.core.model.parseFnMusicCoverLocator
import top.iwesley.lyn.music.core.model.parseFnMusicSongLocator
import top.iwesley.lyn.music.core.model.redactRemoteSourceUrlForLog
import top.iwesley.lyn.music.core.model.requireUi
import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.unsupportedAudioImportFailure
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock

const val FN_MUSIC_LYRICS_SOURCE_ID = "fnmusic-lyrics"
internal const val FN_MUSIC_DEVICE_ID_CREDENTIAL_KEY = "fn-music-device-id"

/** `rootReference` of an FN Connect source; address-mode sources store their LAN URL there instead. */
const val FN_MUSIC_CONNECT_ROOT_PREFIX = "fnconnect://"

const val FN_MUSIC_API_PREFIX = "/music/api/v1"
private const val FN_MUSIC_SIGN_PREFIX = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
private const val FN_MUSIC_SIGN_KEY = "6D5602D4-A342-4799-A0F0-BB795E7167D0"
private const val FN_CONNECT_SIGN_KEY = "zIGtkc3dqZnJpd29qZXJqa2w7c"
private const val FN_CONNECT_RESOLVE_URL = "https://5ddd.com/api/v1/fn/con"
private const val FN_CONNECT_HOST_SUFFIX = ".5ddd.com"
private const val FN_MUSIC_PAGE_SIZE = 100
private const val FN_MUSIC_LISTING_ATTEMPTS = 3
private const val FN_MUSIC_METADATA_CONCURRENCY = 8
private const val FN_MUSIC_MAX_PAGE_SIZE = 200
private const val FN_MUSIC_LOCAL_PROBE_TIMEOUT_MILLIS = 3_000L
private const val FN_MUSIC_REMOTE_PROBE_TIMEOUT_MILLIS = 10_000L
private const val FN_MUSIC_COVER_SIZE = "640"
private const val FN_MUSIC_LOG_TAG = "FnMusic"
private val FN_MUSIC_SENSITIVE_HEADERS = listOf("cookie", "authorization", "authx", "x-access-code", "x-access-source")
private val fnMusicJson = Json { ignoreUnknownKeys = true }

/** The localized server name used as the `%1$s` argument of the shared `server_*` strings. */
val FN_MUSIC_NAME: UiText get() = uiText(Res.string.fn_music_name)

fun fnMusicAccessCodeCredentialKey(sourceId: String): String = "credential-$sourceId-fn-access"

data class FnMusicEndpoint(
    val baseUrl: String,
    val kind: RemoteSourceAddressKind,
    val relay: Boolean = false,
)

/** Everything needed to talk to one FN Music server, from a draft or a stored source. */
data class FnMusicConnection(
    val sourceId: String,
    val mode: FnMusicConnectionMode,
    val lanBaseUrl: String?,
    val wanBaseUrl: String?,
    val fnId: String?,
    val username: String,
    val password: String,
    val accessCode: String?,
    val deviceId: String,
) {
    internal val identity: String
        get() = listOf(mode.name, lanBaseUrl, wanBaseUrl, fnId, username, password, accessCode, deviceId)
            .joinToString(separator = "\u0000") { it.orEmpty() }
}

data class FnMusicFavoritePayload(val guid: String)

data class FnMusicPlaylistPayload(
    val guid: String,
    val name: String,
    val trackCount: Int?,
)

data class FnMusicRecentTrackPayload(
    val guid: String,
    val albumGuid: String?,
    val playedAt: Long?,
)

internal class FnMusicUnauthorizedException(text: UiText) : UiTextException(text), RemoteSourceHttpFailure {
    override val httpStatusCode: Int = 401
}

/** A write may or may not have been applied (the connection broke after sending); it must not be resent elsewhere. */
internal class FnMusicAmbiguousWriteException(val writeFailure: Throwable) : UiTextArgumentException(
    text = uiText(Res.string.fn_music_write_uncertain),
    diagnosticMessage = "FN Music write not confirmed: ${writeFailure::class.simpleName}: ${writeFailure.message.orEmpty()}",
)

internal class FnMusicAccessCodeException :
    UiTextArgumentException(uiText(Res.string.fn_music_access_code_invalid)), RemoteSourceHttpFailure {
    override val httpStatusCode: Int = 403
}

// region Wire format

/**
 * Builds `{base}/music/api/v1{path}` the way the FN Music apps do: a base that already ends with `/music`
 * or `/music/api/v1` is completed, not duplicated. Query parameters are sorted, matching the signature.
 */
fun buildFnMusicApiUrl(baseUrl: String, path: String, query: List<Pair<String, String>> = emptyList()): String {
    val parsed = parseUrl(baseUrl) ?: throw UiTextException(uiText(Res.string.server_address_invalid, FN_MUSIC_NAME))
    var basePath = parsed.encodedPath.trim('/')
    if (basePath != "music/api/v1" && !basePath.endsWith("/music/api/v1")) {
        basePath += if (basePath == "music" || basePath.endsWith("/music")) "/api/v1" else "/music/api/v1"
    }
    val fullPath = "/" + basePath.trim('/') + path
    val encodedQuery = sortedFnMusicQuery(query).joinToString("&") { (name, value) ->
        name.encodeURLParameter() + "=" + value.encodeURLParameter()
    }
    return URLBuilder(parsed).apply {
        encodedPath = fullPath
        encodedParameters.clear()
        encodedFragment = ""
    }.buildString().substringBefore('?') + if (encodedQuery.isEmpty()) "" else "?$encodedQuery"
}

private fun sortedFnMusicQuery(query: List<Pair<String, String>>): List<Pair<String, String>> =
    query.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })

/**
 * The `authx` header value. [signedPath] is the URL path from `/music/api/v1` on (or the whole path when
 * the prefix is absent); [data] is the decoded, sorted query string for GET or the exact body for POST.
 */
fun fnMusicSignature(
    signedPath: String,
    data: String,
    nonce: String,
    timestampMillis: Long,
    key: String = FN_MUSIC_SIGN_KEY,
): String {
    val sign = md5Hex("${FN_MUSIC_SIGN_PREFIX}_${signedPath}_${nonce}_${timestampMillis}_${md5Hex(data)}_$key")
    return "nonce=$nonce&timestamp=$timestampMillis&sign=$sign"
}

/** Signs a request for [url]; GET signs the decoded sorted query, POST signs [body]. */
fun fnMusicAuthx(
    url: String,
    body: String? = null,
    key: String = FN_MUSIC_SIGN_KEY,
    nonce: String = Random.nextInt(100_000, 1_000_000).toString(),
    timestampMillis: Long = Clock.System.now().toEpochMilliseconds(),
): String {
    val parsed = Url(url)
    val path = parsed.encodedPath.decodeURLPart().let { path ->
        val index = path.indexOf(FN_MUSIC_API_PREFIX)
        if (index >= 0) path.substring(index) else path
    }
    val data = body ?: parsed.encodedQuery
        .split('&')
        .filter { it.isNotEmpty() }
        .map { part ->
            val name = part.substringBefore('=').decodeURLPart()
            val value = part.substringAfter('=', "").decodeURLPart()
            name to value
        }
        .let(::sortedFnMusicQuery)
        .joinToString("&") { (name, value) -> "$name=$value" }
    return fnMusicSignature(path, data, nonce, timestampMillis, key)
}

/**
 * Returns [headers] with a fresh `authx` for [url]. Players call this before every media request because the
 * signature carries a timestamp; headers without `authx` (e.g. after a cross-origin redirect) stay unsigned.
 */
fun signFnMusicMediaHeaders(url: String, headers: Map<String, String>): Map<String, String> {
    if (headers.keys.none { it.equals("authx", ignoreCase = true) }) return headers
    return headers.filterKeys { !it.equals("authx", ignoreCase = true) } + ("authx" to fnMusicAuthx(url))
}

/**
 * Headers for following a media redirect from [fromUrl] to [toUrl]: a different origin (e.g. a CDN) never
 * receives the session cookie, signature or access code; the same origin is re-signed. Returns null when the
 * redirect must not be followed (non-http scheme, embedded credentials or an https→http downgrade).
 */
fun fnMusicRedirectHeaders(fromUrl: String, toUrl: String, headers: Map<String, String>): Map<String, String>? {
    val from = parseUrl(fromUrl) ?: return null
    val to = parseUrl(toUrl) ?: return null
    val toScheme = to.protocol.name.lowercase()
    if (toScheme != "http" && toScheme != "https") return null
    if (to.user != null || to.password != null) return null
    if (from.protocol.name.equals("https", ignoreCase = true) && toScheme == "http") return null
    if (from.fnMusicOrigin() != to.fnMusicOrigin()) {
        return headers.filterKeys { name -> FN_MUSIC_SENSITIVE_HEADERS.none { it.equals(name, ignoreCase = true) } }
    }
    return if (to.encodedPath.contains(FN_MUSIC_API_PREFIX)) signFnMusicMediaHeaders(toUrl, headers) else headers
}

private fun Url.fnMusicOrigin(): String = "${protocol.name.lowercase()}://${host.lowercase()}:$port"

/** Normalizes a user-entered FN ID or `xxx.5ddd.com` address to the lower-case ID. */
fun normalizeFnMusicId(raw: String?): String {
    val value = raw.orEmpty().trim()
    requireUi(value.isNotBlank()) { uiText(Res.string.fn_music_fn_id_required) }
    val id = if ('.' in value || "://" in value) {
        val host = parseUrl(if ("://" in value) value else "https://$value")?.host.orEmpty().lowercase()
        requireUi(host.endsWith(FN_CONNECT_HOST_SUFFIX)) { uiText(Res.string.fn_music_fn_id_invalid) }
        host.removeSuffix(FN_CONNECT_HOST_SUFFIX)
    } else {
        value
    }
    requireUi(id.isNotEmpty() && id.length <= 63 && id.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' }) {
        uiText(Res.string.fn_music_fn_id_invalid)
    }
    return id.lowercase()
}

fun normalizeFnMusicBaseUrl(rawUrl: String?): String {
    val value = rawUrl.orEmpty().trim()
    requireUi(value.isNotBlank()) { uiText(Res.string.server_address_required, FN_MUSIC_NAME) }
    requireUi('?' !in value && '#' !in value) { uiText(Res.string.server_address_no_query, FN_MUSIC_NAME) }
    val parsed = parseUrl(value) ?: throw UiTextException(uiText(Res.string.server_address_invalid, FN_MUSIC_NAME))
    requireUi(parsed.protocol.name in setOf("http", "https")) { uiText(Res.string.server_address_http_required, FN_MUSIC_NAME) }
    requireUi(parsed.host.isNotBlank()) { uiText(Res.string.server_address_host_required, FN_MUSIC_NAME) }
    requireUi(parsed.user == null && parsed.password == null) { uiText(Res.string.server_address_no_credentials, FN_MUSIC_NAME) }
    val decodedSegments = parsed.encodedPath.split('/').filter { it.isNotBlank() }.map { it.decodeURLPart() }
    val normalizedPath = URLBuilder().apply {
        encodedPath = "/"
        if (decodedSegments.isNotEmpty()) appendPathSegments(decodedSegments)
    }.encodedPath.removeSuffix("/").ifBlank { "/" }
    return URLBuilder(parsed).apply {
        encodedUser = null
        encodedPassword = null
        encodedParameters.clear()
        encodedFragment = ""
        encodedPath = normalizedPath
        if (port == protocol.defaultPort) port = DEFAULT_PORT
    }.buildString().removeSuffix("/")
}

suspend fun resolveFnMusicDeviceId(secureCredentialStore: SecureCredentialStore): String {
    secureCredentialStore.get(FN_MUSIC_DEVICE_ID_CREDENTIAL_KEY)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    val bytes = ByteArray(16).also(Random::nextBytes)
    val deviceId = bytes.joinToString(separator = "") { ((it.toInt() and 0xff).toString(16)).padStart(2, '0') }
    secureCredentialStore.put(FN_MUSIC_DEVICE_ID_CREDENTIAL_KEY, deviceId)
    return deviceId
}

/** Validates a draft and normalizes its addresses or FN ID. */
fun prepareFnMusicDraft(draft: FnMusicSourceDraft): FnMusicSourceDraft {
    requireUi(draft.username.isNotBlank()) { uiText(Res.string.server_username_required, FN_MUSIC_NAME) }
    return when (draft.connectionMode) {
        FnMusicConnectionMode.ADDRESS -> {
            val (lan, wan) = normalizeRemoteSourceBaseUrls(
                sourceType = ImportSourceType.FN_MUSIC,
                lanBaseUrl = draft.baseUrl,
                wanBaseUrl = draft.wanBaseUrl,
                normalizeBaseUrl = ::normalizeFnMusicBaseUrl,
            )
            draft.copy(label = draft.label.trim(), baseUrl = lan, wanBaseUrl = wan.orEmpty(), fnId = "", username = draft.username.trim())
        }
        FnMusicConnectionMode.FN_CONNECT -> draft.copy(
            label = draft.label.trim(),
            baseUrl = "",
            wanBaseUrl = "",
            fnId = normalizeFnMusicId(draft.fnId),
            username = draft.username.trim(),
        )
    }
}

fun FnMusicSourceDraft.toFnMusicConnection(sourceId: String, deviceId: String): FnMusicConnection = FnMusicConnection(
    sourceId = sourceId,
    mode = connectionMode,
    lanBaseUrl = baseUrl.takeIf { connectionMode == FnMusicConnectionMode.ADDRESS && it.isNotBlank() },
    wanBaseUrl = wanBaseUrl.takeIf { connectionMode == FnMusicConnectionMode.ADDRESS && it.isNotBlank() },
    fnId = fnId.takeIf { connectionMode == FnMusicConnectionMode.FN_CONNECT && it.isNotBlank() },
    username = username,
    password = password,
    accessCode = accessCode.takeIf { it.isNotBlank() },
    deviceId = deviceId,
)

/** The `rootReference` stored for a prepared draft. */
fun FnMusicSourceDraft.fnMusicRootReference(): String = when (connectionMode) {
    FnMusicConnectionMode.ADDRESS -> baseUrl
    FnMusicConnectionMode.FN_CONNECT -> FN_MUSIC_CONNECT_ROOT_PREFIX + fnId
}

fun fnMusicConnectionModeOf(rootReference: String?): FnMusicConnectionMode =
    if (rootReference.orEmpty().startsWith(FN_MUSIC_CONNECT_ROOT_PREFIX)) FnMusicConnectionMode.FN_CONNECT else FnMusicConnectionMode.ADDRESS

fun fnMusicIdOf(rootReference: String?): String? =
    rootReference?.takeIf { it.startsWith(FN_MUSIC_CONNECT_ROOT_PREFIX) }?.removePrefix(FN_MUSIC_CONNECT_ROOT_PREFIX)

// endregion

// region Sessions

/** A resolved FN Connect route and the network it was resolved on; read and replaced as one value. */
internal data class FnMusicRouteCache(val endpoint: FnMusicEndpoint, val networkVersion: Long)

internal class FnMusicSession(val identity: String) {
    val loginMutex = Mutex()
    val routeMutex = Mutex()

    /** Read without the lock by many threads (players, readers); written under [loginMutex]. */
    @Volatile
    var token: String? = null

    /** Read without the lock on the fast path; written under [routeMutex]. */
    @Volatile
    var route: FnMusicRouteCache? = null
}

/** Process-wide token and FN Connect route cache, keyed by source id and invalidated by any connection change. */
object FnMusicSessions {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, FnMusicSession>()

    internal suspend fun sessionFor(connection: FnMusicConnection): FnMusicSession {
        if (connection.sourceId.isBlank()) return FnMusicSession(connection.identity)
        return mutex.withLock {
            sessions[connection.sourceId]?.takeIf { it.identity == connection.identity }
                ?: FnMusicSession(connection.identity).also { sessions[connection.sourceId] = it }
        }
    }

    suspend fun invalidate(sourceId: String) {
        mutex.withLock { sessions.remove(sourceId) }
    }

    suspend fun clear() {
        mutex.withLock { sessions.clear() }
    }
}

// endregion

// region Client

internal class FnMusicClient(
    private val connection: FnMusicConnection,
    private val httpClient: LyricsHttpClient,
    private val addressSelector: RemoteSourceAddressSelector?,
    private val logger: DiagnosticLogger,
    private val timeoutMillis: Long?,
    private val session: FnMusicSession,
) {
    /**
     * Sends a JSON API call and returns the envelope's `data`, logging in and re-authenticating as needed. A write
     * (a call with a [body]) only moves to another address when the request certainly never left; once it may have
     * reached the server, resending it could apply it twice, so it fails with [FnMusicAmbiguousWriteException].
     */
    suspend fun call(operation: String, path: String, query: List<Pair<String, String>> = emptyList(), body: JsonObject? = null): JsonElement {
        repeat(2) { attempt ->
            val token = currentToken()
            try {
                return withEndpoint { endpoint ->
                    try {
                        // A write must not be resent by the HTTP library either, for the same reason.
                        send(endpoint, operation, path, query, body, token, allowTransportRetry = body == null)
                    } catch (failure: Throwable) {
                        if (failure is CancellationException) throw failure
                        if (body != null && isRemoteSourceAddressFallbackAllowed(failure) && !isRequestNotSentFailure(failure)) {
                            throw FnMusicAmbiguousWriteException(failure)
                        }
                        throw failure
                    }
                }
            } catch (unauthorized: FnMusicUnauthorizedException) {
                if (attempt > 0) throw unauthorized
                clearToken(token)
            }
        }
        error("unreachable")
    }

    /**
     * A signed media request, e.g. `/track/stream`, for each usable endpoint in preference order; its headers are
     * re-signed per request by [signFnMusicMediaHeaders]. Endpoints whose API root is in [excludedApiRoots] (they just
     * failed media requests) are avoided while anything else remains.
     */
    suspend fun mediaRequests(
        path: String,
        query: List<Pair<String, String>>,
        excludedApiRoots: Set<String> = emptySet(),
    ): List<RemotePlaybackUrlCandidate> {
        val token = currentToken()
        return endpoints(excludedApiRoots).map { endpoint ->
            val url = buildFnMusicApiUrl(endpoint.baseUrl, path, query)
            RemotePlaybackUrlCandidate(
                sourceId = connection.sourceId,
                addressKind = endpoint.kind.name,
                value = url,
                headers = requestHeaders(endpoint, token, contentType = false, json = false) + ("authx" to fnMusicAuthx(url)),
            )
        }
    }

    /** Logs in again unless a concurrent caller already replaced [staleToken]; returns the token to use now. */
    suspend fun reauthorize(staleToken: String?): String {
        staleToken?.let { clearToken(it) }
        return currentToken()
    }

    /** Forgets the cached FN Connect route so the next request resolves it again. */
    suspend fun invalidateRoute() {
        session.routeMutex.withLock { session.route = null }
    }

    private suspend fun currentToken(): String {
        session.token?.let { return it }
        return session.loginMutex.withLock {
            session.token ?: login().also { session.token = it }
        }
    }

    private suspend fun clearToken(token: String) {
        session.loginMutex.withLock {
            if (session.token == token) session.token = null
        }
    }

    private suspend fun login(): String {
        requireUi(connection.username.isNotBlank()) { uiText(Res.string.server_username_required, FN_MUSIC_NAME) }
        requireUi(connection.password.isNotBlank()) { uiText(Res.string.server_password_required, FN_MUSIC_NAME) }
        val body = sortedJsonObject(
            "deviceId" to JsonPrimitive(connection.deviceId),
            "password" to JsonPrimitive(sha256Hex(connection.password)),
            "username" to JsonPrimitive(connection.username),
        )
        val data = withEndpoint { endpoint ->
            send(endpoint, "password-login", "/user/password-login", emptyList(), body, token = null)
        }
        return (data as? JsonObject)?.string("userToken")
            ?: throw UiTextException(uiText(Res.string.fn_music_response_invalid, "userToken"))
    }

    private suspend fun endpoints(excludedApiRoots: Set<String> = emptySet()): List<FnMusicEndpoint> = when (connection.mode) {
        FnMusicConnectionMode.ADDRESS -> {
            val all = addressBaseUrls().map { FnMusicEndpoint(it.value, it.kind) }
            all.filterNot { fnMusicApiRootOf(it.baseUrl) in excludedApiRoots }.ifEmpty { all }
        }
        FnMusicConnectionMode.FN_CONNECT -> {
            if (excludedApiRoots.isEmpty()) {
                listOf(connectEndpoint())
            } else {
                // A NAS that fails media requests on one port fails them on the other too: skip its whole host.
                val excludedHosts = excludedApiRoots.mapNotNull(::fnMusicHostOf).toSet()
                val isExcluded = { endpoint: FnMusicEndpoint -> fnMusicHostOf(endpoint.baseUrl) in excludedHosts }
                listOf(connectEndpoint(excluded = isExcluded))
            }
        }
    }

    private fun addressBaseUrls(): List<RemoteSourceBaseUrl> {
        val selector = addressSelector
        if (selector != null && connection.sourceId.isNotBlank()) {
            return selector.orderedBaseUrls(
                sourceId = connection.sourceId,
                sourceType = ImportSourceType.FN_MUSIC,
                lanBaseUrl = connection.lanBaseUrl,
                wanBaseUrl = connection.wanBaseUrl,
                normalizeBaseUrl = ::normalizeFnMusicBaseUrl,
            )
        }
        return buildList {
            connection.lanBaseUrl?.let { add(RemoteSourceBaseUrl(RemoteSourceAddressKind.LAN, normalizeFnMusicBaseUrl(it))) }
            connection.wanBaseUrl?.let { add(RemoteSourceBaseUrl(RemoteSourceAddressKind.WAN, normalizeFnMusicBaseUrl(it))) }
        }.ifEmpty { throw UiTextException(uiText(Res.string.source_server_address_required, FN_MUSIC_NAME)) }
    }

    /** Runs [block] against the preferred endpoint, moving to the next address (or a fresh FN Connect route) on transport failures. */
    private suspend fun <T> withEndpoint(block: suspend (FnMusicEndpoint) -> T): T {
        return when (connection.mode) {
            FnMusicConnectionMode.ADDRESS -> {
                val selector = addressSelector
                if (selector != null && connection.sourceId.isNotBlank()) {
                    selector.withAddressFallback(
                        sourceId = connection.sourceId,
                        sourceType = ImportSourceType.FN_MUSIC,
                        lanBaseUrl = connection.lanBaseUrl,
                        wanBaseUrl = connection.wanBaseUrl,
                        normalizeBaseUrl = ::normalizeFnMusicBaseUrl,
                    ) { candidate -> block(FnMusicEndpoint(candidate.value, candidate.kind)) }
                } else {
                    val candidates = addressBaseUrls()
                    var lastFailure: Throwable? = null
                    candidates.forEachIndexed { index, candidate ->
                        try {
                            return block(FnMusicEndpoint(candidate.value, candidate.kind))
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            lastFailure = throwable
                            if (index == candidates.lastIndex || !isRemoteSourceAddressFallbackAllowed(throwable)) throw throwable
                        }
                    }
                    throw lastFailure ?: UiTextException(uiText(Res.string.fn_music_unreachable))
                }
            }
            FnMusicConnectionMode.FN_CONNECT -> {
                val endpoint = connectEndpoint()
                try {
                    block(endpoint)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    if (!isRemoteSourceAddressFallbackAllowed(throwable)) throw throwable
                    // The cached route went away (e.g. left the LAN); resolve again once.
                    invalidateRoute(endpoint)
                    block(connectEndpoint(excluded = { it.baseUrl == endpoint.baseUrl }))
                }
            }
        }
    }

    private suspend fun invalidateRoute(endpoint: FnMusicEndpoint) {
        session.routeMutex.withLock {
            if (session.route?.endpoint == endpoint) session.route = null
        }
    }

    /**
     * The cached FN Connect route when it was resolved on the current network and is not [excluded]; otherwise a fresh
     * resolution (avoiding [excluded]) that replaces the cache. Both the lock-free check and the locked re-check apply
     * the same conditions, so a failing route written back by a concurrent caller is never reused against an exclusion.
     */
    private suspend fun connectEndpoint(excluded: (FnMusicEndpoint) -> Boolean = { false }): FnMusicEndpoint {
        val networkVersion = addressSelector?.networkVersion() ?: 0L
        fun FnMusicRouteCache?.usable(): FnMusicEndpoint? =
            this?.takeIf { it.networkVersion == networkVersion && !excluded(it.endpoint) }?.endpoint
        session.route.usable()?.let { return it }
        return session.routeMutex.withLock {
            session.route.usable()
                ?: resolveFnConnectEndpoint(excluded).also { session.route = FnMusicRouteCache(it, networkVersion) }
        }
    }

    private suspend fun resolveFnConnectEndpoint(excluded: (FnMusicEndpoint) -> Boolean): FnMusicEndpoint {
        val fnId = normalizeFnMusicId(connection.fnId)
        val body = JsonObject(mapOf("fnId" to JsonPrimitive(fnId))).toString()
        val request = LyricsRequest(
            method = RequestMethod.POST,
            url = FN_CONNECT_RESOLVE_URL,
            headers = mapOf(
                "Content-Type" to "application/json",
                "authx" to fnMusicAuthx(FN_CONNECT_RESOLVE_URL, body = body, key = FN_CONNECT_SIGN_KEY),
            ),
            body = body,
            timeoutMillis = FN_MUSIC_REMOTE_PROBE_TIMEOUT_MILLIS,
        )
        logRequest("fn-connect", request.url)
        val response = httpClient.request(request).getOrElse { throwable ->
            throw RemoteSourceRequestException(uiText(Res.string.server_request_failed, FN_MUSIC_NAME, "FN Connect", throwable.uiErrorDetail()), throwable)
        }
        val payload = parseEnvelope(response.statusCode, response.body, "FN Connect") as? JsonObject
            ?: throw UiTextException(uiText(Res.string.fn_music_response_invalid, "FN Connect"))
        val groups = fnConnectEndpointGroups(fnId, payload)
        val allExcluded = groups.flatten().all(excluded)
        val available = if (allExcluded) groups else groups.map { group -> group.filterNot(excluded) }
        var deterministic: Throwable? = null
        var lastFailure: Throwable? = null
        for (group in available) {
            if (group.isEmpty()) continue
            val results = coroutineScope {
                group.map { endpoint ->
                    async {
                        try {
                            Result.success(probe(endpoint))
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            Result.failure(throwable)
                        }
                    }
                }.awaitAll()
            }
            results.firstNotNullOfOrNull { it.getOrNull() }?.let { return it }
            results.mapNotNull { it.exceptionOrNull() }.forEach { failure ->
                lastFailure = failure
                if (!isRemoteSourceAddressFallbackAllowed(failure) && deterministic == null) deterministic = failure
            }
        }
        throw deterministic ?: lastFailure?.let { RemoteSourceRequestException(uiText(Res.string.fn_music_unreachable), it) }
            ?: UiTextException(uiText(Res.string.fn_music_unreachable))
    }

    private suspend fun probe(endpoint: FnMusicEndpoint): FnMusicEndpoint {
        val timeout = if (endpoint.kind == RemoteSourceAddressKind.LAN) FN_MUSIC_LOCAL_PROBE_TIMEOUT_MILLIS else FN_MUSIC_REMOTE_PROBE_TIMEOUT_MILLIS
        val headers = requestHeaders(endpoint, token = null, contentType = false)
        val verifyUrl = endpoint.baseUrl.trimEnd('/') + "/access_code_verify"
        val verify = httpClient.request(LyricsRequest(RequestMethod.GET, verifyUrl, headers, timeoutMillis = timeout)).getOrElse { throwable ->
            throw RemoteSourceRequestException(uiText(Res.string.server_request_failed, FN_MUSIC_NAME, "access_code_verify", throwable.uiErrorDetail()), throwable)
        }
        if (verify.statusCode in setOf(401, 403, 429)) throw FnMusicAccessCodeException()
        if (verify.statusCode !in 200..299 && verify.statusCode != 404) {
            throw RemoteSourceHttpException(verify.statusCode, uiText(Res.string.server_http_failed, FN_MUSIC_NAME, "access_code_verify", verify.statusCode))
        }
        val url = buildFnMusicApiUrl(endpoint.baseUrl, "/sys/config")
        val response = httpClient.request(
            LyricsRequest(RequestMethod.GET, url, headers + ("authx" to fnMusicAuthx(url)), timeoutMillis = timeout),
        ).getOrElse { throwable ->
            throw RemoteSourceRequestException(uiText(Res.string.server_request_failed, FN_MUSIC_NAME, "sys/config", throwable.uiErrorDetail()), throwable)
        }
        if (response.statusCode in setOf(401, 403, 429)) throw FnMusicAccessCodeException()
        parseEnvelope(response.statusCode, response.body, "sys/config")
        return endpoint
    }

    private suspend fun send(
        endpoint: FnMusicEndpoint,
        operation: String,
        path: String,
        query: List<Pair<String, String>>,
        body: JsonObject?,
        token: String?,
        allowTransportRetry: Boolean = true,
    ): JsonElement {
        val url = buildFnMusicApiUrl(endpoint.baseUrl, path, query)
        val bodyText = body?.toString()
        val request = LyricsRequest(
            method = if (body == null) RequestMethod.GET else RequestMethod.POST,
            url = url,
            headers = requestHeaders(endpoint, token, contentType = body != null) + ("authx" to fnMusicAuthx(url, bodyText)),
            body = bodyText,
            timeoutMillis = timeoutMillis,
            allowTransportRetry = allowTransportRetry,
        )
        logRequest(operation, url)
        val response = httpClient.request(request).getOrElse { throwable ->
            throw RemoteSourceRequestException(uiText(Res.string.server_request_failed, FN_MUSIC_NAME, operation, throwable.uiErrorDetail()), throwable)
        }
        return parseEnvelope(response.statusCode, response.body, operation)
    }

    /** [json] asks for a JSON answer; media requests (audio, covers) must not, or a negotiating proxy may refuse them. */
    private fun requestHeaders(
        endpoint: FnMusicEndpoint,
        token: String?,
        contentType: Boolean,
        json: Boolean = true,
    ): Map<String, String> = buildMap {
        if (json) put("Accept", "application/json")
        if (contentType) put("Content-Type", "application/json")
        val cookies = buildList {
            token?.let { add("music-token=" + it.encodeURLParameter()) }
            if (endpoint.relay) add("mode=relay")
        }
        if (cookies.isNotEmpty()) put("Cookie", cookies.joinToString("; "))
        connection.accessCode?.takeIf { it.isNotEmpty() }?.let { code ->
            put("x-access-code", code.encodeToByteArray().encodeBase64())
            put("x-access-source", "app")
        }
    }

    private fun logRequest(operation: String, url: String) {
        if (logger === NoopDiagnosticLogger) return
        logger.log(DiagnosticLogLevel.INFO, FN_MUSIC_LOG_TAG, "request operation=$operation\nurl: ${redactRemoteSourceUrlForLog(url)}")
    }
}

/** Parses the `{code, msg, data}` envelope and returns `data`. */
internal fun parseEnvelope(statusCode: Int, body: String, operation: String): JsonElement {
    if (statusCode == 401 || statusCode == 403) {
        throw FnMusicUnauthorizedException(uiText(Res.string.server_http_failed, FN_MUSIC_NAME, operation, statusCode))
    }
    if (statusCode !in 200..299) {
        throw RemoteSourceHttpArgumentException(statusCode, uiText(Res.string.server_http_failed, FN_MUSIC_NAME, operation, statusCode))
    }
    val payload = runCatching { fnMusicJson.parseToJsonElement(body) }.getOrNull() as? JsonObject
        ?: throw UiTextException(uiText(Res.string.server_response_not_object, FN_MUSIC_NAME, operation))
    val code = payload.int("code") ?: throw UiTextException(uiText(Res.string.fn_music_response_invalid, operation))
    val message = payload.string("msg")?.trim()?.takeIf { it.isNotEmpty() }
    if (code == 401 || code == 120001 || message?.contains("invalid token", ignoreCase = true) == true) {
        // Show the server's own reason (e.g. signed in elsewhere) rather than a status the server never sent.
        throw FnMusicUnauthorizedException(
            if (message != null) {
                uiText(Res.string.fn_music_server_error, message)
            } else {
                uiText(Res.string.server_http_failed, FN_MUSIC_NAME, operation, 401)
            },
        )
    }
    if (code != 0 && code != 200) {
        throw UiTextException(uiText(Res.string.fn_music_server_error, message ?: code.toString()))
    }
    return payload["data"] ?: JsonNull
}

/** FN Connect candidates in probe order: LAN http, LAN https, public IPv6, public IPv4, then relays. */
internal fun fnConnectEndpointGroups(fnId: String, payload: JsonObject): List<List<FnMusicEndpoint>> {
    fun strings(key: String): List<String> = when (val element = payload[key]) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
        is JsonPrimitive -> listOfNotNull(element.contentOrNull?.trim()?.takeIf(String::isNotEmpty))
        else -> emptyList()
    }
    val ports = payload["port"] as? JsonObject
    val httpsPort = ports?.int("httpsPort") ?: 5667
    val httpPort = ports?.int("httpPort") ?: 5666
    fun candidates(hosts: List<String>, scheme: String, port: Int, kind: RemoteSourceAddressKind): List<FnMusicEndpoint> {
        if (port !in 1..65535) return emptyList()
        return hosts.map { host -> FnMusicEndpoint("$scheme://${bracketIpv6(host)}:$port", kind) }
    }
    val local = strings("ipv4") + strings("ipv6")
    val relays = strings("fn").ifEmpty { listOf("$fnId$FN_CONNECT_HOST_SUFFIX") }
    return listOf(
        candidates(local, "http", httpPort, RemoteSourceAddressKind.LAN),
        candidates(local, "https", httpsPort, RemoteSourceAddressKind.LAN),
        candidates(strings("publicIpv6"), "https", httpsPort, RemoteSourceAddressKind.WAN),
        candidates(strings("publicIpv4"), "https", httpsPort, RemoteSourceAddressKind.WAN),
        relays.map { relay ->
            FnMusicEndpoint(if ("://" in relay) relay.trimEnd('/') else "https://$relay", RemoteSourceAddressKind.WAN, relay = true)
        },
    )
}

private fun bracketIpv6(host: String): String {
    val value = host.trim()
    if (value.startsWith("[") && value.endsWith("]")) return value
    return if (':' in value) "[$value]" else value
}

// endregion

// region Media credential recovery

/**
 * True when FN Music refused a media request's session: HTTP 401/403, or a JSON answer (FN Music reports these with
 * HTTP 200 too) whose code is 401/120001 or whose message says the token is invalid.
 */
fun isFnMusicMediaAuthFailure(statusCode: Int, body: String?): Boolean {
    if (statusCode == 401 || statusCode == 403) return true
    val payload = body?.let { runCatching { fnMusicJson.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return false
    val code = payload.int("code")
    return code == 401 || code == 120001 || payload.string("msg")?.contains("invalid token", ignoreCase = true) == true
}

/** `{base}/music/api/v1` of an FN Music URL (an endpoint base or a full media URL); identifies the endpoint. */
fun fnMusicApiRootOf(url: String): String {
    val index = url.indexOf(FN_MUSIC_API_PREFIX)
    return if (index >= 0) url.substring(0, index + FN_MUSIC_API_PREFIX.length) else buildFnMusicApiUrl(url, "")
}

private fun fnMusicHostOf(url: String): String? = parseUrl(url)?.host?.lowercase()?.removePrefix("[")?.removeSuffix("]")

/** The session token carried in a media request's `Cookie` header. */
fun fnMusicTokenFromHeaders(headers: Map<String, String>): String? {
    val cookie = headers.entries.firstOrNull { it.key.equals("Cookie", ignoreCase = true) }?.value ?: return null
    return cookie.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("music-token=") }
        ?.removePrefix("music-token=")
        ?.decodeURLPart()
        ?.takeIf { it.isNotEmpty() }
}

/** [headers] with the session token replaced; other cookies (e.g. `mode=relay`) and the access code are kept. */
fun withFnMusicToken(headers: Map<String, String>, token: String): Map<String, String> {
    val cookieName = headers.keys.firstOrNull { it.equals("Cookie", ignoreCase = true) } ?: "Cookie"
    val others = headers[cookieName].orEmpty().split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("music-token=") }
    val cookie = (listOf("music-token=" + token.encodeURLParameter()) + others).joinToString("; ")
    return headers - cookieName + (cookieName to cookie)
}

/** [candidate] (a stream or cover request) with a fresh session token after FN Music refused the old one. */
suspend fun refreshFnMusicCandidate(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    candidate: RemotePlaybackUrlCandidate,
    httpClient: LyricsHttpClient,
    addressSelector: RemoteSourceAddressSelector,
): RemotePlaybackUrlCandidate? {
    val staleToken = fnMusicTokenFromHeaders(candidate.headers) ?: return null
    val source = resolveFnMusicSource(database, secureCredentialStore, candidate.sourceId) ?: return null
    val token = source.client(httpClient, addressSelector).reauthorize(staleToken)
    return candidate.copy(headers = withFnMusicToken(candidate.headers, token))
}

/** What a platform needs to build a recoverable FN Music stream: the signed candidates and FN Music's hooks. */
class FnMusicStreamSpec(
    val candidates: List<RemotePlaybackUrlCandidate>,
    val hooks: FnMusicStreamHooks,
)

/**
 * Resolves a `lynmusic-fnmusic://` locator against the database and credentials into a [FnMusicStreamSpec]; null for
 * other locators or an unavailable source. Platforms only wrap it in a `SignedRemoteStream`.
 */
suspend fun resolveFnMusicStreamSpec(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    httpClient: LyricsHttpClient,
    addressSelector: RemoteSourceAddressSelector,
): FnMusicStreamSpec? {
    val candidates = resolveFnMusicStreamCandidates(database, secureCredentialStore, locator, httpClient, addressSelector)
        ?: return null
    return FnMusicStreamSpec(
        candidates = candidates,
        hooks = FnMusicStreamHooks(database, secureCredentialStore, locator, httpClient, addressSelector),
    )
}

/** Signs FN Music media requests per hop: the same origin is re-signed, another origin never sees the session. */
object FnMusicRequestSigner : RemoteRequestSigner {
    override fun sign(url: String, headers: Map<String, String>): Map<String, String> = signFnMusicMediaHeaders(url, headers)

    override fun redirectHeaders(fromUrl: String, toUrl: String, headers: Map<String, String>): Map<String, String>? =
        fnMusicRedirectHeaders(fromUrl, toUrl, headers)
}

/** FN Music's side of a recoverable signed stream: signing, re-login on refused sessions and route re-resolution. */
class FnMusicStreamHooks(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val locator: String,
    private val httpClient: LyricsHttpClient,
    private val addressSelector: RemoteSourceAddressSelector,
) : SignedRemoteStreamHooks, RemoteRequestSigner by FnMusicRequestSigner {

    override fun isAuthFailure(candidate: RemotePlaybackUrlCandidate, statusCode: Int, body: String?): Boolean =
        isFnMusicMediaAuthFailure(statusCode, body)

    override suspend fun reauthorize(candidate: RemotePlaybackUrlCandidate): RemotePlaybackUrlCandidate? =
        refreshFnMusicCandidate(database, secureCredentialStore, candidate, httpClient, addressSelector)

    override suspend fun reresolve(failedCandidates: List<RemotePlaybackUrlCandidate>): List<RemotePlaybackUrlCandidate>? {
        val (sourceId, _) = parseFnMusicSongLocator(locator) ?: return null
        resolveFnMusicSource(database, secureCredentialStore, sourceId)?.client(httpClient, addressSelector)?.invalidateRoute()
        return resolveFnMusicStreamCandidates(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = locator,
            httpClient = httpClient,
            addressSelector = addressSelector,
            excludedApiRoots = failedCandidates.mapTo(mutableSetOf()) { fnMusicApiRootOf(it.value) },
        )
    }

    override fun isAddressFallbackAllowed(failure: Throwable): Boolean = isRemoteSourceAddressFallbackAllowed(failure)

    override fun onSucceeded(candidate: RemotePlaybackUrlCandidate) {
        val kind = candidate.addressKindOrNull ?: return
        if (candidate.sourceId.isNotBlank()) addressSelector.markSuccess(candidate.sourceId, kind)
    }
}

// endregion

// region Source resolution

data class FnMusicResolvedSource(
    val sourceId: String,
    val connection: FnMusicConnection,
)

internal suspend fun resolveFnMusicSource(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    sourceId: String,
): FnMusicResolvedSource? {
    val source = database.importSourceDao().getById(sourceId)
        ?.takeIf { it.type == ImportSourceType.FN_MUSIC.name && it.enabled }
        ?: return null
    val password = source.credentialKey?.let { secureCredentialStore.get(it) }?.takeIf { it.isNotEmpty() } ?: return null
    val mode = fnMusicConnectionModeOf(source.rootReference)
    return FnMusicResolvedSource(
        sourceId = source.id,
        connection = FnMusicConnection(
            sourceId = source.id,
            mode = mode,
            lanBaseUrl = source.rootReference.takeIf { mode == FnMusicConnectionMode.ADDRESS && it.isNotBlank() },
            wanBaseUrl = source.wanRootReference?.takeIf { mode == FnMusicConnectionMode.ADDRESS && it.isNotBlank() },
            fnId = fnMusicIdOf(source.rootReference),
            username = source.username.orEmpty(),
            password = password,
            accessCode = secureCredentialStore.get(fnMusicAccessCodeCredentialKey(source.id))?.takeIf { it.isNotEmpty() },
            deviceId = resolveFnMusicDeviceId(secureCredentialStore),
        ),
    )
}

internal suspend fun FnMusicResolvedSource.client(
    httpClient: LyricsHttpClient,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    timeoutMillis: Long? = null,
): FnMusicClient = fnMusicClient(connection, httpClient, addressSelector, logger, timeoutMillis)

internal suspend fun fnMusicClient(
    connection: FnMusicConnection,
    httpClient: LyricsHttpClient,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    timeoutMillis: Long? = null,
): FnMusicClient = FnMusicClient(
    connection = connection,
    httpClient = httpClient,
    addressSelector = addressSelector,
    logger = logger,
    timeoutMillis = timeoutMillis,
    session = FnMusicSessions.sessionFor(connection),
)

/** Signed stream candidates for a `lynmusic-fnmusic://` locator, one per usable endpoint; null for other locators. */
suspend fun resolveFnMusicStreamCandidates(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    httpClient: LyricsHttpClient,
    addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
    excludedApiRoots: Set<String> = emptySet(),
): List<RemotePlaybackUrlCandidate>? {
    val (sourceId, guid) = parseFnMusicSongLocator(locator) ?: return null
    val source = resolveFnMusicSource(database, secureCredentialStore, sourceId) ?: return null
    return source.client(httpClient, addressSelector)
        .mediaRequests("/track/stream", listOf("guid" to guid), excludedApiRoots)
        .takeIf { it.isNotEmpty() }
}

/** Signed cover candidates for a `lynmusic-fnmusic-cover://` locator; null for other locators. */
suspend fun resolveFnMusicCoverCandidates(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    httpClient: LyricsHttpClient,
    addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
): List<RemotePlaybackUrlCandidate>? {
    val (sourceId, coverId) = parseFnMusicCoverLocator(locator) ?: return null
    val source = resolveFnMusicSource(database, secureCredentialStore, sourceId) ?: return null
    return source.client(httpClient, addressSelector)
        .mediaRequests("/static/cover", listOf("coverId" to coverId, "size" to FN_MUSIC_COVER_SIZE))
}

// endregion

// region Import

suspend fun testFnMusicConnection(
    draft: FnMusicSourceDraft,
    deviceId: String,
    httpClient: LyricsHttpClient,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    timeoutMillis: Long? = null,
) {
    val connection = prepareFnMusicDraft(draft).toFnMusicConnection(sourceId = "", deviceId = deviceId)
    fnMusicClient(connection, httpClient, addressSelector = null, logger = logger, timeoutMillis = timeoutMillis)
        .call("track/list", "/track/list", listOf("page" to "1", "size" to "1"))
}

suspend fun scanFnMusicLibrary(
    draft: FnMusicSourceDraft,
    sourceId: String,
    deviceId: String,
    httpClient: LyricsHttpClient,
    supportedImportExtensions: Set<String>,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    progressSink: ImportScanProgressSink = ImportScanProgressSink.NoOp,
    timeoutMillis: Long? = null,
): ImportScanReport {
    val connection = prepareFnMusicDraft(draft).toFnMusicConnection(sourceId = "", deviceId = deviceId)
    val client = fnMusicClient(connection, httpClient, addressSelector = null, logger = logger, timeoutMillis = timeoutMillis)
    val tracks = mutableListOf<ImportedTrackCandidate>()
    val failures = mutableListOf<ImportScanFailure>()
    var discovered = 0
    var cueSkipped = 0
    var totalTrackCount: Int? = null
    val metadataRequests = Semaphore(FN_MUSIC_METADATA_CONCURRENCY)
    client.forEachPage(
        operation = "track/list",
        path = "/track/list",
        query = listOf("sort" to "createdAt,asc"),
        // The library changed while listing: start over so no track is missed (and later removed locally).
        onRestart = {
            tracks.clear()
            failures.clear()
            discovered = 0
            cueSkipped = 0
        },
    ) { items, total ->
        totalTrackCount = total
        progressSink.onProgress(ImportScanProgress(sourceId, ImportScanPhase.Scanning, tracks.size, total))
        // Fetch missing metadata for the page in parallel (bounded), keeping the listing order.
        val enriched = coroutineScope {
            items.map { raw ->
                async {
                    // CUE tracks are skipped anyway, so never spend a metadata request on them.
                    if (raw.isFnMusicCue()) raw else metadataRequests.withPermit { client.withMetadata(raw) }
                }
            }.awaitAll()
        }
        enriched.forEach { item ->
            if (item.isFnMusicCue()) {
                cueSkipped += 1
                return@forEach
            }
            discovered += 1
            val candidate = item.toFnMusicTrackCandidate(sourceId)
                ?: throw UiTextException(uiText(Res.string.fn_music_response_invalid, "track/list"))
            when (classifyAudioExtensionForImport(item.fnMusicExtension(), supportedImportExtensions)) {
                NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> tracks += candidate
                NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED,
                NonNavidromeAudioScanResult.NOT_AUDIO,
                -> failures += unsupportedAudioImportFailure(candidate.relativePath)
            }
        }
        progressSink.onProgress(ImportScanProgress(sourceId, ImportScanPhase.Scanning, tracks.size, total))
    }
    logger.log(
        DiagnosticLogLevel.INFO,
        FN_MUSIC_LOG_TAG,
        "scan-complete source=$sourceId discovered=$discovered imported=${tracks.size} failures=${failures.size} cueSkipped=$cueSkipped",
    )
    val warnings = buildList {
        if (discovered == 0 && cueSkipped == 0) {
            add(ImportScanWarning("当前飞牛音乐账号下没有可同步的歌曲。", uiText(Res.string.source_account_no_syncable_tracks, FN_MUSIC_NAME)))
        }
        if (cueSkipped > 0) {
            add(ImportScanWarning("跳过 $cueSkipped 首 CUE 分轨歌曲。", uiText(Res.string.fn_music_cue_tracks_skipped, cueSkipped)))
        }
    }
    return ImportScanReport(
        tracks = tracks,
        warnings = warnings,
        discoveredAudioFileCount = discovered,
        failures = failures,
        totalTrackCount = totalTrackCount?.minus(cueSkipped),
    )
}

/**
 * Walks a `{list, total}` paged endpoint, checking that the total stays stable and guids never repeat. Offset paging
 * over a library that changes mid-walk shifts items between pages, so a page may skip or repeat some; a full scan
 * would then drop the skipped tracks locally. On such a change the whole listing is walked again from page 1 (after
 * [onRestart] lets the caller discard what it collected), up to [FN_MUSIC_LISTING_ATTEMPTS] times.
 */
private suspend fun FnMusicClient.forEachPage(
    operation: String,
    path: String,
    query: List<Pair<String, String>> = emptyList(),
    pageSize: Int = FN_MUSIC_PAGE_SIZE,
    allowDuplicates: Boolean = false,
    maxItems: Int = Int.MAX_VALUE,
    onRestart: suspend () -> Unit = {},
    onPage: suspend (items: List<JsonObject>, total: Int) -> Unit,
) {
    val size = pageSize.coerceIn(1, FN_MUSIC_MAX_PAGE_SIZE)
    repeat(FN_MUSIC_LISTING_ATTEMPTS) { attempt ->
        if (attempt > 0) onRestart()
        var page = 1
        var seen = 0
        var total: Int? = null
        val guids = mutableSetOf<String>()
        var changed = false
        while (seen < maxItems) {
            val data = call(operation, path, query + listOf("page" to page.toString(), "size" to size.toString())) as? JsonObject
                ?: throw UiTextException(uiText(Res.string.fn_music_response_invalid, operation))
            val list = data["list"] as? JsonArray ?: throw UiTextException(uiText(Res.string.fn_music_response_invalid, operation))
            val pageTotal = data.int("total")?.takeIf { it >= 0 } ?: throw UiTextException(uiText(Res.string.fn_music_response_invalid, operation))
            val items = list.mapNotNull { it as? JsonObject }
            val itemGuids = items.map { it.fnGuid() ?: throw UiTextException(uiText(Res.string.fn_music_response_invalid, operation)) }
            if (total != null && total != pageTotal || !allowDuplicates && itemGuids.any { it in guids }) {
                changed = true
                break
            }
            total = pageTotal
            if (!allowDuplicates) {
                // A guid repeated within one page is malformed data, not a moving library.
                if (itemGuids.toSet().size != itemGuids.size) throw UiTextException(uiText(Res.string.fn_music_response_invalid, operation))
                guids += itemGuids
            }
            val accepted = items.take(maxItems - seen)
            if (accepted.isNotEmpty()) onPage(accepted, pageTotal)
            seen += items.size
            if (items.isEmpty() || seen >= pageTotal) break
            page += 1
        }
        if (!changed) return
    }
    throw UiTextException(uiText(Res.string.fn_music_response_invalid, operation))
}

private suspend fun FnMusicClient.collect(
    operation: String,
    path: String,
    query: List<Pair<String, String>> = emptyList(),
    allowDuplicates: Boolean = false,
    maxItems: Int = Int.MAX_VALUE,
    pageSize: Int = FN_MUSIC_PAGE_SIZE,
): List<JsonObject> {
    val result = mutableListOf<JsonObject>()
    forEachPage(operation, path, query, pageSize, allowDuplicates, maxItems, onRestart = { result.clear() }) { items, _ ->
        result += items
    }
    return result
}

/** List items may omit the audio spec (and CUE details); `/track/metadata` fills them in. */
private suspend fun FnMusicClient.withMetadata(item: JsonObject): JsonObject {
    val audio = item["audioSpec"] as? JsonObject
    val missingFormat = audio?.string("format") == null && audio?.string("extension") == null
    if (!missingFormat && !item.isFnMusicCue()) return item
    val guid = item.fnGuid() ?: return item
    val metadata = call("track/metadata", "/track/metadata", listOf("guid" to guid)) as? JsonObject ?: return item
    val merged = when (val track = metadata["track"]) {
        is JsonObject -> JsonObject(item + track + listOfNotNull((metadata["audioSpec"] as? JsonObject)?.let { "audioSpec" to it }))
        else -> if (metadata.fnGuid() != null) metadata else item
    }
    if (merged.fnGuid() != guid) throw UiTextException(uiText(Res.string.fn_music_response_invalid, "track/metadata"))
    return merged
}

internal fun JsonObject.isFnMusicCue(): Boolean = bool("isCue") || string("cueSheet") != null

internal fun JsonObject.fnMusicExtension(): String? {
    val audio = this["audioSpec"] as? JsonObject
    return (audio?.string("extension") ?: audio?.string("format") ?: string("extension") ?: fnMusicPath()?.substringAfterLast('.', ""))
        ?.trim()?.trimStart('.')?.lowercase()?.takeIf { it.isNotBlank() }
}

private fun JsonObject.fnMusicPath(): String? = (this["audioSpec"] as? JsonObject)?.string("path") ?: string("path")

internal fun JsonObject.toFnMusicTrackCandidate(sourceId: String): ImportedTrackCandidate? {
    val guid = fnGuid() ?: return null
    val audio = this["audioSpec"] as? JsonObject
    val album = this["album"] as? JsonObject
    val artistName = (this["artists"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.fnName() }
        .joinToString(", ")
        .ifBlank { string("artistName") ?: string("albumArtist") }
    val title = string("title") ?: string("name") ?: string("filename") ?: guid
    val extension = fnMusicExtension()
    val coverId = fnCover() ?: album?.fnCover()
    val updatedAt = double("updatedAt")?.let { if (it > 100_000_000_000.0) it.toLong() else (it * 1000).toLong() }
    return ImportedTrackCandidate(
        title = title,
        artistName = artistName?.takeIf { it.isNotBlank() },
        albumTitle = album?.fnName()?.takeIf { it.isNotBlank() } ?: string("albumName"),
        durationMs = (audio?.double("duration") ?: double("duration") ?: 0.0).toLong().coerceAtLeast(0L),
        trackNumber = int("trackNo"),
        discNumber = int("discNo"),
        mediaLocator = buildFnMusicSongLocator(sourceId, guid),
        relativePath = fnMusicRelativePath(artistName, album?.fnName(), title, extension),
        artworkLocator = coverId?.let { buildFnMusicCoverLocator(sourceId, it) },
        sizeBytes = audio?.double("size")?.toLong() ?: 0L,
        modifiedAt = updatedAt ?: 0L,
        bitDepth = audio?.int("bitDepth"),
        samplingRate = audio?.double("sampleRate")?.toInt(),
        bitRate = audio?.int("bitrate"),
        channelCount = audio?.int("channels"),
    )
}

private fun fnMusicRelativePath(artist: String?, album: String?, title: String, extension: String?): String {
    fun segment(value: String?, fallback: String) = value.orEmpty().trim().replace('/', '／').replace('\\', '／').ifBlank { fallback }
    val fileName = segment(title, "未知曲目") + (extension?.let { ".$it" } ?: "")
    return listOf(segment(artist, "未知艺人"), segment(album, "未知专辑"), fileName).joinToString("/")
}

// endregion

// region Library features

internal suspend fun requestFnMusicLyricsDocument(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    guid: String,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): LyricsDocument? {
    val data = source.client(httpClient, addressSelector, logger).call("lyric/list", "/lyric/list", listOf("trackGUID" to guid)) as? JsonObject
        ?: return null
    val items = (data["list"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val preferred = data.string("preferred")
    val selected = items.firstOrNull { preferred != null && it.fnGuid() == preferred } ?: items.firstOrNull() ?: return null
    val text = (selected.string("content") ?: selected.string("text"))?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return parseCachedLyrics(FN_MUSIC_LYRICS_SOURCE_ID, text)
}

internal suspend fun fetchFnMusicFavorites(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): List<FnMusicFavoritePayload> {
    return source.client(httpClient, addressSelector, logger)
        .collect("favorite-track/list", "/favorite-track/list")
        .mapNotNull { item -> item.fnGuid()?.let(::FnMusicFavoritePayload) }
}

internal suspend fun setFnMusicFavorite(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    guid: String,
    favorite: Boolean,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    val path = if (favorite) "/favorite-track/create" else "/favorite-track/delete"
    source.client(httpClient, addressSelector, logger)
        .call(path.removePrefix("/"), path, body = JsonObject(mapOf("trackGUID" to JsonPrimitive(guid))))
}

internal suspend fun fetchFnMusicPlaylists(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): List<FnMusicPlaylistPayload> {
    return source.client(httpClient, addressSelector, logger)
        .collect("playlist/list", "/playlist/list")
        .mapNotNull { item ->
            val guid = item.fnGuid() ?: return@mapNotNull null
            FnMusicPlaylistPayload(guid = guid, name = item.fnName().trim().ifBlank { "未命名歌单" }, trackCount = item.int("trackCount"))
        }
}

/** Track guids of a playlist in order; a playlist may contain the same track more than once. */
internal suspend fun fetchFnMusicPlaylistTrackGuids(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    playlistGuid: String,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): List<String> {
    return source.client(httpClient, addressSelector, logger)
        .collect("track/playlist-detail/list", "/track/playlist-detail/list", listOf("playlistGUID" to playlistGuid), allowDuplicates = true)
        .mapNotNull { it.fnGuid() }
}

internal suspend fun createFnMusicPlaylist(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    name: String,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): FnMusicPlaylistPayload {
    val data = source.client(httpClient, addressSelector, logger)
        .call("playlist/create", "/playlist/create", body = JsonObject(mapOf("name" to JsonPrimitive(name)))) as? JsonObject
    val guid = data?.fnGuid() ?: throw UiTextException(uiText(Res.string.server_response_field_missing, FN_MUSIC_NAME, "guid"))
    return FnMusicPlaylistPayload(guid = guid, name = data.fnName().ifBlank { name }, trackCount = 0)
}

internal suspend fun renameFnMusicPlaylist(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    playlistGuid: String,
    name: String,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    source.client(httpClient, addressSelector, logger).call(
        "playlist/edit",
        "/playlist/edit",
        body = sortedJsonObject("guid" to JsonPrimitive(playlistGuid), "name" to JsonPrimitive(name)),
    )
}

internal suspend fun deleteFnMusicPlaylist(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    playlistGuid: String,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    source.client(httpClient, addressSelector, logger)
        .call("playlist/delete", "/playlist/delete", body = JsonObject(mapOf("guid" to JsonPrimitive(playlistGuid))))
}

internal suspend fun addFnMusicPlaylistTracks(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    playlistGuid: String,
    trackGuids: List<String>,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    if (trackGuids.isEmpty()) return
    source.client(httpClient, addressSelector, logger).call(
        "playlist/add-track",
        "/playlist/add-track",
        body = playlistTracksBody(playlistGuid, trackGuids),
    )
}

internal suspend fun removeFnMusicPlaylistTracks(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    playlistGuid: String,
    trackGuids: List<String>,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    if (trackGuids.isEmpty()) return
    source.client(httpClient, addressSelector, logger).call(
        "playlist/remove-track",
        "/playlist/remove-track",
        body = playlistTracksBody(playlistGuid, trackGuids),
    )
}

private fun playlistTracksBody(playlistGuid: String, trackGuids: List<String>): JsonObject = sortedJsonObject(
    "guid" to JsonPrimitive(playlistGuid),
    "trackGUIDs" to JsonArray(trackGuids.map(::JsonPrimitive)),
)

internal suspend fun fetchFnMusicRecentTracks(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    limit: Int,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): List<FnMusicRecentTrackPayload> {
    val max = limit.coerceIn(1, FN_MUSIC_MAX_PAGE_SIZE)
    return source.client(httpClient, addressSelector, logger)
        .collect("play-history/list", "/play-history/list", allowDuplicates = true, maxItems = max, pageSize = max)
        .mapNotNull { item ->
            val guid = item.fnGuid() ?: return@mapNotNull null
            // Only explicit play times count; a track's own timestamps would fake recent plays.
            val playedAt = (item.double("playedAt") ?: item.double("playTime") ?: item.double("lastPlayedAt") ?: item.double("lastPlayTime"))
                ?.let { if (it > 100_000_000_000.0) it.toLong() else (it * 1000).toLong() }
            FnMusicRecentTrackPayload(guid = guid, albumGuid = (item["album"] as? JsonObject)?.fnGuid(), playedAt = playedAt)
        }
        .distinctBy { it.guid }
}

internal suspend fun reportFnMusicPlay(
    httpClient: LyricsHttpClient,
    source: FnMusicResolvedSource,
    guid: String,
    playedAtMillis: Long,
    addressSelector: RemoteSourceAddressSelector?,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    val event = sortedJsonObject(
        "eventType" to JsonPrimitive("track_play"),
        "occurredAt" to JsonPrimitive(playedAtMillis),
        "payload" to JsonObject(mapOf("trackGUID" to JsonPrimitive(guid))),
    )
    source.client(httpClient, addressSelector, logger)
        .call("event/report", "/event/report", body = JsonObject(mapOf("events" to JsonArray(listOf(event)))))
}

// endregion

// region JSON helpers

/** Request bodies are signed byte-for-byte, so keys go out in a stable (sorted) order like the official apps. */
private fun sortedJsonObject(vararg entries: Pair<String, JsonElement>): JsonObject =
    JsonObject(linkedMapOf(*entries.sortedBy { it.first }.toTypedArray()))

private fun JsonObject.fnGuid(): String? = string("guid") ?: string("id")

private fun JsonObject.fnName(): String = string("name") ?: string("title") ?: ""

private fun JsonObject.fnCover(): String? = string("coverId") ?: string("coverID")

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotEmpty() }

private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

private fun JsonObject.int(key: String): Int? = double(key)?.takeIf { it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()

private fun JsonObject.bool(key: String): Boolean {
    val value = this[key] as? JsonPrimitive ?: return false
    return value.booleanOrNull ?: (value.contentOrNull?.toDoubleOrNull() == 1.0)
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

// endregion
