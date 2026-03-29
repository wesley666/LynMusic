package top.iwesley.lyn.music.feature.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LibraryRepository

data class LibraryState(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val filteredTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
)

sealed interface LibraryIntent {
    data class SearchChanged(val query: String) : LibraryIntent
}

sealed interface LibraryEffect

class LibraryStore(
    private val repository: LibraryRepository,
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
            ) { tracks, albums, artists ->
                Triple(tracks, albums, artists)
            }.collect { (tracks, albums, artists) ->
                updateState { state ->
                    val nextState = state.copy(
                        tracks = tracks,
                        albums = albums,
                        artists = artists,
                    )
                    nextState.copy(filteredTracks = filterTracks(nextState.tracks, nextState.query))
                }
            }
        }
    }

    override suspend fun handleIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.SearchChanged -> updateState { state ->
                state.copy(
                    query = intent.query,
                    filteredTracks = filterTracks(state.tracks, intent.query),
                )
            }
        }
    }

    private fun filterTracks(tracks: List<Track>, query: String): List<Track> {
        if (query.isBlank()) return tracks
        val normalized = query.trim().lowercase()
        return tracks.filter { track ->
            track.title.lowercase().contains(normalized) ||
                track.artistName.orEmpty().lowercase().contains(normalized) ||
                track.albumTitle.orEmpty().lowercase().contains(normalized)
        }
    }
}
