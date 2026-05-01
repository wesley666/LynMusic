package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.RecentTrack
import top.iwesley.lyn.music.core.model.Track

class MyUiTest {
    @Test
    fun `recent preview limit is three on mobile and six on desktop`() {
        assertEquals(3, recentPreviewLimit(isMobile = true))
        assertEquals(6, recentPreviewLimit(isMobile = false))
        assertFalse(recentPreviewUsesScrolling(isMobile = true))
        assertFalse(recentPreviewUsesScrolling(isMobile = false))
    }

    @Test
    fun `recent preview items are capped by platform`() {
        val tracks = (1..8).map { sampleRecentTrack("track-$it") }

        assertEquals(tracks.take(3), recentPreviewItems(tracks, isMobile = true))
        assertEquals(tracks.take(6), recentPreviewItems(tracks, isMobile = false))
    }

    @Test
    fun `recent tracks play intent uses selected recent track index`() {
        val recentTracks = listOf(
            sampleRecentTrack("track-1"),
            sampleRecentTrack("track-2"),
            sampleRecentTrack("track-3"),
        )

        val intent = buildRecentTracksPlayIntent(recentTracks, startIndex = 2)

        assertEquals(recentTracks.map { it.track }, intent?.tracks)
        assertEquals(2, intent?.startIndex)
    }

    @Test
    fun `recent tracks play intent returns null for empty or invalid index`() {
        val recentTracks = listOf(sampleRecentTrack("track-1"))

        assertNull(buildRecentTracksPlayIntent(emptyList(), startIndex = 0))
        assertNull(buildRecentTracksPlayIntent(recentTracks, startIndex = -1))
        assertNull(buildRecentTracksPlayIntent(recentTracks, startIndex = 1))
    }

    @Test
    fun `daily recommendation play intent uses selected track index`() {
        val tracks = listOf(sampleTrack("track-1"), sampleTrack("track-2"))

        val intent = buildDailyRecommendationPlayIntent(tracks, startIndex = 1)

        assertEquals(tracks, intent?.tracks)
        assertEquals(1, intent?.startIndex)
    }

    @Test
    fun `daily recommendation preview play intent can start from third track`() {
        val tracks = listOf(
            sampleTrack("track-1"),
            sampleTrack("track-2"),
            sampleTrack("track-3"),
        )

        val intent = buildDailyRecommendationPlayIntent(tracks, startIndex = 2)

        assertEquals(tracks, intent?.tracks)
        assertEquals(2, intent?.startIndex)
    }

    @Test
    fun `daily recommendation play intent returns null for empty or invalid index`() {
        val tracks = listOf(sampleTrack("track-1"))

        assertNull(buildDailyRecommendationPlayIntent(emptyList(), startIndex = 0))
        assertNull(buildDailyRecommendationPlayIntent(tracks, startIndex = -1))
        assertNull(buildDailyRecommendationPlayIntent(tracks, startIndex = 1))
    }
}

private fun sampleRecentTrack(id: String): RecentTrack {
    return RecentTrack(
        track = sampleTrack(id),
        playCount = 1,
        lastPlayedAt = 1_700_000_000_000L,
    )
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
