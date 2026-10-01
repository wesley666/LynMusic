package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class AppLanguageTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun languageViewsEmitOnlyChangesAndReadCurrentStateImmediately() = runTest {
        val preferences = object : AppLanguagePreferencesStore {
            override val appLanguage = MutableStateFlow(AppLanguage.System)
            override suspend fun setAppLanguage(language: AppLanguage) { appLanguage.value = language }
        }
        AppLanguageRuntime.install(preferences, "en-US")
        val selections = mutableListOf<AppLanguage>()
        val effectiveLanguages = mutableListOf<AppLanguage>()
        backgroundScope.launch { AppLanguageRuntime.appLanguage.collect { selections += it } }
        backgroundScope.launch { AppLanguageRuntime.effectiveLanguage.collect { effectiveLanguages += it } }
        runCurrent()
        AppLanguageRuntime.updateSystemLanguage("zh-TW")
        assertEquals(AppLanguage.TraditionalChinese, AppLanguageRuntime.effectiveLanguage.value)
        assertEquals(listOf(AppLanguage.TraditionalChinese), AppLanguageRuntime.effectiveLanguage.replayCache)
        runCurrent()
        AppLanguageRuntime.select(AppLanguage.English)
        assertEquals(AppLanguage.English, AppLanguageRuntime.appLanguage.value)
        assertEquals(AppLanguage.English, AppLanguageRuntime.effectiveLanguage.value)
        runCurrent()
        AppLanguageRuntime.updateSystemLanguage("zh-CN")
        runCurrent()
        AppLanguageRuntime.select(AppLanguage.System)
        assertEquals(AppLanguage.SimplifiedChinese, AppLanguageRuntime.effectiveLanguage.value)
        runCurrent()
        assertEquals(listOf(AppLanguage.System, AppLanguage.English, AppLanguage.System), selections)
        assertEquals(listOf(AppLanguage.English, AppLanguage.TraditionalChinese, AppLanguage.English,
            AppLanguage.SimplifiedChinese), effectiveLanguages)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun concurrentSelectionsKeepPersistenceAndRuntimeInTheSameOrder() = runTest {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<AppLanguage>()
        val preferences = object : AppLanguagePreferencesStore {
            override val appLanguage = MutableStateFlow(AppLanguage.System)
            override suspend fun setAppLanguage(language: AppLanguage) {
                writes += language
                if (language == AppLanguage.SimplifiedChinese) {
                    firstWriteStarted.complete(Unit)
                    releaseFirstWrite.await()
                }
                appLanguage.value = language
                AppLanguageRuntime.update(language)
            }
        }
        AppLanguageRuntime.install(preferences, "zh-TW")
        val firstSelection = launch { AppLanguageRuntime.select(AppLanguage.SimplifiedChinese) }
        firstWriteStarted.await()
        val secondSelection = launch { AppLanguageRuntime.select(AppLanguage.English) }
        runCurrent()
        assertEquals(listOf(AppLanguage.SimplifiedChinese), writes)
        AppLanguageRuntime.updateSystemLanguage("zh-HK")
        releaseFirstWrite.complete(Unit)
        firstSelection.join()
        secondSelection.join()
        assertEquals(listOf(AppLanguage.SimplifiedChinese, AppLanguage.English), writes)
        assertEquals(AppLanguage.English, preferences.appLanguage.value)
        assertEquals(AppLanguage.English, AppLanguageRuntime.appLanguage.value)
        assertEquals(AppLanguage.English, AppLanguageRuntime.effectiveLanguage.value)
    }

    @Test fun systemLanguageChangesPreserveManualPreferenceAndDoNotWriteSettings() = runTest {
        var writes = 0
        val preferences = object : AppLanguagePreferencesStore {
            override val appLanguage = MutableStateFlow(AppLanguage.System)
            override suspend fun setAppLanguage(language: AppLanguage) {
                writes++
                appLanguage.value = language
            }
        }
        AppLanguageRuntime.install(preferences, "zh-CN")
        listOf("en-US", "zh-TW", "zh-SG").forEach { tag ->
            AppLanguageRuntime.updateSystemLanguage(tag)
            assertEquals(resolveSystemAppLanguage(tag), AppLanguageRuntime.effectiveLanguage.value)
            assertEquals(AppLanguage.System, AppLanguageRuntime.appLanguage.value)
            assertEquals(AppLanguage.System, preferences.appLanguage.value)
        }
        assertEquals(0, writes)
        AppLanguageRuntime.select(AppLanguage.English)
        AppLanguageRuntime.updateSystemLanguage("zh-HK")
        assertEquals(AppLanguage.English, AppLanguageRuntime.effectiveLanguage.value)
        assertEquals(AppLanguage.English, preferences.appLanguage.value)
        assertEquals(1, writes)
        AppLanguageRuntime.select(AppLanguage.System)
        assertEquals(AppLanguage.TraditionalChinese, AppLanguageRuntime.effectiveLanguage.value)
        assertEquals(2, writes)
    }

    @Test fun systemLanguageMatching() = runTest {
        listOf("zh", "zh-CN", "zh-SG", "zh_Hans_HK", "zh-Hans", "zh-US").forEach {
            assertEquals(AppLanguage.SimplifiedChinese, resolveSystemAppLanguage(it), it)
        }
        listOf("zh-TW", "zh-HK", "zh-MO", "zh-Hant-CN", "ZH_hant").forEach {
            assertEquals(AppLanguage.TraditionalChinese, resolveSystemAppLanguage(it), it)
        }
        listOf("en", "en-US", "fr", "ja-JP", "", "x-zh").forEach {
            assertEquals(AppLanguage.English, resolveSystemAppLanguage(it), it)
        }
    }

    @Test fun stablePreferenceValues() = runTest {
        AppLanguage.entries.forEach { assertEquals(it, appLanguageOrDefault(it.storageValue)) }
        assertEquals(AppLanguage.System, appLanguageOrDefault(null))
        assertEquals(AppLanguage.System, appLanguageOrDefault("unknown"))
    }

    @Test fun repeatedSelectionAndFollowSystem() = runTest {
        val preferences = object : AppLanguagePreferencesStore {
            override val appLanguage = MutableStateFlow(AppLanguage.System)
            override suspend fun setAppLanguage(language: AppLanguage) { appLanguage.value = language }
        }
        AppLanguageRuntime.install(preferences, "zh-HK")
        assertEquals(AppLanguage.TraditionalChinese, AppLanguageRuntime.effectiveLanguage.value)
        repeat(2) {
            AppLanguage.entries.forEach { language ->
                AppLanguageRuntime.select(language)
                assertEquals(language, preferences.appLanguage.value)
                assertEquals(language, AppLanguageRuntime.appLanguage.value)
                assertEquals(if (language == AppLanguage.System) AppLanguage.TraditionalChinese else language,
                    AppLanguageRuntime.effectiveLanguage.value)
            }
        }
        AppLanguageRuntime.updateSystemLanguage("zh-CN")
        assertEquals(AppLanguage.English, AppLanguageRuntime.effectiveLanguage.value)
        AppLanguageRuntime.select(AppLanguage.System)
        assertEquals(AppLanguage.SimplifiedChinese, AppLanguageRuntime.effectiveLanguage.value)
    }

    @Test fun messageParametersArePreservedAcrossLanguages() = runTest {
        val details = "server: 中文 details %1\$s"
        val text = uiText(Res.string.ui_error_with_context, uiText(Res.string.ui_operation_failed), UiText.Raw(details))
        assertEquals("Operation failed.\n$details", resolveUiText(text, AppLanguage.English))
        assertEquals("操作失败。\n$details", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("操作失敗。\n$details", resolveUiText(text, AppLanguage.TraditionalChinese))
        assertFailsWith<IndexOutOfBoundsException> { resolveUiText(uiText(Res.string.ui_error_with_context), AppLanguage.English) }
    }

    @Test fun completeSentenceSupportsMultipleQuantities() = runTest {
        val summary = uiText(Res.string.import_scan_summary, uiPlural(Res.plurals.import_audio_file_count, (1).toInt(), 1),
            uiPlural(Res.plurals.common_track_count_short, (2).toInt(), 2), uiPlural(Res.plurals.import_failure_count, (0).toInt(), 0))
        assertEquals("Found 1 audio file, imported 2 songs, 0 failed", resolveUiText(summary, AppLanguage.English))
        assertEquals("发现 1 个音频文件，成功导入 2 首，0 个失败", resolveUiText(summary, AppLanguage.SimplifiedChinese))
        assertEquals("發現 1 個音訊檔案，成功匯入 2 首，0 個失敗", resolveUiText(summary, AppLanguage.TraditionalChinese))
    }

    @Test fun zeroOneAndTwoQuantities() = runTest {
        val resource = Res.plurals.common_track_count_short
        assertEquals("0 songs", resolveUiText(uiPlural(resource, 0, 0), AppLanguage.English))
        assertEquals("1 song", resolveUiText(uiPlural(resource, 1, 1), AppLanguage.English))
        assertEquals("2 songs", resolveUiText(uiPlural(resource, 2, 2), AppLanguage.English))
        assertEquals("1 首", resolveUiText(uiPlural(resource, 1, 1), AppLanguage.SimplifiedChinese))
        assertEquals("2 首", resolveUiText(uiPlural(resource, 2, 2), AppLanguage.TraditionalChinese))
    }
}
