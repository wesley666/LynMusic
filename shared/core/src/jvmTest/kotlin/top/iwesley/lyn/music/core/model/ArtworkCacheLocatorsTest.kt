package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkCacheLocatorsTest {
    @Test
    fun `track artwork cache key prefers source and album id`() {
        val track = Track(
            id = "track-1",
            sourceId = "source-1",
            title = "Song",
            artistName = "Artist",
            albumTitle = "Album",
            albumId = "album-id",
            mediaLocator = "file:///song.flac",
            relativePath = "song.flac",
            artworkLocator = "https://img.example.com/cover.jpg",
        )

        assertEquals("album:source-1:album-id", trackArtworkCacheKey(track))
    }

    @Test
    fun `track artwork cache key falls back to normalized artist and album`() {
        val track = Track(
            id = "track-1",
            sourceId = "source-1",
            title = "Song",
            artistName = "  Artist   Name ",
            albumTitle = " Album   Title ",
            mediaLocator = "file:///song.flac",
            relativePath = "song.flac",
            artworkLocator = "https://img.example.com/cover.jpg",
        )

        assertEquals("album:source-1:artist name:album title", trackArtworkCacheKey(track))
    }

    @Test
    fun `track artwork cache key falls back to artwork locator without album`() {
        val track = Track(
            id = "track-1",
            sourceId = "source-1",
            title = "Song",
            mediaLocator = "file:///song.flac",
            relativePath = "song.flac",
            artworkLocator = " https://img.example.com/cover.jpg ",
        )

        assertEquals("https://img.example.com/cover.jpg", trackArtworkCacheKey(track))
    }

    @Test
    fun `artwork bytes hash is stable for same bytes`() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertEquals(bytes.stableArtworkBytesHash(), byteArrayOf(1, 2, 3, 4).stableArtworkBytesHash())
    }

    @Test
    fun `ios artwork locator round trips stable cache file name`() {
        val locator = buildIosArtworkCacheLocator("f358180aff319859.jpg")

        assertEquals("lynmusic-ios-artwork://v1/f358180aff319859.jpg", locator)
        assertEquals("f358180aff319859.jpg", parseIosArtworkCacheLocator(locator))
    }

    @Test
    fun `ios artwork locator accepts unicode file name`() {
        val locator = buildIosArtworkCacheLocator("四季歌_封面.png")

        assertEquals("四季歌_封面.png", parseIosArtworkCacheLocator(locator))
    }

    @Test
    fun `ios artwork locator rejects path traversal and malformed names`() {
        assertNull(buildIosArtworkCacheLocator("../cover.jpg"))
        assertNull(buildIosArtworkCacheLocator("nested/cover.jpg"))
        assertNull(buildIosArtworkCacheLocator("nested\\cover.jpg"))
        assertNull(parseIosArtworkCacheLocator("lynmusic-ios-artwork://v1/../cover.jpg"))
        assertNull(parseIosArtworkCacheLocator("lynmusic-ios-artwork://v1/%2e%2e%2fcover.jpg"))
        assertNull(parseIosArtworkCacheLocator("lynmusic-ios-artwork://v2/cover.jpg"))
    }

    @Test
    fun `ios artwork cache backed locator recognizes legacy container paths`() {
        val legacyPath =
            "/var/mobile/Containers/Data/Application/OLD/Library/Caches/lynmusic-artwork-cache/f358180aff319859.jpg"
        val legacyFileUrl = "file://$legacyPath"
        val privateLegacyPath = "/private$legacyPath"
        val simulatorLegacyPath =
            "/Users/test/Library/Developer/CoreSimulator/Devices/DEVICE/data/Containers/Data/Application/OLD/Library/Caches/lynmusic-artwork-cache/f358180aff319859.jpg"

        assertEquals("f358180aff319859.jpg", parseLegacyIosArtworkCacheFileName(legacyPath))
        assertEquals("f358180aff319859.jpg", parseLegacyIosArtworkCacheFileName(legacyFileUrl))
        assertEquals("f358180aff319859.jpg", parseLegacyIosArtworkCacheFileName(privateLegacyPath))
        assertEquals("f358180aff319859.jpg", parseLegacyIosArtworkCacheFileName(simulatorLegacyPath))
        assertTrue(isIosArtworkCacheBackedLocator(legacyPath))
        assertTrue(isIosArtworkCacheBackedLocator(legacyFileUrl))
        assertTrue(isIosArtworkCacheBackedLocator("lynmusic-ios-artwork://v1/f358180aff319859.jpg"))
        assertFalse(isIosArtworkCacheBackedLocator("/tmp/f358180aff319859.jpg"))
    }

    @Test
    fun `legacy ios artwork locator rejects nested and unsafe cache paths`() {
        val cacheRoot =
            "/var/mobile/Containers/Data/Application/OLD/Library/Caches/lynmusic-artwork-cache/"
        assertNull(parseLegacyIosArtworkCacheFileName("${cacheRoot}nested/cover.jpg"))
        assertNull(parseLegacyIosArtworkCacheFileName("${cacheRoot}../cover.jpg"))
        assertNull(parseLegacyIosArtworkCacheFileName("${cacheRoot}nested\\cover.jpg"))
        assertNull(
            parseLegacyIosArtworkCacheFileName(
                "/var/mobile/Containers/Data/Application/OLD/Library/Caches/not-lynmusic-artwork-cache/cover.jpg",
            ),
        )
        assertNull(
            parseLegacyIosArtworkCacheFileName(
                "https://example.com/Containers/Data/Application/OLD/Library/Caches/lynmusic-artwork-cache/cover.jpg",
            ),
        )
        assertNull(
            parseLegacyIosArtworkCacheFileName(
                "/tmp/Containers/Data/Application/OLD/lynmusic-artwork-cache/cover.jpg",
            ),
        )
        assertFalse(
            isIosArtworkCacheBackedLocator(
                "https://example.com/Containers/Data/Application/OLD/Library/Caches/lynmusic-artwork-cache/cover.jpg",
            ),
        )
    }

    @Test
    fun `plain artwork target skips candidates that need request headers`() = kotlinx.coroutines.test.runTest {
        var candidateLookups = 0
        NavidromeLocatorRuntime.install(
            object : NavidromeLocatorResolver {
                override suspend fun resolveStreamUrl(locator: String, audioQuality: NavidromeAudioQuality): String? = null
                override suspend fun resolveCoverArtUrl(locator: String): String? = null
                override suspend fun resolveCoverArtUrlCandidates(locator: String): List<RemotePlaybackUrlCandidate> {
                    candidateLookups += 1
                    return listOf(
                        RemotePlaybackUrlCandidate(value = "https://nas/cover?id=1", headers = mapOf("Cookie" to "music-token=t")),
                    )
                }
            },
        )
        try {
            val locator = buildFnMusicCoverLocator("fn-1", "c1")
            kotlin.test.assertNull(resolveArtworkCacheTarget(locator))
            // FN Music covers are answered without resolving them: that would log in or look up FN Connect for nothing.
            kotlin.test.assertEquals(0, candidateLookups)
            kotlin.test.assertEquals(1, resolveArtworkCacheTargets(locator).size)
            kotlin.test.assertEquals("https://example.com/a.jpg", resolveArtworkCacheTarget("https://example.com/a.jpg"))
        } finally {
            NavidromeLocatorRuntime.install(
                object : NavidromeLocatorResolver {
                    override suspend fun resolveStreamUrl(locator: String, audioQuality: NavidromeAudioQuality): String? = null
                    override suspend fun resolveCoverArtUrl(locator: String): String? = null
                },
            )
        }
    }
}
