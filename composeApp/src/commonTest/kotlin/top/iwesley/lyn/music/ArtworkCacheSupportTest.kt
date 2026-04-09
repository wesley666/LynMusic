package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.NavidromeLocatorResolver
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.platform.artworkCacheExtension
import top.iwesley.lyn.music.platform.resolveArtworkCacheTarget
import top.iwesley.lyn.music.platform.stableArtworkCacheHash

class ArtworkCacheSupportTest {

    @Test
    fun `navidrome cover locator resolves through runtime before caching`() = runTest {
        NavidromeLocatorRuntime.install(
            object : NavidromeLocatorResolver {
                override suspend fun resolveStreamUrl(locator: String): String? = null

                override suspend fun resolveCoverArtUrl(locator: String): String? {
                    return "https://demo.example.com/rest/getCoverArt.view?id=cover-123"
                }
            },
        )

        val resolved = resolveArtworkCacheTarget(buildNavidromeCoverLocator("nav-source", "cover-123"))

        assertEquals(
            "https://demo.example.com/rest/getCoverArt.view?id=cover-123",
            resolved,
        )
    }

    @Test
    fun `artwork cache extension handles query strings and missing extensions`() {
        assertEquals(
            ".jpg",
            artworkCacheExtension("https://example.com/cover.jpg?size=1080"),
        )
        assertEquals(
            ".jpg",
            artworkCacheExtension("https://example.com/cover"),
        )
    }

    @Test
    fun `artwork file extension prefers image bytes signature`() {
        assertEquals(
            ".png",
            inferArtworkFileExtension(
                locator = "https://example.com/cover",
                bytes = byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                ),
            ),
        )
    }

    @Test
    fun `artwork cache hash is deterministic`() {
        val first = "https://example.com/cover.jpg".stableArtworkCacheHash()
        val second = "https://example.com/cover.jpg".stableArtworkCacheHash()

        assertEquals(first, second)
        assertTrue(first.isNotBlank())
    }
}
