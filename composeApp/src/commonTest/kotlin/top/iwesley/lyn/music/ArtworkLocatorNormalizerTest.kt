package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator

class ArtworkLocatorNormalizerTest {

    @Test
    fun `gtimg artwork url missing dot before extension is corrected`() {
        val original = "https://y.gtimg.cn/music/photo_new/T002R800x800M000001O06fF2b3W8Pjpg?max_age=2592000"

        val normalized = normalizeArtworkLocator(original)

        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T002R800x800M000001O06fF2b3W8P.jpg?max_age=2592000",
            normalized,
        )
    }

    @Test
    fun `non gtimg or already valid artwork urls are left unchanged`() {
        val validGtimg = "https://y.gtimg.cn/music/photo_new/T002R800x800M000001O06fF2b3W8P.jpg?max_age=2592000"
        val otherHost = "https://example.com/music/photo_new/T002R800x800M000001O06fF2b3W8Pjpg?max_age=2592000"

        assertEquals(validGtimg, normalizeArtworkLocator(validGtimg))
        assertEquals(otherHost, normalizeArtworkLocator(otherHost))
    }
}
