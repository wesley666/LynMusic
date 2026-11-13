package top.iwesley.lyn.music.platform

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.security.KeyStore
import java.security.Provider
import java.security.Security
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [27, 35],
    application = Application::class,
    shadows = [HostBundledSQLiteDriver::class],
)
@LooperMode(LooperMode.Mode.PAUSED)
class AndroidRuntimeDatabaseLifecycleTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    @OptIn(ExperimentalResourceApi::class)
    fun initializeProcess() {
        val hostKeyStore = KeyStore.getInstance("JCEKS")
        val provider = object : Provider(KEY_STORE_PROVIDER_NAME, 1.0, "Host key store for lifecycle tests") {}
        provider.put("KeyStore.AndroidKeyStore", hostKeyStore.provider.getService("KeyStore", "JCEKS").className)
        Security.addProvider(provider)
        shadowOf(context as Application).grantPermissions(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        setResourceReaderAndroidContext(context)
        clearProcessDatabase()
        initializeAndroidAppLanguage(context)
    }

    @After
    fun finishProcess() {
        try {
            clearProcessDatabase()
        } finally {
            Security.removeProvider(KEY_STORE_PROVIDER_NAME)
        }
    }

    @Test
    fun realEntrySharesDatabaseWithActivityAndBothServices() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create()
        try {
            val database = openAndroidRuntimeDatabase(activity.get())
            withServices { playback, lyrics ->
                assertSame(database, openAndroidRuntimeDatabase(context))
                assertSame(database, playbackDatabase(playback))
                assertSame(database, lyricsDatabase(lyrics))
                assertQueueCanBeSaved(database, positionMs = 1_000L)
            }
        } finally {
            activity.destroy()
        }
    }

    @Test
    fun concurrentRealEntryCallsShareOneDatabase() {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = List(8) {
                executor.submit<LynMusicDatabase> {
                    check(start.await(10, TimeUnit.SECONDS))
                    openAndroidRuntimeDatabase(context)
                }
            }
            start.countDown()
            val databases = results.map { it.get(10, TimeUnit.SECONDS) }

            databases.forEach { assertSame(databases.first(), it) }
            assertSame(databases.first(), openAndroidRuntimeDatabase(context))
            assertQueueCanBeSaved(databases.first(), positionMs = 2_000L)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun actualOpenFailureDoesNotCacheFailedDatabase() {
        val file = context.getDatabasePath("lynmusic.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.version = 22
        }

        assertTrue(runCatching { openAndroidRuntimeDatabase(context) }.isFailure)
        assertNull(cachedDatabase())
        assertTrue(context.deleteDatabase("lynmusic.db"))

        val recovered = openAndroidRuntimeDatabase(context)
        assertSame(recovered, openAndroidRuntimeDatabase(context))
        assertQueueCanBeSaved(recovered, positionMs = 3_000L)
    }

    @Test
    fun realGraphBuildFailureKeepsPreviouslySharedDatabaseUsable() {
        val database = openAndroidRuntimeDatabase(context)
        val failure = IllegalStateException("activity actions unavailable")
        val actions = object : AndroidActivityActions by MutableAndroidActivityActions() {
            override val activityResumedEvents: kotlinx.coroutines.flow.Flow<Unit>
                get() = throw failure
        }

        assertSame(failure, assertFailsWith<IllegalStateException> {
            createAndroidRuntimeGraph(context, actions)
        })
        assertSame(database, openAndroidRuntimeDatabase(context))
        assertQueueCanBeSaved(database, positionMs = 4_000L)
    }

    @Test
    fun realGraphsReuseDatabaseAfterComponentBuildFailureCleanup() {
        val first = createAndroidRuntimeGraph(context, MutableAndroidActivityActions())
        val database = first.sharedGraph.database
        try {
            assertSame(database, openAndroidRuntimeDatabase(context))
            assertQueueCanBeSaved(database, positionMs = 5_000L)
        } finally {
            first.disposeAfterComponentBuildFailure().getOrThrow()
        }
        assertQueueCanBeSaved(database, positionMs = 6_000L)

        val recreated = createAndroidRuntimeGraph(context, MutableAndroidActivityActions())
        try {
            assertSame(database, recreated.sharedGraph.database)
            assertQueueCanBeSaved(recreated.sharedGraph.database, positionMs = 7_000L)
        } finally {
            recreated.disposeAfterComponentBuildFailure().getOrThrow()
        }
    }

    @Test
    fun realServiceDestroyAndRecreateKeepSharedDatabaseAndSavedQueue() {
        val database = openAndroidRuntimeDatabase(context)
        withServices { playback, lyrics ->
            assertSame(database, playbackDatabase(playback))
            assertSame(database, lyricsDatabase(lyrics))
            assertQueueCanBeSaved(database, positionMs = 8_000L)
        }
        assertNull(AndroidPlaybackRuntimeRegistry.repository.value)
        assertEquals(8_000L, runBlocking { database.playbackQueueSnapshotDao().get()?.positionMs })
        assertQueueCanBeSaved(database, positionMs = 9_000L)

        withServices { recreatedPlayback, recreatedLyrics ->
            assertSame(database, playbackDatabase(recreatedPlayback))
            assertSame(database, lyricsDatabase(recreatedLyrics))
            assertSame(database, openAndroidRuntimeDatabase(context))
            assertEquals(9_000L, runBlocking { database.playbackQueueSnapshotDao().get()?.positionMs })
            assertQueueCanBeSaved(database, positionMs = 10_000L)
        }
        assertQueueCanBeSaved(database, positionMs = 11_000L)
    }

    private fun playbackDatabase(service: LynPlaybackMediaLibraryService): LynMusicDatabase {
        val runtime = assertNotNull(ReflectionHelpers.getField<Any?>(service, "runtime"))
        return ReflectionHelpers.getField(runtime, "database")
    }

    private fun withServices(block: (LynPlaybackMediaLibraryService, AndroidDesktopLyricsOverlayService) -> Unit) {
        val playback = Robolectric.buildService(LynPlaybackMediaLibraryService::class.java).create()
        try {
            val lyrics = Robolectric.buildService(AndroidDesktopLyricsOverlayService::class.java).create()
            try {
                block(playback.get(), lyrics.get())
            } finally {
                lyrics.destroy()
            }
        } finally {
            playback.destroy()
        }
    }

    private fun lyricsDatabase(service: AndroidDesktopLyricsOverlayService): LynMusicDatabase {
        val repository = ReflectionHelpers.getField<Any>(service, "lyricsRepository")
        return ReflectionHelpers.getField(repository, "database")
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

    private fun processProvider(): Any = ReflectionHelpers.getStaticField(
        Class.forName("top.iwesley.lyn.music.platform.AndroidPlatformKt"),
        "processDatabaseProvider",
    )

    private fun cachedDatabase(): LynMusicDatabase? =
        ReflectionHelpers.getField(processProvider(), "database")

    private fun clearProcessDatabase() {
        // Reset the simulated process only after all component controllers have been destroyed.
        cachedDatabase()?.close()
        ReflectionHelpers.setField(processProvider(), "database", null)
        context.deleteDatabase("lynmusic.db")
    }

    private companion object {
        const val KEY_STORE_PROVIDER_NAME = "LynMusicTestAndroidKeyStore"
    }
}

/** Uses Robolectric's SQLite implementation while keeping the real Room builder and app entrypoints. */
@Implements(value = BundledSQLiteDriver::class, isInAndroidSdk = false)
class HostBundledSQLiteDriver {
    @Implementation
    fun hasConnectionPool(): Boolean = true

    @Implementation
    fun open(fileName: String): SQLiteConnection = AndroidSQLiteDriver().open(fileName)
}
