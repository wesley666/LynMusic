package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import io.ktor.http.encodedPath
import io.ktor.http.parseUrl
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.db.*

class NavidromeArtistDetailTest {
    @Test fun songsKeepDuetsExcludeAlbumOnlyAndComposerCreditsAndReadPastFilteredPages() = runTest {
        fixture { repository, client ->
            val songs = repository.artistTracks("source", "jay")
            assertEquals(listOf("Duet", "Solo"), songs.map { it.title })
            assertEquals(listOf(true, false), songs.map { it.remoteFavoriteHint })
            assertEquals("other", songs.first().artistId)
            assertEquals("album", songs.first().albumId)
            assertEquals("source", songs.first().sourceId)
            val pages = client.requests.filter { it.url.contains("/api/song?") }
            assertEquals(listOf("0", "200"), pages.map { parseUrl(it.url)!!.parameters["_start"] })
            assertTrue(pages.all { parseUrl(it.url)!!.parameters["artists_id"] == "jay" })
            assertTrue(pages.all { parseUrl(it.url)!!.parameters["_sort"] == "id" })
            assertEquals(1, client.requests.count { it.url.endsWith("/auth/login") })
            assertTrue(client.requests.none { it.url.contains("/rest/") || it.url.contains("/api/album") })
        }
    }

    @Test fun albumsPageIndependentlyMapNativeMetadataAndDoNotRequestSongs() = runTest {
        fixture { repository, client ->
            val albums = repository.artistAlbums("source", "jay")
            assertEquals(listOf("Compilation", "Main album"), albums.map { it.album.title })
            assertEquals(listOf("Various artists", "Jay"), albums.map { it.album.artistName })
            assertEquals(listOf(12, 8), albums.map { it.album.trackCount })
            assertEquals(buildSubsonicCompatibleCoverLocator(ImportSourceType.NAVIDROME, "source", "compilation"), albums.first().artworkLocator)
            val pages = client.requests.filter { it.url.contains("/api/album?") }
            assertEquals(listOf("0", "1"), pages.map { parseUrl(it.url)!!.parameters["_start"] })
            assertTrue(pages.all { parseUrl(it.url)!!.parameters["artist_id"] == "jay" })
            assertTrue(client.requests.none { it.url.contains("/api/song") || it.url.contains("getAlbum") || it.url.contains("getStarred") })
        }
    }

    @Test fun cancellationIsNotConvertedToAnEmptySuccessfulResult() = runTest {
        fixture { repository, client ->
            client.cancel = true
            assertFailsWith<CancellationException> { repository.artistTracks("source", "jay") }
            assertFailsWith<CancellationException> { repository.artistAlbums("source", "jay") }
        }
    }

    private suspend fun fixture(block: suspend (NavidromeOnlineRepository, ArtistHttpClient) -> Unit) {
        val path = Files.createTempFile("lynmusic-artist-detail", ".db")
        val database = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()))
        try {
            database.importSourceDao().upsert(ImportSourceEntity(
                id = "source", type = ImportSourceType.NAVIDROME.name, label = "Server", rootReference = "https://server.example",
                server = null, shareName = null, directoryPath = null, username = "user", credentialKey = "credential",
                allowInsecureTls = false, lastScannedAt = null, createdAt = 1L, indexMode = ImportSourceIndexMode.ONLINE.name,
            ))
            val credentials = object : SecureCredentialStore {
                override suspend fun get(key: String) = "password"
                override suspend fun put(key: String, value: String) = error("Unexpected write")
                override suspend fun remove(key: String) = error("Unexpected write")
            }
            val client = ArtistHttpClient()
            block(NavidromeOnlineRepository(database, credentials, client), client)
            assertEquals(0, database.trackDao().count())
        } finally {
            database.close()
            Files.deleteIfExists(path)
        }
    }

    private class ArtistHttpClient : LyricsHttpClient {
        val requests = mutableListOf<LyricsRequest>()
        var cancel = false
        override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
            requests += request
            if (cancel) throw CancellationException("cancelled")
            val url = parseUrl(request.url)!!
            if (url.encodedPath == "/auth/login") return Result.success(LyricsHttpResponse(200, """{"token":"test-token"}"""))
            assertEquals("Bearer test-token", request.headers["X-ND-Authorization"])
            assertEquals(IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS, request.timeoutMillis)
            val offset = url.parameters["_start"]!!.toInt()
            val response = when (url.encodedPath) {
                "/api/song" -> if (offset == 0) {
                    // This entire candidate page disappears after role filtering. It must not end paging.
                    val items = (0 until 200).joinToString(",") { index ->
                        """{"id":"candidate-$index","participants":{"albumartist":[{"id":"jay"}],"composer":[{"id":"jay"}],"artist":[{"id":"other"}]}}"""
                    }
                    LyricsHttpResponse(200, "[$items]", headers = mapOf("X-Total-Count" to "203"))
                } else LyricsHttpResponse(200, """[
                    {"id":"duet","title":"Duet","artistId":"other","artist":"Other & Jay","albumId":"album","starred":true,"participants":{"artist":[{"id":"other"},{"id":"jay"}]}},
                    {"id":"solo","title":"Solo","artistId":"jay","participants":{"artist":[{"id":"jay"}]}},
                    {"id":"duet","title":"Duet","artistId":"other","albumId":"album","starred":true,"participants":{"artist":[{"id":"jay"}]}}
                ]""", headers = mapOf("X-Total-Count" to "203"))
                "/api/album" -> if (offset == 0) LyricsHttpResponse(200,
                    """[{"id":"main","name":"Main album","albumArtist":"Jay","songCount":8}]""", headers = mapOf("X-Total-Count" to "2"))
                else LyricsHttpResponse(200, """[{"id":"compilation","name":"Compilation","albumArtist":"Various artists","songCount":12}]""", headers = mapOf("X-Total-Count" to "2"))
                else -> error("Unexpected request: ${url.encodedPath}")
            }
            return Result.success(response)
        }
    }
}
