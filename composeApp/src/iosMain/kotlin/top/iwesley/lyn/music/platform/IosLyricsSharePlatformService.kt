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
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.argbWithAlpha
import top.iwesley.lyn.music.core.model.buildLyricsShareTitleArtistLine
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Point
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface

class IosLyricsSharePlatformService : LyricsSharePlatformService {
    private val artworkCacheStore = createIosArtworkCacheStore()

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            val artworkImage = loadArtworkImage(model.artworkLocator, artworkCacheStore)
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

    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> {
        return Result.failure(IllegalStateException("当前平台暂不支持读取系统字体。"))
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
        val footerText = buildLyricsShareTitleArtistLine(model.title, model.artistName)
        val footerLines = wrapLines(
            listOf(footerText),
            maxCharsPerLine = 28,
        ).take(1)

        val lyricsTypeface = resolveIosShareTypeface(
            text = model.lyricsLines.joinToString(separator = "\n"),
            style = FontStyle.BOLD,
        )
        val footerTypeface = resolveIosShareTypeface(
            text = footerText,
            style = FontStyle.BOLD,
        )
        val brandTypeface = resolveIosShareTypeface(
            text = LyricsShareCardSpec.BRAND_TEXT,
            style = FontStyle.NORMAL,
        )
        val titleFont = Font(footerTypeface, LyricsShareCardSpec.TITLE_FONT_SIZE_PX)
        val brandFont = Font(brandTypeface, LyricsShareCardSpec.BRAND_FONT_SIZE_PX)
        val titleLineHeight = LyricsShareCardSpec.TITLE_FONT_SIZE_PX + 12f
        val fixedHeight =
            LyricsShareCardSpec.OUTER_PADDING_PX * 2 +
                LyricsShareCardSpec.SHADOW_OFFSET_PX +
                LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                max(1, footerLines.size) * titleLineHeight +
                LyricsShareCardSpec.BRAND_TOP_GAP_PX +
                LyricsShareCardSpec.BRAND_FONT_SIZE_PX +
                LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX
        val fittedLyrics = fitIosLyricsLayout(
            lines = model.lyricsLines,
            baseMaxCharsPerLine = 12,
            baseFontSizePx = LyricsShareCardSpec.LYRICS_FONT_SIZE_PX,
            baseLineGapPx = LyricsShareCardSpec.LYRICS_IOS_LINE_GAP_PX,
            maxTotalHeight = LyricsShareCardSpec.IMAGE_MAX_HEIGHT_PX,
            fixedHeight = fixedHeight,
            minFontScale = LyricsShareCardSpec.LYRICS_MIN_FONT_SCALE,
            shrinkStep = LyricsShareCardSpec.LYRICS_FONT_SHRINK_STEP,
            typeface = lyricsTypeface,
        )
        val contentHeight =
            LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                fittedLyrics.blockHeight +
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
        var cursorY = artworkY + LyricsShareCardSpec.ARTWORK_SIZE_PX + LyricsShareCardSpec.LYRICS_TOP_GAP_PX + fittedLyrics.fontSizePx
        fittedLyrics.lines.forEach { line ->
            canvas.drawString(line, textX, cursorY, fittedLyrics.font, textPrimaryPaint)
            cursorY += fittedLyrics.lineHeight
        }
        cursorY += LyricsShareCardSpec.FOOTER_TOP_GAP_PX - fittedLyrics.fontSizePx
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
        val footerText = buildLyricsShareTitleArtistLine(model.title, model.artistName)
        val footerLines = wrapLines(
            listOf(footerText),
            maxCharsPerLine = 28,
        ).take(1)
        val theme = model.artworkTintTheme ?: sampleArtworkTintTheme(artworkImage)

        val lyricsTypeface = resolveIosShareTypeface(
            text = model.lyricsLines.joinToString(separator = "\n"),
            style = FontStyle.BOLD,
        )
        val footerTypeface = resolveIosShareTypeface(
            text = footerText,
            style = FontStyle.BOLD,
        )
        val brandTypeface = resolveIosShareTypeface(
            text = LyricsShareCardSpec.BRAND_TEXT,
            style = FontStyle.NORMAL,
        )
        val titleFont = Font(footerTypeface, LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX)
        val brandFont = Font(brandTypeface, LyricsShareArtworkTintSpec.BRAND_FONT_SIZE_PX)
        val titleLineHeight = LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX + 12f
        val fixedHeight =
            LyricsShareArtworkTintSpec.OUTER_PADDING_PX +
                LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX +
                LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX +
                max(1, footerLines.size) * titleLineHeight +
                LyricsShareArtworkTintSpec.BRAND_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.BRAND_FONT_SIZE_PX +
                LyricsShareArtworkTintSpec.OUTER_PADDING_PX
        val fittedLyrics = fitIosLyricsLayout(
            lines = model.lyricsLines,
            baseMaxCharsPerLine = 12,
            baseFontSizePx = LyricsShareArtworkTintSpec.LYRICS_FONT_SIZE_PX,
            baseLineGapPx = LyricsShareArtworkTintSpec.LYRICS_IOS_LINE_GAP_PX,
            maxTotalHeight = LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX,
            fixedHeight = fixedHeight,
            minFontScale = LyricsShareArtworkTintSpec.LYRICS_MIN_FONT_SCALE,
            shrinkStep = LyricsShareArtworkTintSpec.LYRICS_FONT_SHRINK_STEP,
            typeface = lyricsTypeface,
        )
        val contentHeight =
            LyricsShareArtworkTintSpec.OUTER_PADDING_PX +
                LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX +
                LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX +
                fittedLyrics.blockHeight +
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
        var cursorY = artworkY + LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX + LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX + fittedLyrics.fontSizePx
        fittedLyrics.lines.forEach { line ->
            canvas.drawString(line, textX, cursorY, fittedLyrics.font, textPrimaryPaint)
            cursorY += fittedLyrics.lineHeight
        }
        cursorY += LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX - fittedLyrics.fontSizePx
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

private data class IosFittedLyricsLayout(
    val font: Font,
    val fontSizePx: Float,
    val lineHeight: Float,
    val lines: List<String>,
    val blockHeight: Float,
)

private fun fitIosLyricsLayout(
    lines: List<String>,
    baseMaxCharsPerLine: Int,
    baseFontSizePx: Float,
    baseLineGapPx: Float,
    maxTotalHeight: Int,
    fixedHeight: Float,
    minFontScale: Float,
    shrinkStep: Float,
    typeface: Typeface?,
): IosFittedLyricsLayout {
    var fontSizePx = baseFontSizePx
    var lineGapPx = baseLineGapPx
    val minFontSizePx = (baseFontSizePx * minFontScale).coerceAtLeast(1f)
    while (true) {
        val scale = (fontSizePx / baseFontSizePx).coerceAtLeast(0.01f)
        val maxCharsPerLine = (baseMaxCharsPerLine / scale).toInt().coerceAtLeast(baseMaxCharsPerLine)
        val wrappedLines = wrapLines(lines, maxCharsPerLine = maxCharsPerLine)
        val lineHeight = fontSizePx + lineGapPx
        val blockHeight = max(1, wrappedLines.size) * lineHeight
        val font = Font(typeface, fontSizePx)
        if (fixedHeight + blockHeight <= maxTotalHeight || fontSizePx <= minFontSizePx) {
            return IosFittedLyricsLayout(
                font = font,
                fontSizePx = fontSizePx,
                lineHeight = lineHeight,
                lines = wrappedLines,
                blockHeight = blockHeight,
            )
        }
        val nextFontSizePx = (fontSizePx * shrinkStep).coerceAtLeast(minFontSizePx)
        if (nextFontSizePx == fontSizePx) {
            return IosFittedLyricsLayout(
                font = font,
                fontSizePx = fontSizePx,
                lineHeight = lineHeight,
                lines = wrappedLines,
                blockHeight = blockHeight,
            )
        }
        fontSizePx = nextFontSizePx
        lineGapPx = (lineGapPx * shrinkStep).coerceAtLeast(0f)
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

private fun resolveIosShareTypeface(
    text: String,
    style: FontStyle,
): Typeface? {
    val codePoint = preferredIosShareCodePoint(text)
    return when {
        codePoint != null ->
            FontMgr.default.matchFamiliesStyleCharacter(
                families = IOS_SHARE_FONT_FAMILIES,
                style = style,
                bcp47 = IOS_SHARE_LANGUAGE_HINTS,
                character = codePoint,
            ) ?: FontMgr.default.matchFamilyStyleCharacter(
                familyName = null,
                style = style,
                bcp47 = IOS_SHARE_LANGUAGE_HINTS,
                character = codePoint,
            )

        else -> null
    } ?: FontMgr.default.matchFamiliesStyle(IOS_SHARE_FONT_FAMILIES, style)
        ?: FontMgr.default.matchFamilyStyle(null, style)
}

private fun preferredIosShareCodePoint(text: String): Int? {
    val trimmed = text.trim()
    val nonAscii = trimmed.firstOrNull { !it.isWhitespace() && it.code > 0x7F }
    return nonAscii?.code ?: trimmed.firstOrNull { !it.isWhitespace() }?.code
}

private val IOS_SHARE_LANGUAGE_HINTS = arrayOf("zh-Hans", "zh-Hant", "ja", "ko", "en")

private val IOS_SHARE_FONT_FAMILIES = arrayOf<String?>(
    "PingFang SC",
    "PingFang TC",
    "PingFang HK",
    "Hiragino Sans GB",
    "Hiragino Sans",
    "STHeiti",
    "Heiti SC",
    "Heiti TC",
    "Apple SD Gothic Neo",
    "Arial Unicode MS",
)

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

private fun sampleArtworkTintTheme(artworkImage: Image?): top.iwesley.lyn.music.core.model.ArtworkTintTheme? {
    val image = artworkImage ?: return null
    val bitmap = runCatching { Bitmap.makeFromImage(image) }.getOrNull() ?: return null
    val stepX = max(1, bitmap.imageInfo.width / 24)
    val stepY = max(1, bitmap.imageInfo.height / 24)
    return deriveArtworkTintTheme(
        buildList {
            for (y in 0 until bitmap.imageInfo.height step stepY) {
                for (x in 0 until bitmap.imageInfo.width step stepX) {
                    add(bitmap.getColor(x, y))
                }
            }
        },
    ).also {
        bitmap.close()
    }
}

private fun writeTempPng(pngBytes: ByteArray): String {
    val path = NSTemporaryDirectory() + "lynmusic-lyrics-share.png"
    if (!writeIosFileBytes(path, pngBytes)) {
        error("无法创建临时图片文件。")
    }
    return path
}
