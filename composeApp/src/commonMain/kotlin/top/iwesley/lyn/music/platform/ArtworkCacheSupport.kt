package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator

internal suspend fun resolveArtworkCacheTarget(locator: String?): String? {
    val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
    if (rawTarget.isBlank()) return null
    val target = if (parseNavidromeCoverLocator(rawTarget) != null) {
        NavidromeLocatorRuntime.resolveCoverArtUrl(rawTarget).orEmpty()
    } else {
        rawTarget
    }
    return target.takeIf { it.isNotBlank() }
}

internal fun artworkCacheExtension(
    locator: String,
    bytes: ByteArray? = null,
): String {
    return inferArtworkFileExtension(locator = locator, bytes = bytes)
}

internal fun String.stableArtworkCacheHash(): String {
    var hash = 2166136261u
    encodeToByteArray().forEach { byte ->
        hash = (hash xor byte.toUByte().toUInt()) * 16777619u
    }
    return hash.toString(16)
}
