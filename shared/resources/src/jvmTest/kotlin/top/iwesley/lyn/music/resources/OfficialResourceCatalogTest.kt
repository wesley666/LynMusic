package top.iwesley.lyn.music.resources

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getPluralString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialResourceCatalogTest {
    @Test fun everyStringAndPluralLoadsAndFormatsInAllLanguages() = runTest {
        assertEquals(1281, Res.allStringResources.size)
        assertEquals(43, Res.allPluralStringResources.size)
        val placeholders = Regex("%([1-9][0-9]*)\\$([sd])")
        fun arguments(pattern: String): Array<Any> {
            val slots = placeholders.findAll(pattern).associate { it.groupValues[1].toInt() to it.groupValues[2] }
            return Array(slots.keys.maxOrNull() ?: 0) { index ->
                if (slots[index + 1] == "d") 2 else "原始详情 %1\$s"
            }
        }
        for (tag in listOf("en", "zh-CN", "zh-TW")) {
            val environment = uiResourceEnvironment(tag)
            Res.allStringResources.forEach { (key, resource) ->
                val pattern = getString(environment, resource)
                val result = getString(environment, resource, *arguments(pattern))
                assertTrue(result.isNotEmpty() || pattern.isEmpty(), "$tag/$key")
            }
            Res.allPluralStringResources.forEach { (key, resource) ->
                for (quantity in 0..2) {
                    val pattern = getPluralString(environment, resource, quantity)
                    val result = getPluralString(environment, resource, quantity, *arguments(pattern))
                    assertTrue(result.isNotEmpty(), "$tag/$key/$quantity")
                }
            }
        }
    }
}
