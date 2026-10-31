package top.iwesley.lyn.music.feature.favorites

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.plus

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.FavoriteTrackMetadata
import top.iwesley.lyn.music.data.repository.FavoritesRepository
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.NoopOfflineDownloadRepository
import top.iwesley.lyn.music.data.repository.OfflineDownloadRepository
import top.iwesley.lyn.music.data.repository.TrackPlaybackStat
import top.iwesley.lyn.music.data.repository.TrackPlaybackStatsRepository
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterOption
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore
import top.iwesley.lyn.music.feature.library.availableOrAll
import top.iwesley.lyn.music.feature.library.buildLibrarySourceFilterOptions
import top.iwesley.lyn.music.feature.library.TrackSortMode
import top.iwesley.lyn.music.feature.library.deriveVisibleAlbums
import top.iwesley.lyn.music.feature.library.deriveVisibleArtists
import top.iwesley.lyn.music.feature.library.isLocalIndexedEnabled
import top.iwesley.lyn.music.feature.library.matchesLibrarySourceFilter
import top.iwesley.lyn.music.feature.library.sortTracks

data class FavoritesState(
    val query: String = "",
    val isLoadingContent: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val filteredTracks: List<Track> = emptyList(),
    val filteredAlbums: List<Album> = emptyList(),
    val filteredArtists: List<Artist> = emptyList(),
    val favoriteTrackIds: Set<String> = emptySet(),
    val favoriteTrackMetadata: Map<String, FavoriteTrackMetadata> = emptyMap(),
    val selectedSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    val sourceFilterOptions: List<LibrarySourceFilterOption> = buildLibrarySourceFilterOptions(),
    val selectedTrackSortMode: TrackSortMode = TrackSortMode.ADDED_AT,
    val trackPlaybackStats: Map<String, TrackPlaybackStat> = emptyMap(),
    val offlineDownloadsByTrackId: Map<String, OfflineDownload> = emptyMap(),
    val visibleAlbumCount: Int = 0,
    val visibleArtistCount: Int = 0,
    val sourceTypesById: Map<String, ImportSourceType> = emptyMap(),
    val canRefreshRemote: Boolean = false,
    val isRefreshing: Boolean = false,
    val message: UiText? = null,
) {
    val availableSourceFilters: List<LibrarySourceFilter> get() = sourceFilterOptions.map { it.filter }
}

sealed interface FavoritesIntent {
    data class SearchChanged(val query: String) : FavoritesIntent
    data class SourceFilterChanged(val filter: LibrarySourceFilter) : FavoritesIntent
    data class TrackSortChanged(val mode: TrackSortMode) : FavoritesIntent
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
    private val trackPlaybackStatsRepository: TrackPlaybackStatsRepository = EmptyTrackPlaybackStatsRepository,
    private val offlineDownloadRepository: OfflineDownloadRepository = NoopOfflineDownloadRepository,
    startImmediately: Boolean = true,
) : BaseStore<FavoritesState, FavoritesIntent, FavoritesEffect>(
    initialState = FavoritesState(),
    scope = storeScope,
) {
    private var contentStarted = false
    private var hasTriggeredInitialRemoteRefresh = false

    init {
        storeScope.launch {
            favoritesRepository.favoriteTrackIds.collect { favoriteTrackIds ->
                updateState { it.copy(favoriteTrackIds = favoriteTrackIds) }
            }
        }
        if (startImmediately) {
            ensureContentStarted()
        }
    }

    fun ensureContentStarted() {
        if (contentStarted) return
        contentStarted = true
        storeScope.launch {
            val preferencesFlow = combine(
                preferencesStore.favoritesSourceFilter,
                preferencesStore.favoritesTrackSortMode,
            ) { selectedSourceFilter, selectedTrackSortMode ->
                selectedSourceFilter to selectedTrackSortMode
            }
            val contentFlow = combine(
                favoritesRepository.favoriteTracks,
                favoritesRepository.favoriteTrackMetadata,
                importSourceRepository.observeSources(),
                offlineDownloadRepository.downloads,
            ) { tracks, favoriteTrackMetadata, sources, offlineDownloads ->
                val enabledSources = sources.map { it.source }.filter { it.isLocalIndexedEnabled() }
                FavoritesContentSnapshot(
                    tracks = tracks,
                    favoriteTrackMetadata = favoriteTrackMetadata,
                    offlineDownloadsByTrackId = offlineDownloads,
                    sourceTypesById = enabledSources.associate { it.id to it.type },
                    sourceFilterOptions = buildLibrarySourceFilterOptions(enabledSources),
                    navidromeSourceIds = enabledSources
                        .filter {
                            it.type == ImportSourceType.NAVIDROME ||
                                it.type == ImportSourceType.SUBSONIC ||
                                it.type == ImportSourceType.EMBY ||
                                it.type == ImportSourceType.FN_MUSIC
                        }
                        .mapTo(linkedSetOf()) { it.id },
                )
            }
            combine(
                contentFlow,
                preferencesFlow,
                trackPlaybackStatsRepository.trackStats,
            ) { content, preferences, trackPlaybackStats ->
                FavoritesSnapshot(
                    tracks = content.tracks,
                    favoriteTrackMetadata = content.favoriteTrackMetadata,
                    selectedSourceFilter = preferences.first,
                    selectedTrackSortMode = preferences.second,
                    trackPlaybackStats = trackPlaybackStats,
                    offlineDownloadsByTrackId = content.offlineDownloadsByTrackId,
                    sourceTypesById = content.sourceTypesById,
                    sourceFilterOptions = content.sourceFilterOptions,
                    navidromeSourceIds = content.navidromeSourceIds,
                )
            }.collect { snapshot ->
                refreshNavidromeFavoritesOnFirstActivation(snapshot.navidromeSourceIds)
                val selectedSourceFilter = snapshot.selectedSourceFilter.availableOrAll(snapshot.sourceFilterOptions, snapshot.sourceTypesById)
                updateState { state ->
                    deriveState(
                        state.copy(
                            isLoadingContent = false,
                            tracks = snapshot.tracks,
                            favoriteTrackMetadata = snapshot.favoriteTrackMetadata,
                            selectedSourceFilter = selectedSourceFilter,
                            selectedTrackSortMode = snapshot.selectedTrackSortMode,
                            trackPlaybackStats = snapshot.trackPlaybackStats,
                            offlineDownloadsByTrackId = snapshot.offlineDownloadsByTrackId,
                            sourceTypesById = snapshot.sourceTypesById,
                            sourceFilterOptions = snapshot.sourceFilterOptions,
                            canRefreshRemote = snapshot.navidromeSourceIds.isNotEmpty(),
                        ),
                    )
                }
                if (selectedSourceFilter != snapshot.selectedSourceFilter) {
                    try {
                        preferencesStore.compareAndSetFavoritesSourceFilter(
                            expected = snapshot.selectedSourceFilter,
                            filter = selectedSourceFilter,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // A failed settings write must not stop content updates.
                    }
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

            is FavoritesIntent.TrackSortChanged -> preferencesStore.setFavoritesTrackSortMode(intent.mode)

            is FavoritesIntent.ToggleFavorite -> {
                favoritesRepository.toggleFavorite(intent.track)
                    .onSuccess {
                        updateState { it.copy(message = null) }
                    }
                    .onFailure { throwable ->
                        setMessage(uiText(Res.string.favorites_update_failed, throwable.uiErrorDetail()))
                    }
            }

            is FavoritesIntent.EnsureFavorite -> {
                favoritesRepository.setFavorite(intent.track, favorite = true)
                    .onSuccess {
                        updateState { it.copy(message = null) }
                    }
                    .onFailure { throwable ->
                        setMessage(uiText(Res.string.favorites_update_failed, throwable.uiErrorDetail()))
                    }
            }
        }
    }

    private fun refreshNavidromeFavoritesOnFirstActivation(navidromeSourceIds: Set<String>) {
        if (hasTriggeredInitialRemoteRefresh || navidromeSourceIds.isEmpty()) return
        hasTriggeredInitialRemoteRefresh = true
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
                            uiText(Res.string.favorites_refresh_failed, throwable.uiErrorDetail())
                        } else {
                            uiText(Res.string.favorites_remote_sync_failed, throwable.uiErrorDetail())
                        },
                    )
                }
            }
    }

    private fun deriveState(state: FavoritesState): FavoritesState {
        val selectedSourceFilter = state.selectedSourceFilter.availableOrAll(state.sourceFilterOptions, state.sourceTypesById)
        val filteredTracks = sortTracks(
            tracks = filterTracks(
                tracks = state.tracks,
                query = state.query,
                selectedSourceFilter = selectedSourceFilter,
                sourceTypesById = state.sourceTypesById,
                offlineDownloadsByTrackId = state.offlineDownloadsByTrackId,
            ),
            sortMode = state.selectedTrackSortMode,
            trackPlaybackStats = state.trackPlaybackStats,
            addedAtByTrackId = state.favoriteTrackMetadata.mapValues { it.value.favoritedAt },
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
        offlineDownloadsByTrackId: Map<String, OfflineDownload>,
    ): List<Track> {
        val normalized = query.trim().lowercase()
        return tracks.filter { track ->
            matchesLibrarySourceFilter(track, selectedSourceFilter, sourceTypesById, offlineDownloadsByTrackId) &&
                (
                    normalized.isBlank() ||
                        track.title.lowercase().contains(normalized) ||
                        track.artistName.orEmpty().lowercase().contains(normalized) ||
                        track.albumTitle.orEmpty().lowercase().contains(normalized)
                    )
        }
    }

    private fun setMessage(message: UiText) {
        updateState { it.copy(message = message) }
    }

    private data class FavoritesContentSnapshot(
        val tracks: List<Track>,
        val favoriteTrackMetadata: Map<String, FavoriteTrackMetadata>,
        val offlineDownloadsByTrackId: Map<String, OfflineDownload>,
        val sourceTypesById: Map<String, ImportSourceType>,
        val sourceFilterOptions: List<LibrarySourceFilterOption>,
        val navidromeSourceIds: Set<String>,
    )

    private data class FavoritesSnapshot(
        val tracks: List<Track>,
        val favoriteTrackMetadata: Map<String, FavoriteTrackMetadata>,
        val selectedSourceFilter: LibrarySourceFilter,
        val selectedTrackSortMode: TrackSortMode,
        val trackPlaybackStats: Map<String, TrackPlaybackStat>,
        val offlineDownloadsByTrackId: Map<String, OfflineDownload>,
        val sourceTypesById: Map<String, ImportSourceType>,
        val sourceFilterOptions: List<LibrarySourceFilterOption>,
        val navidromeSourceIds: Set<String>,
    )
}

private object EmptyTrackPlaybackStatsRepository : TrackPlaybackStatsRepository {
    override val trackStats = kotlinx.coroutines.flow.MutableStateFlow<Map<String, TrackPlaybackStat>>(emptyMap())
}
