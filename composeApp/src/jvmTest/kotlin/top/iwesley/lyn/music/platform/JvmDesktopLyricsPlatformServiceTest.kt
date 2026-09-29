package top.iwesley.lyn.music.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS
import top.iwesley.lyn.music.core.model.DesktopLyricsPosition
import top.iwesley.lyn.music.core.model.DesktopLyricsPositionPreferencesStore

@OptIn(ExperimentalCoroutinesApi::class)
class JvmDesktopLyricsPlatformServiceTest {
    @Test
    fun `jvm service consumes app lyrics updates`() {
        val service = JvmDesktopLyricsPlatformService(RecordingDesktopLyricsWindowAdapter())

        assertTrue(service.consumesAppLyricsUpdates)
    }

    @Test
    fun `update and hide delegate to window adapter`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val service = JvmDesktopLyricsPlatformService(adapter)

        service.updateLyrics("当前歌词")
        service.hideLyrics()
        service.release()

        assertEquals(listOf("当前歌词"), adapter.shownTexts)
        assertEquals(1, adapter.hideCalls)
        assertEquals(1, adapter.releaseCalls)
    }

    @Test
    fun `disabling desktop lyrics hides window`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val service = JvmDesktopLyricsPlatformService(adapter)

        service.setDesktopLyricsEnabled(false)

        assertEquals(1, adapter.hideCalls)
    }

    @Test
    fun `close request from window is exposed as service flow`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val service = JvmDesktopLyricsPlatformService(adapter)
        val request = async(start = CoroutineStart.UNDISPATCHED) { service.closeRequests.first() }

        adapter.requestClose()

        assertEquals(Unit, request.await())
    }

    @Test
    fun `saved position is restored and window changes persist after five seconds`() = runTest {
        val initialPosition = DesktopLyricsPosition(0.25f, 0.75f, "primary")
        val changedPosition = DesktopLyricsPosition(0.6f, 0.4f, "secondary")
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(initialPosition)

        val service = JvmDesktopLyricsPlatformService(
            window = adapter,
            positionPreferencesStore = preferences,
            persistenceScope = backgroundScope,
        )

        assertEquals(initialPosition, adapter.restoredPosition)
        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(changedPosition)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS - 1L)
        runCurrent()
        assertTrue(preferences.savedPositions.isEmpty())

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(changedPosition), preferences.savedPositions)
        assertEquals(changedPosition, preferences.desktopLyricsPosition.value)
    }

    @Test
    fun `disabling or releasing discards an unstable position`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(null)
        val service = JvmDesktopLyricsPlatformService(
            window = adapter,
            positionPreferencesStore = preferences,
            persistenceScope = backgroundScope,
        )

        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(DesktopLyricsPosition(0.2f, 0.8f))
        runCurrent()
        service.setDesktopLyricsEnabled(false)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertTrue(preferences.savedPositions.isEmpty())

        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(DesktopLyricsPosition(0.7f, 0.3f))
        runCurrent()
        service.release()
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertTrue(preferences.savedPositions.isEmpty())
    }

    @Test
    fun `temporary hide does not discard a pending position`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(null)
        val service = JvmDesktopLyricsPlatformService(
            window = adapter,
            positionPreferencesStore = preferences,
            persistenceScope = backgroundScope,
        )
        val position = DesktopLyricsPosition(0.35f, 0.65f)

        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(position)
        runCurrent()
        service.hideLyrics()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()

        assertEquals(listOf(position), preferences.savedPositions)
    }

    @Test
    fun `window close immediately discards a pending position`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(null)
        val service = JvmDesktopLyricsPlatformService(
            window = adapter,
            positionPreferencesStore = preferences,
            persistenceScope = backgroundScope,
        )

        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(DesktopLyricsPosition(0.45f, 0.55f))
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS - 1L)
        adapter.requestClose()
        service.setDesktopLyricsEnabled(true)
        adapter.finishPositionChange(DesktopLyricsPosition(0.8f, 0.2f))
        advanceTimeBy(1L)
        runCurrent()

        assertTrue(preferences.savedPositions.isEmpty())
    }

    @Test
    fun `position callbacks are ignored while disabled and after release`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(null)
        val service = JvmDesktopLyricsPlatformService(
            window = adapter,
            positionPreferencesStore = preferences,
            persistenceScope = backgroundScope,
        )

        adapter.startPositionChange()
        adapter.finishPositionChange(DesktopLyricsPosition(0.1f, 0.9f))
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertTrue(preferences.savedPositions.isEmpty())

        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(DesktopLyricsPosition(0.4f, 0.6f))
        service.setDesktopLyricsEnabled(false)
        adapter.finishPositionChange(DesktopLyricsPosition(0.6f, 0.4f))
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertTrue(preferences.savedPositions.isEmpty())

        val reenabledPosition = DesktopLyricsPosition(0.7f, 0.3f)
        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(reenabledPosition)
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertEquals(listOf(reenabledPosition), preferences.savedPositions)

        service.release()
        adapter.finishPositionChange(DesktopLyricsPosition(0.9f, 0.1f))
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertEquals(listOf(reenabledPosition), preferences.savedPositions)
    }

    @Test
    fun `ordinary click without movement does not open a position change session`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(null)
        val service = JvmDesktopLyricsPlatformService(
            window = adapter,
            positionPreferencesStore = preferences,
            persistenceScope = backgroundScope,
        )

        service.setDesktopLyricsEnabled(true)
        adapter.finishPositionChange(null)
        adapter.finishPositionChange(DesktopLyricsPosition(0.8f, 0.2f))
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()

        assertTrue(preferences.savedPositions.isEmpty())
    }

    @Test
    fun `click after three seconds preserves the original save deadline`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(null)
        val service = JvmDesktopLyricsPlatformService(adapter, preferences, backgroundScope)
        val position = DesktopLyricsPosition(0.3f, 0.7f)
        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(position)
        runCurrent()

        advanceTimeBy(3_000L)
        // Press/release without movement never emits the started callback.
        adapter.finishPositionChange(null)
        advanceTimeBy(1_999L)
        runCurrent()
        assertTrue(preferences.savedPositions.isEmpty())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(position), preferences.savedPositions)
    }

    @Test
    fun `holding without movement does not postpone a pending save`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(null)
        val service = JvmDesktopLyricsPlatformService(adapter, preferences, backgroundScope)
        val position = DesktopLyricsPosition(0.3f, 0.7f)
        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(position)
        runCurrent()

        advanceTimeBy(3_000L)
        // A stationary press emits no position callback, even across the save deadline.
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(listOf(position), preferences.savedPositions)
        advanceTimeBy(1_000L)
        adapter.finishPositionChange(null)
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertEquals(listOf(position), preferences.savedPositions)
    }

    @Test
    fun `new movement cancels pending save until five seconds after release`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(null)
        val service = JvmDesktopLyricsPlatformService(adapter, preferences, backgroundScope)
        val latest = DesktopLyricsPosition(0.7f, 0.3f)
        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        adapter.finishPositionChange(DesktopLyricsPosition(0.3f, 0.7f))
        runCurrent()

        advanceTimeBy(3_000L)
        adapter.startPositionChange()
        advanceTimeBy(6_000L)
        runCurrent()
        assertTrue(preferences.savedPositions.isEmpty())
        adapter.finishPositionChange(latest)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS - 1L)
        runCurrent()
        assertTrue(preferences.savedPositions.isEmpty())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(latest), preferences.savedPositions)
    }

    @Test
    fun `stale finish after reenable is ignored until a new position change starts`() = runTest {
        val adapter = RecordingDesktopLyricsWindowAdapter()
        val preferences = RecordingDesktopLyricsPositionPreferences(null)
        val service = JvmDesktopLyricsPlatformService(
            window = adapter,
            positionPreferencesStore = preferences,
            persistenceScope = backgroundScope,
        )
        val stalePosition = DesktopLyricsPosition(0.2f, 0.8f)
        val freshPosition = DesktopLyricsPosition(0.65f, 0.35f)

        service.setDesktopLyricsEnabled(true)
        adapter.startPositionChange()
        service.setDesktopLyricsEnabled(false)
        service.setDesktopLyricsEnabled(true)
        adapter.finishPositionChange(stalePosition)
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertTrue(preferences.savedPositions.isEmpty())

        adapter.startPositionChange()
        adapter.finishPositionChange(freshPosition)
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()

        assertEquals(listOf(freshPosition), preferences.savedPositions)
    }
}

private class RecordingDesktopLyricsWindowAdapter : JvmDesktopLyricsOverlayWindowAdapter {
    val shownTexts = mutableListOf<String>()
    var hideCalls = 0
    var releaseCalls = 0
    private var closeRequestHandler: (() -> Unit)? = null
    private var positionChangeStartedHandler: (() -> Unit)? = null
    private var positionChangeFinishedHandler: ((DesktopLyricsPosition?) -> Unit)? = null
    var restoredPosition: DesktopLyricsPosition? = null

    override fun showText(text: String) {
        shownTexts += text
    }

    override fun hide() {
        hideCalls += 1
    }

    override fun release() {
        releaseCalls += 1
    }

    override fun setCloseRequestHandler(handler: () -> Unit) {
        closeRequestHandler = handler
    }

    override fun restorePosition(position: DesktopLyricsPosition?) {
        restoredPosition = position
    }

    override fun setPositionChangeStartedHandler(handler: () -> Unit) {
        positionChangeStartedHandler = handler
    }

    override fun setPositionChangeFinishedHandler(handler: (DesktopLyricsPosition?) -> Unit) {
        positionChangeFinishedHandler = handler
    }

    fun requestClose() {
        closeRequestHandler?.invoke()
    }

    fun startPositionChange() {
        positionChangeStartedHandler?.invoke()
    }

    fun finishPositionChange(position: DesktopLyricsPosition?) {
        positionChangeFinishedHandler?.invoke(position)
    }
}

private class RecordingDesktopLyricsPositionPreferences(
    initialPosition: DesktopLyricsPosition?,
) : DesktopLyricsPositionPreferencesStore {
    private val mutablePosition = MutableStateFlow(initialPosition)
    val savedPositions = mutableListOf<DesktopLyricsPosition>()

    override val desktopLyricsPosition: StateFlow<DesktopLyricsPosition?> = mutablePosition.asStateFlow()

    override suspend fun setDesktopLyricsPosition(position: DesktopLyricsPosition) {
        savedPositions += position
        mutablePosition.value = position
    }
}
