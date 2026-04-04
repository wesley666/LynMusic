package top.iwesley.lyn.music.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
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
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.argbWithAlpha
import top.iwesley.lyn.music.core.model.buildLyricsShareTitleArtistLine
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Point
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

@OptIn(ExperimentalForeignApi::class)
class IosLyricsSharePlatformService : LyricsSharePlatformService {
    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            val artworkImage = loadArtworkImage(model.artworkLocator)
            renderLyricsShareImage(model, artworkImage = artworkImage)
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
        return when (model.template) {
            LyricsShareTemplate.NOTE -> renderNoteLyricsShareImage(model, artworkImage)
            LyricsShareTemplate.ARTWORK_TINT -> renderArtworkTintLyricsShareImage(model, artworkImage)
        }
    }

    private fun renderNoteLyricsShareImage(
        model: LyricsShareCardModel,
        artworkImage: Image?,
    ): ByteArray {
        val width = LyricsShareCardSpec.IMAGE_WIDTH_PX.toFloat()
        val lyricsLines = wrapLines(model.lyricsLines, maxCharsPerLine = 12)
        val footerLines = wrapLines(
            listOf(buildLyricsShareTitleArtistLine(model.title, model.artistName)),
            maxCharsPerLine = 28,
        ).take(1)

        val lyricsFont = Font(null, LyricsShareCardSpec.LYRICS_FONT_SIZE_PX)
        val titleFont = Font(null, LyricsShareCardSpec.TITLE_FONT_SIZE_PX)
        val brandFont = Font(null, LyricsShareCardSpec.BRAND_FONT_SIZE_PX)
        val lyricsLineHeight = LyricsShareCardSpec.LYRICS_FONT_SIZE_PX + LyricsShareCardSpec.LYRICS_IOS_LINE_GAP_PX
        val titleLineHeight = LyricsShareCardSpec.TITLE_FONT_SIZE_PX + 12f
        val contentHeight =
            LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                max(1, lyricsLines.size) * lyricsLineHeight +
                LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                max(1, footerLines.size) * titleLineHeight +
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
        val textFooterPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareCardSpec.TEXT_FOOTER_ARGB
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
        footerLines.forEach { line ->
            canvas.drawString(line, textX, cursorY + LyricsShareCardSpec.TITLE_FONT_SIZE_PX, titleFont, textFooterPaint)
            cursorY += titleLineHeight
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

    private fun renderArtworkTintLyricsShareImage(
        model: LyricsShareCardModel,
        artworkImage: Image?,
    ): ByteArray {
        val width = LyricsShareArtworkTintSpec.IMAGE_WIDTH_PX.toFloat()
        val lyricsLines = wrapLines(model.lyricsLines, maxCharsPerLine = 12)
        val footerLines = wrapLines(
            listOf(buildLyricsShareTitleArtistLine(model.title, model.artistName)),
            maxCharsPerLine = 28,
        ).take(1)
        val theme = model.artworkTintTheme ?: sampleArtworkTintTheme(artworkImage)

        val lyricsFont = Font(null, LyricsShareArtworkTintSpec.LYRICS_FONT_SIZE_PX)
        val titleFont = Font(null, LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX)
        val brandFont = Font(null, LyricsShareArtworkTintSpec.BRAND_FONT_SIZE_PX)
        val lyricsLineHeight = LyricsShareArtworkTintSpec.LYRICS_FONT_SIZE_PX + LyricsShareArtworkTintSpec.LYRICS_IOS_LINE_GAP_PX
        val titleLineHeight = LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX + 12f
        val contentHeight =
            LyricsShareArtworkTintSpec.OUTER_PADDING_PX +
                LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX +
                LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX +
                max(1, lyricsLines.size) * lyricsLineHeight +
                LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX +
                max(1, footerLines.size) * titleLineHeight +
                LyricsShareArtworkTintSpec.BRAND_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.BRAND_FONT_SIZE_PX +
                LyricsShareArtworkTintSpec.OUTER_PADDING_PX
        val height = contentHeight
            .toInt()
            .coerceIn(LyricsShareArtworkTintSpec.IMAGE_MIN_HEIGHT_PX, LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX)

        val surface = Surface.makeRasterN32Premul(width.toInt(), height)
        val canvas = surface.canvas
        val backgroundPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB
        }
        val topGradientPaint = Paint().apply {
            isAntiAlias = true
            shader = Shader.makeLinearGradient(
                Point(0f, 0f),
                Point(0f, height.toFloat()),
                intArrayOf(
                    argbWithAlpha(
                        theme?.innerGlowColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
                        if (theme != null) 0.22f else 0f,
                    ),
                    argbWithAlpha(
                        theme?.glowColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
                        if (theme != null) 0.18f else 0f,
                    ),
                    0x00000000,
                ),
                floatArrayOf(0f, 0.46f, 1f),
            )
        }
        val accentGradientPaint = Paint().apply {
            isAntiAlias = true
            shader = Shader.makeLinearGradient(
                Point(0f, height * 0.22f),
                Point(width, height.toFloat()),
                intArrayOf(
                    0x00000000,
                    argbWithAlpha(
                        theme?.rimColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
                        if (theme != null) 0.12f else 0f,
                    ),
                    0x00000000,
                ),
                floatArrayOf(0f, 0.58f, 1f),
            )
        }
        val placeholderPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareArtworkTintSpec.PLACEHOLDER_ARGB
        }
        val artworkShadowPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareArtworkTintSpec.ARTWORK_SHADOW_ARGB
        }
        val textPrimaryPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareArtworkTintSpec.TEXT_PRIMARY_ARGB
        }
        val textFooterPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareArtworkTintSpec.TEXT_FOOTER_ARGB
        }
        val textSecondaryPaint = Paint().apply {
            isAntiAlias = true
            color = LyricsShareArtworkTintSpec.TEXT_SECONDARY_ARGB
        }

        canvas.drawRect(Rect.makeWH(width, height.toFloat()), backgroundPaint)
        canvas.drawRect(Rect.makeWH(width, height.toFloat()), topGradientPaint)
        canvas.drawRect(Rect.makeWH(width, height.toFloat()), accentGradientPaint)

        val artworkX = LyricsShareArtworkTintSpec.OUTER_PADDING_PX.toFloat()
        val artworkY = LyricsShareArtworkTintSpec.OUTER_PADDING_PX + LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX.toFloat()
        canvas.drawRRect(
            RRect.makeXYWH(
                artworkX,
                artworkY + 10f,
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
                LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
                LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
            ),
            artworkShadowPaint,
        )
        val artworkRect = Rect.makeXYWH(
            artworkX,
            artworkY,
            LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
            LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
        )
        if (artworkImage != null) {
            canvas.drawImageRect(artworkImage, artworkRect)
        } else {
            canvas.drawRRect(
                RRect.makeXYWH(
                    artworkX,
                    artworkY,
                    LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
                    LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
                    LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
                    LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
                ),
                placeholderPaint,
            )
        }

        val textX = artworkX
        var cursorY = artworkY + LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX + LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX + LyricsShareArtworkTintSpec.LYRICS_FONT_SIZE_PX
        lyricsLines.forEach { line ->
            canvas.drawString(line, textX, cursorY, lyricsFont, textPrimaryPaint)
            cursorY += lyricsLineHeight
        }
        cursorY += LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX - LyricsShareArtworkTintSpec.LYRICS_FONT_SIZE_PX
        footerLines.forEach { line ->
            canvas.drawString(line, textX, cursorY + LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX, titleFont, textFooterPaint)
            cursorY += titleLineHeight
        }

        val brandText = LyricsShareCardSpec.BRAND_TEXT
        val brandBounds = brandFont.measureText(brandText)
        val brandWidth = brandBounds.right - brandBounds.left
        val brandX = (width - brandWidth) / 2f
        val brandY = height - LyricsShareArtworkTintSpec.OUTER_PADDING_PX.toFloat()
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

private fun loadArtworkImage(locator: String?): Image? {
    val normalized = locator?.trim().orEmpty()
    if (normalized.isBlank()) return null
    if (normalized.startsWith("http://", ignoreCase = true) || normalized.startsWith("https://", ignoreCase = true)) {
        return null
    }
    val localPath = if (normalized.startsWith("file://", ignoreCase = true)) {
        NSURL.URLWithString(normalized)?.path ?: normalized.removePrefix("file://")
    } else {
        normalized
    }
    return readFileBytes(localPath)?.let(Image::makeFromEncoded)
}

@OptIn(ExperimentalForeignApi::class)
private fun readFileBytes(path: String): ByteArray? {
    val file = fopen(path, "rb") ?: return null
    return try {
        if (fseek(file, 0, SEEK_END) != 0) return null
        val byteCount = ftell(file).toInt()
        if (byteCount < 0) return null
        if (fseek(file, 0, SEEK_SET) != 0) return null
        val byteArray = ByteArray(byteCount)
        val bytesRead = byteArray.usePinned { pinned ->
            fread(
                pinned.addressOf(0).reinterpret<ByteVar>(),
                1.convert(),
                byteCount.convert(),
                file,
            ).toInt()
        }
        if (bytesRead != byteCount) return null
        byteArray
    } finally {
        fclose(file)
    }
}

private fun sampleArtworkTintTheme(artworkImage: Image?): top.iwesley.lyn.music.core.model.ArtworkTintTheme? {
    val image = artworkImage ?: return null
    val pixmap = image.peekPixels() ?: return null
    val stepX = max(1, pixmap.info.width / 24)
    val stepY = max(1, pixmap.info.height / 24)
    return deriveArtworkTintTheme(
        buildList {
            for (y in 0 until pixmap.info.height step stepY) {
                for (x in 0 until pixmap.info.width step stepX) {
                    add(pixmap.getColor(x, y))
                }
            }
        },
    )
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
