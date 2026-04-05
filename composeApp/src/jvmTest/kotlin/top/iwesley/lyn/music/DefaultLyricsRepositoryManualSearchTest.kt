package top.iwesley.lyn.music

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildSambaLocator
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.data.repository.DefaultLyricsRepository

class DefaultLyricsRepositoryManualSearchTest {

    @Test
    fun `manual search prepends current track lyrics from realtime tags and applying it persists artwork`() = runTest {
        val database = createTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-plain",
                name = "纯文本源",
                priority = 10,
                urlTemplate = "https://lyrics.example/plain",
                responseFormat = LyricsResponseFormat.TEXT,
                extractor = "text",
            ),
        )
        val track = sampleTrack(artworkLocator = null)
        seedTrack(database, track)
        val httpClient = FakeLyricsHttpClient(
            mapOf(
                "https://lyrics.example/plain" to Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = "外部歌词第一句\n外部歌词第二句",
                    ),
                ),
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = EmptySecureCredentialStore,
            audioTagGateway = FakeAudioTagGateway(
                canEdit = true,
                snapshot = AudioTagSnapshot(
                    title = "标签标题",
                    artistName = "标签歌手",
                    albumTitle = "标签专辑",
                    embeddedLyrics = "[00:01.00]标签第一句",
                    artworkLocator = "/tmp/tag-cover.jpg",
                ),
            ),
            logger = NoopDiagnosticLogger,
        )

        val candidates = repository.searchLyricsCandidates(track)
        val applied = repository.applyLyricsCandidate(track.id, candidates.first())
        val storedTrack = database.trackDao().getByIds(listOf(track.id)).single()

        assertEquals(listOf("embedded-tag", "source-plain"), candidates.map { it.sourceId })
        assertTrue(candidates.first().isTrackProvided)
        assertEquals("/tmp/tag-cover.jpg", candidates.first().artworkLocator)
        assertEquals("标签标题", candidates.first().title)
        assertEquals("embedded-tag", applied.sourceId)
        assertEquals("/tmp/tag-cover.jpg", storedTrack.artworkLocator)
    }

    @Test
    fun `manual search returns one candidate per successful source and applying it caches for future lookup`() = runTest {
        val database = createTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-synced",
                name = "同步源",
                priority = 20,
                urlTemplate = "https://lyrics.example/synced",
                responseFormat = LyricsResponseFormat.LRC,
                extractor = "text",
            ),
        )
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-plain",
                name = "纯文本源",
                priority = 10,
                urlTemplate = "https://lyrics.example/plain",
                responseFormat = LyricsResponseFormat.TEXT,
                extractor = "text",
            ),
        )
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-failed",
                name = "失败源",
                priority = 5,
                urlTemplate = "https://lyrics.example/failed",
                responseFormat = LyricsResponseFormat.TEXT,
                extractor = "text",
            ),
        )
        val httpClient = FakeLyricsHttpClient(
            mapOf(
                "https://lyrics.example/synced" to Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = "[00:01.00]第一句\n[00:02.00]第二句",
                    ),
                ),
                "https://lyrics.example/plain" to Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = "纯文本第一句\n纯文本第二句",
                    ),
                ),
                "https://lyrics.example/failed" to Result.failure(IllegalStateException("boom")),
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = EmptySecureCredentialStore,
            logger = NoopDiagnosticLogger,
        )
        val track = sampleTrack()

        val candidates = repository.searchLyricsCandidates(track)

        assertEquals(listOf("source-synced", "source-plain"), candidates.map { it.sourceId })
        assertTrue(candidates.first().document.isSynced)
        assertFalse(candidates.last().document.isSynced)
        assertTrue(database.lyricsCacheDao().getByTrack(track.id).isEmpty())

        val applied = repository.applyLyricsCandidate(track.id, candidates.last())
        val cachedRows = database.lyricsCacheDao().getByTrack(track.id)

        assertEquals("source-plain", applied.sourceId)
        assertEquals(listOf("source-plain"), cachedRows.map { it.sourceId })

        val requestCountAfterManualSearch = httpClient.requestCount
        val cached = repository.getLyrics(track.copy(title = "不会再次发请求"))

        assertNotNull(cached)
        assertEquals("source-plain", cached.document.sourceId)
        assertEquals(requestCountAfterManualSearch, httpClient.requestCount)
    }

    @Test
    fun `manual search falls back to cached embedded lyrics when realtime tags are unavailable`() = runTest {
        val database = createTestDatabase()
        val track = sampleTrack(artworkLocator = "/tmp/cached-cover.jpg")
        seedTrack(database, track)
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = track.id,
                sourceId = "embedded-tag",
                rawPayload = "缓存歌词第一句\n缓存歌词第二句",
                updatedAt = 1L,
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = FakeLyricsHttpClient(emptyMap()),
            secureCredentialStore = EmptySecureCredentialStore,
            audioTagGateway = FakeAudioTagGateway(canEdit = false),
            logger = NoopDiagnosticLogger,
        )

        val candidates = repository.searchLyricsCandidates(track)

        assertEquals(1, candidates.size)
        assertEquals("embedded-tag", candidates.single().sourceId)
        assertTrue(candidates.single().isTrackProvided)
        assertEquals("/tmp/cached-cover.jpg", candidates.single().artworkLocator)
    }

    @Test
    fun `manual search exposes metadata mapped by json map extractor`() = runTest {
        val database = createTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-mapped",
                name = "映射源",
                priority = 20,
                urlTemplate = "https://lyrics.example/mapped",
                responseFormat = LyricsResponseFormat.JSON,
                extractor = "json-map:[0]|lyrics=syncedLyrics,title=trackName,artist=artistName,album=albumName,durationSeconds=duration,id=id,coverUrl=cover",
            ),
        )
        val httpClient = FakeLyricsHttpClient(
            mapOf(
                "https://lyrics.example/mapped" to Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = """
                            [
                              {
                                "id": "song-42",
                                "trackName": "映射标题",
                                "artistName": "映射歌手",
                                "albumName": "映射专辑",
                                "duration": "201.0",
                                "syncedLyrics": "[00:01.00]第一句\n[00:02.00]第二句",
                                "cover": "https://img.example.com/mapped.jpg"
                              }
                            ]
                        """.trimIndent(),
                    ),
                ),
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = EmptySecureCredentialStore,
            logger = NoopDiagnosticLogger,
        )

        val candidate = repository.searchLyricsCandidates(sampleTrack()).single()

        assertEquals("source-mapped", candidate.sourceId)
        assertEquals("song-42", candidate.itemId)
        assertEquals("映射标题", candidate.title)
        assertEquals("映射歌手", candidate.artistName)
        assertEquals("映射专辑", candidate.albumTitle)
        assertEquals(201, candidate.durationSeconds)
        assertEquals("https://img.example.com/mapped.jpg", candidate.artworkLocator)
        assertTrue(candidate.document.isSynced)
    }

    @Test
    fun `auto direct lyrics can return artwork locator from json map cover field`() = runTest {
        val database = createTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-lrcapi",
                name = "LrcAPI",
                priority = 110,
                urlTemplate = "https://api.lrc.cx/jsonapi",
                responseFormat = LyricsResponseFormat.JSON,
                extractor = "json-map:[0]|lyrics=lyrics,title=title,artist=artist,album=album,id=id,coverUrl=cover",
            ),
        )
        val track = sampleTrack()
        val httpClient = FakeLyricsHttpClient(
            mapOf(
                "https://api.lrc.cx/jsonapi" to Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = """
                            [
                              {
                                "id": "song-1",
                                "title": "原始标题",
                                "artist": "原始歌手",
                                "album": "原始专辑",
                                "cover": "https://img.example.com/lrcapi.jpg",
                                "lyrics": "[00:01.00]第一句"
                              }
                            ]
                        """.trimIndent(),
                    ),
                ),
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = EmptySecureCredentialStore,
            logger = NoopDiagnosticLogger,
        )

        val resolved = repository.getLyrics(track)

        assertNotNull(resolved)
        assertEquals("source-lrcapi", resolved.document.sourceId)
        assertEquals("https://img.example.com/lrcapi.jpg", resolved.artworkLocator)
    }

    @Test
    fun `manual search expands multiple candidates from one direct json map source`() = runTest {
        val database = createTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-many",
                name = "多候选源",
                priority = 20,
                urlTemplate = "https://lyrics.example/many",
                responseFormat = LyricsResponseFormat.JSON,
                extractor = "json-map:lyrics=plainLyrics,title=trackName,artist=artistName,album=albumName,durationSeconds=duration,id=id",
            ),
        )
        val httpClient = FakeLyricsHttpClient(
            mapOf(
                "https://lyrics.example/many" to Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = """
                            [
                              {
                                "id": "song-1",
                                "trackName": "别让我哭",
                                "artistName": "陈升",
                                "albumName": "魔鬼的情诗",
                                "duration": 404.16,
                                "plainLyrics": "第一版歌词"
                              },
                              {
                                "id": "song-2",
                                "trackName": "别让我哭",
                                "artistName": "陈升",
                                "albumName": "魔鬼A春天",
                                "duration": 402.0,
                                "plainLyrics": "第二版歌词"
                              }
                            ]
                        """.trimIndent(),
                    ),
                ),
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = EmptySecureCredentialStore,
            logger = NoopDiagnosticLogger,
        )

        val candidates = repository.searchLyricsCandidates(sampleTrack())

        assertEquals(2, candidates.size)
        assertEquals(listOf("song-1", "song-2"), candidates.map { it.itemId })
        assertEquals(listOf("魔鬼的情诗", "魔鬼A春天"), candidates.map { it.albumTitle })
        assertEquals(listOf(404, 402), candidates.map { it.durationSeconds })
        assertTrue(candidates.all { !it.document.isSynced })
    }

    @Test
    fun `manual search still returns external candidates when realtime tag reading fails`() = runTest {
        val database = createTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-plain",
                name = "纯文本源",
                priority = 10,
                urlTemplate = "https://lyrics.example/plain",
                responseFormat = LyricsResponseFormat.TEXT,
                extractor = "text",
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = FakeLyricsHttpClient(
                mapOf(
                    "https://lyrics.example/plain" to Result.success(
                        LyricsHttpResponse(statusCode = 200, body = "外部歌词"),
                    ),
                ),
            ),
            secureCredentialStore = EmptySecureCredentialStore,
            audioTagGateway = FakeAudioTagGateway(
                canEdit = true,
                readFailure = IllegalStateException("read failed"),
            ),
            logger = NoopDiagnosticLogger,
        )

        val candidates = repository.searchLyricsCandidates(sampleTrack())

        assertEquals(listOf("source-plain"), candidates.map { it.sourceId })
    }

    @Test
    fun `manual search samba track uses remote tag read even when canEdit is false`() = runTest {
        val database = createTestDatabase()
        val track = sampleSambaTrack(artworkLocator = "/tmp/scanned-cover.jpg")
        seedSambaSource(database, track.sourceId)
        seedTrack(database, track)
        val gateway = FakeAudioTagGateway(
            canEdit = false,
            snapshot = AudioTagSnapshot(
                title = "远端标题",
                artistName = "远端歌手",
                albumTitle = "远端专辑",
                embeddedLyrics = "[00:01.00]远端歌词",
                artworkLocator = "/tmp/remote-cover.jpg",
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = FakeLyricsHttpClient(emptyMap()),
            secureCredentialStore = EmptySecureCredentialStore,
            audioTagGateway = gateway,
            logger = NoopDiagnosticLogger,
        )

        val candidates = repository.searchLyricsCandidates(track)

        assertEquals(1, gateway.readCount)
        assertEquals(listOf("embedded-tag"), candidates.map { it.sourceId })
        assertEquals("远端标题", candidates.single().title)
        assertEquals("/tmp/remote-cover.jpg", candidates.single().artworkLocator)
        assertTrue(candidates.single().isTrackProvided)
    }

    @Test
    fun `manual search samba track does not fall back to cached embedded lyrics when remote read fails`() = runTest {
        val database = createTestDatabase()
        val track = sampleSambaTrack(artworkLocator = "/tmp/scanned-cover.jpg")
        seedSambaSource(database, track.sourceId)
        seedTrack(database, track)
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = track.id,
                sourceId = "embedded-tag",
                rawPayload = "缓存歌词第一句\n缓存歌词第二句",
                updatedAt = 1L,
            ),
        )
        val gateway = FakeAudioTagGateway(
            canEdit = false,
            readFailure = IllegalStateException("remote read failed"),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = FakeLyricsHttpClient(emptyMap()),
            secureCredentialStore = EmptySecureCredentialStore,
            audioTagGateway = gateway,
            logger = NoopDiagnosticLogger,
        )

        val candidates = repository.searchLyricsCandidates(track)

        assertEquals(1, gateway.readCount)
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `manual search can skip track provided candidate and avoid samba remote read`() = runTest {
        val database = createTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-plain",
                name = "纯文本源",
                priority = 10,
                urlTemplate = "https://lyrics.example/plain",
                responseFormat = LyricsResponseFormat.TEXT,
                extractor = "text",
            ),
        )
        val track = sampleSambaTrack()
        seedSambaSource(database, track.sourceId)
        val gateway = FakeAudioTagGateway(
            canEdit = false,
            snapshot = AudioTagSnapshot(
                title = "远端标题",
                embeddedLyrics = "远端歌词",
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = FakeLyricsHttpClient(
                mapOf(
                    "https://lyrics.example/plain" to Result.success(
                        LyricsHttpResponse(statusCode = 200, body = "外部歌词"),
                    ),
                ),
            ),
            secureCredentialStore = EmptySecureCredentialStore,
            audioTagGateway = gateway,
            logger = NoopDiagnosticLogger,
        )

        val candidates = repository.searchLyricsCandidates(track, includeTrackProvidedCandidate = false)

        assertEquals(0, gateway.readCount)
        assertEquals(listOf("source-plain"), candidates.map { it.sourceId })
    }

    private fun createTestDatabase(): LynMusicDatabase {
        val path = Files.createTempFile("lynmusic-lyrics-manual", ".db")
        return buildLynMusicDatabase(
            Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
        )
    }

    private fun lyricsSourceConfig(
        id: String,
        name: String,
        priority: Int,
        urlTemplate: String,
        responseFormat: LyricsResponseFormat,
        extractor: String,
    ): LyricsSourceConfigEntity {
        return LyricsSourceConfigEntity(
            id = id,
            name = name,
            priority = priority,
            urlTemplate = urlTemplate,
            method = "GET",
            headersTemplate = "",
            queryTemplate = "",
            bodyTemplate = "",
            responseFormat = responseFormat.name,
            extractor = extractor,
            enabled = true,
        )
    }

    private suspend fun seedTrack(database: LynMusicDatabase, track: Track) {
        database.trackDao().upsertAll(
            listOf(
                TrackEntity(
                    id = track.id,
                    sourceId = track.sourceId,
                    title = track.title,
                    artistId = null,
                    artistName = track.artistName,
                    albumId = null,
                    albumTitle = track.albumTitle,
                    durationMs = track.durationMs,
                    trackNumber = null,
                    discNumber = null,
                    mediaLocator = track.mediaLocator,
                    relativePath = track.relativePath,
                    artworkLocator = track.artworkLocator,
                    sizeBytes = 0L,
                    modifiedAt = 0L,
                ),
            ),
        )
    }

    private suspend fun seedSambaSource(database: LynMusicDatabase, sourceId: String) {
        database.importSourceDao().upsert(
            ImportSourceEntity(
                id = sourceId,
                type = "SAMBA",
                label = "Samba",
                rootReference = "nas.local/Media",
                server = "nas.local",
                shareName = "445",
                directoryPath = "Media/Music",
                username = "user",
                credentialKey = null,
                allowInsecureTls = false,
                lastScannedAt = null,
                createdAt = 0L,
            ),
        )
    }

    private fun sampleTrack(artworkLocator: String? = null): Track {
        return Track(
            id = "track-1",
            sourceId = "local-1",
            title = "原始标题",
            artistName = "原始歌手",
            albumTitle = "原始专辑",
            durationMs = 123_000L,
            mediaLocator = "file:///music/song.mp3",
            relativePath = "song.mp3",
            artworkLocator = artworkLocator,
        )
    }

    private fun sampleSambaTrack(artworkLocator: String? = null): Track {
        return Track(
            id = "track-samba-1",
            sourceId = "samba-1",
            title = "扫描标题",
            artistName = "扫描歌手",
            albumTitle = "扫描专辑",
            durationMs = 123_000L,
            mediaLocator = buildSambaLocator("samba-1", "Artist/Song.mp3"),
            relativePath = "Artist/Song.mp3",
            artworkLocator = artworkLocator,
        )
    }
}

private class FakeLyricsHttpClient(
    private val responses: Map<String, Result<LyricsHttpResponse>>,
) : LyricsHttpClient {
    var requestCount: Int = 0
        private set

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requestCount += 1
        return responses[request.url] ?: Result.failure(IllegalArgumentException("Unexpected request: ${request.url}"))
    }
}

private class FakeAudioTagGateway(
    private val canEdit: Boolean,
    private val snapshot: AudioTagSnapshot? = null,
    private val readFailure: Throwable? = null,
) : AudioTagGateway {
    var readCount: Int = 0
        private set

    override suspend fun canEdit(track: Track): Boolean = canEdit

    override suspend fun canWrite(track: Track): Boolean = false

    override suspend fun read(track: Track): Result<AudioTagSnapshot> {
        readCount += 1
        readFailure?.let { return Result.failure(it) }
        return snapshot?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("No snapshot configured"))
    }

    override suspend fun write(track: Track, patch: AudioTagPatch): Result<AudioTagSnapshot> {
        return Result.failure(IllegalStateException("Not implemented"))
    }
}
