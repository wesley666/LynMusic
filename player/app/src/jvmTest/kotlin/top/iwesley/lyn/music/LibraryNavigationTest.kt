package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId

class LibraryNavigationTest {
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
    fun `track row targets require desktop metadata navigation`() {
        val track = testTrack(albumTitle = "Parachutes", artistName = "Coldplay")

        val desktopTargets = resolveTrackRowLibraryNavigationTargets(
            track = track,
            showDuration = true,
            metadataNavigationEnabled = true,
        )
        val mobileTargets = resolveTrackRowLibraryNavigationTargets(
            track = track,
            showDuration = false,
            metadataNavigationEnabled = true,
        )
        val disabledTargets = resolveTrackRowLibraryNavigationTargets(
            track = track,
            showDuration = true,
            metadataNavigationEnabled = false,
        )

        assertEquals(
            LibraryNavigationTarget.Album(libraryAlbumId("Coldplay", "Parachutes")),
            desktopTargets.albumTarget,
        )
        assertEquals(
            LibraryNavigationTarget.Artist(libraryArtistId("Coldplay")),
            desktopTargets.artistTarget,
        )
        assertNull(mobileTargets.albumTarget)
        assertNull(mobileTargets.artistTarget)
        assertNull(disabledTargets.albumTarget)
        assertNull(disabledTargets.artistTarget)
    }

    @Test
    fun `resolve command resets filters when query is active`() {
        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Artist(libraryArtistId("Coldplay")),
            query = "cold",
            selectedSourceFilter = LibrarySourceFilter.ALL,
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        assertEquals(LibraryNavigationCommand.ResetFilters, result)
    }

    @Test
    fun `resolve command resets filters when source filter is not all`() {
        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Album(libraryAlbumId("Coldplay", "Parachutes")),
            query = "",
            selectedSourceFilter = LibrarySourceFilter.LOCAL_FOLDER,
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        assertEquals(LibraryNavigationCommand.ResetFilters, result)
    }

    @Test
    fun `resolve command opens album detail when target exists`() {
        val albumId = libraryAlbumId("Coldplay", "Parachutes")

        val result = resolveLibraryNavigationCommand(
            target = LibraryNavigationTarget.Album(albumId),
            query = "",
            selectedSourceFilter = LibrarySourceFilter.ALL,
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
            filteredAlbums = emptyList(),
            filteredArtists = emptyList(),
        )

        val navigate = assertIs<LibraryNavigationCommand.Navigate>(result)
        assertEquals(LibraryBrowserRootView.Artists, navigate.resolution.rootView)
        assertNull(navigate.resolution.selectedArtistId)
    }
}

private fun testTrack(
    albumTitle: String?,
    artistName: String?,
): Track = Track(
    id = "track-1",
    sourceId = "source-1",
    title = "Yellow",
    artistName = artistName,
    albumTitle = albumTitle,
    mediaLocator = "file:///music/yellow.mp3",
    relativePath = "Yellow.mp3",
)
