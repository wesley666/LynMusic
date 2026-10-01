package top.iwesley.lyn.music.feature.settings

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import top.iwesley.lyn.music.testing.assertLocalizedEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppReleaseInfo
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppStorageCategoryUsage
import top.iwesley.lyn.music.core.model.AppStorageGateway
import top.iwesley.lyn.music.core.model.AppStorageSnapshot
import top.iwesley.lyn.music.core.model.AppDataLocationChangeMode
import top.iwesley.lyn.music.core.model.AppDataLocationPlatformService
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.DeviceInfoGateway
import top.iwesley.lyn.music.core.model.DeviceInfoSnapshot
import top.iwesley.lyn.music.core.model.DesktopLyricsPlatformService
import top.iwesley.lyn.music.core.model.DEFAULT_MINIMIZE_WINDOW_ON_CLOSE
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontKind
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.LynMusicUpdateLinks
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.VlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.WorkflowLyricsConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsStepConfig
import top.iwesley.lyn.music.core.model.WorkflowRequestConfig
import top.iwesley.lyn.music.core.model.WorkflowSearchConfig
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.deriveAppThemePalette
import top.iwesley.lyn.music.core.model.isAppReleaseNewer
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.data.repository.AppUpdateRepository
import top.iwesley.lyn.music.data.repository.SettingsRepository
import top.iwesley.lyn.music.domain.DEFAULT_LRCAPI_URL
import top.iwesley.lyn.music.domain.MANAGED_LRCAPI_SOURCE_ID
import top.iwesley.lyn.music.domain.buildManagedLrcApiConfig
import top.iwesley.lyn.music.domain.MANAGED_MUSICMATCH_SOURCE_ID
import top.iwesley.lyn.music.domain.buildManagedMusicmatchWorkflowJson
import top.iwesley.lyn.music.domain.parseWorkflowLyricsSourceConfig

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @Test
    fun languageSaveFailureIsReportedWithoutChangingSelectionOrForm() = runTest {
        val preferences = FakeSettingsRepository()
        preferences.setAppLanguage(AppLanguage.TraditionalChinese)
        val repository = object : SettingsRepository by preferences {
            override suspend fun setAppLanguage(language: AppLanguage) { error("磁盘原文 %1\$s") }
        }
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob() + CoroutineExceptionHandler { _, error -> uncaught += error })
        try {
            val store = SettingsStore(repository, scope)
            store.dispatch(SettingsIntent.NameChanged("用户来源"))
            store.dispatch(SettingsIntent.UrlChanged("https://example.test/lyrics"))
            advanceUntilIdle()
            val before = store.state.value
            store.dispatch(SettingsIntent.AppLanguageChanged(AppLanguage.English))
            advanceUntilIdle()
            val message = kotlin.test.assertNotNull(store.state.value.message)
            kotlin.test.assertEquals(before.copy(message = message), store.state.value)
            kotlin.test.assertEquals(AppLanguage.TraditionalChinese, preferences.appLanguage.value)
            assertTrue(uncaught.isEmpty())
            val expectations = listOf(
                AppLanguage.English to "The language setting could not be saved. Please try again.\n磁盘原文 %1\$s",
                AppLanguage.SimplifiedChinese to "语言设置保存失败，请重试。\n磁盘原文 %1\$s",
                AppLanguage.TraditionalChinese to "語言設定儲存失敗，請重試。\n磁盘原文 %1\$s",
            )
            for ((language, expected) in expectations) kotlin.test.assertEquals(expected, resolveUiText(message, language))
        } finally { scope.cancel() }
    }

    @Test
    fun cancelledLanguageSaveDoesNotBecomeAnErrorMessage() = runTest {
        val preferences = FakeSettingsRepository()
        val repository = object : SettingsRepository by preferences {
            override suspend fun setAppLanguage(language: AppLanguage) { throw CancellationException("cancelled") }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = SettingsStore(repository, scope)
            advanceUntilIdle()
            val before = store.state.value
            store.dispatch(SettingsIntent.AppLanguageChanged(AppLanguage.English))
            advanceUntilIdle()
            kotlin.test.assertEquals(before, store.state.value)
            kotlin.test.assertEquals(AppLanguage.System, preferences.appLanguage.value)
        } finally { scope.cancel() }
    }

    @Test
    fun delayedSaveFailureCannotRevertANewerSuccessfulSelection() = runTest {
        val preferences = FakeSettingsRepository()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = object : SettingsRepository by preferences {
            override suspend fun setAppLanguage(language: AppLanguage) {
                if (language == AppLanguage.SimplifiedChinese) {
                    entered.complete(Unit)
                    release.await()
                    error("disk unavailable")
                }
                preferences.setAppLanguage(language)
            }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = SettingsStore(repository, scope)
            store.dispatch(SettingsIntent.AppLanguageChanged(AppLanguage.SimplifiedChinese))
            entered.await()
            store.dispatch(SettingsIntent.AppLanguageChanged(AppLanguage.English))
            advanceUntilIdle()
            release.complete(Unit)
            advanceUntilIdle()
            kotlin.test.assertEquals(AppLanguage.English, store.state.value.appLanguage)
            kotlin.test.assertEquals(AppLanguage.English, preferences.appLanguage.value)
            kotlin.test.assertNotNull(store.state.value.message)
        } finally { scope.cancel() }
    }


    @Test
    fun delayedLanguageWriteCompletionCannotOverwriteTheObservedSelection() = runTest {
        val preferences = FakeSettingsRepository()
        val firstWritePublished = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val repository = object : SettingsRepository by preferences {
            override suspend fun setAppLanguage(language: AppLanguage) {
                preferences.setAppLanguage(language)
                if (language == AppLanguage.SimplifiedChinese) {
                    firstWritePublished.complete(Unit)
                    releaseFirstWrite.await()
                }
            }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = SettingsStore(repository, scope)
            store.dispatch(SettingsIntent.NameChanged("User lyrics source"))
            store.dispatch(SettingsIntent.UrlChanged("https://example.com/lyrics"))
            advanceUntilIdle()
            val before = store.state.value
            store.dispatch(SettingsIntent.AppLanguageChanged(AppLanguage.SimplifiedChinese))
            firstWritePublished.await()
            store.dispatch(SettingsIntent.AppLanguageChanged(AppLanguage.English))
            advanceUntilIdle()
            kotlin.test.assertEquals(AppLanguage.English, store.state.value.appLanguage)
            releaseFirstWrite.complete(Unit)
            advanceUntilIdle()
            kotlin.test.assertEquals(AppLanguage.English, preferences.appLanguage.value)
            kotlin.test.assertEquals(before.copy(appLanguage = AppLanguage.English), store.state.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `language changes preserve form and other settings`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)
        advanceUntilIdle()
        store.dispatch(SettingsIntent.NameChanged("User lyrics source"))
        store.dispatch(SettingsIntent.UrlChanged("https://example.com/lyrics"))
        advanceUntilIdle()
        val before = store.state.value
        for (language in top.iwesley.lyn.music.core.model.AppLanguage.entries) {
            store.dispatch(SettingsIntent.AppLanguageChanged(language))
            advanceUntilIdle()
            kotlin.test.assertEquals(before.copy(appLanguage = language), store.state.value)
        }
        scope.cancel()
    }

    @Test
    fun `store exposes lyrics share font import support when platform service is available`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            lyricsShareFontLibraryPlatformService = FakeLyricsShareFontLibraryPlatformService(),
        )

        advanceUntilIdle()

        assertTrue(store.state.value.supportsLyricsShareFontImport)
        scope.cancel()
    }

    @Test
    fun `loading imported lyrics share fonts writes list into state`() = runTest {
        val repository = FakeSettingsRepository()
        val fontLibrary = FakeLyricsShareFontLibraryPlatformService(
            initialFonts = listOf(
                LyricsShareFontOption(
                    fontKey = "imported:abc",
                    displayName = "My Imported Font",
                    previewText = "你好 Hello",
                    kind = LyricsShareFontKind.IMPORTED,
                    fontFilePath = "/tmp/abc__My Imported Font.ttf",
                ),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            lyricsShareFontLibraryPlatformService = fontLibrary,
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadLyricsShareImportedFonts)
        advanceUntilIdle()

        assertLocalizedEquals(1, fontLibrary.listCalls)
        assertLocalizedEquals(listOf("imported:abc"), store.state.value.importedLyricsShareFonts.map { it.fontKey })
        assertLocalizedEquals("/tmp/abc__My Imported Font.ttf", store.state.value.importedLyricsShareFonts.single().fontFilePath)
        assertFalse(store.state.value.lyricsShareFontsLoading)
        scope.cancel()
    }

    @Test
    fun `importing lyrics share font refreshes imported list and message`() = runTest {
        val repository = FakeSettingsRepository()
        val importedFont = LyricsShareFontOption(
            fontKey = "imported:def",
            displayName = "Fancy Imported Font",
            previewText = "你好 Hello",
            kind = LyricsShareFontKind.IMPORTED,
            fontFilePath = "/tmp/def__Fancy Imported Font.ttf",
        )
        val fontLibrary = FakeLyricsShareFontLibraryPlatformService().apply {
            nextImportResult = Result.success(importedFont)
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            lyricsShareFontLibraryPlatformService = fontLibrary,
        )
        val effects = mutableListOf<SettingsEffect>()
        val effectJob = launch { store.effects.collect { effects += it } }

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ImportLyricsShareFont)
        advanceUntilIdle()

        assertLocalizedEquals(1, fontLibrary.importCalls)
        assertLocalizedEquals(listOf("imported:def"), store.state.value.importedLyricsShareFonts.map { it.fontKey })
        assertLocalizedEquals("/tmp/def__Fancy Imported Font.ttf", store.state.value.importedLyricsShareFonts.single().fontFilePath)
        assertLocalizedEquals("字体已导入。", store.state.value.message)
        assertFalse(store.state.value.importingLyricsShareFont)
        assertLocalizedEquals(listOf<SettingsEffect>(SettingsEffect.LyricsShareFontsChanged), effects)
        effectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `desktop lyrics toggle writes repository when permission is available`() = runTest {
        val repository = FakeSettingsRepository()
        val desktopLyricsService = FakeDesktopLyricsPlatformService(permission = true)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            desktopLyricsPlatformService = desktopLyricsService,
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ShowDesktopLyricsChanged(true))
        advanceUntilIdle()

        assertTrue(repository.currentShowDesktopLyrics())
        assertTrue(store.state.value.showDesktopLyrics)
        assertLocalizedEquals(listOf(true), desktopLyricsService.enabledCalls)
        scope.cancel()
    }

    @Test
    fun `desktop lyrics toggle off disables service and hides overlay`() = runTest {
        val repository = FakeSettingsRepository(showDesktopLyrics = true)
        val desktopLyricsService = FakeDesktopLyricsPlatformService(permission = true)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            desktopLyricsPlatformService = desktopLyricsService,
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ShowDesktopLyricsChanged(false))
        advanceUntilIdle()

        assertFalse(repository.currentShowDesktopLyrics())
        assertFalse(store.state.value.showDesktopLyrics)
        assertLocalizedEquals(listOf(true, false), desktopLyricsService.enabledCalls)
        assertTrue(desktopLyricsService.hidden)
        scope.cancel()
    }

    @Test
    fun `desktop lyrics toggle does not save true when permission is missing`() = runTest {
        val repository = FakeSettingsRepository()
        val desktopLyricsService = FakeDesktopLyricsPlatformService(permission = false)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            desktopLyricsPlatformService = desktopLyricsService,
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ShowDesktopLyricsChanged(true))
        advanceUntilIdle()

        assertFalse(repository.currentShowDesktopLyrics())
        assertFalse(store.state.value.showDesktopLyrics)
        assertLocalizedEquals(1, desktopLyricsService.permissionRequests)
        scope.cancel()
    }

    @Test
    fun `desktop lyrics permission recheck enables pending request when permission is granted`() = runTest {
        val repository = FakeSettingsRepository()
        val desktopLyricsService = FakeDesktopLyricsPlatformService(permission = false)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            desktopLyricsPlatformService = desktopLyricsService,
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ShowDesktopLyricsChanged(true))
        advanceUntilIdle()
        desktopLyricsService.permission = true
        store.dispatch(SettingsIntent.RecheckDesktopLyricsPermission)
        advanceUntilIdle()

        assertTrue(repository.currentShowDesktopLyrics())
        assertTrue(store.state.value.showDesktopLyrics)
        assertLocalizedEquals(listOf(true), desktopLyricsService.enabledCalls)
        scope.cancel()
    }

    @Test
    fun `menu bar lyrics controls toggle writes repository when supported`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ShowMenuBarLyricsControlsChanged(true))
        advanceUntilIdle()

        assertTrue(repository.currentShowMenuBarLyricsControls())
        assertTrue(store.state.value.showMenuBarLyricsControls)
        scope.cancel()
    }

    @Test
    fun `importing lyrics share font emits change effect even when list refresh fails`() = runTest {
        val repository = FakeSettingsRepository()
        val importedFont = LyricsShareFontOption(
            fontKey = "imported:def",
            displayName = "Fancy Imported Font",
            previewText = "你好 Hello",
            kind = LyricsShareFontKind.IMPORTED,
        )
        val fontLibrary = FakeLyricsShareFontLibraryPlatformService().apply {
            nextImportResult = Result.success(importedFont)
            nextListResult = Result.failure(IllegalStateException("读取失败"))
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            lyricsShareFontLibraryPlatformService = fontLibrary,
        )
        val effects = mutableListOf<SettingsEffect>()
        val effectJob = launch { store.effects.collect { effects += it } }

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ImportLyricsShareFont)
        advanceUntilIdle()

        assertLocalizedEquals(1, fontLibrary.importCalls)
        assertLocalizedEquals("字体已导入，但刷新列表失败。\n读取失败", store.state.value.message)
        assertFalse(store.state.value.importingLyricsShareFont)
        assertLocalizedEquals(listOf<SettingsEffect>(SettingsEffect.LyricsShareFontsChanged), effects)
        effectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `deleting selected imported font clears saved key and refreshes list`() = runTest {
        val repository = FakeSettingsRepository()
        val fontLibrary = FakeLyricsShareFontLibraryPlatformService(
            initialFonts = listOf(
                LyricsShareFontOption(
                    fontKey = "imported:gone",
                    displayName = "Imported Font",
                    previewText = "你好 Hello",
                    kind = LyricsShareFontKind.IMPORTED,
                ),
            ),
        )
        val fontPreferencesStore = FakeLyricsShareFontPreferencesStore(initialFontKey = "imported:gone")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            lyricsShareFontLibraryPlatformService = fontLibrary,
            lyricsShareFontPreferencesStore = fontPreferencesStore,
        )
        val effects = mutableListOf<SettingsEffect>()
        val effectJob = launch { store.effects.collect { effects += it } }

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadLyricsShareImportedFonts)
        advanceUntilIdle()
        store.dispatch(SettingsIntent.DeleteLyricsShareImportedFont("imported:gone"))
        advanceUntilIdle()

        assertLocalizedEquals(listOf("imported:gone"), fontLibrary.deletedFontKeys)
        assertTrue(store.state.value.importedLyricsShareFonts.isEmpty())
        assertLocalizedEquals(null, fontPreferencesStore.selectedLyricsShareFontKey.value)
        assertLocalizedEquals("字体已删除。", store.state.value.message)
        assertLocalizedEquals(null, store.state.value.deletingLyricsShareFontKey)
        assertLocalizedEquals(listOf<SettingsEffect>(SettingsEffect.LyricsShareFontsChanged), effects)
        effectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `store loads persisted theme state`() = runTest {
        val customTokens = AppThemeTokens(
            backgroundArgb = 0xFF101820.toInt(),
            accentArgb = 0xFF2F9E44.toInt(),
            focusArgb = 0xFF94D82D.toInt(),
        )
        val repository = FakeSettingsRepository(
            selectedTheme = AppThemeId.Custom,
            customThemeTokens = customTokens,
            textPalettePreferences = defaultThemeTextPalettePreferences().withThemePalette(
                AppThemeId.Custom,
                AppThemeTextPalette.Black,
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(AppThemeId.Custom, state.selectedTheme)
        assertLocalizedEquals(customTokens, state.customThemeTokens)
        assertLocalizedEquals(AppThemeTextPalette.Black, state.textPalettePreferences.custom)
        scope.cancel()
    }

    @Test
    fun `store loads persisted desktop vlc path state`() = runTest {
        val repository = FakeSettingsRepository(
            desktopVlcAutoDetectedPath = "/Applications/VLC.app/Contents/MacOS/lib",
            desktopVlcManualPath = "/opt/homebrew/Cellar/vlc/lib",
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("/Applications/VLC.app/Contents/MacOS/lib", state.desktopVlcAutoDetectedPath)
        assertLocalizedEquals("/opt/homebrew/Cellar/vlc/lib", state.desktopVlcManualPath)
        assertLocalizedEquals("/opt/homebrew/Cellar/vlc/lib", state.desktopVlcEffectivePath)
        scope.cancel()
    }

    @Test
    fun `store loads persisted compact player lyrics preference`() = runTest {
        val repository = FakeSettingsRepository(showCompactPlayerLyrics = true)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertTrue(store.state.value.showCompactPlayerLyrics)
        scope.cancel()
    }

    @Test
    fun `updating compact player lyrics preference writes through immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        assertFalse(store.state.value.showCompactPlayerLyrics)

        store.dispatch(SettingsIntent.ShowCompactPlayerLyricsChanged(true))
        advanceUntilIdle()
        assertTrue(store.state.value.showCompactPlayerLyrics)
        assertTrue(repository.currentShowCompactPlayerLyrics())

        store.dispatch(SettingsIntent.ShowCompactPlayerLyricsChanged(false))
        advanceUntilIdle()
        assertFalse(store.state.value.showCompactPlayerLyrics)
        assertFalse(repository.currentShowCompactPlayerLyrics())
        scope.cancel()
    }

    @Test
    fun `auto play on startup preference defaults to false`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertFalse(store.state.value.autoPlayOnStartup)
        scope.cancel()
    }

    @Test
    fun `store loads persisted auto play on startup preference`() = runTest {
        val repository = FakeSettingsRepository(autoPlayOnStartup = true)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertTrue(store.state.value.autoPlayOnStartup)
        scope.cancel()
    }

    @Test
    fun `updating auto play on startup preference writes through immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        assertFalse(store.state.value.autoPlayOnStartup)

        store.dispatch(SettingsIntent.AutoPlayOnStartupChanged(true))
        advanceUntilIdle()
        assertTrue(store.state.value.autoPlayOnStartup)
        assertTrue(repository.currentAutoPlayOnStartup())

        store.dispatch(SettingsIntent.AutoPlayOnStartupChanged(false))
        advanceUntilIdle()
        assertFalse(store.state.value.autoPlayOnStartup)
        assertFalse(repository.currentAutoPlayOnStartup())
        scope.cancel()
    }

    @Test
    fun `auto open player on startup preference defaults to false`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        assertFalse(store.state.value.autoOpenPlayerOnStartup)
        scope.cancel()
    }

    @Test
    fun `store initializes persisted auto open player on startup preference synchronously`() = runTest {
        val repository = FakeSettingsRepository(autoOpenPlayerOnStartup = true)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        assertTrue(store.state.value.autoOpenPlayerOnStartup)
        scope.cancel()
    }

    @Test
    fun `updating auto open player on startup preference writes through immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        store.dispatch(SettingsIntent.AutoOpenPlayerOnStartupChanged(true))
        advanceUntilIdle()
        assertTrue(store.state.value.autoOpenPlayerOnStartup)
        assertTrue(repository.currentAutoOpenPlayerOnStartup())

        store.dispatch(SettingsIntent.AutoOpenPlayerOnStartupChanged(false))
        advanceUntilIdle()
        assertFalse(store.state.value.autoOpenPlayerOnStartup)
        assertFalse(repository.currentAutoOpenPlayerOnStartup())
        scope.cancel()
    }

    @Test
    fun `minimize window on close preference defaults to true immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        assertTrue(store.state.value.minimizeWindowOnClose)
        scope.cancel()
    }

    @Test
    fun `store loads persisted disabled minimize window on close preference immediately`() = runTest {
        val repository = FakeSettingsRepository(minimizeWindowOnClose = false)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        assertFalse(store.state.value.minimizeWindowOnClose)
        scope.cancel()
    }

    @Test
    fun `updating minimize window on close preference writes through immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        store.dispatch(SettingsIntent.MinimizeWindowOnCloseChanged(false))
        assertFalse(store.state.value.minimizeWindowOnClose)
        assertTrue(repository.currentMinimizeWindowOnClose())

        advanceUntilIdle()
        assertFalse(repository.currentMinimizeWindowOnClose())

        store.dispatch(SettingsIntent.MinimizeWindowOnCloseChanged(true))
        assertTrue(store.state.value.minimizeWindowOnClose)
        assertFalse(repository.currentMinimizeWindowOnClose())

        advanceUntilIdle()
        assertTrue(repository.currentMinimizeWindowOnClose())
        scope.cancel()
    }

    @Test
    fun `latest minimize window on close value wins while an older write is pending`() = runTest {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        var writeCount = 0
        val repository = FakeSettingsRepository(
            onSetMinimizeWindowOnClose = {
                if (writeCount++ == 0) {
                    firstWriteStarted.complete(Unit)
                    releaseFirstWrite.await()
                }
            },
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)
        var persistenceWait: Deferred<Unit>? = null

        try {
            store.dispatch(SettingsIntent.MinimizeWindowOnCloseChanged(false))
            runCurrent()
            assertTrue(firstWriteStarted.isCompleted)
            val activePersistenceWait = async(start = CoroutineStart.UNDISPATCHED) {
                store.awaitMinimizeWindowOnClosePersistence()
            }
            persistenceWait = activePersistenceWait
            assertFalse(activePersistenceWait.isCompleted)

            store.dispatch(SettingsIntent.MinimizeWindowOnCloseChanged(true))
            assertTrue(store.state.value.minimizeWindowOnClose)

            releaseFirstWrite.complete(Unit)
            advanceUntilIdle()

            assertTrue(store.state.value.minimizeWindowOnClose)
            assertTrue(repository.currentMinimizeWindowOnClose())
            activePersistenceWait.await()
        } finally {
            releaseFirstWrite.complete(Unit)
            persistenceWait?.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `stale failed minimize write cannot roll back an ABA sequence`() = runTest {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        var writeCount = 0
        val repository = FakeSettingsRepository(
            onSetMinimizeWindowOnClose = {
                if (writeCount++ == 0) {
                    firstWriteStarted.complete(Unit)
                    releaseFirstWrite.await()
                    error("first write failed")
                }
            },
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        try {
            store.dispatch(SettingsIntent.MinimizeWindowOnCloseChanged(false))
            runCurrent()
            assertTrue(firstWriteStarted.isCompleted)

            store.dispatch(SettingsIntent.MinimizeWindowOnCloseChanged(true))
            store.dispatch(SettingsIntent.MinimizeWindowOnCloseChanged(false))
            assertFalse(store.state.value.minimizeWindowOnClose)

            releaseFirstWrite.complete(Unit)
            advanceUntilIdle()

            assertLocalizedEquals(2, writeCount)
            assertFalse(store.state.value.minimizeWindowOnClose)
            assertFalse(repository.currentMinimizeWindowOnClose())
            assertLocalizedEquals(null, store.state.value.message)
        } finally {
            releaseFirstWrite.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun `failed minimize window on close write restores persisted value`() = runTest {
        val repository = FakeSettingsRepository(
            onSetMinimizeWindowOnClose = { error("disk unavailable") },
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        store.dispatch(SettingsIntent.MinimizeWindowOnCloseChanged(false))
        assertFalse(store.state.value.minimizeWindowOnClose)

        advanceUntilIdle()

        assertTrue(store.state.value.minimizeWindowOnClose)
        assertLocalizedEquals("关闭按钮行为保存失败。", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `cancelled store scope does not leave minimize persistence pending`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)
        scope.cancel()

        store.dispatch(SettingsIntent.MinimizeWindowOnCloseChanged(false))
        store.awaitMinimizeWindowOnClosePersistence()

        assertFalse(store.state.value.minimizeWindowOnClose)
        assertTrue(repository.currentMinimizeWindowOnClose())
    }

    @Test
    fun `android extension decoder preference defaults to false`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertFalse(store.state.value.useAndroidExtensionDecoder)
        scope.cancel()
    }

    @Test
    fun `store loads persisted android extension decoder preference`() = runTest {
        val repository = FakeSettingsRepository(useAndroidExtensionDecoder = true)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertTrue(store.state.value.useAndroidExtensionDecoder)
        scope.cancel()
    }

    @Test
    fun `updating android extension decoder preference writes through immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        assertFalse(store.state.value.useAndroidExtensionDecoder)

        store.dispatch(SettingsIntent.AndroidExtensionDecoderChanged(true))
        advanceUntilIdle()
        assertTrue(store.state.value.useAndroidExtensionDecoder)
        assertTrue(repository.currentUseAndroidExtensionDecoder())

        store.dispatch(SettingsIntent.AndroidExtensionDecoderChanged(false))
        advanceUntilIdle()
        assertFalse(store.state.value.useAndroidExtensionDecoder)
        assertFalse(repository.currentUseAndroidExtensionDecoder())
        scope.cancel()
    }

    @Test
    fun `store loads persisted player artwork style`() = runTest {
        val repository = FakeSettingsRepository(playerArtworkStyle = PlayerArtworkStyle.HALF_RECORD)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertLocalizedEquals(PlayerArtworkStyle.HALF_RECORD, store.state.value.playerArtworkStyle)
        scope.cancel()
    }

    @Test
    fun `updating player artwork style writes through immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        assertLocalizedEquals(PlayerArtworkStyle.VINYL, store.state.value.playerArtworkStyle)

        store.dispatch(SettingsIntent.PlayerArtworkStyleChanged(PlayerArtworkStyle.MINIMAL_COVER))
        advanceUntilIdle()

        assertLocalizedEquals(PlayerArtworkStyle.MINIMAL_COVER, store.state.value.playerArtworkStyle)
        assertLocalizedEquals(PlayerArtworkStyle.MINIMAL_COVER, repository.currentPlayerArtworkStyle())
        scope.cancel()
    }

    @Test
    fun `store loads persisted app display scale preset`() = runTest {
        val repository = FakeSettingsRepository(appDisplayScalePreset = AppDisplayScalePreset.Large)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertLocalizedEquals(AppDisplayScalePreset.Large, store.state.value.appDisplayScalePreset)
        scope.cancel()
    }

    @Test
    fun `updating app display scale preset writes through immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        assertLocalizedEquals(AppDisplayScalePreset.Default, store.state.value.appDisplayScalePreset)

        store.dispatch(SettingsIntent.AppDisplayScalePresetChanged(AppDisplayScalePreset.Large))
        advanceUntilIdle()

        assertLocalizedEquals(AppDisplayScalePreset.Large, store.state.value.appDisplayScalePreset)
        assertLocalizedEquals(AppDisplayScalePreset.Large, repository.currentAppDisplayScalePreset())
        scope.cancel()
    }

    @Test
    fun `app display scale preset flow updates store state`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        assertLocalizedEquals(AppDisplayScalePreset.Default, store.state.value.appDisplayScalePreset)

        repository.setAppDisplayScalePreset(AppDisplayScalePreset.Compact)
        advanceUntilIdle()

        assertLocalizedEquals(AppDisplayScalePreset.Compact, store.state.value.appDisplayScalePreset)
        scope.cancel()
    }

    @Test
    fun `store loads persisted navidrome audio quality preferences`() = runTest {
        val repository = FakeSettingsRepository(
            navidromeWifiAudioQuality = NavidromeAudioQuality.Kbps320,
            navidromeMobileAudioQuality = NavidromeAudioQuality.Kbps128,
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertLocalizedEquals(NavidromeAudioQuality.Kbps320, store.state.value.navidromeWifiAudioQuality)
        assertLocalizedEquals(NavidromeAudioQuality.Kbps128, store.state.value.navidromeMobileAudioQuality)
        scope.cancel()
    }

    @Test
    fun `updating navidrome audio quality preferences writes through immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        store.dispatch(SettingsIntent.NavidromeWifiAudioQualityChanged(NavidromeAudioQuality.Kbps320))
        store.dispatch(SettingsIntent.NavidromeMobileAudioQualityChanged(NavidromeAudioQuality.Kbps128))
        advanceUntilIdle()

        assertLocalizedEquals(NavidromeAudioQuality.Kbps320, store.state.value.navidromeWifiAudioQuality)
        assertLocalizedEquals(NavidromeAudioQuality.Kbps128, store.state.value.navidromeMobileAudioQuality)
        assertLocalizedEquals(NavidromeAudioQuality.Kbps320, repository.currentNavidromeWifiAudioQuality())
        assertLocalizedEquals(NavidromeAudioQuality.Kbps128, repository.currentNavidromeMobileAudioQuality())
        scope.cancel()
    }

    @Test
    fun `forest theme defaults to black text palette`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertLocalizedEquals(AppThemeTextPalette.Black, store.state.value.textPalettePreferences.forest)
        scope.cancel()
    }

    @Test
    fun `ocean theme defaults to black text palette`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertLocalizedEquals(AppThemeTextPalette.Black, store.state.value.textPalettePreferences.ocean)
        scope.cancel()
    }

    @Test
    fun `saving direct source forces get json and clears body template`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.NameChanged("Custom Source"))
        store.dispatch(SettingsIntent.UrlChanged("https://lyrics.example/direct"))
        store.dispatch(SettingsIntent.QueryChanged("title={title}"))
        store.dispatch(SettingsIntent.HeadersChanged("Authorization: Bearer token"))
        store.dispatch(SettingsIntent.ExtractorChanged("json-map:lyrics=plainLyrics,title=trackName"))
        store.dispatch(SettingsIntent.PriorityChanged("12"))
        store.dispatch(SettingsIntent.MethodChanged(RequestMethod.POST))
        store.dispatch(SettingsIntent.BodyChanged("{\"title\":\"{title}\"}"))
        store.dispatch(SettingsIntent.ResponseFormatChanged(LyricsResponseFormat.XML))
        store.dispatch(SettingsIntent.Save)
        advanceUntilIdle()

        val saved = repository.currentSources().filterIsInstance<LyricsSourceConfig>().single()
        assertLocalizedEquals(RequestMethod.GET, saved.method)
        assertLocalizedEquals("", saved.bodyTemplate)
        assertLocalizedEquals(LyricsResponseFormat.JSON, saved.responseFormat)
        scope.cancel()
    }

    @Test
    fun `selecting theme text palette updates repository and state`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ThemeTextPaletteSelected(AppThemeId.Forest, AppThemeTextPalette.Black))
        advanceUntilIdle()

        assertLocalizedEquals(AppThemeTextPalette.Black, store.state.value.textPalettePreferences.forest)
        assertLocalizedEquals(AppThemeTextPalette.Black, repository.currentTextPalettePreferences().forest)
        scope.cancel()
    }

    @Test
    fun `switching themes keeps per theme text palette choices`() = runTest {
        val repository = FakeSettingsRepository(
            textPalettePreferences = AppThemeTextPalettePreferences(
                classic = AppThemeTextPalette.White,
                forest = AppThemeTextPalette.Black,
                ocean = AppThemeTextPalette.White,
                sand = AppThemeTextPalette.White,
                custom = AppThemeTextPalette.Black,
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ThemeSelected(AppThemeId.Forest))
        advanceUntilIdle()
        assertLocalizedEquals(
            AppThemeTextPalette.Black,
            resolveAppThemeTextPalette(store.state.value.selectedTheme, store.state.value.textPalettePreferences),
        )

        store.dispatch(SettingsIntent.ThemeSelected(AppThemeId.Custom))
        advanceUntilIdle()
        assertLocalizedEquals(
            AppThemeTextPalette.Black,
            resolveAppThemeTextPalette(store.state.value.selectedTheme, store.state.value.textPalettePreferences),
        )
        scope.cancel()
    }

    @Test
    fun `selecting preset theme updates repository and state`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ThemeSelected(AppThemeId.Forest))
        advanceUntilIdle()

        assertLocalizedEquals(AppThemeId.Forest, store.state.value.selectedTheme)
        assertLocalizedEquals(AppThemeId.Forest, repository.currentSelectedTheme())
        scope.cancel()
    }

    @Test
    fun `updating custom background color writes through immediately`() = runTest {
        val repository = FakeSettingsRepository(selectedTheme = AppThemeId.Custom)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CustomThemeColorUpdated(CustomThemeColorRole.Background, 0xFF102030.toInt()))
        advanceUntilIdle()

        val expected = defaultCustomThemeTokens().copy(backgroundArgb = 0xFF102030.toInt())
        assertLocalizedEquals(expected, repository.currentCustomThemeTokens())
        assertLocalizedEquals(expected, store.state.value.customThemeTokens)
        assertLocalizedEquals(1, repository.setCustomThemeTokensCalls)
        assertLocalizedEquals(null, store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `updating custom accent color writes through immediately`() = runTest {
        val repository = FakeSettingsRepository(selectedTheme = AppThemeId.Custom)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CustomThemeColorUpdated(CustomThemeColorRole.Accent, 0xFF405060.toInt()))
        advanceUntilIdle()

        val expected = defaultCustomThemeTokens().copy(accentArgb = 0xFF405060.toInt())
        assertLocalizedEquals(expected, repository.currentCustomThemeTokens())
        assertLocalizedEquals(expected, store.state.value.customThemeTokens)
        assertLocalizedEquals(1, repository.setCustomThemeTokensCalls)
        assertLocalizedEquals(null, store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `updating custom focus color writes through immediately`() = runTest {
        val repository = FakeSettingsRepository(selectedTheme = AppThemeId.Custom)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CustomThemeColorUpdated(CustomThemeColorRole.Focus, 0xFF708090.toInt()))
        advanceUntilIdle()

        val expected = defaultCustomThemeTokens().copy(focusArgb = 0xFF708090.toInt())
        assertLocalizedEquals(expected, repository.currentCustomThemeTokens())
        assertLocalizedEquals(expected, store.state.value.customThemeTokens)
        assertLocalizedEquals(1, repository.setCustomThemeTokensCalls)
        assertLocalizedEquals(null, store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `resetting custom theme restores classic defaults`() = runTest {
        val repository = FakeSettingsRepository(
            selectedTheme = AppThemeId.Custom,
            customThemeTokens = AppThemeTokens(
                backgroundArgb = 0xFF102030.toInt(),
                accentArgb = 0xFF405060.toInt(),
                focusArgb = 0xFF708090.toInt(),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ResetCustomTheme)
        advanceUntilIdle()

        assertLocalizedEquals(defaultCustomThemeTokens(), repository.currentCustomThemeTokens())
        assertLocalizedEquals(defaultCustomThemeTokens(), store.state.value.customThemeTokens)
        assertLocalizedEquals("自定义主题已重置。", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `picking desktop vlc path saves manual override and restart message`() = runTest {
        val repository = FakeSettingsRepository(
            desktopVlcAutoDetectedPath = "/Applications/VLC.app/Contents/MacOS/lib",
        )
        val picker = FakeVlcPathPickerPlatformService(
            nextResult = Result.success("/opt/homebrew/Cellar/vlc/lib"),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            vlcPathPickerPlatformService = picker,
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.PickDesktopVlcPath)
        advanceUntilIdle()

        assertLocalizedEquals("/opt/homebrew/Cellar/vlc/lib", repository.currentDesktopVlcManualPath())
        assertLocalizedEquals("/opt/homebrew/Cellar/vlc/lib", store.state.value.desktopVlcEffectivePath)
        assertLocalizedEquals("VLC 路径已保存，将在下次启动后生效。", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `canceling desktop vlc path picking leaves state unchanged`() = runTest {
        val repository = FakeSettingsRepository(
            desktopVlcAutoDetectedPath = "/Applications/VLC.app/Contents/MacOS/lib",
        )
        val picker = FakeVlcPathPickerPlatformService(
            nextResult = Result.success(null),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository,
            scope,
            vlcPathPickerPlatformService = picker,
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.PickDesktopVlcPath)
        advanceUntilIdle()

        assertLocalizedEquals(null, repository.currentDesktopVlcManualPath())
        assertLocalizedEquals("/Applications/VLC.app/Contents/MacOS/lib", store.state.value.desktopVlcAutoDetectedPath)
        assertLocalizedEquals("/Applications/VLC.app/Contents/MacOS/lib", store.state.value.desktopVlcEffectivePath)
        assertLocalizedEquals(null, store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `clearing desktop vlc manual path restores auto detected path`() = runTest {
        val repository = FakeSettingsRepository(
            desktopVlcAutoDetectedPath = "/Applications/VLC.app/Contents/MacOS/lib",
            desktopVlcManualPath = "/opt/homebrew/Cellar/vlc/lib",
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ClearDesktopVlcManualPath)
        advanceUntilIdle()

        assertLocalizedEquals(null, repository.currentDesktopVlcManualPath())
        assertLocalizedEquals("/Applications/VLC.app/Contents/MacOS/lib", store.state.value.desktopVlcEffectivePath)
        assertLocalizedEquals("已恢复自动识别，将在下次启动后生效。", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `theme palette derivation preserves configured tokens and contrast`() = runTest {
        val whiteTextPalette = deriveAppThemePalette(defaultCustomThemeTokens(), AppThemeTextPalette.White)
        val blackTextPalette = deriveAppThemePalette(defaultCustomThemeTokens(), AppThemeTextPalette.Black)
        val lightPalette = deriveAppThemePalette(
            AppThemeTokens(
                backgroundArgb = 0xFFF4EEE8.toInt(),
                accentArgb = 0xFF1971C2.toInt(),
                focusArgb = 0xFF3BC9DB.toInt(),
            ),
            AppThemeTextPalette.Black,
        )

        assertLocalizedEquals(defaultCustomThemeTokens().accentArgb, whiteTextPalette.primaryArgb)
        assertLocalizedEquals(defaultCustomThemeTokens().focusArgb, whiteTextPalette.secondaryArgb)
        assertLocalizedEquals(0xFFF7F5F3.toInt(), whiteTextPalette.onBackgroundArgb)
        assertLocalizedEquals(0xFF111111.toInt(), blackTextPalette.onBackgroundArgb)
        assertLocalizedEquals(0xFFD6D1CD.toInt(), whiteTextPalette.onSurfaceVariantArgb)
        assertLocalizedEquals(0xFF4A4541.toInt(), blackTextPalette.onSurfaceVariantArgb)
        assertLocalizedEquals(0xFF111111.toInt(), lightPalette.onBackgroundArgb)
        assertTrue(lightPalette.surfaceArgb != lightPalette.backgroundArgb)
    }

    @Test
    fun `selecting direct source enters direct edit state`() = runTest {
        val direct = sampleDirectSource()
        val repository = FakeSettingsRepository(listOf(direct, sampleWorkflowSource()))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.SelectConfig(direct))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("direct-1", state.editingId)
        assertLocalizedEquals("My Direct Source", state.name)
        assertLocalizedEquals("https://lyrics.example/direct", state.urlTemplate)
        assertLocalizedEquals(null, state.editingWorkflowId)
        scope.cancel()
    }

    @Test
    fun `selecting workflow source enters workflow edit state and backfills json`() = runTest {
        val workflow = sampleWorkflowSource()
        val repository = FakeSettingsRepository(listOf(sampleDirectSource(), workflow))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(workflow))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("wf-1", state.editingWorkflowId)
        assertLocalizedEquals(workflow.rawJson, state.workflowJsonInput)
        scope.cancel()
    }

    @Test
    fun `selecting workflow source rewrites enabled flag in editor json to current source state`() = runTest {
        val workflow = sampleWorkflowSource(
            rawJson = workflowJson(
                id = "wf-1",
                name = "Workflow Source",
                enabled = false,
            ),
        ).copy(enabled = true)
        val repository = FakeSettingsRepository(listOf(sampleDirectSource(), workflow))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(workflow))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("wf-1", state.editingWorkflowId)
        assertTrue(state.workflowJsonInput.contains("\"enabled\": true"))
        scope.cancel()
    }

    @Test
    fun `creating new direct source from edit saves a second source`() = runTest {
        val direct = sampleDirectSource()
        val repository = FakeSettingsRepository(listOf(direct))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.SelectConfig(direct))
        store.dispatch(SettingsIntent.NameChanged("Forked Source"))
        store.dispatch(SettingsIntent.CreateNew)
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("歌词源已新建。", state.message)
        assertLocalizedEquals("Forked Source", state.name)
        assertLocalizedEquals("https://lyrics.example/direct", state.urlTemplate)
        assertLocalizedEquals(2, repository.currentSources().size)
        assertLocalizedEquals(setOf("My Direct Source", "Forked Source"), repository.currentSources().map { it.name }.toSet())
        scope.cancel()
    }

    @Test
    fun `workflow json edits keep current workflow edit state`() = runTest {
        val workflow = sampleWorkflowSource()
        val repository = FakeSettingsRepository(listOf(workflow))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(workflow))
        store.dispatch(SettingsIntent.WorkflowJsonChanged(workflow.rawJson.replace("Workflow Source", "Workflow Source v2")))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("wf-1", state.editingWorkflowId)
        assertLocalizedEquals(true, "Workflow Source v2" in state.workflowJsonInput)
        scope.cancel()
    }

    @Test
    fun `creating new workflow from edit saves a second source with a new id`() = runTest {
        val workflow = sampleWorkflowSource()
        val repository = FakeSettingsRepository(listOf(workflow))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(workflow))
        store.dispatch(SettingsIntent.WorkflowJsonChanged(workflow.rawJson.replace("Workflow Source", "Forked Workflow")))
        store.dispatch(SettingsIntent.CreateNewWorkflow)
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("Workflow 源已新建。", state.message)
        val workflowSources = repository.currentSources().filterIsInstance<WorkflowLyricsSourceConfig>()
        assertLocalizedEquals(2, workflowSources.size)
        assertLocalizedEquals(setOf("Workflow Source", "Forked Workflow"), workflowSources.map { it.name }.toSet())
        assertLocalizedEquals(true, workflowSources.any { it.id != "wf-1" && it.name == "Forked Workflow" })
        assertLocalizedEquals(true, "Forked Workflow" in state.workflowJsonInput)
        scope.cancel()
    }

    @Test
    fun `duplicate direct save failure sets message and preserves form`() = runTest {
        val repository = FakeSettingsRepository(
            listOf(
                sampleDirectSource(name = "Taken Name"),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.NameChanged(" taken name "))
        store.dispatch(SettingsIntent.UrlChanged("https://lyrics.example/new"))
        store.dispatch(SettingsIntent.Save)
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("歌词源保存失败。\n歌词源名称已存在。", state.message)
        assertLocalizedEquals(" taken name ", state.name)
        assertLocalizedEquals("https://lyrics.example/new", state.urlTemplate)
        assertLocalizedEquals(null, state.editingId)
        scope.cancel()
    }

    @Test
    fun `clearing workflow edit resets workflow editor and keeps sources`() = runTest {
        val workflow = sampleWorkflowSource()
        val repository = FakeSettingsRepository(listOf(sampleDirectSource(), workflow))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(workflow))
        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(null))
        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(null))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(null, state.editingWorkflowId)
        assertLocalizedEquals("", state.workflowJsonInput)
        assertLocalizedEquals(2, state.sources.size)
        scope.cancel()
    }

    @Test
    fun `saving musicmatch token creates managed workflow source`() = runTest {
        val repository = FakeSettingsRepository(emptyList())
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.MusicmatchUserTokenChanged("token-123"))
        store.dispatch(SettingsIntent.SaveMusicmatch)
        advanceUntilIdle()

        val state = store.state.value
        val managed = repository.currentSources()
            .filterIsInstance<WorkflowLyricsSourceConfig>()
            .single()

        assertLocalizedEquals("Musicmatch 已保存。", state.message)
        assertLocalizedEquals("token-123", state.musicmatchUserToken)
        assertLocalizedEquals(true, state.hasMusicmatchSource)
        assertLocalizedEquals(MANAGED_MUSICMATCH_SOURCE_ID, managed.id)
        scope.cancel()
    }

    @Test
    fun `saving lrcapi address creates managed direct source`() = runTest {
        val repository = FakeSettingsRepository(emptyList())
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LrcApiUrlChanged("https://lyrics.example/jsonapi"))
        store.dispatch(SettingsIntent.SaveLrcApi)
        advanceUntilIdle()

        val state = store.state.value
        val managed = repository.currentSources()
            .filterIsInstance<LyricsSourceConfig>()
            .single()

        assertLocalizedEquals("LrcAPI 已保存。", state.message)
        assertLocalizedEquals("https://lyrics.example/jsonapi", state.lrcApiUrl)
        assertLocalizedEquals(true, state.hasLrcApiSource)
        assertLocalizedEquals(MANAGED_LRCAPI_SOURCE_ID, managed.id)
        assertLocalizedEquals("LrcAPI", managed.name)
        assertLocalizedEquals(110, managed.priority)
        assertLocalizedEquals("title={title}&artist={artist}", managed.queryTemplate)
        assertLocalizedEquals("json-map:lyrics=lyrics|lrc,title=title,artist=artist,album=album,durationSeconds=duration,id=id,coverUrl=cover", managed.extractor)
        assertLocalizedEquals(true, managed.enabled)
        scope.cancel()
    }

    @Test
    fun `clearing lrcapi restores default managed direct source`() = runTest {
        val repository = FakeSettingsRepository(listOf(sampleLrcApiSource(urlTemplate = "https://lyrics.example/jsonapi")))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ClearLrcApi)
        advanceUntilIdle()

        val state = store.state.value
        val managed = repository.currentSources()
            .filterIsInstance<LyricsSourceConfig>()
            .single()
        assertLocalizedEquals(DEFAULT_LRCAPI_URL, state.lrcApiUrl)
        assertLocalizedEquals(true, state.hasLrcApiSource)
        assertLocalizedEquals("LrcAPI 已恢复默认。", state.message)
        assertLocalizedEquals(MANAGED_LRCAPI_SOURCE_ID, managed.id)
        assertLocalizedEquals(DEFAULT_LRCAPI_URL, managed.urlTemplate)
        assertLocalizedEquals(true, managed.enabled)
        scope.cancel()
    }

    @Test
    fun `selecting managed lrcapi keeps direct editor closed and backfills dedicated card`() = runTest {
        val lrcApi = sampleLrcApiSource(urlTemplate = "https://lyrics.example/jsonapi")
        val repository = FakeSettingsRepository(listOf(lrcApi))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.SelectConfig(lrcApi))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("https://lyrics.example/jsonapi", state.lrcApiUrl)
        assertLocalizedEquals(true, state.hasLrcApiSource)
        assertLocalizedEquals(null, state.editingId)
        assertLocalizedEquals("", state.workflowJsonInput)
        scope.cancel()
    }

    @Test
    fun `clearing musicmatch removes managed workflow source`() = runTest {
        val repository = FakeSettingsRepository(listOf(sampleMusicmatchSource()))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ClearMusicmatch)
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("", state.musicmatchUserToken)
        assertLocalizedEquals(false, state.hasMusicmatchSource)
        assertLocalizedEquals(emptyList(), repository.currentSources())
        scope.cancel()
    }

    @Test
    fun `viewing managed musicmatch workflow keeps raw workflow editor closed`() = runTest {
        val musicmatch = sampleMusicmatchSource()
        val repository = FakeSettingsRepository(listOf(musicmatch))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(musicmatch))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("token-123", state.musicmatchUserToken)
        assertLocalizedEquals(true, state.hasMusicmatchSource)
        assertLocalizedEquals(null, state.editingWorkflowId)
        assertLocalizedEquals("", state.workflowJsonInput)
        scope.cancel()
    }

    @Test
    fun `deleting managed lrcapi source resets dedicated card state`() = runTest {
        val repository = FakeSettingsRepository(listOf(sampleLrcApiSource(urlTemplate = "https://lyrics.example/jsonapi")))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.DeleteSource(MANAGED_LRCAPI_SOURCE_ID))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("", state.lrcApiUrl)
        assertLocalizedEquals(false, state.hasLrcApiSource)
        assertLocalizedEquals("歌词源已删除。", state.message)
        scope.cancel()
    }

    @Test
    fun `load storage usage writes snapshot into state`() = runTest {
        val repository = FakeSettingsRepository()
        val storageGateway = FakeAppStorageGateway(
            initialSnapshot = sampleStorageSnapshot(
                artwork = 1_024L,
                playback = 2_048L,
                lyricsShare = 256L,
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope, appStorageGateway = storageGateway)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadStorageUsage())
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(3_328L, state.storageSnapshot?.totalSizeBytes)
        assertLocalizedEquals(true, state.storageLoaded)
        assertLocalizedEquals(false, state.storageLoading)
        assertLocalizedEquals(1, storageGateway.loadCalls)
        scope.cancel()
    }

    @Test
    fun `pick data location exposes selected LynMusic directory`() = runTest {
        val service = FakeAppDataLocationPlatformService(
            currentDataRootPath = "C:\\Users\\tester\\.lynmusic",
            nextPickedPath = Result.success("D:\\MusicData\\LynMusic"),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = FakeSettingsRepository(),
            scope = scope,
            appDataLocationPlatformService = service,
        )

        store.dispatch(SettingsIntent.PickDataLocation)
        advanceUntilIdle()

        assertLocalizedEquals("C:\\Users\\tester\\.lynmusic", store.state.value.currentDataRootPath)
        assertLocalizedEquals("D:\\MusicData\\LynMusic", store.state.value.pendingDataRootPath)
        assertFalse(store.state.value.dataLocationBusy)
        scope.cancel()
    }

    @Test
    fun `duplicate pick data location intents start only one picker`() = runTest {
        val pickerCompletion = CompletableDeferred<Unit>()
        val service = FakeAppDataLocationPlatformService(
            nextPickedPath = Result.success("D:\\MusicData\\LynMusic"),
        ).apply {
            this.pickerCompletion = pickerCompletion
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = FakeSettingsRepository(),
            scope = scope,
            appDataLocationPlatformService = service,
        )

        store.dispatch(SettingsIntent.PickDataLocation)
        assertTrue(store.state.value.dataLocationBusy)
        store.dispatch(SettingsIntent.PickDataLocation)
        runCurrent()

        assertLocalizedEquals(1, service.pickCalls)
        assertTrue(store.state.value.dataLocationBusy)

        pickerCompletion.complete(Unit)
        advanceUntilIdle()
        assertLocalizedEquals("D:\\MusicData\\LynMusic", store.state.value.pendingDataRootPath)
        assertFalse(store.state.value.dataLocationBusy)
        scope.cancel()
    }

    @Test
    fun `migrate data location is scheduled and requests restart`() = runTest {
        val service = FakeAppDataLocationPlatformService(
            nextPickedPath = Result.success("D:\\LynMusic"),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = FakeSettingsRepository(),
            scope = scope,
            appDataLocationPlatformService = service,
        )

        store.dispatch(SettingsIntent.PickDataLocation)
        advanceUntilIdle()
        store.dispatch(SettingsIntent.RequestDataLocationChange(AppDataLocationChangeMode.Migrate))
        advanceUntilIdle()

        assertLocalizedEquals(listOf("D:\\LynMusic" to AppDataLocationChangeMode.Migrate), service.scheduledChanges)
        assertTrue(store.state.value.dataLocationRestartRequired)
        assertLocalizedEquals(null, store.state.value.pendingDataRootPath)
        scope.cancel()
    }

    @Test
    fun `duplicate schedule and cancel intents cannot race an accepted migration`() = runTest {
        val scheduleCompletion = CompletableDeferred<Unit>()
        val service = FakeAppDataLocationPlatformService(
            nextPickedPath = Result.success("D:\\LynMusic"),
        ).apply {
            this.scheduleCompletion = scheduleCompletion
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = FakeSettingsRepository(),
            scope = scope,
            appDataLocationPlatformService = service,
        )

        store.dispatch(SettingsIntent.PickDataLocation)
        advanceUntilIdle()
        store.dispatch(SettingsIntent.RequestDataLocationChange(AppDataLocationChangeMode.Migrate))
        assertTrue(store.state.value.dataLocationBusy)
        store.dispatch(SettingsIntent.RequestDataLocationChange(AppDataLocationChangeMode.Migrate))
        store.dispatch(SettingsIntent.CancelDataLocationSelection)
        runCurrent()

        assertLocalizedEquals(listOf("D:\\LynMusic" to AppDataLocationChangeMode.Migrate), service.scheduledChanges)
        assertLocalizedEquals("D:\\LynMusic", store.state.value.pendingDataRootPath)
        assertTrue(store.state.value.dataLocationBusy)

        scheduleCompletion.complete(Unit)
        advanceUntilIdle()
        assertTrue(store.state.value.dataLocationRestartRequired)
        assertLocalizedEquals(null, store.state.value.pendingDataRootPath)
        assertLocalizedEquals(null, store.state.value.message)
        assertFalse(store.state.value.dataLocationBusy)
        scope.cancel()
    }

    @Test
    fun `cancelling store scope releases a claimed data location operation`() = runTest {
        val service = FakeAppDataLocationPlatformService(
            nextPickedPath = Result.success("D:\\LynMusic"),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = FakeSettingsRepository(),
            scope = scope,
            appDataLocationPlatformService = service,
        )

        store.dispatch(SettingsIntent.PickDataLocation)
        assertTrue(store.state.value.dataLocationBusy)
        scope.cancel()
        runCurrent()

        assertFalse(store.state.value.dataLocationBusy)
        assertLocalizedEquals(0, service.pickCalls)
    }

    @Test
    fun `discard data location requires confirmation before scheduling and exit effect`() = runTest {
        val service = FakeAppDataLocationPlatformService(
            nextPickedPath = Result.success("E:\\AppData\\LynMusic"),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = FakeSettingsRepository(),
            scope = scope,
            appDataLocationPlatformService = service,
        )
        val effects = mutableListOf<SettingsEffect>()
        val effectJob = launch { store.effects.collect { effects += it } }

        store.dispatch(SettingsIntent.PickDataLocation)
        advanceUntilIdle()
        store.dispatch(SettingsIntent.RequestDataLocationChange(AppDataLocationChangeMode.Discard))
        advanceUntilIdle()

        assertTrue(store.state.value.dataLocationDiscardConfirmationRequired)
        assertTrue(service.scheduledChanges.isEmpty())

        store.dispatch(SettingsIntent.ConfirmDiscardDataLocation)
        advanceUntilIdle()
        store.dispatch(SettingsIntent.ConfirmDataLocationRestart)
        advanceUntilIdle()

        assertLocalizedEquals(listOf("E:\\AppData\\LynMusic" to AppDataLocationChangeMode.Discard), service.scheduledChanges)
        assertLocalizedEquals(listOf<SettingsEffect>(SettingsEffect.ExitApplicationRequested), effects)
        effectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `pending cleanup disables location picker and retry clears residual path`() = runTest {
        val service = FakeAppDataLocationPlatformService(
            pendingCleanupRootPath = "C:\\Users\\tester\\.lynmusic.discarding",
            nextPickedPath = Result.success("D:\\LynMusic"),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = FakeSettingsRepository(),
            scope = scope,
            appDataLocationPlatformService = service,
        )

        store.dispatch(SettingsIntent.PickDataLocation)
        advanceUntilIdle()
        assertLocalizedEquals(null, store.state.value.pendingDataRootPath)

        store.dispatch(SettingsIntent.RetryDataLocationCleanup)
        advanceUntilIdle()
        assertLocalizedEquals(null, store.state.value.pendingDataCleanupRootPath)
        assertLocalizedEquals("旧数据目录已清理。", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `load device info snapshot writes snapshot into state`() = runTest {
        val repository = FakeSettingsRepository()
        val deviceInfoGateway = FakeDeviceInfoGateway(
            initialSnapshot = sampleDeviceInfoSnapshot(
                systemName = "Android",
                systemVersion = "14 (SDK 34)",
                resolution = "1080 × 2400 px",
                cpuDescription = "Snapdragon 8 Gen 2 · arm64-v8a · 8 核",
                totalMemoryBytes = 8L * 1024 * 1024 * 1024,
                deviceModel = "Google Pixel 8",
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope, deviceInfoGateway = deviceInfoGateway)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadDeviceInfo())
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("Google Pixel 8", state.deviceInfoSnapshot?.deviceModel)
        assertLocalizedEquals("Android", state.deviceInfoSnapshot?.systemName)
        assertLocalizedEquals(true, state.deviceInfoLoaded)
        assertLocalizedEquals(false, state.deviceInfoLoading)
        assertLocalizedEquals(1, deviceInfoGateway.loadCalls)
        scope.cancel()
    }

    @Test
    fun cachedDeviceInfoSurvivesLanguageChangesWithoutReloading() = runTest {
        val repository = FakeSettingsRepository()
        val snapshot = DeviceInfoSnapshot("Android", "14", cpuDescription = "arm64", logicalCoreCount = 2)
        val gateway = FakeDeviceInfoGateway(initialSnapshot = snapshot)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = SettingsStore(repository, scope, deviceInfoGateway = gateway)
            store.dispatch(SettingsIntent.LoadDeviceInfo())
            advanceUntilIdle()
            val before = store.state.value
            store.dispatch(SettingsIntent.AppLanguageChanged(top.iwesley.lyn.music.core.model.AppLanguage.English))
            advanceUntilIdle()
            store.dispatch(SettingsIntent.LoadDeviceInfo())
            advanceUntilIdle()
            kotlin.test.assertEquals(before.copy(appLanguage = top.iwesley.lyn.music.core.model.AppLanguage.English), store.state.value)
            kotlin.test.assertSame(snapshot, store.state.value.deviceInfoSnapshot)
            kotlin.test.assertEquals(1, gateway.loadCalls)
            kotlin.test.assertEquals("arm64 · 2 cores", top.iwesley.lyn.music.core.model.resolveUiText(
                checkNotNull(snapshot.cpuDescriptionText), top.iwesley.lyn.music.core.model.AppLanguage.English,
            ))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `load device info failure preserves existing snapshot`() = runTest {
        val repository = FakeSettingsRepository()
        val deviceInfoGateway = FakeDeviceInfoGateway(
            initialSnapshot = sampleDeviceInfoSnapshot(
                systemName = "macOS",
                systemVersion = "15.3.1",
                resolution = "3024 × 1964 px",
                cpuDescription = "arm64 · 8 核",
                totalMemoryBytes = 16L * 1024 * 1024 * 1024,
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope, deviceInfoGateway = deviceInfoGateway)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadDeviceInfo())
        advanceUntilIdle()
        deviceInfoGateway.nextLoadFailure = IllegalStateException("读取设备信息失败。")

        store.dispatch(SettingsIntent.LoadDeviceInfo(force = true))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals("macOS", state.deviceInfoSnapshot?.systemName)
        assertLocalizedEquals("读取设备信息失败。\n读取设备信息失败。", state.message)
        assertLocalizedEquals(true, state.deviceInfoLoaded)
        scope.cancel()
    }

    @Test
    fun `loaded device info does not reload until forced`() = runTest {
        val repository = FakeSettingsRepository()
        val deviceInfoGateway = FakeDeviceInfoGateway(
            initialSnapshot = sampleDeviceInfoSnapshot(
                systemName = "iOS",
                systemVersion = "18.0",
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope, deviceInfoGateway = deviceInfoGateway)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadDeviceInfo())
        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadDeviceInfo())
        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadDeviceInfo(force = true))
        advanceUntilIdle()

        assertLocalizedEquals(2, deviceInfoGateway.loadCalls)
        scope.cancel()
    }

    @Test
    fun `load storage usage failure preserves existing snapshot`() = runTest {
        val repository = FakeSettingsRepository()
        val storageGateway = FakeAppStorageGateway(
            initialSnapshot = sampleStorageSnapshot(artwork = 2_048L),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope, appStorageGateway = storageGateway)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadStorageUsage())
        advanceUntilIdle()
        storageGateway.nextLoadFailure = IllegalStateException("缓存统计失败。")

        store.dispatch(SettingsIntent.LoadStorageUsage(force = true))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(2_048L, state.storageSnapshot?.totalSizeBytes)
        assertLocalizedEquals("缓存统计失败。\n缓存统计失败。", state.message)
        assertLocalizedEquals(true, state.storageLoaded)
        scope.cancel()
    }

    @Test
    fun `clearing storage category refreshes snapshot`() = runTest {
        val repository = FakeSettingsRepository()
        val storageGateway = FakeAppStorageGateway(
            initialSnapshot = sampleStorageSnapshot(
                artwork = 2_048L,
                playback = 4_096L,
                lyricsShare = 128L,
                tagEdit = 64L,
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope, appStorageGateway = storageGateway)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadStorageUsage())
        advanceUntilIdle()
        store.dispatch(SettingsIntent.ClearStorageCategory(AppStorageCategory.Artwork))
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(4_288L, state.storageSnapshot?.totalSizeBytes)
        assertLocalizedEquals(0L, state.storageSnapshot?.categories?.first { it.category == AppStorageCategory.Artwork }?.sizeBytes)
        assertLocalizedEquals("封面缓存已清除。", state.message)
        assertLocalizedEquals(null, state.clearingStorageCategory)
        scope.cancel()
    }

    @Test
    fun `loaded storage usage does not reload until forced`() = runTest {
        val repository = FakeSettingsRepository()
        val storageGateway = FakeAppStorageGateway(
            initialSnapshot = sampleStorageSnapshot(playback = 2_048L),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope, appStorageGateway = storageGateway)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadStorageUsage())
        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadStorageUsage())
        advanceUntilIdle()
        store.dispatch(SettingsIntent.LoadStorageUsage(force = true))
        advanceUntilIdle()

        assertLocalizedEquals(2, storageGateway.loadCalls)
        scope.cancel()
    }

    @Test
    fun `checking app update marks newer release`() = runTest {
        val repository = FakeSettingsRepository()
        val appUpdateRepository = FakeAppUpdateRepository(
            Result.success(sampleAppRelease(tagName = "v1.0.8.1")),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = repository,
            scope = scope,
            appUpdateRepository = appUpdateRepository,
            currentAppVersionName = "1.0.8",
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CheckAppUpdate)
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(1, appUpdateRepository.calls)
        assertFalse(state.appUpdateChecking)
        assertLocalizedEquals("v1.0.8.1", state.appUpdateLatestRelease?.tagName)
        assertLocalizedEquals(true, state.appUpdateHasNewVersion)
        assertLocalizedEquals(null, state.appUpdateError)
        assertLocalizedEquals("发现新版本 v1.0.8.1。", state.message)
        scope.cancel()
    }

    @Test
    fun `checking app update marks same release as latest`() = runTest {
        val repository = FakeSettingsRepository()
        val appUpdateRepository = FakeAppUpdateRepository(
            Result.success(sampleAppRelease(tagName = "v1.0.8")),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = repository,
            scope = scope,
            appUpdateRepository = appUpdateRepository,
            currentAppVersionName = "1.0.8",
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CheckAppUpdate)
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.appUpdateChecking)
        assertLocalizedEquals("v1.0.8", state.appUpdateLatestRelease?.tagName)
        assertLocalizedEquals(false, state.appUpdateHasNewVersion)
        assertLocalizedEquals(null, state.appUpdateError)
        assertLocalizedEquals("当前已是最新版本。", state.message)
        scope.cancel()
    }

    @Test
    fun `app release comparison treats prerelease lower than stable release`() = runTest {
        assertFalse(isAppReleaseNewer(currentVersionName = "2.0.0", releaseTagName = "v2.0.0-rc1"))
        assertTrue(isAppReleaseNewer(currentVersionName = "2.0.0-rc1", releaseTagName = "v2.0.0"))
        assertTrue(isAppReleaseNewer(currentVersionName = "1.0.8", releaseTagName = "v1.0.8.1"))
        assertFalse(isAppReleaseNewer(currentVersionName = "1.0.8", releaseTagName = "v1.0.8"))
    }

    @Test
    fun `app update ui model maps checking error update latest and idle states`() = runTest {
        val checking = SettingsState(appUpdateChecking = true).toAppUpdateUiModel()
        assertLocalizedEquals(AppUpdateUiStatus.Checking, checking.status)
        assertLocalizedEquals("正在检查最新版本...", checking.message)

        val error = SettingsState(appUpdateError = top.iwesley.lyn.music.core.model.UiText.Raw("network down")).toAppUpdateUiModel()
        assertLocalizedEquals(AppUpdateUiStatus.Error, error.status)
        assertLocalizedEquals("network down", error.message)

        val update = SettingsState(
            appUpdateLatestRelease = sampleAppRelease(tagName = "v1.0.8.1"),
            appUpdateHasNewVersion = true,
        ).toAppUpdateUiModel()
        assertLocalizedEquals(AppUpdateUiStatus.UpdateAvailable, update.status)
        assertLocalizedEquals("v1.0.8.1", update.latestVersion)
        assertLocalizedEquals("发现可用更新，可以到公众号获取云盘链接或者 GitHub 下载。", update.message)
        assertLocalizedEquals("https://github.com/wesley666/LynMusic/releases/tag/v1.0.8.1", update.downloadUrl)

        val updateWithFallbackUrl = SettingsState(
            appUpdateLatestRelease = sampleAppRelease(tagName = "v1.0.8.1").copy(htmlUrl = ""),
            appUpdateHasNewVersion = true,
        ).toAppUpdateUiModel()
        assertLocalizedEquals(LynMusicUpdateLinks.RELEASES_URL, updateWithFallbackUrl.downloadUrl)

        val updateWithError = SettingsState(
            appUpdateLatestRelease = sampleAppRelease(tagName = "v1.0.8.1"),
            appUpdateHasNewVersion = true,
            appUpdateError = top.iwesley.lyn.music.core.model.UiText.Raw("network down"),
        ).toAppUpdateUiModel()
        assertLocalizedEquals(AppUpdateUiStatus.UpdateAvailable, updateWithError.status)
        assertLocalizedEquals("v1.0.8.1", updateWithError.latestVersion)
        assertLocalizedEquals("network down", updateWithError.errorMessage)

        val latest = SettingsState(
            appUpdateLatestRelease = sampleAppRelease(tagName = "v1.0.8"),
            appUpdateHasNewVersion = false,
        ).toAppUpdateUiModel()
        assertLocalizedEquals(AppUpdateUiStatus.UpToDate, latest.status)
        assertLocalizedEquals("当前已是最新版本。", latest.message)

        val idle = SettingsState().toAppUpdateUiModel()
        assertLocalizedEquals(AppUpdateUiStatus.Idle, idle.status)
        assertLocalizedEquals(null, idle.message)
    }

    @Test
    fun `checking app update failure restores loading state and exposes error`() = runTest {
        val repository = FakeSettingsRepository()
        val appUpdateRepository = FakeAppUpdateRepository(
            Result.failure(IllegalStateException("network down")),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = repository,
            scope = scope,
            appUpdateRepository = appUpdateRepository,
            currentAppVersionName = "1.0.8",
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CheckAppUpdate)
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.appUpdateChecking)
        assertLocalizedEquals(null, state.appUpdateLatestRelease)
        assertLocalizedEquals(null, state.appUpdateHasNewVersion)
        assertLocalizedEquals("检查更新失败。\nnetwork down", state.appUpdateError)
        assertLocalizedEquals("检查更新失败。\nnetwork down", state.message)
        scope.cancel()
    }

    @Test
    fun `concurrent app update checks only request latest release once`() = runTest {
        val repository = FakeSettingsRepository()
        val appUpdateRepository = SuspendedAppUpdateRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = repository,
            scope = scope,
            appUpdateRepository = appUpdateRepository,
            currentAppVersionName = "1.0.8",
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CheckAppUpdate)
        store.dispatch(SettingsIntent.CheckAppUpdate)
        advanceUntilIdle()

        assertLocalizedEquals(1, appUpdateRepository.calls)

        appUpdateRepository.complete(Result.success(sampleAppRelease(tagName = "v1.0.8.1")))
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.appUpdateChecking)
        assertLocalizedEquals("v1.0.8.1", state.appUpdateLatestRelease?.tagName)
        assertLocalizedEquals(true, state.appUpdateHasNewVersion)
        scope.cancel()
    }

    @Test
    fun `silent app update check marks newer release without message`() = runTest {
        val repository = FakeSettingsRepository()
        val appUpdateRepository = FakeAppUpdateRepository(
            Result.success(sampleAppRelease(tagName = "v1.0.8.1")),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = repository,
            scope = scope,
            appUpdateRepository = appUpdateRepository,
            currentAppVersionName = "1.0.8",
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CheckAppUpdateSilently)
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(1, appUpdateRepository.calls)
        assertFalse(state.appUpdateChecking)
        assertLocalizedEquals("v1.0.8.1", state.appUpdateLatestRelease?.tagName)
        assertLocalizedEquals(true, state.appUpdateHasNewVersion)
        assertLocalizedEquals(null, state.appUpdateError)
        assertLocalizedEquals(null, state.message)
        scope.cancel()
    }

    @Test
    fun `silent app update check failure does not expose message`() = runTest {
        val repository = FakeSettingsRepository()
        val appUpdateRepository = FakeAppUpdateRepository(
            Result.failure(IllegalStateException("network down")),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = repository,
            scope = scope,
            appUpdateRepository = appUpdateRepository,
            currentAppVersionName = "1.0.8",
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CheckAppUpdateSilently)
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(1, appUpdateRepository.calls)
        assertFalse(state.appUpdateChecking)
        assertLocalizedEquals(null, state.appUpdateLatestRelease)
        assertLocalizedEquals(null, state.appUpdateHasNewVersion)
        assertLocalizedEquals(null, state.appUpdateError)
        assertLocalizedEquals(null, state.message)
        scope.cancel()
    }

    @Test
    fun `silent app update check only runs once and manual check can run again`() = runTest {
        val repository = FakeSettingsRepository()
        val appUpdateRepository = FakeAppUpdateRepository(
            Result.success(sampleAppRelease(tagName = "v1.0.8.1")),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = repository,
            scope = scope,
            appUpdateRepository = appUpdateRepository,
            currentAppVersionName = "1.0.8",
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CheckAppUpdateSilently)
        advanceUntilIdle()
        store.dispatch(SettingsIntent.CheckAppUpdateSilently)
        advanceUntilIdle()

        assertLocalizedEquals(1, appUpdateRepository.calls)

        appUpdateRepository.nextResult = Result.success(sampleAppRelease(tagName = "v1.0.9"))
        store.dispatch(SettingsIntent.CheckAppUpdate)
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(2, appUpdateRepository.calls)
        assertFalse(state.appUpdateChecking)
        assertLocalizedEquals("v1.0.9", state.appUpdateLatestRelease?.tagName)
        assertLocalizedEquals(true, state.appUpdateHasNewVersion)
        assertLocalizedEquals("发现新版本 v1.0.9。", state.message)
        scope.cancel()
    }

    @Test
    fun `manual app update failure preserves newer release found silently`() = runTest {
        val repository = FakeSettingsRepository()
        val appUpdateRepository = FakeAppUpdateRepository(
            Result.success(sampleAppRelease(tagName = "v1.0.8.1")),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            repository = repository,
            scope = scope,
            appUpdateRepository = appUpdateRepository,
            currentAppVersionName = "1.0.8",
        )

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CheckAppUpdateSilently)
        advanceUntilIdle()

        appUpdateRepository.nextResult = Result.failure(IllegalStateException("network down"))
        store.dispatch(SettingsIntent.CheckAppUpdate)
        advanceUntilIdle()

        val state = store.state.value
        assertLocalizedEquals(2, appUpdateRepository.calls)
        assertFalse(state.appUpdateChecking)
        assertLocalizedEquals("v1.0.8.1", state.appUpdateLatestRelease?.tagName)
        assertLocalizedEquals(true, state.appUpdateHasNewVersion)
        assertLocalizedEquals("检查更新失败。\nnetwork down", state.appUpdateError)
        assertLocalizedEquals("检查更新失败。\nnetwork down", state.message)
        scope.cancel()
    }
}

private class FakeSettingsRepository(
    sources: List<LyricsSourceDefinition> = emptyList(),
    showCompactPlayerLyrics: Boolean = false,
    showDesktopLyrics: Boolean = false,
    showMenuBarLyricsControls: Boolean = false,
    autoPlayOnStartup: Boolean = false,
    autoOpenPlayerOnStartup: Boolean = false,
    minimizeWindowOnClose: Boolean = DEFAULT_MINIMIZE_WINDOW_ON_CLOSE,
    useAndroidExtensionDecoder: Boolean = false,
    playerArtworkStyle: PlayerArtworkStyle = PlayerArtworkStyle.VINYL,
    appDisplayScalePreset: AppDisplayScalePreset = AppDisplayScalePreset.Default,
    navidromeWifiAudioQuality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
    navidromeMobileAudioQuality: NavidromeAudioQuality = NavidromeAudioQuality.Kbps192,
    selectedTheme: AppThemeId = AppThemeId.Ocean,
    customThemeTokens: AppThemeTokens = defaultCustomThemeTokens(),
    textPalettePreferences: AppThemeTextPalettePreferences = defaultThemeTextPalettePreferences(),
    desktopVlcAutoDetectedPath: String? = null,
    desktopVlcManualPath: String? = null,
    private val onSetMinimizeWindowOnClose: suspend (Boolean) -> Unit = {},
) : SettingsRepository {
    override val appLanguage = MutableStateFlow(top.iwesley.lyn.music.core.model.AppLanguage.System)
    override suspend fun setAppLanguage(language: top.iwesley.lyn.music.core.model.AppLanguage) {
        appLanguage.value = language
    }
    private val mutableSources = MutableStateFlow(sources)
    private val mutableUseSambaCache = MutableStateFlow(false)
    private val mutableShowCompactPlayerLyrics = MutableStateFlow(showCompactPlayerLyrics)
    private val mutableShowDesktopLyrics = MutableStateFlow(showDesktopLyrics)
    private val mutableShowMenuBarLyricsControls = MutableStateFlow(showMenuBarLyricsControls)
    private val mutableAutoPlayOnStartup = MutableStateFlow(autoPlayOnStartup)
    private val mutableAutoOpenPlayerOnStartup = MutableStateFlow(autoOpenPlayerOnStartup)
    private val mutableMinimizeWindowOnClose = MutableStateFlow(minimizeWindowOnClose)
    private val mutableUseAndroidExtensionDecoder = MutableStateFlow(useAndroidExtensionDecoder)
    private val mutablePlayerArtworkStyle = MutableStateFlow(playerArtworkStyle)
    private val mutableAppDisplayScalePreset = MutableStateFlow(appDisplayScalePreset)
    private val mutableNavidromeWifiAudioQuality = MutableStateFlow(navidromeWifiAudioQuality)
    private val mutableNavidromeMobileAudioQuality = MutableStateFlow(navidromeMobileAudioQuality)
    private val mutableSelectedTheme = MutableStateFlow(selectedTheme)
    private val mutableCustomThemeTokens = MutableStateFlow(customThemeTokens)
    private val mutableTextPalettePreferences = MutableStateFlow(textPalettePreferences)
    private val mutableDesktopVlcAutoDetectedPath = MutableStateFlow(desktopVlcAutoDetectedPath)
    private val mutableDesktopVlcManualPath = MutableStateFlow(desktopVlcManualPath)
    private val mutableDesktopVlcEffectivePath = MutableStateFlow(
        desktopVlcManualPath ?: desktopVlcAutoDetectedPath,
    )

    override val lyricsSources: Flow<List<LyricsSourceDefinition>> = mutableSources.asStateFlow()
    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val showCompactPlayerLyrics: StateFlow<Boolean> = mutableShowCompactPlayerLyrics.asStateFlow()
    override val showDesktopLyrics: StateFlow<Boolean> = mutableShowDesktopLyrics.asStateFlow()
    override val showMenuBarLyricsControls: StateFlow<Boolean> =
        mutableShowMenuBarLyricsControls.asStateFlow()
    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup.asStateFlow()
    override val autoOpenPlayerOnStartup: StateFlow<Boolean> =
        mutableAutoOpenPlayerOnStartup.asStateFlow()
    override val minimizeWindowOnClose: StateFlow<Boolean> = mutableMinimizeWindowOnClose.asStateFlow()
    override val useAndroidExtensionDecoder: StateFlow<Boolean> =
        mutableUseAndroidExtensionDecoder.asStateFlow()
    override val playerArtworkStyle: StateFlow<PlayerArtworkStyle> = mutablePlayerArtworkStyle.asStateFlow()
    override val appDisplayScalePreset: StateFlow<AppDisplayScalePreset> = mutableAppDisplayScalePreset.asStateFlow()
    override val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality> =
        mutableNavidromeWifiAudioQuality.asStateFlow()
    override val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality> =
        mutableNavidromeMobileAudioQuality.asStateFlow()
    override val selectedTheme: StateFlow<AppThemeId> = mutableSelectedTheme.asStateFlow()
    override val customThemeTokens: StateFlow<AppThemeTokens> = mutableCustomThemeTokens.asStateFlow()
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = mutableTextPalettePreferences.asStateFlow()
    override val desktopVlcAutoDetectedPath: StateFlow<String?> = mutableDesktopVlcAutoDetectedPath.asStateFlow()
    override val desktopVlcManualPath: StateFlow<String?> = mutableDesktopVlcManualPath.asStateFlow()
    override val desktopVlcEffectivePath: StateFlow<String?> = mutableDesktopVlcEffectivePath.asStateFlow()

    var setCustomThemeTokensCalls: Int = 0
        private set

    fun currentSources(): List<LyricsSourceDefinition> = mutableSources.value
    fun currentShowCompactPlayerLyrics(): Boolean = mutableShowCompactPlayerLyrics.value
    fun currentShowDesktopLyrics(): Boolean = mutableShowDesktopLyrics.value
    fun currentShowMenuBarLyricsControls(): Boolean = mutableShowMenuBarLyricsControls.value
    fun currentAutoPlayOnStartup(): Boolean = mutableAutoPlayOnStartup.value
    fun currentAutoOpenPlayerOnStartup(): Boolean = mutableAutoOpenPlayerOnStartup.value
    fun currentMinimizeWindowOnClose(): Boolean = mutableMinimizeWindowOnClose.value
    fun currentUseAndroidExtensionDecoder(): Boolean = mutableUseAndroidExtensionDecoder.value
    fun currentPlayerArtworkStyle(): PlayerArtworkStyle = mutablePlayerArtworkStyle.value
    fun currentAppDisplayScalePreset(): AppDisplayScalePreset = mutableAppDisplayScalePreset.value
    fun currentNavidromeWifiAudioQuality(): NavidromeAudioQuality = mutableNavidromeWifiAudioQuality.value
    fun currentNavidromeMobileAudioQuality(): NavidromeAudioQuality = mutableNavidromeMobileAudioQuality.value
    fun currentSelectedTheme(): AppThemeId = mutableSelectedTheme.value
    fun currentCustomThemeTokens(): AppThemeTokens = mutableCustomThemeTokens.value
    fun currentTextPalettePreferences(): AppThemeTextPalettePreferences = mutableTextPalettePreferences.value
    fun currentDesktopVlcManualPath(): String? = mutableDesktopVlcManualPath.value

    override suspend fun ensureDefaults() = Unit

    override suspend fun setUseSambaCache(enabled: Boolean) {
        mutableUseSambaCache.value = enabled
    }

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        mutableShowCompactPlayerLyrics.value = enabled
    }

    override suspend fun setShowDesktopLyrics(enabled: Boolean) {
        mutableShowDesktopLyrics.value = enabled
    }

    override suspend fun setShowMenuBarLyricsControls(enabled: Boolean) {
        mutableShowMenuBarLyricsControls.value = enabled
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        mutableAutoPlayOnStartup.value = enabled
    }

    override suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean) {
        mutableAutoOpenPlayerOnStartup.value = enabled
    }

    override suspend fun setMinimizeWindowOnClose(enabled: Boolean) {
        onSetMinimizeWindowOnClose(enabled)
        mutableMinimizeWindowOnClose.value = enabled
    }

    override suspend fun setUseAndroidExtensionDecoder(enabled: Boolean) {
        mutableUseAndroidExtensionDecoder.value = enabled
    }

    override suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle) {
        mutablePlayerArtworkStyle.value = style
    }

    override suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset) {
        mutableAppDisplayScalePreset.value = preset
    }

    override suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality) {
        mutableNavidromeWifiAudioQuality.value = quality
    }

    override suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality) {
        mutableNavidromeMobileAudioQuality.value = quality
    }

    override suspend fun setSelectedTheme(themeId: AppThemeId) {
        mutableSelectedTheme.value = themeId
    }

    override suspend fun setCustomThemeTokens(tokens: AppThemeTokens) {
        setCustomThemeTokensCalls += 1
        mutableCustomThemeTokens.value = tokens
    }

    override suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette) {
        mutableTextPalettePreferences.value = mutableTextPalettePreferences.value.withThemePalette(themeId, palette)
    }

    override suspend fun setDesktopVlcManualPath(path: String) {
        mutableDesktopVlcManualPath.value = path
        mutableDesktopVlcEffectivePath.value = path
    }

    override suspend fun clearDesktopVlcManualPath() {
        mutableDesktopVlcManualPath.value = null
        mutableDesktopVlcEffectivePath.value = mutableDesktopVlcAutoDetectedPath.value
    }

    override suspend fun saveLyricsSource(config: LyricsSourceConfig) {
        val normalized = normalizeName(config.name)
        if (mutableSources.value.any { it.id != config.id && normalizeName(it.name) == normalized }) {
            error("歌词源名称已存在。")
        }
        mutableSources.value = (mutableSources.value.filterNot { it.id == config.id } + config)
            .sortedWith(compareByDescending<LyricsSourceDefinition> { it.priority }.thenBy { it.name.lowercase() })
    }

    override suspend fun saveWorkflowLyricsSource(rawJson: String, editingId: String?): WorkflowLyricsSourceConfig {
        val parsed = parseWorkflowLyricsSourceConfig(rawJson)
        val config = parsed
        if (editingId != null && config.id != editingId) {
            error("Workflow 源 id 不支持修改。")
        }
        val normalized = normalizeName(config.name)
        if (mutableSources.value.any { it.id != config.id && normalizeName(it.name) == normalized }) {
            error("歌词源名称已存在。")
        }
        mutableSources.value = (mutableSources.value.filterNot { it.id == config.id } + config)
            .sortedWith(compareByDescending<LyricsSourceDefinition> { it.priority }.thenBy { it.name.lowercase() })
        return config
    }

    override suspend fun setLyricsSourceEnabled(sourceId: String, enabled: Boolean) = Unit

    override suspend fun deleteLyricsSource(configId: String) {
        mutableSources.value = mutableSources.value.filterNot { it.id == configId }
    }
}

private class FakeAppUpdateRepository(
    var nextResult: Result<AppReleaseInfo>,
) : AppUpdateRepository {
    var calls: Int = 0
        private set

    override suspend fun latestRelease(): Result<AppReleaseInfo> {
        calls += 1
        return nextResult
    }
}

private class SuspendedAppUpdateRepository : AppUpdateRepository {
    private val nextResult = CompletableDeferred<Result<AppReleaseInfo>>()
    var calls: Int = 0
        private set

    override suspend fun latestRelease(): Result<AppReleaseInfo> {
        calls += 1
        return nextResult.await()
    }

    fun complete(result: Result<AppReleaseInfo>) {
        nextResult.complete(result)
    }
}

private class FakeVlcPathPickerPlatformService(
    var nextResult: Result<String?> = Result.success(null),
) : VlcPathPickerPlatformService {
    override suspend fun pickVlcDirectory(): Result<String?> = nextResult
}

private class FakeAppDataLocationPlatformService(
    override val currentDataRootPath: String = "C:\\Users\\tester\\.lynmusic",
    override var pendingCleanupRootPath: String? = null,
    var nextPickedPath: Result<String?> = Result.success(null),
    var nextScheduleResult: Result<Unit> = Result.success(Unit),
    var nextCleanupResult: Result<Unit> = Result.success(Unit),
) : AppDataLocationPlatformService {
    val scheduledChanges = mutableListOf<Pair<String, AppDataLocationChangeMode>>()
    var pickCalls: Int = 0
        private set
    var pickerCompletion: CompletableDeferred<Unit>? = null
    var scheduleCompletion: CompletableDeferred<Unit>? = null

    override suspend fun pickTargetDataRoot(): Result<String?> {
        pickCalls += 1
        pickerCompletion?.await()
        return nextPickedPath
    }

    override suspend fun scheduleChange(
        targetDataRootPath: String,
        mode: AppDataLocationChangeMode,
    ): Result<Unit> {
        scheduledChanges += targetDataRootPath to mode
        scheduleCompletion?.await()
        return nextScheduleResult
    }

    override suspend fun retryPendingCleanup(): Result<Unit> {
        return nextCleanupResult.onSuccess { pendingCleanupRootPath = null }
    }
}

private class FakeDesktopLyricsPlatformService(
    var permission: Boolean = true,
    override val isSupported: Boolean = true,
    override val consumesAppLyricsUpdates: Boolean = false,
) : DesktopLyricsPlatformService {
    override val closeRequests: Flow<Unit> = emptyFlow()
    val enabledCalls = mutableListOf<Boolean>()
    var permissionRequests = 0
        private set
    var hidden = false
        private set

    override fun hasOverlayPermission(): Boolean = permission

    override suspend fun requestOverlayPermission(): Boolean {
        permissionRequests += 1
        return permission
    }

    override suspend fun setDesktopLyricsEnabled(enabled: Boolean) {
        enabledCalls += enabled
    }

    override suspend fun updateLyrics(text: String) = Unit

    override suspend fun hideLyrics() {
        hidden = true
    }

    override suspend fun release() = Unit
}

private class FakeAppStorageGateway(
    initialSnapshot: AppStorageSnapshot = sampleStorageSnapshot(),
) : AppStorageGateway {
    var currentSnapshot: AppStorageSnapshot = initialSnapshot
    var loadCalls: Int = 0
        private set
    var nextLoadFailure: Throwable? = null

    override suspend fun loadStorageSnapshot(): Result<AppStorageSnapshot> {
        loadCalls += 1
        val failure = nextLoadFailure
        if (failure != null) {
            nextLoadFailure = null
            return Result.failure(failure)
        }
        return Result.success(currentSnapshot)
    }

    override suspend fun clearCategory(category: AppStorageCategory): Result<Unit> {
        currentSnapshot = currentSnapshot.copy(
            totalSizeBytes = currentSnapshot.totalSizeBytes -
                currentSnapshot.categories.firstOrNull { it.category == category }?.sizeBytes.orZero(),
            categories = currentSnapshot.categories.map { usage ->
                if (usage.category == category) usage.copy(sizeBytes = 0L) else usage
            },
        )
        return Result.success(Unit)
    }
}

private class FakeDeviceInfoGateway(
    initialSnapshot: DeviceInfoSnapshot = sampleDeviceInfoSnapshot(),
) : DeviceInfoGateway {
    var currentSnapshot: DeviceInfoSnapshot = initialSnapshot
    var loadCalls: Int = 0
        private set
    var nextLoadFailure: Throwable? = null

    override suspend fun loadDeviceInfoSnapshot(): Result<DeviceInfoSnapshot> {
        loadCalls += 1
        val failure = nextLoadFailure
        if (failure != null) {
            nextLoadFailure = null
            return Result.failure(failure)
        }
        return Result.success(currentSnapshot)
    }
}

private class FakeLyricsShareFontLibraryPlatformService(
    initialFonts: List<LyricsShareFontOption> = emptyList(),
) : LyricsShareFontLibraryPlatformService {
    private val mutableFonts = initialFonts.toMutableList()

    var nextListResult: Result<List<LyricsShareFontOption>>? = null
    var nextImportResult: Result<LyricsShareFontOption?> = Result.success(null)
    var nextDeleteResult: Result<Unit> = Result.success(Unit)
    var listCalls: Int = 0
        private set
    var importCalls: Int = 0
        private set
    val deletedFontKeys = mutableListOf<String>()

    override suspend fun listImportedFonts(): Result<List<LyricsShareFontOption>> {
        listCalls += 1
        val result = nextListResult
        nextListResult = null
        return result ?: Result.success(mutableFonts.toList())
    }

    override suspend fun importFont(): Result<LyricsShareFontOption?> {
        importCalls += 1
        val result = nextImportResult
        result.getOrNull()?.let { imported ->
            mutableFonts.removeAll { it.fontKey == imported.fontKey }
            mutableFonts.add(imported)
        }
        return result
    }

    override suspend fun deleteImportedFont(fontKey: String): Result<Unit> {
        deletedFontKeys += fontKey
        val result = nextDeleteResult
        if (result.isSuccess) {
            mutableFonts.removeAll { it.fontKey == fontKey }
        }
        return result
    }

    override suspend fun resolveImportedFontPath(fontKey: String): Result<String?> = Result.success(null)
}

private class FakeLyricsShareFontPreferencesStore(
    initialFontKey: String? = null,
) : LyricsShareFontPreferencesStore {
    private val mutableSelectedLyricsShareFontKey = MutableStateFlow(initialFontKey)

    override val selectedLyricsShareFontKey: StateFlow<String?> = mutableSelectedLyricsShareFontKey.asStateFlow()

    override suspend fun setSelectedLyricsShareFontKey(value: String?) {
        mutableSelectedLyricsShareFontKey.value = value
    }
}

private fun sampleDirectSource(
    id: String = "direct-1",
    name: String = "My Direct Source",
): LyricsSourceConfig {
    return LyricsSourceConfig(
        id = id,
        name = name,
        method = RequestMethod.GET,
        urlTemplate = "https://lyrics.example/direct",
        responseFormat = LyricsResponseFormat.JSON,
        extractor = "json-map:lyrics=plainLyrics,title=trackName",
        priority = 10,
        enabled = true,
    )
}

private fun sampleWorkflowSource(
    id: String = "wf-1",
    name: String = "Workflow Source",
    rawJson: String = workflowJson(id, name),
): WorkflowLyricsSourceConfig {
    return WorkflowLyricsSourceConfig(
        id = id,
        name = name,
        priority = 5,
        enabled = true,
        search = WorkflowSearchConfig(
            request = WorkflowRequestConfig(
                method = RequestMethod.GET,
                url = "https://lyrics.example/search",
                responseFormat = LyricsResponseFormat.JSON,
            ),
            resultPath = "items",
            mapping = mapOf(
                "id" to "id",
                "title" to "title",
                "artists" to "artists",
            ),
        ),
        lyrics = WorkflowLyricsConfig(
            steps = listOf(
                WorkflowLyricsStepConfig(
                    request = WorkflowRequestConfig(
                        method = RequestMethod.GET,
                        url = "https://lyrics.example/item/{candidate.id}",
                        responseFormat = LyricsResponseFormat.JSON,
                    ),
                    payloadPath = "lyrics",
                    format = LyricsResponseFormat.TEXT,
                ),
            ),
        ),
        rawJson = rawJson,
    )
}

private fun sampleMusicmatchSource(): WorkflowLyricsSourceConfig {
    return parseWorkflowLyricsSourceConfig(buildManagedMusicmatchWorkflowJson("token-123"))
}

private fun sampleLrcApiSource(
    urlTemplate: String = "https://lyrics.example/jsonapi",
): LyricsSourceConfig {
    return buildManagedLrcApiConfig(urlTemplate)
}

private fun sampleAppRelease(
    tagName: String,
): AppReleaseInfo {
    return AppReleaseInfo(
        tagName = tagName,
        name = "$tagName Release",
        body = "Release body",
        htmlUrl = "https://github.com/wesley666/LynMusic/releases/tag/$tagName",
        publishedAt = "2026-05-17T10:45:00Z",
    )
}

private fun workflowJson(
    id: String,
    name: String,
    enabled: Boolean = true,
): String {
    return """
        {
          "id": "$id",
          "name": "$name",
          "kind": "workflow",
          "enabled": $enabled,
          "priority": 5,
          "search": {
            "method": "GET",
            "url": "https://lyrics.example/search",
            "responseFormat": "JSON",
            "resultPath": "items",
            "mapping": {
              "id": "id",
              "title": "title",
              "artists": "artists"
            }
          },
          "lyrics": {
            "steps": [
              {
                "method": "GET",
                "url": "https://lyrics.example/item/{candidate.id}",
                "responseFormat": "JSON",
                "payloadPath": "lyrics",
                "format": "TEXT"
              }
            ]
          }
        }
    """.trimIndent()
}

private fun normalizeName(value: String): String = value.trim().lowercase()

private fun sampleStorageSnapshot(
    artwork: Long = 0L,
    playback: Long = 0L,
    lyricsShare: Long = 0L,
    tagEdit: Long = 0L,
): AppStorageSnapshot {
    val categories = listOfNotNull(
        AppStorageCategoryUsage(AppStorageCategory.Artwork, artwork),
        AppStorageCategoryUsage(AppStorageCategory.PlaybackCache, playback).takeIf { playback >= 0L },
        AppStorageCategoryUsage(AppStorageCategory.LyricsShareTemp, lyricsShare).takeIf { lyricsShare >= 0L },
        AppStorageCategoryUsage(AppStorageCategory.TagEditTemp, tagEdit).takeIf { tagEdit >= 0L },
    )
    return AppStorageSnapshot(
        totalSizeBytes = categories.sumOf { it.sizeBytes },
        categories = categories,
    )
}

private fun sampleDeviceInfoSnapshot(
    systemName: String = "Desktop",
    systemVersion: String = "1.0",
    resolution: String? = "1920 × 1080 px",
    resolutionWidthPx: Int? = 1920,
    resolutionHeightPx: Int? = 1080,
    cpuDescription: String? = "arm64 · 8 核",
    totalMemoryBytes: Long? = 8L * 1024 * 1024 * 1024,
    deviceModel: String? = null,
): DeviceInfoSnapshot {
    return DeviceInfoSnapshot(
        systemName = systemName,
        systemVersion = systemVersion,
        resolution = resolution,
        resolutionWidthPx = resolutionWidthPx,
        resolutionHeightPx = resolutionHeightPx,
        cpuDescription = cpuDescription,
        totalMemoryBytes = totalMemoryBytes,
        deviceModel = deviceModel,
    )
}

private fun Long?.orZero(): Long = this ?: 0L
