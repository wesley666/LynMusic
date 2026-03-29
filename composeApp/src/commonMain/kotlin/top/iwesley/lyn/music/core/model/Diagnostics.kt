package top.iwesley.lyn.music.core.model

enum class DiagnosticLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

interface DiagnosticLogger {
    fun log(
        level: DiagnosticLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}

object NoopDiagnosticLogger : DiagnosticLogger {
    override fun log(
        level: DiagnosticLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = Unit
}

class ConsoleDiagnosticLogger(
    private val enabled: Boolean,
    private val label: String,
) : DiagnosticLogger {
    override fun log(
        level: DiagnosticLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (!enabled) return
        val prefix = "[$label][$tag][${level.name}]"
        println("$prefix $message")
        throwable?.message?.takeIf { it.isNotBlank() }?.let { detail ->
            println("$prefix cause=$detail")
        }
    }
}

inline fun DiagnosticLogger.debug(tag: String, message: () -> String) {
    log(DiagnosticLogLevel.DEBUG, tag, message())
}

inline fun DiagnosticLogger.info(tag: String, message: () -> String) {
    log(DiagnosticLogLevel.INFO, tag, message())
}

inline fun DiagnosticLogger.warn(tag: String, message: () -> String) {
    log(DiagnosticLogLevel.WARN, tag, message())
}

inline fun DiagnosticLogger.error(tag: String, throwable: Throwable? = null, message: () -> String) {
    log(DiagnosticLogLevel.ERROR, tag, message(), throwable)
}
