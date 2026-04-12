package top.iwesley.lyn.music.feature.playlists

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.PlaylistRepository
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter

data class PlaylistsState(
    val isLoadingContent: Boolean = true,
    val playlists: List<PlaylistSummary> = emptyList(),
    val selectedPlaylistId: String? = null,
    val selectedPlaylist: PlaylistDetail? = null,
    val selectedSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    val availableSourceFilters: List<LibrarySourceFilter> = listOf(LibrarySourceFilter.ALL),
    val sourceTypesById: Map<String, ImportSourceType> = emptyMap(),
    val isRefreshing: Boolean = false,
    val message: String? = null,
)

sealed interface PlaylistsIntent {
    data class SelectPlaylist(val playlistId: String?) : PlaylistsIntent
    data class SourceFilterChanged(val filter: LibrarySourceFilter) : PlaylistsIntent
    data object BackToList : PlaylistsIntent
    data class CreatePlaylist(val name: String) : PlaylistsIntent
    data class CreatePlaylistAndAddTrack(val name: String, val track: Track?) : PlaylistsIntent
    data class AddTrackToPlaylist(val playlistId: String, val track: Track) : PlaylistsIntent
    data class RemoveTrackFromPlaylist(val playlistId: String, val trackId: String) : PlaylistsIntent
    data object Refresh : PlaylistsIntent
    data object ClearMessage : PlaylistsIntent
}

sealed interface PlaylistsEffect

class PlaylistsStore(
    private val playlistRepository: PlaylistRepository,
    private val importSourceRepository: ImportSourceRepository,
    private val storeScope: CoroutineScope,
    startImmediately: Boolean = true,
) : BaseStore<PlaylistsState, PlaylistsIntent, PlaylistsEffect>(
    initialState = PlaylistsState(),
    scope = storeScope,
) {
    private var detailJob: Job? = null
    private var contentStarted = false
    private var hasTriggeredInitialRemoteRefresh = false

    init {
        if (startImmediately) {
            ensureContentStarted()
        }
    }

    fun ensureContentStarted() {
        if (contentStarted) return
        contentStarted = true
        storeScope.launch {
            combine(
                playlistRepository.playlists,
                importSourceRepository.observeSources(),
            ) { playlists, sources ->
                Snapshot(
                    playlists = playlists,
                    sources = sources,
                    navidromeSourceIds = sources
                        .map(SourceWithStatus::source)
                        .filter { it.type == ImportSourceType.NAVIDROME }
                        .mapTo(linkedSetOf()) { it.id },
                )
            }.collect { snapshot ->
                refreshNavidromePlaylistsOnFirstActivation(snapshot.navidromeSourceIds)
                val previousSelectedId = state.value.selectedPlaylistId
                val nextSelectedId = previousSelectedId?.takeIf { selectedId ->
                    snapshot.playlists.any { it.id == selectedId }
                }
                updateState {
                    val availableSourceFilters = buildAvailableSourceFilters(snapshot.sources)
                    val selectedSourceFilter = it.selectedSourceFilter
                        .takeIf { filter ->
                            filter == LibrarySourceFilter.ALL || filter in availableSourceFilters
                        }
                        ?: LibrarySourceFilter.ALL
                    it.copy(
                        isLoadingContent = false,
                        playlists = snapshot.playlists,
                        selectedPlaylistId = nextSelectedId,
                        selectedSourceFilter = selectedSourceFilter,
                        availableSourceFilters = availableSourceFilters,
                        sourceTypesById = snapshot.sources.associate { sourceWithStatus ->
                            sourceWithStatus.source.id to sourceWithStatus.source.type
                        },
                    )
                }
                if (nextSelectedId != previousSelectedId) {
                    observeSelectedPlaylist(nextSelectedId)
                }
            }
        }
    }

    override suspend fun handleIntent(intent: PlaylistsIntent) {
        when (intent) {
            PlaylistsIntent.BackToList -> observeSelectedPlaylist(null)
            PlaylistsIntent.ClearMessage -> updateState { it.copy(message = null) }
            PlaylistsIntent.Refresh -> refreshPlaylists()
            is PlaylistsIntent.SourceFilterChanged -> updateState { state ->
                state.copy(
                    selectedSourceFilter = intent.filter
                        .takeIf { it == LibrarySourceFilter.ALL || it in state.availableSourceFilters }
                        ?: LibrarySourceFilter.ALL,
                )
            }
            is PlaylistsIntent.SelectPlaylist -> observeSelectedPlaylist(intent.playlistId)
            is PlaylistsIntent.CreatePlaylist -> createPlaylist(intent.name)
            is PlaylistsIntent.CreatePlaylistAndAddTrack -> createPlaylistAndMaybeAddTrack(intent.name, intent.track)
            is PlaylistsIntent.AddTrackToPlaylist -> addTrackToPlaylist(intent.playlistId, intent.track)
            is PlaylistsIntent.RemoveTrackFromPlaylist -> removeTrackFromPlaylist(intent.playlistId, intent.trackId)
        }
    }

    private fun observeSelectedPlaylist(playlistId: String?) {
        detailJob?.cancel()
        updateState {
            it.copy(
                selectedPlaylistId = playlistId,
                selectedPlaylist = if (playlistId == null) null else it.selectedPlaylist,
            )
        }
        if (playlistId == null) {
            updateState { it.copy(selectedPlaylist = null) }
            return
        }
        detailJob = storeScope.launch {
            playlistRepository.observePlaylistDetail(playlistId).collect { detail ->
                updateState {
                    it.copy(
                        selectedPlaylistId = detail?.id,
                        selectedPlaylist = detail,
                    )
                }
            }
        }
    }

    private suspend fun createPlaylist(name: String) {
        playlistRepository.createPlaylist(name)
            .onSuccess { summary ->
                observeSelectedPlaylist(summary.id)
                updateState { it.copy(message = null) }
            }
            .onFailure { throwable ->
                updateState { it.copy(message = throwable.message.orEmpty().ifBlank { "歌单创建失败。" }) }
            }
    }

    private suspend fun createPlaylistAndMaybeAddTrack(
        name: String,
        track: Track?,
    ) {
        playlistRepository.createPlaylist(name)
            .onSuccess { summary ->
                observeSelectedPlaylist(summary.id)
                if (track != null) {
                    playlistRepository.addTrackToPlaylist(summary.id, track)
                        .onSuccess { updateState { it.copy(message = null) } }
                        .onFailure { throwable ->
                            updateState { it.copy(message = throwable.message.orEmpty().ifBlank { "加入歌单失败。" }) }
                        }
                } else {
                    updateState { it.copy(message = null) }
                }
            }
            .onFailure { throwable ->
                updateState { it.copy(message = throwable.message.orEmpty().ifBlank { "歌单创建失败。" }) }
            }
    }

    private suspend fun addTrackToPlaylist(
        playlistId: String,
        track: Track,
    ) {
        playlistRepository.addTrackToPlaylist(playlistId, track)
            .onSuccess { updateState { it.copy(message = null) } }
            .onFailure { throwable ->
                updateState { it.copy(message = throwable.message.orEmpty().ifBlank { "加入歌单失败。" }) }
            }
    }

    private suspend fun removeTrackFromPlaylist(
        playlistId: String,
        trackId: String,
    ) {
        playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
            .onSuccess { updateState { it.copy(message = null) } }
            .onFailure { throwable ->
                updateState { it.copy(message = throwable.message.orEmpty().ifBlank { "移出歌单失败。" }) }
            }
    }

    private suspend fun refreshPlaylists() {
        updateState { it.copy(isRefreshing = true, message = null) }
        playlistRepository.refreshNavidromePlaylists()
            .onSuccess {
                updateState { it.copy(isRefreshing = false, message = null) }
            }
            .onFailure { throwable ->
                updateState {
                    it.copy(
                        isRefreshing = false,
                        message = throwable.message.orEmpty().ifBlank { "歌单同步失败。" },
                    )
                }
            }
    }

    private fun refreshNavidromePlaylistsOnFirstActivation(navidromeSourceIds: Set<String>) {
        if (hasTriggeredInitialRemoteRefresh || navidromeSourceIds.isEmpty()) return
        hasTriggeredInitialRemoteRefresh = true
        storeScope.launch {
            refreshPlaylists()
        }
    }

    private data class Snapshot(
        val playlists: List<PlaylistSummary>,
        val sources: List<SourceWithStatus>,
        val navidromeSourceIds: Set<String>,
    )

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

    private companion object {
        val FILTER_ORDER = listOf(
            LibrarySourceFilter.LOCAL_FOLDER,
            LibrarySourceFilter.SAMBA,
            LibrarySourceFilter.WEBDAV,
            LibrarySourceFilter.NAVIDROME,
        )
    }
}
