@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package top.iwesley.lyn.music.automotive.ui

import androidx.compose.runtime.*
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*
import top.iwesley.lyn.music.ANDROID_AUTOMOTIVE_PLATFORM_NAME
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class AutomotiveShellCompositionTest {
    @Test
    fun `landscape navigation opens every maintained page`() = onUiThread {
        Fixture(1280, 800).use { ui ->
            for (expanded in listOf(false, true)) {
                if (expanded) ui.clickLabel("Expand navigation")
                for (tab in automotiveLandscapeNavigationTabs) {
                    ui.clickTab(tab)
                    assertEquals(tab, ui.selectedTab)
                    ui.capture("landscape-${if (expanded) "expanded" else "collapsed"}-${tab.name.lowercase()}")
                }
            }
        }
    }

    @Test
    fun `rail toggle preserves library content and playing track without dispatching playback actions`() = onUiThread {
        Fixture(1280, 800, playing = true).use { ui ->
            ui.clickLabel("Album")
            ui.assertHasLabel("Loading Album")
            val libraryIntents = ui.libraryIntents.toList()
            repeat(2) {
                ui.clickLabel("Expand navigation")
                assertEquals(AppTab.Library, ui.selectedTab)
                ui.assertHasLabel("Sidebar playback test")
                ui.assertHasLabel("Loading Album")
                ui.capture("landscape-playing-expanded")
                ui.clickLabel("Collapse navigation")
                ui.assertHasLabel("Sidebar playback test")
                ui.assertHasLabel("Loading Album")
            }
            assertEquals(libraryIntents, ui.libraryIntents)
            assertTrue(ui.playerIntents.isEmpty())
        }
    }

    @Test
    fun `portrait keeps all destinations accessible through collections and more`() = onUiThread {
        Fixture(480, 900).use { ui ->
            ui.capture("portrait-library")
            ui.clickLabel("Favorites")
            assertEquals(AppTab.Favorites, ui.selectedTab)
            ui.capture("portrait-collection")
            ui.clickLabel("More")
            ui.clickLabel("Settings")
            assertEquals(AppTab.Settings, ui.selectedTab)
            ui.capture("portrait-settings")
        }
    }

    @Test
    fun `orientation round trip preserves selected library category`() = onUiThread {
        Fixture(1280, 800, playing = true).use { ui ->
            ui.clickLabel("Album")
            ui.resize(480, 900)
            ui.assertHasLabel("Loading Album")
            ui.resize(1280, 800)
            ui.assertHasLabel("Loading Album")
            ui.assertHasLabel("Sidebar playback test")
            ui.clickTab(AppTab.My)
            ui.clickTab(AppTab.Library)
            ui.assertHasLabel("Loading Album")
            assertTrue(ui.playerIntents.isEmpty())
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(width: Int, height: Int, playing: Boolean = false) : AutoCloseable {
        var portrait by mutableStateOf(width < height)
        var selectedTab by mutableStateOf(AppTab.Library)
        val libraryIntents = mutableListOf<LibraryIntent>()
        val playerIntents = mutableListOf<PlayerIntent>()
        private val playerState = if (playing) PlayerState(snapshot = PlaybackSnapshot(
            queue = listOf(Track("rail-test", "local", "Sidebar playback test", mediaLocator = "", relativePath = "")),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 60_000,
            durationMs = 180_000,
        )) else PlayerState()
        private val platform = PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        private var frame = 0L
        private val scene = ImageComposeScene(width, height) {
            ProvideUiResourceLanguage("en") {
                CompositionLocalProvider(LocalPlatformDescriptor provides platform) {
                    LynMusicTheme {
                        val railState = rememberWideNavigationRailState()
                        val libraryEntry = rememberAutomotiveLibraryEntry()
                        CompositionLocalProvider(LocalAutomotiveLibraryEntry provides libraryEntry) {
                        if (!portrait) {
                            AutomotiveLandscapeShell(
                                navigationRailState = railState,
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
                                importState = ImportState(capabilities = platform.capabilities),
                                playerState = playerState,
                                settingsState = SettingsState(),
                                onMyIntent = {},
                                onLibraryIntent = { libraryIntents += it },
                                onOnlineLibraryIntent = {},
                                onPlaylistsIntent = {},
                                onOnlinePlaylistsIntent = {},
                                onFavoritesIntent = {},
                                onOnlineFavoritesIntent = {},
                                onImportIntent = {},
                                onPlayerIntent = { playerIntents += it },
                                onSettingsIntent = {},
                                onOpenBackgroundRunSettings = {},
                                onOpenLibraryNavigationTarget = {},
                                onOpenAddToPlaylist = {},
                            )
                        } else {
                            AutomotivePortraitShell(
                                portraitNavigation = true,
                                mobilePortraitMiniPlayer = true,
                                hideMiniPlayerBar = false,
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
            val node = nodes().single {
                it.config.getOrNull(SemanticsProperties.TestTag) == "automotive-navigation-${tab.name}"
            }
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center, type = PointerType.Touch)
            render()
            val selected = nodes().single {
                it.config.getOrNull(SemanticsProperties.TestTag) == "automotive-navigation-${tab.name}"
            }
            assertEquals(true, selected.config[SemanticsProperties.Selected])
        }

        fun clickLabel(label: String) {
            val node = nodes().lastOrNull {
                    (it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true)
            } ?: error("Missing navigation: $label")
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center, type = PointerType.Touch)
            render()
        }

        fun assertHasLabel(label: String) {
            assertTrue(nodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
            }, "Missing label: $label")
        }

        fun resize(width: Int, height: Int) {
            portrait = width < height
            scene.constraints = Constraints.fixed(width, height)
            render()
        }

        fun capture(name: String) {
            val file = File("build/reports/automotive-ui/$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image ->
                image.encodeToData()!!.use { file.writeBytes(it.bytes) }
            }
        }
        override fun close() = scene.close()
    }
}
