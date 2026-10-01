package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.resources.*

class ImportScanStoredTextTest {
    private val languages = listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)

    @Test fun mixedWarningsAreRenderedAtSaveTimeAndMissingDescriptionsUseOriginalText() = runTest {
        val previous = AppLanguageRuntime.appLanguage.value
        try {
            val description = uiText(Res.string.source_account_no_syncable_tracks, "原始来源名")
            val warnings = listOf(
                ImportScanWarning("应用诊断", description),
                ImportScanWarning("第三方原文 %1\$s "),
            )
            for (language in languages) {
                AppLanguageRuntime.update(language)
                val saved = assertNotNull(scanWarningsForStorage(warnings))
                assertEquals(resolveUiText(description, language) + "\n" + warnings[1].diagnostic, saved)
                for (next in languages) {
                    AppLanguageRuntime.update(next)
                    assertEquals(saved, resolveUiText(UiText.Raw(saved), next))
                }
            }
            val rawWarnings = warnings.map { it.copy(text = null) }
            assertEquals(warnings.joinToString("\n") { it.diagnostic }, scanWarningsForStorage(rawWarnings))
            assertNull(scanWarningsForStorage(emptyList()))
            assertNull(scanWarningsForStorage(listOf(ImportScanWarning(" ", description), ImportScanWarning(""))))
        } finally { AppLanguageRuntime.update(previous) }
    }

    @Test fun reorderingAddingAndRemovingWarningsKeepsDescriptionsWithTheirDiagnostics() = runTest {
        val previous = AppLanguageRuntime.appLanguage.value
        try {
            val accountText = uiText(Res.string.source_account_no_syncable_tracks, "用户来源 %1\$s")
            val folderText = uiText(Res.string.source_folder_missing, "/用户目录/100%")
            val account = ImportScanWarning("账号原始诊断", accountText)
            val folder = ImportScanWarning("目录原始诊断", folderText)
            val raw = ImportScanWarning("服务端中文原文 %1\$s ")
            val inserted = ImportScanWarning("新增服务端原文")
            val report = ImportScanReport(emptyList(), warnings = listOf(account, folder, raw))
            for (language in languages) {
                AppLanguageRuntime.update(language)
                val accountMessage = resolveUiText(accountText, language)
                val folderMessage = resolveUiText(folderText, language)
                val cases = listOf(
                    report to listOf(accountMessage, folderMessage, raw.diagnostic),
                    report.copy(warnings = listOf(raw, account, folder)) to listOf(raw.diagnostic, accountMessage, folderMessage),
                    report.copy(warnings = listOf(account, inserted, folder, raw)) to listOf(accountMessage, inserted.diagnostic, folderMessage, raw.diagnostic),
                    report.copy(warnings = listOf(folder, raw)) to listOf(folderMessage, raw.diagnostic),
                )
                for ((changed, expected) in cases) {
                    assertEquals(expected.joinToString("\n"), scanWarningsForStorage(changed.warnings))
                    assertEquals(0, changed.discoveredAudioFileCount)
                    assertTrue(changed.tracks.isEmpty())
                }
            }
            assertEquals("账号原始诊断", account.diagnostic)
            assertEquals("目录原始诊断", folder.diagnostic)
        } finally { AppLanguageRuntime.update(previous) }
    }

    @Test fun wrappedErrorsNestedArgumentsAndExplicitPluralQuantitiesAreSavedAsCompleteText() = runTest {
        val previous = AppLanguageRuntime.appLanguage.value
        try {
            val path = "/用户路径/music %1\$s\nfolder"
            val descriptions = buildList {
                add(uiText(Res.string.server_request_failed, "用户来源", "scan", uiText(Res.string.source_folder_missing, path)))
                add(uiText(Res.string.source_folder_missing, null))
                add(UiText.Raw("服务端原文\n%1\$s "))
                for (quantity in 0..2) {
                    add(UiText.Joined(
                        listOf(uiPlural(Res.plurals.tv_search_result_track_count, quantity, "用户查询", quantity), UiText.Raw("第三方 100% ")),
                        separatorText = uiText(Res.string.common_close),
                    ))
                }
            }
            for (text in descriptions) for (language in languages) {
                AppLanguageRuntime.update(language)
                val original = UiTextException(text, diagnosticMessage = "explicit 原始诊断")
                val wrapped = IllegalStateException("wrapper 原文", original)
                val saved = scanErrorMessageForStorage(wrapped)
                assertEquals(resolveUiText(text, language), saved)
                assertEquals("explicit 原始诊断", original.message)
                assertSame(original, wrapped.cause)
                assertEquals(UiText.Raw(saved), ImportIndexState("source", 7, lastError = saved).lastErrorUiText())
            }
        } finally { AppLanguageRuntime.update(previous) }
    }

    @Test fun ordinaryAndHistoricalTextIsNotInterpretedAsResourceKeys() = runTest {
        for (raw in listOf("历史中文诊断 %1\$s", "source_folder_missing(arg1=/old)", "lynmusic:scan-error:invalid")) {
            assertEquals(raw, scanErrorMessageForStorage(IllegalStateException(raw)))
            assertEquals(UiText.Raw(raw), ImportIndexState("source", 0, lastError = raw).lastErrorUiText())
        }
        assertNull(ImportIndexState("source", 0).lastErrorUiText())
    }

    @Test fun emptyMessagesUseLocalizedScanFailureAndResolutionFailuresDoNotPersistKeys() = runTest {
        val previous = AppLanguageRuntime.appLanguage.value
        try {
            for (language in languages) {
                AppLanguageRuntime.update(language)
                for (error in listOf(IllegalStateException(), IllegalStateException(""), IllegalStateException(" "))) {
                    assertEquals(resolveUiText(uiText(Res.string.source_scan_failed_status), language), scanErrorMessageForStorage(error))
                }
            }
            // The template requires two arguments; a failed parse must never persist its key.
            val broken = uiText(Res.string.ui_error_with_context, "one argument")
            assertEquals("Scan failed.", scanErrorMessageForStorage(UiTextException(broken)))
            val warnings = listOf(
                ImportScanWarning("有效警告原始诊断", uiText(Res.string.common_read_failed)),
                ImportScanWarning("原始扫描说明", broken),
                ImportScanWarning("第三方 原文"),
            )
            assertEquals(warnings.joinToString("\n") { it.diagnostic }, scanWarningsForStorage(warnings))
        } finally { AppLanguageRuntime.update(previous) }
    }
}
