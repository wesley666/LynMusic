package top.iwesley.lyn.music.automotive

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class LynMusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (isCrashReportProcess()) {
            return
        }
        installAndroidUncaughtExceptionHandler(applicationContext)
    }

    private fun isCrashReportProcess(): Boolean {
        val processName = resolveCurrentProcessName(applicationContext) ?: return false
        return processName == "$packageName:crash"
    }
}

private fun installAndroidUncaughtExceptionHandler(context: Context) {
    Thread.setDefaultUncaughtExceptionHandler(
        AndroidUncaughtExceptionHandler(context.applicationContext),
    )
}

private class AndroidUncaughtExceptionHandler(
    private val context: Context,
) : Thread.UncaughtExceptionHandler {
    private val isHandlingCrash = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (isHandlingCrash.compareAndSet(false, true)) {
            val report = formatAndroidCrashReport(threadName = thread.name, throwable = throwable)
            runCatching {
                context.startActivity(
                    Intent(context, CrashReportActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra(EXTRA_ANDROID_CRASH_REPORT, report)
                    },
                )
            }.onFailure { launchFailure ->
                Log.e(ANDROID_CRASH_LOG_TAG, "Failed to launch crash report activity.", launchFailure)
            }
        } else {
            Log.e(ANDROID_CRASH_LOG_TAG, "Unhandled exception while crash reporter was already active.", throwable)
        }

        Process.killProcess(Process.myPid())
        exitProcess(ANDROID_CRASH_EXIT_CODE)
    }
}

internal fun formatAndroidCrashReport(
    threadName: String,
    throwable: Throwable,
    maxChars: Int = MAX_ANDROID_CRASH_REPORT_CHARS,
): String {
    val writer = StringWriter()
    PrintWriter(writer).use { printer ->
        printer.println("LynMusic Android Crash")
        printer.println("Thread: $threadName")
        printer.println("Exception: ${throwable.javaClass.name}")
        throwable.message?.takeIf { it.isNotBlank() }?.let { message ->
            printer.println("Message: $message")
        }
        printer.println()
        throwable.printStackTrace(printer)
    }
    return truncateAndroidCrashReport(writer.toString(), maxChars)
}

private fun truncateAndroidCrashReport(report: String, maxChars: Int): String {
    if (report.length <= maxChars) return report
    val suffix = "\n\n[crash report truncated]\n"
    return if (maxChars <= suffix.length) {
        suffix.take(maxChars)
    } else {
        report.take(maxChars - suffix.length) + suffix
    }
}

private fun resolveCurrentProcessName(context: Context): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Application.getProcessName()?.takeIf { it.isNotBlank() }?.let { processName ->
            return processName
        }
    }
    val currentPid = Process.myPid()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return activityManager
        ?.runningAppProcesses
        ?.firstOrNull { processInfo -> processInfo.pid == currentPid }
        ?.processName
}

internal const val EXTRA_ANDROID_CRASH_REPORT = "top.iwesley.lyn.music.automotive.extra.CRASH_REPORT"

private const val ANDROID_CRASH_LOG_TAG = "LynMusic"
private const val ANDROID_CRASH_EXIT_CODE = 10
private const val MAX_ANDROID_CRASH_REPORT_CHARS = 120_000
