package top.iwesley.lyn.music.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.WorkflowRequestConfig

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
}
