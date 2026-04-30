package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class PlaybackStatsReporterTest {

    @Test
    fun `composite reporter sends events to all reporters when one fails`() = runBlocking {
        val failingReporter = object : PlaybackStatsReporter {
            override suspend fun reportNowPlaying(track: Track, atMillis: Long) {
                error("now playing failed")
            }

            override suspend fun submitPlay(track: Track, atMillis: Long) {
                error("submit failed")
            }
        }
        val recordingReporter = RecordingPlaybackStatsReporter()
        val composite = CompositePlaybackStatsReporter(
            reporters = listOf(failingReporter, recordingReporter),
        )

        composite.reportNowPlaying(sampleTrack(), atMillis = 100L)
        composite.submitPlay(sampleTrack(), atMillis = 200L)

        assertEquals(listOf("now-playing:track-1:100", "submit:track-1:200"), recordingReporter.events)
    }
}

private fun sampleTrack(): Track {
    return Track(
        id = "track-1",
        sourceId = "local-1",
        title = "Blue",
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 215_000L,
        mediaLocator = "file:///music/Blue.flac",
        relativePath = "Artist A/Album A/Blue.flac",
    )
}

private class RecordingPlaybackStatsReporter : PlaybackStatsReporter {
    val events = mutableListOf<String>()

    override suspend fun reportNowPlaying(track: Track, atMillis: Long) {
        events += "now-playing:${track.id}:$atMillis"
    }

    override suspend fun submitPlay(track: Track, atMillis: Long) {
        events += "submit:${track.id}:$atMillis"
    }
}
