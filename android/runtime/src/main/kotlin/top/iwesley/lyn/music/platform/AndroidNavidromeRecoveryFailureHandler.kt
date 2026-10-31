package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.domain.isRemoteSourceAddressFallbackAllowed

internal data class NavidromeRecoveryFailure(
    val error: Throwable,
    val positionMs: Long,
    val result: NavidromeReadRecoveryResult,
    val mediaId: String,
    val recoveryVersion: Long,
)

/** Shared by immediate source errors and failures discovered after capability/response callbacks. */
internal class NavidromeRecoveryFailureHandler(
    private val isCurrent: (NavidromeRecoveryFailure) -> Boolean,
    private val tryNextAddress: (Throwable) -> Boolean,
    private val publishFailure: (NavidromeRecoveryFailure) -> Unit,
) {
    private var reportedToken: Pair<String, Long>? = null

    fun handle(failure: NavidromeRecoveryFailure) {
        val token = failure.mediaId to failure.recoveryVersion
        if (!isCurrent(failure) || failure.result == NavidromeReadRecoveryResult.Recovering || reportedToken == token) return
        if (failure.result != NavidromeReadRecoveryResult.InvalidTarget &&
            isRemoteSourceAddressFallbackAllowed(failure.error) && tryNextAddress(failure.error)) return
        // A fallback callback may have replaced or canceled this recovery even if it returned false.
        if (!isCurrent(failure)) return
        reportedToken = token
        publishFailure(failure)
    }
}
