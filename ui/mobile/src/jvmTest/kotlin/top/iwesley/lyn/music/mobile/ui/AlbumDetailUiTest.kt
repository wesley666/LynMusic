@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package top.iwesley.lyn.music.mobile.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.details.*
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.tags.MusicTagsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class AlbumDetailUiTest {
    @Test fun capturesSelectedLayoutsInsideActualShells() = onUiThread {
        for ((width, height) in listOf(390 to 844, 1194 to 834, 834 to 1194, 640 to 400)) {
            Fixture(width, height, visual = true, count = 9).use { ui ->
                assertEquals(width == 1194, ui.visibleTag("album-content-split"))
                assertTrue(ui.visibleTag("album-toolbar"))
                assertTrue(ui.visible("作词：罗大佑"), "The actual player must be present")
                assertEquals(1, ui.requests)
                assertTrue(ui.intents.isEmpty())
                if (width == 1194) {
                    assertTrue(ui.textNode("曲库").boundsInRoot.right <= 240f)
                    assertTrue(ui.tagged("album-summary").boundsInRoot.right < ui.tagged("album-tracks").boundsInRoot.left)
                    assertTrue(ui.visibleTag("album-column-duration"))
                }
                ui.capture("$width-$height")
                ui.scrollTo(9)
                val lastMenu = ui.textNode("河 — 9 的更多操作")
                assertTrue(lastMenu.boundsInRoot.bottom <= ui.textNode("作词：罗大佑").boundsInRoot.top,
                    "Last song menu must scroll clear of the player")
                ui.capture("$width-$height-bottom")
            }
        }
    }

    @Test fun splitThresholdUsesAvailableContentAfterSidebarAndPlayer() = onUiThread {
        for ((width, height, split) in listOf(Triple(1139, 834, false), Triple(1140, 834, true), Triple(1194, 600, false))) {
            Fixture(width, height).use { ui ->
                assertEquals(split, ui.visibleTag("album-content-split"), "$width x $height")
                assertEquals(!split, ui.visibleTag("album-content-stacked"))
                assertEquals(1, ui.requests)
            }
        }
    }

    @Test fun phoneHeroScrollsAwayWhileToolbarAndPlayerStayFixed() = onUiThread {
        Fixture(390, 844).use { ui ->
            val toolbar = ui.tagged("album-toolbar").boundsInRoot
            val player = ui.textNode("Now playing").boundsInRoot
            ui.scrollTo(12, 9)
            assertFalse(ui.visibleTag("album-cover"))
            assertEquals(toolbar, ui.tagged("album-toolbar").boundsInRoot)
            assertEquals(player, ui.textNode("Now playing").boundsInRoot)
            assertEquals(1, ui.requests)
            assertTrue(ui.intents.isEmpty())
        }
    }

    @Test fun layoutAndHostChangesKeepSongAnchorWithoutAnotherQuery() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.scrollTo(24, 11)
            for ((width, height) in listOf(1194 to 834, 834 to 1194, 390 to 844)) {
                ui.rebuild(width, height)
                assertEquals(24, ui.entry.contentListState.firstVisibleItemIndex)
                assertEquals(11, ui.entry.contentListState.firstVisibleItemScrollOffset)
                assertEquals(1, ui.requests)
            }
            ui.navigation.open(LibraryNavigationTarget.OnlineArtist("server", "artist", "Artist A"))
            ui.render()
            ui.navigation.pop(); ui.render()
            assertEquals(24, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(11, ui.entry.contentListState.firstVisibleItemScrollOffset)
            assertEquals(1, ui.requests)
        }
    }

    @Test fun switchingLayoutWhileHeroIsPartiallyVisibleStartsAtTheNewHeader() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.scrollTo(0, 180)
            assertEquals(0, ui.entry.contentListState.firstVisibleItemIndex)
            ui.rebuild(1194, 834)
            assertEquals(0, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(0, ui.entry.contentListState.firstVisibleItemScrollOffset)
            assertTrue(ui.visible("Song 0"))
            assertEquals(1, ui.requests)
        }
    }

    @Test fun rotatingStackedLayoutResetsHeaderButKeepsSongAnchor() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.scrollTo(0, 330)
            assertTrue(ui.visibleTag("album-content-stacked"))
            assertEquals(0, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(330, ui.entry.contentListState.firstVisibleItemScrollOffset)

            ui.rebuild(640, 400)
            assertTrue(ui.visibleTag("album-content-stacked"))
            assertEquals(0, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(0, ui.entry.contentListState.firstVisibleItemScrollOffset)
            assertTrue(ui.visibleTag("album-cover"))

            ui.scrollTo(12, 9)
            ui.rebuild(390, 844)
            assertEquals(12, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(9, ui.entry.contentListState.firstVisibleItemScrollOffset)
            assertEquals(1, ui.requests)
            assertTrue(ui.intents.isEmpty())
        }
    }

    @Test fun shrinkingStackedLayoutResetsHeaderButKeepsSongAnchor() = onUiThread {
        Fixture(834, 1194).use { ui ->
            ui.scrollTo(0, 360)
            assertTrue(ui.visibleTag("album-content-stacked"))
            assertEquals(0, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(360, ui.entry.contentListState.firstVisibleItemScrollOffset)

            ui.rebuild(390, 844)
            assertTrue(ui.visibleTag("album-content-stacked"))
            assertEquals(0, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(0, ui.entry.contentListState.firstVisibleItemScrollOffset)
            assertTrue(ui.visibleTag("album-cover"))

            ui.scrollTo(24, 11)
            ui.rebuild(834, 1194)
            assertEquals(24, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(11, ui.entry.contentListState.firstVisibleItemScrollOffset)
            assertEquals(1, ui.requests)
            assertTrue(ui.intents.isEmpty())
        }
    }

    @Test fun loadingRetryAndEmptyStatePreserveNavigationAndContentPosition() = onUiThread {
        val gate = CompletableDeferred<Unit>()
        Fixture(390, 844, gate = gate).use { ui ->
            assertTrue(ui.visibleTag("album-loading"))
            assertTrue(ui.visibleTag("album-toolbar"))
            assertFalse(ui.visibleTag("album-play-all"))
            gate.complete(Unit); ui.render()
            ui.scrollTo(20, 7)
            ui.fail = true
            ui.entry.retry(); ui.render()
            assertTrue(ui.visibleTag("album-error"))
            ui.rebuild(1194, 834)
            assertEquals(20, ui.entry.contentListState.firstVisibleItemIndex)
            ui.fail = false
            ui.click("Retry")
            assertEquals(20, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(7, ui.entry.contentListState.firstVisibleItemScrollOffset)
            assertEquals(3, ui.requests)
            ui.empty = true
            ui.entry.retry(); ui.render()
            assertTrue(ui.visibleTag("album-empty"))
            assertTrue(ui.visible("0 songs"))
            assertFalse(ui.visibleTag("album-play-all"))
            assertTrue(ui.visibleTag("album-toolbar"))
        }
    }

    @Test fun standardSongMenusFavoritesAndPlayActionsKeepTheirSelectedTracks() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.clickTag("album-play-all")
            val all = ui.intents.single() as PlayerIntent.PlayTracks
            assertEquals(ui.tracks, all.tracks)
            assertEquals(0, all.startIndex)
            ui.intents.clear()
            ui.click("More actions for Song 0")
            ui.click("Add to favorites")
            assertEquals(setOf("track-0"), ui.favoriteIds)
            assertTrue(ui.visible("Liked"))
            assertFalse(ui.visible("Remove from favorites"), "Favorite is in the menu, not a standalone button")
            ui.click("Song 1")
            assertEquals(ui.tracks, (ui.intents.single() as PlayerIntent.PlayTracks).tracks)
            assertEquals(1, (ui.intents.single() as PlayerIntent.PlayTracks).startIndex)
            ui.intents.clear()
            ui.click("More actions for Song 0")
            ui.click("View artist")
            assertEquals("artist", ui.navigation.top.kind)
            assertFalse(ui.visible("View artist"))
            assertTrue(ui.intents.isEmpty())
        }
    }

    @Test fun longNamesLargeTextAndMissingArtworkRemainScrollable() = onUiThread {
        for ((width, height) in listOf(390 to 844, 1194 to 834, 640 to 400)) {
            Fixture(width, height, fontScale = 2f, longNames = true).use { ui ->
                assertTrue(ui.visibleTag("album-toolbar"))
                val list = ui.entry.contentListState
                val header = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                if (!ui.visibleTag("album-play-all") && header != null) ui.scrollTo(0, (header.size - 180).coerceAtLeast(0))
                if (ui.visibleTag("album-content-split")) {
                    val scroll = ui.tagged("album-summary-scroll").config[SemanticsActions.ScrollBy].action!!
                    assertTrue(scroll(0f, 1000f)); ui.render()
                    assertFalse(ui.visibleTag("album-column-duration"))
                }
                assertTrue(ui.visibleTag("album-play-all"), "Playback must remain reachable at large font sizes")
                val button = ui.tagged("album-play-all").boundsInRoot
                assertTrue(button.width > 80f)
                assertTrue(button.bottom <= ui.textNode("Now playing").boundsInRoot.top)
                ui.scrollTo(15)
                assertTrue(ui.visible("Song 14"))
                assertEquals(1, ui.requests)
                ui.capture("large-text-$width-$height")
            }
        }
    }

    @Test fun sidebarSelectionAndToolbarBackLeaveTheAlbumWithoutChangingPlayback() = onUiThread {
        Fixture(1194, 834).use { ui ->
            ui.click("Library")
            assertFalse(ui.navigation.hasDetails)
            assertTrue(ui.visible("Now playing"))
            assertTrue(ui.intents.isEmpty())
        }
        Fixture(390, 844).use { ui ->
            ui.click("Back")
            assertFalse(ui.navigation.hasDetails)
            assertTrue(ui.visible("Now playing"))
            assertTrue(ui.intents.isEmpty())
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(
        var width: Int,
        var height: Int,
        val visual: Boolean = false,
        val count: Int = 60,
        val fontScale: Float = 1f,
        val longNames: Boolean = false,
        val gate: CompletableDeferred<Unit>? = null,
    ) : AutoCloseable {
        private val scheduler = TestCoroutineScheduler()
        private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        private val platform = PlatformDescriptor(ANDROID_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        private var frame = 0L
        var requests = 0
        var fail = false
        var empty = false
        var selectedTab by mutableStateOf(AppTab.Library)
        var favoriteIds by mutableStateOf(if (visual) setOf("track-1", "track-2") else emptySet())
        val intents = mutableListOf<PlayerIntent>()
        private val albumTitle = if (longNames) "A very long album title that must fit within two lines without pushing actions outside the screen" else if (visual) "夏" else "Album A"
        private val artist = if (longNames) "A very long artist name with several collaborators" else if (visual) "陈升" else "Artist A"
        val tracks = (0 until count).map { index ->
            val titles = listOf("Summer", "南风", "镜子", "四条腿", "河")
            Track("track-$index", "server", if (visual) if (index < 5) titles[index] else "河 — ${index + 1}" else "Song $index",
                artist, albumTitle, durationMs = 255000, mediaLocator = "", relativePath = "$index",
                albumId = "album", artistId = "artist")
        }
        private val player = PlayerState(snapshot = PlaybackSnapshot(
            queue = listOf(Track("playing", "local", if (visual) "作词：罗大佑" else "Now playing", "陈升", "夏", mediaLocator = "", relativePath = "playing")),
            currentIndex = 0, positionMs = 97000, durationMs = 255000, canSeek = true,
        ))
        private val queries = TestMusicDetailQueries(album = { flow {
            requests++
            gate?.await()
            if (fail) error("Controlled album failure")
            emit(AlbumDetailData(albumTitle, artist, tracks = if (empty) emptyList() else tracks))
        } })
        val navigation = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), queries, scope, mutableStateOf(false), {})
        private val albumRoute = navigation.run { open(LibraryNavigationTarget.OnlineAlbum("server", "album", albumTitle, artist)); top }
        val entry get() = navigation.entry(albumRoute)
        private var scene = createScene()

        init { render() }

        private fun createScene() = ImageComposeScene(width, height, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalPlatformDescriptor provides platform,
                LocalMusicDetailNavigation provides navigation,
                LocalPlayerPresentation provides remember { PlayerPresentation() },
                LocalTrackListActionBindings provides TrackListActionBindings(
                    favoriteState = { TrackFavoriteUiState(it.id in favoriteIds) },
                    onSetFavorite = { track, liked -> favoriteIds = if (liked) favoriteIds + track.id else favoriteIds - track.id },
                ),
            ) {
                ProvideUiResourceLanguage(if (visual) "zh-CN" else "en") {
                    LynMusicTheme(themeTokens = OCEAN_APP_THEME_TOKENS, textPalette = AppThemeTextPalette.Black) {
                        Shell()
                    }
                }
            }
        }

        @Composable
        private fun Shell() {
            val select: (AppTab) -> Unit = { navigation.clear(); selectedTab = it }
            if (buildLayoutProfile(width.dp, height.dp).isExpandedLayout) {
                TabletShell(
                    selectedTab = selectedTab, onTabSelected = select, platform = platform,
                    myState = MyState(isLoadingContent = false), libraryState = LibraryState(),
                    onlineLibraryState = OnlineLibraryState(), playlistsState = PlaylistsState(),
                    onlinePlaylistsState = OnlinePlaylistsState(), favoritesState = FavoritesState(),
                    onlineFavoritesState = OnlineFavoritesState(), musicTagsState = MusicTagsState(),
                    musicTagsEffects = emptyFlow(), onMusicTagsIntent = {},
                    importState = ImportState(capabilities = platform.capabilities), playerState = player,
                    settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = {},
                    onOnlineLibraryIntent = {}, onPlaylistsIntent = {}, onOnlinePlaylistsIntent = {},
                    onFavoritesIntent = {}, onOnlineFavoritesIntent = {}, onImportIntent = {},
                    onPlayerIntent = { intents += it }, onSettingsIntent = {}, onOpenBackgroundRunSettings = {},
                    onOpenLibraryNavigationTarget = { navigation.open(it) }, onOpenAddToPlaylist = {},
                )
            } else {
                MobileShell(
                    phoneNavigation = height >= width, mobilePortraitMiniPlayer = true,
                    hideMiniPlayerBar = false, onMobileEditorVisibilityChanged = {},
                    selectedTab = selectedTab, onTabSelected = select, platform = platform,
                    myState = MyState(isLoadingContent = false), libraryState = LibraryState(),
                    onlineLibraryState = OnlineLibraryState(), playlistsState = PlaylistsState(),
                    onlinePlaylistsState = OnlinePlaylistsState(), favoritesState = FavoritesState(),
                    onlineFavoritesState = OnlineFavoritesState(), musicTagsState = MusicTagsState(),
                    musicTagsEffects = emptyFlow(), onMusicTagsIntent = {},
                    importState = ImportState(capabilities = platform.capabilities), playerState = player,
                    settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = {},
                    onOnlineLibraryIntent = {}, onPlaylistsIntent = {}, onOnlinePlaylistsIntent = {},
                    onFavoritesIntent = {}, onOnlineFavoritesIntent = {}, onImportIntent = {},
                    onPlayerIntent = { intents += it }, onSettingsIntent = {}, onOpenBackgroundRunSettings = {},
                    onOpenLibraryNavigationTarget = { navigation.open(it) }, onOpenAddToPlaylist = {},
                )
            }
        }

        fun render(frames: Int = 40) {
            repeat(frames) { scheduler.runCurrent(); Snapshot.sendApplyNotifications(); scene.render(++frame * 16_666_667L).close() }
        }
        fun rebuild(newWidth: Int, newHeight: Int) {
            scene.close(); width = newWidth; height = newHeight
            scene = createScene(); render()
        }
        fun scrollTo(index: Int, offset: Int = 0) {
            scope.launch { entry.contentListState.scrollToItem(index, offset) }; render()
        }
        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            val viewport = Rect(0f, 0f, width.toFloat(), height.toFloat())
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                .filter { !it.boundsInRoot.isEmpty && it.boundsInRoot.overlaps(viewport) }
        }
        private fun matches(node: SemanticsNode, label: String) = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        fun tagged(tag: String) = nodes().last { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun visibleTag(tag: String) = nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun visible(label: String) = nodes().any { matches(it, label) }
        fun textNode(label: String) = nodes().lastOrNull { matches(it, label) } ?: error("Missing $label")
        private fun clickNode(node: SemanticsNode) {
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center)
            render()
        }
        fun click(label: String) = clickNode(textNode(label))
        fun clickTag(tag: String) = clickNode(tagged(tag))
        fun capture(name: String) {
            val file = File("build/reports/album-detail/album-$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() { scene.close(); navigation.dispose(); scope.cancel() }
    }
}
