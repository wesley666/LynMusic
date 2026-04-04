package top.iwesley.lyn.music.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
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
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

@OptIn(ExperimentalForeignApi::class)
class IosLyricsSharePlatformService : LyricsSharePlatformService {
    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            renderLyricsShareImage(model, artworkImage = null)
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

    private fun renderLyricsShareImage(
        model: LyricsShareCardModel,
        artworkImage: Image?,
    ): ByteArray {
        val width = LyricsShareCardSpec.IMAGE_WIDTH_PX.toFloat()
        val lyricsLines = wrapLines(model.lyricsLines, maxCharsPerLine = 12)
        val titleLines = wrapLines(listOf(model.title.ifBlank { "当前歌曲" }), maxCharsPerLine = 18).take(2)
        val artistLines = wrapLines(listOf(model.artistName?.ifBlank { "未知艺人" } ?: "未知艺人"), maxCharsPerLine = 22).take(2)

        val lyricsFont = Font(null, LyricsShareCardSpec.LYRICS_FONT_SIZE_PX)
        val titleFont = Font(null, LyricsShareCardSpec.TITLE_FONT_SIZE_PX)
        val artistFont = Font(null, LyricsShareCardSpec.META_FONT_SIZE_PX)
        val brandFont = Font(null, LyricsShareCardSpec.BRAND_FONT_SIZE_PX)
        val lyricsLineHeight = LyricsShareCardSpec.LYRICS_FONT_SIZE_PX + 18f
        val titleLineHeight = LyricsShareCardSpec.TITLE_FONT_SIZE_PX + 12f
        val artistLineHeight = LyricsShareCardSpec.META_FONT_SIZE_PX + 8f
        val contentHeight =
            LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                max(1, lyricsLines.size) * lyricsLineHeight +
                LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                max(1, titleLines.size) * titleLineHeight +
                max(1, artistLines.size) * artistLineHeight +
                LyricsShareCardSpec.BRAND_TOP_GAP_PX +
                LyricsShareCardSpec.BRAND_FONT_SIZE_PX +
                LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX
        val height = (contentHeight + LyricsShareCardSpec.OUTER_PADDING_PX * 2 + LyricsShareCardSpec.SHADOW_OFFSET_PX)
            .toInt()
            .coerceIn(LyricsShareCardSpec.IMAGE_MIN_HEIGHT_PX, LyricsShareCardSpec.IMAGE_MAX_HEIGHT_PX)

        val surface = Surface.makeRasterN32Premul(width.toInt(), height)
        val canvas = surface.canvas
        val backgroundPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareCardSpec.CANVAS_BACKGROUND_ARGB
        }
        val paperPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareCardSpec.PAPER_BACKGROUND_ARGB
        }
        val shadowPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareCardSpec.PAPER_SHADOW_ARGB
        }
        val tapePaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareCardSpec.TAPE_ARGB
        }
        val placeholderPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareCardSpec.PLACEHOLDER_ARGB
        }
        val textPrimaryPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareCardSpec.TEXT_PRIMARY_ARGB
        }
        val textSecondaryPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareCardSpec.TEXT_SECONDARY_ARGB
        }

        canvas.drawRect(Rect.makeWH(width, height.toFloat()), backgroundPaint)

        val paperX = LyricsShareCardSpec.OUTER_PADDING_PX.toFloat()
        val paperY = LyricsShareCardSpec.OUTER_PADDING_PX.toFloat()
        val paperWidth = width - LyricsShareCardSpec.OUTER_PADDING_PX * 2f
        val paperHeight = height.toFloat() - LyricsShareCardSpec.OUTER_PADDING_PX * 2f - LyricsShareCardSpec.SHADOW_OFFSET_PX
        val radius = LyricsShareCardSpec.PAPER_RADIUS_PX.toFloat()
        canvas.drawRRect(
            RRect.makeXYWH(
                paperX,
                paperY + LyricsShareCardSpec.SHADOW_OFFSET_PX,
                paperWidth,
                paperHeight,
                radius,
                radius,
            ),
            shadowPaint,
        )
        canvas.drawRRect(
            RRect.makeXYWH(
                paperX,
                paperY,
                paperWidth,
                paperHeight,
                radius,
                radius,
            ),
            paperPaint,
        )
        canvas.drawRRect(
            RRect.makeXYWH(
                paperX + 40f,
                paperY - 8f,
                LyricsShareCardSpec.TAPE_WIDTH_PX.toFloat(),
                LyricsShareCardSpec.TAPE_HEIGHT_PX.toFloat(),
                10f,
                10f,
            ),
            tapePaint,
        )

        val artworkX = paperX + LyricsShareCardSpec.PAPER_PADDING_HORIZONTAL_PX
        val artworkY = paperY + LyricsShareCardSpec.PAPER_PADDING_TOP_PX
        val artworkRect = Rect.makeXYWH(
            artworkX,
            artworkY,
            LyricsShareCardSpec.ARTWORK_SIZE_PX.toFloat(),
            LyricsShareCardSpec.ARTWORK_SIZE_PX.toFloat(),
        )
        if (artworkImage != null) {
            canvas.drawImageRect(artworkImage, artworkRect)
        } else {
            canvas.drawRRect(
                RRect.makeXYWH(
                    artworkX,
                    artworkY,
                    LyricsShareCardSpec.ARTWORK_SIZE_PX.toFloat(),
                    LyricsShareCardSpec.ARTWORK_SIZE_PX.toFloat(),
                    24f,
                    24f,
                ),
                placeholderPaint,
            )
        }

        val textX = artworkX
        var cursorY = artworkY + LyricsShareCardSpec.ARTWORK_SIZE_PX + LyricsShareCardSpec.LYRICS_TOP_GAP_PX + LyricsShareCardSpec.LYRICS_FONT_SIZE_PX
        lyricsLines.forEach { line ->
            canvas.drawString(line, textX, cursorY, lyricsFont, textPrimaryPaint)
            cursorY += lyricsLineHeight
        }
        cursorY += LyricsShareCardSpec.FOOTER_TOP_GAP_PX - LyricsShareCardSpec.LYRICS_FONT_SIZE_PX
        titleLines.forEach { line ->
            canvas.drawString(line, textX, cursorY + LyricsShareCardSpec.TITLE_FONT_SIZE_PX, titleFont, textPrimaryPaint)
            cursorY += titleLineHeight
        }
        artistLines.forEach { line ->
            canvas.drawString(line, textX, cursorY + LyricsShareCardSpec.META_FONT_SIZE_PX, artistFont, textSecondaryPaint)
            cursorY += artistLineHeight
        }

        val brandText = LyricsShareCardSpec.BRAND_TEXT
        val brandBounds = brandFont.measureText(brandText)
        val brandWidth = brandBounds.right - brandBounds.left
        val brandX = paperX + (paperWidth - brandWidth) / 2f
        val brandY = paperY + paperHeight - LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX.toFloat()
        canvas.drawString(brandText, brandX, brandY, brandFont, textSecondaryPaint)

        val encoded = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG, 100)
            ?: error("无法导出 PNG 数据。")
        return encoded.bytes
    }
}

private fun wrapLines(
    lines: List<String>,
    maxCharsPerLine: Int,
): List<String> {
    return lines.flatMap { line ->
        val normalized = line.trim().ifBlank { " " }
        normalized.chunked(maxCharsPerLine.coerceAtLeast(1))
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

@OptIn(ExperimentalForeignApi::class)
private fun writeTempPng(pngBytes: ByteArray): String {
    val path = NSTemporaryDirectory() + "lynmusic-lyrics-share.png"
    val file = fopen(path, "wb") ?: error("无法创建临时图片文件。")
    try {
        pngBytes.usePinned { pinned ->
            fwrite(
                pinned.addressOf(0),
                1.convert(),
                pngBytes.size.convert(),
                file,
            )
        }
    } finally {
        fclose(file)
    }
    return path
}
