package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppDataLocationChangeMode
import top.iwesley.lyn.music.platform.JvmDataLocationManager
import top.iwesley.lyn.music.platform.JvmAppInstanceLock
import top.iwesley.lyn.music.platform.isJvmWindowsOs
import top.iwesley.lyn.music.platform.safeDeleteOwnedTree
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.OfflineDownloadEntity
import top.iwesley.lyn.music.data.db.openLynMusicDatabase

class JvmDataLocationManagerTest {
    @Test
    fun `startup data location operation is disabled outside windows`() = runTest {
        withTemporaryHome { home ->
            updateLocationProperty(home, "pending_source_root", File(home, ".lynmusic").absolutePath)

            assertFalse(JvmDataLocationManager(home, "Mac OS X").requiresStartupDataLocationOperation())
            assertFalse(JvmDataLocationManager(home, "Linux").requiresStartupDataLocationOperation())
        }
    }

    @Test
    fun `windows starts synchronously without pending migration or cleanup`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "custom/LynMusic").apply { mkdirs() }
            File(active, ".lynmusic-data-root").writeText("owned")
            updateLocationProperty(home, "active_data_root", active.absolutePath)
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertFalse(manager.requiresStartupDataLocationOperation())
            assertEquals(active.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `windows uses startup data location operation for pending migration or cleanup`() = runTest {
        withTemporaryHome { home ->
            updateLocationProperty(home, "pending_source_root", File(home, ".lynmusic").absolutePath)
            assertTrue(
                JvmDataLocationManager(home, "Windows 11").requiresStartupDataLocationOperation(),
            )

            File(home, ".lynmusic-location.properties").delete()
            updateLocationProperty(home, "cleanup_root", File(home, ".lynmusic.cleanup").absolutePath)
            assertTrue(
                JvmDataLocationManager(home, "Windows 11").requiresStartupDataLocationOperation(),
            )
        }
    }

    @Test
    fun `missing or invalid config uses default root`() = runTest {
        withTemporaryHome { home ->
            val manager = JvmDataLocationManager(home, "Windows 11")
            assertEquals(File(home, ".lynmusic").canonicalFile, manager.currentRootDirectory().canonicalFile)

            File(home, ".lynmusic-location.properties").writeText("active_data_root=relative/path\n")
            assertEquals(File(home, ".lynmusic").canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `corrupt location config fails closed without opening default root`() = runTest {
        withTemporaryHome { home ->
            File(home, ".lynmusic-location.properties").writeText(
                "active_data_root=" + '\\' + "u12G4\n",
            )
            val manager = JvmDataLocationManager(home, "Windows 11")

            val startupError = assertFailsWith<IllegalStateException> {
                manager.requiresStartupDataLocationOperation()
            }
            assertTrue(startupError.message.orEmpty().contains("无法读取数据位置配置"))
            assertFailsWith<IllegalStateException> { manager.currentRootDirectory() }
            assertFalse(File(home, ".lynmusic").exists())
        }
    }

    @Test
    fun `location config path must be a regular file`() = runTest {
        withTemporaryHome { home ->
            File(home, ".lynmusic-location.properties").mkdirs()
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = assertFailsWith<IllegalStateException> {
                manager.requiresStartupDataLocationOperation()
            }
            assertTrue(error.message.orEmpty().contains("不是普通文件"))
            assertFalse(File(home, ".lynmusic").exists())
        }
    }

    @Test
    fun `valid owned custom active root is accepted`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "custom/LynMusic").apply { mkdirs() }
            File(active, ".lynmusic-data-root").writeText("owned")
            updateLocationProperty(home, "active_data_root", active.absolutePath)
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertEquals(active.canonicalFile, manager.applyPendingChange().getOrThrow().canonicalFile)
        }
    }

    @Test
    fun `custom active root without ownership marker fails before startup`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "unowned/LynMusic").apply { mkdirs() }
            File(active, "keep.txt").writeText("external")
            updateLocationProperty(home, "active_data_root", active.absolutePath)
            val manager = JvmDataLocationManager(home, "Windows 11")

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("data_location_current_unowned"))
            assertEquals("external", File(active, "keep.txt").readText())
            assertFalse(File(home, ".lynmusic").exists())
        }
    }

    @Test
    fun `missing custom active root fails instead of falling back to default`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "missing/LynMusic")
            updateLocationProperty(home, "active_data_root", active.absolutePath)
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertTrue(manager.applyPendingChange().isFailure)
            assertFalse(File(home, ".lynmusic").exists())
        }
    }

    @Test
    fun `linked custom active root is rejected`() = runTest {
        withTemporaryHome { home ->
            val external = File(home, "external-data").apply { mkdirs() }
            File(external, ".lynmusic-data-root").writeText("owned")
            File(external, "keep.txt").writeText("external")
            val active = File(File(home, "linked").apply { mkdirs() }, "LynMusic")
            if (!createSymbolicLinkOrSkip(active.toPath(), external.toPath())) return@withTemporaryHome
            updateLocationProperty(home, "active_data_root", active.absolutePath)
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertTrue(manager.applyPendingChange().isFailure)
            assertEquals("external", File(external, "keep.txt").readText())
        }
    }

    @Test
    fun `owned custom active root with unexpected name is rejected`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "custom/UnexpectedName").apply { mkdirs() }
            File(active, ".lynmusic-data-root").writeText("owned")
            updateLocationProperty(home, "active_data_root", active.absolutePath)
            val manager = JvmDataLocationManager(home, "Windows 11")

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("data_location_current_name_required"))
        }
    }

    @Test
    fun `migration moves full data root and persists target`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("theme=ocean")
            File(source, "offline/song.mp3").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val targetParent = File(home, "second-drive").apply { mkdirs() }
            val target = File(targetParent, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11")

            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            assertEquals("move", loadLocationProperties(home).getProperty("pending_strategy"))
            assertEquals("prepared", loadLocationProperties(home).getProperty("pending_phase"))
            val result = manager.applyPendingChange().getOrThrow()

            assertEquals(target.canonicalFile, result.canonicalFile)
            assertEquals("theme=ocean", File(target, "settings.properties").readText())
            assertTrue(File(target, "offline/song.mp3").isFile)
            assertTrue(File(target, ".lynmusic-data-root").isFile)
            assertFalse(source.exists())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
            val properties = loadLocationProperties(home)
            assertEquals(null, properties.getProperty("pending_strategy"))
            assertEquals(null, properties.getProperty("pending_phase"))
        }
    }

    @Test
    fun `apply and cancel wait for the same data location operation`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("source=true")
            val target = File(File(home, "serialized-operation").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> true }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            val applyReachedMove = CountDownLatch(1)
            val allowApplyToContinue = CountDownLatch(1)
            val cancelStarted = CountDownLatch(1)
            val cancelFinished = CountDownLatch(1)

            val applyResult = async(Dispatchers.IO) {
                manager.applyPendingChange { progress ->
                    if (progress.fraction == 0.35f) {
                        applyReachedMove.countDown()
                        check(allowApplyToContinue.await(5, TimeUnit.SECONDS))
                    }
                }
            }
            try {
                assertTrue(applyReachedMove.await(5, TimeUnit.SECONDS))
                val cancelResult = async(Dispatchers.IO) {
                    cancelStarted.countDown()
                    manager.cancelPendingChange().also { cancelFinished.countDown() }
                }
                assertTrue(cancelStarted.await(5, TimeUnit.SECONDS))
                assertFalse(cancelFinished.await(200, TimeUnit.MILLISECONDS))

                allowApplyToContinue.countDown()
                assertTrue(applyResult.await().isSuccess)
                assertTrue(cancelResult.await().isSuccess)
            } finally {
                allowApplyToContinue.countDown()
            }

            assertFalse(source.exists())
            assertEquals("source=true", File(target, "settings.properties").readText())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
            assertFalse(manager.hasPendingChange())
        }
    }

    @Test
    fun `a second schedule cannot overwrite an existing pending change`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("source=true")
            val firstTarget = File(File(home, "first-target").apply { mkdirs() }, "LynMusic")
            val secondTarget = File(File(home, "second-target").apply { mkdirs() }, "LynMusic")
            val resolverCalls = AtomicInteger()
            val firstScheduleReachedResolver = CountDownLatch(1)
            val allowFirstScheduleToContinue = CountDownLatch(1)
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ ->
                if (resolverCalls.incrementAndGet() == 1) {
                    firstScheduleReachedResolver.countDown()
                    check(allowFirstScheduleToContinue.await(5, TimeUnit.SECONDS))
                }
                true
            }
            val secondScheduleFinished = CountDownLatch(1)

            val firstResult = async(Dispatchers.IO) {
                runCatching { manager.scheduleChange(firstTarget, AppDataLocationChangeMode.Migrate) }
            }
            assertTrue(firstScheduleReachedResolver.await(5, TimeUnit.SECONDS))
            val secondResult = async(Dispatchers.IO) {
                runCatching {
                    manager.scheduleChange(secondTarget, AppDataLocationChangeMode.Migrate)
                }.also { secondScheduleFinished.countDown() }
            }
            try {
                assertFalse(secondScheduleFinished.await(200, TimeUnit.MILLISECONDS))
            } finally {
                allowFirstScheduleToContinue.countDown()
            }

            assertTrue(firstResult.await().isSuccess)
            val originalPending = loadLocationProperties(home)
            val rejectedResult = secondResult.await()
            val persistedPending = loadLocationProperties(home)

            assertTrue(rejectedResult.isFailure)
            assertTrue(rejectedResult.exceptionOrNull()?.message.orEmpty().contains("data_location_change_pending"))
            assertEquals(firstTarget.absolutePath, persistedPending.getProperty("pending_target_root"))
            assertEquals(originalPending.getProperty("pending_id"), persistedPending.getProperty("pending_id"))
            assertEquals(originalPending.getProperty("pending_phase"), persistedPending.getProperty("pending_phase"))
            assertFalse(secondTarget.exists())
        }
    }

    @Test
    fun `single instance lock excludes a second owner and releases cleanly`() = runTest {
        withTemporaryHome { home ->
            val first = JvmAppInstanceLock.tryAcquire(home)
            assertTrue(first != null)
            assertEquals(null, JvmAppInstanceLock.tryAcquire(home))

            first.close()
            val afterRelease = JvmAppInstanceLock.tryAcquire(home)
            assertTrue(afterRelease != null)
            afterRelease.close()
        }
    }

    @Test
    fun `discard creates empty target and permanently removes source`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "lynmusic.db").writeText("old database")
            val targetParent = File(home, "new-location").apply { mkdirs() }
            val target = File(targetParent, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 10")

            manager.scheduleChange(target, AppDataLocationChangeMode.Discard)
            manager.applyPendingChange().getOrThrow()

            assertFalse(source.exists())
            assertTrue(target.isDirectory)
            assertEquals(listOf(".lynmusic-data-root"), target.list()?.sorted())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `discard accepts an existing empty target directory`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("old=true")
            val target = File(File(home, "existing-target").apply { mkdirs() }, "LynMusic").apply { mkdirs() }
            val manager = JvmDataLocationManager(home, "Windows 11")

            manager.scheduleChange(target, AppDataLocationChangeMode.Discard)
            manager.applyPendingChange().getOrThrow()

            assertFalse(source.exists())
            assertEquals(listOf(".lynmusic-data-root"), target.list()?.sorted())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `discard recovers operation-only target left before root marker`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("old=true")
            val target = File(File(home, "discard-operation-only").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11")
            manager.scheduleChange(target, AppDataLocationChangeMode.Discard)
            writePendingOperationMarker(home, target, role = "target", root = PendingMarkerRoot.Target)

            manager.applyPendingChange().getOrThrow()

            assertFalse(source.exists())
            assertEquals(listOf(".lynmusic-data-root"), target.list()?.sorted())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `migration rewrites database paths that belong to old root`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val sourceOfflineFile = File(source, "offline/song.mp3").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val sourceDatabase = openTestDatabase(File(source, "lynmusic.db"))
            sourceDatabase.offlineDownloadDao().upsert(
                OfflineDownloadEntity(
                    trackId = "track-1",
                    sourceId = "source-1",
                    originalMediaLocator = "remote://song-1",
                    localMediaLocator = sourceOfflineFile.canonicalPath,
                    quality = "Original",
                    status = "Completed",
                    downloadedBytes = 3L,
                    totalBytes = 3L,
                    updatedAt = 1L,
                    errorMessage = null,
                ),
            )
            sourceDatabase.close()
            val target = File(File(home, "new-drive").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11")

            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            manager.applyPendingChange().getOrThrow()

            val targetDatabase = openTestDatabase(File(target, "lynmusic.db"))
            val moved = targetDatabase.offlineDownloadDao().getByTrackId("track-1")
            targetDatabase.close()
            assertEquals(File(target, "offline/song.mp3").canonicalPath, moved?.localMediaLocator)
        }
    }

    @Test
    fun `prepared move with marked target and missing source resumes at data ready`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("source=true")
            val targetParent = File(home, "retry-drive").apply { mkdirs() }
            val target = File(targetParent, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> true }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            writePendingOperationMarker(home, source, role = "target", root = PendingMarkerRoot.Source)
            assertTrue(source.renameTo(target))

            manager.applyPendingChange().getOrThrow()

            assertEquals("source=true", File(target, "settings.properties").readText())
            assertFalse(source.exists())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `prepared move resumes when configured custom source has already moved`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, "old-location/LynMusic").apply { mkdirs() }
            File(source, ".lynmusic-data-root").writeText("owned")
            File(source, "settings.properties").writeText("source=true")
            updateLocationProperty(home, "active_data_root", source.absolutePath)
            val target = File(File(home, "new-location").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> true }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            writePendingOperationMarker(home, source, role = "target", root = PendingMarkerRoot.Source)
            assertTrue(source.renameTo(target))

            manager.applyPendingChange().getOrThrow()

            assertEquals("source=true", File(target, "settings.properties").readText())
            assertFalse(source.exists())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `cross store migration uses logical database backup and excludes sidecars`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val database = openTestDatabase(File(source, "lynmusic.db"))
            database.offlineDownloadDao().upsert(
                OfflineDownloadEntity(
                    trackId = "wal-track",
                    sourceId = "source-1",
                    originalMediaLocator = "remote://wal-track",
                    localMediaLocator = null,
                    quality = "Original",
                    status = "Queued",
                    downloadedBytes = 0,
                    totalBytes = null,
                    updatedAt = 1,
                    errorMessage = null,
                ),
            )
            database.close()
            File(source, "lynmusic.db-wal").writeText("stale sidecar")
            File(source, "settings.properties").writeText("theme=ocean")
            val target = File(File(home, "other-store").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }

            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            assertEquals("copy", loadLocationProperties(home).getProperty("pending_strategy"))
            manager.applyPendingChange().getOrThrow()

            assertFalse(File(target, "lynmusic.db-wal").exists())
            assertEquals("theme=ocean", File(target, "settings.properties").readText())
            val migrated = openTestDatabase(File(target, "lynmusic.db"))
            assertEquals("wal-track", migrated.offlineDownloadDao().getByTrackId("wal-track")?.trackId)
            migrated.close()
        }
    }

    @Test
    fun `prepared copy rebuilds an owned target that is missing its database`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val sourceDatabase = openTestDatabase(File(source, "lynmusic.db"))
            sourceDatabase.offlineDownloadDao().upsert(sampleOfflineDownload("source-row"))
            sourceDatabase.close()
            File(source, "settings.properties").writeText("source=true")
            val target = File(File(home, "copy-recovery").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            createPendingOwnedDirectory(home, target, role = "target")
            File(target, "settings.properties").writeText("partial=true")

            manager.applyPendingChange().getOrThrow()

            assertEquals("source=true", File(target, "settings.properties").readText())
            val targetDatabase = openTestDatabase(File(target, "lynmusic.db"))
            assertEquals("source-row", targetDatabase.offlineDownloadDao().getByTrackId("source-row")?.trackId)
            targetDatabase.close()
        }
    }

    @Test
    fun `prepared copy preserves complete owned target when source is missing`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("source=true")
            val target = File(File(home, "copy-source-missing").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            createPendingOwnedDirectory(home, target, role = "target")
            File(target, "settings.properties").writeText("complete-target=true")
            source.deleteRecursively()

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertEquals("complete-target=true", File(target, "settings.properties").readText())
            assertTrue(File(target, ".lynmusic-data-root").isFile)
            assertEquals(source.absolutePath, loadLocationProperties(home).getProperty("active_data_root") ?: source.absolutePath)
            assertEquals("prepared", loadLocationProperties(home).getProperty("pending_phase"))
        }
    }

    @Test
    fun `prepared copy validates orphan source sidecars before deleting complete target`() = runTest {
        databaseArtifactNames.drop(1).forEach { artifactName ->
            withTemporaryHome { home ->
                val source = File(home, ".lynmusic").apply { mkdirs() }
                File(source, "settings.properties").writeText("source=true")
                val target = File(
                    File(home, "orphan-prepared-${artifactName.replace('.', '-')}").apply { mkdirs() },
                    "LynMusic",
                )
                val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
                manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
                createPendingOwnedDirectory(home, target, role = "target")
                val targetDatabaseBytes = "verified-target-$artifactName".encodeToByteArray()
                File(target, "lynmusic.db").writeBytes(targetDatabaseBytes)
                File(target, "keep.txt").writeText("complete-target")
                File(source, artifactName).writeText("orphan")

                val result = manager.applyPendingChange()

                assertTrue(result.isFailure, artifactName)
                assertEquals("orphan", File(source, artifactName).readText())
                assertEquals("complete-target", File(target, "keep.txt").readText())
                assertTrue(targetDatabaseBytes.contentEquals(File(target, "lynmusic.db").readBytes()))
                assertEquals("prepared", loadLocationProperties(home).getProperty("pending_phase"))
            }
        }
    }

    @Test
    fun `prepared copy never deletes target owned by a different operation`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("source=true")
            val target = File(File(home, "foreign-operation").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            createPendingOwnedDirectory(
                home = home,
                directory = target,
                role = "target",
                operationId = UUID.randomUUID().toString(),
            )
            File(target, "keep.txt").writeText("another-operation")

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertEquals("another-operation", File(target, "keep.txt").readText())
            assertTrue(File(target, ".lynmusic-data-operation").isFile)
            assertEquals(source.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `prepared copy recovers operation-only staging left before root marker`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("source=true")
            val target = File(File(home, "operation-only-staging").apply { mkdirs() }, "LynMusic")
            val staging = File(target.parentFile, ".LynMusic.migrating")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            writePendingOperationMarker(home, staging, role = "staging", root = PendingMarkerRoot.Target)

            manager.applyPendingChange().getOrThrow()

            assertFalse(staging.exists())
            assertEquals("source=true", File(target, "settings.properties").readText())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `cancelling copy with missing source preserves target and pending journal`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val target = File(File(home, "copy-cancel-source-missing").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            target.mkdirs()
            File(target, ".lynmusic-data-root").writeText("owned")
            File(target, "keep.txt").writeText("target")
            source.deleteRecursively()

            val result = manager.cancelPendingChange()

            assertTrue(result.isFailure)
            assertEquals("target", File(target, "keep.txt").readText())
            assertTrue(loadLocationProperties(home).containsKey("pending_source_root"))
        }
    }

    @Test
    fun `data ready copy with missing target database fails without deleting source`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            openTestDatabase(File(source, "lynmusic.db")).close()
            File(source, "settings.properties").writeText("source=true")
            val target = File(File(home, "broken-ready").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            createPendingOwnedDirectory(home, target, role = "target")
            File(target, "settings.properties").writeText("source=true")
            updateLocationProperty(home, "pending_phase", "data_ready")

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertTrue(File(source, "lynmusic.db").isFile)
            assertEquals(source.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `copy without database succeeds only when both roots have no database artifacts`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("source=true")
            val target = File(File(home, "no-database-copy").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }

            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            manager.applyPendingChange().getOrThrow()

            assertFalse(source.exists())
            assertEquals("source=true", File(target, "settings.properties").readText())
            assertTrue(databaseArtifactNames.none { name -> File(target, name).exists() })
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `data ready copy rejects orphan database artifacts on either root`() = runTest {
        listOf("source", "target").forEach { artifactRoot ->
            databaseArtifactNames.drop(1).forEach { artifactName ->
                withTemporaryHome { home ->
                    val source = File(home, ".lynmusic").apply { mkdirs() }
                    File(source, "settings.properties").writeText("source=true")
                    val target = File(
                        File(home, "orphan-$artifactRoot-${artifactName.replace('.', '-')}").apply { mkdirs() },
                        "LynMusic",
                    )
                    val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
                    manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
                    createPendingOwnedDirectory(home, target, role = "target")
                    File(target, "settings.properties").writeText("source=true")
                    val artifact = File(if (artifactRoot == "source") source else target, artifactName)
                    artifact.writeText("orphan")
                    updateLocationProperty(home, "pending_phase", "data_ready")

                    val result = manager.applyPendingChange()

                    assertTrue(result.isFailure, "$artifactRoot/$artifactName should fail")
                    assertEquals("orphan", artifact.readText())
                    assertEquals(source.canonicalFile, manager.currentRootDirectory().canonicalFile)
                    assertEquals("data_ready", loadLocationProperties(home).getProperty("pending_phase"))
                }
            }
        }
    }

    @Test
    fun `data ready copy with invalid target fails before upgrading source`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val sourceDatabaseFile = File(source, "lynmusic.db")
            openTestDatabase(sourceDatabaseFile).close()
            downgradeDatabaseToVersion18(sourceDatabaseFile)
            val target = File(File(home, "wrong-version").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            createPendingOwnedDirectory(home, target, role = "target")
            File(target, "lynmusic.db").writeBytes(byteArrayOf())
            updateLocationProperty(home, "pending_phase", "data_ready")

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertTrue(File(source, "lynmusic.db").isFile)
            assertEquals(18L, readUserVersion(sourceDatabaseFile))
            assertEquals(source.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `read only preflight leaves corrupt target database unchanged`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val sourceDatabaseFile = File(source, "lynmusic.db")
            openTestDatabase(sourceDatabaseFile).close()
            val target = File(File(home, "readonly-preflight").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            createPendingOwnedDirectory(home, target, role = "target")
            val targetDatabaseFile = File(target, "lynmusic.db")
            val originalBytes = "not-a-sqlite-database".encodeToByteArray()
            targetDatabaseFile.writeBytes(originalBytes)
            val originalModifiedTime = Files.getLastModifiedTime(targetDatabaseFile.toPath())
            updateLocationProperty(home, "pending_phase", "data_ready")

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertTrue(originalBytes.contentEquals(targetDatabaseFile.readBytes()))
            assertEquals(originalModifiedTime, Files.getLastModifiedTime(targetDatabaseFile.toPath()))
            assertEquals(
                listOf("lynmusic.db"),
                databaseArtifactNames.filter { name -> File(target, name).exists() },
            )
            assertEquals(source.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `read only preflight rejects target database symlink without touching external database`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            openTestDatabase(File(source, "lynmusic.db")).close()
            val externalDatabase = File(home, "external.db")
            openTestDatabase(externalDatabase).close()
            val originalBytes = externalDatabase.readBytes()
            val target = File(File(home, "symlink-preflight").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            createPendingOwnedDirectory(home, target, role = "target")
            val targetDatabaseLink = File(target, "lynmusic.db").toPath()
            if (!createSymbolicLinkOrSkip(targetDatabaseLink, externalDatabase.toPath())) return@withTemporaryHome
            updateLocationProperty(home, "pending_phase", "data_ready")

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertTrue(Files.isSymbolicLink(targetDatabaseLink))
            assertTrue(originalBytes.contentEquals(externalDatabase.readBytes()))
            assertEquals(source.canonicalFile, manager.currentRootDirectory().canonicalFile)
            assertEquals("data_ready", loadLocationProperties(home).getProperty("pending_phase"))
        }
    }

    @Test
    fun `prepared copy upgrades source database before logical backup`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val sourceDatabaseFile = File(source, "lynmusic.db")
            openTestDatabase(sourceDatabaseFile).close()
            downgradeDatabaseToVersion18(sourceDatabaseFile)
            val target = File(File(home, "upgrade-before-copy").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }

            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            manager.applyPendingChange().getOrThrow()

            assertEquals(20L, readUserVersion(File(target, "lynmusic.db")))
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `data ready copy upgrades old source to match already upgraded target`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val sourceDatabaseFile = File(source, "lynmusic.db")
            openTestDatabase(sourceDatabaseFile).close()
            downgradeDatabaseToVersion18(sourceDatabaseFile)
            File(source, ".lynmusic-data-root").writeText("owned")
            val target = File(File(home, "upgrade-data-ready").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            createPendingOwnedDirectory(home, target, role = "target")
            Files.copy(sourceDatabaseFile.toPath(), File(target, "lynmusic.db").toPath())
            openTestDatabase(File(target, "lynmusic.db")).close()
            updateLocationProperty(home, "pending_phase", "data_ready")

            manager.applyPendingChange().getOrThrow()

            assertEquals(20L, readUserVersion(File(target, "lynmusic.db")))
            assertFalse(source.exists())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `data ready copy upgrades both old source and old target`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val sourceDatabaseFile = File(source, "lynmusic.db")
            openTestDatabase(sourceDatabaseFile).close()
            downgradeDatabaseToVersion18(sourceDatabaseFile)
            File(source, ".lynmusic-data-root").writeText("owned")
            val target = File(File(home, "upgrade-both-data-ready").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            createPendingOwnedDirectory(home, target, role = "target")
            Files.copy(sourceDatabaseFile.toPath(), File(target, "lynmusic.db").toPath())
            updateLocationProperty(home, "pending_phase", "data_ready")

            manager.applyPendingChange().getOrThrow()

            assertEquals(20L, readUserVersion(File(target, "lynmusic.db")))
            assertFalse(source.exists())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `discard deletes a symlink only and preserves its external target`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val external = File(home, "external.txt").apply { writeText("keep") }
            val link = File(source, "external-link").toPath()
            if (!createSymbolicLinkOrSkip(link, external.toPath())) return@withTemporaryHome
            val target = File(File(home, "discard-target").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11")

            manager.scheduleChange(target, AppDataLocationChangeMode.Discard)
            manager.applyPendingChange().getOrThrow()

            assertEquals("keep", external.readText())
            assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        }
    }

    @Test
    fun `migration rejects a link inside the data root and reports its path`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            val external = File(home, "outside.txt").apply { writeText("keep") }
            val link = File(source, "outside-link").toPath()
            if (!createSymbolicLinkOrSkip(link, external.toPath())) return@withTemporaryHome
            val target = File(File(home, "migration-target").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = runCatching {
                manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            }.exceptionOrNull()

            assertTrue(error?.message.orEmpty().contains(link.fileName.toString()))
            assertEquals("keep", external.readText())
        }
    }

    @Test
    fun `cleanup failure blocks another location change until retry succeeds`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "active/LynMusic").apply { mkdirs() }
            val cleanup = File(home, "residual").apply { mkdirs() }
            val activeRootId = UUID.randomUUID().toString()
            val cleanupRootId = UUID.randomUUID().toString()
            val cleanupOperationId = UUID.randomUUID().toString()
            writeRootMarker(active, activeRootId)
            val properties = Properties().apply {
                setProperty("active_data_root", active.absolutePath)
                setProperty("active_root_id", activeRootId)
                setProperty("cleanup_root", cleanup.absolutePath)
                setProperty("cleanup_root_id", cleanupRootId)
                setProperty("cleanup_operation_id", cleanupOperationId)
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            val manager = JvmDataLocationManager(home, "Windows 11")
            val next = File(File(home, "next").apply { mkdirs() }, "LynMusic")

            assertTrue(manager.applyPendingChange().isSuccess)
            assertTrue(manager.cleanupWarning != null)
            assertTrue(runCatching {
                manager.scheduleChange(next, AppDataLocationChangeMode.Migrate)
            }.exceptionOrNull()?.message.orEmpty().contains("data_location_cleanup_pending"))

            writeRootMarker(cleanup, cleanupRootId)
            writeOperationMarker(cleanup, cleanupOperationId, cleanupRootId, "cleanup")
            manager.retryPendingCleanup().getOrThrow()
            assertEquals(null, manager.pendingCleanupRootPath())
            manager.scheduleChange(next, AppDataLocationChangeMode.Migrate)
        }
    }

    @Test
    fun `invalid cleanup root is cleared without deleting files and no longer blocks scheduling`() = runTest {
        withTemporaryHome { home ->
            File(home, ".lynmusic").mkdirs()
            val unrelated = File(home, "relative/residual/keep.txt").apply {
                parentFile.mkdirs()
                writeText("keep")
            }
            val properties = Properties().apply { setProperty("cleanup_root", "relative/residual") }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            val target = File(File(home, "next-location").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertEquals(null, manager.pendingCleanupRootPath())
            assertTrue(manager.cleanupWarning?.let { top.iwesley.lyn.music.core.model.resolveUiText(it, top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese) }.orEmpty().contains("未删除任何目录"))
            assertEquals(null, loadLocationProperties(home).getProperty("cleanup_root"))
            assertEquals("keep", unrelated.readText())
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
        }
    }

    @Test
    fun `owned tree deletion keeps marker when another file deletion fails`() = runTest {
        withTemporaryHome { home ->
            val root = File(home, "owned-delete").apply { mkdirs() }
            val marker = File(root, ".lynmusic-data-root").apply { writeText("owned") }
            File(root, "locked.dat").writeText("keep until retry")

            val result = runCatching {
                safeDeleteOwnedTree(root.toPath()) { path ->
                    if (path.fileName.toString() == "locked.dat") throw IOException("injected delete failure")
                    Files.deleteIfExists(path)
                    Unit
                }
            }

            assertTrue(result.isFailure)
            assertTrue(marker.isFile)
            safeDeleteOwnedTree(root.toPath())
            assertFalse(root.exists())
        }
    }

    @Test
    fun `deleting cleanup phase finishes empty markerless directory`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "active/LynMusic").apply { mkdirs() }
            val cleanup = File(home, "empty-cleanup").apply { mkdirs() }
            val activeRootId = UUID.randomUUID().toString()
            val cleanupRootId = UUID.randomUUID().toString()
            val cleanupOperationId = UUID.randomUUID().toString()
            writeRootMarker(active, activeRootId)
            val properties = Properties().apply {
                setProperty("active_data_root", active.absolutePath)
                setProperty("active_root_id", activeRootId)
                setProperty("cleanup_root", cleanup.absolutePath)
                setProperty("cleanup_phase", "deleting")
                setProperty("cleanup_root_id", cleanupRootId)
                setProperty("cleanup_operation_id", cleanupOperationId)
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            val manager = JvmDataLocationManager(home, "Windows 11")

            manager.retryPendingCleanup().getOrThrow()

            assertFalse(cleanup.exists())
            assertEquals(null, manager.pendingCleanupRootPath())
            assertEquals(null, loadLocationProperties(home).getProperty("cleanup_phase"))
        }
    }

    @Test
    fun `deleting cleanup phase rejects nonempty markerless directory`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "active/LynMusic").apply { mkdirs() }
            val cleanup = File(home, "unsafe-cleanup").apply { mkdirs() }
            File(cleanup, "keep.txt").writeText("external")
            val activeRootId = UUID.randomUUID().toString()
            val cleanupRootId = UUID.randomUUID().toString()
            val cleanupOperationId = UUID.randomUUID().toString()
            writeRootMarker(active, activeRootId)
            val properties = Properties().apply {
                setProperty("active_data_root", active.absolutePath)
                setProperty("active_root_id", activeRootId)
                setProperty("cleanup_root", cleanup.absolutePath)
                setProperty("cleanup_phase", "deleting")
                setProperty("cleanup_root_id", cleanupRootId)
                setProperty("cleanup_operation_id", cleanupOperationId)
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            val manager = JvmDataLocationManager(home, "Windows 11")

            val result = manager.retryPendingCleanup()

            assertTrue(result.isFailure)
            assertEquals("external", File(cleanup, "keep.txt").readText())
            assertEquals(cleanup.absolutePath, manager.pendingCleanupRootPath())
        }
    }

    @Test
    fun `invalid cleanup phase remains visible and never deletes data`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "active/LynMusic").apply { mkdirs() }
            File(active, ".lynmusic-data-root").writeText("owned")
            val cleanup = File(home, "invalid-phase").apply { mkdirs() }
            File(cleanup, ".lynmusic-data-root").writeText("owned")
            File(cleanup, "keep.txt").writeText("keep")
            val properties = Properties().apply {
                setProperty("active_data_root", active.absolutePath)
                setProperty("cleanup_root", cleanup.absolutePath)
                setProperty("cleanup_phase", "invalid")
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertEquals(cleanup.absolutePath, manager.pendingCleanupRootPath())
            assertTrue(manager.retryPendingCleanup().isFailure)
            assertEquals("keep", File(cleanup, "keep.txt").readText())
            assertEquals("invalid", loadLocationProperties(home).getProperty("cleanup_phase"))
        }
    }

    @Test
    fun `cleanup operation id mismatch remains visible and never deletes data`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "active/LynMusic")
            val cleanup = File(home, "foreign-cleanup")
            val activeRootId = UUID.randomUUID().toString()
            val cleanupRootId = UUID.randomUUID().toString()
            writeRootMarker(active, activeRootId)
            writeRootMarker(cleanup, cleanupRootId)
            writeOperationMarker(
                directory = cleanup,
                operationId = UUID.randomUUID().toString(),
                rootId = cleanupRootId,
                role = "cleanup",
            )
            File(cleanup, "keep.txt").writeText("foreign-operation")
            val properties = Properties().apply {
                setProperty("active_data_root", active.absolutePath)
                setProperty("active_root_id", activeRootId)
                setProperty("cleanup_root", cleanup.absolutePath)
                setProperty("cleanup_phase", "pending")
                setProperty("cleanup_root_id", cleanupRootId)
                setProperty("cleanup_operation_id", UUID.randomUUID().toString())
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            val manager = JvmDataLocationManager(home, "Windows 11")

            val result = manager.retryPendingCleanup()

            assertTrue(result.isFailure)
            assertEquals("foreign-operation", File(cleanup, "keep.txt").readText())
            assertEquals(cleanup.absolutePath, manager.pendingCleanupRootPath())
            assertEquals("pending", loadLocationProperties(home).getProperty("cleanup_phase"))
        }
    }

    @Test
    fun `cancel restores data moved to discard tombstone`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "lynmusic.db").writeText("keep me")
            val target = File(File(home, "discard-drive").apply { mkdirs() }, "LynMusic")
            val tombstone = File(home, "..lynmusic.discarding")
            val manager = JvmDataLocationManager(home, "Windows 11")
            manager.scheduleChange(target, AppDataLocationChangeMode.Discard)
            writePendingOperationMarker(home, source, role = "tombstone", root = PendingMarkerRoot.Source)
            assertTrue(source.renameTo(tombstone))
            createPendingOwnedDirectory(home, target, role = "target")

            manager.cancelPendingChange().getOrThrow()

            assertEquals("keep me", File(source, "lynmusic.db").readText())
            assertFalse(tombstone.exists())
            assertFalse(target.exists())
            assertEquals(source.canonicalFile, manager.applyPendingChange().getOrThrow().canonicalFile)
        }
    }

    @Test
    fun `non empty target is rejected`() = runTest {
        withTemporaryHome { home ->
            File(home, ".lynmusic").mkdirs()
            val target = File(home, "drive/LynMusic").apply {
                mkdirs()
                resolve("unrelated.txt").writeText("keep")
            }
            val manager = JvmDataLocationManager(home, "Windows 11")

            val error = runCatching {
                manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            }.exceptionOrNull()

            assertTrue(error?.message.orEmpty().contains("data_location_target_must_be_empty"))
            assertTrue(File(target, "unrelated.txt").isFile)
        }
    }

    @Test
    fun `pending source different from active root fails before touching target`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "keep.txt").writeText("active")
            val target = File(File(home, "pending-source-mismatch").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            target.mkdirs()
            File(target, ".lynmusic-data-root").writeText("owned")
            File(target, "keep.txt").writeText("target")
            val otherSource = File(home, "other/LynMusic").apply { mkdirs() }
            File(otherSource, ".lynmusic-data-root").writeText("owned")
            updateLocationProperty(home, "pending_source_root", otherSource.absolutePath)

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertEquals("active", File(source, "keep.txt").readText())
            assertEquals("target", File(target, "keep.txt").readText())
            assertTrue(loadLocationProperties(home).containsKey("pending_source_root"))
        }
    }

    @Test
    fun `explicit invalid strategy and mode strategy conflict never fall back`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "keep.txt").writeText("source")
            val target = File(File(home, "invalid-pending").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)

            updateLocationProperty(home, "pending_strategy", "not-a-strategy")
            assertTrue(manager.applyPendingChange().isFailure)
            assertEquals("source", File(source, "keep.txt").readText())

            updateLocationProperty(home, "pending_strategy", "discard")
            assertTrue(manager.applyPendingChange().isFailure)
            assertEquals("source", File(source, "keep.txt").readText())
            assertFalse(target.exists())

            updateLocationProperty(home, "pending_strategy", "copy")
            updateLocationProperty(home, "pending_phase", "not-a-phase")
            assertTrue(manager.applyPendingChange().isFailure)
            assertEquals("source", File(source, "keep.txt").readText())
            assertFalse(target.exists())
        }
    }

    @Test
    fun `legacy pending without strategy and phase defaults safely`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "settings.properties").writeText("legacy=true")
            val target = File(File(home, "legacy-pending").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            val properties = loadLocationProperties(home).apply {
                remove("pending_strategy")
                remove("pending_phase")
                remove("pending_id")
                remove("pending_source_root_id")
                remove("pending_target_root_id")
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }

            manager.applyPendingChange().getOrThrow()

            assertEquals("legacy=true", File(target, "settings.properties").readText())
            assertEquals(target.canonicalFile, manager.currentRootDirectory().canonicalFile)
        }
    }

    @Test
    fun `legacy pending with nonempty residual is not upgraded or deleted`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "keep.txt").writeText("source")
            val target = File(File(home, "legacy-residual").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            val properties = loadLocationProperties(home).apply {
                remove("pending_id")
                remove("pending_source_root_id")
                remove("pending_target_root_id")
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            target.mkdirs()
            File(target, ".lynmusic-data-root").writeText("legacy-owned")
            File(target, "keep.txt").writeText("unknown-residual")

            val result = manager.applyPendingChange()

            assertTrue(result.isFailure)
            assertEquals("source", File(source, "keep.txt").readText())
            assertEquals("unknown-residual", File(target, "keep.txt").readText())
            assertEquals(null, loadLocationProperties(home).getProperty("pending_id"))

            manager.cancelPendingChange().getOrThrow()
            assertEquals("unknown-residual", File(target, "keep.txt").readText())
            assertFalse(manager.hasPendingChange())
        }
    }

    @Test
    fun `cancelling corrupt pending only clears journal and preserves all directories`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "keep.txt").writeText("source")
            val target = File(File(home, "corrupt-cancel").apply { mkdirs() }, "LynMusic")
            val manager = JvmDataLocationManager(home, "Windows 11") { _, _ -> false }
            manager.scheduleChange(target, AppDataLocationChangeMode.Migrate)
            target.mkdirs()
            File(target, ".lynmusic-data-root").writeText("owned")
            File(target, "keep.txt").writeText("target")
            updateLocationProperty(home, "pending_strategy", "discard")

            manager.cancelPendingChange().getOrThrow()

            assertEquals("source", File(source, "keep.txt").readText())
            assertEquals("target", File(target, "keep.txt").readText())
            val properties = loadLocationProperties(home)
            assertFalse(properties.containsKey("pending_source_root"))
            assertFalse(properties.containsKey("pending_target_root"))
            assertFalse(properties.containsKey("pending_mode"))
            assertFalse(properties.containsKey("pending_strategy"))
            assertFalse(properties.containsKey("pending_phase"))
            manager.applyPendingChange().getOrThrow()
            assertTrue(manager.cleanupWarning?.let { top.iwesley.lyn.music.core.model.resolveUiText(it, top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese) }.orEmpty().contains("未移动或删除任何目录"))
            val warning = requireNotNull(manager.cleanupWarning)
            assertTrue(top.iwesley.lyn.music.core.model.resolveUiText(warning, top.iwesley.lyn.music.core.model.AppLanguage.English).startsWith("The damaged data-location change record was safely cancelled. No folders were moved or deleted:"))
            assertTrue(top.iwesley.lyn.music.core.model.resolveUiText(warning, top.iwesley.lyn.music.core.model.AppLanguage.TraditionalChinese).startsWith("已安全取消損壞的資料位置切換記錄，未移動或刪除任何目錄："))
        }
    }

    @Test
    fun `pending target without existing parent fails without touching active data`() = runTest {
        withTemporaryHome { home ->
            val source = File(home, ".lynmusic").apply { mkdirs() }
            File(source, "keep.txt").writeText("source")
            val target = File(File(home, "missing-parent"), "LynMusic")
            val properties = Properties().apply {
                setProperty("pending_source_root", source.absolutePath)
                setProperty("pending_target_root", target.absolutePath)
                setProperty("pending_mode", AppDataLocationChangeMode.Migrate.name)
                setProperty("pending_strategy", "copy")
                setProperty("pending_phase", "prepared")
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertTrue(manager.applyPendingChange().isFailure)
            assertEquals("source", File(source, "keep.txt").readText())
            assertFalse(target.exists())
        }
    }

    @Test
    fun `pending target equal to active root is rejected before deletion`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "active/LynMusic").apply { mkdirs() }
            File(active, ".lynmusic-data-root").writeText("owned")
            File(active, "keep.txt").writeText("active")
            val properties = Properties().apply {
                setProperty("active_data_root", active.absolutePath)
                setProperty("pending_source_root", active.absolutePath)
                setProperty("pending_target_root", active.absolutePath)
                setProperty("pending_mode", AppDataLocationChangeMode.Migrate.name)
                setProperty("pending_strategy", "copy")
                setProperty("pending_phase", "prepared")
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertTrue(manager.applyPendingChange().isFailure)
            assertEquals("active", File(active, "keep.txt").readText())
            assertTrue(File(active, ".lynmusic-data-root").isFile)
        }
    }

    @Test
    fun `pending target canonical alias of active root is rejected before deletion`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "active/LynMusic").apply { mkdirs() }
            File(active, ".lynmusic-data-root").writeText("owned")
            File(active, "keep.txt").writeText("active")
            val aliasParent = File(home, "alias-parent").apply { mkdirs() }
            val aliasTarget = File(aliasParent, "LynMusic")
            if (!createSymbolicLinkOrSkip(aliasTarget.toPath(), active.toPath())) return@withTemporaryHome
            val properties = Properties().apply {
                setProperty("active_data_root", active.absolutePath)
                setProperty("pending_source_root", active.absolutePath)
                setProperty("pending_target_root", aliasTarget.absolutePath)
                setProperty("pending_mode", AppDataLocationChangeMode.Migrate.name)
                setProperty("pending_strategy", "copy")
                setProperty("pending_phase", "prepared")
            }
            File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertTrue(manager.applyPendingChange().isFailure)
            assertEquals("active", File(active, "keep.txt").readText())
            assertTrue(Files.isSymbolicLink(aliasTarget.toPath()))
        }
    }

    @Test
    fun `invalid active root has no cancellable pending change`() = runTest {
        withTemporaryHome { home ->
            val active = File(home, "invalid-active/LynMusic").apply { mkdirs() }
            File(active, "keep.txt").writeText("external")
            updateLocationProperty(home, "active_data_root", active.absolutePath)
            val manager = JvmDataLocationManager(home, "Windows 11")

            assertFalse(manager.hasPendingChange())
            assertTrue(manager.applyPendingChange().isFailure)
            assertFalse(manager.hasPendingChange())
            assertEquals("external", File(active, "keep.txt").readText())
        }
    }

    @Test
    fun `windows detection does not enable mac or linux`() = runTest {
        assertTrue(isJvmWindowsOs("Windows 11"))
        assertFalse(isJvmWindowsOs("Mac OS X"))
        assertFalse(isJvmWindowsOs("Darwin"))
        assertFalse(isJvmWindowsOs("Linux"))
    }
}

private inline fun withTemporaryHome(block: (File) -> Unit) {
    val directory = Files.createTempDirectory("lynmusic-data-location-test").toFile()
    try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}

private fun openTestDatabase(file: File): LynMusicDatabase {
    file.parentFile.mkdirs()
    return openLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = file.absolutePath),
    ).getOrThrow()
}

private fun downgradeDatabaseToVersion18(file: File) {
    BundledSQLiteDriver().open(file.absolutePath).use { connection ->
        connection.execTestSql("ALTER TABLE playback_queue_snapshot RENAME TO playback_queue_snapshot_v19")
        connection.execTestSql(
            """
            CREATE TABLE playback_queue_snapshot (
                id INTEGER NOT NULL PRIMARY KEY,
                queueTrackIds TEXT NOT NULL,
                orderedQueueTrackIds TEXT NOT NULL,
                currentIndex INTEGER NOT NULL,
                positionMs INTEGER NOT NULL,
                mode TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        connection.execTestSql(
            """
            INSERT INTO playback_queue_snapshot (
                id, queueTrackIds, orderedQueueTrackIds, currentIndex, positionMs, mode, updatedAt
            )
            SELECT id, queueTrackIds, orderedQueueTrackIds, currentIndex, positionMs, mode, updatedAt
            FROM playback_queue_snapshot_v19
            """.trimIndent(),
        )
        connection.execTestSql("DROP TABLE playback_queue_snapshot_v19")
        connection.execTestSql("ALTER TABLE import_source DROP COLUMN selectedDirectories")
        connection.execTestSql("PRAGMA user_version = 18")
    }
}

private fun readUserVersion(file: File): Long {
    return BundledSQLiteDriver().open(file.absolutePath).use { connection ->
        connection.prepare("PRAGMA user_version").use { statement ->
            check(statement.step())
            statement.getLong(0)
        }
    }
}

private val databaseArtifactNames = listOf(
    "lynmusic.db",
    "lynmusic.db-wal",
    "lynmusic.db-shm",
    "lynmusic.db-journal",
    "lynmusic.db.lck",
)

private fun SQLiteConnection.execTestSql(sql: String) {
    prepare(sql).use { it.step() }
}

private fun loadLocationProperties(home: File): Properties = Properties().apply {
    val file = File(home, ".lynmusic-location.properties")
    if (file.isFile) file.inputStream().use(::load)
}

private fun updateLocationProperty(home: File, key: String, value: String) {
    val properties = loadLocationProperties(home).apply { setProperty(key, value) }
    File(home, ".lynmusic-location.properties").outputStream().use { properties.store(it, "test") }
}

private enum class PendingMarkerRoot { Source, Target }

private fun createPendingOwnedDirectory(
    home: File,
    directory: File,
    role: String,
    root: PendingMarkerRoot = PendingMarkerRoot.Target,
    operationId: String? = null,
) {
    directory.mkdirs()
    val pending = loadLocationProperties(home)
    val rootId = pending.getProperty(
        when (root) {
            PendingMarkerRoot.Source -> "pending_source_root_id"
            PendingMarkerRoot.Target -> "pending_target_root_id"
        },
    )
    writeRootMarker(directory, rootId)
    writeOperationMarker(
        directory = directory,
        operationId = operationId ?: pending.getProperty("pending_id"),
        rootId = rootId,
        role = role,
    )
}

private fun writePendingOperationMarker(
    home: File,
    directory: File,
    role: String,
    root: PendingMarkerRoot,
) {
    val pending = loadLocationProperties(home)
    val rootId = pending.getProperty(
        when (root) {
            PendingMarkerRoot.Source -> "pending_source_root_id"
            PendingMarkerRoot.Target -> "pending_target_root_id"
        },
    )
    writeOperationMarker(directory, pending.getProperty("pending_id"), rootId, role)
}

private fun writeRootMarker(directory: File, rootId: String) {
    directory.mkdirs()
    val marker = Properties().apply {
        setProperty("format", "1")
        setProperty("root_id", rootId)
    }
    File(directory, ".lynmusic-data-root").outputStream().use { marker.store(it, "test root") }
}

private fun writeOperationMarker(directory: File, operationId: String, rootId: String, role: String) {
    directory.mkdirs()
    val marker = Properties().apply {
        setProperty("format", "1")
        setProperty("operation_id", operationId)
        setProperty("root_id", rootId)
        setProperty("role", role)
    }
    File(directory, ".lynmusic-data-operation").outputStream().use { marker.store(it, "test operation") }
}

private fun sampleOfflineDownload(trackId: String) = OfflineDownloadEntity(
    trackId = trackId,
    sourceId = "source-1",
    originalMediaLocator = "remote://$trackId",
    localMediaLocator = null,
    quality = "Original",
    status = "Queued",
    downloadedBytes = 0,
    totalBytes = null,
    updatedAt = 1,
    errorMessage = null,
)

private fun createSymbolicLinkOrSkip(link: Path, target: Path): Boolean {
    return runCatching {
        Files.createSymbolicLink(link, target)
        true
    }.getOrDefault(false)
}
