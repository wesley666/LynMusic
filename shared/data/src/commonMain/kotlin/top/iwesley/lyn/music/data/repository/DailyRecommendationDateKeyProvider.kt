package top.iwesley.lyn.music.data.repository

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DailyRecommendationDateKeyProvider {
    fun currentDateKey(): String
}

interface DailyRecommendationDateChangeNotifier {
    val dateKeys: Flow<String>
    fun refreshCurrentDateKey()
}

class DefaultDailyRecommendationDateChangeNotifier(
    private val dateKeyProvider: DailyRecommendationDateKeyProvider,
) : DailyRecommendationDateChangeNotifier {
    private val mutableDateKeys = MutableStateFlow(dateKeyProvider.currentDateKey())

    override val dateKeys: Flow<String> = mutableDateKeys.asStateFlow()

    override fun refreshCurrentDateKey() {
        mutableDateKeys.value = dateKeyProvider.currentDateKey()
    }
}

object UtcDailyRecommendationDateKeyProvider : DailyRecommendationDateKeyProvider {
    override fun currentDateKey(): String {
        return Clock.System.now()
            .toString()
            .take(10)
    }
}
