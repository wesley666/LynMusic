package top.iwesley.lyn.music.feature.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.LibraryRepository

enum class LibrarySourceFilter {
    ALL,
    LOCAL_FOLDER,
    SAMBA,
    WEBDAV,
    NAVIDROME,
}

data class LibraryState(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val filteredTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val filteredAlbums: List<Album> = emptyList(),
    val filteredArtists: List<Artist> = emptyList(),
    val selectedSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    val availableSourceFilters: List<LibrarySourceFilter> = listOf(LibrarySourceFilter.ALL),
    val visibleAlbumCount: Int = 0,
    val visibleArtistCount: Int = 0,
    val sourceTypesById: Map<String, ImportSourceType> = emptyMap(),
)

sealed interface LibraryIntent {
    data class SearchChanged(val query: String) : LibraryIntent
    data class SourceFilterChanged(val filter: LibrarySourceFilter) : LibraryIntent
}

sealed interface LibraryEffect

class LibraryStore(
    private val repository: LibraryRepository,
    private val importSourceRepository: ImportSourceRepository,
    scope: CoroutineScope,
) : BaseStore<LibraryState, LibraryIntent, LibraryEffect>(
    initialState = LibraryState(),
    scope = scope,
) {
    init {
        scope.launch {
            combine(
                repository.tracks,
                repository.albums,
                repository.artists,
                importSourceRepository.observeSources(),
            ) { tracks, albums, artists, sources ->
                LibrarySnapshot(
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    sourceTypesById = sources.associate { it.source.id to it.source.type },
                    availableSourceFilters = buildAvailableSourceFilters(sources.map { it.source.type }),
                )
            }.collect { snapshot ->
                updateState { state ->
                    deriveState(
                        state.copy(
                            tracks = snapshot.tracks,
                            albums = snapshot.albums,
                            artists = snapshot.artists,
                            sourceTypesById = snapshot.sourceTypesById,
                            availableSourceFilters = snapshot.availableSourceFilters,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun handleIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.SearchChanged -> updateState { state ->
                deriveState(state.copy(query = intent.query))
            }

            is LibraryIntent.SourceFilterChanged -> updateState { state ->
                deriveState(state.copy(selectedSourceFilter = intent.filter))
            }
        }
    }

    private fun deriveState(state: LibraryState): LibraryState {
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

    private fun buildAvailableSourceFilters(types: List<ImportSourceType>): List<LibrarySourceFilter> {
        val presentFilters = types.map { it.toLibrarySourceFilter() }.toSet()
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

    private data class LibrarySnapshot(
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val sourceTypesById: Map<String, ImportSourceType>,
        val availableSourceFilters: List<LibrarySourceFilter>,
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
