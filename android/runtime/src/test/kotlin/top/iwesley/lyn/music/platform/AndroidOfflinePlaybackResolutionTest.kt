package top.iwesley.lyn.music.platform

import android.app.Application
import androidx.room.Room
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.OfflineDownloadEntity
import top.iwesley.lyn.music.resources.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidOfflinePlaybackResolutionTest {
    @Before
    @OptIn(ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
    }

    @Test fun missingFileSavesCurrentLanguageWhileExistingFileKeepsPlaybackTarget() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = File.createTempFile("lynmusic-offline-test", ".db", context.cacheDir)
        val database = Room.databaseBuilder<LynMusicDatabase>(context, databaseFile.absolutePath)
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        val audioFile = File.createTempFile("lynmusic-offline-test", ".mp3", context.cacheDir)
        audioFile.writeText("audio")
        val previous = AppLanguageRuntime.appLanguage.value
        val track = Track(id = "track-1", sourceId = "nav-source", title = "用户歌曲", mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"), relativePath = "Song.flac")
        fun row(path: String) = OfflineDownloadEntity(
            trackId = track.id, sourceId = track.sourceId, originalMediaLocator = track.mediaLocator,
            localMediaLocator = path, quality = NavidromeAudioQuality.Kbps192.name,
            status = OfflineDownloadStatus.Completed.name, downloadedBytes = 5, totalBytes = 5,
            updatedAt = 1, errorMessage = null,
        )
        try {
            database.offlineDownloadDao().upsert(row(audioFile.absolutePath))
            val target = assertNotNull(resolveAndroidOfflinePlaybackTarget(database, track))
            assertEquals(audioFile, target.file)
            assertEquals(NavidromeAudioQuality.Kbps192, target.quality)
            assertEquals(OfflineDownloadStatus.Completed.name, database.offlineDownloadDao().getByTrackId(track.id)?.status)
            for (language in listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)) {
                AppLanguageRuntime.update(language)
                database.offlineDownloadDao().upsert(row(audioFile.absolutePath + ".missing"))
                assertNull(resolveAndroidOfflinePlaybackTarget(database, track))
                val stored = database.offlineDownloadDao().getByTrackId(track.id)
                val expected = resolveUiText(uiText(Res.string.offline_file_missing), language)
                assertEquals(OfflineDownloadStatus.Failed.name, stored?.status)
                assertNull(stored?.localMediaLocator)
                assertEquals(expected, stored?.errorMessage)
                AppLanguageRuntime.update(AppLanguage.English)
                assertEquals(expected, database.offlineDownloadDao().getByTrackId(track.id)?.errorMessage)
            }
        } finally {
            database.close()
            audioFile.delete()
            databaseFile.delete()
            AppLanguageRuntime.update(previous)
        }
    }
}
