package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals

class WebDavUiTextTest {
    @Test fun authenticationAndHttpFailuresSelectTheCorrectExplanation() = runTest {
        data class Case(val status: Int, val auth: Boolean, val detail: String?, val resource: org.jetbrains.compose.resources.StringResource)
        listOf(
            Case(401, false, "Basic realm=Music", Res.string.webdav_anonymous_basic_auth_required),
            Case(401, false, null, Res.string.webdav_anonymous_auth_required),
            Case(401, true, "Basic realm=Music", Res.string.webdav_basic_auth_failed),
            Case(401, true, null, Res.string.webdav_operation_auth_failed),
            Case(403, true, null, Res.string.webdav_operation_access_denied),
            Case(500, false, null, Res.string.webdav_operation_http_failed),
        ).forEach { case ->
            val base = when (case.resource) {
                Res.string.webdav_operation_http_failed -> uiText(case.resource, WebDavOperation.TestConnection.text, case.status)
                Res.string.webdav_operation_auth_failed, Res.string.webdav_operation_access_denied -> uiText(case.resource, WebDavOperation.TestConnection.text)
                else -> uiText(case.resource)
            }
            val expected = case.detail?.let { uiText(Res.string.webdav_failure_with_server_detail, base, UiText.Raw(it)) } ?: base
            assertEquals(expected, webDavHttpFailureText(WebDavOperation.TestConnection, case.status, case.auth, case.detail))
        }
    }

    @Test fun operationNamesResolveAtDisplayTime() = runTest {
        val names = listOf("connection test", "scan", "lyrics file read", "metadata probe", "playback")
        WebDavOperation.entries.zip(names).forEach { (operation, name) ->
            val text = webDavHttpFailureText(operation, 500, true, " ")
            assertEquals("WebDAV $name failed, HTTP 500.", resolveUiText(text, AppLanguage.English))
            assertEquals("WebDAV ${resolveUiText(operation.text, AppLanguage.TraditionalChinese)} 失敗，HTTP 500。",
                resolveUiText(text, AppLanguage.TraditionalChinese))
        }
    }

    @Test fun theSameAuthenticationErrorChangesLanguageAndPreservesServerDetails() = runTest {
        val detail = "Basic 原始服务端消息 %1\$s"
        val text = webDavHttpFailureText(WebDavOperation.TestConnection, 401, true, " $detail ")
        assertEquals("WebDAV Basic Auth failed. Check your username and password. Server details: $detail",
            resolveUiText(text, AppLanguage.English))
        assertEquals("WebDAV Basic Auth 认证失败，请检查用户名和密码。 服务端信息: $detail",
            resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("WebDAV Basic Auth 驗證失敗，請檢查使用者名稱和密碼。 伺服器資訊: $detail",
            resolveUiText(text, AppLanguage.TraditionalChinese))
    }

    @Test fun diagnosticDescriptionsUseReadableKeysAndOriginalArguments() = runTest {
        assertEquals("webdav_failure_with_server_detail(arg1=webdav_operation_access_denied(arg1=测试连接), arg2=Forbidden)",
            describeWebDavHttpFailure("测试连接", 403, true, "Forbidden"))
        assertEquals("webdav_failure_with_server_detail(arg1=webdav_anonymous_basic_auth_required, arg2=Basic realm=Music)",
            describeWebDavHttpFailure("扫描", 401, false, "Basic realm=Music"))
    }
}
