package top.iwesley.lyn.music.tv

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.android.controller.ActivityController
import java.time.Duration
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.*
import top.iwesley.lyn.music.feature.player.*

/** Exercises the real Compose owner across Android Activity recreation, with one process Store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerPresentationLifecycleTest {
    @Test
    fun openQueueSurvivesActivityRecreation() = withPlayer {
        activity.presentation.showQueue(true)
        recreate()

        assertTrue(activity.presentation.isQueueVisible)
        assertTrue(store.state.value.snapshot.isPlaying)
        assertEquals(12_000L, store.state.value.snapshot.positionMs)
        assertEquals(listOf(track), store.state.value.snapshot.queue)
    }

    @Test
    fun searchFormAndResultsSurviveActivityRecreation() = withPlayer {
        activity.presentation.openSearch()
        store.dispatch(PlayerIntent.ManualLyricsTitleChanged("Edited title"))
        store.dispatch(PlayerIntent.ManualLyricsArtistChanged("Edited artist"))
        store.dispatch(PlayerIntent.ManualLyricsAlbumChanged("Edited album"))
        store.dispatch(PlayerIntent.SearchManualLyrics)
        drain()
        val session = store.state.value.manualLyricsSearchSession
        assertEquals(listOf(candidate), store.state.value.manualLyricsResults)

        recreate()

        assertTrue(activity.presentation.isManualLyricsSearchVisible)
        assertEquals(session, store.state.value.manualLyricsSearchSession)
        assertEquals("Edited title", store.state.value.manualLyricsTitle)
        assertEquals("Edited artist", store.state.value.manualLyricsArtistName)
        assertEquals("Edited album", store.state.value.manualLyricsAlbumTitle)
        assertEquals(listOf(candidate), store.state.value.manualLyricsResults)
        assertTrue(store.state.value.hasManualLyricsSearchResult)
        assertEquals(1, lyrics.searchCalls)
    }

    @Test
    fun pendingSearchCompletesAfterActivityRecreation() = withPlayer {
        lyrics.searchGate = CompletableDeferred()
        activity.presentation.openSearch()
        store.dispatch(PlayerIntent.SearchManualLyrics)
        drain()
        assertTrue(store.state.value.isManualLyricsSearchLoading)
        val session = store.state.value.manualLyricsSearchSession

        recreate()

        assertEquals(session, store.state.value.manualLyricsSearchSession)
        assertFalse(lyrics.searchCancelled)
        lyrics.searchGate!!.complete(Unit)
        drain()
        assertTrue(activity.presentation.isManualLyricsSearchVisible)
        assertEquals(listOf(candidate), store.state.value.manualLyricsResults)
        assertEquals(1, lyrics.searchCalls)
    }

    @Test
    fun pendingLyricsApplicationCompletesAfterActivityRecreation() = withPlayer {
        lyrics.applyGate = CompletableDeferred()
        activity.presentation.openSearch()
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate, LyricsSearchApplyMode.LYRICS_ONLY))
        drain()
        assertEquals(1, lyrics.applyCalls)

        recreate()

        assertFalse(lyrics.applyCancelled)
        lyrics.applyGate!!.complete(Unit)
        drain()
        assertEquals(candidate.document, store.state.value.lyrics)
        assertNull(store.state.value.manualLyricsSearchSession)
        assertFalse(activity.presentation.isManualLyricsSearchVisible)
        assertEquals(1, lyrics.applyCalls)
    }

    @Test
    fun finishingActivityCancelsPendingSearchWithoutStoppingPlayback() = withPlayer {
        lyrics.searchGate = CompletableDeferred()
        activity.presentation.openSearch()
        store.dispatch(PlayerIntent.SearchManualLyrics)
        drain()

        finishPlayer()

        assertTrue(lyrics.searchCancelled)
        assertNull(store.state.value.manualLyricsSearchSession)
        assertFalse(store.state.value.isManualLyricsSearchLoading)
        assertTrue(store.state.value.snapshot.isPlaying)
        // The Application-owned Store must remain usable after the player Activity ends.
        playback.snapshot.value = PlaybackSnapshot(queue = listOf(track.copy(id = "next")), currentIndex = 0)
        drain()
        assertEquals("next", store.state.value.snapshot.currentTrack?.id)
    }

    @Test
    fun finishingActivityKeepsCommittedLyricsApplication() = withPlayer {
        lyrics.applyGate = CompletableDeferred()
        activity.presentation.openSearch()
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate, LyricsSearchApplyMode.LYRICS_ONLY))
        drain()

        finishPlayer()

        assertFalse(lyrics.applyCancelled)
        lyrics.applyGate!!.complete(Unit)
        drain()
        assertEquals(candidate.document, lyrics.appliedDocument)
        assertNull(store.state.value.manualLyricsSearchSession)
        assertEquals(1, store.state.value.operationNotices.size)
    }

    @Test
    fun explicitlyClosingSearchAfterRecreationStillCancelsItsTask() = withPlayer {
        lyrics.searchGate = CompletableDeferred()
        activity.presentation.openSearch()
        store.dispatch(PlayerIntent.SearchManualLyrics)
        drain()
        recreate()

        activity.presentation.closeSearch()
        drain()

        assertTrue(lyrics.searchCancelled)
        assertNull(store.state.value.manualLyricsSearchSession)
        assertFalse(activity.presentation.isManualLyricsSearchVisible)
    }

    @Test
    fun trackChangeBetweenCompositionsInvalidatesSearchAndCancelsItsTask() = withPlayer {
        lyrics.searchGate = CompletableDeferred()
        activity.presentation.openSearch()
        store.dispatch(PlayerIntent.SearchManualLyrics)
        drain()
        activity.unmountPlayer()
        drain()
        assertFalse(lyrics.searchCancelled)

        playback.snapshot.value = PlaybackSnapshot(queue = listOf(track.copy(id = "next")), currentIndex = 0)
        drain()

        assertTrue(lyrics.searchCancelled)
        assertFalse(activity.presentation.isManualLyricsSearchVisible)
        activity.mountPlayer()
        drain()
        assertNull(store.state.value.manualLyricsSearchSession)
        assertFalse(activity.presentation.isManualLyricsSearchVisible)
    }

    @Test
    fun completedLyricsApplicationBetweenCompositionsDoesNotReopenSearch() = withPlayer {
        lyrics.applyGate = CompletableDeferred()
        activity.presentation.openSearch()
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate, LyricsSearchApplyMode.LYRICS_ONLY))
        drain()
        activity.unmountPlayer()
        drain()

        lyrics.applyGate!!.complete(Unit)
        drain()

        assertEquals(candidate.document, store.state.value.lyrics)
        assertFalse(activity.presentation.isManualLyricsSearchVisible)
        activity.mountPlayer()
        drain()
        assertFalse(activity.presentation.isManualLyricsSearchVisible)
    }

    @Test
    fun transientPlaybackBetweenCompositionsStillClosesQueue() = withPlayer {
        activity.presentation.showQueue(true)
        activity.unmountPlayer()
        drain()
        assertTrue(activity.presentation.isQueueVisible)

        store.dispatch(PlayerIntent.PlayTransientTracks(listOf(track.copy(id = "next")), startIndex = 0))
        drain()

        assertFalse(activity.presentation.isQueueVisible)
        activity.mountPlayer()
        drain()
        assertFalse(activity.presentation.isQueueVisible)
    }

    @Test
    fun clearingPlaybackBetweenCompositionsClosesQueue() = withPlayer {
        activity.presentation.showQueue(true)
        activity.unmountPlayer()
        drain()

        playback.snapshot.value = PlaybackSnapshot()
        drain()

        assertFalse(activity.presentation.isQueueVisible)
        activity.mountPlayer()
        drain()
        assertFalse(activity.presentation.isQueueVisible)
    }

    @Test
    fun differentActivitiesKeepUiSeparateAndOldActivityCannotCancelNewSearch() = withPlayer {
        activity.presentation.showQueue(true)
        activity.presentation.openSearch()
        val second = Robolectric.buildActivity(PresentationTestActivity::class.java).setup().visible()
        try {
            drain()
            assertNotSame(activity.presentation, second.get().presentation)
            assertFalse(second.get().presentation.isQueueVisible)
            assertFalse(second.get().presentation.isManualLyricsSearchVisible)
            second.get().presentation.openSearch()
            // Finish before collectors run: the session identity guard must protect the new owner.
            val session = store.state.value.manualLyricsSearchSession

            finishPlayer()

            assertEquals(session, store.state.value.manualLyricsSearchSession)
            assertTrue(second.get().presentation.isManualLyricsSearchVisible)
        } finally {
            second.get().finish()
            second.pause().stop().destroy()
            drain()
        }
        assertNull(store.state.value.manualLyricsSearchSession)
    }

    private fun withPlayer(block: Fixture.() -> Unit) = runTest {
        val fixture = Fixture(this)
        try {
            fixture.block()
        } finally {
            fixture.close()
        }
    }

    private class Fixture(private val test: TestScope) : AutoCloseable {
        val track = Track("song", "local", "Song", mediaLocator = "", relativePath = "")
        val candidate = LyricsSearchCandidate("fixture", "Fixture", LyricsDocument(
            listOf(LyricsLine(0, "Selected lyrics")), sourceId = "fixture", rawPayload = ""))
        val playback = LifecyclePlaybackRepository(PlaybackSnapshot(
            queue = listOf(track), currentIndex = 0, isPlaying = true, positionMs = 12_000L))
        val lyrics = LifecycleLyricsRepository(candidate)
        private val storeScope = CoroutineScope(StandardTestDispatcher(test.testScheduler) + SupervisorJob())
        val store = PlayerStore(playback, lyrics, storeScope)
        private val controller: ActivityController<PresentationTestActivity>
        private var destroyed = false
        val activity: PresentationTestActivity get() = controller.get()

        init {
            PresentationTestActivity.store = store
            test.testScheduler.runCurrent()
            controller = Robolectric.buildActivity(PresentationTestActivity::class.java).setup().visible()
            drain()
        }

        fun recreate() {
            val previous = activity
            val presentation = previous.presentation
            controller.recreate()
            drain()
            assertNotSame(previous, activity)
            assertSame(presentation, activity.presentation)
        }

        fun drain() {
            repeat(3) {
                test.testScheduler.runCurrent()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            }
        }

        fun finishPlayer() {
            if (destroyed) return
            activity.finish()
            controller.pause().stop().destroy()
            destroyed = true
            drain()
        }

        override fun close() {
            finishPlayer()
            storeScope.cancel()
            test.testScheduler.runCurrent()
            PresentationTestActivity.store = null
        }
    }
}

/** Minimal host uses exactly the same presentation binding as TvPlayerApp. */
class PresentationTestActivity : ComponentActivity() {
    internal lateinit var presentation: PlayerPresentation
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mountPlayer()
    }

    internal fun mountPlayer() {
        val playerStore = checkNotNull(store)
        setContent {
            presentation = rememberPlayerPresentation(playerStore)
        }
    }

    internal fun unmountPlayer() = setContent {}

    companion object {
        internal var store: PlayerStore? = null
    }
}

private class LifecycleLyricsRepository(private val candidate: LyricsSearchCandidate) : LyricsRepository {
    var searchGate: CompletableDeferred<Unit>? = null
    var applyGate: CompletableDeferred<Unit>? = null
    var searchCalls = 0
    var applyCalls = 0
    var searchCancelled = false
    var applyCancelled = false
    var appliedDocument: LyricsDocument? = null
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? = null
    override suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean): List<LyricsSearchCandidate> {
        searchCalls++
        try {
            searchGate?.await()
            return listOf(candidate)
        } catch (error: kotlinx.coroutines.CancellationException) {
            searchCancelled = true
            throw error
        }
    }

    override suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate, mode: LyricsSearchApplyMode): AppliedLyricsResult {
        applyCalls++
        try {
            applyGate?.await()
            appliedDocument = candidate.document
            return AppliedLyricsResult(document = candidate.document)
        } catch (error: kotlinx.coroutines.CancellationException) {
            applyCancelled = true
            throw error
        }
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()
    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult = error("Not used")
    override suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate, mode: LyricsSearchApplyMode): AppliedLyricsResult = error("Not used")
}

private class LifecyclePlaybackRepository(initial: PlaybackSnapshot) : PlaybackRepository {
    override val snapshot = MutableStateFlow(initial)
    override suspend fun hydratePersistedQueueIfNeeded() = PlaybackHydrationResult.ExistingPlayback
    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) {
        snapshot.value = snapshot.value.copy(queue = tracks, currentIndex = startIndex)
    }
    override suspend fun playTransientTracks(tracks: List<Track>, startIndex: Int) = playTracks(tracks, startIndex)
    override suspend fun prepareExternalPlaybackQueue(tracks: List<Track>, startIndex: Int): PlaybackSnapshot? = null
    override suspend fun playQueueIndex(index: Int) { snapshot.value = snapshot.value.copy(currentIndex = index) }
    override suspend fun togglePlayPause() = Unit
    override suspend fun pause() = Unit
    override suspend fun skipNext() = Unit
    override suspend fun skipPrevious() = Unit
    override suspend fun seekTo(positionMs: Long) = Unit
    override suspend fun setVolume(volume: Float) = Unit
    override suspend fun cycleMode() = Unit
    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?, expectedTrackId: String?) = Unit
    override suspend fun close() = Unit
}
