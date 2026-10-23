@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*
import kotlinx.coroutines.flow.emptyFlow
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.favorites.*
import top.iwesley.lyn.music.feature.importing.*
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.playlists.*
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.tags.MusicTagsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class MobileCollectionCompositionTest {
    @Test
    fun `library keeps playback order and album folder navigation`() = onUiThread {
        for (width in listOf(480, 1280)) Fixture(width = width).use { ui ->
            ui.click("Second track")
            ui.assertPlayback(listOf("one", "two"), 1)
            ui.click("Album")
            ui.click("Fixture album")
            ui.assertVisible("First track")
            ui.capture("library-album-$width")
            ui.click("Back")
            ui.click("Folder")
            ui.click("Fixture source")
            ui.click("Record")
            ui.click("Second track")
            ui.assertPlayback(listOf("one", "two"), 1)
            ui.click("Back")
            ui.assertVisible("Record")
        }
    }

    @Test
    fun `favorites routes playback and removal through shared intents`() = onUiThread {
        Fixture(AppTab.Favorites).use { ui ->
            ui.click("First track")
            ui.assertPlayback(listOf("one"), 0)
            ui.click("Remove from favorites")
            assertEquals("one", (ui.favoriteIntents.last() as FavoritesIntent.ToggleFavorite).track.id)
            ui.capture("favorites")
        }
    }

    @Test
    fun `playlist selection opens loaded detail and retains import dialog`() = onUiThread {
        Fixture(AppTab.Playlists).use { ui ->
            ui.click("Fixture playlist")
            assertEquals(PlaylistsIntent.SelectPlaylist("playlist"), ui.playlistIntents.last())
            ui.click("Second track")
            ui.assertPlayback(listOf("one", "two"), 1)
            ui.capture("playlist-detail")
            ui.click("Import playlist")
            ui.assertVisible("Import playlist")
            ui.click("Close")
            ui.assertVisible("Second track")
        }
    }

    @Test
    fun `sources routes edit rescan and confirmed deletion independently`() = onUiThread {
        Fixture(AppTab.Sources).use { ui ->
            ui.click("Edit source")
            assertEquals(ImportIntent.OpenRemoteSourceEditor("source"), ui.importIntents.last())
            ui.click("Rescan source")
            assertEquals(ImportIntent.RescanSource("source"), ui.importIntents.last())
            ui.click("Delete source")
            assertTrue(ui.importIntents.none { it is ImportIntent.DeleteSource })
            ui.click("Cancel")
            assertTrue(ui.importIntents.none { it is ImportIntent.DeleteSource })
            ui.capture("sources")
            ui.click("Delete source")
            ui.click("Delete")
            assertEquals(ImportIntent.DeleteSource("source"), ui.importIntents.last())
        }
    }

    @Test
    fun `my recent album navigates into this platforms library`() = onUiThread {
        Fixture(AppTab.My, height = 1600).use { ui ->
            ui.click("Second track")
            ui.assertPlayback(listOf("one", "two"), 1)
            ui.capture("my")
            ui.click("Fixture album")
            assertEquals(LibraryNavigationTarget.Album(libraryAlbumId("Fixture artist", "Fixture album")), ui.lastNavigationTarget)
            assertEquals(AppTab.Library, ui.selectedTab)
            ui.assertVisible("First track")
            ui.assertVisible("Back")
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(initialTab: AppTab = AppTab.Library, private val width: Int = 480, height: Int = 1000) : AutoCloseable {
        var selectedTab by mutableStateOf(initialTab)
        var lastNavigationTarget: LibraryNavigationTarget? = null
        private var navigationTarget by mutableStateOf<LibraryNavigationTarget?>(null)
        val playerIntents = mutableListOf<PlayerIntent>()
        val favoriteIntents = mutableListOf<FavoritesIntent>()
        val playlistIntents = mutableListOf<PlaylistsIntent>()
        val importIntents = mutableListOf<ImportIntent>()
        private val platform = PlatformDescriptor(ANDROID_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        private val tracks = listOf("one" to "First track", "two" to "Second track").map { (id, title) ->
            Track(id = id, sourceId = "source", title = title, artistName = "Fixture artist",
                albumTitle = "Fixture album", durationMs = 180_000,
                mediaLocator = "/fixture/$id.mp3", relativePath = "Record/$id.mp3")
        }
        private val albums = deriveVisibleAlbums(tracks)
        private val artists = deriveVisibleArtists(tracks)
        private val library = LibraryState(isLoadingContent = false, tracks = tracks, filteredTracks = tracks,
            albums = albums, filteredAlbums = albums, artists = artists, filteredArtists = artists,
            visibleAlbumCount = 1, visibleArtistCount = 1, sourceLabelsById = mapOf("source" to "Fixture source"))
        private val playlist = PlaylistSummary("playlist", "Fixture playlist", trackCount = 2)
        private var playlists by mutableStateOf(PlaylistsState(isLoadingContent = false, playlists = listOf(playlist)))
        private var frame = 0L
        private val scene = ImageComposeScene(width, height) {
            ProvideUiResourceLanguage("en") {
                CompositionLocalProvider(LocalPlatformDescriptor provides platform) {
                    LynMusicTheme {
                        MobilePageContent(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            platform = platform,
                            myState = MyState(isLoadingContent = false,
                                recentTracks = tracks.map { RecentTrack(it, 1, 1L) },
                                recentAlbums = albums.map { RecentAlbum(it, 1, 1L) }),
                            libraryState = library,
                            onlineLibraryState = OnlineLibraryState(),
                            playlistsState = playlists,
                            onlinePlaylistsState = OnlinePlaylistsState(),
                            favoritesState = FavoritesState(isLoadingContent = false,
                                tracks = tracks.take(1), filteredTracks = tracks.take(1), favoriteTrackIds = setOf("one")),
                            onlineFavoritesState = OnlineFavoritesState(),
                            musicTagsState = MusicTagsState(), musicTagsEffects = emptyFlow(), onMusicTagsIntent = {},
                            importState = ImportState(capabilities = platform.capabilities, sources = listOf(
                                SourceWithStatus(ImportSource("source", ImportSourceType.WEBDAV, "Fixture source", "https://example.invalid/music"),
                                    ImportIndexState("source", 2)))),
                            settingsState = SettingsState(),
                            onMyIntent = {}, onLibraryIntent = {}, onOnlineLibraryIntent = {},
                            onPlaylistsIntent = { intent ->
                                playlistIntents += intent
                                if (intent is PlaylistsIntent.SelectPlaylist) {
                                    playlists = playlists.copy(selectedPlaylistId = intent.playlistId,
                                        selectedPlaylist = intent.playlistId?.let {
                                            PlaylistDetail(it, "Fixture playlist", tracks = tracks.map(::PlaylistTrackEntry))
                                        })
                                }
                            },
                            onOnlinePlaylistsIntent = {}, onFavoritesIntent = { favoriteIntents += it },
                            onOnlineFavoritesIntent = {}, onImportIntent = { importIntents += it },
                            onPlayerIntent = { playerIntents += it }, onSettingsIntent = {},
                            onOpenBackgroundRunSettings = {},
                            libraryNavigationTarget = navigationTarget,
                            onLibraryNavigationHandled = { navigationTarget = null },
                            onOpenLibraryNavigationTarget = {
                                lastNavigationTarget = it
                                navigationTarget = it
                                selectedTab = AppTab.Library
                            },
                            compact = width < 1000,
                            mobileLibraryHub = width < 1000,
                            phoneLibraryNavigation = width < 1000,
                        )
                    }
                }
            }
        }
        init { render() }

        private fun render() {
            repeat(24) {
                Snapshot.sendApplyNotifications()
                scene.render(++frame * 16_666_667L).close()
            }
        }
        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }
        private fun matches(node: SemanticsNode, label: String) =
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true

        fun assertVisible(label: String) {
            assertTrue(nodes().any { matches(it, label) }, "Missing: $label")
        }
        fun click(label: String) {
            val node = nodes().lastOrNull { matches(it, label) }
                ?: error("Missing: $label; labels=" + nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() })
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center)
            render()
        }
        fun assertPlayback(ids: List<String>, index: Int) {
            val intent = playerIntents.last() as PlayerIntent.PlayTracks
            assertEquals(ids, intent.tracks.map { it.id })
            assertEquals(index, intent.startIndex)
        }
        fun capture(name: String) {
            scene.sendPointerEvent(PointerEventType.Move, androidx.compose.ui.geometry.Offset.Zero)
            render()
            val file = File("build/reports/collection-ui/$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() = scene.close()
    }
}
