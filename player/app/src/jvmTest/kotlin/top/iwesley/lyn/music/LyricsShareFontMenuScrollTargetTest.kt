package top.iwesley.lyn.music

import top.iwesley.lyn.music.testing.uiDisplayName
import top.iwesley.lyn.music.testing.buildLyricsShareFontMenuIndexEntries

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.testing.buildLyricsShareFontButtonLabel

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.uiText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsShareFontMenuScrollTargetTest {
    @Test fun androidButtonResolvesBuiltinBeforeLazyLoadAndOverridesStaleCache() = runTest {
        val previousLanguage = AppLanguageRuntime.appLanguage.value
        try {
            listOf(
                Triple(AppLanguage.English, "Font", "Sans serif"),
                Triple(AppLanguage.SimplifiedChinese, "字体", "无衬线"),
                Triple(AppLanguage.TraditionalChinese, "字體", "無襯線"),
                Triple(AppLanguage.English, "Font", "Sans serif"),
            ).forEach { (language, prefix, name) ->
                AppLanguageRuntime.update(language)
                assertEquals("$prefix · $name", buildLyricsShareFontButtonLabel("sans-serif", "sans-serif", emptyList(), isAndroid = true))
                assertEquals("$prefix · 最新名称", buildLyricsShareFontButtonLabel("sans-serif", "旧缓存", listOf(LyricsShareFontOption("sans-serif", "最新名称")), isAndroid = true))
                assertEquals("$prefix · 用户字体", buildLyricsShareFontButtonLabel("imported:abc", " 用户字体 ", emptyList(), isAndroid = true))
                assertEquals("$prefix · 缓存名称", buildLyricsShareFontButtonLabel("custom-family", "缓存名称", emptyList(), isAndroid = true))
                assertEquals("$prefix · custom-family", buildLyricsShareFontButtonLabel("custom-family", availableFonts = emptyList(), isAndroid = true))
                assertEquals("$prefix · sans-serif", buildLyricsShareFontButtonLabel("sans-serif", availableFonts = emptyList()))
                assertEquals("$prefix · Serif", buildLyricsShareFontButtonLabel("Serif", availableFonts = emptyList()))
                val serif = when (language) {
                    AppLanguage.SimplifiedChinese -> "衬线"
                    AppLanguage.TraditionalChinese -> "襯線"
                    else -> "Serif"
                }
                listOf(null, "Serif", "imported:missing").forEach { key ->
                    assertEquals("$prefix · $serif", buildLyricsShareFontButtonLabel(key, availableFonts = emptyList(), isAndroid = true))
                }
            }
        } finally { AppLanguageRuntime.update(previousLanguage) }
    }

    @Test fun builtinFontButtonMenuAndIndexResolveTheSameCurrentLabels() = runTest {
        val previousLanguage = AppLanguageRuntime.appLanguage.value
        try {
            val fonts = listOf(
                LyricsShareFontOption("sans-serif", "sans-serif", displayNameText = uiText(Res.string.font_sans_serif)),
                LyricsShareFontOption("serif", "serif", displayNameText = uiText(Res.string.font_serif)),
                LyricsShareFontOption("imported:abc", "用户字体"),
            )
            val expected = listOf(
                AppLanguage.English to listOf("Sans serif", "Serif", "用户字体"),
                AppLanguage.SimplifiedChinese to listOf("无衬线", "衬线", "用户字体"),
                AppLanguage.TraditionalChinese to listOf("無襯線", "襯線", "用户字体"),
                AppLanguage.English to listOf("Sans serif", "Serif", "用户字体"),
            )
            expected.forEach { (language, names) ->
                AppLanguageRuntime.update(language)
                assertEquals(names, fonts.map { it.uiDisplayName() })
                val prefix = when (language) {
                    AppLanguage.English -> "Font"
                    AppLanguage.TraditionalChinese -> "字體"
                    else -> "字体"
                }
                assertEquals("$prefix · ${names[0]}", buildLyricsShareFontButtonLabel("sans-serif", "旧缓存", fonts))
                assertEquals("$prefix · 用户字体", buildLyricsShareFontButtonLabel("imported:abc", "旧缓存", fonts))
                assertEquals(if (language == AppLanguage.English) listOf("S", "#") else listOf("#"), buildLyricsShareFontMenuIndexEntries(fonts).map { it.label })
            }
        } finally { AppLanguageRuntime.update(previousLanguage) }
    }

    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `middle item is positioned near menu center`() = runTest {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = 5,
            itemCount = 12,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(148, offset)
    }

    @Test
    fun `first item clamps to top`() = runTest {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = 0,
            itemCount = 12,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(0, offset)
    }

    @Test
    fun `last item clamps to bottom`() = runTest {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = 11,
            itemCount = 12,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(352, offset)
    }

    @Test
    fun `short list keeps zero scroll offset`() = runTest {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = 2,
            itemCount = 4,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(0, offset)
    }

    @Test
    fun `missing selection keeps zero scroll offset`() = runTest {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = -1,
            itemCount = 12,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(0, offset)
    }

    @Test
    fun `index entries add favorites entry and ignore prioritized letters`() = runTest {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "PingFang SC", displayName = "PingFang SC", isPrioritized = true),
                LyricsShareFontOption(fontKey = "Baskerville", displayName = "Baskerville", isPrioritized = true),
                LyricsShareFontOption(fontKey = "Courier New", displayName = "Courier New"),
                LyricsShareFontOption(fontKey = "Arial", displayName = "Arial"),
                LyricsShareFontOption(fontKey = "你好字体", displayName = "你好字体"),
            )
        )

        assertContentEquals(
            listOf("★", "A", "C", "#"),
            entries.map { it.label },
        )
        assertEquals(0, entries.first().firstIndex)
        assertTrue(isLyricsShareFontFavoritesIndexEntry(entries.first()))
    }

    @Test
    fun `non latin leading font maps to hash group after letters`() = runTest {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "你好字体", displayName = "你好字体"),
                LyricsShareFontOption(fontKey = ".Apple Symbols", displayName = ".Apple Symbols"),
                LyricsShareFontOption(fontKey = "Avenir Next", displayName = "Avenir Next"),
            )
        )

        assertContentEquals(
            listOf("A", "#"),
            entries.map { it.label },
        )
    }

    @Test
    fun `index entries skip favorites entry when nothing is prioritized`() = runTest {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "Avenir Next", displayName = "Avenir Next"),
                LyricsShareFontOption(fontKey = "Courier New", displayName = "Courier New"),
            )
        )

        assertContentEquals(
            listOf("A", "C"),
            entries.map { it.label },
        )
    }

    @Test
    fun `entry firstIndex points to first matching non prioritized font in displayed list`() = runTest {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "Baskerville", displayName = "Baskerville", isPrioritized = true),
                LyricsShareFontOption(fontKey = "PingFang SC", displayName = "PingFang SC", isPrioritized = true),
                LyricsShareFontOption(fontKey = "Arial", displayName = "Arial"),
                LyricsShareFontOption(fontKey = "Avenir Next", displayName = "Avenir Next"),
                LyricsShareFontOption(fontKey = "Courier New", displayName = "Courier New"),
            )
        )

        assertContentEquals(
            listOf("★", "A", "C"),
            entries.map { it.label },
        )
        assertEquals(0, entries[0].firstIndex)
        assertEquals(2, entries[1].firstIndex)
        assertEquals(4, entries[2].firstIndex)
    }

    @Test
    fun `prioritized letter still appears when regular section has same letter`() = runTest {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "PingFang SC", displayName = "PingFang SC", isPrioritized = true),
                LyricsShareFontOption(fontKey = "Arial", displayName = "Arial"),
                LyricsShareFontOption(fontKey = "Papyrus", displayName = "Papyrus"),
            )
        )

        assertContentEquals(
            listOf("★", "A", "P"),
            entries.map { it.label },
        )
        assertEquals(2, entries.last().firstIndex)
    }

    @Test
    fun `pointer at top maps to first index entry`() = runTest {
        val targetIndex = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 0f,
            trackHeightPx = 320,
            entryCount = 4,
        )

        assertEquals(0, targetIndex)
    }

    @Test
    fun `pointer at bottom maps to last index entry`() = runTest {
        val targetIndex = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 320f,
            trackHeightPx = 320,
            entryCount = 4,
        )

        assertEquals(3, targetIndex)
    }

    @Test
    fun `middle pointer maps to middle entry`() = runTest {
        val targetIndex = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 150f,
            trackHeightPx = 320,
            entryCount = 4,
        )

        assertEquals(1, targetIndex)
    }

    @Test
    fun `out of range pointer clamps to valid entry`() = runTest {
        val top = calculateLyricsShareFontMenuIndexTarget(
            pointerY = -40f,
            trackHeightPx = 320,
            entryCount = 4,
        )
        val bottom = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 480f,
            trackHeightPx = 320,
            entryCount = 4,
        )

        assertEquals(0, top)
        assertEquals(3, bottom)
    }

    @Test
    fun `invalid track height keeps target missing`() = runTest {
        val targetIndex = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 40f,
            trackHeightPx = 0,
            entryCount = 4,
        )

        assertEquals(-1, targetIndex)
    }

    @Test
    fun `button label uses imported font display name before list loads`() = runTest {
        val label = buildLyricsShareFontButtonLabel(
            selectedFontKey = "imported:abcdef123456",
            selectedFontDisplayName = "霞鹜文楷",
            availableFonts = emptyList(),
        )

        assertEquals("字体 · 霞鹜文楷", label)
    }

    @Test
    fun `button label falls back to default instead of imported font hash when name is missing`() = runTest {
        val label = buildLyricsShareFontButtonLabel(
            selectedFontKey = "imported:abcdef123456",
            availableFonts = emptyList(),
        )

        assertEquals("字体 · Serif", label)
    }

    @Test
    fun `button label prefers latest imported font display name after list loads`() = runTest {
        val label = buildLyricsShareFontButtonLabel(
            selectedFontKey = "imported:abcdef123456",
            selectedFontDisplayName = "旧名字",
            availableFonts = listOf(
                LyricsShareFontOption(
                    fontKey = "imported:abcdef123456",
                    displayName = "新名字",
                ),
            ),
        )

        assertEquals("字体 · 新名字", label)
    }
}
