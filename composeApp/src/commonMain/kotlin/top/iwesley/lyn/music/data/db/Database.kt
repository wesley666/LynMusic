package top.iwesley.lyn.music.data.db

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.RoomDatabase.Builder
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.SQLiteConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val trackCount: Int,
)

@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistName: String?,
    val trackCount: Int,
)

@Entity(tableName = "import_source")
data class ImportSourceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val label: String,
    val rootReference: String,
    val server: String?,
    val shareName: String?,
    val directoryPath: String?,
    val username: String?,
    val credentialKey: String?,
    val allowInsecureTls: Boolean,
    val lastScannedAt: Long?,
    val createdAt: Long,
)

@Entity(tableName = "import_index_state")
data class ImportIndexStateEntity(
    @PrimaryKey val sourceId: String,
    val trackCount: Int,
    val lastScannedAt: Long?,
    val lastError: String?,
)

@Entity(tableName = "track")
data class TrackEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val title: String,
    val artistId: String?,
    val artistName: String?,
    val albumId: String?,
    val albumTitle: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val mediaLocator: String,
    val relativePath: String,
    val artworkLocator: String?,
    val modifiedAt: Long,
)

@Entity(tableName = "playback_queue_snapshot")
data class PlaybackQueueSnapshotEntity(
    @PrimaryKey val id: Int = 0,
    val queueTrackIds: String,
    val currentIndex: Int,
    val positionMs: Long,
    val mode: String,
    val updatedAt: Long,
)

@Entity(tableName = "lyrics_source_config")
data class LyricsSourceConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val method: String,
    val urlTemplate: String,
    val headersTemplate: String,
    val queryTemplate: String,
    val bodyTemplate: String,
    val responseFormat: String,
    val extractor: String,
    val priority: Int,
    val enabled: Boolean,
)

@Entity(tableName = "workflow_lyrics_source_config")
data class WorkflowLyricsSourceConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val priority: Int,
    val enabled: Boolean,
    val rawJson: String,
)

@Entity(
    tableName = "lyrics_cache",
    primaryKeys = ["trackId", "sourceId"],
)
data class LyricsCacheEntity(
    val trackId: String,
    val sourceId: String,
    val rawPayload: String,
    val updatedAt: Long,
)

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artist ORDER BY trackCount DESC, name ASC")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("DELETE FROM artist")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(items: List<ArtistEntity>)
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album ORDER BY trackCount DESC, title ASC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("DELETE FROM album")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(items: List<AlbumEntity>)
}

@Dao
interface ImportSourceDao {
    @Query("SELECT * FROM import_source ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImportSourceEntity>>

    @Query("SELECT * FROM import_source WHERE id = :sourceId LIMIT 1")
    suspend fun getById(sourceId: String): ImportSourceEntity?

    @Upsert
    suspend fun upsert(item: ImportSourceEntity)

    @Query("DELETE FROM import_source WHERE id = :sourceId")
    suspend fun deleteById(sourceId: String)
}

@Dao
interface ImportIndexStateDao {
    @Query("SELECT * FROM import_index_state")
    fun observeAll(): Flow<List<ImportIndexStateEntity>>

    @Query("SELECT * FROM import_index_state WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(sourceId: String): ImportIndexStateEntity?

    @Query("DELETE FROM import_index_state WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Upsert
    suspend fun upsert(item: ImportIndexStateEntity)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM track ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT * FROM track WHERE id IN (:trackIds)")
    suspend fun getByIds(trackIds: List<String>): List<TrackEntity>

    @Query("DELETE FROM track WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Query("SELECT COUNT(*) FROM track")
    suspend fun count(): Int

    @Query("UPDATE track SET artworkLocator = :artworkLocator WHERE id = :trackId")
    suspend fun updateArtworkLocator(trackId: String, artworkLocator: String?)

    @Upsert
    suspend fun upsertAll(items: List<TrackEntity>)
}

@Dao
interface PlaybackQueueSnapshotDao {
    @Query("SELECT * FROM playback_queue_snapshot WHERE id = 0 LIMIT 1")
    suspend fun get(): PlaybackQueueSnapshotEntity?

    @Upsert
    suspend fun upsert(item: PlaybackQueueSnapshotEntity)
}

@Dao
interface LyricsSourceConfigDao {
    @Query("SELECT * FROM lyrics_source_config ORDER BY priority DESC, name ASC")
    fun observeAll(): Flow<List<LyricsSourceConfigEntity>>

    @Query("SELECT * FROM lyrics_source_config")
    suspend fun getAll(): List<LyricsSourceConfigEntity>

    @Query("SELECT * FROM lyrics_source_config WHERE enabled = 1 ORDER BY priority DESC, name ASC")
    suspend fun getEnabled(): List<LyricsSourceConfigEntity>

    @Upsert
    suspend fun upsert(item: LyricsSourceConfigEntity)

    @Query("DELETE FROM lyrics_source_config WHERE id = :configId")
    suspend fun delete(configId: String)
}

@Dao
interface WorkflowLyricsSourceConfigDao {
    @Query("SELECT * FROM workflow_lyrics_source_config ORDER BY priority DESC, name ASC")
    fun observeAll(): Flow<List<WorkflowLyricsSourceConfigEntity>>

    @Query("SELECT * FROM workflow_lyrics_source_config")
    suspend fun getAll(): List<WorkflowLyricsSourceConfigEntity>

    @Query("SELECT * FROM workflow_lyrics_source_config WHERE enabled = 1 ORDER BY priority DESC, name ASC")
    suspend fun getEnabled(): List<WorkflowLyricsSourceConfigEntity>

    @Query("SELECT * FROM workflow_lyrics_source_config WHERE id = :configId LIMIT 1")
    suspend fun getById(configId: String): WorkflowLyricsSourceConfigEntity?

    @Upsert
    suspend fun upsert(item: WorkflowLyricsSourceConfigEntity)

    @Query("DELETE FROM workflow_lyrics_source_config WHERE id = :configId")
    suspend fun delete(configId: String)
}

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId ORDER BY updatedAt DESC")
    suspend fun getByTrack(trackId: String): List<LyricsCacheEntity>

    @Query("DELETE FROM lyrics_cache WHERE trackId LIKE :trackIdPrefix")
    suspend fun deleteByTrackIdPrefix(trackIdPrefix: String)

    @Query("DELETE FROM lyrics_cache WHERE trackId LIKE :trackIdPrefix AND sourceId = :sourceId")
    suspend fun deleteByTrackIdPrefixAndSourceId(trackIdPrefix: String, sourceId: String)

    @Upsert
    suspend fun upsert(item: LyricsCacheEntity)
}

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        ImportSourceEntity::class,
        ImportIndexStateEntity::class,
        TrackEntity::class,
        PlaybackQueueSnapshotEntity::class,
        LyricsSourceConfigEntity::class,
        WorkflowLyricsSourceConfigEntity::class,
        LyricsCacheEntity::class,
    ],
    version = 3,
)
@ConstructedBy(LynMusicDatabaseConstructor::class)
abstract class LynMusicDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun importSourceDao(): ImportSourceDao
    abstract fun importIndexStateDao(): ImportIndexStateDao
    abstract fun trackDao(): TrackDao
    abstract fun playbackQueueSnapshotDao(): PlaybackQueueSnapshotDao
    abstract fun lyricsSourceConfigDao(): LyricsSourceConfigDao
    abstract fun workflowLyricsSourceConfigDao(): WorkflowLyricsSourceConfigDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
}

@Suppress("KotlinNoActualForExpect")
expect object LynMusicDatabaseConstructor : RoomDatabaseConstructor<LynMusicDatabase> {
    override fun initialize(): LynMusicDatabase
}

fun buildLynMusicDatabase(builder: Builder<LynMusicDatabase>): LynMusicDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .addMigrations(MIGRATION_1_2)
        .addMigrations(MIGRATION_2_3)
        .fallbackToDestructiveMigration(true)
        .build()
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSql(
            """
            ALTER TABLE import_source
            ADD COLUMN allowInsecureTls INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSql(
            """
            CREATE TABLE IF NOT EXISTS workflow_lyrics_source_config (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                priority INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                rawJson TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private fun SQLiteConnection.execSql(sql: String) {
    prepare(sql).use { statement ->
        statement.step()
    }
}
