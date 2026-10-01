package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.DeviceInfoSnapshot
import top.iwesley.lyn.music.core.model.uiText

/** Pure label logic; does not create a Compose window or run a UI test. */
class UiLocalizationTest {
    @Test fun cachedHardwareTextFollowsLanguageWithoutReloadingSnapshot() = runTest {
        val snapshot = DeviceInfoSnapshot("Android", "14", cpuDescription = "arm64", logicalCoreCount = 2)
        val description = checkNotNull(snapshot.cpuDescriptionText)
        repeat(2) {
            AppLanguageRuntime.update(AppLanguage.English)
            assertEquals("arm64 · 2 cores", resolveUiText(description, AppLanguageRuntime.effectiveLanguage.value))
            AppLanguageRuntime.update(AppLanguage.SimplifiedChinese)
            assertEquals("arm64 · 2 核", resolveUiText(description, AppLanguageRuntime.effectiveLanguage.value))
            AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
            assertEquals("arm64 · 2 核", resolveUiText(description, AppLanguageRuntime.effectiveLanguage.value))
        }
        assertEquals("arm64", snapshot.cpuDescription)
        assertEquals(2, snapshot.logicalCoreCount)
    }

    @Test fun existingMessagesAndFontLabelsFollowRepeatedChanges() = runTest {
        val message = uiText(Res.string.ui_operation_failed)
        val font = LyricsShareFontOption("serif", "", displayNameText = uiText(Res.string.font_serif))
        repeat(2) {
            AppLanguageRuntime.update(AppLanguage.English)
            assertEquals("Operation failed.", resolveUiText(message, AppLanguageRuntime.effectiveLanguage.value))
            assertEquals("Serif", resolveUiText(checkNotNull(font.displayNameText), AppLanguageRuntime.effectiveLanguage.value))
            AppLanguageRuntime.update(AppLanguage.SimplifiedChinese)
            assertEquals("操作失败。", resolveUiText(message, AppLanguageRuntime.effectiveLanguage.value))
            assertEquals("衬线", resolveUiText(checkNotNull(font.displayNameText), AppLanguageRuntime.effectiveLanguage.value))
            AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
            assertEquals("操作失敗。", resolveUiText(message, AppLanguageRuntime.effectiveLanguage.value))
            assertEquals("襯線", resolveUiText(checkNotNull(font.displayNameText), AppLanguageRuntime.effectiveLanguage.value))
        }
    }
}
