package top.iwesley.lyn.music.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSelectionConfig

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

    @Test
    fun `title containment scores slightly below exact match`() {
        assertEquals(1.0, normalizedTitleSimilarity("硬币", "硬币"))
        assertEquals(LYRICS_TITLE_CONTAINMENT_SCORE, normalizedTitleSimilarity("硬币", "硬币 (Live)"))
        assertEquals(LYRICS_TITLE_CONTAINMENT_SCORE, normalizedTitleSimilarity("硬币", "硬币 - Live"))
    }

    @Test
    fun `single character title containment does not use near exact score`() {
        val score = normalizedTitleSimilarity("爱", "爱你")

        assertTrue(score < LYRICS_TITLE_CONTAINMENT_SCORE)
    }

    @Test
    fun `direct candidate artist containment counts as full match`() {
        val selection = artistOnlySelection()

        val containingCandidateScore = scoreDirectLyricsCandidate(
            track = sampleTrack(artistName = "周杰伦"),
            candidate = parsedCandidate(artistName = "周杰伦 / 林迈可"),
            selection = selection,
        )
        val containedCandidateScore = scoreDirectLyricsCandidate(
            track = sampleTrack(artistName = "周杰伦 / 林迈可"),
            candidate = parsedCandidate(artistName = "周杰伦"),
            selection = selection,
        )

        assertEquals(1.0, containingCandidateScore)
        assertEquals(1.0, containedCandidateScore)
    }

    @Test
    fun `direct candidate artist containment does not match blank artist`() {
        val score = scoreDirectLyricsCandidate(
            track = sampleTrack(artistName = "周杰伦"),
            candidate = parsedCandidate(artistName = ""),
            selection = artistOnlySelection(),
        )

        assertEquals(0.0, score)
    }

    @Test
    fun `direct candidate album containment counts as full match`() {
        val selection = albumOnlySelection()

        val containingCandidateScore = scoreDirectLyricsCandidate(
            track = sampleTrack(albumTitle = "叶惠美"),
            candidate = parsedCandidate(albumTitle = "叶惠美 2003"),
            selection = selection,
        )
        val containedCandidateScore = scoreDirectLyricsCandidate(
            track = sampleTrack(albumTitle = "叶惠美 2003"),
            candidate = parsedCandidate(albumTitle = "叶惠美"),
            selection = selection,
        )

        assertEquals(1.0 + LYRICS_ALBUM_MATCH_BONUS, containingCandidateScore)
        assertEquals(1.0 + LYRICS_ALBUM_MATCH_BONUS, containedCandidateScore)
    }

    @Test
    fun `direct candidate album containment does not match blank album`() {
        val score = scoreDirectLyricsCandidate(
            track = sampleTrack(albumTitle = "叶惠美"),
            candidate = parsedCandidate(albumTitle = ""),
            selection = albumOnlySelection(),
        )

        assertEquals(0.0, score)
    }

    @Test
    fun `direct candidate unrelated album falls back to ordinary similarity`() {
        val score = scoreDirectLyricsCandidate(
            track = sampleTrack(albumTitle = "叶惠美"),
            candidate = parsedCandidate(albumTitle = "十一月的萧邦"),
            selection = albumOnlySelection(),
        )

        assertTrue(score < 1.0)
    }

    @Test
    fun `direct candidate album match applies bonus`() {
        val score = scoreDirectLyricsCandidate(
            track = sampleTrack(albumTitle = "存在·超级巡回上海演唱会"),
            candidate = parsedCandidate(albumTitle = "存在·超级巡回上海演唱会"),
            selection = albumOnlySelection(),
        )

        assertEquals(1.0 + LYRICS_ALBUM_MATCH_BONUS, score)
    }

    @Test
    fun `title version and album match outrank exact title with wrong metadata`() {
        val ranked = rankDirectLyricsCandidates(
            track = sampleTrack(
                title = "硬币",
                artistName = "汪峰",
                albumTitle = "存在·超级巡回上海演唱会",
            ),
            candidates = listOf(
                parsedCandidate(
                    itemId = "wrong-metadata",
                    title = "硬币",
                    artistName = "安瑞兮",
                    albumTitle = "硬币 - Single",
                    durationSeconds = 231,
                ),
                parsedCandidate(
                    itemId = "right-live",
                    title = "硬币 (Live)",
                    artistName = "汪峰",
                    albumTitle = "存在·超级巡回上海演唱会",
                    durationSeconds = 231,
                ),
            ),
        )

        assertEquals("right-live", ranked.first().candidate.itemId)
        assertTrue(ranked.first().score > ranked[1].score)
    }
}

private fun sampleTrack(
    title: String = "Blue",
    artistName: String? = "Artist A",
    albumTitle: String? = "Album A",
): Track {
    return Track(
        id = "track-1",
        sourceId = "source-1",
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        durationMs = 215_000L,
        mediaLocator = "file:///music/blue.mp3",
        relativePath = "Artist A/Album A/Blue.mp3",
    )
}

private fun artistOnlySelection(): WorkflowSelectionConfig {
    return WorkflowSelectionConfig(
        titleWeight = 0.0,
        artistWeight = 1.0,
        albumWeight = 0.0,
        durationWeight = 0.0,
        minScore = 0.0,
    )
}

private fun albumOnlySelection(): WorkflowSelectionConfig {
    return WorkflowSelectionConfig(
        titleWeight = 0.0,
        artistWeight = 0.0,
        albumWeight = 1.0,
        durationWeight = 0.0,
        minScore = 0.0,
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
