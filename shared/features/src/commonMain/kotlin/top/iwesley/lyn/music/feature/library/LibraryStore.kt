package top.iwesley.lyn.music.feature.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.LibraryRepository
import top.iwesley.lyn.music.data.repository.TrackPlaybackStat
import top.iwesley.lyn.music.data.repository.TrackPlaybackStatsRepository

enum class LibrarySourceFilter {
    ALL,
    LOCAL_FOLDER,
    SAMBA,
    WEBDAV,
    NAVIDROME,
}

enum class TrackSortMode {
    TITLE,
    ARTIST,
    ALBUM,
    PLAY_COUNT,
    ADDED_AT,
}

data class LibraryState(
    val query: String = "",
    val isLoadingContent: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val filteredTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val filteredAlbums: List<Album> = emptyList(),
    val filteredArtists: List<Artist> = emptyList(),
    val selectedSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    val availableSourceFilters: List<LibrarySourceFilter> = listOf(LibrarySourceFilter.ALL),
    val selectedTrackSortMode: TrackSortMode = TrackSortMode.TITLE,
    val trackPlaybackStats: Map<String, TrackPlaybackStat> = emptyMap(),
    val visibleAlbumCount: Int = 0,
    val visibleArtistCount: Int = 0,
    val sourceTypesById: Map<String, ImportSourceType> = emptyMap(),
)

sealed interface LibraryIntent {
    data class SearchChanged(val query: String) : LibraryIntent
    data class SourceFilterChanged(val filter: LibrarySourceFilter) : LibraryIntent
    data class TrackSortChanged(val mode: TrackSortMode) : LibraryIntent
}

sealed interface LibraryEffect

class LibraryStore(
    private val repository: LibraryRepository,
    private val importSourceRepository: ImportSourceRepository,
    private val preferencesStore: LibrarySourceFilterPreferencesStore,
    private val storeScope: CoroutineScope,
    private val trackPlaybackStatsRepository: TrackPlaybackStatsRepository = EmptyTrackPlaybackStatsRepository,
    startImmediately: Boolean = true,
) : BaseStore<LibraryState, LibraryIntent, LibraryEffect>(
    initialState = LibraryState(),
    scope = storeScope,
) {
    private var hasStarted = false

    init {
        if (startImmediately) {
            ensureStarted()
        }
    }

    fun ensureStarted() {
        if (hasStarted) return
        hasStarted = true
        storeScope.launch {
            val contentFlow = combine(
                repository.tracks,
                repository.albums,
                repository.artists,
                importSourceRepository.observeSources(),
            ) { tracks, albums, artists, sources ->
                LibraryContentSnapshot(
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    sources = sources,
                )
            }
            val preferencesFlow = combine(
                preferencesStore.librarySourceFilter,
                preferencesStore.libraryTrackSortMode,
            ) { selectedSourceFilter, selectedTrackSortMode ->
                selectedSourceFilter to selectedTrackSortMode
            }
            combine(
                contentFlow,
                preferencesFlow,
                trackPlaybackStatsRepository.trackStats,
            ) { content, preferences, trackPlaybackStats ->
                val enabledSources = content.sources.map { it.source }.filter { it.enabled }
                LibrarySnapshot(
                    tracks = content.tracks,
                    albums = content.albums,
                    artists = content.artists,
                    selectedSourceFilter = preferences.first,
                    selectedTrackSortMode = preferences.second,
                    trackPlaybackStats = trackPlaybackStats,
                    sourceTypesById = enabledSources.associate { it.id to it.type },
                    availableSourceFilters = buildAvailableSourceFilters(enabledSources.map { it.type }),
                )
            }.collect { snapshot ->
                updateState { state ->
                    deriveState(
                        state.copy(
                            isLoadingContent = false,
                            tracks = snapshot.tracks,
                            albums = snapshot.albums,
                            artists = snapshot.artists,
                            selectedSourceFilter = snapshot.selectedSourceFilter,
                            selectedTrackSortMode = snapshot.selectedTrackSortMode,
                            trackPlaybackStats = snapshot.trackPlaybackStats,
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

            is LibraryIntent.SourceFilterChanged -> preferencesStore.setLibrarySourceFilter(intent.filter)

            is LibraryIntent.TrackSortChanged -> preferencesStore.setLibraryTrackSortMode(intent.mode)
        }
    }

    private fun deriveState(state: LibraryState): LibraryState {
        val selectedSourceFilter = state.selectedSourceFilter
            .takeIf { it == LibrarySourceFilter.ALL || it in state.availableSourceFilters }
            ?: LibrarySourceFilter.ALL
        val filteredTracks = sortTracks(
            tracks = filterTracks(
                tracks = state.tracks,
                query = state.query,
                selectedSourceFilter = selectedSourceFilter,
                sourceTypesById = state.sourceTypesById,
            ),
            sortMode = state.selectedTrackSortMode,
            trackPlaybackStats = state.trackPlaybackStats,
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
        val selectedSourceFilter: LibrarySourceFilter,
        val selectedTrackSortMode: TrackSortMode,
        val trackPlaybackStats: Map<String, TrackPlaybackStat>,
        val sourceTypesById: Map<String, ImportSourceType>,
        val availableSourceFilters: List<LibrarySourceFilter>,
    )

    private data class LibraryContentSnapshot(
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val sources: List<top.iwesley.lyn.music.core.model.SourceWithStatus>,
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

internal fun sortTracks(
    tracks: List<Track>,
    sortMode: TrackSortMode,
    trackPlaybackStats: Map<String, TrackPlaybackStat>,
    addedAtByTrackId: Map<String, Long> = emptyMap(),
): List<Track> {
    fun textKey(value: String?): String = value?.trim().orEmpty().lowercase()
    fun missingText(value: String?): Boolean = value.isNullOrBlank()
    fun addedAt(track: Track): Long = addedAtByTrackId[track.id] ?: track.addedAt
    val comparator = when (sortMode) {
        TrackSortMode.TITLE -> compareBy<Track> { textKey(it.title) }
            .thenBy { it.id }

        TrackSortMode.ARTIST -> compareBy<Track> { missingText(it.artistName) }
            .thenBy { textKey(it.artistName) }
            .thenBy { textKey(it.title) }
            .thenBy { it.id }

        TrackSortMode.ALBUM -> compareBy<Track> { missingText(it.albumTitle) }
            .thenBy { textKey(it.albumTitle) }
            .thenBy { it.discNumber ?: Int.MAX_VALUE }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy { textKey(it.title) }
            .thenBy { it.id }

        TrackSortMode.PLAY_COUNT -> compareByDescending<Track> { trackPlaybackStats[it.id]?.playCount ?: 0 }
            .thenBy { textKey(it.title) }
            .thenBy { it.id }

        TrackSortMode.ADDED_AT -> compareByDescending<Track> { addedAt(it) }
            .thenBy { textKey(it.title) }
            .thenBy { it.id }
    }
    return tracks.sortedWith(comparator)
}

private object EmptyTrackPlaybackStatsRepository : TrackPlaybackStatsRepository {
    override val trackStats = MutableStateFlow<Map<String, TrackPlaybackStat>>(emptyMap())
}
