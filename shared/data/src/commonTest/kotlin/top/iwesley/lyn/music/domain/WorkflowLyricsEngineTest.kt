package top.iwesley.lyn.music.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowRequestConfig
import top.iwesley.lyn.music.core.model.WorkflowSelectionConfig
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate

class WorkflowLyricsEngineTest {

    @Test
    fun `query template literal spaces are percent encoded`() {
        val request = buildWorkflowRequest(
            request = WorkflowRequestConfig(
                method = RequestMethod.GET,
                url = "https://example.com/search",
                queryTemplate = "s={title} {artist} {album}&type=1",
            ),
            variables = mapOf(
                "title" to "Love Story",
                "artist" to "Taylor Swift",
                "album" to "Fearless",
            ),
        )

        assertEquals(
            "https://example.com/search?s=Love%20Story%20Taylor%20Swift%20Fearless&type=1",
            request.url,
        )
    }

    @Test
    fun `literal spaces already present in url are percent encoded`() {
        val request = buildWorkflowRequest(
            request = WorkflowRequestConfig(
                method = RequestMethod.GET,
                url = "https://example.com/api path/{title}",
            ),
            variables = mapOf(
                "title" to "Love Story",
            ),
        )

        assertEquals(
            "https://example.com/api%20path/Love%20Story",
            request.url,
        )
    }

    @Test
    fun `body template remains unencoded`() {
        val request = buildWorkflowRequest(
            request = WorkflowRequestConfig(
                method = RequestMethod.POST,
                url = "https://example.com/search",
                bodyTemplate = "{\"query\":\"{title} {artist}\"}",
            ),
            variables = mapOf(
                "title" to "Love Story",
                "artist" to "Taylor Swift",
            ),
        )

        assertEquals(
            "{\"query\":\"Love Story Taylor Swift\"}",
            request.body,
        )
    }

    @Test
    fun `workflow ranking artist containment can satisfy selection`() {
        val ranked = rankWorkflowSongCandidates(
            track = workflowTrack(artistName = "周杰伦"),
            candidates = listOf(workflowCandidate(artists = listOf("周杰伦", "林迈可"))),
            selection = artistOnlyWorkflowSelection(minScore = 1.0),
        )

        assertEquals("candidate-1", ranked.single().id)
    }

    @Test
    fun `workflow ranking album containment can satisfy selection`() {
        val ranked = rankWorkflowSongCandidates(
            track = workflowTrack(albumTitle = "叶惠美"),
            candidates = listOf(workflowCandidate(album = "叶惠美 2003")),
            selection = albumOnlyWorkflowSelection(minScore = 1.0),
        )

        assertEquals("candidate-1", ranked.single().id)
    }

    @Test
    fun `workflow ranking blank album does not satisfy album selection`() {
        val ranked = rankWorkflowSongCandidates(
            track = workflowTrack(albumTitle = "叶惠美"),
            candidates = listOf(workflowCandidate(album = null)),
            selection = albumOnlyWorkflowSelection(minScore = 1.0),
        )

        assertEquals(emptyList(), ranked)
    }

    @Test
    fun `workflow scoring applies title containment score`() {
        val score = scoreWorkflowSongCandidate(
            track = workflowTrack(title = "硬币", albumTitle = null),
            candidate = workflowCandidate(title = "硬币 (Live)", album = null),
            selection = titleOnlyWorkflowSelection(),
        )

        assertEquals(LYRICS_TITLE_CONTAINMENT_SCORE, score)
    }

    @Test
    fun `workflow scoring applies album match bonus`() {
        val score = scoreWorkflowSongCandidate(
            track = workflowTrack(albumTitle = "存在·超级巡回上海演唱会"),
            candidate = workflowCandidate(album = "存在·超级巡回上海演唱会"),
            selection = albumOnlyWorkflowSelection(minScore = 0.0),
        )

        assertEquals(1.0 + LYRICS_ALBUM_MATCH_BONUS, score)
    }

    @Test
    fun `workflow ranking title version and album match outrank exact title with wrong metadata`() {
        val ranked = rankWorkflowSongCandidates(
            track = workflowTrack(
                title = "硬币",
                artistName = "汪峰",
                albumTitle = "存在·超级巡回上海演唱会",
            ),
            candidates = listOf(
                workflowCandidate(
                    id = "wrong-metadata",
                    title = "硬币",
                    artists = listOf("安瑞兮"),
                    album = "硬币 - Single",
                ),
                workflowCandidate(
                    id = "right-live",
                    title = "硬币 (Live)",
                    artists = listOf("汪峰"),
                    album = "存在·超级巡回上海演唱会",
                ),
            ),
            selection = WorkflowSelectionConfig(minScore = 0.0),
        )

        assertEquals("right-live", ranked.first().id)
        assertTrue(
            scoreWorkflowSongCandidate(
                track = workflowTrack(
                    title = "硬币",
                    artistName = "汪峰",
                    albumTitle = "存在·超级巡回上海演唱会",
                ),
                candidate = ranked.first(),
                selection = WorkflowSelectionConfig(minScore = 0.0),
            ) > scoreWorkflowSongCandidate(
                track = workflowTrack(
                    title = "硬币",
                    artistName = "汪峰",
                    albumTitle = "存在·超级巡回上海演唱会",
                ),
                candidate = ranked[1],
                selection = WorkflowSelectionConfig(minScore = 0.0),
            ),
        )
    }
}

private fun workflowTrack(
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
        relativePath = "Blue.mp3",
    )
}

private fun workflowCandidate(
    id: String = "candidate-1",
    title: String = "Blue",
    artists: List<String> = listOf("Artist A"),
    album: String? = "Album A",
): WorkflowSongCandidate {
    return WorkflowSongCandidate(
        sourceId = "workflow-source",
        sourceName = "Workflow",
        id = id,
        title = title,
        artists = artists,
        album = album,
        durationSeconds = 215,
    )
}

private fun titleOnlyWorkflowSelection(): WorkflowSelectionConfig {
    return WorkflowSelectionConfig(
        titleWeight = 1.0,
        artistWeight = 0.0,
        albumWeight = 0.0,
        durationWeight = 0.0,
        minScore = 0.0,
    )
}

private fun artistOnlyWorkflowSelection(minScore: Double): WorkflowSelectionConfig {
    return WorkflowSelectionConfig(
        titleWeight = 0.0,
        artistWeight = 1.0,
        albumWeight = 0.0,
        durationWeight = 0.0,
        minScore = minScore,
    )
}

private fun albumOnlyWorkflowSelection(minScore: Double): WorkflowSelectionConfig {
    return WorkflowSelectionConfig(
        titleWeight = 0.0,
        artistWeight = 0.0,
        albumWeight = 1.0,
        durationWeight = 0.0,
        minScore = minScore,
    )
}
