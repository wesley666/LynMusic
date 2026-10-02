package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.diagnosticMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.iwesley.lyn.music.core.model.AppleMediaLocatorResolver
import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.NetworkConnectionState
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedNavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.WifiNetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.parseEmbySongLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.resolveNavidromeAudioQualityForCurrentNetwork
import top.iwesley.lyn.music.domain.addressKindOrNull
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.isRemoteSourceAddressFallbackAllowed
import top.iwesley.lyn.music.domain.remoteCandidateIndexForNetworkChange

private data class AppleRemotePlaybackFallback(
    val candidates: List<RemotePlaybackUrlCandidate>,
    val selectedIndex: Int,
    val playWhenReady: Boolean,
) {
    fun currentCandidate(): RemotePlaybackUrlCandidate? = candidates.getOrNull(selectedIndex)
}

internal data class AppleLocalMediaAccess(
    val locator: AppleResolvedMediaLocator,
    val release: () -> Unit,
)

internal fun interface AppleLocalMediaAccessResolver {
    suspend fun resolve(locator: String): AppleLocalMediaAccess?

    companion object {
        val None: AppleLocalMediaAccessResolver = AppleLocalMediaAccessResolver { null }
    }
}

internal class ApplePlaybackGateway(
    private val platformLabel: String,
    private val navidromeAudioQualityPreferencesStore: NavidromeAudioQualityPreferencesStore =
        UnsupportedNavidromeAudioQualityPreferencesStore,
    private val networkConnectionTypeProvider: NetworkConnectionTypeProvider = WifiNetworkConnectionTypeProvider,
    private val addressSelector: RemoteSourceAddressSelector? = null,
    private val localMediaAccessResolver: AppleLocalMediaAccessResolver = AppleLocalMediaAccessResolver.None,
    private val player: ApplePlaybackEngine = AppleNativePlayer(platformLabel),
) : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState(volume = 1f))
    private var currentRemotePlaybackFallback: AppleRemotePlaybackFallback? = null
    private var currentLocalMediaAccess: AppleLocalMediaAccess? = null

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    /**
     * Gateway state and every AVPlayer call are confined to the main thread, where the player callbacks and
     * network updates already arrive. The playback repository calls in from other threads, so its entry points
     * hop here; the main queue then serializes everything without locks. The hops use `Main.immediate` because
     * the repository also calls in via `runBlocking` on the main thread (startup volume/queue restore, dispose),
     * where a plain `Main` dispatch could never run and would deadlock.
     */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        AppleAudioSessionCoordinator.configureForPlayback()
        mainScope.launch {
            networkConnectionTypeProvider.networkConnectionState
                .distinctUntilChangedBy { it.version }
                .drop(1)
                .collect { state -> switchRemoteCandidateForNetwork(state) }
        }
        player.onProgress = { publishState() }
        player.onCompleted = {
            // A finished track must not be reloaded by a later network change or error retry.
            currentRemotePlaybackFallback = null
            releaseCurrentLocalMediaAccess()
            mutableState.update {
                it.copy(
                    isPlaying = false,
                    positionMs = 0L,
                    canSeek = false,
                    completionCount = it.completionCount + 1,
                    errorMessage = null,
                    errorText = null,
                )
            }
        }
        player.onFailed = onFailed@ { errorMessage ->
            if (tryApplyRemoteAddressFallback(errorMessage)) {
                return@onFailed
            }
            releaseCurrentLocalMediaAccess()
            val text = applePlaybackFailureText(platformLabel, errorMessage)
            publishState(
                errorOverride = errorMessage ?: (text).diagnosticMessage(),
                errorTextOverride = text,
            )
        }
    }

    override suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        if (!loadToken.isCurrent()) {
            return
        }
        // Re-check on the main thread: a newer load may have committed while this one waited for it, and
        // resetting then would stop the newer track.
        withContext(Dispatchers.Main.immediate) {
            if (loadToken.isCurrent()) stopAndResetForTrackSwitch()
        }
        if (!loadToken.isCurrent()) {
            return
        }
        val subsonicCompatible = parseSubsonicCompatibleSongLocator(track.mediaLocator)
        val embySong = parseEmbySongLocator(track.mediaLocator)
        val navidromeAudioQuality = subsonicCompatible?.let {
            resolveNavidromeAudioQualityForCurrentNetwork(
                preferencesStore = navidromeAudioQualityPreferencesStore,
                networkConnectionTypeProvider = networkConnectionTypeProvider,
            )
        }
        val remotePlaybackCandidates = when {
            subsonicCompatible != null -> NavidromeLocatorRuntime.resolveStreamUrlCandidates(
                locator = track.mediaLocator,
                audioQuality = requireNotNull(navidromeAudioQuality),
            )

            embySong != null -> NavidromeLocatorRuntime.resolveStreamUrlCandidates(track.mediaLocator)
            else -> null
        }?.takeIf { it.isNotEmpty() }
        val effectiveLocator = when {
            remotePlaybackCandidates != null -> remotePlaybackCandidates.first().value
            subsonicCompatible != null -> NavidromeLocatorRuntime.resolveStreamUrl(
                locator = track.mediaLocator,
                audioQuality = requireNotNull(navidromeAudioQuality),
            ) ?: track.mediaLocator

            embySong != null -> NavidromeLocatorRuntime.resolveStreamUrl(track.mediaLocator) ?: track.mediaLocator
            else -> track.mediaLocator
        }
        if (!loadToken.isCurrent()) {
            return
        }
        val localAccess = runCatching { localMediaAccessResolver.resolve(effectiveLocator) }
            .getOrElse { throwable ->
                publishFailure(appleLocalAccessFailureText(platformLabel, throwable), throwable.message)
                return
            }
        if (!loadToken.isCurrent()) {
            localAccess?.let { access -> runCatching(access.release) }
            return
        }
        when (val resolved = localAccess?.locator ?: AppleMediaLocatorResolver.resolve(effectiveLocator)) {
            is AppleResolvedMediaLocator.Unsupported -> {
                if (!loadToken.isCurrent()) {
                    return
                }
                publishFailure(resolved.messageText, resolved.message)
            }

            else -> {
                // Until commitLoad takes ownership, the local file access is ours to release, including when
                // the load is cancelled while waiting for the main thread.
                var localAccessHandedOver = false
                try {
                    withContext(Dispatchers.Main.immediate) {
                        if (loadToken.isCurrent()) {
                            localAccessHandedOver = true
                            commitLoad(
                                resolved = resolved,
                                remotePlaybackCandidates = remotePlaybackCandidates,
                                localAccess = localAccess,
                                playWhenReady = playWhenReady,
                                startPositionMs = startPositionMs,
                                navidromeAudioQuality = navidromeAudioQuality,
                            )
                        }
                    }
                } finally {
                    if (!localAccessHandedOver) {
                        localAccess?.let { access -> runCatching(access.release) }
                    }
                }
            }
        }
    }

    private fun commitLoad(
        resolved: AppleResolvedMediaLocator,
        remotePlaybackCandidates: List<RemotePlaybackUrlCandidate>?,
        localAccess: AppleLocalMediaAccess?,
        playWhenReady: Boolean,
        startPositionMs: Long,
        navidromeAudioQuality: NavidromeAudioQuality?,
    ) {
        currentRemotePlaybackFallback = remotePlaybackCandidates?.let { candidates ->
            AppleRemotePlaybackFallback(
                candidates = candidates,
                selectedIndex = 0,
                playWhenReady = playWhenReady,
            )
        }
        currentLocalMediaAccess = localAccess
        try {
            player.load(resolved)
        } catch (throwable: Throwable) {
            releaseCurrentLocalMediaAccess()
            publishFailure(applePlaybackLoadFailureText(platformLabel, throwable), throwable.message)
            return
        }
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        if (playWhenReady) {
            player.play()
        } else {
            player.pause()
        }
        mutableState.update {
            it.copy(
                isPlaying = playWhenReady,
                positionMs = 0L,
                durationMs = 0L,
                canSeek = player.canSeek(),
                currentNavidromeAudioQuality = navidromeAudioQuality,
                errorMessage = null,
                errorText = null,
            )
        }
        publishState()
    }

    override suspend fun play() = withContext(Dispatchers.Main.immediate) {
        currentRemotePlaybackFallback = currentRemotePlaybackFallback?.copy(playWhenReady = true)
        player.play()
        publishState()
    }

    override suspend fun pause() = withContext(Dispatchers.Main.immediate) {
        currentRemotePlaybackFallback = currentRemotePlaybackFallback?.copy(playWhenReady = false)
        player.pause()
        publishState()
    }

    override suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.Main.immediate) {
        if (!player.canSeek()) {
            mutableState.update { it.copy(canSeek = false) }
            return@withContext
        }
        player.seekTo(positionMs)
        mutableState.update {
            it.copy(
                positionMs = positionMs.coerceAtLeast(0L),
                canSeek = player.canSeek(),
                errorMessage = null,
                errorText = null,
            )
        }
        publishState()
    }

    override suspend fun setVolume(volume: Float) = withContext(Dispatchers.Main.immediate) {
        player.setVolume(volume)
        publishState()
    }

    override suspend fun release() {
        withContext(Dispatchers.Main.immediate) {
            currentRemotePlaybackFallback = null
            releaseCurrentLocalMediaAccess()
            player.release()
        }
        mainScope.cancel()
        AppleAudioSessionCoordinator.deactivate()
    }

    private fun stopAndResetForTrackSwitch() {
        player.stopAndClear()
        currentRemotePlaybackFallback = null
        releaseCurrentLocalMediaAccess()
        mutableState.update {
            it.resetForTrackSwitch(volumeOverride = player.volume())
        }
    }

    private fun releaseCurrentLocalMediaAccess() {
        currentLocalMediaAccess?.let { access -> runCatching(access.release) }
        currentLocalMediaAccess = null
    }

    private fun publishFailure(text: UiText, diagnostic: String? = null) {
        mutableState.update {
            it.copy(
                canSeek = false,
                errorMessage = diagnostic ?: (text).diagnosticMessage(),
                errorText = text,
            )
        }
    }

    private fun publishState(errorOverride: String? = null, errorTextOverride: UiText? = null) {
        if (player.isPlaying()) {
            markCurrentRemotePlaybackSuccess()
        }
        val errorMessage = errorOverride ?: player.errorMessage()
        mutableState.update {
            it.copy(
                isPlaying = player.isPlaying(),
                positionMs = player.positionMs().coerceAtLeast(0L),
                durationMs = player.durationMs()?.takeIf { value -> value > 0L } ?: it.durationMs,
                canSeek = player.canSeek(),
                volume = player.volume().coerceIn(0f, 1f),
                errorMessage = errorMessage,
                errorText = errorTextOverride ?: errorMessage?.let { applePlaybackFailureText(platformLabel, it) },
            )
        }
    }

    private fun tryApplyRemoteAddressFallback(errorMessage: String?): Boolean {
        val fallback = currentRemotePlaybackFallback ?: return false
        if (!isAppleRemotePlaybackFallbackAllowed(errorMessage)) return false
        return switchRemoteCandidate(
            fallback = fallback,
            targetIndex = fallback.selectedIndex + 1,
            playWhenReady = player.isPlaying() || fallback.playWhenReady,
        )
    }

    /** Leaves a LAN stream as soon as the device drops onto mobile data instead of waiting for it to stall. */
    private fun switchRemoteCandidateForNetwork(networkState: NetworkConnectionState) {
        val fallback = currentRemotePlaybackFallback ?: return
        val targetIndex = remoteCandidateIndexForNetworkChange(
            candidateKinds = fallback.candidates.map { it.addressKindOrNull },
            currentIndex = fallback.selectedIndex,
            networkState = networkState,
            // The fallback is cleared when a track completes, so an existing one belongs to an active track.
            isPlaybackActive = true,
        ) ?: return
        // isPlaying() is true while buffering, so a paused track stays paused after the switch.
        switchRemoteCandidate(fallback, targetIndex, playWhenReady = player.isPlaying())
    }

    private fun switchRemoteCandidate(
        fallback: AppleRemotePlaybackFallback,
        targetIndex: Int,
        playWhenReady: Boolean,
    ): Boolean {
        val nextCandidate = fallback.candidates.getOrNull(targetIndex) ?: return false
        val resolved = AppleMediaLocatorResolver.resolve(nextCandidate.value)
        if (resolved is AppleResolvedMediaLocator.Unsupported) return false
        val retryPositionMs = player.positionMs().coerceAtLeast(0L)
        currentRemotePlaybackFallback = fallback.copy(
            selectedIndex = targetIndex,
            playWhenReady = playWhenReady,
        )
        player.load(resolved)
        if (retryPositionMs > 0L) {
            player.seekTo(retryPositionMs)
        }
        if (playWhenReady) {
            player.play()
        } else {
            player.pause()
        }
        mutableState.update {
            it.copy(
                isPlaying = playWhenReady,
                positionMs = retryPositionMs,
                canSeek = player.canSeek(),
                errorMessage = null,
                errorText = null,
            )
        }
        return true
    }

    private fun markCurrentRemotePlaybackSuccess() {
        val candidate = currentRemotePlaybackFallback?.currentCandidate() ?: return
        val kind = candidate.addressKindOrNull ?: return
        candidate.sourceId.takeIf { it.isNotBlank() }?.let { sourceId ->
            addressSelector?.markSuccess(sourceId, kind)
        }
    }

    private fun isAppleRemotePlaybackFallbackAllowed(errorMessage: String?): Boolean =
        isRemoteSourceAddressFallbackAllowed(IllegalStateException(errorMessage.orEmpty()))
}
