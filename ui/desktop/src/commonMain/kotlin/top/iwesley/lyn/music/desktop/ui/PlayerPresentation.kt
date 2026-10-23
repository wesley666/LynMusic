package top.iwesley.lyn.music.desktop.ui

import androidx.compose.runtime.*
import top.iwesley.lyn.music.feature.player.*

/** Presentation belongs to this UI instance, even when playback services outlive it. */
@Stable
class PlayerPresentation(
    private val playback: () -> PlayerState = { PlayerState() },
    private val dispatch: (PlayerIntent) -> Unit = {},
) {
    var isExpanded by mutableStateOf(false)
        private set
    var isQueueVisible by mutableStateOf(false)
        private set
    var isLyricsStyleVisible by mutableStateOf(false)
    var isManualLyricsSearchVisible by mutableStateOf(false)
        private set
    var isLyricsShareVisible by mutableStateOf(false)
        private set
    var isCastSheetVisible by mutableStateOf(false)
        private set
    private var searchSession: PlayerTaskSession? = null
    private var shareSession: PlayerTaskSession? = null
    private var discoverySession: PlayerTaskSession? = null

    fun expand() { isExpanded = true }

    fun collapse() {
        // Also close a session that became ready before its overlay was composed.
        closeShare()
        isExpanded = false
    }

    fun showQueue(visible: Boolean) {
        isQueueVisible = visible && playback().snapshot.currentTrack != null
    }

    fun openLyricsStyle() {
        isLyricsStyleVisible = true
        dispatch(PlayerIntent.RequestLyricsFonts)
    }

    fun closeLyricsStyle() { isLyricsStyleVisible = false }

    fun openSearch() {
        dispatch(PlayerIntent.BeginManualLyricsSearch)
        searchSession = playback().manualLyricsSearchSession
        isManualLyricsSearchVisible = searchSession != null
    }

    fun closeSearch() {
        if (searchSession != null && playback().manualLyricsSearchSession == searchSession) {
            dispatch(PlayerIntent.EndManualLyricsSearch)
        }
        searchSession = null
        isManualLyricsSearchVisible = false
    }

    fun openShare() {
        closeSearch()
        dispatch(PlayerIntent.BeginLyricsShare)
        shareSession = playback().lyricsShareSession
        isLyricsShareVisible = shareSession != null && !playback().isLyricsShareInitializing
    }

    fun closeShare() {
        if (shareSession != null && playback().lyricsShareSession == shareSession) {
            dispatch(PlayerIntent.EndLyricsShare)
        }
        shareSession = null
        isLyricsShareVisible = false
    }

    fun openCast() {
        dispatch(PlayerIntent.BeginCastDiscovery)
        discoverySession = playback().castDiscoverySession
        isCastSheetVisible = discoverySession != null
    }

    fun closeCast() {
        if (discoverySession != null && playback().castDiscoverySession == discoverySession) {
            dispatch(PlayerIntent.EndCastDiscovery)
        }
        discoverySession = null
        isCastSheetVisible = false
    }

    fun onPlaybackIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.PlayQueueIndex -> if (intent.index in playback().snapshot.queue.indices) showQueue(false)
            is PlayerIntent.PlayTransientTracks -> if (intent.tracks.isNotEmpty()) showQueue(false)
            else -> Unit
        }
    }

    fun synchronize(state: PlayerState) {
        if (state.snapshot.currentTrack == null) showQueue(false)
        if (state.manualLyricsSearchSession != searchSession) {
            searchSession = null
            isManualLyricsSearchVisible = false
        }
        if (state.lyricsShareSession != shareSession) {
            shareSession = null
        }
        isLyricsShareVisible = shareSession != null && !state.isLyricsShareInitializing
        if (state.castDiscoverySession != discoverySession) {
            discoverySession = null
            isCastSheetVisible = false
        }
    }

    fun dispose() { closeSearch(); closeShare(); closeCast() }
}

internal val LocalPlayerPresentation = staticCompositionLocalOf<PlayerPresentation?> { null }

@Composable
internal fun currentPlayerPresentation(): PlayerPresentation =
    LocalPlayerPresentation.current ?: remember { PlayerPresentation() }

@Composable
internal fun rememberPlayerPresentation(store: PlayerStore, state: PlayerState): PlayerPresentation {
    val presentation = remember(store) { PlayerPresentation({ store.state.value }, store::dispatch) }
    LaunchedEffect(state.snapshot.currentTrack?.id,
        state.manualLyricsSearchSession, state.lyricsShareSession, state.isLyricsShareInitializing, state.castDiscoverySession) {
        presentation.synchronize(store.state.value)
    }
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            if (effect == PlayerEffect.TransientPlaybackStarted) presentation.showQueue(false)
        }
    }
    DisposableEffect(presentation) { onDispose { presentation.dispose() } }
    return presentation
}
