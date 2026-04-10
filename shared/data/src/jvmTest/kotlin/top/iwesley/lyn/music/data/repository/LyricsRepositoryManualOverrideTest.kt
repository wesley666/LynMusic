package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.FavoriteTrackEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.WorkflowLyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.NAVIDROME_LYRICS_SOURCE_ID

class LyricsRepositoryManualOverrideTest {

    @Test
    fun `auto direct lyrics picks highest scored candidate instead of first result`() = runTest {
        val database = createTestDatabase()
        val track = localTrack()
        database.trackDao().upsertAll(listOf(track.toEntity()))
        database.lyricsSourceConfigDao().upsert(
            directSourceEntity(
                id = "direct-auto",
                urlTemplate = "https://lyrics.example/direct-auto",
                priority = 100,
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(
                responses = mapOf(
                    "https://lyrics.example/direct-auto" to Result.success(
                        LyricsHttpResponse(
                            statusCode = 200,
                            body = """
                                {"data":[
                                  {"id":"wrong","title":"Glue","artist":"Artist A","album":"Album A","duration":215,"lyrics":"wrong line","cover":"https://img.example.com/wrong.jpg"},
                                  {"id":"right","title":"Blue","artist":"Artist A","album":"Album A","duration":215,"lyrics":"right line","cover":"https://img.example.com/right.jpg"}
                                ]}
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
            secureCredentialStore = MapCredentialStore(),
            logger = NoopDiagnosticLogger,
        )

        val resolved = repository.getLyrics(track)
        val cachedRow = database.lyricsCacheDao().getByTrackIdAndSourceId(track.id, "direct-auto")

        assertNotNull(resolved)
        assertEquals("direct-auto", resolved.document.sourceId)
        assertEquals("right line", resolved.document.lines.single().text)
        assertEquals("https://img.example.com/right.jpg", resolved.artworkLocator)
        assertNotNull(cachedRow)
        assertEquals("https://img.example.com/right.jpg", cachedRow.artworkLocator)
    }

    @Test
    fun `auto direct lyrics skips low scored candidate and falls through to workflow source`() = runTest {
        val database = createTestDatabase()
        val track = localTrack()
        database.trackDao().upsertAll(listOf(track.toEntity()))
        database.lyricsSourceConfigDao().upsert(
            directSourceEntity(
                id = "direct-low-score",
                urlTemplate = "https://lyrics.example/direct-low-score",
                priority = 100,
            ),
        )
        database.workflowLyricsSourceConfigDao().upsert(
            WorkflowLyricsSourceConfigEntity(
                id = "workflow-manual",
                name = "Workflow Manual",
                priority = 50,
                enabled = true,
                rawJson = TEST_WORKFLOW_JSON,
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(
                responses = mapOf(
                    "https://lyrics.example/direct-low-score" to Result.success(
                        LyricsHttpResponse(
                            statusCode = 200,
                            body = """
                                {"data":[
                                  {"id":"miss","title":"Completely Different","artist":"Someone Else","album":"Elsewhere","duration":180,"lyrics":"miss line"}
                                ]}
                            """.trimIndent(),
                        ),
                    ),
                    "https://lyrics.example/search?title=Blue" to Result.success(
                        LyricsHttpResponse(
                            statusCode = 200,
                            body = """{"data":[{"id":"wf-1","title":"Blue","artist":"Artist A","coverUrl":"https://img.example.com/workflow.jpg"}]}""",
                        ),
                    ),
                    "https://lyrics.example/workflow?id=wf-1" to Result.success(
                        LyricsHttpResponse(
                            statusCode = 200,
                            body = """{"data":{"content":"[00:01.00]workflow line"}}""",
                        ),
                    ),
                ),
            ),
            secureCredentialStore = MapCredentialStore(),
            logger = NoopDiagnosticLogger,
        )

        val resolved = repository.getLyrics(track)
        val directRow = database.lyricsCacheDao().getByTrackIdAndSourceId(track.id, "direct-low-score")

        assertNotNull(resolved)
        assertEquals("workflow-manual", resolved.document.sourceId)
        assertEquals("workflow line", resolved.document.lines.single().text)
        assertNull(directRow)
    }

    @Test
    fun `manual direct lyrics search sorts candidates by score descending`() = runTest {
        val database = createTestDatabase()
        val track = localTrack()
        database.lyricsSourceConfigDao().upsert(
            directSourceEntity(
                id = "direct-manual",
                urlTemplate = "https://lyrics.example/direct-manual",
                priority = 100,
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(
                responses = mapOf(
                    "https://lyrics.example/direct-manual" to Result.success(
                        LyricsHttpResponse(
                            statusCode = 200,
                            body = """
                                {"data":[
                                  {"id":"third","title":"Completely Different","artist":"Someone Else","album":"Elsewhere","duration":100,"lyrics":"third line"},
                                  {"id":"first","title":"Blue","artist":"Artist A","album":"Album A","duration":215,"lyrics":"first line"},
                                  {"id":"second","title":"Blue","artist":"Artist B","album":"Album A","duration":215,"lyrics":"second line"}
                                ]}
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
            secureCredentialStore = MapCredentialStore(),
            logger = NoopDiagnosticLogger,
        )

        val candidates = repository.searchLyricsCandidates(track, includeTrackProvidedCandidate = false)

        assertEquals(listOf("first", "second", "third"), candidates.mapNotNull { it.itemId })
    }

    @Test
    fun `navidrome lyrics prefer manual override over navidrome cache`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val track = navidromeTrack()
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = track.id,
                sourceId = NAVIDROME_LYRICS_SOURCE_ID,
                rawPayload = "[00:01.00]navidrome line",
                updatedAt = 1L,
            ),
        )
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = track.id,
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = "manual line",
                updatedAt = 2L,
                artworkLocator = "/tmp/manual-cover.jpg",
            ),
        )
        val httpClient = RecordingLyricsHttpClient()
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        )

        val resolved = repository.getLyrics(track)

        assertNotNull(resolved)
        assertEquals(MANUAL_LYRICS_OVERRIDE_SOURCE_ID, resolved.document.sourceId)
        assertEquals("manual line", resolved.document.lines.single().text)
        assertEquals("/tmp/manual-cover.jpg", resolved.artworkLocator)
        assertEquals(emptyList(), httpClient.requestedUrls)
    }

    @Test
    fun `manual direct apply persists override lyrics and artwork for library and favorites`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val track = navidromeTrack(artworkLocator = buildNavidromeCoverLocator("nav-source", "cover-original"))
        database.trackDao().upsertAll(listOf(track.toEntity()))
        database.favoriteTrackDao().upsert(
            FavoriteTrackEntity(
                trackId = track.id,
                sourceId = track.sourceId,
                remoteSongId = "song-1",
                favoritedAt = 1L,
            ),
        )
        val artworkCacheStore = FakeArtworkCacheStore(
            cached = mapOf("https://img.example.com/manual.jpg" to "/tmp/cache/manual.jpg"),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            artworkCacheStore = artworkCacheStore,
            logger = NoopDiagnosticLogger,
        )

        val applied = repository.applyLyricsCandidate(
            trackId = track.id,
            candidate = top.iwesley.lyn.music.core.model.LyricsSearchCandidate(
                sourceId = "direct-source",
                sourceName = "Direct Source",
                document = plainLyricsDocument("direct-source", "manual external line"),
                itemId = "song-42",
                artworkLocator = "https://img.example.com/manual.jpg",
                isTrackProvided = false,
            ),
        )

        val overrideRow = database.lyricsCacheDao()
            .getByTrackIdAndSourceId(track.id, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
        val resolved = repository.getLyrics(track)
        val libraryTrack = RoomLibraryRepository(database).getTracksByIds(listOf(track.id)).single()
        val favoriteTrack = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapCredentialStore(),
            httpClient = RecordingLyricsHttpClient(),
            logger = NoopDiagnosticLogger,
        ).favoriteTracks.first().single()

        assertNotNull(overrideRow)
        assertEquals(MANUAL_LYRICS_OVERRIDE_SOURCE_ID, overrideRow.sourceId)
        assertEquals("https://img.example.com/manual.jpg", overrideRow.artworkLocator)
        assertEquals("direct-source", assertNotNull(applied.document).sourceId)
        assertEquals("https://img.example.com/manual.jpg", applied.artworkLocator)
        assertNotNull(resolved)
        assertEquals(MANUAL_LYRICS_OVERRIDE_SOURCE_ID, resolved.document.sourceId)
        assertEquals("manual external line", resolved.document.lines.single().text)
        assertEquals("https://img.example.com/manual.jpg", resolved.artworkLocator)
        assertEquals("https://img.example.com/manual.jpg", libraryTrack.artworkLocator)
        assertEquals("https://img.example.com/manual.jpg", favoriteTrack.artworkLocator)
        assertEquals(
            listOf("https://img.example.com/manual.jpg" to "https://img.example.com/manual.jpg"),
            artworkCacheStore.requests,
        )
    }

    @Test
    fun `manual workflow apply persists override lyrics and artwork`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val track = navidromeTrack()
        database.trackDao().upsertAll(listOf(track.toEntity()))
        database.workflowLyricsSourceConfigDao().upsert(
            WorkflowLyricsSourceConfigEntity(
                id = "workflow-manual",
                name = "Workflow Manual",
                priority = 50,
                enabled = true,
                rawJson = TEST_WORKFLOW_JSON,
            ),
        )
        val artworkCacheStore = FakeArtworkCacheStore(
            cached = mapOf("https://img.example.com/workflow.jpg" to "/tmp/cache/workflow.jpg"),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(
                responses = mapOf(
                    "https://lyrics.example/workflow?id=wf-1" to Result.success(
                        LyricsHttpResponse(
                            statusCode = 200,
                            body = """{"data":{"content":"[00:01.00]workflow line"}}""",
                        ),
                    ),
                ),
            ),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            artworkCacheStore = artworkCacheStore,
            logger = NoopDiagnosticLogger,
        )

        val applied = repository.applyWorkflowSongCandidate(
            trackId = track.id,
            candidate = WorkflowSongCandidate(
                id = "wf-1",
                sourceId = "workflow-manual",
                sourceName = "Workflow Manual",
                title = "Blue",
                artists = listOf("Artist A"),
                album = "Album A",
                durationSeconds = 215,
                imageUrl = "https://img.example.com/workflow.jpg",
            ),
        )

        val overrideRow = database.lyricsCacheDao()
            .getByTrackIdAndSourceId(track.id, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
        val libraryTrack = RoomLibraryRepository(database).getTracksByIds(listOf(track.id)).single()
        val resolved = repository.getLyrics(track)

        assertNotNull(overrideRow)
        assertEquals("https://img.example.com/workflow.jpg", overrideRow.artworkLocator)
        assertEquals("workflow-manual", assertNotNull(applied.document).sourceId)
        assertEquals("https://img.example.com/workflow.jpg", applied.artworkLocator)
        assertNotNull(resolved)
        assertEquals(MANUAL_LYRICS_OVERRIDE_SOURCE_ID, resolved.document.sourceId)
        assertEquals("workflow line", resolved.document.lines.single().text)
        assertEquals("https://img.example.com/workflow.jpg", resolved.artworkLocator)
        assertEquals("https://img.example.com/workflow.jpg", libraryTrack.artworkLocator)
        assertEquals(
            listOf("https://img.example.com/workflow.jpg" to "https://img.example.com/workflow.jpg"),
            artworkCacheStore.requests,
        )
    }

    @Test
    fun `manual workflow apply rejects json error payload`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val track = navidromeTrack()
        database.trackDao().upsertAll(listOf(track.toEntity()))
        database.workflowLyricsSourceConfigDao().upsert(
            WorkflowLyricsSourceConfigEntity(
                id = "workflow-json-error",
                name = "Workflow Json Error",
                priority = 50,
                enabled = true,
                rawJson = TEST_JSON_ERROR_WORKFLOW_JSON,
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(
                responses = mapOf(
                    "https://lyrics.example/workflow-error?id=wf-404" to Result.success(
                        LyricsHttpResponse(
                            statusCode = 200,
                            body = """{"message":{"header":{"status_code":404,"execute_time":0.0054500102996826,"instrumental":0},"body":{}}}""",
                        ),
                    ),
                ),
            ),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        )

        val error = assertFailsWith<IllegalStateException> {
            repository.applyWorkflowSongCandidate(
                trackId = track.id,
                candidate = WorkflowSongCandidate(
                    id = "wf-404",
                    sourceId = "workflow-json-error",
                    sourceName = "Workflow Json Error",
                    title = "Blue",
                    artists = listOf("Artist A"),
                    album = "Album A",
                    durationSeconds = 215,
                ),
            )
        }
        val overrideRow = database.lyricsCacheDao()
            .getByTrackIdAndSourceId(track.id, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)

        assertEquals("Workflow lyrics source Workflow Json Error 没有返回可解析歌词。", error.message)
        assertNull(overrideRow)
    }

    @Test
    fun `track provided apply clears manual override and restores source lyrics`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val originalArtwork = buildNavidromeCoverLocator("nav-source", "cover-original")
        val track = navidromeTrack(artworkLocator = originalArtwork)
        database.trackDao().upsertAll(listOf(track.toEntity()))
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = track.id,
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = "manual line",
                updatedAt = 10L,
                artworkLocator = "/tmp/cache/manual.jpg",
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        )

        val applied = repository.applyLyricsCandidate(
            trackId = track.id,
            candidate = top.iwesley.lyn.music.core.model.LyricsSearchCandidate(
                sourceId = NAVIDROME_LYRICS_SOURCE_ID,
                sourceName = "Navidrome",
                document = syncedLyricsDocument(NAVIDROME_LYRICS_SOURCE_ID, "source line"),
                artworkLocator = originalArtwork,
                isTrackProvided = true,
            ),
        )

        val overrideRow = database.lyricsCacheDao()
            .getByTrackIdAndSourceId(track.id, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
        val resolved = repository.getLyrics(track)
        val libraryTrack = RoomLibraryRepository(database).getTracksByIds(listOf(track.id)).single()

        assertNull(overrideRow)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, assertNotNull(applied.document).sourceId)
        assertEquals(originalArtwork, applied.artworkLocator)
        assertNotNull(resolved)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, resolved.document.sourceId)
        assertEquals("source line", resolved.document.lines.single().text)
        assertEquals(originalArtwork, libraryTrack.artworkLocator)
    }

    @Test
    fun `rescan keeps manual override lyrics and artwork`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val originalTrack = navidromeTrack(artworkLocator = buildNavidromeCoverLocator("nav-source", "cover-original"))
        database.trackDao().upsertAll(listOf(originalTrack.toEntity()))
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = originalTrack.id,
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = "manual line",
                updatedAt = 5L,
                artworkLocator = "/tmp/cache/manual.jpg",
            ),
        )
        val importRepository = RoomImportSourceRepository(
            database = database,
            gateway = FakeImportGateway(
                navidromeReport = ImportScanReport(
                    tracks = listOf(
                        ImportedTrackCandidate(
                            title = "Blue (Rescanned)",
                            artistName = "Artist A",
                            albumTitle = "Album A",
                            durationMs = 215_000L,
                            mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
                            relativePath = "Artist A/Album A/Blue.flac",
                            artworkLocator = buildNavidromeCoverLocator("nav-source", "cover-rescanned"),
                            sizeBytes = 1L,
                            modifiedAt = 2L,
                        ),
                    ),
                ),
            ),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
        )

        importRepository.rescanSource("nav-source").getOrThrow()

        val libraryTrack = RoomLibraryRepository(database).getTracksByIds(listOf(originalTrack.id)).single()
        val resolved = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        ).getLyrics(libraryTrack)

        assertEquals("Blue (Rescanned)", libraryTrack.title)
        assertEquals("/tmp/cache/manual.jpg", libraryTrack.artworkLocator)
        assertNotNull(resolved)
        assertEquals(MANUAL_LYRICS_OVERRIDE_SOURCE_ID, resolved.document.sourceId)
        assertEquals("manual line", resolved.document.lines.single().text)
    }

    @Test
    fun `manual lyrics only apply preserves existing artwork override`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val track = navidromeTrack(artworkLocator = buildNavidromeCoverLocator("nav-source", "cover-original"))
        database.trackDao().upsertAll(listOf(track.toEntity()))
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = track.id,
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = "old manual line",
                updatedAt = 1L,
                artworkLocator = "/tmp/cache/existing.jpg",
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        )

        val applied = repository.applyLyricsCandidate(
            trackId = track.id,
            candidate = top.iwesley.lyn.music.core.model.LyricsSearchCandidate(
                sourceId = "direct-source",
                sourceName = "Direct Source",
                document = plainLyricsDocument("direct-source", "new manual line"),
                itemId = "song-42",
                artworkLocator = "https://img.example.com/new.jpg",
                isTrackProvided = false,
            ),
            mode = LyricsSearchApplyMode.LYRICS_ONLY,
        )

        val overrideRow = database.lyricsCacheDao()
            .getByTrackIdAndSourceId(track.id, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
        val resolved = repository.getLyrics(track)
        val libraryTrack = RoomLibraryRepository(database).getTracksByIds(listOf(track.id)).single()

        assertNotNull(overrideRow)
        assertEquals("/tmp/cache/existing.jpg", overrideRow.artworkLocator)
        assertEquals("new manual line", assertNotNull(resolved).document.lines.single().text)
        assertEquals("/tmp/cache/existing.jpg", resolved.artworkLocator)
        assertEquals("/tmp/cache/existing.jpg", applied.artworkLocator)
        assertEquals("direct-source", assertNotNull(applied.document).sourceId)
        assertEquals("/tmp/cache/existing.jpg", libraryTrack.artworkLocator)
    }

    @Test
    fun `manual artwork only override updates artwork without shadowing cached source lyrics`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val track = navidromeTrack(artworkLocator = buildNavidromeCoverLocator("nav-source", "cover-original"))
        database.trackDao().upsertAll(listOf(track.toEntity()))
        database.favoriteTrackDao().upsert(
            FavoriteTrackEntity(
                trackId = track.id,
                sourceId = track.sourceId,
                remoteSongId = "song-1",
                favoritedAt = 1L,
            ),
        )
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = track.id,
                sourceId = NAVIDROME_LYRICS_SOURCE_ID,
                rawPayload = "[00:01.00]source line",
                updatedAt = 2L,
            ),
        )
        val artworkCacheStore = FakeArtworkCacheStore(
            cached = mapOf("https://img.example.com/art-only.jpg" to "/tmp/cache/art-only.jpg"),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            artworkCacheStore = artworkCacheStore,
            logger = NoopDiagnosticLogger,
        )

        val applied = repository.applyLyricsCandidate(
            trackId = track.id,
            candidate = top.iwesley.lyn.music.core.model.LyricsSearchCandidate(
                sourceId = "direct-source",
                sourceName = "Direct Source",
                document = plainLyricsDocument("direct-source", "ignored line"),
                itemId = "song-77",
                artworkLocator = "https://img.example.com/art-only.jpg",
                isTrackProvided = false,
            ),
            mode = LyricsSearchApplyMode.ARTWORK_ONLY,
        )

        val overrideRow = database.lyricsCacheDao()
            .getByTrackIdAndSourceId(track.id, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
        val resolved = repository.getLyrics(track)
        val libraryTrack = RoomLibraryRepository(database).getTracksByIds(listOf(track.id)).single()
        val favoriteTrack = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapCredentialStore(),
            httpClient = RecordingLyricsHttpClient(),
            logger = NoopDiagnosticLogger,
        ).favoriteTracks.first().single()

        assertNotNull(overrideRow)
        assertEquals("", overrideRow.rawPayload)
        assertEquals("https://img.example.com/art-only.jpg", overrideRow.artworkLocator)
        assertNull(applied.document)
        assertEquals("https://img.example.com/art-only.jpg", applied.artworkLocator)
        assertNotNull(resolved)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, resolved.document.sourceId)
        assertEquals("source line", resolved.document.lines.single().text)
        assertEquals("https://img.example.com/art-only.jpg", resolved.artworkLocator)
        assertEquals("https://img.example.com/art-only.jpg", libraryTrack.artworkLocator)
        assertEquals("https://img.example.com/art-only.jpg", favoriteTrack.artworkLocator)
        assertEquals(
            listOf("https://img.example.com/art-only.jpg" to "https://img.example.com/art-only.jpg"),
            artworkCacheStore.requests,
        )
    }

    @Test
    fun `track provided artwork only clears manual artwork override but keeps manual lyrics`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val originalArtwork = buildNavidromeCoverLocator("nav-source", "cover-original")
        val track = navidromeTrack(artworkLocator = originalArtwork)
        database.trackDao().upsertAll(listOf(track.toEntity()))
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = track.id,
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = "manual line",
                updatedAt = 10L,
                artworkLocator = "/tmp/cache/manual.jpg",
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        )

        val applied = repository.applyLyricsCandidate(
            trackId = track.id,
            candidate = top.iwesley.lyn.music.core.model.LyricsSearchCandidate(
                sourceId = NAVIDROME_LYRICS_SOURCE_ID,
                sourceName = "Navidrome",
                document = syncedLyricsDocument(NAVIDROME_LYRICS_SOURCE_ID, "source line"),
                artworkLocator = originalArtwork,
                isTrackProvided = true,
            ),
            mode = LyricsSearchApplyMode.ARTWORK_ONLY,
        )

        val overrideRow = database.lyricsCacheDao()
            .getByTrackIdAndSourceId(track.id, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
        val resolved = repository.getLyrics(track)
        val libraryTrack = RoomLibraryRepository(database).getTracksByIds(listOf(track.id)).single()

        assertNotNull(overrideRow)
        assertEquals("manual line", overrideRow.rawPayload)
        assertNull(overrideRow.artworkLocator)
        assertNull(applied.document)
        assertEquals(originalArtwork, applied.artworkLocator)
        assertNotNull(resolved)
        assertEquals(MANUAL_LYRICS_OVERRIDE_SOURCE_ID, resolved.document.sourceId)
        assertEquals("manual line", resolved.document.lines.single().text)
        assertNull(resolved.artworkLocator)
        assertEquals(originalArtwork, libraryTrack.artworkLocator)
    }

    @Test
    fun `manual artwork apply persists normalized artwork locator and caches by normalized locator`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val track = navidromeTrack()
        database.trackDao().upsertAll(listOf(track.toEntity()))
        val rawArtworkLocator = "https://y.gtimg.cn/music/photo_new/T002R800x800M000001O06fF2b3W8Pjpg?max_age=2592000"
        val normalizedArtworkLocator = "https://y.gtimg.cn/music/photo_new/T002R800x800M000001O06fF2b3W8P.jpg?max_age=2592000"
        val artworkCacheStore = FakeArtworkCacheStore(
            cached = mapOf(normalizedArtworkLocator to "/tmp/cache/gtimg.jpg"),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = RecordingLyricsHttpClient(),
            secureCredentialStore = MapCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            artworkCacheStore = artworkCacheStore,
            logger = NoopDiagnosticLogger,
        )

        val applied = repository.applyLyricsCandidate(
            trackId = track.id,
            candidate = top.iwesley.lyn.music.core.model.LyricsSearchCandidate(
                sourceId = "direct-source",
                sourceName = "Direct Source",
                document = plainLyricsDocument("direct-source", "ignored line"),
                itemId = "song-88",
                artworkLocator = rawArtworkLocator,
                isTrackProvided = false,
            ),
            mode = LyricsSearchApplyMode.ARTWORK_ONLY,
        )

        val overrideRow = database.lyricsCacheDao()
            .getByTrackIdAndSourceId(track.id, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
        val libraryTrack = RoomLibraryRepository(database).getTracksByIds(listOf(track.id)).single()

        assertNotNull(overrideRow)
        assertEquals(normalizedArtworkLocator, overrideRow.artworkLocator)
        assertEquals(normalizedArtworkLocator, applied.artworkLocator)
        assertEquals(normalizedArtworkLocator, libraryTrack.artworkLocator)
        assertEquals(
            listOf(normalizedArtworkLocator to normalizedArtworkLocator),
            artworkCacheStore.requests,
        )
    }
}

private fun createTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-manual-override", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private suspend fun seedNavidromeSource(database: LynMusicDatabase) {
    database.importSourceDao().upsert(
        ImportSourceEntity(
            id = "nav-source",
            type = ImportSourceType.NAVIDROME.name,
            label = "Navidrome",
            rootReference = "https://demo.example.com/navidrome",
            server = null,
            shareName = null,
            directoryPath = null,
            username = "demo",
            credentialKey = "nav-cred",
            allowInsecureTls = false,
            lastScannedAt = null,
            createdAt = 1L,
        ),
    )
}

private fun directSourceEntity(
    id: String,
    urlTemplate: String,
    priority: Int,
): LyricsSourceConfigEntity {
    return LyricsSourceConfigEntity(
        id = id,
        name = id,
        method = "GET",
        urlTemplate = urlTemplate,
        headersTemplate = "",
        queryTemplate = "",
        bodyTemplate = "",
        responseFormat = "JSON",
        extractor = "json-map:data|lyrics=lyrics,title=title,artist=artist,album=album,durationSeconds=duration,id=id,coverUrl=cover",
        priority = priority,
        enabled = true,
    )
}

private fun navidromeTrack(
    artworkLocator: String? = buildNavidromeCoverLocator("nav-source", "cover-1"),
): Track {
    return Track(
        id = navidromeTrackIdFor("nav-source", "song-1"),
        sourceId = "nav-source",
        title = "Blue",
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 215_000L,
        mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
        relativePath = "Artist A/Album A/Blue.flac",
        artworkLocator = artworkLocator,
        sizeBytes = 1L,
        modifiedAt = 1L,
    )
}

private fun localTrack(
    artworkLocator: String? = "/tmp/default-artwork.jpg",
): Track {
    return Track(
        id = "track:local-source:artist a/album a/blue.mp3",
        sourceId = "local-source",
        title = "Blue",
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 215_000L,
        mediaLocator = "file:///music/Artist%20A/Album%20A/Blue.mp3",
        relativePath = "Artist A/Album A/Blue.mp3",
        artworkLocator = artworkLocator,
        sizeBytes = 1L,
        modifiedAt = 1L,
    )
}

private fun Track.toEntity(): TrackEntity {
    return TrackEntity(
        id = id,
        sourceId = sourceId,
        title = title,
        artistId = artistName?.let(::artistIdForLibraryMetadata),
        artistName = artistName,
        albumId = albumTitle?.let { albumIdForLibraryMetadata(artistName, it) },
        albumTitle = albumTitle,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        mediaLocator = mediaLocator,
        relativePath = relativePath,
        artworkLocator = artworkLocator,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
    )
}

private fun plainLyricsDocument(sourceId: String, line: String): LyricsDocument {
    return LyricsDocument(
        lines = listOf(LyricsLine(timestampMs = null, text = line)),
        sourceId = sourceId,
        rawPayload = line,
    )
}

private fun syncedLyricsDocument(sourceId: String, line: String): LyricsDocument {
    return LyricsDocument(
        lines = listOf(LyricsLine(timestampMs = 1_000L, text = line)),
        sourceId = sourceId,
        rawPayload = "[00:01.00]$line",
    )
}

private class RecordingLyricsHttpClient(
    private val responses: Map<String, Result<LyricsHttpResponse>> = emptyMap(),
) : LyricsHttpClient {
    val requestedUrls = mutableListOf<String>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requestedUrls += request.url
        return responses[request.url]
            ?: Result.failure(IllegalArgumentException("Unexpected request: ${request.url}"))
    }
}

private class FakeArtworkCacheStore(
    private val cached: Map<String, String>,
) : ArtworkCacheStore {
    val requests = mutableListOf<Pair<String, String>>()

    override suspend fun cache(locator: String, cacheKey: String): String? {
        requests += locator to cacheKey
        return cached[locator] ?: locator
    }
}

private class MapCredentialStore(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : SecureCredentialStore {
    override suspend fun put(key: String, value: String) {
        values[key] = value
    }

    override suspend fun get(key: String): String? = values[key]

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}

private class FakeImportGateway(
    private val navidromeReport: ImportScanReport,
) : ImportSourceGateway {
    override suspend fun pickLocalFolder(): LocalFolderSelection? = null

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        error("Unexpected local scan")
    }

    override suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport {
        error("Unexpected samba scan")
    }

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        error("Unexpected webdav scan")
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        return navidromeReport
    }
}

private const val TEST_WORKFLOW_JSON = """
{
  "id": "workflow-manual",
  "name": "Workflow Manual",
  "kind": "workflow",
  "enabled": true,
  "priority": 50,
  "search": {
    "method": "GET",
    "url": "https://lyrics.example/search",
    "queryTemplate": "title={title}",
    "responseFormat": "json",
    "resultPath": "data",
    "mapping": {
      "id": "id",
      "title": "title",
      "artists": "artist"
    }
  },
  "selection": {
    "titleWeight": 1.0,
    "artistWeight": 0.0,
    "albumWeight": 0.0,
    "durationWeight": 0.0,
    "durationToleranceSeconds": 3,
    "minScore": 0.0,
    "maxCandidates": 10
  },
  "lyrics": {
    "steps": [
      {
        "method": "GET",
        "url": "https://lyrics.example/workflow",
        "queryTemplate": "id={candidate.id}",
        "responseFormat": "json",
        "payloadPath": "data.content",
        "format": "lrc",
        "transforms": ["trim"]
      }
    ]
  },
  "optionalFields": {
    "coverUrlField": "coverUrl"
  }
}
"""

private const val TEST_JSON_ERROR_WORKFLOW_JSON = """
{
  "id": "workflow-json-error",
  "name": "Workflow Json Error",
  "kind": "workflow",
  "enabled": true,
  "priority": 50,
  "search": {
    "method": "GET",
    "url": "https://lyrics.example/search",
    "queryTemplate": "title={title}",
    "responseFormat": "json",
    "resultPath": "data",
    "mapping": {
      "id": "id",
      "title": "title",
      "artists": "artist"
    }
  },
  "selection": {
    "titleWeight": 1.0,
    "artistWeight": 0.0,
    "albumWeight": 0.0,
    "durationWeight": 0.0,
    "durationToleranceSeconds": 3,
    "minScore": 0.0,
    "maxCandidates": 10
  },
  "lyrics": {
    "steps": [
      {
        "method": "GET",
        "url": "https://lyrics.example/workflow-error",
        "queryTemplate": "id={candidate.id}",
        "responseFormat": "json",
        "payloadPath": "message.body",
        "format": "json",
        "extractor": "json-lines:time.total,text",
        "transforms": ["trim"]
      }
    ]
  }
}
"""
