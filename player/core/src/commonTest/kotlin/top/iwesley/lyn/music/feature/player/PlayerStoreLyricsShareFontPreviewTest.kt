package top.iwesley.lyn.music.feature.player

import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

class PlayerStoreLyricsShareFontPreviewTest {
    // Known P2 regression: these assert the required terminal state, not the current stuck state.
    @Test fun androidFontNormalizationDoesNotLeaveSuccessfulPreviewRunning() = fontArrivesDuringPreview("serif", false)
    @Test fun androidFontNormalizationDoesNotLeaveFailedPreviewRunning() = fontArrivesDuringPreview("serif", true)
    @Test fun fallbackFontDoesNotLeaveSuccessfulPreviewRunning() = fontArrivesDuringPreview("Fallback", false)
    @Test fun fallbackFontDoesNotLeaveFailedPreviewRunning() = fontArrivesDuringPreview("Fallback", true)

    private fun fontArrivesDuringPreview(font: String, failOldPreview: Boolean) = withLyricsShareLifecycle {
        open()
        dispatch(PlayerIntent.ClearLyricsSelection)
        platform.fontsAutomatically = false
        loadFonts()
        assertTrue(store.state.value.isLyricsShareFontsLoading)
        platform.renderAutomatically = false
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        val oldPreview = platform.renders.last()
        assertTrue(store.state.value.isShareRendering)
        platform.fonts.single().succeed(listOf(LyricsShareFontOption(font, font)))
        drain()
        assertFalse(store.state.value.isLyricsShareFontsLoading)
        if (failOldPreview) oldPreview.fail() else oldPreview.succeed(oldPreview.input.lifecycleImage())
        drain()
        // A replacement is allowed. Do not pin the fix to a particular number of render jobs.
        platform.renders.filter { it !== oldPreview }.forEach { it.succeed(it.input.lifecycleImage()) }
        drain()
        assertFalse(store.state.value.isShareRendering, "Every render has ended; generation must reach a terminal state")
        assertTrue(store.state.value.hasFreshSharePreview || store.state.value.sharePreviewError != null,
            "The current content needs either a completed preview or visible failure")
    }

    @Test fun fontListBeforeSelectionUsesResolvedFontForTheNextPreview() = withLyricsShareLifecycle {
        open()
        dispatch(PlayerIntent.ClearLyricsSelection)
        platform.fontsAutomatically = false
        loadFonts()
        platform.fonts.single().succeed(listOf(LyricsShareFontOption("Fallback", "Fallback")))
        drain()
        assertFalse(store.state.value.isShareRendering)
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        assertEquals("Fallback", platform.renders.last().input.fontKey)
        assertEquals(listOf("original second"), platform.renders.last().input.lyricsLines)
        assertTrue(store.state.value.hasFreshSharePreview)
    }

    @Test fun unchangedFontListDoesNotPreventPendingPreviewFromCompleting() = withLyricsShareLifecycle {
        open()
        dispatch(PlayerIntent.ClearLyricsSelection)
        platform.fontsAutomatically = false
        loadFonts()
        platform.renderAutomatically = false
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        platform.fonts.single().succeed(listOf(LyricsShareFontOption("Serif", "Renamed label")))
        drain()
        platform.renders.last().let { it.succeed(it.input.lifecycleImage()) }
        drain()
        assertFalse(store.state.value.isShareRendering)
        assertTrue(store.state.value.hasFreshSharePreview)
    }

    @Test fun clearingWhileFontsLoadDoesNotResurrectSelectionOrPreview() = withLyricsShareLifecycle {
        open()
        platform.fontsAutomatically = false
        loadFonts()
        dispatch(PlayerIntent.ClearLyricsSelection)
        platform.fonts.single().succeed(listOf(LyricsShareFontOption("Fallback", "Fallback")))
        drain()
        assertTrue(store.state.value.selectedLyricsLineIndices.isEmpty())
        assertNull(store.state.value.shareCardModel)
        assertNull(store.state.value.sharePreviewBytes)
        assertFalse(store.state.value.isShareRendering)
        assertFalse(store.state.value.isLyricsShareFontsLoading)
    }

    @Test fun fontResolutionRebuildsLatestSelectionAndTemplate() = withLyricsShareLifecycle {
        open()
        platform.fontsAutomatically = false
        platform.renderAutomatically = false
        loadFonts()
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        drain()
        platform.fonts.single().succeed(listOf(LyricsShareFontOption("Fallback", "Fallback")))
        drain()
        val latest = platform.renders.last()
        assertEquals(listOf("original first", "original second"), latest.input.lyricsLines)
        assertEquals(LyricsShareTemplate.ARTWORK_TINT, latest.input.template)
        assertEquals("Fallback", latest.input.fontKey)
        latest.succeed(latest.input.lifecycleImage())
        drain()
        platform.renders.drop(1).filter { it !== latest }.forEach { it.fail() }
        drain()
        assertContentEquals(latest.input.lifecycleImage(), store.state.value.sharePreviewBytes)
        assertNull(store.state.value.sharePreviewError)
        assertFalse(store.state.value.isShareRendering)
    }

    @Test fun invalidatedFontLookupCannotReplaceNewCacheOrSelection() = withLyricsShareLifecycle {
        open()
        platform.fontsAutomatically = false
        platform.ignoreFontCancellation = true
        loadFonts()
        val old = platform.fonts.single()
        dispatch(PlayerIntent.InvalidateLyricsShareFontCache)
        drain()
        loadFonts()
        platform.fonts.last().succeed(listOf(LyricsShareFontOption("Current", "Current")))
        drain()
        old.succeed(listOf(LyricsShareFontOption("Obsolete", "Obsolete")))
        drain()
        assertEquals(listOf("Current"), store.state.value.availableLyricsShareFonts.map { it.fontKey })
        assertEquals("Current", store.state.value.selectedLyricsShareFontKey)
        assertEquals("Current", preferences.selectedLyricsShareFontKey.value)
        assertFalse(store.state.value.isLyricsShareFontsLoading)
    }

    @Test fun lateFontSuccessCannotEditReopenedShare() = lateFonts(false, false)
    @Test fun lateFontFailureCannotEditReopenedShare() = lateFonts(false, true)
    @Test fun lateFontSuccessCannotEditShareAfterTrackRoundTrip() = lateFonts(true, false)
    @Test fun lateFontFailureCannotEditShareAfterTrackRoundTrip() = lateFonts(true, true)

    private fun lateFonts(trackRoundTrip: Boolean, fail: Boolean) = withLyricsShareLifecycle {
        open()
        platform.fontsAutomatically = false
        platform.ignoreFontCancellation = true
        loadFonts()
        val old = platform.fonts.single()
        val oldSession = store.state.value.lyricsShareSession
        closeShare()
        if (trackRoundTrip) {
            switchTrack(original.copy(id = "next", title = "Next"))
            switchTrack(original)
        }
        open()
        loadFonts()
        platform.fonts.last().succeed(listOf(LyricsShareFontOption("Current", "Current")))
        drain()
        val current = store.state.value
        assertNotEquals(oldSession, current.lyricsShareSession)
        if (fail) old.fail() else old.succeed(listOf(LyricsShareFontOption("Obsolete", "Obsolete")))
        drain()
        assertEquals(current.lyricsShareSession, store.state.value.lyricsShareSession)
        assertEquals(current.selectedLyricsShareFontKey, store.state.value.selectedLyricsShareFontKey)
        assertEquals(current.availableLyricsShareFonts, store.state.value.availableLyricsShareFonts)
        assertContentEquals(current.sharePreviewBytes, store.state.value.sharePreviewBytes)
        assertNull(store.state.value.lyricsShareFontsError)
        assertEquals("Current", preferences.selectedLyricsShareFontKey.value)
    }

    @Test fun repeatedFontRequestsSharePendingLookupAndFailureAllowsRetry() = withLyricsShareLifecycle {
        open()
        platform.fontsAutomatically = false
        repeat(3) { dispatch(PlayerIntent.RequestLyricsShareFonts); drain() }
        assertEquals(1, platform.fonts.size)
        platform.fonts.single().fail()
        drain()
        assertFalse(store.state.value.isLyricsShareFontsLoading)
        assertNotNull(store.state.value.lyricsShareFontsError)
        loadFonts()
        assertEquals(2, platform.fonts.size)
        platform.fonts.last().succeed(listOf(LyricsShareFontOption("Serif", "Serif")))
        drain()
        assertFalse(store.state.value.isLyricsShareFontsLoading)
        assertNull(store.state.value.lyricsShareFontsError)
        assertTrue(store.state.value.hasFreshSharePreview)
    }
}
