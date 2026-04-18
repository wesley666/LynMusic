package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_FAMILY
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.platform.JvmLyricsSharePlatformService
import top.iwesley.lyn.music.platform.filterJvmLyricsShareFontFamilyNames
import top.iwesley.lyn.music.platform.lyricsShareFontWhitelistForDesktop

class JvmLyricsShareFontSupportTest {

    private val service = JvmLyricsSharePlatformService()

    @Test
    fun `listAvailableFontFamilies returns whitelist filtered fonts`() = runBlocking {
        val fonts = service.listAvailableFontFamilies().getOrThrow()
        val whitelist = lyricsShareFontWhitelistForDesktop(System.getProperty("os.name").orEmpty())

        assertTrue(fonts.isNotEmpty())
        assertEquals(fonts.map { it.familyName.lowercase() }.distinct(), fonts.map { it.familyName.lowercase() })
        assertTrue(fonts.all { font ->
            whitelist.any { preset ->
                preset.familyName.equals(font.familyName, ignoreCase = true) &&
                    preset.previewText == font.previewText
            }
        })
    }

    @Test
    fun `filterJvmLyricsShareFontFamilyNames keeps macos whitelist order`() {
        val filtered = filterJvmLyricsShareFontFamilyNames(
            osName = "macOS 15.0",
            availableFonts = listOf("Times New Roman", "Baskerville", "PingFang SC", "Avenir Next", "Arial"),
        )

        assertEquals(
            listOf("PingFang SC", "Avenir Next", "Baskerville", "Times New Roman"),
            filtered.map { it.familyName },
        )
        assertEquals(
            listOf("你好", "Hello", "Hello", "Hello"),
            filtered.map { it.previewText },
        )
    }

    @Test
    fun `filterJvmLyricsShareFontFamilyNames keeps windows whitelist order`() {
        val filtered = filterJvmLyricsShareFontFamilyNames(
            osName = "Windows 11",
            availableFonts = listOf("Georgia", "Segoe UI", "SimSun", "Arial", "Microsoft YaHei"),
        )

        assertEquals(
            listOf("SimSun", "Microsoft YaHei", "Segoe UI", "Georgia"),
            filtered.map { it.familyName },
        )
        assertEquals(
            listOf("你好", "你好", "Hello", "Hello"),
            filtered.map { it.previewText },
        )
    }

    @Test
    fun `filterJvmLyricsShareFontFamilyNames keeps linux whitelist order`() {
        val filtered = filterJvmLyricsShareFontFamilyNames(
            osName = "Linux",
            availableFonts = listOf("DejaVu Serif", "Noto Serif", "Noto Sans", "Arial", "Noto Sans CJK SC"),
        )

        assertEquals(
            listOf("Noto Sans CJK SC", "Noto Sans", "Noto Serif", "DejaVu Serif"),
            filtered.map { it.familyName },
        )
        assertEquals(
            listOf("你好", "Hello", "Hello", "Hello"),
            filtered.map { it.previewText },
        )
    }

    @Test
    fun `filterJvmLyricsShareFontFamilyNames returns empty when whitelist has no matches`() {
        val filtered = filterJvmLyricsShareFontFamilyNames(
            osName = "Linux",
            availableFonts = listOf("Arial", "Courier New"),
        )

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `buildPreview succeeds with a valid system font family`() = runBlocking {
        val fontFamily = service.listAvailableFontFamilies().getOrThrow().first().familyName

        val preview = renderPreview(fontFamilyName = fontFamily)

        assertTrue(preview.isNotEmpty())
    }

    @Test
    fun `buildPreview falls back when font family is invalid`() {
        val expected = renderPreview(fontFamilyName = DEFAULT_LYRICS_SHARE_FONT_FAMILY)
        val actual = renderPreview(fontFamilyName = "Definitely Missing Font Family")

        assertContentEquals(expected, actual)
    }

    private fun renderPreview(
        fontFamilyName: String?,
    ): ByteArray {
        return runBlocking {
            service.buildPreview(
                LyricsShareCardModel(
                    title = "字体测试",
                    artistName = "LynMusic",
                    artworkLocator = null,
                    template = LyricsShareTemplate.NOTE,
                    lyricsLines = listOf("第一句", "第二句"),
                    fontFamilyName = fontFamilyName,
                ),
            ).getOrThrow()
        }
    }
}
