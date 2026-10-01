package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackUiTextTest {
    @Test fun applicationErrorsKeepDescriptionsAndDiagnosticsSeparate() = runTest {
        val text = uiText(Res.string.playback_vlc_unavailable)
        val state = PlaybackGatewayState(errorMessage = "仅用于诊断", errorText = text)
        assertEquals(text, state.playbackErrorText())
        assertEquals("VLC was not detected. Install VLC or choose its path in Settings.", resolveUiText(text, AppLanguage.English))
        assertEquals("未检测到 VLC，请安装或在设置手动选择 VLC 路径。", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("未偵測到 VLC，請安裝或在設定中手動選擇 VLC 路徑。", resolveUiText(text, AppLanguage.TraditionalChinese))
        assertEquals("仅用于诊断", state.errorMessage)
    }

    @Test fun loadFailuresPreserveRawDetailsAndNestedApplicationDescriptions() = runTest {
        val details = "server 中文 detail %1\$s"
        val rawFailure = playbackLoadFailureText(IllegalStateException(details))
        assertEquals("Failed to access the song: $details", resolveUiText(rawFailure, AppLanguage.English))
        assertEquals("存取歌曲失敗：$details", resolveUiText(rawFailure, AppLanguage.TraditionalChinese))
        val describedFailure = playbackLoadFailureText(UiTextException(uiText(Res.string.webdav_root_invalid)))
        assertEquals("Failed to access the song: The WebDAV root URL is invalid.", resolveUiText(describedFailure, AppLanguage.English))
        val wrappedFailure = playbackLoadFailureText(IllegalStateException("External wrapper", UiTextException(uiText(Res.string.webdav_root_invalid))))
        assertEquals(describedFailure, wrappedFailure)
        val snapshot = PlaybackSnapshot(errorMessage = details)
        assertEquals("Operation failed: $details", resolveUiText(checkNotNull(snapshot.playbackErrorText()), AppLanguage.English))
    }
}
