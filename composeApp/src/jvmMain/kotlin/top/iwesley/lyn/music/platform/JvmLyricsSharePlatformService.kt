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
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.argbWithAlpha
import top.iwesley.lyn.music.core.model.buildLyricsShareTitleArtistLine
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import kotlin.math.max

class JvmLyricsSharePlatformService : LyricsSharePlatformService {
    private val artworkCacheStore = createJvmArtworkCacheStore()

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
        return withContext(Dispatchers.Swing) {
            runCatching {
                val chooser = JFileChooser().apply {
                    selectedFile = File(ensurePngFileName(suggestedName))
                }
                if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
                    error("已取消保存。")
                }
                val output = chooser.selectedFile?.let { selected ->
                    if (selected.name.endsWith(".png", ignoreCase = true)) selected else File(selected.parentFile, "${selected.name}.png")
                } ?: error("没有可用的输出文件。")
                output.parentFile?.mkdirs()
                output.writeBytes(pngBytes)
                LyricsShareSaveResult(message = "图片已保存到文件")
            }
        }
    }

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> = withContext(Dispatchers.Swing) {
        runCatching {
            val image = ImageIO.read(ByteArrayInputStream(pngBytes)) ?: error("无法读取图片数据。")
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(ImageTransferable(image), null)
        }
    }

    private suspend fun loadArtworkImage(locator: String?): BufferedImage? {
        return runCatching {
            val normalized = locator?.trim().orEmpty()
            if (normalized.isBlank()) return@runCatching null
            val target = artworkCacheStore.cache(normalized, normalized).orEmpty()
            if (target.isBlank()) return@runCatching null
            when {
                target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                    URL(target).openStream().use(ImageIO::read)

                target.startsWith("file://", ignoreCase = true) ->
                    ImageIO.read(Paths.get(URI(target)).toFile())

                else -> ImageIO.read(File(target))
            }
        }.getOrNull()
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
        val width = LyricsShareCardSpec.IMAGE_WIDTH_PX
        val canvasFont = Font("Serif", Font.BOLD, LyricsShareCardSpec.LYRICS_FONT_SIZE_PX.toInt())
        val titleFont = Font("Serif", Font.BOLD, LyricsShareCardSpec.TITLE_FONT_SIZE_PX.toInt())
        val brandFont = Font("Serif", Font.PLAIN, LyricsShareCardSpec.BRAND_FONT_SIZE_PX.toInt())
        val probe = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).createGraphics()
        val contentWidth = width - LyricsShareCardSpec.OUTER_PADDING_PX * 2 - LyricsShareCardSpec.PAPER_PADDING_HORIZONTAL_PX * 2
        val wrappedLyrics = wrapTextLines(model.lyricsLines, probe, canvasFont, contentWidth)
        val wrappedFooterLine = wrapSingleBlock(
            buildLyricsShareTitleArtistLine(model.title, model.artistName),
            probe,
            titleFont,
            contentWidth,
            1,
        )
        val lyricsLineHeight = probe.getFontMetrics(canvasFont).height + LyricsShareCardSpec.LYRICS_JVM_LINE_GAP_PX
        val titleLineHeight = probe.getFontMetrics(titleFont).height + 4
        val brandLineHeight = probe.getFontMetrics(brandFont).height
        probe.dispose()

        val contentHeight =
            LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                max(1, wrappedLyrics.size) * lyricsLineHeight +
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
        graphics.font = canvasFont
        val lyricsMetrics = graphics.fontMetrics
        wrappedLyrics.forEach { line ->
            cursorY += lyricsMetrics.ascent
            graphics.drawString(line, textX, cursorY)
            cursorY += lyricsMetrics.descent + lyricsMetrics.leading + LyricsShareCardSpec.LYRICS_JVM_LINE_GAP_PX
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
        val width = LyricsShareArtworkTintSpec.IMAGE_WIDTH_PX
        val lyricsFont = Font("Serif", Font.BOLD, LyricsShareArtworkTintSpec.LYRICS_FONT_SIZE_PX.toInt())
        val titleFont = Font("Serif", Font.BOLD, LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX.toInt())
        val brandFont = Font("Serif", Font.PLAIN, LyricsShareArtworkTintSpec.BRAND_FONT_SIZE_PX.toInt())
        val probe = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).createGraphics()
        val contentWidth = width - LyricsShareArtworkTintSpec.OUTER_PADDING_PX * 2
        val wrappedLyrics = wrapTextLines(model.lyricsLines, probe, lyricsFont, contentWidth)
        val wrappedFooterLine = wrapSingleBlock(
            buildLyricsShareTitleArtistLine(model.title, model.artistName),
            probe,
            titleFont,
            contentWidth,
            1,
        )
        val lyricsLineHeight = probe.getFontMetrics(lyricsFont).height + LyricsShareArtworkTintSpec.LYRICS_JVM_LINE_GAP_PX
        val titleLineHeight = probe.getFontMetrics(titleFont).height + 4
        val brandLineHeight = probe.getFontMetrics(brandFont).height
        probe.dispose()

        val contentHeight =
            LyricsShareArtworkTintSpec.OUTER_PADDING_PX +
                LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX +
                LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX +
                max(1, wrappedLyrics.size) * lyricsLineHeight +
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
        graphics.font = lyricsFont
        val lyricsMetrics = graphics.fontMetrics
        wrappedLyrics.forEach { line ->
            cursorY += lyricsMetrics.ascent
            graphics.drawString(line, textX, cursorY)
            cursorY += lyricsMetrics.descent + lyricsMetrics.leading + LyricsShareArtworkTintSpec.LYRICS_JVM_LINE_GAP_PX
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
        val brandY = (height - LyricsShareArtworkTintSpec.OUTER_PADDING_PX - brandMetrics.descent).toInt()
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
