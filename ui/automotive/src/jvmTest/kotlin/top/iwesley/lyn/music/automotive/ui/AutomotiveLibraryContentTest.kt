@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*
import top.iwesley.lyn.music.ANDROID_AUTOMOTIVE_PLATFORM_NAME
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class AutomotiveLibraryContentTest {
    @Test
    fun `both layouts share tab clicks swipes and non looping edges`() = onUiThread {
        for ((width, height) in listOf(1184 to 800, 480 to 900)) {
            Fixture(width, height).use { ui ->
                ui.swipe(forward = false)
                ui.assertRoot(LibraryBrowserRootView.Tracks)
                for (root in LibraryBrowserRootView.entries.drop(1)) {
                    ui.swipe(forward = true)
                    ui.assertRoot(root)
                }
                ui.swipe(forward = true)
                ui.assertRoot(LibraryBrowserRootView.Folders)
                for (root in LibraryBrowserRootView.entries.reversed()) {
                    ui.clickRoot(root)
                    ui.assertRoot(root)
                }
                assertTrue(ui.playerIntents.isEmpty())
            }
        }
    }

    @Test
    fun `category scroll and detail survive orientation and registry restoration`() = onUiThread {
        val saved = Fixture().use { ui ->
            ui.scroll(LibraryBrowserRootView.Tracks, 900f)
            val tracksPosition = ui.scrollPosition(LibraryBrowserRootView.Tracks)
            val headerBounds = ui.tag("automotive-library-header").boundsInRoot
            ui.clickRoot(LibraryBrowserRootView.Albums)
            ui.scroll(LibraryBrowserRootView.Albums, 650f)
            val albumsPosition = ui.scrollPosition(LibraryBrowserRootView.Albums)
            ui.clickRoot(LibraryBrowserRootView.Tracks)
            assertEquals(tracksPosition, ui.scrollPosition(LibraryBrowserRootView.Tracks))
            ui.resize(480, 900)
            ui.assertRoot(LibraryBrowserRootView.Tracks)
            assertEquals(tracksPosition, ui.scrollPosition(LibraryBrowserRootView.Tracks))
            ui.clickRoot(LibraryBrowserRootView.Albums)
            assertEquals(albumsPosition, ui.scrollPosition(LibraryBrowserRootView.Albums))
            ui.scroll(LibraryBrowserRootView.Albums, -10_000f)
            ui.clickLabel("Album 00")
            assertTrue(ui.hasLabel("Back"))
            ui.resize(1184, 800)
            assertTrue(ui.hasLabel("Back"))
            ui.registry.performSave()
        }
        Fixture(restored = saved).use { ui ->
            assertTrue(ui.hasLabel("Back"))
            ui.clickLabel("Back")
            ui.assertRoot(LibraryBrowserRootView.Albums)
            assertFalse(ui.hasLabel("Back"))
            ui.clickLabel("Album 00")
            ui.clickLabel("Back")
            assertFalse(ui.hasLabel("Back"))
            assertTrue(ui.playerIntents.isEmpty())
        }
    }

    @Test
    fun `narrow search keeps editing through resize and clears on exit`() = onUiThread {
        Fixture(560, 360).use { ui ->
            assertFalse(ui.hasTag("automotive-library-inline-search"))
            ui.clickLabel("Search")
            ui.setQuery("Track")
            ui.swipe(forward = true)
            ui.resize(1184, 800)
            assertTrue(ui.hasLabel("Exit search"))
            assertEquals("Track", ui.library.query)
            ui.clickLabel("Clear search")
            assertEquals("", ui.library.query)
            assertTrue(ui.hasLabel("Exit search"))
            ui.setQuery("Track 01")
            ui.clickLabel("Exit search")
            assertEquals("", ui.library.query)
            ui.assertRoot(LibraryBrowserRootView.Tracks)
            assertTrue(ui.hasTag("automotive-library-inline-search"))
            ui.swipe(forward = true)
            ui.assertRoot(LibraryBrowserRootView.Albums)
        }
    }

    @Test
    fun `inline search moves to full search on narrowing and portrait exits clear query`() = onUiThread {
        Fixture().use { ui ->
            val search = ui.tag("automotive-library-inline-search").boundsInRoot
            assertTrue(search.left > ui.tag("automotive-library-tab-Folders").boundsInRoot.right)
            assertTrue(search.right < ui.label("Select source").boundsInRoot.left)
            ui.setQuery("Track")
            ui.resize(560, 360)
            assertTrue(ui.hasLabel("Exit search"))
            assertEquals("Track", ui.library.query)
            ui.resize(480, 900)
            assertTrue(ui.hasLabel("Exit search"))
            ui.clickLabel("Exit search")
            assertEquals("", ui.library.query)
            ui.clickLabel("Search")
            ui.setQuery("Track 02")
            ui.clickLabel("Exit search")
            assertEquals("", ui.library.query)
        }
    }

    @Test
    fun `search fallback accounts for visible actions and preserves blank editing`() = onUiThread {
        Fixture(588, 360).use { ui ->
            assertTrue(ui.hasTag("automotive-library-inline-search"))
            ui.resize(587, 360)
            assertFalse(ui.hasTag("automotive-library-inline-search"))
            ui.clickRoot(LibraryBrowserRootView.Albums)
            assertTrue(ui.hasTag("automotive-library-inline-search"))
            ui.clickRoot(LibraryBrowserRootView.Tracks)
            assertFalse(ui.hasTag("automotive-library-inline-search"))
            ui.resize(1184, 800)
            ui.focusInlineSearch()
            ui.resize(560, 360)
            assertTrue(ui.hasLabel("Exit search"))
            assertEquals("", ui.library.query)
        }
    }

    @Test
    fun `landscape track metadata still opens album and artist details`() = onUiThread {
        Fixture().use { ui ->
            ui.clickLabel("Album 00")
            assertTrue(ui.hasLabel("Back"))
            assertIs<LibraryNavigationTarget.Album>(ui.metadataTargets.last())
            ui.clickLabel("Back")
            ui.clickLabel("Artist 00")
            assertTrue(ui.hasLabel("Back"))
            assertIs<LibraryNavigationTarget.Artist>(ui.metadataTargets.last())
            assertTrue(ui.playerIntents.isEmpty())
        }
    }

    @Test
    fun `online folder remains accessible with disabled search and source switching`() = onUiThread {
        Fixture().use { ui ->
            ui.clickRoot(LibraryBrowserRootView.Folders)
            ui.clickLabel("Select source")
            ui.clickLabel("Server · Online")
            ui.assertRoot(LibraryBrowserRootView.Folders)
            assertTrue(ui.hasLabel("This source does not support folder browsing"))
            assertTrue(ui.tag("automotive-library-inline-search").config.contains(SemanticsProperties.Disabled))
            ui.capture("online-folder")
            ui.swipe(forward = false)
            ui.assertRoot(LibraryBrowserRootView.Artists)
            ui.clickRoot(LibraryBrowserRootView.Folders)
            ui.clickLabel("Select source")
            ui.clickLabel("All")
            assertNull(ui.online.sourceId)
            ui.assertRoot(LibraryBrowserRootView.Folders)
            assertFalse(ui.hasLabel("This source does not support folder browsing"))
            assertFalse(ui.tag("automotive-library-inline-search").config.contains(SemanticsProperties.Disabled))
        }
    }

    @Test
    fun `batch mode prevents swiping and repeating the tab exits selection`() = onUiThread {
        Fixture().use { ui ->
            ui.clickLabel("Batch actions")
            ui.swipe(forward = true)
            ui.assertRoot(LibraryBrowserRootView.Tracks)
            ui.clickRoot(LibraryBrowserRootView.Tracks)
            ui.swipe(forward = true)
            ui.assertRoot(LibraryBrowserRootView.Albums)
            assertFalse(ui.hasLabel("Batch actions"))
            assertFalse(ui.hasLabel("Sort tracks"))
        }
    }

    @Test
    fun `headers fit languages themes large text and low height`() = onUiThread {
        for (language in listOf("en", "zh-CN")) for (light in listOf(false, true)) {
            for ((name, size) in listOf("low" to (704 to 360), "wide" to (1184 to 800), "portrait" to (480 to 900))) {
                Fixture(size.first, size.second, language = language, light = light, fontScale = 1.5f).use { ui ->
                    ui.capture("$name-$language-${if (light) "light" else "dark"}-large")
                    ui.assertHeaderFits()
                }
            }
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(
        width: Int = 1184,
        height: Int = 800,
        language: String = "en",
        light: Boolean = false,
        fontScale: Float = 1f,
        restored: Map<String, List<Any?>>? = null,
    ) : AutoCloseable {
        val registry = SaveableStateRegistry(restored) { true }
        private val tracks = (0 until 80).map {
            Track("track-$it", "local", "Track %02d".format(it), artistName = "Artist %02d".format(it / 2),
                albumTitle = "Album %02d".format(it / 2), durationMs = 245_000,
                mediaLocator = "", relativePath = "Music/Album ${it / 2}/$it.mp3")
        }
        private val albums = (0 until 40).map {
            Album(libraryAlbumId("Artist %02d".format(it), "Album %02d".format(it)),
                "Album %02d".format(it), "Artist %02d".format(it), 2)
        }
        private val artists = (0 until 40).map { Artist(libraryArtistId("Artist %02d".format(it)), "Artist %02d".format(it), 2) }
        var library by mutableStateOf(LibraryState(isLoadingContent = false, tracks = tracks, filteredTracks = tracks,
            albums = albums, filteredAlbums = albums, artists = artists, filteredArtists = artists,
            visibleAlbumCount = albums.size, visibleArtistCount = artists.size))
        var online by mutableStateOf(OnlineLibraryState())
        val playerIntents = mutableListOf<PlayerIntent>()
        val metadataTargets = mutableListOf<LibraryNavigationTarget>()
        private var navigationTarget by mutableStateOf<LibraryNavigationTarget?>(null)
        private val platform = PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        private val importState = ImportState(capabilities = platform.capabilities, sources = listOf(SourceWithStatus(
            ImportSource("server", ImportSourceType.NAVIDROME, "Server", "", indexMode = ImportSourceIndexMode.ONLINE),
        )))
        private lateinit var detailNavigation: MusicDetailNavigation
        private val detailQueries = top.iwesley.lyn.music.feature.details.MusicDetailQueries { request ->
            kotlinx.coroutines.flow.flowOf(detailFixtureData(request, tracks))
        }
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, density = Density(1f, fontScale)) {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {

            val details = rememberMusicDetailNavigation(detailQueries) {}.also { detailNavigation = it }
                CompositionLocalProvider(LocalMusicDetailNavigation provides details,
                LocalSaveableStateRegistry provides registry,
                LocalPlatformDescriptor provides platform,
                LocalOfflineDownloadUiState provides OfflineDownloadUiState(onIntent = {}),
            ) {
                ProvideUiResourceLanguage(language) {
                    LynMusicTheme(
                        themeTokens = if (light) OCEAN_APP_THEME_TOKENS else CLASSIC_APP_THEME_TOKENS,
                        textPalette = if (light) AppThemeTextPalette.Black else AppThemeTextPalette.White,
                    ) {
                        val entry = rememberAutomotiveLibraryEntry()
                        MusicDetailHost(onPlayerIntent = { playerIntents += it }, modifier = Modifier.fillMaxSize()) {
                        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            val compact = maxWidth < maxHeight
                            // Mirrors replacement of the app's orientation branch, keeping its holder outside.
                            key(compact) {
                                entry {
                                    AutomotiveLibraryContent(
                                        libraryState = library, onlineLibraryState = online,
                                        favoritesState = FavoritesState(), importState = importState,
                                        onLibraryIntent = { intent -> library = when (intent) {
                                            is LibraryIntent.SearchChanged -> library.copy(query = intent.query,
                                                filteredTracks = tracks.filter { it.title.contains(intent.query, true) })
                                            is LibraryIntent.SourceFilterChanged -> library.copy(selectedSourceFilter = intent.filter)
                                            is LibraryIntent.TrackSortChanged -> library.copy(selectedTrackSortMode = intent.mode)
                                        } },
                                        onOnlineLibraryIntent = { intent -> online = when (intent) {
                                            is OnlineLibraryIntent.SelectSource -> OnlineLibraryState(sourceId = intent.sourceId)
                                            is OnlineLibraryIntent.SearchChanged -> online.copy(query = intent.query)
                                            else -> online
                                        } },
                                        onFavoritesIntent = {}, onPlayerIntent = { playerIntents += it },
                                        compact = compact,
                                        libraryNavigationTarget = null,
                                        onLibraryNavigationHandled = { navigationTarget = null },
                                        onOpenLibraryNavigationTarget = { target ->
                                            metadataTargets += target
                                            detailNavigation.open(target)
                                        },
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
            }
}
        init { render() }
        fun render(frames: Int = 40) { repeat(frames) {
            Snapshot.sendApplyNotifications()
            scene.render(++frame * 16_666_667L).close()
        } }
        fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }
        fun tag(tag: String) = nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun hasTag(tag: String) = nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        private fun matches(node: SemanticsNode, label: String) =
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
        fun label(label: String) = nodes().lastOrNull { matches(it, label) } ?: error("Missing $label; labels: ${nodes().map { it.config }}")
        fun hasLabel(label: String) = nodes().any { matches(it, label) }
        private fun click(node: SemanticsNode) {
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center, type = PointerType.Touch)
            render()
        }
        fun focusInlineSearch() = click(tag("automotive-library-inline-search"))
        fun clickLabel(label: String) = click(label(label))
        fun clickRoot(root: LibraryBrowserRootView) = click(tag("automotive-library-tab-${root.name}"))
        fun assertRoot(root: LibraryBrowserRootView) {
            assertEquals(true, tag("automotive-library-tab-${root.name}").config[SemanticsProperties.Selected])
        }
        fun resize(width: Int, height: Int) { scene.constraints = Constraints.fixed(width, height); render() }
        fun scroll(root: LibraryBrowserRootView, delta: Float) {
            assertTrue(tag("automotive-library-list-${root.name}").config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta))
            render()
        }
        fun scrollPosition(root: LibraryBrowserRootView) = tag("automotive-library-list-${root.name}").config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun setQuery(query: String) {
            val input = nodes().single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(input.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(query)))
            render()
        }
        fun swipe(forward: Boolean) {
            val bounds = tag("automotive-library-pager").boundsInRoot
            val start = Offset(bounds.left + bounds.width * (if (forward) .85f else .15f), bounds.center.y)
            val distance = bounds.width * (if (forward) -.70f else .70f)
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, timeMillis = frame * 17)
            for (step in 1..10) {
                scene.sendPointerEvent(PointerEventType.Move, start + Offset(distance * step / 10, 0f), type = PointerType.Touch, timeMillis = frame * 17 + 30)
                render(2)
            }
            scene.sendPointerEvent(PointerEventType.Release, start + Offset(distance, 0f), type = PointerType.Touch, timeMillis = frame * 17 + 30)
            render()
        }
        fun assertHeaderFits() {
            val header = tag("automotive-library-header").boundsInRoot
            assertTrue(header.top == 0f && header.bottom <= 64f)
            for (root in LibraryBrowserRootView.entries) {
                val bounds = tag("automotive-library-tab-${root.name}").boundsInRoot
                assertTrue(bounds.height >= 40f, "$root vertically clipped: $bounds")
            }
            assertTrue(tag("automotive-library-pager").boundsInRoot.height > 200f)
            val tabs = tag("automotive-library-tabs")
            assertTrue(tabs.config[SemanticsActions.ScrollBy].action!!.invoke(1_000f, 0f))
            render()
            val folder = tag("automotive-library-tab-Folders")
            assertTrue(folder.positionInRoot.x + folder.size.width <= tabs.boundsInRoot.right + 1f)
            assertTrue(folder.boundsInRoot.width >= 48f)
            for (node in nodes().filter { matches(it, "04:05") }) {
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                assertTrue(layouts.filter { it.layoutInput.text.text == "04:05" }.all { it.lineCount == 1 && !it.hasVisualOverflow },
                    "Duration overflow: ${layouts.map { Triple(it.layoutInput.text.text, it.lineCount, it.hasVisualOverflow) }}")
            }
        }
        fun capture(name: String) {
            val file = File("build/reports/automotive-library/$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        override fun close() = scene.close()
    }
}

private fun detailFixtureData(request: top.iwesley.lyn.music.feature.details.MusicDetailRequest, tracks: List<Track>): top.iwesley.lyn.music.feature.details.MusicDetailData {
    val album = deriveVisibleAlbums(tracks).firstOrNull { it.id == request.entityId }
    val artist = deriveVisibleArtists(tracks).firstOrNull { it.id == request.entityId }
    return top.iwesley.lyn.music.feature.details.MusicDetailData(
        title = request.title ?: (if (request.kind == top.iwesley.lyn.music.feature.details.MusicDetailKind.Album) album?.title ?: tracks.firstOrNull()?.albumTitle else artist?.name ?: tracks.firstOrNull()?.artistName).orEmpty(),
        artistName = request.artistName ?: album?.artistName,
        tracks = tracks,
        albums = if (request.kind == top.iwesley.lyn.music.feature.details.MusicDetailKind.Artist) deriveVisibleAlbums(tracks).map { top.iwesley.lyn.music.feature.details.MusicDetailAlbum(it) } else emptyList(),
    )
}
