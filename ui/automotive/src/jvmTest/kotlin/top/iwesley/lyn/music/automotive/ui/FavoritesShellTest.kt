@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.details.*
import top.iwesley.lyn.music.feature.favorites.*
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import androidx.compose.ui.state.ToggleableState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class FavoritesShellTest {
    @Test fun folderLayoutsUseActualShellsAndKeepLastActionsAboveThePlayer() = onUiThread {
        for ((w,h) in listOf(1280 to 720,1920 to 720,800 to 1280,480 to 900,640 to 400))
            Fixture(w,h,visual = true,folderMode = true,count = 8).use { ui ->
                ui.navigation.libraryRootState.value = LibraryBrowserRootView.Folders; ui.render()
                ui.tag("folder-roots-heading"); ui.capture("folders-list-$w-$h")
                ui.openFolder(""); ui.capture("folders-root-$w-$h")
                ui.openFolder("Parent"); ui.capture("folders-mixed-$w-$h")
                ui.openFolder("Only folders"); ui.capture("folders-subdirs-$w-$h")
                ui.openFolder(); ui.capture("folders-songs-$w-$h")
                ui.scrollFolderTo(ui.folderEntry.list.layoutInfo.totalItemsCount - 1)
                val menu = ui.label("山阴路的夏天 8 的更多操作").boundsInRoot
                assertTrue(menu.bottom <= ui.playerTop(), "$w/$h: $menu")
                ui.capture("folders-bottom-$w-$h")
                assertTrue(ui.intents.isEmpty())
            }
    }

    @Test fun folderSourceOverviewRestoresItsAnchorAfterOpeningAndRotating() = onUiThread {
        Fixture(1280,720,folderMode = true,folderSources = 24).use { ui ->
            ui.navigation.libraryRootState.value = LibraryBrowserRootView.Folders; ui.render()
            ui.scrollFolderRootsTo(9,9)
            val list = ui.navigation.folderListState
            val anchor = list.layoutInfo.visibleItemsInfo.first().key
            ui.clickTag("folder-row:${LibraryFolderKey("extra-10","").stableId}")
            assertEquals("extra-10",ui.navigation.top.sourceId)
            ui.resize(800,1280)
            ui.navigation.requestBack(); ui.render()
            assertEquals(LibraryBrowserRootView.Folders,ui.navigation.libraryRootState.value)
            assertEquals(anchor,list.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(9,list.firstVisibleItemScrollOffset)
            val loaded = ui.library
            ui.library = loaded.copy(isLoadingContent = true,filteredTracks = emptyList()); ui.render()
            ui.library = loaded; ui.render()
            assertEquals(anchor,ui.navigation.folderListState.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(9,ui.navigation.folderListState.firstVisibleItemScrollOffset)
            ui.clickTag("folder-row:${LibraryFolderKey("extra-10","").stableId}")
            assertEquals("extra-10",ui.navigation.top.sourceId)
            assertTrue(ui.libraryIntents.isEmpty())
        }
    }

    @Test fun folderEachLevelKeepsItsPositionAcrossChildrenShellsAndLoading() = onUiThread {
        Fixture(1280,720,folderMode = true).use { ui ->
            ui.openFolder(""); ui.click("Parent")
            ui.scrollFolderTo(8,7); val parent = ui.navigation.top
            ui.openFolder(); ui.scrollFolderTo(40,11); val leaf = ui.navigation.top
            for ((w,h) in listOf(800 to 1280,1920 to 720,480 to 900,640 to 400,1280 to 720)) {
                ui.resize(w,h)
                assertEquals(leaf,ui.navigation.top)
                assertEquals(40,ui.folderEntry.list.firstVisibleItemIndex)
                assertEquals(11,ui.folderEntry.list.firstVisibleItemScrollOffset)
            }
            ui.navigation.open(LibraryNavigationTarget.Album("album")); ui.render()
            ui.navigation.pop(); ui.render()
            assertEquals(40,ui.folderEntry.list.firstVisibleItemIndex)
            val loaded = ui.library
            ui.library = loaded.copy(isLoadingContent = true,filteredTracks = emptyList()); ui.render()
            ui.tag("folder-loading")
            ui.library = loaded; ui.render()
            assertEquals(11,ui.folderEntry.list.firstVisibleItemScrollOffset)
            ui.rebuild(); assertEquals(40,ui.folderEntry.list.firstVisibleItemIndex)
            ui.navigation.requestBack(); ui.render()
            assertEquals(parent,ui.navigation.top)
            assertEquals(8,ui.folderEntry.list.firstVisibleItemIndex)
            assertEquals(7,ui.folderEntry.list.firstVisibleItemScrollOffset)
            assertTrue(ui.libraryIntents.isEmpty())
        }
    }

    @Test fun folderPlaybackUsesOnlyDirectSongsAndExistingMenus() = onUiThread {
        Fixture(1280,720,folderMode = true).use { ui ->
            ui.openFolder("Parent")
            ui.scrollFolderTo(ui.folderEntry.list.layoutInfo.totalItemsCount - 1)
            ui.click("Play all")
            assertEquals(listOf("parent-song"),(ui.intents.last() as PlayerIntent.PlayTracks).tracks.map { it.id })
            ui.openFolder("Only folders")
            assertFalse(ui.visible("Play all")); assertFalse(ui.hasTag("folder-songs-heading"))
            ui.openFolder()
            ui.click("Play all"); assertEquals(ui.tracks,(ui.intents.last() as PlayerIntent.PlayTracks).tracks)
            ui.click("More actions for Song 0"); ui.click("Add to favorites")
            assertTrue(ui.favorites.any { it.id == "track-0" })
            ui.click("More actions for Song 0"); ui.click("Add to playlist")
            assertEquals(ui.tracks.first(),ui.playlistTargets.last())
            for (kind in listOf("album","artist")) {
                ui.click("More actions for Song 0"); ui.click("View $kind")
                assertEquals(kind,ui.navigation.top.kind)
                ui.navigation.pop(); ui.render()
            }
            ui.click("02"); assertEquals(PlayerIntent.PlayTracks(ui.tracks,1),ui.intents.last())
            ui.intents.clear(); ui.swipeVertically("folder-content"); assertTrue(ui.intents.isEmpty())
        }
    }

    @Test fun folderEmptyAndUnavailableStatesPreserveTheRouteWithoutShowingOtherSources() = onUiThread {
        Fixture(480,900,folderMode = true).use { ui ->
            ui.openFolder(); val route = ui.navigation.top; val loaded = ui.library
            ui.library = loaded.copy(filteredTracks = emptyList()); ui.render()
            ui.tag("folder-empty"); assertEquals(route,ui.navigation.top)
            assertFalse(ui.visible("Play all"))
            ui.library = loaded.copy(sourceLabelsById = emptyMap()); ui.render()
            assertTrue(ui.visible("This source is unavailable. Go back or retry after restoring the source."))
            ui.library = loaded.copy(isLoadingContent = true,filteredTracks = emptyList(),sourceLabelsById = emptyMap()); ui.render()
            ui.tag("folder-loading")
            ui.library = loaded
            val originalSources = ui.importState
            for (source in listOf(originalSources.sources.first().source.copy(enabled = false),
                originalSources.sources.first().source.copy(indexMode = ImportSourceIndexMode.ONLINE))) {
                ui.importState = originalSources.copy(sources = listOf(SourceWithStatus(source))); ui.render()
                assertTrue(ui.visible("This source is unavailable. Go back or retry after restoring the source."))
                assertEquals(route,ui.navigation.top)
            }
            ui.importState = originalSources
            ui.openFolder(sourceId = "other")
            assertTrue(ui.visible("Other source song")); assertFalse(ui.visible("Song 0"))
            assertTrue(ui.libraryIntents.isEmpty())
        }
    }

    @Test fun folderScalesAndWidthThresholdsKeepPlaybackAndMenusReachable() = onUiThread {
        for ((w,h) in listOf(1280 to 720,480 to 900,640 to 400))
            for (scale in AppDisplayScalePreset.entries) for (font in listOf(1f,1.5f,2f))
                Fixture(w,h,density = scale.scale,fontScale = font,dark = scale.ordinal % 2 == 0,
                    longNames = true,folderMode = true,count = 120).use { ui ->
                    ui.openFolder(); ui.scrollFolderTo(2)
                    val play = ui.tag("folder-play-all").boundsInRoot
                    assertTrue(play.height + 1f >= 48f * scale.scale)
                    assertTrue(play.top >= ui.tag("folder-toolbar").boundsInRoot.bottom && play.bottom <= ui.playerTop(), "$w/$h/$scale/$font: $play")
                    ui.scrollFolderTo(ui.folderEntry.list.layoutInfo.totalItemsCount - 1)
                    assertTrue(ui.label("More actions for Song 119").boundsInRoot.bottom <= ui.playerTop())
                    if (scale == AppDisplayScalePreset.ExtraLarge && font == 2f) ui.capture("folders-large-$w-$h")
                }
        Fixture(591,991,folderMode = true).use { ui ->
            ui.openFolder()
            for (width in listOf(591,592,599,600,1119,1120)) {
                ui.fontScale = if (width >= 1119) 2f else 1f
                ui.resize(width,width + 400)
                ui.scrollFolderTo(0)
                assertEquals(if (width >= 600) 24f else 20f,ui.tag("folder-path").boundsInRoot.left,1f)
                ui.scrollFolderTo(ui.folderEntry.list.layoutInfo.totalItemsCount - 1)
                assertEquals(width !in listOf(591,1119),ui.visible("120:00"),"width=$width")
            }
        }
    }

    @Test fun playlistListAndDetailsUseRealShellsInBothOrientations() = onUiThread {
        for ((w, h) in listOf(1280 to 720, 1920 to 720, 800 to 1280, 480 to 900, 640 to 400))
            Fixture(w, h, visual = true, playlistMode = true).use { ui ->
                assertTrue(ui.visible("通勤歌单"))
                ui.capture("playlists-list-$w-$h")
                ui.openPlaylist()
                ui.tag(if (w >= 1280) "playlist-content-split" else "playlist-content-stacked")
                ui.capture("playlists-detail-$w-$h")
                ui.scrollPlaylistTo(96)
                val menu = ui.label("山阴路的夏天 96 的更多操作").boundsInRoot
                assertTrue(menu.bottom <= ui.playerTop(), "$w/$h: last menu $menu")
                ui.click("山阴路的夏天 96 的更多操作")
                assertTrue(ui.visible("移出歌单"))
            }
        Fixture(1280, 720, visual = true, fontScale = 2f, longNames = true, dark = true, playlistMode = true).use { ui ->
            ui.capture("playlists-list-large-font")
            ui.openPlaylist()
            ui.capture("playlists-detail-large-font")
        }
    }

    @Test fun playlistLayoutThresholdsUsePageSizeAndAvailableHeight() = onUiThread {
        Fixture(1280,720,playlistMode = true).use { ui ->
            ui.openPlaylist()
            val heading = ui.tag("playlist-column-duration").boundsInRoot
            val duration = ui.label("120:00").boundsInRoot
            assertEquals(duration.left,heading.left,1f)
            assertEquals(duration.right,heading.right,1f)
            val rail = (ui.width - ui.tag("music-detail-page-playlist").boundsInRoot.width).toInt()
            for (width in listOf(599,600,899,900)) {
                ui.resize(width + rail,600)
                val layout = ui.navigation.playlistEntry(ui.navigation.top).headerLayout!!
                assertEquals(width >= 900,layout.split,"W=$width")
                assertEquals((width - if (width >= 600) 48 else 40).dp,layout.width)
                if (width < 900) assertEquals(if (width < 600) 176.dp else 220.dp,layout.cover)
            }
            ui.resize(1280,720)
            for (height in listOf(239,240,359,360)) {
                val available = ui.tag("music-detail-page-playlist").boundsInRoot.bottom - ui.tag("playlist-toolbar").boundsInRoot.bottom
                ui.resize(1280,ui.height + height - available.toInt())
                val layout = ui.navigation.playlistEntry(ui.navigation.top).headerLayout!!
                assertEquals(height >= 240,layout.split,"H=$height")
                assertEquals(when { height < 240 -> 112.dp; height < 360 -> 160.dp; else -> 288.dp },layout.cover)
            }
        }
    }

    @Test fun playlistScalesKeepPlaybackAndLastMenusReachable() = onUiThread {
        for ((w,h) in listOf(1280 to 720,480 to 900,640 to 400))
            for (scale in AppDisplayScalePreset.entries) for (font in listOf(1f,1.5f,2f))
                Fixture(w,h,density = scale.scale,fontScale = font,longNames = true,
                    dark = scale.ordinal % 2 == 0,playlistMode = true,count = 24).use { ui ->
                    ui.openPlaylist()
                    val entry = ui.navigation.playlistEntry(ui.navigation.top)
                    ui.revealPlaylistPlayback()
                    val play = ui.tag("playlist-play-all").boundsInRoot
                    assertTrue(play.height + 1f >= 48f * scale.scale, "$w/$h/$scale/$font: $play")
                    assertTrue(play.top >= ui.tag("playlist-toolbar").boundsInRoot.bottom && play.bottom <= ui.playerTop(),
                        "$w/$h/$scale/$font: $play")
                    ui.scrollPlaylistTo(24)
                    val menu = ui.label("More actions for Song 23").boundsInRoot
                    assertTrue(menu.bottom <= ui.playerTop(), "$w/$h/$scale/$font: $menu")
                    assertEquals(1,ui.playlistIntents.filterIsInstance<PlaylistsIntent.SelectPlaylist>().size)
                    assertTrue(ui.intents.isEmpty())
                    assertEquals(entry,ui.navigation.playlistEntry(ui.navigation.top))
                }
    }

    @Test fun playlistCreateUsesUniqueNewTargetAndFailureCancelsAutoOpen() = onUiThread {
        for (online in listOf(false,true)) Fixture(1280,720,online = online,playlistMode = true).use { ui ->
            ui.click("More actions for Road playlist"); ui.click("Rename"); ui.setField("Renamed"); ui.click("Save")
            if (online) assertTrue(ui.onlinePlaylistIntents.last() is OnlinePlaylistsIntent.RenamePlaylist)
            else assertTrue(ui.playlistIntents.last() is PlaylistsIntent.RenamePlaylist)
            ui.click("More actions for Road playlist"); ui.click("Delete playlist"); ui.click("Delete")
            if (online) assertEquals(OnlinePlaylistsIntent.DeletePlaylist("road"),ui.onlinePlaylistIntents.last())
            else assertEquals(PlaylistsIntent.DeletePlaylist("road"),ui.playlistIntents.last())
            ui.click("New playlist"); ui.setField("New"); ui.click("Create")
            assertEquals("New",ui.playlistsEntry.pendingCreation?.name)
            val new = PlaylistSummary("new","New")
            if (online) ui.onlinePlaylists = ui.onlinePlaylists.copy(playlists = ui.onlinePlaylists.playlists + new)
            else ui.playlists = ui.playlists.copy(playlists = ui.playlists.playlists + new)
            ui.render()
            assertEquals("new",ui.navigation.top.entityId)
            assertNull(ui.playlistsEntry.pendingCreation)
            ui.click("More actions for " + if (online) "Road playlist" else "New")
            ui.click("Import playlist"); ui.setField("Song - Artist"); ui.click("Import")
            if (online) assertEquals(OnlinePlaylistsIntent.ImportPlaylistText("new","Song - Artist"),ui.onlinePlaylistIntents.last())
            else assertEquals(PlaylistsIntent.ImportPlaylistText("new","Song - Artist"),ui.playlistIntents.last())
            ui.click("Close")
        }
        Fixture(1280,720,playlistMode = true).use { ui ->
            ui.playlistsEntry.pendingCreation = PendingPlaylistCreation(null,"New",setOf("road"))
            ui.playlists = ui.playlists.copy(message = UiText.Raw("Create failed")); ui.render()
            ui.playlists = ui.playlists.copy(message = null,playlists = ui.playlists.playlists + PlaylistSummary("new","New")); ui.render()
            assertFalse(ui.navigation.hasDetails)
            assertNull(ui.playlistsEntry.pendingCreation)
        }
    }

    @Test fun playlistPositionAndBindingSurviveRotationChildrenAndHostRestore() = onUiThread {
        Fixture(1920, 720, playlistMode = true).use { ui ->
            ui.openPlaylist(); assertEquals(1, ui.playlistIntents.filterIsInstance<PlaylistsIntent.SelectPlaylist>().size)
            ui.scrollPlaylistTo(40)
            val route = ui.navigation.top
            val entry = ui.navigation.playlistEntry(route)
            val index = entry.list.firstVisibleItemIndex
            val offset = entry.list.firstVisibleItemScrollOffset
            ui.resize(800,1280)
            assertEquals(index, entry.list.firstVisibleItemIndex)
            assertEquals(offset, entry.list.firstVisibleItemScrollOffset)
            ui.navigation.open(LibraryNavigationTarget.Album("album")); ui.render()
            ui.navigation.pop(); ui.render()
            assertEquals(index, entry.list.firstVisibleItemIndex)
            ui.rebuild()
            assertEquals(index, entry.list.firstVisibleItemIndex)
            assertEquals(1, ui.playlistIntents.filterIsInstance<PlaylistsIntent.SelectPlaylist>().size)
            ui.navigation.requestBack(); ui.render()
            assertEquals(PlaylistsIntent.BackToList, ui.playlistIntents.last())
            assertTrue(ui.visible("Road playlist"))
        }
    }

    @Test fun playlistSearchClearsOnLandscapeAndRejectsRetiredTextCallbacks() = onUiThread {
        Fixture(480,900,playlistMode = true).use { ui ->
            ui.changeSearch("Road")
            val retired = ui.nodes().last { it.config.contains(SemanticsActions.SetText) }.config[SemanticsActions.SetText].action!!
            ui.resize(1280,720)
            assertEquals("",ui.playlistsEntry.query)
            assertFalse(ui.playlistsEntry.searchActive)
            retired.invoke(androidx.compose.ui.text.AnnotatedString("late")); ui.render()
            assertEquals("",ui.playlistsEntry.query)
            ui.resize(480,900)
            assertTrue(ui.visible("Search")); assertTrue(ui.visible("Road playlist"))
            ui.changeSearch("new")
            retired.invoke(androidx.compose.ui.text.AnnotatedString("late again")); ui.render()
            assertEquals("new",ui.playlistsEntry.query)
        }
    }

    @Test fun playlistOnlineFailurePersistsAndDuplicateRemovalUsesOriginalIndex() = onUiThread {
        Fixture(1280,720,online = true,playlistMode = true).use { ui ->
            ui.openPlaylist()
            val route = ui.navigation.top
            ui.onlinePlaylists = ui.onlinePlaylists.copy(selectedPlaylist = null,
                errorMessage = UiText.Raw("detail failed")); ui.render()
            assertTrue(ui.visible("detail failed")); assertTrue(ui.visible("Retry"))
            ui.onlinePlaylists = ui.onlinePlaylists.copy(errorMessage = null); ui.render()
            assertTrue(ui.visible("detail failed"))
            ui.click("Retry")
            assertEquals(2,ui.onlinePlaylistIntents.filterIsInstance<OnlinePlaylistsIntent.SelectPlaylist>().size)
            val track = ui.tracks.first()
            ui.onlinePlaylists = ui.onlinePlaylists.copy(selectedPlaylist = PlaylistDetail("road", "Road playlist",
                tracks = listOf(PlaylistTrackEntry(track),PlaylistTrackEntry(track))))
            ui.render()
            assertEquals(null,ui.navigation.playlistEntry(route).loadFailure)
            ui.click("Play all")
            assertEquals(listOf(track,track),(ui.intents.last() as PlayerIntent.PlayTracks).tracks)
            ui.click("More actions for Song 0"); ui.click("Remove from playlist")
            assertEquals(1,(ui.onlinePlaylistIntents.last() as OnlinePlaylistsIntent.RemoveTrack).index)
            ui.playlistBindingSuspended = true
            ui.onlinePlaylists = ui.onlinePlaylists.copy(sourceId = "other",playlists = listOf(PlaylistSummary("road","Wrong source")),
                selectedPlaylist = PlaylistDetail("road","Wrong source",tracks = listOf(PlaylistTrackEntry(track.copy(title = "Wrong song")))))
            ui.render()
            assertFalse(ui.visible("Wrong source")); assertFalse(ui.visible("Wrong song"))
            ui.playlistBindingSuspended = false; ui.render()
            assertEquals("source",ui.onlinePlaylists.sourceId)
            assertTrue(ui.visible("Road playlist"))
        }
    }

    @Test fun actualShellsShowSongsAndTheLastMenuClearsThePlayer() = onUiThread {
        for ((w, h) in listOf(1280 to 720, 1920 to 720, 800 to 1280, 480 to 900, 640 to 400)) Fixture(w, h, visual = true).use { ui ->
            assertTrue(ui.visible("96 首歌曲"))
            assertTrue(ui.visible("播放全部"))
            ui.capture("$w-$h")
            ui.scrollTo(99)
            val menu = ui.label("山阴路的夏天 96 的更多操作").boundsInRoot
            assertTrue(menu.bottom <= ui.playerTop(), "$w/$h: $menu")
            ui.capture("$w-$h-bottom")
            ui.click("山阴路的夏天 96 的更多操作")
            assertTrue(ui.visible("查看歌手"))
            assertTrue(ui.intents.isEmpty())
        }
    }
    @Test fun orientationTabsDetailsAndHostRecreationKeepTheSameSongPosition() = onUiThread {
        Fixture(1280, 720).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            ui.resize(480, 900); assertEquals(position, ui.position(), 0.02f)
            ui.selectedTab = AppTab.Playlists; ui.render()
            ui.selectedTab = AppTab.Favorites; ui.render(); assertEquals(position, ui.position(), 0.02f)
            ui.selectedTab = AppTab.My; ui.render()
            ui.selectedTab = AppTab.Favorites; ui.render(); assertEquals(position, ui.position(), 0.02f)
            ui.navigation.open(LibraryNavigationTarget.Artist("artist", artistName = "Artist A")); ui.render()
            ui.navigation.pop(); ui.render(); assertEquals(position, ui.position(), 0.02f)
            ui.resize(1280, 720); assertEquals(position, ui.position(), 0.02f)
            ui.rebuild(); assertEquals(position, ui.position(), 0.02f)
            assertTrue(ui.localIntents.isEmpty()); assertTrue(ui.onlineIntents.isEmpty()); assertTrue(ui.intents.isEmpty())
        }
    }
    @Test fun loadingKeepsContentPositionWhileSearchSourceAndSortResetIt() = onUiThread {
        Fixture(1280, 720).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            ui.loading = true; ui.render(); assertTrue(ui.visible("Loading"))
            ui.loading = false; ui.render(); assertEquals(position, ui.position(), 0.02f)
            ui.sendFavorite(FavoritesIntent.SearchChanged("Song")); ui.render(); assertTrue(ui.position() < 4f)
            ui.scrollTo(35); ui.sendFavorite(FavoritesIntent.SourceFilterChanged(LibrarySourceFilter.DOWNLOADED)); ui.render(); assertTrue(ui.position() < 4f)
            ui.scrollTo(35); ui.sendFavorite(FavoritesIntent.TrackSortChanged(TrackSortMode.entries.first { it != ui.sort })); ui.render(); assertTrue(ui.position() < 4f)
        }
    }
    @Test fun batchRequestsWorkAgainAfterSwitchingShellsAndSelectionDoesNotPlay() = onUiThread {
        Fixture(480, 900).use { ui ->
            ui.enterBatch(); assertTrue(ui.visible("Cancel"))
            ui.resize(1280, 720); assertTrue(ui.visible("Cancel")); ui.click("Cancel")
            ui.resize(480, 900); ui.capture("batch-after-return-portrait"); ui.enterBatch(); assertTrue(ui.visible("Cancel"))
            ui.click("Song 0")
            assertTrue(ui.nodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On })
            assertTrue(ui.intents.isEmpty())
            ui.sendFavorite(FavoritesIntent.SearchChanged("Song 1")); ui.render(); assertFalse(ui.visible("Cancel"))
        }
    }
    @Test fun restoredOnlinePositionSurvivesRememberedSourceLoading() = onUiThread {
        for ((w, h) in listOf(1280 to 720, 480 to 900)) Fixture(w, h, online = true).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            val loaded = ui.onlineState
            // A fresh Store retains the remembered source while its data is still loading.
            ui.loading = true; ui.onlineState = OnlineFavoritesState(sourceId = loaded.sourceId, isLoading = true)
            ui.rebuild()
            ui.onlineState = OnlineFavoritesState(sourceId = "source", isLoading = true); ui.render()
            ui.onlineState = loaded; ui.loading = false; ui.render()
            assertEquals(position, ui.position(), 0.02f)
            assertTrue(ui.localIntents.isEmpty()); assertTrue(ui.onlineIntents.isEmpty())
        }
    }
    @Test fun bothShellsUseConditionActionsWithoutResettingOnSameConditions() = onUiThread {
        for ((w, h) in listOf(1280 to 720, 480 to 900)) Fixture(w, h).use { ui ->
            ui.scrollTo(35)
            val position = ui.position()
            ui.sendFavorite(FavoritesIntent.SearchChanged("")); ui.render()
            ui.sendFavorite(FavoritesIntent.TrackSortChanged(ui.sort)); ui.render()
            ui.sendFavorite(FavoritesIntent.Refresh); ui.render()
            assertEquals(position, ui.position(), 0.02f)
            if (w > h) ui.scrollTo(0) // The landscape search tool scrolls with the list.
            ui.changeSearch("Song"); assertTrue(ui.position() < 4f)
            if (w < h) ui.click("Exit search")
            ui.scrollTo(35)
            if (w < h) {
                ui.click("More actions"); ui.click("Sources"); ui.click("Server · Online")
            } else {
                ui.sendOnline(OnlineFavoritesIntent.SelectSource("source")); ui.render()
            }
            ui.onlineState = OnlineFavoritesState(sourceId = "source", tracks = ui.tracks); ui.render()
            assertTrue(ui.position() < 4f)
            assertEquals(OnlineFavoritesIntent.SelectSource("source"), ui.onlineIntents.single())
        }
    }
    @Test fun rebuildingClosesUnsubmittedQualitySelectionWithoutDownloading() = onUiThread {
        Fixture(1280, 720).use { ui ->
            ui.click("Batch actions"); ui.click("Song 0"); ui.click("Download selected songs")
            assertTrue(ui.visible("Select download quality"))
            ui.rebuild()
            assertFalse(ui.visible("Select download quality"))
            assertTrue(ui.offlineIntents.none { it is OfflineDownloadIntent.DownloadMany })
            assertTrue(ui.nodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On })
        }
    }
    @Test fun sameSourceRefreshAndPagingKeepPositionWhileAnExplicitConditionChangeClosesTheMenu() = onUiThread {
        Fixture(480, 900, online = true).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            val loaded = ui.onlineState
            ui.sendOnline(OnlineFavoritesIntent.Refresh)
            ui.onlineState = loaded.copy(tracks = emptyList(), isLoading = true, canLoadMore = false); ui.render()
            ui.onlineState = loaded; ui.render()
            assertEquals(position, ui.position(), 0.02f)
            ui.sendOnline(OnlineFavoritesIntent.LoadMore); ui.render()
            assertEquals(position, ui.position(), 0.02f)
            ui.click("More actions for Song 31"); assertTrue(ui.visible("View album"))
            ui.sendOnline(OnlineFavoritesIntent.SearchChanged("Song")); ui.render()
            assertTrue(ui.position() < 4f)
            assertFalse(ui.visible("View album"))
            assertEquals(listOf(OnlineFavoritesIntent.Refresh, OnlineFavoritesIntent.LoadMore,
                OnlineFavoritesIntent.SearchChanged("Song")), ui.onlineIntents)
        }
    }
    @Test fun batchDownloadSubmitsOnlySelectedSongsAndTheirQuality() = onUiThread {
        Fixture(1280,720).use { ui ->
            ui.click("Batch actions"); ui.click("Song 0"); ui.click("Song 2"); ui.click("Download selected songs")
            ui.chooseOriginalQuality()
            val download = ui.offlineIntents.filterIsInstance<OfflineDownloadIntent.DownloadMany>().single()
            assertEquals(listOf("track-0","track-2"),download.tracks.map { it.id })
            assertEquals(NavidromeAudioQuality.Original,download.quality)
            assertTrue(ui.intents.isEmpty()); assertFalse(ui.visible("Cancel"))
        }
    }
    @Test fun scalesAndFontsKeepPlaybackMenusAndTheToolbarReachable() = onUiThread {
        for ((w,h) in listOf(1280 to 720, 480 to 900, 640 to 400)) for (scale in AppDisplayScalePreset.entries) for (font in listOf(1f, 1.5f, 2f)) {
            Fixture(w,h,density = scale.scale,fontScale = font,longNames = true,dark = scale.ordinal % 2 == 0).use { ui ->
                val list = ui.tag("automotive-favorites-list").boundsInRoot
                assertTrue((minOf(list.bottom,ui.playerTop()) - list.top) / scale.scale >= 96f)
                if (w > h) {
                    val toolbar = ui.tag("favorites-toolbar").boundsInRoot
                    assertTrue(toolbar.right <= list.right && toolbar.bottom <= ui.playerTop())
                }
                ui.scrollTo(99)
                val menu = ui.label("More actions for Song 95").boundsInRoot
                assertTrue(menu.bottom <= ui.playerTop())
                if (scale == AppDisplayScalePreset.ExtraLarge && font == 2f) ui.capture("large-$w-$h")
                ui.click("More actions for Song 95"); assertTrue(ui.visible("View artist"))
                assertTrue(ui.intents.isEmpty())
            }
        }
    }
    @Test fun rowColumnsUseTheirActualWidthAcrossBothOrientations() = onUiThread {
        for (font in listOf(1f,1.5f,2f)) for (delta in listOf(-1,0)) {
            val width = (520 * font).toInt() + 48 + delta
            Fixture(width,2000,fontScale = font).use { ui ->
                ui.scrollTo(4)
                assertEquals(delta == 0,ui.visible("120:00"))
            }
        }
    }
    @Test fun onlinePartialResultsKeepLoadMoreAboveThePlayerAndDoNotLoadOnRotation() = onUiThread {
        Fixture(480,900,online = true).use { ui ->
            assertTrue(ui.visible("96+ songs")); assertTrue(ui.visible("Play loaded songs"))
            ui.scrollTo(100)
            assertTrue(ui.tag("favorites-load-more").boundsInRoot.bottom <= ui.playerTop())
            ui.capture("online-partial-portrait")
            ui.resize(1280,720); assertTrue(ui.onlineIntents.isEmpty())
            ui.scrollTo(100); ui.clickTag("favorites-load-more")
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.LoadMore),ui.onlineIntents)
        }
    }
    @Test fun verticalTouchScrollingDoesNotPlaySongs() = onUiThread {
        for ((w,h) in listOf(1280 to 720,480 to 900)) Fixture(w,h).use { ui ->
            ui.scrollTo(5); val before = ui.position(); ui.swipeVertically()
            assertTrue(ui.position() > before); assertTrue(ui.intents.isEmpty())
        }
    }
    @Test fun liveScaleAndFontChangesKeepTheCurrentSong() = onUiThread {
        Fixture(1280,720).use { ui ->
            ui.scrollTo(35); val position = ui.position()
            ui.density = 1.2f; ui.fontScale = 2f; ui.render()
            assertEquals(position,ui.position(),0.02f)
            ui.density = 0.8f; ui.fontScale = 1f; ui.render()
            assertEquals(position,ui.position(),0.02f)
            assertTrue(ui.localIntents.isEmpty()); assertTrue(ui.intents.isEmpty())
        }
    }
    @Test fun switchingOnlineSourcesDuringLoadingUsesOnlyTheLatestPageForActions() = onUiThread {
        Fixture(480,900,online = true).use { ui ->
            ui.scrollTo(35)
            ui.onlineState = OnlineFavoritesState(sourceId = "second",isLoading = true); ui.render()
            ui.resize(1280,720)
            ui.onlineState = OnlineFavoritesState(sourceId = "third",isLoading = true); ui.render()
            val latest = ui.tracks.first().copy(id = "latest",sourceId = "third",title = "Latest song")
            ui.onlineState = OnlineFavoritesState(sourceId = "third",tracks = listOf(latest)); ui.render()
            ui.click("Play all")
            assertEquals(PlayerIntent.PlayTracks(listOf(latest),0),ui.intents.single())
            ui.click("More actions for Latest song"); ui.click("Remove from favorites")
            assertEquals(listOf<OnlineFavoritesIntent>(OnlineFavoritesIntent.SetFavorite("third",latest,false)),ui.onlineIntents)
        }
    }
    @Test fun threeDigitIndicesAndTheLastMenuFitLargePortraitText() = onUiThread {
        Fixture(480,900,fontScale = 2f,count = 125).use { ui ->
            ui.scrollTo(128)
            assertTrue(ui.visible("125"))
            assertTrue(ui.label("More actions for Song 124").boundsInRoot.bottom <= ui.playerTop())
            ui.click("More actions for Song 124"); assertTrue(ui.visible("View album"))
        }
    }
    @Test fun leavingSearchClearsItsQueryWithoutChangingPage() = onUiThread {
        Fixture(480,900).use { ui ->
            ui.query = "Song"; ui.render()
            assertTrue(ui.visible("Exit search"))
            ui.click("Exit search")
            assertEquals("",ui.query)
            assertFalse(ui.visible("Exit search"))
            assertFalse(ui.navigation.hasDetails)
        }
    }
    @Test fun aFinishedEmptyRefreshClearsSelectionsAfterTransientLoading() = onUiThread {
        Fixture(1280,720).use { ui ->
            ui.click("Batch actions"); ui.click("Song 0")
            ui.loading = true; ui.favorites = emptyList(); ui.render()
            assertTrue(ui.visible("Cancel"))
            ui.loading = false; ui.render()
            assertFalse(ui.visible("Cancel"))
            assertTrue(ui.visible("0 songs")); assertTrue(ui.intents.isEmpty())
        }
    }
    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (e: Throwable) { failure = e } }
        failure?.let { throw it }
    }
    private class Fixture(
        width: Int, height: Int, val visual: Boolean = false, density: Float = 1f,
        fontScale: Float = 1f, val dark: Boolean = false, val longNames: Boolean = false,
        online: Boolean = false, count: Int = 96, val playlistMode: Boolean = false, val folderMode: Boolean = false, folderSources: Int = 0,
    ) : AutoCloseable {
        var width by mutableStateOf(width)
        var height by mutableStateOf(height)
        var density by mutableStateOf(density)
        var fontScale by mutableStateOf(fontScale)
        var selectedTab by mutableStateOf(if (folderMode) AppTab.Library else if (playlistMode) AppTab.Playlists else AppTab.Favorites)
        private var registry = SaveableStateRegistry(null) { true }
        private val scheduler = TestCoroutineScheduler()
        private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        private val platform = PlatformDescriptor(ANDROID_AUTOMOTIVE_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, false))
        private val folderName = if (longNames) "A long folder name with 中文 and additional information for wrapping" else "Album"
        val tracks = (0 until count).map { index -> Track("track-$index", "source",
            if (visual) "山阴路的夏天 ${index + 1}" else "Song $index",
            if (longNames) "A very long artist name with several collaborators and additional information" else if (visual) "李志" else "Artist A",
            if (longNames) "A very long album title with additional information" else if (visual) "我爱南京" else "Album A",
            durationMs = 7_200_000, mediaLocator = buildNavidromeSongLocator("source", "$index"), relativePath = if (folderMode) "Parent/$folderName/${index.toString().padStart(3,'0')}.flac" else "$index",
            artistId = "artist", albumId = "album") }
        private val folderTracks = tracks + if (folderMode) listOf(
            tracks.first().copy(id = "parent-song",title = "Parent song",relativePath = "Parent/parent.flac"),
            tracks.first().copy(id = "root-song",title = "Root song",relativePath = "root.flac"),
            tracks.first().copy(id = "only-folders",relativePath = "Only folders/Nested/song.flac"),
            tracks.first().copy(id = "other-song",sourceId = "other",title = "Other source song",relativePath = "Parent/$folderName/song.flac")) +
            (0..15).map { tracks.first().copy(id = "sibling-$it",relativePath = "Parent/Sibling $it/song.flac") } +
            (0 until folderSources).map { tracks.first().copy(id = "extra-song-$it",sourceId = "extra-$it",relativePath = "song.flac") } else emptyList()
        var library by mutableStateOf(if (folderMode) LibraryState(isLoadingContent = false,tracks = folderTracks,filteredTracks = folderTracks,
            sourceLabelsById = mapOf("source" to if (visual) "本地音乐" else "Storage", "other" to "Other storage") +
                (0 until folderSources).associate { "extra-$it" to "Storage %02d".format(it) }) else LibraryState())
        val libraryIntents = mutableListOf<LibraryIntent>()
        val playlistTargets = mutableListOf<Track>()
        fun sendLibrary(intent: LibraryIntent) {
            libraryIntents += intent
            when (intent) {
                is LibraryIntent.SearchChanged -> library = library.copy(query = intent.query,
                    filteredTracks = folderTracks.filter { it.title.contains(intent.query,true) || it.relativePath.contains(intent.query,true) })
                is LibraryIntent.SourceFilterChanged -> library = library.copy(selectedSourceFilter = intent.filter)
                is LibraryIntent.TrackSortChanged -> library = library.copy(selectedTrackSortMode = intent.mode)
            }
        }
        val folderEntry get() = navigation.folderEntry(navigation.top)
        fun openFolder(path: String = "Parent/$folderName", sourceId: String = "source") {
            val tree = deriveLibraryFolderTree(library.filteredTracks,library.sourceLabelsById)
            navigation.openFolder(tree.nodesByKey.getValue(LibraryFolderKey(sourceId,path)),library.selectedSourceFilter); render()
        }
        fun scrollFolderRootsTo(index: Int,offset: Int = 0) { scope.launch { navigation.folderListState.scrollToItem(index,offset) }; render() }
        fun scrollFolderTo(index: Int,offset: Int = 0) { scope.launch { folderEntry.list.scrollToItem(index,offset) }; render() }
        var favorites by mutableStateOf(if (folderMode) emptyList() else tracks)
        var loading by mutableStateOf(false)
        var query by mutableStateOf("")
        var filter by mutableStateOf<LibrarySourceFilter>(LibrarySourceFilter.ALL)
        var sort by mutableStateOf(FavoritesState().selectedTrackSortMode)
        var onlineState by mutableStateOf(if (online) OnlineFavoritesState(sourceId = "source", tracks = tracks, canLoadMore = true) else OnlineFavoritesState())
        val localIntents = mutableListOf<FavoritesIntent>()
        val onlineIntents = mutableListOf<OnlineFavoritesIntent>()
        val offlineIntents = mutableListOf<OfflineDownloadIntent>()
        val intents = mutableListOf<PlayerIntent>()
        private lateinit var favoritesEntry: AutomotiveFavoritesEntry
        lateinit var playlistsEntry: AutomotivePlaylistsEntry
        private val playlistSummary = PlaylistSummary("road", if (longNames) "A very long playlist name with additional information that spans several lines" else if (visual) "通勤歌单" else "Road playlist", trackCount = count)
        var playlists by mutableStateOf(PlaylistsState(isLoadingContent = false, playlists = listOf(playlistSummary)))
        var onlinePlaylists by mutableStateOf(OnlinePlaylistsState(sourceId = if (online && playlistMode) "source" else null,
            playlists = listOf(playlistSummary)))
        var playlistBindingSuspended by mutableStateOf(false)
        val playlistIntents = mutableListOf<PlaylistsIntent>()
        val onlinePlaylistIntents = mutableListOf<OnlinePlaylistsIntent>()
        fun sendPlaylist(intent: PlaylistsIntent) {
            playlistIntents += intent
            when (intent) {
                is PlaylistsIntent.SelectPlaylist -> playlists = playlists.copy(selectedPlaylistId = intent.playlistId,
                    selectedPlaylist = intent.playlistId?.let { id -> PlaylistDetail(id,playlists.playlists.firstOrNull { it.id == id }?.name.orEmpty(),
                        tracks = tracks.map { PlaylistTrackEntry(it) }) })
                PlaylistsIntent.BackToList -> playlists = playlists.copy(selectedPlaylistId = null,selectedPlaylist = null)
                else -> Unit
            }
        }
        fun sendOnlinePlaylist(intent: OnlinePlaylistsIntent) {
            onlinePlaylistIntents += intent
            when (intent) {
                is OnlinePlaylistsIntent.SelectSource -> onlinePlaylists = OnlinePlaylistsState(sourceId = intent.sourceId)
                is OnlinePlaylistsIntent.SelectPlaylist -> onlinePlaylists = onlinePlaylists.copy(selectedPlaylistId = intent.playlistId,
                    selectedPlaylist = intent.playlistId?.let { PlaylistDetail(it,playlistSummary.name,tracks = tracks.map { PlaylistTrackEntry(it) }) })
                else -> Unit
            }
        }
        fun openPlaylist() {
            navigation.openPlaylist(playlistSummary,onlinePlaylists.sourceId,LibrarySourceFilter.ALL); render()
        }
        fun scrollPlaylistTo(index: Int, offset: Int = 0) {
            scope.launch { navigation.playlistEntry(navigation.top).list.scrollToItem(index,offset) }; render()
        }
        fun revealPlaylistPlayback() {
            val entry = navigation.playlistEntry(navigation.top)
            if (entry.headerLayout?.split == true) scope.launch { entry.summaryScroll.scrollTo(Int.MAX_VALUE) }
            else {
                val header = entry.list.layoutInfo.visibleItemsInfo.first { it.index == 0 }
                scope.launch { entry.list.scrollToItem(0,(header.size - 140 * density).toInt().coerceAtLeast(0)) }
            }
            render()
        }
        var importState by mutableStateOf(ImportState(capabilities = platform.capabilities, sources = listOf(SourceWithStatus(
            ImportSource("source", if (folderMode) ImportSourceType.LOCAL_FOLDER else ImportSourceType.NAVIDROME, "Server", "",
                indexMode = if (folderMode) ImportSourceIndexMode.LOCAL_INDEX else ImportSourceIndexMode.ONLINE)))))
        private val player = PlayerState(snapshot = PlaybackSnapshot(queue = listOf(Track("playing", "local",
            if (visual) "正在播放" else "Now playing", mediaLocator = "", relativePath = "playing")), currentIndex = 0))
        private val queries = TestMusicDetailQueries(tracks = { tracks })
        val navigation = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), queries, scope, mutableStateOf(false), {})
        private var frame = 0L
        private var scene = createScene()
        init { render() }
        private fun localState(): FavoritesState {
            val visible = if (loading) emptyList() else favorites.filter { query.isBlank() || it.title.contains(query, true) }
            return FavoritesState(isLoadingContent = loading, tracks = favorites, filteredTracks = visible,
                favoriteTrackIds = favorites.mapTo(mutableSetOf()) { it.id }, query = query,
                selectedSourceFilter = filter, selectedTrackSortMode = sort, canRefreshRemote = true)
        }
        private fun onFavorite(intent: FavoritesIntent) {
            localIntents += intent
            when (intent) {
                is FavoritesIntent.SearchChanged -> query = intent.query
                is FavoritesIntent.SourceFilterChanged -> filter = intent.filter
                is FavoritesIntent.TrackSortChanged -> sort = intent.mode
                is FavoritesIntent.ToggleFavorite -> favorites = if (favorites.any { it.id == intent.track.id }) favorites.filterNot { it.id == intent.track.id } else favorites + intent.track
                else -> Unit
            }
        }
        fun sendFavorite(intent: FavoritesIntent) {
            favoritesEntry.prepareForIntent(intent, localState())
            onFavorite(intent)
        }
        fun sendOnline(intent: OnlineFavoritesIntent) {
            favoritesEntry.prepareForIntent(intent, onlineState)
            onlineIntents += intent
            when (intent) {
                is OnlineFavoritesIntent.SearchChanged -> onlineState = onlineState.copy(query = intent.query)
                is OnlineFavoritesIntent.SelectSource -> onlineState = OnlineFavoritesState(sourceId = intent.sourceId, isLoading = intent.sourceId != null)
                else -> Unit
            }
        }
        private fun createScene() = ImageComposeScene(width, height, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                // The entry owner belongs above either shell and participates in this registry.
                val savedEntry = rememberAutomotiveFavoritesEntry()
                favoritesEntry = savedEntry
                playlistsEntry = rememberAutomotivePlaylistsEntry()
                BindPlaylistDetailNavigation(navigation,playlists,onlinePlaylists,importState,playlistBindingSuspended,::sendPlaylist,::sendOnlinePlaylist)
                CompositionLocalProvider(LocalAutomotiveFavoritesEntry provides savedEntry,
                    LocalAutomotivePlaylistsEntry provides playlistsEntry,
                    LocalDensity provides Density(density, fontScale), LocalPlatformDescriptor provides platform,
                    LocalMusicDetailNavigation provides navigation,
                    LocalPlayerPresentation provides remember { PlayerPresentation() },
                    LocalOfflineDownloadUiState provides OfflineDownloadUiState(onIntent = { offlineIntents += it }, availableSpaceBytes = Long.MAX_VALUE),
                    LocalTrackListActionBindings provides TrackListActionBindings(
                        favoriteState = { TrackFavoriteUiState(it.id in favorites.map { track -> track.id }) },
                        onSetFavorite = { track, _ -> onFavorite(FavoritesIntent.ToggleFavorite(track)) },
                        onAddToPlaylist = { playlistTargets += it },
                    )) {
                    ProvideUiResourceLanguage(if (visual) "zh-CN" else "en") {
                        LynMusicTheme(themeTokens = if (dark) CLASSIC_APP_THEME_TOKENS else OCEAN_APP_THEME_TOKENS,
                            textPalette = if (dark) AppThemeTextPalette.White else AppThemeTextPalette.Black) { Shell() }
                    }
                }
            }
        }
        @Composable private fun Shell() {
            // Match AutomotiveApp's backing surface around the real responsive shell.
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth > maxHeight) AutomotiveLandscapeShell(
                navigationRailState = rememberWideNavigationRailState(),
                selectedTab = selectedTab, onTabSelected = { navigation.clear(); selectedTab = it }, platform = platform,
                myState = MyState(), libraryState = library, onlineLibraryState = OnlineLibraryState(),
                playlistsState = playlists, onlinePlaylistsState = onlinePlaylists,
                favoritesState = localState(), onlineFavoritesState = onlineState,
                importState = importState, playerState = player,
                settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = ::sendLibrary, onOnlineLibraryIntent = {},
                onPlaylistsIntent = ::sendPlaylist, onOnlinePlaylistsIntent = ::sendOnlinePlaylist, onFavoritesIntent = ::sendFavorite, onOnlineFavoritesIntent = ::sendOnline,
                onImportIntent = {}, onPlayerIntent = { intents += it }, onSettingsIntent = {},
                onOpenBackgroundRunSettings = {}, onOpenLibraryNavigationTarget = { navigation.open(it) }, onOpenAddToPlaylist = {},
            ) else AutomotivePortraitShell(
                portraitNavigation = true, mobilePortraitMiniPlayer = true, hideMiniPlayerBar = false,
                selectedTab = selectedTab, onTabSelected = { navigation.clear(); selectedTab = it }, platform = platform,
                myState = MyState(), libraryState = library, onlineLibraryState = OnlineLibraryState(),
                playlistsState = playlists, onlinePlaylistsState = onlinePlaylists,
                favoritesState = localState(), onlineFavoritesState = onlineState,
                importState = importState, playerState = player,
                settingsState = SettingsState(), onMyIntent = {}, onLibraryIntent = ::sendLibrary, onOnlineLibraryIntent = {},
                onPlaylistsIntent = ::sendPlaylist, onOnlinePlaylistsIntent = ::sendOnlinePlaylist, onFavoritesIntent = ::sendFavorite, onOnlineFavoritesIntent = ::sendOnline,
                onImportIntent = {}, onPlayerIntent = { intents += it }, onSettingsIntent = {},
                onOpenBackgroundRunSettings = {}, onOpenLibraryNavigationTarget = { navigation.open(it) }, onOpenAddToPlaylist = {},
            )
        }
        }
        fun render(frames: Int = 40) { repeat(frames) {
            scheduler.runCurrent(); Snapshot.sendApplyNotifications(); scene.render(++frame * 16_666_667L).close()
        } }
        fun resize(w: Int, h: Int) {
            width = w; height = h; scene.constraints = Constraints.fixed(w, h); render()
            assertEquals(w > h, nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "automotive-navigation-rail" })
        }
        fun rebuild() {
            val saved = registry.performSave(); scene.close()
            registry = SaveableStateRegistry(saved) { true }; scene = createScene(); render()
        }
        fun scrollTo(index: Int) {
            assertTrue(tag("automotive-favorites-list").config[SemanticsActions.ScrollToIndex].action!!.invoke(index)); render()
        }
        fun position() = tag("automotive-favorites-list").config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun playerTop(): Float = generateSequence(label(if (visual) "正在播放" else "Now playing").parent) { it.parent }
            .first { it.config.contains(SemanticsActions.OnClick) }.boundsInRoot.top
        fun nodes(): List<SemanticsNode> {
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.filter { !it.boundsInRoot.isEmpty }
        }
        fun hasTag(value: String) = nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == value }
        fun tag(value: String) = nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == value } ?: error("Missing $value; labels=${nodes().map { it.config.getOrNull(SemanticsProperties.Text) }}")
        private fun matches(n: SemanticsNode, value: String) = n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true ||
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true
        fun label(value: String) = nodes().lastOrNull { matches(it, value) }
            ?: error("Missing $value; labels=${nodes().map { it.config.getOrNull(SemanticsProperties.Text) to it.config.getOrNull(SemanticsProperties.ContentDescription) }}")
        fun visible(value: String) = nodes().any { matches(it, value) }
        fun click(value: String) { clickNode(label(value)) }
        fun clickTag(value: String) { clickNode(tag(value)) }
        private fun clickNode(node: SemanticsNode) {
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center, type = PointerType.Touch); render(80)
        }
        fun enterBatch() { click("More actions"); click("Batch actions") }
        fun changeSearch(value: String) {
            if (width < height) click("Search")
            val field = nodes().last { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString(value)))
            render()
        }
        fun setField(value: String) {
            val field = nodes().last { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString(value)))
            render()
        }
        fun chooseOriginalQuality() { clickNode(nodes().first { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains("Original", true) } == true
        }) }
        fun capture(name: String) {
            val file = File("build/reports/favorites-layout/automotive-shell-$name.png"); file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        }
        fun swipeVertically(listTag: String = "automotive-favorites-list") {
            val list = tag(listTag).boundsInRoot
            val start = Offset(list.center.x, minOf(list.bottom, playerTop()) - 12f)
            val end = Offset(list.center.x, list.top + 12f)
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, timeMillis = frame * 16)
            repeat(12) { step ->
                scene.sendPointerEvent(PointerEventType.Move, start + (end - start) * ((step + 1) / 12f), type = PointerType.Touch, timeMillis = frame * 16)
                render(1)
            }
            scene.sendPointerEvent(PointerEventType.Release, end, type = PointerType.Touch, timeMillis = frame * 16); render(80)
        }
        override fun close() { scene.close(); navigation.dispose(); scope.cancel() }
    }
}
