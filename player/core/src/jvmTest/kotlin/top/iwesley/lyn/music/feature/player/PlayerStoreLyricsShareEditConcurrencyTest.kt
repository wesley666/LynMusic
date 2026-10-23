package top.iwesley.lyn.music.feature.player

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.core.mvi.BaseStore

class PlayerStoreLyricsShareEditConcurrencyTest {
    @Test fun oldPreviewSuccessCannotUndoImmediateClearDuringCasRetry() = racePreview(Edit.Clear, fail = false)
    @Test fun oldPreviewFailureCannotUndoImmediateClearDuringCasRetry() = racePreview(Edit.Clear, fail = true)
    @Test fun oldPreviewCannotPublishAsNewSelectionDuringCasRetry() = racePreview(Edit.Selection, fail = false)
    @Test fun oldPreviewFailureCannotReplaceNewTemplateDuringCasRetry() = racePreview(Edit.Template, fail = true)
    @Test fun oldPreviewCannotRegainOwnershipAfterSelectionChangesBack() = racePreview(Edit.SelectionRoundTrip, fail = false)
    @Test fun oldPreviewFailureCannotRegainOwnershipAfterSelectionChangesBack() = racePreview(Edit.SelectionRoundTrip, fail = true)
    @Test fun oldPreviewSuccessCannotUndoImmediateFontEditDuringCasRetry() = racePreview(Edit.Font, fail = false)
    @Test fun oldPreviewFailureCannotReplaceNewFontDuringCasRetry() = racePreview(Edit.Font, fail = true)
    @Test fun oldPreviewCannotRegainOwnershipAfterFontChangesBack() = racePreview(Edit.FontRoundTrip, fail = false)

    private enum class Edit { Clear, Selection, Template, SelectionRoundTrip, Font, FontRoundTrip }

    private fun racePreview(edit: Edit, fail: Boolean) = runBlocking {
        val worker = AtomicReference<Thread>()
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "share-edit-race-worker").also(worker::set)
        }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val oldStarted = CompletableDeferred<Unit>()
            val oldResult = CompletableDeferred<Result<ByteArray>>()
            val newResult = CompletableDeferred<Unit>()
            val initialBytes = "initial preview".encodeToByteArray()
            var renderCount = 0
            val service = object : LyricsSharePlatformService by CopyTestShareService() {
                override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = when (renderCount++) {
                    0 -> Result.success(initialBytes)
                    1 -> { oldStarted.complete(Unit); oldResult.await() }
                    else -> { newResult.await(); Result.success(model.copyTestBytes()) }
                }
            }
            val track = Track("original", "source", "Original", mediaLocator = "", relativePath = "")
            val preferences = object : LyricsShareFontPreferencesStore {
                override val selectedLyricsShareFontKey = MutableStateFlow<String?>("Serif")
                override suspend fun setSelectedLyricsShareFontKey(value: String?) { selectedLyricsShareFontKey.value = value }
            }
            val store = PlayerStore(
                CommitPlaybackRepository(PlaybackSnapshot(queue = listOf(track), currentIndex = 0)),
                CopyTestLyricsRepository(), scope, lyricsSharePlatformService = service,
                lyricsShareFontPreferencesStore = preferences,
            )
            withTimeout(5_000) { store.state.first { it.lyrics != null } }
            store.dispatch(PlayerIntent.BeginLyricsShare)
            withTimeout(5_000) { store.state.first { it.hasFreshSharePreview } }
            if (edit == Edit.Font || edit == Edit.FontRoundTrip) {
                store.dispatch(PlayerIntent.RequestLyricsShareFonts)
                withTimeout(5_000) { store.state.first { it.availableLyricsShareFonts.isNotEmpty() } }
            }
            store.dispatch(PlayerIntent.BuildLyricsSharePreview)
            withTimeout(5_000) { oldStarted.await() }
            val oldRequest = assertNotNull(store.state.value.lyricsSharePreviewRequestId)
            val field = BaseStore::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
            // Freeze the old completion between its transform and CAS. Synchronous UI edits can
            // reenter the monitor; the worker must recheck ownership when its CAS subsequently fails.
            synchronized(field.get(store)) {
                oldResult.complete(if (fail) Result.failure(IllegalStateException("old render failed"))
                    else Result.success("obsolete preview".encodeToByteArray()))
                val deadline = System.nanoTime() + 5_000_000_000L
                while (true) {
                    val thread = worker.get()
                    val frames = thread.stackTrace
                    if (thread.state == Thread.State.BLOCKED &&
                        frames.any { it.className.endsWith("StateFlowImpl") && it.methodName == "updateState" } &&
                        frames.any { it.className.endsWith("PlayerLyricsShare") && it.methodName.contains("rebuildLyricsSharePreview") }
                    ) break
                    check(System.nanoTime() < deadline) { "Worker did not reach preview completion CAS" }
                    Thread.sleep(1)
                }
                when (edit) {
                    Edit.Clear -> store.dispatch(PlayerIntent.ClearLyricsSelection)
                    Edit.Selection -> store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
                    Edit.Template -> store.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
                    Edit.Font -> store.dispatch(PlayerIntent.LyricsShareFontChanged("Another font"))
                    Edit.FontRoundTrip -> {
                        store.dispatch(PlayerIntent.LyricsShareFontChanged("Another font"))
                        store.dispatch(PlayerIntent.LyricsShareFontChanged("Serif"))
                    }
                    Edit.SelectionRoundTrip -> {
                        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
                        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
                    }
                }
                // The replacement now publishes its request synchronously with the edit.
                assertNotEquals(oldRequest, store.state.value.lyricsSharePreviewRequestId)
            }
            // Drain previously queued work on the sole worker. New preview rendering remains gated.
            withContext(dispatcher) { }
            assertNull(store.state.value.sharePreviewError)
            if (edit == Edit.Clear) {
                assertTrue(store.state.value.selectedLyricsLineIndices.isEmpty())
                assertNull(store.state.value.shareCardModel)
                assertNull(store.state.value.sharePreviewBytes)
                assertNull(store.state.value.lyricsSharePreviewRequestId)
                assertFalse(store.state.value.isShareRendering)
                assertEquals(2, renderCount)
            } else {
                assertContentEquals(initialBytes, store.state.value.sharePreviewBytes)
                assertTrue(store.state.value.isShareRendering)
                assertNotEquals(oldRequest, assertNotNull(store.state.value.lyricsSharePreviewRequestId))
                val currentModel = assertNotNull(store.state.value.shareCardModel)
                assertEquals(if (edit == Edit.Selection) listOf("original first", "original second")
                    else listOf("original first"), currentModel.lyricsLines)
                if (edit == Edit.Template) assertEquals(LyricsShareTemplate.ARTWORK_TINT, currentModel.template)
                if (edit == Edit.Font) assertEquals("Another font", currentModel.fontKey)
                if (edit == Edit.FontRoundTrip) assertEquals("Serif", currentModel.fontKey)
                newResult.complete(Unit)
                withTimeout(5_000) { store.state.first { !it.isShareRendering } }
                assertContentEquals(currentModel.copyTestBytes(), store.state.value.sharePreviewBytes)
                assertNull(store.state.value.lyricsSharePreviewRequestId)
                assertTrue(store.state.value.hasFreshSharePreview)
            }
            Unit
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            dispatcher.close()
        }
    }
}
