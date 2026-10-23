package top.iwesley.lyn.music.feature.player

import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.resources.Res
import top.iwesley.lyn.music.resources.lyrics_image_save_cancelled

class PlayerStoreLyricsShareExportLifecycleTest {
    @Test fun saveOwnsCapturedPreviewBytesAfterCloseAndTrackChange() = withLyricsShareLifecycle {
        open()
        val preview = assertNotNull(store.state.value.sharePreviewBytes)
        val expected = preview.copyOf()
        dispatch(PlayerIntent.SaveLyricsShareImage)
        // The write has not started. A later buffer edit must not alter the accepted export.
        preview.fill(0)
        dispatch(PlayerIntent.EndLyricsShare)
        switchTrack(original.copy(id = "next", title = "Next"))
        assertContentEquals(expected, platform.saves.single().input.bytes)
        assertEquals(buildLyricsShareSuggestedName(original.title), platform.saves.single().input.suggestedName)
        platform.saves.single().succeed(savedShareImage())
        drain()
        assertContentEquals(expected, platform.saved.single().bytes)
        assertNull(store.state.value.shareMessage)
        assertEquals(1, store.state.value.operationNotices.size)
    }

    @Test fun saveCapturesImmediatelyPrecedingSelection() = editThenSave(template = false)
    @Test fun saveCapturesImmediatelyPrecedingTemplate() = editThenSave(template = true)

    private fun editThenSave(template: Boolean) = withLyricsShareLifecycle {
        open()
        val initial = assertNotNull(store.state.value.shareCardModel)
        dispatch(if (template) PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT)
            else PlayerIntent.ToggleLyricsLineSelection(1))
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        platform.saves.single().succeed(savedShareImage())
        drain()
        val expected = if (template) initial.copy(template = LyricsShareTemplate.ARTWORK_TINT)
            else initial.copy(lyricsLines = listOf("original first", "original second"))
        assertContentEquals(expected.lifecycleImage(), platform.saved.single().bytes)
        assertFalse(store.state.value.isShareSaving)
    }

    @Test fun saveAfterImmediateClearDoesNotWriteImage() = withLyricsShareLifecycle {
        open()
        dispatch(PlayerIntent.ClearLyricsSelection)
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        assertTrue(platform.saves.isEmpty())
        assertFalse(store.state.value.isShareSaving)
        assertNotNull(store.state.value.shareMessage)
    }

    @Test fun saveAfterClearAndReselectCapturesOnlyTheNewSelection() = withLyricsShareLifecycle {
        open()
        val expected = assertNotNull(store.state.value.shareCardModel).copy(lyricsLines = listOf("original second"))
        dispatch(PlayerIntent.ClearLyricsSelection)
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        platform.saves.single().succeed(savedShareImage())
        drain()
        assertContentEquals(expected.lifecycleImage(), platform.saved.single().bytes)
    }

    @Test fun clipboardAndSaveQueuesPreserveTheirOwnOrderWithoutBlockingEachOther() = withLyricsShareLifecycle {
        open()
        val first = assertNotNull(store.state.value.shareCardModel)
        dispatch(PlayerIntent.SaveLyricsShareImage)
        dispatch(PlayerIntent.CopyLyricsShareImage)
        dispatch(PlayerIntent.ClearLyricsSelection)
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        dispatch(PlayerIntent.CopyLyricsShareText)
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        val second = first.copy(template = LyricsShareTemplate.ARTWORK_TINT, lyricsLines = listOf("original second"))
        assertEquals(1, platform.copies.size)
        assertEquals(1, platform.saves.size)
        assertContentEquals(first.lifecycleImage(), platform.saves.single().input.bytes)
        // Completing the first copy starts the second copy while the first save dialog is pending.
        platform.copies.single().succeed(Unit)
        drain()
        assertEquals(2, platform.copies.size)
        assertEquals(listOf("image:" + first.lifecycleImage().decodeToString()), platform.copied)
        assertEquals(1, platform.saves.size)
        assertTrue(store.state.value.isShareCopying)
        assertTrue(store.state.value.isShareSaving)
        // Both saves can finish while the second clipboard write is still pending.
        platform.saves.single().succeed(savedShareImage())
        drain()
        assertTrue(store.state.value.isShareSaving, "The first save cannot clear the second request's busy state")
        assertEquals(2, platform.saves.size)
        platform.saves.last().succeed(savedShareImage())
        drain()
        assertContentEquals(first.lifecycleImage(), platform.saved[0].bytes)
        assertContentEquals(second.lifecycleImage(), platform.saved[1].bytes)
        assertFalse(store.state.value.isShareSaving)
        assertTrue(store.state.value.isShareCopying)
        platform.copies.last().succeed(Unit)
        drain()
        assertEquals(listOf("image:" + first.lifecycleImage().decodeToString(), "text:original second"), platform.copied)
        assertFalse(store.state.value.isShareCopying)
    }

    @Test fun cancellingSaveDialogAndFailingNextWriteDoNotBlockRetry() = withLyricsShareLifecycle {
        open()
        dispatch(PlayerIntent.SaveLyricsShareImage)
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        platform.saves.single().fail(UiTextException(uiText(Res.string.lyrics_image_save_cancelled)))
        drain()
        assertEquals(2, platform.saves.size)
        assertTrue(store.state.value.isShareSaving)
        assertNull(store.state.value.shareMessage)
        assertEquals(1, store.state.value.operationNotices.size)
        platform.saves.last().fail()
        drain()
        assertFalse(store.state.value.isShareSaving)
        assertNotNull(store.state.value.shareMessage)
        val failedInput = platform.saves.last().input
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        assertEquals(3, platform.saves.size)
        assertNull(store.state.value.shareMessage)
        assertContentEquals(failedInput.bytes, platform.saves.last().input.bytes)
        platform.saves.last().succeed(savedShareImage())
        drain()
        assertEquals(1, platform.saved.size)
        assertFalse(store.state.value.isShareSaving)
    }

    @Test fun closeCancelsFontLookupAndPreviewButCommittedExportRendersFinishIndependently() = withLyricsShareLifecycle {
        open()
        platform.fontsAutomatically = false
        loadFonts()
        platform.renderAutomatically = false
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        val livePreview = platform.renders.last()
        val expected = livePreview.input
        val precedingRenders = platform.renders.size
        dispatch(PlayerIntent.CopyLyricsShareImage)
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        val committedRenders = platform.renders.drop(precedingRenders)
        assertEquals(2, committedRenders.size)
        closeShare()
        assertTrue(livePreview.cancelled)
        assertTrue(platform.fonts.single().cancelled)
        assertTrue(committedRenders.none { it.cancelled })
        committedRenders.forEach { it.succeed(it.input.lifecycleImage()) }
        drain()
        platform.copies.single().succeed(Unit)
        platform.saves.single().succeed(savedShareImage())
        drain()
        assertEquals(listOf("image:" + expected.lifecycleImage().decodeToString()), platform.copied)
        assertContentEquals(expected.lifecycleImage(), platform.saved.single().bytes)
        assertEquals(buildLyricsShareSuggestedName("Original"), platform.saved.single().suggestedName)
        assertNull(store.state.value.lyricsShareSession)
        assertNull(store.state.value.sharePreviewBytes)
        assertNull(store.state.value.shareMessage)
        assertEquals(2, store.state.value.operationNotices.size)
    }

    @Test fun oldExportsSucceedAfterTrackRoundTripWithoutFinishingNewRequests() = exportsAfterTrackRoundTrip(false)
    @Test fun oldExportsFailAfterTrackRoundTripWithoutFinishingNewRequests() = exportsAfterTrackRoundTrip(true)

    private fun exportsAfterTrackRoundTrip(fail: Boolean) = withLyricsShareLifecycle {
        open()
        val oldSession = store.state.value.lyricsShareSession
        val oldModel = assertNotNull(store.state.value.shareCardModel)
        dispatch(PlayerIntent.CopyLyricsShareImage)
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        switchTrack(original.copy(id = "next", title = "Next"))
        switchTrack(original)
        open()
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        dispatch(PlayerIntent.CopyLyricsShareText)
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        val current = store.state.value
        assertNotEquals(oldSession, current.lyricsShareSession)
        if (fail) {
            platform.copies.single().fail()
            platform.saves.single().fail()
        } else {
            platform.copies.single().succeed(Unit)
            platform.saves.single().succeed(savedShareImage())
        }
        drain()
        assertTrue(store.state.value.isShareCopying)
        assertTrue(store.state.value.isShareSaving)
        assertEquals(current.lyricsShareSession, store.state.value.lyricsShareSession)
        assertEquals(current.shareCardModel, store.state.value.shareCardModel)
        assertContentEquals(current.sharePreviewBytes, store.state.value.sharePreviewBytes)
        assertNull(store.state.value.shareMessage)
        assertEquals(2, store.state.value.operationNotices.size)
        assertEquals(2, platform.copies.size)
        assertEquals(2, platform.saves.size)
        platform.copies.last().succeed(Unit)
        platform.saves.last().succeed(savedShareImage())
        drain()
        val currentImage = assertNotNull(current.shareCardModel).lifecycleImage()
        assertEquals(if (fail) 1 else 2, platform.saved.size)
        assertContentEquals(currentImage, platform.saved.last().bytes)
        assertEquals("text:original first\noriginal second", platform.copied.last())
        if (!fail) {
            assertContentEquals(oldModel.lifecycleImage(), platform.saved.first().bytes)
            assertEquals("image:" + oldModel.lifecycleImage().decodeToString(), platform.copied.first())
        }
        assertFalse(store.state.value.isShareCopying)
        assertFalse(store.state.value.isShareSaving)
    }

    @Test fun storeShutdownCancelsBothWorkersWithoutStartingQueuedWrites() = withLyricsShareLifecycle {
        open()
        dispatch(PlayerIntent.CopyLyricsShareText)
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        dispatch(PlayerIntent.CopyLyricsShareImage)
        dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        assertEquals(1, platform.copies.size)
        assertEquals(1, platform.saves.size)
        shutdown()
        assertTrue(platform.copies.single().cancelled)
        assertTrue(platform.saves.single().cancelled)
        platform.copies.single().succeed(Unit)
        platform.saves.single().succeed(savedShareImage())
        drain()
        assertEquals(1, platform.copies.size)
        assertEquals(1, platform.saves.size)
        assertTrue(platform.copied.isEmpty())
        assertTrue(platform.saved.isEmpty())
        assertTrue(store.state.value.operationNotices.isEmpty())
    }
}
