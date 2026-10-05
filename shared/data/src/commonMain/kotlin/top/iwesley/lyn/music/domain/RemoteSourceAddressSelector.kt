package top.iwesley.lyn.music.domain

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.requireUi
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiTextFailure
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.UiTextArgumentException
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.diagnosticMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NetworkConnectionState
import top.iwesley.lyn.music.core.model.NetworkConnectionType
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.RemoteSourceConnectTimeout
import top.iwesley.lyn.music.core.model.WifiNetworkConnectionTypeProvider

enum class RemoteSourceAddressKind {
    LAN,
    WAN,
}

/** The candidate's address kind, or null when it was built without one. */
val RemotePlaybackUrlCandidate.addressKindOrNull: RemoteSourceAddressKind?
    get() = RemoteSourceAddressKind.entries.firstOrNull { it.name == addressKind }

data class RemoteSourceBaseUrl(
    val kind: RemoteSourceAddressKind,
    val value: String,
)

data class RemoteSourceResolvedUrl(
    val sourceId: String,
    val kind: RemoteSourceAddressKind,
    val value: String,
)

class RemoteSourceAddressSelector(
    private val networkConnectionTypeProvider: NetworkConnectionTypeProvider = WifiNetworkConnectionTypeProvider,
    private val ttlMillis: Long = DEFAULT_REMOTE_SOURCE_ADDRESS_CACHE_TTL_MILLIS,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val successfulAddressCache = MutableStateFlow<Map<String, SuccessfulAddress>>(emptyMap())

    fun orderedBaseUrls(
        sourceId: String,
        sourceType: ImportSourceType,
        lanBaseUrl: String?,
        wanBaseUrl: String?,
        normalizeBaseUrl: (String) -> String,
    ): List<RemoteSourceBaseUrl> {
        val addresses = normalizeAddresses(
            sourceType = sourceType,
            lanBaseUrl = lanBaseUrl,
            wanBaseUrl = wanBaseUrl,
            normalizeBaseUrl = normalizeBaseUrl,
        )
        if (addresses.size <= 1) return addresses
        val networkState = networkConnectionTypeProvider.networkConnectionState.value
        val now = nowMillis()
        successfulAddressCache.value[sourceId]
            ?.takeIf { it.networkVersion == networkState.version && now - it.recordedAtMillis <= ttlMillis }
            ?.let { cached ->
                addresses.firstOrNull { it.kind == cached.kind }?.let { preferred ->
                    return listOf(preferred) + addresses.filterNot { it.kind == preferred.kind }
                }
            }
        return addresses.orderedForNetwork(networkState.type)
    }

    suspend fun <T> withAddressFallback(
        sourceId: String,
        sourceType: ImportSourceType,
        lanBaseUrl: String?,
        wanBaseUrl: String?,
        normalizeBaseUrl: (String) -> String,
        block: suspend (RemoteSourceBaseUrl) -> T,
    ): T {
        val candidates = orderedBaseUrls(
            sourceId = sourceId,
            sourceType = sourceType,
            lanBaseUrl = lanBaseUrl,
            wanBaseUrl = wanBaseUrl,
            normalizeBaseUrl = normalizeBaseUrl,
        )
        var lastFailure: Throwable? = null
        candidates.forEachIndexed { index, candidate ->
            try {
                val result = withRemoteSourceFallbackConnectTimeout(
                    enabled = index < candidates.lastIndex && shouldCapRemoteSourceConnect(candidate.kind),
                ) {
                    block(candidate)
                }
                markSuccess(sourceId, candidate.kind)
                return result
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                lastFailure = throwable
                val hasFallback = index < candidates.lastIndex
                if (!hasFallback || !isRemoteSourceAddressFallbackAllowed(throwable)) {
                    throw throwable
                }
            }
        }
        throw lastFailure ?: IllegalStateException("${sourceType.displayName()} 来源缺少可用服务器地址。")
    }

    fun markSuccess(sourceId: String, kind: RemoteSourceAddressKind) {
        val networkState = networkConnectionTypeProvider.networkConnectionState.value
        val now = nowMillis()
        successfulAddressCache.update { cache ->
            val existing = cache[sourceId]
            // Keep the original timestamp while the same address keeps succeeding on the same network,
            // so a fallback address expires after the TTL and the preferred address gets probed again.
            val recordedAtMillis = existing
                ?.takeIf {
                    it.kind == kind &&
                        it.networkVersion == networkState.version &&
                        now - it.recordedAtMillis <= ttlMillis
                }
                ?.recordedAtMillis
                ?: now
            val successfulAddress = SuccessfulAddress(
                kind = kind,
                networkVersion = networkState.version,
                recordedAtMillis = recordedAtMillis,
            )
            cache + (sourceId to successfulAddress)
        }
    }

    /** Changes whenever the network changes; callers cache resolved endpoints against it. */
    fun networkVersion(): Long = networkConnectionTypeProvider.networkConnectionState.value.version

    fun invalidate(sourceId: String) {
        successfulAddressCache.update { cache -> cache - sourceId }
    }

    fun clear() {
        successfulAddressCache.update { emptyMap() }
    }
}

/**
 * Decides whether a playing remote stream should move to another address after a network change.
 * Only a LAN stream that has landed on a connected mobile network moves (to WAN); Wi-Fi changes and
 * disconnected gaps keep the current address and leave recovery to the player's own retry/fallback.
 */
fun remoteCandidateIndexForNetworkChange(
    candidateKinds: List<RemoteSourceAddressKind?>,
    currentIndex: Int,
    networkState: NetworkConnectionState,
    isPlaybackActive: Boolean,
): Int? {
    // An ended or idle player has nothing to move; reloading it would replay or re-complete the track.
    if (!isPlaybackActive) return null
    if (!networkState.isConnected || networkState.type != NetworkConnectionType.MOBILE) return null
    if (candidateKinds.getOrNull(currentIndex) != RemoteSourceAddressKind.LAN) return null
    return candidateKinds.indexOf(RemoteSourceAddressKind.WAN).takeIf { it >= 0 }
}

/** What a playing signed stream should do after a network change; see [remoteStreamNetworkAction]. */
sealed interface RemoteStreamNetworkAction {
    data object None : RemoteStreamNetworkAction
    data class Switch(val index: Int) : RemoteStreamNetworkAction
    data object Reresolve : RemoteStreamNetworkAction
}

/**
 * Like [remoteCandidateIndexForNetworkChange], but a LAN stream without a WAN candidate (an FN Connect route resolved
 * at home) re-resolves its addresses instead of staying on the unreachable LAN address.
 */
fun remoteStreamNetworkAction(
    candidateKinds: List<RemoteSourceAddressKind?>,
    currentIndex: Int,
    networkState: NetworkConnectionState,
    isPlaybackActive: Boolean,
): RemoteStreamNetworkAction {
    remoteCandidateIndexForNetworkChange(candidateKinds, currentIndex, networkState, isPlaybackActive)
        ?.let { return RemoteStreamNetworkAction.Switch(it) }
    if (!isPlaybackActive || !networkState.isConnected || networkState.type != NetworkConnectionType.MOBILE) {
        return RemoteStreamNetworkAction.None
    }
    return if (candidateKinds.getOrNull(currentIndex) == RemoteSourceAddressKind.LAN) {
        RemoteStreamNetworkAction.Reresolve
    } else {
        RemoteStreamNetworkAction.None
    }
}

/**
 * Tests every configured address independently (unlike [RemoteSourceAddressSelector.withAddressFallback]),
 * so a mistyped LAN or WAN address is reported even when the other one works.
 */
suspend fun testEachRemoteSourceAddress(
    sourceType: ImportSourceType,
    lanBaseUrl: String?,
    wanBaseUrl: String?,
    normalizeBaseUrl: (String) -> String,
    block: suspend (RemoteSourceBaseUrl) -> Unit,
) {
    val addresses = normalizeAddresses(sourceType, lanBaseUrl, wanBaseUrl, normalizeBaseUrl)
    if (addresses.size == 1) {
        block(addresses.single())
        return
    }
    val results = coroutineScope {
        addresses.map { address ->
            async {
                address to try {
                    // The LAN address is usually the unreachable one away from home; don't wait the full timeout.
                    withRemoteSourceFallbackConnectTimeout(enabled = shouldCapRemoteSourceConnect(address.kind)) {
                        block(address)
                    }
                    null
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    throwable
                }
            }
        }.awaitAll()
    }
    val failures = results.mapNotNull { (_, failure) -> failure }
    if (failures.isEmpty()) return
    // Input validation fails identically for every address; report it once. HTTP failures also use the
    // argument exception type but are per-address answers, so they stay in the per-address report.
    failures.firstOrNull { it is UiTextArgumentException && it !is RemoteSourceHttpFailure }?.let { throw it }
    val anySucceeded = failures.size < results.size
    val lines = results.map { (address, failure) ->
        when {
            failure == null && address.kind == RemoteSourceAddressKind.LAN ->
                uiText(Res.string.source_address_test_lan_succeeded)
            failure == null -> uiText(Res.string.source_address_test_wan_succeeded)
            // An unreachable LAN address is expected away from home when WAN works.
            address.kind == RemoteSourceAddressKind.LAN && anySucceeded ->
                uiText(Res.string.source_address_test_lan_unreachable_hint, failure.uiErrorDetail())
            address.kind == RemoteSourceAddressKind.LAN ->
                uiText(Res.string.source_address_test_lan_failed, failure.uiErrorDetail())
            else -> uiText(Res.string.source_address_test_wan_failed, failure.uiErrorDetail())
        }
    }
    throw RemoteSourceAddressTestException(
        text = UiText.Joined(lines, separator = "\n"),
        anySucceeded = anySucceeded,
        cause = failures.first(),
    )
}

/** Per-address connection test result; [anySucceeded] means the source is usable through another address. */
class RemoteSourceAddressTestException(
    text: UiText,
    val anySucceeded: Boolean,
    cause: Throwable? = null,
) : UiTextException(text, cause)

fun normalizeRemoteSourceBaseUrls(
    sourceType: ImportSourceType,
    lanBaseUrl: String?,
    wanBaseUrl: String?,
    normalizeBaseUrl: (String) -> String,
): Pair<String, String?> {
    val addresses = normalizeAddresses(sourceType, lanBaseUrl, wanBaseUrl, normalizeBaseUrl)
    val lan = addresses.firstOrNull { it.kind == RemoteSourceAddressKind.LAN }?.value.orEmpty()
    val wan = addresses.firstOrNull { it.kind == RemoteSourceAddressKind.WAN }?.value
    return lan to wan
}

fun isRemoteSourceAddressFallbackAllowed(throwable: Throwable): Boolean {
    val failures = throwable.failureChain()
    val chain = failures.messageChain()
    val statusCodes = failures.mapNotNull { (it as? RemoteSourceHttpFailure)?.httpStatusCode } +
        HTTP_STATUS_REGEX.findAll(chain).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }.toList()
    if (statusCodes.any { it == 401 || it == 403 }) return false
    if (statusCodes.isNotEmpty()) return statusCodes.any { it == 408 || it in 500..599 }
    if (failures.any { it is UiTextArgumentException }) return false
    val lowered = chain.lowercase()
    if (
        lowered.contains("地址无效") ||
        lowered.contains("不能包含 query") ||
        lowered.contains("只支持 http") ||
        lowered.contains("缺少主机名") ||
        lowered.contains("内嵌用户名") ||
        lowered.contains("缺少有效凭据") ||
        lowered.contains("缺少有效密码") ||
        lowered.contains("token 为空") ||
        lowered.contains("用户 id 为空")
    ) {
        return false
    }
    if (failures.any { it is RemoteSourceRequestException }) return true
    return lowered.contains("请求失败") ||
        lowered.contains("timeout") ||
        lowered.contains("timed out") ||
        lowered.contains("unknownhost") ||
        lowered.contains("unknown host") ||
        lowered.contains("nsurlerrordomain code=-1001") ||
        lowered.contains("nsurlerrordomain code=-1003") ||
        lowered.contains("nsurlerrordomain code=-1004") ||
        lowered.contains("nsurlerrordomain code=-1005") ||
        lowered.contains("nsurlerrordomain code=-1009") ||
        lowered.contains("nsurlerrordomain code=-1200") ||
        lowered.contains("nsurlerrordomain code=-1202") ||
        lowered.contains("resolve") ||
        lowered.contains("connect") ||
        lowered.contains("connection") ||
        lowered.contains("network") ||
        lowered.contains("tls") ||
        lowered.contains("ssl")
}

/**
 * True when [throwable] proves the request never reached the server (refused connection, unknown host, connect
 * timeout), so even a write can safely be sent to another address. Read timeouts and resets are not: the server may
 * already have applied it. Classified by exception type names, never by message text.
 */
fun isRequestNotSentFailure(throwable: Throwable): Boolean {
    return throwable.failureChain().any { failure ->
        val name = failure::class.simpleName.orEmpty()
        REQUEST_NOT_SENT_EXCEPTION_NAMES.any { name.contains(it) }
    }
}

private val REQUEST_NOT_SENT_EXCEPTION_NAMES = listOf(
    "ConnectException",
    "ConnectTimeout",
    "UnknownHost",
    "NoRouteToHost",
    "PortUnreachable",
    "UnresolvedAddress",
)

suspend fun <T> readRemotePlaybackUrlCandidateWithFallback(
    candidates: List<RemotePlaybackUrlCandidate>,
    isRemoteUrl: (String) -> Boolean = { value ->
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
    },
    read: suspend (RemotePlaybackUrlCandidate) -> T,
    isValidPayload: (T) -> Boolean = { true },
): Pair<RemotePlaybackUrlCandidate, T>? {
    val remoteCandidates = candidates.filter { candidate -> isRemoteUrl(candidate.value) }
    if (remoteCandidates.isEmpty()) return null
    var lastFailure: Throwable? = null
    remoteCandidates.forEachIndexed { index, candidate ->
        try {
            val payload = withRemoteSourceFallbackConnectTimeout(
                enabled = index < remoteCandidates.lastIndex &&
                    shouldCapRemoteSourceConnect(candidate.addressKindOrNull),
            ) {
                read(candidate)
            }
            return if (isValidPayload(payload)) candidate to payload else null
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            lastFailure = throwable
            val hasFallback = index < remoteCandidates.lastIndex
            if (!hasFallback || !isRemoteSourceAddressFallbackAllowed(throwable)) {
                throw throwable
            }
        }
    }
    throw lastFailure ?: IllegalStateException("远程来源缺少可用地址。")
}

/**
 * Only a LAN address is likely to be unreachable (away from home); WAN and unknown addresses keep the
 * normal timeout.
 */
private fun shouldCapRemoteSourceConnect(kind: RemoteSourceAddressKind?): Boolean = kind == RemoteSourceAddressKind.LAN


/**
 * Caps connecting to a LAN address that may be unreachable. WAN addresses keep the normal timeout so a slow
 * mobile handshake is not cut short and pushed onto an unreachable LAN address.
 */
private suspend fun <T> withRemoteSourceFallbackConnectTimeout(
    enabled: Boolean,
    block: suspend () -> T,
): T {
    return if (enabled) {
        withContext(RemoteSourceConnectTimeout(REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS)) { block() }
    } else {
        block()
    }
}

private fun normalizeAddresses(
    sourceType: ImportSourceType,
    lanBaseUrl: String?,
    wanBaseUrl: String?,
    normalizeBaseUrl: (String) -> String,
): List<RemoteSourceBaseUrl> {
    val lan = lanBaseUrl.orEmpty().trim()
        .takeIf { it.isNotBlank() }
        ?.let(normalizeBaseUrl)
    val wan = wanBaseUrl.orEmpty().trim()
        .takeIf { it.isNotBlank() }
        ?.let(normalizeBaseUrl)
    requireUi(lan != null || wan != null) { uiText(Res.string.source_server_address_required, sourceType.displayNameArgument()) }
    return buildList {
        lan?.let { add(RemoteSourceBaseUrl(RemoteSourceAddressKind.LAN, it)) }
        wan?.let { add(RemoteSourceBaseUrl(RemoteSourceAddressKind.WAN, it)) }
    }
}

private fun List<RemoteSourceBaseUrl>.orderedForNetwork(networkType: NetworkConnectionType): List<RemoteSourceBaseUrl> {
    val preferredKind = when (networkType) {
        NetworkConnectionType.WIFI -> RemoteSourceAddressKind.LAN
        NetworkConnectionType.MOBILE -> RemoteSourceAddressKind.WAN
    }
    val preferred = firstOrNull { it.kind == preferredKind }
    return if (preferred == null) this else listOf(preferred) + filterNot { it.kind == preferredKind }
}

private fun Throwable.failureChain(): List<Throwable> {
    val failures = mutableListOf<Throwable>()
    var current: Throwable? = this
    while (current != null && failures.none { it === current }) {
        failures += current
        current = current.cause
    }
    return failures
}

private fun List<Throwable>.messageChain(): String {
    return map { throwable ->
        val name = throwable::class.simpleName ?: throwable::class.qualifiedName ?: "Throwable"
        // Resource keys and their parameters are diagnostics, never classification input.
        // Explicit platform diagnostics and third-party exceptions retain legacy handling.
        val message = throwable.message?.takeIf {
            it.isNotBlank() &&
                (throwable !is UiTextFailure || throwable.text is UiText.Raw || it != throwable.text.diagnosticMessage())
        }
        if (message == null) name else "$name: $message"
    }
        .distinct()
        .joinToString(" -> ")
}

/** The source name as a UI text argument: a localized resource where one exists, the brand name otherwise. */
private fun ImportSourceType.displayNameArgument(): Any = when (this) {
    ImportSourceType.FN_MUSIC -> FN_MUSIC_NAME
    else -> displayName()
}

private fun ImportSourceType.displayName(): String {
    return when (this) {
        ImportSourceType.NAVIDROME -> "Navidrome"
        ImportSourceType.SUBSONIC -> "Subsonic"
        ImportSourceType.EMBY -> "Emby"
        ImportSourceType.FN_MUSIC -> "飞牛音乐"
        else -> "远程"
    }
}

private data class SuccessfulAddress(
    val kind: RemoteSourceAddressKind,
    val networkVersion: Long,
    val recordedAtMillis: Long,
)

private val HTTP_STATUS_REGEX =
    Regex("\\b(?:HTTP\\s+|status\\s*[=:]?\\s*|response\\s*code\\s*[=:]?\\s*|code\\s*[=:]?\\s*)(\\d{3})\\b", RegexOption.IGNORE_CASE)
private const val DEFAULT_REMOTE_SOURCE_ADDRESS_CACHE_TTL_MILLIS = 10 * 60 * 1_000L
