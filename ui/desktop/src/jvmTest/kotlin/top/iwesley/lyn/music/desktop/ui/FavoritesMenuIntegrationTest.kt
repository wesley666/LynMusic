@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.ui.InternalComposeUiApi::class,
    androidx.compose.runtime.InternalComposeApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package top.iwesley.lyn.music.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import javax.swing.SwingUtilities
import kotlin.test.*
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class FavoritesMenuIntegrationTest {
    @Test
    fun `online favorites preserve the pages remove callback`() = onUiThread {
        Fixture(online = true).use { ui ->
            ui.click("More actions for Track A")
            ui.click("Remove from favorites")
            ui.assertOnlineRemoval()
        }
    }

    @Test
    fun `server returned favorites remain removable despite an earlier unlike`() = onUiThread {
        for (albumDetail in listOf(false, true)) {
            Fixture(online = true, previousUnlike = true).use { ui ->
                if (albumDetail) {
                    ui.click("Album")
                    ui.click("Album A")
                }
                ui.click("More actions for Track A")
                assertFalse(ui.visible("Add to favorites"))
                ui.click("Remove from favorites")
                ui.assertOnlineRemoval()
            }
        }
    }

    @Test
    fun `online favorites keep refresh and selected track playlist callbacks`() = onUiThread {
        Fixture(online = true, previousUnlike = true).use { ui ->
            ui.click("Refresh")
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.Refresh), ui.onlineIntents)
            ui.click("More actions for Track A")
            ui.click("Add to playlist")
            assertEquals(listOf(ui.track), ui.playlistTracks)
            assertFalse(ui.visible("Remove from favorites"))
            assertTrue(ui.appFavoriteTargets.isEmpty())
            assertTrue(ui.localIntents.isEmpty())
        }
    }

    @Test
    fun `favorites menu policy does not change online library actions`() = onUiThread {
        Fixture(online = true, previousUnlike = true).use { ui ->
            ui.showFavorites = false
            ui.render()
            ui.click("More actions for Track A")
            assertFalse(ui.visible("Remove from favorites"))
            ui.click("Add to favorites")
            assertEquals(listOf(ui.track to true), ui.appFavoriteTargets)
            assertEquals(OnlineFavoritesIntent.SetFavorite("source", ui.track, true), ui.onlineIntents.single())
        }
    }

    @Test
    fun `local indexed favorites retain refresh and toggle events`() = onUiThread {
        Fixture(online = false).use { ui ->
            ui.click("Refresh")
            assertEquals(listOf<FavoritesIntent>(FavoritesIntent.Refresh), ui.localIntents)
            // Model the existing Store delivering the refreshed local favorite IDs.
            ui.setLocalFavorite(false)
            ui.render()
            assertFalse(ui.visible("More actions for Track A"))
            ui.setLocalFavorite(true)
            ui.render()
            ui.click("More actions for Track A")
            ui.click("Remove from favorites")
            assertEquals(listOf(FavoritesIntent.Refresh, FavoritesIntent.ToggleFavorite(ui.track)), ui.localIntents)
            assertEquals(listOf(ui.track to false), ui.appFavoriteTargets)
            assertTrue(ui.onlineIntents.isEmpty())
            assertFalse(ui.visible("More actions for Track A"))
        }
    }

    @Test
    fun `local indexed library still adds and removes favorites through existing toggle`() = onUiThread {
        Fixture(online = false).use { ui ->
            ui.setLocalFavorite(false)
            ui.showFavorites = false
            ui.render()
            assertFalse(ui.visible("Liked"))
            ui.click("More actions for Track A")
            ui.click("Add to favorites")
            assertTrue(ui.visible("Liked"))
            ui.click("More actions for Track A")
            ui.click("Remove from favorites")
            assertFalse(ui.visible("Liked"))
            assertEquals(List<FavoritesIntent>(2) { FavoritesIntent.ToggleFavorite(ui.track) }, ui.localIntents)
            assertEquals(listOf(ui.track to true, ui.track to false), ui.appFavoriteTargets)
            assertTrue(ui.onlineIntents.isEmpty())
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try { block() } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    private class Fixture(online: Boolean, previousUnlike: Boolean = false) : AutoCloseable {
        val platform = PlatformDescriptor("Desktop", PlatformCapabilities(true, true, true, true, false))
        val track = Track(
            id = "a",
            sourceId = "source",
            title = "Track A",
            artistName = "Artist A",
            albumTitle = "Album A",
            durationMs = 180_000L,
            relativePath = "a.flac",
            mediaLocator = buildNavidromeSongLocator("source", "a"),
            remoteFavoriteHint = true,
        )
        private val importState = ImportState(
            capabilities = platform.capabilities,
            sources = listOf(SourceWithStatus(ImportSource(
                id = "source",
                type = ImportSourceType.NAVIDROME,
                label = "Server",
                rootReference = "https://example.invalid",
                indexMode = if (online) ImportSourceIndexMode.ONLINE else ImportSourceIndexMode.LOCAL_INDEX,
            ))),
        )
        // Existing online refresh accepts the server row while retaining the earlier operation record.
        private val onlineFavorites = if (online) OnlineFavoritesState(
            sourceId = "source",
            tracks = listOf(track),
            favoriteOverridesBySourceId = if (previousUnlike) mapOf("source" to mapOf("a" to false)) else emptyMap(),
        ) else OnlineFavoritesState()
        private val onlineLibrary = if (online) OnlineLibraryState(sourceId = "source", tracks = listOf(track)) else OnlineLibraryState()
        private val library = LibraryState(isLoadingContent = false, tracks = listOf(track), filteredTracks = listOf(track))
        private var localFavorites by mutableStateOf(localFavoriteState(true))
        var showFavorites by mutableStateOf(true)
        val onlineIntents = mutableListOf<OnlineFavoritesIntent>()
        val localIntents = mutableListOf<FavoritesIntent>()
        val appFavoriteTargets = mutableListOf<Pair<Track, Boolean>>()
        val playlistTracks = mutableListOf<Track>()
        private val scheduler = TestCoroutineScheduler()
        private var frame = 0L
        private val scene = ImageComposeScene(1000, 1000, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            ProvideUiResourceLanguage("en") {
                // Use the same state resolver and event routing as the app, rather than a preset liked flag.
                val bindings = TrackListActionBindings(
                    favoriteState = { item ->
                        val binding = playerFavoriteBinding(item, localFavorites.favoriteTrackIds, onlineFavorites, importState)
                        TrackFavoriteUiState(binding.isFavorite, binding.isFavoriteKnown, binding.canToggleFavorite)
                    },
                    onSetFavorite = { item, favorite ->
                        appFavoriteTargets += item to favorite
                        val sourceId = item.onlineNavidromeSourceIdOrNull(importState)
                        if (sourceId != null) {
                            onlineIntents += OnlineFavoritesIntent.SetFavorite(sourceId, item, favorite)
                        } else {
                            onLocalIntent(FavoritesIntent.ToggleFavorite(item))
                        }
                    },
                    onAddToPlaylist = { playlistTracks += it },
                )
                CompositionLocalProvider(
                    LocalPlatformDescriptor provides platform,
                    LocalTrackListActionBindings provides bindings,
                ) {
                    LynMusicTheme {
                        if (showFavorites) {
                            FavoritesTab(
                                state = localFavorites,
                                importState = importState,
                                onlineState = onlineFavorites,
                                onFavoritesIntent = ::onLocalIntent,
                                onOnlineIntent = { onlineIntents += it },
                                onPlayerIntent = { error("Menu action must not play a song") },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LibraryTab(
                                state = library,
                                favoritesState = localFavorites,
                                importState = importState,
                                onlineState = onlineLibrary,
                                onLibraryIntent = {},
                                onFavoritesIntent = ::onLocalIntent,
                                onOnlineIntent = {},
                                onPlayerIntent = { error("Menu action must not play a song") },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        init { render() }

        private fun localFavoriteState(liked: Boolean) = FavoritesState(
            isLoadingContent = false,
            tracks = if (liked) listOf(track) else emptyList(),
            filteredTracks = if (liked) listOf(track) else emptyList(),
            favoriteTrackIds = if (liked) setOf(track.id) else emptySet(),
            canRefreshRemote = true,
        )

        fun setLocalFavorite(liked: Boolean) { localFavorites = localFavoriteState(liked) }

        private fun onLocalIntent(intent: FavoritesIntent) {
            localIntents += intent
            if (intent is FavoritesIntent.ToggleFavorite) {
                setLocalFavorite(intent.track.id !in localFavorites.favoriteTrackIds)
            }
        }

        fun assertOnlineRemoval() {
            assertEquals(OnlineFavoritesIntent.SetFavorite("source", track, false), onlineIntents.single())
            assertTrue(appFavoriteTargets.isEmpty(), "Favorites must use the page's existing removal callback")
            assertTrue(localIntents.isEmpty())
        }

        fun render() {
            repeat(40) {
                scheduler.runCurrent()
                Snapshot.sendApplyNotifications()
                scene.render(++frame * 16_666_667L).close()
            }
        }

        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        }

        private fun matches(node: SemanticsNode, label: String) =
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true

        fun visible(label: String) = nodes().any { matches(it, label) }

        fun click(label: String) {
            val node = nodes().lastOrNull { matches(it, label) } ?: error("Missing $label")
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center)
            render()
        }

        override fun close() = scene.close()
    }
}
