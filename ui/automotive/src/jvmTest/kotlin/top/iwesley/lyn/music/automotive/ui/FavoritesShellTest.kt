@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
import top.iwesley.lyn.music.feature.favorites.*
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import androidx.compose.ui.state.ToggleableState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class FavoritesShellTest {
    @Test fun actualShellsShowSongsAndTheLastMenuClearsThePlayer() = onUiThread {
        for ((w, h) in listOf(1280 to 720, 1920 to 720, 800 to 1280, 480 to 900, 640 to 400)) Fixture(w, h, visual = true).use { ui ->
            assertTrue(ui.visible("96 首歌曲"))
            assertTrue(ui.visible("播放全部"))
            ui.capture("$w-$h")
            ui.scrollTo(99)
            val menu = ui.label("山阴路的夏天 96 的更多操作").boundsInRoot
            assertTrue(menu.bottom <= ui.playerTop(), "$w/$h: $menu")
            ui.capture("$w-$h-bottom")
            ui.click("山阴路的夏天 96 的更多操作")
            assertTrue(ui.visible("查看歌手"))
            assertTrue(ui.intents.isEmpty())
        }
    }
    @Test fun orientationTabsDetailsAndHostRecreationKeepTheSameSongPosition() = onUiThread {
        Fixture(1280, 720).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            ui.resize(480, 900); assertEquals(position, ui.position(), 0.02f)
            ui.selectedTab = AppTab.Playlists; ui.render()
            ui.selectedTab = AppTab.Favorites; ui.render(); assertEquals(position, ui.position(), 0.02f)
            ui.selectedTab = AppTab.My; ui.render()
            ui.selectedTab = AppTab.Favorites; ui.render(); assertEquals(position, ui.position(), 0.02f)
            ui.navigation.open(LibraryNavigationTarget.Artist("artist", artistName = "Artist A")); ui.render()
            ui.navigation.pop(); ui.render(); assertEquals(position, ui.position(), 0.02f)
            ui.resize(1280, 720); assertEquals(position, ui.position(), 0.02f)
            ui.rebuild(); assertEquals(position, ui.position(), 0.02f)
            assertTrue(ui.localIntents.isEmpty()); assertTrue(ui.onlineIntents.isEmpty()); assertTrue(ui.intents.isEmpty())
        }
    }
    @Test fun loadingKeepsContentPositionWhileSearchSourceAndSortResetIt() = onUiThread {
        Fixture(1280, 720).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            ui.loading = true; ui.render(); assertTrue(ui.visible("Loading"))
            ui.loading = false; ui.render(); assertEquals(position, ui.position(), 0.02f)
            ui.sendFavorite(FavoritesIntent.SearchChanged("Song")); ui.render(); assertTrue(ui.position() < 4f)
            ui.scrollTo(35); ui.sendFavorite(FavoritesIntent.SourceFilterChanged(LibrarySourceFilter.DOWNLOADED)); ui.render(); assertTrue(ui.position() < 4f)
            ui.scrollTo(35); ui.sendFavorite(FavoritesIntent.TrackSortChanged(TrackSortMode.entries.first { it != ui.sort })); ui.render(); assertTrue(ui.position() < 4f)
        }
    }
    @Test fun batchRequestsWorkAgainAfterSwitchingShellsAndSelectionDoesNotPlay() = onUiThread {
        Fixture(480, 900).use { ui ->
            ui.enterBatch(); assertTrue(ui.visible("Cancel"))
            ui.resize(1280, 720); assertTrue(ui.visible("Cancel")); ui.click("Cancel")
            ui.resize(480, 900); ui.capture("batch-after-return-portrait"); ui.enterBatch(); assertTrue(ui.visible("Cancel"))
            ui.click("Song 0")
            assertTrue(ui.nodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On })
            assertTrue(ui.intents.isEmpty())
            ui.sendFavorite(FavoritesIntent.SearchChanged("Song 1")); ui.render(); assertFalse(ui.visible("Cancel"))
        }
    }
    @Test fun restoredOnlinePositionSurvivesRememberedSourceLoading() = onUiThread {
        for ((w, h) in listOf(1280 to 720, 480 to 900)) Fixture(w, h, online = true).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            val loaded = ui.onlineState
            // A fresh Store retains the remembered source while its data is still loading.
            ui.loading = true; ui.onlineState = OnlineFavoritesState(sourceId = loaded.sourceId, isLoading = true)
            ui.rebuild()
            ui.onlineState = OnlineFavoritesState(sourceId = "source", isLoading = true); ui.render()
            ui.onlineState = loaded; ui.loading = false; ui.render()
            assertEquals(position, ui.position(), 0.02f)
            assertTrue(ui.localIntents.isEmpty()); assertTrue(ui.onlineIntents.isEmpty())
        }
    }
    @Test fun bothShellsUseConditionActionsWithoutResettingOnSameConditions() = onUiThread {
        for ((w, h) in listOf(1280 to 720, 480 to 900)) Fixture(w, h).use { ui ->
            ui.scrollTo(35)
            val position = ui.position()
            ui.sendFavorite(FavoritesIntent.SearchChanged("")); ui.render()
            ui.sendFavorite(FavoritesIntent.TrackSortChanged(ui.sort)); ui.render()
            ui.sendFavorite(FavoritesIntent.Refresh); ui.render()
            assertEquals(position, ui.position(), 0.02f)
            if (w > h) ui.scrollTo(0) // The landscape search tool scrolls with the list.
            ui.changeSearch("Song"); assertTrue(ui.position() < 4f)
            if (w < h) ui.click("Exit search")
            ui.scrollTo(35)
            if (w < h) {
                ui.click("More actions"); ui.click("Sources"); ui.click("Server · Online")
            } else {
                ui.sendOnline(OnlineFavoritesIntent.SelectSource("source")); ui.render()
            }
            ui.onlineState = OnlineFavoritesState(sourceId = "source", tracks = ui.tracks); ui.render()
            assertTrue(ui.position() < 4f)
            assertEquals(OnlineFavoritesIntent.SelectSource("source"), ui.onlineIntents.single())
        }
    }
    @Test fun rebuildingClosesUnsubmittedQualitySelectionWithoutDownloading() = onUiThread {
        Fixture(1280, 720).use { ui ->
            ui.click("Batch actions"); ui.click("Song 0"); ui.click("Download selected songs")
            assertTrue(ui.visible("Select download quality"))
            ui.rebuild()
            assertFalse(ui.visible("Select download quality"))
            assertTrue(ui.offlineIntents.none { it is OfflineDownloadIntent.DownloadMany })
            assertTrue(ui.nodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On })
        }
    }
    @Test fun sameSourceRefreshAndPagingKeepPositionWhileAnExplicitConditionChangeClosesTheMenu() = onUiThread {
        Fixture(480, 900, online = true).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            val loaded = ui.onlineState
            ui.sendOnline(OnlineFavoritesIntent.Refresh)
            ui.onlineState = loaded.copy(tracks = emptyList(), isLoading = true, canLoadMore = false); ui.render()
            ui.onlineState = loaded; ui.render()
            assertEquals(position, ui.position(), 0.02f)
            ui.sendOnline(OnlineFavoritesIntent.LoadMore); ui.render()
            assertEquals(position, ui.position(), 0.02f)
            ui.click("More actions for Song 31"); assertTrue(ui.visible("View album"))
            ui.sendOnline(OnlineFavoritesIntent.SearchChanged("Song")); ui.render()
            assertTrue(ui.position() < 4f)
            assertFalse(ui.visible("View album"))
            assertEquals(listOf(OnlineFavoritesIntent.Refresh, OnlineFavoritesIntent.LoadMore,
                OnlineFavoritesIntent.SearchChanged("Song")), ui.onlineIntents)
        }
    }
    @Test fun batchDownloadSubmitsOnlySelectedSongsAndTheirQuality() = onUiThread {
        Fixture(1280,720).use { ui ->
            ui.click("Batch actions"); ui.click("Song 0"); ui.click("Song 2"); ui.click("Download selected songs")
            ui.chooseOriginalQuality()
            val download = ui.offlineIntents.filterIsInstance<OfflineDownloadIntent.DownloadMany>().single()
            assertEquals(listOf("track-0","track-2"),download.tracks.map { it.id })
            assertEquals(NavidromeAudioQuality.Original,download.quality)
            assertTrue(ui.intents.isEmpty()); assertFalse(ui.visible("Cancel"))
        }
    }
    @Test fun scalesAndFontsKeepPlaybackMenusAndTheToolbarReachable() = onUiThread {
        for ((w,h) in listOf(1280 to 720, 480 to 900, 640 to 400)) for (scale in AppDisplayScalePreset.entries) for (font in listOf(1f, 1.5f, 2f)) {
            Fixture(w,h,density = scale.scale,fontScale = font,longNames = true,dark = scale.ordinal % 2 == 0).use { ui ->
                val list = ui.tag("automotive-favorites-list").boundsInRoot
                assertTrue((minOf(list.bottom,ui.playerTop()) - list.top) / scale.scale >= 96f)
                if (w > h) {
                    val toolbar = ui.tag("favorites-toolbar").boundsInRoot
                    assertTrue(toolbar.right <= list.right && toolbar.bottom <= ui.playerTop())
                }
                ui.scrollTo(99)
                val menu = ui.label("More actions for Song 95").boundsInRoot
                assertTrue(menu.bottom <= ui.playerTop())
                if (scale == AppDisplayScalePreset.ExtraLarge && font == 2f) ui.capture("large-$w-$h")
                ui.click("More actions for Song 95"); assertTrue(ui.visible("View artist"))
                assertTrue(ui.intents.isEmpty())
            }
        }
    }
    @Test fun rowColumnsUseTheirActualWidthAcrossBothOrientations() = onUiThread {
        for (font in listOf(1f,1.5f,2f)) for (delta in listOf(-1,0)) {
            val width = (520 * font).toInt() + 48 + delta
            Fixture(width,2000,fontScale = font).use { ui ->
                ui.scrollTo(4)
                assertEquals(delta == 0,ui.visible("120:00"))
            }
        }
    }
    @Test fun onlinePartialResultsKeepLoadMoreAboveThePlayerAndDoNotLoadOnRotation() = onUiThread {
        Fixture(480,900,online = true).use { ui ->
            assertTrue(ui.visible("96+ songs")); assertTrue(ui.visible("Play loaded songs"))
            ui.scrollTo(100)
            assertTrue(ui.tag("favorites-load-more").boundsInRoot.bottom <= ui.playerTop())
            ui.capture("online-partial-portrait")
            ui.resize(1280,720); assertTrue(ui.onlineIntents.isEmpty())
            ui.scrollTo(100); ui.clickTag("favorites-load-more")
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.LoadMore),ui.onlineIntents)
        }
    }
    @Test fun verticalTouchScrollingDoesNotPlaySongs() = onUiThread {
        for ((w,h) in listOf(1280 to 720,480 to 900)) Fixture(w,h).use { ui ->
            ui.scrollTo(5); val before = ui.position(); ui.swipeVertically()
            assertTrue(ui.position() > before); assertTrue(ui.intents.isEmpty())
        }
    }
    @Test fun liveScaleAndFontChangesKeepTheCurrentSong() = onUiThread {
        Fixture(1280,720).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            ui.density = 1.2f; ui.fontScale = 2f; ui.render()
            assertEquals(position,ui.position(),0.02f)
            ui.density = 0.8f; ui.fontScale = 1f; ui.render()
            assertEquals(position,ui.position(),0.02f)
            assertTrue(ui.localIntents.isEmpty()); assertTrue(ui.intents.isEmpty())
        }
    }
    @Test fun switchingOnlineSourcesDuringLoadingUsesOnlyTheLatestPageForActions() = onUiThread {
        Fixture(480,900,online = true).use { ui ->
            ui.scrollTo(35)
            ui.onlineState = OnlineFavoritesState(sourceId = "second",isLoading = true); ui.render()
            ui.resize(1280,720)
            ui.onlineState = OnlineFavoritesState(sourceId = "third",isLoading = true); ui.render()
            val latest = ui.tracks.first().copy(id = "latest",sourceId = "third",title = "Latest song")
            ui.onlineState = OnlineFavoritesState(sourceId = "third",tracks = listOf(latest)); ui.render()
            ui.click("Play all")
            assertEquals(PlayerIntent.PlayTracks(listOf(latest),0),ui.intents.single())
            ui.click("More actions for Latest song"); ui.click("Remove from favorites")
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.SetFavorite("third",latest,false)),ui.onlineIntents)
        }
    }
    @Test fun threeDigitIndicesAndTheLastMenuFitLargePortraitText() = onUiThread {
        Fixture(480,900,fontScale = 2f,count = 125).use { ui ->
            ui.scrollTo(128)
            assertTrue(ui.visible("125"))
            assertTrue(ui.label("More actions for Song 124").boundsInRoot.bottom <= ui.playerTop())
            ui.click("More actions for Song 124"); assertTrue(ui.visible("View album"))
        }
    }
    @Test fun leavingSearchClearsItsQueryWithoutChangingPage() = onUiThread {
        Fixture(480,900).use { ui ->
            ui.query = "Song"; ui.render()
            assertTrue(ui.visible("Exit search"))
            ui.click("Exit search")
            assertEquals("",ui.query)
            assertFalse(ui.visible("Exit search"))
            assertFalse(ui.navigation.hasDetails)
        }
    }
    @Test fun aFinishedEmptyRefreshClearsSelectionsAfterTransientLoading() = onUiThread {
        Fixture(1280,720).use { ui ->
            ui.click("Batch actions"); ui.click("Song 0")
            ui.loading = true; ui.favorites = emptyList(); ui.render()
            assertTrue(ui.visible("Cancel"))
            ui.loading = false; ui.render()
            assertFalse(ui.visible("Cancel"))
            assertTrue(ui.visible("0 songs")); assertTrue(ui.intents.isEmpty())
        }
    }
    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (e: Throwable) { failure = e } }
        failure?.let { throw it }
    }
    private class Fixture(
        width: Int, height: Int, val visual: Boolean = false, density: Float = 1f,
        fontScale: Float = 1f, val dark: Boolean = false, val longNames: Boolean = false,
        online: Boolean = false, count: Int = 96,
    ) : AutoCloseable {
        var width by mutableStateOf(width)
        var height by mutableStateOf(height)
        var density by mutableStateOf(density)
        var fontScale by mutableStateOf(fontScale)
        var selectedTab by mutableStateOf(AppTab.Favorites)
        private var registry = SaveableStateRegistry(null) { true }
        private val scheduler = TestCoroutineScheduler()
        private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        private val platform = PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        val tracks = (0 until count).map { index -> Track("track-$index", "source",
            if (visual) "山阴路的夏天 ${index + 1}" else "Song $index",
            if (longNames) "A very long artist name with several collaborators and additional information" else if (visual) "李志" else "Artist A",
            if (longNames) "A very long album title with additional information" else if (visual) "我爱南京" else "Album A",
            durationMs = 7_200_000, mediaLocator = buildNavidromeSongLocator("source", "$index"), relativePath = "$index",
            artistId = "artist", albumId = "album") }
        var favorites by mutableStateOf(tracks)
        var loading by mutableStateOf(false)
        var query by mutableStateOf("")
        var filter by mutableStateOf<LibrarySourceFilter>(LibrarySourceFilter.ALL)
        var sort by mutableStateOf(FavoritesState().selectedTrackSortMode)
        var onlineState by mutableStateOf(if (online) OnlineFavoritesState(sourceId = "source", tracks = tracks, canLoadMore = true) else OnlineFavoritesState())
        val localIntents = mutableListOf<FavoritesIntent>()
        val onlineIntents = mutableListOf<OnlineFavoritesIntent>()
        val offlineIntents = mutableListOf<OfflineDownloadIntent>()
        val intents = mutableListOf<PlayerIntent>()
        private lateinit var favoritesEntry: AutomotiveFavoritesEntry
        private val importState = ImportState(capabilities = platform.capabilities, sources = listOf(SourceWithStatus(
            ImportSource("source", ImportSourceType.NAVIDROME, "Server", "", indexMode = ImportSourceIndexMode.ONLINE))))
        private val player = PlayerState(snapshot = PlaybackSnapshot(queue = listOf(Track("playing", "local",
            if (visual) "正在播放" else "Now playing", mediaLocator = "", relativePath = "playing")), currentIndex = 0))
        private val queries = TestMusicDetailQueries(tracks = { tracks })
        val navigation = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), queries, scope, mutableStateOf(false), {})
        private var frame = 0L
        private var scene = createScene()
        init { render() }
        private fun localState(): FavoritesState {
            val visible = if (loading) emptyList() else favorites.filter { query.isBlank() || it.title.contains(query, true) }
            return FavoritesState(isLoadingContent = loading, tracks = favorites, filteredTracks = visible,
                favoriteTrackIds = favorites.mapTo(mutableSetOf()) { it.id }, query = query,
                selectedSourceFilter = filter, selectedTrackSortMode = sort, canRefreshRemote = true)
        }
        private fun onFavorite(intent: FavoritesIntent) {
            localIntents += intent
            when (intent) {
                is FavoritesIntent.SearchChanged -> query = intent.query
                is FavoritesIntent.SourceFilterChanged -> filter = intent.filter
                is FavoritesIntent.TrackSortChanged -> sort = intent.mode
                is FavoritesIntent.ToggleFavorite -> favorites = favorites.filterNot { it.id == intent.track.id }
                else -> Unit
            }
        }
        fun sendFavorite(intent: FavoritesIntent) {
            favoritesEntry.prepareForIntent(intent, localState())
            onFavorite(intent)
        }
        fun sendOnline(intent: OnlineFavoritesIntent) {
            favoritesEntry.prepareForIntent(intent, onlineState)
            onlineIntents += intent
            when (intent) {
                is OnlineFavoritesIntent.SearchChanged -> onlineState = onlineState.copy(query = intent.query)
                is OnlineFavoritesIntent.SelectSource -> onlineState = OnlineFavoritesState(sourceId = intent.sourceId, isLoading = intent.sourceId != null)
                else -> Unit
            }
        }
        private fun createScene() = ImageComposeScene(width, height, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                // The entry owner belongs above either shell and participates in this registry.
                val savedEntry = rememberAutomotiveFavoritesEntry()
                favoritesEntry = savedEntry
                CompositionLocalProvider(LocalAutomotiveFavoritesEntry provides savedEntry,
                    LocalDensity provides Density(density, fontScale), LocalPlatformDescriptor provides platform,
                    LocalMusicDetailNavigation provides navigation,
                    LocalPlayerPresentation provides remember { PlayerPresentation() },
                    LocalOfflineDownloadUiState provides OfflineDownloadUiState(onIntent = { offlineIntents += it }, availableSpaceBytes = Long.MAX_VALUE),
                    LocalTrackListActionBindings provides TrackListActionBindings(
                        favoriteState = { TrackFavoriteUiState(it.id in favorites.map { track -> track.id }) },
                        onSetFavorite = { track, _ -> onFavorite(FavoritesIntent.ToggleFavorite(track)) },
                    )) {
                    ProvideUiResourceLanguage(if (visual) "zh-CN" else "en") {
                        LynMusicTheme(themeTokens = if (dark) CLASSIC_APP_THEME_TOKENS else OCEAN_APP_THEME_TOKENS,
                            textPalette = if (dark) AppThemeTextPalette.White else AppThemeTextPalette.Black) { Shell() }
                    }
                }
            }
        }
        @Composable private fun Shell() {
            BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) AutomotiveLandscapeShell(
                navigationRailState = rememberWideNavigationRailState(),
                selectedTab = selectedTab, onTabSelected = { navigation.clear(); selectedTab = it }, platform = platform,
                myState = MyState(), libraryState = LibraryState(), onlineLibraryState = OnlineLibraryState(),
                playlistsState = PlaylistsState(), onlinePlaylistsState = OnlinePlaylistsState(),
                favoritesState = localState(), onlineFavoritesState = onlineState,
                importState = importState, playerState = player,
                settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = {}, onOnlineLibraryIntent = {},
                onPlaylistsIntent = {}, onOnlinePlaylistsIntent = {}, onFavoritesIntent = ::sendFavorite, onOnlineFavoritesIntent = ::sendOnline,
                onImportIntent = {}, onPlayerIntent = { intents += it }, onSettingsIntent = {},
                onOpenBackgroundRunSettings = {}, onOpenLibraryNavigationTarget = { navigation.open(it) }, onOpenAddToPlaylist = {},
            ) else AutomotivePortraitShell(
                portraitNavigation = true, mobilePortraitMiniPlayer = true, hideMiniPlayerBar = false,
                selectedTab = selectedTab, onTabSelected = { navigation.clear(); selectedTab = it }, platform = platform,
                myState = MyState(), libraryState = LibraryState(), onlineLibraryState = OnlineLibraryState(),
                playlistsState = PlaylistsState(), onlinePlaylistsState = OnlinePlaylistsState(),
                favoritesState = localState(), onlineFavoritesState = onlineState,
                importState = importState, playerState = player,
                settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = {}, onOnlineLibraryIntent = {},
                onPlaylistsIntent = {}, onOnlinePlaylistsIntent = {}, onFavoritesIntent = ::sendFavorite, onOnlineFavoritesIntent = ::sendOnline,
                onImportIntent = {}, onPlayerIntent = { intents += it }, onSettingsIntent = {},
                onOpenBackgroundRunSettings = {}, onOpenLibraryNavigationTarget = { navigation.open(it) }, onOpenAddToPlaylist = {},
            )
        }
        }
        fun render(frames: Int = 40) { repeat(frames) {
            scheduler.runCurrent(); Snapshot.sendApplyNotifications(); scene.render(++frame * 16_666_667L).close()
        } }
        fun resize(w: Int, h: Int) {
            width = w; height = h; scene.constraints = Constraints.fixed(w, h); render()
            assertEquals(w > h, nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "automotive-navigation-rail" })
        }
        fun rebuild() {
            val saved = registry.performSave(); scene.close()
            registry = SaveableStateRegistry(saved) { true }; scene = createScene(); render()
        }
        fun scrollTo(index: Int) {
            assertTrue(tag("automotive-favorites-list").config[SemanticsActions.ScrollToIndex].action!!.invoke(index)); render()
        }
        fun position() = tag("automotive-favorites-list").config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun playerTop(): Float = generateSequence(label(if (visual) "正在播放" else "Now playing").parent) { it.parent }
            .first { it.config.contains(SemanticsActions.OnClick) }.boundsInRoot.top
        fun nodes(): List<SemanticsNode> {
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.filter { !it.boundsInRoot.isEmpty }
        }
        fun tag(value: String) = nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == value } ?: error("Missing $value; labels=${nodes().map { it.config.getOrNull(SemanticsProperties.Text) }}")
        private fun matches(n: SemanticsNode, value: String) = n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true ||
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true
        fun label(value: String) = nodes().lastOrNull { matches(it, value) }
            ?: error("Missing $value; labels=${nodes().map { it.config.getOrNull(SemanticsProperties.Text) to it.config.getOrNull(SemanticsProperties.ContentDescription) }}")
        fun visible(value: String) = nodes().any { matches(it, value) }
        fun click(value: String) { clickNode(label(value)) }
        fun clickTag(value: String) { clickNode(tag(value)) }
        private fun clickNode(node: SemanticsNode) {
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center, type = PointerType.Touch); render(80)
        }
        fun enterBatch() { click("More actions"); click("Batch actions") }
        fun changeSearch(value: String) {
            if (width < height) click("Search")
            val field = nodes().last { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString(value)))
            render()
        }
        fun chooseOriginalQuality() { clickNode(nodes().first { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains("Original", true) } == true
        }) }
        fun capture(name: String) {
            val file = File("build/reports/favorites-layout/automotive-shell-$name.png"); file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        fun swipeVertically() {
            val list = tag("automotive-favorites-list").boundsInRoot
            val start = Offset(list.center.x, minOf(list.bottom, playerTop()) - 12f)
            val end = Offset(list.center.x, list.top + 12f)
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, timeMillis = frame * 16)
            repeat(12) { step ->
                scene.sendPointerEvent(PointerEventType.Move, start + (end - start) * ((step + 1) / 12f), type = PointerType.Touch, timeMillis = frame * 16)
                render(1)
            }
            scene.sendPointerEvent(PointerEventType.Release, end, type = PointerType.Touch, timeMillis = frame * 16); render(80)
        }
        override fun close() { scene.close(); navigation.dispose(); scope.cancel() }
    }
}
