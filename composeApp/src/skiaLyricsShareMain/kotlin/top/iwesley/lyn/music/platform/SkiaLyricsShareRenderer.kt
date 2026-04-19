package top.iwesley.lyn.music.platform

import kotlin.math.max
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Point
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import top.iwesley.lyn.music.core.model.ArtworkTintTheme
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.argbWithAlpha
import top.iwesley.lyn.music.core.model.buildLyricsShareTitleArtistLine
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme

internal object SkiaLyricsShareRenderer {
    fun render(
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
        val contentWidth = width - LyricsShareCardSpec.OUTER_PADDING_PX * 2f - LyricsShareCardSpec.PAPER_PADDING_HORIZONTAL_PX * 2f
        val footerText = buildLyricsShareTitleArtistLine(model.title, model.artistName)

        val lyricsTypeface = resolveSkiaLyricsShareTypeface(
            text = model.lyricsLines.joinToString(separator = "\n"),
            style = FontStyle.BOLD,
            requestedFamilyName = model.fontFamilyName,
        )
        val footerTypeface = resolveSkiaLyricsShareTypeface(
            text = footerText,
            style = FontStyle.BOLD,
            requestedFamilyName = model.fontFamilyName,
        )
        val brandTypeface = resolveSkiaLyricsShareTypeface(
            text = LyricsShareCardSpec.BRAND_TEXT,
            style = FontStyle.NORMAL,
            requestedFamilyName = model.fontFamilyName,
        )
        val titleFont = Font(footerTypeface, LyricsShareCardSpec.TITLE_FONT_SIZE_PX)
        val brandFont = Font(brandTypeface, LyricsShareCardSpec.BRAND_FONT_SIZE_PX)
        val footerLine = fitSkiaSingleLineWithEllipsis(
            text = footerText,
            font = titleFont,
            maxWidth = contentWidth,
        )
        val titleLineHeight = LyricsShareCardSpec.TITLE_FONT_SIZE_PX + 12f
        val fixedHeight =
            LyricsShareCardSpec.OUTER_PADDING_PX * 2 +
                LyricsShareCardSpec.SHADOW_OFFSET_PX +
                LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                titleLineHeight +
                LyricsShareCardSpec.BRAND_TOP_GAP_PX +
                LyricsShareCardSpec.BRAND_FONT_SIZE_PX +
                LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX
        val fittedLyrics = fitSkiaLyricsLayout(
            lines = model.lyricsLines,
            baseFontSizePx = LyricsShareCardSpec.LYRICS_FONT_SIZE_PX,
            baseLineGapPx = LyricsShareCardSpec.LYRICS_IOS_LINE_GAP_PX,
            maxWidth = contentWidth,
            maxBlockHeight = LyricsShareCardSpec.IMAGE_MAX_HEIGHT_PX - fixedHeight,
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
                titleLineHeight +
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
        canvas.drawString(footerLine, textX, cursorY + LyricsShareCardSpec.TITLE_FONT_SIZE_PX, titleFont, textFooterPaint)
        cursorY += titleLineHeight

        val brandText = LyricsShareCardSpec.BRAND_TEXT
        val brandBounds = brandFont.measureText(brandText)
        val brandWidth = brandBounds.right - brandBounds.left
        val brandX = paperX + (paperWidth - brandWidth) / 2f
        val brandY = paperY + paperHeight - LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX.toFloat()
        canvas.drawString(brandText, brandX, brandY, brandFont, textSecondaryPaint)

        return encodeSkiaSurface(surface)
    }

    private fun renderArtworkTintLyricsShareImage(
        model: LyricsShareCardModel,
        artworkImage: Image?,
    ): ByteArray {
        val width = LyricsShareArtworkTintSpec.IMAGE_WIDTH_PX.toFloat()
        val contentWidth = width - LyricsShareArtworkTintSpec.OUTER_PADDING_PX * 2f
        val footerText = buildLyricsShareTitleArtistLine(model.title, model.artistName)
        val theme = model.artworkTintTheme ?: sampleSkiaLyricsShareArtworkTintTheme(artworkImage)

        val lyricsTypeface = resolveSkiaLyricsShareTypeface(
            text = model.lyricsLines.joinToString(separator = "\n"),
            style = FontStyle.BOLD,
            requestedFamilyName = model.fontFamilyName,
        )
        val footerTypeface = resolveSkiaLyricsShareTypeface(
            text = footerText,
            style = FontStyle.BOLD,
            requestedFamilyName = model.fontFamilyName,
        )
        val brandTypeface = resolveSkiaLyricsShareTypeface(
            text = LyricsShareCardSpec.BRAND_TEXT,
            style = FontStyle.NORMAL,
            requestedFamilyName = model.fontFamilyName,
        )
        val titleFont = Font(footerTypeface, LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX)
        val brandFont = Font(brandTypeface, LyricsShareArtworkTintSpec.BRAND_FONT_SIZE_PX)
        val footerLine = fitSkiaSingleLineWithEllipsis(
            text = footerText,
            font = titleFont,
            maxWidth = contentWidth,
        )
        val titleLineHeight = LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX + 12f
        val fixedHeight =
            LyricsShareArtworkTintSpec.OUTER_PADDING_PX +
                LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX +
                LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX +
                titleLineHeight +
                LyricsShareArtworkTintSpec.BRAND_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.BRAND_FONT_SIZE_PX +
                LyricsShareArtworkTintSpec.OUTER_PADDING_PX
        val fittedLyrics = fitSkiaLyricsLayout(
            lines = model.lyricsLines,
            baseFontSizePx = LyricsShareArtworkTintSpec.LYRICS_FONT_SIZE_PX,
            baseLineGapPx = LyricsShareArtworkTintSpec.LYRICS_IOS_LINE_GAP_PX,
            maxWidth = contentWidth,
            maxBlockHeight = LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX - fixedHeight,
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
                titleLineHeight +
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
        canvas.drawString(footerLine, textX, cursorY + LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX, titleFont, textFooterPaint)
        cursorY += titleLineHeight

        val brandText = LyricsShareCardSpec.BRAND_TEXT
        val brandBounds = brandFont.measureText(brandText)
        val brandWidth = brandBounds.right - brandBounds.left
        val brandX = (width - brandWidth) / 2f
        val brandY = height - LyricsShareArtworkTintSpec.OUTER_PADDING_PX.toFloat()
        canvas.drawString(brandText, brandX, brandY, brandFont, textSecondaryPaint)

        return encodeSkiaSurface(surface)
    }
}

internal fun listSkiaLyricsShareFontFamilyNames(
    fontMgr: FontMgr = FontMgr.default,
): List<String> {
    return (0 until fontMgr.familiesCount)
        .mapNotNull { index -> fontMgr.getFamilyName(index).trim().takeIf { it.isNotEmpty() } }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
}

internal fun resolveSkiaLyricsShareTypeface(
    text: String,
    style: FontStyle,
    requestedFamilyName: String?,
    fontMgr: FontMgr = FontMgr.default,
): Typeface? {
    val requestedFamily = requestedFamilyName?.trim()?.takeIf { it.isNotEmpty() }
    val codePoint = preferredSkiaLyricsShareCodePoint(text)

    val requestedTypeface = when {
        requestedFamily == null -> null
        codePoint != null ->
            fontMgr.matchFamilyStyleCharacter(
                familyName = requestedFamily,
                style = style,
                bcp47 = skiaLyricsShareLanguageHints,
                character = codePoint,
            ) ?: fontMgr.matchFamilyStyle(requestedFamily, style)

        else -> fontMgr.matchFamilyStyle(requestedFamily, style)
    }

    val fallbackTypeface = when {
        codePoint != null ->
            fontMgr.matchFamiliesStyleCharacter(
                families = skiaLyricsShareFallbackFontFamilies,
                style = style,
                bcp47 = skiaLyricsShareLanguageHints,
                character = codePoint,
            ) ?: fontMgr.matchFamiliesStyle(skiaLyricsShareFallbackFontFamilies, style)

        else -> fontMgr.matchFamiliesStyle(skiaLyricsShareFallbackFontFamilies, style)
    }

    val defaultTypeface = when {
        codePoint != null ->
            fontMgr.matchFamilyStyleCharacter(
                familyName = null,
                style = style,
                bcp47 = skiaLyricsShareLanguageHints,
                character = codePoint,
            ) ?: fontMgr.matchFamilyStyle(null, style)

        else -> fontMgr.matchFamilyStyle(null, style)
    }

    return requestedTypeface ?: fallbackTypeface ?: defaultTypeface
}

private data class SkiaFittedLyricsLayout(
    val font: Font,
    val fontSizePx: Float,
    val lineHeight: Float,
    val lines: List<String>,
    val blockHeight: Float,
)

private fun fitSkiaLyricsLayout(
    lines: List<String>,
    baseFontSizePx: Float,
    baseLineGapPx: Float,
    maxWidth: Float,
    maxBlockHeight: Float,
    fixedHeight: Float,
    minFontScale: Float,
    shrinkStep: Float,
    typeface: Typeface?,
): SkiaFittedLyricsLayout {
    var fontSizePx = baseFontSizePx
    var lineGapPx = baseLineGapPx
    val minFontSizePx = (baseFontSizePx * minFontScale).coerceAtLeast(1f)
    while (true) {
        val font = Font(typeface, fontSizePx)
        val wrappedLines = wrapSkiaLyricsShareLines(
            lines = lines,
            font = font,
            maxWidth = maxWidth,
        )
        val lineHeight = fontSizePx + lineGapPx
        val blockHeight = max(1, wrappedLines.size) * lineHeight
        if (blockHeight <= maxBlockHeight || fixedHeight + blockHeight <= fixedHeight || fontSizePx <= minFontSizePx) {
            return SkiaFittedLyricsLayout(
                font = font,
                fontSizePx = fontSizePx,
                lineHeight = lineHeight,
                lines = wrappedLines,
                blockHeight = blockHeight,
            )
        }
        val nextFontSizePx = (fontSizePx * shrinkStep).coerceAtLeast(minFontSizePx)
        if (nextFontSizePx == fontSizePx) {
            return SkiaFittedLyricsLayout(
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

internal fun wrapSkiaLyricsShareLines(
    lines: List<String>,
    font: Font,
    maxWidth: Float,
): List<String> {
    return lines.flatMap { line ->
        wrapSingleSkiaLyricsShareLine(
            text = line,
            font = font,
            maxWidth = maxWidth,
        )
    }
}

internal fun fitSkiaSingleLineWithEllipsis(
    text: String,
    font: Font,
    maxWidth: Float,
): String {
    val normalized = text.trim().ifBlank { " " }
    if (measureSkiaTextWidth(font, normalized) <= maxWidth) return normalized
    val ellipsis = "…"
    if (measureSkiaTextWidth(font, ellipsis) >= maxWidth) return ellipsis

    val tokens = tokenizeSkiaLyricsShareLine(normalized)
    val builder = StringBuilder()
    var best = ellipsis
    for (token in tokens) {
        builder.append(token.text)
        val candidate = builder.toString().trimEnd()
        if (candidate.isEmpty()) continue
        val candidateWithEllipsis = candidate + ellipsis
        if (measureSkiaTextWidth(font, candidateWithEllipsis) <= maxWidth) {
            best = candidateWithEllipsis
            continue
        }
        if (token.kind == SkiaLyricsShareTokenKind.WORD) {
            val fallback = fitSkiaWordTokenWithEllipsis(candidate, font, maxWidth)
            if (fallback != null) best = fallback
        }
        break
    }
    return best
}

internal fun tokenizeSkiaLyricsShareLine(
    text: String,
): List<SkiaLyricsShareToken> {
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<SkiaLyricsShareToken>()
    val buffer = StringBuilder()
    var bufferKind: SkiaLyricsShareTokenKind? = null

    fun flushBuffer() {
        if (buffer.isEmpty()) return
        result += SkiaLyricsShareToken(buffer.toString(), checkNotNull(bufferKind))
        buffer.setLength(0)
        bufferKind = null
    }

    text.forEach { char ->
        val kind = classifySkiaLyricsShareChar(char)
        when (kind) {
            SkiaLyricsShareTokenKind.WORD -> {
                if (bufferKind == SkiaLyricsShareTokenKind.WORD) {
                    buffer.append(char)
                } else {
                    flushBuffer()
                    bufferKind = SkiaLyricsShareTokenKind.WORD
                    buffer.append(char)
                }
            }

            SkiaLyricsShareTokenKind.WHITESPACE,
            SkiaLyricsShareTokenKind.CJK,
            SkiaLyricsShareTokenKind.OTHER -> {
                flushBuffer()
                result += SkiaLyricsShareToken(char.toString(), kind)
            }
        }
    }
    flushBuffer()
    return result
}

internal fun wrapSingleSkiaLyricsShareLine(
    text: String,
    font: Font,
    maxWidth: Float,
): List<String> {
    val normalized = text.trim().ifBlank { " " }
    if (measureSkiaTextWidth(font, normalized) <= maxWidth) return listOf(normalized)

    val tokens = tokenizeSkiaLyricsShareLine(normalized)
    val lines = mutableListOf<String>()
    val current = StringBuilder()

    fun appendCurrentLine() {
        if (current.isEmpty()) return
        lines += current.toString().trimEnd()
        current.setLength(0)
    }

    fun appendToken(tokenText: String) {
        current.append(tokenText)
    }

    fun canAppend(tokenText: String): Boolean {
        val candidate = current.toString() + tokenText
        return measureSkiaTextWidth(font, candidate) <= maxWidth
    }

    tokens.forEach { token ->
        when (token.kind) {
            SkiaLyricsShareTokenKind.WHITESPACE -> {
                if (current.isNotEmpty() && canAppend(token.text)) {
                    appendToken(token.text)
                } else if (current.isNotEmpty()) {
                    appendCurrentLine()
                }
            }

            SkiaLyricsShareTokenKind.WORD -> {
                when {
                    current.isEmpty() && measureSkiaTextWidth(font, token.text) <= maxWidth -> appendToken(token.text)
                    current.isNotEmpty() && canAppend(token.text) -> appendToken(token.text)
                    measureSkiaTextWidth(font, token.text) <= maxWidth -> {
                        appendCurrentLine()
                        appendToken(token.text)
                    }

                    else -> {
                        appendCurrentLine()
                        val splitWordLines = wrapOversizedSkiaWordToken(token.text, font, maxWidth)
                        if (splitWordLines.isNotEmpty()) {
                            lines += splitWordLines.dropLast(1)
                            current.append(splitWordLines.last())
                        }
                    }
                }
            }

            SkiaLyricsShareTokenKind.CJK,
            SkiaLyricsShareTokenKind.OTHER -> {
                when {
                    current.isEmpty() -> appendToken(token.text)
                    canAppend(token.text) -> appendToken(token.text)
                    else -> {
                        appendCurrentLine()
                        appendToken(token.text)
                    }
                }
            }
        }
    }

    appendCurrentLine()
    return lines.ifEmpty { listOf(" ") }
}

private fun wrapOversizedSkiaWordToken(
    word: String,
    font: Font,
    maxWidth: Float,
): List<String> {
    val lines = mutableListOf<String>()
    val current = StringBuilder()
    word.forEach { char ->
        val candidate = current.toString() + char
        if (current.isNotEmpty() && measureSkiaTextWidth(font, candidate) > maxWidth) {
            lines += current.toString()
            current.setLength(0)
        }
        current.append(char)
    }
    if (current.isNotEmpty()) {
        lines += current.toString()
    }
    return lines.ifEmpty { listOf(word) }
}

private fun fitSkiaWordTokenWithEllipsis(
    text: String,
    font: Font,
    maxWidth: Float,
): String? {
    val ellipsis = "…"
    if (measureSkiaTextWidth(font, ellipsis) >= maxWidth) return ellipsis
    val chars = text.trimEnd().toCharArray()
    for (index in chars.indices.reversed()) {
        val candidate = chars.concatToString(0, index + 1).trimEnd() + ellipsis
        if (candidate != ellipsis && measureSkiaTextWidth(font, candidate) <= maxWidth) {
            return candidate
        }
    }
    return ellipsis
}

private fun measureSkiaTextWidth(
    font: Font,
    text: String,
): Float {
    val bounds = font.measureText(text)
    return bounds.right - bounds.left
}

private fun classifySkiaLyricsShareChar(
    char: Char,
): SkiaLyricsShareTokenKind {
    return when {
        char.isWhitespace() -> SkiaLyricsShareTokenKind.WHITESPACE
        char.isAsciiWordChar() -> SkiaLyricsShareTokenKind.WORD
        char.isCjkChar() -> SkiaLyricsShareTokenKind.CJK
        else -> SkiaLyricsShareTokenKind.OTHER
    }
}

private fun Char.isAsciiWordChar(): Boolean {
    return this.isLetterOrDigit() && this.code <= 0x7F || this == '\'' || this == '-'
}

private fun Char.isCjkChar(): Boolean {
    val code = this.code
    return code in 0x4E00..0x9FFF ||
        code in 0x3400..0x4DBF ||
        code in 0x3040..0x30FF ||
        code in 0xAC00..0xD7AF
}

internal data class SkiaLyricsShareToken(
    val text: String,
    val kind: SkiaLyricsShareTokenKind,
)

internal enum class SkiaLyricsShareTokenKind {
    WORD,
    WHITESPACE,
    CJK,
    OTHER,
}

private fun preferredSkiaLyricsShareCodePoint(text: String): Int? {
    val trimmed = text.trim()
    val nonAscii = trimmed.firstOrNull { !it.isWhitespace() && it.code > 0x7F }
    return nonAscii?.code ?: trimmed.firstOrNull { !it.isWhitespace() }?.code
}

private fun sampleSkiaLyricsShareArtworkTintTheme(
    artworkImage: Image?,
): ArtworkTintTheme? {
    val image = artworkImage ?: return null
    val bitmap = runCatching { Bitmap.makeFromImage(image) }.getOrNull() ?: return null
    return try {
        val stepX = max(1, bitmap.imageInfo.width / 24)
        val stepY = max(1, bitmap.imageInfo.height / 24)
        deriveArtworkTintTheme(
            buildList {
                for (y in 0 until bitmap.imageInfo.height step stepY) {
                    for (x in 0 until bitmap.imageInfo.width step stepX) {
                        add(bitmap.getColor(x, y))
                    }
                }
            },
        )
    } finally {
        bitmap.close()
    }
}

private fun encodeSkiaSurface(surface: Surface): ByteArray {
    val encoded = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG, 100)
        ?: error("无法导出 PNG 数据。")
    return encoded.bytes
}
