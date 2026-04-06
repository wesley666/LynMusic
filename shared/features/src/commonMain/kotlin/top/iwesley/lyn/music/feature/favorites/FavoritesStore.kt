package top.iwesley.lyn.music.feature.favorites

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.FavoritesRepository
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.feature.library.deriveVisibleAlbums
import top.iwesley.lyn.music.feature.library.deriveVisibleArtists
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore

data class FavoritesState(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val filteredTracks: List<Track> = emptyList(),
    val filteredAlbums: List<Album> = emptyList(),
    val filteredArtists: List<Artist> = emptyList(),
    val favoriteTrackIds: Set<String> = emptySet(),
    val selectedSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    val availableSourceFilters: List<LibrarySourceFilter> = listOf(LibrarySourceFilter.ALL),
    val visibleAlbumCount: Int = 0,
    val visibleArtistCount: Int = 0,
    val sourceTypesById: Map<String, ImportSourceType> = emptyMap(),
    val canRefreshRemote: Boolean = false,
    val isRefreshing: Boolean = false,
    val message: String? = null,
)

sealed interface FavoritesIntent {
    data class SearchChanged(val query: String) : FavoritesIntent
    data class SourceFilterChanged(val filter: LibrarySourceFilter) : FavoritesIntent
    data class ToggleFavorite(val track: Track) : FavoritesIntent
    data class EnsureFavorite(val track: Track) : FavoritesIntent
    data object Refresh : FavoritesIntent
    data object ClearMessage : FavoritesIntent
}

sealed interface FavoritesEffect

class FavoritesStore(
    private val favoritesRepository: FavoritesRepository,
    private val importSourceRepository: ImportSourceRepository,
    private val preferencesStore: LibrarySourceFilterPreferencesStore,
    private val storeScope: CoroutineScope,
) : BaseStore<FavoritesState, FavoritesIntent, FavoritesEffect>(
    initialState = FavoritesState(),
    scope = storeScope,
) {
    private var lastNavidromeSourceIds: Set<String>? = null

    init {
        storeScope.launch {
            combine(
                favoritesRepository.favoriteTracks,
                favoritesRepository.favoriteTrackIds,
                importSourceRepository.observeSources(),
                preferencesStore.favoritesSourceFilter,
            ) { tracks, favoriteTrackIds, sources, selectedSourceFilter ->
                FavoritesSnapshot(
                    tracks = tracks,
                    favoriteTrackIds = favoriteTrackIds,
                    selectedSourceFilter = selectedSourceFilter,
                    sourceTypesById = sources.associate { it.source.id to it.source.type },
                    availableSourceFilters = buildAvailableSourceFilters(sources),
                    navidromeSourceIds = sources
                        .map { it.source }
                        .filter { it.type == ImportSourceType.NAVIDROME }
                        .mapTo(linkedSetOf()) { it.id },
                )
            }.collect { snapshot ->
                refreshNavidromeFavoritesIfNeeded(snapshot.navidromeSourceIds)
                updateState { state ->
                    deriveState(
                        state.copy(
                            tracks = snapshot.tracks,
                            favoriteTrackIds = snapshot.favoriteTrackIds,
                            selectedSourceFilter = snapshot.selectedSourceFilter,
                            sourceTypesById = snapshot.sourceTypesById,
                            availableSourceFilters = snapshot.availableSourceFilters,
                            canRefreshRemote = snapshot.navidromeSourceIds.isNotEmpty(),
                        ),
                    )
                }
            }
        }
    }

    override suspend fun handleIntent(intent: FavoritesIntent) {
        when (intent) {
            FavoritesIntent.ClearMessage -> updateState { it.copy(message = null) }
            FavoritesIntent.Refresh -> refreshNavidromeFavorites(manual = true)

            is FavoritesIntent.SearchChanged -> updateState { state ->
                deriveState(state.copy(query = intent.query))
            }

            is FavoritesIntent.SourceFilterChanged -> preferencesStore.setFavoritesSourceFilter(intent.filter)

            is FavoritesIntent.ToggleFavorite -> {
                favoritesRepository.toggleFavorite(intent.track)
                    .onSuccess {
                        updateState { it.copy(message = null) }
                    }
                    .onFailure { throwable ->
                        setMessage("更新喜欢状态失败: ${throwable.message.orEmpty()}")
                    }
            }

            is FavoritesIntent.EnsureFavorite -> {
                favoritesRepository.setFavorite(intent.track, favorite = true)
                    .onSuccess {
                        updateState { it.copy(message = null) }
                    }
                    .onFailure { throwable ->
                        setMessage("更新喜欢状态失败: ${throwable.message.orEmpty()}")
                    }
            }
        }
    }

    private fun refreshNavidromeFavoritesIfNeeded(navidromeSourceIds: Set<String>) {
        if (lastNavidromeSourceIds == navidromeSourceIds) return
        lastNavidromeSourceIds = navidromeSourceIds
        if (navidromeSourceIds.isEmpty()) return
        storeScope.launch {
            refreshNavidromeFavorites(canRefreshRemote = true, manual = false)
        }
    }

    private suspend fun refreshNavidromeFavorites(
        canRefreshRemote: Boolean = state.value.canRefreshRemote,
        manual: Boolean,
    ) {
        if (state.value.isRefreshing || !canRefreshRemote) return
        updateState { it.copy(isRefreshing = true, message = null) }
        favoritesRepository.refreshNavidromeFavorites()
            .onSuccess {
                updateState { it.copy(isRefreshing = false, message = null) }
            }
            .onFailure { throwable ->
                updateState {
                    it.copy(
                        isRefreshing = false,
                        message = if (manual) {
                            "刷新喜欢失败: ${throwable.message.orEmpty()}"
                        } else {
                            "同步 Navidrome 喜欢失败: ${throwable.message.orEmpty()}"
                        },
                    )
                }
            }
    }

    private fun deriveState(state: FavoritesState): FavoritesState {
        val selectedSourceFilter = state.selectedSourceFilter
            .takeIf { it == LibrarySourceFilter.ALL || it in state.availableSourceFilters }
            ?: LibrarySourceFilter.ALL
        val filteredTracks = filterTracks(
            tracks = state.tracks,
            query = state.query,
            selectedSourceFilter = selectedSourceFilter,
            sourceTypesById = state.sourceTypesById,
        )
        val filteredAlbums = deriveVisibleAlbums(filteredTracks)
        val filteredArtists = deriveVisibleArtists(filteredTracks)
        return state.copy(
            selectedSourceFilter = selectedSourceFilter,
            filteredTracks = filteredTracks,
            filteredAlbums = filteredAlbums,
            filteredArtists = filteredArtists,
            visibleAlbumCount = filteredAlbums.size,
            visibleArtistCount = filteredArtists.size,
        )
    }

    private fun filterTracks(
        tracks: List<Track>,
        query: String,
        selectedSourceFilter: LibrarySourceFilter,
        sourceTypesById: Map<String, ImportSourceType>,
    ): List<Track> {
        val normalized = query.trim().lowercase()
        return tracks.filter { track ->
            matchesSourceFilter(track, selectedSourceFilter, sourceTypesById) &&
                (
                    normalized.isBlank() ||
                        track.title.lowercase().contains(normalized) ||
                        track.artistName.orEmpty().lowercase().contains(normalized) ||
                        track.albumTitle.orEmpty().lowercase().contains(normalized)
                    )
        }
    }

    private fun matchesSourceFilter(
        track: Track,
        selectedSourceFilter: LibrarySourceFilter,
        sourceTypesById: Map<String, ImportSourceType>,
    ): Boolean {
        if (selectedSourceFilter == LibrarySourceFilter.ALL) return true
        return sourceTypesById[track.sourceId]?.toLibrarySourceFilter() == selectedSourceFilter
    }

    private fun buildAvailableSourceFilters(sources: List<SourceWithStatus>): List<LibrarySourceFilter> {
        val presentFilters = sources.map { it.source.type.toLibrarySourceFilter() }.toSet()
        return buildList {
            add(LibrarySourceFilter.ALL)
            FILTER_ORDER.filter { it in presentFilters }.forEach(::add)
        }
    }

    private fun ImportSourceType.toLibrarySourceFilter(): LibrarySourceFilter {
        return when (this) {
            ImportSourceType.LOCAL_FOLDER -> LibrarySourceFilter.LOCAL_FOLDER
            ImportSourceType.SAMBA -> LibrarySourceFilter.SAMBA
            ImportSourceType.WEBDAV -> LibrarySourceFilter.WEBDAV
            ImportSourceType.NAVIDROME -> LibrarySourceFilter.NAVIDROME
        }
    }

    private fun setMessage(message: String) {
        updateState { it.copy(message = message) }
    }

    private data class FavoritesSnapshot(
        val tracks: List<Track>,
        val favoriteTrackIds: Set<String>,
        val selectedSourceFilter: LibrarySourceFilter,
        val sourceTypesById: Map<String, ImportSourceType>,
        val availableSourceFilters: List<LibrarySourceFilter>,
        val navidromeSourceIds: Set<String>,
    )

    private companion object {
        val FILTER_ORDER = listOf(
            LibrarySourceFilter.LOCAL_FOLDER,
            LibrarySourceFilter.SAMBA,
            LibrarySourceFilter.WEBDAV,
            LibrarySourceFilter.NAVIDROME,
        )
    }
}
