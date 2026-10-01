package top.iwesley.lyn.music.platform

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.compose.resources.StringResource
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.resolveCurrentUiText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.resources.*

/** Emergency labels must remain available when the resource assets cannot be read. */
enum class AndroidCrashReportText(
    val resource: StringResource,
    private val english: String,
    private val simplifiedChinese: String,
    private val traditionalChinese: String,
) {
    Title(
        Res.string.crash_unhandled_exception_title,
        "LynMusic encountered an unhandled exception",
        "LynMusic 遇到未捕获异常",
        "LynMusic 遇到未捕獲異常",
    ),
    Instructions(
        Res.string.crash_report_instructions,
        "The main app process has stopped. Copy the stack trace below and send it to the developer for investigation.",
        "应用主进程已停止。你可以复制下面的崩溃堆栈给开发者用于排查。",
        "應用主進程已停止。你可以複製下面的崩潰堆棧給開發者用於排查。",
    ),
    MissingStackTrace(
        Res.string.crash_stack_trace_missing,
        "No crash stack trace received.",
        "未收到崩溃堆栈。",
        "未收到崩潰堆棧。",
    ),
    CopyStackTrace(
        Res.string.crash_copy_stack_trace,
        "Copy stack trace",
        "复制堆栈",
        "複製堆棧",
    ),
    Close(
        Res.string.common_close,
        "Close",
        "关闭",
        "關閉",
    ),
    StackTraceCopied(
        Res.string.crash_stack_trace_copied,
        "Crash stack trace copied",
        "已复制崩溃堆栈",
        "已複製崩潰堆棧",
    );

    fun fallbackText(language: AppLanguage = AppLanguageRuntime.effectiveLanguage.value): String = when (language) {
        AppLanguage.SimplifiedChinese -> simplifiedChinese
        AppLanguage.TraditionalChinese -> traditionalChinese
        AppLanguage.English, AppLanguage.System -> english
    }

    /** Observers pass their captured language; events omit it to use the latest language revision. */
    suspend fun resolve(language: AppLanguage? = null): String = try {
        val description = uiText(resource)
        if (language == null) resolveCurrentUiText(description) else resolveUiText(description, language)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        currentCoroutineContext().ensureActive()
        Log.e("CrashReport", "Failed to resolve crash report label: $name", error)
        fallbackText(language ?: AppLanguageRuntime.effectiveLanguage.value)
    }
}
