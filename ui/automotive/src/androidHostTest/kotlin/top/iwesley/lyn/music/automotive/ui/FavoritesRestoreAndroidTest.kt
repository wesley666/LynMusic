@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package top.iwesley.lyn.music.automotive.ui

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollToIndex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.iwesley.lyn.music.ANDROID_AUTOMOTIVE_PLATFORM_NAME
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.NavidromeOnlineDataSource
import top.iwesley.lyn.music.data.repository.OnlinePage
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], application = Application::class, qualifiers = "w480dp-h900dp",
    shadows = [RecordingActivityClient::class])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesRestoreAndroidTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun activityRecreationKeepsOnlinePositionWhenIndexedFavoritesFinishFirst() {
        for (indexedCount in listOf(0, 3)) checkRecreation(indexedCount, indexedFirst = true)
    }

    @Test fun activityRecreationKeepsOnlinePositionWhenOnlineSourceRestoresFirst() {
        for (indexedCount in listOf(0, 3)) checkRecreation(indexedCount, indexedFirst = false)
    }

    private fun checkRecreation(indexedCount: Int, indexedFirst: Boolean) {
        val preferences = object : LibrarySourceFilterPreferencesStore by unused() {
            override val onlineFavoritesSourceId = MutableStateFlow<String?>("source")
        }
        val tracks = (0..49).map {
            Track("song-$it", "source", "Song $it", mediaLocator = "", relativePath = "$it")
        }
        var requests = 0
        var response = CompletableDeferred(OnlinePage(tracks, totalCount = 50))
        val repository = object : NavidromeOnlineDataSource by unused() {
            override suspend fun favoriteTracks(sourceId: String, offset: Int, limit: Int, query: String): OnlinePage<Track> {
                assertEquals("source", sourceId)
                assertEquals(0, offset)
                requests++
                return response.await()
            }
        }
        val saved: Bundle
        val position: Pair<Int, Int>
        Fixture(preferences, repository).use { ui ->
            ui.restoreSources()
            assertEquals(50, ui.store.state.value.tracks.size)
            compose.onNodeWithTag("automotive-favorites-list").performScrollToIndex(30)
            ui.frames()
            position = ui.position()
            assertTrue(position.first >= 4)
            assertEquals(1, requests)
            saved = ui.saveAndDestroy()
        }

        // Recreate the Android saved-state owner and the real Store, not just the composition.
        response = CompletableDeferred()
        Fixture(preferences, repository, saved).use { ui ->
            assertEquals("source", ui.store.state.value.sourceId)
            assertTrue(ui.store.state.value.isLoading)
            assertEquals(position, ui.position())
            assertEquals(1, requests)
            if (indexedFirst) ui.finishIndexedLoad(indexedCount)
            assertEquals(position, ui.position())
            compose.onNodeWithTag("favorites-track-count").assertTextEquals("Loading")
            compose.onNodeWithText("Indexed song 0").assertDoesNotExist()
            ui.restoreSources()
            assertEquals("source", ui.store.state.value.sourceId)
            assertTrue(ui.store.state.value.isLoading)
            if (!indexedFirst) ui.finishIndexedLoad(indexedCount)
            assertEquals(position, ui.position())
            compose.onNodeWithText("Indexed song 0").assertDoesNotExist()
            response.complete(OnlinePage(tracks, totalCount = 50))
            ui.frames()
            assertEquals(position, ui.position())
            compose.onNodeWithText("Song 26").assertIsDisplayed()
            assertEquals(2, requests)
        }
    }

    private inner class Fixture(
        preferences: LibrarySourceFilterPreferencesStore,
        repository: NavidromeOnlineDataSource,
        saved: Bundle? = null,
    ) : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val sources = MutableSharedFlow<List<SourceWithStatus>>(replay = 1)
        private val source = SourceWithStatus(ImportSource("source", ImportSourceType.NAVIDROME,
            "Server", "https://server.example", indexMode = ImportSourceIndexMode.ONLINE))
        private val platform = PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME,
            PlatformCapabilities(true, true, true, true, false))
        val store = OnlineFavoritesStore(repository, object : ImportSourceRepository by unused() {
            override fun observeSources() = sources
        }, preferences, scope, startImmediately = false)
        private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        private val indexedState = androidx.compose.runtime.mutableStateOf(FavoritesState())
        private var destroyed = false
        private lateinit var entry: AutomotiveFavoritesEntry

        init {
            org.jetbrains.compose.resources.setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
            controller.create(saved).start().resume().visible().windowFocusChanged(true)
            controller.get().setContent {
                entry = rememberAutomotiveFavoritesEntry()
                val online by store.state.collectAsState()
                val local by indexedState
                val navigation = rememberMusicDetailNavigation(TestMusicDetailQueries()) {}
                CompositionLocalProvider(LocalAutomotiveFavoritesEntry provides entry,
                    LocalPlatformDescriptor provides platform, LocalMusicDetailNavigation provides navigation,
                    LocalPlayerPresentation provides remember { PlayerPresentation() }) {
                    ProvideUiResourceLanguage("en") { LynMusicTheme {
                        AutomotivePortraitShell(
                            portraitNavigation = true, mobilePortraitMiniPlayer = true, hideMiniPlayerBar = false,
                            selectedTab = AppTab.Favorites, onTabSelected = {}, platform = platform,
                            myState = MyState(), libraryState = LibraryState(), onlineLibraryState = OnlineLibraryState(),
                            playlistsState = PlaylistsState(), onlinePlaylistsState = OnlinePlaylistsState(),
                            favoritesState = local, onlineFavoritesState = online,
                            importState = ImportState(capabilities = platform.capabilities, sources = listOf(source)),
                            playerState = PlayerState(), settingsState = SettingsState(),
                            onMyIntent = {}, onLibraryIntent = {}, onOnlineLibraryIntent = {},
                            onPlaylistsIntent = {}, onOnlinePlaylistsIntent = {},
                            onFavoritesIntent = { entry.prepareForIntent(it, local) },
                            onOnlineFavoritesIntent = {
                                entry.prepareForIntent(it, store.state.value, preferences.onlineFavoritesSourceId.value)
                                store.dispatch(it)
                            },
                            onImportIntent = {}, onPlayerIntent = {}, onSettingsIntent = {},
                            onOpenBackgroundRunSettings = {}, onOpenLibraryNavigationTarget = {}, onOpenAddToPlaylist = {},
                        )
                    } }
                }
                LaunchedEffect(store) {
                    withFrameNanos { }
                    store.ensureStartedIfRememberedSource()
                }
            }
            frames()
        }

        fun restoreSources() { assertTrue(sources.tryEmit(listOf(source))); frames() }
        fun finishIndexedLoad(count: Int) {
            val tracks = (0 until count).map {
                Track("indexed-$it", "indexed-source", "Indexed song $it", mediaLocator = "", relativePath = "$it")
            }
            indexedState.value = FavoritesState(isLoadingContent = false, tracks = tracks, filteredTracks = tracks)
            frames()
        }
        fun position() = entry.pageState.tracks.let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }
        fun frames() {
            compose.waitForIdle()
            repeat(20) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
            compose.waitForIdle()
        }
        fun saveAndDestroy(): Bundle {
            val saved = Bundle()
            controller.pause().saveInstanceState(saved).stop().destroy()
            destroyed = true
            scope.cancel()
            return saved
        }
        override fun close() {
            if (!destroyed) controller.pause().stop().destroy()
            scope.cancel()
        }
    }
}

/** Fail on any API outside the favorites restore path so this fixture cannot hide extra requests. */
private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
    error("Unexpected ${T::class.simpleName}.${method.name}")
} as T
