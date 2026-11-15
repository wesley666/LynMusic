package top.iwesley.lyn.music.automotive.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import top.iwesley.lyn.music.automotive.ui.platform.PlatformMusicDetailBackInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.*
import top.iwesley.lyn.music.feature.details.*
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.feature.player.PlayerIntent
import kotlin.random.Random

@Serializable
internal data class MusicDetailRoute(
    val entryId: String = "root",
    val kind: String = "root",
    val entityId: String = "",
    val sourceId: String? = null,
    val sourceFilter: String = "all",
    val title: String? = null,
    val artistName: String? = null,
    val artworkLocator: String? = null,
    val artworkCacheKey: String? = null,
    val folderPath: String? = null,
) : NavKey {
    val isRoot get() = kind == "root"
    fun sameTarget(other: MusicDetailRoute) = kind == other.kind && entityId == other.entityId &&
        sourceId == other.sourceId && sourceFilter == other.sourceFilter
    val librarySourceFilter get() = decodeLibrarySourceFilter(sourceFilter)
    fun artistRequest() = ArtistDetailRequest(entityId, librarySourceFilter, sourceId)
    fun albumRequest() = AlbumDetailRequest(entityId, librarySourceFilter, sourceId, title, artistName, artworkLocator)

}

internal fun LibraryNavigationTarget.detailRoute(): MusicDetailRoute = when (this) {
    is LibraryNavigationTarget.Album -> MusicDetailRoute(kind = "album", entityId = albumId,
        sourceFilter = encodeLibrarySourceFilter(preferredSourceFilter))
    is LibraryNavigationTarget.Artist -> MusicDetailRoute(kind = "artist", entityId = artistId,
        sourceFilter = encodeLibrarySourceFilter(preferredSourceFilter), title = artistName)
    is LibraryNavigationTarget.OnlineAlbum -> MusicDetailRoute(kind = "album", entityId = albumId,
        sourceId = sourceId, title = albumTitle, artistName = artistName, artworkLocator = artworkLocator)
    is LibraryNavigationTarget.OnlineArtist -> MusicDetailRoute(kind = "artist", entityId = artistId,
        sourceId = sourceId, title = artistName)
}

/** Each independently retriable query owns its job and publication generation. */
internal class DetailQueryState<T>(
    private val scope: CoroutineScope,
    private val query: () -> Flow<T>,
) {
    var data by mutableStateOf<T?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    private var version = 0L
    private var job: Job? = null
    private var active = true

    fun retry() {
        if (!active) return
        val requestVersion = ++version
        job?.cancel()
        loading = true
        error = null
        job = scope.launch {
            try {
                query().collect { result ->
                    if (active && version == requestVersion) {
                        data = result
                        loading = false
                        error = null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (active && version == requestVersion) {
                    data = null
                    error = failure
                    loading = false
                }
            }
        }
    }

    fun cancel() {
        active = false
        version++
        job?.cancel()
    }
}

internal enum class ArtistDetailTab { Songs, Albums, Favorites }

@OptIn(ExperimentalMaterial3Api::class)
internal class MusicDetailEntryState(
    private val route: MusicDetailRoute,
    queries: MusicDetailQueries,
    scope: CoroutineScope,
) {
    private val album = DetailQueryState(scope) {
        queries.observeAlbum(route.albumRequest())
    }
    val artistTracks = DetailQueryState(scope) { queries.observeArtistTracks(route.artistRequest()) }
    val artistAlbums = DetailQueryState(scope) { queries.observeArtistAlbums(route.artistRequest()) }
    val data get() = album.data
    val loading get() = album.loading
    val error get() = album.error
    var contentListState = LazyListState()
    var albumSummaryScrollState = ScrollState(0)
    var albumHeaderLayout: AlbumHeaderLayout? = null
    var albumListState = LazyListState()
    var favoriteListState = LazyListState()
    var selectedArtistTab by mutableStateOf(ArtistDetailTab.Songs)
    var artistHeaderState = TopAppBarState(-Float.MAX_VALUE, 0f, 0f)

    fun retry() {
        if (route.kind == "album") album.retry() else {
            artistTracks.retry()
            artistAlbums.retry()
        }
    }

    fun cancel() {
        album.cancel()
        artistTracks.cancel()
        artistAlbums.cancel()
    }
}

internal class MusicDetailNavigation(
    val backStack: NavBackStack<NavKey>,
    private val queries: MusicDetailQueries,
    private val scope: CoroutineScope,
    private val restorePlayer: MutableState<Boolean>,
    private val onRestorePlayer: () -> Unit,
    val libraryAlbumState: LibraryAlbumGridState = LibraryAlbumGridState(),
    val libraryRootState: MutableState<LibraryBrowserRootView> = mutableStateOf(
        if (backStack.any { (it as MusicDetailRoute).kind == "folder" }) LibraryBrowserRootView.Folders else LibraryBrowserRootView.Tracks),
) {
    val top get() = backStack.last() as MusicDetailRoute
    val hasDetails get() = backStack.size > 1
    var generation by mutableLongStateOf(0L)
        private set
    var immediate by mutableStateOf(false)
        private set
    private val entries = mutableMapOf<String, MusicDetailEntryState>()
    private val playlistEntries = mutableMapOf<String, PlaylistDetailEntryState>()
    private val folderEntries = mutableMapOf<String, FolderDetailEntryState>()
    var folderListState = LazyListState()
    private val backHandlers = mutableMapOf<String, () -> Unit>()
    val playlistRoute get() = backStack.filterIsInstance<MusicDetailRoute>().lastOrNull { it.kind == "playlist" }
    var gestureGeneration: Long? = null
    var gestureInProgress: () -> Boolean = { false }
    private fun captureGesture() {
        if (gestureGeneration == null && gestureInProgress()) gestureGeneration = generation
    }

    fun open(target: LibraryNavigationTarget, fromPlayer: Boolean = false) {
        val route = target.detailRoute()
        if (route.entityId.isBlank() || route.sourceId?.isBlank() == true) return
        if (fromPlayer) restorePlayer.value = true
        if (top.sameTarget(route)) return
        captureGesture()
        generation++
        immediate = false
        backStack.add(route.copy(entryId = "detail:${Random.nextLong()}"))
    }

    fun openPlaylist(playlist: PlaylistSummary, sourceId: String?, filter: LibrarySourceFilter) {
        if (playlist.id.isBlank() || sourceId?.isBlank() == true) return
        val route = MusicDetailRoute(kind = "playlist", entityId = playlist.id, sourceId = sourceId,
            sourceFilter = encodeLibrarySourceFilter(filter), title = playlist.name,
            artworkLocator = playlist.artworkLocator, artworkCacheKey = playlist.artworkCacheKey)
        if (top.sameTarget(route)) return
        captureGesture()
        generation++
        immediate = false
        backStack.add(route.copy(entryId = "detail:${Random.nextLong()}"))
    }

    fun playlistEntry(route: MusicDetailRoute): PlaylistDetailEntryState {
        playlistEntries[route.entryId]?.let { return it }
        return PlaylistDetailEntryState().also { state ->
            if (backStack.any { (it as MusicDetailRoute).entryId == route.entryId }) playlistEntries[route.entryId] = state
        }
    }

    fun openFolder(folder: top.iwesley.lyn.music.LibraryFolderNode, filter: LibrarySourceFilter) {
        if (folder.sourceId.isBlank()) return
        val route = MusicDetailRoute(kind = "folder", entityId = folder.key.stableId,
            sourceId = folder.sourceId, folderPath = folder.path, title = folder.name,
            sourceFilter = encodeLibrarySourceFilter(filter))
        if (top.sameTarget(route)) return
        libraryRootState.value = LibraryBrowserRootView.Folders
        captureGesture()
        generation++
        immediate = false
        backStack.add(route.copy(entryId = "detail:${Random.nextLong()}"))
    }

    fun folderEntry(route: MusicDetailRoute): FolderDetailEntryState {
        folderEntries[route.entryId]?.let { return it }
        return FolderDetailEntryState().also { state ->
            if (backStack.any { (it as MusicDetailRoute).entryId == route.entryId }) folderEntries[route.entryId] = state
        }
    }

    fun setBackHandler(id: String, handler: (() -> Unit)?) {
        if (handler == null) backHandlers.remove(id) else backHandlers[id] = handler
    }

    fun requestBack(id: String = top.entryId, epoch: Long = generation) {
        if (top.entryId != id || generation != epoch) return
        backHandlers[id]?.invoke() ?: pop(id, epoch)
    }

    fun pop(expectedEntryId: String = top.entryId, expectedGeneration: Long = generation) {
        if (!hasDetails || top.entryId != expectedEntryId || generation != expectedGeneration) return
        generation++
        entries[top.entryId]?.cancel()
        backStack.removeAt(backStack.lastIndex)
        if (!hasDetails && restorePlayer.value) {
            restorePlayer.value = false
            onRestorePlayer()
        }
    }

    fun clear() {
        captureGesture()
        generation++
        immediate = true
        restorePlayer.value = false
        entries.values.forEach { it.cancel() }
        entries.clear()
        playlistEntries.clear()
        folderEntries.clear()
        backHandlers.clear()
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun entry(route: MusicDetailRoute): MusicDetailEntryState {
        entries[route.entryId]?.let { return it }
        return MusicDetailEntryState(route, queries, scope).also { state ->
            if (backStack.any { (it as MusicDetailRoute).entryId == route.entryId }) {
                entries[route.entryId] = state
                state.retry()
            } else {
                // A cleared route can still be composed while its old host is being disposed.
                state.cancel()
            }
        }
    }
    fun release(key: Any) { entries.remove(key)?.cancel(); playlistEntries.remove(key); folderEntries.remove(key); backHandlers.remove(key) }
    fun releaseRemovedEntries() {
        val activeIds = backStack.map { (it as MusicDetailRoute).entryId }.toSet()
        (entries.keys + playlistEntries.keys + folderEntries.keys).filterNot(activeIds::contains).forEach { release(it) }
    }
    fun dispose() { entries.values.forEach { it.cancel() }; entries.clear(); playlistEntries.clear(); folderEntries.clear(); backHandlers.clear() }
}

internal val LocalMusicDetailNavigation = staticCompositionLocalOf<MusicDetailNavigation?> { null }
internal val LocalMusicDetailIsForeground = staticCompositionLocalOf { true }
private val LocalMusicDetailEntryId = staticCompositionLocalOf<String?> { null }

@Composable
internal fun MusicDetailBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val navigation = LocalMusicDetailNavigation.current
    val id = LocalMusicDetailEntryId.current
    val latestBack by rememberUpdatedState(onBack)
    DisposableEffect(navigation, id, enabled) {
        if (enabled && id != null) navigation?.setBackHandler(id) { latestBack() }
        onDispose { if (enabled && id != null) navigation?.setBackHandler(id, null) }
    }
}

private val PlayerPresentation.isMusicDetailForeground: Boolean
    get() = !isExpanded && !isQueueVisible && !isManualLyricsSearchVisible &&
        !isLyricsShareVisible && !isLyricsStyleVisible && !isCastSheetVisible

internal fun Modifier.musicDetailKeyboardBack(
    navigation: MusicDetailNavigation,
    presentation: PlayerPresentation,
): Modifier {
    val entryId = navigation.top.entryId
    val generation = navigation.generation
    val foreground = presentation.isMusicDetailForeground
    return onKeyEvent { event ->
        if (foreground && navigation.hasDetails && event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
            navigation.requestBack(entryId, generation)
            true
        } else false
    }
}

private val detailSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclass(MusicDetailRoute::class, MusicDetailRoute.serializer()) }
    }
}

@Composable
internal fun rememberMusicDetailNavigation(
    queries: MusicDetailQueries,
    onRestorePlayer: () -> Unit,
): MusicDetailNavigation {
    val stack = rememberNavBackStack(detailSavedStateConfiguration, MusicDetailRoute())
    val scope = rememberCoroutineScope()
    val restore = rememberSaveable { mutableStateOf(false) }
    val latestRestore by rememberUpdatedState(onRestorePlayer)
    val libraryRoot = rememberSaveable { mutableStateOf(
        if (stack.any { (it as MusicDetailRoute).kind == "folder" }) LibraryBrowserRootView.Folders else LibraryBrowserRootView.Tracks) }
    val libraryAlbums = rememberLibraryAlbumGridState()
    val navigation = remember(stack, queries, libraryAlbums) { MusicDetailNavigation(stack, queries, scope, restore, { latestRestore() },
        libraryAlbumState = libraryAlbums, libraryRootState = libraryRoot) }
    DisposableEffect(navigation) { onDispose { navigation.dispose() } }
    return navigation
}

@Composable
internal fun MusicDetailHost(
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
    showDuration: Boolean = true,
    playlist: @Composable (MusicDetailRoute) -> Unit = {},
    folder: @Composable (MusicDetailRoute) -> Unit = {},
    root: @Composable () -> Unit,
) {
    val navigation = LocalMusicDetailNavigation.current
    val currentPlaylist by rememberUpdatedState(playlist)
    val currentFolder by rememberUpdatedState(folder)
    if (navigation == null) { root(); return }
    DisposableEffect(navigation) {
        onDispose { navigation.releaseRemovedEntries() }
    }
    PlatformMusicDetailBackInput(navigation.generation)
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    SideEffect {
        navigation.gestureInProgress = { dispatcher?.transitionState?.value is NavigationEventTransitionState.InProgress }
    }
    LaunchedEffect(navigation, dispatcher) {
        dispatcher?.transitionState?.collectLatest { transition ->
            if (transition is NavigationEventTransitionState.InProgress) {
                if (navigation.gestureGeneration == null) navigation.gestureGeneration = navigation.generation
            } else {
                // Completion callbacks run before the next frame. Keep their original epoch until then.
                withFrameNanos { }
                navigation.gestureGeneration = null
            }
        }
    }
    // Shells can replace this host on layout or tab-group changes. A saveable decorator must
    // belong to one host; the root navigation owner retains the actual detail entry states.
    val saveable = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val lifetime = remember(navigation) {
        NavEntryDecorator<NavKey>(onPop = navigation::release) { entry -> entry.Content() }
    }
    val decorators = listOf(saveable, lifetime)

    val current = navigation.top
    val generation = navigation.generation
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val duration = if (navigation.immediate) 0 else 300
    val presentation = currentPlayerPresentation()
    val focus = remember { FocusRequester() }
    val foreground = presentation.isMusicDetailForeground
    LaunchedEffect(current.entryId, foreground) {
        if (navigation.hasDetails && foreground) focus.requestFocus()
    }
    NavDisplay(
        backStack = navigation.backStack,
        modifier = modifier.fillMaxSize().clipToBounds().focusRequester(focus)
            .musicDetailKeyboardBack(navigation, presentation).focusable(),
        entryDecorators = decorators,
        onBack = {
            if (foreground && (navigation.gestureGeneration == null || navigation.gestureGeneration == generation)) {
                navigation.requestBack(current.entryId, generation)
            }
        },
        transitionSpec = {
            slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it * direction } togetherWith
                slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it * direction }
        },
        popTransitionSpec = {
            slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it * direction } togetherWith
                slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it * direction }
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it * direction } togetherWith
                slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it * direction }
        },
        entryProvider = { key ->
            val route = key as MusicDetailRoute
            NavEntry(key, contentKey = route.entryId) {
                // NavEntry caches this content; observe current state inside its composition.
                val entryForeground = currentPlayerPresentation().isMusicDetailForeground &&
                    route.entryId == navigation.top.entryId
                CompositionLocalProvider(LocalMusicDetailIsForeground provides entryForeground,
                    LocalMusicDetailEntryId provides route.entryId) {
                    when {
                        route.isRoot -> root()
                        route.kind == "playlist" -> currentPlaylist(route)
                        route.kind == "folder" -> currentFolder(route)
                        route.kind == "album" -> AlbumDetailPage(route, navigation, onPlayerIntent, allowSplit = showDuration)
                        else -> ArtistDetailPage(route, navigation, onPlayerIntent)
                    }
                }
            }
        },
    )
}
