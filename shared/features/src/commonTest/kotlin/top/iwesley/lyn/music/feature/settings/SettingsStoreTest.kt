package top.iwesley.lyn.music.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppStorageCategoryUsage
import top.iwesley.lyn.music.core.model.AppStorageGateway
import top.iwesley.lyn.music.core.model.AppStorageSnapshot
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.DeviceInfoGateway
import top.iwesley.lyn.music.core.model.DeviceInfoSnapshot
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.WorkflowLyricsConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsStepConfig
import top.iwesley.lyn.music.core.model.WorkflowRequestConfig
import top.iwesley.lyn.music.core.model.WorkflowSearchConfig
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.deriveAppThemePalette
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.data.repository.SettingsRepository
import top.iwesley.lyn.music.domain.MANAGED_LRCAPI_SOURCE_ID
import top.iwesley.lyn.music.domain.buildManagedLrcApiConfig
import top.iwesley.lyn.music.domain.MANAGED_MUSICMATCH_SOURCE_ID
import top.iwesley.lyn.music.domain.buildManagedMusicmatchWorkflowJson
import top.iwesley.lyn.music.domain.parseWorkflowLyricsSourceConfig

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

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
        assertEquals(AppThemeId.Custom, state.selectedTheme)
        assertEquals(customTokens, state.customThemeTokens)
        assertEquals("#101820", state.customBackgroundHex)
        assertEquals(AppThemeTextPalette.Black, state.textPalettePreferences.custom)
        scope.cancel()
    }

    @Test
    fun `forest theme defaults to black text palette`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertEquals(AppThemeTextPalette.Black, store.state.value.textPalettePreferences.forest)
        scope.cancel()
    }

    @Test
    fun `ocean theme defaults to black text palette`() = runTest {
        val repository = FakeSettingsRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()

        assertEquals(AppThemeTextPalette.Black, store.state.value.textPalettePreferences.ocean)
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
        assertEquals(RequestMethod.GET, saved.method)
        assertEquals("", saved.bodyTemplate)
        assertEquals(LyricsResponseFormat.JSON, saved.responseFormat)
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

        assertEquals(AppThemeTextPalette.Black, store.state.value.textPalettePreferences.forest)
        assertEquals(AppThemeTextPalette.Black, repository.currentTextPalettePreferences().forest)
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
        assertEquals(
            AppThemeTextPalette.Black,
            resolveAppThemeTextPalette(store.state.value.selectedTheme, store.state.value.textPalettePreferences),
        )

        store.dispatch(SettingsIntent.ThemeSelected(AppThemeId.Custom))
        advanceUntilIdle()
        assertEquals(
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

        assertEquals(AppThemeId.Forest, store.state.value.selectedTheme)
        assertEquals(AppThemeId.Forest, repository.currentSelectedTheme())
        scope.cancel()
    }

    @Test
    fun `saving valid custom theme updates repository and clears error`() = runTest {
        val repository = FakeSettingsRepository(selectedTheme = AppThemeId.Custom)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CustomThemeBackgroundChanged("#102030"))
        store.dispatch(SettingsIntent.CustomThemeAccentChanged("#405060"))
        store.dispatch(SettingsIntent.CustomThemeFocusChanged("#708090"))
        store.dispatch(SettingsIntent.SaveCustomTheme)
        advanceUntilIdle()

        val expected = AppThemeTokens(
            backgroundArgb = 0xFF102030.toInt(),
            accentArgb = 0xFF405060.toInt(),
            focusArgb = 0xFF708090.toInt(),
        )
        assertEquals(expected, repository.currentCustomThemeTokens())
        assertEquals(expected, store.state.value.customThemeTokens)
        assertEquals(null, store.state.value.themeInputError)
        assertEquals("自定义主题已保存。", store.state.value.message)
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

        assertEquals(defaultCustomThemeTokens(), repository.currentCustomThemeTokens())
        assertEquals(defaultCustomThemeTokens(), store.state.value.customThemeTokens)
        assertEquals("#120B0D", store.state.value.customBackgroundHex)
        scope.cancel()
    }

    @Test
    fun `invalid custom theme hex sets error and does not save`() = runTest {
        val repository = FakeSettingsRepository(selectedTheme = AppThemeId.Custom)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.CustomThemeBackgroundChanged("#12345"))
        store.dispatch(SettingsIntent.CustomThemeAccentChanged("#ZZZZZZ"))
        store.dispatch(SettingsIntent.CustomThemeFocusChanged("#708090"))
        store.dispatch(SettingsIntent.SaveCustomTheme)
        advanceUntilIdle()

        assertEquals(0, repository.setCustomThemeTokensCalls)
        assertEquals("主背景色格式不正确，请使用 #RRGGBB。", store.state.value.themeInputError)
        scope.cancel()
    }

    @Test
    fun `theme palette derivation preserves configured tokens and contrast`() {
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

        assertEquals(defaultCustomThemeTokens().accentArgb, whiteTextPalette.primaryArgb)
        assertEquals(defaultCustomThemeTokens().focusArgb, whiteTextPalette.secondaryArgb)
        assertEquals(0xFFF7F5F3.toInt(), whiteTextPalette.onBackgroundArgb)
        assertEquals(0xFF111111.toInt(), blackTextPalette.onBackgroundArgb)
        assertEquals(0xFFD6D1CD.toInt(), whiteTextPalette.onSurfaceVariantArgb)
        assertEquals(0xFF4A4541.toInt(), blackTextPalette.onSurfaceVariantArgb)
        assertEquals(0xFF111111.toInt(), lightPalette.onBackgroundArgb)
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
        assertEquals("direct-1", state.editingId)
        assertEquals("My Direct Source", state.name)
        assertEquals("https://lyrics.example/direct", state.urlTemplate)
        assertEquals(null, state.editingWorkflowId)
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
        assertEquals("wf-1", state.editingWorkflowId)
        assertEquals(workflow.rawJson, state.workflowJsonInput)
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
        assertEquals("歌词源已新建。", state.message)
        assertEquals("Forked Source", state.name)
        assertEquals("https://lyrics.example/direct", state.urlTemplate)
        assertEquals(2, repository.currentSources().size)
        assertEquals(setOf("My Direct Source", "Forked Source"), repository.currentSources().map { it.name }.toSet())
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
        assertEquals("wf-1", state.editingWorkflowId)
        assertEquals(true, "Workflow Source v2" in state.workflowJsonInput)
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
        assertEquals("Workflow 源已新建。", state.message)
        val workflowSources = repository.currentSources().filterIsInstance<WorkflowLyricsSourceConfig>()
        assertEquals(2, workflowSources.size)
        assertEquals(setOf("Workflow Source", "Forked Workflow"), workflowSources.map { it.name }.toSet())
        assertEquals(true, workflowSources.any { it.id != "wf-1" && it.name == "Forked Workflow" })
        assertEquals(true, "Forked Workflow" in state.workflowJsonInput)
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
        assertEquals("歌词源名称已存在。", state.message)
        assertEquals(" taken name ", state.name)
        assertEquals("https://lyrics.example/new", state.urlTemplate)
        assertEquals(null, state.editingId)
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
        assertEquals(null, state.editingWorkflowId)
        assertEquals("", state.workflowJsonInput)
        assertEquals(2, state.sources.size)
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

        assertEquals("Musicmatch 已保存。", state.message)
        assertEquals("token-123", state.musicmatchUserToken)
        assertEquals(true, state.hasMusicmatchSource)
        assertEquals(MANAGED_MUSICMATCH_SOURCE_ID, managed.id)
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

        assertEquals("LrcAPI 已保存。", state.message)
        assertEquals("https://lyrics.example/jsonapi", state.lrcApiUrl)
        assertEquals(true, state.hasLrcApiSource)
        assertEquals(MANAGED_LRCAPI_SOURCE_ID, managed.id)
        assertEquals("LrcAPI", managed.name)
        assertEquals(110, managed.priority)
        assertEquals("title={title}&artist={artist}", managed.queryTemplate)
        assertEquals("json-map:lyrics=lyrics|lrc,title=title,artist=artist,album=album,durationSeconds=duration,id=id,coverUrl=cover", managed.extractor)
        assertEquals(true, managed.enabled)
        scope.cancel()
    }

    @Test
    fun `clearing lrcapi removes managed direct source`() = runTest {
        val repository = FakeSettingsRepository(listOf(sampleLrcApiSource()))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ClearLrcApi)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("", state.lrcApiUrl)
        assertEquals(false, state.hasLrcApiSource)
        assertEquals(emptyList(), repository.currentSources())
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
        assertEquals("https://lyrics.example/jsonapi", state.lrcApiUrl)
        assertEquals(true, state.hasLrcApiSource)
        assertEquals(null, state.editingId)
        assertEquals("", state.workflowJsonInput)
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
        assertEquals("", state.musicmatchUserToken)
        assertEquals(false, state.hasMusicmatchSource)
        assertEquals(emptyList(), repository.currentSources())
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
        assertEquals("token-123", state.musicmatchUserToken)
        assertEquals(true, state.hasMusicmatchSource)
        assertEquals(null, state.editingWorkflowId)
        assertEquals("", state.workflowJsonInput)
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
        assertEquals("", state.lrcApiUrl)
        assertEquals(false, state.hasLrcApiSource)
        assertEquals("歌词源已删除。", state.message)
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
        assertEquals(3_328L, state.storageSnapshot?.totalSizeBytes)
        assertEquals(true, state.storageLoaded)
        assertEquals(false, state.storageLoading)
        assertEquals(1, storageGateway.loadCalls)
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
        assertEquals("Google Pixel 8", state.deviceInfoSnapshot?.deviceModel)
        assertEquals("Android", state.deviceInfoSnapshot?.systemName)
        assertEquals(true, state.deviceInfoLoaded)
        assertEquals(false, state.deviceInfoLoading)
        assertEquals(1, deviceInfoGateway.loadCalls)
        scope.cancel()
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
        assertEquals("macOS", state.deviceInfoSnapshot?.systemName)
        assertEquals("读取设备信息失败。", state.message)
        assertEquals(true, state.deviceInfoLoaded)
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

        assertEquals(2, deviceInfoGateway.loadCalls)
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
        assertEquals(2_048L, state.storageSnapshot?.totalSizeBytes)
        assertEquals("缓存统计失败。", state.message)
        assertEquals(true, state.storageLoaded)
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
        assertEquals(4_288L, state.storageSnapshot?.totalSizeBytes)
        assertEquals(0L, state.storageSnapshot?.categories?.first { it.category == AppStorageCategory.Artwork }?.sizeBytes)
        assertEquals("封面缓存已清除。", state.message)
        assertEquals(null, state.clearingStorageCategory)
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

        assertEquals(2, storageGateway.loadCalls)
        scope.cancel()
    }
}

private class FakeSettingsRepository(
    sources: List<LyricsSourceDefinition> = emptyList(),
    selectedTheme: AppThemeId = AppThemeId.Classic,
    customThemeTokens: AppThemeTokens = defaultCustomThemeTokens(),
    textPalettePreferences: AppThemeTextPalettePreferences = defaultThemeTextPalettePreferences(),
) : SettingsRepository {
    private val mutableSources = MutableStateFlow(sources)
    private val mutableUseSambaCache = MutableStateFlow(true)
    private val mutableSelectedTheme = MutableStateFlow(selectedTheme)
    private val mutableCustomThemeTokens = MutableStateFlow(customThemeTokens)
    private val mutableTextPalettePreferences = MutableStateFlow(textPalettePreferences)

    override val lyricsSources: Flow<List<LyricsSourceDefinition>> = mutableSources.asStateFlow()
    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val selectedTheme: StateFlow<AppThemeId> = mutableSelectedTheme.asStateFlow()
    override val customThemeTokens: StateFlow<AppThemeTokens> = mutableCustomThemeTokens.asStateFlow()
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = mutableTextPalettePreferences.asStateFlow()

    var setCustomThemeTokensCalls: Int = 0
        private set

    fun currentSources(): List<LyricsSourceDefinition> = mutableSources.value
    fun currentSelectedTheme(): AppThemeId = mutableSelectedTheme.value
    fun currentCustomThemeTokens(): AppThemeTokens = mutableCustomThemeTokens.value
    fun currentTextPalettePreferences(): AppThemeTextPalettePreferences = mutableTextPalettePreferences.value

    override suspend fun ensureDefaults() = Unit

    override suspend fun setUseSambaCache(enabled: Boolean) {
        mutableUseSambaCache.value = enabled
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

private fun workflowJson(
    id: String,
    name: String,
): String {
    return """
        {
          "id": "$id",
          "name": "$name",
          "kind": "workflow",
          "enabled": true,
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
    cpuDescription: String? = "arm64 · 8 核",
    totalMemoryBytes: Long? = 8L * 1024 * 1024 * 1024,
    deviceModel: String? = null,
): DeviceInfoSnapshot {
    return DeviceInfoSnapshot(
        systemName = systemName,
        systemVersion = systemVersion,
        resolution = resolution,
        cpuDescription = cpuDescription,
        totalMemoryBytes = totalMemoryBytes,
        deviceModel = deviceModel,
    )
}

private fun Long?.orZero(): Long = this ?: 0L
