package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.resources.*

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsShareExportExecutorTest {
    @Test fun mixedCopiesWaitForImageRenderAndClipboardCompletion() = withExports {
        platform.renderAutomatically = false
        val image = imageCopy(1)
        executor.submitCopy(image)
        executor.submitCopy(textCopy(2))
        drain()
        assertEquals(1, platform.renders.size)
        assertTrue(platform.copies.isEmpty())
        platform.renders.single().let { it.succeed(it.input.lifecycleImage()) }
        drain()
        assertEquals(1, platform.copies.size)
        assertTrue(platform.copies.single().input.startsWith("image:"))
        platform.copies.single().succeed(Unit)
        drain()
        assertEquals(listOf(1L), copied.map { it.first.id })
        assertEquals("text:Text 2", platform.copies.last().input)
        platform.copies.last().succeed(Unit)
        drain()
        assertEquals(listOf(1L, 2L), copied.map { it.first.id })
        assertEquals(listOf(platform.copies.first().input, "text:Text 2"), platform.copied)
    }

    @Test fun savesWaitForPreviousRenderAndDialog() = withExports {
        platform.renderAutomatically = false
        val first = imageSave(1)
        val second = imageSave(2)
        executor.submitSave(first)
        executor.submitSave(second)
        drain()
        assertEquals(listOf(first.model), platform.renders.map { it.input })
        platform.renders.single().let { it.succeed(it.input.lifecycleImage()) }
        drain()
        assertEquals(1, platform.saves.size)
        assertEquals(1, platform.renders.size)
        platform.saves.single().succeed(savedShareImage())
        drain()
        assertEquals(listOf(first.model, second.model), platform.renders.map { it.input })
        platform.renders.last().let { it.succeed(it.input.lifecycleImage()) }
        drain()
        platform.saves.last().succeed(savedShareImage())
        drain()
        assertEquals(listOf(first, second), saved.map { it.first })
        assertEquals(listOf(first, second), finished)
        assertEquals(listOf(first.suggestedName, second.suggestedName), platform.saved.map { it.suggestedName })
    }

    @Test fun saveAndCopyDoNotBlockEachOther() = withExports {
        executor.submitSave(imageSave(1))
        executor.submitCopy(textCopy(2))
        executor.submitCopy(textCopy(3))
        drain()
        platform.copies.single().succeed(Unit)
        drain()
        assertEquals("text:Text 3", platform.copies.last().input)
        assertTrue(saved.isEmpty(), "Copy advanced while the save dialog remained open")
        executor.submitSave(imageSave(4))
        platform.saves.single().succeed(savedShareImage())
        drain()
        platform.saves.last().succeed(savedShareImage())
        drain()
        assertEquals(listOf(1L, 4L), saved.map { it.first.id })
        assertEquals(listOf(2L), copied.map { it.first.id }, "Saves advanced while the clipboard remained busy")
        platform.copies.last().succeed(Unit)
        drain()
        assertEquals(listOf(2L, 3L), copied.map { it.first.id })
    }

    @Test fun cachedImageBypassesRenderAndUsesCapturedSaveName() = withExports {
        val bytes = "captured image".encodeToByteArray()
        val copy = imageCopy(1, bytes)
        val save = imageSave(2, bytes).copy(suggestedName = "captured-name.png")
        executor.submitCopy(copy)
        executor.submitSave(save)
        drain()
        assertTrue(platform.renders.isEmpty())
        assertEquals("image:captured image", platform.copies.single().input)
        assertContentEquals(bytes, platform.saves.single().input.bytes)
        assertEquals("captured-name.png", platform.saves.single().input.suggestedName)
        platform.copies.single().succeed(Unit)
        platform.saves.single().succeed(savedShareImage())
        drain()
        assertEquals(copy, copied.single().first)
        assertEquals(save, saved.single().first)
    }

    @Test fun failedCopyRenderReportsOnceAndNextCopyContinues() = renderFailure(copy = true, throws = false)
    @Test fun thrownCopyRenderReportsOnceAndNextCopyContinues() = renderFailure(copy = true, throws = true)
    @Test fun failedSaveRenderReportsOnceAndNextSaveContinues() = renderFailure(copy = false, throws = false)
    @Test fun thrownSaveRenderReportsOnceAndNextSaveContinues() = renderFailure(copy = false, throws = true)

    private fun renderFailure(copy: Boolean, throws: Boolean) = withExports {
        platform.renderAutomatically = false
        if (throws) platform.renderException = IllegalStateException("Render threw")
        if (copy) executor.submitCopy(imageCopy(1)) else executor.submitSave(imageSave(1))
        drain()
        if (!throws) { platform.renders.single().fail(); drain() }
        assertTrue(platform.copies.isEmpty())
        assertTrue(platform.saves.isEmpty())
        val failed = if (copy) copied.single().second else saved.single().second
        assertEquals(uiText(Res.string.lyrics_share_image_generation_failed,
            IllegalStateException(if (throws) "Render threw" else "Controlled failure").uiErrorDetail()), failed)
        platform.renderException = null
        platform.renderAutomatically = true
        if (copy) executor.submitCopy(imageCopy(2)) else executor.submitSave(imageSave(2))
        drain()
        if (copy) platform.copies.single().succeed(Unit) else platform.saves.single().succeed(savedShareImage())
        drain()
        assertEquals(listOf(1L, 2L), if (copy) copied.map { it.first.id } else saved.map { it.first.id })
        if (!copy) assertEquals(listOf(1L, 2L), finished.map { it.id })
    }

    @Test fun failedClipboardWriteDoesNotStopNextCopy() = withExports {
        executor.submitCopy(textCopy(1))
        executor.submitCopy(textCopy(2))
        drain()
        platform.copies.single().fail()
        drain()
        assertEquals(listOf(1L), copied.map { it.first.id })
        platform.copies.last().succeed(Unit)
        drain()
        assertEquals(listOf(1L, 2L), copied.map { it.first.id })
        assertEquals(listOf("text:Text 2"), platform.copied)
    }

    @Test fun thrownClipboardWriteDoesNotStopNextCopy() = withExports(transform = { delegate ->
        object : LyricsSharePlatformService by delegate {
            override suspend fun copyText(text: String): Result<Unit> {
                if (text == "Text 1") error("Clipboard threw")
                return delegate.copyText(text)
            }
        }
    }) {
        executor.submitCopy(textCopy(1))
        executor.submitCopy(textCopy(2))
        drain()
        assertEquals(listOf(1L), copied.map { it.first.id })
        assertEquals("text:Text 2", platform.copies.single().input)
        platform.copies.single().succeed(Unit)
        drain()
        assertEquals(listOf(1L, 2L), copied.map { it.first.id })
    }

    @Test fun cancelledSaveDialogDoesNotStopNextSave() = withExports {
        executor.submitSave(imageSave(1))
        executor.submitSave(imageSave(2))
        drain()
        val cancelled = uiText(Res.string.lyrics_image_save_cancelled)
        platform.saves.single().fail(UiTextException(cancelled))
        drain()
        assertEquals(uiText(Res.string.lyrics_share_save_image_failed, cancelled), saved.single().second)
        assertEquals(listOf(1L), finished.map { it.id })
        platform.saves.last().succeed(savedShareImage())
        drain()
        assertEquals(listOf(1L, 2L), saved.map { it.first.id })
        assertEquals(listOf(1L, 2L), finished.map { it.id })
    }

    @Test fun thrownSaveDoesNotStopNextSave() = withExports(transform = { delegate ->
        object : LyricsSharePlatformService by delegate {
            override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> {
                if (suggestedName.startsWith("Track 1")) error("Save threw")
                return delegate.saveImage(pngBytes, suggestedName)
            }
        }
    }) {
        executor.submitSave(imageSave(1))
        executor.submitSave(imageSave(2))
        drain()
        assertEquals(listOf(1L), saved.map { it.first.id })
        assertEquals(listOf(1L), finished.map { it.id })
        platform.saves.single().succeed(savedShareImage())
        drain()
        assertEquals(listOf(1L, 2L), saved.map { it.first.id })
        assertEquals(listOf(1L, 2L), finished.map { it.id })
    }

    @Test fun platformCancellationWithoutStoreDisposalDoesNotKillQueues() = withExports {
        executor.submitCopy(textCopy(1))
        executor.submitCopy(textCopy(2))
        executor.submitSave(imageSave(3))
        executor.submitSave(imageSave(4))
        drain()
        platform.copies.single().fail(CancellationException("Operation cancelled"))
        platform.saves.single().fail(CancellationException("Operation cancelled"))
        drain()
        assertEquals(listOf(1L), copied.map { it.first.id })
        assertEquals(listOf(3L), saved.map { it.first.id })
        platform.copies.last().succeed(Unit)
        platform.saves.last().succeed(savedShareImage())
        drain()
        assertEquals(listOf(1L, 2L), copied.map { it.first.id })
        assertEquals(listOf(3L, 4L), saved.map { it.first.id })
    }

    @Test fun disposalAfterNonCooperativeRenderDoesNotStartWritesOrQueuedWork() = withExports {
        platform.renderAutomatically = false
        platform.ignoreRenderCancellation = true
        executor.submitCopy(imageCopy(1))
        executor.submitCopy(textCopy(2))
        executor.submitSave(imageSave(3))
        executor.submitSave(imageSave(4))
        drain()
        assertEquals(2, platform.renders.size)
        scope.cancel()
        platform.renders.forEach { it.succeed(it.input.lifecycleImage()) }
        drain()
        assertTrue(platform.copies.isEmpty())
        assertTrue(platform.saves.isEmpty())
        assertTrue(copied.isEmpty())
        assertTrue(saved.isEmpty())
        assertEquals(listOf(3L), finished.map { it.id })
        executor.submitCopy(textCopy(5))
        executor.submitSave(imageSave(6))
        drain()
        assertEquals(2, platform.renders.size)
        assertTrue(platform.copies.isEmpty())
        assertTrue(platform.saves.isEmpty())
    }

    @Test fun disposalDuringNonCooperativeWritesRejectsLateFeedbackAndQueuedWrites() = withExports(transform = { delegate ->
        object : LyricsSharePlatformService by delegate {
            override suspend fun copyText(text: String) = withContext(NonCancellable) { delegate.copyText(text) }
            override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String) =
                withContext(NonCancellable) { delegate.saveImage(pngBytes, suggestedName) }
        }
    }) {
        executor.submitCopy(textCopy(1))
        executor.submitCopy(textCopy(2))
        executor.submitSave(imageSave(3))
        executor.submitSave(imageSave(4))
        drain()
        scope.cancel()
        platform.copies.single().succeed(Unit)
        platform.saves.single().succeed(savedShareImage())
        drain()
        // The already-started external writes may finish; there must be no feedback or next write.
        assertEquals(1, platform.copies.size)
        assertEquals(1, platform.saves.size)
        assertTrue(copied.isEmpty())
        assertTrue(saved.isEmpty())
        assertEquals(listOf(3L), finished.map { it.id })
    }

    @Test fun disposalBeforeWorkersStartDoesNotExecuteQueuedRequests() = withExports {
        executor.submitCopy(textCopy(1))
        executor.submitSave(imageSave(2))
        scope.cancel()
        drain()
        assertTrue(platform.renders.isEmpty())
        assertTrue(platform.copies.isEmpty())
        assertTrue(platform.saves.isEmpty())
        assertTrue(copied.isEmpty())
        assertTrue(saved.isEmpty())
    }

    private fun withExports(
        transform: (LifecycleSharePlatform) -> LyricsSharePlatformService = { it },
        test: ExportFixture.() -> Unit,
    ) = runTest {
        val fixture = ExportFixture(this, transform)
        try { fixture.test() } finally {
            fixture.scope.cancel()
            fixture.platform.releasePending()
            fixture.drain()
        }
    }

    private class ExportFixture(
        private val test: TestScope,
        transform: (LifecycleSharePlatform) -> LyricsSharePlatformService,
    ) {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler))
        val platform = LifecycleSharePlatform()
        val copied = mutableListOf<Pair<SubmittedLyricsCopy, UiText>>()
        val saved = mutableListOf<Pair<SubmittedImageSave, UiText>>()
        val finished = mutableListOf<SubmittedImageSave>()
        val executor = LyricsShareExportExecutor(scope, transform(platform),
            onCopyCompleted = { request, message -> copied += request to message },
            onSaveCompleted = { request, message -> saved += request to message },
            onSaveFinished = { finished += it })
        fun drain() = test.testScheduler.advanceUntilIdle()
    }

    private fun track(id: Long) = Track("track$id", "source", "Track $id", mediaLocator = "", relativePath = "")
    private fun model(id: Long) = LyricsShareCardModel(title = "Title $id", lyricsLines = listOf("Line $id"))
    private fun imageSave(id: Long, bytes: ByteArray? = null) = SubmittedImageSave(
        id, PlayerTaskSession(id, track(id).id), track(id), model(id), bytes, buildLyricsShareSuggestedName(track(id).title))
    private fun imageCopy(id: Long, bytes: ByteArray? = null) = SubmittedLyricsCopy(
        id, PlayerTaskSession(id, track(id).id), track(id), SubmittedLyricsCopyContent.Image(model(id), bytes))
    private fun textCopy(id: Long) = SubmittedLyricsCopy(
        id, PlayerTaskSession(id, track(id).id), track(id), SubmittedLyricsCopyContent.Text("Text $id"))
}
