package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.db.ImportSourceEntity

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class SambaCastProxyUiFailureTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun unavailableSourcesKeepResourceErrorsAndEnabledSourceKeepsItsFields() = runTest {
        val source = ImportSourceEntity("source", "SAMBA", "用户来源", "smb://nas/Music", "nas", "Music", "", "user", "credential", false, true, null, 1L)
        assertSame(source, requireEnabledSambaCastSource(source))
        for (unavailable in listOf(null, source.copy(enabled = false))) {
            val error = checkNotNull(runCatching { requireEnabledSambaCastSource(unavailable) }.exceptionOrNull())
            assertIs<UiTextFailure>(error)
            val text = error.uiErrorText()
            assertEquals("The Samba source is unavailable.", resolveUiText(text, AppLanguage.English))
            assertEquals("Samba 来源不可用。", resolveUiText(text, AppLanguage.SimplifiedChinese))
            assertEquals("Samba 來源無法使用。", resolveUiText(text, AppLanguage.TraditionalChinese))
        }
    }
}
