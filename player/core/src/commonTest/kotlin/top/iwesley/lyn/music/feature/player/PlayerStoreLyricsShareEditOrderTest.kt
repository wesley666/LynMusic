package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStoreLyricsShareEditOrderTest {
    @Test fun textCopyCapturesImmediatelyPrecedingSelection() = verifyEditCopy(image = false)
    @Test fun imageCopyCapturesImmediatelyPrecedingSelection() = verifyEditCopy(image = true)
    @Test fun textCopyAfterImmediateClearDoesNotWriteClipboard() = verifyEditCopy(image = false, clear = true)
    @Test fun imageCopyAfterImmediateClearDoesNotWriteClipboard() = verifyEditCopy(image = true, clear = true)
    @Test fun imageCopyCapturesImmediatelyPrecedingTemplate() = verifyEditCopy(image = true, template = true)

    @Test fun mixedCopiesCaptureEachPrecedingEditAndWriteInSubmissionOrder() = withFixture {
        open()
        val initialModel = assertNotNull(store.state.value.shareCardModel)
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        store.dispatch(PlayerIntent.ClearLyricsSelection)
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        store.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        store.dispatch(PlayerIntent.CopyLyricsShareText) // Empty selection must not create a request.
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(0))
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        val latest = assertNotNull(store.state.value.lyricsShareCopyRequestId)
        assertEquals(setOf(0), store.state.value.selectedLyricsLineIndices)
        assertTrue(share.attempts.isEmpty(), "No asynchronous work has run between dispatches")
        drain()
        assertEquals(listOf("text:original first\noriginal second"), share.attempts)
        release(0)
        assertTrue(store.state.value.isShareCopying)
        assertEquals(latest, store.state.value.lyricsShareCopyRequestId)
        assertNull(store.state.value.shareMessage)
        release(1)
        assertTrue(store.state.value.isShareCopying)
        assertEquals(latest, store.state.value.lyricsShareCopyRequestId)
        release(2)
        assertEquals(listOf(
            "text:original first\noriginal second",
            "image:" + initialModel.copy(
                template = LyricsShareTemplate.ARTWORK_TINT, lyricsLines = listOf("original second"),
            ).copyTestBytes().decodeToString(),
            "text:original first",
        ), share.writes)
        assertFalse(store.state.value.isShareCopying)
        assertNull(store.state.value.lyricsShareCopyRequestId)
        assertEquals(2, store.state.value.operationNotices.size)
    }

    @Test fun editedImageSurvivesImmediateCloseAndDoesNotOverwriteReopenedShare() = editCopyAndExit(image = true, changeTrack = false)
    @Test fun editedTextSurvivesImmediateCloseAndDoesNotOverwriteReopenedShare() = editCopyAndExit(image = false, changeTrack = false)
    @Test fun editedImageSurvivesTrackChangeAndDoesNotOverwriteNewSongShare() = editCopyAndExit(image = true, changeTrack = true)
    @Test fun editedTextSurvivesTrackChangeAndDoesNotOverwriteNewSongShare() = editCopyAndExit(image = false, changeTrack = true)

    @Test fun failedCopyCanRetryWithImmediatelyEditedContent() = withFixture {
        open()
        share.failedCopies += 0
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        drain()
        release(0)
        assertFalse(store.state.value.isShareCopying)
        assertNotNull(store.state.value.shareMessage)
        store.dispatch(PlayerIntent.ClearLyricsSelection)
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        assertNull(store.state.value.shareMessage)
        drain()
        release(1)
        assertEquals(listOf("text:original first\noriginal second", "text:original second"), share.attempts)
        assertEquals(listOf("text:original second"), share.writes)
        assertFalse(store.state.value.isShareCopying)
        assertNull(store.state.value.lyricsShareCopyRequestId)
    }

    @Test fun editsOutsideShareDoNotChangeContentOrStartWork() = withFixture {
        val before = store.state.value
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        store.dispatch(PlayerIntent.ClearLyricsSelection)
        store.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        drain()
        assertEquals(before, store.state.value)
        assertTrue(share.models.isEmpty())
        assertTrue(share.attempts.isEmpty())
    }

    private fun verifyEditCopy(image: Boolean, clear: Boolean = false, template: Boolean = false) = withFixture {
        open()
        val initialModel = assertNotNull(store.state.value.shareCardModel)
        val previousRenders = share.models.size
        store.dispatch(when {
            template -> PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT)
            clear -> PlayerIntent.ClearLyricsSelection
            else -> PlayerIntent.ToggleLyricsLineSelection(1)
        })
        // Do not drain the scheduler: capture must see the edit before any preview work starts.
        assertEquals(if (clear) emptySet() else if (template) setOf(0) else setOf(0, 1), store.state.value.selectedLyricsLineIndices)
        assertEquals(previousRenders, share.models.size)
        store.dispatch(if (image) PlayerIntent.CopyLyricsShareImage else PlayerIntent.CopyLyricsShareText)
        drain()
        if (clear) {
            assertTrue(share.attempts.isEmpty())
            assertFalse(store.state.value.isShareCopying)
            assertNotNull(store.state.value.shareMessage)
        } else {
            release(0)
            val expectedModel = if (template) initialModel.copy(template = LyricsShareTemplate.ARTWORK_TINT)
                else initialModel.copy(lyricsLines = listOf("original first", "original second"))
            val expected = if (image) "image:" + expectedModel.copyTestBytes().decodeToString()
                else "text:original first\noriginal second"
            assertEquals(listOf(expected), share.writes)
            assertFalse(store.state.value.isShareCopying)
        }
    }

    private fun editCopyAndExit(image: Boolean, changeTrack: Boolean) = withFixture {
        open()
        val oldSession = store.state.value.lyricsShareSession
        val oldModel = assertNotNull(store.state.value.shareCardModel)
        store.dispatch(PlayerIntent.ClearLyricsSelection)
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        store.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        store.dispatch(if (image) PlayerIntent.CopyLyricsShareImage else PlayerIntent.CopyLyricsShareText)
        if (changeTrack) {
            playback.snapshot.value = PlaybackSnapshot(queue = listOf(track.copy(id = "next", title = "Next")), currentIndex = 0)
        } else {
            store.dispatch(PlayerIntent.EndLyricsShare)
        }
        drain()
        assertNull(store.state.value.lyricsShareSession)
        open()
        assertNotEquals(oldSession, store.state.value.lyricsShareSession)
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        val newSession = store.state.value.lyricsShareSession
        val newRequest = assertNotNull(store.state.value.lyricsShareCopyRequestId)
        val newModel = store.state.value.shareCardModel
        drain()
        release(0)
        val oldContent = if (image) "image:" + oldModel.copy(
            template = LyricsShareTemplate.ARTWORK_TINT, lyricsLines = listOf("original second"),
        ).copyTestBytes().decodeToString() else "text:original second"
        assertEquals(listOf(oldContent), share.writes)
        assertEquals(newSession, store.state.value.lyricsShareSession)
        assertEquals(newRequest, store.state.value.lyricsShareCopyRequestId)
        assertEquals(newModel, store.state.value.shareCardModel)
        assertTrue(store.state.value.isShareCopying)
        assertNull(store.state.value.shareMessage)
        assertEquals(1, store.state.value.operationNotices.size)
        release(1)
        assertEquals(listOf(oldContent, "text:${if (changeTrack) "next" else "original"} first"), share.writes)
        assertFalse(store.state.value.isShareCopying)
    }

    private fun withFixture(test: Fixture.() -> Unit) = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val fixture = Fixture(this, scope)
            advanceUntilIdle()
            fixture.test()
        } finally {
            scope.cancel()
            advanceUntilIdle()
        }
    }

    private class Fixture(val testScope: TestScope, scope: CoroutineScope) {
        val share = CopyTestShareService()
        val track = Track("original", "source", "Original", mediaLocator = "", relativePath = "")
        val playback = CommitPlaybackRepository(PlaybackSnapshot(queue = listOf(track), currentIndex = 0))
        val store = PlayerStore(playback, CopyTestLyricsRepository(), scope, lyricsSharePlatformService = share)
        fun drain() = testScope.advanceUntilIdle()
        fun open() { store.dispatch(PlayerIntent.BeginLyricsShare); drain() }
        fun release(index: Int) { share.copyGates[index].complete(Unit); drain() }
    }
}
