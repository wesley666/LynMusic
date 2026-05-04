package top.iwesley.lyn.music.feature.my

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.RecentAlbum
import top.iwesley.lyn.music.core.model.RecentTrack
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.MyRepository

data class MyState(
    val isLoadingContent: Boolean = true,
    val recentTracks: List<RecentTrack> = emptyList(),
    val recentAlbums: List<RecentAlbum> = emptyList(),
    val dailyRecommendationTracks: List<Track> = emptyList(),
    val isGeneratingDailyRecommendation: Boolean = false,
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
    private var dailyRecommendationJob: Job? = null
    private var dailyRecommendationDateJob: Job? = null
    private var dailyRecommendationCandidateJob: Job? = null

    init {
        if (startImmediately) {
            ensureStarted()
        }
    }

    fun ensureStarted() {
        repository.refreshDailyRecommendationDateKey()
        if (hasStarted) return
        hasStarted = true
        storeScope.launch {
            combine(
                repository.recentTracks,
                repository.recentAlbums,
                repository.dailyRecommendation,
            ) { tracks, albums, dailyRecommendation ->
                Triple(tracks, albums, dailyRecommendation)
            }.collect { (tracks, albums, dailyRecommendation) ->
                updateState {
                    it.copy(
                        isLoadingContent = false,
                        recentTracks = tracks,
                        recentAlbums = albums,
                        dailyRecommendationTracks = dailyRecommendation,
                    )
                }
            }
        }
        if (dailyRecommendationDateJob?.isActive != true) {
            dailyRecommendationDateJob = storeScope.launch {
                repository.dailyRecommendationDateKey
                    .distinctUntilChanged()
                    .collect {
                        ensureDailyRecommendation()
                    }
            }
        }
        if (dailyRecommendationCandidateJob?.isActive != true) {
            dailyRecommendationCandidateJob = storeScope.launch {
                var previousHasCandidates: Boolean? = null
                repository.hasDailyRecommendationCandidates
                    .distinctUntilChanged()
                    .collect { hasCandidates ->
                        val shouldGenerate =
                            previousHasCandidates == false &&
                                hasCandidates &&
                                state.value.dailyRecommendationTracks.isEmpty()
                        previousHasCandidates = hasCandidates
                        if (shouldGenerate) {
                            ensureDailyRecommendation()
                        }
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

    private fun ensureDailyRecommendation() {
        if (dailyRecommendationJob?.isActive == true) return
        dailyRecommendationJob = storeScope.launch {
            updateState { it.copy(isGeneratingDailyRecommendation = true) }
            repository.ensureDailyRecommendation()
                .onSuccess {
                    updateState { it.copy(isGeneratingDailyRecommendation = false) }
                }
                .onFailure { throwable ->
                    updateState {
                        it.copy(
                            isGeneratingDailyRecommendation = false,
                            message = throwable.message.orEmpty().ifBlank { "每日推荐生成失败，已显示最近播放。" },
                        )
                    }
                }
        }
    }
}
