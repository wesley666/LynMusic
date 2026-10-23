package top.iwesley.lyn.music


import kotlinx.coroutines.test.runTest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.buildWebDavLocator
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.online.OnlineFavoritesState

class OnlineTrackBindingTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }











































    @Test
    fun `player favorite binding uses online remote favorite hint`() = runTest {
        val track = sampleNavidromeTrack("nav").copy(remoteFavoriteHint = true)

        val binding = playerFavoriteBinding(
            track = track,
            localFavoriteTrackIds = emptySet(),
            onlineFavoritesState = OnlineFavoritesState(),
            importState = importStateWithOnlineSource(),
        )

        assertTrue(binding.isFavorite)
        assertEquals("source-1", binding.onlineSourceId)
        assertTrue(binding.isFavoriteKnown)
        assertTrue(binding.canToggleFavorite)
    }

    @Test
    fun `player favorite binding override wins over online hint`() = runTest {
        val track = sampleNavidromeTrack("nav").copy(remoteFavoriteHint = true)

        val binding = playerFavoriteBinding(
            track = track,
            localFavoriteTrackIds = emptySet(),
            onlineFavoritesState = OnlineFavoritesState(
                favoriteOverridesBySourceId = mapOf(
                    "source-1" to mapOf("nav" to false),
                ),
            ),
            importState = importStateWithOnlineSource(),
        )

        assertFalse(binding.isFavorite)
        assertEquals("source-1", binding.onlineSourceId)
        assertTrue(binding.isFavoriteKnown)
        assertTrue(binding.canToggleFavorite)
    }

    @Test
    fun `player favorite binding allows toggling online loading gap as unknown`() = runTest {
        val track = sampleNavidromeTrack("nav")

        val binding = playerFavoriteBinding(
            track = track,
            localFavoriteTrackIds = emptySet(),
            onlineFavoritesState = OnlineFavoritesState(
                sourceId = "source-1",
                isLoading = true,
                tracks = emptyList(),
            ),
            importState = importStateWithOnlineSource(),
        )

        assertFalse(binding.isFavorite)
        assertFalse(binding.isFavoriteKnown)
        assertTrue(binding.canToggleFavorite)
        assertEquals("source-1", binding.onlineSourceId)
    }

    @Test
    fun `player favorite binding allows toggling unselected online favorites source as unknown`() = runTest {
        val track = sampleNavidromeTrack("nav")

        val binding = playerFavoriteBinding(
            track = track,
            localFavoriteTrackIds = emptySet(),
            onlineFavoritesState = OnlineFavoritesState(sourceId = null),
            importState = importStateWithOnlineSource(),
        )

        assertFalse(binding.isFavorite)
        assertFalse(binding.isFavoriteKnown)
        assertTrue(binding.canToggleFavorite)
        assertEquals("source-1", binding.onlineSourceId)
    }

    @Test
    fun `player favorite binding allows toggling mismatched online favorites source as unknown`() = runTest {
        val track = sampleNavidromeTrack("nav")

        val binding = playerFavoriteBinding(
            track = track,
            localFavoriteTrackIds = emptySet(),
            onlineFavoritesState = OnlineFavoritesState(sourceId = "other-source"),
            importState = importStateWithOnlineSource(),
        )

        assertFalse(binding.isFavorite)
        assertFalse(binding.isFavoriteKnown)
        assertTrue(binding.canToggleFavorite)
        assertEquals("source-1", binding.onlineSourceId)
    }

    @Test
    fun `player favorite binding allows toggling paginated online favorites as unknown`() = runTest {
        val track = sampleNavidromeTrack("nav")

        val binding = playerFavoriteBinding(
            track = track,
            localFavoriteTrackIds = emptySet(),
            onlineFavoritesState = OnlineFavoritesState(
                sourceId = "source-1",
                tracks = listOf(sampleNavidromeTrack("loaded-favorite")),
                canLoadMore = true,
            ),
            importState = importStateWithOnlineSource(),
        )

        assertFalse(binding.isFavorite)
        assertFalse(binding.isFavoriteKnown)
        assertTrue(binding.canToggleFavorite)
        assertEquals("source-1", binding.onlineSourceId)
    }

    @Test
    fun `player favorite binding allows toggling loading more online favorites as unknown`() = runTest {
        val track = sampleNavidromeTrack("nav")

        val binding = playerFavoriteBinding(
            track = track,
            localFavoriteTrackIds = emptySet(),
            onlineFavoritesState = OnlineFavoritesState(
                sourceId = "source-1",
                tracks = listOf(sampleNavidromeTrack("loaded-favorite")),
                isLoadingMore = true,
                canLoadMore = true,
            ),
            importState = importStateWithOnlineSource(),
        )

        assertFalse(binding.isFavorite)
        assertFalse(binding.isFavoriteKnown)
        assertTrue(binding.canToggleFavorite)
        assertEquals("source-1", binding.onlineSourceId)
    }

    @Test
    fun `player favorite binding treats fully loaded online absence as known`() = runTest {
        val track = sampleNavidromeTrack("nav")

        val binding = playerFavoriteBinding(
            track = track,
            localFavoriteTrackIds = emptySet(),
            onlineFavoritesState = OnlineFavoritesState(
                sourceId = "source-1",
                tracks = listOf(sampleNavidromeTrack("loaded-favorite")),
                isLoading = false,
                isLoadingMore = false,
                canLoadMore = false,
            ),
            importState = importStateWithOnlineSource(),
        )

        assertFalse(binding.isFavorite)
        assertTrue(binding.isFavoriteKnown)
        assertTrue(binding.canToggleFavorite)
        assertEquals("source-1", binding.onlineSourceId)
    }

    @Test
    fun `player favorite binding keeps local favorites independent of online overrides`() = runTest {
        val track = sampleTrack("local")

        val binding = playerFavoriteBinding(
            track = track,
            localFavoriteTrackIds = setOf("local"),
            onlineFavoritesState = OnlineFavoritesState(
                favoriteOverridesBySourceId = mapOf(
                    "source-1" to mapOf("local" to false),
                ),
            ),
            importState = importStateWithOnlineSource(),
        )

        assertTrue(binding.isFavorite)
        assertNull(binding.onlineSourceId)
        assertTrue(binding.isFavoriteKnown)
        assertTrue(binding.canToggleFavorite)
    }













    private fun completedDownload(
        trackId: String = "song-1",
        quality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
        localMediaLocator: String? = "/tmp/offline/song.mp3",
        status: OfflineDownloadStatus = OfflineDownloadStatus.Completed,
        downloadedBytes: Long = 1_024L,
    ): OfflineDownload {
        return OfflineDownload(
            trackId = trackId,
            sourceId = "source-1",
            originalMediaLocator = "remote://$trackId",
            localMediaLocator = localMediaLocator,
            quality = quality,
            status = status,
            downloadedBytes = downloadedBytes,
        )
    }

    private fun sampleNavidromeTrack(
        id: String,
        sizeBytes: Long = 0L,
        durationMs: Long = 0L,
    ): Track {
        return sampleTrack(
            id = id,
            mediaLocator = buildNavidromeSongLocator("source-1", id),
            sizeBytes = sizeBytes,
            durationMs = durationMs,
        )
    }

    private fun sampleWebDavTrack(
        id: String,
        sizeBytes: Long = 0L,
    ): Track {
        return sampleTrack(
            id = id,
            mediaLocator = buildWebDavLocator("source-1", "$id.mp3"),
            sizeBytes = sizeBytes,
        )
    }

    private fun sampleTrack(
        id: String,
        mediaLocator: String = "file:///music/$id.mp3",
        sizeBytes: Long = 0L,
        durationMs: Long = 0L,
    ): Track {
        return Track(
            id = id,
            sourceId = "source-1",
            title = id,
            durationMs = durationMs,
            mediaLocator = mediaLocator,
            relativePath = "$id.mp3",
            sizeBytes = sizeBytes,
        )
    }

    private fun importStateWithOnlineSource(): ImportState {
        return ImportState(
            capabilities = testPlatformCapabilities(),
            sources = listOf(
                SourceWithStatus(
                    source = ImportSource(
                        id = "source-1",
                        type = ImportSourceType.NAVIDROME,
                        label = "Navidrome",
                        rootReference = "https://navidrome.example",
                        enabled = true,
                        indexMode = ImportSourceIndexMode.ONLINE,
                    ),
                ),
            ),
        )
    }

    private fun testPlatformCapabilities(): PlatformCapabilities {
        return PlatformCapabilities(
            supportsLocalFolderImport = true,
            supportsSambaImport = true,
            supportsWebDavImport = true,
            supportsNavidromeImport = true,
            supportsSystemMediaControls = true,
        )
    }
}
