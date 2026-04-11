package top.iwesley.lyn.music

import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.platform.JvmLyricsSharePlatformService
import top.iwesley.lyn.music.platform.loadBundledDefaultCoverBytes

class JvmLyricsShareDefaultCoverTest {

    private val service = JvmLyricsSharePlatformService()

    @Test
    fun `note template uses bundled default cover when artwork is missing`() {
        val expected = renderPreview(
            template = LyricsShareTemplate.NOTE,
            artworkLocator = defaultCoverFileUri(),
        )
        val actual = renderPreview(
            template = LyricsShareTemplate.NOTE,
            artworkLocator = null,
        )

        assertContentEquals(expected, actual)
    }

    @Test
    fun `artwork tint template uses bundled default cover when artwork path is invalid`() {
        val expected = renderPreview(
            template = LyricsShareTemplate.ARTWORK_TINT,
            artworkLocator = defaultCoverFileUri(),
        )
        val actual = renderPreview(
            template = LyricsShareTemplate.ARTWORK_TINT,
            artworkLocator = "file:///definitely-missing-cover.png",
        )

        assertContentEquals(expected, actual)
    }

    private fun renderPreview(
        template: LyricsShareTemplate,
        artworkLocator: String?,
    ): ByteArray {
        return runBlocking {
            service.buildPreview(
                LyricsShareCardModel(
                    title = "默认封面测试",
                    artistName = "LynMusic",
                    artworkLocator = artworkLocator,
                    template = template,
                    lyricsLines = listOf("第一句", "第二句"),
                ),
            ).getOrThrow()
        }
    }

    private fun defaultCoverFileUri(): String {
        val bytes = runBlocking { loadBundledDefaultCoverBytes() }
        val path = Files.createTempFile("lynmusic-default-cover", ".png")
        path.writeBytes(assertNotNull(bytes))
        return path.toUri().toString()
    }
}
