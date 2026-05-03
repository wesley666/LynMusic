package top.iwesley.lyn.music.platform

import java.awt.Image as AwtImage
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontKind
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget

class JvmLyricsSharePlatformService(
    private val fontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
    private val artworkCacheStore: ArtworkCacheStore = createJvmArtworkCacheStore(),
) : LyricsSharePlatformService {
    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val artwork = loadArtworkImage(model.artworkLocator, model.artworkCacheKey)
            val importedFontPath = fontLibraryPlatformService.resolveImportedFontPath(model.fontKey.orEmpty()).getOrNull()
            SkiaLyricsShareRenderer.render(model, artwork, importedFontPath)
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

    override suspend fun copyText(text: String): Result<Unit> = withContext(Dispatchers.Swing) {
        runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
        }
    }

    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> = withContext(Dispatchers.IO) {
        runCatching {
            val importedFonts = fontLibraryPlatformService.listImportedFonts().getOrDefault(emptyList())
            prioritizeJvmLyricsShareFontFamilyNames(
                osName = System.getProperty("os.name").orEmpty(),
                importedFonts = importedFonts,
                availableFonts = listSkiaLyricsShareFontFamilyNames(),
            )
        }
    }

    private suspend fun loadArtworkImage(locator: String?, artworkCacheKey: String?): Image? {
        val artworkBytes = loadLyricsShareArtworkBytes(locator, artworkCacheKey)
            ?: loadBundledDefaultCoverBytes()
        return artworkBytes?.let(Image::makeFromEncoded)
    }

    private suspend fun loadLyricsShareArtworkBytes(locator: String?, artworkCacheKey: String?): ByteArray? {
        val normalized = normalizedArtworkCacheLocator(locator) ?: return null
        val target = resolveJvmLyricsShareArtworkTarget(normalized, artworkCacheKey) ?: return null
        return readJvmLyricsShareArtworkTargetBytes(target)
    }

    private suspend fun resolveJvmLyricsShareArtworkTarget(
        normalizedLocator: String,
        artworkCacheKey: String?,
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
}

private suspend fun readJvmLyricsShareArtworkTargetBytes(target: String): ByteArray? {
    return runCatching {
        when {
            target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                URI(target).toURL().openStream().use { it.readBytes() }

            target.startsWith("file://", ignoreCase = true) -> Files.readAllBytes(Paths.get(URI(target)))
            else -> Files.readAllBytes(Paths.get(target))
        }
    }.getOrNull()?.takeIf(::isCompleteArtworkPayload)
}

private fun shouldCacheLyricsShareArtwork(normalizedLocator: String): Boolean {
    return parseNavidromeCoverLocator(normalizedLocator) != null ||
        normalizedLocator.startsWith("http://", ignoreCase = true) ||
        normalizedLocator.startsWith("https://", ignoreCase = true)
}

internal fun prioritizeJvmLyricsShareFontFamilyNames(
    osName: String,
    importedFonts: List<LyricsShareFontOption>,
    availableFonts: List<String>,
): List<LyricsShareFontOption> {
    val availableByName = linkedMapOf<String, String>()
    availableFonts.forEach { familyName ->
        val normalizedFamilyName = familyName.trim().takeIf { it.isNotEmpty() } ?: return@forEach
        availableByName.putIfAbsent(normalizedFamilyName.lowercase(), normalizedFamilyName)
    }
    val prioritizedKeys = linkedSetOf<String>()
    val prioritizedFonts = lyricsShareFontWhitelistForDesktop(osName).mapNotNull { candidate ->
        availableByName[candidate.familyName.lowercase()]?.let { resolvedFamilyName ->
            prioritizedKeys += resolvedFamilyName.lowercase()
            LyricsShareFontOption(
                fontKey = resolvedFamilyName,
                displayName = resolvedFamilyName,
                previewText = candidate.previewText,
                isPrioritized = true,
            )
        }
    }
    val remainingFamilies = availableByName.values
        .filterNot { familyName -> familyName.lowercase() in prioritizedKeys }
    val (alphabeticFamilies, nonAlphabeticFamilies) = remainingFamilies.partition(::isJvmLyricsShareAlphabeticFamilyName)
    val otherFonts = (alphabeticFamilies + nonAlphabeticFamilies).map { familyName ->
        LyricsShareFontOption(
            fontKey = familyName,
            displayName = familyName,
            previewText = DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT,
        )
    }
    return importedFonts.filter { it.kind == LyricsShareFontKind.IMPORTED } + prioritizedFonts + otherFonts
}

internal fun isJvmLyricsShareAlphabeticFamilyName(
    familyName: String,
): Boolean {
    val firstChar = familyName.trim().firstOrNull()?.uppercaseChar() ?: return false
    return firstChar in 'A'..'Z'
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
    JvmLyricsShareFontPreset("Songti SC", "你好 Hello"),
    JvmLyricsShareFontPreset("PingFang SC", "你好 Hello"),
    JvmLyricsShareFontPreset("Hiragino Sans GB", "你好 Hello"),
    JvmLyricsShareFontPreset("LingWai SC", "你好 Hello"),
    JvmLyricsShareFontPreset("Xingkai SC", "你好 Hello"),
    JvmLyricsShareFontPreset("Wawati SC", "你好 Hello"),
    JvmLyricsShareFontPreset("Avenir Next", "你好 Hello"),
    JvmLyricsShareFontPreset("Baskerville", "你好 Hello"),
    JvmLyricsShareFontPreset("Times New Roman", "你好 Hello"),
)

private val JVM_LYRICS_SHARE_FONT_WHITELIST_WINDOWS = listOf(
    JvmLyricsShareFontPreset("SimSun", "你好 Hello"),
    JvmLyricsShareFontPreset("Microsoft YaHei", "你好 Hello"),
    JvmLyricsShareFontPreset("Segoe UI", "你好 Hello"),
    JvmLyricsShareFontPreset("Georgia", "你好 Hello"),
    JvmLyricsShareFontPreset("Constantia", "你好 Hello"),
)

private val JVM_LYRICS_SHARE_FONT_WHITELIST_LINUX = listOf(
    JvmLyricsShareFontPreset("Noto Sans CJK SC", "你好 Hello"),
    JvmLyricsShareFontPreset("Noto Serif CJK SC", "你好 Hello"),
    JvmLyricsShareFontPreset("Noto Sans", "你好 Hello"),
    JvmLyricsShareFontPreset("Noto Serif", "你好 Hello"),
    JvmLyricsShareFontPreset("DejaVu Serif", "你好 Hello"),
)

private fun ensurePngFileName(name: String): String {
    val normalized = name.trim().ifBlank { "lynmusic-lyrics-share.png" }
    return if (normalized.endsWith(".png", ignoreCase = true)) normalized else "$normalized.png"
}

private class ImageTransferable(
    private val image: AwtImage,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        check(isDataFlavorSupported(flavor)) { "Unsupported flavor: $flavor" }
        return image
    }
}
