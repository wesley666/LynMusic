package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.platform.appleLocalAccessFailureText
import top.iwesley.lyn.music.platform.applePlaybackFailureText
import top.iwesley.lyn.music.platform.applePlaybackLoadFailureText

class ApplePlaybackErrorTextTest {
    @Test fun localAccessKeepsDescribedFailuresAcrossLanguages() = runTest {
        val exception = UiTextException(uiText(Res.string.local_track_file_missing))
        val text = appleLocalAccessFailureText("iOS", exception)
        assertEquals(exception.text, text)
        assertEquals("The local song file no longer exists.", resolveUiText(text, AppLanguage.English))
        assertEquals("本地歌曲文件已不存在。", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("本機歌曲檔案已不存在。", resolveUiText(text, AppLanguage.TraditionalChinese))
        assertEquals("local_track_file_missing", exception.message)
    }

    @Test fun nativeDetailsRemainRawWhileContextAndFallbackFollowLanguage() = runTest {
        val detail = "AVPlayer 原始错误 %1\$s"
        val text = applePlaybackFailureText("macOS", detail)
        assertEquals("macOS playback failed.\n$detail", resolveUiText(text, AppLanguage.English))
        assertEquals("macOS 播放失败。\n$detail", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("macOS 播放失敗。\n$detail", resolveUiText(text, AppLanguage.TraditionalChinese))
        assertEquals("iOS playback failed.", resolveUiText(applePlaybackFailureText("iOS", null), AppLanguage.English))
        assertEquals("iOS 播放失敗。", resolveUiText(applePlaybackFailureText("iOS", ""), AppLanguage.TraditionalChinese))
    }

    @Test fun loadErrorsKeepApplicationDescriptionsAndRawThirdPartyDetails() = runTest {
        val exception = UiTextException(uiText(Res.string.apple_invalid_media_url))
        assertEquals(exception.text, applePlaybackLoadFailureText("iOS", exception))
        val text = appleLocalAccessFailureText("iOS", IllegalStateException("provider 原文"))
        assertEquals("iOS could not access the local song.\nprovider 原文", resolveUiText(text, AppLanguage.English))
        assertEquals("iOS 無法存取本機歌曲。\nprovider 原文", resolveUiText(text, AppLanguage.TraditionalChinese))
        val missingDetail = appleLocalAccessFailureText("iOS", IllegalStateException())
        assertEquals("iOS could not access the local song.", resolveUiText(missingDetail, AppLanguage.English))
    }
}
