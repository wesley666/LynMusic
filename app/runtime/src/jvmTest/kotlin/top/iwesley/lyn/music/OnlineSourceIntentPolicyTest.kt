package top.iwesley.lyn.music

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.feature.online.*

class OnlineSourceIntentPolicyTest {
    @Test
    fun `passive online library intents do not require store startup`() = runTest {
        val clearSource = OnlineLibraryIntent.SelectSource(sourceId = null)
        val temporaryClearSource = OnlineLibraryIntent.SelectSource(sourceId = null, persist = false)

        assertTrue(clearSource.isPassiveLibrarySourceClear())
        assertFalse(clearSource.shouldStartOnlineLibraryStore())
        assertFalse(
            clearSource.shouldClearRememberedOnlineLibrarySource(
                currentOnlineSourceId = null,
                rememberedOnlineSourceId = null,
            ),
        )
        assertTrue(
            clearSource.shouldClearRememberedOnlineLibrarySource(
                currentOnlineSourceId = null,
                rememberedOnlineSourceId = "nav-online",
            ),
        )
        assertFalse(
            clearSource.shouldClearRememberedOnlineLibrarySource(
                currentOnlineSourceId = "nav-online",
                rememberedOnlineSourceId = "nav-online",
            ),
        )
        assertFalse(temporaryClearSource.isPassiveLibrarySourceClear())
        assertFalse(temporaryClearSource.shouldStartOnlineLibraryStore())
        assertFalse(OnlineLibraryIntent.ClearError.shouldStartOnlineLibraryStore())
        assertTrue(OnlineLibraryIntent.SelectSource("nav-online").shouldStartOnlineLibraryStore())
        assertTrue(OnlineLibraryIntent.Refresh.shouldStartOnlineLibraryStore())
    }

    @Test
    fun `passive online favorites intents do not require store startup`() = runTest {
        val clearSource = OnlineFavoritesIntent.SelectSource(sourceId = null)
        val temporaryClearSource = OnlineFavoritesIntent.SelectSource(sourceId = null, persist = false)

        assertTrue(clearSource.isPassiveFavoritesSourceClear())
        assertFalse(clearSource.shouldStartOnlineFavoritesStore())
        assertFalse(
            clearSource.shouldClearRememberedOnlineFavoritesSource(
                currentOnlineSourceId = null,
                rememberedOnlineSourceId = null,
            ),
        )
        assertTrue(
            clearSource.shouldClearRememberedOnlineFavoritesSource(
                currentOnlineSourceId = null,
                rememberedOnlineSourceId = "nav-online",
            ),
        )
        assertFalse(temporaryClearSource.isPassiveFavoritesSourceClear())
        assertFalse(temporaryClearSource.shouldStartOnlineFavoritesStore())
        assertFalse(OnlineFavoritesIntent.ClearMessage.shouldStartOnlineFavoritesStore())
        assertTrue(OnlineFavoritesIntent.SelectSource("nav-online").shouldStartOnlineFavoritesStore())
        assertTrue(OnlineFavoritesIntent.Refresh.shouldStartOnlineFavoritesStore())
    }

    @Test
    fun `passive online playlists intents do not require store startup`() = runTest {
        val clearSource = OnlinePlaylistsIntent.SelectSource(sourceId = null)
        val temporaryClearSource = OnlinePlaylistsIntent.SelectSource(sourceId = null, persist = false)

        assertTrue(clearSource.isPassivePlaylistsSourceClear())
        assertFalse(clearSource.shouldStartOnlinePlaylistsStore())
        assertFalse(
            clearSource.shouldClearRememberedOnlinePlaylistsSource(
                currentOnlineSourceId = null,
                rememberedOnlineSourceId = null,
            ),
        )
        assertTrue(
            clearSource.shouldClearRememberedOnlinePlaylistsSource(
                currentOnlineSourceId = null,
                rememberedOnlineSourceId = "nav-online",
            ),
        )
        assertFalse(temporaryClearSource.isPassivePlaylistsSourceClear())
        assertFalse(temporaryClearSource.shouldStartOnlinePlaylistsStore())
        assertFalse(OnlinePlaylistsIntent.ClearMessage.shouldStartOnlinePlaylistsStore())
        assertFalse(OnlinePlaylistsIntent.ClearPlaylistImportReport.shouldStartOnlinePlaylistsStore())
        assertFalse(OnlinePlaylistsIntent.SelectPlaylist(null).shouldStartOnlinePlaylistsStore())
        assertTrue(OnlinePlaylistsIntent.SelectSource("nav-online").shouldStartOnlinePlaylistsStore())
        assertTrue(OnlinePlaylistsIntent.SelectPlaylist("playlist-1").shouldStartOnlinePlaylistsStore())
        assertTrue(OnlinePlaylistsIntent.Refresh.shouldStartOnlinePlaylistsStore())
    }
}
