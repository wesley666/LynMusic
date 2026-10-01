package top.iwesley.lyn.music.platform

import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.*

class JvmUiFailureFollowupTest {
    @Test fun cleanupFailureWarningKeepsGuidanceAndNestedResourceDescription() = runTest {
        withHome { home ->
            home.resolve(".lynmusic").mkdirs()
            val cleanup = home.resolve("old").apply { mkdirs() }
            val retained = cleanup.resolve("keep.txt").apply { writeText("keep") }
            writeProperties(home, "cleanup_root" to cleanup.absolutePath)
            val manager = JvmDataLocationManager(home, "Windows 11")
            assertTrue(manager.applyPendingChange().isSuccess)
            val warning = assertNotNull(manager.cleanupWarning)
            assertEquals("The new data location is active, but the old folder could not be cleaned up. Retry in storage settings: The old-data cleanup record is damaged: The cleanup record is missing the folder identity or operation ID. No folders were deleted.", resolveUiText(warning, AppLanguage.English))
            assertTrue(resolveUiText(warning, AppLanguage.TraditionalChinese).startsWith("新資料位置已啟用，但舊目錄清理失敗"))
            assertEquals("keep", retained.readText())
        }
    }
    @Test fun vlcSelectionGuidanceUsesPlatformSpecificResources() = runTest {
        val mac = desktopVlcInvalidSelectionMessage("Mac OS X")
        assertEquals("Select VLC.app, or a folder containing libvlc.dylib and libvlccore.dylib.", resolveUiText(mac, AppLanguage.English))
        assertEquals("請選擇 VLC.app，或直接選擇包含 libvlc.dylib 和 libvlccore.dylib 的目錄。", resolveUiText(mac, AppLanguage.TraditionalChinese))
        for (os in listOf("Windows 11", "Linux")) {
            val text = desktopVlcInvalidSelectionMessage(os)
            assertEquals("Select the VLC installation folder containing libvlc and libvlccore.", resolveUiText(text, AppLanguage.English))
            assertEquals("请选择包含 libvlc 和 libvlccore 的 VLC 安装目录。", resolveUiText(text, AppLanguage.SimplifiedChinese))
        }
    }

    @Test fun missingScanDirectoryKeepsOriginalPathWithoutNetworkOrFileMutation() = runTest {
        withHome { home ->
            var requests = 0
            val client = object : LyricsHttpClient {
                override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> { requests++; error("Unexpected request") }
            }
            val gateway = JvmImportSourceGateway(NoopDiagnosticLogger, client)
            val path = File(home, "用户目录/removed").absolutePath
            val error = checkNotNull(runCatching { gateway.scanLocalFolder(LocalFolderSelection("saved", path), "source") }.exceptionOrNull())
            assertIs<UiTextFailure>(error)
            val text = error.uiErrorDetail()
            assertEquals("Folder does not exist: $path", resolveUiText(text, AppLanguage.English))
            assertEquals("目录不存在：$path", resolveUiText(text, AppLanguage.SimplifiedChinese))
            assertEquals("目錄不存在：$path", resolveUiText(text, AppLanguage.TraditionalChinese))
            assertFalse(File(path).exists())
            assertEquals(0, requests)
        }
    }

    @Test fun ignoredCleanupPathWarningIsUnresolvedAndDoesNotDeleteFiles() = runTest {
        withHome { home ->
            val retained = home.resolve("relative/keep.txt").apply { parentFile.mkdirs(); writeText("keep") }
            writeProperties(home, "cleanup_root" to "relative/用户目录")
            val manager = JvmDataLocationManager(home, "Windows 11")
            assertEquals(null, manager.pendingCleanupRootPath())
            val warning = assertNotNull(manager.cleanupWarning)
            assertEquals("The invalid old-data cleanup record was ignored. No folders were deleted: relative/用户目录", resolveUiText(warning, AppLanguage.English))
            assertEquals("已忽略无效的旧数据清理记录，未删除任何目录：relative/用户目录", resolveUiText(warning, AppLanguage.SimplifiedChinese))
            assertEquals("已忽略無效的舊資料清理記錄，未刪除任何目錄：relative/用户目录", resolveUiText(warning, AppLanguage.TraditionalChinese))
            assertEquals("keep", retained.readText())
        }
    }

    @Test fun invalidCleanupPhaseAndIdentityWarningsFollowLanguageChanges() = runTest {
        withHome { home ->
            val cleanup = home.resolve("old").apply { mkdirs() }
            writeProperties(home, "cleanup_root" to cleanup.absolutePath, "cleanup_phase" to "原始值")
            val phaseManager = JvmDataLocationManager(home, "Windows 11")
            assertEquals(cleanup.absolutePath, phaseManager.pendingCleanupRootPath())
            val phase = assertNotNull(phaseManager.cleanupWarning)
            assertEquals("The old-data cleanup record is damaged: invalid cleanup_phase=原始值. No folders were deleted.", resolveUiText(phase, AppLanguage.English))
            writeProperties(home, "cleanup_root" to cleanup.absolutePath)
            val identityManager = JvmDataLocationManager(home, "Windows 11")
            identityManager.pendingCleanupRootPath()
            val identity = assertNotNull(identityManager.cleanupWarning)
            assertEquals("The old-data cleanup record is damaged: The cleanup record is missing the folder identity or operation ID. No folders were deleted.", resolveUiText(identity, AppLanguage.English))
            assertEquals("舊資料清理設定損壞：清理記錄缺少目錄身分或 operation ID。未刪除任何目錄。", resolveUiText(identity, AppLanguage.TraditionalChinese))
            assertTrue(cleanup.exists())
        }
    }

    private fun writeProperties(home: File, vararg values: Pair<String, String>) {
        val properties = Properties().apply { values.forEach { (key, value) -> setProperty(key, value) } }
        home.resolve(".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
    }
    private inline fun withHome(action: (File) -> Unit) {
        val home = Files.createTempDirectory("lyn-ui-followup").toFile()
        try { action(home) } finally { home.deleteRecursively() }
    }
}
