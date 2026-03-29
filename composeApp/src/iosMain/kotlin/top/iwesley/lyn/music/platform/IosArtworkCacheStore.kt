package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.ArtworkCacheStore

fun createIosArtworkCacheStore(): ArtworkCacheStore = object : ArtworkCacheStore {
    override suspend fun cache(locator: String, cacheKey: String): String? = locator
}
