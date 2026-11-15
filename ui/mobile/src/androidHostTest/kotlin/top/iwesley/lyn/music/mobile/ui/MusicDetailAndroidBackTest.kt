@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package top.iwesley.lyn.music.mobile.ui

import android.app.Application
import android.os.IBinder
import android.os.Looper
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.assertIsSelected
import org.junit.Rule
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.ClassName
import org.robolectric.shadows.ShadowWindowManagerGlobal
import org.robolectric.util.ReflectionHelpers
import top.iwesley.lyn.music.feature.details.AlbumDetailData
import top.iwesley.lyn.music.feature.details.MusicDetailQueries
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], application = Application::class, shadows = [RecordingActivityClient::class])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicDetailAndroidBackTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun folderStackAndScrollPositionRestoreAfterActivityRecreation() {
        Fixture().use { ui ->
            val tree = top.iwesley.lyn.music.deriveLibraryFolderTree(ui.folderLibrary.filteredTracks,
                ui.folderLibrary.sourceLabelsById)
            for (path in listOf("", "Parent", "Parent/Child")) ui.navigation.openFolder(
                tree.nodesByKey.getValue(top.iwesley.lyn.music.LibraryFolderKey("local", path)),
                top.iwesley.lyn.music.feature.library.LibrarySourceFilter.ALL)
            ui.frames()
            val original = ui.navigation.top
            compose.runOnIdle { ui.navigation.folderEntry(original).list.requestScrollToItem(23, 11) }
            ui.frames()
            assertEquals(23, ui.navigation.folderEntry(original).list.firstVisibleItemIndex)
            ui.recreate()
            assertEquals(original, ui.navigation.top)
            assertEquals(listOf(null, "", "Parent", "Parent/Child"),
                ui.navigation.backStack.map { (it as MusicDetailRoute).folderPath })
            assertEquals(23, ui.navigation.folderEntry(original).list.firstVisibleItemIndex)
            assertEquals(11, ui.navigation.folderEntry(original).list.firstVisibleItemScrollOffset)
            assertEquals(LibraryBrowserRootView.Folders, ui.navigation.libraryRootView)
            ui.navigation.requestBack(); ui.frames()
            assertEquals("Parent", ui.navigation.top.folderPath)
        }
    }
    @Test fun folderBackGesturesCancelAndPopOneDirectoryAtATimeOnBothEdges() {
        for (edge in listOf(BackEvent.EDGE_LEFT, BackEvent.EDGE_RIGHT)) Fixture().use { ui ->
            val tree = top.iwesley.lyn.music.deriveLibraryFolderTree(ui.folderLibrary.filteredTracks,
                ui.folderLibrary.sourceLabelsById)
            for (path in listOf("", "Parent", "Parent/Child")) ui.navigation.openFolder(
                tree.nodesByKey.getValue(top.iwesley.lyn.music.LibraryFolderKey("local", path)),
                top.iwesley.lyn.music.feature.library.LibrarySourceFilter.ALL)
            ui.frames()
            for (expectedParent in listOf("Parent", "", null)) {
                val original = ui.navigation.top
                val cancelled = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge))
                cancelled.moveBy(if (edge == BackEvent.EDGE_LEFT) 160f else -160f, 0f)
                cancelled.cancel()
                ui.frames()
                assertEquals(original, ui.navigation.top)
                assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge)).close()
                ui.frames()
                assertEquals(expectedParent, ui.navigation.top.folderPath)
                assertFalse(ui.activity.isFinishing)
                assertEquals(0, RecordingActivityClient.backRequests)
            }
            assertFalse(ui.navigation.hasDetails)
        }
    }
    @Test fun playlistDetailUsesBothSystemBackEdgesWithoutFinishingTheActivity() {
        for (edge in listOf(BackEvent.EDGE_LEFT, BackEvent.EDGE_RIGHT)) Fixture().use { ui ->
            ui.navigation.openPlaylist(top.iwesley.lyn.music.core.model.PlaylistSummary("playlist", "Playlist"),
                null, top.iwesley.lyn.music.feature.library.LibrarySourceFilter.ALL)
            ui.frames()
            val original = ui.navigation.top
            val cancelled = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge))
            cancelled.moveBy(if (edge == BackEvent.EDGE_LEFT) 160f else -160f, 0f)
            cancelled.cancel()
            ui.frames()
            assertEquals(original, ui.navigation.top)
            val completed = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge))
            completed.close()
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            assertFalse(ui.activity.isFinishing)
            assertEquals(0, RecordingActivityClient.backRequests)
        }
    }
    @Test fun lastDetailGestureDoesNotFinishActivity() {
        Fixture().use { ui ->
            ui.navigation.open(LibraryNavigationTarget.Album("album"))
            ui.frames()
            val gesture = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_RIGHT))
            gesture.moveBy(-200f, 0f)
            ui.frames(2)
            assertTrue(ui.navigation.hasDetails)
            gesture.close()
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            assertFalse(ui.activity.isFinishing)
            assertEquals(0, RecordingActivityClient.backRequests)
        }
    }

    @Test fun retiredGestureCompletionDoesNotFallThroughToActivity() {
        Fixture().use { ui ->
            ui.navigation.open(LibraryNavigationTarget.Album("album"))
            ui.frames()
            val callback = ui.animationCallback()
            val gesture = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_RIGHT))
            gesture.moveBy(-200f, 0f)
            gesture.close()
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            // A native gesture can retain the callback after its registration was removed.
            callback.onBackInvoked()
            ui.frames()
            assertFalse(ui.activity.isFinishing)
            assertEquals(0, RecordingActivityClient.backRequests)
        }
    }

    @Test fun rootStillAllowsTheNextSystemBack() {
        Fixture().use { ui ->
            ui.topCallback().onBackInvoked()
            assertEquals(1, RecordingActivityClient.backRequests)
        }
    }

    @Test fun bothEdgesCancelAndThenCompleteWithoutLeavingTheActivity() {
        for (edge in listOf(BackEvent.EDGE_LEFT, BackEvent.EDGE_RIGHT)) Fixture().use { ui ->
            ui.navigation.open(LibraryNavigationTarget.Album("album"))
            ui.frames()
            val original = ui.navigation.top
            val cancelled = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge))
            cancelled.moveBy(if (edge == BackEvent.EDGE_LEFT) 160f else -160f, 0f)
            ui.frames(2)
            cancelled.cancel()
            ui.frames()
            assertEquals(original, ui.navigation.top)
            val completed = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge))
            completed.close()
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            assertFalse(ui.activity.isFinishing)
            assertEquals(0, RecordingActivityClient.backRequests)
            ui.topCallback().onBackInvoked()
            assertEquals(1, RecordingActivityClient.backRequests)
        }
    }

    @Test fun artistPagerKeepsItsTabWhenEitherSystemBackEdgeIsCancelled() {
        for (edge in listOf(BackEvent.EDGE_LEFT, BackEvent.EDGE_RIGHT)) Fixture().use { ui ->
            ui.navigation.open(LibraryNavigationTarget.Artist("artist", artistName = "Artist"))
            ui.frames()
            val route = ui.navigation.top
            compose.onNodeWithTag("artist-pager").performTouchInput { swipeLeft() }
            ui.frames()
            compose.onNodeWithTag("artist-tab-Albums").assertIsSelected()
            assertEquals(ArtistDetailTab.Albums, ui.navigation.entry(route).selectedArtistTab)
            val cancelled = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge))
            cancelled.moveBy(if (edge == BackEvent.EDGE_LEFT) 160f else -160f, 0f)
            ui.frames(2)
            cancelled.cancel()
            ui.frames()
            assertEquals(route, ui.navigation.top)
            compose.onNodeWithTag("artist-tab-Albums").assertIsSelected()
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge)).close()
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            assertFalse(ui.activity.isFinishing)
            assertEquals(0, RecordingActivityClient.backRequests)
        }
    }

    @Test fun nestedPopRetiresItsCallbackAndANewGestureCanPopTheLastDetail() {
        Fixture().use { ui ->
            ui.navigation.open(LibraryNavigationTarget.Album("album"))
            ui.navigation.open(LibraryNavigationTarget.Artist("artist"))
            ui.frames()
            val oldCallback = ui.animationCallback()
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_LEFT)).close()
            ui.frames()
            assertEquals("album", ui.navigation.top.kind)
            oldCallback.onBackInvoked()
            ui.frames()
            assertEquals("album", ui.navigation.top.kind)
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_RIGHT)).close()
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            assertFalse(ui.activity.isFinishing)
            assertEquals(0, RecordingActivityClient.backRequests)
        }
    }

    @Test fun clearDuringGestureCannotFinishActivityOrPopANewDetail() {
        Fixture().use { ui ->
            ui.navigation.open(LibraryNavigationTarget.Album("old"))
            ui.frames()
            val callback = ui.animationCallback()
            val gesture = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_LEFT))
            gesture.moveBy(160f, 0f)
            ui.frames(2)
            ui.navigation.clear()
            ui.navigation.open(LibraryNavigationTarget.Artist("new"))
            ui.frames()
            gesture.cancel()
            callback.onBackInvoked()
            callback.onBackProgressed(BackEvent(160f, 200f, 0.8f, BackEvent.EDGE_LEFT))
            callback.onBackCancelled()
            ui.frames()
            assertEquals("new", ui.navigation.top.entityId)
            assertFalse(ui.activity.isFinishing)
            assertEquals(0, RecordingActivityClient.backRequests)
        }
    }

    @Test fun ordinaryButtonBackWithoutPreviewStillWorks() {
        Fixture().use { ui ->
            ui.navigation.open(LibraryNavigationTarget.Album("album"))
            ui.frames()
            val old = ui.animationCallback()
            old.onBackInvoked()
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            old.onBackInvoked()
            ui.frames()
            assertFalse(ui.activity.isFinishing)
            ui.topCallback().onBackInvoked()
            assertEquals(1, RecordingActivityClient.backRequests)
        }
    }

    @Test fun foregroundBackCallbackConsumesGestureBeforeDetails() {
        Fixture().use { ui ->
            ui.navigation.open(LibraryNavigationTarget.Album("album"))
            ui.frames()
            val route = ui.navigation.top
            var dismissals = 0
            val overlay = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { dismissals++; isEnabled = false }
            }
            ui.activity.onBackPressedDispatcher.addCallback(overlay)
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_LEFT)).close()
            ui.frames()
            assertEquals(1, dismissals)
            assertEquals(route, ui.navigation.top)
            overlay.remove()
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_LEFT)).close()
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            assertFalse(ui.activity.isFinishing)
        }
    }

    @Test fun tagsSystemBackRequestsTheEditorsExitInsteadOfPoppingItsRoute() {
        Fixture().use { ui ->
            ui.navigation.openMusicTags("track", "local")
            ui.frames()
            val route = ui.navigation.top
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_LEFT)).close()
            ui.frames()
            assertEquals(1, ui.tagBackRequests)
            assertEquals(route, ui.navigation.top)
            assertFalse(ui.activity.isFinishing)
            // The editor pops only after its own discard confirmation completes.
            ui.navigation.pop(route.entryId)
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            assertEquals(0, RecordingActivityClient.backRequests)
        }
    }

    private inner class Fixture : AutoCloseable {
        val folderLibrary = top.iwesley.lyn.music.feature.library.LibraryState(isLoadingContent = false,
            filteredTracks = (0 until 60).map { top.iwesley.lyn.music.core.model.Track("folder-song-$it", "local", "Song $it",
                mediaLocator = "", relativePath = "Parent/Child/${it.toString().padStart(3, '0')}.mp3") },
            sourceLabelsById = mapOf("local" to "Local"))
        private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        private val queries = TestMusicDetailQueries(album = { flowOf(AlbumDetailData("Album")) })
        val activity get() = controller.get()
        lateinit var navigation: MusicDetailNavigation
        var tagBackRequests = 0

        init {
            RecordingActivityClient.backRequests = 0
            org.jetbrains.compose.resources.setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
            val shadow = Class.forName("org.robolectric.shadows.ShadowWindowOnBackInvokedDispatcher")
            ReflectionHelpers.callStaticMethod<Any>(shadow, "setEnablePredictiveBack", ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
            ReflectionHelpers.callInstanceMethod<Any>(RuntimeEnvironment.getApplication().applicationInfo, "setEnableOnBackInvokedCallback", ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
            controller.setup().visible().windowFocusChanged(true)
            setContent()
            frames()
        }

        private fun setContent() {
            activity.setContent {
                navigation = rememberMusicDetailNavigation(queries) {}
                CompositionLocalProvider(
                    LocalMusicDetailNavigation provides navigation,
                    LocalPlayerPresentation provides androidx.compose.runtime.remember { PlayerPresentation() },
                ) {
                    ProvideUiResourceLanguage("en") {
                        LynMusicTheme {
                            MusicDetailHost(onPlayerIntent = {}, musicTags = {
                                MusicDetailBackHandler(enabled = true) { tagBackRequests++ }
                                Text("Tags")
                            }, folder = { route ->
                                FolderDetailPage(route, navigation, folderLibrary,
                                    top.iwesley.lyn.music.feature.favorites.FavoritesState(),
                                    top.iwesley.lyn.music.feature.importing.ImportState(capabilities =
                                        top.iwesley.lyn.music.core.model.PlatformCapabilities(true, true, true, true, false)),
                                    {}, {}, showDuration = false)
                            }) { Text("Root") }
                        }
                    }
                }
            }
        }

        fun recreate() {
            controller.recreate().visible().windowFocusChanged(true)
            setContent()
            frames()
        }

        fun frames(count: Int = 30) {
            compose.waitForIdle()
            repeat(count) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
            compose.waitForIdle()
        }

        fun topCallback(): OnBackInvokedCallback = ReflectionHelpers.callInstanceMethod(
            activity.window.decorView.findOnBackInvokedDispatcher(), "getTopCallback",
        )

        fun animationCallback(): OnBackAnimationCallback {
            val session = ReflectionHelpers.getStaticField<Any>(ShadowWindowManagerGlobal::class.java, "windowSessionDelegate")
            val info = ReflectionHelpers.getField<Any>(session, "onBackInvokedCallbackInfo")
            val binder = ReflectionHelpers.callInstanceMethod<Any>(info, "getCallback")
            val callback = ReflectionHelpers.getField<java.lang.ref.WeakReference<OnBackInvokedCallback>>(binder, "mCallback").get()
            return callback as OnBackAnimationCallback
        }

        override fun close() { controller.pause().stop().destroy() }
    }
}

@Implements(className = "android.app.ActivityClient", isInAndroidSdk = false)
class RecordingActivityClient {
    @Implementation
    fun onBackPressed(token: IBinder?, @ClassName("android.app.IRequestFinishCallback") callback: Any?) {
        backRequests++
    }

    companion object { var backRequests = 0 }
}
