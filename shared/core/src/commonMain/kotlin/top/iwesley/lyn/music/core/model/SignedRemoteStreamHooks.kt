package top.iwesley.lyn.music.core.model

/** Signs requests whose headers depend on the exact URL (e.g. FN Music's timestamped `authx`). */
interface RemoteRequestSigner {
    /** [headers] signed for [url]; called before every request, including each redirect hop. */
    fun sign(url: String, headers: Map<String, String>): Map<String, String>

    /** Headers that may follow a redirect from [fromUrl] to [toUrl], or null to refuse the redirect. */
    fun redirectHeaders(fromUrl: String, toUrl: String, headers: Map<String, String>): Map<String, String>?
}

/** Source-specific behaviour of a [SignedRemoteStream] (signing, credential refresh, re-resolving addresses). */
interface SignedRemoteStreamHooks : RemoteRequestSigner {

    /** True when the server refused [candidate]'s credentials ([body] is its JSON answer, if any), so [reauthorize] may recover. */
    fun isAuthFailure(candidate: RemotePlaybackUrlCandidate, statusCode: Int, body: String?): Boolean

    /** [candidate] with fresh credentials, or null when it cannot be recovered. */
    suspend fun reauthorize(candidate: RemotePlaybackUrlCandidate): RemotePlaybackUrlCandidate?

    /**
     * A fresh candidate list (e.g. a new FN Connect route), or null when there is nothing new to try.
     * [failedCandidates] just failed and should be avoided; empty when re-resolving after a network change.
     */
    suspend fun reresolve(failedCandidates: List<RemotePlaybackUrlCandidate>): List<RemotePlaybackUrlCandidate>?

    /** Whether another address may answer where [failure] happened (transport errors, 5xx). */
    fun isAddressFallbackAllowed(failure: Throwable): Boolean

    fun onSucceeded(candidate: RemotePlaybackUrlCandidate) = Unit
}
