package top.iwesley.lyn.music.core.model

import android.util.Log

class AndroidDiagnosticLogger(
    private val enabled: Boolean = true,
    private val label: String = "Android",
) : DiagnosticLogger {
    override fun log(
        level: DiagnosticLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (!enabled) return
        val androidTag = DEFAULT_ANDROID_LOG_TAG //tag.take(MAX_ANDROID_LOG_TAG_LENGTH).ifBlank { DEFAULT_ANDROID_LOG_TAG }
        // Log lines and exception messages can quote remote stream URLs; never write their credentials.
        // The stack trace is rendered here so its messages are redacted too.
        val formattedMessage = redactRemoteSourceUrlForLog(
            buildString {
                append("[$label][$tag][${level.name}] $message")
                throwable?.let { append('\n').append(Log.getStackTraceString(it)) }
            },
        )
        when (level) {
            DiagnosticLogLevel.DEBUG -> Log.d(androidTag, formattedMessage)
            DiagnosticLogLevel.INFO -> Log.i(androidTag, formattedMessage)
            DiagnosticLogLevel.WARN -> Log.w(androidTag, formattedMessage)
            DiagnosticLogLevel.ERROR -> Log.e(androidTag, formattedMessage)
        }
    }
}

private const val DEFAULT_ANDROID_LOG_TAG = "LynMusic"
private const val MAX_ANDROID_LOG_TAG_LENGTH = 23
