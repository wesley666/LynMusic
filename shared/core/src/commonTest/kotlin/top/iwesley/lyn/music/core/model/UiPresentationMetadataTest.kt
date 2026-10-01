package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals

class UiPresentationMetadataTest {
    @Test fun unsupportedScanFailureChangesLanguageWhileDiagnosticsAndResultsStayOriginal() = runTest {
        val failure = unsupportedAudioImportFailure("用户目录/song.ape")
        val summary = ImportScanReport(emptyList(), failures = listOf(failure), discoveredAudioFileCount = 1)
        val text = failure.reasonUiText()
        assertEquals("This platform does not support importing this audio format yet.", resolveUiText(text, AppLanguage.English))
        assertEquals("当前平台暂不支持导入该音频格式。", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("目前平台尚不支援匯入此音訊格式。", resolveUiText(text, AppLanguage.TraditionalChinese))
        assertEquals("This platform does not support importing this audio format yet.", resolveUiText(text, AppLanguage.English))
        assertEquals(UNSUPPORTED_AUDIO_IMPORT_REASON, failure.reason)
        assertEquals("用户目录/song.ape", failure.relativePath)
        assertEquals(listOf(failure), summary.failures)
        val historical = ImportScanFailure("old.ape", "历史诊断 %1\$s")
        assertEquals("历史诊断 %1\$s", resolveUiText(historical.reasonUiText(), AppLanguage.English))
    }

    @Test fun sourceNamesRemainRawByDefaultAndBuiltinLabelsResolveAtDisplayTime() = runTest {
        val document = LyricsDocument(listOf(LyricsLine(null, "歌词原文")), sourceId = "source", rawPayload = "歌词原文")
        val candidate = LyricsSearchCandidate("source", "用户来源名", document)
        assertEquals("用户来源名", resolveUiText(candidate.sourceNameUiText(), AppLanguage.English))
        val builtin = candidate.copy(sourceName = "歌曲标签", sourceNameText = uiText(Res.string.lyrics_source_song_tags))
        assertEquals("Song tags", resolveUiText(builtin.sourceNameUiText(), AppLanguage.English))
        assertEquals("歌曲標籤", resolveUiText(builtin.sourceNameUiText(), AppLanguage.TraditionalChinese))
        assertEquals(document, builtin.document)
        assertEquals("歌曲标签", builtin.sourceName)
    }

}
