package top.iwesley.lyn.music

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.data.db.MIGRATION_1_2

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
}
