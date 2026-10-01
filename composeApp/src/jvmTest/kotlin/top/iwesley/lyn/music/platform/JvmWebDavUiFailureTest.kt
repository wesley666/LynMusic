package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*

import com.github.sardine.impl.SardineException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiTextFailure
import top.iwesley.lyn.music.core.model.WebDavOperation
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.uiText

class JvmWebDavUiFailureTest {
    @Test fun httpFailuresKeepTheirCauseAndIOExceptionTypeWhileTranslatingDetails() = runTest {
        val original = SardineException("response", 401, "Basic realm=Music")
        val error = original.asJvmWebDavIOException(WebDavOperation.TestConnection, true)
        assertIs<IOException>(error)
        assertIs<UiTextFailure>(error)
        assertSame(original, error.cause)
        assertEquals("WebDAV Basic Auth failed. Check your username and password. Server details: Basic realm=Music",
            resolveUiText(error.uiErrorDetail(), AppLanguage.English))
        assertEquals("webdav_failure_with_server_detail(arg1=webdav_basic_auth_failed, arg2=Basic realm=Music)", error.message)
    }

    @Test fun localValidationDescriptionsSurviveIOExceptionWrapping() = runTest {
        val original = UiTextException(uiText(Res.string.webdav_root_not_directory))
        val error = original.asJvmWebDavIOException(WebDavOperation.TestConnection, false)
        assertSame(original, error.cause)
        assertEquals(original.text, error.uiErrorDetail())
        assertEquals("The WebDAV root URL does not point to a directory.", resolveUiText(error.uiErrorDetail(), AppLanguage.English))
    }

    @Test fun externalIOExceptionsArePreservedForRetryAndDiagnostics() = runTest {
        val original = IOException("Third-party 原始详情")
        assertSame(original, original.asJvmWebDavIOException(WebDavOperation.Scan, false))
        val wrapped = IllegalStateException("Third-party 原始详情").asJvmWebDavIOException(WebDavOperation.Scan, false)
        assertEquals("WebDAV scan failed.\nThird-party 原始详情", resolveUiText(wrapped.uiErrorDetail(), AppLanguage.English))
    }
}
