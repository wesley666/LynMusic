package top.iwesley.lyn.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger

class AppResourceDisposerTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `best effort close attempts every resource and aggregates failures`() = runBlocking {
        val calls = mutableListOf<String>()

        val result = runCatching {
            closeAppResourcesBestEffort(
                logger = NoopDiagnosticLogger,
                resources = listOf(
                    AppCloseAction("first") {
                        calls += "first"
                        error("first failed")
                    },
                    AppCloseAction("database") { calls += "database" },
                    AppCloseAction("last") {
                        calls += "last"
                        error("last failed")
                    },
                ),
            )
        }

        assertTrue(result.isFailure)
        assertEquals(listOf("first", "database", "last"), calls)
        assertEquals(1, result.exceptionOrNull()?.suppressedExceptions?.size)
    }

    @Test
    fun `logger failure does not skip later resource closes`() = runBlocking {
        val calls = mutableListOf<String>()
        val closeFailure = IllegalStateException("resource failed")
        val loggingFailure = IllegalStateException("logger failed")
        val throwingLogger = object : DiagnosticLogger {
            override fun log(
                level: DiagnosticLogLevel,
                tag: String,
                message: String,
                throwable: Throwable?,
            ) {
                throw loggingFailure
            }
        }

        val result = runCatching {
            closeAppResourcesBestEffort(
                logger = throwingLogger,
                resources = listOf(
                    AppCloseAction("failing") {
                        calls += "failing"
                        throw closeFailure
                    },
                    AppCloseAction("http") { calls += "http" },
                    AppCloseAction("database") { calls += "database" },
                ),
            )
        }

        assertTrue(result.isFailure)
        assertEquals(listOf("failing", "http", "database"), calls)
        assertSame(closeFailure, result.exceptionOrNull())
        assertTrue(closeFailure.suppressedExceptions.contains(loggingFailure))
    }

    @Test
    fun `repeated resource failure instance does not self suppress`() = runBlocking {
        val calls = mutableListOf<String>()
        val sharedFailure = IllegalStateException("shared failure")

        val result = runCatching {
            closeAppResourcesBestEffort(
                logger = NoopDiagnosticLogger,
                resources = listOf(
                    AppCloseAction("first") {
                        calls += "first"
                        throw sharedFailure
                    },
                    AppCloseAction("database") { calls += "database" },
                    AppCloseAction("last") {
                        calls += "last"
                        throw sharedFailure
                    },
                ),
            )
        }

        assertEquals(listOf("first", "database", "last"), calls)
        assertSame(sharedFailure, result.exceptionOrNull())
        assertTrue(sharedFailure.suppressedExceptions.none { it === sharedFailure })
    }

    @Test
    fun `logger rethrowing resource failure does not self suppress or skip closes`() = runBlocking {
        val calls = mutableListOf<String>()
        val closeFailure = IllegalStateException("resource failed")
        val rethrowingLogger = object : DiagnosticLogger {
            override fun log(
                level: DiagnosticLogLevel,
                tag: String,
                message: String,
                throwable: Throwable?,
            ) {
                throw requireNotNull(throwable)
            }
        }

        val result = runCatching {
            closeAppResourcesBestEffort(
                logger = rethrowingLogger,
                resources = listOf(
                    AppCloseAction("failing") {
                        calls += "failing"
                        throw closeFailure
                    },
                    AppCloseAction("http") { calls += "http" },
                    AppCloseAction("database") { calls += "database" },
                ),
            )
        }

        assertEquals(listOf("failing", "http", "database"), calls)
        assertSame(closeFailure, result.exceptionOrNull())
        assertTrue(closeFailure.suppressedExceptions.none { it === closeFailure })
    }

    @Test
    fun `disposer cancels scope and executes close sequence only once`() {
        val job = SupervisorJob()
        val scope = CoroutineScope(job)
        var closeCalls = 0
        val disposer = AppResourceDisposer(scope, NoopDiagnosticLogger) { closeCalls += 1 }

        val first = disposer.dispose()
        val second = disposer.dispose()

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals(1, closeCalls)
        assertFalse(job.isActive)
    }

    @Test
    fun `concurrent dispose callers wait for and share one result`() {
        val executor = Executors.newFixedThreadPool(2)
        val closeStarted = CountDownLatch(1)
        val allowClose = CountDownLatch(1)
        val closeCalls = AtomicInteger(0)
        val disposer = AppResourceDisposer(
            scope = CoroutineScope(SupervisorJob()),
            logger = NoopDiagnosticLogger,
            onDispose = {
                closeCalls.incrementAndGet()
                closeStarted.countDown()
                withContext(Dispatchers.IO) { allowClose.await() }
            },
        )
        try {
            val first = executor.submit<Result<Unit>> { disposer.dispose() }
            assertTrue(closeStarted.await(1, TimeUnit.SECONDS))
            val second = executor.submit<Result<Unit>> { disposer.dispose() }
            Thread.sleep(50)
            assertFalse(second.isDone)

            allowClose.countDown()
            val firstResult = first.get(1, TimeUnit.SECONDS)
            val secondResult = second.get(1, TimeUnit.SECONDS)

            assertTrue(firstResult.isSuccess)
            assertTrue(secondResult.isSuccess)
            assertEquals(1, closeCalls.get())
        } finally {
            allowClose.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `disposer waits for shared scope children before closing resources`() {
        val executor = Executors.newSingleThreadExecutor()
        val childStarted = CountDownLatch(1)
        val childCleanupStarted = CountDownLatch(1)
        val allowChildCleanup = CountDownLatch(1)
        val calls = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            childStarted.countDown()
            try {
                awaitCancellation()
            } finally {
                childCleanupStarted.countDown()
                withContext(NonCancellable + Dispatchers.IO) {
                    allowChildCleanup.await()
                    synchronized(calls) { calls += "child-finished" }
                }
            }
        }
        assertTrue(childStarted.await(1, TimeUnit.SECONDS))
        val disposer = AppResourceDisposer(scope, NoopDiagnosticLogger) {
            synchronized(calls) { calls += "resources" }
        }
        try {
            val result = executor.submit<Result<Unit>> { disposer.dispose() }
            assertTrue(childCleanupStarted.await(1, TimeUnit.SECONDS))
            Thread.sleep(50)
            assertFalse(result.isDone)

            allowChildCleanup.countDown()
            assertTrue(result.get(1, TimeUnit.SECONDS).isSuccess)
            assertEquals(listOf("child-finished", "resources"), synchronized(calls) { calls.toList() })
        } finally {
            allowChildCleanup.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `scope shutdown timeout still closes owned resources`() {
        val executor = Executors.newSingleThreadExecutor()
        val childStarted = CountDownLatch(1)
        val childCleanupStarted = CountDownLatch(1)
        val allowChildCleanup = CountDownLatch(1)
        val closeCalls = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            childStarted.countDown()
            try {
                awaitCancellation()
            } finally {
                childCleanupStarted.countDown()
                withContext(NonCancellable + Dispatchers.IO) { allowChildCleanup.await() }
            }
        }
        assertTrue(childStarted.await(1, TimeUnit.SECONDS))
        val disposer = AppResourceDisposer(
            scope = scope,
            logger = NoopDiagnosticLogger,
            onDispose = { closeCalls.incrementAndGet() },
            scopeShutdownTimeoutMillis = 50L,
        )
        try {
            val result = executor.submit<Result<Unit>> { disposer.dispose() }
            assertTrue(childCleanupStarted.await(1, TimeUnit.SECONDS))
            val disposeResult = result.get(1, TimeUnit.SECONDS)

            assertTrue(disposeResult.isFailure)
            assertEquals(1, closeCalls.get())
        } finally {
            allowChildCleanup.countDown()
            executor.shutdownNow()
        }
    }
}
