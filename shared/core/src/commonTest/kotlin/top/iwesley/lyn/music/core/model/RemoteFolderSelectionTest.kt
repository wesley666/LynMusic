package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteFolderSelectionTest {
    @Test
    fun `normalizing drops duplicates and folders covered by a selected ancestor`() {
        assertEquals(
            listOf("Backup", "Media"),
            normalizeSelectedDirectories(listOf("/Media/", "Media/Music", "Backup", "Media", "Media/Music/Live")),
        )
        assertEquals(listOf(""), normalizeSelectedDirectories(listOf("Media", "", "Backup")))
        assertEquals(listOf("Media", "MediaX"), normalizeSelectedDirectories(listOf("MediaX", "Media")))
    }

    @Test
    fun `listed folder names keep their spaces and backslashes`() {
        assertEquals(listOf(" Live ", "A\\B"), normalizeSelectedDirectories(listOf("A\\B", "/ Live /")))
    }

    @Test
    fun `an unreadable folder becomes a failure while the others still scan`() {
        val failures = mutableListOf<ImportScanFailure>()

        val scan = scanSelectedFolders(listOf("Music", "Gone", "Live"), failures, folderPath = { it }) { folder ->
            if (folder == "Gone") throw IllegalStateException("not found")
            2
        }

        assertEquals(4, scan.discoveredAudioFileCount)
        assertEquals(listOf("Gone"), scan.unreadableFolders)
        assertEquals(listOf("Gone"), failures.map { it.relativePath })
    }

    @Test
    fun `scan fails when no selected folder could be read`() {
        val failures = mutableListOf<ImportScanFailure>()

        val error = kotlin.test.assertFailsWith<IllegalStateException> {
            scanSelectedFolders(listOf("Music", "Live"), failures, folderPath = { it }) { throw IllegalStateException("offline") }
        }

        assertEquals("offline", error.message)
    }

    @Test
    fun `an empty stored selection scans the whole root`() {
        assertEquals(listOf(""), effectiveSelectedDirectories(emptyList()))
        assertEquals(listOf("Music"), effectiveSelectedDirectories(listOf("Music")))
    }

    @Test
    fun `ancestor check matches whole path segments only`() {
        assertTrue(isSameOrAncestorDirectory("", "Media"))
        assertTrue(isSameOrAncestorDirectory("Media", "Media/Music"))
        assertTrue(isSameOrAncestorDirectory("Media", "Media"))
        assertFalse(isSameOrAncestorDirectory("Media", "MediaX/Music"))
    }

    @Test
    fun `legacy samba roots resolve exactly as before`() {
        assertEquals(SambaPath("Media", "Music/Artist/Song.mp3"), resolveSambaRemoteFile("Media/Music", "Artist/Song.mp3"))
        assertEquals(SambaPath("Media", "Music"), resolveSambaRemoteFile("Media/Music", ""))
    }

    @Test
    fun `samba folder and file names keep their surrounding spaces`() {
        assertEquals(SambaPath("Media", "Music/ Live/a.mp3"), resolveSambaRemoteFile("Media/Music", " Live/a.mp3"))
        assertEquals(SambaPath("Media", "Music/Live/a.mp3 "), resolveSambaRemoteFile("Media/Music", "Live/a.mp3 "))
        assertEquals(SambaPath("Media", "Music/ Live"), resolveSambaRemoteFile(" /Media/Music/ ", " Live"))
        assertEquals(
            listOf(SambaScanTarget(shareName = "Media", directoryPath = "Music/ Live", relativePrefix = " Live")),
            planSambaScanTargets("Media/Music", listOf(" Live")),
        )
    }

    @Test
    fun `server rooted samba sources take the share from the track path`() {
        assertEquals(SambaPath("Media", "Music/Song.mp3"), resolveSambaRemoteFile("", "Media/Music/Song.mp3"))
        assertEquals(SambaPath("Media", ""), resolveSambaRemoteFile(null, "Media"))
        assertNull(resolveSambaRemoteFile("", ""))
    }

    @Test
    fun `scan targets keep track paths relative to the source root`() {
        assertEquals(
            listOf(
                SambaScanTarget(shareName = "Backup", directoryPath = "", relativePrefix = "Backup"),
                SambaScanTarget(shareName = "Media", directoryPath = "Music", relativePrefix = "Media/Music"),
            ),
            planSambaScanTargets("", listOf("Media/Music", "Backup", "Media/Music/Live")),
        )
        assertEquals(
            listOf(SambaScanTarget(shareName = "Media", directoryPath = "Music", relativePrefix = "")),
            planSambaScanTargets("Media/Music", emptyList()),
        )
        assertEquals(
            listOf(SambaScanTarget(shareName = "Media", directoryPath = "Music/Live", relativePrefix = "Live")),
            planSambaScanTargets("Media/Music", listOf("Live")),
        )
        assertTrue(planSambaScanTargets("", emptyList()).isEmpty())
    }
}
