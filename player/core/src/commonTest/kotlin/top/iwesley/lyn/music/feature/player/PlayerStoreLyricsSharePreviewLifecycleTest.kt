package top.iwesley.lyn.music.feature.player

import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

class PlayerStoreLyricsSharePreviewLifecycleTest {
    @Test fun olderPreviewSuccessCannotReplaceNewSelectionAndTemplate() = oldPreviewAfterNew(false)
    @Test fun olderPreviewFailureCannotReplaceNewSelectionAndTemplate() = oldPreviewAfterNew(true)

    private fun oldPreviewAfterNew(fail: Boolean) = withLyricsShareLifecycle {
        open()
        platform.renderAutomatically = false
        platform.ignoreRenderCancellation = true
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        val old = platform.renders.last()
        dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        drain()
        val latest = platform.renders.last()
        assertNotEquals(old.input, latest.input)
        latest.succeed(latest.input.lifecycleImage())
        drain()
        if (fail) old.fail() else old.succeed(old.input.lifecycleImage())
        drain()
        assertEquals(latest.input, store.state.value.shareCardModel)
        assertContentEquals(latest.input.lifecycleImage(), store.state.value.sharePreviewBytes)
        assertTrue(store.state.value.hasFreshSharePreview)
        assertNull(store.state.value.sharePreviewError)
        assertFalse(store.state.value.isShareRendering)
    }

    @Test fun olderSuccessCannotClearLatestFailureAndRetryUsesLatestContent() = withLyricsShareLifecycle {
        open()
        platform.renderAutomatically = false
        platform.ignoreRenderCancellation = true
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        val old = platform.renders.last()
        dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        drain()
        val latest = platform.renders.last()
        latest.fail()
        drain()
        val error = assertNotNull(store.state.value.sharePreviewError)
        old.succeed(old.input.lifecycleImage())
        drain()
        assertEquals(error, store.state.value.sharePreviewError)
        assertFalse(store.state.value.isShareRendering)
        assertFalse(store.state.value.hasFreshSharePreview)
        dispatch(PlayerIntent.BuildLyricsSharePreview)
        drain()
        val retry = platform.renders.last()
        assertEquals(latest.input, retry.input)
        retry.succeed(retry.input.lifecycleImage())
        drain()
        assertNull(store.state.value.sharePreviewError)
        assertFalse(store.state.value.isShareRendering)
        assertTrue(store.state.value.hasFreshSharePreview)
        assertContentEquals(retry.input.lifecycleImage(), store.state.value.sharePreviewBytes)
    }

    @Test fun clearingSelectionRejectsLatePreviewSuccess() = clearDuringPreview(false)
    @Test fun clearingSelectionRejectsLatePreviewFailure() = clearDuringPreview(true)

    private fun clearDuringPreview(fail: Boolean) = withLyricsShareLifecycle {
        open()
        platform.renderAutomatically = false
        platform.ignoreRenderCancellation = true
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        val pending = platform.renders.last()
        dispatch(PlayerIntent.ClearLyricsSelection)
        if (fail) pending.fail() else pending.succeed(pending.input.lifecycleImage())
        drain()
        assertTrue(store.state.value.selectedLyricsLineIndices.isEmpty())
        assertNull(store.state.value.shareCardModel)
        assertNull(store.state.value.sharePreviewBytes)
        assertNull(store.state.value.sharePreviewError)
        assertFalse(store.state.value.isShareRendering)
    }

    @Test fun reopenedShareRejectsLatePreviewSuccess() = latePreview(false, false)
    @Test fun reopenedShareRejectsLatePreviewFailure() = latePreview(false, true)
    @Test fun trackRoundTripRejectsLatePreviewSuccess() = latePreview(true, false)
    @Test fun trackRoundTripRejectsLatePreviewFailure() = latePreview(true, true)

    private fun latePreview(trackRoundTrip: Boolean, fail: Boolean) = withLyricsShareLifecycle {
        open()
        val oldSession = store.state.value.lyricsShareSession
        platform.renderAutomatically = false
        platform.ignoreRenderCancellation = true
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        val pending = platform.renders.last()
        if (trackRoundTrip) {
            switchTrack(original.copy(id = "next", title = "Next"))
            switchTrack(original)
        } else closeShare()
        platform.renderAutomatically = true
        open()
        val current = store.state.value
        assertNotEquals(oldSession, current.lyricsShareSession)
        if (fail) pending.fail() else pending.succeed(pending.input.lifecycleImage())
        drain()
        assertEquals(current.lyricsShareSession, store.state.value.lyricsShareSession)
        assertEquals(current.shareCardModel, store.state.value.shareCardModel)
        assertContentEquals(current.sharePreviewBytes, store.state.value.sharePreviewBytes)
        assertNull(store.state.value.sharePreviewError)
        assertTrue(store.state.value.hasFreshSharePreview)
        assertFalse(store.state.value.isShareRendering)
    }
}
