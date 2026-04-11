package top.iwesley.lyn.music

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.platform.loadBundledDefaultCoverBytes

class BundledDefaultCoverResourceTest {

    @Test
    fun `bundled default cover bytes are readable and decodable`() {
        val bytes = runBlocking { loadBundledDefaultCoverBytes() }
        val image = bytes?.let { ImageIO.read(ByteArrayInputStream(it)) }

        assertNotNull(bytes)
        assertNotNull(image)
        assertEquals(1024, image.width)
        assertEquals(1024, image.height)
    }
}
