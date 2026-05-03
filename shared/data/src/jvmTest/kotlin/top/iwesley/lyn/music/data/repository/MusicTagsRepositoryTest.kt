package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

class MusicTagsRepositoryTest {

    @Test
    fun `local tracks flow only includes local folder sources`() = runTest {
        val database = createMusicTagsTestDatabase()
        seedSource(database, id = "local-1", type = "LOCAL_FOLDER")
        seedSource(database, id = "smb-1", type = "SAMBA")
        seedTrack(database, id = "local-track", sourceId = "local-1", title = "本地歌")
        seedTrack(database, id = "remote-track", sourceId = "smb-1", title = "远程歌")
        val repository = RoomMusicTagsRepository(database, FakeAudioTagGateway())

        val tracks = repository.localTracks.first()

        assertEquals(listOf("local-track"), tracks.map { it.id })
    }

    @Test
    fun `save tags updates track fields and rebuilds summaries`() = runTest {
        val database = createMusicTagsTestDatabase()
        seedSource(database, id = "local-1", type = "LOCAL_FOLDER")
        seedTrack(
            database = database,
            id = "track-1",
            sourceId = "local-1",
            title = "旧标题",
            artistName = "旧艺人",
            albumTitle = "旧专辑",
            trackNumber = 1,
            discNumber = 1,
        )
        val gateway = FakeAudioTagGateway(
            writeSnapshots = mapOf(
                "track-1" to AudioTagSnapshot(
                    title = "新标题",
                    artistName = "新艺人",
                    albumTitle = "新专辑",
                    albumArtist = "新艺人",
                    year = 2024,
                    genre = "Pop",
                    comment = "updated",
                    composer = "composer",
                    isCompilation = false,
                    tagLabel = "ID3v2.4",
                    trackNumber = 7,
                    discNumber = 2,
                    artworkLocator = "/tmp/new-artwork.png",
                ),
            ),
        )
        val repository = RoomMusicTagsRepository(database, gateway)
        val original = database.trackDao().getByIds(listOf("track-1")).first().toDomain()

        repository.saveTags(
            track = original,
            patch = AudioTagPatch(
                title = "新标题",
                artistName = "新艺人",
                albumTitle = "新专辑",
                trackNumber = 7,
                discNumber = 2,
            ),
        ).getOrThrow()

        val saved = database.trackDao().getByIds(listOf("track-1")).first()
        assertEquals("新标题", saved.title)
        assertEquals("新艺人", saved.artistName)
        assertEquals("新专辑", saved.albumTitle)
        assertEquals(7, saved.trackNumber)
        assertEquals(2, saved.discNumber)
        assertEquals("/tmp/new-artwork.png", saved.artworkLocator)

        val artists = database.artistDao().observeAll().first()
        val albums = database.albumDao().observeAll().first()
        assertEquals(listOf("新艺人"), artists.map { it.name })
        assertEquals(listOf("新专辑"), albums.map { it.title })
    }

    @Test
    fun `save tags replaces album artwork cache when artwork bytes changed`() = runTest {
        val database = createMusicTagsTestDatabase()
        seedSource(database, id = "local-1", type = "LOCAL_FOLDER")
        seedTrack(
            database = database,
            id = "track-1",
            sourceId = "local-1",
            title = "旧标题",
            artistName = "旧艺人",
            albumTitle = "旧专辑",
        )
        val artworkCacheStore = FakeMusicTagsArtworkCacheStore()
        val repository = RoomMusicTagsRepository(
            database = database,
            audioTagGateway = FakeAudioTagGateway(
                writeSnapshots = mapOf(
                    "track-1" to sampleTagSnapshot(
                        title = "旧标题",
                        artistName = "新艺人",
                        albumTitle = "新专辑",
                        artworkLocator = "/tmp/new-artwork.png",
                    ),
                ),
            ),
            artworkCacheStore = artworkCacheStore,
        )
        val original = database.trackDao().getByIds(listOf("track-1")).first().toDomain()

        repository.saveTags(
            track = original,
            patch = AudioTagPatch(artworkBytes = byteArrayOf(1, 2, 3)),
        ).getOrThrow()

        assertEquals(
            listOf(
                ArtworkCacheRequest(
                    locator = "/tmp/new-artwork.png",
                    cacheKey = "album:local-1:album:新艺人:新专辑",
                    replaceExisting = true,
                ),
            ),
            artworkCacheStore.requests,
        )
    }

    @Test
    fun `save tags updates embedded tag cache only when lyrics changed and preserves other sources`() = runTest {
        val database = createMusicTagsTestDatabase()
        seedSource(database, id = "local-1", type = "LOCAL_FOLDER")
        seedTrack(
            database = database,
            id = "track-1",
            sourceId = "local-1",
            title = "旧标题",
            artistName = "旧艺人",
            albumTitle = "旧专辑",
        )
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = "track-1",
                sourceId = EMBEDDED_LYRICS_SOURCE_ID,
                rawPayload = "旧内嵌歌词",
                updatedAt = 10L,
            ),
        )
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = "track-1",
                sourceId = "source-plain",
                rawPayload = "外部歌词",
                updatedAt = 20L,
            ),
        )
        val repository = RoomMusicTagsRepository(
            database = database,
            audioTagGateway = FakeAudioTagGateway(
                writeSnapshots = mapOf(
                    "track-1" to sampleTagSnapshot(
                        title = "新标题",
                        artistName = "旧艺人",
                        albumTitle = "旧专辑",
                        embeddedLyrics = "[00:01.00]新的内嵌歌词",
                    ),
                ),
            ),
        )
        val original = database.trackDao().getByIds(listOf("track-1")).first().toDomain()

        repository.saveTags(
            track = original,
            patch = AudioTagPatch(
                title = "新标题",
                embeddedLyrics = "[00:01.00]新的内嵌歌词",
            ),
        ).getOrThrow()

        val cachedRows = database.lyricsCacheDao().getByTrack("track-1")
        val embeddedRow = cachedRows.first { it.sourceId == EMBEDDED_LYRICS_SOURCE_ID }
        val externalRow = cachedRows.first { it.sourceId == "source-plain" }

        assertEquals("[00:01.00]新的内嵌歌词", embeddedRow.rawPayload)
        assertTrue(embeddedRow.updatedAt > 10L)
        assertEquals("外部歌词", externalRow.rawPayload)
        assertEquals(20L, externalRow.updatedAt)
    }

    @Test
    fun `save tags deletes embedded tag cache when lyrics are cleared`() = runTest {
        val database = createMusicTagsTestDatabase()
        seedSource(database, id = "local-1", type = "LOCAL_FOLDER")
        seedTrack(database, id = "track-1", sourceId = "local-1", title = "旧标题")
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = "track-1",
                sourceId = EMBEDDED_LYRICS_SOURCE_ID,
                rawPayload = "旧内嵌歌词",
                updatedAt = 10L,
            ),
        )
        val repository = RoomMusicTagsRepository(
            database = database,
            audioTagGateway = FakeAudioTagGateway(
                writeSnapshots = mapOf(
                    "track-1" to sampleTagSnapshot(
                        title = "旧标题",
                        embeddedLyrics = "   ",
                    ),
                ),
            ),
        )
        val original = database.trackDao().getByIds(listOf("track-1")).first().toDomain()

        repository.saveTags(
            track = original,
            patch = AudioTagPatch(embeddedLyrics = null),
        ).getOrThrow()

        val cachedRows = database.lyricsCacheDao().getByTrack("track-1")
        assertTrue(cachedRows.none { it.sourceId == EMBEDDED_LYRICS_SOURCE_ID })
    }

    @Test
    fun `save tags leaves embedded tag cache timestamp unchanged when lyrics are unchanged`() = runTest {
        val database = createMusicTagsTestDatabase()
        seedSource(database, id = "local-1", type = "LOCAL_FOLDER")
        seedTrack(database, id = "track-1", sourceId = "local-1", title = "旧标题")
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = "track-1",
                sourceId = EMBEDDED_LYRICS_SOURCE_ID,
                rawPayload = "[00:01.00]同一段歌词",
                updatedAt = 10L,
            ),
        )
        val repository = RoomMusicTagsRepository(
            database = database,
            audioTagGateway = FakeAudioTagGateway(
                writeSnapshots = mapOf(
                    "track-1" to sampleTagSnapshot(
                        title = "新标题",
                        embeddedLyrics = "[00:01.00]同一段歌词",
                    ),
                ),
            ),
        )
        val original = database.trackDao().getByIds(listOf("track-1")).first().toDomain()

        repository.saveTags(
            track = original,
            patch = AudioTagPatch(title = "新标题"),
        ).getOrThrow()

        val cachedRows = database.lyricsCacheDao().getByTrack("track-1")
        val embeddedRow = cachedRows.first { it.sourceId == EMBEDDED_LYRICS_SOURCE_ID }
        val savedTrack = database.trackDao().getByIds(listOf("track-1")).first()

        assertEquals(10L, embeddedRow.updatedAt)
        assertEquals("新标题", savedTrack.title)
    }

    @Test
    fun `refresh tags syncs embedded cache and default lyrics repository resolves refreshed lyrics`() = runTest {
        val database = createMusicTagsTestDatabase()
        seedSource(database, id = "local-1", type = "LOCAL_FOLDER")
        seedTrack(database, id = "track-1", sourceId = "local-1", title = "旧标题")
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = "track-1",
                sourceId = EMBEDDED_LYRICS_SOURCE_ID,
                rawPayload = "旧歌词",
                updatedAt = 10L,
            ),
        )
        val tagsRepository = RoomMusicTagsRepository(
            database = database,
            audioTagGateway = FakeAudioTagGateway(
                readSnapshots = mapOf(
                    "track-1" to sampleTagSnapshot(
                        title = "旧标题",
                        embeddedLyrics = "[00:01.00]刷新后的歌词",
                    ),
                ),
            ),
        )
        val original = database.trackDao().getByIds(listOf("track-1")).first().toDomain()

        val refreshed = tagsRepository.refreshTags(original).getOrThrow()
        val lyricsRepository = DefaultLyricsRepository(
            database = database,
            httpClient = NoopLyricsHttpClient,
            secureCredentialStore = NoopSecureCredentialStore,
            logger = NoopDiagnosticLogger,
        )

        val resolved = lyricsRepository.getLyrics(refreshed.track)
        val embeddedRow = database.lyricsCacheDao().getByTrack("track-1")
            .firstOrNull { it.sourceId == EMBEDDED_LYRICS_SOURCE_ID }

        assertNotNull(resolved)
        assertEquals(EMBEDDED_LYRICS_SOURCE_ID, resolved.document.sourceId)
        assertEquals("刷新后的歌词", resolved.document.lines.single().text)
        assertEquals(1_000L, resolved.document.lines.single().timestampMs)
        assertNotNull(embeddedRow)
        assertEquals("[00:01.00]刷新后的歌词", embeddedRow.rawPayload)
    }
}

private data class ArtworkCacheRequest(
    val locator: String,
    val cacheKey: String,
    val replaceExisting: Boolean,
)

private class FakeMusicTagsArtworkCacheStore : ArtworkCacheStore {
    val requests = mutableListOf<ArtworkCacheRequest>()

    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? {
        requests += ArtworkCacheRequest(locator, cacheKey, replaceExisting)
        return locator
    }
}

private class FakeAudioTagGateway(
    private val readSnapshots: Map<String, AudioTagSnapshot> = emptyMap(),
    private val writeSnapshots: Map<String, AudioTagSnapshot> = readSnapshots,
) : AudioTagGateway {
    override suspend fun canEdit(track: Track): Boolean = true

    override suspend fun canWrite(track: Track): Boolean = true

    override suspend fun read(track: Track): Result<AudioTagSnapshot> {
        return readSnapshots[track.id]?.let(Result.Companion::success)
            ?: Result.failure(IllegalStateException("missing snapshot"))
    }

    override suspend fun write(track: Track, patch: AudioTagPatch): Result<AudioTagSnapshot> {
        return writeSnapshots[track.id]?.let(Result.Companion::success)
            ?: Result.failure(IllegalStateException("missing snapshot"))
    }
}

private object NoopLyricsHttpClient : LyricsHttpClient {
    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        return Result.failure(IllegalStateException("HTTP should not be used in music tags repository tests"))
    }
}

private object NoopSecureCredentialStore : SecureCredentialStore {
    override suspend fun put(key: String, value: String) = Unit

    override suspend fun get(key: String): String? = null

    override suspend fun remove(key: String) = Unit
}

private fun createMusicTagsTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-tags", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private suspend fun seedSource(
    database: LynMusicDatabase,
    id: String,
    type: String,
) {
    database.importSourceDao().upsert(
        ImportSourceEntity(
            id = id,
            type = type,
            label = id,
            rootReference = "/music",
            server = null,
            shareName = null,
            directoryPath = null,
            username = null,
            credentialKey = null,
            allowInsecureTls = false,
            lastScannedAt = null,
            createdAt = 1L,
        ),
    )
}

private suspend fun seedTrack(
    database: LynMusicDatabase,
    id: String,
    sourceId: String,
    title: String,
    artistName: String? = null,
    albumTitle: String? = null,
    trackNumber: Int? = null,
    discNumber: Int? = null,
) {
    database.trackDao().upsertAll(
        listOf(
            TrackEntity(
                id = id,
                sourceId = sourceId,
                title = title,
                artistId = artistName?.let(::artistIdForLibraryMetadata),
                artistName = artistName,
                albumId = albumTitle?.let { albumIdForLibraryMetadata(artistName, it) },
                albumTitle = albumTitle,
                durationMs = 200_000,
                trackNumber = trackNumber,
                discNumber = discNumber,
                mediaLocator = "/music/$title.mp3",
                relativePath = "$title.mp3",
                artworkLocator = null,
                sizeBytes = 100L,
                modifiedAt = 1L,
            ),
        ),
    )
}

private fun sampleTagSnapshot(
    title: String,
    artistName: String? = "旧艺人",
    albumTitle: String? = "旧专辑",
    embeddedLyrics: String? = null,
    artworkLocator: String? = "/tmp/art.png",
): AudioTagSnapshot {
    return AudioTagSnapshot(
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        albumArtist = artistName,
        year = 2024,
        genre = "Pop",
        comment = "comment",
        composer = "composer",
        isCompilation = false,
        tagLabel = "ID3v2.4",
        trackNumber = 1,
        discNumber = 1,
        embeddedLyrics = embeddedLyrics,
        artworkLocator = artworkLocator,
    )
}
