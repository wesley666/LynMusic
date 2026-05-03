package top.iwesley.lyn.music.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import platform.UIKit.UIPasteboard
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import org.jetbrains.skia.FontMgr
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.jetbrains.skia.Image

class IosLyricsSharePlatformService : LyricsSharePlatformService {
    private val artworkCacheStore = createIosArtworkCacheStore()

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            val artworkImage = loadArtworkImage(model.artworkLocator, model.artworkCacheKey, artworkCacheStore)
            SkiaLyricsShareRenderer.render(model, artworkImage = artworkImage)
        }
    }

    override suspend fun saveImage(
        pngBytes: ByteArray,
        suggestedName: String,
    ): Result<LyricsShareSaveResult> = withContext(Dispatchers.Main) {
        runCatching {
            val image = loadUiImageFromPngBytes(pngBytes) ?: error("无法创建图片。")
            val status = requestPhotoAuthorization()
            if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
                error("没有相册写入权限。")
            }
            suspendCancellableCoroutine<Unit> { continuation ->
                PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                    changeBlock = {
                        PHAssetChangeRequest.creationRequestForAssetFromImage(image)
                    },
                    completionHandler = { success, error ->
                        if (success) {
                            continuation.resume(Unit)
                        } else {
                            continuation.resumeWithException(
                                IllegalStateException(error?.localizedDescription ?: "保存图片失败。"),
                            )
                        }
                    },
                )
            }
            LyricsShareSaveResult(message = "图片已保存到相册")
        }
    }

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            UIPasteboard.generalPasteboard.image = loadUiImageFromPngBytes(pngBytes)
                ?: error("无法创建图片。")
        }
    }

    override suspend fun copyText(text: String): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            UIPasteboard.generalPasteboard.string = text
        }
    }

    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> = withContext(Dispatchers.Default) {
        runCatching {
            prioritizeIosLyricsShareFontFamilyNames(
                availableFonts = listSkiaLyricsShareFontFamilyNames(FontMgr.default),
            )
        }
    }
}

private suspend fun requestPhotoAuthorization() = suspendCancellableCoroutine { continuation ->
    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
        continuation.resume(status)
    }
}

private fun loadUiImageFromPngBytes(pngBytes: ByteArray): UIImage? {
    val tempPath = writeTempPng(pngBytes)
    return UIImage.imageWithContentsOfFile(tempPath)
}

private suspend fun loadArtworkImage(
    locator: String?,
    artworkCacheKey: String?,
    artworkCacheStore: ArtworkCacheStore,
): Image? {
    val normalized = normalizedArtworkCacheLocator(locator)
    val artworkBytes = normalized
        ?.let { resolveIosLyricsShareArtworkTarget(it, artworkCacheKey, artworkCacheStore) }
        ?.let { readIosLyricsShareArtworkTargetBytes(it) }
    return (artworkBytes ?: loadBundledDefaultCoverBytes())?.let(Image::makeFromEncoded)
}

private suspend fun resolveIosLyricsShareArtworkTarget(
    normalizedLocator: String,
    artworkCacheKey: String?,
    artworkCacheStore: ArtworkCacheStore,
): String? {
    if (shouldCacheLyricsShareArtwork(normalizedLocator)) {
        val cacheKey = artworkCacheKey?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedLocator
        return artworkCacheStore.cache(normalizedLocator, cacheKey)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
    return resolveArtworkCacheTarget(normalizedLocator)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private suspend fun readIosLyricsShareArtworkTargetBytes(target: String): ByteArray? {
    return when {
        target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
            readIosRemoteBytes(target)

        target.startsWith("file://", ignoreCase = true) ->
            readIosLocalBytes(NSURL.URLWithString(target)?.path ?: target.removePrefix("file://"))

        else -> readIosLocalBytes(target)
    }
}

private fun shouldCacheLyricsShareArtwork(normalizedLocator: String): Boolean {
    return parseNavidromeCoverLocator(normalizedLocator) != null ||
        normalizedLocator.startsWith("http://", ignoreCase = true) ||
        normalizedLocator.startsWith("https://", ignoreCase = true)
}

private fun writeTempPng(pngBytes: ByteArray): String {
    val path = NSTemporaryDirectory() + "lynmusic-lyrics-share.png"
    if (!writeIosFileBytes(path, pngBytes)) {
        error("无法创建临时图片文件。")
    }
    return path
}
