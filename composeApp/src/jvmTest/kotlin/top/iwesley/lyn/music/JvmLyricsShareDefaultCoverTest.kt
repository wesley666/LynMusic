package top.iwesley.lyn.music

import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
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

    @Test
    fun `local default cover file is read without writing artwork cache`() {
        val artworkCacheStore = RecordingArtworkCacheStore()
        renderPreview(
            service = JvmLyricsSharePlatformService(artworkCacheStore = artworkCacheStore),
            template = LyricsShareTemplate.NOTE,
            artworkLocator = defaultCoverFileUri(),
        )

        assertEquals(emptyList(), artworkCacheStore.requests)
    }

    @Test
    fun `remote artwork uses share artwork cache key`() {
        val remoteLocator = "https://example.com/cover.png"
        val cachedArtworkPath = defaultCoverFileUri()
        val artworkCacheStore = RecordingArtworkCacheStore(
            targets = mapOf(remoteLocator to cachedArtworkPath),
        )

        renderPreview(
            service = JvmLyricsSharePlatformService(artworkCacheStore = artworkCacheStore),
            template = LyricsShareTemplate.NOTE,
            artworkLocator = remoteLocator,
            artworkCacheKey = "album:source:album-id",
        )

        assertEquals(
            listOf(ArtworkCacheRequest(remoteLocator, "album:source:album-id", false)),
            artworkCacheStore.requests,
        )
    }

    private fun renderPreview(
        service: JvmLyricsSharePlatformService = this.service,
        template: LyricsShareTemplate,
        artworkLocator: String?,
        artworkCacheKey: String? = null,
    ): ByteArray {
        return runBlocking {
            service.buildPreview(
                LyricsShareCardModel(
                    title = "默认封面测试",
                    artistName = "LynMusic",
                    artworkLocator = artworkLocator,
                    artworkCacheKey = artworkCacheKey,
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

private data class ArtworkCacheRequest(
    val locator: String,
    val cacheKey: String,
    val replaceExisting: Boolean,
)

private class RecordingArtworkCacheStore(
    private val targets: Map<String, String> = emptyMap(),
) : ArtworkCacheStore {
    val requests = mutableListOf<ArtworkCacheRequest>()

    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? {
        requests += ArtworkCacheRequest(locator, cacheKey, replaceExisting)
        return targets[locator]
    }
}
