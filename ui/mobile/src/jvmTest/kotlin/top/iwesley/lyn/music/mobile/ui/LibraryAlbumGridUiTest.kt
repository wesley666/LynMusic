@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package top.iwesley.lyn.music.mobile.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.OnlineAlbumItem
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.tags.MusicTagsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class LibraryAlbumGridUiTest {
    @Test fun columnsUseContentWidthAndCoversStaySquare() = onUiThread {
        for ((width, columns) in listOf(360 to 2, 390 to 2, 599 to 2, 600 to 3, 834 to 4, 1194 to 6)) {
            Fixture(width, count = 9).use { ui ->
                val first = ui.tagged("album-grid-cover-album-0").boundsInRoot
                assertEquals(first.width, first.height, 1f)
                val row = ui.browser.grid.layoutInfo.visibleItemsInfo.filter { it.key.toString().startsWith("album-") }
                    .groupBy { it.row }.minBy { it.key }.value
                assertEquals(columns, row.size, "width=$width")
                assertEquals(16, row[1].offset.x - row[0].offset.x - row[0].size.width)
                assertTrue(ui.browser.grid.layoutInfo.visibleItemsInfo.all { it.size.width <= width })
                if (width == 390 || width == 834 || width == 1194) ui.capture("$width")
                ui.scrollGrid(8)
                assertTrue(ui.visibleTag("album-grid-card-album-8"), "Incomplete final row must be reachable")
            }
        }
    }

    @Test fun albumAndArtistNamesStayOnOneEllipsizedLineAtLargeFontSizes() = onUiThread {
        for (fontScale in listOf(1f, 2f)) {
            Fixture(390, count = 3, fontScale = fontScale, longNames = true).use { ui ->
                ui.capture("long-names-$fontScale")
                for (field in listOf("title", "artist")) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    assertTrue(ui.tagged("album-grid-$field-album-0").config[SemanticsActions.GetTextLayoutResult].action!!(layouts))
                    assertEquals(1, layouts.single().lineCount)
                    val result = layouts.single()
                    // SkiaParagraph.isLineEllipsized is unimplemented on JVM; use its native truncation result.
                    assertTrue(result.multiParagraph.didExceedMaxLines, "$field must truncate at scale=$fontScale")
                }
                val title = ui.tagged("album-grid-title-album-0").boundsInRoot
                val artist = ui.tagged("album-grid-artist-album-0").boundsInRoot
                assertTrue(title.bottom <= artist.top)
            }
        }
    }

    @Test fun phoneMenuAndTabletToolbarSwitchViewsAndRestoreIndependentPositions() = onUiThread {
        for (width in listOf(390, 1194)) Fixture(width).use { ui ->
            assertEquals(AlbumViewMode.Grid, ui.browser.viewMode.value)
            ui.chooseView(AlbumViewMode.List)
            assertFalse(ui.visibleTag("library-album-grid"))
            ui.chooseView(AlbumViewMode.Grid)
            ui.chooseView(AlbumViewMode.List)
            ui.scope.launch { ui.browser.list.scrollToItem(18, 5) }; ui.render()
            if (width >= 600) assertTrue(ui.visibleTag("album-view-menu"))
            ui.chooseView(AlbumViewMode.Grid)
            ui.scrollGrid(24, 7)
            val gridAnchor = ui.browser.grid.firstVisibleItemIndex
            val gridOffset = ui.browser.grid.firstVisibleItemScrollOffset
            if (width >= 600) assertTrue(ui.visibleTag("album-view-menu"))
            ui.chooseView(AlbumViewMode.List)
            assertEquals(18, ui.browser.list.firstVisibleItemIndex)
            assertEquals(5, ui.browser.list.firstVisibleItemScrollOffset)
            ui.rebuild(width, ui.height)
            assertEquals(AlbumViewMode.List, ui.browser.viewMode.value)
            assertEquals(18, ui.browser.list.firstVisibleItemIndex)
            assertEquals(5, ui.browser.list.firstVisibleItemScrollOffset)
            ui.chooseView(AlbumViewMode.Grid)
            assertEquals(gridAnchor, ui.browser.grid.firstVisibleItemIndex)
            assertEquals(gridOffset, ui.browser.grid.firstVisibleItemScrollOffset)
            ui.root.value = LibraryBrowserRootView.Artists; ui.render()
            ui.root.value = LibraryBrowserRootView.Albums; ui.render()
            assertEquals(gridAnchor, ui.browser.grid.firstVisibleItemIndex)
            ui.rebuild(if (width == 390) 834 else 390, 1000)
            assertEquals(AlbumViewMode.Grid, ui.browser.viewMode.value)
            assertTrue(ui.browser.grid.layoutInfo.visibleItemsInfo.any { it.index == gridAnchor }, "Keep the album anchor after resizing")
        }
    }

    @Test fun cardsKeepLocalAndOnlineNavigationAndDisabledOnlineAlbums() = onUiThread {
        for (online in listOf(false, true)) Fixture(390, count = 3, online = online).use { ui ->
            ui.clickTag("album-grid-card-album-0")
            assertFalse(ui.visibleTag("library-album-grid"))
            val target = ui.targets.single()
            if (online) {
                assertIs<LibraryNavigationTarget.OnlineAlbum>(target)
                assertEquals("server", target.sourceId)
                assertEquals("album-0", target.albumId)
            } else {
                assertIs<LibraryNavigationTarget.Album>(target)
                assertEquals("album-0", target.albumId)
            }
            ui.navigation.pop(); ui.render()
            assertTrue(ui.visibleTag("library-album-grid"))
            assertEquals(AlbumViewMode.Grid, ui.browser.viewMode.value)
            ui.albums = listOf(Album("", "", null, 0)); ui.render()
            assertTrue(ui.visible("Unknown album"))
            assertTrue(ui.visible("Unknown artist"))
            if (online) assertTrue(ui.tagged("album-grid-card-").config.contains(SemanticsProperties.Disabled))
        }
    }

    @Test fun compactTabletHubSharesItsAlbumViewMenuWithTheBrowser() = onUiThread {
        Fixture(834, compactHub = true, count = 9).use { ui ->
            ui.chooseView(AlbumViewMode.List)
            assertFalse(ui.visibleTag("library-album-grid"))
            ui.chooseView(AlbumViewMode.Grid)
            assertTrue(ui.visibleTag("library-album-grid"))
        }
    }

    @Test fun lastAlbumScrollsClearOfTheFloatingPlayer() = onUiThread {
        Fixture(390, count = 9, dockInset = 140).use { ui ->
            ui.scrollGrid(8)
            assertTrue(ui.tagged("album-grid-card-album-8").boundsInRoot.bottom <= ui.height - 140)
        }
    }

    @Test fun onlinePaginationAndEmptySearchUseFullWidthItems() = onUiThread {
        Fixture(390, count = 3, online = true, canLoadMore = true).use { ui ->
            val footer = ui.browser.grid.layoutInfo.visibleItemsInfo.last()
            assertEquals(342, footer.size.width)
            ui.click("Load more")
            assertEquals(listOf<OnlineLibraryIntent>(OnlineLibraryIntent.LoadMoreAlbums), ui.onlineIntents)
            ui.albums = ui.albums + Album("album-3", "Next album", "Next artist", 4)
            ui.canLoadMore = false; ui.render()
            assertTrue(ui.visibleTag("album-grid-card-album-3"))
            assertFalse(ui.visible("Load more"))
            ui.query = "does-not-match"; ui.render()
            assertEquals(1, ui.browser.grid.layoutInfo.totalItemsCount)
            assertEquals(342, ui.browser.grid.layoutInfo.visibleItemsInfo.single().size.width)
            assertFalse(ui.visibleTag("library-album-scrollbar"))
        }
    }

    @Test fun draggingGridScrollbarReachesBothEndsWithPartialLastRows() = onUiThread {
        for (width in listOf(390, 834)) Fixture(width, count = 41).use { ui ->
            ui.dragScrollbar(toBottom = true)
            assertFalse(ui.browser.grid.canScrollForward)
            assertTrue(ui.visibleTag("album-grid-card-album-40"))
            ui.dragScrollbar(toBottom = false)
            assertFalse(ui.browser.grid.canScrollBackward)
            assertTrue(ui.visibleTag("album-grid-card-album-0"))
        }
    }

    @Test fun scrollbarCanPositionWithinTheFirstRowWhenOverflowIsLessThanOneRow() = onUiThread {
        for (height in listOf(844, 1000)) Fixture(390, height = height, count = 7).use { ui ->
            assertTrue(ui.browser.grid.canScrollForward)
            ui.dragScrollbar(toBottom = true)
            assertEquals(0, ui.browser.grid.firstVisibleItemIndex)
            val maxOffset = ui.browser.grid.firstVisibleItemScrollOffset
            assertTrue(maxOffset > 0)
            ui.dragScrollbar(toBottom = false)
            ui.dragScrollbarTo(0.5f)
            assertEquals(0, ui.browser.grid.firstVisibleItemIndex)
            assertEquals(maxOffset / 2f, ui.browser.grid.firstVisibleItemScrollOffset.toFloat(), 2f)
            assertTrue(ui.browser.grid.canScrollBackward)
            assertTrue(ui.browser.grid.canScrollForward)
        }
    }

    @Test fun scrollbarProgressDoesNotReturnToTheTopWhenTheHeaderLeavesTheViewport() = onUiThread {
        Fixture(1194, height = 834, count = 13).use { ui ->
            val headerHeight = ui.browser.grid.layoutInfo.visibleItemsInfo.first().size.height
            ui.scrollGrid(0, headerHeight - 2)
            val before = ui.scrollbarThumbTop()
            ui.scrollGrid(1, 5)
            assertEquals(1, ui.browser.grid.firstVisibleItemIndex)
            assertEquals(0, ui.browser.grid.layoutInfo.visibleItemsInfo.first().index,
                "The old header remains in content padding while the logical anchor has advanced")
            assertTrue(ui.scrollbarThumbTop() >= before, "The rendered thumb must advance with the content")
        }
    }

    @Test fun restoredGridKeepsForwardScrollbarDragsMovingForward() = onUiThread {
        Fixture(1194, height = 834, count = 13).use { ui ->
            ui.dragScrollbar(toBottom = true)
            ui.chooseView(AlbumViewMode.List)
            ui.chooseView(AlbumViewMode.Grid)
            val positions = listOf(0.65f, 0.75f, 0.85f, 0.95f).map { fraction ->
                ui.dragScrollbarTo(fraction)
                ui.browser.grid.firstVisibleItemIndex to ui.browser.grid.firstVisibleItemScrollOffset
            }
            fun movesForward(values: List<Pair<Int, Int>>) = values.zipWithNext().all { (a, b) ->
                b.first > a.first || b.first == a.first && b.second >= a.second
            }
            assertTrue(movesForward(positions), "Increasing drag positions must not reverse the content: $positions")
            val continuousDrag = ui.dragScrollbarTo(0.95f).drop(3)
            assertTrue(movesForward(continuousDrag), "A continuous drag must not reverse after its mapping is captured")
            ui.dragScrollbar(toBottom = false)
            assertFalse(ui.browser.grid.canScrollBackward)
            ui.dragScrollbar(toBottom = true)
            assertFalse(ui.browser.grid.canScrollForward)
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(
        var width: Int,
        var height: Int = 1000,
        count: Int = 60,
        val fontScale: Float = 1f,
        val longNames: Boolean = false,
        val online: Boolean = false,
        canLoadMore: Boolean = false,
        val compactHub: Boolean = false,
        val dockInset: Int = 0,
    ) : AutoCloseable {
        val scope = TestScope(UnconfinedTestDispatcher())
        private val platform = PlatformDescriptor(ANDROID_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        val root = mutableStateOf(LibraryBrowserRootView.Albums)
        var albums by mutableStateOf(List(count) { index ->
            Album("album-$index", if (longNames) "周杰伦 2004 无与伦比 Live Concert 演唱会精选 ".repeat(4) else "Album $index",
                if (longNames) "Very Long Artist Name 歌手姓名 ".repeat(5) else "Artist $index", index + 1)
        })
        var query by mutableStateOf("")
        var canLoadMore by mutableStateOf(canLoadMore)
        val targets = mutableListOf<LibraryNavigationTarget>()
        val onlineIntents = mutableListOf<OnlineLibraryIntent>()
        lateinit var browser: LibraryAlbumBrowserState
        lateinit var navigation: MusicDetailNavigation
        private var registry = SaveableStateRegistry(null) { true }
        private var frame = 0L
        private var scene = createScene()

        init { render() }

        private fun createScene() = ImageComposeScene(width, height, coroutineContext = UnconfinedTestDispatcher(scope.testScheduler)) {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                browser = rememberLibraryAlbumBrowserState()
                navigation = remember(browser) {
                    MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), TestMusicDetailQueries(), scope,
                        mutableStateOf(false), {}, libraryRootState = root, libraryAlbumState = browser)
                }
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale),
                    LocalPlatformDescriptor provides platform,
                    LocalMusicDetailNavigation provides navigation,
                    LocalPlayerPresentation provides remember { PlayerPresentation() },
                    LocalDockBottomInset provides dockInset.dp,
                ) {
                    ProvideUiResourceLanguage("en") {
                        LynMusicTheme(themeTokens = OCEAN_APP_THEME_TOKENS, textPalette = AppThemeTextPalette.Black) {
                            val visible = albums.filter { query.isBlank() || it.title.contains(query) }
                            val tracks = albums.map { Track("track-${it.id}", "source", "Song", it.artistName, it.title, albumId = it.id, mediaLocator = "", relativePath = it.id) }
                            MobilePageContent(
                                selectedTab = AppTab.Library, platform = platform, myState = MyState(),
                                libraryState = LibraryState(isLoadingContent = false, query = query, tracks = tracks,
                                    filteredTracks = tracks, albums = albums, filteredAlbums = visible),
                                onlineLibraryState = if (online) OnlineLibraryState(sourceId = "server", query = query,
                                    albums = visible.map { OnlineAlbumItem(it) }, canLoadMoreAlbums = canLoadMore) else OnlineLibraryState(),
                                playlistsState = PlaylistsState(), onlinePlaylistsState = OnlinePlaylistsState(),
                                favoritesState = FavoritesState(), onlineFavoritesState = OnlineFavoritesState(),
                                musicTagsState = MusicTagsState(), musicTagsEffects = emptyFlow(),
                                importState = ImportState(capabilities = platform.capabilities), settingsState = SettingsState(),
                                compact = width < 600, mobileLibraryHub = width < 600 || compactHub, phoneLibraryNavigation = width < 600,
                                onMyIntent = {}, onLibraryIntent = {}, onOnlineLibraryIntent = { onlineIntents += it },
                                onPlaylistsIntent = {}, onOnlinePlaylistsIntent = {}, onFavoritesIntent = {}, onOnlineFavoritesIntent = {},
                                onMusicTagsIntent = {}, onImportIntent = {}, onPlayerIntent = {}, onSettingsIntent = {},
                                onOpenBackgroundRunSettings = {}, onTabSelected = {},
                                onOpenLibraryNavigationTarget = { targets += it; navigation.open(it) },
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            )
                        }
                    }
                }
            }
        }

        fun render(frames: Int = 24) {
            repeat(frames) { Snapshot.sendApplyNotifications(); scene.render(++frame * 16_666_667L).close() }
        }
        fun rebuild(width: Int, height: Int) {
            val saved = registry.performSave()
            scene.close(); navigation.dispose()
            registry = SaveableStateRegistry(saved) { true }
            this.width = width; this.height = height
            scene = createScene(); render()
        }
        fun scrollGrid(index: Int, offset: Int = 0) { scope.launch { browser.grid.scrollToItem(index, offset) }; render() }
        fun chooseView(mode: AlbumViewMode) {
            if (width < 600 || compactHub) click("More actions") else clickTag("album-view-menu")
            clickTag("album-view-${mode.name}")
            assertEquals(mode, browser.viewMode.value)
        }
        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            val viewport = Rect(0f, 0f, width.toFloat(), height.toFloat())
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                .filter { !it.boundsInRoot.isEmpty && it.boundsInRoot.overlaps(viewport) }
        }
        private fun matches(node: SemanticsNode, label: String) = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        fun visible(label: String) = nodes().any { matches(it, label) }
        fun visibleTag(tag: String) = nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun tagged(tag: String) = nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag } ?: error("Missing $tag")
        private fun clickNode(node: SemanticsNode) {
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center); render()
        }
        fun click(label: String) = clickNode(nodes().lastOrNull { matches(it, label) } ?: error("Missing $label"))
        fun clickTag(tag: String) = clickNode(tagged(tag))
        fun dragScrollbar(toBottom: Boolean) = dragScrollbarTo(if (toBottom) 1f else 0f)
        fun dragScrollbarTo(fraction: Float): List<Pair<Int, Int>> {
            val track = tagged("library-album-scrollbar").boundsInRoot
            val start = if (fraction == 0.5f) Offset(track.center.x, track.top + 4) else track.center
            val end = Offset(track.center.x, track.top + 1 + (track.height - 2) * fraction)
            val positions = mutableListOf<Pair<Int, Int>>()
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, timeMillis = frame * 16)
            repeat(12) { step ->
                scene.sendPointerEvent(PointerEventType.Move, start + (end - start) * ((step + 1) / 12f),
                    type = PointerType.Touch, timeMillis = frame * 16)
                render(1)
                positions += browser.grid.firstVisibleItemIndex to browser.grid.firstVisibleItemScrollOffset
            }
            scene.sendPointerEvent(PointerEventType.Release, end, type = PointerType.Touch, timeMillis = frame * 16); render()
            return positions
        }
        fun scrollbarThumbTop(): Int {
            val track = tagged("library-album-scrollbar").boundsInRoot
            return scene.render(++frame * 16_666_667L).use { image ->
                val pixels = image.toComposeImageBitmap().toPixelMap()
                (track.top.toInt() until track.bottom.toInt()).first { y ->
                    val color = pixels[track.center.x.toInt(), y]
                    color.red > 0.6f && color.green < 0.4f && color.blue < 0.4f
                }
            }
        }
        fun capture(name: String) {
            val file = File("build/reports/album-grid/albums-$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() { scene.close(); navigation.dispose(); scope.cancel() }
    }
}
