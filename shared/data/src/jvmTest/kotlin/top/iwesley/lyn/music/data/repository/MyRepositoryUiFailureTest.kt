package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.resources.*

import androidx.room.Room
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.db.*

class MyRepositoryUiFailureTest {
    @Test fun recentSyncFailureUsesSingleDescriptionAndSkipsDisabledSources() = runTest {
        val directory = Files.createTempDirectory("lyn-my-ui").toFile()
        val database = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = directory.resolve("test.db").absolutePath))
        var requests = 0
        val httpClient = object : LyricsHttpClient {
            override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
                requests++
                return Result.failure(IOException("服务器诊断原文"))
            }
        }
        val credentials = object : SecureCredentialStore {
            override suspend fun get(key: String): String = "secret"
            override suspend fun put(key: String, value: String) = Unit
            override suspend fun remove(key: String) = Unit
        }
        try {
            val source = ImportSourceEntity("enabled", "NAVIDROME", "用户来源名", "https://server.test", null, null, null, "user", "credential", false, true, null, 1L)
            database.importSourceDao().upsert(source)
            database.importSourceDao().upsert(source.copy(id = "disabled", enabled = false))
            val repository = RoomMyRepository(database, credentials, httpClient)
            val error = checkNotNull(repository.refreshNavidromeRecentPlays().exceptionOrNull())
            assertIs<UiTextFailure>(error)
            val text = error.uiErrorText(uiText(Res.string.recent_remote_sync_failed_fallback))
            assertEquals("Remote recent plays could not be synced. Local statistics are shown.", resolveUiText(text, AppLanguage.English))
            assertEquals("远程最近播放同步失败，已显示本地统计。", resolveUiText(text, AppLanguage.SimplifiedChinese))
            assertEquals("遠程最近播放同步失敗，已顯示本機統計。", resolveUiText(text, AppLanguage.TraditionalChinese))
            assertEquals(1, requests)
            assertEquals("用户来源名", database.importSourceDao().getById("enabled")?.label)
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }
}
