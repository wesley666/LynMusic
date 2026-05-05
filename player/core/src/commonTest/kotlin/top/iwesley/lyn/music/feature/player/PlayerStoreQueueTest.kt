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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import top.iwesley.lyn.music.cast.CastDevice
import top.iwesley.lyn.music.cast.CastGateway
import top.iwesley.lyn.music.cast.CastMediaRequest
import top.iwesley.lyn.music.cast.CastMediaUrlResolver
import top.iwesley.lyn.music.cast.CastPlaybackState
import top.iwesley.lyn.music.cast.CastProxySession
import top.iwesley.lyn.music.cast.CastSessionState
import top.iwesley.lyn.music.cast.CastSessionStatus
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.repository.AppliedLyricsResult
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository
import top.iwesley.lyn.music.data.repository.ResolvedLyricsResult

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStoreQueueTest {

    @Test
    fun `queue visibility intent updates state`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.QueueVisibilityChanged(true))
        advanceUntilIdle()

        assertEquals(true, store.state.value.isQueueVisible)
        scope.cancel()
    }

    @Test
    fun `play queue index jumps within current queue and closes drawer`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.QueueVisibilityChanged(true))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.PlayQueueIndex(1))
        advanceUntilIdle()

        assertEquals(1, playbackRepository.lastPlayQueueIndex)
        assertEquals(null, playbackRepository.lastPlayTracks)
        assertEquals("track-2", store.state.value.snapshot.currentTrack?.id)
        assertFalse(store.state.value.isQueueVisible)
        scope.cancel()
    }

    @Test
    fun `null current track closes queue drawer automatically`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.QueueVisibilityChanged(true))
        advanceUntilIdle()
        playbackRepository.updateSnapshot(PlaybackSnapshot(mode = PlaybackMode.ORDER))
        advanceUntilIdle()

        assertFalse(store.state.value.isQueueVisible)
        scope.cancel()
    }

    @Test
    fun `playback error updates transient message once per track and error`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        playbackRepository.updateSnapshot(sampleSnapshot().copy(errorMessage = "访问歌曲失败"))
        advanceUntilIdle()

        assertEquals("访问歌曲失败", store.state.value.message)

        store.dispatch(PlayerIntent.ClearMessage)
        advanceUntilIdle()
        playbackRepository.updateSnapshot(sampleSnapshot().copy(positionMs = 1_000L, errorMessage = "访问歌曲失败"))
        advanceUntilIdle()

        assertEquals(null, store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `same playback error is shown again for a different track`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        playbackRepository.updateSnapshot(sampleSnapshot().copy(errorMessage = "无法播放当前歌曲"))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.ClearMessage)
        advanceUntilIdle()

        playbackRepository.updateSnapshot(sampleSnapshot().copy(currentIndex = 1, errorMessage = "无法播放当前歌曲"))
        advanceUntilIdle()

        assertEquals("无法播放当前歌曲", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `seek intent shows message and does not call repository when track cannot seek`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot().copy(canSeek = false))
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.SeekTo(12_345L))
        advanceUntilIdle()

        assertEquals("歌曲可能正在转码，不支持快进。", store.state.value.message)
        assertEquals(emptyList(), playbackRepository.seekCalls)
        scope.cancel()
    }

    @Test
    fun `seek intent calls repository when track can seek`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot().copy(canSeek = true))
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.SeekTo(12_345L))
        advanceUntilIdle()

        assertEquals(null, store.state.value.message)
        assertEquals(listOf(12_345L), playbackRepository.seekCalls)
        scope.cancel()
    }

    @Test
    fun `invalid queue index leaves playback state unchanged`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.QueueVisibilityChanged(true))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.PlayQueueIndex(99))
        advanceUntilIdle()

        assertEquals(null, playbackRepository.lastPlayTracks)
        assertEquals(null, playbackRepository.lastPlayQueueIndex)
        assertEquals("track-1", store.state.value.snapshot.currentTrack?.id)
        assertEquals(true, store.state.value.isQueueVisible)
        scope.cancel()
    }

    @Test
    fun `sleep timer counts down and pauses playback when finished`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        runCurrent()
        store.dispatch(PlayerIntent.StartSleepTimer(1))
        runCurrent()

        assertEquals(1, store.state.value.sleepTimer.durationMinutes)
        assertEquals(60_000L, store.state.value.sleepTimer.remainingMs)

        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(59_000L, store.state.value.sleepTimer.remainingMs)

        advanceTimeBy(59_000L)
        runCurrent()

        assertEquals(1, playbackRepository.pauseCallCount)
        assertFalse(store.state.value.sleepTimer.isActive)
        scope.cancel()
    }

    @Test
    fun `starting a new sleep timer replaces the previous one`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        runCurrent()
        store.dispatch(PlayerIntent.StartSleepTimer(1))
        runCurrent()
        advanceTimeBy(30_000L)
        runCurrent()
        store.dispatch(PlayerIntent.StartSleepTimer(2))
        runCurrent()
        advanceTimeBy(30_000L)
        runCurrent()

        assertEquals(2, store.state.value.sleepTimer.durationMinutes)
        assertEquals(90_000L, store.state.value.sleepTimer.remainingMs)
        assertEquals(0, playbackRepository.pauseCallCount)
        scope.cancel()
    }

    @Test
    fun `cancel sleep timer prevents automatic pause`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        runCurrent()
        store.dispatch(PlayerIntent.StartSleepTimer(1))
        runCurrent()
        store.dispatch(PlayerIntent.CancelSleepTimer)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()

        assertFalse(store.state.value.sleepTimer.isActive)
        assertEquals(0, playbackRepository.pauseCallCount)
        scope.cancel()
    }

    @Test
    fun `manual apply uses repository returned artwork locator`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val lyricsRepository = RecordingQueueLyricsRepository(
            applyResult = AppliedLyricsResult(
                document = LyricsDocument(
                    lines = listOf(LyricsLine(timestampMs = null, text = "manual line")),
                    sourceId = "direct-source",
                    rawPayload = "manual line",
                ),
                artworkLocator = "/tmp/cache/manual.jpg",
            ),
        )
        val store = PlayerStore(playbackRepository, lyricsRepository, scope)

        advanceUntilIdle()
        store.dispatch(
            PlayerIntent.ApplyManualLyricsCandidate(
                LyricsSearchCandidate(
                    sourceId = "direct-source",
                    sourceName = "Direct",
                    document = LyricsDocument(
                        lines = listOf(LyricsLine(timestampMs = null, text = "candidate line")),
                        sourceId = "direct-source",
                        rawPayload = "candidate line",
                    ),
                    artworkLocator = "https://img.example.com/raw.jpg",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals("/tmp/cache/manual.jpg", playbackRepository.lastArtworkOverride)
        assertEquals("/tmp/cache/manual.jpg", store.state.value.snapshot.currentTrack?.artworkLocator)
        scope.cancel()
    }

    @Test
    fun `playback snapshot keeps up while previous automatic lyrics request is still pending`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val lyricsRepository = DeferredQueueLyricsRepository()
        val store = PlayerStore(playbackRepository, lyricsRepository, scope)

        advanceUntilIdle()
        assertEquals(listOf("track-1"), lyricsRepository.requestedTrackIds)

        playbackRepository.updateSnapshot(sampleSnapshot().copy(currentIndex = 2))
        advanceUntilIdle()

        assertEquals("track-3", store.state.value.snapshot.currentTrack?.id)
        assertEquals(true, store.state.value.isLyricsLoading)
        assertEquals(listOf("track-1", "track-3"), lyricsRepository.requestedTrackIds)

        lyricsRepository.complete(
            trackId = "track-1",
            result = resolvedLyricsResult(
                sourceId = "source-track-1",
                line = "lyrics for track-1",
                artworkLocator = "/tmp/track-1.jpg",
            ),
        )
        advanceUntilIdle()

        assertEquals("track-3", store.state.value.snapshot.currentTrack?.id)
        assertEquals(null, store.state.value.lyrics)
        assertEquals(null, playbackRepository.lastArtworkOverride)

        lyricsRepository.complete(
            trackId = "track-3",
            result = resolvedLyricsResult(
                sourceId = "source-track-3",
                line = "lyrics for track-3",
                artworkLocator = "/tmp/track-3.jpg",
            ),
        )
        advanceUntilIdle()

        assertEquals("track-3", store.state.value.snapshot.currentTrack?.id)
        assertEquals("lyrics for track-3", store.state.value.lyrics?.rawPayload)
        assertEquals("/tmp/track-3.jpg", playbackRepository.lastArtworkOverride)
        scope.cancel()
    }

    @Test
    fun `automatic lyrics artwork does not override playback artwork when album cache exists`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val lyricsRepository = DeferredQueueLyricsRepository()
        val artworkCacheStore = FakePlayerArtworkCacheStore(
            cachedKeys = setOf("album:local-1:artist a:album a"),
        )
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = lyricsRepository,
            storeScope = scope,
            artworkCacheStore = artworkCacheStore,
        )

        advanceUntilIdle()
        lyricsRepository.complete(
            trackId = "track-1",
            result = resolvedLyricsResult(
                sourceId = "source-track-1",
                line = "lyrics for track-1",
                artworkLocator = "/tmp/track-1-auto.jpg",
            ),
        )
        advanceUntilIdle()

        assertEquals(null, playbackRepository.lastArtworkOverride)
        assertEquals(listOf("album:local-1:artist a:album a"), artworkCacheStore.checkedKeys)
        assertEquals("lyrics for track-1", store.state.value.lyrics?.rawPayload)
        scope.cancel()
    }

    @Test
    fun `automatic lyrics artwork overrides playback artwork when navidrome album cache is replaceable placeholder`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleNavidromeSnapshot())
        val lyricsRepository = DeferredQueueLyricsRepository()
        val artworkCacheStore = FakePlayerArtworkCacheStore(
            cachedKeys = setOf("album:nav-source:artist a:album a"),
            replaceablePlaceholderKeys = setOf("album:nav-source:artist a:album a"),
        )
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = lyricsRepository,
            storeScope = scope,
            artworkCacheStore = artworkCacheStore,
        )

        advanceUntilIdle()
        lyricsRepository.complete(
            trackId = "track-1",
            result = resolvedLyricsResult(
                sourceId = "source-track-1",
                line = "lyrics for track-1",
                artworkLocator = "/tmp/track-1-auto.jpg",
            ),
        )
        advanceUntilIdle()

        assertEquals("/tmp/track-1-auto.jpg", playbackRepository.lastArtworkOverride)
        assertEquals(listOf("album:nav-source:artist a:album a"), artworkCacheStore.checkedPlaceholderKeys)
        assertEquals("lyrics for track-1", store.state.value.lyrics?.rawPayload)
        scope.cancel()
    }

    @Test
    fun `direct http cast does not request proxy resolver`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val track = sampleTrack("track-http", "Http Song").copy(
            mediaLocator = "https://example.com/song.mp3",
            relativePath = "song.mp3",
        )
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot().copy(queue = listOf(track)))
        val castGateway = FakePlayerCastGateway()
        val resolver = FakeCastMediaUrlResolver()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = resolver,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()

        assertEquals(0, resolver.resolveCallCount)
        assertEquals("https://example.com/song.mp3", castGateway.lastRequest?.uri)
        scope.cancel()
    }

    @Test
    fun `non direct cast uses proxy resolver and closes session on stop`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val castGateway = FakePlayerCastGateway()
        val proxySession = FakeCastProxySession()
        val resolver = FakeCastMediaUrlResolver(proxySession)
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = resolver,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.StopCast)
        advanceUntilIdle()

        assertEquals(1, resolver.resolveCallCount)
        assertEquals(proxySession.uri, castGateway.lastRequest?.uri)
        assertEquals(proxySession.mimeType, castGateway.lastRequest?.mimeType)
        assertEquals(1, proxySession.closeCallCount)
        scope.cancel()
    }

    @Test
    fun `cast success pauses local playback and effective snapshot mirrors remote playback`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(
            sampleSnapshot().copy(isPlaying = true, canSeek = true),
        )
        val castGateway = FakePlayerCastGateway()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = FakeCastMediaUrlResolver(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()
        castGateway.updatePlayback(
            CastPlaybackState(
                positionMs = 12_345L,
                durationMs = 180_000L,
                isPlaying = false,
                canSeek = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, playbackRepository.pauseCallCount)
        assertEquals(false, store.state.value.effectiveSnapshot.isPlaying)
        assertEquals(12_345L, store.state.value.effectiveSnapshot.positionMs)
        assertEquals(emptyList(), playbackRepository.seekCalls)
        scope.cancel()
    }

    @Test
    fun `opening cast sheet while casting keeps remote playback mirrored`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(
            sampleSnapshot().copy(isPlaying = true, canSeek = true),
        )
        val castGateway = FakePlayerCastGateway()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = FakeCastMediaUrlResolver(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenCastSheet)
        advanceUntilIdle()
        castGateway.updatePlayback(
            CastPlaybackState(
                positionMs = 24_000L,
                durationMs = 180_000L,
                isPlaying = true,
                canSeek = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, castGateway.startDiscoveryCallCount)
        assertEquals(CastSessionStatus.Casting, store.state.value.castState.status)
        assertEquals(true, store.state.value.isCastSheetVisible)
        assertEquals(24_000L, store.state.value.effectiveSnapshot.positionMs)
        scope.cancel()
    }

    @Test
    fun `recasting queue item keeps effective snapshot on target while connecting`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(
            sampleSnapshot().copy(
                isPlaying = true,
                canSeek = true,
                positionMs = 73_000L,
            ),
        )
        val castGateway = FakePlayerCastGateway()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = FakeCastMediaUrlResolver(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()
        castGateway.completeCastImmediately = false
        store.dispatch(PlayerIntent.SkipNext)
        advanceUntilIdle()

        assertEquals(CastSessionStatus.Connecting, store.state.value.castState.status)
        assertEquals("track-1", store.state.value.snapshot.currentTrack?.id)
        assertEquals("track-2", store.state.value.effectiveSnapshot.currentTrack?.id)
        assertEquals("Second Song", store.state.value.effectiveSnapshot.currentDisplayTitle)
        assertEquals(0L, store.state.value.effectiveSnapshot.positionMs)
        assertEquals(false, store.state.value.effectiveSnapshot.isPlaying)
        assertEquals("Second Song", castGateway.lastRequest?.title)
        scope.cancel()
    }

    @Test
    fun `recasting queue item reloads lyrics for effective track without changing local snapshot`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(
            sampleSnapshot().copy(isPlaying = true, canSeek = true),
        )
        val lyricsRepository = DeferredQueueLyricsRepository()
        val castGateway = FakePlayerCastGateway()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = lyricsRepository,
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = FakeCastMediaUrlResolver(),
        )

        advanceUntilIdle()
        lyricsRepository.complete(
            trackId = "track-1",
            result = resolvedLyricsResult(sourceId = "source-track-1", line = "lyrics for track-1"),
        )
        advanceUntilIdle()
        assertEquals("lyrics for track-1", store.state.value.lyrics?.rawPayload)

        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()
        castGateway.completeCastImmediately = false
        store.dispatch(PlayerIntent.SkipNext)
        advanceUntilIdle()

        assertEquals("track-1", store.state.value.snapshot.currentTrack?.id)
        assertEquals("track-2", store.state.value.effectiveSnapshot.currentTrack?.id)
        assertEquals(listOf("track-1", "track-2"), lyricsRepository.requestedTrackIds)
        assertEquals(null, store.state.value.lyrics)

        lyricsRepository.complete(
            trackId = "track-2",
            result = resolvedLyricsResult(sourceId = "source-track-2", line = "lyrics for track-2"),
        )
        advanceUntilIdle()

        assertEquals("track-1", store.state.value.snapshot.currentTrack?.id)
        assertEquals("track-2", store.state.value.effectiveSnapshot.currentTrack?.id)
        assertEquals("lyrics for track-2", store.state.value.lyrics?.rawPayload)
        assertEquals(null, playbackRepository.lastArtworkOverride)
        scope.cancel()
    }

    @Test
    fun `play tracks uses local playback when not casting`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val castGateway = FakePlayerCastGateway()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = FakeCastMediaUrlResolver(),
        )
        val tracks = listOf(
            sampleTrack("track-4", "Fourth Song"),
            sampleTrack("track-5", "Fifth Song"),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.PlayTracks(tracks, 1))
        advanceUntilIdle()

        assertEquals(tracks, playbackRepository.lastPlayTracks)
        assertEquals(1, playbackRepository.lastPlayStartIndex)
        assertEquals(0, playbackRepository.prepareExternalPlaybackQueueCallCount)
        assertEquals(null, castGateway.lastRequest)
        assertEquals("track-5", store.state.value.snapshot.currentTrack?.id)
        scope.cancel()
    }

    @Test
    fun `play tracks while casting prepares external queue and recasts without local playback`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(
            sampleSnapshot().copy(isPlaying = true, canSeek = true),
        )
        val castGateway = FakePlayerCastGateway()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = FakeCastMediaUrlResolver(),
        )
        val tracks = listOf(
            sampleTrack("track-4", "Fourth Song"),
            sampleTrack("track-5", "Fifth Song"),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.PlayTracks(tracks, 1))
        advanceUntilIdle()

        assertEquals(null, playbackRepository.lastPlayTracks)
        assertEquals(1, playbackRepository.prepareExternalPlaybackQueueCallCount)
        assertEquals(2, playbackRepository.pauseCallCount)
        assertEquals("track-5", playbackRepository.snapshot.value.currentTrack?.id)
        assertEquals("track-5", store.state.value.snapshot.currentTrack?.id)
        assertEquals("track-5", store.state.value.effectiveSnapshot.currentTrack?.id)
        assertEquals("Fifth Song", castGateway.lastRequest?.title)
        assertEquals(1, store.state.value.castQueueIndex)
        scope.cancel()
    }

    @Test
    fun `cast controls route play pause and seek to cast gateway`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(
            sampleSnapshot().copy(isPlaying = true, canSeek = true),
        )
        val castGateway = FakePlayerCastGateway()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = FakeCastMediaUrlResolver(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.TogglePlayPause)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.SeekTo(45_000L))
        advanceUntilIdle()

        assertEquals(1, castGateway.pauseCastCallCount)
        assertEquals(listOf(45_000L), castGateway.seekCastCalls)
        assertEquals(emptyList(), playbackRepository.seekCalls)
        scope.cancel()
    }

    @Test
    fun `remote ended automatically casts next queue item`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(
            sampleSnapshot().copy(isPlaying = true, canSeek = true),
        )
        val castGateway = FakePlayerCastGateway()
        val proxySession = FakeCastProxySession()
        val resolver = FakeCastMediaUrlResolver(proxySession)
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = resolver,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()
        castGateway.updatePlayback(
            CastPlaybackState(
                positionMs = 180_000L,
                durationMs = 180_000L,
                isPlaying = false,
                canSeek = true,
                isEnded = true,
            ),
        )
        advanceUntilIdle()

        assertEquals("Second Song", castGateway.lastRequest?.title)
        assertEquals(1, store.state.value.castQueueIndex)
        assertEquals(2, resolver.resolveCallCount)
        assertEquals(1, proxySession.closeCallCount)
        assertEquals(null, playbackRepository.lastPlayQueueIndex)
        scope.cancel()
    }

    @Test
    fun `stop cast resumes local playback from remote position`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(
            sampleSnapshot().copy(isPlaying = true, canSeek = true),
        )
        val castGateway = FakePlayerCastGateway()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = NoopQueueLyricsRepository(),
            storeScope = scope,
            castGateway = castGateway,
            castMediaUrlResolver = FakeCastMediaUrlResolver(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.CastToDevice("device-1"))
        advanceUntilIdle()
        castGateway.updatePlayback(
            CastPlaybackState(
                positionMs = 42_000L,
                durationMs = 180_000L,
                isPlaying = true,
                canSeek = true,
            ),
        )
        advanceUntilIdle()
        store.dispatch(PlayerIntent.StopCast)
        advanceUntilIdle()

        assertEquals(listOf(42_000L), playbackRepository.seekCalls)
        assertEquals(1, playbackRepository.togglePlayPauseCallCount)
        assertEquals(1, playbackRepository.pauseCallCount)
        assertEquals(null, store.state.value.castQueueIndex)
        scope.cancel()
    }

    private fun sampleSnapshot(): PlaybackSnapshot {
        return PlaybackSnapshot(
            queue = listOf(
                sampleTrack("track-1", "First Song"),
                sampleTrack("track-2", "Second Song"),
                sampleTrack("track-3", "Third Song"),
            ),
            currentIndex = 0,
            mode = PlaybackMode.ORDER,
            durationMs = 180_000L,
        )
    }

    private fun sampleNavidromeSnapshot(): PlaybackSnapshot {
        return sampleSnapshot().copy(
            queue = listOf(
                sampleNavidromeTrack("track-1", "First Song"),
                sampleNavidromeTrack("track-2", "Second Song"),
                sampleNavidromeTrack("track-3", "Third Song"),
            ),
        )
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

    private fun sampleNavidromeTrack(id: String, title: String): Track {
        return Track(
            id = id,
            sourceId = "nav-source",
            title = title,
            artistName = "Artist A",
            albumTitle = "Album A",
            durationMs = 180_000L,
            mediaLocator = buildNavidromeSongLocator("nav-source", id),
            relativePath = "$title.flac",
        )
    }
}

private class FakePlayerArtworkCacheStore(
    private val cachedKeys: Set<String>,
    private val replaceablePlaceholderKeys: Set<String> = emptySet(),
) : ArtworkCacheStore {
    val checkedKeys = mutableListOf<String>()
    val checkedPlaceholderKeys = mutableListOf<String>()

    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? = locator

    override suspend fun hasCached(cacheKey: String): Boolean {
        checkedKeys += cacheKey
        return cacheKey in cachedKeys
    }

    override suspend fun hasReplaceableNavidromePlaceholderCached(cacheKey: String): Boolean {
        checkedPlaceholderKeys += cacheKey
        return cacheKey in replaceablePlaceholderKeys
    }
}

private class FakePlayerCastGateway : CastGateway {
    private val mutableState = MutableStateFlow(
        CastSessionState(
            devices = listOf(CastDevice(id = "device-1", name = "TV")),
        ),
    )

    var lastRequest: CastMediaRequest? = null
        private set
    var playCastCallCount: Int = 0
        private set
    var pauseCastCallCount: Int = 0
        private set
    var startDiscoveryCallCount: Int = 0
        private set
    val seekCastCalls = mutableListOf<Long>()
    var completeCastImmediately: Boolean = true

    override val state: StateFlow<CastSessionState> = mutableState.asStateFlow()
    override val isSupported: Boolean = true

    override suspend fun startDiscovery() {
        startDiscoveryCallCount += 1
    }
    override suspend fun stopDiscovery() = Unit

    override suspend fun cast(deviceId: String, request: CastMediaRequest) {
        lastRequest = request
        mutableState.value = mutableState.value.copy(
            status = CastSessionStatus.Connecting,
            selectedDeviceId = deviceId,
            playback = null,
        )
        if (!completeCastImmediately) return
        mutableState.value = mutableState.value.copy(
            status = CastSessionStatus.Casting,
            selectedDeviceId = deviceId,
            playback = CastPlaybackState(
                durationMs = request.durationMs,
                isPlaying = true,
                canSeek = request.durationMs > 0L,
            ),
        )
    }

    override suspend fun playCast() {
        playCastCallCount += 1
        mutableState.value = mutableState.value.copy(
            playback = mutableState.value.playback?.copy(isPlaying = true)
                ?: CastPlaybackState(isPlaying = true),
        )
    }

    override suspend fun pauseCast() {
        pauseCastCallCount += 1
        mutableState.value = mutableState.value.copy(
            playback = mutableState.value.playback?.copy(isPlaying = false)
                ?: CastPlaybackState(isPlaying = false),
        )
    }

    override suspend fun seekCast(positionMs: Long) {
        seekCastCalls += positionMs
        mutableState.value = mutableState.value.copy(
            playback = mutableState.value.playback?.copy(positionMs = positionMs)
                ?: CastPlaybackState(positionMs = positionMs),
        )
    }

    override suspend fun stopCast() {
        mutableState.value = mutableState.value.copy(status = CastSessionStatus.Idle, playback = null)
    }

    override suspend fun release() = Unit

    fun updatePlayback(playback: CastPlaybackState) {
        mutableState.value = mutableState.value.copy(playback = playback)
    }
}

private class FakeCastMediaUrlResolver(
    private val session: CastProxySession = FakeCastProxySession(),
) : CastMediaUrlResolver {
    var resolveCallCount: Int = 0
        private set

    override suspend fun resolve(track: Track, snapshot: PlaybackSnapshot): Result<CastProxySession> {
        resolveCallCount += 1
        return Result.success(session)
    }

    override suspend fun release() = Unit
}

private class FakeCastProxySession : CastProxySession {
    override val uri: String = "http://192.168.1.2:3000/cast/stream/token"
    override val mimeType: String = "audio/flac"
    override val durationMs: Long = 180_000L

    var closeCallCount: Int = 0
        private set

    override suspend fun close() {
        closeCallCount += 1
    }
}

private class FakeQueuePlaybackRepository(
    initialSnapshot: PlaybackSnapshot,
) : PlaybackRepository {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)

    var lastPlayTracks: List<Track>? = null
        private set
    var lastPlayStartIndex: Int? = null
        private set
    var lastPlayQueueIndex: Int? = null
        private set
    var prepareExternalPlaybackQueueCallCount: Int = 0
        private set
    var lastArtworkOverride: String? = null
        private set
    var pauseCallCount: Int = 0
        private set
    var togglePlayPauseCallCount: Int = 0
        private set
    val seekCalls = mutableListOf<Long>()

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    fun updateSnapshot(snapshot: PlaybackSnapshot) {
        mutableSnapshot.value = snapshot
    }

    override suspend fun hydratePersistedQueueIfNeeded() = Unit

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) {
        lastPlayTracks = tracks
        lastPlayStartIndex = startIndex
        val targetIndex = startIndex.coerceIn(0, tracks.lastIndex)
        mutableSnapshot.value = mutableSnapshot.value.copy(
            queue = tracks,
            currentIndex = targetIndex,
            durationMs = tracks[targetIndex].durationMs,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
            metadataArtworkLocator = null,
        )
    }

    override suspend fun prepareExternalPlaybackQueue(tracks: List<Track>, startIndex: Int): PlaybackSnapshot? {
        prepareExternalPlaybackQueueCallCount += 1
        if (tracks.isEmpty()) return null
        val targetIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val snapshot = mutableSnapshot.value.copy(
            queue = tracks,
            orderedQueue = tracks,
            currentIndex = targetIndex,
            isPlaying = false,
            positionMs = 0L,
            durationMs = tracks[targetIndex].durationMs,
            canSeek = false,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
            metadataArtworkLocator = null,
            currentNavidromeAudioQuality = null,
            currentPlaybackAudioFormat = null,
        )
        mutableSnapshot.value = snapshot
        return snapshot
    }

    override suspend fun playQueueIndex(index: Int) {
        lastPlayQueueIndex = index
        val target = mutableSnapshot.value.queue.getOrNull(index) ?: return
        mutableSnapshot.value = mutableSnapshot.value.copy(
            currentIndex = index,
            durationMs = target.durationMs,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
            metadataArtworkLocator = null,
        )
    }

    override suspend fun togglePlayPause() {
        togglePlayPauseCallCount += 1
        mutableSnapshot.value = mutableSnapshot.value.copy(isPlaying = !mutableSnapshot.value.isPlaying)
    }

    override suspend fun pause() {
        pauseCallCount += 1
        mutableSnapshot.value = mutableSnapshot.value.copy(isPlaying = false)
    }

    override suspend fun skipNext() = Unit

    override suspend fun skipPrevious() = Unit

    override suspend fun seekTo(positionMs: Long) {
        seekCalls += positionMs
        mutableSnapshot.value = mutableSnapshot.value.copy(positionMs = positionMs)
    }

    override suspend fun setVolume(volume: Float) = Unit

    override suspend fun cycleMode() = Unit

    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?) {
        lastArtworkOverride = artworkLocator
        val currentIndex = mutableSnapshot.value.currentIndex
        val currentTrack = mutableSnapshot.value.currentTrack ?: return
        val updatedQueue = mutableSnapshot.value.queue.toMutableList()
        updatedQueue[currentIndex] = currentTrack.copy(artworkLocator = artworkLocator ?: currentTrack.artworkLocator)
        mutableSnapshot.value = mutableSnapshot.value.copy(queue = updatedQueue)
    }

    override suspend fun close() = Unit
}

private class NoopQueueLyricsRepository : LyricsRepository {
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? = null

    override suspend fun searchLyricsCandidates(
        track: Track,
        includeTrackProvidedCandidate: Boolean,
    ): List<LyricsSearchCandidate> = emptyList()

    override suspend fun applyLyricsCandidate(
        trackId: String,
        candidate: LyricsSearchCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in queue tests")
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        error("Not used in queue tests")
    }

    override suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in queue tests")
    }
}

private class RecordingQueueLyricsRepository(
    private val applyResult: AppliedLyricsResult,
) : LyricsRepository {
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? = null

    override suspend fun searchLyricsCandidates(
        track: Track,
        includeTrackProvidedCandidate: Boolean,
    ): List<LyricsSearchCandidate> = emptyList()

    override suspend fun applyLyricsCandidate(
        trackId: String,
        candidate: LyricsSearchCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        return applyResult
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        error("Not used in queue tests")
    }

    override suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in queue tests")
    }
}

private class DeferredQueueLyricsRepository : LyricsRepository {
    private val pendingResults = mutableMapOf<String, CompletableDeferred<ResolvedLyricsResult?>>()

    val requestedTrackIds = mutableListOf<String>()

    fun complete(trackId: String, result: ResolvedLyricsResult?) {
        pendingResults.getOrPut(trackId) { CompletableDeferred() }.complete(result)
    }

    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? {
        requestedTrackIds += track.id
        return pendingResults.getOrPut(track.id) { CompletableDeferred() }.await()
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
        error("Not used in queue tests")
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        error("Not used in queue tests")
    }

    override suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in queue tests")
    }
}

private fun resolvedLyricsResult(
    sourceId: String,
    line: String,
    artworkLocator: String? = null,
): ResolvedLyricsResult {
    return ResolvedLyricsResult(
        document = LyricsDocument(
            lines = listOf(LyricsLine(timestampMs = null, text = line)),
            sourceId = sourceId,
            rawPayload = line,
        ),
        artworkLocator = artworkLocator,
    )
}
