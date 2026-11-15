@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package top.iwesley.lyn.music.automotive.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.favorites.*
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.resources.Res
import top.iwesley.lyn.music.resources.favorites_online_load_failed
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class FavoritesLayoutUiTest {
    @Test fun songsOnlyHeaderPlaysTheDisplayedOrderOnPhoneAndTablet() = onUiThread {
        for (width in listOf(390, 1194)) Fixture(width = width).use { ui ->
            assertTrue(ui.visible("3 songs"))
            assertFalse(ui.visible("Song"))
            assertFalse(ui.visible("Album"))
            assertFalse(ui.visible("Artist"))
            val count = ui.tagged("favorites-track-count").boundsInRoot
            val play = ui.tagged("favorites-play-all").boundsInRoot
            assertTrue(play.right < count.left)
            ui.capture("$width")
            ui.click("Play all")
            assertEquals(listOf<PlayerIntent>(PlayerIntent.PlayTracks(ui.tracks, 0)), ui.plays)
            assertTrue(ui.onlineIntents.isEmpty())
        }
    }

    @Test fun searchAndRemovalUpdateTheCountAndPlaybackList() = onUiThread {
        Fixture().use { ui ->
            ui.query = "Song B"; ui.render()
            assertTrue(ui.visible("1 song"))
            assertFalse(ui.visible("Song A"))
            ui.click("Play all")
            assertEquals(listOf(ui.tracks[1]), (ui.plays.single() as PlayerIntent.PlayTracks).tracks)
            ui.click("More actions for Song B")
            ui.click("Remove from favorites")
            assertTrue(ui.visible("0 songs"))
            assertTrue(ui.tagged("favorites-play-all").config.contains(SemanticsProperties.Disabled))
            ui.query = ""; ui.render()
            assertTrue(ui.visible("2 songs"))
            assertFalse(ui.visible("Song B"))
        }
    }

    @Test fun onlinePlaybackUsesLoadedSongsAndDoesNotRequestAnotherPage() = onUiThread {
        Fixture(online = true).use { ui ->
            assertTrue(ui.visible("2+ songs"))
            assertTrue(ui.visible("Play loaded songs"))
            assertTrue(ui.onlineIntents.isEmpty())
            ui.click("Play loaded songs")
            assertEquals(PlayerIntent.PlayTracks(ui.tracks.take(2), 0), ui.plays.single())
            assertTrue(ui.onlineIntents.isEmpty())
            ui.click("Load more")
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.LoadMore), ui.onlineIntents)
            ui.finishLoadingMore()
            assertTrue(ui.visible("3 songs"))
            assertFalse(ui.visible("Play loaded songs"))
            ui.click("Play all")
            assertEquals(PlayerIntent.PlayTracks(ui.tracks, 0), ui.plays.last())
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.LoadMore), ui.onlineIntents)
        }
    }

    @Test fun loadingAndEmptyListsKeepPlaybackDisabled() = onUiThread {
        for (online in listOf(false, true)) Fixture(online = online, loading = true).use { ui ->
            assertTrue(ui.visible("Loading"))
            assertFalse(ui.visible("0 songs"))
            assertTrue(ui.tagged("favorites-play-all").config.contains(SemanticsProperties.Disabled))
            ui.showEmpty()
            assertTrue(ui.visible("0 songs"))
            assertTrue(ui.tagged("favorites-play-all").config.contains(SemanticsProperties.Disabled))
            assertTrue(ui.plays.isEmpty())
            assertTrue(ui.onlineIntents.isEmpty())
        }
    }

    @Test fun horizontalDragDoesNotRevealOtherCategoriesOrPlayMusic() = onUiThread {
        Fixture().use { ui ->
            ui.swipe()
            assertTrue(ui.visible("3 songs"))
            assertFalse(ui.visible("Album"))
            assertFalse(ui.visible("Artist"))
            assertTrue(ui.visible("Song B"))
            assertTrue(ui.plays.isEmpty())
        }
    }

    @Test fun largeTextWrapsTheHeaderWithoutCoveringSongs() = onUiThread {
        Fixture(fontScale = 2f).use { ui ->
            val header = ui.tagged("favorites-track-actions").boundsInRoot
            val play = ui.tagged("favorites-play-all").boundsInRoot
            assertTrue(header.contains(play.topLeft) && header.contains(play.bottomRight - Offset(1f, 1f)))
            assertTrue(ui.textNode("Song A").boundsInRoot.top > header.bottom)
            ui.click("Play all")
            assertEquals(PlayerIntent.PlayTracks(ui.tracks, 0), ui.plays.single())
        }
    }

    @Test fun anEmptyOnlinePageWithMoreResultsStillOffersLoadMore() = onUiThread {
        Fixture(online = true).use { ui ->
            ui.onlineState = ui.onlineState.copy(tracks = emptyList(), canLoadMore = true)
            ui.render()
            assertTrue(ui.visible("0+ songs"))
            assertFalse(ui.visible("No favorite songs yet"))
            assertTrue(ui.tagged("favorites-play-all").config.contains(SemanticsProperties.Disabled))
            ui.click("Load more")
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.LoadMore), ui.onlineIntents)
        }
    }

    @Test fun initialFailureRetriesRefreshAndPagingFailureRetainsPlayback() = onUiThread {
        Fixture(online = true).use { ui ->
            val error = uiText(Res.string.favorites_online_load_failed)
            ui.onlineState = ui.onlineState.copy(tracks = emptyList(), canLoadMore = false, errorMessage = error)
            ui.render(); ui.click("Retry")
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.Refresh), ui.onlineIntents)
            ui.onlineState = ui.onlineState.copy(tracks = ui.tracks.take(2), canLoadMore = true, errorMessage = error)
            ui.render(); ui.click("Play loaded songs")
            assertEquals(PlayerIntent.PlayTracks(ui.tracks.take(2), 0), ui.plays.single())
            ui.click("Retry")
            assertEquals(listOf(OnlineFavoritesIntent.Refresh, OnlineFavoritesIntent.LoadMore), ui.onlineIntents)
        }
    }

    @Test fun loadingAnotherPageDisablesOnlyThePagingButton() = onUiThread {
        Fixture(online = true).use { ui ->
            ui.onlineState = ui.onlineState.copy(isLoadingMore = true)
            ui.render()
            assertTrue(ui.tagged("favorites-load-more").config.contains(SemanticsProperties.Disabled))
            assertFalse(ui.tagged("favorites-play-all").config.contains(SemanticsProperties.Disabled))
            ui.click("Play loaded songs")
            assertEquals(PlayerIntent.PlayTracks(ui.tracks.take(2), 0), ui.plays.single())
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(width: Int = 390, val online: Boolean = false, loading: Boolean = false, fontScale: Float = 1f) : AutoCloseable {
        private val platform = PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        val tracks = ('A'..'C').map { letter ->
            Track(letter.toString(), "source", "Song $letter", "Artist A", "Album A",
                mediaLocator = "", relativePath = "$letter", artistId = "artist", albumId = "album")
        }
        private var favorites by mutableStateOf(if (loading) emptyList() else tracks)
        private var loading by mutableStateOf(loading)
        var query by mutableStateOf("")
        var onlineState by mutableStateOf(if (online) OnlineFavoritesState(
            sourceId = "source", tracks = if (loading) emptyList() else tracks.take(2),
            isLoading = loading, canLoadMore = !loading,
        ) else OnlineFavoritesState())
        val plays = mutableListOf<PlayerIntent>()
        val onlineIntents = mutableListOf<OnlineFavoritesIntent>()
        private var frame = 0L
        private val scene = ImageComposeScene(width, 844) {
            ProvideUiResourceLanguage("en") {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale),
                    LocalPlatformDescriptor provides platform,
                    LocalPlayerPresentation provides remember { PlayerPresentation() },
                ) {
                    LynMusicTheme(themeTokens = OCEAN_APP_THEME_TOKENS, textPalette = AppThemeTextPalette.Black) {
                        val visible = favorites.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
                        FavoritesTab(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            state = FavoritesState(isLoadingContent = this@Fixture.loading, query = query,
                                tracks = favorites, filteredTracks = visible,
                                filteredAlbums = deriveVisibleAlbums(visible), filteredArtists = deriveVisibleArtists(visible),
                                favoriteTrackIds = favorites.mapTo(mutableSetOf()) { it.id }),
                            importState = ImportState(capabilities = platform.capabilities), onlineState = onlineState,
                            showSearchField = false, showRefreshActionButton = false, showDuration = width >= 600,
                            showInlineBatchOperationButton = false,
                            onFavoritesIntent = { intent ->
                                if (intent is FavoritesIntent.ToggleFavorite) favorites = favorites.filterNot { it.id == intent.track.id }
                            },
                            onOnlineIntent = { onlineIntents += it }, onPlayerIntent = { plays += it },
                        )
                    }
                }
            }
        }
        init { render() }
        fun finishLoadingMore() { onlineState = onlineState.copy(tracks = tracks, canLoadMore = false); render() }
        fun showEmpty() {
            favorites = emptyList(); loading = false
            if (online) onlineState = onlineState.copy(tracks = emptyList(), isLoading = false, canLoadMore = false)
            render()
        }
        fun render(frames: Int = 24) { repeat(frames) { Snapshot.sendApplyNotifications(); scene.render(++frame * 16_666_667L).close() } }
        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.filter { !it.boundsInRoot.isEmpty }
        }
        private fun matches(node: SemanticsNode, label: String) = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        fun visible(label: String) = nodes().any { matches(it, label) }
        fun textNode(label: String) = nodes().lastOrNull { matches(it, label) } ?: error("Missing $label")
        fun tagged(tag: String) = nodes().last { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun click(label: String) {
            val point = textNode(label).boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point)
            scene.sendPointerEvent(PointerEventType.Release, point); render()
        }
        fun swipe() {
            val start = Offset(320f, 200f); val end = Offset(60f, 200f)
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, timeMillis = frame * 16)
            repeat(12) { step ->
                scene.sendPointerEvent(PointerEventType.Move, start + (end - start) * ((step + 1) / 12f),
                    type = PointerType.Touch, timeMillis = frame * 16)
                render(1)
            }
            scene.sendPointerEvent(PointerEventType.Release, end, type = PointerType.Touch, timeMillis = frame * 16); render()
        }
        fun capture(name: String) {
            val file = File("build/reports/favorites-layout/automotive-$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() = scene.close()
    }
}
