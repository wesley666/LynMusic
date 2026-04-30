package top.iwesley.lyn.music

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.data.db.MIGRATION_1_2
import top.iwesley.lyn.music.data.db.MIGRATION_2_3
import top.iwesley.lyn.music.data.db.MIGRATION_4_5
import top.iwesley.lyn.music.data.db.MIGRATION_8_9
import top.iwesley.lyn.music.data.db.MIGRATION_9_10
import top.iwesley.lyn.music.data.db.MIGRATION_10_11

class DatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 adds allow insecure tls column with default`() {
        val databasePath = Files.createTempFile("lynmusic-migration", ".db")
        val driver = BundledSQLiteDriver()

        driver.open(databasePath.absolutePathString()).use { connection ->
            connection.execSql(
                """
                CREATE TABLE import_source (
                    id TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    label TEXT NOT NULL,
                    rootReference TEXT NOT NULL,
                    server TEXT,
                    shareName TEXT,
                    directoryPath TEXT,
                    username TEXT,
                    credentialKey TEXT,
                    lastScannedAt INTEGER,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSql(
                """
                INSERT INTO import_source (
                    id, type, label, rootReference, server, shareName, directoryPath, username, credentialKey, lastScannedAt, createdAt
                ) VALUES (
                    'dav-1', 'WEBDAV', 'NAS', 'https://dav.example.com/music/', NULL, NULL, NULL, 'guest', NULL, NULL, 1
                )
                """.trimIndent(),
            )

            MIGRATION_1_2.migrate(connection)

            assertTrue(connection.hasColumn("import_source", "allowInsecureTls"))
            assertEquals(0L, connection.singleLong("SELECT allowInsecureTls FROM import_source WHERE id = 'dav-1'"))
        }
    }

    @Test
    fun `migration 2 to 3 creates workflow lyrics source config table`() {
        val databasePath = Files.createTempFile("lynmusic-migration", ".db")
        val driver = BundledSQLiteDriver()

        driver.open(databasePath.absolutePathString()).use { connection ->
            connection.execSql(
                """
                CREATE TABLE lyrics_source_config (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    method TEXT NOT NULL,
                    urlTemplate TEXT NOT NULL,
                    headersTemplate TEXT NOT NULL,
                    queryTemplate TEXT NOT NULL,
                    bodyTemplate TEXT NOT NULL,
                    responseFormat TEXT NOT NULL,
                    extractor TEXT NOT NULL,
                    priority INTEGER NOT NULL,
                    enabled INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            MIGRATION_2_3.migrate(connection)

            assertTrue(connection.hasColumn("workflow_lyrics_source_config", "rawJson"))
        }
    }

    @Test
    fun `migration 4 to 5 creates favorite track table`() {
        val databasePath = Files.createTempFile("lynmusic-migration", ".db")
        val driver = BundledSQLiteDriver()

        driver.open(databasePath.absolutePathString()).use { connection ->
            connection.execSql(
                """
                CREATE TABLE track (
                    id TEXT NOT NULL PRIMARY KEY,
                    sourceId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artistId TEXT,
                    artistName TEXT,
                    albumId TEXT,
                    albumTitle TEXT,
                    durationMs INTEGER NOT NULL,
                    trackNumber INTEGER,
                    discNumber INTEGER,
                    mediaLocator TEXT NOT NULL,
                    relativePath TEXT NOT NULL,
                    artworkLocator TEXT,
                    sizeBytes INTEGER NOT NULL,
                    modifiedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            MIGRATION_4_5.migrate(connection)

            assertTrue(connection.hasColumn("favorite_track", "trackId"))
            assertTrue(connection.hasColumn("favorite_track", "sourceId"))
            assertTrue(connection.hasColumn("favorite_track", "remoteSongId"))
            assertTrue(connection.hasColumn("favorite_track", "favoritedAt"))
            assertEquals(listOf("trackId"), connection.primaryKeyColumns("favorite_track"))
        }
    }

    @Test
    fun `migration 8 to 9 adds ordered queue track ids with default`() {
        val databasePath = Files.createTempFile("lynmusic-migration", ".db")
        val driver = BundledSQLiteDriver()

        driver.open(databasePath.absolutePathString()).use { connection ->
            connection.execSql(
                """
                CREATE TABLE playback_queue_snapshot (
                    id INTEGER NOT NULL PRIMARY KEY,
                    queueTrackIds TEXT NOT NULL,
                    currentIndex INTEGER NOT NULL,
                    positionMs INTEGER NOT NULL,
                    mode TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSql(
                """
                INSERT INTO playback_queue_snapshot (
                    id, queueTrackIds, currentIndex, positionMs, mode, updatedAt
                ) VALUES (
                    0, 'track-1,track-2', 1, 12000, 'SHUFFLE', 1
                )
                """.trimIndent(),
            )

            MIGRATION_8_9.migrate(connection)

            assertTrue(connection.hasColumn("playback_queue_snapshot", "orderedQueueTrackIds"))
            assertEquals("", connection.singleText("SELECT orderedQueueTrackIds FROM playback_queue_snapshot WHERE id = 0"))
        }
    }

    @Test
    fun `migration 9 to 10 adds track audio quality columns`() {
        val databasePath = Files.createTempFile("lynmusic-migration", ".db")
        val driver = BundledSQLiteDriver()

        driver.open(databasePath.absolutePathString()).use { connection ->
            connection.execSql(
                """
                CREATE TABLE track (
                    id TEXT NOT NULL PRIMARY KEY,
                    sourceId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artistId TEXT,
                    artistName TEXT,
                    albumId TEXT,
                    albumTitle TEXT,
                    durationMs INTEGER NOT NULL,
                    trackNumber INTEGER,
                    discNumber INTEGER,
                    mediaLocator TEXT NOT NULL,
                    relativePath TEXT NOT NULL,
                    artworkLocator TEXT,
                    sizeBytes INTEGER NOT NULL,
                    modifiedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSql(
                """
                INSERT INTO track (
                    id, sourceId, title, artistId, artistName, albumId, albumTitle,
                    durationMs, trackNumber, discNumber, mediaLocator, relativePath,
                    artworkLocator, sizeBytes, modifiedAt
                ) VALUES (
                    'track-1', 'nav-1', 'Blue', NULL, 'Artist A', NULL, 'Album A',
                    215000, 4, 1, 'lynmusic-navidrome://nav-1/song-1',
                    'Artist A/Album A/Blue.flac', NULL, 12345, 0
                )
                """.trimIndent(),
            )

            MIGRATION_9_10.migrate(connection)

            assertTrue(connection.hasColumn("track", "bitDepth"))
            assertTrue(connection.hasColumn("track", "samplingRate"))
            assertTrue(connection.hasColumn("track", "bitRate"))
            assertTrue(connection.hasColumn("track", "channelCount"))
            assertNull(connection.singleNullableLong("SELECT bitDepth FROM track WHERE id = 'track-1'"))
            assertNull(connection.singleNullableLong("SELECT samplingRate FROM track WHERE id = 'track-1'"))
            assertNull(connection.singleNullableLong("SELECT bitRate FROM track WHERE id = 'track-1'"))
            assertNull(connection.singleNullableLong("SELECT channelCount FROM track WHERE id = 'track-1'"))
        }
    }

    @Test
    fun `migration 10 to 11 creates playback stats tables without changing library data`() {
        val databasePath = Files.createTempFile("lynmusic-migration", ".db")
        val driver = BundledSQLiteDriver()

        driver.open(databasePath.absolutePathString()).use { connection ->
            connection.execSql(
                """
                CREATE TABLE track (
                    id TEXT NOT NULL PRIMARY KEY,
                    sourceId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artistId TEXT,
                    artistName TEXT,
                    albumId TEXT,
                    albumTitle TEXT,
                    durationMs INTEGER NOT NULL,
                    trackNumber INTEGER,
                    discNumber INTEGER,
                    mediaLocator TEXT NOT NULL,
                    relativePath TEXT NOT NULL,
                    artworkLocator TEXT,
                    sizeBytes INTEGER NOT NULL,
                    modifiedAt INTEGER NOT NULL,
                    bitDepth INTEGER,
                    samplingRate INTEGER,
                    bitRate INTEGER,
                    channelCount INTEGER
                )
                """.trimIndent(),
            )
            connection.execSql(
                """
                INSERT INTO track (
                    id, sourceId, title, artistId, artistName, albumId, albumTitle,
                    durationMs, trackNumber, discNumber, mediaLocator, relativePath,
                    artworkLocator, sizeBytes, modifiedAt, bitDepth, samplingRate, bitRate, channelCount
                ) VALUES (
                    'track-1', 'local-1', 'Blue', NULL, 'Artist A', 'album-1', 'Album A',
                    215000, 4, 1, 'file:///music/Blue.flac',
                    'Artist A/Album A/Blue.flac', NULL, 12345, 0, 16, 44100, 900000, 2
                )
                """.trimIndent(),
            )

            MIGRATION_10_11.migrate(connection)

            assertTrue(connection.hasColumn("track_playback_stats", "trackId"))
            assertTrue(connection.hasColumn("track_playback_stats", "sourceId"))
            assertTrue(connection.hasColumn("track_playback_stats", "playCount"))
            assertTrue(connection.hasColumn("track_playback_stats", "lastPlayedAt"))
            assertEquals(listOf("trackId"), connection.primaryKeyColumns("track_playback_stats"))
            assertTrue(connection.hasColumn("album_playback_stats", "albumId"))
            assertTrue(connection.hasColumn("album_playback_stats", "playCount"))
            assertTrue(connection.hasColumn("album_playback_stats", "lastPlayedAt"))
            assertEquals(listOf("albumId"), connection.primaryKeyColumns("album_playback_stats"))
            assertEquals("Blue", connection.singleText("SELECT title FROM track WHERE id = 'track-1'"))
        }
    }

    private fun SQLiteConnection.execSql(sql: String) {
        prepare(sql).use { statement ->
            statement.step()
        }
    }

    private fun SQLiteConnection.hasColumn(tableName: String, columnName: String): Boolean {
        prepare("PRAGMA table_info($tableName)").use { statement ->
            while (statement.step()) {
                if (statement.getText(1) == columnName) {
                    return true
                }
            }
        }
        return false
    }

    private fun SQLiteConnection.singleLong(sql: String): Long {
        prepare(sql).use { statement ->
            check(statement.step()) { "Expected a row for query: $sql" }
            return statement.getLong(0)
        }
    }

    private fun SQLiteConnection.singleText(sql: String): String {
        prepare(sql).use { statement ->
            check(statement.step()) { "Expected a row for query: $sql" }
            return statement.getText(0)
        }
    }

    private fun SQLiteConnection.singleNullableLong(sql: String): Long? {
        prepare(sql).use { statement ->
            check(statement.step()) { "Expected a row for query: $sql" }
            return if (statement.isNull(0)) null else statement.getLong(0)
        }
    }

    private fun SQLiteConnection.primaryKeyColumns(tableName: String): List<String> {
        val columns = mutableListOf<Pair<Int, String>>()
        prepare("PRAGMA table_info($tableName)").use { statement ->
            while (statement.step()) {
                val keyPosition = statement.getLong(5).toInt()
                if (keyPosition > 0) {
                    columns += keyPosition to statement.getText(1)
                }
            }
        }
        return columns.sortedBy { it.first }.map { it.second }
    }
}
