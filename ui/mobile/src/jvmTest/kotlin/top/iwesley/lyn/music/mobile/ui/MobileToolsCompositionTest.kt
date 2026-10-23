@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*
import kotlinx.coroutines.flow.emptyFlow
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.importing.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.feature.settings.*
import top.iwesley.lyn.music.feature.tags.*
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class MobileToolsCompositionTest {
    @Test fun `settings selection works in each supported layout`() = onUiThread {
        for (width in listOf(480, 1280)) Fixture(Page.Settings, width).use { ui ->
            ui.click("Theme")
            ui.click("Classic black")
            assertEquals(SettingsIntent.ThemeSelected(AppThemeId.Classic), ui.settingsIntents.last())
            ui.capture("settings-$width")
        }
    }
    @Test fun `source creation edits and tests the draft before dismissal`() = onUiThread {
        Fixture(Page.SourceCreate).use { ui ->
            ui.edit("https://before.invalid", "https://after.invalid")
            assertEquals(ImportIntent.WebDavRootUrlChanged("https://after.invalid"), ui.importIntents.last())
            ui.click("Test connection")
            assertEquals(ImportIntent.TestWebDavSource, ui.importIntents.last())
            ui.capture("source-create")
            ui.click("Cancel")
            assertEquals(ImportIntent.DismissRemoteSourceCreator, ui.importIntents.last())
            assertNull(ui.importState.creatingSourceType)
        }
    }
    @Test fun `source editing keeps its independent dismiss action`() = onUiThread {
        Fixture(Page.SourceEdit).use { ui ->
            ui.click("Test connection")
            assertEquals(ImportIntent.TestRemoteSource, ui.importIntents.last())
            ui.capture("source-edit")
            ui.click("Cancel")
            assertEquals(ImportIntent.DismissRemoteSourceEditor, ui.importIntents.last())
            assertNull(ui.importState.editingSource)
        }
    }
    @Test fun `tag editor routes edits and online search to the shared store`() = onUiThread {
        Fixture(Page.Tags, height = 1500).use { ui ->
            ui.clickAction("fixture.mp3")
            assertEquals(MusicTagsIntent.SelectTrack("track"), ui.tagIntents.filterIsInstance<MusicTagsIntent.SelectTrack>().last())
            ui.edit("Fixture track", "Edited title")
            assertEquals(MusicTagsIntent.TitleChanged("Edited title"), ui.tagIntents.last())
            ui.click("Online search")
            assertTrue(ui.tagIntents.contains(MusicTagsIntent.OpenOnlineLyricsSearch))
            ui.click("Close")
            assertEquals(MusicTagsIntent.DismissOnlineLyricsSearch, ui.tagIntents.last())
            assertEquals("Edited title", ui.tagsState.draft.title)
            ui.click("Back to list")
            ui.assertVisible("Discard unsaved changes?")
            ui.click("Continue editing")
            assertTrue(ui.editorVisible)
            assertTrue(ui.tagsState.isDirty)
            ui.capture("tags")
        }
    }
    @Test fun `manual lyrics search applies an explicit mode and dismisses`() = onUiThread {
        Fixture(Page.Search).use { ui ->
            ui.edit("Fixture track", "Edited search")
            assertEquals(PlayerIntent.ManualLyricsTitleChanged("Edited search"), ui.playerIntents.last())
            ui.click("Search")
            assertTrue(ui.playerIntents.contains(PlayerIntent.SearchManualLyrics))
            ui.click("Matched song")
            ui.click("Apply lyrics only")
            val intent = ui.playerIntents.last() as PlayerIntent.ApplyManualLyricsCandidate
            assertEquals(LyricsSearchApplyMode.LYRICS_ONLY, intent.mode)
            assertEquals("fixture", intent.candidate.sourceId)
            ui.capture("lyrics-search")
            ui.click("Close")
            assertEquals(PlayerIntent.EndManualLyricsSearch, ui.playerIntents.last())
            assertFalse(ui.presentation.isManualLyricsSearchVisible)
        }
    }
    @Test fun `lyrics sharing keeps copy clear and close callbacks`() = onUiThread {
        Fixture(Page.Share).use { ui ->
            ui.capture("share-before-copy")
            ui.click("Copy")
            ui.capture("share-copy-menu")
            ui.click("Copy text")
            assertEquals(PlayerIntent.CopyLyricsShareText, ui.playerIntents.last())
            ui.capture("lyrics-share")
            ui.click("Clear selection")
            assertEquals(PlayerIntent.ClearLyricsSelection, ui.playerIntents.last())
            assertTrue(ui.playerState.selectedLyricsLineIndices.isEmpty())
            ui.click("Close")
            assertEquals(PlayerIntent.EndLyricsShare, ui.playerIntents.last())
            assertFalse(ui.presentation.isLyricsShareVisible)
        }
    }
    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private enum class Page { Settings, SourceCreate, SourceEdit, Tags, Search, Share }
    private class Fixture(private val page: Page, private val width: Int = 480, height: Int = 1000) : AutoCloseable {
        val settingsIntents = mutableListOf<SettingsIntent>()
        val importIntents = mutableListOf<ImportIntent>()
        val tagIntents = mutableListOf<MusicTagsIntent>()
        val playerIntents = mutableListOf<PlayerIntent>()
        private val platform = PlatformDescriptor("Android", PlatformCapabilities(true, true, true, true, false))
        private val track = Track("track", "source", "Fixture track", mediaLocator = "/fixture.mp3", relativePath = "fixture.mp3")
        private val lyrics = LyricsDocument(listOf(LyricsLine(0L, "A first line"), LyricsLine(1000L, "A second line")), sourceId = "fixture", rawPayload = "")
        private val candidate = LyricsSearchCandidate("fixture", "Fixture source", lyrics, title = "Matched song")
        private var settingsState by mutableStateOf(SettingsState())
        var importState by mutableStateOf(ImportState(
            capabilities = platform.capabilities,
            webDavRootUrl = "https://before.invalid", webDavUsername = "fixture",
            creatingSourceType = if (page == Page.SourceCreate) ImportSourceType.WEBDAV else null,
            editingSource = if (page == Page.SourceEdit) RemoteSourceEditorState("source", ImportSourceType.WEBDAV, label = "Fixture source", rootUrl = "https://before.invalid", username = "fixture") else null,
        ))
        var tagsState by mutableStateOf(MusicTagsState(isLoadingContent = false, tracks = listOf(track),
            selectedSnapshot = AudioTagSnapshot("Fixture track"), draft = MusicTagsDraft(title = "Fixture track"),
            canEditSelected = true, canWriteSelected = true))
        var playerState by mutableStateOf(PlayerState(snapshot = PlaybackSnapshot(queue = listOf(track), currentIndex = 0), lyrics = lyrics,
            manualLyricsTitle = "Fixture track",
            selectedLyricsLineIndices = setOf(0),
            shareCardModel = LyricsShareCardModel("Fixture track", lyricsLines = listOf("A first line"))))
        var editorVisible = false
        val presentation = PlayerPresentation({ playerState }, ::onPlayer).apply {
            if (page == Page.Search) openSearch()
            if (page == Page.Share) openShare()
        }
        private var frame = 0L
        private val scene = ImageComposeScene(width, height) {
            ProvideUiResourceLanguage("en") {
                CompositionLocalProvider(LocalPlayerPresentation provides presentation, LocalPlatformDescriptor provides platform) {
                    LynMusicTheme {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            when (page) {
                                Page.Settings -> SettingsTab(platform, settingsState, onSettingsIntent = {
                                    settingsIntents += it
                                    if (it is SettingsIntent.ThemeSelected) settingsState = settingsState.copy(selectedTheme = it.value)
                                }, onOpenBackgroundRunSettings = {})
                                Page.SourceCreate, Page.SourceEdit -> SourcesTab(platform, importState, onImportIntent = ::onImport, compact = true)
                                Page.Tags -> MusicTagsTab(tagsState, effects = emptyFlow(), onMusicTagsIntent = ::onTag,
                                    onPlayerIntent = ::onPlayer, onMobileEditorVisibilityChanged = { editorVisible = it })
                                Page.Search -> if (presentation.isManualLyricsSearchVisible) ManualLyricsSearchOverlay(playerState, ::onPlayer)
                                Page.Share -> if (presentation.isLyricsShareVisible) LyricsShareOverlay(playerState, ::onPlayer)
                            }
                        }
                    }
                }
            }
        }
        init { render() }
        private fun onImport(intent: ImportIntent) {
            importIntents += intent
            when (intent) {
                is ImportIntent.WebDavRootUrlChanged -> importState = importState.copy(webDavRootUrl = intent.value)
                ImportIntent.DismissRemoteSourceCreator -> importState = importState.copy(creatingSourceType = null)
                ImportIntent.DismissRemoteSourceEditor -> importState = importState.copy(editingSource = null)
                else -> Unit
            }
        }
        private fun onTag(intent: MusicTagsIntent) {
            tagIntents += intent
            when (intent) {
                is MusicTagsIntent.SelectTrack -> tagsState = tagsState.copy(selectedTrackId = intent.trackId)
                is MusicTagsIntent.TitleChanged -> tagsState = tagsState.copy(draft = tagsState.draft.copy(title = intent.value), isDirty = true)
                MusicTagsIntent.OpenOnlineLyricsSearch -> tagsState = tagsState.copy(onlineLyricsSearch = MusicTagsLyricsSearchState(isVisible = true, title = tagsState.draft.title))
                MusicTagsIntent.DismissOnlineLyricsSearch -> tagsState = tagsState.copy(onlineLyricsSearch = tagsState.onlineLyricsSearch.copy(isVisible = false))
                else -> Unit
            }
        }
        private fun onPlayer(intent: PlayerIntent) {
            playerIntents += intent
            when (intent) {
                is PlayerIntent.ManualLyricsTitleChanged -> playerState = playerState.copy(manualLyricsTitle = intent.value)
                PlayerIntent.SearchManualLyrics -> playerState = playerState.copy(hasManualLyricsSearchResult = true, manualLyricsResults = listOf(candidate))
                PlayerIntent.BeginManualLyricsSearch -> playerState = playerState.copy(manualLyricsSearchSession = PlayerTaskSession(1, track.id))
                PlayerIntent.BeginLyricsShare -> playerState = playerState.copy(lyricsShareSession = PlayerTaskSession(2, track.id))
                PlayerIntent.EndManualLyricsSearch -> playerState = playerState.copy(manualLyricsSearchSession = null)
                PlayerIntent.ClearLyricsSelection -> playerState = playerState.copy(selectedLyricsLineIndices = emptySet(), shareCardModel = null)
                PlayerIntent.EndLyricsShare -> playerState = playerState.copy(lyricsShareSession = null)
                else -> Unit
            }
        }
        private fun render() {
            repeat(24) { Snapshot.sendApplyNotifications(); scene.render(++frame * 16_666_667L).close() }
        }
        private fun nodes(): List<SemanticsNode> {
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        }
        private fun matches(node: SemanticsNode, label: String) = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true || node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        fun assertVisible(label: String) { assertTrue(nodes().any { matches(it, label) }, "Missing $label") }
        fun click(label: String) {
            val node = nodes().lastOrNull { matches(it, label) } ?: error("Missing $label; labels=" + nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() })
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center)
            render()
        }
        fun clickAction(label: String) {
            var node = nodes().last { matches(it, label) }
            while (node.config.getOrNull(SemanticsActions.OnClick)?.action == null) node = requireNotNull(node.parent)
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            render()
        }
        fun edit(original: String, value: String) {
            val node = nodes().single { it.config.getOrNull(SemanticsProperties.EditableText)?.text == original }
            assertTrue(node.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)))
            render()
        }
        fun capture(name: String) {
            scene.sendPointerEvent(PointerEventType.Move, Offset.Zero); render()
            val file = File("build/reports/tools-ui/$name.png"); file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { img -> img.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() = scene.close()
    }
}
