package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.core.model.resolveUiString

import top.iwesley.lyn.music.resources.*

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.uiText

/** Official Android resource reads in a local JVM sandbox; no Activity, window or device. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class AndroidNativeUiStringsTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun rootUiLabelChangesWhileSavedLabelStaysOriginal() = runTest {
        val root = AndroidStorageRoot("内置存储", File("/storage"), false, uiText(Res.string.folder_picker_internal_storage))
        AppLanguageRuntime.update(AppLanguage.English)
        assertEquals("Internal storage", root.uiLabel?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) })
        AppLanguageRuntime.update(AppLanguage.SimplifiedChinese)
        assertEquals("内置存储", root.uiLabel?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) })
        AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
        assertEquals("內建存儲", root.uiLabel?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) })
        assertEquals("内置存储", root.label)
    }

    @Test fun officialNativeResourcesResolveZeroOneAndTwoQuantities() = runTest {
        AppLanguageRuntime.update(AppLanguage.English)
        assertEquals("0 songs", resolveUiString(Res.plurals.common_track_count_short, (0).toInt(), 0))
        assertEquals("1 song", resolveUiString(Res.plurals.common_track_count_short, (1).toInt(), 1))
        assertEquals("2 songs", resolveUiString(Res.plurals.common_track_count_short, (2).toInt(), 2))
    }
}
