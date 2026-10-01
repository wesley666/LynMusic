package top.iwesley.lyn.music

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.OfflineDownloadEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.platform.resolveJvmOfflinePlaybackPath
import top.iwesley.lyn.music.resources.*

class JvmOfflinePlaybackResolutionTest {
    @Test
    fun `completed offline file is preferred for playback`() = runTest {
        val database = createOfflinePlaybackTestDatabase()
        val offlineFile = Files.createTempFile("lynmusic-offline-playback", ".mp3")
        offlineFile.writeText("audio")
        database.offlineDownloadDao().upsert(
            offlineRow(localMediaLocator = offlineFile.absolutePathString()),
        )

        val resolved = resolveJvmOfflinePlaybackPath(database, navidromeTrack())

        assertEquals(offlineFile.absolutePathString(), resolved)
    }

    @Test
    fun `missing offline file is marked failed and playback falls back to remote`() = runTest {
        val database = createOfflinePlaybackTestDatabase()
        val previous = AppLanguageRuntime.appLanguage.value
        val directory = Files.createTempDirectory("lynmusic-offline-playback")
        try {
            val missingPath = directory.resolve("missing.mp3").absolutePathString()
            for (language in listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)) {
                AppLanguageRuntime.update(language)
                database.offlineDownloadDao().upsert(offlineRow(localMediaLocator = missingPath))
                assertNull(resolveJvmOfflinePlaybackPath(database, navidromeTrack()))
                val row = database.offlineDownloadDao().getByTrackId("track-1")
                assertEquals(OfflineDownloadStatus.Failed.name, row?.status)
                assertNull(row?.localMediaLocator)
                val expected = resolveUiText(uiText(Res.string.offline_file_missing), language)
                assertEquals(expected, row?.errorMessage)
                AppLanguageRuntime.update(AppLanguage.English)
                assertEquals(expected, database.offlineDownloadDao().getByTrackId("track-1")?.errorMessage)
            }
        } finally {
            database.close()
            Files.deleteIfExists(directory)
            AppLanguageRuntime.update(previous)
        }
    }
}

private fun createOfflinePlaybackTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-offline-playback", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private fun navidromeTrack(): Track {
    return Track(
        id = "track-1",
        sourceId = "nav-source",
        title = "Song",
        mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
        relativePath = "Song.flac",
    )
}

private fun offlineRow(localMediaLocator: String): OfflineDownloadEntity {
    return OfflineDownloadEntity(
        trackId = "track-1",
        sourceId = "nav-source",
        originalMediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
        localMediaLocator = localMediaLocator,
        quality = NavidromeAudioQuality.Original.name,
        status = OfflineDownloadStatus.Completed.name,
        downloadedBytes = 4L,
        totalBytes = 4L,
        updatedAt = 1L,
        errorMessage = null,
    )
}
