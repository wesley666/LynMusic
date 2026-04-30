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
    fun `direct candidate ranking ignores synced status without bonus`() {
        val ranked = rankDirectLyricsCandidates(
            track = sampleTrack(),
            candidates = listOf(
                parsedCandidate(itemId = "plain", title = "Blue", artistName = "Artist A", albumTitle = "Album A", durationSeconds = 215),
                parsedCandidate(
                    itemId = "synced",
                    title = "Blue",
                    artistName = "Artist A",
                    albumTitle = "Album A",
                    durationSeconds = 215,
                    synced = true,
                ),
            ),
        )

        assertEquals(listOf("plain", "synced"), ranked.map { it.candidate.itemId })
        assertEquals(ranked.first().score, ranked[1].score)
    }

    @Test
    fun `direct candidate ranking applies synced bonus when requested`() {
        val ranked = rankDirectLyricsCandidates(
            track = sampleTrack(),
            candidates = listOf(
                parsedCandidate(itemId = "plain", title = "Blue", artistName = "Artist A", albumTitle = "Album A", durationSeconds = 215),
                parsedCandidate(
                    itemId = "synced",
                    title = "Blue",
                    artistName = "Artist A",
                    albumTitle = "Album A",
                    durationSeconds = 215,
                    synced = true,
                ),
            ),
            syncedBonus = AUTO_DIRECT_LYRICS_SYNCED_BONUS,
        )

        assertEquals("synced", ranked.first().candidate.itemId)
        assertTrue(ranked.first().score > ranked[1].score)
    }

    @Test
    fun `direct candidate synced bonus can reverse a small metadata gap`() {
        val track = sampleTrack()
        val plain = parsedCandidate(
            itemId = "plain",
            title = "Blue",
            artistName = "Artist A",
            albumTitle = "Album A",
            durationSeconds = 215,
        )
        val synced = parsedCandidate(
            itemId = "synced",
            title = "Blue",
            artistName = "Artist A",
            albumTitle = "Album A",
            durationSeconds = 219,
            synced = true,
        )

        val plainBaseScore = scoreDirectLyricsCandidate(track = track, candidate = plain)
        val syncedBaseScore = scoreDirectLyricsCandidate(track = track, candidate = synced)
        val ranked = rankDirectLyricsCandidates(
            track = track,
            candidates = listOf(plain, synced),
            syncedBonus = AUTO_DIRECT_LYRICS_SYNCED_BONUS,
        )

        assertTrue(plainBaseScore - syncedBaseScore < AUTO_DIRECT_LYRICS_SYNCED_BONUS)
        assertEquals("synced", ranked.first().candidate.itemId)
    }

    @Test
    fun `direct candidate synced bonus does not override a larger metadata gap`() {
        val track = sampleTrack()
        val plain = parsedCandidate(
            itemId = "plain",
            title = "Blue",
            artistName = "Artist A",
            albumTitle = "Album A",
            durationSeconds = 215,
        )
        val synced = parsedCandidate(
            itemId = "synced",
            title = "Glue",
            artistName = "Artist A",
            albumTitle = "Album A",
            durationSeconds = 215,
            synced = true,
        )
        val ranked = rankDirectLyricsCandidates(
            track = track,
            candidates = listOf(plain, synced),
            syncedBonus = AUTO_DIRECT_LYRICS_SYNCED_BONUS,
        )

        assertEquals("plain", ranked.first().candidate.itemId)
        assertTrue(ranked.first().score > ranked[1].score)
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
    synced: Boolean = false,
): ParsedLyricsPayload {
    val lines = if (synced) parseLrc("[00:01.00]line") else parsePlainText("line")
    return ParsedLyricsPayload(
        document = LyricsDocument(
            lines = lines,
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
