@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.runtime.InternalComposeApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.navigationevent.*
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import javax.swing.SwingUtilities
import java.io.File
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.details.*
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class MusicDetailUiTest {
    @Test fun nestedPagesReturnToUnchangedEntryAndExactListPosition()=onUiThread {
        for(width in listOf(360,1120)) for(online in listOf(false,true)) Fixture(width,online).use { ui ->
            ui.scope.launch { ui.rootList.scrollToItem(25,13) };ui.render()
            ui.openAlbum();assertTrue(ui.visible("Album A"));assertTrue(ui.visible("Root Tab"));assertTrue(ui.visible("Mini player"))
            assertEquals("original search",ui.query);assertEquals("original source",ui.source)
            ui.navigation.open(if(online) LibraryNavigationTarget.OnlineArtist("server","artist","Artist A") else LibraryNavigationTarget.Artist(libraryArtistId("Artist A"), artistName = "Artist A"))
            ui.render();assertTrue(ui.visible("Artist A"))
            ui.back.complete();ui.render();assertEquals("album",ui.navigation.top.kind)
            ui.back.complete();ui.render();assertFalse(ui.navigation.hasDetails)
            assertEquals(25,ui.rootList.firstVisibleItemIndex);assertEquals(13,ui.rootList.firstVisibleItemScrollOffset)
            assertEquals("original search",ui.query);assertTrue(ui.actions.isEmpty())
        }
    }
    @Test fun restoredAlbumAndArtistPositionsSurviveFailureAndRetry()=onUiThread {
        for(artist in listOf(false,true)) Fixture().use { ui ->
            if(artist) ui.navigation.open(LibraryNavigationTarget.Artist(libraryArtistId("Artist A"), artistName = "Artist A")) else ui.openAlbum()
            ui.render();val state=ui.navigation.entry(ui.navigation.top)
            ui.scope.launch { state.contentListState.scrollToItem(35,17) };ui.render()
            ui.fail=true;ui.rebuild()
            assertTrue(ui.visible("Retry"))
            val restored=ui.navigation.entry(ui.navigation.top)
            assertEquals(35,restored.contentListState.firstVisibleItemIndex)
            assertEquals(17,restored.contentListState.firstVisibleItemScrollOffset)
            ui.click("Retry");assertTrue(ui.visible("Retry"))
            ui.fail=false;ui.click("Retry")
            assertEquals(35,restored.contentListState.firstVisibleItemIndex)
            assertEquals(17,restored.contentListState.firstVisibleItemScrollOffset)
            assertFalse(ui.visible("Retry"))
        }
    }
    @Test fun successfulEmptyRetryUsesContentInsteadOfErrorLayout()=onUiThread {
        Fixture().use { ui ->
            ui.fail=true;ui.openAlbum();assertTrue(ui.visible("Retry"))
            ui.empty=true;ui.fail=false;ui.click("Retry")
            assertTrue(ui.visible("This album or artist has no songs to display."));assertFalse(ui.visible("Retry"))
        }
    }
    @Test fun nativePredictiveBackCancelsOrPopsExactlyOnce()=onUiThread {
        for(edge in listOf(NavigationEvent.EDGE_LEFT,NavigationEvent.EDGE_RIGHT)) Fixture().use { ui ->
            ui.openAlbum();val top=ui.navigation.top
            ui.back.start(edge);ui.render(2);ui.back.progress(edge,0.5f);ui.render(2)
            assertEquals(top,ui.navigation.top)
            ui.back.cancel();ui.render();assertEquals(top,ui.navigation.top)
            ui.back.start(edge);ui.back.progress(edge,0.5f);ui.render(2);ui.back.complete();ui.render()
            assertFalse(ui.navigation.hasDetails)
        }
    }
    @Test fun clearingDuringGestureCannotPopANewDetail()=onUiThread {
        for (previewFrames in listOf(0, 2)) Fixture().use { ui ->
            ui.openAlbum();ui.back.start(NavigationEvent.EDGE_LEFT);ui.back.progress(NavigationEvent.EDGE_LEFT,0.5f);ui.render(previewFrames)
            ui.navigation.clear();ui.navigation.open(LibraryNavigationTarget.Artist("new-artist"));ui.render(2)
            ui.back.complete();ui.render()
            assertTrue(ui.navigation.hasDetails);assertEquals("new-artist",ui.navigation.top.entityId)
        }
    }
    @Test fun menusUseSelectedDetailTrackAndCloseBeforeNavigation()=onUiThread {
        Fixture().use { ui ->
            ui.openAlbum();ui.click("More actions for Detail track 0");ui.click("View artist")
            assertEquals("artist",ui.navigation.top.kind);assertFalse(ui.visible("View artist"))
            assertTrue(ui.actions.isEmpty())
            ui.back.complete();ui.render();ui.click("More actions for Detail track 0");ui.click("Add to favorites")
            assertEquals(listOf("favorite:track-0"),ui.actions)
        }
    }
    @Test fun horizontalTransitionKeepsPageSizeAndMainNavigationFixed() = onUiThread {
        for (width in listOf(360, 1120)) Fixture(width).use { ui ->
            val navBounds = ui.tagged("detail-test-navigation").boundsInRoot
            val playerBounds = ui.tagged("detail-test-player").boundsInRoot
            ui.navigation.open(LibraryNavigationTarget.Album("album"))
            ui.render(4)
            val first = ui.tagged("music-detail-page-album").boundsInRoot.left
            assertEquals(width, ui.tagged("music-detail-page-album").layoutInfo.width)
            ui.render(8)
            val middle = ui.tagged("music-detail-page-album").boundsInRoot.left
            assertTrue(first > middle && middle > 0f, "first=$first, middle=$middle")
            ui.render()
            assertEquals(0f, ui.tagged("music-detail-page-album").boundsInRoot.left)
            assertEquals(navBounds, ui.tagged("detail-test-navigation").boundsInRoot)
            assertEquals(playerBounds, ui.tagged("detail-test-player").boundsInRoot)
        }
    }
    @Test fun escapeDismissesTheSongMenuWithoutPoppingItsPage() = onUiThread {
        Fixture(1120).use { ui ->
            ui.openAlbum()
            val route = ui.navigation.top
            ui.click("More actions for Detail track 0")
            if (!ui.scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))) ui.back.complete()
            ui.scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
            ui.render()
            assertFalse(ui.visible("View artist"))
            assertEquals(route, ui.navigation.top)
        }
    }
    @Test fun escapeAndTabClearKeepNavigationOutsideContent()=onUiThread {
        Fixture(1120).use { ui ->
            ui.openAlbum();ui.capture("album")
            ui.scene.sendKeyEvent(KeyEvent(Key.Escape,KeyEventType.KeyDown));ui.scene.sendKeyEvent(KeyEvent(Key.Escape,KeyEventType.KeyUp));ui.render()
            assertFalse(ui.navigation.hasDetails)
            ui.openAlbum();ui.click("Root Tab");assertFalse(ui.navigation.hasDetails)
            assertTrue(ui.visible("Mini player"))
        }
    }
    @Test fun artistTabsReuseQueriesFilterFavoritesAndKeepAnExistingQueue() = onUiThread {
        for (width in listOf(390, 1194)) Fixture(width, online = true).use { ui ->
            ui.favoriteIds = setOf("track-0", "track-2")
            ui.openArtist()
            assertEquals(1, ui.trackRequests)
            assertEquals(1, ui.albumRequests)
            assertEquals(0, ui.albumDetailRequests)
            ui.clickTag("artist-tab-Favorites")
            assertTrue(ui.visible("Detail track 0"))
            assertTrue(ui.visible("Detail track 2"))
            assertFalse(ui.visible("Detail track 1"))
            ui.click("Play liked")
            assertEquals(listOf("track-0", "track-2"), ui.plays.single().map { it.id })
            ui.click("More actions for Detail track 0")
            ui.click("Remove from favorites")
            assertFalse(ui.visible("Detail track 0"))
            assertEquals(listOf("track-0", "track-2"), ui.plays.single().map { it.id })
            ui.clickTag("artist-tab-Albums")
            assertTrue(ui.visible("Album 0"))
            assertFalse(ui.visible("Detail track 2"))
            assertEquals(0, ui.albumDetailRequests)
            assertEquals(1, ui.trackRequests)
            assertEquals(1, ui.albumRequests)
            ui.click("Album 0")
            assertEquals(1, ui.albumDetailRequests)
            ui.back.complete(); ui.render()
            assertEquals(ArtistDetailTab.Albums, ui.navigation.entry(ui.navigation.top).selectedArtistTab)
            assertEquals(1, ui.trackRequests)
            assertEquals(1, ui.albumRequests)
        }
    }

    @Test fun artistPagerSwipesBothWaysWithoutLoopingOrTriggeringRowActions() = onUiThread {
        for (width in listOf(390, 1194)) Fixture(width, online = true).use { ui ->
            ui.openArtist()
            val route = ui.navigation.top
            val tabBounds = ui.tagged("artist-tab-Songs").boundsInRoot
            ui.assertArtistTab(ArtistDetailTab.Songs)
            ui.swipeArtist(left = false, overFirstTrack = true)
            ui.assertArtistTab(ArtistDetailTab.Songs)
            ui.swipeArtist(left = true, overFirstTrack = true)
            ui.assertArtistTab(ArtistDetailTab.Albums)
            assertTrue(ui.visible("Album 0"))
            assertFalse(ui.visible("Detail track 0"))
            ui.swipeArtist(left = true)
            ui.assertArtistTab(ArtistDetailTab.Favorites)
            ui.swipeArtist(left = true)
            ui.assertArtistTab(ArtistDetailTab.Favorites)
            ui.swipeArtist(left = false)
            ui.assertArtistTab(ArtistDetailTab.Albums)
            ui.swipeArtist(left = false)
            ui.assertArtistTab(ArtistDetailTab.Songs)
            assertEquals(tabBounds, ui.tagged("artist-tab-Songs").boundsInRoot)
            assertEquals(route, ui.navigation.top)
            assertTrue(ui.actions.isEmpty())
            assertEquals(1, ui.trackRequests)
            assertEquals(1, ui.albumRequests)
            assertEquals(0, ui.albumDetailRequests)
        }
    }

    @Test fun artistPagerHandlesRapidTabClicksAndVerticalSongScrolling() = onUiThread {
        for (width in listOf(390, 1194)) Fixture(width).use { ui ->
            ui.openArtist()
            ui.clickTag("artist-tab-Albums", frames = 2)
            ui.clickTag("artist-tab-Favorites", frames = 2)
            ui.clickTag("artist-tab-Songs", frames = 2)
            ui.clickTag("artist-tab-Favorites")
            ui.assertArtistTab(ArtistDetailTab.Favorites)
            ui.swipeArtist(left = false)
            ui.assertArtistTab(ArtistDetailTab.Albums)
            ui.clickTag("artist-tab-Songs")
            ui.scrollArtistUp()
            ui.assertArtistTab(ArtistDetailTab.Songs)
            assertTrue(ui.navigation.entry(ui.navigation.top).contentListState.firstVisibleItemIndex > 0)
            assertTrue(ui.actions.isEmpty())
            assertEquals(1, ui.trackRequests)
            assertEquals(1, ui.albumRequests)
        }
    }

    @Test fun successfulEmptyArtistPagesStillSwipeWithoutAdditionalRequests() = onUiThread {
        Fixture().use { ui ->
            ui.empty = true
            ui.openArtist()
            ui.swipeArtist(left = true)
            ui.assertArtistTab(ArtistDetailTab.Albums)
            assertTrue(ui.visible("No albums to display."))
            ui.swipeArtist(left = true)
            ui.assertArtistTab(ArtistDetailTab.Favorites)
            assertTrue(ui.visible("No liked songs by this artist yet."))
            ui.swipeArtist(left = false)
            ui.assertArtistTab(ArtistDetailTab.Albums)
            assertEquals(1, ui.trackRequests)
            assertEquals(1, ui.albumRequests)
            assertEquals(0, ui.albumDetailRequests)
        }
    }

    @Test fun albumsKeepTheirOwnSongCountWithoutAddingParticipationWhenSongsFinish() = onUiThread {
        Fixture(390, online = true, height = 844, visual = true).use { ui ->
            val gate = CompletableDeferred<Unit>()
            ui.trackGate = gate
            ui.openArtist(ArtistDetailTab.Albums)
            val before = ui.textNode("我爱南京").boundsInRoot
            assertTrue(ui.labels().any { it.contains("14") && it.contains("首") })
            assertFalse(ui.labels().any { it.startsWith("参与 ") })
            gate.complete(Unit); ui.render()
            assertEquals(before, ui.textNode("我爱南京").boundsInRoot)
            assertTrue(ui.labels().any { it.contains("14") && it.contains("首") })
            assertFalse(ui.labels().any { it.startsWith("参与 ") })
            assertEquals(0, ui.albumDetailRequests)
        }
    }

    @Test fun artistHeaderAndSongsSurviveAlbumFailureAndOnlyAlbumsRetry() = onUiThread {
        Fixture().use { ui ->
            ui.failAlbums = true
            ui.openArtist()
            assertTrue(ui.visible("Artist A"))
            assertTrue(ui.visible("Detail track 0"))
            ui.swipeArtist(left = true)
            assertTrue(ui.visible("Retry"))
            ui.swipeArtist(left = true)
            ui.assertArtistTab(ArtistDetailTab.Favorites)
            ui.swipeArtist(left = false)
            ui.assertArtistTab(ArtistDetailTab.Albums)
            ui.failAlbums = false
            ui.click("Retry")
            assertTrue(ui.visible("Album 0"))
            assertEquals(1, ui.trackRequests)
            assertEquals(2, ui.albumRequests)
        }
    }

    @Test fun albumsRemainUsableWhileSongsAndFavoritesShareLoadingAndRetry() = onUiThread {
        Fixture().use { ui ->
            val gate = CompletableDeferred<Unit>()
            ui.trackGate = gate
            ui.openArtist()
            assertTrue(ui.visible("Artist A"))
            assertTrue(ui.visible("Loading details…"))
            ui.swipeArtist(left = true)
            assertTrue(ui.visible("Album 0"))
            ui.swipeArtist(left = true)
            assertTrue(ui.visible("Loading details…"))
            ui.failTracks = true
            gate.complete(Unit); ui.render()
            assertTrue(ui.visible("Retry"))
            ui.failTracks = false
            ui.click("Retry")
            assertTrue(ui.visible("No liked songs by this artist yet."))
            assertEquals(2, ui.trackRequests)
            assertEquals(1, ui.albumRequests)
            assertEquals(0, ui.albumDetailRequests)
        }
    }

    @Test fun artistTabsKeepSeparatePositionsAcrossHostRecreation() = onUiThread {
        Fixture().use { ui ->
            ui.favoriteIds = (0..100).map { "track-$it" }.toSet()
            ui.openArtist()
            val entry = ui.navigation.entry(ui.navigation.top)
            ui.scope.launch { entry.contentListState.scrollToItem(30, 9) }; ui.render()
            ui.swipeArtist(left = true)
            ui.scope.launch { entry.albumListState.scrollToItem(25, 7) }; ui.render()
            ui.swipeArtist(left = true)
            ui.scope.launch { entry.favoriteListState.scrollToItem(20, 5) }; ui.render()
            ui.rebuild()
            val restored = ui.navigation.entry(ui.navigation.top)
            assertEquals(ArtistDetailTab.Favorites, restored.selectedArtistTab)
            assertEquals(20, restored.favoriteListState.firstVisibleItemIndex)
            ui.swipeArtist(left = false)
            assertEquals(25, restored.albumListState.firstVisibleItemIndex)
            assertEquals(7, restored.albumListState.firstVisibleItemScrollOffset)
            ui.swipeArtist(left = false)
            assertEquals(30, restored.contentListState.firstVisibleItemIndex)
            assertEquals(9, restored.contentListState.firstVisibleItemScrollOffset)
            ui.clickTag("artist-tab-Albums")
            assertEquals(25, restored.albumListState.firstVisibleItemIndex)
            assertEquals(7, restored.albumListState.firstVisibleItemScrollOffset)
        }
    }

    @Test fun captureArtistWidePortraitAndShortHeightLayouts() = onUiThread {
        for ((width, height) in listOf(390 to 844, 1194 to 834, 834 to 1194, 640 to 400)) {
            Fixture(width, online = true, height = height, visual = true).use { ui ->
                ui.favoriteIds = (0 until 18).map { "track-$it" }.toSet()
                ui.openArtist(ArtistDetailTab.Favorites)
                assertTrue(ui.visible("李志"))
                assertTrue(ui.visible("山阴路的夏天"))
                assertTrue(ui.visible("Mini player"))
                ui.capture("artist-liked-$width-$height")
                ui.clickTag("artist-tab-Albums")
                ui.capture("artist-albums-$width-$height")
            }
        }
    }

    private fun onUiThread(block:()->Unit) { var failure:Throwable?=null;SwingUtilities.invokeAndWait { try {block()} catch(error:Throwable){failure=error} };failure?.let{throw it} }
    private class BackInput:NavigationEventInput() {
        fun start(edge:Int)=dispatchOnBackStarted(NavigationEvent(swipeEdge=edge))
        fun progress(edge:Int,fraction:Float)=dispatchOnBackProgressed(NavigationEvent(swipeEdge=edge,progress=fraction))
        fun cancel()=dispatchOnBackCancelled()
        fun complete()=dispatchOnBackCompleted()
    }
    private class Fixture(val width:Int=480,val online:Boolean=false,val height:Int=800,val visual:Boolean=false):AutoCloseable {
        val scheduler=TestCoroutineScheduler()
        val back=BackInput()
        var fail=false;var empty=false
        var failTracks = false; var failAlbums = false
        var trackGate: CompletableDeferred<Unit>? = null
        var trackRequests = 0; var albumRequests = 0; var albumDetailRequests = 0
        var favoriteIds by mutableStateOf(emptySet<String>())
        val plays = mutableListOf<List<Track>>()
        val query="original search";val source="original source"
        val actions=mutableListOf<String>()
        lateinit var navigation:MusicDetailNavigation
        lateinit var rootList:LazyListState
        lateinit var scope:CoroutineScope
        var registry=SaveableStateRegistry(null){true}
        private var frame=0L
        private fun fixtureTracks() = if (empty) emptyList() else (0 until if (visual) 96 else 141).map {
            val titles = listOf("山阴路的夏天", "梵高先生", "定西", "和你在一起", "忽然", "热河", "山阴路的夏天")
            Track("track-$it", "server", if (visual) titles[it % titles.size] else "Detail track $it",
                if (visual) "李志" else "Artist A", if (visual) "我爱南京" else "Album A", durationMs = 255000,
                mediaLocator = "", relativePath = "$it", albumId = "album-${it % 7}", artistId = "artist")
        }
        private val queries = TestMusicDetailQueries(
            album = { flow {
                albumDetailRequests++
                if (fail) error("controlled failure")
                emit(AlbumDetailData("Album A", artistName = "Artist A", tracks = fixtureTracks()))
            } },
            artistTracks = { flow {
                trackRequests++
                trackGate?.await()
                if (failTracks) error("tracks failed")
                if (fail) error("controlled failure")
                emit(fixtureTracks())
            } },
            artistAlbums = { flow {
                albumRequests++
                if (failAlbums) error("albums failed")
                if (fail) error("controlled failure")
                emit(if (empty) emptyList() else (0 until if (visual) 7 else 141).map { MusicDetailAlbum(Album("album-$it", if (visual) listOf("我爱南京", "梵高先生", "1701", "这个世界会好吗", "被禁忌的游戏", "工体东路没有人", "F")[it] else "Album $it", if (visual) "李志" else "Artist A", trackCount = if (visual) 14 else 0)) })
            } },
        )
        var scene=createScene()
        private fun createScene()=ImageComposeScene(width,height,coroutineContext=UnconfinedTestDispatcher(scheduler)) {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                val dispatcher=LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
                DisposableEffect(dispatcher){dispatcher.addInput(back);onDispose{dispatcher.removeInput(back)}}
                scope=rememberCoroutineScope()
                navigation=rememberMusicDetailNavigation(queries) { actions+="restore" }
                val presentation=remember {PlayerPresentation()}
                CompositionLocalProvider(LocalPlatformDescriptor provides PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME, PlatformCapabilities(true,true,true,true,false)),
                    LocalMusicDetailNavigation provides navigation,LocalPlayerPresentation provides presentation,
                    LocalTrackListActionBindings provides TrackListActionBindings(favoriteState={TrackFavoriteUiState(it.id in favoriteIds)},onSetFavorite={track,liked->favoriteIds = if (liked) favoriteIds + track.id else favoriteIds - track.id;actions+="favorite:${track.id}"})) {
                    ProvideUiResourceLanguage(if (visual) "zh-CN" else "en") {LynMusicTheme(themeTokens = if (visual) OCEAN_APP_THEME_TOKENS else CLASSIC_APP_THEME_TOKENS,
                        textPalette = if (visual) AppThemeTextPalette.Black else AppThemeTextPalette.White) {
                        Column(Modifier.fillMaxSize()) {
                            Text("Root Tab",Modifier.testTag("detail-test-navigation").clickable {navigation.clear()}.padding(12.dp))
                            MusicDetailHost(onPlayerIntent={intent-> actions+="play"; if (intent is top.iwesley.lyn.music.feature.player.PlayerIntent.PlayTracks) plays += intent.tracks},modifier=Modifier.weight(1f),showDuration=width>=600) {
                                rootList=rememberLazyListState()
                                LazyColumn(Modifier.fillMaxSize(),state=rootList) {items(100){Text("Root $it",Modifier.fillMaxWidth().height(45.dp))}}
                            }
                            Text("Mini player",Modifier.testTag("detail-test-player").padding(12.dp))
                        }
                    }}
                }
            }
        }
        init {render()}
        fun openArtist(initialTab: ArtistDetailTab = ArtistDetailTab.Songs) {
            val title = if (visual) "李志" else "Artist A"
            navigation.open(if (online) LibraryNavigationTarget.OnlineArtist("server", "artist", title)
                else LibraryNavigationTarget.Artist(libraryArtistId(title), artistName = title))
            navigation.entry(navigation.top).selectedArtistTab = initialTab
            render()
        }
        fun clickTag(tag: String, frames: Int = 80) {
            val node = tagged(tag)
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center, type = PointerType.Touch)
            render(frames)
        }
        fun assertArtistTab(tab: ArtistDetailTab) {
            assertEquals(tab, navigation.entry(navigation.top).selectedArtistTab)
            assertEquals(true, tagged("artist-tab-${tab.name}").config.getOrNull(SemanticsProperties.Selected))
            assertTrue(tagged("artist-content-${tab.name}").boundsInRoot.width > 0f)
        }
        fun swipeArtist(left: Boolean, overFirstTrack: Boolean = false) {
            val viewport = tagged("artist-pager").boundsInRoot
            val y = if (overFirstTrack) textNode("Detail track 0").boundsInRoot.center.y else viewport.center.y
            drag(Offset(viewport.left + viewport.width * if (left) 0.82f else 0.18f, y),
                Offset(viewport.left + viewport.width * if (left) 0.18f else 0.82f, y))
        }
        fun scrollArtistUp() {
            val viewport = tagged("artist-pager").boundsInRoot
            drag(Offset(viewport.center.x, viewport.bottom - 20f), Offset(viewport.center.x, viewport.top + 40f))
        }
        private fun drag(start: Offset, end: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, timeMillis = frame * 16L)
            render(1)
            repeat(12) { step ->
                val point = start + (end - start) * ((step + 1) / 12f)
                scene.sendPointerEvent(PointerEventType.Move, point, type = PointerType.Touch, timeMillis = frame * 16L)
                render(1)
            }
            scene.sendPointerEvent(PointerEventType.Release, end, type = PointerType.Touch, timeMillis = frame * 16L)
            render(80)
        }
        fun openAlbum(){navigation.open(if(online) LibraryNavigationTarget.OnlineAlbum("server","album","Album A","Artist A") else LibraryNavigationTarget.Album(libraryAlbumId("Artist A","Album A")));render()}
        fun render(frames:Int=40){repeat(frames){scheduler.runCurrent();Snapshot.sendApplyNotifications();scene.render(++frame*16_666_667L).close()}}
        fun rebuild(){val saved=registry.performSave();scene.close();registry=SaveableStateRegistry(saved){true};scene=createScene();render()}
        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            val viewport = Rect(0f, 0f, width.toFloat(), height.toFloat())
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                .filter { !it.boundsInRoot.isEmpty && it.boundsInRoot.overlaps(viewport) }
        }
        private fun matches(node:SemanticsNode,label:String)=node.config.getOrNull(SemanticsProperties.Text)?.any{it.text==label}==true || node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label)==true
        fun tagged(tag: String) = nodes().last { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun textNode(label: String) = nodes().last { matches(it, label) }
        fun labels() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        fun visible(label:String)=nodes().any{matches(it,label)}
        fun click(label:String){val node=nodes().lastOrNull{matches(it,label)}?:error("Missing $label");scene.sendPointerEvent(PointerEventType.Press,node.boundsInRoot.center);scene.sendPointerEvent(PointerEventType.Release,node.boundsInRoot.center);render()}
        fun capture(name:String){val file=File("build/reports/music-detail/automotive-$name.png");file.parentFile.mkdirs();scene.render(++frame*16_666_667L).use{it.encodeToData()!!.use{data->file.writeBytes(data.bytes)}}}
        override fun close()=scene.close()
    }
}
