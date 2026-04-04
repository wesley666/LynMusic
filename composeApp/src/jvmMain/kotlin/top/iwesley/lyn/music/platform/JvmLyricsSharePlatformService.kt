package top.iwesley.lyn.music.platform

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Image
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
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
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
        val normalized = locator?.trim().orEmpty()
        if (normalized.isBlank()) return null
        val target = artworkCacheStore.cache(normalized, normalized).orEmpty()
        if (target.isBlank()) return null
        return runCatching {
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
        val width = LyricsShareCardSpec.IMAGE_WIDTH_PX
        val canvasFont = Font("Serif", Font.BOLD, LyricsShareCardSpec.LYRICS_FONT_SIZE_PX.toInt())
        val titleFont = Font("Serif", Font.BOLD, LyricsShareCardSpec.TITLE_FONT_SIZE_PX.toInt())
        val metaFont = Font("Serif", Font.PLAIN, LyricsShareCardSpec.META_FONT_SIZE_PX.toInt())
        val brandFont = Font("Serif", Font.PLAIN, LyricsShareCardSpec.BRAND_FONT_SIZE_PX.toInt())
        val probe = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).createGraphics()
        val contentWidth = width - LyricsShareCardSpec.OUTER_PADDING_PX * 2 - LyricsShareCardSpec.PAPER_PADDING_HORIZONTAL_PX * 2
        val wrappedLyrics = wrapTextLines(model.lyricsLines, probe, canvasFont, contentWidth)
        val wrappedTitle = wrapSingleBlock(model.title.ifBlank { "当前歌曲" }, probe, titleFont, contentWidth, 2)
        val wrappedArtist = wrapSingleBlock(model.artistName?.ifBlank { "未知艺人" } ?: "未知艺人", probe, metaFont, contentWidth, 2)
        val lyricsLineHeight = probe.getFontMetrics(canvasFont).height + 8
        val titleLineHeight = probe.getFontMetrics(titleFont).height + 4
        val artistLineHeight = probe.getFontMetrics(metaFont).height + 2
        val brandLineHeight = probe.getFontMetrics(brandFont).height
        probe.dispose()

        val contentHeight =
            LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                max(1, wrappedLyrics.size) * lyricsLineHeight +
                LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                max(1, wrappedTitle.size) * titleLineHeight +
                max(1, wrappedArtist.size) * artistLineHeight +
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
            cursorY += lyricsMetrics.descent + lyricsMetrics.leading + 8
        }

        cursorY += LyricsShareCardSpec.FOOTER_TOP_GAP_PX
        graphics.font = titleFont
        val titleMetrics = graphics.fontMetrics
        wrappedTitle.forEach { line ->
            cursorY += titleMetrics.ascent
            graphics.drawString(line, textX, cursorY)
            cursorY += titleMetrics.descent + titleMetrics.leading + 4
        }

        graphics.color = textSecondary
        graphics.font = metaFont
        val metaMetrics = graphics.fontMetrics
        wrappedArtist.forEach { line ->
            cursorY += metaMetrics.ascent
            graphics.drawString(line, textX, cursorY)
            cursorY += metaMetrics.descent + metaMetrics.leading + 2
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
