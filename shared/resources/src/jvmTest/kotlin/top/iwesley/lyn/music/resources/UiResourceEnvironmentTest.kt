package top.iwesley.lyn.music.resources

import java.util.Locale
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getPluralString

class UiResourceEnvironmentTest {
    @Test fun readsThreeLanguagesWithoutChangingProcessLocale() = runTest {
        val original = Locale.getDefault()
        for ((tag, expected) in listOf("en" to "Close", "zh-CN" to "关闭", "zh-TW" to "關閉")) {
            val environment = uiResourceEnvironment(tag)
            assertEquals(expected, getString(environment, Res.string.common_close))
            assertEquals(environment, uiResourceEnvironment(tag, environment))
            assertEquals(original, Locale.getDefault())
        }
    }

    @Test fun usesOfficialPluralRulesAndFormatting() = runTest {
        for ((tag, expected) in listOf(
            "en" to listOf("0 songs", "1 song", "2 songs"),
            "zh-CN" to listOf("0 首歌曲", "1 首歌曲", "2 首歌曲"),
            "zh-TW" to listOf("0 首歌曲", "1 首歌曲", "2 首歌曲"),
        )) {
            val environment = uiResourceEnvironment(tag)
            for (count in 0..2) {
                assertEquals(expected[count], getPluralString(environment, Res.plurals.common_track_count, count, count))
            }
            assertEquals("name\ndetails", getString(environment, Res.string.ui_error_with_context, "name", "details"))
        }
    }

    @Test fun rejectsUnsupportedEnvironmentTags() {
        assertFailsWith<IllegalStateException> { uiResourceEnvironment("fr") }
    }
}
