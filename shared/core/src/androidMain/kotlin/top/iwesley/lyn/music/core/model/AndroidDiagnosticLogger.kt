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
        val formattedMessage = "[$label][$tag][${level.name}] $message"
        when (level) {
            DiagnosticLogLevel.DEBUG -> {
                if (throwable != null) Log.d(androidTag, formattedMessage, throwable) else Log.d(androidTag, formattedMessage)
            }

            DiagnosticLogLevel.INFO -> {
                if (throwable != null) Log.i(androidTag, formattedMessage, throwable) else Log.i(androidTag, formattedMessage)
            }

            DiagnosticLogLevel.WARN -> {
                if (throwable != null) Log.w(androidTag, formattedMessage, throwable) else Log.w(androidTag, formattedMessage)
            }

            DiagnosticLogLevel.ERROR -> {
                if (throwable != null) Log.e(androidTag, formattedMessage, throwable) else Log.e(androidTag, formattedMessage)
            }
        }
    }
}

private const val DEFAULT_ANDROID_LOG_TAG = "LynMusic"
private const val MAX_ANDROID_LOG_TAG_LENGTH = 23
