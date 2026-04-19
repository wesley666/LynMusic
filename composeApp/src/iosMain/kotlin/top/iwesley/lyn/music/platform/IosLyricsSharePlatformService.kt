package top.iwesley.lyn.music.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSTemporaryDirectory
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
import org.jetbrains.skia.FontMgr
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.jetbrains.skia.Image

class IosLyricsSharePlatformService : LyricsSharePlatformService {
    private val artworkCacheStore = createIosArtworkCacheStore()

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            val artworkImage = loadArtworkImage(model.artworkLocator, artworkCacheStore)
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
    artworkCacheStore: ArtworkCacheStore,
): Image? {
    val normalized = locator?.trim().orEmpty()
    val artworkBytes = if (normalized.isBlank()) {
        null
    } else {
        val localPath = artworkCacheStore.cache(normalized, normalized).orEmpty()
        if (localPath.isBlank()) null else readIosLocalBytes(localPath)
    }
    return (artworkBytes ?: loadBundledDefaultCoverBytes())?.let(Image::makeFromEncoded)
}

private fun writeTempPng(pngBytes: ByteArray): String {
    val path = NSTemporaryDirectory() + "lynmusic-lyrics-share.png"
    if (!writeIosFileBytes(path, pngBytes)) {
        error("无法创建临时图片文件。")
    }
    return path
}
