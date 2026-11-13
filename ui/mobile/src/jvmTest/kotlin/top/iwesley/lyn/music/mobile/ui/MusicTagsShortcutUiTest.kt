@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.runtime.InternalComposeApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import javax.swing.SwingUtilities
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.details.*
import top.iwesley.lyn.music.feature.tags.*
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

/** Exercises the real page and NavDisplay; Store states are supplied explicitly, without file I/O. */
class MusicTagsShortcutUiTest {
    @Test fun initialSelectionWaitsForContentReadRefreshAndSaveAndIsSentOnlyOnce() = onUiThread {
        Fixture().use { ui ->
            ui.open()
            assertTrue(ui.intents.isEmpty())
            ui.origin(isLoadingSelected = true)
            assertTrue(ui.intents.isEmpty())
            ui.state = ui.state.copy(isLoadingSelected = false, isRefreshing = true); ui.render()
            assertTrue(ui.intents.isEmpty())
            ui.state = ui.state.copy(isRefreshing = false, isSaving = true); ui.render()
            assertTrue(ui.intents.isEmpty())
            ui.state = ui.state.copy(isSaving = false); ui.render()
            assertEquals(listOf(MusicTagsIntent.SelectTrack("b")), ui.intents.filterIsInstance<MusicTagsIntent.SelectTrack>())
            assertFalse(ui.hasFormValue("Saved A"))
            ui.beginTargetRead()
            assertFalse(ui.hasFormValue("Saved A"))
            assertFalse(ui.hasFormValue("Saved B"))
            ui.finishTargetRead()
            assertTrue(ui.hasFormValue("Saved B"))
            ui.state = ui.state.copy(message = UiText.Raw("Unrelated message")); ui.render()
            assertEquals(1, ui.intents.count { it is MusicTagsIntent.SelectTrack })
            assertFalse(ui.intents.any { it is MusicTagsIntent.ActivateTrack })
            assertEquals(0, ui.plays)
        }
    }

    @Test fun cancellingInitialSwitchDismissesThePendingActionBeforeReturningAndPreservesOldDraft() = onUiThread {
        Fixture().use { ui ->
            ui.origin(dirty = true); ui.open()
            ui.pendingSwitch()
            assertFalse(ui.hasFormValue("A unsaved draft"))
            ui.click("Continue editing")
            assertEquals(MusicTagsIntent.DismissDiscardSelection, ui.intents.last())
            assertEquals("tags", ui.navigation.top.kind)
            ui.state = ui.state.copy(showDiscardChangesDialog = false, pendingTrackAction = null); ui.render()
            assertFalse(ui.navigation.hasDetails)
            assertEquals("a", ui.state.selectedTrackId)
            assertEquals("A unsaved draft", ui.state.draft.title)
            assertTrue(ui.state.isDirty)
            assertFalse(ui.intents.contains(MusicTagsIntent.ResetDraft))
        }
    }

    @Test fun confirmingInitialSwitchWaitsForTheRequestedTagsWithoutShowingOldFields() = onUiThread {
        for (width in listOf(360, 1120)) Fixture(width).use { ui ->
            ui.origin(dirty = true); ui.open(); ui.pendingSwitch()
            ui.click("Discard and switch")
            assertEquals(MusicTagsIntent.ConfirmDiscardSelection, ui.intents.last())
            assertFalse(ui.hasFormValue("A unsaved draft"))
            ui.beginTargetRead()
            assertFalse(ui.hasFormValue("Saved A"))
            ui.finishTargetRead()
            assertTrue(ui.hasFormValue("Saved B"))
            assertEquals(0, ui.plays)
        }
    }

    @Test fun enteringTheSameSongKeepsItsDraftAndTheOrdinaryTagsEntryKeepsItsExistingLayout() = onUiThread {
        Fixture().use { ui ->
            ui.origin(); ui.beginTargetRead(); ui.finishTargetRead()
            ui.state = ui.state.copy(draft = MusicTagsDraft(title = "B unsaved draft"), isDirty = true)
            ui.open()
            assertTrue(ui.hasFormValue("B unsaved draft"))
            assertFalse(ui.visible("Discard unsaved changes?"))
            assertEquals(listOf(MusicTagsIntent.SelectTrack("b")), ui.intents.filterIsInstance<MusicTagsIntent.SelectTrack>())
        }
        Fixture(ordinaryTags = true).use { ui ->
            ui.origin()
            assertTrue(ui.visible("Music tags"))
            assertTrue(ui.intents.none { it is MusicTagsIntent.SelectTrack })
            assertFalse(ui.visible("Back"))
        }
    }

    @Test fun everyReturnPathUsesTheDiscardDialogAndRestoresTheOriginAndScrollPosition() = onUiThread {
        for (width in listOf(360, 1120)) for (action in ReturnAction.entries) Fixture(width).use { ui ->
            ui.origin(); ui.scope.launch { ui.rootList.scrollToItem(25, 13) }; ui.render()
            ui.open(); ui.beginTargetRead(); ui.finishTargetRead()
            ui.state = ui.state.copy(draft = MusicTagsDraft(title = "B unsaved draft"), isDirty = true); ui.render()
            ui.requestReturn(action)
            assertTrue(ui.visible("Discard unsaved changes?"))
            ui.click("Continue editing")
            assertEquals("tags", ui.navigation.top.kind)
            assertTrue(ui.hasFormValue("B unsaved draft"))
            ui.requestReturn(action)
            ui.click("Discard and go back")
            assertEquals(MusicTagsIntent.ResetDraft, ui.intents.last())
            assertEquals("tags", ui.navigation.top.kind) // Wait for the Store's reset acknowledgement.
            ui.state = ui.state.copy(draft = MusicTagsDraft(title = "Saved B"), isDirty = false); ui.render()
            assertFalse(ui.navigation.hasDetails)
            assertEquals(25, ui.rootList.firstVisibleItemIndex)
            assertEquals(13, ui.rootList.firstVisibleItemScrollOffset)
            assertTrue(ui.visible("Origin filter: unchanged"))
            assertEquals(0, ui.plays)
        }
    }

    @Test fun returningBeforeInitialContentLoadsDoesNotSendASelection() = onUiThread {
        Fixture().use { ui ->
            ui.open()
            ui.requestReturn(ReturnAction.System)
            assertFalse(ui.navigation.hasDetails)
            assertTrue(ui.intents.isEmpty())
        }
    }

    @Test fun everyReturnPathLeavesACleanTargetWithoutResettingItsDraft() = onUiThread {
        for (action in ReturnAction.entries) Fixture().use { ui ->
            ui.origin(); ui.open(); ui.beginTargetRead(); ui.finishTargetRead()
            ui.requestReturn(action)
            assertFalse(ui.navigation.hasDetails)
            assertFalse(ui.intents.contains(MusicTagsIntent.ResetDraft))
            assertEquals("Saved B", ui.state.draft.title)
        }
    }

    @Test fun backDuringSavingWaitsForTheExistingSaveResult() = onUiThread {
        Fixture().use { ui ->
            ui.origin(); ui.open(); ui.beginTargetRead(); ui.finishTargetRead()
            ui.state = ui.state.copy(isSaving = true, isDirty = true); ui.render()
            ui.requestReturn(ReturnAction.System)
            assertEquals("tags", ui.navigation.top.kind)
            assertFalse(ui.visible("Discard unsaved changes?"))
            ui.state = ui.state.copy(isSaving = false, isDirty = false); ui.render()
            assertFalse(ui.navigation.hasDetails)
            assertFalse(ui.intents.contains(MusicTagsIntent.ResetDraft))
        }
    }

    @Test fun missingTargetAndReadFailureNeverExposeThePreviousSongsFields() = onUiThread {
        Fixture().use { ui ->
            ui.origin(dirty = true)
            ui.state = ui.state.copy(tracks = listOf(ui.a)); ui.open()
            assertTrue(ui.visible("Unavailable"))
            assertFalse(ui.hasFormValue("A unsaved draft"))
            assertTrue(ui.intents.isEmpty())
            ui.requestReturn(ReturnAction.Button)
            assertFalse(ui.navigation.hasDetails)
            assertTrue(ui.state.isDirty)
        }
        Fixture().use { ui ->
            ui.origin(); ui.open(); ui.beginTargetRead()
            ui.state = ui.state.copy(isLoadingSelected = false, message = UiText.Raw("Read failed"), canWriteSelected = true)
            ui.render()
            assertTrue(ui.visible("Read failed"))
            assertFalse(ui.hasFormValue("Saved A"))
            assertFalse(ui.enabled("Save"))
        }
    }

    @Test fun readOnlyTargetStillShowsItsTagsWithoutEnablingSave() = onUiThread {
        Fixture().use { ui ->
            ui.origin(); ui.open(); ui.beginTargetRead(); ui.finishTargetRead()
            ui.state = ui.state.copy(canWriteSelected = false); ui.render()
            assertTrue(ui.hasFormValue("Saved B"))
            assertFalse(ui.enabled("Save"))
            assertFalse(ui.intents.contains(MusicTagsIntent.Save))
        }
    }

    @Test fun onlyTheForegroundPageShowsTheSwitchConfirmation() = onUiThread {
        Fixture(ordinaryTags = true).use { ui ->
            ui.origin(dirty = true); ui.open(); ui.pendingSwitch()
            assertEquals(1, ui.count("Discard unsaved changes?"))
            ui.click("Continue editing")
            assertEquals(1, ui.intents.count { it == MusicTagsIntent.DismissDiscardSelection })
        }
    }

    private enum class ReturnAction { Button, Escape, System }
    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(width: Int = 360, val ordinaryTags: Boolean = false) : AutoCloseable {
        val a = Track("a", "local", "Row A", mediaLocator = "/a.flac", relativePath = "a.flac")
        val b = Track("b", "local", "Row B", mediaLocator = "/b.flac", relativePath = "b.flac")
        var state by mutableStateOf(MusicTagsState())
        val intents = mutableListOf<MusicTagsIntent>()
        var plays = 0
        val scheduler = TestCoroutineScheduler()
        lateinit var navigation: MusicDetailNavigation
        lateinit var scope: CoroutineScope
        lateinit var rootList: LazyListState
        val effects = MutableSharedFlow<MusicTagsEffect>()
        private var frame = 0L
        private val back = object : NavigationEventInput() { fun complete() = dispatchOnBackCompleted() }
        private val scene = ImageComposeScene(width, 1000, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
            DisposableEffect(dispatcher) { dispatcher.addInput(back); onDispose { dispatcher.removeInput(back) } }
            scope = rememberCoroutineScope()
            navigation = rememberMusicDetailNavigation(MusicDetailQueries { flowOf(MusicDetailData("Unused")) }) {}
            CompositionLocalProvider(
                LocalMusicDetailNavigation provides navigation,
                LocalPlatformDescriptor provides PlatformDescriptor(ANDROID_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false)),
            ) {
                ProvideUiResourceLanguage("en") { LynMusicTheme {
                    MusicDetailHost(onPlayerIntent = { plays++ }, musicTags = { route ->
                        MusicTagsTab(state, effects, { intents += it }, { plays++ },
                            targetTrackId = route.entityId, targetSourceId = route.sourceId,
                            onReturn = { navigation.pop(route.entryId) })
                    }) {
                        if (ordinaryTags) MusicTagsTab(state, effects, { intents += it }, { plays++ })
                        else {
                            rootList = rememberLazyListState()
                            Column {
                                Text("Origin filter: unchanged")
                                LazyColumn(Modifier.fillMaxSize(), state = rootList) {
                                    items(100) { Text("Origin $it", Modifier.fillMaxWidth().height(45.dp)) }
                                }
                            }
                        }
                    }
                } }
            }
        }
        init { render() }
        fun origin(dirty: Boolean = false, isLoadingSelected: Boolean = false) {
            state = MusicTagsState(isLoadingContent = false, tracks = listOf(a, b), selectedTrackId = a.id,
                selectedSnapshot = AudioTagSnapshot("Saved A"),
                draft = MusicTagsDraft(title = if (dirty) "A unsaved draft" else "Saved A"),
                isDirty = dirty, canEditSelected = true, canWriteSelected = true, isLoadingSelected = isLoadingSelected)
            render()
        }
        fun open() { navigation.openMusicTags(b.id, b.sourceId); render() }
        fun pendingSwitch() {
            state = state.copy(pendingTrackAction = MusicTagsPendingTrackAction.SelectOnly(b.id), showDiscardChangesDialog = true)
            render()
        }
        fun beginTargetRead() {
            state = state.copy(selectedTrackId = b.id, selectedSnapshot = null, draft = MusicTagsDraft(), isDirty = false,
                isLoadingSelected = true, pendingTrackAction = null, showDiscardChangesDialog = false)
            render()
        }
        fun finishTargetRead() {
            state = state.copy(isLoadingSelected = false, selectedSnapshot = AudioTagSnapshot("Saved B"),
                draft = MusicTagsDraft(title = "Saved B"), canEditSelected = true, canWriteSelected = true)
            render()
        }
        fun requestReturn(action: ReturnAction) {
            when (action) {
                ReturnAction.Button -> click("Back")
                ReturnAction.Escape -> {
                    scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
                    scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
                }
                ReturnAction.System -> back.complete()
            }
            render()
        }
        fun render() { repeat(40) { scheduler.runCurrent(); Snapshot.sendApplyNotifications(); scene.render(++frame * 16_666_667L).close() } }
        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        }
        private fun matches(node: SemanticsNode, label: String) = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
        fun count(label: String) = nodes().count { matches(it, label) }
        fun visible(label: String) = count(label) > 0
        fun hasFormValue(value: String) = nodes().any { it.config.getOrNull(SemanticsProperties.EditableText)?.text == value }
        fun enabled(label: String): Boolean {
            var node = nodes().lastOrNull { matches(it, label) } ?: return false
            while (true) {
                if (node.config.contains(SemanticsProperties.Disabled)) return false
                if (node.config.contains(SemanticsActions.OnClick)) return true
                node = node.parent ?: return false
            }
        }
        fun click(label: String) {
            val node = nodes().lastOrNull { matches(it, label) } ?: error("Missing $label")
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center)
            render()
        }
        override fun close() = scene.close()
    }
}
