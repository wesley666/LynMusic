@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.iwesley.lyn.music.automotive.ui

import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.details.*
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class ArtistDetailShellTest {
    @Test fun allThreeTabsFitActualCarShellsAndLastSongClearsThePlayer() = onUiThread {
        for ((width, height) in listOf(1280 to 720, 1920 to 720, 800 to 1280, 480 to 900, 640 to 400)) {
            Fixture(width, height, visual = true).use { ui ->
                val toolbar = ui.tagged("artist-toolbar").boundsInRoot
                assertTrue(ui.visible("正在播放"))
                for (tab in ArtistDetailTab.entries) {
                    ui.choose(tab)
                    assertTrue(ui.tagged("artist-pager").boundsInRoot.height > 48f)
                    assertEquals(toolbar, ui.tagged("artist-toolbar").boundsInRoot)
                    assertTrue(ui.tagged("artist-tab-${tab.name}").boundsInRoot.height >= 48f)
                    ui.capture("${width}x${height}-${tab.name.lowercase()}")
                }
                ui.choose(ArtistDetailTab.Songs)
                ui.collapseHeader()
                ui.scrollTo(96)
                val menu = ui.lastSongMenu()
                assertTrue(menu.boundsInRoot.bottom <= ui.textNode("正在播放").boundsInRoot.top,
                    "Last menu must clear the player at $width x $height")
                assertTrue(ui.entry.artistHeaderState.collapsedFraction > 0.5f)
                assertEquals(toolbar, ui.tagged("artist-toolbar").boundsInRoot)
                assertEquals(1, ui.trackRequests)
                assertEquals(1, ui.albumRequests)
                assertTrue(ui.intents.isEmpty())
                ui.capture("${width}x${height}-bottom")
            }
        }
    }

    @Test fun shellReplacementAndNestedAlbumKeepAllArtistStateAndQueries() = onUiThread {
        Fixture(1280, 720).use { ui ->
            val entry = ui.entry
            ui.collapseHeader()
            ui.scrollTo(30, 9)
            ui.choose(ArtistDetailTab.Albums)
            ui.scrollTo(25, 7)
            ui.choose(ArtistDetailTab.Favorites)
            ui.scrollTo(20, 5)
            val fraction = entry.artistHeaderState.collapsedFraction
            ui.resize(480, 900)
            assertSame(entry, ui.entry)
            assertEquals(ArtistDetailTab.Favorites, entry.selectedArtistTab)
            assertEquals(20, entry.favoriteListState.firstVisibleItemIndex)
            assertEquals(5, entry.favoriteListState.firstVisibleItemScrollOffset)
            assertEquals(fraction, entry.artistHeaderState.collapsedFraction, 0.02f)
            ui.choose(ArtistDetailTab.Albums)
            assertEquals(25, entry.albumListState.firstVisibleItemIndex)
            assertEquals(7, entry.albumListState.firstVisibleItemScrollOffset)
            ui.navigation.open(LibraryNavigationTarget.OnlineAlbum("server", "album", "Album"))
            ui.render()
            ui.navigation.pop(); ui.render()
            assertEquals(ArtistDetailTab.Albums, entry.selectedArtistTab)
            ui.resize(1280, 720)
            ui.choose(ArtistDetailTab.Songs)
            assertEquals(30, entry.contentListState.firstVisibleItemIndex)
            assertEquals(9, entry.contentListState.firstVisibleItemScrollOffset)
            assertEquals(1, ui.trackRequests)
            assertEquals(1, ui.albumRequests)
            assertTrue(ui.intents.isEmpty())
        }
    }

    @Test fun fiveDisplayScalesAndLargeTextKeepActionsReachableInBothThemes() = onUiThread {
        for ((width, height) in listOf(1280 to 720, 480 to 900)) {
            for (scale in AppDisplayScalePreset.entries) Fixture(width, height, density = scale.scale,
                longNames = true, dark = scale.ordinal % 2 == 0).use { ui ->
                assertTrue(ui.tagged("artist-pager").boundsInRoot.height > 48f)
                ui.choose(ArtistDetailTab.Favorites)
                ui.scrollTo(96)
                assertTrue(ui.lastSongMenu().boundsInRoot.bottom <= ui.textNode("Now playing").boundsInRoot.top)
            }
        }
        Fixture(480, 900, fontScale = 2f, longNames = true).use { ui ->
            assertTrue(ui.hasTag("artist-tabs-scrollable"))
            // Pager gestures also make a tab beyond the initial horizontal viewport reachable.
            ui.swipeToFavorites()
            assertTrue(ui.tagged("artist-tab-Favorites").boundsInRoot.width > 0f)
            ui.scrollTo(96)
            assertTrue(ui.lastSongMenu().boundsInRoot.bottom <= ui.textNode("Now playing").boundsInRoot.top)
            ui.capture("480x900-large-text")
        }
    }

    @Test fun headerAndSongColumnsUseActualPageWidthAndHeight() = onUiThread {
        // The portrait shell has no sidebar, so these widths exercise the content breakpoints directly.
        for ((width, horizontal, wide) in listOf(Triple(559, false, false), Triple(560, false, true),
            Triple(599, false, true), Triple(600, true, true))) Fixture(width, 1280).use { ui ->
            assertEquals(horizontal, ui.hasTag("artist-header-horizontal"))
            assertEquals(wide, ui.visible("04:15"))
        }
        for (pageHeight in listOf(419, 420)) Fixture(640, 500).use { ui ->
            val currentHeight = ui.tagged("music-detail-page-artist").boundsInRoot.height.toInt()
            ui.resize(640, 500 + pageHeight - currentHeight)
            assertEquals(pageHeight.toFloat(), ui.tagged("music-detail-page-artist").boundsInRoot.height)
            assertEquals(if (pageHeight < 420) 48f else 80f, ui.tagged("artist-avatar").boundsInRoot.width)
        }
    }

    @Test fun shortPortraitWindowAccountsForTheNavigationAndMiniPlayerDock() = onUiThread {
        Fixture(480, 560).use { ui ->
            assertTrue(ui.hasTag("artist-header-horizontal"))
            assertEquals(48f, ui.tagged("artist-avatar").boundsInRoot.width)
            assertTrue(ui.tagged("artist-pager").boundsInRoot.height > 48f)
            ui.scrollTo(96)
            assertTrue(ui.lastSongMenu().boundsInRoot.bottom <= ui.textNode("Now playing").boundsInRoot.top)
        }
    }

    @Test fun shortWindowsKeepTabsAndNinetySixDpOfVisibleContentAcrossScaleAndFontCombinations() = onUiThread {
        for ((width, height) in listOf(640 to 400, 480 to 560)) {
            for (scale in AppDisplayScalePreset.entries) for (font in listOf(1f, 1.5f, 2f)) {
                Fixture(width, height, density = scale.scale, fontScale = font, longNames = true,
                    dark = scale.ordinal % 2 == 0).use { ui ->
                    for (tab in ArtistDetailTab.entries) {
                        ui.choose(tab)
                        val tabs = ui.tagged("artist-tabs").boundsInRoot
                        val pager = ui.visiblePagerBounds()
                        assertTrue(tabs.bottom <= ui.playerTop(), "$width x $height, $scale, font=$font: tabs overlap player")
                        assertTrue(pager.height >= 96f * scale.scale - 1f,
                            "$width x $height, $scale, font=$font: visible content=${pager.height}")
                    }
                    ui.choose(ArtistDetailTab.Songs)
                    val before = ui.entry.contentListState.firstVisibleItemIndex
                    repeat(3) { if (ui.entry.contentListState.firstVisibleItemIndex == before) ui.collapseHeader() }
                    assertTrue(ui.entry.contentListState.firstVisibleItemIndex > before,
                        "$width x $height, $scale, font=$font: upward gestures must collapse the header and scroll the list")
                    ui.scrollTo(96)
                    assertTrue(ui.lastSongMenu().boundsInRoot.bottom <= ui.playerTop())
                    ui.clickLastSongMenu()
                    assertTrue(ui.hasOpenSongMenu())
                    assertTrue(ui.intents.isEmpty())
                    if (scale == AppDisplayScalePreset.ExtraLarge && font == 2f) {
                        ui.dismissMenu()
                        ui.capture("fixed-${width}x${height}-scale120-font200")
                    }
                }
            }
        }
    }

    @Test fun heightLimitedHeaderRestoresUserCollapseAndListPositionsAfterResizeAndChildNavigation() = onUiThread {
        for (fraction in listOf(0f, 0.65f)) Fixture(1280, 720, fontScale = 1.5f, longNames = true).use { ui ->
            val entry = ui.entry
            entry.artistHeaderState.heightOffset = entry.artistHeaderState.heightOffsetLimit * fraction
            ui.scrollTo(20, 5)
            ui.choose(ArtistDetailTab.Albums)
            ui.scrollTo(25, 7)
            ui.resize(640, 400)
            assertFalse(ui.hasTag("artist-avatar"))
            assertTrue(ui.tagged("artist-toolbar-title").config[SemanticsProperties.Text].single().text.startsWith("A very long"))
            assertEquals(fraction, entry.artistHeaderState.collapsedFraction, 0.02f)
            ui.collapseHeader()
            assertEquals(fraction, entry.artistHeaderState.collapsedFraction, 0.02f)
            val albumIndex = entry.albumListState.firstVisibleItemIndex
            val albumOffset = entry.albumListState.firstVisibleItemScrollOffset
            ui.navigation.open(LibraryNavigationTarget.OnlineAlbum("server", "album", "Album")); ui.render()
            ui.navigation.pop(); ui.render()
            assertEquals(ArtistDetailTab.Albums, entry.selectedArtistTab)
            assertEquals(albumIndex, entry.albumListState.firstVisibleItemIndex)
            ui.resize(1280, 720)
            assertEquals(fraction, entry.artistHeaderState.collapsedFraction, 0.02f)
            assertEquals(albumIndex, entry.albumListState.firstVisibleItemIndex)
            assertEquals(albumOffset, entry.albumListState.firstVisibleItemScrollOffset)
            ui.choose(ArtistDetailTab.Songs)
            assertEquals(20, entry.contentListState.firstVisibleItemIndex)
            assertEquals(5, entry.contentListState.firstVisibleItemScrollOffset)
            assertEquals(1, ui.trackRequests)
            assertEquals(1, ui.albumRequests)
        }
    }

    @Test fun avatarLetterFitsAllAvatarSizesWithLargeLatinAndChineseFonts() = onUiThread {
        for ((width, height, size) in listOf(Triple(1280, 480, 48f), Triple(1280, 720, 80f), Triple(480, 900, 88f))) {
            for (font in listOf(1f, 1.5f, 2f)) for (chinese in listOf(false, true)) {
                Fixture(width, height, fontScale = font, visual = chinese).use { ui ->
                    val avatar = ui.tagged("artist-avatar").boundsInRoot
                    assertEquals(size, avatar.width)
                    val letter = ui.tagged("artist-avatar-letter").boundsInRoot
                    assertTrue(avatar.contains(letter.topLeft) && avatar.contains(letter.bottomRight))
                    val layout = ui.textLayout(if (chinese) "李" else "A")
                    assertEquals(1, layout.lineCount)
                    assertFalse(layout.didOverflowHeight, "size=$size font=$font chinese=$chinese")
                    assertTrue(layout.getLineBottom(0) <= size - 8f + 1f)
                    if (size == 48f && font == 2f && chinese) ui.capture("fixed-avatar-font200-zh")
                }
            }
        }
    }

    @Test fun durationStaysOnOneLineForLargeFontsAndThreeDigitMinutes() = onUiThread {
        for (scale in listOf(1f, 1.2f)) for (font in listOf(1f, 1.5f, 2f)) {
            Fixture(1920, 720, density = scale, fontScale = font).use { ui ->
                assertEquals(1, ui.textLayout("04:15").lineCount, "scale=$scale font=$font")
                ui.updateDuration(120L * 60 * 1000)
                assertEquals(1, ui.textLayout("120:00").lineCount)
                if (scale == 1f && font == 1.5f) ui.capture("fixed-duration-font150")
            }
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(width: Int, height: Int, val density: Float = 1f, val fontScale: Float = 1f,
        val visual: Boolean = false, longNames: Boolean = false, val dark: Boolean = false) : AutoCloseable {
        var width by mutableStateOf(width)
        var height by mutableStateOf(height)
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        val intents = mutableListOf<PlayerIntent>()
        var trackRequests = 0
        var albumRequests = 0
        private val title = if (longNames) "A very long artist name with multiple collaborators and additional information"
            else if (visual) "李志" else "Artist A"
        private var tracks = (1..96).map { index -> Track("song-$index", "server",
            if (visual) "山阴路的夏天 $index" else "Song $index", title, if (visual) "我爱南京" else "Album",
            durationMs = 255000, mediaLocator = "", relativePath = "$index", artistId = "artist", albumId = "album") }
        private var favoriteIds by mutableStateOf((if (visual) tracks.take(18) else tracks).map { it.id }.toSet())
        private val queries = TestMusicDetailQueries(
            artistTracks = { flow { trackRequests++; emit(tracks) } },
            artistAlbums = { flow { albumRequests++; emit((1..if (visual) 7 else 96).map { index ->
                MusicDetailAlbum(Album("album-$index", if (visual) "我爱南京 $index" else "Album $index", title, trackCount = 14))
            }) } },
        )
        val navigation = MusicDetailNavigation(androidx.navigation3.runtime.NavBackStack<androidx.navigation3.runtime.NavKey>(MusicDetailRoute()),
            queries, scope, mutableStateOf(false), {})
        private val route = navigation.run { open(LibraryNavigationTarget.OnlineArtist("server", "artist", title)); top }
        val entry get() = navigation.entry(route)
        private val platform = PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        private val player = PlayerState(snapshot = PlaybackSnapshot(queue = listOf(Track("playing", "local",
            if (visual) "正在播放" else "Now playing", mediaLocator = "", relativePath = "playing")), currentIndex = 0))
        private var frame = 0L
        private var scene = createScene()
        init { render() }

        private fun createScene() = ImageComposeScene(width, height, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale),
                LocalPlatformDescriptor provides platform, LocalMusicDetailNavigation provides navigation,
                LocalPlayerPresentation provides remember { PlayerPresentation() },
                LocalTrackListActionBindings provides TrackListActionBindings(
                    favoriteState = { TrackFavoriteUiState(it.id in favoriteIds) },
                    onSetFavorite = { track, liked -> favoriteIds = if (liked) favoriteIds + track.id else favoriteIds - track.id },
                )) {
                ProvideUiResourceLanguage(if (visual) "zh-CN" else "en") {
                    LynMusicTheme(themeTokens = if (dark) CLASSIC_APP_THEME_TOKENS else OCEAN_APP_THEME_TOKENS,
                        textPalette = if (dark) AppThemeTextPalette.White else AppThemeTextPalette.Black) { Shell() }
                }
            }
        }

        @Composable private fun Shell() {
            if (width > height) AutomotiveLandscapeShell(
                navigationRailState = rememberWideNavigationRailState(),
                selectedTab = AppTab.Library, onTabSelected = { navigation.clear() }, platform = platform,
                myState = MyState(), libraryState = LibraryState(), onlineLibraryState = OnlineLibraryState(),
                playlistsState = PlaylistsState(), onlinePlaylistsState = OnlinePlaylistsState(),
                favoritesState = FavoritesState(), onlineFavoritesState = OnlineFavoritesState(),
                importState = ImportState(capabilities = platform.capabilities), playerState = player,
                settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = {}, onOnlineLibraryIntent = {},
                onPlaylistsIntent = {}, onOnlinePlaylistsIntent = {}, onFavoritesIntent = {}, onOnlineFavoritesIntent = {},
                onImportIntent = {}, onPlayerIntent = { intents += it }, onSettingsIntent = {},
                onOpenBackgroundRunSettings = {}, onOpenLibraryNavigationTarget = { navigation.open(it) }, onOpenAddToPlaylist = {},
            ) else AutomotivePortraitShell(
                portraitNavigation = true, mobilePortraitMiniPlayer = true, hideMiniPlayerBar = false,
                selectedTab = AppTab.Library, onTabSelected = { navigation.clear() }, platform = platform,
                myState = MyState(), libraryState = LibraryState(), onlineLibraryState = OnlineLibraryState(),
                playlistsState = PlaylistsState(), onlinePlaylistsState = OnlinePlaylistsState(),
                favoritesState = FavoritesState(), onlineFavoritesState = OnlineFavoritesState(),
                importState = ImportState(capabilities = platform.capabilities), playerState = player,
                settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = {}, onOnlineLibraryIntent = {},
                onPlaylistsIntent = {}, onOnlinePlaylistsIntent = {}, onFavoritesIntent = {}, onOnlineFavoritesIntent = {},
                onImportIntent = {}, onPlayerIntent = { intents += it }, onSettingsIntent = {},
                onOpenBackgroundRunSettings = {}, onOpenLibraryNavigationTarget = { navigation.open(it) }, onOpenAddToPlaylist = {},
            )
        }
        fun render(frames: Int = 40) {
            repeat(frames) { scheduler.runCurrent(); Snapshot.sendApplyNotifications(); scene.render(++frame * 16_666_667L).close() }
        }
        fun resize(newWidth: Int, newHeight: Int) {
            width = newWidth; height = newHeight
            scene.constraints = Constraints.fixed(width, height); render()
        }
        fun choose(tab: ArtistDetailTab) {
            if (entry.selectedArtistTab == tab) return
            repeat(2) {
                if (!hasTag("artist-tab-${tab.name}") && entry.selectedArtistTab != tab) {
                    swipePage(tab.ordinal > entry.selectedArtistTab.ordinal)
                }
            }
            if (entry.selectedArtistTab == tab) return
            clickNode(tagged("artist-tab-${tab.name}"))
            assertEquals(tab, entry.selectedArtistTab)
        }
        fun swipeToFavorites() {
            repeat(2) { if (entry.selectedArtistTab != ArtistDetailTab.Favorites) swipePage(true) }
            assertEquals(ArtistDetailTab.Favorites, entry.selectedArtistTab)
        }
        private fun swipePage(forward: Boolean) {
            val viewport = visiblePagerBounds()
            drag(androidx.compose.ui.geometry.Offset(viewport.left + viewport.width * if (forward) 0.85f else 0.15f, viewport.center.y),
                androidx.compose.ui.geometry.Offset(viewport.left + viewport.width * if (forward) 0.15f else 0.85f, viewport.center.y))
        }
        fun collapseHeader() {
            val viewport = visiblePagerBounds()
            drag(androidx.compose.ui.geometry.Offset(viewport.center.x, viewport.bottom - 8f),
                androidx.compose.ui.geometry.Offset(viewport.center.x, viewport.top + 8f))
        }
        private fun drag(start: androidx.compose.ui.geometry.Offset, end: androidx.compose.ui.geometry.Offset) {
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, timeMillis = frame * 16)
            render(1)
            repeat(12) { step ->
                scene.sendPointerEvent(PointerEventType.Move, start + (end - start) * ((step + 1) / 12f),
                    type = PointerType.Touch, timeMillis = frame * 16)
                render(1)
            }
            scene.sendPointerEvent(PointerEventType.Release, end, type = PointerType.Touch, timeMillis = frame * 16)
            render(80)
        }
        fun playerTop(): Float {
            val player = textNode(if (visual) "正在播放" else "Now playing")
            return generateSequence(player.parent) { it.parent }.first { it.config.contains(SemanticsActions.OnClick) }.boundsInRoot.top
        }
        fun visiblePagerBounds(): Rect {
            val pager = tagged("artist-pager").boundsInRoot
            return Rect(pager.left, pager.top, pager.right, minOf(pager.bottom, playerTop()))
        }
        private fun clickNode(node: SemanticsNode) {
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center, type = PointerType.Touch)
            render(80)
        }
        fun clickLastSongMenu() = clickNode(lastSongMenu())
        fun hasOpenSongMenu() = visible(tracks.last().title) && nodes().any { it.config.contains(SemanticsActions.Dismiss) }
        fun dismissMenu() { nodes().first { it.config.contains(SemanticsActions.Dismiss) }.config[SemanticsActions.Dismiss].action!!.invoke(); render() }
        fun updateDuration(durationMs: Long) {
            tracks = tracks.map { it.copy(durationMs = durationMs) }
            entry.artistTracks.retry(); render()
        }
        fun textLayout(label: String): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(textNode(label).config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
            return results.single()
        }
        fun scrollTo(index: Int, offset: Int = 0) {
            val list = when (entry.selectedArtistTab) {
                ArtistDetailTab.Songs -> entry.contentListState
                ArtistDetailTab.Albums -> entry.albumListState
                ArtistDetailTab.Favorites -> entry.favoriteListState
            }
            scope.launch { list.scrollToItem(index, offset) }; render()
        }
        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            val viewport = Rect(0f, 0f, width.toFloat(), height.toFloat())
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                .filter { !it.boundsInRoot.isEmpty && it.boundsInRoot.overlaps(viewport) }
        }
        fun tagged(tag: String) = nodes().last { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun hasTag(tag: String) = nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun textNode(label: String) = nodes().last { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true }
        fun visible(label: String) = nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true }
        fun lastSongMenu() = nodes().last { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text -> text.contains(tracks.last().title) } == true }
        fun capture(name: String) {
            val file = File("build/reports/artist-detail/automotive-$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() { scene.close(); navigation.dispose(); scope.cancel() }
    }
}
