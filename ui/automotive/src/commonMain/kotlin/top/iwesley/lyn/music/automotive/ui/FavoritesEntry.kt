package top.iwesley.lyn.music.automotive.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.online.OnlineFavoritesIntent
import top.iwesley.lyn.music.feature.online.OnlineFavoritesState
import top.iwesley.lyn.music.feature.player.PlayerIntent

internal data class AutomotiveFavoritesPage(
    val state: FavoritesState,
    val importState: ImportState,
    val onlineState: OnlineFavoritesState,
    val onFavoritesIntent: (FavoritesIntent) -> Unit,
    val onOnlineIntent: (OnlineFavoritesIntent) -> Unit,
    val onPlayerIntent: (PlayerIntent) -> Unit,
    val onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)?,
    val modifier: Modifier,
    val showSearchField: Boolean = true,
    val showRefreshActionButton: Boolean = true,
    val showInlineBatchOperationButton: Boolean = true,
)

/** Both shells render from one application-owned page state. */
internal class AutomotiveFavoritesEntry(
    val content: @Composable (AutomotiveFavoritesPage) -> Unit,
    val batchSelectionRequest: MutableIntState,
    val pageState: FavoritesPageState,
) {
    // Only explicit UI intents reset the page; Store hydration never enters here.
    fun prepareForIntent(intent: FavoritesIntent, state: FavoritesState) {
        val changed = when (intent) {
            is FavoritesIntent.SearchChanged -> intent.query != state.query
            is FavoritesIntent.SourceFilterChanged -> intent.filter != state.selectedSourceFilter
            is FavoritesIntent.TrackSortChanged -> intent.mode != state.selectedTrackSortMode
            else -> false
        }
        if (changed) pageState.resetForQueryChange()
    }

    fun prepareForIntent(
        intent: OnlineFavoritesIntent,
        state: OnlineFavoritesState,
        rememberedSourceId: String? = state.sourceId,
    ) {
        val changed = when (intent) {
            is OnlineFavoritesIntent.SearchChanged -> intent.query != state.query
            is OnlineFavoritesIntent.SelectSource -> {
                val sourceId = intent.sourceId?.trim()?.takeIf { it.isNotEmpty() }
                sourceId != (state.sourceId ?: rememberedSourceId) || state.query.isNotEmpty()
            }
            else -> false
        }
        if (changed) pageState.resetForQueryChange()
    }
}

/** Durable UI state shared by both shells; transient dialogs stay local to their content. */
internal class FavoritesPageState(val tracks: LazyListState = LazyListState()) {
    val selectionMode = mutableStateOf(false)
    val selectedTrackIds = mutableStateOf(emptyList<String>())
    val handledBatchRequest = mutableStateOf(0)
    val headerLayout = mutableStateOf<List<String>?>(null)

    fun resetForQueryChange() {
        tracks.requestScrollToItem(0)
        selectionMode.value = false
        selectedTrackIds.value = emptyList()
    }

    companion object {
        val Saver = listSaver<FavoritesPageState, Any>(
            save = { listOf(it.tracks.firstVisibleItemIndex, it.tracks.firstVisibleItemScrollOffset,
                it.selectionMode.value, it.selectedTrackIds.value, it.handledBatchRequest.value,
                it.headerLayout.value.orEmpty()) },
            restore = { values ->
                @Suppress("UNCHECKED_CAST")
                FavoritesPageState(LazyListState(values[0] as Int, values[1] as Int)).apply {
                    selectionMode.value = values[2] as Boolean
                    selectedTrackIds.value = values[3] as List<String>
                    handledBatchRequest.value = values[4] as Int
                    headerLayout.value = (values.last() as List<String>).takeIf { it.isNotEmpty() }
                }
            },
        )
    }
}

internal val LocalFavoritesPageState = staticCompositionLocalOf<FavoritesPageState?> { null }

internal val LocalAutomotiveFavoritesEntry = staticCompositionLocalOf<AutomotiveFavoritesEntry?> { null }

@Composable
internal fun rememberAutomotiveFavoritesEntry(): AutomotiveFavoritesEntry {
    val batchRequest = rememberSaveable { mutableIntStateOf(0) }
    val pageState = rememberSaveable(saver = FavoritesPageState.Saver) { FavoritesPageState() }
    return remember(batchRequest, pageState) {
        AutomotiveFavoritesEntry(
            content = { page ->
                // Keep layout nodes local to each NavDisplay/Pager subcomposition.
                CompositionLocalProvider(LocalFavoritesPageState provides pageState) {
                    FavoritesTab(
                        state = page.state, importState = page.importState, onlineState = page.onlineState,
                        onFavoritesIntent = page.onFavoritesIntent, onOnlineIntent = page.onOnlineIntent,
                        onPlayerIntent = page.onPlayerIntent, showSearchField = page.showSearchField,
                        showRefreshActionButton = page.showRefreshActionButton,
                        showInlineBatchOperationButton = page.showInlineBatchOperationButton,
                        batchSelectionRequestKey = batchRequest.intValue,
                        onOpenLibraryNavigationTarget = page.onOpenLibraryNavigationTarget, modifier = page.modifier,
                    )
                }
            },
            batchSelectionRequest = batchRequest,
            pageState = pageState,
        )
    }
}
