package top.iwesley.lyn.music

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_KEY
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.PlaybackArtworkBackgroundPalette
import top.iwesley.lyn.music.platform.JvmLyricsSharePlatformService
import top.iwesley.lyn.music.platform.SkiaLyricsShareTokenKind
import top.iwesley.lyn.music.platform.fitSkiaSingleLineWithEllipsis
import top.iwesley.lyn.music.platform.isJvmLyricsShareAlphabeticFamilyName
import top.iwesley.lyn.music.platform.listSkiaLyricsShareFontFamilyNames
import top.iwesley.lyn.music.platform.lyricsShareFontWhitelistForDesktop
import top.iwesley.lyn.music.platform.prioritizeIosLyricsShareFontFamilyNames
import top.iwesley.lyn.music.platform.prioritizeJvmLyricsShareFontFamilyNames
import top.iwesley.lyn.music.platform.resolveSkiaLyricsShareTypeface
import top.iwesley.lyn.music.platform.skiaLyricsShareFallbackFontFamilyNames
import top.iwesley.lyn.music.platform.tokenizeSkiaLyricsShareLine
import top.iwesley.lyn.music.platform.wrapSingleSkiaLyricsShareLine
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontStyle

class JvmLyricsShareFontSupportTest {

    private val service = JvmLyricsSharePlatformService()

    @Test
    fun `listAvailableFontFamilies returns all system fonts with prioritized prefix`() = runBlocking {
        val fonts = service.listAvailableFontFamilies().getOrThrow()
        val availableSystemFonts = listSkiaLyricsShareFontFamilyNames()
        val whitelist = lyricsShareFontWhitelistForDesktop(System.getProperty("os.name").orEmpty())
        val prioritizedPrefix = whitelist.mapNotNull { preset ->
            availableSystemFonts.firstOrNull { familyName ->
                familyName.equals(preset.familyName, ignoreCase = true)
            }?.let { familyName ->
                familyName to preset.previewText
            }
        }
        val expectedOrder = prioritizedPrefix.map { it.first } +
            availableSystemFonts
                .filterNot { familyName ->
                    prioritizedPrefix.any { prioritized ->
                        prioritized.first.equals(familyName, ignoreCase = true)
                    }
                }
                .partition(::isJvmLyricsShareAlphabeticFamilyName)
                .let { (alphabeticFonts, nonAlphabeticFonts) -> alphabeticFonts + nonAlphabeticFonts }

        assertTrue(fonts.isNotEmpty())
        assertEquals(fonts.map { it.fontKey.lowercase() }.distinct(), fonts.map { it.fontKey.lowercase() })
        assertContentEquals(
            expectedOrder.map { it.lowercase() },
            fonts.map { it.fontKey.lowercase() },
        )
        assertContentEquals(
            prioritizedPrefix.map { it.first },
            fonts.take(prioritizedPrefix.size).map { it.fontKey },
        )
        assertContentEquals(
            prioritizedPrefix.map { it.second },
            fonts.take(prioritizedPrefix.size).map { it.previewText },
        )
        assertTrue(fonts.take(prioritizedPrefix.size).all { it.isPrioritized })
        assertTrue(fonts.drop(prioritizedPrefix.size).all { !it.isPrioritized && it.previewText == "你好 Hello" })
    }

    @Test
    fun `prioritizeJvmLyricsShareFontFamilyNames keeps macos whitelist at top and appends others`() {
        val prioritized = prioritizeJvmLyricsShareFontFamilyNames(
            osName = "macOS 15.0",
            importedFonts = emptyList(),
            availableFonts = listOf("Times New Roman", "Baskerville", "PingFang SC", "Avenir Next", "Arial"),
        )

        assertEquals(
            listOf("PingFang SC", "Avenir Next", "Baskerville", "Times New Roman", "Arial"),
            prioritized.map { it.fontKey },
        )
        assertEquals(
            listOf("你好 Hello", "你好 Hello", "你好 Hello", "你好 Hello", "你好 Hello"),
            prioritized.map { it.previewText },
        )
        assertContentEquals(
            listOf(true, true, true, true, false),
            prioritized.map { it.isPrioritized },
        )
    }

    @Test
    fun `prioritizeJvmLyricsShareFontFamilyNames keeps windows whitelist at top and appends others`() {
        val prioritized = prioritizeJvmLyricsShareFontFamilyNames(
            osName = "Windows 11",
            importedFonts = emptyList(),
            availableFonts = listOf("Georgia", "Segoe UI", "SimSun", "Arial", "Microsoft YaHei"),
        )

        assertEquals(
            listOf("SimSun", "Microsoft YaHei", "Segoe UI", "Georgia", "Arial"),
            prioritized.map { it.fontKey },
        )
        assertEquals(
            listOf("你好 Hello", "你好 Hello", "你好 Hello", "你好 Hello", "你好 Hello"),
            prioritized.map { it.previewText },
        )
    }

    @Test
    fun `prioritizeJvmLyricsShareFontFamilyNames keeps linux whitelist at top and appends others`() {
        val prioritized = prioritizeJvmLyricsShareFontFamilyNames(
            osName = "Linux",
            importedFonts = emptyList(),
            availableFonts = listOf("DejaVu Serif", "Noto Serif", "Noto Sans", "Arial", "Noto Sans CJK SC"),
        )

        assertEquals(
            listOf("Noto Sans CJK SC", "Noto Sans", "Noto Serif", "DejaVu Serif", "Arial"),
            prioritized.map { it.fontKey },
        )
        assertEquals(
            listOf("你好 Hello", "你好 Hello", "你好 Hello", "你好 Hello", "你好 Hello"),
            prioritized.map { it.previewText },
        )
    }

    @Test
    fun `prioritizeJvmLyricsShareFontFamilyNames returns all fonts when whitelist has no matches`() {
        val prioritized = prioritizeJvmLyricsShareFontFamilyNames(
            osName = "Linux",
            importedFonts = emptyList(),
            availableFonts = listOf("Arial", "Courier New"),
        )

        assertEquals(listOf("Arial", "Courier New"), prioritized.map { it.fontKey })
        assertTrue(prioritized.all { !it.isPrioritized })
        assertContentEquals(listOf("你好 Hello", "你好 Hello"), prioritized.map { it.previewText })
    }

    @Test
    fun `prioritizeJvmLyricsShareFontFamilyNames moves non alphabetic families to end`() {
        val prioritized = prioritizeJvmLyricsShareFontFamilyNames(
            osName = "Linux",
            importedFonts = emptyList(),
            availableFonts = listOf(".Apple Symbols", "Arial", "你好字体", "Courier New"),
        )

        assertEquals(
            listOf("Arial", "Courier New", ".Apple Symbols", "你好字体"),
            prioritized.map { it.fontKey },
        )
    }

    @Test
    fun `prioritizeIosLyricsShareFontFamilyNames keeps shared fallback fonts at top and appends others`() {
        val prioritized = prioritizeIosLyricsShareFontFamilyNames(
            availableFonts = listOf(".SF NS", "Arial", "Arial Unicode MS", "Hiragino Sans GB", "PingFang HK", "Zapfino"),
        )
        val expectedPrioritizedPrefix = skiaLyricsShareFallbackFontFamilyNames().filter { familyName ->
            familyName in listOf(".SF NS", "Arial", "Arial Unicode MS", "Hiragino Sans GB", "PingFang HK", "Zapfino")
        }
        val expectedRemaining = listOf("Arial", "Arial Unicode MS", "Hiragino Sans GB", "PingFang HK", "Zapfino", ".SF NS")
            .filterNot { it in expectedPrioritizedPrefix }

        assertEquals(
            expectedPrioritizedPrefix + expectedRemaining,
            prioritized.map { it.fontKey },
        )
        assertContentEquals(
            List(expectedPrioritizedPrefix.size) { true } + List(expectedRemaining.size) { false },
            prioritized.map { it.isPrioritized },
        )
        assertTrue(prioritized.all { it.previewText == "你好 Hello" })
    }

    @Test
    fun `prioritizeIosLyricsShareFontFamilyNames returns all fonts when fallback list has no matches`() {
        val prioritized = prioritizeIosLyricsShareFontFamilyNames(
            availableFonts = listOf(".SF NS", "Arial", "Zapfino"),
        )

        assertEquals(listOf("Arial", "Zapfino", ".SF NS"), prioritized.map { it.fontKey })
        assertTrue(prioritized.all { !it.isPrioritized && it.previewText == "你好 Hello" })
    }

    @Test
    fun `buildPreview succeeds with a valid system font family`() = runBlocking {
        val fontKey = service.listAvailableFontFamilies().getOrThrow().first().fontKey

        val preview = renderPreview(fontKey = fontKey)

        assertTrue(preview.isNotEmpty())
    }

    @Test
    fun `buildPreview falls back when font family is invalid`() {
        val expected = renderPreview(fontKey = DEFAULT_LYRICS_SHARE_FONT_KEY)
        val actual = renderPreview(fontKey = "Definitely Missing Font Family")

        assertContentEquals(expected, actual)
    }

    @Test
    fun `buildPreview renders note and artwork tint templates with expected output bounds`() {
        val note = renderPreview(
            fontKey = null,
            template = LyricsShareTemplate.NOTE,
        )
        val artworkTint = renderPreview(
            fontKey = null,
            template = LyricsShareTemplate.ARTWORK_TINT,
        )

        val noteImage = ImageIO.read(ByteArrayInputStream(note))
        val artworkTintImage = ImageIO.read(ByteArrayInputStream(artworkTint))

        assertEquals(LyricsShareCardSpec.IMAGE_WIDTH_PX, noteImage.width)
        assertTrue(
            noteImage.height in LyricsShareCardSpec.IMAGE_MIN_HEIGHT_PX..LyricsShareCardSpec.IMAGE_MAX_HEIGHT_PX,
        )
        assertEquals(LyricsShareArtworkTintSpec.IMAGE_WIDTH_PX, artworkTintImage.width)
        assertTrue(
            artworkTintImage.height in
                LyricsShareArtworkTintSpec.IMAGE_MIN_HEIGHT_PX..LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX,
        )
    }

    @Test
    fun `buildPreview uses playback background palette for artwork tint template`() {
        val warm = renderPreview(
            fontKey = null,
            template = LyricsShareTemplate.ARTWORK_TINT,
            artworkBackgroundPalette = PlaybackArtworkBackgroundPalette(
                baseColorArgb = 0xFF241315.toInt(),
                primaryColorArgb = 0xFFB84D55.toInt(),
                secondaryColorArgb = 0xFF96612F.toInt(),
                tertiaryColorArgb = 0xFF5E314E.toInt(),
            ),
        )
        val cool = renderPreview(
            fontKey = null,
            template = LyricsShareTemplate.ARTWORK_TINT,
            artworkBackgroundPalette = PlaybackArtworkBackgroundPalette(
                baseColorArgb = 0xFF101D2A.toInt(),
                primaryColorArgb = 0xFF2E7DB3.toInt(),
                secondaryColorArgb = 0xFF356F66.toInt(),
                tertiaryColorArgb = 0xFF473C84.toInt(),
            ),
        )

        val warmImage = ImageIO.read(ByteArrayInputStream(warm))
        val coolImage = ImageIO.read(ByteArrayInputStream(cool))

        assertEquals(LyricsShareArtworkTintSpec.IMAGE_WIDTH_PX, warmImage.width)
        assertEquals(LyricsShareArtworkTintSpec.IMAGE_WIDTH_PX, coolImage.width)
        assertTrue(
            warmImage.height in
                LyricsShareArtworkTintSpec.IMAGE_MIN_HEIGHT_PX..LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX,
        )
        assertTrue(
            coolImage.height in
                LyricsShareArtworkTintSpec.IMAGE_MIN_HEIGHT_PX..LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX,
        )
        assertNotEquals(
            warmImage.getRGB(warmImage.width - 90, 230),
            coolImage.getRGB(coolImage.width - 90, 230),
        )
    }

    private fun renderPreview(
        fontKey: String?,
        template: LyricsShareTemplate = LyricsShareTemplate.NOTE,
        artworkBackgroundPalette: PlaybackArtworkBackgroundPalette? = null,
    ): ByteArray {
        return runBlocking {
            service.buildPreview(
                LyricsShareCardModel(
                    title = "字体测试",
                    artistName = "LynMusic",
                    artworkLocator = null,
                    template = template,
                    artworkBackgroundPalette = artworkBackgroundPalette,
                    lyricsLines = listOf("第一句", "第二句"),
                    fontKey = fontKey,
                ),
            ).getOrThrow()
        }
    }

    @Test
    fun `tokenizeSkiaLyricsShareLine groups english words and cjk characters`() {
        val tokens = tokenizeSkiaLyricsShareLine("You're not alone 你好")

        assertEquals(
            listOf("You're", " ", "not", " ", "alone", " ", "你", "好"),
            tokens.map { it.text },
        )
        assertEquals(
            listOf(
                SkiaLyricsShareTokenKind.WORD,
                SkiaLyricsShareTokenKind.WHITESPACE,
                SkiaLyricsShareTokenKind.WORD,
                SkiaLyricsShareTokenKind.WHITESPACE,
                SkiaLyricsShareTokenKind.WORD,
                SkiaLyricsShareTokenKind.WHITESPACE,
                SkiaLyricsShareTokenKind.CJK,
                SkiaLyricsShareTokenKind.CJK,
            ),
            tokens.map { it.kind },
        )
    }

    @Test
    fun `wrapSingleSkiaLyricsShareLine keeps english short line unbroken when width allows`() {
        val font = testFont(size = 32f)
        val text = "Let me help pick up the pieces"

        val wrapped = wrapSingleSkiaLyricsShareLine(
            text = text,
            font = font,
            maxWidth = 10_000f,
        )

        assertEquals(listOf(text), wrapped)
    }

    @Test
    fun `wrapSingleSkiaLyricsShareLine prefers english word boundaries`() {
        val font = testFont(size = 32f)
        val wrapped = wrapSingleSkiaLyricsShareLine(
            text = "You're not alone, I'm by your side",
            font = font,
            maxWidth = 220f,
        )

        assertTrue(wrapped.size > 1)
        assertTrue(wrapped.none { it.startsWith(" ") })
        assertTrue(wrapped.none { line -> line.contains(" alon") || line.contains(" by y") })
    }

    @Test
    fun `wrapSingleSkiaLyricsShareLine splits cjk by character`() {
        val font = testFont(size = 32f)
        val wrapped = wrapSingleSkiaLyricsShareLine(
            text = "让我陪你把碎片拾起",
            font = font,
            maxWidth = 96f,
        )

        assertTrue(wrapped.size > 1)
        assertTrue(wrapped.joinToString("") == "让我陪你把碎片拾起")
    }

    @Test
    fun `wrapSingleSkiaLyricsShareLine falls back to character split for oversized english word`() {
        val font = testFont(size = 32f)
        val wrapped = wrapSingleSkiaLyricsShareLine(
            text = "supercalifragilisticexpialidocious",
            font = font,
            maxWidth = 120f,
        )

        assertTrue(wrapped.size > 1)
        assertTrue(wrapped.joinToString("") == "supercalifragilisticexpialidocious")
    }

    @Test
    fun `fitSkiaSingleLineWithEllipsis preserves full footer when width allows`() {
        val font = testFont(size = 28f)
        val text = "Walk Thru Fire · Vicetone&Meron Ryan"

        val result = fitSkiaSingleLineWithEllipsis(
            text = text,
            font = font,
            maxWidth = 10_000f,
        )

        assertEquals(text, result)
    }

    @Test
    fun `fitSkiaSingleLineWithEllipsis truncates footer by width`() {
        val font = testFont(size = 28f)
        val result = fitSkiaSingleLineWithEllipsis(
            text = "Walk Thru Fire · Vicetone&Meron Ryan",
            font = font,
            maxWidth = 260f,
        )

        assertTrue(result.endsWith("…"))
        assertTrue(result.length < "Walk Thru Fire · Vicetone&Meron Ryan".length)
    }

    private fun testFont(
        size: Float,
    ): Font {
        return Font(resolveSkiaLyricsShareTypeface("Hello 你好", FontStyle.NORMAL, null), size)
    }
}
