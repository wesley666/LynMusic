package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidBackNavigationTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `library browser prioritizes album detail over artist detail`() {
        assertEquals(
            LibraryBrowserBackTarget.Album,
            resolveLibraryBrowserBackTarget(
                selectedArtistId = "artist-1",
                selectedAlbumId = "album-1",
            ),
        )
    }

    @Test
    fun `library browser returns artist detail when only artist is selected`() {
        assertEquals(
            LibraryBrowserBackTarget.Artist,
            resolveLibraryBrowserBackTarget(
                selectedArtistId = "artist-1",
                selectedAlbumId = null,
            ),
        )
    }

    @Test
    fun `library browser returns null when already at root`() {
        assertNull(
            resolveLibraryBrowserBackTarget(
                selectedArtistId = null,
                selectedAlbumId = null,
            ),
        )
    }

    @Test
    fun `library browser returns folder detail when folder is selected`() {
        assertEquals(
            LibraryBrowserBackTarget.Folder,
            resolveLibraryBrowserBackTarget(
                selectedArtistId = null,
                selectedAlbumId = null,
                selectedFolderSourceId = "source-1",
            ),
        )
    }

    @Test
    fun `library folder back destination returns parent folder for nested path`() {
        assertEquals(
            LibraryFolderBackDestination(
                sourceId = "source-1",
                path = "Music/Rock",
            ),
            resolveLibraryFolderBackDestination(
                selectedFolderSourceId = "source-1",
                selectedFolderPath = "Music/Rock/Live",
            ),
        )
    }

    @Test
    fun `library folder back destination returns source root from first level folder`() {
        assertEquals(
            LibraryFolderBackDestination(
                sourceId = "source-1",
                path = "",
            ),
            resolveLibraryFolderBackDestination(
                selectedFolderSourceId = "source-1",
                selectedFolderPath = "Music",
            ),
        )
    }

    @Test
    fun `library folder back destination exits source root to folder list`() {
        assertEquals(
            LibraryFolderBackDestination(
                sourceId = null,
                path = null,
            ),
            resolveLibraryFolderBackDestination(
                selectedFolderSourceId = "source-1",
                selectedFolderPath = "",
            ),
        )
    }

    @Test
    fun `playlists detail can navigate back when a playlist is selected`() {
        assertTrue(canNavigateBackFromPlaylistDetail("playlist-1"))
    }

    @Test
    fun `playlists detail cannot navigate back without a selected playlist`() {
        assertFalse(canNavigateBackFromPlaylistDetail(null))
    }

    @Test
    fun `music tags detail can navigate back when a track is selected`() {
        assertTrue(canNavigateBackFromMusicTagsDetail("track-1"))
    }

    @Test
    fun `music tags detail cannot navigate back without a selected track`() {
        assertFalse(canNavigateBackFromMusicTagsDetail(null))
    }
}
