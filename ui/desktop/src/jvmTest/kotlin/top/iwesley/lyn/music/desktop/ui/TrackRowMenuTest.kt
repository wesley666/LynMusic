@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.runtime.InternalComposeApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package top.iwesley.lyn.music.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class TrackRowMenuTest {
    @Test fun `badge and menu fit narrow and wide rows and favorites hides only badge`() = onUiThread {
        for (width in listOf(360, 1100)) Fixture(width).use { ui ->
            val heart = ui.node("Liked").boundsInRoot
            val menu = ui.node("More actions for Track A").boundsInRoot
            assertEquals(14f, heart.width)
            assertEquals(24f, menu.width)
            assertTrue(heart.right < menu.left)
            assertTrue(menu.right <= width)
            assertEquals(width >= 600, ui.visible("03:00"))
            ui.capture("row-$width")
            ui.showBadge = false
            ui.render()
            assertFalse(ui.visible("Liked"))
            ui.click("More actions for Track A")
            assertTrue(ui.visible("Remove from favorites"))
            ui.capture("menu-$width")
            assertEquals(0, ui.plays)
        }
    }

    @Test fun `menu actions target this row and never play navigate or select`() = onUiThread {
        Fixture().use { ui ->
            ui.click("More actions for Track A")
            ui.click("Add to playlist")
            assertEquals(listOf("a"), ui.added.map { it.id })
            assertFalse(ui.visible("Remove from favorites"))
            ui.click("More actions for Track A")
            ui.click("Song information")
            assertTrue(ui.visible("Music/a.flac"))
            assertFalse(ui.visible("Remove from favorites"))
            ui.escape()
            assertFalse(ui.visible("Music/a.flac"))
            ui.click("More actions for Track A")
            ui.click("Remove from favorites")
            assertEquals(listOf("a" to false), ui.favorites)
            assertFalse(ui.visible("Liked"))
            ui.click("More actions for Track A")
            assertTrue(ui.visible("Add to favorites"))
            ui.escape()
            assertEquals(0, ui.plays)
            assertEquals(0, ui.navigations)
            assertEquals(0, ui.selections)
            ui.click("Track A")
            assertEquals(1, ui.plays)
        }
    }

    @Test fun `context identity and selection close menus but index and favorite updates keep target`() = onUiThread {
        Fixture().use { ui ->
            ui.click("More actions for Track A")
            ui.index = 9
            ui.favorite = TrackFavoriteUiState(false, isKnown = false)
            ui.render()
            assertTrue(ui.visible("Add to favorites"))
            ui.click("Add to favorites")
            assertEquals(listOf("a" to true), ui.favorites)
            ui.click("More actions for Track A")
            ui.context = "album"
            ui.render()
            assertFalse(ui.visible("Remove from favorites"))
            ui.openGesture()
            assertTrue(ui.visible("Remove from favorites"))
            ui.track = ui.track.copy(sourceId = "another", title = "Track A2")
            ui.render()
            assertFalse(ui.visible("Remove from favorites"))
            ui.click("More actions for Track A2")
            ui.selection = true
            ui.render()
            assertFalse(ui.visible("Remove from favorites"))
            assertFalse(ui.visible("More actions for Track A2"))
            assertTrue(ui.visible("Liked"))
            ui.click("Track A2")
            assertEquals(1, ui.selections)
            assertEquals(0, ui.plays)
        }
    }

    @Test fun `gesture menu and three dots share items and respect disabled actions`() = onUiThread {
        Fixture().use { ui ->
            ui.openGesture()
            assertTrue(ui.visible("Remove from favorites"))
            assertTrue(ui.visible("Add to playlist"))
            ui.favorite = ui.favorite.copy(canChange = false)
            ui.render()
            ui.click("Remove from favorites")
            assertTrue(ui.favorites.isEmpty())
            assertTrue(ui.visible("Song information"))
            ui.escape()
            assertFalse(ui.visible("Song information"))
            assertEquals(0, ui.plays)
        }
    }

    @Test fun `offline handoff preserves selected track quality actions and closes menu`() = onUiThread {
        Fixture(offline = true).use { ui ->
            ui.click("More actions for Track A")
            ui.click("Offline downloads")
            assertFalse(ui.visible("Add to playlist"))
            assertTrue(ui.offlineIntents.contains(OfflineDownloadIntent.RefreshAvailableSpace))
            ui.capture("offline")
            val download = ui.nodes().first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains("Original", ignoreCase = true) } == true }
            ui.clickAt(download.boundsInRoot.center)
            val intent = ui.offlineIntents.filterIsInstance<OfflineDownloadIntent.Download>().single()
            assertEquals("a", intent.track.id)
            assertEquals(0, ui.plays)
        }
    }

    @Test fun `other rows retain their original offline gesture without the general menu`() = onUiThread {
        Fixture(offline = true, legacy = true).use { ui ->
            ui.openGesture()
            assertFalse(ui.visible("Add to playlist"))
            assertTrue(ui.offlineIntents.contains(OfflineDownloadIntent.RefreshAvailableSpace))
            assertEquals(0, ui.plays)
        }
    }

    @Test fun `unknown artist and offline badge keep heart visible before download state`() = onUiThread {
        Fixture(360, offline = true).use { ui ->
            ui.track = ui.track.copy(artistName = null)
            ui.downloads = mapOf("a" to ui.download(OfflineDownloadStatus.Completed))
            ui.render()
            assertTrue(ui.visible("Unknown artist"))
            assertTrue(ui.node("Liked").boundsInRoot.right < ui.node("Downloaded music").boundsInRoot.left)
            assertTrue(ui.node("Downloaded music").boundsInRoot.right < ui.node("More actions for Track A").boundsInRoot.left)
            ui.capture("offline-badges")
            ui.favorite = TrackFavoriteUiState(true, isKnown = false)
            ui.render()
            assertFalse(ui.visible("Liked"))
            ui.click("More actions for Track A")
            assertTrue(ui.visible("Add to favorites"))
        }
    }

    @Test fun `offline cancel and delete remain available and return directly to list`() = onUiThread {
        Fixture(offline = true).use { ui ->
            ui.downloads = mapOf("a" to ui.download(OfflineDownloadStatus.Downloading))
            ui.render()
            ui.click("More actions for Track A")
            ui.click("Offline downloads")
            ui.click("Cancel download")
            assertEquals(OfflineDownloadIntent.Cancel("a"), ui.offlineIntents.last())
            assertFalse(ui.visible("Add to playlist"))
            ui.downloads = mapOf("a" to ui.download(OfflineDownloadStatus.Completed))
            ui.render()
            ui.click("More actions for Track A")
            ui.click("Offline downloads")
            ui.click("Delete downloaded music")
            assertEquals(OfflineDownloadIntent.Delete("a"), ui.offlineIntents.last())
            assertEquals(0, ui.plays)
        }
    }

    @Test fun `artist and album short clicks still navigate and long gestures open the same menu`() = onUiThread {
        Fixture().use { ui ->
            for (label in listOf(ui.track.artistName!!, "Album A")) {
                ui.click(label)
                ui.openGesture(label)
                assertTrue(ui.visible("Add to playlist"))
                ui.escape()
            }
            assertEquals(2, ui.navigations)
            assertEquals(0, ui.plays)
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private class Fixture(val width: Int = 700, offline: Boolean = false, legacy: Boolean = false) : AutoCloseable {
        var track by mutableStateOf(Track("a", "source", "Track A", artistName = "A very long artist name that must leave room for status icons",
            albumTitle = "Album A", durationMs = 180_000L, relativePath = "Music/a.flac",
            mediaLocator = if (offline) buildNavidromeSongLocator("source", "a") else "/Music/a.flac"))
        var favorite by mutableStateOf(TrackFavoriteUiState(true))
        var index by mutableStateOf(0)
        var showBadge by mutableStateOf(true)
        var selection by mutableStateOf(false)
        var context by mutableStateOf("tracks")
        var plays = 0
        var navigations = 0
        var selections = 0
        val added = mutableListOf<Track>()
        val favorites = mutableListOf<Pair<String, Boolean>>()
        val offlineIntents = mutableListOf<OfflineDownloadIntent>()
        var downloads by mutableStateOf<Map<String, OfflineDownload>>(emptyMap())
        fun download(status: OfflineDownloadStatus) = OfflineDownload("a", "source", track.mediaLocator,
            localMediaLocator = "/offline/a.flac", status = status, downloadedBytes = 512L, totalBytes = 1024L)
        val scheduler = TestCoroutineScheduler()
        private val back = object : NavigationEventInput() { fun complete() = dispatchOnBackCompleted() }
        private var frame = 0L
        val scene = ImageComposeScene(width, 740, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
            DisposableEffect(dispatcher) { dispatcher.addInput(back); onDispose { dispatcher.removeInput(back) } }
            ProvideUiResourceLanguage("en") {
                CompositionLocalProvider(
                    LocalPlatformDescriptor provides PlatformDescriptor(ANDROID_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false)),
                    LocalTrackListActionBindings provides TrackListActionBindings(
                        favoriteState = { favorite },
                        onSetFavorite = { target, value -> favorites += target.id to value; favorite = TrackFavoriteUiState(value) },
                        onAddToPlaylist = { added += it },
                    ),
                    LocalOfflineDownloadUiState provides OfflineDownloadUiState(downloadsByTrackId = downloads, onIntent = if (offline) ({ offlineIntents += it }) else null),
                ) {
                    LynMusicTheme {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 100.dp)) {
                            if (legacy) {
                                TrackActionContainer(track, { plays++ }, modifier = Modifier.fillMaxWidth().height(80.dp)) { Text("Track A") }
                            } else {
                                TrackRow(track, index, false, {}, showFavoriteIndicator = showBadge, menuContextKey = context,
                                    showDuration = width >= 600, onArtistClick = { navigations++ }, onAlbumClick = { navigations++ },
                                    selectionMode = selection, onSelectionToggle = { selections++ }, onClick = { plays++ })
                            }
                            Text("Playing Track B")
                        }
                    }
                }
            }
        }
        init { render() }
        fun render() { repeat(40) { scheduler.runCurrent(); Snapshot.sendApplyNotifications(); scene.render(++frame * 16_666_667L).close() } }
        fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        }
        private fun matches(node: SemanticsNode, label: String) = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true || node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        fun visible(label: String) = nodes().any { matches(it, label) }
        fun node(label: String) = nodes().lastOrNull { matches(it, label) } ?: error("Missing $label: ${nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }}")
        fun click(label: String) = clickAt(node(label).boundsInRoot.center)
        fun clickAt(point: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, point)
            scene.sendPointerEvent(PointerEventType.Release, point)
            render()
        }
        fun openGesture(label: String = track.title) {
            val point = node(label).boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point, buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary)
            scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Secondary)
            render()
        }
        fun escape() {
            if (!scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))) back.complete()
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
            render()
        }
        fun capture(name: String) {
            val output = System.getenv("LYN_TRACK_MENU_QA_OUTPUT") ?: return
            val file = File(output, "desktop-$name.png"); file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() = scene.close()
    }
}
