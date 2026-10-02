package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.ArtworkWritePolicy
import top.iwesley.lyn.music.core.model.ArtworkCacheResult

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.ArtworkCachedTarget
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey

@OptIn(ExperimentalCoroutinesApi::class)
class JvmSystemPlaybackControlsPlatformServiceTest {

    @Test
    fun `builds payload from playback snapshot`() {
        val payload = buildJvmNowPlayingPayload(
            snapshot = PlaybackSnapshot(
                queue = listOf(track(title = "Database Title")),
                currentIndex = 0,
                isPlaying = true,
                positionMs = 12_000,
                durationMs = 180_000,
                canSeek = true,
                metadataTitle = "Metadata Title",
                metadataArtistName = "Metadata Artist",
                metadataAlbumTitle = "Metadata Album",
            ),
            artworkPath = "/tmp/cover.jpg",
        )

        assertEquals(
            JvmNowPlayingPayload(
                title = "Metadata Title",
                artist = "Metadata Artist",
                album = "Metadata Album",
                artworkPath = "/tmp/cover.jpg",
                durationMs = 180_000,
                positionMs = 12_000,
                isPlaying = true,
                canSeek = true,
                hasNext = false,
                hasPrevious = false,
            ),
            payload,
        )
    }

    @Test
    fun `payload enables queue navigation when queue has multiple tracks`() {
        val payload = buildJvmNowPlayingPayload(
            snapshot = PlaybackSnapshot(
                queue = listOf(track(id = "1"), track(id = "2")),
                currentIndex = 0,
            ),
            artworkPath = null,
        )

        assertTrue(requireNotNull(payload).hasNext)
        assertTrue(payload.hasPrevious)
    }

    @Test
    fun `payload clamps position to duration`() {
        val payload = buildJvmNowPlayingPayload(
            snapshot = PlaybackSnapshot(
                queue = listOf(track(durationMs = 10_000)),
                currentIndex = 0,
                durationMs = 10_000,
                positionMs = 12_000,
            ),
            artworkPath = null,
        )

        assertEquals(10_000, requireNotNull(payload).positionMs)
    }

    @Test
    fun `payload is null without current track`() {
        assertNull(buildJvmNowPlayingPayload(PlaybackSnapshot(), artworkPath = null))
    }

    @Test
    fun `service clears bridge without current track`() = runTest {
        val bridge = RecordingNowPlayingBridge()
        val service = JvmSystemPlaybackControlsPlatformService(
            bridge = bridge,
            artworkCacheStore = RecordingArtworkCacheStore(),
            scope = this,
        )

        service.updateSnapshot(PlaybackSnapshot())

        assertEquals(1, bridge.clearCount)
        assertTrue(bridge.updates.isEmpty())
    }

    @Test
    fun `service resolves artwork and updates bridge`() = runTest {
        val bridge = RecordingNowPlayingBridge()
        val artworkStore = RecordingArtworkCacheStore(cachedPath = "/tmp/cached-cover.jpg")
        val service = JvmSystemPlaybackControlsPlatformService(
            bridge = bridge,
            artworkCacheStore = artworkStore,
            scope = this,
        )

        service.updateSnapshot(
            PlaybackSnapshot(
                queue = listOf(track(artworkLocator = "https://example.com/cover.jpg")),
                currentIndex = 0,
            ),
        )

        assertEquals(
            listOf("https://example.com/cover.jpg" to "album:source-1:artist:album"),
            artworkStore.requests,
        )
        assertEquals("/tmp/cached-cover.jpg", bridge.updates.single().artworkPath)
    }

    @Test
    fun `service prefers track artwork cache target used by player ui`() = runTest {
        val bridge = RecordingNowPlayingBridge()
        val cachedTarget = ArtworkCachedTarget(
            target = "/tmp/album-cache-cover.jpg",
            version = "1:2",
            isLocalFile = true,
        )
        val track = track(
            artworkLocator = "https://example.com/original-cover.jpg",
            albumId = "album-1",
        )
        val artworkStore = RecordingArtworkCacheStore(
            cachedPath = "/tmp/original-cover.jpg",
            peekTargets = mapOf(requireNotNull(trackArtworkCacheKey(track)) to cachedTarget),
        )
        val service = JvmSystemPlaybackControlsPlatformService(
            bridge = bridge,
            artworkCacheStore = artworkStore,
            scope = this,
        )

        service.updateSnapshot(
            PlaybackSnapshot(
                queue = listOf(track),
                currentIndex = 0,
            ),
        )

        assertTrue(artworkStore.requests.isEmpty())
        assertEquals("/tmp/album-cache-cover.jpg", bridge.updates.single().artworkPath)
    }

    @Test
    fun `service reuses resolved artwork for same locator`() = runTest {
        val bridge = RecordingNowPlayingBridge()
        val artworkStore = RecordingArtworkCacheStore(cachedPath = "/tmp/cached-cover.jpg")
        val service = JvmSystemPlaybackControlsPlatformService(
            bridge = bridge,
            artworkCacheStore = artworkStore,
            scope = this,
        )
        val snapshot = PlaybackSnapshot(
            queue = listOf(track(artworkLocator = "https://example.com/cover.jpg")),
            currentIndex = 0,
            positionMs = 1_000,
        )

        service.updateSnapshot(snapshot)
        service.updateSnapshot(snapshot.copy(positionMs = 2_000))

        assertEquals(1, artworkStore.requests.size)
        assertEquals(2, bridge.updates.size)
    }

    @Test
    fun `service forwards native command callbacks`() = runTest {
        val bridge = RecordingNowPlayingBridge()
        val events = mutableListOf<String>()
        val service = JvmSystemPlaybackControlsPlatformService(
            bridge = bridge,
            artworkCacheStore = RecordingArtworkCacheStore(),
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        service.bind(
            SystemPlaybackControlCallbacks(
                play = { events += "play" },
                pause = { events += "pause" },
                togglePlayPause = { events += "toggle" },
                skipNext = { events += "next" },
                skipPrevious = { events += "previous" },
                seekTo = { positionMs -> events += "seek:$positionMs" },
            ),
        )

        bridge.emit(MacOsNowPlayingCommand.Play)
        bridge.emit(MacOsNowPlayingCommand.Pause)
        bridge.emit(MacOsNowPlayingCommand.TogglePlayPause)
        bridge.emit(MacOsNowPlayingCommand.Next)
        bridge.emit(MacOsNowPlayingCommand.Previous)
        bridge.emit(MacOsNowPlayingCommand.Seek(42_000))
        advanceUntilIdle()

        assertEquals(
            listOf("play", "pause", "toggle", "next", "previous", "seek:42000"),
            events,
        )
    }

    @Test
    fun `service close clears and disposes bridge`() = runTest {
        val bridge = RecordingNowPlayingBridge()
        val service = JvmSystemPlaybackControlsPlatformService(
            bridge = bridge,
            artworkCacheStore = RecordingArtworkCacheStore(),
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

        service.close()

        assertEquals(1, bridge.clearCount)
        assertTrue(bridge.disposed)
    }

    @Test
    fun `factory reports unsupported outside macos`() {
        val registration = createJvmSystemPlaybackControlsPlatformService(
            artworkCacheStore = RecordingArtworkCacheStore(),
            osName = "Linux",
        )

        assertFalse(registration.isSupported)
    }

    @Test
    fun `factory reports unsupported when macos bridge fails to load`() {
        val registration = createJvmSystemPlaybackControlsPlatformService(
            artworkCacheStore = RecordingArtworkCacheStore(),
            osName = "Mac OS X",
            bridgeLoader = { error("missing native bridge") },
        )

        assertFalse(registration.isSupported)
    }

    private class RecordingNowPlayingBridge : MacOsNowPlayingBridge {
        val updates = mutableListOf<JvmNowPlayingPayload>()
        var clearCount = 0
        var disposed = false
        private var handler: (MacOsNowPlayingCommand) -> Unit = {}

        override fun setCommandHandler(handler: (MacOsNowPlayingCommand) -> Unit) {
            this.handler = handler
        }

        override fun update(payload: JvmNowPlayingPayload) {
            updates += payload
        }

        override fun clear() {
            clearCount += 1
        }

        override fun dispose() {
            disposed = true
        }

        fun emit(command: MacOsNowPlayingCommand) {
            handler(command)
        }
    }

    private class RecordingArtworkCacheStore(
        private val cachedPath: String? = null,
        private val peekTargets: Map<String, ArtworkCachedTarget> = emptyMap(),
    ) : ArtworkCacheStore {
        val requests = mutableListOf<Pair<String, String>>()

        override suspend fun cache(locator: String, cacheKey: String, policy: ArtworkWritePolicy): ArtworkCacheResult? {
            requests += locator to cacheKey
            return cachedPath?.let { ArtworkCacheResult(it, true) }
        }

        override suspend fun hasCached(cacheKey: String): Boolean = false

        override fun observeVersion(cacheKey: String): Flow<Long> = flowOf(0L)

        override fun peekCachedTarget(cacheKey: String): ArtworkCachedTarget? = peekTargets[cacheKey]
    }
}

private fun track(
    id: String = "track-1",
    title: String = "Song Title",
    artistName: String? = "Artist",
    albumTitle: String? = "Album",
    durationMs: Long = 60_000,
    artworkLocator: String? = null,
    albumId: String? = null,
): Track {
    return Track(
        id = id,
        sourceId = "source-1",
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        durationMs = durationMs,
        mediaLocator = "file:///tmp/$id.mp3",
        relativePath = "$id.mp3",
        artworkLocator = artworkLocator,
        albumId = albumId,
    )
}
