package top.iwesley.lyn.music.feature.player

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.data.repository.PlaybackHydrationResult

/** Identifies cancellable work, independent of any page, drawer, or window. */
data class PlayerTaskSession(val id: Long, val trackId: String?)

data class PlayerRestorationResult(
    val hydration: PlaybackHydrationResult,
    val snapshot: PlaybackSnapshot,
) {
    // Keep the token check live: user playback can supersede restoration before the host consumes it.
    val hasCurrentPlayback: Boolean
        get() = snapshot.currentTrack != null && when (val result = hydration) {
            is PlaybackHydrationResult.Restored -> result.loadToken.isCurrent()
            PlaybackHydrationResult.ExistingPlayback -> true
            else -> false
        }
}

/** Completion feedback outlives the search/share session that submitted the operation. */
data class PlayerOperationNotice(val id: Long, val message: UiText)
