@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package top.iwesley.lyn.music.desktop.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.feature.tags.MusicTagsState
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class DesktopShellCompositionTest {
    @Test
    fun `landscape navigation opens every maintained page`() = onUiThread {
        Fixture(1280, 800).use { ui ->
            for (tab in desktopNavigationTabs()) {
                ui.clickTab(tab)
                assertEquals(tab, ui.selectedTab)
                ui.capture("landscape-${tab.name.lowercase()}")
            }
        }
    }

    @Test
    fun `tall desktop windows keep the sidebar at the supported minimum width`() = onUiThread {
        Fixture(1200, 1400).use { ui ->
            ui.clickTab(AppTab.Settings)
            assertEquals(AppTab.Settings, ui.selectedTab)
            ui.capture("tall-settings")
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(private val width: Int, height: Int) : AutoCloseable {
        var selectedTab by mutableStateOf(AppTab.Library)
        private val platform = PlatformDescriptor("Desktop", PlatformCapabilities(true, true, true, true, false))
        private var frame = 0L
        private val scene = ImageComposeScene(width, height) {
            ProvideUiResourceLanguage("en") {
                CompositionLocalProvider(LocalPlatformDescriptor provides platform) {
                    LynMusicTheme {
                        DesktopShell(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            platform = platform,
                            myState = MyState(isLoadingContent = false),
                            libraryState = LibraryState(),
                            onlineLibraryState = OnlineLibraryState(),
                            playlistsState = PlaylistsState(),
                            onlinePlaylistsState = OnlinePlaylistsState(),
                            favoritesState = FavoritesState(),
                            onlineFavoritesState = OnlineFavoritesState(),
                            musicTagsState = MusicTagsState(),
                            musicTagsEffects = emptyFlow(),
                            onMusicTagsIntent = {},
                            importState = ImportState(capabilities = platform.capabilities),
                            playerState = PlayerState(),
                            settingsState = SettingsState(),
                            onMyIntent = {},
                            onLibraryIntent = {},
                            onOnlineLibraryIntent = {},
                            onPlaylistsIntent = {},
                            onOnlinePlaylistsIntent = {},
                            onFavoritesIntent = {},
                            onOnlineFavoritesIntent = {},
                            onImportIntent = {},
                            onPlayerIntent = {},
                            onSettingsIntent = {},
                            onOpenBackgroundRunSettings = {},
                            onOpenLibraryNavigationTarget = {},
                            onOpenAddToPlaylist = {},
                        )
                    }
                }
            }
        }
        init { render() }

        private fun render() {
            repeat(20) {
                Snapshot.sendApplyNotifications()
                scene.render(++frame * 16_666_667L).close()
            }
        }

        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }

        fun clickTab(tab: AppTab) {
            val label = kotlinx.coroutines.runBlocking { resolveUiText(desktopTabLabelText(tab), AppLanguage.English) }
            clickLabel(label, sidebarOnly = true)
        }

        fun clickLabel(label: String, sidebarOnly: Boolean = false) {
            val node = nodes().lastOrNull {
                (!sidebarOnly || it.boundsInRoot.right < 240f) &&
                    (it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true)
            } ?: error("Missing navigation: $label")
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center)
            render()
        }

        fun capture(name: String) {
            val file = File("build/reports/split-ui/$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image ->
                image.encodeToData()!!.use { file.writeBytes(it.bytes) }
            }
        }
        override fun close() = scene.close()
    }
}
