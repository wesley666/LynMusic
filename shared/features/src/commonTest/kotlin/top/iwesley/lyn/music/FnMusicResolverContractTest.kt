package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.DiagnosticLogger

class FnMusicResolverContractTest {
    @Test
    fun `a failing FN Music resolution answers nothing and is logged`() = runTest {
        val logger = RecordingLogger()

        val result = resolveFnMusicOrNull<List<String>>(logger, "cover-candidates", "fn-locator") {
            throw IllegalStateException("Unable to resolve host \"5ddd.com\"")
        }

        assertNull(result)
        assertEquals(DiagnosticLogLevel.WARN, logger.entries.single().first)
        assertTrue(logger.entries.single().second.contains("cover-candidates-failed"))
    }

    @Test
    fun `a successful resolution passes through and cancellation still propagates`() = runTest {
        val logger = RecordingLogger()

        assertEquals(listOf("a"), resolveFnMusicOrNull(logger, "op", "fn-locator") { listOf("a") })
        assertFailsWith<CancellationException> {
            resolveFnMusicOrNull<String>(logger, "op", "fn-locator") { throw CancellationException("left the screen") }
        }
        assertTrue(logger.entries.isEmpty())
    }
}

private class RecordingLogger : DiagnosticLogger {
    val entries = mutableListOf<Pair<DiagnosticLogLevel, String>>()

    override fun log(level: DiagnosticLogLevel, tag: String, message: String, throwable: Throwable?) {
        entries += level to message
    }
}
