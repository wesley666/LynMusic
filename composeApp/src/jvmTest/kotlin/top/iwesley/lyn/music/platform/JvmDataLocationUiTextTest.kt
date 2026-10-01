package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppDataLocationChangeMode
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.UiTextArgumentException
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiErrorText

class JvmDataLocationUiTextTest {
    @Test fun nonEmptyTargetFailureKeepsFilesAndFollowsLanguageChanges() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val sourceFile = source.resolve("keep.txt").apply { writeText("source") }
            val target = File(home, "drive/LynMusic").apply { mkdirs() }
            val targetFile = target.resolve("keep.txt").apply { writeText("target") }
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = assertFailsWith<UiTextArgumentException> {
                manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            }

            assertDescriptions(
                error.uiErrorText(),
                "The target LynMusic directory must be empty.",
                "目标 LynMusic 目录必须为空。",
                "目標 LynMusic 目錄必須為空。",
            )
            assertEquals("source", sourceFile.readText())
            assertEquals("target", targetFile.readText())
            assertFalse(manager.hasPendingChange())
            assertFalse(File(home, ".lynmusic-location.properties").exists())
            assertFalse(source.resolve(".lynmusic-data-root").exists())
        }
    }

    @Test fun targetNameFailureKeepsItsArgumentAndDiagnosticMessage() = runTest {
        withTemporaryHome { home ->
            File(home, ".lynmusic").mkdirs()
            val target = File(home, "UnexpectedName")
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = assertFailsWith<UiTextArgumentException> {
                manager.scheduleChange(target, AppDataLocationChangeMode.Discard)
            }

            assertDescriptions(
                error.uiErrorText(),
                "The target data directory must be named LynMusic.",
                "目标数据目录必须命名为 LynMusic。",
                "目標資料目錄必須命名為 LynMusic。",
            )
            assertEquals("data_location_target_name_required(arg1=LynMusic)", error.message)
            assertFalse(target.exists())
            assertFalse(manager.hasPendingChange())
        }
    }

    @Test fun missingParentFailureDoesNotCreateTheSelectedDirectory() = runTest {
        withTemporaryHome { home ->
            File(home, ".lynmusic").mkdirs()
            val target = File(home, "missing/LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = assertFailsWith<UiTextArgumentException> {
                manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            }

            assertDescriptions(
                error.uiErrorText(),
                "The selected directory does not exist.",
                "所选目录不存在。",
                "所選目錄不存在。",
            )
            assertFalse(target.parentFile.exists())
            assertFalse(manager.hasPendingChange())
        }
    }

    @Test fun overlappingTargetFailureKeepsTheCurrentRoot() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val targetParent = source.resolve("nested").apply { mkdirs() }
            val target = targetParent.resolve("LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = assertFailsWith<UiTextArgumentException> {
                manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            }

            assertDescriptions(
                error.uiErrorText(),
                "The new data location cannot be inside the current data directory or contain it.",
                "新数据位置不能位于当前数据目录内部，也不能包含当前数据目录。",
                "新資料位置不能位於目前資料目錄內部，也不能包含目前資料目錄。",
            )
            assertEquals(source, manager.currentRootDirectory())
            assertFalse(target.exists())
            assertFalse(manager.hasPendingChange())
        }
    }

    @Test fun unchangedTargetFailurePreservesTheExistingConfiguration() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "active/LynMusic").apply { mkdirs() }
            active.resolve(".lynmusic-data-root").writeText("owned")
            val configuration = File(home, ".lynmusic-location.properties")
            writeConfiguration(configuration, "active_data_root" to active.absolutePath)
            val original = configuration.readText()
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = assertFailsWith<UiTextArgumentException> {
                manager.scheduleChange(active, AppDataLocationChangeMode.Discard)
            }

            assertDescriptions(
                error.uiErrorText(),
                "The new data location is the same as the current location.",
                "新数据位置与当前位置相同。",
                "新資料位置與目前位置相同。",
            )
            assertEquals(original, configuration.readText())
            assertFalse(manager.hasPendingChange())
        }
    }

    @Test fun pendingChangeFailureDoesNotOverwriteItsRecord() = runTest {
        withTemporaryHome { home ->
            File(home, ".lynmusic").mkdirs()
            val configuration = File(home, ".lynmusic-location.properties")
            writeConfiguration(configuration, "pending_source_root" to File(home, ".lynmusic").absolutePath)
            val original = configuration.readText()
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = assertFailsWith<UiTextArgumentException> {
                manager.scheduleChange(File(home, "LynMusic"), AppDataLocationChangeMode.Migrate)
            }

            assertDescriptions(
                error.uiErrorText(),
                "A data location change is pending. Reopen the app to complete it first.",
                "已有待处理的数据位置切换，请先重新打开应用完成切换。",
                "已有待處理的資料位置切換，請先重新開啟應用程式完成切換。",
            )
            assertEquals(original, configuration.readText())
            assertTrue(manager.hasPendingChange())
        }
    }

    @Test fun pendingCleanupFailurePreservesItsRecordAndDirectory() = runTest {
        withTemporaryHome { home ->
            File(home, ".lynmusic").mkdirs()
            val cleanup = File(home, "old/LynMusic").apply { mkdirs() }
            val keep = cleanup.resolve("keep.txt").apply { writeText("keep") }
            val configuration = File(home, ".lynmusic-location.properties")
            writeConfiguration(
                configuration,
                "cleanup_root" to cleanup.absolutePath,
                "cleanup_root_id" to "00000000-0000-0000-0000-000000000001",
                "cleanup_operation_id" to "00000000-0000-0000-0000-000000000002",
            )
            val original = configuration.readText()
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = assertFailsWith<UiTextArgumentException> {
                manager.scheduleChange(File(home, "LynMusic"), AppDataLocationChangeMode.Migrate)
            }

            assertDescriptions(
                error.uiErrorText(),
                "The old data directory has not been fully cleaned up. Retry cleanup first.",
                "旧数据目录尚未清理完成，请先重试清理。",
                "舊資料目錄尚未清理完成，請先重試清理。",
            )
            assertEquals(original, configuration.readText())
            assertEquals("keep", keep.readText())
            assertFalse(manager.hasPendingChange())
        }
    }

    @Test fun unsupportedPlatformFailureKeepsItsExceptionType() = runTest {
        withTemporaryHome { home ->
            val manager = JvmDataLocationManager(home, "Linux")
            val error = assertFailsWith<UiTextException> {
                manager.scheduleChange(File(home, "LynMusic"), AppDataLocationChangeMode.Migrate)
            }

            assertIs<IllegalStateException>(error)
            assertDescriptions(
                error.uiErrorText(),
                "Changing the data location is not supported on this platform.",
                "当前平台暂不支持修改数据位置。",
                "目前平台暫不支援變更資料位置。",
            )
            assertFalse(File(home, ".lynmusic-location.properties").exists())
        }
    }

    private suspend fun assertDescriptions(text: UiText, english: String, simplified: String, traditional: String) {
        for (language in listOf(AppLanguage.English, AppLanguage.TraditionalChinese, AppLanguage.SimplifiedChinese, AppLanguage.English)) {
            val expected = when (language) {
                AppLanguage.SimplifiedChinese -> simplified
                AppLanguage.TraditionalChinese -> traditional
                else -> english
            }
            assertEquals(expected, resolveUiText(text, language))
        }
    }

    private fun writeConfiguration(file: File, vararg entries: Pair<String, String>) {
        val properties = Properties().apply { entries.forEach { (key, value) -> setProperty(key, value) } }
        file.outputStream().use { properties.store(it, "test") }
    }

    private inline fun withTemporaryHome(block: (File) -> Unit) {
        val home = Files.createTempDirectory("lynmusic-location-ui-test-").toFile()
        try { block(home) } finally { home.deleteRecursively() }
    }
}
