package top.iwesley.lyn.music.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import top.iwesley.lyn.music.displayText
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsIntent
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsState
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.onlineNavidromeSourceOptions

/** Lives above the navigation host, so child pages and shell changes do not reselect a playlist. */
internal class PlaylistDetailBinding {
    private var boundRoute: MusicDetailRoute? = null
    private var sourceRequested = false
    private var playlistRequested = false
    private var wasSuspended = false
    private var lastSelectedPlaylistId: String? = null

    fun sync(
        route: MusicDetailRoute?,
        state: PlaylistsState,
        online: OnlinePlaylistsState,
        availableSourceIds: Set<String>,
        suspended: Boolean,
        localIntent: (PlaylistsIntent) -> Unit,
        onlineIntent: (OnlinePlaylistsIntent) -> Unit,
        hasRetainedFailure: Boolean = false,
    ) {
        // The existing add-to-playlist dialog can temporarily select a different online source.
        if (suspended) { wasSuspended = true; return }
        if (boundRoute?.entryId != route?.entryId || wasSuspended) {
            sourceRequested = false
            playlistRequested = false
            wasSuspended = false
            lastSelectedPlaylistId = null
        }
        if (route == null) {
            boundRoute = null
            return
        }
        boundRoute = route
        if (route.sourceId != null && route.sourceId !in availableSourceIds) return
        if (online.sourceId != route.sourceId) {
            if (!sourceRequested) {
                sourceRequested = true
                onlineIntent(OnlinePlaylistsIntent.SelectSource(route.sourceId, persist = false))
            }
            return
        }
        val selectedId = if (route.sourceId == null) state.selectedPlaylistId else online.selectedPlaylistId
        if (selectedId != lastSelectedPlaylistId) {
            lastSelectedPlaylistId = selectedId
            // Creating a playlist in the add dialog can select it after the dialog has closed.
            if (selectedId != null && selectedId != route.entityId) playlistRequested = false
        }
        if (!playlistRequested) {
            playlistRequested = true
            val needsOnlineLoad = route.sourceId != null && online.selectedPlaylist?.id != route.entityId &&
                !online.isLoadingDetail && online.errorMessage == null && !hasRetainedFailure
            if (selectedId != route.entityId || needsOnlineLoad) {
                if (route.sourceId == null) localIntent(PlaylistsIntent.SelectPlaylist(route.entityId))
                else onlineIntent(OnlinePlaylistsIntent.SelectPlaylist(route.entityId))
            }
        }
    }
}

@Composable
internal fun BindPlaylistDetailNavigation(
    navigation: MusicDetailNavigation,
    state: PlaylistsState,
    online: OnlinePlaylistsState,
    importState: ImportState,
    suspended: Boolean,
    localIntent: (PlaylistsIntent) -> Unit,
    onlineIntent: (OnlinePlaylistsIntent) -> Unit,
) {
    val binding = remember(navigation) { PlaylistDetailBinding() }
    val route = navigation.playlistRoute
    val entry = route?.let(navigation::playlistEntry)
    var savedFailure by rememberSaveable(route?.entryId) { mutableStateOf(entry?.loadFailure) }
    val targetSelected = route?.sourceId != null && online.sourceId == route.sourceId &&
        online.selectedPlaylistId == route.entityId
    val detailReady = targetSelected && online.selectedPlaylist?.id == route?.entityId
    val currentFailure = if (targetSelected && !online.isLoadingDetail && !detailReady) online.errorMessage?.displayText() else null
    // Keep the detail error independently of the global, automatically dismissed message.
    val retainedFailure = when {
        targetSelected && (online.isLoadingDetail || detailReady) -> null
        currentFailure != null -> currentFailure
        else -> savedFailure
    }
    SideEffect {
        savedFailure = retainedFailure
        entry?.loadFailure = retainedFailure
    }
    val sources = importState.onlineNavidromeSourceOptions().map { it.sourceId }.toSet()
    LaunchedEffect(route, state.selectedPlaylistId, online.sourceId, online.selectedPlaylistId, sources, suspended) {
        binding.sync(route, state, online, sources, suspended, localIntent, onlineIntent,
            hasRetainedFailure = retainedFailure != null)
    }
}
