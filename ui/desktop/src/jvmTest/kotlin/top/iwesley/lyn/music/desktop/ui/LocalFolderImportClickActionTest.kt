package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

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
