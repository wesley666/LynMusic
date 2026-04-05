package top.iwesley.lyn.music.feature.library

import kotlinx.coroutines.flow.StateFlow

interface LibrarySourceFilterPreferencesStore {
    val librarySourceFilter: StateFlow<LibrarySourceFilter>
    val favoritesSourceFilter: StateFlow<LibrarySourceFilter>

    suspend fun setLibrarySourceFilter(filter: LibrarySourceFilter)

    suspend fun setFavoritesSourceFilter(filter: LibrarySourceFilter)
}
