package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.Track

class MyUiTest {
    @Test
    fun `daily recommendation play intent uses selected track index`() {
        val tracks = listOf(sampleTrack("track-1"), sampleTrack("track-2"))

        val intent = buildDailyRecommendationPlayIntent(tracks, startIndex = 1)

        assertEquals(tracks, intent?.tracks)
        assertEquals(1, intent?.startIndex)
    }

    @Test
    fun `daily recommendation play intent returns null for empty or invalid index`() {
        val tracks = listOf(sampleTrack("track-1"))

        assertNull(buildDailyRecommendationPlayIntent(emptyList(), startIndex = 0))
        assertNull(buildDailyRecommendationPlayIntent(tracks, startIndex = -1))
        assertNull(buildDailyRecommendationPlayIntent(tracks, startIndex = 1))
    }
}

private fun sampleTrack(id: String): Track {
    return Track(
        id = id,
        sourceId = "local-1",
        title = "Blue",
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 180_000L,
        mediaLocator = "file:///music/Blue.flac",
        relativePath = "Artist A/Album A/Blue.flac",
    )
}
