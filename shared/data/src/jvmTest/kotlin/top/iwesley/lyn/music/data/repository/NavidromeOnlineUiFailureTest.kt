package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.db.*

class NavidromeOnlineUiFailureTest {
    @Test fun missingOrMismatchedSongIdsFailBeforeRequestsOrStateWrites() = runTest {
        val path = Files.createTempFile("lynmusic-online-ui-failure", ".db")
        val database = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()))
        try {
            database.importSourceDao().upsert(ImportSourceEntity(
                id = "source", type = ImportSourceType.NAVIDROME.name, label = "用户来源",
                rootReference = "https://server.example", server = null, shareName = null, directoryPath = null,
                username = "user", credentialKey = "credential", allowInsecureTls = false,
                lastScannedAt = null, createdAt = 1L, indexMode = ImportSourceIndexMode.ONLINE.name,
            ))
            var requests = 0
            val client = object : LyricsHttpClient {
                override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
                    requests++
                    error("No request should be sent")
                }
            }
            val credentials = object : SecureCredentialStore {
                override suspend fun get(key: String): String = "password"
                override suspend fun put(key: String, value: String) = error("No credential write expected")
                override suspend fun remove(key: String) = error("No credential removal expected")
            }
            val repository = NavidromeOnlineRepository(database, credentials, client)
            val sourceBefore = database.importSourceDao().getById("source")
            val favoritesBefore = database.favoriteTrackDao().getAll()
            val playlistsBefore = database.playlistDao().getAll()
            val locators = listOf("file:///用户路径/song.flac", buildNavidromeSongLocator("other-source", "song"))
            for (locator in locators) {
                val track = Track("track", "source", "用户歌曲", mediaLocator = locator, relativePath = "用户路径/song.flac")
                val failures = listOf(
                    assertFailsWith<IllegalStateException> { repository.setFavorite("source", track, true) },
                    assertFailsWith<IllegalStateException> { repository.setFavorite("source", track, false) },
                    assertFailsWith<IllegalStateException> { repository.addTrackToPlaylist("source", "playlist", track) },
                )
                for (failure in failures) {
                    assertIs<UiTextFailure>(failure)
                    assertEquals("online_track_remote_id_missing", failure.message)
                    val wrapped = IllegalStateException("第三方包装原文", failure)
                    assertSame(failure, wrapped.cause)
                    for ((language, expected) in listOf(
                        AppLanguage.English to "The online song has no remote song ID.",
                        AppLanguage.SimplifiedChinese to "在线歌曲缺少远端 song id。",
                        AppLanguage.TraditionalChinese to "線上歌曲缺少遠端 song id。",
                        AppLanguage.English to "The online song has no remote song ID.",
                    )) assertEquals(expected, resolveUiText(wrapped.uiErrorDetail(), language))
                }
                assertEquals(locator, track.mediaLocator)
            }
            assertEquals(0, requests)
            assertEquals(sourceBefore, database.importSourceDao().getById("source"))
            assertEquals(favoritesBefore, database.favoriteTrackDao().getAll())
            assertEquals(playlistsBefore, database.playlistDao().getAll())
            assertEquals(0, database.trackDao().count())
        } finally {
            database.close()
            Files.deleteIfExists(path)
        }
    }
}
