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
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.deriveVisibleAlbums
import top.iwesley.lyn.music.feature.library.deriveVisibleArtists
import top.iwesley.lyn.music.data.repository.OnlineAlbumItem
import top.iwesley.lyn.music.data.repository.OnlineArtistItem
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class FavoritesMenuIntegrationTest {
    @Test
    fun `favorite menu opens independent details without changing library mode or search`() = onUiThread {
        for (onlineFavorites in listOf(false, true)) {
            for (query in listOf("", "unrelated search")) {
                for (label in listOf("View artist", "View album")) {
                    Fixture(online = onlineFavorites, width = 360, navigationEnabled = true,
                        libraryInitiallyOnline = !onlineFavorites, navigateToLibrary = true,
                        libraryQuery = query, deferLibrarySourceChange = !onlineFavorites).use { ui ->
                        ui.click("More actions for Track A")
                        ui.click(label)
                        assertFalse(ui.visible("View artist"))
                        assertFalse(ui.visible("View album"))
                        assertEquals(if (!onlineFavorites) "library-online" else null, ui.onlineLibrary.sourceId)
                        assertEquals(query, if (!onlineFavorites) ui.onlineLibrary.query else ui.library.query)
                        assertEquals(1, ui.navigationHandledCount)
                        assertEquals(1, ui.navigationTargets.size)
                        val expected = if (onlineFavorites) {
                            if (label == "View artist") LibraryNavigationTarget.OnlineArtist("source", "artist-a", "Artist A")
                            else LibraryNavigationTarget.OnlineAlbum("source", "album-a", "Album A", "Artist A")
                        } else {
                            if (label == "View artist") LibraryNavigationTarget.Artist(libraryArtistId("Artist A"))
                            else LibraryNavigationTarget.Album(libraryAlbumId("Artist A", "Album A"))
                        }
                        assertEquals(expected, ui.navigationTargets.single())
                        assertTrue(ui.visible("Back"))
                        assertTrue(ui.libraryOnlineIntents.isEmpty())
                        assertTrue(ui.appFavoriteTargets.isEmpty())
                        assertTrue(ui.localIntents.isEmpty())
                        assertTrue(ui.onlineIntents.isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun `library and favorites menus expose row navigation in narrow and wide layouts`() = onUiThread {
        for (online in listOf(false, true)) {
            for (favorites in listOf(false, true)) {
                for (width in listOf(360, 1000)) Fixture(online, width = width, navigationEnabled = true).use { ui ->
                    ui.showFavorites = favorites
                    ui.render()
                    ui.click("More actions for Track A")
                    ui.click("View artist")
                    assertFalse(ui.visible("View album"))
                    ui.backFromDetail()
                    ui.click("More actions for Track A")
                    ui.click("View album")
                    assertFalse(ui.visible("View artist"))
                    val expected = if (online) listOf(
                        LibraryNavigationTarget.OnlineArtist("source", "artist-a", "Artist A"),
                        LibraryNavigationTarget.OnlineAlbum("source", "album-a", "Album A", "Artist A"),
                    ) else listOf(
                        LibraryNavigationTarget.Artist(libraryArtistId("Artist A")),
                        LibraryNavigationTarget.Album(libraryAlbumId("Artist A", "Album A")),
                    )
                    assertEquals(expected, ui.navigationTargets)
                    assertTrue(ui.appFavoriteTargets.isEmpty())
                    assertTrue(ui.localIntents.isEmpty())
                    assertTrue(ui.onlineIntents.isEmpty())
                    assertTrue(ui.playlistTracks.isEmpty())
                }
            }
        }
    }

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
        Fixture(online = true, previousUnlike = true).use { ui ->
            ui.click("More actions for Track A")
            assertFalse(ui.visible("Add to favorites"))
            ui.click("Remove from favorites")
            ui.assertOnlineRemoval()
        }
    }

    @Test
    fun `independent album detail uses the app favorite state instead of assuming the whole album is liked`() = onUiThread {
        Fixture(online = true, previousUnlike = true, navigationEnabled = true).use { ui ->
            ui.click("Album")
            ui.click("Album A")
            ui.click("More actions for Track A")
            assertTrue(ui.visible("Add to favorites"))
            ui.click("Add to favorites")
            assertEquals(listOf(ui.track to true), ui.appFavoriteTargets)
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.SetFavorite("source", ui.track, true)), ui.onlineIntents)
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

    private class Fixture(
        online: Boolean, previousUnlike: Boolean = false, width: Int = 1000, navigationEnabled: Boolean = false,
        libraryInitiallyOnline: Boolean = online, private val navigateToLibrary: Boolean = false,
        libraryQuery: String = "", private val deferLibrarySourceChange: Boolean = false,
    ) : AutoCloseable {
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
            artistId = if (online && navigationEnabled) "artist-a" else null,
            albumId = if (online && navigationEnabled) "album-a" else null,
        )
        private val importState = ImportState(
            capabilities = platform.capabilities,
            sources = listOf(SourceWithStatus(ImportSource(
                id = "source",
                type = ImportSourceType.NAVIDROME,
                label = "Server",
                rootReference = "https://example.invalid",
                indexMode = if (online) ImportSourceIndexMode.ONLINE else ImportSourceIndexMode.LOCAL_INDEX,
            ))) + if (libraryInitiallyOnline && !online) listOf(SourceWithStatus(ImportSource(
                id = "library-online", type = ImportSourceType.NAVIDROME, label = "Library server",
                rootReference = "https://library.example.invalid", indexMode = ImportSourceIndexMode.ONLINE,
            ))) else emptyList(),
        )
        // Existing online refresh accepts the server row while retaining the earlier operation record.
        private val onlineFavorites = if (online) OnlineFavoritesState(
            sourceId = "source",
            tracks = listOf(track),
            favoriteOverridesBySourceId = if (previousUnlike) mapOf("source" to mapOf("a" to false)) else emptyMap(),
        ) else OnlineFavoritesState()
        var onlineLibrary by mutableStateOf(if (libraryInitiallyOnline) OnlineLibraryState(
            sourceId = if (online) "source" else "library-online", query = libraryQuery,
            tracks = if (online) listOf(track) else emptyList(),
        ) else OnlineLibraryState())
        var library by mutableStateOf(localLibraryState(libraryQuery))
        private var pendingNavigation by mutableStateOf<LibraryNavigationTarget?>(null)
        private var deferredSourceChange: OnlineLibraryIntent.SelectSource? = null
        var navigationHandledCount = 0
        val libraryOnlineIntents = mutableListOf<OnlineLibraryIntent>()
        private var localFavorites by mutableStateOf(localFavoriteState(true))
        var showFavorites by mutableStateOf(true)
        val onlineIntents = mutableListOf<OnlineFavoritesIntent>()
        val localIntents = mutableListOf<FavoritesIntent>()
        val appFavoriteTargets = mutableListOf<Pair<Track, Boolean>>()
        val playlistTracks = mutableListOf<Track>()
        val navigationTargets = mutableListOf<LibraryNavigationTarget>()
        private val scheduler = TestCoroutineScheduler()
        private lateinit var detailNavigation: MusicDetailNavigation
        private val detailQueries = top.iwesley.lyn.music.feature.details.MusicDetailQueries { request ->
            kotlinx.coroutines.flow.flowOf(detailFixtureData(request, listOf(track)))
        }
        private var frame = 0L
        private val scene = ImageComposeScene(width, 1000, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
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
                val details = rememberMusicDetailNavigation(detailQueries) {}.also { detailNavigation = it }
                CompositionLocalProvider(LocalMusicDetailNavigation provides details,
                    LocalPlatformDescriptor provides platform,
                    LocalTrackListActionBindings provides bindings,
                ) {
                    LynMusicTheme {
                        MusicDetailHost(onPlayerIntent = { error("Unexpected playback") }, modifier = Modifier.fillMaxSize()) {

                        if (showFavorites) {
                            FavoritesTab(
                                state = localFavorites,
                                importState = importState,
                                onlineState = onlineFavorites,
                                onFavoritesIntent = ::onLocalIntent,
                                onOnlineIntent = { onlineIntents += it },
                                onPlayerIntent = { error("Menu action must not play a song") },
                                showDuration = width >= 600,
                                onOpenLibraryNavigationTarget = if (navigationEnabled) (::openNavigation) else null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LibraryTab(
                                state = library,
                                favoritesState = localFavorites,
                                importState = importState,
                                onlineState = onlineLibrary,
                                onLibraryIntent = { intent ->
                                    library = when (intent) {
                                        is LibraryIntent.SearchChanged -> localLibraryState(intent.query, library.selectedSourceFilter)
                                        is LibraryIntent.SourceFilterChanged -> localLibraryState(library.query, intent.filter)
                                        else -> library
                                    }
                                },
                                onFavoritesIntent = ::onLocalIntent,
                                onOnlineIntent = ::onLibraryOnlineIntent,
                                onPlayerIntent = { error("Menu action must not play a song") },
                                showDuration = width >= 600,
                                navigationTarget = pendingNavigation,
                                onNavigationHandled = { navigationHandledCount++; pendingNavigation = null },
                                onOpenLibraryNavigationTarget = if (navigationEnabled) (::openNavigation) else null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        }
                    }
                }
            }
        }

        init { render() }

        private fun localLibraryState(query: String, filter: LibrarySourceFilter = LibrarySourceFilter.ALL): LibraryState {
            val tracks = listOf(track)
            val visible = tracks.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            return LibraryState(isLoadingContent = false, query = query, tracks = tracks, filteredTracks = visible,
                albums = deriveVisibleAlbums(tracks), filteredAlbums = deriveVisibleAlbums(visible),
                artists = deriveVisibleArtists(tracks), filteredArtists = deriveVisibleArtists(visible), selectedSourceFilter = filter)
        }

        fun backFromDetail() { detailNavigation.pop(); render() }

        private fun openNavigation(target: LibraryNavigationTarget) {
            navigationTargets += target
            navigationHandledCount++
            detailNavigation.open(target)
        }

        private fun onLibraryOnlineIntent(intent: OnlineLibraryIntent) {
            libraryOnlineIntents += intent
            when (intent) {
                is OnlineLibraryIntent.SelectSource -> {
                    if (deferLibrarySourceChange) deferredSourceChange = intent
                    else onlineLibrary = OnlineLibraryState(sourceId = intent.sourceId)
                }
                is OnlineLibraryIntent.SearchChanged -> onlineLibrary = onlineLibrary.copy(query = intent.query)
                is OnlineLibraryIntent.PrepareAlbumNavigation -> onlineLibrary = onlineLibrary.copy(sourceId = intent.sourceId,
                    knownAlbumItemsById = onlineLibrary.knownAlbumItemsById + (intent.albumId to OnlineAlbumItem(
                        Album(intent.albumId, intent.albumTitle.orEmpty(), intent.artistName), intent.artworkLocator)))
                is OnlineLibraryIntent.PrepareArtistNavigation -> onlineLibrary = onlineLibrary.copy(sourceId = intent.sourceId,
                    knownArtistItemsById = onlineLibrary.knownArtistItemsById + (intent.artistId to OnlineArtistItem(
                        Artist(intent.artistId, intent.artistName.orEmpty()))))
                is OnlineLibraryIntent.LoadAlbumTracks -> onlineLibrary = onlineLibrary.copy(
                    selectedAlbumTracks = onlineLibrary.selectedAlbumTracks + (intent.albumId to listOf(track)))
                is OnlineLibraryIntent.LoadArtistAlbums -> onlineLibrary = onlineLibrary.copy(
                    selectedArtistAlbumsById = onlineLibrary.selectedArtistAlbumsById + (intent.artistId to emptyList()))
                else -> Unit
            }
        }

        fun completeLibrarySourceChange() {
            onlineLibrary = OnlineLibraryState(sourceId = assertNotNull(deferredSourceChange).sourceId)
            deferredSourceChange = null
            render()
        }

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

private fun detailFixtureData(request: top.iwesley.lyn.music.feature.details.MusicDetailRequest, tracks: List<Track>): top.iwesley.lyn.music.feature.details.MusicDetailData {
    val album = deriveVisibleAlbums(tracks).firstOrNull { it.id == request.entityId }
    val artist = deriveVisibleArtists(tracks).firstOrNull { it.id == request.entityId }
    return top.iwesley.lyn.music.feature.details.MusicDetailData(
        title = request.title ?: (if (request.kind == top.iwesley.lyn.music.feature.details.MusicDetailKind.Album) album?.title ?: tracks.firstOrNull()?.albumTitle else artist?.name ?: tracks.firstOrNull()?.artistName).orEmpty(),
        artistName = request.artistName ?: album?.artistName,
        tracks = tracks,
        albums = if (request.kind == top.iwesley.lyn.music.feature.details.MusicDetailKind.Artist) deriveVisibleAlbums(tracks).map { top.iwesley.lyn.music.feature.details.MusicDetailAlbum(it) } else emptyList(),
    )
}
