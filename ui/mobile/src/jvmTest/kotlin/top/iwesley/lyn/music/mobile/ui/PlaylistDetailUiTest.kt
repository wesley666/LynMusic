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
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.tags.MusicTagsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class PlaylistDetailUiTest {
    @Test fun selectedLayoutsUseActualShellsAndKeepTheLastSongAboveThePlayer() = onUiThread {
        for ((width, height) in listOf(390 to 844, 1194 to 834, 834 to 1194, 640 to 400)) {
            Fixture(width, height, visual = true, count = 9).use { ui ->
                assertEquals(width == 1194, ui.visibleTag("playlist-content-split"))
                assertTrue(ui.visibleTag("playlist-toolbar"))
                assertTrue(ui.visible("作词：罗大佑"))
                assertFalse(ui.visible("返回歌单列表"))
                assertEquals(1, ui.requests)
                if (width == 1194) assertTrue(ui.tagged("playlist-summary").boundsInRoot.right < ui.tagged("playlist-tracks").boundsInRoot.left)
                ui.capture("$width-$height")
                ui.scrollTo(9)
                assertTrue(ui.textNode("河 — 9 的更多操作").boundsInRoot.bottom <= ui.textNode("作词：罗大佑").boundsInRoot.top)
                ui.capture("$width-$height-bottom")
            }
        }
    }

    @Test fun headerScrollsAwayAndChildNavigationAndResizeRetainTheSongAnchor() = onUiThread {
        Fixture(390, 844).use { ui ->
            val toolbar = ui.tagged("playlist-toolbar").boundsInRoot
            ui.scrollTo(24, 11)
            assertFalse(ui.visibleTag("playlist-cover"))
            assertEquals(toolbar, ui.tagged("playlist-toolbar").boundsInRoot)
            for ((width, height) in listOf(640 to 400, 1194 to 834, 834 to 1194, 390 to 844)) {
                ui.rebuild(width, height)
                assertEquals(24, ui.entry.list.firstVisibleItemIndex)
                assertEquals(11, ui.entry.list.firstVisibleItemScrollOffset)
                assertEquals(1, ui.requests)
            }
            ui.navigation.open(LibraryNavigationTarget.Artist("artist")); ui.render()
            ui.navigation.pop(); ui.render()
            assertEquals(24, ui.entry.list.firstVisibleItemIndex)
            assertEquals(11, ui.entry.list.firstVisibleItemScrollOffset)
            assertEquals(1, ui.requests)
        }
    }

    @Test fun partiallyVisibleHeaderResetsAcrossBothStackedAndSplitResizes() = onUiThread {
        for ((width, height) in listOf(640 to 400, 1194 to 834, 834 to 1194)) Fixture(390, 844).use { ui ->
            ui.scrollTo(0, 150)
            ui.rebuild(width, height)
            assertEquals(0, ui.entry.list.firstVisibleItemIndex)
            assertEquals(0, ui.entry.list.firstVisibleItemScrollOffset)
            assertEquals(1, ui.requests)
        }
    }

    @Test fun standardTrackMenuSupportsFavoritesRemovalAndAlbumNavigation() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.clickTag("playlist-play-all")
            assertEquals(PlayerIntent.PlayTracks(ui.tracks, 0), ui.intents.single())
            ui.click("More actions for Song 0")
            ui.click("Remove from playlist")
            assertEquals<PlaylistsIntent>(PlaylistsIntent.RemoveTrackFromPlaylist("playlist", "track-0"), ui.localIntents.last())
            assertTrue(ui.visible("59 songs"))
            ui.click("More actions for Song 1")
            ui.click("Add to favorites")
            assertEquals(setOf("track-1"), ui.favoriteIds)
            ui.click("More actions for Song 1")
            ui.click("View album")
            assertEquals("album", ui.navigation.top.kind)
            ui.navigation.pop(); ui.render()
            assertEquals(1, ui.requests)
        }
    }

    @Test fun emptyAndLargeTextRemainReachableAndMoreMenuContainsImport() = onUiThread {
        Fixture(390, 844, count = 0).use { ui ->
            assertTrue(ui.visible("0 songs"))
            assertTrue(ui.visibleTag("playlist-empty"))
            assertFalse(ui.visibleTag("playlist-play-all"))
            ui.click("More actions for Playlist A")
            assertTrue(ui.visible("Import playlist"))
            ui.click("Import playlist")
            assertEquals("playlist", ui.navigation.top.kind)
            ui.navigation.requestBack(); ui.render()
            assertEquals("playlist", ui.navigation.top.kind)
        }
        Fixture(390, 844, fontScale = 2f, longNames = true).use { ui ->
            ui.scrollTo(0, 150)
            assertTrue(ui.visibleTag("playlist-play-all"))
            ui.clickTag("playlist-play-all")
            assertEquals(ui.tracks, (ui.intents.single() as PlayerIntent.PlayTracks).tracks)
            ui.capture("large-text")
        }
    }

    @Test fun listClickUsesNavigationAndBackRestoresTheListInsteadOfAnInlineDetail() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.navigation.pop(); ui.render()
            assertFalse(ui.visibleTag("playlist-toolbar"))
            assertEquals("playlist", ui.playlistState.selectedPlaylistId)
            ui.click("Playlist A")
            assertEquals("playlist", ui.navigation.top.kind)
            assertTrue(ui.visibleTag("playlist-toolbar"))
            assertEquals(1, ui.requests)
        }
    }

    @Test fun anotherSelectedPlaylistCannotPublishItsTracksIntoThisRoute() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.playlistState = ui.playlistState.copy(selectedPlaylistId = "other",
                selectedPlaylist = PlaylistDetail("other", "Other playlist", tracks = listOf(
                    PlaylistTrackEntry(ui.tracks.first().copy(id = "other-track", title = "Other song")))))
            ui.render()
            assertEquals("playlist", ui.playlistState.selectedPlaylistId)
            assertEquals(PlaylistsIntent.SelectPlaylist("playlist"), ui.localIntents.last())
            assertEquals(2, ui.requests)
            assertFalse(ui.visibleTag("playlist-loading"))
            assertTrue(ui.visible("Song 0"))
            assertFalse(ui.visible("Other playlist"))
            assertFalse(ui.visible("Other song"))
        }
    }

    @Test fun onlineFailureStopsLoadingAndRetriesOnlyOnClickThenRemovesTheCorrectOccurrence() = onUiThread {
        Fixture(390, 844, count = 3).use { ui ->
            ui.openOnlineFailure()
            assertTrue(ui.visibleTag("playlist-error"))
            assertFalse(ui.visibleTag("playlist-loading"))
            assertTrue(ui.visible("Controlled failure"))
            val before = ui.onlineIntents.size
            ui.rebuild(640, 400)
            assertEquals(before, ui.onlineIntents.size)
            ui.onlineFailure = false
            ui.click("Retry")
            assertTrue(ui.visibleTag("playlist-content-stacked"))
            ui.scrollTo(0, 150)
            assertTrue(ui.visibleTag("playlist-play-all"))
            ui.rebuild(390, 844)
            ui.click("More actions for Song 1")
            ui.click("Remove from playlist")
            assertEquals<OnlinePlaylistsIntent>(OnlinePlaylistsIntent.RemoveTrack("playlist", 1), ui.onlineIntents.last())
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
    ) : AutoCloseable {
        private val scheduler = TestCoroutineScheduler()
        private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        private val platform = PlatformDescriptor(ANDROID_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        private var frame = 0L
        var requests = 0
        var selectedTab by mutableStateOf(AppTab.Playlists)
        var favoriteIds by mutableStateOf(if (visual) setOf("track-1", "track-2") else emptySet())
        val intents = mutableListOf<PlayerIntent>()
        private val albumTitle = if (longNames) "A very long album title that must fit within two lines without pushing actions outside the screen" else if (visual) "高规格" else "Playlist A"
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
        val summary = PlaylistSummary("playlist", albumTitle, trackCount = count)
        var playlistState by mutableStateOf(PlaylistsState(isLoadingContent = false, playlists = listOf(summary)))
        private var onlineState by mutableStateOf(OnlinePlaylistsState())
        private var importState by mutableStateOf(ImportState(capabilities = platform.capabilities))
        val onlineIntents = mutableListOf<OnlinePlaylistsIntent>()
        var onlineFailure = false
        val localIntents = mutableListOf<PlaylistsIntent>()
        private val queries = TestMusicDetailQueries()
        val navigation = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), queries, scope, mutableStateOf(false), {})
        private val playlistRoute = navigation.run { openPlaylist(summary, null, LibrarySourceFilter.ALL); top }
        val entry get() = navigation.playlistEntry(navigation.playlistRoute ?: playlistRoute)
        private fun localIntent(intent: PlaylistsIntent) {
            localIntents += intent
            when (intent) {
                is PlaylistsIntent.SelectPlaylist -> {
                    requests++
                    playlistState = playlistState.copy(selectedPlaylistId = intent.playlistId,
                        selectedPlaylist = intent.playlistId?.let { PlaylistDetail(it, albumTitle, tracks = tracks.map(::PlaylistTrackEntry)) })
                }
                PlaylistsIntent.BackToList -> playlistState = playlistState.copy(selectedPlaylistId = null, selectedPlaylist = null)
                is PlaylistsIntent.RemoveTrackFromPlaylist -> playlistState = playlistState.copy(selectedPlaylist =
                    playlistState.selectedPlaylist?.let { detail -> detail.copy(tracks = detail.tracks.filterNot { it.track.id == intent.trackId }) })
                else -> Unit
            }
        }
        private fun onlineIntent(intent: OnlinePlaylistsIntent) {
            onlineIntents += intent
            if (intent is OnlinePlaylistsIntent.SelectPlaylist) {
                val id = intent.playlistId
                onlineState = onlineState.copy(selectedPlaylistId = id,
                    selectedPlaylist = if (onlineFailure || id == null) null else
                        PlaylistDetail(id, albumTitle, tracks = tracks.map(::PlaylistTrackEntry)),
                    errorMessage = if (onlineFailure) UiText.Raw("Controlled failure") else null)
            }
        }
        fun openOnlineFailure() {
            onlineFailure = true
            importState = importState.copy(sources = listOf(SourceWithStatus(ImportSource("server", ImportSourceType.NAVIDROME,
                "Server", "", indexMode = ImportSourceIndexMode.ONLINE))))
            onlineState = OnlinePlaylistsState(sourceId = "server", playlists = listOf(summary))
            navigation.clear()
            navigation.openPlaylist(summary, "server", LibrarySourceFilter.ALL)
            render()
        }
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
            BindPlaylistDetailNavigation(navigation, playlistState, onlineState,
                importState, false, ::localIntent, ::onlineIntent)
            val select: (AppTab) -> Unit = { navigation.clear(); selectedTab = it }
            if (buildLayoutProfile(width.dp, height.dp).isExpandedLayout) {
                TabletShell(
                    selectedTab = selectedTab, onTabSelected = select, platform = platform,
                    myState = MyState(isLoadingContent = false), libraryState = LibraryState(),
                    onlineLibraryState = OnlineLibraryState(), playlistsState = playlistState,
                    onlinePlaylistsState = onlineState, favoritesState = FavoritesState(),
                    onlineFavoritesState = OnlineFavoritesState(), musicTagsState = MusicTagsState(),
                    musicTagsEffects = emptyFlow(), onMusicTagsIntent = {},
                    importState = importState, playerState = player,
                    settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = {},
                    onOnlineLibraryIntent = {}, onPlaylistsIntent = ::localIntent, onOnlinePlaylistsIntent = ::onlineIntent,
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
                    onlineLibraryState = OnlineLibraryState(), playlistsState = playlistState,
                    onlinePlaylistsState = onlineState, favoritesState = FavoritesState(),
                    onlineFavoritesState = OnlineFavoritesState(), musicTagsState = MusicTagsState(),
                    musicTagsEffects = emptyFlow(), onMusicTagsIntent = {},
                    importState = importState, playerState = player,
                    settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = {},
                    onOnlineLibraryIntent = {}, onPlaylistsIntent = ::localIntent, onOnlinePlaylistsIntent = ::onlineIntent,
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
            scope.launch { entry.list.scrollToItem(index, offset) }; render()
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
            val file = File("build/reports/playlist-detail/playlist-$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() { scene.close(); navigation.dispose(); scope.cancel() }
    }
}
