package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppLanguageRevisionTest {
    @Test fun delayedSubscribersStillObserveARoundTripToTheSameLanguage() = runTest {
        AppLanguageRuntime.update(AppLanguage.English)
        val initial = AppLanguageRuntime.effectiveLanguageRevision.value
        val revisions = mutableListOf<Long>()
        backgroundScope.launch { AppLanguageRuntime.effectiveLanguageRevision.collect { revisions += it } }
        runCurrent()

        AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
        AppLanguageRuntime.update(AppLanguage.English)
        assertEquals(AppLanguage.English, AppLanguageRuntime.effectiveLanguage.value)
        assertEquals(initial + 2, AppLanguageRuntime.effectiveLanguageRevision.value)
        assertEquals(listOf(initial + 2), AppLanguageRuntime.effectiveLanguageRevision.replayCache)
        runCurrent()
        assertEquals(listOf(initial, initial + 2), revisions)
    }

    @Test fun noOpChangesAndReinstallationPreserveTheRevisionSequence() = runTest {
        val preferences = object : AppLanguagePreferencesStore {
            override val appLanguage = MutableStateFlow(AppLanguage.English)
            var writes = 0
            override suspend fun setAppLanguage(language: AppLanguage) {
                writes++
                appLanguage.value = language
            }
        }
        AppLanguageRuntime.install(preferences, "en-US")
        val initial = AppLanguageRuntime.effectiveLanguageRevision.value
        val revisions = mutableListOf<Long>()
        backgroundScope.launch { AppLanguageRuntime.effectiveLanguageRevision.collect { revisions += it } }
        runCurrent()
        AppLanguageRuntime.update(AppLanguage.English)
        AppLanguageRuntime.updateSystemLanguage("zh-TW")
        AppLanguageRuntime.install(preferences, "zh-CN")
        runCurrent()
        assertEquals(listOf(initial), revisions)

        AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
        runCurrent()
        AppLanguageRuntime.install(preferences, "en-US")
        runCurrent()
        assertEquals(listOf(initial, initial + 1, initial + 2), revisions)
        assertEquals(0, preferences.writes)
        assertEquals(AppLanguage.English, preferences.appLanguage.value)
    }
}
