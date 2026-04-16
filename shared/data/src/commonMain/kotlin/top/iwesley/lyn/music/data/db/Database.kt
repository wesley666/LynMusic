package top.iwesley.lyn.music.data.db

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
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
    val enabled: Boolean = true,
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
    val sizeBytes: Long,
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

@Entity(tableName = "favorite_track")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val sourceId: String,
    val remoteSongId: String?,
    val favoritedAt: Long,
)

@Entity(
    tableName = "playlist",
    indices = [
        Index(value = ["normalizedName"], unique = true),
    ],
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val createdLocally: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_track",
    primaryKeys = ["playlistId", "trackId"],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["sourceId"]),
    ],
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val sourceId: String,
    val addedAt: Long,
    val localOrdinal: Int?,
    val remoteOrdinal: Int?,
)

@Entity(
    tableName = "playlist_remote_binding",
    primaryKeys = ["playlistId", "sourceId"],
    indices = [
        Index(value = ["sourceId"]),
    ],
)
data class PlaylistRemoteBindingEntity(
    val playlistId: String,
    val sourceId: String,
    val remotePlaylistId: String,
    val remoteName: String,
    val lastSyncedAt: Long?,
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
    val artworkLocator: String? = null,
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

    @Query("SELECT * FROM import_source ORDER BY createdAt DESC")
    suspend fun getAll(): List<ImportSourceEntity>

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

    @Query(
        """
        UPDATE track
        SET title = :title,
            artistId = :artistId,
            artistName = :artistName,
            albumId = :albumId,
            albumTitle = :albumTitle,
            trackNumber = :trackNumber,
            discNumber = :discNumber,
            artworkLocator = :artworkLocator,
            modifiedAt = :modifiedAt
        WHERE id = :trackId
        """,
    )
    suspend fun updateLibraryMetadata(
        trackId: String,
        title: String,
        artistId: String?,
        artistName: String?,
        albumId: String?,
        albumTitle: String?,
        trackNumber: Int?,
        discNumber: Int?,
        artworkLocator: String?,
        modifiedAt: Long,
    )

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
interface FavoriteTrackDao {
    @Query("SELECT * FROM favorite_track ORDER BY favoritedAt DESC, trackId ASC")
    fun observeAll(): Flow<List<FavoriteTrackEntity>>

    @Query("SELECT * FROM favorite_track WHERE trackId = :trackId LIMIT 1")
    suspend fun getByTrackId(trackId: String): FavoriteTrackEntity?

    @Query("SELECT * FROM favorite_track WHERE sourceId = :sourceId ORDER BY favoritedAt DESC, trackId ASC")
    suspend fun getBySourceId(sourceId: String): List<FavoriteTrackEntity>

    @Query("DELETE FROM favorite_track WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)

    @Query("DELETE FROM favorite_track WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Query(
        """
        DELETE FROM favorite_track
        WHERE sourceId = :sourceId
          AND trackId NOT IN (SELECT id FROM track WHERE sourceId = :sourceId)
        """,
    )
    suspend fun deleteOrphansBySourceId(sourceId: String)

    @Upsert
    suspend fun upsert(item: FavoriteTrackEntity)

    @Upsert
    suspend fun upsertAll(items: List<FavoriteTrackEntity>)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY updatedAt DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist ORDER BY updatedAt DESC, name COLLATE NOCASE ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist WHERE id = :playlistId LIMIT 1")
    suspend fun getById(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM playlist WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): PlaylistEntity?

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun deleteById(playlistId: String)

    @Upsert
    suspend fun upsert(item: PlaylistEntity)

    @Upsert
    suspend fun upsertAll(items: List<PlaylistEntity>)
}

@Dao
interface PlaylistTrackDao {
    @Query("SELECT * FROM playlist_track")
    fun observeAll(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_track")
    suspend fun getAll(): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlist_track WHERE playlistId = :playlistId")
    suspend fun getByPlaylistId(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlist_track WHERE playlistId = :playlistId AND sourceId = :sourceId")
    suspend fun getByPlaylistIdAndSourceId(playlistId: String, sourceId: String): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlist_track WHERE playlistId = :playlistId AND trackId = :trackId LIMIT 1")
    suspend fun getByPlaylistIdAndTrackId(playlistId: String, trackId: String): PlaylistTrackEntity?

    @Query("DELETE FROM playlist_track WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylistId(playlistId: String)

    @Query("DELETE FROM playlist_track WHERE playlistId = :playlistId AND sourceId = :sourceId")
    suspend fun deleteByPlaylistIdAndSourceId(playlistId: String, sourceId: String)

    @Query("DELETE FROM playlist_track WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteByPlaylistIdAndTrackId(playlistId: String, trackId: String)

    @Upsert
    suspend fun upsert(item: PlaylistTrackEntity)

    @Upsert
    suspend fun upsertAll(items: List<PlaylistTrackEntity>)
}

@Dao
interface PlaylistRemoteBindingDao {
    @Query("SELECT * FROM playlist_remote_binding")
    fun observeAll(): Flow<List<PlaylistRemoteBindingEntity>>

    @Query("SELECT * FROM playlist_remote_binding")
    suspend fun getAll(): List<PlaylistRemoteBindingEntity>

    @Query("SELECT * FROM playlist_remote_binding WHERE playlistId = :playlistId")
    suspend fun getByPlaylistId(playlistId: String): List<PlaylistRemoteBindingEntity>

    @Query("SELECT * FROM playlist_remote_binding WHERE playlistId = :playlistId AND sourceId = :sourceId LIMIT 1")
    suspend fun getByPlaylistIdAndSourceId(playlistId: String, sourceId: String): PlaylistRemoteBindingEntity?

    @Query("SELECT * FROM playlist_remote_binding WHERE sourceId = :sourceId")
    suspend fun getBySourceId(sourceId: String): List<PlaylistRemoteBindingEntity>

    @Query("DELETE FROM playlist_remote_binding WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Query("DELETE FROM playlist_remote_binding WHERE playlistId = :playlistId AND sourceId = :sourceId")
    suspend fun deleteByPlaylistIdAndSourceId(playlistId: String, sourceId: String)

    @Query("DELETE FROM playlist_remote_binding WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylistId(playlistId: String)

    @Upsert
    suspend fun upsert(item: PlaylistRemoteBindingEntity)

    @Upsert
    suspend fun upsertAll(items: List<PlaylistRemoteBindingEntity>)
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

    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId AND sourceId = :sourceId LIMIT 1")
    suspend fun getByTrackIdAndSourceId(trackId: String, sourceId: String): LyricsCacheEntity?

    @Query("SELECT * FROM lyrics_cache WHERE trackId IN (:trackIds) AND sourceId = :sourceId")
    suspend fun getByTrackIdsAndSourceId(trackIds: List<String>, sourceId: String): List<LyricsCacheEntity>

    @Query("SELECT * FROM lyrics_cache WHERE sourceId = :sourceId ORDER BY updatedAt DESC, trackId ASC")
    fun observeBySourceId(sourceId: String): Flow<List<LyricsCacheEntity>>

    @Query("DELETE FROM lyrics_cache WHERE trackId = :trackId AND sourceId = :sourceId")
    suspend fun deleteByTrackIdAndSourceId(trackId: String, sourceId: String)

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
        FavoriteTrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlaylistRemoteBindingEntity::class,
        LyricsSourceConfigEntity::class,
        WorkflowLyricsSourceConfigEntity::class,
        LyricsCacheEntity::class,
    ],
    version = 8,
)
@ConstructedBy(LynMusicDatabaseConstructor::class)
abstract class LynMusicDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun importSourceDao(): ImportSourceDao
    abstract fun importIndexStateDao(): ImportIndexStateDao
    abstract fun trackDao(): TrackDao
    abstract fun playbackQueueSnapshotDao(): PlaybackQueueSnapshotDao
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    abstract fun playlistRemoteBindingDao(): PlaylistRemoteBindingDao
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
        .addMigrations(MIGRATION_3_4)
        .addMigrations(MIGRATION_4_5)
        .addMigrations(MIGRATION_5_6)
        .addMigrations(MIGRATION_6_7)
        .addMigrations(MIGRATION_7_8)
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

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSql(
            """
            ALTER TABLE track
            ADD COLUMN sizeBytes INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
    }
}

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSql(
            """
            CREATE TABLE IF NOT EXISTS favorite_track (
                trackId TEXT NOT NULL PRIMARY KEY,
                sourceId TEXT NOT NULL,
                remoteSongId TEXT,
                favoritedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSql(
            """
            ALTER TABLE lyrics_cache
            ADD COLUMN artworkLocator TEXT
            """.trimIndent(),
        )
    }
}

val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSql(
            """
            CREATE TABLE IF NOT EXISTS playlist (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                normalizedName TEXT NOT NULL,
                createdLocally INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        connection.execSql(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_normalizedName
            ON playlist(normalizedName)
            """.trimIndent(),
        )
        connection.execSql(
            """
            CREATE TABLE IF NOT EXISTS playlist_track (
                playlistId TEXT NOT NULL,
                trackId TEXT NOT NULL,
                sourceId TEXT NOT NULL,
                addedAt INTEGER NOT NULL,
                localOrdinal INTEGER,
                remoteOrdinal INTEGER,
                PRIMARY KEY(playlistId, trackId)
            )
            """.trimIndent(),
        )
        connection.execSql(
            """
            CREATE INDEX IF NOT EXISTS index_playlist_track_playlistId
            ON playlist_track(playlistId)
            """.trimIndent(),
        )
        connection.execSql(
            """
            CREATE INDEX IF NOT EXISTS index_playlist_track_sourceId
            ON playlist_track(sourceId)
            """.trimIndent(),
        )
        connection.execSql(
            """
            CREATE TABLE IF NOT EXISTS playlist_remote_binding (
                playlistId TEXT NOT NULL,
                sourceId TEXT NOT NULL,
                remotePlaylistId TEXT NOT NULL,
                remoteName TEXT NOT NULL,
                lastSyncedAt INTEGER,
                PRIMARY KEY(playlistId, sourceId)
            )
            """.trimIndent(),
        )
        connection.execSql(
            """
            CREATE INDEX IF NOT EXISTS index_playlist_remote_binding_sourceId
            ON playlist_remote_binding(sourceId)
            """.trimIndent(),
        )
    }
}

val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSql(
            """
            ALTER TABLE import_source
            ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1
            """.trimIndent(),
        )
    }
}

private fun SQLiteConnection.execSql(sql: String) {
    prepare(sql).use { statement ->
        statement.step()
    }
}
