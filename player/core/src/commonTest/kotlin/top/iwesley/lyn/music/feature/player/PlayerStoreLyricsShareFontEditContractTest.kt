package top.iwesley.lyn.music.feature.player

import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

/** Font edits are immediate; preference writes are ordered and never reapply an old draft. */
class PlayerStoreLyricsShareFontEditContractTest {
    @Test fun immediateImageCopyUsesFontChoiceBeforePreferenceWriteCompletes() = editThenExport(save = false)
    @Test fun immediateImageSaveUsesFontChoiceBeforePreferenceWriteCompletes() = editThenExport(save = true)

    private fun editThenExport(save: Boolean) = withLyricsShareLifecycle {
        open()
        loadFonts()
        val expected = assertNotNull(store.state.value.shareCardModel).copy(fontKey = "Another")
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        dispatch(if (save) PlayerIntent.SaveLyricsShareImage else PlayerIntent.CopyLyricsShareImage)
        drain()
        if (save) {
            platform.saves.single().succeed(savedShareImage())
            drain()
            assertContentEquals(expected.lifecycleImage(), platform.saved.single().bytes)
        } else {
            platform.copies.single().succeed(Unit)
            drain()
            assertEquals(listOf("image:" + expected.lifecycleImage().decodeToString()), platform.copied)
        }
    }

    @Test fun acceptedFontChoicePersistsAfterClosingAndReopeningShare() = withLyricsShareLifecycle {
        open()
        loadFonts()
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        drain()
        val write = preferences.calls.last()
        closeShare()
        write.succeed(Unit)
        drain()
        assertEquals("Another", preferences.selectedLyricsShareFontKey.value)
        open()
        assertEquals("Another", store.state.value.selectedLyricsShareFontKey)
    }

    @Test fun returningToOriginalFontWhileWriteIsPendingKeepsLastChoice() = withLyricsShareLifecycle {
        open()
        loadFonts()
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        drain()
        dispatch(PlayerIntent.LyricsShareFontChanged("Serif"))
        drain()
        preferences.calls.filter { it.input == "Another" }.forEach { it.succeed(Unit) }
        drain()
        preferences.calls.filter { it.input == "Serif" }.forEach { it.succeed(Unit) }
        drain()
        assertEquals("Serif", store.state.value.selectedLyricsShareFontKey)
        assertEquals("Serif", preferences.selectedLyricsShareFontKey.value)
        assertEquals("Serif", store.state.value.shareCardModel?.fontKey)
    }

    @Test fun fontPreferenceWritesFollowEditOrderWhilePreviewUsesLatestChoiceImmediately() = withLyricsShareLifecycle {
        open()
        loadFonts()
        val previousWrites = preferences.calls.size
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        dispatch(PlayerIntent.LyricsShareFontChanged("Serif"))
        assertEquals("Serif", store.state.value.selectedLyricsShareFontKey)
        drain()
        assertEquals(listOf("Another"), preferences.calls.drop(previousWrites).map { it.input })
        assertEquals("Serif", platform.renders.last().input.fontKey)
        assertTrue(store.state.value.hasFreshSharePreview)
        preferences.calls.last().succeed(Unit)
        drain()
        assertEquals(listOf("Another", "Serif"), preferences.calls.drop(previousWrites).map { it.input })
        assertEquals("Serif", store.state.value.selectedLyricsShareFontKey)
        preferences.calls.last().succeed(Unit)
        drain()
        assertEquals("Serif", preferences.selectedLyricsShareFontKey.value)
    }

    @Test fun preferenceCompletionCannotRestoreClearedSelectionOrPreview() = withLyricsShareLifecycle {
        open()
        loadFonts()
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        drain()
        dispatch(PlayerIntent.ClearLyricsSelection)
        preferences.calls.last().succeed(Unit)
        drain()
        assertTrue(store.state.value.selectedLyricsLineIndices.isEmpty())
        assertNull(store.state.value.shareCardModel)
        assertNull(store.state.value.sharePreviewBytes)
        assertFalse(store.state.value.isShareRendering)
        assertEquals("Another", preferences.selectedLyricsShareFontKey.value)
    }

    @Test fun failedPreferenceWriteKeepsDraftAndReselectingTheSameFontRetries() = withLyricsShareLifecycle {
        open()
        loadFonts()
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        drain()
        val preview = store.state.value.sharePreviewBytes
        val renderCount = platform.renders.size
        preferences.calls.last().fail()
        drain()
        assertEquals("Another", store.state.value.selectedLyricsShareFontKey)
        assertNotNull(store.state.value.shareMessage)
        val attempts = preferences.calls.size
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        assertNull(store.state.value.shareMessage)
        drain()
        assertEquals(attempts + 1, preferences.calls.size)
        preferences.calls.last().succeed(Unit)
        drain()
        assertEquals("Another", preferences.selectedLyricsShareFontKey.value)
        assertEquals("Another", store.state.value.selectedLyricsShareFontKey)
        assertContentEquals(preview, store.state.value.sharePreviewBytes)
        assertEquals(renderCount, platform.renders.size, "Retrying persistence does not invalidate a completed preview")
    }

    @Test fun failedEarlierChoiceDoesNotReportIntoLatestChoiceAndWriterContinues() = withLyricsShareLifecycle {
        open()
        loadFonts()
        val previousWrites = preferences.calls.size
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        dispatch(PlayerIntent.LyricsShareFontChanged("Serif"))
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        drain()
        preferences.calls.last().fail()
        drain()
        assertNull(store.state.value.shareMessage, "The earlier failure must not replace the latest request's feedback")
        // The middle choice already matches persistence, but must not stop the following write.
        assertEquals(listOf("Another", "Another"), preferences.calls.drop(previousWrites).map { it.input })
        preferences.calls.last().succeed(Unit)
        drain()
        assertEquals("Another", preferences.selectedLyricsShareFontKey.value)
        assertEquals("Another", store.state.value.selectedLyricsShareFontKey)
    }

    @Test fun oldPreferenceSuccessCannotEditReopenedShare() = oldPreferenceAcrossSessions(false, false)
    @Test fun oldPreferenceFailureCannotEditReopenedShare() = oldPreferenceAcrossSessions(false, true)
    @Test fun oldPreferenceSuccessCannotEditNewSongShare() = oldPreferenceAcrossSessions(true, false)
    @Test fun oldPreferenceFailureCannotEditNewSongShare() = oldPreferenceAcrossSessions(true, true)

    private fun oldPreferenceAcrossSessions(changeTrack: Boolean, fail: Boolean) = withLyricsShareLifecycle {
        open()
        loadFonts()
        val oldSession = store.state.value.lyricsShareSession
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        drain()
        val old = preferences.calls.last()
        if (changeTrack) switchTrack(original.copy(id = "next", title = "Next")) else closeShare()
        open()
        dispatch(PlayerIntent.LyricsShareFontChanged("Serif"))
        dispatch(PlayerIntent.ClearLyricsSelection)
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        dispatch(PlayerIntent.CopyLyricsShareText)
        drain()
        val current = store.state.value
        assertNotEquals(oldSession, current.lyricsShareSession)
        assertEquals("Serif", current.selectedLyricsShareFontKey)
        assertFalse(old.cancelled)
        if (fail) old.fail() else old.succeed(Unit)
        drain()
        assertEquals(current.lyricsShareSession, store.state.value.lyricsShareSession)
        assertEquals(current.shareCardModel, store.state.value.shareCardModel)
        assertContentEquals(current.sharePreviewBytes, store.state.value.sharePreviewBytes)
        assertEquals(current.lyricsShareCopyRequestId, store.state.value.lyricsShareCopyRequestId)
        assertTrue(store.state.value.isShareCopying)
        assertNull(store.state.value.shareMessage)
        if (!fail) preferences.calls.last().succeed(Unit)
        drain()
        assertEquals("Serif", preferences.selectedLyricsShareFontKey.value)
        platform.copies.single().succeed(Unit)
        drain()
        assertEquals(listOf("text:${if (changeTrack) "next" else "original"} second"), platform.copied)
    }

    @Test fun storeShutdownCancelsPreferenceWriteAndDoesNotStartNextChoice() = withLyricsShareLifecycle {
        open()
        loadFonts()
        val before = preferences.calls.size
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        dispatch(PlayerIntent.LyricsShareFontChanged("Serif"))
        drain()
        val pending = preferences.calls.last()
        assertEquals(before + 1, preferences.calls.size)
        shutdown()
        assertTrue(pending.cancelled)
        pending.succeed(Unit)
        drain()
        assertEquals(before + 1, preferences.calls.size)
        assertEquals("Serif", preferences.selectedLyricsShareFontKey.value)
    }

    @Test fun automaticFontFallbackWaitsForAllPreviouslySubmittedChoices() = withLyricsShareLifecycle {
        open()
        loadFonts()
        val before = preferences.calls.size
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        dispatch(PlayerIntent.LyricsShareFontChanged("Serif"))
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        dispatch(PlayerIntent.InvalidateLyricsShareFontCache)
        platform.fontsAutomatically = false
        loadFonts()
        platform.fonts.last().succeed(listOf(LyricsShareFontOption("Fallback", "Fallback")))
        drain()
        val expected = listOf("Another", "Serif", "Another", "Fallback")
        expected.indices.forEach { index ->
            assertEquals(expected.take(index + 1), preferences.calls.drop(before).map { it.input })
            preferences.calls.last().succeed(Unit)
            drain()
        }
        assertEquals("Fallback", preferences.selectedLyricsShareFontKey.value)
        assertEquals("Fallback", store.state.value.selectedLyricsShareFontKey)
        assertTrue(store.state.value.hasFreshSharePreview)
    }

    @Test fun closingShareCancelsQueuedNormalizationButKeepsCommittedFontChoice() = withLyricsShareLifecycle {
        open()
        loadFonts()
        val before = preferences.calls.size
        preferences.writeAutomatically = false
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        dispatch(PlayerIntent.InvalidateLyricsShareFontCache)
        platform.fontsAutomatically = false
        loadFonts()
        platform.fonts.last().succeed(listOf(LyricsShareFontOption("Fallback", "Fallback")))
        drain()
        closeShare()
        preferences.calls.last().succeed(Unit)
        drain()
        assertEquals(listOf("Another"), preferences.calls.drop(before).map { it.input })
        assertEquals("Another", preferences.selectedLyricsShareFontKey.value)
        assertNull(store.state.value.lyricsShareSession)
        assertFalse(store.state.value.isLyricsShareFontsLoading)
    }

    @Test fun cancellingActiveNormalizationDoesNotStopLaterCommittedFontWrites() = withLyricsShareLifecycle {
        open()
        preferences.writeAutomatically = false
        platform.fontsAutomatically = false
        loadFonts()
        platform.fonts.last().succeed(listOf(LyricsShareFontOption("Fallback", "Fallback")))
        drain()
        val automaticWrite = preferences.calls.last()
        closeShare()
        assertTrue(automaticWrite.cancelled)
        platform.fontsAutomatically = true
        preferences.writeAutomatically = true
        open()
        loadFonts()
        dispatch(PlayerIntent.LyricsShareFontChanged("Another"))
        drain()
        assertEquals("Another", preferences.selectedLyricsShareFontKey.value)
        assertEquals("Another", store.state.value.selectedLyricsShareFontKey)
        assertTrue(store.state.value.hasFreshSharePreview)
    }
}
