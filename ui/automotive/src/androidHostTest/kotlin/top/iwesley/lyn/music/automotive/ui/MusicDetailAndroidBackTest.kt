@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package top.iwesley.lyn.music.automotive.ui

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
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.ANDROID_AUTOMOTIVE_PLATFORM_NAME
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.online.OnlineFavoritesState
import top.iwesley.lyn.music.feature.online.OnlineLibraryState
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsState
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.core.model.AppTab
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
    private val platform = PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME,
        PlatformCapabilities(true, true, true, true, false))
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

    @Test fun albumScrollSurvivesEitherCancelledSystemBackEdgeWithoutReloading() {
        for (edge in listOf(BackEvent.EDGE_LEFT, BackEvent.EDGE_RIGHT)) Fixture(withTracks = true).use { ui ->
            ui.navigation.open(LibraryNavigationTarget.Album("album"))
            ui.frames()
            val route = ui.navigation.top
            compose.onNodeWithTag("album-tracks").performScrollToIndex(15)
            ui.frames()
            val entry = ui.navigation.entry(route)
            val index = entry.contentListState.firstVisibleItemIndex
            val offset = entry.contentListState.firstVisibleItemScrollOffset
            val gesture = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge))
            gesture.moveBy(if (edge == BackEvent.EDGE_LEFT) 160f else -160f, 0f)
            ui.frames(2)
            gesture.cancel(); ui.frames()
            assertEquals(route, ui.navigation.top)
            assertEquals(index, entry.contentListState.firstVisibleItemIndex)
            assertEquals(offset, entry.contentListState.firstVisibleItemScrollOffset)
            assertEquals(1, ui.albumRequests)
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge)).close()
            ui.frames()
            assertFalse(ui.navigation.hasDetails)
            assertFalse(ui.activity.isFinishing)
            assertEquals(0, RecordingActivityClient.backRequests)
        }
    }

    @Test fun favoritesMenuDetailReturnsToItsSongsAfterCancelledAndCompletedBack() {
        for (edge in listOf(BackEvent.EDGE_LEFT, BackEvent.EDGE_RIGHT)) Fixture(favoritesRoot = true).use { ui ->
            compose.onNodeWithContentDescription("More actions for Favorite 0").performClick()
            compose.onNodeWithText("View album").performClick()
            ui.focusActivity()
            val route = ui.navigation.top
            assertEquals("album", route.kind)
            val gesture = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge))
            gesture.moveBy(if (edge == BackEvent.EDGE_LEFT) 160f else -160f, 0f)
            ui.frames(2); gesture.cancel(); ui.frames()
            assertEquals(route, ui.navigation.top)
            assertEquals(1, ui.albumRequests)
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(edge)).close(); ui.frames()
            assertFalse(ui.navigation.hasDetails)
            compose.onNodeWithContentDescription("More actions for Favorite 0").assertExists()
            assertFalse(ui.activity.isFinishing)
        }
    }

    @Test fun favoritesSearchCancelsOrCompletesSystemBackWithoutFinishingActivity() {
        Fixture(portraitFavoritesRoot = true).use { ui ->
            ui.favoritesQuery.value = "Favorite"; ui.frames()
            compose.onNodeWithContentDescription("Exit search").assertExists()
            val gesture = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_LEFT))
            gesture.moveBy(160f,0f); gesture.cancel(); ui.frames()
            assertEquals("Favorite",ui.favoritesQuery.value)
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_RIGHT)).close(); ui.frames()
            assertEquals("",ui.favoritesQuery.value)
            compose.onNodeWithContentDescription("Search").assertExists()
            assertFalse(ui.activity.isFinishing)
            assertEquals(0,RecordingActivityClient.backRequests)
            // Saving an Activity lowers its lifecycle state and unregisters back callbacks.
            ui.saveState()
        }
    }

    @Test fun favoritesSelectionAndDetailBackKeepTheirOwnOrder() {
        Fixture(favoritesRoot = true).use { ui ->
            compose.onNodeWithContentDescription("Batch actions").performClick(); ui.frames()
            compose.onNodeWithText("Cancel").assertExists()
            val cancelled = assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_LEFT))
            cancelled.moveBy(160f,0f); cancelled.cancel(); ui.frames()
            compose.onNodeWithText("Cancel").assertExists()
            ui.navigation.open(LibraryNavigationTarget.Album("album")); ui.frames()
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_RIGHT)).close(); ui.frames()
            assertFalse(ui.navigation.hasDetails)
            compose.onNodeWithText("Cancel").assertExists()
            assertNotNull(ShadowWindowManagerGlobal.startPredictiveBackGesture(BackEvent.EDGE_LEFT)).close(); ui.frames()
            compose.onNodeWithContentDescription("Batch actions").assertExists()
            assertFalse(ui.activity.isFinishing)
            assertEquals(0,RecordingActivityClient.backRequests)
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

    @Test
    @Config(sdk = [27, 33])
    fun legacyButtonBackPopsArtistAndClearsNavigationWithoutFinishingActivity() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible().windowFocusChanged(true)
        val activity = controller.get()
        lateinit var navigation: MusicDetailNavigation
        val queries = TestMusicDetailQueries()
        try {
            activity.setContent {
                navigation = rememberMusicDetailNavigation(queries) {}
                CompositionLocalProvider(LocalPlatformDescriptor provides platform, LocalMusicDetailNavigation provides navigation,
                    LocalPlayerPresentation provides androidx.compose.runtime.remember { PlayerPresentation() }) {
                    ProvideUiResourceLanguage("en") {
                        LynMusicTheme { MusicDetailHost(onPlayerIntent = {}) { Text("Root") } }
                    }
                }
            }
            fun frames() {
                compose.waitForIdle()
                repeat(30) {
                    Snapshot.sendApplyNotifications()
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                }
                compose.waitForIdle()
            }
            frames()
            navigation.open(LibraryNavigationTarget.Album("album"))
            navigation.open(LibraryNavigationTarget.Artist("artist", artistName = "Artist"))
            frames()
            activity.onBackPressedDispatcher.onBackPressed()
            frames()
            assertEquals("album", navigation.top.kind)
            assertFalse(activity.isFinishing)
            activity.onBackPressedDispatcher.onBackPressed()
            frames()
            assertFalse(navigation.hasDetails)
            assertFalse(activity.isFinishing)
            navigation.open(LibraryNavigationTarget.Album("album"))
            navigation.open(LibraryNavigationTarget.Artist("artist", artistName = "Artist"))
            frames()
            navigation.clear()
            frames()
            assertFalse(navigation.hasDetails)
            assertFalse(activity.isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private inner class Fixture(withTracks: Boolean = false, favoritesRoot: Boolean = false,
        private val portraitFavoritesRoot: Boolean = false) : AutoCloseable {
        private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        var albumRequests = 0
        private val queries = TestMusicDetailQueries(album = {
            albumRequests++
            flowOf(AlbumDetailData("Album", tracks = if (withTracks) (0..39).map {
                Track("song-$it", "source", "Song $it", mediaLocator = "", relativePath = "$it")
            } else emptyList()))
        })
        val activity = controller.get()
        lateinit var navigation: MusicDetailNavigation
        val favoritesQuery = androidx.compose.runtime.mutableStateOf("")

        init {
            RecordingActivityClient.backRequests = 0
            org.jetbrains.compose.resources.setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
            val shadow = Class.forName("org.robolectric.shadows.ShadowWindowOnBackInvokedDispatcher")
            ReflectionHelpers.callStaticMethod<Any>(shadow, "setEnablePredictiveBack", ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
            ReflectionHelpers.callInstanceMethod<Any>(RuntimeEnvironment.getApplication().applicationInfo, "setEnableOnBackInvokedCallback", ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
            controller.setup().visible().windowFocusChanged(true)
            activity.setContent {
                navigation = rememberMusicDetailNavigation(queries) {}
                val favoritesEntry = rememberAutomotiveFavoritesEntry()
                CompositionLocalProvider(
                    LocalAutomotiveFavoritesEntry provides favoritesEntry,
                    LocalPlatformDescriptor provides platform,
                    LocalOfflineDownloadUiState provides if (favoritesRoot) OfflineDownloadUiState(onIntent = {}) else OfflineDownloadUiState(),
                    LocalMusicDetailNavigation provides navigation,
                    LocalPlayerPresentation provides androidx.compose.runtime.remember { PlayerPresentation() },
                ) {
                    ProvideUiResourceLanguage("en") {
                        LynMusicTheme { if (portraitFavoritesRoot) PortraitFavorites() else MusicDetailHost(onPlayerIntent = {}) {
                            if (favoritesRoot) {
                                val tracks = androidx.compose.runtime.remember { (0..2).map {
                                    Track("favorite-$it", "source", "Favorite $it", "Artist", "Album",
                                        mediaLocator = "", relativePath = "$it")
                                } }
                                FavoritesTab(
                                    state = FavoritesState(isLoadingContent = false, tracks = tracks, filteredTracks = tracks,
                                        favoriteTrackIds = tracks.mapTo(mutableSetOf()) { it.id }),
                                    importState = ImportState(capabilities = platform.capabilities), onlineState = OnlineFavoritesState(),
                                    onFavoritesIntent = {}, onOnlineIntent = {}, onPlayerIntent = {},
                                    showSearchField = false, showRefreshActionButton = false,
                                    onOpenLibraryNavigationTarget = { navigation.open(it) },
                                )
                            } else Text("Root")
                        } }
                    }
                }
            }
            frames()
        }

        @androidx.compose.runtime.Composable
        private fun PortraitFavorites() {
            val tracks = androidx.compose.runtime.remember { (0..2).map {
                Track("favorite-$it","source","Favorite $it",mediaLocator = "",relativePath = "$it")
            } }
            AutomotivePortraitShell(
                portraitNavigation = true,mobilePortraitMiniPlayer = true,hideMiniPlayerBar = false,
                selectedTab = AppTab.Favorites,onTabSelected = {},platform = platform,
                myState = MyState(),libraryState = LibraryState(),onlineLibraryState = OnlineLibraryState(),
                playlistsState = PlaylistsState(),onlinePlaylistsState = OnlinePlaylistsState(),
                favoritesState = FavoritesState(isLoadingContent = false,query = favoritesQuery.value,
                    tracks = tracks,filteredTracks = tracks,favoriteTrackIds = tracks.mapTo(mutableSetOf()) { it.id }),
                onlineFavoritesState = OnlineFavoritesState(),importState = ImportState(capabilities = platform.capabilities),
                playerState = PlayerState(),settingsState = SettingsState(),
                onMyIntent = {},onLibraryIntent = {},onOnlineLibraryIntent = {},onPlaylistsIntent = {},onOnlinePlaylistsIntent = {},
                onFavoritesIntent = { if (it is FavoritesIntent.SearchChanged) favoritesQuery.value = it.query },
                onOnlineFavoritesIntent = {},onImportIntent = {},onPlayerIntent = {},onSettingsIntent = {},
                onOpenBackgroundRunSettings = {},onOpenLibraryNavigationTarget = { navigation.open(it) },onOpenAddToPlaylist = {},
            )
        }

        fun saveState() { controller.saveInstanceState(android.os.Bundle()) }

        fun frames(count: Int = 30) {
            compose.waitForIdle()
            repeat(count) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
            compose.waitForIdle()
        }

        fun focusActivity() {
            controller.windowFocusChanged(false).windowFocusChanged(true); frames()
            assertTrue(topCallback() is OnBackAnimationCallback)
            // Robolectric keeps callback info for only the most recent window globally.
            // Resync the real Activity callback after a dismissed sheet's window loses focus.
            val marker = OnBackInvokedCallback {}
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(0,marker)
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(marker)
            frames()
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
