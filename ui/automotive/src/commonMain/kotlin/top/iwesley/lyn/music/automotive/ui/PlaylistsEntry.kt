package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** UI-only state owned above both shells. It never owns or starts a Store. */
internal class AutomotivePlaylistsEntry(val list: LazyListState = LazyListState()) {
    var query by mutableStateOf("")
    var searchActive by mutableStateOf(false)
    var searchGeneration by mutableLongStateOf(0L)
        private set
    var pendingCreation by mutableStateOf<PendingPlaylistCreation?>(null)

    fun changeQuery(value: String) {
        if (query == value) return
        query = value
        list.requestScrollToItem(0)
    }

    fun openSearch() {
        if (!searchActive) searchGeneration++
        searchActive = true
    }

    fun closeSearch() {
        searchGeneration++
        searchActive = false
        changeQuery("")
    }

    companion object {
        val Saver = listSaver<AutomotivePlaylistsEntry, Any>(
            save = { listOf(it.list.firstVisibleItemIndex, it.list.firstVisibleItemScrollOffset, it.query, it.searchActive) },
            restore = { values -> AutomotivePlaylistsEntry(LazyListState(values[0] as Int, values[1] as Int)).apply {
                query = values[2] as String
                searchActive = values[3] as Boolean
            } },
        )
    }
}

internal data class PendingPlaylistCreation(val sourceId: String?, val name: String, val previousIds: Set<String>)
internal val LocalAutomotivePlaylistsEntry = staticCompositionLocalOf<AutomotivePlaylistsEntry?> { null }

@Composable
internal fun rememberAutomotivePlaylistsEntry(): AutomotivePlaylistsEntry =
    rememberSaveable(saver = AutomotivePlaylistsEntry.Saver) { AutomotivePlaylistsEntry() }
