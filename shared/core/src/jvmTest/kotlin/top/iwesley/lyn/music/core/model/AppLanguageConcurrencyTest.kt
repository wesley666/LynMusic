package top.iwesley.lyn.music.core.model

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class AppLanguageConcurrencyTest {
    @Test fun systemRefreshCannotOverwriteAConcurrentManualSelection() {
        val preferences = object : AppLanguagePreferencesStore {
            override val appLanguage = MutableStateFlow(AppLanguage.System)
            override suspend fun setAppLanguage(language: AppLanguage) {
                // Match the Android preference adapter's update order.
                AppLanguageRuntime.updateSystemLanguage("en-US")
                appLanguage.value = language
                AppLanguageRuntime.update(language)
            }
        }
        AppLanguageRuntime.install(preferences, "en-US")
        val iterations = 100_000
        val barrier = CyclicBarrier(3)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val selection = executor.submit {
                repeat(iterations) {
                    barrier.await(10, TimeUnit.SECONDS)
                    runBlocking { AppLanguageRuntime.select(AppLanguage.English) }
                    barrier.await(10, TimeUnit.SECONDS)
                }
            }
            val systemRefresh = executor.submit {
                repeat(iterations) {
                    barrier.await(10, TimeUnit.SECONDS)
                    AppLanguageRuntime.updateSystemLanguage("zh-CN")
                    barrier.await(10, TimeUnit.SECONDS)
                }
            }
            repeat(iterations) { iteration ->
                preferences.appLanguage.value = AppLanguage.System
                AppLanguageRuntime.update(AppLanguage.System)
                barrier.await(10, TimeUnit.SECONDS)
                barrier.await(10, TimeUnit.SECONDS)
                assertEquals(AppLanguage.English, preferences.appLanguage.value, "Saved preference at $iteration")
                assertEquals(AppLanguage.English, AppLanguageRuntime.appLanguage.value, "Runtime preference at $iteration")
                assertEquals(AppLanguage.English, AppLanguageRuntime.effectiveLanguage.value, "Display language at $iteration")
            }
            selection.get(10, TimeUnit.SECONDS)
            systemRefresh.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }
}
