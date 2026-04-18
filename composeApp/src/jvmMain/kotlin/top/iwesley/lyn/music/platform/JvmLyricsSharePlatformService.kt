package top.iwesley.lyn.music.platform

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Image
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.geom.AffineTransform
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_FAMILY
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.argbWithAlpha
import top.iwesley.lyn.music.core.model.buildLyricsShareTitleArtistLine
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import java.awt.GraphicsEnvironment
import kotlin.math.max
import kotlin.math.roundToInt

class JvmLyricsSharePlatformService : LyricsSharePlatformService {
    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val artwork = loadArtworkImage(model.artworkLocator)
            renderLyricsShareImage(model, artwork)
        }
    }

    override suspend fun saveImage(
        pngBytes: ByteArray,
        suggestedName: String,
    ): Result<LyricsShareSaveResult> {
        return runCatching {
            val output = JvmNativeFilePicker.pickSaveFile(
                title = "保存歌词图片",
                suggestedName = ensurePngFileName(suggestedName),
                defaultExtension = "png",
            ) ?: error("已取消保存。")
            withContext(Dispatchers.IO) {
                output.parent?.toFile()?.mkdirs()
                output.toFile().writeBytes(pngBytes)
            }
            LyricsShareSaveResult(message = "图片已保存到文件")
        }
    }

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> = withContext(Dispatchers.Swing) {
        runCatching {
            val image = ImageIO.read(ByteArrayInputStream(pngBytes)) ?: error("无法读取图片数据。")
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(ImageTransferable(image), null)
        }
    }

    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> = withContext(Dispatchers.IO) {
        runCatching {
            filterJvmLyricsShareFontFamilyNames(
                osName = System.getProperty("os.name").orEmpty(),
                availableFonts = listJvmFontFamilyNames(),
            )
        }
    }

    private suspend fun loadArtworkImage(locator: String?): BufferedImage? {
        val artworkBytes = loadJvmArtworkBytes(locator)
        return artworkBytes?.let(::ByteArrayInputStream)?.use(ImageIO::read)
    }

    private fun renderLyricsShareImage(
        model: LyricsShareCardModel,
        artworkImage: BufferedImage?,
    ): ByteArray {
        return when (model.template) {
            LyricsShareTemplate.NOTE -> renderNoteLyricsShareImage(model, artworkImage)
            LyricsShareTemplate.ARTWORK_TINT -> renderArtworkTintLyricsShareImage(model, artworkImage)
        }
    }

    private fun renderNoteLyricsShareImage(
        model: LyricsShareCardModel,
        artworkImage: BufferedImage?,
    ): ByteArray {
        val fontFamilyName = resolveJvmLyricsShareFontFamilyName(model.fontFamilyName)
        val width = LyricsShareCardSpec.IMAGE_WIDTH_PX
        val titleFont = buildJvmLyricsShareFont(fontFamilyName, Font.BOLD, LyricsShareCardSpec.TITLE_FONT_SIZE_PX)
        val brandFont = buildJvmLyricsShareFont(fontFamilyName, Font.PLAIN, LyricsShareCardSpec.BRAND_FONT_SIZE_PX)
        val probe = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).createGraphics()
        val contentWidth = width - LyricsShareCardSpec.OUTER_PADDING_PX * 2 - LyricsShareCardSpec.PAPER_PADDING_HORIZONTAL_PX * 2
        val wrappedFooterLine = wrapSingleBlock(
            buildLyricsShareTitleArtistLine(model.title, model.artistName),
            probe,
            titleFont,
            contentWidth,
            1,
        )
        val titleLineHeight = probe.getFontMetrics(titleFont).height + 4
        val brandLineHeight = probe.getFontMetrics(brandFont).height
        val maxLyricsBlockHeight =
            LyricsShareCardSpec.IMAGE_MAX_HEIGHT_PX -
                (
                    LyricsShareCardSpec.OUTER_PADDING_PX * 2 +
                        LyricsShareCardSpec.SHADOW_OFFSET_PX +
                        LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                        LyricsShareCardSpec.ARTWORK_SIZE_PX +
                        LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                        LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                        max(1, wrappedFooterLine.size) * titleLineHeight +
                        LyricsShareCardSpec.BRAND_TOP_GAP_PX +
                        brandLineHeight +
                        LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX
                    )
        val fittedLyrics = fitJvmLyricsLayout(
            lines = model.lyricsLines,
            graphics = probe,
            fontFamily = fontFamilyName,
            fontStyle = Font.BOLD,
            baseFontSizePx = LyricsShareCardSpec.LYRICS_FONT_SIZE_PX,
            baseLineGapPx = LyricsShareCardSpec.LYRICS_JVM_LINE_GAP_PX,
            maxWidth = contentWidth,
            maxBlockHeight = maxLyricsBlockHeight,
            minFontScale = LyricsShareCardSpec.LYRICS_MIN_FONT_SCALE,
            shrinkStep = LyricsShareCardSpec.LYRICS_FONT_SHRINK_STEP,
        )
        probe.dispose()

        val contentHeight =
            LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                fittedLyrics.blockHeight +
                LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                max(1, wrappedFooterLine.size) * titleLineHeight +
                LyricsShareCardSpec.BRAND_TOP_GAP_PX +
                brandLineHeight +
                LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX
        val height = (contentHeight + LyricsShareCardSpec.OUTER_PADDING_PX * 2 + LyricsShareCardSpec.SHADOW_OFFSET_PX)
            .coerceIn(LyricsShareCardSpec.IMAGE_MIN_HEIGHT_PX, LyricsShareCardSpec.IMAGE_MAX_HEIGHT_PX)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        configureGraphics(graphics)

        val canvasColor = awtColor(LyricsShareCardSpec.CANVAS_BACKGROUND_ARGB)
        val paperColor = awtColor(LyricsShareCardSpec.PAPER_BACKGROUND_ARGB)
        val shadowColor = awtColor(LyricsShareCardSpec.PAPER_SHADOW_ARGB)
        val tapeColor = awtColor(LyricsShareCardSpec.TAPE_ARGB)
        val textPrimary = awtColor(LyricsShareCardSpec.TEXT_PRIMARY_ARGB)
        val textFooter = awtColor(LyricsShareCardSpec.TEXT_FOOTER_ARGB)
        val textSecondary = awtColor(LyricsShareCardSpec.TEXT_SECONDARY_ARGB)
        val placeholder = awtColor(LyricsShareCardSpec.PLACEHOLDER_ARGB)

        graphics.color = canvasColor
        graphics.fillRect(0, 0, width, height)

        val paperX = LyricsShareCardSpec.OUTER_PADDING_PX.toFloat()
        val paperY = LyricsShareCardSpec.OUTER_PADDING_PX.toFloat()
        val paperWidth = (width - LyricsShareCardSpec.OUTER_PADDING_PX * 2).toFloat()
        val paperHeight = (height - LyricsShareCardSpec.OUTER_PADDING_PX * 2 - LyricsShareCardSpec.SHADOW_OFFSET_PX).toFloat()
        val shadowShape = RoundRectangle2D.Float(
            paperX,
            paperY + LyricsShareCardSpec.SHADOW_OFFSET_PX,
            paperWidth,
            paperHeight,
            LyricsShareCardSpec.PAPER_RADIUS_PX.toFloat(),
            LyricsShareCardSpec.PAPER_RADIUS_PX.toFloat(),
        )
        graphics.color = shadowColor
        graphics.fill(shadowShape)
        val paperShape = RoundRectangle2D.Float(
            paperX,
            paperY,
            paperWidth,
            paperHeight,
            LyricsShareCardSpec.PAPER_RADIUS_PX.toFloat(),
            LyricsShareCardSpec.PAPER_RADIUS_PX.toFloat(),
        )
        graphics.color = paperColor
        graphics.fill(paperShape)

        val tapeTransform = graphics.transform
        graphics.transform = AffineTransform().apply {
            rotate(Math.toRadians(-8.0), paperX + 132.0, paperY + 8.0)
        }
        graphics.composite = AlphaComposite.SrcOver.derive(0.75f)
        graphics.color = tapeColor
        graphics.fillRoundRect(
            (paperX + 40).toInt(),
            (paperY - 8).toInt(),
            LyricsShareCardSpec.TAPE_WIDTH_PX,
            LyricsShareCardSpec.TAPE_HEIGHT_PX,
            14,
            14,
        )
        graphics.composite = AlphaComposite.SrcOver
        graphics.transform = tapeTransform

        val artworkX = (paperX + LyricsShareCardSpec.PAPER_PADDING_HORIZONTAL_PX).toInt()
        val artworkY = (paperY + LyricsShareCardSpec.PAPER_PADDING_TOP_PX).toInt()
        val clip = graphics.clip
        val artworkShape = RoundRectangle2D.Float(
            artworkX.toFloat(),
            artworkY.toFloat(),
            LyricsShareCardSpec.ARTWORK_SIZE_PX.toFloat(),
            LyricsShareCardSpec.ARTWORK_SIZE_PX.toFloat(),
            32f,
            32f,
        )
        graphics.clip = artworkShape
        if (artworkImage != null) {
            graphics.drawImage(
                artworkImage,
                artworkX,
                artworkY,
                artworkX + LyricsShareCardSpec.ARTWORK_SIZE_PX,
                artworkY + LyricsShareCardSpec.ARTWORK_SIZE_PX,
                0,
                0,
                artworkImage.width,
                artworkImage.height,
                null,
            )
        } else {
            graphics.color = placeholder
            graphics.fill(artworkShape)
        }
        graphics.clip = clip

        val textX = artworkX
        var cursorY = artworkY + LyricsShareCardSpec.ARTWORK_SIZE_PX + LyricsShareCardSpec.LYRICS_TOP_GAP_PX

        graphics.color = textPrimary
        graphics.font = fittedLyrics.font
        val lyricsMetrics = graphics.fontMetrics
        fittedLyrics.lines.forEach { line ->
            cursorY += lyricsMetrics.ascent
            graphics.drawString(line, textX, cursorY)
            cursorY += lyricsMetrics.descent + lyricsMetrics.leading + fittedLyrics.lineGapPx
        }

        cursorY += LyricsShareCardSpec.FOOTER_TOP_GAP_PX
        graphics.color = textFooter
        graphics.font = titleFont
        val titleMetrics = graphics.fontMetrics
        wrappedFooterLine.forEach { line ->
            cursorY += titleMetrics.ascent
            graphics.drawString(line, textX, cursorY)
            cursorY += titleMetrics.descent + titleMetrics.leading + 4
        }

        graphics.color = textSecondary
        graphics.font = brandFont
        val brandMetrics = graphics.fontMetrics
        val brandText = LyricsShareCardSpec.BRAND_TEXT
        val brandX = (paperX + (paperWidth - brandMetrics.stringWidth(brandText)) / 2f).toInt()
        val brandY = (paperY + paperHeight - LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX - brandMetrics.descent).toInt()
        graphics.drawString(brandText, brandX, brandY)

        graphics.dispose()
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }

    private fun renderArtworkTintLyricsShareImage(
        model: LyricsShareCardModel,
        artworkImage: BufferedImage?,
    ): ByteArray {
        val fontFamilyName = resolveJvmLyricsShareFontFamilyName(model.fontFamilyName)
        val width = LyricsShareArtworkTintSpec.IMAGE_WIDTH_PX
        val titleFont = buildJvmLyricsShareFont(fontFamilyName, Font.BOLD, LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX)
        val brandFont = buildJvmLyricsShareFont(fontFamilyName, Font.PLAIN, LyricsShareArtworkTintSpec.BRAND_FONT_SIZE_PX)
        val probe = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).createGraphics()
        val contentWidth = width - LyricsShareArtworkTintSpec.OUTER_PADDING_PX * 2
        val wrappedFooterLine = wrapSingleBlock(
            buildLyricsShareTitleArtistLine(model.title, model.artistName),
            probe,
            titleFont,
            contentWidth,
            1,
        )
        val titleLineHeight = probe.getFontMetrics(titleFont).height + 4
        val brandLineHeight = probe.getFontMetrics(brandFont).height
        val maxLyricsBlockHeight =
            LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX -
                (
                    LyricsShareArtworkTintSpec.OUTER_PADDING_PX +
                        LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX +
                        LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX +
                        LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX +
                        LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX +
                        max(1, wrappedFooterLine.size) * titleLineHeight +
                        LyricsShareArtworkTintSpec.BRAND_TOP_GAP_PX +
                        brandLineHeight +
                        LyricsShareArtworkTintSpec.OUTER_PADDING_PX
                    )
        val fittedLyrics = fitJvmLyricsLayout(
            lines = model.lyricsLines,
            graphics = probe,
            fontFamily = fontFamilyName,
            fontStyle = Font.BOLD,
            baseFontSizePx = LyricsShareArtworkTintSpec.LYRICS_FONT_SIZE_PX,
            baseLineGapPx = LyricsShareArtworkTintSpec.LYRICS_JVM_LINE_GAP_PX,
            maxWidth = contentWidth,
            maxBlockHeight = maxLyricsBlockHeight,
            minFontScale = LyricsShareArtworkTintSpec.LYRICS_MIN_FONT_SCALE,
            shrinkStep = LyricsShareArtworkTintSpec.LYRICS_FONT_SHRINK_STEP,
        )
        probe.dispose()

        val contentHeight =
            LyricsShareArtworkTintSpec.OUTER_PADDING_PX +
                LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX +
                LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX +
                fittedLyrics.blockHeight +
                LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX +
                max(1, wrappedFooterLine.size) * titleLineHeight +
                LyricsShareArtworkTintSpec.BRAND_TOP_GAP_PX +
                brandLineHeight +
                LyricsShareArtworkTintSpec.OUTER_PADDING_PX
        val height = contentHeight
            .coerceIn(LyricsShareArtworkTintSpec.IMAGE_MIN_HEIGHT_PX, LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX)

        val theme = model.artworkTintTheme ?: sampleArtworkTintTheme(artworkImage)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        configureGraphics(graphics)

        val backgroundColor = awtColor(LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB)
        val topTint = awtColor(
            argbWithAlpha(
                theme?.innerGlowColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
                if (theme != null) 0.22f else 0f,
            ),
        )
        val midTint = awtColor(
            argbWithAlpha(
                theme?.glowColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
                if (theme != null) 0.18f else 0f,
            ),
        )
        val accentTint = awtColor(
            argbWithAlpha(
                theme?.rimColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
                if (theme != null) 0.12f else 0f,
            ),
        )
        val textPrimary = awtColor(LyricsShareArtworkTintSpec.TEXT_PRIMARY_ARGB)
        val textFooter = awtColor(LyricsShareArtworkTintSpec.TEXT_FOOTER_ARGB)
        val textSecondary = awtColor(LyricsShareArtworkTintSpec.TEXT_SECONDARY_ARGB)
        val placeholder = awtColor(LyricsShareArtworkTintSpec.PLACEHOLDER_ARGB)
        val artworkShadow = awtColor(LyricsShareArtworkTintSpec.ARTWORK_SHADOW_ARGB)
        val transparent = Color(0, 0, 0, 0)

        graphics.color = backgroundColor
        graphics.fillRect(0, 0, width, height)
        graphics.paint = LinearGradientPaint(
            0f,
            0f,
            0f,
            height.toFloat(),
            floatArrayOf(0f, 0.46f, 1f),
            arrayOf(topTint, midTint, transparent),
        )
        graphics.fillRect(0, 0, width, height)
        graphics.paint = LinearGradientPaint(
            0f,
            height * 0.22f,
            width.toFloat(),
            height.toFloat(),
            floatArrayOf(0f, 0.58f, 1f),
            arrayOf(transparent, accentTint, transparent),
        )
        graphics.fillRect(0, 0, width, height)

        val artworkX = LyricsShareArtworkTintSpec.OUTER_PADDING_PX
        val artworkY = LyricsShareArtworkTintSpec.OUTER_PADDING_PX + LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX
        val artworkShape = RoundRectangle2D.Float(
            artworkX.toFloat(),
            artworkY.toFloat(),
            LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
            LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
            LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
            LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
        )
        val shadowShape = RoundRectangle2D.Float(
            artworkX.toFloat(),
            artworkY.toFloat() + 10f,
            LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
            LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX.toFloat(),
            LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
            LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
        )
        graphics.color = artworkShadow
        graphics.fill(shadowShape)
        val clip = graphics.clip
        graphics.clip = artworkShape
        if (artworkImage != null) {
            graphics.drawImage(
                artworkImage,
                artworkX,
                artworkY,
                artworkX + LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX,
                artworkY + LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX,
                0,
                0,
                artworkImage.width,
                artworkImage.height,
                null,
            )
        } else {
            graphics.color = placeholder
            graphics.fill(artworkShape)
        }
        graphics.clip = clip

        val textX = artworkX
        var cursorY = artworkY + LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX + LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX
        graphics.color = textPrimary
        graphics.font = fittedLyrics.font
        val lyricsMetrics = graphics.fontMetrics
        fittedLyrics.lines.forEach { line ->
            cursorY += lyricsMetrics.ascent
            graphics.drawString(line, textX, cursorY)
            cursorY += lyricsMetrics.descent + lyricsMetrics.leading + fittedLyrics.lineGapPx
        }

        cursorY += LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX
        graphics.color = textFooter
        graphics.font = titleFont
        val titleMetrics = graphics.fontMetrics
        wrappedFooterLine.forEach { line ->
            cursorY += titleMetrics.ascent
            graphics.drawString(line, textX, cursorY)
            cursorY += titleMetrics.descent + titleMetrics.leading + 4
        }

        graphics.color = textSecondary
        graphics.font = brandFont
        val brandMetrics = graphics.fontMetrics
        val brandText = LyricsShareCardSpec.BRAND_TEXT
        val brandX = ((width - brandMetrics.stringWidth(brandText)) / 2f).toInt()
        val brandY = height - LyricsShareArtworkTintSpec.OUTER_PADDING_PX - brandMetrics.descent
        graphics.drawString(brandText, brandX, brandY)

        graphics.dispose()
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}

private fun configureGraphics(graphics: Graphics2D) {
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
}

private fun awtColor(argb: Int): Color {
    return Color(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
        (argb ushr 24) and 0xFF,
    )
}

private fun sampleArtworkTintTheme(artworkImage: BufferedImage?): top.iwesley.lyn.music.core.model.ArtworkTintTheme? {
    if (artworkImage == null) return null
    val stepX = max(1, artworkImage.width / 24)
    val stepY = max(1, artworkImage.height / 24)
    return deriveArtworkTintTheme(
        buildList {
            for (y in 0 until artworkImage.height step stepY) {
                for (x in 0 until artworkImage.width step stepX) {
                    add(artworkImage.getRGB(x, y))
                }
            }
        },
    )
}

private data class JvmFittedLyricsLayout(
    val font: Font,
    val lines: List<String>,
    val lineGapPx: Int,
    val blockHeight: Int,
)

private fun fitJvmLyricsLayout(
    lines: List<String>,
    graphics: Graphics2D,
    fontFamily: String,
    fontStyle: Int,
    baseFontSizePx: Float,
    baseLineGapPx: Int,
    maxWidth: Int,
    maxBlockHeight: Int,
    minFontScale: Float,
    shrinkStep: Float,
): JvmFittedLyricsLayout {
    var fontSizePx = baseFontSizePx
    var lineGapPx = baseLineGapPx.toFloat()
    val minFontSizePx = (baseFontSizePx * minFontScale).coerceAtLeast(1f)
    while (true) {
        val font = buildJvmLyricsShareFont(fontFamily, fontStyle, fontSizePx)
        val wrappedLines = wrapTextLines(lines, graphics, font, maxWidth)
        val effectiveLineGapPx = lineGapPx.roundToInt().coerceAtLeast(0)
        val lineHeight = graphics.getFontMetrics(font).height + effectiveLineGapPx
        val blockHeight = max(1, wrappedLines.size) * lineHeight
        if (blockHeight <= maxBlockHeight || fontSizePx <= minFontSizePx) {
            return JvmFittedLyricsLayout(
                font = font,
                lines = wrappedLines,
                lineGapPx = effectiveLineGapPx,
                blockHeight = blockHeight,
            )
        }
        val nextFontSizePx = (fontSizePx * shrinkStep).coerceAtLeast(minFontSizePx)
        if (nextFontSizePx == fontSizePx) {
            return JvmFittedLyricsLayout(
                font = font,
                lines = wrappedLines,
                lineGapPx = effectiveLineGapPx,
                blockHeight = blockHeight,
            )
        }
        fontSizePx = nextFontSizePx
        lineGapPx = (lineGapPx * shrinkStep).coerceAtLeast(0f)
    }
}

private fun resolveJvmLyricsShareFontFamilyName(requestedFontFamily: String?): String {
    val availableFonts = listJvmFontFamilyNames()
    fun resolveCandidate(candidate: String?): String? {
        val normalizedCandidate = candidate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return availableFonts.firstOrNull { it.equals(normalizedCandidate, ignoreCase = true) }
    }
    return resolveCandidate(requestedFontFamily)
        ?: resolveCandidate(DEFAULT_LYRICS_SHARE_FONT_FAMILY)
        ?: availableFonts.firstOrNull()
        ?: Font.SERIF
}

private fun buildJvmLyricsShareFont(
    fontFamily: String,
    fontStyle: Int,
    fontSizePx: Float,
): Font {
    return Font(fontFamily, fontStyle, fontSizePx.roundToInt().coerceAtLeast(1))
}

private fun listJvmFontFamilyNames(): List<String> {
    return GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .availableFontFamilyNames
        .mapNotNull { familyName -> familyName.trim().takeIf { it.isNotEmpty() } }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
}

internal fun filterJvmLyricsShareFontFamilyNames(
    osName: String,
    availableFonts: List<String>,
) : List<LyricsShareFontOption> {
    val availableByName = linkedMapOf<String, String>()
    availableFonts.forEach { familyName ->
        val normalizedFamilyName = familyName.trim().takeIf { it.isNotEmpty() } ?: return@forEach
        availableByName.putIfAbsent(normalizedFamilyName.lowercase(), normalizedFamilyName)
    }
    return lyricsShareFontWhitelistForDesktop(osName).mapNotNull { candidate ->
        availableByName[candidate.familyName.lowercase()]?.let { resolvedFamilyName ->
            LyricsShareFontOption(
                familyName = resolvedFamilyName,
                previewText = candidate.previewText,
            )
        }
    }
}

internal fun lyricsShareFontWhitelistForDesktop(osName: String): List<JvmLyricsShareFontPreset> {
    return when (resolveJvmLyricsShareDesktopPlatform(osName)) {
        JvmLyricsShareDesktopPlatform.MACOS -> JVM_LYRICS_SHARE_FONT_WHITELIST_MACOS
        JvmLyricsShareDesktopPlatform.WINDOWS -> JVM_LYRICS_SHARE_FONT_WHITELIST_WINDOWS
        JvmLyricsShareDesktopPlatform.LINUX -> JVM_LYRICS_SHARE_FONT_WHITELIST_LINUX
    }
}

internal fun resolveJvmLyricsShareDesktopPlatform(osName: String): JvmLyricsShareDesktopPlatform {
    return when {
        osName.contains("mac", ignoreCase = true) -> JvmLyricsShareDesktopPlatform.MACOS
        osName.contains("win", ignoreCase = true) -> JvmLyricsShareDesktopPlatform.WINDOWS
        else -> JvmLyricsShareDesktopPlatform.LINUX
    }
}

internal enum class JvmLyricsShareDesktopPlatform {
    MACOS,
    WINDOWS,
    LINUX,
}

internal data class JvmLyricsShareFontPreset(
    val familyName: String,
    val previewText: String,
)

private val JVM_LYRICS_SHARE_FONT_WHITELIST_MACOS = listOf(
    JvmLyricsShareFontPreset("PingFang SC", "你好"),
    JvmLyricsShareFontPreset("Songti SC", "你好"),
    JvmLyricsShareFontPreset("Hiragino Sans GB", "你好"),
    JvmLyricsShareFontPreset("Avenir Next", "Hello"),
    JvmLyricsShareFontPreset("Baskerville", "Hello"),
    JvmLyricsShareFontPreset("Times New Roman", "Hello"),
)

private val JVM_LYRICS_SHARE_FONT_WHITELIST_WINDOWS = listOf(
    JvmLyricsShareFontPreset("SimSun", "你好"),
    JvmLyricsShareFontPreset("Microsoft YaHei", "你好"),
    JvmLyricsShareFontPreset("Segoe UI", "Hello"),
    JvmLyricsShareFontPreset("Georgia", "Hello"),
    JvmLyricsShareFontPreset("Constantia", "Hello"),
)

private val JVM_LYRICS_SHARE_FONT_WHITELIST_LINUX = listOf(
    JvmLyricsShareFontPreset("Noto Sans CJK SC", "你好"),
    JvmLyricsShareFontPreset("Noto Serif CJK SC", "你好"),
    JvmLyricsShareFontPreset("Noto Sans", "Hello"),
    JvmLyricsShareFontPreset("Noto Serif", "Hello"),
    JvmLyricsShareFontPreset("DejaVu Serif", "Hello"),
)

private fun wrapTextLines(
    lines: List<String>,
    graphics: Graphics2D,
    font: Font,
    maxWidth: Int,
): List<String> {
    return lines.flatMap { wrapSingleBlock(it, graphics, font, maxWidth, Int.MAX_VALUE) }
}

private fun wrapSingleBlock(
    text: String,
    graphics: Graphics2D,
    font: Font,
    maxWidth: Int,
    maxLines: Int,
): List<String> {
    val normalized = text.trim().ifBlank { " " }
    val metrics = graphics.getFontMetrics(font)
    val result = mutableListOf<String>()
    var current = StringBuilder()
    normalized.forEach { char ->
        val candidate = current.toString() + char
        if (current.isNotEmpty() && metrics.stringWidth(candidate) > maxWidth) {
            result += current.toString()
            current = StringBuilder().append(char)
        } else {
            current.append(char)
        }
    }
    if (current.isNotEmpty()) {
        result += current.toString()
    }
    return when {
        result.size <= maxLines -> result
        maxLines <= 0 -> emptyList()
        else -> result.take(maxLines).toMutableList().also { linesOut ->
            val lastIndex = linesOut.lastIndex
            linesOut[lastIndex] = linesOut[lastIndex].trimEnd().trimEnd('.') + "…"
        }
    }
}

private fun ensurePngFileName(name: String): String {
    val normalized = name.trim().ifBlank { "lynmusic-lyrics-share.png" }
    return if (normalized.endsWith(".png", ignoreCase = true)) normalized else "$normalized.png"
}

private class ImageTransferable(
    private val image: Image,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        check(isDataFlavorSupported(flavor)) { "Unsupported flavor: $flavor" }
        return image
    }
}
