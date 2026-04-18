package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_FAMILY
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.data.repository.AppliedLyricsResult
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository
import top.iwesley.lyn.music.data.repository.ResolvedLyricsResult

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStoreLyricsShareTest {

    @Test
    fun `open lyrics share preselects highlighted line and builds preview`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val lyrics = syncedLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeLyricsSharePlaybackRepository(
            PlaybackSnapshot(
                queue = listOf(track),
                currentIndex = 0,
                mode = PlaybackMode.ORDER,
                positionMs = 1_500L,
            ),
        )
        val shareService = FakeLyricsSharePlatformService()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.isLyricsShareVisible)
        assertEquals(setOf(0), state.selectedLyricsLineIndices)
        assertEquals(listOf("第一句"), state.shareCardModel?.lyricsLines)
        assertEquals(1, shareService.buildPreviewCalls)
        assertEquals(FakeLyricsSharePlatformService.previewBytes.toList(), state.sharePreviewBytes?.toList())
        assertEquals(setOf(0), state.sharePreviewSelection)
        assertTrue(state.hasFreshSharePreview)
        scope.cancel()
    }

    @Test
    fun `toggling lyrics selection rebuilds preview`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val lyrics = syncedLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService()
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        advanceUntilIdle()

        assertEquals(setOf(0, 1), store.state.value.selectedLyricsLineIndices)
        assertEquals(listOf("第一句", "第二句"), store.state.value.shareCardModel?.lyricsLines)
        assertEquals(2, shareService.buildPreviewCalls)
        assertEquals(setOf(0, 1), store.state.value.sharePreviewSelection)
        assertTrue(store.state.value.hasFreshSharePreview)
        scope.cancel()
    }

    @Test
    fun `toggling lyrics selection keeps previous preview visible while next preview renders`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val lyrics = syncedLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val delayedPreview = CompletableDeferred<Result<ByteArray>>()
        val shareService = FakeLyricsSharePlatformService(
            previewResults = ArrayDeque(
                listOf(
                    PreviewResponse.Immediate(Result.success(FakeLyricsSharePlatformService.previewBytes)),
                    PreviewResponse.Deferred(delayedPreview),
                ),
            ),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()

        val previousPreview = store.state.value.sharePreviewBytes
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        runCurrent()

        assertTrue(store.state.value.isShareRendering)
        assertEquals(previousPreview?.toList(), store.state.value.sharePreviewBytes?.toList())
        assertEquals(setOf(0), store.state.value.sharePreviewSelection)
        assertFalse(store.state.value.hasFreshSharePreview)

        delayedPreview.complete(Result.success(byteArrayOf(0x09, 0x08, 0x07, 0x06)))
        advanceUntilIdle()

        assertEquals(setOf(0, 1), store.state.value.sharePreviewSelection)
        assertEquals(byteArrayOf(0x09, 0x08, 0x07, 0x06).toList(), store.state.value.sharePreviewBytes?.toList())
        assertTrue(store.state.value.hasFreshSharePreview)
        scope.cancel()
    }

    @Test
    fun `switching lyrics share template rebuilds preview with selected template`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService(
            previewResults = ArrayDeque(
                listOf(
                    PreviewResponse.Immediate(Result.success(FakeLyricsSharePlatformService.previewBytes)),
                    PreviewResponse.Immediate(Result.success(byteArrayOf(0x05, 0x06, 0x07))),
                ),
            ),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LyricsShareTemplate.ARTWORK_TINT, state.selectedLyricsShareTemplate)
        assertEquals(LyricsShareTemplate.ARTWORK_TINT, state.sharePreviewTemplate)
        assertEquals(LyricsShareTemplate.ARTWORK_TINT, shareService.lastPreviewModel?.template)
        assertEquals(byteArrayOf(0x05, 0x06, 0x07).toList(), state.sharePreviewBytes?.toList())
        assertTrue(state.hasFreshSharePreview)
        assertEquals(2, shareService.buildPreviewCalls)
        scope.cancel()
    }

    @Test
    fun `open lyrics share keeps font list unloaded until explicitly requested`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val fontPreferencesStore = FakeLyricsShareFontPreferencesStore()
        val shareService = FakeLyricsSharePlatformService(
            fontListResult = Result.success(
                listOf(
                    LyricsShareFontOption("PingFang SC", "你好"),
                    LyricsShareFontOption("Serif", "Hello"),
                    LyricsShareFontOption("pingfang sc", "不同预览"),
                    LyricsShareFontOption("Arial", "Hello"),
                ),
            ),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
            lyricsShareFontPreferencesStore = fontPreferencesStore,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.availableLyricsShareFonts.isEmpty())
        assertNull(state.lyricsShareFontsError)
        assertEquals(DEFAULT_LYRICS_SHARE_FONT_FAMILY, state.selectedLyricsShareFontFamily)
        assertEquals(DEFAULT_LYRICS_SHARE_FONT_FAMILY, state.shareCardModel?.fontFamilyName)
        assertEquals(DEFAULT_LYRICS_SHARE_FONT_FAMILY, shareService.lastPreviewModel?.fontFamilyName)
        assertEquals(0, shareService.listAvailableFontFamiliesCalls)
        assertEquals(1, shareService.buildPreviewCalls)
        scope.cancel()
    }

    @Test
    fun `request lyrics share fonts loads font list and applies default serif selection`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val fontPreferencesStore = FakeLyricsShareFontPreferencesStore()
        val shareService = FakeLyricsSharePlatformService(
            fontListResult = Result.success(
                listOf(
                    LyricsShareFontOption("PingFang SC", "你好"),
                    LyricsShareFontOption("Serif", "Hello"),
                    LyricsShareFontOption("pingfang sc", "不同预览"),
                    LyricsShareFontOption("Arial", "Hello"),
                ),
            ),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
            lyricsShareFontPreferencesStore = fontPreferencesStore,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.RequestLyricsShareFonts)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(listOf("PingFang SC", "Serif", "Arial"), state.availableLyricsShareFonts.map { it.familyName })
        assertEquals(listOf("你好", "Hello", "Hello"), state.availableLyricsShareFonts.map { it.previewText })
        assertEquals(DEFAULT_LYRICS_SHARE_FONT_FAMILY, state.selectedLyricsShareFontFamily)
        assertEquals(DEFAULT_LYRICS_SHARE_FONT_FAMILY, state.shareCardModel?.fontFamilyName)
        assertEquals(DEFAULT_LYRICS_SHARE_FONT_FAMILY, shareService.lastPreviewModel?.fontFamilyName)
        assertEquals(DEFAULT_LYRICS_SHARE_FONT_FAMILY, fontPreferencesStore.selectedLyricsShareFontFamily.value)
        assertEquals(1, shareService.listAvailableFontFamiliesCalls)
        assertEquals(1, shareService.buildPreviewCalls)
        scope.cancel()
    }

    @Test
    fun `request lyrics share fonts exposes loading state while lookup is in progress`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val delayedFonts = CompletableDeferred<Result<List<LyricsShareFontOption>>>()
        val shareService = FakeDeferredFontLookupLyricsSharePlatformService(delayedFonts)
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
            lyricsShareFontPreferencesStore = FakeLyricsShareFontPreferencesStore(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.RequestLyricsShareFonts)
        runCurrent()

        assertTrue(store.state.value.isLyricsShareFontsLoading)
        assertNull(store.state.value.lyricsShareFontsError)
        assertEquals(1, shareService.listAvailableFontFamiliesCalls)

        delayedFonts.complete(
            Result.success(
                listOf(
                    LyricsShareFontOption("Serif", "Hello"),
                    LyricsShareFontOption("PingFang SC", "你好"),
                ),
            ),
        )
        advanceUntilIdle()

        assertFalse(store.state.value.isLyricsShareFontsLoading)
        assertEquals(listOf("Serif", "PingFang SC"), store.state.value.availableLyricsShareFonts.map { it.familyName })
        scope.cancel()
    }

    @Test
    fun `changing lyrics share font rebuilds preview and persists selection`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val fontPreferencesStore = FakeLyricsShareFontPreferencesStore()
        val shareService = FakeLyricsSharePlatformService(
            fontListResult = Result.success(
                listOf(
                    LyricsShareFontOption("Serif", "Hello"),
                    LyricsShareFontOption("PingFang SC", "你好"),
                ),
            ),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
            lyricsShareFontPreferencesStore = fontPreferencesStore,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.RequestLyricsShareFonts)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.LyricsShareFontChanged("PingFang SC"))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("PingFang SC", state.selectedLyricsShareFontFamily)
        assertEquals("PingFang SC", state.shareCardModel?.fontFamilyName)
        assertEquals("PingFang SC", state.sharePreviewFontFamilyName)
        assertEquals("PingFang SC", shareService.lastPreviewModel?.fontFamilyName)
        assertEquals("PingFang SC", fontPreferencesStore.selectedLyricsShareFontFamily.value)
        assertEquals(2, shareService.buildPreviewCalls)
        assertTrue(state.hasFreshSharePreview)
        scope.cancel()
    }

    @Test
    fun `invalid saved lyrics share font falls back to serif then first available`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

        val serifPrefs = FakeLyricsShareFontPreferencesStore("Missing Font")
        val serifStore = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = FakeLyricsSharePlatformService(
                fontListResult = Result.success(
                    listOf(
                        LyricsShareFontOption("PingFang SC"),
                        LyricsShareFontOption("Serif"),
                    ),
                ),
            ),
            lyricsShareFontPreferencesStore = serifPrefs,
        )

        advanceUntilIdle()
        serifStore.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        serifStore.dispatch(PlayerIntent.RequestLyricsShareFonts)
        advanceUntilIdle()

        assertEquals("Serif", serifStore.state.value.selectedLyricsShareFontFamily)
        assertEquals("Serif", serifPrefs.selectedLyricsShareFontFamily.value)

        val firstAvailablePrefs = FakeLyricsShareFontPreferencesStore("Missing Font")
        val firstAvailableStore = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = FakeLyricsSharePlatformService(
                fontListResult = Result.success(
                    listOf(
                        LyricsShareFontOption("PingFang SC"),
                        LyricsShareFontOption("Arial"),
                    ),
                ),
            ),
            lyricsShareFontPreferencesStore = firstAvailablePrefs,
        )

        advanceUntilIdle()
        firstAvailableStore.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        firstAvailableStore.dispatch(PlayerIntent.RequestLyricsShareFonts)
        advanceUntilIdle()

        assertEquals("PingFang SC", firstAvailableStore.state.value.selectedLyricsShareFontFamily)
        assertEquals("PingFang SC", firstAvailablePrefs.selectedLyricsShareFontFamily.value)
        scope.cancel()
    }

    @Test
    fun `empty font whitelist result keeps lyrics share available without showing fonts`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService(
            fontListResult = Result.success(emptyList()),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
            lyricsShareFontPreferencesStore = FakeLyricsShareFontPreferencesStore(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.RequestLyricsShareFonts)
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.availableLyricsShareFonts.isEmpty())
        assertFalse(state.isLyricsShareFontsLoading)
        assertEquals("读取系统字体失败", state.lyricsShareFontsError)
        assertNull(state.shareMessage)
        assertTrue(state.sharePreviewBytes?.isNotEmpty() == true)
        assertEquals(1, shareService.listAvailableFontFamiliesCalls)
        scope.cancel()
    }

    @Test
    fun `font list failure does not block lyrics share preview save or copy`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService(
            fontListResult = Result.failure(IllegalStateException("font lookup boom")),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
            lyricsShareFontPreferencesStore = FakeLyricsShareFontPreferencesStore(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.RequestLyricsShareFonts)
        advanceUntilIdle()

        assertEquals("读取系统字体失败", store.state.value.lyricsShareFontsError)
        assertNull(store.state.value.shareMessage)
        assertTrue(store.state.value.sharePreviewBytes?.isNotEmpty() == true)
        assertTrue(store.state.value.availableLyricsShareFonts.isEmpty())
        assertFalse(store.state.value.isLyricsShareFontsLoading)

        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        advanceUntilIdle()
        assertEquals(1, shareService.copyImageCalls)
        assertEquals("图片已复制", store.state.value.shareMessage)

        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        advanceUntilIdle()
        assertEquals(1, shareService.saveImageCalls)
        assertEquals("图片已保存到文件", store.state.value.shareMessage)
        scope.cancel()
    }

    @Test
    fun `request lyrics share fonts clears previous error and reloads successfully`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val fontPreferencesStore = FakeLyricsShareFontPreferencesStore()
        val shareService = FakeReloadableLyricsSharePlatformService(
            initialFontListResult = Result.failure(IllegalStateException("font lookup boom")),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
            lyricsShareFontPreferencesStore = fontPreferencesStore,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.RequestLyricsShareFonts)
        advanceUntilIdle()

        assertEquals("读取系统字体失败", store.state.value.lyricsShareFontsError)

        shareService.fontListResult = Result.success(
            listOf(
                LyricsShareFontOption("Serif", "Hello"),
                LyricsShareFontOption("PingFang SC", "你好"),
            ),
        )
        store.dispatch(PlayerIntent.RequestLyricsShareFonts)
        advanceUntilIdle()

        assertEquals(listOf("Serif", "PingFang SC"), store.state.value.availableLyricsShareFonts.map { it.familyName })
        assertEquals("Serif", store.state.value.selectedLyricsShareFontFamily)
        assertNull(store.state.value.lyricsShareFontsError)
        assertEquals(2, shareService.listAvailableFontFamiliesCalls)
        scope.cancel()
    }

    @Test
    fun `track change dismisses lyrics share and clears preview`() = runTest {
        val firstTrack = sampleTrack("track-1", "第一首")
        val secondTrack = sampleTrack("track-2", "第二首")
        val lyrics = syncedLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeLyricsSharePlaybackRepository(
            PlaybackSnapshot(
                queue = listOf(firstTrack),
                currentIndex = 0,
                positionMs = 1_500L,
            ),
        )
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = FakeLyricsSharePlatformService(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        advanceUntilIdle()

        playbackRepository.updateSnapshot(
            PlaybackSnapshot(
                queue = listOf(secondTrack),
                currentIndex = 0,
                positionMs = 0L,
            ),
        )
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.isLyricsShareVisible)
        assertTrue(state.selectedLyricsLineIndices.isEmpty())
        assertEquals(LyricsShareTemplate.ARTWORK_TINT, state.selectedLyricsShareTemplate)
        assertNull(state.shareCardModel)
        assertNull(state.sharePreviewBytes)
        scope.cancel()
    }

    @Test
    fun `save and copy require at least one selected lyric line`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val lyrics = pureTextLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService()
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        advanceUntilIdle()

        assertEquals("请先选择至少一句歌词", store.state.value.shareMessage)
        assertEquals(0, shareService.saveImageCalls)

        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        advanceUntilIdle()

        assertEquals("请先选择至少一句歌词", store.state.value.shareMessage)
        assertEquals(0, shareService.copyImageCalls)
        scope.cancel()
    }

    @Test
    fun `preview failure surfaces visible error`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService(
            previewResult = Result.failure(IllegalStateException("boom")),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()

        assertTrue(store.state.value.sharePreviewError?.contains("boom") == true)
        assertNull(store.state.value.sharePreviewBytes)
        scope.cancel()
    }

    @Test
    fun `save and copy use generated preview bytes`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService()
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        advanceUntilIdle()
        assertEquals(1, shareService.copyImageCalls)
        assertEquals("图片已复制", store.state.value.shareMessage)

        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        advanceUntilIdle()
        assertEquals(1, shareService.saveImageCalls)
        assertEquals("图片已保存到文件", store.state.value.shareMessage)
        scope.cancel()
    }

    private fun sampleTrack(id: String, title: String): Track {
        return Track(
            id = id,
            sourceId = "local-1",
            title = title,
            artistName = "歌手 A",
            albumTitle = "专辑 A",
            durationMs = 180_000L,
            mediaLocator = "file:///music/$id.mp3",
            relativePath = "$title.mp3",
            artworkLocator = "file:///music/$id.jpg",
        )
    }

    private fun syncedLyrics(): LyricsDocument {
        return LyricsDocument(
            lines = listOf(
                LyricsLine(timestampMs = 1_000L, text = "第一句"),
                LyricsLine(timestampMs = 2_000L, text = "第二句"),
                LyricsLine(timestampMs = 3_000L, text = "第三句"),
            ),
            sourceId = "lyrics-1",
            rawPayload = "[00:01.00]第一句\n[00:02.00]第二句\n[00:03.00]第三句",
        )
    }

    private fun pureTextLyrics(): LyricsDocument {
        return LyricsDocument(
            lines = listOf(
                LyricsLine(timestampMs = null, text = "纯文本第一句"),
                LyricsLine(timestampMs = null, text = ""),
                LyricsLine(timestampMs = null, text = "纯文本第二句"),
            ),
            sourceId = "lyrics-2",
            rawPayload = "纯文本第一句\n\n纯文本第二句",
        )
    }
}

private class FakeLyricsSharePlaybackRepository(
    initialSnapshot: PlaybackSnapshot,
) : PlaybackRepository {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    fun updateSnapshot(snapshot: PlaybackSnapshot) {
        mutableSnapshot.value = snapshot
    }

    override suspend fun hydratePersistedQueueIfNeeded() = Unit

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) = Unit

    override suspend fun togglePlayPause() = Unit

    override suspend fun skipNext() = Unit

    override suspend fun skipPrevious() = Unit

    override suspend fun seekTo(positionMs: Long) = Unit

    override suspend fun setVolume(volume: Float) = Unit

    override suspend fun cycleMode() = Unit

    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?) = Unit

    override suspend fun close() = Unit
}

private class FakeLyricsShareRepository(
    private val document: LyricsDocument,
) : LyricsRepository {
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult {
        return ResolvedLyricsResult(document = document, artworkLocator = track.artworkLocator)
    }

    override suspend fun searchLyricsCandidates(
        track: Track,
        includeTrackProvidedCandidate: Boolean,
    ): List<LyricsSearchCandidate> = emptyList()

    override suspend fun applyLyricsCandidate(
        trackId: String,
        candidate: LyricsSearchCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in lyrics share tests")
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        error("Not used in lyrics share tests")
    }

    override suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in lyrics share tests")
    }
}

private class FakeLyricsSharePlatformService(
    private val previewResult: Result<ByteArray> = Result.success(previewBytes),
    private val previewResults: ArrayDeque<PreviewResponse> = ArrayDeque(),
    private val saveResult: Result<LyricsShareSaveResult> = Result.success(LyricsShareSaveResult("图片已保存到文件")),
    private val copyResult: Result<Unit> = Result.success(Unit),
    private val fontListResult: Result<List<LyricsShareFontOption>> = Result.success(emptyList()),
) : LyricsSharePlatformService {
    var buildPreviewCalls: Int = 0
        private set
    var saveImageCalls: Int = 0
        private set
    var copyImageCalls: Int = 0
        private set
    var listAvailableFontFamiliesCalls: Int = 0
        private set
    var lastPreviewModel: LyricsShareCardModel? = null
        private set

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> {
        buildPreviewCalls += 1
        lastPreviewModel = model
        return previewResults.removeFirstOrNull()?.await() ?: previewResult
    }

    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> {
        listAvailableFontFamiliesCalls += 1
        return fontListResult
    }

    override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> {
        saveImageCalls += 1
        assertEquals(previewBytes.toList(), pngBytes.toList())
        return saveResult
    }

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> {
        copyImageCalls += 1
        assertEquals(previewBytes.toList(), pngBytes.toList())
        return copyResult
    }

    companion object {
        val previewBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    }
}

private class FakeReloadableLyricsSharePlatformService(
    initialFontListResult: Result<List<LyricsShareFontOption>>,
    private val previewResult: Result<ByteArray> = Result.success(FakeLyricsSharePlatformService.previewBytes),
) : LyricsSharePlatformService {
    var fontListResult: Result<List<LyricsShareFontOption>> = initialFontListResult
    var buildPreviewCalls: Int = 0
        private set
    var listAvailableFontFamiliesCalls: Int = 0
        private set

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> {
        buildPreviewCalls += 1
        return previewResult
    }

    override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> {
        return Result.success(LyricsShareSaveResult("图片已保存到文件"))
    }

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> {
        listAvailableFontFamiliesCalls += 1
        return fontListResult
    }
}

private class FakeDeferredFontLookupLyricsSharePlatformService(
    private val fontListDeferred: CompletableDeferred<Result<List<LyricsShareFontOption>>>,
    private val previewResult: Result<ByteArray> = Result.success(FakeLyricsSharePlatformService.previewBytes),
) : LyricsSharePlatformService {
    var listAvailableFontFamiliesCalls: Int = 0
        private set

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = previewResult

    override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> {
        return Result.success(LyricsShareSaveResult("图片已保存到文件"))
    }

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> = Result.success(Unit)

    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> {
        listAvailableFontFamiliesCalls += 1
        return fontListDeferred.await()
    }
}

private class FakeLyricsShareFontPreferencesStore(
    initialFontFamily: String? = null,
) : LyricsShareFontPreferencesStore {
    private val mutableSelectedLyricsShareFontFamily = MutableStateFlow(initialFontFamily)

    override val selectedLyricsShareFontFamily: StateFlow<String?> = mutableSelectedLyricsShareFontFamily.asStateFlow()

    override suspend fun setSelectedLyricsShareFontFamily(value: String?) {
        mutableSelectedLyricsShareFontFamily.value = value
    }
}

private sealed interface PreviewResponse {
    suspend fun await(): Result<ByteArray>

    data class Immediate(
        private val result: Result<ByteArray>,
    ) : PreviewResponse {
        override suspend fun await(): Result<ByteArray> = result
    }

    data class Deferred(
        private val deferred: CompletableDeferred<Result<ByteArray>>,
    ) : PreviewResponse {
        override suspend fun await(): Result<ByteArray> = deferred.await()
    }
}
