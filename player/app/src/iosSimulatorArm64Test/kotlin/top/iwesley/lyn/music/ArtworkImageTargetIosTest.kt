package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.ArtworkWritePolicy
import top.iwesley.lyn.music.core.model.ArtworkCacheResult

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.ArtworkCacheStore

class ArtworkImageTargetIosTest {
    @Test
    fun legacyIosCachePathUsesArtworkStoreWhenRemoteCachingIsDisabled() = runBlocking {
        val legacyPath =
            "/var/mobile/Containers/Data/Application/OLD/Library/Caches/lynmusic-artwork-cache/f358180aff319859.jpg"
        val currentPath = "/current/container/Library/Caches/lynmusic-artwork-cache/f358180aff319859.jpg"
        val store = RecordingArtworkCacheStore(currentPath)

        val resolved = requireNotNull(
            resolveLynArtworkTarget(
                locator = legacyPath,
                cacheKey = "album:local:album-1",
                cacheRemote = false,
                artworkCacheStore = store,
            ),
        )

        assertEquals(currentPath, resolved.target)
        assertTrue(resolved.isLocalFile)
        assertEquals(listOf(legacyPath to "album:local:album-1"), store.requests)
    }
}

private class RecordingArtworkCacheStore(
    private val target: String,
) : ArtworkCacheStore {
    val requests = mutableListOf<Pair<String, String>>()

    override suspend fun cache(locator: String, cacheKey: String, policy: ArtworkWritePolicy): ArtworkCacheResult? {
        requests += locator to cacheKey
        return target?.let { ArtworkCacheResult(it, true) }
    }
}
