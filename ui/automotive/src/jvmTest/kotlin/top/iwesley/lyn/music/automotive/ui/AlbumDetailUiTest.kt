@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.iwesley.lyn.music.automotive.ui

import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
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
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class AlbumDetailUiTest {
    @Test fun capturesSelectedLayoutsInsideActualShells() = onUiThread {
        for ((width, height) in listOf(1280 to 720, 1920 to 720, 800 to 1280, 480 to 900, 640 to 400)) {
            Fixture(width, height, visual = true, count = 9).use { ui ->
                assertEquals(width >= 1280, ui.visibleTag("album-content-split"))
                assertTrue(ui.visibleTag("album-toolbar"))
                assertTrue(ui.visible("作词：罗大佑"), "The actual player must be present")
                assertEquals(1, ui.requests)
                assertTrue(ui.intents.isEmpty())
                if (width >= 1280) {
                    assertTrue(ui.tagged("album-summary").boundsInRoot.right < ui.tagged("album-tracks").boundsInRoot.left)
                    assertTrue(ui.visibleTag("album-column-duration"))
                }
                ui.capture("$width-$height")
                ui.scrollTo(9)
                val lastMenu = ui.textNode("河 — 9 的更多操作")
                assertTrue(lastMenu.boundsInRoot.bottom <= ui.playerTop(),
                    "Last song menu must scroll clear of the player")
                ui.capture("$width-$height-bottom")
            }
        }
    }

    @Test fun splitThresholdUsesAvailableContentAfterSidebarAndPlayer() = onUiThread {
        for ((pageWidth, visibleHeight, split) in listOf(Triple(899, 360, false), Triple(900, 360, true),
            Triple(900, 239, false), Triple(900, 240, true))) {
            Fixture(1280, 720).use { ui ->
                ui.resizeContent(pageWidth, visibleHeight)
                assertEquals(split, ui.visibleTag("album-content-split"), "$pageWidth x $visibleHeight")
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
                assertTrue(button.bottom <= ui.playerTop())
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

    @Test fun coverSizesAndPaddingFollowVisibleHeightAndPageWidth() = onUiThread {
        for (width in listOf(599, 600)) Fixture(width, 1280).use { ui ->
            assertFalse(ui.visibleTag("album-content-split"))
            assertEquals(if (width == 599) 176f else 220f, ui.tagged("album-cover").boundsInRoot.width)
            assertEquals(if (width == 599) 20f else 24f, ui.tagged("album-tracks").boundsInRoot.left)
        }
        for (height in listOf(359, 360)) for (width in listOf(899, 900)) Fixture(1280, 720).use { ui ->
            ui.resizeContent(width, height)
            val expected = if (width == 899) { if (height == 359) 112f else 220f }
                else if (height == 359) 160f else 246f
            assertEquals(expected, ui.tagged("album-cover").boundsInRoot.width, 1f)
        }
    }

    @Test fun largeFontsCanFallBackToStackedWhileShortWideWindowsKeepSplit() = onUiThread {
        Fixture(1280, 720, fontScale = 2f).use { ui ->
            ui.resizeContent(900, 360)
            assertTrue(ui.visibleTag("album-content-stacked"))
            ui.resizeContent(1000, 240)
            assertTrue(ui.visibleTag("album-content-split"))
            assertTrue(ui.tagged("album-tracks").boundsInRoot.width >= 640f)
        }
        Fixture(1920, 400).use { ui ->
            assertTrue(ui.visibleTag("album-content-split"))
            ui.scrollSummaryTo(Int.MAX_VALUE)
            val play = ui.tagged("album-play-all").boundsInRoot
            assertTrue(play.top >= ui.tagged("album-toolbar").boundsInRoot.bottom)
            assertTrue(play.bottom <= ui.playerTop())
        }
    }

    @Test fun songColumnsUseTheirOwnWidthInPortraitAndScaleTheirDuration() = onUiThread {
        for (font in listOf(1f, 1.5f, 2f)) {
            val threshold = (520 * font).toInt()
            val padding = if (threshold + 40 < 600) 40 else 48
            for (delta in listOf(-1, 0)) Fixture(threshold + padding + delta, 2000, fontScale = font).use { ui ->
                ui.scrollTo(1)
                assertFalse(ui.visibleTag("album-content-split"))
                assertEquals(delta == 0, ui.visible("04:15"), "font=$font delta=$delta")
            }
        }
        for (font in listOf(1f, 1.5f, 2f)) Fixture(1920, 720, fontScale = font).use { ui ->
            ui.tracks = ui.tracks.map { it.copy(durationMs = 7_200_000) }
            ui.entry.retry(); ui.render()
            assertEquals(1, ui.textLayout("120:00").lineCount)
            assertFalse(ui.textLayout("120:00").didOverflowHeight)
            val title = ui.tagged("album-column-duration").boundsInRoot
            val duration = ui.textNode("120:00").boundsInRoot
            assertEquals(56f * font, title.width, 1f)
            assertEquals(title.left, duration.left, 1f)
            assertEquals(title.right, duration.right, 1f)
        }
    }

    @Test fun allScalesAndFontsKeepLastMenusAndPlaybackAboveActualPlayer() = onUiThread {
        for ((width, height) in listOf(1280 to 720, 480 to 900, 640 to 400)) {
            for (scale in AppDisplayScalePreset.entries) for (font in listOf(1f, 1.5f, 2f)) {
                Fixture(width, height, density = scale.scale, fontScale = font,
                    longNames = true, dark = scale.ordinal % 2 == 0).use { ui ->
                    val toolbar = ui.tagged("album-toolbar").boundsInRoot
                    val viewport = ui.tagged("album-tracks").boundsInRoot
                    assertTrue((minOf(viewport.bottom, ui.playerTop()) - viewport.top) / scale.scale >= 96f)
                    if (ui.visibleTag("album-content-split")) ui.scrollSummaryTo(Int.MAX_VALUE)
                    else {
                        val header = ui.entry.contentListState.layoutInfo.visibleItemsInfo.first { it.index == 0 }
                        ui.scrollTo(0, (header.size - 140 * scale.scale).toInt().coerceAtLeast(0))
                    }
                    val play = ui.tagged("album-play-all").boundsInRoot
                    assertTrue(play.height + 1f >= 48f * scale.scale,
                        "$width/$height/$scale/$font: play target $play")
                    assertTrue(play.top >= toolbar.bottom && play.bottom <= ui.playerTop(), "$width/$height/$scale/$font: $play")
                    ui.scrollTo(ui.tracks.size)
                    val menu = ui.textNode("More actions for Song ${ui.tracks.lastIndex}").boundsInRoot
                    assertTrue(menu.top >= toolbar.bottom && menu.bottom <= ui.playerTop())
                    if (scale == AppDisplayScalePreset.ExtraLarge && font == 2f) ui.capture("large-$width-$height")
                    ui.click("More actions for Song ${ui.tracks.lastIndex}")
                    assertTrue(ui.visible("View artist"))
                    assertTrue(ui.intents.isEmpty())
                    assertEquals(1, ui.requests)
                }
            }
        }
    }

    @Test fun sidebarAndOrientationChangesRetainSongsAndSummaryIndependently() = onUiThread {
        Fixture(1280, 500, fontScale = 1.5f, longNames = true).use { ui ->
            ui.scrollSummaryTo(Int.MAX_VALUE)
            val summary = ui.entry.albumSummaryScrollState.value
            assertTrue(summary > 0)
            ui.scrollTo(24, 11)
            ui.clickTag("automotive-navigation-toggle")
            ui.clickTag("automotive-navigation-toggle")
            assertEquals(24, ui.entry.contentListState.firstVisibleItemIndex)
            ui.resize(480, 900)
            ui.resize(1280, 500)
            assertEquals(summary, ui.entry.albumSummaryScrollState.value)
            assertEquals(24, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(11, ui.entry.contentListState.firstVisibleItemScrollOffset)
            ui.navigation.open(LibraryNavigationTarget.OnlineArtist("server", "artist", "Artist A")); ui.render()
            ui.navigation.pop(); ui.render()
            assertEquals(summary, ui.entry.albumSummaryScrollState.value)
            assertEquals(24, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(1, ui.requests)
        }
    }

    @Test fun aNewNavigationOwnerRestoresBothScrollsAndReloadsOnlyOnce() = onUiThread {
        Fixture(1280, 500, fontScale = 1.5f, longNames = true).use { ui ->
            ui.scrollSummaryTo(Int.MAX_VALUE)
            val summary = ui.entry.albumSummaryScrollState.value
            ui.scrollTo(24, 11)
            val original = ui.entry
            ui.recreateOwner()
            assertNotSame(original, ui.entry)
            assertEquals(24, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(11, ui.entry.contentListState.firstVisibleItemScrollOffset)
            assertEquals(summary, ui.entry.albumSummaryScrollState.value)
            assertEquals(2, ui.requests)
        }
    }

    @Test fun realVerticalScrollingDoesNotTriggerPlaybackAndKeepsToolbarFixed() = onUiThread {
        for ((width, height) in listOf(1280 to 720, 480 to 900)) Fixture(width, height).use { ui ->
            val toolbar = ui.tagged("album-toolbar").boundsInRoot
            ui.scrollTo(1)
            ui.scrollSongsWithTouch()
            assertTrue(ui.entry.contentListState.firstVisibleItemIndex > 1)
            assertEquals(toolbar, ui.tagged("album-toolbar").boundsInRoot)
            assertTrue(ui.intents.isEmpty())
        }
    }

    @Test fun localAndOnlineRequestsPreserveTheirContextAndUnavailableSourcesRetry() = onUiThread {
        for (online in listOf(false, true)) Fixture(480, 900, online = online).use { ui ->
            assertEquals(if (online) "server" else null, ui.observedRequests.single().onlineSourceId)
            assertEquals(if (online) "album" else libraryAlbumId("Artist A", "Album A"), ui.observedRequests.single().albumId)
            ui.scrollTo(20, 7)
            ui.unavailable = true; ui.entry.retry(); ui.render()
            assertTrue(ui.visibleTag("album-error"))
            assertTrue(ui.visibleTag("album-toolbar"))
            assertFalse(ui.visibleTag("album-play-all"))
            ui.unavailable = false; ui.click("Retry")
            assertEquals(20, ui.entry.contentListState.firstVisibleItemIndex)
            assertEquals(7, ui.entry.contentListState.firstVisibleItemScrollOffset)
            assertEquals(3, ui.requests)
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(
        width: Int,
        height: Int,
        val visual: Boolean = false,
        val count: Int = 60,
        val fontScale: Float = 1f,
        val longNames: Boolean = false,
        val gate: CompletableDeferred<Unit>? = null,
        val density: Float = 1f,
        val dark: Boolean = false,
        val online: Boolean = true,
    ) : AutoCloseable {
        var width by mutableStateOf(width)
        var height by mutableStateOf(height)
        private var registry = SaveableStateRegistry(null) { true }
        private val scheduler = TestCoroutineScheduler()
        private var scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        private val platform = PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        private var frame = 0L
        var requests = 0
        var fail = false
        var empty = false
        var unavailable = false
        val observedRequests = mutableListOf<AlbumDetailRequest>()
        var selectedTab by mutableStateOf(AppTab.Library)
        var favoriteIds by mutableStateOf(if (visual) setOf("track-1", "track-2") else emptySet())
        val intents = mutableListOf<PlayerIntent>()
        private val albumTitle = if (longNames) "A very long album title that must fit within two lines without pushing actions outside the screen" else if (visual) "夏" else "Album A"
        private val artist = if (longNames) "A very long artist name with several collaborators" else if (visual) "陈升" else "Artist A"
        var tracks = (0 until count).map { index ->
            val titles = listOf("Summer", "南风", "镜子", "四条腿", "河")
            Track("track-$index", "server", if (visual) if (index < 5) titles[index] else "河 — ${index + 1}" else "Song $index",
                artist, albumTitle, durationMs = 255000, mediaLocator = "", relativePath = "$index",
                albumId = "album", artistId = "artist")
        }
        private val player = PlayerState(snapshot = PlaybackSnapshot(
            queue = listOf(Track("playing", "local", if (visual) "作词：罗大佑" else "Now playing", "陈升", "夏", mediaLocator = "", relativePath = "playing")),
            currentIndex = 0, positionMs = 97000, durationMs = 255000, canSeek = true,
        ))
        private val queries = TestMusicDetailQueries(album = { request -> flow {
            observedRequests += request
            requests++
            gate?.await()
            if (unavailable) throw MusicDetailUnavailableException()
            if (fail) error("Controlled album failure")
            emit(AlbumDetailData(albumTitle, artist, tracks = if (empty) emptyList() else tracks))
        } })
        var navigation = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), queries, scope, mutableStateOf(false), {})
        private val albumRoute = navigation.run { open(if (online) LibraryNavigationTarget.OnlineAlbum("server", "album", albumTitle, artist)
            else LibraryNavigationTarget.Album(libraryAlbumId(artist, albumTitle))); top }
        val entry get() = navigation.entry(albumRoute)
        private var scene = createScene()

        init { render() }

        private fun createScene() = ImageComposeScene(width, height, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            CompositionLocalProvider(
                LocalSaveableStateRegistry provides registry,
                LocalDensity provides Density(density, fontScale),
                LocalPlatformDescriptor provides platform,
                LocalMusicDetailNavigation provides navigation,
                LocalPlayerPresentation provides remember { PlayerPresentation() },
                LocalTrackListActionBindings provides TrackListActionBindings(
                    favoriteState = { TrackFavoriteUiState(it.id in favoriteIds) },
                    onSetFavorite = { track, liked -> favoriteIds = if (liked) favoriteIds + track.id else favoriteIds - track.id },
                ),
            ) {
                ProvideUiResourceLanguage(if (visual) "zh-CN" else "en") {
                    LynMusicTheme(themeTokens = if (dark) CLASSIC_APP_THEME_TOKENS else OCEAN_APP_THEME_TOKENS,
                        textPalette = if (dark) AppThemeTextPalette.White else AppThemeTextPalette.Black) {
                        Shell()
                    }
                }
            }
        }

        @Composable private fun Shell() {
            if (width > height) AutomotiveLandscapeShell(
                navigationRailState = rememberWideNavigationRailState(),
                selectedTab = selectedTab, onTabSelected = { navigation.clear(); selectedTab = it }, platform = platform,
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
                selectedTab = selectedTab, onTabSelected = { navigation.clear(); selectedTab = it }, platform = platform,
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
        fun rebuild(newWidth: Int, newHeight: Int) {
            scene.close(); width = newWidth; height = newHeight
            scene = createScene(); render()
        }
        fun resize(newWidth: Int, newHeight: Int) {
            width = newWidth; height = newHeight
            scene.constraints = Constraints.fixed(width, height); render()
        }
        fun resizeContent(pageWidth: Int, visibleHeight: Int) {
            val page = tagged("music-detail-page-album").boundsInRoot
            val toolbar = tagged("album-toolbar").boundsInRoot
            // The landscape shell removes the entire player slot, including its outer spacing.
            val currentHeight = page.bottom - toolbar.bottom
            resize(width + ((pageWidth * density) - page.width).toInt(),
                height + ((visibleHeight * density) - currentHeight).toInt())
        }
        fun recreateOwner() {
            val saved = registry.performSave()
            val routes = navigation.backStack.toTypedArray()
            scene.close(); navigation.dispose(); scope.cancel()
            registry = SaveableStateRegistry(saved) { true }
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
            navigation = MusicDetailNavigation(NavBackStack<NavKey>(*routes), queries, scope, mutableStateOf(false), {})
            scene = createScene(); render()
        }
        fun scrollSummaryTo(offset: Int) {
            scope.launch { entry.albumSummaryScrollState.scrollTo(offset) }; render()
        }
        fun playerTop(): Float {
            val player = textNode(if (visual) "作词：罗大佑" else "Now playing")
            return generateSequence(player.parent) { it.parent }
                .first { it.config.contains(SemanticsActions.OnClick) }.boundsInRoot.top
        }
        fun textLayout(label: String): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(textNode(label).config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
            return results.single()
        }
        fun scrollSongsWithTouch() {
            val list = tagged("album-tracks").boundsInRoot
            val start = Offset(list.center.x, minOf(list.bottom, playerTop()) - 12f)
            val end = Offset(list.center.x, list.top + 12f)
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
            val file = File("build/reports/album-detail/automotive-$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() { scene.close(); navigation.dispose(); scope.cancel() }
    }
}
