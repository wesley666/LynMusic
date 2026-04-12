package top.iwesley.lyn.music.core.model

import kotlin.concurrent.Volatile

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
    private val enabled: Boolean = true,
    private val label: String = DEFAULT_DIAGNOSTIC_LOGGER_LABEL,
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

/**
 * Global logger facade.
 * Strategy can be replaced at runtime while callers continue to target [DiagnosticLogger].
 */
object GlobalDiagnosticLogger : DiagnosticLogger {
    @Volatile
    private var delegate: DiagnosticLogger = ConsoleDiagnosticLogger()

    val strategy: DiagnosticLogger
        get() = delegate

    fun installStrategy(strategy: DiagnosticLogger) {
        require(strategy !== this) { "GlobalDiagnosticLogger cannot delegate to itself." }
        delegate = strategy
    }

    fun useConsoleStrategy(
        enabled: Boolean = true,
        label: String = DEFAULT_DIAGNOSTIC_LOGGER_LABEL,
    ) {
        delegate = ConsoleDiagnosticLogger(enabled = enabled, label = label)
    }

    fun resetStrategy() {
        delegate = ConsoleDiagnosticLogger()
    }

    override fun log(
        level: DiagnosticLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        delegate.log(level, tag, message, throwable)
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

private const val DEFAULT_DIAGNOSTIC_LOGGER_LABEL = "LynMusic"
