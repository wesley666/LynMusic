package top.iwesley.lyn.music.tv.ui

import kotlinx.coroutines.CoroutineScope
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore

internal enum class TvMainDestination {
    My,
    Library,
    Favorites,
}

internal enum class TvMediaBrowserMode {
    Tracks,
    Albums,
    Artists,
}

internal enum class TvSearchTarget {
    Library,
    Favorites,
}

internal data class TvMediaDetail(
    val mode: TvMediaBrowserMode,
    val id: String,
    val title: String,
    val subtitle: String? = null,
)

internal data class TvSearchDialogState(
    val target: TvSearchTarget,
    val text: String,
)

internal data class TvMainState(
    val selectedDestination: TvMainDestination = TvMainDestination.Library,
    val libraryMode: TvMediaBrowserMode = TvMediaBrowserMode.Tracks,
    val favoritesMode: TvMediaBrowserMode = TvMediaBrowserMode.Tracks,
    val libraryDetail: TvMediaDetail? = null,
    val favoritesDetail: TvMediaDetail? = null,
    val searchDialog: TvSearchDialogState? = null,
)

internal sealed interface TvMainIntent {
    data class ActivateDestination(val destination: TvMainDestination) : TvMainIntent
    data class SelectDestination(val destination: TvMainDestination) : TvMainIntent
    data class SelectLibraryMode(val mode: TvMediaBrowserMode) : TvMainIntent
    data class SelectFavoritesMode(val mode: TvMediaBrowserMode) : TvMainIntent
    data class OpenLibraryDetail(val detail: TvMediaDetail) : TvMainIntent
    data class OpenFavoritesDetail(val detail: TvMediaDetail) : TvMainIntent
    data class OpenSearch(val target: TvSearchTarget, val currentQuery: String) : TvMainIntent
    data class SearchTextChanged(val value: String) : TvMainIntent
    data object SubmitSearch : TvMainIntent
    data class ClearSearch(val target: TvSearchTarget) : TvMainIntent
    data object DismissSearch : TvMainIntent
    data class PlayTracks(val tracks: List<Track>, val startIndex: Int) : TvMainIntent
    data class ToggleFavorite(val track: Track) : TvMainIntent
    data object Back : TvMainIntent
}

internal sealed interface TvMainEffect {
    data object StartMy : TvMainEffect
    data object StartLibrary : TvMainEffect
    data object StartFavorites : TvMainEffect
    data class SearchLibrary(val query: String) : TvMainEffect
    data class SearchFavorites(val query: String) : TvMainEffect
    data class PlayTracks(val tracks: List<Track>, val startIndex: Int) : TvMainEffect
    data class ToggleFavorite(val track: Track) : TvMainEffect
}

internal class TvMainStore(
    scope: CoroutineScope,
) : BaseStore<TvMainState, TvMainIntent, TvMainEffect>(
    initialState = TvMainState(),
    scope = scope,
) {
    override suspend fun handleIntent(intent: TvMainIntent) {
        when (intent) {
            is TvMainIntent.ActivateDestination -> emitStartEffect(intent.destination)
            is TvMainIntent.SelectDestination -> {
                updateState { it.copy(selectedDestination = intent.destination, searchDialog = null) }
                emitStartEffect(intent.destination)
            }

            is TvMainIntent.SelectLibraryMode -> updateState {
                it.copy(libraryMode = intent.mode, libraryDetail = null)
            }

            is TvMainIntent.SelectFavoritesMode -> updateState {
                it.copy(favoritesMode = intent.mode, favoritesDetail = null)
            }

            is TvMainIntent.OpenLibraryDetail -> updateState {
                it.copy(libraryDetail = intent.detail)
            }

            is TvMainIntent.OpenFavoritesDetail -> updateState {
                it.copy(favoritesDetail = intent.detail)
            }

            is TvMainIntent.OpenSearch -> updateState {
                it.copy(searchDialog = TvSearchDialogState(intent.target, intent.currentQuery))
            }

            is TvMainIntent.SearchTextChanged -> updateState { state ->
                state.searchDialog?.let { dialog ->
                    state.copy(searchDialog = dialog.copy(text = intent.value))
                } ?: state
            }

            TvMainIntent.SubmitSearch -> submitSearch()
            is TvMainIntent.ClearSearch -> clearSearch(intent.target)
            TvMainIntent.DismissSearch -> updateState { it.copy(searchDialog = null) }
            is TvMainIntent.PlayTracks -> emitEffect(TvMainEffect.PlayTracks(intent.tracks, intent.startIndex))
            is TvMainIntent.ToggleFavorite -> emitEffect(TvMainEffect.ToggleFavorite(intent.track))
            TvMainIntent.Back -> handleBack()
        }
    }

    private suspend fun emitStartEffect(destination: TvMainDestination) {
        emitEffect(
            when (destination) {
                TvMainDestination.My -> TvMainEffect.StartMy
                TvMainDestination.Library -> TvMainEffect.StartLibrary
                TvMainDestination.Favorites -> TvMainEffect.StartFavorites
            },
        )
    }

    private suspend fun submitSearch() {
        val dialog = state.value.searchDialog ?: return
        val query = dialog.text
        updateState { it.copy(searchDialog = null) }
        when (dialog.target) {
            TvSearchTarget.Library -> emitEffect(TvMainEffect.SearchLibrary(query))
            TvSearchTarget.Favorites -> emitEffect(TvMainEffect.SearchFavorites(query))
        }
    }

    private suspend fun clearSearch(target: TvSearchTarget) {
        updateState { state ->
            if (state.searchDialog?.target == target) {
                state.copy(searchDialog = state.searchDialog.copy(text = ""))
            } else {
                state
            }
        }
        when (target) {
            TvSearchTarget.Library -> emitEffect(TvMainEffect.SearchLibrary(""))
            TvSearchTarget.Favorites -> emitEffect(TvMainEffect.SearchFavorites(""))
        }
    }

    private fun handleBack() {
        updateState { state ->
            when {
                state.searchDialog != null -> state.copy(searchDialog = null)
                state.selectedDestination == TvMainDestination.Library && state.libraryDetail != null ->
                    state.copy(libraryDetail = null)
                state.selectedDestination == TvMainDestination.Favorites && state.favoritesDetail != null ->
                    state.copy(favoritesDetail = null)
                else -> state
            }
        }
    }
}
