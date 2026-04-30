package top.iwesley.lyn.music.feature.my

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.RecentAlbum
import top.iwesley.lyn.music.core.model.RecentTrack
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.MyRepository

data class MyState(
    val isLoadingContent: Boolean = true,
    val recentTracks: List<RecentTrack> = emptyList(),
    val recentAlbums: List<RecentAlbum> = emptyList(),
    val isRefreshingNavidrome: Boolean = false,
    val message: String? = null,
)

sealed interface MyIntent {
    data object RefreshNavidromeRecentPlays : MyIntent
    data object ClearMessage : MyIntent
}

sealed interface MyEffect

class MyStore(
    private val repository: MyRepository,
    private val storeScope: CoroutineScope,
    startImmediately: Boolean = true,
) : BaseStore<MyState, MyIntent, MyEffect>(
    initialState = MyState(),
    scope = storeScope,
) {
    private var hasStarted = false
    private var refreshJob: Job? = null

    init {
        if (startImmediately) {
            ensureStarted()
        }
    }

    fun ensureStarted() {
        if (hasStarted) return
        hasStarted = true
        storeScope.launch {
            combine(
                repository.recentTracks,
                repository.recentAlbums,
            ) { tracks, albums ->
                tracks to albums
            }.collect { (tracks, albums) ->
                updateState {
                    it.copy(
                        isLoadingContent = false,
                        recentTracks = tracks,
                        recentAlbums = albums,
                    )
                }
            }
        }
        refreshNavidromeRecentPlays()
    }

    override suspend fun handleIntent(intent: MyIntent) {
        when (intent) {
            MyIntent.ClearMessage -> updateState { it.copy(message = null) }
            MyIntent.RefreshNavidromeRecentPlays -> refreshNavidromeRecentPlays()
        }
    }

    private fun refreshNavidromeRecentPlays() {
        if (refreshJob?.isActive == true) return
        refreshJob = storeScope.launch {
            updateState { it.copy(isRefreshingNavidrome = true) }
            repository.refreshNavidromeRecentPlays()
                .onSuccess {
                    updateState {
                        it.copy(
                            isRefreshingNavidrome = false,
                            message = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    updateState {
                        it.copy(
                            isRefreshingNavidrome = false,
                            message = throwable.message.orEmpty().ifBlank { "Navidrome 最近播放同步失败，已显示本地统计。" },
                        )
                    }
                }
        }
    }
}
