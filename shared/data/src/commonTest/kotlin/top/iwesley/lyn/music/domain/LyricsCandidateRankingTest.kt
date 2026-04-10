package top.iwesley.lyn.music.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.Track

class LyricsCandidateRankingTest {

    @Test
    fun `direct candidate ranking prefers closest metadata match`() {
        val track = sampleTrack()
        val ranked = rankDirectLyricsCandidates(
            track = track,
            candidates = listOf(
                parsedCandidate(title = "Blue", artistName = "Artist B", durationSeconds = 215),
                parsedCandidate(title = "Blue", artistName = "Artist A", durationSeconds = 215),
                parsedCandidate(title = "Glue", artistName = "Artist A", durationSeconds = 215),
            ),
        )

        assertEquals("Artist A", ranked.first().candidate.artistName)
        assertTrue(ranked.first().score > ranked[1].score)
        assertTrue(ranked[1].score > ranked[2].score)
    }

    @Test
    fun `direct candidate ranking keeps original order for score ties`() {
        val ranked = rankDirectLyricsCandidates(
            track = sampleTrack(),
            candidates = listOf(
                parsedCandidate(itemId = "first"),
                parsedCandidate(itemId = "second"),
                parsedCandidate(itemId = "third"),
            ),
        )

        assertEquals(listOf("first", "second", "third"), ranked.map { it.candidate.itemId })
    }

    @Test
    fun `direct candidate score penalizes missing metadata`() {
        val track = sampleTrack()

        val fullyMatched = scoreDirectLyricsCandidate(
            track = track,
            candidate = parsedCandidate(
                title = "Blue",
                artistName = "Artist A",
                albumTitle = "Album A",
                durationSeconds = 215,
            ),
        )
        val metadataMissing = scoreDirectLyricsCandidate(
            track = track,
            candidate = parsedCandidate(),
        )

        assertTrue(fullyMatched > DEFAULT_DIRECT_LYRICS_SELECTION.minScore)
        assertEquals(0.0, metadataMissing)
    }
}

private fun sampleTrack(): Track {
    return Track(
        id = "track-1",
        sourceId = "source-1",
        title = "Blue",
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 215_000L,
        mediaLocator = "file:///music/blue.mp3",
        relativePath = "Artist A/Album A/Blue.mp3",
    )
}

private fun parsedCandidate(
    itemId: String? = null,
    title: String? = null,
    artistName: String? = null,
    albumTitle: String? = null,
    durationSeconds: Int? = null,
): ParsedLyricsPayload {
    return ParsedLyricsPayload(
        document = LyricsDocument(
            lines = parsePlainText("line"),
            sourceId = "direct-source",
            rawPayload = "line",
        ),
        itemId = itemId,
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        durationSeconds = durationSeconds,
    )
}
