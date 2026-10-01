package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.DEFAULT_PLAYBACK_VOLUME
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.EXTERNAL_OPEN_SOURCE_ID
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlaybackAudioFormat
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.PlaybackStatsReporter
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.playbackErrorText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.buildEmbySongLocator
import top.iwesley.lyn.music.core.model.buildExternalOpenTrackId
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRepositoriesTest {

    @Test
    fun gatewayUiDescriptionsPropagateAndStaleDescriptionsAreClearedOnTrackSwitch() = runTest {
        val database = createTestDatabase()
        val gateway = BlockingPlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(database, gateway, FakePlaybackPreferencesStore(), scope)
        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()
            val text = uiText(Res.string.playback_vlc_unavailable)
            gateway.updateState { it.copy(errorMessage = "仅用于诊断", errorText = text, errorRevision = it.errorRevision + 1) }
            advanceUntilIdle()
            assertEquals(text, repository.snapshot.value.errorText)
            assertEquals("仅用于诊断", repository.snapshot.value.errorMessage)
            val loadGate = CompletableDeferred<Unit>()
            gateway.nextLoadGate = loadGate
            val switch = launch { repository.playTracks(sampleTracks(), startIndex = 1) }
            advanceUntilIdle()
            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)
            assertNull(repository.snapshot.value.errorMessage)
            assertNull(repository.snapshot.value.errorText)
            loadGate.complete(Unit)
            switch.join()
            advanceUntilIdle()
            assertNull(repository.snapshot.value.errorText)
            gateway.updateState { it.copy(errorMessage = null, errorText = text, errorRevision = it.errorRevision + 1) }
            advanceUntilIdle()
            assertEquals(text, repository.snapshot.value.errorText)
            assertEquals("VLC was not detected. Install VLC or choose its path in Settings.",
                resolveUiText(checkNotNull(repository.snapshot.value.playbackErrorText()), AppLanguage.English))
            gateway.updateState { it.copy(errorMessage = null, errorText = null) }
            advanceUntilIdle()
            assertNull(repository.snapshot.value.playbackErrorText())
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback stats threshold uses half duration capped at four minutes`() = runTest {
        assertEquals(90_000L, playbackStatsSubmissionThresholdMs(180_000L))
        assertEquals(240_000L, playbackStatsSubmissionThresholdMs(900_000L))
        assertEquals(240_000L, playbackStatsSubmissionThresholdMs(0L))
    }

    @Test
    fun `playback queue json fallback source ids come from remote locators`() = runTest {
        val localTracks = sampleTracks(1_200)
        val navidromeTrack = sampleNavidromeTrack(
            id = "nav-track-1",
            sourceId = "nav-source",
            songId = "song-1",
        )
        val embyTrack = sampleTrack("emby-track-1", "Emby Song").copy(
            sourceId = "emby-source",
            mediaLocator = buildEmbySongLocator("emby-source", "item-1"),
        )

        assertEquals(emptySet(), remotePlaybackSourceIds(localTracks, emptyList()))
        assertEquals(setOf("nav-source"), remotePlaybackSourceIds(listOf(localTracks.first(), navidromeTrack), emptyList()))
        assertEquals(setOf("emby-source"), remotePlaybackSourceIds(localTracks.take(3), listOf(embyTrack)))
    }

    @Test
    fun `playback snapshot records current navidrome audio quality from gateway`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway().apply {
            nextNavidromeAudioQuality = NavidromeAudioQuality.Kbps192
        }
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            repository.playTracks(listOf(sampleNavidromeTrack()), startIndex = 0)
            advanceUntilIdle()

            assertEquals(NavidromeAudioQuality.Kbps192, repository.snapshot.value.currentNavidromeAudioQuality)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback snapshot records current audio format from gateway`() = runTest {
        val database = createTestDatabase()
        val audioFormat = PlaybackAudioFormat(
            bitRateBps = 320_000,
            samplingRateHz = 44_100,
            channelCount = 2,
        )
        val gateway = FakePlaybackGateway().apply {
            nextPlaybackAudioFormat = audioFormat
        }
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            repository.playTracks(listOf(sampleTrack("track-1", "First Song")), startIndex = 0)
            advanceUntilIdle()

            assertEquals(audioFormat, repository.snapshot.value.currentPlaybackAudioFormat)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback stats reports now playing once when navidrome playback starts`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val reporter = FakePlaybackStatsReporter()
        var nowMs = 1_000L
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
            playbackStatsReporter = reporter,
            currentTimeMillis = { nowMs },
        )

        try {
            repository.playTracks(listOf(sampleNavidromeTrack()), startIndex = 0)
            advanceUntilIdle()

            assertEquals(listOf(PlaybackStatsCall("now", "nav-track-1", 1_000L)), reporter.calls)

            nowMs += 1_000L
            gateway.updateState { it.copy(isPlaying = true, positionMs = 1_000L) }
            advanceUntilIdle()

            assertEquals(listOf(PlaybackStatsCall("now", "nav-track-1", 1_000L)), reporter.calls)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback stats submits once after reaching playback threshold`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val reporter = FakePlaybackStatsReporter()
        var nowMs = 0L
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
            playbackStatsReporter = reporter,
            currentTimeMillis = { nowMs },
        )

        try {
            repository.playTracks(listOf(sampleNavidromeTrack()), startIndex = 0)
            advanceUntilIdle()

            nowMs = 90_000L
            gateway.updateState { it.copy(isPlaying = true, positionMs = 90_000L) }
            advanceUntilIdle()

            assertEquals(
                listOf(
                    PlaybackStatsCall("now", "nav-track-1", 0L),
                    PlaybackStatsCall("submit", "nav-track-1", 90_000L),
                ),
                reporter.calls,
            )

            nowMs = 120_000L
            gateway.updateState { it.copy(isPlaying = true, positionMs = 120_000L) }
            advanceUntilIdle()

            assertEquals(2, reporter.calls.size)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `close flushes final playback stats after shared scope is cancelled`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val reporter = FakePlaybackStatsReporter()
        var nowMs = 0L
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
            playbackStatsReporter = reporter,
            currentTimeMillis = { nowMs },
        )

        try {
            repository.playTracks(listOf(sampleNavidromeTrack()), startIndex = 0)
            advanceUntilIdle()

            nowMs = 90_000L
            scope.cancel()
            repository.close()

            assertEquals(
                listOf(
                    PlaybackStatsCall("now", "nav-track-1", 0L),
                    PlaybackStatsCall("submit", "nav-track-1", 90_000L),
                ),
                reporter.calls,
            )
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback stats does not accumulate while paused`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val reporter = FakePlaybackStatsReporter()
        var nowMs = 0L
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
            playbackStatsReporter = reporter,
            currentTimeMillis = { nowMs },
        )

        try {
            repository.playTracks(listOf(sampleNavidromeTrack()), startIndex = 0)
            advanceUntilIdle()

            nowMs = 30_000L
            gateway.updateState { it.copy(isPlaying = true, positionMs = 30_000L) }
            advanceUntilIdle()

            repository.pause()
            advanceUntilIdle()

            nowMs = 150_000L
            gateway.updateState { it.copy(isPlaying = false, positionMs = 30_000L) }
            advanceUntilIdle()

            assertEquals(listOf(PlaybackStatsCall("now", "nav-track-1", 0L)), reporter.calls)

            repository.togglePlayPause()
            advanceUntilIdle()

            nowMs = 210_000L
            gateway.updateState { it.copy(isPlaying = true, positionMs = 90_000L) }
            advanceUntilIdle()

            assertEquals(
                listOf(
                    PlaybackStatsCall("now", "nav-track-1", 0L),
                    PlaybackStatsCall("submit", "nav-track-1", 210_000L),
                ),
                reporter.calls,
            )
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback stats does not submit when skipped before threshold`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val reporter = FakePlaybackStatsReporter()
        var nowMs = 0L
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
            playbackStatsReporter = reporter,
            currentTimeMillis = { nowMs },
        )

        try {
            repository.playTracks(
                listOf(
                    sampleNavidromeTrack(),
                    sampleNavidromeTrack("nav-track-2", "song-2"),
                ),
                startIndex = 0,
            )
            advanceUntilIdle()

            nowMs = 30_000L
            gateway.updateState { it.copy(isPlaying = true, positionMs = 30_000L) }
            advanceUntilIdle()

            repository.skipNext()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    PlaybackStatsCall("now", "nav-track-1", 0L),
                    PlaybackStatsCall("now", "nav-track-2", 30_000L),
                ),
                reporter.calls,
            )
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback stats starts a new session when same track is loaded again`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val reporter = FakePlaybackStatsReporter()
        var nowMs = 0L
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
            playbackStatsReporter = reporter,
            currentTimeMillis = { nowMs },
        )

        try {
            repository.playTracks(listOf(sampleNavidromeTrack()), startIndex = 0)
            advanceUntilIdle()

            nowMs = 90_000L
            gateway.updateState { it.copy(isPlaying = true, positionMs = 90_000L) }
            advanceUntilIdle()

            repository.playQueueIndex(0)
            advanceUntilIdle()

            nowMs = 180_000L
            gateway.updateState { it.copy(isPlaying = true, positionMs = 90_000L) }
            advanceUntilIdle()

            assertEquals(
                listOf(
                    PlaybackStatsCall("now", "nav-track-1", 0L),
                    PlaybackStatsCall("submit", "nav-track-1", 90_000L),
                    PlaybackStatsCall("now", "nav-track-1", 90_000L),
                    PlaybackStatsCall("submit", "nav-track-1", 180_000L),
                ),
                reporter.calls,
            )
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback snapshot records seek capability from gateway`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()

            assertEquals(true, repository.snapshot.value.canSeek)

            gateway.updateState { it.copy(canSeek = false) }
            advanceUntilIdle()

            assertEquals(false, repository.snapshot.value.canSeek)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback snapshot keeps track duration when gateway reports unknown duration`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            repository.playTracks(listOf(sampleNavidromeTrack()), startIndex = 0)
            advanceUntilIdle()

            gateway.updateState { it.copy(durationMs = 0L) }
            advanceUntilIdle()

            assertEquals(180_000L, repository.snapshot.value.durationMs)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `playback snapshot clears navidrome audio quality for non navidrome track`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway().apply {
            nextNavidromeAudioQuality = NavidromeAudioQuality.Kbps192
        }
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            repository.playTracks(listOf(sampleNavidromeTrack()), startIndex = 0)
            advanceUntilIdle()
            gateway.nextNavidromeAudioQuality = null
            repository.playTracks(listOf(sampleTrack("track-local", "Local Song")), startIndex = 0)
            advanceUntilIdle()

            assertEquals(null, repository.snapshot.value.currentNavidromeAudioQuality)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `natural completion wraps to first track in order mode`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 2)
            advanceUntilIdle()
            val loadCountBeforeCompletion = gateway.loadCalls.size

            gateway.emitCompletion()
            advanceUntilIdle()

            assertEquals(0, repository.snapshot.value.currentIndex)
            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals(true, repository.snapshot.value.isPlaying)
            assertEquals(loadCountBeforeCompletion + 1, gateway.loadCalls.size)
            assertEquals("track-1", gateway.loadCalls.last().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `manual previous wraps to last track at queue start in order mode`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()

            repository.skipPrevious()
            advanceUntilIdle()

            assertEquals(2, repository.snapshot.value.currentIndex)
            assertEquals("track-3", repository.snapshot.value.currentTrack?.id)
            assertEquals(emptyList(), gateway.seekCalls)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `manual previous seeks to current track start after five seconds in order mode`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()
            val loadCountBeforeSkip = gateway.loadCalls.size
            gateway.updateState { it.copy(positionMs = 6_000L, isPlaying = true, canSeek = true) }
            advanceUntilIdle()

            repository.skipPrevious()
            advanceUntilIdle()

            assertEquals(0, repository.snapshot.value.currentIndex)
            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals(0L, repository.snapshot.value.positionMs)
            assertEquals(listOf(0L), gateway.seekCalls)
            assertEquals(loadCountBeforeSkip, gateway.loadCalls.size)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `manual previous advances to previous track after five seconds when current track cannot seek`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()
            val loadCountBeforeSkip = gateway.loadCalls.size
            gateway.updateState { it.copy(positionMs = 6_000L, isPlaying = true, canSeek = false) }
            advanceUntilIdle()

            repository.skipPrevious()
            advanceUntilIdle()

            assertEquals(0, repository.snapshot.value.currentIndex)
            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals(emptyList(), gateway.seekCalls)
            assertEquals(loadCountBeforeSkip + 1, gateway.loadCalls.size)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `natural completion still advances in shuffle mode`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks().take(2), startIndex = 0)
            advanceUntilIdle()
            repository.cycleMode()
            advanceUntilIdle()

            gateway.emitCompletion()
            advanceUntilIdle()

            assertEquals(1, repository.snapshot.value.currentIndex)
            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `cycle to shuffle builds random queue with current track first without reloading`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val seed = 42
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            shuffleRandom = Random(seed),
        )
        val tracks = sampleTracks(5)
        val expectedQueue = listOf(tracks[2]) +
            tracks.filterIndexed { index, _ -> index != 2 }.shuffled(Random(seed))

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 2)
            advanceUntilIdle()
            gateway.updateState { it.copy(positionMs = 37_000L, isPlaying = true) }
            advanceUntilIdle()
            val loadCountBeforeShuffle = gateway.loadCalls.size

            repository.cycleMode()
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(PlaybackMode.SHUFFLE, snapshot.mode)
            assertEquals(0, snapshot.currentIndex)
            assertEquals("track-3", snapshot.currentTrack?.id)
            assertEquals(trackIds(expectedQueue), trackIds(snapshot.queue))
            assertEquals(trackIds(tracks), trackIds(snapshot.orderedQueue))
            assertEquals(37_000L, snapshot.positionMs)
            assertEquals(loadCountBeforeShuffle, gateway.loadCalls.size)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `shuffle next and natural completion follow generated queue order`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            shuffleRandom = Random(7),
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(5), startIndex = 1)
            advanceUntilIdle()
            repository.cycleMode()
            advanceUntilIdle()
            val shuffledQueueIds = trackIds(repository.snapshot.value.queue)

            repository.skipNext()
            advanceUntilIdle()

            assertEquals(1, repository.snapshot.value.currentIndex)
            assertEquals(shuffledQueueIds[1], repository.snapshot.value.currentTrack?.id)
            assertEquals(shuffledQueueIds[1], gateway.loadCalls.last().track.id)

            gateway.emitCompletion()
            advanceUntilIdle()

            assertEquals(2, repository.snapshot.value.currentIndex)
            assertEquals(shuffledQueueIds[2], repository.snapshot.value.currentTrack?.id)
            assertEquals(shuffledQueueIds[2], gateway.loadCalls.last().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `shuffle previous follows generated queue order backwards`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            shuffleRandom = Random(11),
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(5), startIndex = 0)
            advanceUntilIdle()
            repository.cycleMode()
            advanceUntilIdle()
            val shuffledQueueIds = trackIds(repository.snapshot.value.queue)

            repository.skipNext()
            repository.skipNext()
            advanceUntilIdle()
            repository.skipPrevious()
            advanceUntilIdle()

            assertEquals(1, repository.snapshot.value.currentIndex)
            assertEquals(shuffledQueueIds[1], repository.snapshot.value.currentTrack?.id)
            assertEquals(shuffledQueueIds[1], gateway.loadCalls.last().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `switching back to order restores ordered queue and keeps current track position`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = sampleTracks(5)
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            shuffleRandom = Random(13),
        )

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 2)
            advanceUntilIdle()
            repository.cycleMode()
            advanceUntilIdle()
            repository.skipNext()
            advanceUntilIdle()
            gateway.updateState { it.copy(positionMs = 64_000L, isPlaying = true) }
            advanceUntilIdle()
            val currentTrackId = repository.snapshot.value.currentTrack?.id
            val loadCountBeforeModeChanges = gateway.loadCalls.size

            repository.cycleMode()
            repository.cycleMode()
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(PlaybackMode.ORDER, snapshot.mode)
            assertEquals(trackIds(tracks), trackIds(snapshot.queue))
            assertEquals(trackIds(tracks), trackIds(snapshot.orderedQueue))
            assertEquals(currentTrackId, snapshot.currentTrack?.id)
            assertEquals(tracks.indexOfFirst { it.id == currentTrackId }, snapshot.currentIndex)
            assertEquals(64_000L, snapshot.positionMs)
            assertEquals(loadCountBeforeModeChanges, gateway.loadCalls.size)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `play queue index keeps original ordered queue while jumping in shuffled queue`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = sampleTracks(5)
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            shuffleRandom = Random(17),
        )

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 0)
            advanceUntilIdle()
            repository.cycleMode()
            advanceUntilIdle()
            val shuffledQueueIds = trackIds(repository.snapshot.value.queue)

            repository.playQueueIndex(1)
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(1, snapshot.currentIndex)
            assertEquals(shuffledQueueIds[1], snapshot.currentTrack?.id)
            assertEquals(shuffledQueueIds, trackIds(snapshot.queue))
            assertEquals(trackIds(tracks), trackIds(snapshot.orderedQueue))
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `play tracks in shuffle mode builds new shuffled queue from provided order`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val seed = 19
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            shuffleRandom = Random(seed),
        )
        val tracks = sampleTracks(5)
        val expectedQueue = listOf(tracks[3]) +
            tracks.filterIndexed { index, _ -> index != 3 }.shuffled(Random(seed))

        try {
            advanceUntilIdle()
            repository.cycleMode()
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 3)
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(PlaybackMode.SHUFFLE, snapshot.mode)
            assertEquals(0, snapshot.currentIndex)
            assertEquals("track-4", snapshot.currentTrack?.id)
            assertEquals(trackIds(expectedQueue), trackIds(snapshot.queue))
            assertEquals(trackIds(tracks), trackIds(snapshot.orderedQueue))
            assertEquals("track-4", gateway.loadCalls.last().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `persisted queue snapshot stores shuffled queue and ordered queue`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = sampleTracks(5)
        database.trackDao().upsertAll(sampleTrackEntities(tracks))
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            shuffleRandom = Random(23),
        )

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 1)
            advanceUntilIdle()
            repository.cycleMode()
            advanceUntilIdle()
            val snapshot = repository.snapshot.value
            val persisted = database.playbackQueueSnapshotDao().get()

            assertEquals(trackIds(snapshot.queue).joinToString(","), persisted?.queueTrackIds)
            assertEquals(trackIds(tracks).joinToString(","), persisted?.orderedQueueTrackIds)
            assertEquals("", persisted?.queueTracksJson)
            assertEquals("", persisted?.orderedQueueTracksJson)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `local large queue cycle mode stores no fallback json and restores from track table`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = sampleTracks(1_200)
        database.trackDao().upsertAll(sampleTrackEntities(tracks))
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            shuffleRandom = Random(23),
        )

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 10)
            advanceUntilIdle()
            repository.cycleMode()
            advanceUntilIdle()
            val persisted = database.playbackQueueSnapshotDao().get()

            assertEquals("", persisted?.queueTracksJson)
            assertEquals("", persisted?.orderedQueueTracksJson)

            repository.close()
            val restoredScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val restoredRepository = DefaultPlaybackRepository(
                database = database,
                gateway = FakePlaybackGateway(),
                playbackPreferencesStore = playbackPreferencesStore,
                scope = restoredScope,
                shuffleRandom = Random(23),
            )
            try {
                advanceUntilIdle()
                val restoredSnapshot = restoredRepository.snapshot.value

                assertEquals(1_200, restoredSnapshot.queue.size)
                assertEquals(1_200, restoredSnapshot.orderedQueue.size)
                assertEquals(PlaybackMode.SHUFFLE, restoredSnapshot.mode)
                assertEquals(tracks[10].id, restoredSnapshot.currentTrack?.id)
            } finally {
                restoredRepository.close()
                restoredScope.cancel()
            }
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `local index remote queue stores no fallback json and restores from track table`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = (1..1_200).map { index ->
            sampleNavidromeTrack(
                id = "track:nav-local:navidrome:song-$index",
                sourceId = "nav-local",
                songId = "song-$index",
            ).copy(
                title = "Remote Indexed Song $index",
                relativePath = "Remote Indexed Song $index.mp3",
            )
        }
        database.importSourceDao().upsert(
            sampleNavidromeSourceEntity("nav-local", ImportSourceIndexMode.LOCAL_INDEX),
        )
        database.trackDao().upsertAll(tracks.map(::sampleTrackEntity))
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            shuffleRandom = Random(23),
        )

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 25)
            advanceUntilIdle()
            repository.cycleMode()
            advanceUntilIdle()
            val persisted = database.playbackQueueSnapshotDao().get()

            assertEquals("", persisted?.queueTracksJson)
            assertEquals("", persisted?.orderedQueueTracksJson)

            repository.close()
            val restoredScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val restoredRepository = DefaultPlaybackRepository(
                database = database,
                gateway = FakePlaybackGateway(),
                playbackPreferencesStore = playbackPreferencesStore,
                scope = restoredScope,
                shuffleRandom = Random(23),
            )
            try {
                advanceUntilIdle()
                val restoredSnapshot = restoredRepository.snapshot.value

                assertEquals(1_200, restoredSnapshot.queue.size)
                assertEquals(1_200, restoredSnapshot.orderedQueue.size)
                assertEquals(PlaybackMode.SHUFFLE, restoredSnapshot.mode)
                assertEquals(tracks[25].id, restoredSnapshot.currentTrack?.id)
                assertEquals("nav-local", restoredSnapshot.currentTrack?.sourceId)
            } finally {
                restoredRepository.close()
                restoredScope.cancel()
            }
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `persisted queue snapshot stores fallback json only for online tracks missing from local table`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = listOf(
            sampleNavidromeTrack(id = "nav-track-1", songId = "song-1"),
            sampleNavidromeTrack(id = "nav-track-2", songId = "song-2"),
        )
        database.importSourceDao().upsert(
            sampleNavidromeSourceEntity("nav-source", ImportSourceIndexMode.ONLINE),
        )
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 1)
            advanceUntilIdle()

            val persisted = database.playbackQueueSnapshotDao().get()
            assertEquals("nav-track-1,nav-track-2", persisted?.queueTrackIds)
            assertEquals("", persisted?.orderedQueueTracksJson)
            assertEquals(true, persisted?.queueTracksJson?.contains("\"id\":\"nav-track-1\""))
            assertEquals(true, persisted?.queueTracksJson?.contains("\"id\":\"nav-track-2\""))
            assertEquals(true, persisted?.queueTracksJson?.contains("\"mediaLocator\":\"lynmusic-navidrome://nav-source/song-2\""))
            assertEquals(true, persisted?.queueTracksJson?.contains("\"relativePath\":\"Remote Song.mp3\""))
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `online remote queue writes fallback only for tracks missing from local table`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val indexedOnlineTrack = sampleNavidromeTrack(id = "nav-track-1", songId = "song-1")
        val missingOnlineTrack = sampleNavidromeTrack(id = "nav-track-2", songId = "song-2")
        database.importSourceDao().upsert(
            sampleNavidromeSourceEntity("nav-source", ImportSourceIndexMode.ONLINE),
        )
        database.trackDao().upsertAll(listOf(sampleTrackEntity(indexedOnlineTrack)))
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(listOf(indexedOnlineTrack, missingOnlineTrack), startIndex = 0)
            advanceUntilIdle()

            val persisted = database.playbackQueueSnapshotDao().get()
            assertEquals(false, persisted?.queueTracksJson?.contains("\"id\":\"nav-track-1\""))
            assertEquals(true, persisted?.queueTracksJson?.contains("\"id\":\"nav-track-2\""))
            assertEquals("", persisted?.orderedQueueTracksJson)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `mixed local index and online remote queue writes fallback only for online source`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val localIndexTrack = sampleNavidromeTrack(
            id = "track:nav-local:navidrome:song-1",
            sourceId = "nav-local",
            songId = "song-1",
        )
        val onlineTrack = sampleNavidromeTrack(
            id = "track:nav-online:navidrome:song-2",
            sourceId = "nav-online",
            songId = "song-2",
        )
        database.importSourceDao().upsert(
            sampleNavidromeSourceEntity("nav-local", ImportSourceIndexMode.LOCAL_INDEX),
        )
        database.importSourceDao().upsert(
            sampleNavidromeSourceEntity("nav-online", ImportSourceIndexMode.ONLINE),
        )
        database.trackDao().upsertAll(listOf(sampleTrackEntity(localIndexTrack)))
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(listOf(localIndexTrack, onlineTrack), startIndex = 0)
            advanceUntilIdle()

            val persisted = database.playbackQueueSnapshotDao().get()
            assertEquals(false, persisted?.queueTracksJson?.contains("\"id\":\"${localIndexTrack.id}\""))
            assertEquals(true, persisted?.queueTracksJson?.contains("\"id\":\"${onlineTrack.id}\""))
            assertEquals("", persisted?.orderedQueueTracksJson)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `unknown remote source is treated as needing fallback json`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val track = sampleNavidromeTrack(id = "nav-missing-source-track", sourceId = "nav-missing", songId = "song-1")
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(listOf(track), startIndex = 0)
            advanceUntilIdle()

            val persisted = database.playbackQueueSnapshotDao().get()
            assertEquals(true, persisted?.queueTracksJson?.contains("\"id\":\"nav-missing-source-track\""))
            assertEquals("", persisted?.orderedQueueTracksJson)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `persisted queue snapshot omits local tracks from mixed fallback json`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val localTrack = sampleTrack("track-local", "Local Song")
        val onlineTrack = sampleNavidromeTrack(id = "nav-track-1", songId = "song-1")
        database.trackDao().upsertAll(listOf(sampleTrackEntity(localTrack.id, localTrack.title)))
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(listOf(localTrack, onlineTrack), startIndex = 0)
            advanceUntilIdle()

            val persisted = database.playbackQueueSnapshotDao().get()
            assertEquals("track-local,nav-track-1", persisted?.queueTrackIds)
            assertEquals(false, persisted?.queueTracksJson?.contains("\"id\":\"track-local\""))
            assertEquals(true, persisted?.queueTracksJson?.contains("\"id\":\"nav-track-1\""))
            assertEquals("", persisted?.orderedQueueTracksJson)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `local queue skips update cursor without writing fallback json`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = sampleTracks(1_200)
        database.trackDao().upsertAll(sampleTrackEntities(tracks))
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 0)
            advanceUntilIdle()

            repeat(10) {
                repository.skipNext()
                advanceUntilIdle()
            }

            val persisted = database.playbackQueueSnapshotDao().get()
            assertEquals(10, persisted?.currentIndex)
            assertEquals("", persisted?.queueTracksJson)
            assertEquals("", persisted?.orderedQueueTracksJson)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `online queue skips update cursor without rewriting fallback json`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = (1..1_200).map { index ->
            sampleNavidromeTrack(id = "nav-track-$index", songId = "song-$index").copy(
                title = "Remote Song $index",
                relativePath = "Remote Song $index.mp3",
            )
        }
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 0)
            advanceUntilIdle()
            val initialPersisted = database.playbackQueueSnapshotDao().get()
            val initialFallbackJson = initialPersisted?.queueTracksJson

            repeat(10) {
                repository.skipNext()
                advanceUntilIdle()
            }

            val persisted = database.playbackQueueSnapshotDao().get()
            assertEquals(10, persisted?.currentIndex)
            assertEquals(initialFallbackJson, persisted?.queueTracksJson)
            assertEquals("", persisted?.orderedQueueTracksJson)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `online skip before first content snapshot exists does not persist fallback json from cursor path`() = runTest {
        val database = createTestDatabase()
        val gateway = BlockingPlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = (1..1_200).map { index ->
            sampleNavidromeTrack(id = "nav-track-$index", songId = "song-$index").copy(
                title = "Remote Song $index",
                relativePath = "Remote Song $index.mp3",
            )
        }
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            val initialLoadGate = CompletableDeferred<Unit>()
            gateway.nextLoadGate = initialLoadGate
            val playJob = launch { repository.playTracks(tracks, startIndex = 0) }
            advanceUntilIdle()

            assertEquals("nav-track-1", repository.snapshot.value.currentTrack?.id)
            assertNull(database.playbackQueueSnapshotDao().get())

            val skipJob = launch { repository.skipNext() }
            advanceUntilIdle()

            assertEquals("nav-track-2", repository.snapshot.value.currentTrack?.id)
            assertNull(database.playbackQueueSnapshotDao().get())

            initialLoadGate.complete(Unit)
            playJob.join()
            skipJob.join()
            advanceUntilIdle()

            val persisted = database.playbackQueueSnapshotDao().get()
            assertEquals(1, persisted?.currentIndex)
            assertEquals(true, persisted?.queueTracksJson?.contains("\"id\":\"nav-track-2\""))
            assertEquals("", persisted?.orderedQueueTracksJson)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `seek and automatic next update cursor without rewriting fallback json`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val tracks = listOf(
            sampleNavidromeTrack(id = "nav-track-1", songId = "song-1"),
            sampleNavidromeTrack(id = "nav-track-2", songId = "song-2"),
            sampleNavidromeTrack(id = "nav-track-3", songId = "song-3"),
        )
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(tracks, startIndex = 0)
            advanceUntilIdle()
            val initialFallbackJson = database.playbackQueueSnapshotDao().get()?.queueTracksJson

            repository.seekTo(12_345L)
            advanceUntilIdle()
            val afterSeek = database.playbackQueueSnapshotDao().get()
            assertEquals(12_345L, afterSeek?.positionMs)
            assertEquals(initialFallbackJson, afterSeek?.queueTracksJson)

            gateway.emitCompletion()
            advanceUntilIdle()
            var afterCompletion = database.playbackQueueSnapshotDao().get()
            var completionAttempts = 0
            while (afterCompletion?.currentIndex != 1 && completionAttempts < 5) {
                completionAttempts += 1
                advanceUntilIdle()
                afterCompletion = database.playbackQueueSnapshotDao().get()
            }
            assertEquals(1, afterCompletion?.currentIndex)
            assertEquals(initialFallbackJson, afterCompletion?.queueTracksJson)
            assertEquals("", afterCompletion?.orderedQueueTracksJson)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `transient playback loads track without persisting queue snapshot`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            val track = sampleExternalOpenTrack("content://media/external/audio/media/42")
            repository.playTransientTracks(listOf(track), startIndex = 0)
            advanceUntilIdle()

            assertEquals(track.id, repository.snapshot.value.currentTrack?.id)
            assertEquals(track.mediaLocator, gateway.loadCalls.last().track.mediaLocator)
            assertNull(database.playbackQueueSnapshotDao().get())
        } finally {
            repository.close()
            scope.cancel()
            advanceUntilIdle()
            database.close()
        }
    }

    @Test
    fun `transient playback does not replace existing persisted queue snapshot`() = runTest {
        val database = createTestDatabase()
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                orderedQueueTrackIds = "track-1",
                currentIndex = 0,
                positionMs = 12_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1_000L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            repository.playTransientTracks(
                tracks = listOf(sampleExternalOpenTrack("content://media/external/audio/media/43")),
                startIndex = 0,
            )
            advanceUntilIdle()

            val persisted = database.playbackQueueSnapshotDao().get()
            assertEquals("track-1", persisted?.queueTrackIds)
            assertEquals("track-1", persisted?.orderedQueueTrackIds)
            assertEquals(12_000L, persisted?.positionMs)
        } finally {
            repository.close()
            scope.cancel()
            advanceUntilIdle()
            database.close()
        }
    }

    @Test
    fun `restore queue snapshot keeps persisted shuffled queue and ordered queue`() = runTest {
        val database = createTestDatabase()
        val tracks = sampleTracks(4)
        database.trackDao().upsertAll(tracks.map { track -> sampleTrackEntity(track.id, track.title) })
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-3,track-1,track-4,track-2",
                orderedQueueTrackIds = "track-1,track-2,track-3,track-4",
                currentIndex = 2,
                positionMs = 12_000L,
                mode = PlaybackMode.SHUFFLE.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            assertEquals(
                PlaybackHydrationResult.ExistingPlayback,
                repository.hydratePersistedQueueIfNeeded(),
            )
            val snapshot = repository.snapshot.value
            assertEquals(listOf("track-3", "track-1", "track-4", "track-2"), trackIds(snapshot.queue))
            assertEquals(listOf("track-1", "track-2", "track-3", "track-4"), trackIds(snapshot.orderedQueue))
            assertEquals(2, snapshot.currentIndex)
            assertEquals("track-4", snapshot.currentTrack?.id)
            assertEquals(false, snapshot.isPlaying)
            assertEquals("track-4", gateway.loadCalls.single().track.id)
            assertEquals(false, gateway.loadCalls.single().playWhenReady)
            assertEquals(12_000L, gateway.loadCalls.single().startPositionMs)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `hydrate reports empty when no persisted queue exists`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(),
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            assertEquals(
                PlaybackHydrationResult.Empty,
                repository.hydratePersistedQueueIfNeeded(),
            )
            assertEquals(
                PlaybackHydrationResult.Empty,
                repository.hydratePersistedQueueIfNeeded(),
            )
            assertEquals(false, repository.snapshot.value.isHydratingPlayback)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `hydrate reports existing playback when playback already supplied a queue`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(),
            scope = scope,
            hydrateImmediately = false,
        )
        val tracks = sampleTracks(1)

        try {
            repository.playTracks(tracks, startIndex = 0)

            assertEquals(
                PlaybackHydrationResult.ExistingPlayback,
                repository.hydratePersistedQueueIfNeeded(),
            )
            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `hydrate reports existing playback after an empty result is cached`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(),
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            assertEquals(
                PlaybackHydrationResult.Empty,
                repository.hydratePersistedQueueIfNeeded(),
            )

            repository.playTracks(sampleTracks(1), startIndex = 0)

            assertEquals(
                PlaybackHydrationResult.ExistingPlayback,
                repository.hydratePersistedQueueIfNeeded(),
            )
            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `hydrate reports superseded when playback changes during restored track load`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(
            listOf(sampleTrackEntity("track-1", "Restored Song")),
        )
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                orderedQueueTrackIds = "track-1",
                currentIndex = 0,
                positionMs = 12_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = BlockingPlaybackGateway()
        val restoreLoadStarted = CompletableDeferred<Unit>()
        val restoreLoadGate = CompletableDeferred<Unit>()
        gateway.nextLoadStarted = restoreLoadStarted
        gateway.nextLoadGate = restoreLoadGate
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(),
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            val hydration = async { repository.hydratePersistedQueueIfNeeded() }
            restoreLoadStarted.await()

            repository.playTracks(sampleTracks(2), startIndex = 1)
            restoreLoadGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                PlaybackHydrationResult.SupersededByPlayback,
                hydration.await(),
            )
            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `cached restored hydration reports existing playback after playback changes`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(
            listOf(sampleTrackEntity("track-1", "Restored Song")),
        )
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                orderedQueueTrackIds = "track-1",
                currentIndex = 0,
                positionMs = 12_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(),
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            assertIs<PlaybackHydrationResult.Restored>(
                repository.hydratePersistedQueueIfNeeded(),
            )

            repository.playTracks(sampleTracks(2), startIndex = 1)

            assertEquals(
                PlaybackHydrationResult.ExistingPlayback,
                repository.hydratePersistedQueueIfNeeded(),
            )
            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue snapshot auto plays when startup preference is enabled`() = runTest {
        val database = createTestDatabase()
        val tracks = sampleTracks(4)
        database.trackDao().upsertAll(tracks.map { track -> sampleTrackEntity(track.id, track.title) })
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-3,track-1,track-4,track-2",
                orderedQueueTrackIds = "track-1,track-2,track-3,track-4",
                currentIndex = 2,
                positionMs = 12_000L,
                mode = PlaybackMode.SHUFFLE.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore(autoPlayOnStartup = true)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(listOf("track-3", "track-1", "track-4", "track-2"), trackIds(snapshot.queue))
            assertEquals(listOf("track-1", "track-2", "track-3", "track-4"), trackIds(snapshot.orderedQueue))
            assertEquals(2, snapshot.currentIndex)
            assertEquals("track-4", snapshot.currentTrack?.id)
            assertEquals(true, snapshot.isPlaying)
            assertEquals("track-4", gateway.loadCalls.single().track.id)
            assertEquals(true, gateway.loadCalls.single().playWhenReady)
            assertEquals(12_000L, gateway.loadCalls.single().startPositionMs)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue snapshot uses json fallback for online tracks missing from local table`() = runTest {
        val database = createTestDatabase()
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "nav-track-1,nav-track-2",
                orderedQueueTrackIds = "nav-track-1,nav-track-2",
                queueTracksJson = """
                    [
                      {
                        "id": "nav-track-1",
                        "sourceId": "nav-source",
                        "title": "Online One",
                        "artistName": "Remote Artist",
                        "albumTitle": "Remote Album",
                        "durationMs": 181000,
                        "mediaLocator": "lynmusic-navidrome://nav-source/song-1",
                        "relativePath": "Remote Artist/Remote Album/Online One.flac",
                        "artworkLocator": "lynmusic-cover://nav-source/cover-1",
                        "albumId": "album-remote",
                        "artistId": "artist-remote"
                      },
                      {
                        "id": "nav-track-2",
                        "sourceId": "nav-source",
                        "title": "Online Two",
                        "artistName": "Remote Artist",
                        "albumTitle": "Remote Album",
                        "durationMs": 182000,
                        "mediaLocator": "lynmusic-navidrome://nav-source/song-2",
                        "relativePath": "Remote Artist/Remote Album/Online Two.flac",
                        "artworkLocator": "lynmusic-cover://nav-source/cover-2",
                        "albumId": "album-remote",
                        "artistId": "artist-remote",
                        "remoteFavoriteHint": true
                      }
                    ]
                """.trimIndent(),
                orderedQueueTracksJson = """
                    [
                      {
                        "id": "nav-track-1",
                        "sourceId": "nav-source",
                        "title": "Online One",
                        "durationMs": 181000,
                        "mediaLocator": "lynmusic-navidrome://nav-source/song-1"
                      },
                      {
                        "id": "nav-track-2",
                        "sourceId": "nav-source",
                        "title": "Online Two",
                        "durationMs": 182000,
                        "mediaLocator": "lynmusic-navidrome://nav-source/song-2",
                        "remoteFavoriteHint": true
                      }
                    ]
                """.trimIndent(),
                currentIndex = 1,
                positionMs = 42_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(listOf("nav-track-1", "nav-track-2"), trackIds(snapshot.queue))
            assertEquals(1, snapshot.currentIndex)
            assertEquals("Online Two", snapshot.currentTrack?.title)
            assertEquals("lynmusic-navidrome://nav-source/song-2", snapshot.currentTrack?.mediaLocator)
            assertEquals("Remote Artist/Remote Album/Online Two.flac", snapshot.currentTrack?.relativePath)
            assertEquals(
                "lynmusic-navidrome://nav-source/song-2",
                snapshot.orderedQueue.getOrNull(1)?.relativePath,
            )
            assertEquals("album-remote", snapshot.currentTrack?.albumId)
            assertEquals("artist-remote", snapshot.currentTrack?.artistId)
            assertEquals(true, snapshot.currentTrack?.remoteFavoriteHint)
            assertEquals(42_000L, snapshot.positionMs)
            assertEquals("nav-track-2", gateway.loadCalls.single().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue snapshot uses legacy ordered json fallback when queue json is empty`() = runTest {
        val database = createTestDatabase()
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "nav-track-1,nav-track-2",
                orderedQueueTrackIds = "nav-track-1,nav-track-2",
                queueTracksJson = "",
                orderedQueueTracksJson = """
                    [
                      {
                        "id": "nav-track-1",
                        "sourceId": "nav-source",
                        "title": "Online One",
                        "durationMs": 181000,
                        "mediaLocator": "lynmusic-navidrome://nav-source/song-1",
                        "relativePath": "Remote Artist/Remote Album/Online One.flac"
                      },
                      {
                        "id": "nav-track-2",
                        "sourceId": "nav-source",
                        "title": "Online Two",
                        "durationMs": 182000,
                        "mediaLocator": "lynmusic-navidrome://nav-source/song-2",
                        "relativePath": "Remote Artist/Remote Album/Online Two.flac"
                      }
                    ]
                """.trimIndent(),
                currentIndex = 1,
                positionMs = 42_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(listOf("nav-track-1", "nav-track-2"), trackIds(snapshot.queue))
            assertEquals(1, snapshot.currentIndex)
            assertEquals("Online Two", snapshot.currentTrack?.title)
            assertEquals("Remote Artist/Remote Album/Online Two.flac", snapshot.currentTrack?.relativePath)
            assertEquals("nav-track-2", gateway.loadCalls.single().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue snapshot prefers database track over json fallback`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(listOf(sampleTrackEntity("track-1", "Fresh DB Title")))
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                orderedQueueTrackIds = "track-1",
                queueTracksJson = """
                    [
                      {
                        "id": "track-1",
                        "sourceId": "nav-source",
                        "title": "Stale Snapshot Title",
                        "durationMs": 999000,
                        "mediaLocator": "lynmusic-navidrome://nav-source/song-stale"
                      }
                    ]
                """.trimIndent(),
                orderedQueueTracksJson = "",
                currentIndex = 0,
                positionMs = 8_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            assertEquals("Fresh DB Title", repository.snapshot.value.currentTrack?.title)
            assertEquals("file:///music/track-1.mp3", gateway.loadCalls.single().track.mediaLocator)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue snapshot ignores broken json while keeping restorable local tracks`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(listOf(sampleTrackEntity("track-1", "First Song")))
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1,nav-missing",
                orderedQueueTrackIds = "track-1,nav-missing",
                queueTracksJson = "not-json",
                orderedQueueTracksJson = "not-json",
                currentIndex = 1,
                positionMs = 8_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            assertEquals(listOf("track-1"), trackIds(repository.snapshot.value.queue))
            assertEquals(0, repository.snapshot.value.currentIndex)
            assertEquals("track-1", gateway.loadCalls.single().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue snapshot maps current index after skipped tracks`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(
            listOf(
                sampleTrackEntity("track-1", "First Song"),
                sampleTrackEntity("track-4", "Fourth Song"),
                sampleTrackEntity("track-5", "Fifth Song"),
            ),
        )
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1,missing-2,missing-3,track-4,track-5",
                orderedQueueTrackIds = "track-1,missing-2,missing-3,track-4,track-5",
                currentIndex = 4,
                positionMs = 11_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(listOf("track-1", "track-4", "track-5"), trackIds(snapshot.queue))
            assertEquals(2, snapshot.currentIndex)
            assertEquals("track-5", snapshot.currentTrack?.id)
            assertEquals("track-5", gateway.loadCalls.single().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue snapshot falls forward when current track is missing`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(
            listOf(
                sampleTrackEntity("track-1", "First Song"),
                sampleTrackEntity("track-3", "Third Song"),
            ),
        )
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1,missing-2,track-3",
                orderedQueueTrackIds = "track-1,missing-2,track-3",
                currentIndex = 1,
                positionMs = 8_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(listOf("track-1", "track-3"), trackIds(snapshot.queue))
            assertEquals(1, snapshot.currentIndex)
            assertEquals("track-3", snapshot.currentTrack?.id)
            assertEquals("track-3", gateway.loadCalls.single().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue snapshot falls backward when current track has no restorable successor`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(listOf(sampleTrackEntity("track-1", "First Song")))
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1,missing-2,missing-3",
                orderedQueueTrackIds = "track-1,missing-2,missing-3",
                currentIndex = 2,
                positionMs = 8_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            val snapshot = repository.snapshot.value
            assertEquals(listOf("track-1"), trackIds(snapshot.queue))
            assertEquals(0, snapshot.currentIndex)
            assertEquals("track-1", snapshot.currentTrack?.id)
            assertEquals("track-1", gateway.loadCalls.single().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `manual next advances to next track in repeat one`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()
            repository.cycleMode()
            repository.cycleMode()
            advanceUntilIdle()
            val queuedTrackIds = trackIds(repository.snapshot.value.queue)

            repository.skipNext()
            advanceUntilIdle()

            assertEquals(1, repository.snapshot.value.currentIndex)
            assertEquals(queuedTrackIds[1], repository.snapshot.value.currentTrack?.id)
            assertEquals(queuedTrackIds[1], gateway.loadCalls.last().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `manual previous switches track in repeat one even after five seconds`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()
            repository.cycleMode()
            repository.cycleMode()
            advanceUntilIdle()
            val queuedTrackIds = trackIds(repository.snapshot.value.queue)
            repository.skipNext()
            advanceUntilIdle()
            gateway.updateState { it.copy(positionMs = 6_000L) }
            advanceUntilIdle()

            repository.skipPrevious()
            advanceUntilIdle()

            assertEquals(0, repository.snapshot.value.currentIndex)
            assertEquals(queuedTrackIds[0], repository.snapshot.value.currentTrack?.id)
            assertEquals(emptyList(), gateway.seekCalls)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `natural completion stays on current track in repeat one`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()
            repository.cycleMode()
            repository.cycleMode()
            advanceUntilIdle()
            val currentTrackId = repository.snapshot.value.currentTrack?.id
            val currentIndex = repository.snapshot.value.currentIndex
            val loadCountBeforeCompletion = gateway.loadCalls.size

            gateway.emitCompletion()
            advanceUntilIdle()

            assertEquals(currentIndex, repository.snapshot.value.currentIndex)
            assertEquals(currentTrackId, repository.snapshot.value.currentTrack?.id)
            assertEquals(loadCountBeforeCompletion + 1, gateway.loadCalls.size)
            assertEquals(currentTrackId, gateway.loadCalls.last().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `automatic artwork override survives repeat one natural reload`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val trackEntity = sampleTrackEntity("track-1", "First Song")
        database.trackDao().upsertAll(listOf(trackEntity))
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = "track-1",
                sourceId = "auto-lyrics",
                rawPayload = "auto line",
                updatedAt = 1L,
                artworkLocator = "/tmp/auto.jpg",
            ),
        )
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                currentIndex = 0,
                positionMs = 0L,
                mode = PlaybackMode.REPEAT_ONE.name,
                updatedAt = 1L,
            ),
        )
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            assertEquals(PlaybackMode.REPEAT_ONE, repository.snapshot.value.mode)
            assertEquals("/tmp/auto.jpg", repository.snapshot.value.currentTrack?.artworkLocator)

            gateway.emitCompletion()
            advanceUntilIdle()

            assertEquals(PlaybackMode.REPEAT_ONE, repository.snapshot.value.mode)
            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals("/tmp/auto.jpg", repository.snapshot.value.currentDisplayArtworkLocator)
            assertEquals("/tmp/auto.jpg", gateway.loadCalls.last().track.artworkLocator)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `concurrent skip next commands keep latest requested track while stale load finishes later`() = runTest {
        val database = createTestDatabase()
        val gateway = BlockingPlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()

            val firstSkipGate = CompletableDeferred<Unit>()
            gateway.nextLoadGate = firstSkipGate
            val firstSkipJob = launch { repository.skipNext() }
            advanceUntilIdle()

            val secondSkipJob = launch { repository.skipNext() }
            advanceUntilIdle()

            assertEquals("track-3", repository.snapshot.value.currentTrack?.id)
            assertEquals(listOf("track-1", "track-2", "track-3"), gateway.loadCalls.map { it.track.id })

            firstSkipGate.complete(Unit)
            firstSkipJob.join()
            secondSkipJob.join()
            advanceUntilIdle()

            assertEquals("track-3", repository.snapshot.value.currentTrack?.id)
            assertEquals("track-3", gateway.appliedTrackIds.last())
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue and live refresh use manual artwork override`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val trackEntity = TrackEntity(
            id = "track-1",
            sourceId = "local-1",
            title = "First Song",
            artistId = null,
            artistName = "Artist A",
            albumId = null,
            albumTitle = "Album A",
            durationMs = 180_000L,
            trackNumber = null,
            discNumber = null,
            mediaLocator = "file:///music/track-1.mp3",
            relativePath = "First Song.mp3",
            artworkLocator = "/tmp/original.jpg",
            sizeBytes = 0L,
            modifiedAt = 0L,
        )
        database.trackDao().upsertAll(listOf(trackEntity))
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = "track-1",
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = "manual line",
                updatedAt = 1L,
                artworkLocator = "/tmp/manual.jpg",
            ),
        )
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                currentIndex = 0,
                positionMs = 0L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            assertEquals("/tmp/manual.jpg", repository.snapshot.value.currentTrack?.artworkLocator)
            assertEquals("/tmp/manual.jpg", gateway.loadCalls.single().track.artworkLocator)

            database.lyricsCacheDao().deleteByTrackIdAndSourceId("track-1", MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
            advanceUntilIdle()
            database.trackDao().upsertAll(listOf(trackEntity.copy(modifiedAt = 1L)))
            advanceUntilIdle()

            var artworkLocator = repository.snapshot.value.currentTrack?.artworkLocator
            var refreshAttempts = 0
            while (artworkLocator != "/tmp/original.jpg" && refreshAttempts < 5) {
                refreshAttempts += 1
                advanceUntilIdle()
                artworkLocator = repository.snapshot.value.currentTrack?.artworkLocator
            }
            assertEquals("/tmp/original.jpg", artworkLocator)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `close cancels lifecycle collectors before database shutdown`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val systemControls = FakeSystemPlaybackControlsPlatformService()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        database.trackDao().upsertAll(listOf(sampleTrackEntity("track-1", "First Song")))
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            systemPlaybackControlsPlatformService = systemControls,
            hydrateImmediately = false,
        )

        try {
            repository.playTracks(listOf(sampleTrack("track-1", "First Song")), startIndex = 0)
            advanceUntilIdle()

            repository.close()
            val closedSnapshot = repository.snapshot.value
            database.trackDao().upsertAll(
                listOf(sampleTrackEntity("track-1", "Updated Song").copy(modifiedAt = 1L)),
            )
            gateway.updateState { it.copy(positionMs = 12_345L) }
            advanceUntilIdle()

            assertEquals("First Song", repository.snapshot.value.currentTrack?.title)
            assertEquals(closedSnapshot.positionMs, repository.snapshot.value.positionMs)
            assertEquals(closedSnapshot, systemControls.lastSnapshot)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `close releases gateway even when system controls close fails`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val systemControls = FakeSystemPlaybackControlsPlatformService(
            closeFailure = IllegalStateException("controls failed"),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(),
            scope = scope,
            systemPlaybackControlsPlatformService = systemControls,
            hydrateImmediately = false,
        )

        try {
            val result = runCatching { repository.close() }

            assertTrue(result.isFailure)
            assertEquals(1, systemControls.closeCalls)
            assertEquals(1, gateway.releaseCalls)
            repository.close()
            assertEquals(1, gateway.releaseCalls)
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `close releases gateway when failure logging also fails`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val controlsFailure = IllegalStateException("controls failed")
        val loggingFailure = IllegalStateException("logger failed")
        val systemControls = FakeSystemPlaybackControlsPlatformService(closeFailure = controlsFailure)
        val throwingLogger = object : DiagnosticLogger {
            override fun log(
                level: DiagnosticLogLevel,
                tag: String,
                message: String,
                throwable: Throwable?,
            ) {
                if (throwable === controlsFailure) throw loggingFailure
            }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(),
            scope = scope,
            systemPlaybackControlsPlatformService = systemControls,
            logger = throwingLogger,
            hydrateImmediately = false,
        )

        try {
            val result = runCatching { repository.close() }

            assertSame(controlsFailure, result.exceptionOrNull())
            assertEquals(1, systemControls.closeCalls)
            assertEquals(1, gateway.releaseCalls)
            assertTrue(controlsFailure.suppressedExceptions.contains(loggingFailure))
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `close preserves shared failure instance without self suppression`() = runTest {
        val database = createTestDatabase()
        val sharedFailure = IllegalStateException("shared failure")
        val gateway = FakePlaybackGateway(releaseFailure = sharedFailure)
        val systemControls = FakeSystemPlaybackControlsPlatformService(closeFailure = sharedFailure)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(),
            scope = scope,
            systemPlaybackControlsPlatformService = systemControls,
            hydrateImmediately = false,
        )

        try {
            val result = runCatching { repository.close() }

            assertSame(sharedFailure, result.exceptionOrNull())
            assertEquals(1, systemControls.closeCalls)
            assertEquals(1, gateway.releaseCalls)
            assertTrue(sharedFailure.suppressedExceptions.none { it === sharedFailure })
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `current track artwork update clears stale playback metadata artwork`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val trackEntity = TrackEntity(
            id = "track-1",
            sourceId = "local-1",
            title = "First Song",
            artistId = null,
            artistName = "Artist A",
            albumId = null,
            albumTitle = "Album A",
            durationMs = 180_000L,
            trackNumber = null,
            discNumber = null,
            mediaLocator = "file:///music/track-1.mp3",
            relativePath = "First Song.mp3",
            artworkLocator = "/tmp/original.jpg",
            sizeBytes = 0L,
            modifiedAt = 0L,
        )
        database.trackDao().upsertAll(listOf(trackEntity))
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(
                tracks = listOf(
                    sampleTrack("track-1", "First Song").copy(artworkLocator = "/tmp/original.jpg"),
                ),
                startIndex = 0,
            )
            advanceUntilIdle()

            repository.overrideCurrentTrackArtwork("https://img.example.com/override.jpg")
            advanceUntilIdle()
            assertEquals("https://img.example.com/override.jpg", repository.snapshot.value.currentDisplayArtworkLocator)

            database.lyricsCacheDao().upsert(
                LyricsCacheEntity(
                    trackId = "track-1",
                    sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                    rawPayload = "manual line",
                    updatedAt = 2L,
                    artworkLocator = "/tmp/manual-new.jpg",
                ),
            )
            advanceUntilIdle()
            database.trackDao().upsertAll(listOf(trackEntity.copy(modifiedAt = 1L)))
            advanceUntilIdle()

            assertEquals("/tmp/manual-new.jpg", repository.snapshot.value.currentTrack?.artworkLocator)
            assertEquals(null, repository.snapshot.value.metadataArtworkLocator)
            assertEquals("/tmp/manual-new.jpg", repository.snapshot.value.currentDisplayArtworkLocator)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `play tracks surfaces gateway load failure without throwing`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway(loadFailure = IllegalStateException("No route to host"))
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()

            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals(false, repository.snapshot.value.isPlaying)
            assertEquals("访问歌曲失败：No route to host", repository.snapshot.value.errorMessage)
            assertEquals("Failed to access the song: No route to host",
                resolveUiText(checkNotNull(repository.snapshot.value.playbackErrorText()), AppLanguage.English))
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `repeated gateway error still surfaces after switching tracks`() = runTest {
        val database = createTestDatabase()
        val gateway = RepeatingErrorPlaybackGateway("未检测到 VLC，请安装或手动选择 VLC 路径。")
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()

            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals("未检测到 VLC，请安装或手动选择 VLC 路径。", repository.snapshot.value.errorMessage)

            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()

            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)
            assertEquals("未检测到 VLC，请安装或手动选择 VLC 路径。", repository.snapshot.value.errorMessage)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `switching tracks ignores stale gateway error from previous track`() = runTest {
        val database = createTestDatabase()
        val gateway = BlockingPlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()
            gateway.updateState {
                it.copy(
                    isPlaying = false,
                    errorMessage = "track-1 failed",
                    errorRevision = it.errorRevision + 1L,
                )
            }
            advanceUntilIdle()

            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals("track-1 failed", repository.snapshot.value.errorMessage)

            val loadGate = CompletableDeferred<Unit>()
            gateway.nextLoadGate = loadGate
            val switchJob = launch {
                repository.playTracks(sampleTracks(), startIndex = 1)
            }
            advanceUntilIdle()

            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)
            assertNull(repository.snapshot.value.errorMessage)

            loadGate.complete(Unit)
            advanceUntilIdle()
            switchJob.cancel()

            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)
            assertNull(repository.snapshot.value.errorMessage)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue surfaces gateway load failure without throwing`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(
            listOf(sampleTrackEntity("track-1", "First Song")),
        )
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                currentIndex = 0,
                positionMs = 12_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway(loadFailure = IllegalStateException("No route to host"))
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals(false, repository.snapshot.value.isPlaying)
            assertEquals(12_000L, repository.snapshot.value.positionMs)
            assertEquals("访问歌曲失败：No route to host", repository.snapshot.value.errorMessage)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `system controls service receives snapshot updates`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val systemControls = FakeSystemPlaybackControlsPlatformService()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            systemPlaybackControlsPlatformService = systemControls,
        )

        try {
            advanceUntilIdle()

            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()
            gateway.updateState { it.copy(positionMs = 42_000L, isPlaying = true) }
            advanceUntilIdle()

            assertEquals("track-2", systemControls.lastSnapshot?.currentTrack?.id)
            assertEquals(true, systemControls.lastSnapshot?.isPlaying)
            assertEquals(42_000L, systemControls.lastSnapshot?.positionMs)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `seek is ignored when current track cannot seek`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()
            gateway.updateState { it.copy(positionMs = 1_000L, canSeek = false) }
            advanceUntilIdle()

            repository.seekTo(12_345L)
            advanceUntilIdle()

            assertEquals(emptyList(), gateway.seekCalls)
            assertEquals(1_000L, repository.snapshot.value.positionMs)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `seek is forwarded when current track can seek`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()

            repository.seekTo(12_345L)
            advanceUntilIdle()

            assertEquals(listOf(12_345L), gateway.seekCalls)
            assertEquals(12_345L, repository.snapshot.value.positionMs)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `system controls callbacks route to repository commands`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val systemControls = FakeSystemPlaybackControlsPlatformService()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            systemPlaybackControlsPlatformService = systemControls,
        )

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()

            systemControls.callbacks.skipNext()
            advanceUntilIdle()
            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)
            gateway.updateState { it.copy(canSeek = true, errorRevision = it.errorRevision + 1L) }
            advanceUntilIdle()

            systemControls.callbacks.seekTo(12_345L)
            advanceUntilIdle()
            assertEquals(12_345L, gateway.seekCalls.last())

            systemControls.callbacks.pause()
            advanceUntilIdle()
            assertEquals(false, repository.snapshot.value.isPlaying)

            systemControls.callbacks.play()
            advanceUntilIdle()
            assertEquals(true, repository.snapshot.value.isPlaying)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `repository restores persisted volume before hydrating queue`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(listOf(sampleTrackEntity("track-1", "First Song")))
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                currentIndex = 0,
                positionMs = 8_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore(initialPlaybackVolume = 0.35f)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
        )

        try {
            advanceUntilIdle()

            assertEquals(listOf("setVolume", "load"), gateway.eventLog.take(2))
            assertEquals(0.35f, repository.snapshot.value.volume)
            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals(8_000L, repository.snapshot.value.positionMs)
            assertEquals(0.35f, gateway.volumeCalls.first())
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `set volume updates snapshot gateway and persisted preferences`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            advanceUntilIdle()

            repository.setVolume(0.35f)

            assertEquals(0.35f, repository.snapshot.value.volume)
            assertEquals(0.35f, gateway.volumeCalls.last())
            assertEquals(listOf(0.35f), playbackPreferencesStore.persistedVolumes)
            assertEquals(0.35f, playbackPreferencesStore.playbackVolume.value)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `set volume clamps values before persisting`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val playbackPreferencesStore = FakePlaybackPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(
            database = database,
            gateway = gateway,
            playbackPreferencesStore = playbackPreferencesStore,
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            advanceUntilIdle()

            repository.setVolume(-1f)
            repository.setVolume(2f)

            assertEquals(listOf(0f, 1f), playbackPreferencesStore.persistedVolumes)
            assertEquals(1f, repository.snapshot.value.volume)
            assertEquals(1f, gateway.volumeCalls.last())
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `missing or invalid persisted volume falls back to default`() = runTest {
        val database = createTestDatabase()
        val missingGateway = FakePlaybackGateway()
        val invalidGateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val missingRepository = DefaultPlaybackRepository(
            database = database,
            gateway = missingGateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(initialPlaybackVolume = null),
            scope = scope,
            hydrateImmediately = false,
        )
        val invalidRepository = DefaultPlaybackRepository(
            database = database,
            gateway = invalidGateway,
            playbackPreferencesStore = FakePlaybackPreferencesStore(initialPlaybackVolume = Float.NaN),
            scope = scope,
            hydrateImmediately = false,
        )

        try {
            advanceUntilIdle()

            assertEquals(DEFAULT_PLAYBACK_VOLUME, missingRepository.snapshot.value.volume)
            assertEquals(DEFAULT_PLAYBACK_VOLUME, missingGateway.volumeCalls.single())
            assertEquals(DEFAULT_PLAYBACK_VOLUME, invalidRepository.snapshot.value.volume)
            assertEquals(DEFAULT_PLAYBACK_VOLUME, invalidGateway.volumeCalls.single())
        } finally {
            invalidRepository.close()
            missingRepository.close()
            scope.cancel()
            database.close()
        }
    }
}

private class RepeatingErrorPlaybackGateway(
    private val message: String,
) : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState())

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    override suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        mutableState.value = mutableState.value.copy(
            isPlaying = false,
            positionMs = startPositionMs.coerceAtLeast(0L),
            durationMs = 0L,
            errorMessage = message,
            errorRevision = mutableState.value.errorRevision + 1L,
        )
    }

    override suspend fun play() {
        mutableState.value = mutableState.value.copy(
            isPlaying = false,
            errorMessage = message,
            errorRevision = mutableState.value.errorRevision + 1L,
        )
    }

    override suspend fun pause() {
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    override suspend fun seekTo(positionMs: Long) {
        mutableState.value = mutableState.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    override suspend fun setVolume(volume: Float) {
        mutableState.value = mutableState.value.copy(volume = volume)
    }

    override suspend fun release() = Unit

}

private fun createTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-playback", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private fun sampleTracks(): List<Track> {
    return listOf(
        sampleTrack("track-1", "First Song"),
        sampleTrack("track-2", "Second Song"),
        sampleTrack("track-3", "Third Song"),
    )
}

private fun sampleTracks(count: Int): List<Track> {
    return (1..count).map { index ->
        sampleTrack("track-$index", "Song $index")
    }
}

private fun trackIds(tracks: List<Track>): List<String> {
    return tracks.map { it.id }
}

private fun sampleTrack(id: String, title: String): Track {
    return Track(
        id = id,
        sourceId = "local-1",
        title = title,
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 180_000L,
        mediaLocator = "file:///music/$id.mp3",
        relativePath = "$title.mp3",
    )
}

private fun sampleTrackEntities(tracks: List<Track>): List<TrackEntity> {
    return tracks.map { track -> sampleTrackEntity(track.id, track.title) }
}

private fun sampleNavidromeTrack(
    id: String = "nav-track-1",
    sourceId: String = "nav-source",
    songId: String = "song-1",
): Track {
    return sampleTrack(id, "Remote Song").copy(
        sourceId = sourceId,
        mediaLocator = buildNavidromeSongLocator(sourceId, songId),
    )
}

private fun sampleExternalOpenTrack(locator: String): Track {
    return Track(
        id = buildExternalOpenTrackId(locator, 0),
        sourceId = EXTERNAL_OPEN_SOURCE_ID,
        title = "External Song",
        artistName = "External Artist",
        durationMs = 120_000L,
        mediaLocator = locator,
        relativePath = "External Song.mp3",
    )
}

private fun sampleTrackEntity(track: Track): TrackEntity {
    return TrackEntity(
        id = track.id,
        sourceId = track.sourceId,
        title = track.title,
        artistId = track.artistId,
        artistName = track.artistName,
        albumId = track.albumId,
        albumTitle = track.albumTitle,
        durationMs = track.durationMs,
        trackNumber = track.trackNumber,
        discNumber = track.discNumber,
        mediaLocator = track.mediaLocator,
        relativePath = track.relativePath,
        artworkLocator = track.artworkLocator,
        sizeBytes = track.sizeBytes,
        modifiedAt = track.modifiedAt,
        addedAt = track.addedAt,
        bitDepth = track.bitDepth,
        samplingRate = track.samplingRate,
        bitRate = track.bitRate,
        channelCount = track.channelCount,
    )
}

private fun sampleNavidromeSourceEntity(
    sourceId: String,
    indexMode: ImportSourceIndexMode,
): ImportSourceEntity {
    return ImportSourceEntity(
        id = sourceId,
        type = ImportSourceType.NAVIDROME.name,
        label = "Navidrome $sourceId",
        rootReference = "https://$sourceId.example.com",
        server = null,
        shareName = null,
        directoryPath = null,
        username = "demo",
        credentialKey = "credential-$sourceId",
        allowInsecureTls = false,
        enabled = true,
        lastScannedAt = 1L,
        createdAt = 1L,
        indexMode = indexMode.name,
    )
}

private fun sampleTrackEntity(id: String, title: String): TrackEntity {
    return TrackEntity(
        id = id,
        sourceId = "local-1",
        title = title,
        artistId = null,
        artistName = "Artist A",
        albumId = null,
        albumTitle = "Album A",
        durationMs = 180_000L,
        trackNumber = null,
        discNumber = null,
        mediaLocator = "file:///music/$id.mp3",
        relativePath = "$title.mp3",
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
    )
}

private data class PlaybackStatsCall(
    val type: String,
    val trackId: String,
    val atMillis: Long,
)

private class FakePlaybackStatsReporter : PlaybackStatsReporter {
    val calls = mutableListOf<PlaybackStatsCall>()

    override suspend fun reportNowPlaying(track: Track, atMillis: Long) {
        calls += PlaybackStatsCall("now", track.id, atMillis)
    }

    override suspend fun submitPlay(track: Track, atMillis: Long) {
        calls += PlaybackStatsCall("submit", track.id, atMillis)
    }
}

private class FakePlaybackPreferencesStore(
    initialPlaybackVolume: Float? = null,
    autoPlayOnStartup: Boolean = false,
) : PlaybackPreferencesStore {
    private val mutableUseSambaCache = MutableStateFlow(false)
    private val mutablePlaybackVolume = MutableStateFlow(initialPlaybackVolume ?: DEFAULT_PLAYBACK_VOLUME)
    private val mutableAutoPlayOnStartup = MutableStateFlow(autoPlayOnStartup)

    val persistedVolumes = mutableListOf<Float>()

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val playbackVolume: StateFlow<Float> = mutablePlaybackVolume.asStateFlow()
    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        mutableUseSambaCache.value = enabled
    }

    override suspend fun setPlaybackVolume(volume: Float) {
        val normalizedVolume = normalizePlaybackVolume(volume)
        persistedVolumes += normalizedVolume
        mutablePlaybackVolume.value = normalizedVolume
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        mutableAutoPlayOnStartup.value = enabled
    }
}

private class FakePlaybackGateway(
    private val loadFailure: Throwable? = null,
    private val releaseFailure: Throwable? = null,
) : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState())

    val eventLog = mutableListOf<String>()
    val loadCalls = mutableListOf<LoadCall>()
    val seekCalls = mutableListOf<Long>()
    val volumeCalls = mutableListOf<Float>()
    var nextNavidromeAudioQuality: NavidromeAudioQuality? = null
    var nextPlaybackAudioFormat: PlaybackAudioFormat? = null
    var releaseCalls: Int = 0

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    override suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        mutableState.value = mutableState.value.copy(canSeek = false)
        loadFailure?.let { throwable ->
            mutableState.value = mutableState.value.copy(
                isPlaying = false,
                positionMs = startPositionMs,
                durationMs = 0L,
                canSeek = false,
                errorMessage = "访问歌曲失败：${throwable.message ?: throwable::class.simpleName.orEmpty()}",
            )
            throw throwable
        }
        eventLog += "load"
        loadCalls += LoadCall(track, playWhenReady, startPositionMs)
        if (!loadToken.isCurrent()) {
            return
        }
        mutableState.value = mutableState.value.copy(
            isPlaying = playWhenReady,
            positionMs = startPositionMs,
            durationMs = track.durationMs,
            canSeek = true,
            currentNavidromeAudioQuality = nextNavidromeAudioQuality,
            currentPlaybackAudioFormat = nextPlaybackAudioFormat,
            errorMessage = null,
        )
    }

    override suspend fun play() {
        mutableState.value = mutableState.value.copy(isPlaying = true)
    }

    override suspend fun pause() {
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    override suspend fun seekTo(positionMs: Long) {
        seekCalls += positionMs
        mutableState.value = mutableState.value.copy(positionMs = positionMs)
    }

    override suspend fun setVolume(volume: Float) {
        eventLog += "setVolume"
        volumeCalls += volume
        mutableState.value = mutableState.value.copy(volume = volume)
    }

    override suspend fun release() {
        releaseCalls += 1
        releaseFailure?.let { throw it }
    }

    fun updateState(transform: (PlaybackGatewayState) -> PlaybackGatewayState) {
        mutableState.value = transform(mutableState.value)
    }

    fun emitCompletion() {
        mutableState.value = mutableState.value.copy(
            completionCount = mutableState.value.completionCount + 1,
        )
    }
}

private class BlockingPlaybackGateway : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState())

    var nextLoadStarted: CompletableDeferred<Unit>? = null
    var nextLoadGate: CompletableDeferred<Unit>? = null
    val loadCalls = mutableListOf<LoadCall>()
    val appliedTrackIds = mutableListOf<String>()

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    override suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        mutableState.value = mutableState.value.copy(canSeek = false)
        loadCalls += LoadCall(track, playWhenReady, startPositionMs)
        nextLoadStarted?.also { started ->
            nextLoadStarted = null
            started.complete(Unit)
        }
        nextLoadGate?.also { gate ->
            nextLoadGate = null
            gate.await()
        }
        if (!loadToken.isCurrent()) {
            return
        }
        appliedTrackIds += track.id
        mutableState.value = mutableState.value.copy(
            isPlaying = playWhenReady,
            positionMs = startPositionMs,
            durationMs = track.durationMs,
            canSeek = true,
            errorMessage = null,
            errorText = null,
        )
    }

    override suspend fun play() {
        mutableState.value = mutableState.value.copy(isPlaying = true)
    }

    override suspend fun pause() {
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    override suspend fun seekTo(positionMs: Long) {
        mutableState.value = mutableState.value.copy(positionMs = positionMs)
    }

    override suspend fun setVolume(volume: Float) {
        mutableState.value = mutableState.value.copy(volume = volume)
    }

    override suspend fun release() = Unit

    fun updateState(transform: (PlaybackGatewayState) -> PlaybackGatewayState) {
        mutableState.value = transform(mutableState.value)
    }
}

private class FakeSystemPlaybackControlsPlatformService(
    private val closeFailure: Throwable? = null,
) : SystemPlaybackControlsPlatformService {
    var callbacks: SystemPlaybackControlCallbacks = SystemPlaybackControlCallbacks()
    var lastSnapshot: PlaybackSnapshot? = null
    var closeCalls: Int = 0

    override fun bind(callbacks: SystemPlaybackControlCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun updateSnapshot(snapshot: PlaybackSnapshot) {
        lastSnapshot = snapshot
    }

    override suspend fun close() {
        closeCalls += 1
        closeFailure?.let { throw it }
    }
}

private data class LoadCall(
    val track: Track,
    val playWhenReady: Boolean,
    val startPositionMs: Long,
)
