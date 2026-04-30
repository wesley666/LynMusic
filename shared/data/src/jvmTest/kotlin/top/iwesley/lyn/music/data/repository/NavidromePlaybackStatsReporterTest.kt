package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import io.ktor.http.parseUrl
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

class NavidromePlaybackStatsReporterTest {

    @Test
    fun `scrobble requests include now playing and submission parameters`() = runTest {
        val database = createTestDatabase()
        val credentials = StatsCredentialStore(mutableMapOf("nav-cred" to "secret"))
        val httpClient = RecordingStatsHttpClient()
        val reporter = NavidromePlaybackStatsReporter(
            database = database,
            secureCredentialStore = credentials,
            httpClient = httpClient,
        )

        try {
            seedNavidromeSource(database)

            reporter.reportNowPlaying(navidromeTrack("song-1"), atMillis = 123L)
            reporter.submitPlay(navidromeTrack("song-1"), atMillis = 456L)

            assertEquals(listOf("scrobble", "scrobble"), httpClient.requestedEndpoints)
            assertEquals("song-1", httpClient.requests[0].query("id"))
            assertEquals("false", httpClient.requests[0].query("submission"))
            assertEquals("123", httpClient.requests[0].query("time"))
            assertEquals("song-1", httpClient.requests[1].query("id"))
            assertEquals("true", httpClient.requests[1].query("submission"))
            assertEquals("456", httpClient.requests[1].query("time"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `non navidrome and mismatched source tracks do not scrobble`() = runTest {
        val database = createTestDatabase()
        val httpClient = RecordingStatsHttpClient()
        val reporter = NavidromePlaybackStatsReporter(
            database = database,
            secureCredentialStore = StatsCredentialStore(mutableMapOf("nav-cred" to "secret")),
            httpClient = httpClient,
        )

        try {
            seedNavidromeSource(database)

            reporter.reportNowPlaying(localTrack(), atMillis = 123L)
            reporter.submitPlay(
                navidromeTrack("song-1").copy(
                    mediaLocator = buildNavidromeSongLocator("other-source", "song-1"),
                ),
                atMillis = 456L,
            )

            assertEquals(emptyList(), httpClient.requests)
        } finally {
            database.close()
        }
    }

    @Test
    fun `disabled or incomplete navidrome sources do not scrobble`() = runTest {
        val database = createTestDatabase()
        val httpClient = RecordingStatsHttpClient()
        val reporter = NavidromePlaybackStatsReporter(
            database = database,
            secureCredentialStore = StatsCredentialStore(mutableMapOf("nav-cred" to "secret")),
            httpClient = httpClient,
        )

        try {
            seedNavidromeSource(database, enabled = false)
            reporter.submitPlay(navidromeTrack("song-1"), atMillis = 456L)

            seedNavidromeSource(database, credentialKey = null)
            reporter.submitPlay(navidromeTrack("song-1"), atMillis = 789L)

            assertEquals(emptyList(), httpClient.requests)
        } finally {
            database.close()
        }
    }

    @Test
    fun `scrobble http failures do not escape reporter`() = runTest {
        val database = createTestDatabase()
        val httpClient = RecordingStatsHttpClient(fail = true)
        val reporter = NavidromePlaybackStatsReporter(
            database = database,
            secureCredentialStore = StatsCredentialStore(mutableMapOf("nav-cred" to "secret")),
            httpClient = httpClient,
        )

        try {
            seedNavidromeSource(database)

            reporter.submitPlay(navidromeTrack("song-1"), atMillis = 456L)

            assertEquals(listOf("scrobble"), httpClient.requestedEndpoints)
        } finally {
            database.close()
        }
    }
}

private fun createTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-navidrome-stats", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private suspend fun seedNavidromeSource(
    database: LynMusicDatabase,
    enabled: Boolean = true,
    credentialKey: String? = "nav-cred",
) {
    database.importSourceDao().upsert(
        ImportSourceEntity(
            id = "nav-source",
            type = "NAVIDROME",
            label = "Navidrome",
            rootReference = "https://demo.example.com/navidrome",
            server = null,
            shareName = null,
            directoryPath = null,
            username = "demo",
            credentialKey = credentialKey,
            allowInsecureTls = false,
            enabled = enabled,
            lastScannedAt = null,
            createdAt = 1L,
        ),
    )
}

private fun navidromeTrack(songId: String): Track {
    return Track(
        id = navidromeTrackIdFor("nav-source", songId),
        sourceId = "nav-source",
        title = "Blue",
        artistName = "Artist B",
        albumTitle = "Album B",
        durationMs = 215_000L,
        mediaLocator = buildNavidromeSongLocator("nav-source", songId),
        relativePath = "Artist B/Album B/Blue.flac",
    )
}

private fun localTrack(): Track {
    return Track(
        id = "track:local-1:blue.mp3",
        sourceId = "local-1",
        title = "Blue",
        artistName = "Artist B",
        albumTitle = "Album B",
        durationMs = 215_000L,
        mediaLocator = "file:///music/blue.mp3",
        relativePath = "Artist B/Album B/Blue.mp3",
    )
}

private class RecordingStatsHttpClient(
    private val fail: Boolean = false,
) : LyricsHttpClient {
    val requests = mutableListOf<LyricsRequest>()
    val requestedEndpoints = mutableListOf<String>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requests += request
        val endpoint = requireNotNull(parseUrl(request.url)).encodedPath.substringAfterLast('/')
        requestedEndpoints += endpoint
        return if (fail) {
            Result.success(
                LyricsHttpResponse(
                    statusCode = 500,
                    body = """{"subsonic-response":{"status":"failed","version":"1.16.1"}}""",
                ),
            )
        } else {
            Result.success(
                LyricsHttpResponse(
                    statusCode = 200,
                    body = """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""",
                ),
            )
        }
    }
}

private fun LyricsRequest.query(name: String): String? {
    return requireNotNull(parseUrl(url)).parameters[name]
}

private class StatsCredentialStore(
    private val values: MutableMap<String, String> = linkedMapOf(),
) : SecureCredentialStore {
    override suspend fun put(key: String, value: String) {
        values[key] = value
    }

    override suspend fun get(key: String): String? = values[key]

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
