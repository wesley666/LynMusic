package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.DEFAULT_PLAYBACK_VOLUME
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRepositoriesTest {

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
            gateway.updateState { it.copy(positionMs = 6_000L, isPlaying = true) }
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
        } finally {
            repository.close()
            scope.cancel()
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

            val snapshot = repository.snapshot.value
            assertEquals(listOf("track-3", "track-1", "track-4", "track-2"), trackIds(snapshot.queue))
            assertEquals(listOf("track-1", "track-2", "track-3", "track-4"), trackIds(snapshot.orderedQueue))
            assertEquals(2, snapshot.currentIndex)
            assertEquals("track-4", snapshot.currentTrack?.id)
            assertEquals("track-4", gateway.loadCalls.single().track.id)
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
            database.trackDao().upsertAll(listOf(trackEntity.copy(modifiedAt = 1L)))
            advanceUntilIdle()

            assertEquals("/tmp/original.jpg", repository.snapshot.value.currentTrack?.artworkLocator)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `temporary playback artwork override survives live track refresh`() = runTest {
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

            database.trackDao().upsertAll(listOf(trackEntity.copy(modifiedAt = 1L)))
            advanceUntilIdle()

            assertEquals("/tmp/original.jpg", repository.snapshot.value.currentTrack?.artworkLocator)
            assertEquals("https://img.example.com/override.jpg", repository.snapshot.value.metadataArtworkLocator)
            assertEquals("https://img.example.com/override.jpg", repository.snapshot.value.currentDisplayArtworkLocator)
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

private fun sampleNavidromeTrack(): Track {
    return sampleTrack("nav-track-1", "Remote Song").copy(
        sourceId = "nav-source",
        mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
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

private class FakePlaybackPreferencesStore(
    initialPlaybackVolume: Float? = null,
) : PlaybackPreferencesStore {
    private val mutableUseSambaCache = MutableStateFlow(false)
    private val mutablePlaybackVolume = MutableStateFlow(initialPlaybackVolume ?: DEFAULT_PLAYBACK_VOLUME)

    val persistedVolumes = mutableListOf<Float>()

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val playbackVolume: StateFlow<Float> = mutablePlaybackVolume.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        mutableUseSambaCache.value = enabled
    }

    override suspend fun setPlaybackVolume(volume: Float) {
        val normalizedVolume = normalizePlaybackVolume(volume)
        persistedVolumes += normalizedVolume
        mutablePlaybackVolume.value = normalizedVolume
    }
}

private class FakePlaybackGateway(
    private val loadFailure: Throwable? = null,
) : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState())

    val eventLog = mutableListOf<String>()
    val loadCalls = mutableListOf<LoadCall>()
    val seekCalls = mutableListOf<Long>()
    val volumeCalls = mutableListOf<Float>()
    var nextNavidromeAudioQuality: NavidromeAudioQuality? = null

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    override suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        loadFailure?.let { throwable ->
            mutableState.value = mutableState.value.copy(
                isPlaying = false,
                positionMs = startPositionMs,
                durationMs = 0L,
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
            currentNavidromeAudioQuality = nextNavidromeAudioQuality,
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

    override suspend fun release() = Unit

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
        loadCalls += LoadCall(track, playWhenReady, startPositionMs)
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
        mutableState.value = mutableState.value.copy(positionMs = positionMs)
    }

    override suspend fun setVolume(volume: Float) {
        mutableState.value = mutableState.value.copy(volume = volume)
    }

    override suspend fun release() = Unit
}

private class FakeSystemPlaybackControlsPlatformService : SystemPlaybackControlsPlatformService {
    var callbacks: SystemPlaybackControlCallbacks = SystemPlaybackControlCallbacks()
    var lastSnapshot: PlaybackSnapshot? = null

    override fun bind(callbacks: SystemPlaybackControlCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun updateSnapshot(snapshot: PlaybackSnapshot) {
        lastSnapshot = snapshot
    }

    override suspend fun close() = Unit
}

private data class LoadCall(
    val track: Track,
    val playWhenReady: Boolean,
    val startPositionMs: Long,
)
