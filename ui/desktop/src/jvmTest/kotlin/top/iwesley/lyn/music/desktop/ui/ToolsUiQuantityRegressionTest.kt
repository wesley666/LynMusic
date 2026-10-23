package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.resolveUiString

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.feature.offline.ActiveBatchDownloadState
import top.iwesley.lyn.music.feature.offline.BatchDownloadSizeEstimate
import top.iwesley.lyn.music.feature.offline.batchDownloadSizeEstimateLabel

class ToolsUiQuantityRegressionTest {

    @Test fun downloadProgressUsesTotalCountRatherThanProcessedCount() = runTest {
        val original = AppLanguageRuntime.appLanguage.value
        try {
            AppLanguageRuntime.update(AppLanguage.English)
            for (total in 1..2) for (processed in 0..total) {
                val batch = ActiveBatchDownloadState(List(total) { "track-$it" }, processedCount = processed)
                val label = resolveUiText(requireNotNull(offlineBatchDownloadStatusSummary(batch, emptyMap())).label, AppLanguageRuntime.effectiveLanguage.value)
                assertTrue(label.startsWith("Downloading $processed/$total ${if (total == 1) "song" else "songs"} ·"), label)
            }
            assertEquals(null, offlineBatchDownloadStatusSummary(ActiveBatchDownloadState(emptyList()), emptyMap()))
        } finally { AppLanguageRuntime.update(original) }
    }

    @Test fun queueHistoryAndOnlineSummaryHandleZeroOneAndTwo() = runTest {
        val original = AppLanguageRuntime.appLanguage.value
        try {
            AppLanguageRuntime.update(AppLanguage.English)
            for (count in 0..2) {
                val noun = if (count == 1) "song" else "songs"
                assertEquals("$count $noun · Mode 原文", resolveUiString(Res.plurals.player_queue_summary, (count).toInt(), count, "Mode 原文"))
                assertEquals("Played $count ${if (count == 1) "time" else "times"} · Today", resolveUiString(Res.plurals.recent_track_play_summary, (count).toInt(), count, "Today"))
                assertEquals("Online mode is enabled. The remote library contains $count $noun; the local index is unchanged.", resolveUiString(Res.plurals.online_source_summary, (count).toInt(), count))
            }
        } finally { AppLanguageRuntime.update(original) }
    }

    @Test fun quantitiesAfterAQueryOrSourceNameDoNotUseTheLeadingArgument() = runTest {
        for (count in 0..2) {
            val noun = if (count == 1) "song" else "songs"
            val query = uiPlural(Res.plurals.tv_search_result_track_count, count, "1 原文 %1\$s", count)
            assertEquals("Search “1 原文 %1\$s” · $count $noun", resolveUiText(query, AppLanguage.English))
            val source = uiPlural(Res.plurals.source_navidrome_online_switch_summary, count, "1", count)
            assertEquals("“1” now uses Navidrome online mode. The remote library contains $count $noun. The old local index is hidden and retained.", resolveUiText(source, AppLanguage.English))
            val prompt = uiPlural(Res.plurals.source_online_rescan_confirmation, count, "1", count)
            assertEquals("“1” contains $count remote $noun. Online mode hides and retains the old local index. You can also rescan the entire remote library into the local index.", resolveUiText(prompt, AppLanguage.English))
            val loaded = uiPlural(Res.plurals.library_loaded_track_progress, count, 0, count)
            assertEquals("Loaded 0 of $count $noun", resolveUiText(loaded, AppLanguage.English))
            assertEquals("已加載 0 / 共 $count 首", resolveUiText(loaded, AppLanguage.TraditionalChinese))
        }
    }

    @Test fun estimatesSelectTheUnknownSongCountAfterTheSizeArguments() = runTest {
        for (count in 0..2) {
            val text = batchDownloadSizeEstimateLabel(BatchDownloadSizeEstimate(totalBytes = 1024, unknownCount = count))
            val expected = if (count == 0) "1.0 KB" else "1.0 KB + $count ${if (count == 1) "song" else "songs"} of unknown size"
            assertEquals(expected, resolveUiText(text, AppLanguage.English))
            assertEquals(if (count == 0) "1.0 KB" else "1.0 KB + $count 首未知", resolveUiText(text, AppLanguage.TraditionalChinese))
        }
    }

    private suspend fun withLanguages(block: suspend (AppLanguage) -> Unit) {
        val original = AppLanguageRuntime.appLanguage.value
        try {
            listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese).forEach { language ->
                AppLanguageRuntime.update(language)
                block(language)
            }
        } finally { AppLanguageRuntime.update(original) }
    }
}
