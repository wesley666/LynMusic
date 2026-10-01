package top.iwesley.lyn.music.platform

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.player.resolveDesktopLyricsOverlayText

@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class AndroidDesktopLyricsLanguageRefreshTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun pausedLoadingTextRefreshesFromLatestSnapshotAndStopsAfterCancellation() = runTest {
        val savedLanguage = AppLanguageRuntime.appLanguage.value
        AppLanguageRuntime.update(AppLanguage.English)
        val track = Track("track", "source", "原文", mediaLocator = "file:///song.mp3", relativePath = "song.mp3")
        val paused = PlaybackSnapshot(queue = listOf(track), currentIndex = 0, isPlaying = false, positionMs = 1250L)
        var latest: PlaybackSnapshot? = paused
        val rendered = mutableListOf<Pair<PlaybackSnapshot, UiText?>>()
        val renderedEvents = Channel<Unit>(Channel.UNLIMITED)
        val descriptions = mutableListOf<String>()
        var lyrics: LyricsDocument? = null
        var loading = true
        val job = backgroundScope.observeDesktopLyricsLanguage(
            latestSnapshot = { latest },
            updateCloseDescription = descriptions::add,
            render = {
                rendered += it to resolveDesktopLyricsOverlayText(lyrics, -1, loading)
                renderedEvents.trySend(Unit)
            },
        )
        try {
            runCurrent(); renderedEvents.receive()
            assertEquals("Preparing lyrics", resolveUiText(checkNotNull(rendered.last().second), AppLanguageRuntime.effectiveLanguage.value))
            AppLanguageRuntime.update(AppLanguage.SimplifiedChinese); runCurrent(); renderedEvents.receive()
            assertEquals("正在准备歌词", resolveUiText(checkNotNull(rendered.last().second), AppLanguageRuntime.effectiveLanguage.value))
            AppLanguageRuntime.update(AppLanguage.TraditionalChinese); runCurrent(); renderedEvents.receive()
            assertEquals("正在準備歌詞", resolveUiText(checkNotNull(rendered.last().second), AppLanguageRuntime.effectiveLanguage.value))
            assertEquals("關閉桌面歌詞", descriptions.last())
            latest = paused.copy(positionMs = 2468L)
            AppLanguageRuntime.update(AppLanguage.English); runCurrent(); renderedEvents.receive()
            assertSame(latest, rendered.last().first)
            assertEquals("Close desktop lyrics", descriptions.last())
            assertEquals(false, rendered.last().first.isPlaying)
            assertSame(track, rendered.last().first.currentTrack)
            val beforeRoundTrip = rendered.size
            AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
            AppLanguageRuntime.update(AppLanguage.English)
            runCurrent(); renderedEvents.receive()
            assertEquals(beforeRoundTrip + 1, rendered.size)
            assertEquals("Preparing lyrics", resolveUiText(checkNotNull(rendered.last().second), AppLanguageRuntime.effectiveLanguage.value))
            lyrics = LyricsDocument(listOf(LyricsLine(null, "歌词原文 %1\$s")), sourceId = "source", rawPayload = "歌词原文 %1\$s")
            loading = false
            AppLanguageRuntime.update(AppLanguage.SimplifiedChinese); runCurrent(); renderedEvents.receive()
            assertEquals("歌词原文 %1\$s", resolveUiText(checkNotNull(rendered.last().second), AppLanguageRuntime.effectiveLanguage.value))
            latest = null
            val count = rendered.size
            AppLanguageRuntime.update(AppLanguage.TraditionalChinese); runCurrent()
            assertEquals(count, rendered.size)
            job.cancel(); runCurrent()
            val descriptionCount = descriptions.size
            AppLanguageRuntime.update(AppLanguage.English); runCurrent()
            assertEquals(descriptionCount, descriptions.size)
        } finally {
            job.cancel()
            AppLanguageRuntime.update(savedLanguage)
        }
    }
}
