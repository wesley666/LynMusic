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

class FolderDetailUiTest {
    @Test fun groupedLayoutsUseTheActualShellAndKeepTheLastSongAboveThePlayer() = onUiThread {
        for ((width, height) in listOf(390 to 844, 1194 to 834, 834 to 1194, 640 to 400)) {
            Fixture(width, height, visual = true, count = 8).use { ui ->
                assertTrue(ui.visibleTag("folder-toolbar"))
                assertTrue(ui.visibleTag("folder-play-all"))
                assertFalse(ui.visibleTag("folder-folders-heading"))
                assertEquals(0, ui.queryCalls)
                ui.capture("$width-$height-songs")
                ui.scrollTo(ui.tracks.size + 1)
                val lastTitle = ui.tracks.last().title
                assertTrue(ui.textNode("$lastTitle 的更多操作").boundsInRoot.bottom <= ui.textNode("你怎可善忘").boundsInRoot.top)
                ui.capture("$width-$height-bottom")
                ui.navigation.pop(); ui.render()
                assertTrue(ui.visibleTag("folder-folders-heading"))
                ui.capture("$width-$height-folders")
            }
        }
    }

    @Test fun everyDirectoryRetainsItsPositionAcrossChildrenAndHostResizes() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.navigation.pop(); ui.render()
            val parent = ui.navigation.top
            ui.scrollTo(10, 7)
            ui.open(ui.leaf)
            ui.scrollTo(24, 11)
            val leaf = ui.navigation.top
            for ((width, height) in listOf(1194 to 834, 834 to 1194, 640 to 400, 390 to 844)) {
                ui.rebuild(width, height)
                assertEquals(24, ui.entry.list.firstVisibleItemIndex)
                assertEquals(11, ui.entry.list.firstVisibleItemScrollOffset)
                assertEquals(leaf, ui.navigation.top)
                assertTrue(ui.visibleTag("folder-toolbar"))
                assertEquals(0, ui.queryCalls)
            }
            ui.navigation.open(LibraryNavigationTarget.Artist("artist")); ui.render()
            ui.navigation.pop(); ui.render()
            assertEquals(24, ui.entry.list.firstVisibleItemIndex)
            ui.navigation.openMusicTags("track-0", "source"); ui.render()
            ui.navigation.pop(); ui.render()
            assertEquals(11, ui.entry.list.firstVisibleItemScrollOffset)
            ui.navigation.pop(); ui.render()
            assertEquals(parent, ui.navigation.top)
            assertEquals(10, ui.entry.list.firstVisibleItemIndex)
            assertEquals(7, ui.entry.list.firstVisibleItemScrollOffset)
        }
    }

    @Test fun realFolderEntrySupportsRepeatedOpenAndLayerByLayerBackWithoutChangingStores() = onUiThread {
        Fixture(390, 844, startAtLeaf = false).use { ui ->
            ui.click("Folder")
            ui.click("nav")
            assertEquals("", ui.navigation.top.folderPath)
            ui.click("Parent")
            ui.click("Album")
            assertEquals("Parent/Album", ui.navigation.top.folderPath)
            assertFalse(ui.visible("Folder"))
            ui.rebuild(1194, 834)
            ui.click("Back")
            assertEquals("Parent", ui.navigation.top.folderPath)
            ui.click("Back")
            assertEquals("", ui.navigation.top.folderPath)
            ui.click("Back")
            assertFalse(ui.navigation.hasDetails)
            ui.click("nav")
            assertEquals("", ui.navigation.top.folderPath)
            assertTrue(ui.libraryIntents.isEmpty())
            assertEquals(0, ui.queryCalls)
        }
    }

    @Test fun sourceOverviewRetainsItsCategoryAndScrollPositionAfterOpeningAndResizing() = onUiThread {
        Fixture(390, 844, startAtLeaf = false, extraSources = 20).use { ui ->
            ui.click("Folder")
            ui.scrollRootsTo(8, 9)
            val anchor = ui.navigation.folderListState.layoutInfo.visibleItemsInfo.first().key
            val source = ui.tree.rootFolders[10]
            ui.clickTag("folder-row:${source.key.stableId}")
            ui.rebuild(1194, 834)
            ui.click("Back")
            assertFalse(ui.navigation.hasDetails)
            assertEquals(anchor, ui.navigation.folderListState.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(9, ui.navigation.folderListState.firstVisibleItemScrollOffset)
            ui.clickTag("folder-row:${source.key.stableId}")
            assertEquals(source.sourceId, ui.navigation.top.sourceId)
        }
    }

    @Test fun mixedDirectoriesPlayOnlyDirectSongsAndHideMissingGroups() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.navigation.pop(); ui.render()
            assertTrue(ui.visibleTag("folder-folders-heading"))
            ui.scrollTo(ui.entry.list.layoutInfo.totalItemsCount - 1)
            assertTrue(ui.visibleTag("folder-songs-heading"))
            ui.clickTag("folder-play-all")
            assertEquals(listOf("parent-song"), (ui.intents.last() as PlayerIntent.PlayTracks).tracks.map { it.id })
            ui.open(ui.tree.nodesByKey.getValue(LibraryFolderKey("source", "Only folders")))
            assertTrue(ui.visibleTag("folder-folders-heading"))
            assertFalse(ui.visibleTag("folder-songs-heading"))
            assertFalse(ui.visibleTag("folder-play-all"))
            ui.open(ui.leaf)
            assertFalse(ui.visibleTag("folder-folders-heading"))
            ui.clickTag("folder-play-all")
            assertEquals(ui.tracks, (ui.intents.last() as PlayerIntent.PlayTracks).tracks)
        }
    }

    @Test fun temporaryLoadingAndEmptyStatesKeepTheSavedAnchorAndBackAvailable() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.scrollTo(24, 11)
            val loaded = ui.library
            ui.library = loaded.copy(isLoadingContent = true, filteredTracks = emptyList())
            ui.render()
            assertTrue(ui.visibleTag("folder-loading"))
            assertTrue(ui.visibleTag("folder-toolbar"))
            ui.library = loaded; ui.render()
            assertEquals(24, ui.entry.list.firstVisibleItemIndex)
            assertEquals(11, ui.entry.list.firstVisibleItemScrollOffset)
            ui.library = loaded.copy(filteredTracks = emptyList()); ui.render()
            assertTrue(ui.visibleTag("folder-empty"))
            assertFalse(ui.visibleTag("folder-play-all"))
            ui.library = ui.library.copy(sourceLabelsById = emptyMap()); ui.render()
            assertTrue(ui.visible("This source is unavailable. Go back or retry after restoring the source."))
            ui.click("Back")
            assertEquals("Parent", ui.navigation.top.folderPath)
            assertTrue(ui.libraryIntents.isEmpty())
        }
    }

    @Test fun standardSongMenusAndLongNamesRemainUsableWithLargeTextAndDarkTheme() = onUiThread {
        Fixture(390, 844).use { ui ->
            ui.click("More actions for Song 0")
            ui.click("Add to favorites")
            assertEquals(setOf("track-0"), ui.favoriteIds)
            ui.click("More actions for Song 0")
            ui.click("View album")
            assertEquals("album", ui.navigation.top.kind)
            ui.navigation.pop(); ui.render()
            assertEquals("Parent/Album", ui.navigation.top.folderPath)
            ui.click("Song 1")
            assertEquals(PlayerIntent.PlayTracks(ui.tracks, 1), ui.intents.last())
        }
        Fixture(390, 844, count = 3, fontScale = 2f, longNames = true, dark = true).use { ui ->
            ui.scrollTo(1)
            assertTrue(ui.visibleTag("folder-toolbar"))
            ui.clickTag("folder-play-all")
            assertEquals(ui.tracks, (ui.intents.last() as PlayerIntent.PlayTracks).tracks)
            ui.capture("large-text-dark")
            ui.navigation.pop(); ui.render()
            assertTrue(ui.visibleTag("folder-row:${ui.leaf.key.stableId}"))
            ui.capture("long-folder-dark")
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
        val dark: Boolean = false,
        startAtLeaf: Boolean = true,
        extraSources: Int = 0,
    ) : AutoCloseable {
        private val scheduler = TestCoroutineScheduler()
        private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        private val platform = PlatformDescriptor(ANDROID_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        private var frame = 0L
        var selectedTab by mutableStateOf(AppTab.Library)
        var favoriteIds by mutableStateOf(emptySet<String>())
        val intents = mutableListOf<PlayerIntent>()
        val libraryIntents = mutableListOf<LibraryIntent>()
        var queryCalls = 0
        private val parentName = if (visual) "[刀郎全合集01]无损FLAC" else "Parent"
        private val folderName = if (longNames) "A very long folder name with Chinese 中文目录 and several nested album titles that must wrap safely" else if (visual) "2004-喀什噶尔胡杨" else "Album"
        val tracks = (0 until count).map { index ->
            val titles = listOf("五一夜市的兄弟", "关于二道桥", "再见乌鲁木齐", "冰山上的雪莲", "喀什噶尔胡杨", "大眼睛", "送战友", "2002年的第一场雪")
            Track("track-$index", "source", if (visual) titles[index % titles.size] else "Song $index",
                if (visual) "刀郎" else "Artist A", if (visual) "喀什噶尔胡杨" else "Album A", durationMs = 255000,
                mediaLocator = "", relativePath = "$parentName/$folderName/${index.toString().padStart(3, '0')}.flac",
                albumId = "album", artistId = "artist")
        }
        private val seed = tracks.first()
        private val sourceLabels = mapOf("source" to "nav", "other" to "Other source") +
            (0 until extraSources).associate { "extra-$it" to "Source ${it.toString().padStart(2, '0')}" }
        private val allTracks = tracks + listOf(
            seed.copy(id = "parent-song", title = "Parent song", relativePath = "$parentName/parent.flac"),
            seed.copy(id = "root-song", title = "Root song", relativePath = "root.flac"),
            seed.copy(id = "only-folders", relativePath = "Only folders/Nested/song.flac"),
            seed.copy(id = "other-source", sourceId = "other", title = "Other source song"),
        ) + (0 until 25).map { seed.copy(id = "branch-$it", relativePath = "$parentName/Branch ${it.toString().padStart(2, '0')}/song.flac") } +
            (0 until extraSources).map { seed.copy(id = "extra-$it", sourceId = "extra-$it", relativePath = "song.flac") }
        val tree = deriveLibraryFolderTree(allTracks, sourceLabels)
        val leaf = tree.nodesByKey.getValue(LibraryFolderKey("source", "$parentName/$folderName"))
        var library by mutableStateOf(LibraryState(isLoadingContent = false, tracks = allTracks, filteredTracks = allTracks,
            sourceLabelsById = sourceLabels))
        private val importState = ImportState(capabilities = platform.capabilities, sources = listOf(
            SourceWithStatus(ImportSource("source", ImportSourceType.LOCAL_FOLDER, "nav", "/fixture/music"))))
        private val player = PlayerState(snapshot = PlaybackSnapshot(
            queue = listOf(Track("playing", "source", "你怎可善忘", "杨千嬅", mediaLocator = "", relativePath = "playing")),
            currentIndex = 0, positionMs = 97000, durationMs = 255000, canSeek = true,
        ))
        private val queries = TestMusicDetailQueries(
            album = { queryCalls++; flowOf(AlbumDetailData("Album A", tracks = tracks)) },
            artistTracks = { queryCalls++; flowOf(tracks) })
        val navigation = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), queries, scope, mutableStateOf(false), {})
        val entry get() = navigation.folderEntry(navigation.top)
        private var scene: ImageComposeScene
        init {
            if (startAtLeaf) {
                for (path in listOf("", parentName, "$parentName/$folderName")) navigation.openFolder(
                    tree.nodesByKey.getValue(LibraryFolderKey("source", path)), LibrarySourceFilter.ALL)
            }
            scene = createScene(); render()
        }

        private fun createScene() = ImageComposeScene(width, height, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale), LocalPlatformDescriptor provides platform,
                LocalMusicDetailNavigation provides navigation,
                LocalPlayerPresentation provides remember { PlayerPresentation() },
                LocalTrackListActionBindings provides TrackListActionBindings(
                    favoriteState = { TrackFavoriteUiState(it.id in favoriteIds) },
                    onSetFavorite = { track, liked -> favoriteIds = if (liked) favoriteIds + track.id else favoriteIds - track.id },
                ),
            ) {
                ProvideUiResourceLanguage(if (visual) "zh-CN" else "en") {
                    LynMusicTheme(themeTokens = if (dark) CLASSIC_APP_THEME_TOKENS else OCEAN_APP_THEME_TOKENS,
                        textPalette = if (dark) AppThemeTextPalette.White else AppThemeTextPalette.Black) { Shell() }
                }
            }
        }

        @Composable
        private fun Shell() {
            val select: (AppTab) -> Unit = { navigation.clear(); selectedTab = it }
            if (buildLayoutProfile(width.dp, height.dp).isExpandedLayout) {
                TabletShell(
                    selectedTab = selectedTab, onTabSelected = select, platform = platform,
                    myState = MyState(isLoadingContent = false), libraryState = library,
                    onlineLibraryState = OnlineLibraryState(), playlistsState = PlaylistsState(),
                    onlinePlaylistsState = OnlinePlaylistsState(), favoritesState = FavoritesState(favoriteTrackIds = favoriteIds),
                    onlineFavoritesState = OnlineFavoritesState(), musicTagsState = MusicTagsState(),
                    musicTagsEffects = emptyFlow(), onMusicTagsIntent = {},
                    importState = importState, playerState = player,
                    settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = { libraryIntents += it },
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
                    myState = MyState(isLoadingContent = false), libraryState = library,
                    onlineLibraryState = OnlineLibraryState(), playlistsState = PlaylistsState(),
                    onlinePlaylistsState = OnlinePlaylistsState(), favoritesState = FavoritesState(favoriteTrackIds = favoriteIds),
                    onlineFavoritesState = OnlineFavoritesState(), musicTagsState = MusicTagsState(),
                    musicTagsEffects = emptyFlow(), onMusicTagsIntent = {},
                    importState = importState, playerState = player,
                    settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = { libraryIntents += it },
                    onOnlineLibraryIntent = {}, onPlaylistsIntent = {}, onOnlinePlaylistsIntent = {},
                    onFavoritesIntent = {}, onOnlineFavoritesIntent = {}, onImportIntent = {},
                    onPlayerIntent = { intents += it }, onSettingsIntent = {}, onOpenBackgroundRunSettings = {},
                    onOpenLibraryNavigationTarget = { navigation.open(it) }, onOpenAddToPlaylist = {},
                )
            }
        }

        fun open(folder: LibraryFolderNode) { navigation.openFolder(folder, LibrarySourceFilter.ALL); render() }
        fun scrollRootsTo(index: Int, offset: Int) {
            scope.launch { navigation.folderListState.scrollToItem(index, offset) }; render()
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
            val file = File("build/reports/folder-detail/folder-$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() { scene.close(); navigation.dispose(); scope.cancel() }
    }
}
