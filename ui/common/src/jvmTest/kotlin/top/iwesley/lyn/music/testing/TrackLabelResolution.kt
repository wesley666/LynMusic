package top.iwesley.lyn.music.testing
import top.iwesley.lyn.music.core.model.*
internal suspend fun formatTrackTechnicalSummary(track: Track): String = resolveUiText(top.iwesley.lyn.music.formatTrackTechnicalSummaryText(track), AppLanguageRuntime.effectiveLanguage.value)
