package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.playlists.*

class PlaylistDetailBindingTest {
    private val summary = PlaylistSummary("playlist", "Playlist", artworkLocator = "cover", artworkCacheKey = "cover-key")
    private val route = MusicDetailRoute(entryId = "playlist-entry", kind = "playlist", entityId = "playlist")

    @Test fun playlistNavigationRetainsItsEntryBelowChildrenAndSerializesItsSource() = runTest {
        val owner = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), TestMusicDetailQueries(), this, mutableStateOf(false), {})
        owner.openPlaylist(summary, "server", LibrarySourceFilter.ALL)
        val playlist = owner.top
        assertEquals(playlist, Json.decodeFromString<MusicDetailRoute>(Json.encodeToString(playlist)))
        assertEquals("cover-key", playlist.artworkCacheKey)
        val state = owner.playlistEntry(playlist)
        state.list = LazyListState(23, 11)
        owner.openPlaylist(summary, "server", LibrarySourceFilter.ALL)
        assertEquals(2, owner.backStack.size)
        owner.open(LibraryNavigationTarget.Artist("artist"))
        assertEquals(playlist, owner.playlistRoute)
        owner.requestBack()
        assertSame(state, owner.playlistEntry(playlist))
        assertEquals(23, state.list.firstVisibleItemIndex)
        owner.clear()
        assertNull(owner.playlistRoute)
        assertNotSame(state, owner.playlistEntry(playlist))
    }

    @Test fun playlistOverlayBackIsHandledBeforePoppingAndOldCallbacksAreIgnored() = runTest {
        val owner = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), TestMusicDetailQueries(), this, mutableStateOf(false), {})
        owner.openPlaylist(summary, null, LibrarySourceFilter.ALL)
        val current = owner.top
        val generation = owner.generation
        var closed = false
        owner.setBackHandler(current.entryId) { closed = true; owner.setBackHandler(current.entryId, null) }
        owner.requestBack()
        assertTrue(closed)
        assertEquals(current, owner.top)
        owner.requestBack()
        assertFalse(owner.hasDetails)
        owner.openPlaylist(summary, null, LibrarySourceFilter.ALL)
        owner.requestBack(current.entryId, generation)
        assertTrue(owner.hasDetails)
        owner.clear()
    }

    @Test fun localBindingLoadsOnceAndRetainsSelectionAfterTheRouteLeavesTheStack() {
        val binding = PlaylistDetailBinding()
        val intents = mutableListOf<PlaylistsIntent>()
        val local = PlaylistsState(isLoadingContent = false)
        repeat(3) { binding.sync(route, local, OnlinePlaylistsState(), emptySet(), false, intents::add, {}) }
        assertEquals(listOf<PlaylistsIntent>(PlaylistsIntent.SelectPlaylist("playlist")), intents)
        val selected = local.copy(selectedPlaylistId = "playlist")
        binding.sync(route, selected, OnlinePlaylistsState(), emptySet(), false, intents::add, {})
        assertEquals(1, intents.size)
        binding.sync(null, selected, OnlinePlaylistsState(), emptySet(), false, intents::add, {})
        assertEquals(1, intents.size)
    }

    @Test fun restorationReusesExistingSelectionIncludingAnUnsuccessfulRequest() {
        val intents = mutableListOf<OnlinePlaylistsIntent>()
        val onlineRoute = route.copy(sourceId = "server")
        val state = OnlinePlaylistsState(sourceId = "server", selectedPlaylistId = "playlist", errorMessage = UiText.Raw("Failed"))
        PlaylistDetailBinding().sync(onlineRoute, PlaylistsState(), state, setOf("server"), false, {}, intents::add)
        assertTrue(intents.isEmpty(), "Restoring a page must not turn its error into an automatic retry")
    }

    @Test fun immediateReopenReusesTheSelectionWithoutCleanupRequests() {
        val binding = PlaylistDetailBinding()
        val intents = mutableListOf<PlaylistsIntent>()
        val state = PlaylistsState(selectedPlaylistId = "playlist", selectedPlaylist = PlaylistDetail("playlist", "Playlist"))
        binding.sync(route, state, OnlinePlaylistsState(), emptySet(), false, intents::add, {})
        binding.sync(null, state, OnlinePlaylistsState(), emptySet(), false, intents::add, {})
        binding.sync(route.copy(entryId = "reopened"), state, OnlinePlaylistsState(), emptySet(), false, intents::add, {})
        assertTrue(intents.isEmpty())
    }

    @Test fun onlineBindingRestoresTheSourceBeforeSelectingAndDoesNotRepeatEitherRequest() {
        val binding = PlaylistDetailBinding()
        val intents = mutableListOf<OnlinePlaylistsIntent>()
        val onlineRoute = route.copy(sourceId = "server")
        repeat(2) { binding.sync(onlineRoute, PlaylistsState(), OnlinePlaylistsState(sourceId = "other"), setOf("server"), false, {}, intents::add) }
        assertEquals(listOf<OnlinePlaylistsIntent>(OnlinePlaylistsIntent.SelectSource("server", persist = false)), intents)
        repeat(2) { binding.sync(onlineRoute, PlaylistsState(), OnlinePlaylistsState(sourceId = "server"), setOf("server"), false, {}, intents::add) }
        assertEquals(OnlinePlaylistsIntent.SelectPlaylist("playlist"), intents.last())
        assertEquals(2, intents.size)
    }

    @Test fun temporaryAddDialogSourceChangesDoNotRetargetTheDetail() {
        val binding = PlaylistDetailBinding()
        val intents = mutableListOf<OnlinePlaylistsIntent>()
        val onlineRoute = route.copy(sourceId = "server")
        binding.sync(onlineRoute, PlaylistsState(), OnlinePlaylistsState(sourceId = "server", selectedPlaylistId = "playlist", selectedPlaylist = PlaylistDetail("playlist", "Playlist")), setOf("server"), false, {}, intents::add)
        binding.sync(onlineRoute, PlaylistsState(), OnlinePlaylistsState(sourceId = "other"), setOf("server"), true, {}, intents::add)
        assertTrue(intents.isEmpty())
        binding.sync(onlineRoute, PlaylistsState(), OnlinePlaylistsState(sourceId = "server"), setOf("server"), false, {}, intents::add)
        assertEquals(listOf<OnlinePlaylistsIntent>(OnlinePlaylistsIntent.SelectPlaylist("playlist")), intents)
    }

    @Test fun unavailableSourcesAreNotRequestedAndRemovalDoesNotStartALoadingLoop() {
        val binding = PlaylistDetailBinding()
        val intents = mutableListOf<OnlinePlaylistsIntent>()
        val onlineRoute = route.copy(sourceId = "server")
        binding.sync(onlineRoute, PlaylistsState(), OnlinePlaylistsState(), emptySet(), false, {}, intents::add)
        assertTrue(intents.isEmpty())
        binding.sync(onlineRoute, PlaylistsState(), OnlinePlaylistsState(sourceId = "server", selectedPlaylistId = "playlist", selectedPlaylist = PlaylistDetail("playlist", "Playlist")), setOf("server"), false, {}, intents::add)
        binding.sync(onlineRoute, PlaylistsState(), OnlinePlaylistsState(sourceId = "server"), setOf("server"), false, {}, intents::add)
        assertTrue(intents.isEmpty())
    }

    @Test fun duplicateTracksHaveDistinctKeysAndKeepTheirOriginalRemovalIndices() {
        val track = Track("track", "source", "Song", mediaLocator = "", relativePath = "")
        val other = track.copy(id = "other")
        val detail = PlaylistDetail("playlist", "Playlist", tracks = listOf(track, other, track).map(::PlaylistTrackEntry))
        val rows = playlistDetailRows(detail, PlaylistsState(), route)
        assertEquals(listOf(0, 1, 2), rows.map { it.rawIndex })
        assertEquals(3, rows.map { it.key }.distinct().size)
        assertEquals(rows.map { it.key }, playlistDetailRows(detail, PlaylistsState(), route).map { it.key })
    }

    @Test fun sourceFilteringKeepsTheOriginalOrderAndRemovalIndices() {
        val first = Track("first", "one", "First", mediaLocator = "", relativePath = "")
        val second = first.copy(id = "second", sourceId = "two")
        val third = first.copy(id = "third")
        val detail = PlaylistDetail("playlist", "Playlist", tracks = listOf(first, second, third).map(::PlaylistTrackEntry))
        val state = PlaylistsState(selectedSourceFilter = LibrarySourceFilter.Source("one"))
        val filtered = route.copy(sourceFilter = "source:two")
        val rows = playlistDetailRows(detail, state, filtered)
        assertEquals(listOf(first, third), rows.map { it.track })
        assertEquals(listOf(0, 2), rows.map { it.rawIndex })
        assertEquals(3, playlistDetailRows(detail, state, filtered.copy(sourceId = "server")).size)
    }
}
