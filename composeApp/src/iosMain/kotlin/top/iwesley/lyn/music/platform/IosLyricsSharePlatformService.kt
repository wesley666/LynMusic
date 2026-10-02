package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiTextArgumentException
import top.iwesley.lyn.music.core.model.UiTextUnsupportedException
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
import top.iwesley.lyn.music.core.model.resolveUiString
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.isIosArtworkCacheBackedLocator
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseEmbyCoverLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleCoverLocator
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
            val image = loadUiImageFromPngBytes(pngBytes) ?: throw UiTextException(uiText(Res.string.lyrics_image_creation_failed))
            val status = requestPhotoAuthorization()
            if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
                throw UiTextException(uiText(Res.string.photo_library_write_permission_missing))
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
                                error?.localizedDescription?.let { IllegalStateException(it) }
                                    ?: UiTextException(uiText(Res.string.lyrics_image_save_failed)),
                            )
                        }
                    },
                )
            }
            LyricsShareSaveResult(message = uiText(Res.string.lyrics_image_saved_photos))
        }
    }

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            UIPasteboard.generalPasteboard.image = loadUiImageFromPngBytes(pngBytes)
                ?: throw UiTextException(uiText(Res.string.lyrics_image_creation_failed))
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

internal suspend fun resolveIosLyricsShareArtworkTarget(
    normalizedLocator: String,
    artworkCacheKey: String?,
    artworkCacheStore: ArtworkCacheStore,
): String? {
    if (shouldResolveLyricsShareArtworkThroughCache(normalizedLocator)) {
        val cacheKey = artworkCacheKey?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedLocator
        return artworkCacheStore.cache(normalizedLocator, cacheKey)?.locator
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

private fun shouldResolveLyricsShareArtworkThroughCache(normalizedLocator: String): Boolean {
    return isIosArtworkCacheBackedLocator(normalizedLocator) ||
        parseSubsonicCompatibleCoverLocator(normalizedLocator) != null ||
        parseEmbyCoverLocator(normalizedLocator) != null ||
        normalizedLocator.startsWith("http://", ignoreCase = true) ||
        normalizedLocator.startsWith("https://", ignoreCase = true)
}

private fun writeTempPng(pngBytes: ByteArray): String {
    val path = NSTemporaryDirectory() + "lynmusic-lyrics-share.png"
    if (!writeIosFileBytes(path, pngBytes)) {
        throw UiTextException(uiText(Res.string.lyrics_temporary_image_creation_failed))
    }
    return path
}
