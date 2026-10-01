package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.LocalFolderPickerMode
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor

class LocalFolderImportClickActionTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `system folder manager option uses automatic picker flow`() {
        assertEquals(LocalFolderPickerMode.Automatic, localFolderPickerDialogSystemMode())
    }

    @Test
    fun `android with system folder picker shows choice dialog`() {
        assertEquals(
            LocalFolderImportClickAction.ShowPickerModeDialog,
            resolveLocalFolderImportClickAction(platformNamed(ANDROID_PLATFORM_NAME, supportsSystemPicker = true)),
        )
    }

    @Test
    fun `android without system folder picker imports with built in picker`() {
        assertEquals(
            LocalFolderImportClickAction.ImportBuiltIn,
            resolveLocalFolderImportClickAction(platformNamed(ANDROID_PLATFORM_NAME, supportsSystemPicker = false)),
        )
    }

    @Test
    fun `android automotive follows android folder picker choice`() {
        assertEquals(
            LocalFolderImportClickAction.ShowPickerModeDialog,
            resolveLocalFolderImportClickAction(
                platformNamed(ANDROID_AUTOMOTIVE_PLATFORM_NAME, supportsSystemPicker = true),
            ),
        )
        assertEquals(
            LocalFolderImportClickAction.ImportBuiltIn,
            resolveLocalFolderImportClickAction(
                platformNamed(ANDROID_AUTOMOTIVE_PLATFORM_NAME, supportsSystemPicker = false),
            ),
        )
    }

    @Test
    fun `android tv keeps automatic import flow`() {
        assertEquals(
            LocalFolderImportClickAction.ImportAutomatic,
            resolveLocalFolderImportClickAction(platformNamed(ANDROID_TV_PLATFORM_NAME, supportsSystemPicker = true)),
        )
    }

    @Test
    fun `non android platforms keep automatic import flow`() {
        assertEquals(
            LocalFolderImportClickAction.ImportAutomatic,
            resolveLocalFolderImportClickAction(platformNamed("Desktop", supportsSystemPicker = true)),
        )
    }

    private fun platformNamed(name: String, supportsSystemPicker: Boolean): PlatformDescriptor {
        return PlatformDescriptor(
            name = name,
            capabilities = PlatformCapabilities(
                supportsLocalFolderImport = true,
                supportsSambaImport = true,
                supportsWebDavImport = true,
                supportsNavidromeImport = true,
                supportsSystemMediaControls = true,
                supportsSystemLocalFolderPicker = supportsSystemPicker,
            ),
        )
    }
}
