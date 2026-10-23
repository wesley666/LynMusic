package top.iwesley.lyn.music.tv

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.feature.player.*

/** TV owns queue visibility and search focus independently of the shared playback service. */
@Stable
internal class PlayerPresentation(
    private val playback: () -> PlayerState = { PlayerState() },
    private val dispatch: (PlayerIntent) -> Unit = {},
) {
    var isQueueVisible by mutableStateOf(false)
        private set
    var isManualLyricsSearchVisible by mutableStateOf(false)
        private set
    private var searchSession: PlayerTaskSession? = null

    fun showQueue(visible: Boolean) {
        isQueueVisible = visible && playback().snapshot.currentTrack != null
    }

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
    }
}

internal val LocalPlayerPresentation = staticCompositionLocalOf<PlayerPresentation?> { null }

@Composable
internal fun currentPlayerPresentation(): PlayerPresentation =
    LocalPlayerPresentation.current ?: remember { PlayerPresentation() }

@Composable
internal fun rememberPlayerPresentation(store: PlayerStore): PlayerPresentation =
    viewModel { PlayerPresentationViewModel(store) }.presentation

/** Retained by the Activity, so recreating its composition neither resets UI nor ends lyrics work. */
private class PlayerPresentationViewModel(store: PlayerStore) : ViewModel() {
    val presentation = PlayerPresentation({ store.state.value }, store::dispatch)

    init {
        // Keep observing even between compositions: a completed task or a track change must still
        // invalidate the old UI session, and transient playback must still close the queue.
        viewModelScope.launch {
            store.state.collect(presentation::synchronize)
        }
        viewModelScope.launch {
            store.effects.collect { effect ->
                if (effect == PlayerEffect.TransientPlaybackStarted) presentation.showQueue(false)
            }
        }
    }

    override fun onCleared() {
        // Ending the logical screen releases only its own search, never the process playback Store.
        presentation.closeSearch()
    }
}
