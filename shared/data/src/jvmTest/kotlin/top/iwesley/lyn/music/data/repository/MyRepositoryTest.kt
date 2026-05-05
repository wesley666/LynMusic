package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import io.ktor.http.parseUrl
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.AlbumEntity
import top.iwesley.lyn.music.data.db.DailyRecommendationEntity
import top.iwesley.lyn.music.data.db.FavoriteTrackEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.TrackPlaybackStatsEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

class MyRepositoryTest {

    @Test
    fun `local recent tracks and albums are sorted by last played and hide disabled sources`() = runTest {
        val database = createMyTestDatabase()
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(),
            httpClient = RecordingMyHttpClient(),
        )

        try {
            seedSource(database, sourceId = "local-1", type = "LOCAL_FOLDER", enabled = true)
            seedSource(database, sourceId = "local-disabled", type = "LOCAL_FOLDER", enabled = false)
            database.albumDao().upsertAll(
                listOf(
                    albumEntity("album-new", "New Album"),
                    albumEntity("album-old", "Old Album"),
                    albumEntity("album-disabled", "Hidden Album"),
                ),
            )
            database.trackDao().upsertAll(
                listOf(
                    trackEntity("track-old", "local-1", "Old Song", "album-old", "Old Album"),
                    trackEntity("track-new", "local-1", "New Song", "album-new", "New Album"),
                    trackEntity("track-disabled", "local-disabled", "Hidden Song", "album-disabled", "Hidden Album"),
                ),
            )
            database.trackPlaybackStatsDao().setPlayStats("track-old", "local-1", playCount = 1, lastPlayedAt = 100L)
            database.trackPlaybackStatsDao().setPlayStats("track-new", "local-1", playCount = 2, lastPlayedAt = 300L)
            database.trackPlaybackStatsDao()
                .setPlayStats("track-disabled", "local-disabled", playCount = 3, lastPlayedAt = 500L)
            database.albumPlaybackStatsDao().setPlayStats("album-old", playCount = 1, lastPlayedAt = 100L)
            database.albumPlaybackStatsDao().setPlayStats("album-new", playCount = 2, lastPlayedAt = 300L)
            database.albumPlaybackStatsDao().setPlayStats("album-disabled", playCount = 3, lastPlayedAt = 500L)

            val recentTracks = repository.recentTracks.first()
            val recentAlbums = repository.recentAlbums.first()

            assertEquals(listOf("track-new", "track-old"), recentTracks.map { it.track.id })
            assertEquals(listOf("album-new", "album-old"), recentAlbums.map { it.album.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun `navidrome refresh requests recent albums and stores server played stats`() = runTest {
        val database = createMyTestDatabase()
        val httpClient = RecordingMyHttpClient(
            albumListBody = navidromeAlbumListBody(),
            albumBody = navidromeAlbumBody(),
        )
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(mutableMapOf("nav-cred" to "secret")),
            httpClient = httpClient,
        )

        try {
            seedSource(database, sourceId = "nav-source", type = "NAVIDROME", enabled = true)
            database.albumDao().upsertAll(listOf(albumEntity("album-nav", "Server Album")))
            database.trackDao().upsertAll(
                listOf(
                    trackEntity(
                        trackId = navidromeTrackIdFor("nav-source", "song-1"),
                        sourceId = "nav-source",
                        title = "Server Song",
                        albumId = "album-nav",
                        albumTitle = "Server Album",
                        mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
                    ),
                ),
            )
            database.trackPlaybackStatsDao().setPlayStats(
                trackId = navidromeTrackIdFor("nav-source", "song-1"),
                sourceId = "nav-source",
                playCount = 1,
                lastPlayedAt = 100L,
            )

            val result = repository.refreshNavidromeRecentPlays()

            assertTrue(result.isSuccess)
            assertEquals(listOf("getAlbumList2", "getAlbum"), httpClient.requestedEndpoints)
            assertEquals("recent", httpClient.requests.first().query("type"))
            val trackStats = database.trackPlaybackStatsDao().getByTrackId(
                navidromeTrackIdFor("nav-source", "song-1"),
            )
            val albumStats = database.albumPlaybackStatsDao().getByAlbumId("album-nav")
            assertEquals(7, trackStats?.playCount)
            assertEquals(playedAt("2026-04-30T10:05:00Z"), trackStats?.lastPlayedAt)
            assertEquals(4, albumStats?.playCount)
            assertEquals(playedAt("2026-04-30T10:00:00Z"), albumStats?.lastPlayedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun `navidrome refresh failure keeps local recent stats available`() = runTest {
        val database = createMyTestDatabase()
        val httpClient = RecordingMyHttpClient(failEndpoint = "getAlbumList2")
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(mutableMapOf("nav-cred" to "secret")),
            httpClient = httpClient,
        )

        try {
            seedSource(database, sourceId = "nav-source", type = "NAVIDROME", enabled = true)
            database.albumDao().upsertAll(listOf(albumEntity("album-nav", "Cached Album")))
            database.trackDao().upsertAll(
                listOf(
                    trackEntity(
                        trackId = navidromeTrackIdFor("nav-source", "song-1"),
                        sourceId = "nav-source",
                        title = "Cached Song",
                        albumId = "album-nav",
                        albumTitle = "Cached Album",
                        mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
                    ),
                ),
            )
            database.trackPlaybackStatsDao().setPlayStats(
                trackId = navidromeTrackIdFor("nav-source", "song-1"),
                sourceId = "nav-source",
                playCount = 2,
                lastPlayedAt = 200L,
            )

            val result = repository.refreshNavidromeRecentPlays()
            val recentTracks = repository.recentTracks.first()

            assertTrue(result.isFailure)
            assertEquals(listOf("getAlbumList2"), httpClient.requestedEndpoints)
            assertEquals(listOf("Cached Song"), recentTracks.map { it.track.title })
        } finally {
            database.close()
        }
    }

    @Test
    fun `daily recommendation caches current date and creates next date separately`() = runTest {
        val database = createMyTestDatabase()
        val dateProvider = FakeDailyRecommendationDateKeyProvider("2026-05-01")
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(),
            httpClient = RecordingMyHttpClient(),
            dailyRecommendationDateKeyProvider = dateProvider,
        )

        try {
            seedSource(database, sourceId = "local-1", type = "LOCAL_FOLDER", enabled = true)
            database.trackDao().upsertAll(
                listOf(
                    trackEntity("track-1", "local-1", "A Song", null, null),
                    trackEntity("track-2", "local-1", "B Song", null, null),
                ),
            )

            assertTrue(repository.ensureDailyRecommendation().isSuccess)
            val first = database.dailyRecommendationDao().getByDateKey("2026-05-01")
            assertTrue(repository.ensureDailyRecommendation().isSuccess)
            val repeated = database.dailyRecommendationDao().getByDateKey("2026-05-01")
            dateProvider.dateKey = "2026-05-02"
            assertTrue(repository.ensureDailyRecommendation().isSuccess)
            val next = database.dailyRecommendationDao().getByDateKey("2026-05-02")

            assertEquals(first?.generatedAt, repeated?.generatedAt)
            assertEquals(first?.trackIds, repeated?.trackIds)
            assertTrue(first?.trackIds.orEmpty().isNotBlank())
            assertTrue(next?.trackIds.orEmpty().isNotBlank())
        } finally {
            database.close()
        }
    }

    @Test
    fun `daily recommendation does not cache empty library`() = runTest {
        val database = createMyTestDatabase()
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(),
            httpClient = RecordingMyHttpClient(),
            dailyRecommendationDateKeyProvider = FakeDailyRecommendationDateKeyProvider("2026-05-01"),
        )

        try {
            seedSource(database, sourceId = "local-1", type = "LOCAL_FOLDER", enabled = true)

            assertTrue(repository.ensureDailyRecommendation().isSuccess)

            assertNull(database.dailyRecommendationDao().getByDateKey("2026-05-01"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `daily recommendation overwrites existing empty cache when songs appear`() = runTest {
        val database = createMyTestDatabase()
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(),
            httpClient = RecordingMyHttpClient(),
            dailyRecommendationDateKeyProvider = FakeDailyRecommendationDateKeyProvider("2026-05-01"),
        )

        try {
            database.dailyRecommendationDao().upsert(
                DailyRecommendationEntity(
                    dateKey = "2026-05-01",
                    generatedAt = 1L,
                    trackIds = "[]",
                ),
            )
            seedSource(database, sourceId = "local-1", type = "LOCAL_FOLDER", enabled = true)
            database.trackDao().upsertAll(
                listOf(
                    trackEntity("track-1", "local-1", "A Song", null, null),
                    trackEntity("track-2", "local-1", "B Song", null, null),
                ),
            )

            assertTrue(repository.ensureDailyRecommendation().isSuccess)
            val recommendation = database.dailyRecommendationDao().getByDateKey("2026-05-01")

            assertTrue(recommendation?.trackIds.orEmpty().contains("track-"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `daily recommendation overwrites cache when cached tracks are no longer visible`() = runTest {
        val database = createMyTestDatabase()
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(),
            httpClient = RecordingMyHttpClient(),
            dailyRecommendationDateKeyProvider = FakeDailyRecommendationDateKeyProvider("2026-05-01"),
        )

        try {
            seedSource(database, sourceId = "navidrome-1", type = "NAVIDROME", enabled = false)
            seedSource(database, sourceId = "samba-1", type = "SAMBA", enabled = true)
            database.trackDao().upsertAll(
                listOf(
                    trackEntity("track-navidrome", "navidrome-1", "Hidden Song", null, null),
                    trackEntity("track-samba", "samba-1", "Visible Song", null, null),
                ),
            )
            database.dailyRecommendationDao().upsert(
                DailyRecommendationEntity(
                    dateKey = "2026-05-01",
                    generatedAt = 1L,
                    trackIds = """["track-navidrome"]""",
                ),
            )

            assertEquals(emptyList(), repository.dailyRecommendation.first().map { it.id })

            assertTrue(repository.ensureDailyRecommendation().isSuccess)
            val recommendation = repository.dailyRecommendation.first()
            val recommendationRow = database.dailyRecommendationDao().getByDateKey("2026-05-01")

            assertEquals(listOf("track-samba"), recommendation.map { it.id })
            assertTrue(recommendationRow?.trackIds.orEmpty().contains("track-samba"))
            assertTrue(!recommendationRow?.trackIds.orEmpty().contains("track-navidrome"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `daily recommendation keeps cache when cached tracks are still visible`() = runTest {
        val database = createMyTestDatabase()
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(),
            httpClient = RecordingMyHttpClient(),
            dailyRecommendationDateKeyProvider = FakeDailyRecommendationDateKeyProvider("2026-05-01"),
        )

        try {
            seedSource(database, sourceId = "local-1", type = "LOCAL_FOLDER", enabled = true)
            database.trackDao().upsertAll(
                listOf(
                    trackEntity("track-visible", "local-1", "Visible Song", null, null),
                    trackEntity("track-other", "local-1", "Other Song", null, null),
                ),
            )
            database.dailyRecommendationDao().upsert(
                DailyRecommendationEntity(
                    dateKey = "2026-05-01",
                    generatedAt = 1L,
                    trackIds = """["track-visible"]""",
                ),
            )

            assertTrue(repository.ensureDailyRecommendation().isSuccess)
            val recommendationRow = database.dailyRecommendationDao().getByDateKey("2026-05-01")

            assertEquals(1L, recommendationRow?.generatedAt)
            assertEquals("""["track-visible"]""", recommendationRow?.trackIds)
        } finally {
            database.close()
        }
    }

    @Test
    fun `daily recommendation flow follows emitted date key changes`() = runTest {
        val database = createMyTestDatabase()
        val dateProvider = FakeDailyRecommendationDateKeyProvider("2026-05-01")
        val dateNotifier = FakeDailyRecommendationDateChangeNotifier("2026-05-01")
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(),
            httpClient = RecordingMyHttpClient(),
            dailyRecommendationDateKeyProvider = dateProvider,
            dailyRecommendationDateChangeNotifier = dateNotifier,
        )

        try {
            seedSource(database, sourceId = "local-1", type = "LOCAL_FOLDER", enabled = true)
            database.trackDao().upsertAll(
                listOf(
                    trackEntity("track-1", "local-1", "A Song", null, null),
                    trackEntity("track-2", "local-1", "B Song", null, null),
                ),
            )

            assertTrue(repository.ensureDailyRecommendation().isSuccess)
            assertTrue(repository.dailyRecommendation.first().isNotEmpty())

            dateProvider.dateKey = "2026-05-02"
            dateNotifier.dateKey = "2026-05-02"
            assertEquals(emptyList(), repository.dailyRecommendation.first().map { it.id })

            assertTrue(repository.ensureDailyRecommendation().isSuccess)
            assertTrue(repository.dailyRecommendation.first().isNotEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun `daily recommendation hides disabled sources from generated and cached rows`() = runTest {
        val database = createMyTestDatabase()
        val repository = RoomMyRepository(
            database = database,
            secureCredentialStore = MyCredentialStore(),
            httpClient = RecordingMyHttpClient(),
            dailyRecommendationDateKeyProvider = FakeDailyRecommendationDateKeyProvider("2026-05-01"),
        )

        try {
            seedSource(database, sourceId = "local-1", type = "LOCAL_FOLDER", enabled = true)
            seedSource(database, sourceId = "local-disabled", type = "LOCAL_FOLDER", enabled = false)
            database.trackDao().upsertAll(
                listOf(
                    trackEntity("track-enabled", "local-1", "Visible Song", null, null),
                    trackEntity("track-disabled", "local-disabled", "Hidden Song", null, null),
                ),
            )

            assertTrue(repository.ensureDailyRecommendation().isSuccess)
            val dailyRecommendation = repository.dailyRecommendation.first()

            assertEquals(listOf("track-enabled"), dailyRecommendation.map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun `daily recommendation ranking rewards liked and played tracks`() {
        val now = 10_000_000_000L
        val tracks = listOf(
            trackEntity("plain", "local-1", "Plain", null, null, addedAt = now - 90.daysMs()),
            trackEntity("liked", "local-1", "Liked", null, null, addedAt = now - 90.daysMs()),
            trackEntity("played", "local-1", "Played", null, null, addedAt = now - 90.daysMs()),
        )

        val ranked = rankDailyRecommendationTrackIds(
            tracks = tracks,
            favoriteTracks = listOf(favoriteEntity("liked")),
            trackStats = mapOf(
                "played" to TrackPlaybackStatsEntity(
                    trackId = "played",
                    sourceId = "local-1",
                    playCount = 20,
                    lastPlayedAt = now - 40.daysMs(),
                ),
            ),
            recentRecommendationTrackIds = emptySet(),
            dateKey = "2026-05-01",
            nowMs = now,
            limit = 3,
        )

        assertTrue(ranked.indexOf("liked") < ranked.indexOf("plain"))
        assertTrue(ranked.indexOf("played") < ranked.indexOf("plain"))
    }

    @Test
    fun `daily recommendation ranking penalizes recent recommendations`() {
        val now = 10_000_000_000L
        val tracks = listOf(
            trackEntity("fresh", "local-1", "Fresh", null, null, addedAt = now - 90.daysMs()),
            trackEntity("recently-recommended", "local-1", "Recent", null, null, addedAt = now - 90.daysMs()),
        )

        val ranked = rankDailyRecommendationTrackIds(
            tracks = tracks,
            favoriteTracks = listOf(favoriteEntity("recently-recommended")),
            trackStats = emptyMap(),
            recentRecommendationTrackIds = setOf("recently-recommended"),
            dateKey = "2026-05-01",
            nowMs = now,
            limit = 2,
        )

        assertEquals("fresh", ranked.first())
    }

    @Test
    fun `daily recommendation ranking avoids duplicate normalized titles when unique titles are available`() {
        val now = 10_000_000_000L
        val tracks = listOf(
            trackEntity("same-liked", "local-1", "Same Song", null, null, artistName = "Artist A", addedAt = now - 90.daysMs()),
            trackEntity("same-plain", "local-1", " same song ", null, null, artistName = "Artist B", addedAt = now - 90.daysMs()),
            trackEntity("unique-a", "local-1", "Unique A", null, null, artistName = "Artist C", addedAt = now - 90.daysMs()),
            trackEntity("unique-b", "local-1", "Unique B", null, null, artistName = "Artist D", addedAt = now - 90.daysMs()),
        )

        val ranked = rankDailyRecommendationTrackIds(
            tracks = tracks,
            favoriteTracks = listOf(favoriteEntity("same-liked")),
            trackStats = emptyMap(),
            recentRecommendationTrackIds = emptySet(),
            dateKey = "2026-05-01",
            nowMs = now,
            limit = 3,
        )

        assertEquals(3, ranked.size)
        assertTrue("same-liked" in ranked)
        assertTrue("same-plain" !in ranked)
        assertEquals(
            ranked.size,
            ranked.map { id -> tracks.first { it.id == id }.title.trim().lowercase() }.toSet().size,
        )
    }

    @Test
    fun `daily recommendation ranking allows duplicate titles when unique titles cannot fill limit`() {
        val now = 10_000_000_000L
        val tracks = listOf(
            trackEntity("same-a", "local-1", "Same Song", null, null, addedAt = now - 90.daysMs()),
            trackEntity("same-b", "local-1", "Same Song", null, null, addedAt = now - 90.daysMs()),
            trackEntity("same-c", "local-1", "Same Song", null, null, addedAt = now - 90.daysMs()),
        )

        val ranked = rankDailyRecommendationTrackIds(
            tracks = tracks,
            favoriteTracks = emptyList(),
            trackStats = emptyMap(),
            recentRecommendationTrackIds = emptySet(),
            dateKey = "2026-05-01",
            nowMs = now,
            limit = 3,
        )

        assertEquals(3, ranked.size)
        assertEquals(tracks.map { it.id }.toSet(), ranked.toSet())
    }
}

private fun createMyTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-my", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private suspend fun seedSource(
    database: LynMusicDatabase,
    sourceId: String,
    type: String,
    enabled: Boolean,
) {
    database.importSourceDao().upsert(
        ImportSourceEntity(
            id = sourceId,
            type = type,
            label = sourceId,
            rootReference = if (type == "NAVIDROME") "https://demo.example.com/navidrome" else "/music",
            server = null,
            shareName = null,
            directoryPath = null,
            username = if (type == "NAVIDROME") "demo" else null,
            credentialKey = if (type == "NAVIDROME") "nav-cred" else null,
            allowInsecureTls = false,
            enabled = enabled,
            lastScannedAt = null,
            createdAt = 1L,
        ),
    )
}

private fun albumEntity(
    albumId: String,
    title: String,
): AlbumEntity {
    return AlbumEntity(
        id = albumId,
        title = title,
        artistName = "Artist A",
        trackCount = 1,
    )
}

private fun trackEntity(
    trackId: String,
    sourceId: String,
    title: String,
    albumId: String?,
    albumTitle: String?,
    mediaLocator: String = "file:///music/$title.flac",
    artistName: String? = "Artist A",
    addedAt: Long = 0L,
): TrackEntity {
    return TrackEntity(
        id = trackId,
        sourceId = sourceId,
        title = title,
        artistId = null,
        artistName = artistName,
        albumId = albumId,
        albumTitle = albumTitle,
        durationMs = 180_000L,
        trackNumber = null,
        discNumber = null,
        mediaLocator = mediaLocator,
        relativePath = "Artist A/${albumTitle.orEmpty()}/$title.flac",
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
        addedAt = addedAt,
    )
}

private fun favoriteEntity(trackId: String): FavoriteTrackEntity {
    return FavoriteTrackEntity(
        trackId = trackId,
        sourceId = "local-1",
        remoteSongId = null,
        favoritedAt = 1L,
    )
}

private fun navidromeAlbumListBody(): String {
    return """
        {"subsonic-response":{"status":"ok","version":"1.16.1","albumList2":{"album":[
            {"id":"remote-album-1","played":"2026-04-30T10:00:00Z","playCount":4}
        ]}}}
    """.trimIndent()
}

private fun navidromeAlbumBody(): String {
    return """
        {"subsonic-response":{"status":"ok","version":"1.16.1","album":{
            "id":"remote-album-1",
            "played":"2026-04-30T10:00:00Z",
            "playCount":4,
            "song":[
                {"id":"song-1","title":"Server Song","played":"2026-04-30T10:05:00Z","playCount":7},
                {"id":"missing-song","title":"Missing Song","played":"2026-04-30T10:06:00Z","playCount":8}
            ]
        }}}
    """.trimIndent()
}

private fun playedAt(value: String): Long {
    return Instant.parse(value).toEpochMilliseconds()
}

private class RecordingMyHttpClient(
    private val albumListBody: String = """{"subsonic-response":{"status":"ok","version":"1.16.1","albumList2":{"album":[]}}}""",
    private val albumBody: String = """{"subsonic-response":{"status":"ok","version":"1.16.1","album":{"song":[]}}}""",
    private val failEndpoint: String? = null,
) : LyricsHttpClient {
    val requests = mutableListOf<LyricsRequest>()
    val requestedEndpoints = mutableListOf<String>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requests += request
        val endpoint = requireNotNull(parseUrl(request.url)).encodedPath.substringAfterLast('/')
        requestedEndpoints += endpoint
        if (endpoint == failEndpoint) {
            return Result.success(
                LyricsHttpResponse(
                    statusCode = 500,
                    body = """{"subsonic-response":{"status":"failed","version":"1.16.1"}}""",
                ),
            )
        }
        return Result.success(
            LyricsHttpResponse(
                statusCode = 200,
                body = when (endpoint) {
                    "getAlbumList2" -> albumListBody
                    "getAlbum" -> albumBody
                    else -> """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                },
            ),
        )
    }
}

private fun LyricsRequest.query(name: String): String? {
    return requireNotNull(parseUrl(url)).parameters[name]
}

private class MyCredentialStore(
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

private class FakeDailyRecommendationDateKeyProvider(
    var dateKey: String,
) : DailyRecommendationDateKeyProvider {
    override fun currentDateKey(): String = dateKey
}

private class FakeDailyRecommendationDateChangeNotifier(
    dateKey: String,
) : DailyRecommendationDateChangeNotifier {
    private val mutableDateKey = MutableStateFlow(dateKey)
    override val dateKeys = mutableDateKey.asStateFlow()

    var dateKey: String
        get() = mutableDateKey.value
        set(value) {
            mutableDateKey.value = value
        }

    override fun refreshCurrentDateKey() {
        mutableDateKey.value = dateKey
    }
}

private fun Int.daysMs(): Long = this.toLong() * 24L * 60L * 60L * 1_000L
