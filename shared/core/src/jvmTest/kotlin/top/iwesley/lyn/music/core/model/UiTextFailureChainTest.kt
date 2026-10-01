package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.resources.*

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiTextFailureChainTest {
    @Test fun nestedPlatformWrappersPreserveTheApplicationDescription() = runTest {
        val text = uiText(Res.string.samba_path_unavailable)
        val error = IllegalStateException("Source error", IOException("Load failed", UiTextException(text)))
        assertEquals(text, error.uiFailureTextOrNull())
        assertEquals("The SMB path does not exist or cannot be accessed.", resolveUiText(text, AppLanguage.English))
        assertEquals("SMB 路徑不存在或無法存取。", resolveUiText(text, AppLanguage.TraditionalChinese))
    }

    @Test fun outerApplicationDescriptionTakesPrecedence() = runTest {
        val inner = UiTextException(uiText(Res.string.samba_path_unavailable))
        val outer = UiTextException(uiText(Res.string.samba_playback_failed, inner.text), inner)
        assertEquals(outer.text, outer.uiFailureTextOrNull())
        assertNull(IOException("provider 原文").uiFailureTextOrNull())
    }

    @Test fun cyclesWithoutApplicationDescriptionsTerminate() = runTest {
        val first = IOException("first")
        val second = IllegalStateException("second")
        first.initCause(second)
        second.initCause(first)
        assertNull(first.uiFailureTextOrNull())
    }
}
