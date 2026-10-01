package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceInfoTest {
    @Test fun hardwareDescriptionKeepsRawModelAndUsesPluralResource() = runTest {
        val snapshot = DeviceInfoSnapshot("Android", "14", cpuDescription = "型号 甲 · arm64", logicalCoreCount = 1)
        val text = checkNotNull(snapshot.cpuDescriptionText)
        assertEquals("型号 甲 · arm64 · 1 core", resolveUiText(text, AppLanguage.English))
        assertEquals("型号 甲 · arm64 · 1 核", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("型号 甲 · arm64 · 1 核", resolveUiText(text, AppLanguage.TraditionalChinese))
        assertEquals("2 cores", resolveUiText(checkNotNull(snapshot.copy(cpuDescription = null, logicalCoreCount = 2).cpuDescriptionText), AppLanguage.English))
    }

    @Test fun incompleteHardwareDataOmitsInvalidCoreCounts() = runTest {
        val snapshot = DeviceInfoSnapshot("", "", cpuDescription = " ", logicalCoreCount = 0)
        assertNull(snapshot.cpuDescriptionText)
        assertNull(snapshot.copy(logicalCoreCount = -1).cpuDescriptionText)
        assertEquals("arm64", resolveUiText(checkNotNull(snapshot.copy(cpuDescription = "arm64").cpuDescriptionText), AppLanguage.English))
    }
}
