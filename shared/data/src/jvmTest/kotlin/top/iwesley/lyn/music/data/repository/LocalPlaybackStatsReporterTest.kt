package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

class LocalPlaybackStatsReporterTest {

    @Test
    fun `submit play records track and album stats`() = runTest {
        val database = createTestDatabase()
        val reporter = LocalPlaybackStatsReporter(database)

        try {
            database.trackDao().upsertAll(
                listOf(localTrackEntity(trackId = "track-1", albumId = "album-1")),
            )

            reporter.submitPlay(localTrack("track-1"), atMillis = 1_000L)

            val trackStats = database.trackPlaybackStatsDao().getByTrackId("track-1")
            val albumStats = database.albumPlaybackStatsDao().getByAlbumId("album-1")
            assertEquals(1, trackStats?.playCount)
            assertEquals("local-1", trackStats?.sourceId)
            assertEquals(1_000L, trackStats?.lastPlayedAt)
            assertEquals(1, albumStats?.playCount)
            assertEquals(1_000L, albumStats?.lastPlayedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun `submit play increments existing stats and updates last played time`() = runTest {
        val database = createTestDatabase()
        val reporter = LocalPlaybackStatsReporter(database)

        try {
            database.trackDao().upsertAll(
                listOf(localTrackEntity(trackId = "track-1", albumId = "album-1")),
            )

            reporter.submitPlay(localTrack("track-1"), atMillis = 1_000L)
            reporter.submitPlay(localTrack("track-1"), atMillis = 2_500L)

            val trackStats = database.trackPlaybackStatsDao().getByTrackId("track-1")
            val albumStats = database.albumPlaybackStatsDao().getByAlbumId("album-1")
            assertEquals(2, trackStats?.playCount)
            assertEquals(2_500L, trackStats?.lastPlayedAt)
            assertEquals(2, albumStats?.playCount)
            assertEquals(2_500L, albumStats?.lastPlayedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun `submit play skips album stats when track has no album id`() = runTest {
        val database = createTestDatabase()
        val reporter = LocalPlaybackStatsReporter(database)

        try {
            database.trackDao().upsertAll(
                listOf(localTrackEntity(trackId = "track-1", albumId = null)),
            )

            reporter.submitPlay(localTrack("track-1"), atMillis = 1_000L)

            assertEquals(1, database.trackPlaybackStatsDao().getByTrackId("track-1")?.playCount)
            assertNull(database.albumPlaybackStatsDao().getByAlbumId("album-1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `missing track does not record stats`() = runTest {
        val database = createTestDatabase()
        val reporter = LocalPlaybackStatsReporter(database)

        try {
            reporter.submitPlay(localTrack("missing-track"), atMillis = 1_000L)

            assertNull(database.trackPlaybackStatsDao().getByTrackId("missing-track"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `navidrome track also records local stats`() = runTest {
        val database = createTestDatabase()
        val reporter = LocalPlaybackStatsReporter(database)

        try {
            database.trackDao().upsertAll(
                listOf(
                    localTrackEntity(
                        trackId = "nav-track-1",
                        sourceId = "nav-source",
                        albumId = "album-nav",
                        mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
                    ),
                ),
            )

            reporter.submitPlay(navidromeTrack(), atMillis = 3_000L)

            val trackStats = database.trackPlaybackStatsDao().getByTrackId("nav-track-1")
            val albumStats = database.albumPlaybackStatsDao().getByAlbumId("album-nav")
            assertEquals(1, trackStats?.playCount)
            assertEquals("nav-source", trackStats?.sourceId)
            assertEquals(3_000L, trackStats?.lastPlayedAt)
            assertEquals(1, albumStats?.playCount)
        } finally {
            database.close()
        }
    }
}

private fun createTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-local-stats", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private fun localTrack(trackId: String): Track {
    return Track(
        id = trackId,
        sourceId = "local-1",
        title = "Blue",
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 215_000L,
        mediaLocator = "file:///music/Blue.flac",
        relativePath = "Artist A/Album A/Blue.flac",
    )
}

private fun navidromeTrack(): Track {
    return Track(
        id = "nav-track-1",
        sourceId = "nav-source",
        title = "Blue",
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 215_000L,
        mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
        relativePath = "Artist A/Album A/Blue.flac",
    )
}

private fun localTrackEntity(
    trackId: String,
    sourceId: String = "local-1",
    albumId: String? = "album-1",
    mediaLocator: String = "file:///music/Blue.flac",
): TrackEntity {
    return TrackEntity(
        id = trackId,
        sourceId = sourceId,
        title = "Blue",
        artistId = "artist-1",
        artistName = "Artist A",
        albumId = albumId,
        albumTitle = albumId?.let { "Album A" },
        durationMs = 215_000L,
        trackNumber = 1,
        discNumber = 1,
        mediaLocator = mediaLocator,
        relativePath = "Artist A/Album A/Blue.flac",
        artworkLocator = null,
        sizeBytes = 123_456L,
        modifiedAt = 1L,
        bitDepth = null,
        samplingRate = null,
        bitRate = null,
        channelCount = null,
    )
}
