package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

class SourceImportEntryPolicyTest {
    @Test fun `source list chooses the supported local folder action`() {
        for (supportsSystemPicker in listOf(false, true)) {
            val platform = PlatformDescriptor("Desktop", PlatformCapabilities(true, true, true, true, true,
                supportsSystemLocalFolderPicker = supportsSystemPicker))
            assertEquals(LocalFolderImportClickAction.ImportAutomatic, resolveLocalFolderImportClickAction(platform))
        }
    }
}
