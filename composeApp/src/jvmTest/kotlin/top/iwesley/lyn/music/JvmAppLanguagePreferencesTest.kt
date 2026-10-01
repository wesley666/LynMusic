package top.iwesley.lyn.music

import java.nio.file.Files
import java.io.File
import java.util.Properties
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.JvmAppDataDirectory
import top.iwesley.lyn.music.platform.JvmDataLocationManager
import top.iwesley.lyn.music.platform.JvmAppPreferencesStore
import top.iwesley.lyn.music.platform.createJvmAppComponent
import top.iwesley.lyn.music.platform.initializeJvmAppLanguage

class JvmAppLanguagePreferencesTest {
    @Test fun startupReadsCustomDirectoryLanguageBeforeDatabaseFailure() = withTemporaryLanguageHome { home ->
        home.resolve(".lynmusic").apply { mkdirs() }
            .resolve("settings.properties").writeText("app_language=zh-Hans\n")
        val activeRoot = home.resolve("custom/LynMusic").apply { mkdirs() }
        activeRoot.resolve(".lynmusic-data-root").writeText("owned")
        activeRoot.resolve("settings.properties").writeText("app_language=en\n")
        activeRoot.resolve("lynmusic.db").mkdir()
        val properties = Properties().apply { setProperty("active_data_root", activeRoot.absolutePath) }
        home.resolve(".lynmusic-location.properties").outputStream().use { properties.store(it, null) }
        val manager = JvmDataLocationManager(home, "Windows 11")
        val systemLocale = Locale.getDefault()

        initializeJvmAppLanguage(manager)

        assertEquals(AppLanguage.English, AppLanguageRuntime.appLanguage.value)
        assertEquals(AppLanguage.English, AppLanguageRuntime.effectiveLanguage.value)
        assertFailsWith<Exception> { createJvmAppComponent(manager) }
        assertEquals(AppLanguage.English, AppLanguageRuntime.appLanguage.value)
        assertEquals(AppLanguage.English, AppLanguageRuntime.effectiveLanguage.value)
        assertEquals(systemLocale, Locale.getDefault())
    }

    @Test fun startupReadsDefaultDirectoryWhenNoCustomLocationIsConfigured() = withTemporaryLanguageHome { home ->
        home.resolve(".lynmusic").apply { mkdirs() }
            .resolve("settings.properties").writeText("app_language=zh-Hant\n")

        initializeJvmAppLanguage(JvmDataLocationManager(home, "Windows 11"))

        assertEquals(AppLanguage.TraditionalChinese, AppLanguageRuntime.appLanguage.value)
        assertEquals(AppLanguage.TraditionalChinese, AppLanguageRuntime.effectiveLanguage.value)
    }

    @Test fun startupFallsBackToSystemWhenSettingsCannotBeRead() = withTemporaryLanguageHome { home ->
        home.resolve(".lynmusic/settings.properties").mkdirs()
        AppLanguageRuntime.update(AppLanguage.TraditionalChinese)

        initializeJvmAppLanguage(JvmDataLocationManager(home, "Windows 11"))

        assertEquals(AppLanguage.System, AppLanguageRuntime.appLanguage.value)
        assertEquals(
            top.iwesley.lyn.music.core.model.resolveSystemAppLanguage(Locale.getDefault().toLanguageTag()),
            AppLanguageRuntime.effectiveLanguage.value,
        )
    }

    @Test fun startupDoesNotReadStaleDefaultPreferenceWhenCustomRootIsInvalid() = withTemporaryLanguageHome { home ->
        home.resolve(".lynmusic").apply { mkdirs() }
            .resolve("settings.properties").writeText("app_language=zh-Hant\n")
        val activeRoot = home.resolve("custom/LynMusic").apply { mkdirs() }
        val properties = Properties().apply { setProperty("active_data_root", activeRoot.absolutePath) }
        home.resolve(".lynmusic-location.properties").outputStream().use { properties.store(it, null) }

        initializeJvmAppLanguage(JvmDataLocationManager(home, "Windows 11"))

        assertEquals(AppLanguage.System, AppLanguageRuntime.appLanguage.value)
    }

    @Test fun failedSettingsFileWriteKeepsSelectionAndRuntimeLanguage() = runTest {
        val directory = Files.createTempDirectory("lyn-language-write-failure").toFile()
        try {
            val file = directory.resolve("settings.properties")
            file.writeText("app_language=zh-Hant\n")
            val store = JvmAppPreferencesStore(file)
            assertEquals(AppLanguage.TraditionalChinese, store.appLanguage.value)
            kotlin.test.assertTrue(file.delete())
            kotlin.test.assertTrue(file.mkdir())
            kotlin.test.assertFailsWith<java.io.IOException> { store.setAppLanguage(AppLanguage.English) }
            assertEquals(AppLanguage.TraditionalChinese, store.appLanguage.value)
            assertEquals(AppLanguage.TraditionalChinese, AppLanguageRuntime.appLanguage.value)
            assertEquals(AppLanguage.TraditionalChinese, AppLanguageRuntime.effectiveLanguage.value)
        } finally { directory.deleteRecursively() }
    }

    @Test fun languagePersistsWithoutChangingSystemLocaleOrOtherPreferences() = runTest {
        val directory = Files.createTempDirectory("lyn-language-test").toFile()
        try {
            val file = directory.resolve("settings.properties")
            file.writeText("custom_user_value=用户的数据\napp_language=invalid\n")
            val systemLocale = Locale.getDefault()
            val store = JvmAppPreferencesStore(file)
            assertEquals(AppLanguage.System, store.appLanguage.value)
            for (language in AppLanguage.entries) {
                store.setAppLanguage(language)
                assertEquals(language, JvmAppPreferencesStore(file).appLanguage.value)
                assertEquals(language, AppLanguageRuntime.appLanguage.value)
                assertEquals(systemLocale, Locale.getDefault())
            }
            kotlin.test.assertTrue(file.readText().contains("custom_user_value="))
        } finally { directory.deleteRecursively() }
    }
}

private fun withTemporaryLanguageHome(action: (File) -> Unit) {
    val home = Files.createTempDirectory("lyn-startup-language").toFile()
    val originalRoot = JvmAppDataDirectory.rootDirectory()
    val originalLanguage = AppLanguageRuntime.appLanguage.value
    try {
        JvmAppDataDirectory.initialize(home.resolve(".lynmusic"))
        action(home)
    } finally {
        JvmAppDataDirectory.initialize(originalRoot)
        AppLanguageRuntime.update(originalLanguage)
        home.deleteRecursively()
    }
}
