package top.iwesley.lyn.music.core.model

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GlobalDiagnosticLoggerTest {
    @AfterTest
    fun tearDown() {
        GlobalDiagnosticLogger.resetStrategy()
    }

    @Test
    fun `default strategy is console logger`() {
        assertIs<ConsoleDiagnosticLogger>(GlobalDiagnosticLogger.strategy)
    }

    @Test
    fun `installed strategy receives delegated log events`() {
        val recordingLogger = RecordingDiagnosticLogger()

        GlobalDiagnosticLogger.installStrategy(recordingLogger)
        GlobalDiagnosticLogger.debug("LayoutProfile") { "hello" }

        assertEquals(
            listOf(
                RecordedLog(
                    level = DiagnosticLogLevel.DEBUG,
                    tag = "LayoutProfile",
                    message = "hello",
                    throwable = null,
                ),
            ),
            recordingLogger.logs,
        )
    }

    @Test
    fun `reset strategy restores console logger`() {
        GlobalDiagnosticLogger.installStrategy(RecordingDiagnosticLogger())

        GlobalDiagnosticLogger.resetStrategy()

        assertIs<ConsoleDiagnosticLogger>(GlobalDiagnosticLogger.strategy)
    }
}

private class RecordingDiagnosticLogger : DiagnosticLogger {
    val logs = mutableListOf<RecordedLog>()

    override fun log(
        level: DiagnosticLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        logs += RecordedLog(level, tag, message, throwable)
    }
}

private data class RecordedLog(
    val level: DiagnosticLogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
)
