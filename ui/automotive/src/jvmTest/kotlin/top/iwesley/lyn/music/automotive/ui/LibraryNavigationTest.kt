package top.iwesley.lyn.music.automotive.ui

import top.iwesley.lyn.music.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId

class LibraryNavigationTest {
    @Test
    fun `local targets leave online context even when filters and visible IDs already match`() {
        val album = Album(libraryAlbumId("Artist", "Album"), "Album", "Artist")
        val artist = Artist(libraryArtistId("Artist"), "Artist")
        for (filter in listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.Source("local-source"))) {
            val targets = listOf(
                LibraryNavigationTarget.Album(album.id, filter),
                LibraryNavigationTarget.Artist(artist.id, filter),
            )
            for (target in targets) {
                for (query in listOf("", "active search")) {
                    val command = resolveLibraryNavigationCommand(
                        target = target, query = query, isOnline = true, onlineSourceId = "online-source",
                        selectedSourceFilter = filter, availableSourceFilters = listOf(LibrarySourceFilter.ALL, filter),
                        filteredAlbums = listOf(album), filteredArtists = listOf(artist),
                    )
                    assertEquals(LibraryNavigationCommand.ApplyContext(filter, clearQuery = query.isNotBlank()), command)
                }
                val localCommand = resolveLibraryNavigationCommand(
                    target = target, query = "", isOnline = false,
                    selectedSourceFilter = filter, availableSourceFilters = listOf(LibrarySourceFilter.ALL, filter),
                    filteredAlbums = listOf(album), filteredArtists = listOf(artist),
                )
                val navigation = assertIs<LibraryNavigationCommand.Navigate>(localCommand)
                when (target) {
                    is LibraryNavigationTarget.Album -> assertEquals(album.id, navigation.resolution.selectedAlbumId)
                    is LibraryNavigationTarget.Artist -> assertEquals(artist.id, navigation.resolution.selectedArtistId)
                    else -> error("Expected a local target")
                }
            }
        }
    }


    @Test
    fun `navigation switches between individual sources of the same protocol`() {
        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Album(
                albumId = libraryAlbumId("Artist", "Album"),
                preferredSourceFilter = LibrarySourceFilter.Source("dav-2"),
            ),
            query = "",
            selectedSourceFilter = LibrarySourceFilter.Source("dav-1"),
            availableSourceFilters = listOf(
                LibrarySourceFilter.ALL,
                LibrarySourceFilter.Source("dav-1"),
                LibrarySourceFilter.Source("dav-2"),
            ),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )
        assertEquals(
            LibraryNavigationCommand.ApplyContext(LibrarySourceFilter.Source("dav-2"), clearQuery = false),
            result,
        )
    }

    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `derive playback targets returns album and artist targets when metadata exists`() {
        val track = testTrack(albumTitle = "Parachutes", artistName = "Coldplay")

        val result = derivePlaybackLibraryNavigationTargets(
            snapshot = PlaybackSnapshot(
                metadataAlbumTitle = "Parachutes",
                metadataArtistName = "Coldplay",
            ),
            track = track,
        )

        assertEquals(
            LibraryNavigationTarget.Album(libraryAlbumId("Coldplay", "Parachutes")),
            result.albumTarget,
        )
        assertEquals(
            LibraryNavigationTarget.Artist(libraryArtistId("Coldplay")),
            result.artistTarget,
        )
    }

    @Test
    fun `derive playback targets returns only artist target when album missing`() {
        val result = derivePlaybackLibraryNavigationTargets(
            snapshot = PlaybackSnapshot(metadataArtistName = "Coldplay"),
            track = testTrack(albumTitle = null, artistName = "Coldplay"),
        )

        assertNull(result.albumTarget)
        assertEquals(
            LibraryNavigationTarget.Artist(libraryArtistId("Coldplay")),
            result.artistTarget,
        )
    }

    @Test
    fun `derive playback targets returns only album target when artist missing`() {
        val result = derivePlaybackLibraryNavigationTargets(
            snapshot = PlaybackSnapshot(metadataAlbumTitle = "Parachutes"),
            track = testTrack(albumTitle = "Parachutes", artistName = null),
        )

        assertEquals(
            LibraryNavigationTarget.Album(libraryAlbumId(null, "Parachutes")),
            result.albumTarget,
        )
        assertNull(result.artistTarget)
    }

    @Test
    fun `derive playback targets falls back to track metadata when snapshot metadata is blank`() {
        val result = derivePlaybackLibraryNavigationTargets(
            snapshot = PlaybackSnapshot(
                metadataAlbumTitle = "   ",
                metadataArtistName = "",
            ),
            track = testTrack(albumTitle = "Parachutes", artistName = "Coldplay"),
        )

        assertEquals(
            LibraryNavigationTarget.Album(libraryAlbumId("Coldplay", "Parachutes")),
            result.albumTarget,
        )
        assertEquals(
            LibraryNavigationTarget.Artist(libraryArtistId("Coldplay")),
            result.artistTarget,
        )
    }

    @Test
    fun `derive playback targets ignores blank values`() {
        val result = derivePlaybackLibraryNavigationTargets(
            snapshot = PlaybackSnapshot(
                metadataAlbumTitle = " ",
                metadataArtistName = "\n",
            ),
            track = testTrack(albumTitle = " ", artistName = ""),
        )

        assertNull(result.albumTarget)
        assertNull(result.artistTarget)
    }

    @Test
    fun `derive track targets returns album and artist targets when metadata exists`() {
        val result = deriveTrackLibraryNavigationTargets(
            testTrack(albumTitle = "Parachutes", artistName = "Coldplay"),
        )

        assertEquals(
            LibraryNavigationTarget.Album(libraryAlbumId("Coldplay", "Parachutes")),
            result.albumTarget,
        )
        assertEquals(
            LibraryNavigationTarget.Artist(libraryArtistId("Coldplay")),
            result.artistTarget,
        )
    }

    @Test
    fun `derive track targets ignores blank values`() {
        val result = deriveTrackLibraryNavigationTargets(
            testTrack(albumTitle = " ", artistName = "\n"),
        )

        assertNull(result.albumTarget)
        assertNull(result.artistTarget)
    }

    @Test
    fun `track row targets require a navigation handler and preserve the source filter`() {
        val track = testTrack(albumTitle = "Parachutes", artistName = "Coldplay")

        val targets = resolveTrackRowLibraryNavigationTargets(
            track = track,
            metadataNavigationEnabled = true,
            preferredSourceFilter = LibrarySourceFilter.Source("dav-1"),
        )
        val disabledTargets = resolveTrackRowLibraryNavigationTargets(
            track = track,
            metadataNavigationEnabled = false,
        )

        assertEquals(
            LibraryNavigationTarget.Album(
                albumId = libraryAlbumId("Coldplay", "Parachutes"),
                preferredSourceFilter = LibrarySourceFilter.Source("dav-1"),
            ),
            targets.albumTarget,
        )
        assertEquals(
            LibraryNavigationTarget.Artist(
                artistId = libraryArtistId("Coldplay"),
                preferredSourceFilter = LibrarySourceFilter.Source("dav-1"),
            ),
            targets.artistTarget,
        )
        assertNull(disabledTargets.albumTarget)
        assertNull(disabledTargets.artistTarget)
    }

    @Test
    fun `online row targets use remote IDs and never fall back to matching local names`() {
        for ((artistId, albumId) in listOf(null to null, "artist-remote" to null, null to "album-remote", "artist-remote" to "album-remote")) {
            val track = testTrack(artistName = "Artist", albumTitle = "Album", artistId = artistId, albumId = albumId)
            for (sourceId in listOf("online-a", "online-b")) {
                val targets = resolveTrackRowLibraryNavigationTargets(
                    track = track,
                    metadataNavigationEnabled = true,
                    onlineSourceId = sourceId,
                )
                assertEquals(artistId?.let { LibraryNavigationTarget.OnlineArtist(sourceId, it, "Artist") }, targets.artistTarget)
                assertEquals(albumId?.let { LibraryNavigationTarget.OnlineAlbum(sourceId, it, "Album", "Artist") }, targets.albumTarget)
            }
        }
    }

    @Test
    fun `online row IDs support missing display names but still require a navigation handler`() {
        val track = testTrack(artistName = null, albumTitle = " ", artistId = "artist-remote", albumId = "album-remote")
        val targets = resolveTrackRowLibraryNavigationTargets(track, metadataNavigationEnabled = true, onlineSourceId = "online")
        assertEquals(LibraryNavigationTarget.OnlineArtist("online", "artist-remote"), targets.artistTarget)
        assertEquals(LibraryNavigationTarget.OnlineAlbum("online", "album-remote"), targets.albumTarget)
        val disabled = resolveTrackRowLibraryNavigationTargets(track, metadataNavigationEnabled = false, onlineSourceId = "online")
        assertNull(disabled.artistTarget)
        assertNull(disabled.albumTarget)
    }

    @Test
    fun `resolve command clears query before navigation`() {
        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Artist(libraryArtistId("Coldplay")),
            query = "cold",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        assertEquals(
            LibraryNavigationCommand.ApplyContext(
                sourceFilter = LibrarySourceFilter.ALL,
                clearQuery = true,
            ),
            result,
        )
    }

    @Test
    fun `navigation query clear guard requires command flag and nonblank query`() {
        assertTrue(shouldClearNavigationQuery(clearQuery = true, query = "cold"))
        assertFalse(shouldClearNavigationQuery(clearQuery = true, query = " "))
        assertFalse(shouldClearNavigationQuery(clearQuery = false, query = "cold"))
    }

    @Test
    fun `resolve command applies default all source for global targets`() {
        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Album(libraryAlbumId("Coldplay", "Parachutes")),
            query = "",
            selectedSourceFilter = LibrarySourceFilter.LOCAL_FOLDER,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.LOCAL_FOLDER),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        assertEquals(
            LibraryNavigationCommand.ApplyContext(
                sourceFilter = LibrarySourceFilter.ALL,
                clearQuery = false,
            ),
            result,
        )
    }

    @Test
    fun `resolve command keeps library source when target prefers current source`() {
        val albumId = libraryAlbumId("Coldplay", "Parachutes")

        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Album(
                albumId = albumId,
                preferredSourceFilter = LibrarySourceFilter.Source("nav-1"),
            ),
            query = "",
            selectedSourceFilter = LibrarySourceFilter.Source("nav-1"),
            availableSourceFilters = listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.Source("nav-1")),
            filteredAlbums = listOf(Album(id = albumId, title = "Parachutes", artistName = "Coldplay")),
            filteredArtists = emptyList(),
        )

        val navigate = assertIs<LibraryNavigationCommand.Navigate>(result)
        assertEquals(LibraryBrowserRootView.Albums, navigate.resolution.rootView)
        assertEquals(albumId, navigate.resolution.selectedAlbumId)
    }

    @Test
    fun `resolve command applies favorite source before opening album`() {
        val albumId = libraryAlbumId("Coldplay", "Parachutes")
        val target = LibraryNavigationTarget.Album(
            albumId = albumId,
            preferredSourceFilter = LibrarySourceFilter.Source("dav-1"),
        )

        val applyContext = resolveLibraryNavigationCommand(
            target = target,
            query = "",
            selectedSourceFilter = LibrarySourceFilter.Source("nav-1"),
            availableSourceFilters = listOf(
                LibrarySourceFilter.ALL,
                LibrarySourceFilter.Source("dav-1"),
                LibrarySourceFilter.Source("nav-1"),
            ),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        assertEquals(
            LibraryNavigationCommand.ApplyContext(
                sourceFilter = LibrarySourceFilter.Source("dav-1"),
                clearQuery = false,
            ),
            applyContext,
        )

        val navigate = resolveLibraryNavigationCommand(
            target = target,
            query = "",
            selectedSourceFilter = LibrarySourceFilter.Source("dav-1"),
            availableSourceFilters = listOf(
                LibrarySourceFilter.ALL,
                LibrarySourceFilter.Source("dav-1"),
                LibrarySourceFilter.Source("nav-1"),
            ),
            filteredAlbums = listOf(Album(id = albumId, title = "Parachutes", artistName = "Coldplay")),
            filteredArtists = emptyList(),
        )

        val navigation = assertIs<LibraryNavigationCommand.Navigate>(navigate)
        assertEquals(LibraryBrowserRootView.Albums, navigation.resolution.rootView)
        assertEquals(albumId, navigation.resolution.selectedAlbumId)
    }

    @Test
    fun `resolve command falls back to all when preferred source is unavailable`() {
        val albumId = libraryAlbumId("Coldplay", "Parachutes")
        val target = LibraryNavigationTarget.Album(
            albumId = albumId,
            preferredSourceFilter = LibrarySourceFilter.Source("emby-1"),
        )

        val applyContext = resolveLibraryNavigationCommand(
            target = target,
            query = "",
            selectedSourceFilter = LibrarySourceFilter.Source("dav-1"),
            availableSourceFilters = listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.Source("dav-1")),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        assertEquals(
            LibraryNavigationCommand.ApplyContext(
                sourceFilter = LibrarySourceFilter.ALL,
                clearQuery = false,
            ),
            applyContext,
        )

        val navigate = resolveLibraryNavigationCommand(
            target = target,
            query = "",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.Source("dav-1")),
            filteredAlbums = listOf(Album(id = albumId, title = "Parachutes", artistName = "Coldplay")),
            filteredArtists = emptyList(),
        )

        val navigation = assertIs<LibraryNavigationCommand.Navigate>(navigate)
        assertEquals(LibraryBrowserRootView.Albums, navigation.resolution.rootView)
        assertEquals(albumId, navigation.resolution.selectedAlbumId)
    }

    @Test
    fun `resolve command opens album detail when target exists`() {
        val albumId = libraryAlbumId("Coldplay", "Parachutes")

        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Album(albumId),
            query = "",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL),
            filteredAlbums = listOf(Album(id = albumId, title = "Parachutes", artistName = "Coldplay")),
            filteredArtists = emptyList(),
        )

        val navigate = assertIs<LibraryNavigationCommand.Navigate>(result)
        assertEquals(LibraryBrowserRootView.Albums, navigate.resolution.rootView)
        assertEquals(albumId, navigate.resolution.selectedAlbumId)
        assertNull(navigate.resolution.selectedArtistId)
    }

    @Test
    fun `resolve command opens artist detail when target exists`() {
        val artistId = libraryArtistId("Coldplay")

        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Artist(artistId),
            query = "",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL),
            filteredAlbums = emptyList(),
            filteredArtists = listOf(Artist(id = artistId, name = "Coldplay")),
        )

        val navigate = assertIs<LibraryNavigationCommand.Navigate>(result)
        assertEquals(LibraryBrowserRootView.Artists, navigate.resolution.rootView)
        assertEquals(artistId, navigate.resolution.selectedArtistId)
        assertNull(navigate.resolution.selectedAlbumId)
    }

    @Test
    fun `resolve command falls back to album root when target is missing`() {
        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Album(libraryAlbumId("Coldplay", "Parachutes")),
            query = "",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        val navigate = assertIs<LibraryNavigationCommand.Navigate>(result)
        assertEquals(LibraryBrowserRootView.Albums, navigate.resolution.rootView)
        assertNull(navigate.resolution.selectedAlbumId)
    }

    @Test
    fun `resolve command falls back to artist root when target is missing`() {
        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Artist(libraryArtistId("Coldplay")),
            query = "",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        val navigate = assertIs<LibraryNavigationCommand.Navigate>(result)
        assertEquals(LibraryBrowserRootView.Artists, navigate.resolution.rootView)
        assertNull(navigate.resolution.selectedArtistId)
    }

    @Test
    fun `derive online playback targets uses remote album and artist ids`() {
        val track = testTrack(
            albumTitle = "Parachutes",
            artistName = "Coldplay",
            albumId = "album-remote",
            artistId = "artist-remote",
            artworkLocator = "lynmusic-navidrome-cover://source-1/cover-1",
        )

        val result = deriveOnlinePlaybackLibraryNavigationTargets(
            snapshot = PlaybackSnapshot(
                metadataAlbumTitle = "Parachutes",
                metadataArtistName = "Coldplay",
            ),
            track = track,
            sourceId = "source-1",
        )

        assertEquals(
            LibraryNavigationTarget.OnlineAlbum(
                sourceId = "source-1",
                albumId = "album-remote",
                albumTitle = "Parachutes",
                artistName = "Coldplay",
                artworkLocator = "lynmusic-navidrome-cover://source-1/cover-1",
            ),
            result.albumTarget,
        )
        assertEquals(
            LibraryNavigationTarget.OnlineArtist(
                sourceId = "source-1",
                artistId = "artist-remote",
                artistName = "Coldplay",
            ),
            result.artistTarget,
        )
    }

    @Test
    fun `derive online playback targets does not fall back to names when remote ids are missing`() {
        val result = deriveOnlinePlaybackLibraryNavigationTargets(
            snapshot = PlaybackSnapshot(
                metadataAlbumTitle = "Parachutes",
                metadataArtistName = "Coldplay",
            ),
            track = testTrack(albumTitle = "Parachutes", artistName = "Coldplay"),
            sourceId = "source-1",
        )

        assertNull(result.albumTarget)
        assertNull(result.artistTarget)
    }

    @Test
    fun `online navigation context guard allows first online album apply`() {
        val target = LibraryNavigationTarget.OnlineAlbum(
            sourceId = "source-online",
            albumId = "album-remote",
        )

        assertTrue(shouldApplyOnlineNavigationContext(target, lastAppliedOnlineContextTarget = null))
    }

    @Test
    fun `online navigation context guard skips repeated online target apply`() {
        val target = LibraryNavigationTarget.OnlineArtist(
            sourceId = "source-online",
            artistId = "artist-remote",
        )

        assertFalse(shouldApplyOnlineNavigationContext(target, lastAppliedOnlineContextTarget = target))
    }

    @Test
    fun `online navigation context guard allows changed online target apply`() {
        val previousTarget = LibraryNavigationTarget.OnlineAlbum(
            sourceId = "source-online",
            albumId = "album-previous",
        )
        val nextTarget = LibraryNavigationTarget.OnlineAlbum(
            sourceId = "source-online",
            albumId = "album-next",
        )

        assertTrue(shouldApplyOnlineNavigationContext(nextTarget, lastAppliedOnlineContextTarget = previousTarget))
    }

    @Test
    fun `online navigation context guard ignores local targets`() {
        val previousTarget = LibraryNavigationTarget.OnlineAlbum(
            sourceId = "source-online",
            albumId = "album-remote",
        )

        assertFalse(
            shouldApplyOnlineNavigationContext(
                LibraryNavigationTarget.Album(libraryAlbumId("Coldplay", "Parachutes")),
                lastAppliedOnlineContextTarget = previousTarget,
            ),
        )
        assertFalse(
            shouldApplyOnlineNavigationContext(
                LibraryNavigationTarget.Artist(libraryArtistId("Coldplay")),
                lastAppliedOnlineContextTarget = null,
            ),
        )
    }

    @Test
    fun `resolve command applies online source before opening online album`() {
        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.OnlineAlbum(
                sourceId = "source-online",
                albumId = "album-remote",
            ),
            query = "",
            isOnline = false,
            onlineSourceId = null,
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        assertEquals(
            LibraryNavigationCommand.ApplyOnlineContext(
                sourceId = "source-online",
                clearQuery = false,
            ),
            result,
        )
    }

    @Test
    fun `resolve command clears query before opening online album`() {
        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.OnlineAlbum(
                sourceId = "source-online",
                albumId = "album-remote",
            ),
            query = "cold",
            isOnline = true,
            onlineSourceId = "source-online",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        assertEquals(
            LibraryNavigationCommand.ApplyOnlineContext(
                sourceId = "source-online",
                clearQuery = true,
            ),
            result,
        )
    }

    @Test
    fun `resolve command opens online targets without requiring visible list items`() {
        val albumResult = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.OnlineAlbum(
                sourceId = "source-online",
                albumId = "album-remote",
            ),
            query = "",
            isOnline = true,
            onlineSourceId = "source-online",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )
        val artistResult = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.OnlineArtist(
                sourceId = "source-online",
                artistId = "artist-remote",
            ),
            query = "",
            isOnline = true,
            onlineSourceId = "source-online",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            availableSourceFilters = listOf(LibrarySourceFilter.ALL),
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        val albumNavigate = assertIs<LibraryNavigationCommand.Navigate>(albumResult)
        assertEquals(LibraryBrowserRootView.Albums, albumNavigate.resolution.rootView)
        assertEquals("album-remote", albumNavigate.resolution.selectedAlbumId)

        val artistNavigate = assertIs<LibraryNavigationCommand.Navigate>(artistResult)
        assertEquals(LibraryBrowserRootView.Artists, artistNavigate.resolution.rootView)
        assertEquals("artist-remote", artistNavigate.resolution.selectedArtistId)
    }
}

private fun testTrack(
    albumTitle: String?,
    artistName: String?,
    albumId: String? = null,
    artistId: String? = null,
    artworkLocator: String? = null,
): Track = Track(
    id = "track-1",
    sourceId = "source-1",
    title = "Yellow",
    artistName = artistName,
    albumTitle = albumTitle,
    artworkLocator = artworkLocator,
    albumId = albumId,
    artistId = artistId,
    mediaLocator = "file:///music/yellow.mp3",
    relativePath = "Yellow.mp3",
)
