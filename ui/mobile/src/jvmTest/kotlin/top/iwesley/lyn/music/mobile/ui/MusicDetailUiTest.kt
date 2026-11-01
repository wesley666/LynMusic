@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.runtime.InternalComposeApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.*
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
            ui.navigation.open(if(online) LibraryNavigationTarget.OnlineArtist("server","artist","Artist A") else LibraryNavigationTarget.Artist(libraryArtistId("Artist A")))
            ui.render();assertTrue(ui.visible("Artist A"))
            ui.back.complete();ui.render();assertEquals("album",ui.navigation.top.kind)
            ui.back.complete();ui.render();assertFalse(ui.navigation.hasDetails)
            assertEquals(25,ui.rootList.firstVisibleItemIndex);assertEquals(13,ui.rootList.firstVisibleItemScrollOffset)
            assertEquals("original search",ui.query);assertTrue(ui.actions.isEmpty())
        }
    }
    @Test fun restoredAlbumAndArtistPositionsSurviveFailureAndRetry()=onUiThread {
        for(artist in listOf(false,true)) Fixture().use { ui ->
            if(artist) ui.navigation.open(LibraryNavigationTarget.Artist(libraryArtistId("Artist A"))) else ui.openAlbum()
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
    private fun onUiThread(block:()->Unit) { var failure:Throwable?=null;SwingUtilities.invokeAndWait { try {block()} catch(error:Throwable){failure=error} };failure?.let{throw it} }
    private class BackInput:NavigationEventInput() {
        fun start(edge:Int)=dispatchOnBackStarted(NavigationEvent(swipeEdge=edge))
        fun progress(edge:Int,fraction:Float)=dispatchOnBackProgressed(NavigationEvent(swipeEdge=edge,progress=fraction))
        fun cancel()=dispatchOnBackCancelled()
        fun complete()=dispatchOnBackCompleted()
    }
    private class Fixture(val width:Int=480,val online:Boolean=false):AutoCloseable {
        val scheduler=TestCoroutineScheduler()
        val back=BackInput()
        var fail=false;var empty=false
        val query="original search";val source="original source"
        val actions=mutableListOf<String>()
        lateinit var navigation:MusicDetailNavigation
        lateinit var rootList:LazyListState
        lateinit var scope:CoroutineScope
        var registry=SaveableStateRegistry(null){true}
        private var frame=0L
        private val queries=MusicDetailQueries { request -> flow {
            if(fail) error("controlled failure")
            val tracks=if(empty || request.kind==MusicDetailKind.Artist) emptyList() else (0..140).map {
                Track("track-$it","server","Detail track $it","Artist A","Album A",mediaLocator="",relativePath="$it",albumId="album",artistId="artist")
            }
            emit(MusicDetailData(if(request.kind==MusicDetailKind.Artist) "Artist A" else "Album A",artistName="Artist A",tracks=tracks,
                albums=if(!empty && request.kind==MusicDetailKind.Artist) (0..140).map {MusicDetailAlbum(Album("album-$it","Album $it","Artist A"))} else emptyList()))
        } }
        var scene=createScene()
        private fun createScene()=ImageComposeScene(width,800,coroutineContext=UnconfinedTestDispatcher(scheduler)) {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                val dispatcher=LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
                DisposableEffect(dispatcher){dispatcher.addInput(back);onDispose{dispatcher.removeInput(back)}}
                scope=rememberCoroutineScope()
                navigation=rememberMusicDetailNavigation(queries) { actions+="restore" }
                val presentation=remember {PlayerPresentation()}
                CompositionLocalProvider(LocalPlatformDescriptor provides PlatformDescriptor(ANDROID_PLATFORM_NAME, PlatformCapabilities(true,true,true,true,false)),
                    LocalMusicDetailNavigation provides navigation,LocalPlayerPresentation provides presentation,
                    LocalTrackListActionBindings provides TrackListActionBindings(favoriteState={TrackFavoriteUiState(false)},onSetFavorite={track,_->actions+="favorite:${track.id}"})) {
                    ProvideUiResourceLanguage("en") {LynMusicTheme {
                        Column(Modifier.fillMaxSize()) {
                            Text("Root Tab",Modifier.testTag("detail-test-navigation").clickable {navigation.clear()}.padding(12.dp))
                            MusicDetailHost(onPlayerIntent={actions+="play"},modifier=Modifier.weight(1f),showDuration=width>=600) {
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
        fun openAlbum(){navigation.open(if(online) LibraryNavigationTarget.OnlineAlbum("server","album","Album A","Artist A") else LibraryNavigationTarget.Album(libraryAlbumId("Artist A","Album A")));render()}
        fun render(frames:Int=40){repeat(frames){scheduler.runCurrent();Snapshot.sendApplyNotifications();scene.render(++frame*16_666_667L).close()}}
        fun rebuild(){val saved=registry.performSave();scene.close();registry=SaveableStateRegistry(saved){true};scene=createScene();render()}
        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        }
        private fun matches(node:SemanticsNode,label:String)=node.config.getOrNull(SemanticsProperties.Text)?.any{it.text==label}==true || node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label)==true
        fun tagged(tag: String) = nodes().last { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun visible(label:String)=nodes().any{matches(it,label)}
        fun click(label:String){val node=nodes().lastOrNull{matches(it,label)}?:error("Missing $label");scene.sendPointerEvent(PointerEventType.Press,node.boundsInRoot.center);scene.sendPointerEvent(PointerEventType.Release,node.boundsInRoot.center);render()}
        fun capture(name:String){val file=File("build/reports/music-detail/mobile-$name.png");file.parentFile.mkdirs();scene.render(++frame*16_666_667L).use{it.encodeToData()!!.use{data->file.writeBytes(data.bytes)}}}
        override fun close()=scene.close()
    }
}
