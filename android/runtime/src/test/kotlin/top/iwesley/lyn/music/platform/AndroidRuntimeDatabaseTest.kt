package top.iwesley.lyn.music.platform

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.iwesley.lyn.music.SharedRuntimeServices
import top.iwesley.lyn.music.buildSharedGraph
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 35], application = Application::class)
class AndroidRuntimeDatabaseTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun componentContextsShareOneDatabaseOpenedWithApplicationContext() = withDatabase { database ->
        var openCount = 0
        val provider = AndroidRuntimeDatabaseProvider { appContext ->
            assertSame(context, appContext)
            openCount += 1
            database
        }
        val componentContexts = List(4) { ContextWrapper(context) }

        componentContexts.forEach { componentContext ->
            assertSame(database, provider.getOrCreate(componentContext))
            assertSame(database, provider.getOrCreate(componentContext))
        }

        assertEquals(1, openCount)
    }

    @Test
    fun concurrentComponentsOpenOnlyOneDatabase() = withDatabase { database ->
        val start = CountDownLatch(1)
        val opening = CountDownLatch(1)
        val finishOpening = CountDownLatch(1)
        val openCount = AtomicInteger()
        val provider = AndroidRuntimeDatabaseProvider {
            openCount.incrementAndGet()
            opening.countDown()
            check(finishOpening.await(5, TimeUnit.SECONDS))
            database
        }
        val executor = Executors.newFixedThreadPool(8)

        try {
            val results = List(8) {
                executor.submit<LynMusicDatabase> {
                    check(start.await(5, TimeUnit.SECONDS))
                    provider.getOrCreate(ContextWrapper(context))
                }
            }
            start.countDown()
            assertTrue(opening.await(5, TimeUnit.SECONDS))
            finishOpening.countDown()

            results.forEach { assertSame(database, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, openCount.get())
        } finally {
            finishOpening.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun failedOpenPropagatesAndNextCallCanInitializeDatabase() = withDatabase { database ->
        var openCount = 0
        val failure = IllegalStateException("database initialization failed")
        val provider = AndroidRuntimeDatabaseProvider {
            openCount += 1
            if (openCount == 1) throw failure
            database
        }

        assertSame(failure, assertFailsWith<IllegalStateException> { provider.getOrCreate(context) })
        assertSame(database, provider.getOrCreate(context))
        assertSame(database, provider.getOrCreate(context))
        assertEquals(2, openCount)
        assertQueueCanBeSaved(database, positionMs = 1_000L)
    }

    @Test
    fun componentBuildFailureCleanupKeepsDatabaseAvailableForOtherComponents() = withDatabase { database ->
        var openCount = 0
        val provider = AndroidRuntimeDatabaseProvider {
            openCount += 1
            database
        }
        var cleanupCount = 0
        val first = runtimeGraph(provider.getOrCreate(context)) { cleanupCount += 1 }
        val second = runtimeGraph(provider.getOrCreate(ContextWrapper(context)))

        try {
            assertQueueCanBeSaved(database, positionMs = 1_000L)
            assertTrue(first.disposeAfterComponentBuildFailure().isSuccess)

            assertEquals(1, cleanupCount)
            assertTrue(database.isOpen)
            assertQueueCanBeSaved(second.sharedGraph.database, positionMs = 2_000L)
            assertSame(database, provider.getOrCreate(ContextWrapper(context)))
            assertEquals(1, openCount)
        } finally {
            first.disposeAfterComponentBuildFailure().getOrThrow()
            second.disposeAfterComponentBuildFailure().getOrThrow()
        }
    }

    @Test
    fun resourceCleanupFailureAlsoLeavesBorrowedDatabaseOpen() = withDatabase { database ->
        val failure = IllegalStateException("component resource cleanup failed")
        val graph = runtimeGraph(database) { throw failure }
        assertQueueCanBeSaved(database, positionMs = 1_000L)

        val result = graph.disposeAfterComponentBuildFailure()

        assertSame(failure, result.exceptionOrNull())
        assertTrue(database.isOpen)
        assertQueueCanBeSaved(database, positionMs = 3_000L)
    }

    private fun runtimeGraph(
        database: LynMusicDatabase,
        closeResource: suspend () -> Unit = {},
    ): AndroidRuntimeGraph {
        val preferences = AndroidAppPreferencesStore(context)
        // Store jobs are unnecessary for checking the component's database ownership.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).apply { cancel() }
        return AndroidRuntimeGraph(
            sharedGraph = buildSharedGraph(
                platform = PlatformDescriptor(
                    name = "Android database test",
                    capabilities = PlatformCapabilities(
                        supportsLocalFolderImport = false,
                        supportsSambaImport = false,
                        supportsWebDavImport = false,
                        supportsNavidromeImport = false,
                        supportsSystemMediaControls = false,
                    ),
                ),
                database = database,
                runtimeServices = SharedRuntimeServices(
                    importSourceGateway = unusedService<ImportSourceGateway>(),
                    secureCredentialStore = unusedService<SecureCredentialStore>(),
                    sambaCachePreferencesStore = preferences,
                    themePreferencesStore = preferences,
                    librarySourceFilterPreferencesStore = preferences,
                    lyricsHttpClient = unusedService<LyricsHttpClient>(),
                ),
                scope = scope,
            ),
            playerRuntimeServices = PlayerRuntimeServices(
                playbackPreferencesStore = preferences,
                closeDesktopResources = {
                    preferences.close()
                    closeResource()
                },
            ),
        )
    }

    private fun assertQueueCanBeSaved(database: LynMusicDatabase, positionMs: Long) = runBlocking {
        val snapshot = PlaybackQueueSnapshotEntity(
            queueTrackIds = "track-1",
            currentIndex = 0,
            positionMs = positionMs,
            mode = "ORDER",
            updatedAt = positionMs,
        )
        database.playbackQueueSnapshotDao().upsert(snapshot)
        assertEquals(snapshot, database.playbackQueueSnapshotDao().get())
    }

    private fun withDatabase(block: (LynMusicDatabase) -> Unit) {
        val databaseName = "runtime-database-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder<LynMusicDatabase>(context, databaseName)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            block(database)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private inline fun <reified T> unusedService(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            error("Unexpected call to ${T::class.java.simpleName}.${method.name}")
        } as T
}
