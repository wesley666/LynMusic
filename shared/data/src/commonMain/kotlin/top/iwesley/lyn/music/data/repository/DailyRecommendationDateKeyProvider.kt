package top.iwesley.lyn.music.data.repository

import kotlin.time.Clock

interface DailyRecommendationDateKeyProvider {
    fun currentDateKey(): String
}

object UtcDailyRecommendationDateKeyProvider : DailyRecommendationDateKeyProvider {
    override fun currentDateKey(): String {
        return Clock.System.now()
            .toString()
            .take(10)
    }
}
