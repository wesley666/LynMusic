package top.iwesley.lyn.music.automotive.ui

import top.iwesley.lyn.music.*

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.resources.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Coroutine and text logic only; no composition, renderer, window or device. */
@OptIn(ExperimentalCoroutinesApi::class)
class UiLanguageStateTest {
    @Test fun delayedResultsNeverOverwriteTheLatestLanguageOrText() = runTest {
        val original = AppLanguageRuntime.appLanguage.value
        AppLanguageRuntime.update(AppLanguage.English)
        val descriptions = MutableStateFlow<UiText?>(uiText(Res.string.common_close))
        val displayed = mutableListOf<String?>()
        val observer = backgroundScope.launch {
            observeResolvedUiText(descriptions, resolve = { text, language ->
                withContext(NonCancellable) { delay(100) }
                resolveUiText(text, language)
            }) { value, _ -> displayed += value }
        }
        try {
            runCurrent()
            AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
            runCurrent()
            descriptions.value = UiText.Raw("用户详情 %1\$s")
            AppLanguageRuntime.update(AppLanguage.SimplifiedChinese)
            runCurrent()
            advanceTimeBy(300)
            runCurrent()
            assertEquals(listOf<String?>("用户详情 %1\$s"), displayed)
            descriptions.value = null
            runCurrent()
            assertNull(displayed.last())
        } finally { observer.cancel(); AppLanguageRuntime.update(original) }
    }

    @Test fun repeatedLanguageRoundTripsRefreshAnExistingDescription() = runTest(timeout = 10.seconds) {
        val original = AppLanguageRuntime.appLanguage.value
        val descriptions = MutableStateFlow<UiText?>(uiText(Res.string.common_close))
        // Resource loading uses an external dispatcher, so await each resolved value.
        val displayed = Channel<String?>(Channel.UNLIMITED)
        val observer = backgroundScope.launch {
            observeResolvedUiText(descriptions) { value, _ -> displayed.send(value) }
        }
        try {
            repeat(3) {
                AppLanguageRuntime.update(AppLanguage.English)
                assertEquals("Close", displayed.receive())
                AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
                assertEquals("關閉", displayed.receive())
                AppLanguageRuntime.update(AppLanguage.SimplifiedChinese)
                assertEquals("关闭", displayed.receive())
            }
        } finally {
            withContext(NonCancellable) {
                observer.cancelAndJoin()
                displayed.close()
                AppLanguageRuntime.update(original)
            }
        }
    }

    @Test fun queuedNativeUpdatesRecheckBothLanguageAndDescription() = runTest {
        val original = AppLanguageRuntime.appLanguage.value
        AppLanguageRuntime.update(AppLanguage.English)
        val descriptions = MutableStateFlow<UiText?>(UiText.Raw("first"))
        val queued = mutableListOf<Pair<String?, () -> Boolean>>()
        val observer = backgroundScope.launch {
            observeResolvedUiText(descriptions) { value, isCurrent -> queued += value to isCurrent }
        }
        try {
            runCurrent()
            val first = queued.single()
            assertTrue(first.second())
            descriptions.value = UiText.Raw("latest")
            // A native event queue may run before the collector receives the next value.
            assertFalse(first.second())
            runCurrent()
            val latest = queued.last()
            assertTrue(latest.second())
            AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
            assertFalse(latest.second())
            runCurrent()
            assertEquals("latest", queued.last().first)
            assertTrue(queued.last().second())
        } finally { observer.cancel(); AppLanguageRuntime.update(original) }
    }

}
