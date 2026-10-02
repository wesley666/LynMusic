package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteSourceUrlRedactionTest {
    @Test
    fun `subsonic token salt password and api key are masked`() {
        assertEquals(
            "https://music.example.com/rest/stream?u=alice&t=<redacted>&s=<redacted>&v=1.16.1&c=lyn&id=42",
            redactRemoteSourceUrlForLog(
                "https://music.example.com/rest/stream?u=alice&t=0123abcd&s=salt9&v=1.16.1&c=lyn&id=42",
            ),
        )
        assertEquals(
            "http://192.168.1.2:4533/rest/stream?p=<redacted>&apiKey=<redacted>&id=42",
            redactRemoteSourceUrlForLog("http://192.168.1.2:4533/rest/stream?p=enc:secret&apiKey=key123&id=42"),
        )
    }

    @Test
    fun `emby api key is masked and other parameters are kept`() {
        assertEquals(
            "https://emby.example.com/Audio/7/stream?static=true&api_key=<redacted>&DeviceId=dev",
            redactRemoteSourceUrlForLog("https://emby.example.com/Audio/7/stream?static=true&api_key=tok&DeviceId=dev"),
        )
    }

    @Test
    fun `secrets inside a whole log line are masked without eating the rest of the line`() {
        assertEquals(
            "remote-address-fallback retry index=1 url=https://m.example.com/rest/stream?u=a&t=<redacted>&s=<redacted> " +
                "recentLogs=http stream error: http://192.168.1.2/Audio/7/stream?api_key=<redacted> || done",
            redactRemoteSourceUrlForLog(
                "remote-address-fallback retry index=1 url=https://m.example.com/rest/stream?u=a&t=tok&s=salt " +
                    "recentLogs=http stream error: http://192.168.1.2/Audio/7/stream?api_key=abc || done",
            ),
        )
    }

    @Test
    fun `console logger redacts messages and causes`() {
        val lines = mutableListOf<String>()

        ConsoleDiagnosticLogger(label = "test", output = { lines += it }).log(
            level = DiagnosticLogLevel.ERROR,
            tag = "playback",
            message = "play-failed url=https://m.example.com/rest/stream?t=tok&s=salt",
            throwable = IllegalStateException("GET https://emby.example.com/Audio/1?api_key=secret failed"),
        )

        assertEquals(
            listOf(
                "[test][playback][ERROR] play-failed url=https://m.example.com/rest/stream?t=<redacted>&s=<redacted>",
                "[test][playback][ERROR] cause=GET https://emby.example.com/Audio/1?api_key=<redacted> failed",
            ),
            lines,
        )
        assertFalse(lines.any { it.contains("tok") || it.contains("salt") || it.contains("secret") })
        assertTrue(lines.all { it.contains("<redacted>") })
    }

    @Test
    fun `parameters that merely end with a secret name are untouched`() {
        val url = "https://music.example.com/rest/stream?format=mp3&maxBitRate=320&id=42"
        assertEquals(url, redactRemoteSourceUrlForLog(url))
    }
}
