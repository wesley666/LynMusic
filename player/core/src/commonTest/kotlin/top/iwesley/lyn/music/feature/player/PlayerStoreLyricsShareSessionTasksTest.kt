package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStoreLyricsShareSessionTasksTest {
    @Test fun editingCancelsObsoleteRenderAndClearCancelsItsReplacement() = withLyricsShareLifecycle {
        open()
        platform.renderAutomatically = false
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        val old = platform.renders.last()
        dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        drain()
        val latest = platform.renders.last()
        assertTrue(old.cancelled)
        assertFalse(latest.cancelled)
        assertTrue(store.state.value.isShareRendering)
        dispatch(PlayerIntent.ClearLyricsSelection)
        drain()
        assertTrue(latest.cancelled)
        assertFalse(store.state.value.isShareRendering)
        assertNull(store.state.value.sharePreviewBytes)
    }

    @Test fun oldPreviewFinallyCannotEndPendingReplacement() = withLyricsShareLifecycle {
        open()
        platform.renderAutomatically = false
        platform.ignoreRenderCancellation = true
        dispatch(PlayerIntent.BuildLyricsSharePreview)
        drain()
        val old = platform.renders.last()
        dispatch(PlayerIntent.BuildLyricsSharePreview)
        drain()
        val currentId = store.state.value.lyricsSharePreviewRequestId
        val latest = platform.renders.last()
        old.succeed(old.input.lifecycleImage())
        drain()
        assertEquals(currentId, store.state.value.lyricsSharePreviewRequestId)
        assertTrue(store.state.value.isShareRendering)
        latest.succeed(latest.input.lifecycleImage())
        drain()
        assertFalse(store.state.value.isShareRendering)
        assertTrue(store.state.value.hasFreshSharePreview)
    }

    @Test fun fontInvalidationCancelsLookupBeforeAnotherRequestIsMade() = withLyricsShareLifecycle {
        open()
        platform.fontsAutomatically = false
        loadFonts()
        val pending = platform.fonts.single()
        dispatch(PlayerIntent.InvalidateLyricsShareFontCache)
        drain()
        assertTrue(pending.cancelled)
        assertFalse(store.state.value.isLyricsShareFontsLoading)
        assertNull(store.state.value.lyricsShareFontsRequestId)
        loadFonts()
        assertEquals(2, platform.fonts.size)
        assertTrue(store.state.value.isLyricsShareFontsLoading)
    }

    @Test fun oldFontFinallyCannotEndPendingReplacement() = withLyricsShareLifecycle {
        open()
        platform.fontsAutomatically = false
        platform.ignoreFontCancellation = true
        loadFonts()
        val old = platform.fonts.single()
        dispatch(PlayerIntent.InvalidateLyricsShareFontCache)
        loadFonts()
        val id = store.state.value.lyricsShareFontsRequestId
        old.fail()
        drain()
        assertTrue(store.state.value.isLyricsShareFontsLoading)
        assertEquals(id, store.state.value.lyricsShareFontsRequestId)
        assertNull(store.state.value.lyricsShareFontsError)
        platform.fonts.last().succeed(listOf(LyricsShareFontOption("Latest", "Latest")))
        drain()
        assertFalse(store.state.value.isLyricsShareFontsLoading)
        assertEquals("Latest", store.state.value.selectedLyricsShareFontKey)
    }

    @Test fun thrownPreviewFailureEndsBusyAndAllowsRetry() = withLyricsShareLifecycle {
        open()
        val previousBytes = store.state.value.sharePreviewBytes
        platform.renderException = IllegalStateException("Renderer failed before returning Result")
        dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        assertFalse(store.state.value.isShareRendering)
        assertNull(store.state.value.lyricsSharePreviewRequestId)
        assertNotNull(store.state.value.sharePreviewError)
        assertContentEquals(previousBytes, store.state.value.sharePreviewBytes)
        assertFalse(store.state.value.hasFreshSharePreview)
        platform.renderException = null
        dispatch(PlayerIntent.BuildLyricsSharePreview)
        drain()
        assertTrue(store.state.value.hasFreshSharePreview)
        assertNull(store.state.value.sharePreviewError)
    }

    @Test fun thrownFontFailureEndsBusyAndAllowsRetry() = withLyricsShareLifecycle {
        open()
        platform.fontsException = IllegalStateException("Font enumeration threw")
        loadFonts()
        assertFalse(store.state.value.isLyricsShareFontsLoading)
        assertNull(store.state.value.lyricsShareFontsRequestId)
        assertNotNull(store.state.value.lyricsShareFontsError)
        platform.fontsException = null
        loadFonts()
        assertTrue(store.state.value.availableLyricsShareFonts.isNotEmpty())
        assertNull(store.state.value.lyricsShareFontsError)
    }

    @Test fun thrownInitializationFailureEndsSessionAndAllowsReopening() = runTest {
        var fail = true
        val library = object : LyricsShareFontLibraryPlatformService by UnsupportedLyricsShareFontLibraryPlatformService {
            override suspend fun resolveImportedFontPath(fontKey: String): Result<String?> {
                if (fail) error("Font path failed")
                return Result.success("/tmp/abcdef__Font.ttf")
            }
        }
        val fixture = LyricsShareLifecycleFixture(this, library)
        try { with(fixture) {
            preferences.selectedLyricsShareFontKey.value = "imported:abcdef"
            dispatch(PlayerIntent.BeginLyricsShare)
            drain()
            assertFalse(store.state.value.isLyricsShareInitializing)
            assertNull(store.state.value.lyricsShareInitializationRequestId)
            assertNull(store.state.value.lyricsShareSession)
            assertNotNull(store.state.value.message)
            fail = false
            open()
            assertTrue(store.state.value.hasFreshSharePreview)
        } } finally { fixture.dispose() }
    }

    @Test fun invalidationRestartsInitializationAndLateOldResultCannotEndIt() = runTest {
        val paths = mutableListOf<SharePlatformCall<String, String?>>()
        val library = object : LyricsShareFontLibraryPlatformService by UnsupportedLyricsShareFontLibraryPlatformService {
            override suspend fun resolveImportedFontPath(fontKey: String): Result<String?> =
                SharePlatformCall<String, String?>(fontKey).also(paths::add).await(ignoreCancellation = true)
        }
        val fixture = LyricsShareLifecycleFixture(this, library)
        try { with(fixture) {
            preferences.selectedLyricsShareFontKey.value = "imported:abcdef"
            dispatch(PlayerIntent.BeginLyricsShare)
            drain()
            val oldId = store.state.value.lyricsShareInitializationRequestId
            dispatch(PlayerIntent.InvalidateLyricsShareFontCache)
            drain()
            val currentId = assertNotNull(store.state.value.lyricsShareInitializationRequestId)
            assertNotEquals(oldId, currentId)
            assertEquals(2, paths.size)
            paths.first().succeed(null)
            drain()
            assertTrue(store.state.value.isLyricsShareInitializing)
            assertEquals(currentId, store.state.value.lyricsShareInitializationRequestId)
            assertTrue(preferences.calls.isEmpty())
            paths.last().succeed("/tmp/abcdef__Font.ttf")
            drain()
            assertFalse(store.state.value.isLyricsShareInitializing)
            assertNull(store.state.value.lyricsShareInitializationRequestId)
            assertEquals("imported:abcdef", store.state.value.selectedLyricsShareFontKey)
            assertTrue(store.state.value.hasFreshSharePreview)
        } } finally {
            paths.forEach { it.succeed(null) }
            fixture.dispose()
        }
    }
}
