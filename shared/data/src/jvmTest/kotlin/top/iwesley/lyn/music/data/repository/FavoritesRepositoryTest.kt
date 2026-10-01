package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*

import androidx.room.Room
import io.ktor.http.parseUrl
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.EmbyCredential
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.buildSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.buildEmbySongLocator
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.FavoriteTrackEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.serializeEmbyCredential

class FavoritesRepositoryTest {

    @Test
    fun `local source toggle writes and deletes favorite rows`() = runTest {
        val database = createTestDatabase()
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(),
            httpClient = RecordingFavoritesHttpClient(),
            logger = NoopDiagnosticLogger,
        )
        val track = localTrack()

        val favoriteResult = repository.toggleFavorite(track)
        assertEquals(true, favoriteResult.getOrThrow())
        assertNotNull(database.favoriteTrackDao().getByTrackId(track.id))

        val unfavoriteResult = repository.toggleFavorite(track)
        assertEquals(false, unfavoriteResult.getOrThrow())
        assertNull(database.favoriteTrackDao().getByTrackId(track.id))
    }

    @Test
    fun `navidrome refresh maps starred songs to local track ids and prefers remote timestamps for existing favorites`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        database.trackDao().upsertAll(
            listOf(
                navidromeTrackEntity(songId = "song-1"),
                navidromeTrackEntity(songId = "song-2"),
            ),
        )
        database.favoriteTrackDao().upsert(
            FavoriteTrackEntity(
                trackId = navidromeTrackIdFor("nav-source", "song-1"),
                sourceId = "nav-source",
                remoteSongId = "song-1",
                favoritedAt = 123L,
            ),
        )
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            httpClient = RecordingFavoritesHttpClient(
                starredSongIds = listOf("song-1", "song-2"),
                starredTimesBySongId = mapOf(
                    "song-1" to "2026-04-06T09:08:31.500488808Z",
                    "song-2" to "2026-04-06T09:09:31.500488808Z",
                ),
            ),
            logger = NoopDiagnosticLogger,
        )

        repository.refreshNavidromeFavorites().getOrThrow()

        val rowsByRemoteSongId = database.favoriteTrackDao().getBySourceId("nav-source")
            .associateBy { it.remoteSongId }
        assertEquals(
            setOf(
                navidromeTrackIdFor("nav-source", "song-1"),
                navidromeTrackIdFor("nav-source", "song-2"),
            ),
            rowsByRemoteSongId.values.mapTo(linkedSetOf()) { it.trackId },
        )
        assertEquals(
            Instant.parse("2026-04-06T09:08:31.500488808Z").toEpochMilliseconds(),
            rowsByRemoteSongId.getValue("song-1").favoritedAt,
        )
        assertEquals(
            listOf(
                navidromeTrackIdFor("nav-source", "song-2"),
                navidromeTrackIdFor("nav-source", "song-1"),
            ),
            repository.favoriteTracks.first().map { it.id },
        )
    }

    @Test
    fun `navidrome refresh keeps existing timestamp when remote starred is missing`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        database.trackDao().upsertAll(
            listOf(
                navidromeTrackEntity(songId = "song-1"),
                navidromeTrackEntity(songId = "song-2"),
            ),
        )
        database.favoriteTrackDao().upsert(
            FavoriteTrackEntity(
                trackId = navidromeTrackIdFor("nav-source", "song-1"),
                sourceId = "nav-source",
                remoteSongId = "song-1",
                favoritedAt = 123L,
            ),
        )
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            httpClient = RecordingFavoritesHttpClient(starredSongIds = listOf("song-1", "song-2")),
            logger = NoopDiagnosticLogger,
        )

        repository.refreshNavidromeFavorites().getOrThrow()

        val rowsByRemoteSongId = database.favoriteTrackDao().getBySourceId("nav-source").associateBy { it.remoteSongId }
        assertEquals(123L, rowsByRemoteSongId.getValue("song-1").favoritedAt)
    }

    @Test
    fun `first navidrome refresh preserves returned order for new favorites`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        database.trackDao().upsertAll(
            listOf(
                navidromeTrackEntity(songId = "song-1"),
                navidromeTrackEntity(songId = "song-2"),
                navidromeTrackEntity(songId = "song-3"),
            ),
        )
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            httpClient = RecordingFavoritesHttpClient(starredSongIds = listOf("song-2", "song-3", "song-1")),
            logger = NoopDiagnosticLogger,
        )

        repository.refreshNavidromeFavorites().getOrThrow()

        assertEquals(
            listOf(
                navidromeTrackIdFor("nav-source", "song-2"),
                navidromeTrackIdFor("nav-source", "song-3"),
                navidromeTrackIdFor("nav-source", "song-1"),
            ),
            repository.favoriteTracks.first().map { it.id },
        )
    }

    @Test
    fun `navidrome refresh uses remote starred timestamp when available`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        database.trackDao().upsertAll(
            listOf(
                navidromeTrackEntity(songId = "song-1"),
                navidromeTrackEntity(songId = "song-2"),
            ),
        )
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            httpClient = RecordingFavoritesHttpClient(
                starredSongIds = listOf("song-2", "song-1"),
                starredTimesBySongId = mapOf(
                    "song-2" to "2026-04-06T09:09:31.500488808Z",
                    "song-1" to "2026-04-06T09:08:31.500488808Z",
                ),
            ),
            logger = NoopDiagnosticLogger,
        )

        repository.refreshNavidromeFavorites().getOrThrow()

        val rowsByRemoteSongId = database.favoriteTrackDao().getBySourceId("nav-source").associateBy { it.remoteSongId }
        assertEquals(
            Instant.parse("2026-04-06T09:09:31.500488808Z").toEpochMilliseconds(),
            rowsByRemoteSongId.getValue("song-2").favoritedAt,
        )
        assertEquals(
            Instant.parse("2026-04-06T09:08:31.500488808Z").toEpochMilliseconds(),
            rowsByRemoteSongId.getValue("song-1").favoritedAt,
        )
        assertEquals(
            listOf(
                navidromeTrackIdFor("nav-source", "song-2"),
                navidromeTrackIdFor("nav-source", "song-1"),
            ),
            repository.favoriteTracks.first().map { it.id },
        )
    }

    @Test
    fun `later navidrome refresh puts new favorites first in returned order`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        database.trackDao().upsertAll(
            listOf(
                navidromeTrackEntity(songId = "song-1"),
                navidromeTrackEntity(songId = "song-2"),
                navidromeTrackEntity(songId = "song-3"),
                navidromeTrackEntity(songId = "song-4"),
            ),
        )
        database.favoriteTrackDao().upsertAll(
            listOf(
                FavoriteTrackEntity(
                    trackId = navidromeTrackIdFor("nav-source", "song-1"),
                    sourceId = "nav-source",
                    remoteSongId = "song-1",
                    favoritedAt = 500L,
                ),
                FavoriteTrackEntity(
                    trackId = navidromeTrackIdFor("nav-source", "song-2"),
                    sourceId = "nav-source",
                    remoteSongId = "song-2",
                    favoritedAt = 499L,
                ),
            ),
        )
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            httpClient = RecordingFavoritesHttpClient(starredSongIds = listOf("song-4", "song-3", "song-1", "song-2")),
            logger = NoopDiagnosticLogger,
        )

        repository.refreshNavidromeFavorites().getOrThrow()

        val rowsByRemoteSongId = database.favoriteTrackDao().getBySourceId("nav-source").associateBy { it.remoteSongId }
        assertEquals(500L, rowsByRemoteSongId.getValue("song-1").favoritedAt)
        assertEquals(499L, rowsByRemoteSongId.getValue("song-2").favoritedAt)
        assertEquals(
            listOf(
                navidromeTrackIdFor("nav-source", "song-4"),
                navidromeTrackIdFor("nav-source", "song-3"),
                navidromeTrackIdFor("nav-source", "song-1"),
                navidromeTrackIdFor("nav-source", "song-2"),
            ),
            repository.favoriteTracks.first().map { it.id },
        )
    }

    @Test
    fun `navidrome toggle favorite updates local cache only after remote success`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val httpClient = RecordingFavoritesHttpClient()
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )
        val track = navidromeTrack(songId = "song-7")

        assertEquals(true, repository.toggleFavorite(track).getOrThrow())
        assertEquals(listOf("star"), httpClient.requestedEndpoints)
        assertEquals("song-7", database.favoriteTrackDao().getByTrackId(track.id)?.remoteSongId)

        assertEquals(false, repository.toggleFavorite(track).getOrThrow())
        assertEquals(listOf("star", "unstar"), httpClient.requestedEndpoints)
        assertNull(database.favoriteTrackDao().getByTrackId(track.id))
    }

    @Test
    fun `online navidrome toggle favorite does not write local favorite rows`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database, indexMode = "ONLINE")
        val httpClient = RecordingFavoritesHttpClient()
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )
        val track = navidromeTrack(songId = "song-online")

        val result = repository.toggleFavorite(track)

        assertEquals(true, result.isFailure)
        assertEquals(emptyList(), httpClient.requestedEndpoints)
        assertNull(database.favoriteTrackDao().getByTrackId(track.id))
    }

    @Test
    fun `set favorite true is idempotent and keeps liked song liked`() = runTest {
        val database = createTestDatabase()
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(),
            httpClient = RecordingFavoritesHttpClient(),
            logger = NoopDiagnosticLogger,
        )
        val track = localTrack()

        assertEquals(true, repository.setFavorite(track, favorite = true).getOrThrow())
        assertEquals(true, repository.setFavorite(track, favorite = true).getOrThrow())

        assertNotNull(database.favoriteTrackDao().getByTrackId(track.id))
    }

    @Test
    fun `navidrome toggle failure keeps local cache unchanged`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            httpClient = RecordingFavoritesHttpClient(failingEndpoints = setOf("star")),
            logger = NoopDiagnosticLogger,
        )
        val track = navidromeTrack(songId = "song-8")

        val result = repository.toggleFavorite(track)

        assertEquals(true, result.isFailure)
        assertNull(database.favoriteTrackDao().getByTrackId(track.id))
    }

    @Test
    fun `emby toggle favorite writes remote before updating local cache`() = runTest {
        val database = createTestDatabase()
        seedEmbySource(database)
        val httpClient = RecordingEmbyFavoritesHttpClient()
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(
                mutableMapOf("emby-cred" to serializeEmbyCredential(EmbyCredential("user-1", "token"))),
            ),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )
        val track = embyTrack(itemId = "song-8")

        assertEquals(true, repository.toggleFavorite(track).getOrThrow())
        assertEquals("song-8", database.favoriteTrackDao().getByTrackId(track.id)?.remoteSongId)

        assertEquals(false, repository.toggleFavorite(track).getOrThrow())
        assertNull(database.favoriteTrackDao().getByTrackId(track.id))
        assertEquals(
            listOf(
                "/emby/Users/user-1/FavoriteItems/song-8",
                "/emby/Users/user-1/FavoriteItems/song-8/Delete",
            ),
            httpClient.requestPaths,
        )
    }

    @Test
    fun `emby refresh maps favorite items to local track ids`() = runTest {
        val database = createTestDatabase()
        seedEmbySource(database)
        database.trackDao().upsertAll(
            listOf(
                embyTrackEntity(itemId = "song-1"),
                embyTrackEntity(itemId = "song-2"),
            ),
        )
        val repository = RoomFavoritesRepository(
            database = database,
            secureCredentialStore = MapSecureCredentialStore(
                mutableMapOf("emby-cred" to serializeEmbyCredential(EmbyCredential("user-1", "token"))),
            ),
            httpClient = RecordingEmbyFavoritesHttpClient(favoriteItemIds = listOf("song-2", "song-1")),
            logger = NoopDiagnosticLogger,
        )

        repository.refreshNavidromeFavorites().getOrThrow()

        assertEquals(
            setOf(
                embyTrackIdFor("emby-source", "song-1"),
                embyTrackIdFor("emby-source", "song-2"),
            ),
            database.favoriteTrackDao().getBySourceId("emby-source").mapTo(linkedSetOf()) { it.trackId },
        )
        assertEquals(
            listOf(
                embyTrackIdFor("emby-source", "song-2"),
                embyTrackIdFor("emby-source", "song-1"),
            ),
            repository.favoriteTracks.first().map { it.id },
        )
    }

    @Test
    fun `source deletion and local rescan prune invalid favorites`() = runTest {
        val database = createTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(listOf(localTrackEntity()))
        database.favoriteTrackDao().upsert(
            FavoriteTrackEntity(
                trackId = "track:local-1:artist a/morning light.mp3",
                sourceId = "local-1",
                remoteSongId = null,
                favoritedAt = 1L,
            ),
        )
        val repository = RoomImportSourceRepository(
            database = database,
            gateway = FakeImportSourceGateway(localFolderReport = ImportScanReport(tracks = emptyList())),
            secureCredentialStore = MapSecureCredentialStore(),
        )

        repository.rescanSource("local-1").getOrThrow()
        assertEquals(emptyList(), database.favoriteTrackDao().getBySourceId("local-1"))

        database.importSourceDao().upsert(localSourceEntity(sourceId = "local-2"))
        database.favoriteTrackDao().upsert(
            FavoriteTrackEntity(
                trackId = "track:local-2:artist a/morning light.mp3",
                sourceId = "local-2",
                remoteSongId = null,
                favoritedAt = 2L,
            ),
        )

        repository.deleteSource("local-2").getOrThrow()
        assertEquals(emptyList(), database.favoriteTrackDao().getBySourceId("local-2"))
    }
}

class FavoritesRepositoryUiTextTest {
    @Test fun multipleHttpFailuresKeepSourceOrderAndSuccessfulFavorites() = runTest {
        val database = createTestDatabase()
        try {
            seedNavidromeSource(database)
            seedEmbySource(database)
            val nav = requireNotNull(database.importSourceDao().getById("nav-source"))
            val emby = requireNotNull(database.importSourceDao().getById("emby-source"))
            database.importSourceDao().upsert(nav.copy(label = "用户甲", createdAt = 4L))
            database.importSourceDao().upsert(nav.copy(id = "sub-source", type = "SUBSONIC", label = "用户乙", createdAt = 3L))
            database.importSourceDao().upsert(emby.copy(label = "用户丙", createdAt = 2L))
            database.importSourceDao().upsert(nav.copy(id = "healthy", username = "healthy", createdAt = 1L))
            val requests = mutableListOf<LyricsRequest>()
            val healthyClient = RecordingFavoritesHttpClient(starredSongIds = listOf("song-good"))
            val client = object : LyricsHttpClient {
                override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
                    requests += request
                    return if (parseUrl(request.url)?.parameters?.get("u") == "healthy") {
                        healthyClient.request(request)
                    } else Result.success(LyricsHttpResponse(503, ""))
                }
            }
            val repository = RoomFavoritesRepository(database, MapSecureCredentialStore(mutableMapOf(
                "nav-cred" to "pass",
                "emby-cred" to serializeEmbyCredential(EmbyCredential("user-1", "token")),
            )), client)
            val error = assertNotNull(repository.refreshNavidromeFavorites().exceptionOrNull())
            val text = assertNotNull(error.uiFailureTextOrNull())
            val rows = database.favoriteTrackDao().getBySourceId("healthy")
            assertEquals(listOf("song-good"), rows.map { it.remoteSongId })
            val requestCount = requests.size
            listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese, AppLanguage.English).forEach { language ->
                val lines = resolveUiText(text, language).lines()
                assertEquals(listOf("用户甲", "用户乙", "用户丙"), lines.map { it.substringBefore(if (language == AppLanguage.English) ": " else "：") })
                assertTrue(lines.all { "HTTP 503" in it })
                if (language == AppLanguage.English) assertTrue(lines.all { "failed" in it })
                if (language == AppLanguage.TraditionalChinese) assertTrue(lines.all { "失敗" in it })
            }
            assertEquals(requestCount, requests.size)
            assertEquals(rows, database.favoriteTrackDao().getBySourceId("healthy"))
        } finally { database.close() }
    }

    @Test fun aggregationKeepsWrappedDescriptionsAndRawExternalDetails() = runTest {
        val database = createTestDatabase()
        try {
            seedNavidromeSource(database)
            seedEmbySource(database)
            val nav = requireNotNull(database.importSourceDao().getById("nav-source"))
            database.importSourceDao().upsert(nav.copy(label = "用户来源", createdAt = 2L))
            val rawDetail = "服务端原文 %1\$s /用户路径"
            val described = uiText(Res.string.server_http_failed, "Navidrome", "getStarred2", 503)
            val credentials = object : SecureCredentialStore by MapSecureCredentialStore() {
                override suspend fun get(key: String): String? = when (key) {
                    "nav-cred" -> throw IllegalStateException("wrapper", UiTextException(described))
                    else -> throw IllegalStateException(rawDetail)
                }
            }
            val client = RecordingFavoritesHttpClient()
            val repository = RoomFavoritesRepository(database, credentials, client)
            val text = assertNotNull(repository.refreshNavidromeFavorites().exceptionOrNull()?.uiFailureTextOrNull())
            val expected = listOf(
                AppLanguage.English to "用户来源: Navidrome getStarred2 failed, HTTP 503\nEmby: $rawDetail",
                AppLanguage.SimplifiedChinese to "用户来源：Navidrome getStarred2 失败，HTTP 503\nEmby：$rawDetail",
                AppLanguage.TraditionalChinese to "用户来源：Navidrome getStarred2 失敗，HTTP 503\nEmby：$rawDetail",
                AppLanguage.English to "用户来源: Navidrome getStarred2 failed, HTTP 503\nEmby: $rawDetail",
            )
            expected.forEach { (language, value) -> assertEquals(value, resolveUiText(text, language)) }
            assertTrue(client.requestedEndpoints.isEmpty())
        } finally { database.close() }
    }

    @Test fun missingCredentialsRemainDescribedForAllRemoteTypes() = runTest {
        val database = createTestDatabase()
        try {
            seedNavidromeSource(database)
            seedEmbySource(database)
            val nav = requireNotNull(database.importSourceDao().getById("nav-source"))
            database.importSourceDao().upsert(nav.copy(id = "sub-source", type = "SUBSONIC"))
            val client = RecordingFavoritesHttpClient()
            val repository = RoomFavoritesRepository(database, MapSecureCredentialStore(), client)
            val text = assertNotNull(repository.refreshNavidromeFavorites().exceptionOrNull()?.uiFailureTextOrNull()) as UiText.Joined
            assertEquals(3, text.items.size)
            text.items.forEach { item ->
                val detail = (item as UiText.StringRef).arguments[1] as UiText.StringRef
                assertEquals("source_credentials_missing", detail.resource.key)
            }
            assertTrue(resolveUiText(text, AppLanguage.English).lines().all { "no valid credentials" in it })
            assertTrue(resolveUiText(text, AppLanguage.TraditionalChinese).lines().all { "有效憑證" in it })
            assertTrue(client.requestedEndpoints.isEmpty())
        } finally { database.close() }
    }

    @Test fun disabledSourcesRejectBothFavoriteChangesWithoutRequestsOrWrites() = runTest {
        val database = createTestDatabase()
        try {
            seedNavidromeSource(database)
            seedEmbySource(database)
            val nav = requireNotNull(database.importSourceDao().getById("nav-source"))
            val emby = requireNotNull(database.importSourceDao().getById("emby-source"))
            database.importSourceDao().upsert(nav.copy(enabled = false))
            database.importSourceDao().upsert(emby.copy(enabled = false))
            database.importSourceDao().upsert(nav.copy(id = "sub-source", type = "SUBSONIC", enabled = false))
            val subTrack = navidromeTrack("song-8").copy(
                id = "sub-track", sourceId = "sub-source",
                mediaLocator = buildSubsonicCompatibleSongLocator(ImportSourceType.SUBSONIC, "sub-source", "song-8"),
            )
            val client = RecordingFavoritesHttpClient()
            val repository = RoomFavoritesRepository(database, MapSecureCredentialStore(), client)
            listOf(navidromeTrack("song-8") to "Subsonic-compatible", subTrack to "Subsonic-compatible", embyTrack("song-8") to "Emby").forEach { (track, service) ->
                val addError = assertNotNull(repository.toggleFavorite(track).exceptionOrNull())
                assertNull(database.favoriteTrackDao().getByTrackId(track.id))
                val row = FavoriteTrackEntity(track.id, track.sourceId, "song-8", 123L)
                database.favoriteTrackDao().upsert(row)
                val removeError = assertNotNull(repository.toggleFavorite(track).exceptionOrNull())
                assertEquals(row, database.favoriteTrackDao().getByTrackId(track.id))
                listOf(addError, removeError).forEach { error ->
                    val text = assertNotNull(error.uiFailureTextOrNull())
                    assertEquals("The $service source is unavailable. Cannot update favorites.", resolveUiText(text, AppLanguage.English))
                    assertEquals("$service 來源不可用，無法更新喜歡狀態。", resolveUiText(text, AppLanguage.TraditionalChinese))
                    assertEquals("$service 来源不可用，无法更新喜欢状态。", resolveUiText(text, AppLanguage.SimplifiedChinese))
                }
            }
            assertTrue(client.requestedEndpoints.isEmpty())
        } finally { database.close() }
    }
}

private fun createTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-favorites", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private suspend fun seedNavidromeSource(
    database: LynMusicDatabase,
    indexMode: String = "LOCAL_INDEX",
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
            credentialKey = "nav-cred",
            allowInsecureTls = false,
            lastScannedAt = null,
            createdAt = 1L,
            indexMode = indexMode,
        ),
    )
}

private suspend fun seedEmbySource(database: LynMusicDatabase) {
    database.importSourceDao().upsert(
        ImportSourceEntity(
            id = "emby-source",
            type = "EMBY",
            label = "Emby",
            rootReference = "https://emby.example.com/emby",
            server = null,
            shareName = null,
            directoryPath = null,
            username = "demo",
            credentialKey = "emby-cred",
            allowInsecureTls = false,
            lastScannedAt = null,
            createdAt = 1L,
        ),
    )
}

private fun localSourceEntity(sourceId: String = "local-1"): ImportSourceEntity {
    return ImportSourceEntity(
        id = sourceId,
        type = "LOCAL_FOLDER",
        label = "下载目录",
        rootReference = "folder://downloads",
        server = null,
        shareName = null,
        directoryPath = null,
        username = null,
        credentialKey = null,
        allowInsecureTls = false,
        lastScannedAt = null,
        createdAt = 1L,
    )
}

private fun localTrack(): Track {
    return Track(
        id = "track:local-1:artist a/morning light.mp3",
        sourceId = "local-1",
        title = "Morning Light",
        artistName = "Artist A",
        albumTitle = "Album One",
        durationMs = 210_000L,
        mediaLocator = "file:///music/morning-light.mp3",
        relativePath = "Artist A/Morning Light.mp3",
    )
}

private fun localTrackEntity(): TrackEntity {
    return TrackEntity(
        id = "track:local-1:artist a/morning light.mp3",
        sourceId = "local-1",
        title = "Morning Light",
        artistId = "artist:artist a",
        artistName = "Artist A",
        albumId = "album:artist a:album one",
        albumTitle = "Album One",
        durationMs = 210_000L,
        trackNumber = 1,
        discNumber = 1,
        mediaLocator = "file:///music/morning-light.mp3",
        relativePath = "Artist A/Morning Light.mp3",
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
    )
}

private fun embyTrack(itemId: String): Track {
    return Track(
        id = embyTrackIdFor("emby-source", itemId),
        sourceId = "emby-source",
        title = "Emby $itemId",
        artistName = "Artist E",
        albumTitle = "Album E",
        durationMs = 215_000L,
        mediaLocator = buildEmbySongLocator("emby-source", itemId),
        relativePath = "Artist E/Album E/Emby $itemId.flac",
    )
}

private fun embyTrackEntity(itemId: String): TrackEntity {
    return TrackEntity(
        id = embyTrackIdFor("emby-source", itemId),
        sourceId = "emby-source",
        title = "Emby $itemId",
        artistId = "artist:artist e",
        artistName = "Artist E",
        albumId = "album:artist e:album e",
        albumTitle = "Album E",
        durationMs = 215_000L,
        trackNumber = 1,
        discNumber = 1,
        mediaLocator = buildEmbySongLocator("emby-source", itemId),
        relativePath = "Artist E/Album E/Emby $itemId.flac",
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
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

private fun navidromeTrackEntity(songId: String): TrackEntity {
    return TrackEntity(
        id = navidromeTrackIdFor("nav-source", songId),
        sourceId = "nav-source",
        title = "Blue $songId",
        artistId = "artist:artist b",
        artistName = "Artist B",
        albumId = "album:artist b:album b",
        albumTitle = "Album B",
        durationMs = 215_000L,
        trackNumber = 1,
        discNumber = 1,
        mediaLocator = buildNavidromeSongLocator("nav-source", songId),
        relativePath = "Artist B/Album B/Blue $songId.flac",
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
    )
}

private class RecordingFavoritesHttpClient(
    private val starredSongIds: List<String> = emptyList(),
    private val starredTimesBySongId: Map<String, String> = emptyMap(),
    private val failingEndpoints: Set<String> = emptySet(),
) : LyricsHttpClient {
    val requestedEndpoints = mutableListOf<String>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val endpoint = requireNotNull(parseUrl(request.url)).encodedPath.substringAfterLast('/')
        requestedEndpoints += endpoint
        if (endpoint in failingEndpoints) {
            return Result.success(
                LyricsHttpResponse(
                    statusCode = 500,
                    body = """{"subsonic-response":{"status":"failed","version":"1.16.1"}}""",
                ),
            )
        }
        val body = when (endpoint) {
            "getStarred2" -> starredBody(starredSongIds)
            "star", "unstar" -> """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
            else -> error("Unexpected request endpoint: $endpoint")
        }
        return Result.success(LyricsHttpResponse(statusCode = 200, body = body))
    }

    private fun starredBody(songIds: List<String>): String {
        val songs = songIds.joinToString(",") { songId ->
            val starred = starredTimesBySongId[songId]
                ?.let { ""","starred":"$it"""" }
                .orEmpty()
            """{"id":"$songId"$starred}"""
        }
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "starred2": {
                  "song": [$songs]
                }
              }
            }
        """.trimIndent()
    }
}

private class RecordingEmbyFavoritesHttpClient(
    private val favoriteItemIds: List<String> = emptyList(),
) : LyricsHttpClient {
    val requestPaths = mutableListOf<String>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        requestPaths += url.encodedPath
        val body = when {
            url.encodedPath.endsWith("/FavoriteItems/song-8") -> ""
            url.encodedPath.endsWith("/FavoriteItems/song-8/Delete") -> ""
            url.encodedPath.endsWith("/Users/user-1/Items") -> favoriteItemsBody()
            else -> error("Unexpected Emby request: ${request.method} ${request.url}")
        }
        return Result.success(LyricsHttpResponse(statusCode = 200, body = body))
    }

    private fun favoriteItemsBody(): String {
        val items = favoriteItemIds.joinToString(",") { itemId -> """{"Id":"$itemId"}""" }
        return """{"Items":[$items],"TotalRecordCount":${favoriteItemIds.size}}"""
    }
}

private class FakeImportSourceGateway(
    private val localFolderReport: ImportScanReport,
) : ImportSourceGateway {
    override suspend fun pickLocalFolder(): LocalFolderSelection? = null

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        return localFolderReport
    }

    override suspend fun testSamba(draft: SambaSourceDraft) {
        error("Unexpected Samba test")
    }

    override suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport {
        error("Unexpected Samba scan")
    }

    override suspend fun testWebDav(draft: WebDavSourceDraft) {
        error("Unexpected WebDAV test")
    }

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        error("Unexpected WebDAV scan")
    }

    override suspend fun testNavidrome(draft: NavidromeSourceDraft) {
        error("Unexpected Navidrome test")
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        error("Unexpected Navidrome scan")
    }
}

private class MapSecureCredentialStore(
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
