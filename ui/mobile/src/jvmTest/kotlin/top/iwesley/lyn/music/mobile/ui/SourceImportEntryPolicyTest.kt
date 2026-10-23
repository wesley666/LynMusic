package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

class SourceImportEntryPolicyTest {
    @Test fun `source list chooses the supported local folder action`() {
        for (supportsSystemPicker in listOf(false, true)) {
            val platform = PlatformDescriptor(ANDROID_PLATFORM_NAME, PlatformCapabilities(true, true, true, true, true,
                supportsSystemLocalFolderPicker = supportsSystemPicker))
            assertEquals(if (supportsSystemPicker) LocalFolderImportClickAction.ShowPickerModeDialog else LocalFolderImportClickAction.ImportBuiltIn, resolveLocalFolderImportClickAction(platform))
        }
    }
}
