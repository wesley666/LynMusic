package top.iwesley.lyn.music.platform

import androidx.compose.runtime.Composable

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.resolveUiString

import top.iwesley.lyn.music.uiString
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiTextArgumentException
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull
import top.iwesley.lyn.music.core.model.checkUi
import top.iwesley.lyn.music.core.model.requireUi
import top.iwesley.lyn.music.core.model.uiText

import androidx.room.PooledConnection
import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.AppDataLocationChangeMode
import top.iwesley.lyn.music.core.model.AppDataLocationPlatformService
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.openLynMusicDatabase

data class JvmDataLocationProgress(
    val message: UiText,
    val fraction: Float? = null,
)

internal enum class JvmDataMigrationStrategy(val configValue: String) {
    Move("move"),
    Copy("copy"),
    Discard("discard"),
}

internal enum class JvmDataMigrationPhase(val configValue: String) {
    Prepared("prepared"),
    DataReady("data_ready"),
}

internal enum class JvmCleanupPhase(val configValue: String) {
    Pending("pending"),
    Deleting("deleting"),
}

internal enum class JvmDataOperationRole(val configValue: String) {
    Staging("staging"),
    Target("target"),
    Tombstone("tombstone"),
    Cleanup("cleanup"),
}

class JvmDataLocationManager(
    private val userHomeDirectory: File = File(System.getProperty("user.home")),
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val sameFileStoreResolver: (File, File) -> Boolean? = ::areOnSameFileStore,
) {
    private val defaultRoot = File(userHomeDirectory, ".lynmusic").normalized()
    private val configFile = File(userHomeDirectory, ".lynmusic-location.properties")
    private val propertyLock = Any()
    private val operationMutex = Mutex()

    val supportsCustomLocation: Boolean
        get() = isJvmWindowsOs(osName)

    var cleanupWarning: UiText? = null
        private set

    private var pendingSafetyWarning: UiText? = null

    fun currentRootDirectory(): File {
        if (!supportsCustomLocation) return defaultRoot
        val active = configuredActiveRoot()
        validateActiveRoot(active)
        return active.directory
    }

    private fun configuredActiveRootDirectory(): File = synchronized(propertyLock) {
        configuredActiveRoot(loadProperties()).directory
    }

    private fun configuredActiveRoot(): ConfiguredActiveRoot = synchronized(propertyLock) {
        configuredActiveRoot(loadProperties())
    }

    private fun configuredActiveRoot(properties: Properties): ConfiguredActiveRoot = ConfiguredActiveRoot(
        directory = properties.getProperty(KEY_ACTIVE_ROOT)?.let(::safeAbsoluteFile) ?: defaultRoot,
        rootId = properties.getProperty(KEY_ACTIVE_ROOT_ID)?.let(::parseIdentity),
    )

    fun pendingCleanupRootPath(): String? = resolveCleanupRootDirectory()?.absolutePath

    fun hasPendingChange(): Boolean = synchronized(propertyLock) {
        PENDING_KEYS.any(loadProperties()::containsKey)
    }

    internal fun requiresStartupDataLocationOperation(): Boolean {
        if (!supportsCustomLocation) return false
        return synchronized(propertyLock) {
            val properties = loadProperties()
            PENDING_KEYS.any(properties::containsKey) || properties.containsKey(KEY_CLEANUP_ROOT)
        }
    }

    suspend fun retryPendingCleanup(): Result<Unit> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            runCatching {
                cleanupWarning = null
                finishPendingCleanup()
            }.onFailure { error ->
                cleanupWarning = cleanupFailureMessage(error)
            }
        }
    }

    suspend fun cancelPendingChange(): Result<Unit> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            runCatching {
                val pending = try {
                    synchronized(propertyLock) { loadPending(loadProperties()) }
                } catch (error: Throwable) {
                    safelyClearCorruptPending(error)
                    return@runCatching
                } ?: return@runCatching
                if (!pending.hasOperationIdentity) {
                    safelyClearCorruptPending(IllegalStateException("旧版迁移记录缺少目录身份，已按安全模式取消。"))
                    return@runCatching
                }
                try {
                    validatePending(pending)
                } catch (error: Throwable) {
                    safelyClearCorruptPending(error)
                    return@runCatching
                }
                when (pending.strategy) {
                    JvmDataMigrationStrategy.Move -> rollbackMovedMigration(pending)
                    JvmDataMigrationStrategy.Copy -> cancelCopiedMigration(pending)
                    JvmDataMigrationStrategy.Discard -> rollbackDiscard(pending)
                }
                synchronized(propertyLock) {
                    val properties = loadProperties()
                    clearPending(properties)
                    persist(properties)
                }
            }
        }
    }

    suspend fun scheduleChange(targetRoot: File, mode: AppDataLocationChangeMode) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            checkUi(supportsCustomLocation) { uiText(Res.string.data_location_custom_unsupported) }
            synchronized(propertyLock) {
                requireUi(PENDING_KEYS.none(loadProperties()::containsKey)) {
                    uiText(Res.string.data_location_change_pending)
                }
            }
            val source = currentRootDirectory().normalized()
            val target = targetRoot.normalized()
            requireUi(resolveCleanupRootDirectory() == null) { uiText(Res.string.data_location_cleanup_pending) }
            validateSourceOwnership(source)
            validateTargetTopology(source, target)
            val strategy = when (mode) {
                AppDataLocationChangeMode.Discard -> JvmDataMigrationStrategy.Discard
                AppDataLocationChangeMode.Migrate -> {
                    if (sameFileStoreResolver(source, target.parentFile) == true) {
                        JvmDataMigrationStrategy.Move
                    } else {
                        JvmDataMigrationStrategy.Copy
                    }
                }
            }
            if (strategy == JvmDataMigrationStrategy.Discard) {
                rejectRootLink(source)
            } else {
                rejectLinksInTree(source)
            }
            validateTargetAvailability(source, target, strategy)
            val sourceRootId = ensureActiveRootIdentity(source)
            val targetRootId = if (strategy == JvmDataMigrationStrategy.Move) sourceRootId else newIdentity()
            val operationId = newIdentity()
            synchronized(propertyLock) {
                val properties = loadProperties()
                when (val cleanup = loadCleanupRootState(properties)) {
                    CleanupRootState.None -> Unit
                    is CleanupRootState.InvalidPath -> clearInvalidCleanupRoot(properties, cleanup)
                    is CleanupRootState.InvalidPhase -> throw UiTextException(cleanup.text)
                    is CleanupRootState.InvalidIdentity -> throw UiTextException(cleanup.text)
                    is CleanupRootState.Valid -> throw UiTextException(uiText(Res.string.data_location_cleanup_pending))
                }
                properties.setProperty(KEY_PENDING_SOURCE, source.absolutePath)
                properties.setProperty(KEY_PENDING_TARGET, target.absolutePath)
                properties.setProperty(KEY_PENDING_MODE, mode.name)
                properties.setProperty(KEY_PENDING_STRATEGY, strategy.configValue)
                properties.setProperty(KEY_PENDING_PHASE, JvmDataMigrationPhase.Prepared.configValue)
                properties.setProperty(KEY_PENDING_ID, operationId)
                properties.setProperty(KEY_PENDING_SOURCE_ROOT_ID, sourceRootId)
                properties.setProperty(KEY_PENDING_TARGET_ROOT_ID, targetRootId)
                persist(properties)
            }
        }
    }

    suspend fun applyPendingChange(
        onProgress: (JvmDataLocationProgress) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            runCatching {
                cleanupWarning = pendingSafetyWarning
                pendingSafetyWarning = null
                if (!supportsCustomLocation) return@runCatching defaultRoot
                if (resolveCleanupRootDirectory() != null) {
                    retryCleanupAtStartup()
                    return@runCatching currentRootDirectory()
                }
                val loadedPending = synchronized(propertyLock) { loadPending(loadProperties()) }
                    ?: return@runCatching currentRootDirectory()
                val pending = ensurePendingIdentity(loadedPending)
                validatePending(pending)
                when (pending.strategy) {
                    JvmDataMigrationStrategy.Move -> migrateByMove(pending, onProgress)
                    JvmDataMigrationStrategy.Copy -> migrateByCopy(pending, onProgress)
                    JvmDataMigrationStrategy.Discard -> discard(pending, onProgress)
                }
            }
        }
    }

    private fun migrateByMove(
        initial: PendingChange,
        onProgress: (JvmDataLocationProgress) -> Unit,
    ): File {
        var pending = initial
        val source = pending.source
        val target = pending.target
        try {
            if (
                pending.phase == JvmDataMigrationPhase.DataReady &&
                !target.existsNoFollow() &&
                source.existsNoFollow()
            ) {
                updatePendingPhase(pending, JvmDataMigrationPhase.Prepared)
                pending = pending.copy(phase = JvmDataMigrationPhase.Prepared)
            }
            if (pending.phase == JvmDataMigrationPhase.Prepared) {
                if (source.existsNoFollow()) {
                    validateSourceOwnership(source)
                    requireRootIdentity(source, pending.requireSourceRootId(), "源数据目录身份不匹配。")
                    rejectLinksInTree(source)
                    validateTargetAvailability(source, target, JvmDataMigrationStrategy.Move)
                    writeOperationMarker(source, pending, JvmDataOperationRole.Target)
                    removeEmptyTarget(target)
                    onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_moving_data), 0.35f))
                    moveDirectory(source.toPath(), target.toPath())
                } else {
                    requireRootIdentity(
                        target,
                        pending.requireTargetRootId(),
                        "源目录不存在，且目标目录不是可恢复的 LynMusic 数据目录。",
                    )
                    requireOperationMarker(target, pending, setOf(JvmDataOperationRole.Target))
                }
                updatePendingPhase(pending, JvmDataMigrationPhase.DataReady)
                pending = pending.copy(phase = JvmDataMigrationPhase.DataReady)
            }
            require(!source.existsNoFollow()) { "同盘移动恢复时源目录和目标目录同时存在。" }
            requireRootIdentity(target, pending.requireTargetRootId(), "移动后的目标数据目录身份不匹配。")
            requireOperationMarker(target, pending, setOf(JvmDataOperationRole.Target))
            onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_repairing_references), 0.8f))
            rewriteMovedDatabasePaths(target, source, target)
            commitActiveRoot(pending, cleanupRoot = null)
            onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_migration_complete), 1f))
            return target
        } catch (error: Throwable) {
            if (!sameLocation(configuredActiveRootDirectory(), target)) {
                runCatching { rollbackMovedMigration(pending) }.onFailure { rollbackError ->
                    error.addSuppressedSafely(rollbackError)
                }
            }
            throw error
        }
    }

    private fun migrateByCopy(
        initial: PendingChange,
        onProgress: (JvmDataLocationProgress) -> Unit,
    ): File {
        var pending = initial
        val source = pending.source
        val target = pending.target
        val staging = stagingDirectory(target)
        var stagingCreatedByThisAttempt = false
        try {
            validateSourceOwnership(source)
            requireRootIdentity(source, pending.requireSourceRootId(), "源数据目录身份不匹配。")
            rejectLinksInTree(source)
            if (pending.phase == JvmDataMigrationPhase.Prepared) {
                validateSourceDatabaseCollection(source)
                validatePendingDirectoryOwnership(
                    target,
                    pending,
                    setOf(JvmDataOperationRole.Target, JvmDataOperationRole.Staging),
                )
                validatePendingDirectoryOwnership(staging, pending, setOf(JvmDataOperationRole.Staging))
                prepareDatabaseForMigration(source, "源")
                validateSourceDatabaseCollection(source)
                if (staging.existsNoFollow()) {
                    deletePendingDirectory(staging, pending, setOf(JvmDataOperationRole.Staging))
                }
                validateTargetParentAvailability(source, target, JvmDataMigrationStrategy.Copy)
                writeOperationMarker(staging, pending, JvmDataOperationRole.Staging)
                ensureRootMarker(staging, pending.requireTargetRootId())
                stagingCreatedByThisAttempt = true
                onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_copying_data), 0f))
                copyNonDatabaseFiles(source, staging, onProgress)
                backupDatabaseLogically(source, staging)
                verifyNonDatabaseCopy(source, staging)
                verifyDatabasePairBeforeUpgrade(source, staging)
                prepareDatabaseForMigration(staging, "目标")
                verifyDatabasePairAfterUpgrade(source, staging)
                if (target.existsNoFollow()) {
                    deletePendingDirectory(
                        target,
                        pending,
                        setOf(JvmDataOperationRole.Target, JvmDataOperationRole.Staging),
                    )
                }
                moveDirectory(staging.toPath(), target.toPath())
                writeOperationMarker(target, pending, JvmDataOperationRole.Target)
                updatePendingPhase(pending, JvmDataMigrationPhase.DataReady)
                pending = pending.copy(phase = JvmDataMigrationPhase.DataReady)
            }
            validateSourceOwnership(source)
            requireRootIdentity(source, pending.requireSourceRootId(), "源数据目录身份不匹配。")
            requireRootIdentity(target, pending.requireTargetRootId(), "目标数据目录不可用，无法继续跨盘迁移。")
            requireOperationMarker(target, pending, setOf(JvmDataOperationRole.Target))
            verifyNonDatabaseCopy(source, target)
            verifyDatabasePairBeforeUpgrade(source, target)
            prepareDatabaseForMigration(source, "源")
            prepareDatabaseForMigration(target, "目标")
            verifyDatabasePairAfterUpgrade(source, target)
            onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_repairing_references), 0.9f))
            rewriteMovedDatabasePaths(target, source, target)
            writeOperationMarker(source, pending, JvmDataOperationRole.Cleanup)
            commitActiveRoot(pending, cleanupRoot = source)
            onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_cleaning_old_data), 0.96f))
            finishCleanupOrRecordWarning()
            onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_migration_complete), 1f))
            return target
        } catch (error: Throwable) {
            if (stagingCreatedByThisAttempt && staging.existsNoFollow()) {
                runCatching {
                    deletePendingDirectory(staging, pending, setOf(JvmDataOperationRole.Staging))
                }.onFailure { cleanupError ->
                    error.addSuppressedSafely(cleanupError)
                }
            }
            throw error
        }
    }

    private fun discard(
        initial: PendingChange,
        onProgress: (JvmDataLocationProgress) -> Unit,
    ): File {
        var pending = initial
        val source = pending.source
        val target = pending.target
        val tombstone = tombstoneDirectory(source)
        var rollbackIsSafe = pending.phase == JvmDataMigrationPhase.DataReady && tombstone.existsNoFollow()
        try {
            if (
                pending.phase == JvmDataMigrationPhase.DataReady &&
                !target.existsNoFollow() &&
                source.existsNoFollow() &&
                !tombstone.existsNoFollow()
            ) {
                updatePendingPhase(pending, JvmDataMigrationPhase.Prepared)
                pending = pending.copy(phase = JvmDataMigrationPhase.Prepared)
            }
            if (pending.phase == JvmDataMigrationPhase.Prepared) {
                if (source.existsNoFollow()) {
                    validateSourceOwnership(source)
                    requireRootIdentity(source, pending.requireSourceRootId(), "源数据目录身份不匹配。")
                    rejectRootLink(source)
                    rollbackIsSafe = true
                    validatePendingDirectoryOwnership(target, pending, setOf(JvmDataOperationRole.Target))
                    validatePendingDirectoryOwnership(tombstone, pending, setOf(JvmDataOperationRole.Tombstone))
                    validateTargetParentAvailability(source, target, JvmDataMigrationStrategy.Discard)
                    if (target.existsNoFollow()) {
                        deletePendingDirectory(target, pending, setOf(JvmDataOperationRole.Target))
                    }
                    writeOperationMarker(target, pending, JvmDataOperationRole.Target)
                    ensureRootMarker(target, pending.requireTargetRootId())
                    if (tombstone.existsNoFollow()) {
                        deletePendingDirectory(tombstone, pending, setOf(JvmDataOperationRole.Tombstone))
                    }
                    writeOperationMarker(source, pending, JvmDataOperationRole.Tombstone)
                    onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_isolating_old_data), 0.3f))
                    moveDirectory(source.toPath(), tombstone.toPath())
                } else {
                    requireRootIdentity(
                        tombstone,
                        pending.requireSourceRootId(),
                        "旧数据目录不可用，且没有可恢复的待删除目录。",
                    )
                    requireOperationMarker(tombstone, pending, setOf(JvmDataOperationRole.Tombstone))
                    requireEmptyOperationDirectory(target, pending, JvmDataOperationRole.Target)
                    rollbackIsSafe = true
                }
                updatePendingPhase(pending, JvmDataMigrationPhase.DataReady)
                pending = pending.copy(phase = JvmDataMigrationPhase.DataReady)
            }
            requireRootIdentity(tombstone, pending.requireSourceRootId(), "待删除数据目录身份不匹配。")
            requireOperationMarker(tombstone, pending, setOf(JvmDataOperationRole.Tombstone))
            requireEmptyOperationDirectory(target, pending, JvmDataOperationRole.Target)
            commitActiveRoot(pending, cleanupRoot = tombstone)
            onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_deleting_old_data), 0.75f))
            finishCleanupOrRecordWarning()
            onProgress.report(JvmDataLocationProgress(uiText(Res.string.startup_new_location_active), 1f))
            return target
        } catch (error: Throwable) {
            if (rollbackIsSafe && !sameLocation(configuredActiveRootDirectory(), target)) {
                runCatching { rollbackDiscard(pending) }.onFailure { rollbackError ->
                    error.addSuppressedSafely(rollbackError)
                }
            }
            throw error
        }
    }

    private fun rollbackMovedMigration(pending: PendingChange) {
        val source = pending.source
        val target = pending.target
        if (!target.existsNoFollow()) {
            removeOperationMarkerIfMatching(source, pending, setOf(JvmDataOperationRole.Target))
            return
        }
        require(!source.existsNoFollow()) { "源目录和目标目录同时存在，拒绝自动回滚。" }
        requireRootIdentity(target, pending.requireTargetRootId(), "移动目标目录身份不匹配，拒绝回滚。")
        requireOperationMarker(target, pending, setOf(JvmDataOperationRole.Target))
        rewriteMovedDatabasePaths(target, target, source)
        moveDirectory(target.toPath(), source.toPath())
        removeOperationMarkerIfMatching(source, pending, setOf(JvmDataOperationRole.Target))
        updatePendingPhase(pending, JvmDataMigrationPhase.Prepared)
    }

    private fun cancelCopiedMigration(pending: PendingChange) {
        validateSourceOwnership(pending.source)
        requireRootIdentity(pending.source, pending.requireSourceRootId(), "源数据目录身份不匹配。")
        rejectLinksInTree(pending.source)
        removeOperationMarkerIfMatching(pending.source, pending, setOf(JvmDataOperationRole.Cleanup))
        val staging = stagingDirectory(pending.target)
        if (staging.existsNoFollow()) {
            deletePendingDirectory(staging, pending, setOf(JvmDataOperationRole.Staging))
        }
        if (pending.target.existsNoFollow() && !sameLocation(configuredActiveRootDirectory(), pending.target)) {
            deletePendingDirectory(
                pending.target,
                pending,
                setOf(JvmDataOperationRole.Target, JvmDataOperationRole.Staging),
            )
        }
    }

    private fun rollbackDiscard(pending: PendingChange) {
        val tombstone = tombstoneDirectory(pending.source)
        if (tombstone.existsNoFollow()) {
            require(!pending.source.existsNoFollow()) { "源目录和待删除目录同时存在，拒绝自动恢复。" }
            requireRootIdentity(tombstone, pending.requireSourceRootId(), "待删除目录身份不匹配。")
            requireOperationMarker(tombstone, pending, setOf(JvmDataOperationRole.Tombstone))
            moveDirectory(tombstone.toPath(), pending.source.toPath())
            removeOperationMarkerIfMatching(pending.source, pending, setOf(JvmDataOperationRole.Tombstone))
        } else {
            validateSourceOwnership(pending.source)
            requireRootIdentity(pending.source, pending.requireSourceRootId(), "源数据目录身份不匹配。")
            rejectRootLink(pending.source)
            removeOperationMarkerIfMatching(
                pending.source,
                pending,
                setOf(JvmDataOperationRole.Tombstone, JvmDataOperationRole.Cleanup),
            )
        }
        if (pending.target.existsNoFollow() && !sameLocation(configuredActiveRootDirectory(), pending.target)) {
            deletePendingDirectory(pending.target, pending, setOf(JvmDataOperationRole.Target))
        }
    }

    private fun validatePending(pending: PendingChange) {
        val active = configuredActiveRoot()
        require(sameLocation(pending.source, active.directory)) {
            "迁移配置损坏：源目录与当前活动数据目录不一致。"
        }
        if (pending.hasOperationIdentity) {
            require(active.rootId == pending.sourceRootId) {
                "迁移配置损坏：源目录身份与当前活动数据目录不一致。"
            }
            if (pending.strategy == JvmDataMigrationStrategy.Move) {
                require(pending.targetRootId == pending.sourceRootId) {
                    "迁移配置损坏：同盘移动必须保留原数据目录身份。"
                }
            } else {
                require(pending.targetRootId != pending.sourceRootId) {
                    "迁移配置损坏：新数据目录身份不能与源目录相同。"
                }
            }
        }
        when (pending.mode) {
            AppDataLocationChangeMode.Discard -> require(pending.strategy == JvmDataMigrationStrategy.Discard) {
                "迁移配置损坏：丢弃模式只能使用 discard 策略。"
            }

            AppDataLocationChangeMode.Migrate -> require(
                pending.strategy == JvmDataMigrationStrategy.Move ||
                    pending.strategy == JvmDataMigrationStrategy.Copy,
            ) {
                "迁移配置损坏：迁移模式只能使用 move 或 copy 策略。"
            }
        }
        validateTargetTopology(pending.source, pending.target)
    }

    private fun ensurePendingIdentity(pending: PendingChange): PendingChange {
        if (pending.hasOperationIdentity) return pending
        validatePending(pending)
        listOf(
            pending.target,
            stagingDirectory(pending.target),
            tombstoneDirectory(pending.source),
        ).forEach(::requireSafeLegacyResidual)
        val sourceRootId = ensureActiveRootIdentity(pending.source)
        val identified = pending.copy(
            operationId = newIdentity(),
            sourceRootId = sourceRootId,
            targetRootId = if (pending.strategy == JvmDataMigrationStrategy.Move) sourceRootId else newIdentity(),
        )
        synchronized(propertyLock) {
            val properties = loadProperties()
            val current = loadPending(properties)
            require(current == pending && !pending.hasOperationIdentity) { "迁移配置已发生变化。" }
            properties.setProperty(KEY_PENDING_ID, identified.requireOperationId())
            properties.setProperty(KEY_PENDING_SOURCE_ROOT_ID, identified.requireSourceRootId())
            properties.setProperty(KEY_PENDING_TARGET_ROOT_ID, identified.requireTargetRootId())
            persist(properties)
        }
        return identified
    }

    private fun requireSafeLegacyResidual(directory: File) {
        if (!directory.existsNoFollow()) return
        rejectRootLink(directory)
        require(directory.isDirectory && directory.listFiles()?.isEmpty() == true) {
            "旧版迁移记录缺少操作身份，且存在无法安全确认的残留目录：${directory.absolutePath}"
        }
    }

    private fun validateTargetTopology(source: File, target: File) {
        val sourcePath = source.toPath()
        val targetPath = target.toPath()
        val canonicalSourcePath = source.canonicalFile.toPath()
        requireUi(target.name == TARGET_DIRECTORY_NAME) { uiText(Res.string.data_location_target_name_required, TARGET_DIRECTORY_NAME) }
        val parent = target.parentFile ?: throw UiTextException(uiText(Res.string.data_location_disk_root_unsupported))
        requireUi(parent.isDirectory) { uiText(Res.string.data_location_selected_directory_missing) }
        val canonicalTargetPath = if (target.existsNoFollow()) {
            target.canonicalFile.toPath()
        } else {
            parent.canonicalFile.toPath().resolve(target.name).normalize()
        }
        requireUi(sourcePath != targetPath) { uiText(Res.string.data_location_target_unchanged) }
        requireUi(
            !sourcePath.startsWith(targetPath) &&
                !targetPath.startsWith(sourcePath) &&
                !canonicalSourcePath.startsWith(canonicalTargetPath) &&
                !canonicalTargetPath.startsWith(canonicalSourcePath),
        ) {
            uiText(Res.string.data_location_target_overlaps_current)
        }
    }

    private fun validateTargetAvailability(source: File, target: File, strategy: JvmDataMigrationStrategy) {
        validateTargetParentAvailability(source, target, strategy)
        if (target.existsNoFollow()) {
            rejectRootLink(target)
            requireUi(target.isDirectory && target.listFiles().orEmpty().isEmpty()) { uiText(Res.string.data_location_target_must_be_empty) }
        }
    }

    private fun validateTargetParentAvailability(source: File, target: File, strategy: JvmDataMigrationStrategy) {
        val parent = target.parentFile ?: throw UiTextException(uiText(Res.string.data_location_disk_root_unsupported))
        val probe = File.createTempFile("lynmusic-location-", ".tmp", parent)
        checkUi(probe.delete()) { uiText(Res.string.data_location_write_access_unverified) }
        val required = when (strategy) {
            JvmDataMigrationStrategy.Move, JvmDataMigrationStrategy.Discard -> MINIMUM_FREE_SPACE_RESERVE_BYTES
            JvmDataMigrationStrategy.Copy ->
                directoryStats(source, ignoredRootNames = DATABASE_FILE_NAMES + MIGRATION_METADATA_FILE_NAMES).bytes +
                    databaseFileSize(source) + MINIMUM_FREE_SPACE_RESERVE_BYTES
        }
        requireUi(parent.usableSpace >= required) { uiText(Res.string.data_location_insufficient_space) }
    }

    private fun validateSourceDatabaseCollection(source: File) {
        val database = File(source, DATABASE_FILE_NAME).toPath()
        if (!Files.exists(database, NOFOLLOW_LINKS)) {
            val artifacts = existingDatabaseArtifacts(source)
            check(artifacts.isEmpty()) {
                "源数据库主文件不存在，但发现孤立数据库文件：${artifacts.joinToString()}"
            }
            return
        }
        verifyDatabaseIntegrityAndReadVersion(database, "源")
    }

    private fun validateSourceOwnership(source: File) {
        rejectRootLink(source)
        requireUi(source.existsNoFollow() && source.isDirectory) { uiText(Res.string.data_location_current_missing) }
        requireUi(source == defaultRoot || source.isOwnedDirectory()) {
            uiText(Res.string.data_location_current_unowned)
        }
    }

    private fun validateActiveRoot(active: ConfiguredActiveRoot) {
        if (active.directory == defaultRoot) {
            if (!active.directory.existsNoFollow()) {
                requireUi(active.rootId == null) { uiText(Res.string.data_location_default_missing_identity) }
                return
            }
            rejectRootLink(active.directory)
            requireUi(Files.isDirectory(active.directory.toPath(), NOFOLLOW_LINKS)) {
                uiText(Res.string.data_location_default_not_directory)
            }
            validateConfiguredRootIdentity(active)
            return
        }
        requireUi(active.directory.name == TARGET_DIRECTORY_NAME) {
            uiText(Res.string.data_location_current_name_required, TARGET_DIRECTORY_NAME)
        }
        validateSourceOwnership(active.directory)
        validateConfiguredRootIdentity(active)
    }

    private fun validateConfiguredRootIdentity(active: ConfiguredActiveRoot) {
        val configuredRootId = active.rootId ?: return
        val marker = readRootMarker(active.directory)
            ?: throw UiTextException(uiText(Res.string.data_location_current_marker_missing))
        requireUi(marker.rootId == configuredRootId) {
            uiText(Res.string.data_location_current_identity_mismatch)
        }
    }

    private fun requireRootIdentity(directory: File, rootId: String, message: String) {
        requireOwnedDirectory(directory, message)
        val marker = readRootMarker(directory)
        require(marker?.rootId == rootId) { message }
    }

    private fun ensureActiveRootIdentity(source: File): String {
        validateSourceOwnership(source)
        val existing = readRootMarker(source)
        val rootId = existing?.rootId ?: newIdentity()
        if (existing?.rootId == null) writeRootMarker(source, rootId)
        synchronized(propertyLock) {
            val properties = loadProperties()
            val active = configuredActiveRoot(properties)
            requireUi(sameLocation(active.directory, source)) { uiText(Res.string.data_location_active_changed) }
            active.rootId?.let { configuredId ->
                requireUi(configuredId == rootId) { uiText(Res.string.data_location_active_identity_changed) }
            }
            properties.setProperty(KEY_ACTIVE_ROOT_ID, rootId)
            persist(properties)
        }
        return rootId
    }

    private fun removeEmptyTarget(target: File) {
        if (!target.existsNoFollow()) return
        rejectRootLink(target)
        requireUi(target.listFiles().orEmpty().isEmpty()) { uiText(Res.string.data_location_target_must_be_empty) }
        Files.delete(target.toPath())
    }

    private fun copyNonDatabaseFiles(
        source: File,
        target: File,
        onProgress: (JvmDataLocationProgress) -> Unit,
    ) {
        val sourcePath = source.toPath()
        val targetPath = target.toPath()
        val totalBytes = directoryStats(source, DATABASE_FILE_NAMES + MIGRATION_METADATA_FILE_NAMES).bytes.coerceAtLeast(1L)
        var copiedBytes = 0L
        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                rejectLink(dir, attrs)
                Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                rejectLink(file, attrs)
                val relative = sourcePath.relativize(file)
                if (!isRootDatabaseFile(relative) && !isRootMigrationMetadata(relative)) {
                    val output = targetPath.resolve(relative)
                    Files.createDirectories(output.parent)
                    Files.copy(file, output, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
                    copiedBytes += attrs.size()
                    onProgress.report(
                        JvmDataLocationProgress(
                            uiText(Res.string.startup_copying_data),
                            (copiedBytes.toFloat() / totalBytes).coerceIn(0f, 0.8f),
                        ),
                    )
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun backupDatabaseLogically(source: File, staging: File) {
        val sourceDatabase = File(source, DATABASE_FILE_NAME)
        if (!sourceDatabase.isFile) return
        val targetDatabase = File(staging, DATABASE_FILE_NAME)
        Files.deleteIfExists(targetDatabase.toPath())
        BundledSQLiteDriver().open(sourceDatabase.absolutePath).use { connection ->
            connection.exec("PRAGMA wal_checkpoint(TRUNCATE)")
            connection.exec("VACUUM INTO '${targetDatabase.absolutePath.replace("'", "''")}'")
        }
    }

    private fun prepareDatabaseForMigration(root: File, role: String) {
        val databaseFile = File(root, DATABASE_FILE_NAME).toPath()
        if (!Files.exists(databaseFile, NOFOLLOW_LINKS)) return
        check(Files.isRegularFile(databaseFile, NOFOLLOW_LINKS)) { "${role}数据库不是普通文件。" }
        val database = openLynMusicDatabase(
            Room.databaseBuilder<LynMusicDatabase>(name = databaseFile.toAbsolutePath().toString()),
        ).getOrThrow()
        database.close()
    }

    private fun verifyDatabasePairBeforeUpgrade(source: File, target: File) {
        verifyDatabasePair(source, target, requireMatchingVersion = false)
    }

    private fun verifyDatabasePairAfterUpgrade(source: File, target: File) {
        verifyDatabasePair(source, target, requireMatchingVersion = true)
    }

    private fun verifyDatabasePair(
        source: File,
        target: File,
        requireMatchingVersion: Boolean,
    ) {
        val sourceDatabase = File(source, DATABASE_FILE_NAME).toPath()
        val targetDatabase = File(target, DATABASE_FILE_NAME).toPath()
        val sourceExists = Files.exists(sourceDatabase, NOFOLLOW_LINKS)
        val targetExists = Files.exists(targetDatabase, NOFOLLOW_LINKS)
        if (!sourceExists) {
            val sourceArtifacts = existingDatabaseArtifacts(source)
            val targetArtifacts = existingDatabaseArtifacts(target)
            check(sourceArtifacts.isEmpty()) {
                "源数据库主文件不存在，但发现孤立数据库文件：${sourceArtifacts.joinToString()}"
            }
            check(targetArtifacts.isEmpty()) {
                "源数据库不存在，但目标目录中出现了意外数据库文件：${targetArtifacts.joinToString()}"
            }
            return
        }
        check(Files.isRegularFile(sourceDatabase, NOFOLLOW_LINKS)) { "源数据库不是普通文件。" }
        check(targetExists && Files.isRegularFile(targetDatabase, NOFOLLOW_LINKS)) { "目标数据库缺失或不是普通文件。" }
        val targetVersion = verifyDatabaseIntegrityAndReadVersion(targetDatabase, "目标")
        if (requireMatchingVersion) {
            val sourceVersion = verifyDatabaseIntegrityAndReadVersion(sourceDatabase, "源")
            check(targetVersion == sourceVersion) {
                "源数据库与目标数据库版本不一致。"
            }
        }
    }

    private fun verifyDatabaseIntegrityAndReadVersion(database: Path, role: String): Long {
        check(Files.exists(database, NOFOLLOW_LINKS)) { "${role}数据库不存在。" }
        check(Files.isRegularFile(database, NOFOLLOW_LINKS)) { "${role}数据库不是普通文件。" }
        check(Files.size(database) > 0L) { "${role}数据库为空。" }
        val realParent = requireNotNull(database.parent) { "${role}数据库缺少父目录。" }.toRealPath()
        val noFollowDatabase = realParent.resolve(database.fileName)
        return BundledSQLiteDriver().open(
            noFollowDatabase.toString(),
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW,
        ).use { connection ->
            connection.prepare("PRAGMA quick_check").use { statement ->
                check(statement.step() && statement.getText(0).equals("ok", ignoreCase = true)) {
                    "${role}数据库完整性校验失败。"
                }
            }
            connection.singleLong("PRAGMA user_version").also { version ->
                check(version > 0L) { "${role}数据库 schema 版本无效。" }
            }
        }
    }

    private fun existingDatabaseArtifacts(root: File): List<String> {
        return DATABASE_FILE_NAMES.filter { name -> File(root, name).existsNoFollow() }
    }

    private fun verifyNonDatabaseCopy(source: File, target: File) {
        val ignored = DATABASE_FILE_NAMES + MIGRATION_METADATA_FILE_NAMES
        checkUi(relativeFileSizes(source, ignored) == relativeFileSizes(target, ignored)) {
            uiText(Res.string.startup_copy_verification_failed)
        }
    }

    private fun updatePendingPhase(pending: PendingChange, phase: JvmDataMigrationPhase) {
        synchronized(propertyLock) {
            val properties = loadProperties()
            val current = loadPending(properties)
            require(current == pending) { "迁移配置已发生变化。" }
            properties.setProperty(KEY_PENDING_PHASE, phase.configValue)
            persist(properties)
        }
    }

    private fun commitActiveRoot(pending: PendingChange, cleanupRoot: File?) {
        val target = pending.target
        requireRootIdentity(target, pending.requireTargetRootId(), "目标数据目录不可用，拒绝切换活动位置。")
        requireOperationMarker(target, pending, setOf(JvmDataOperationRole.Target))
        synchronized(propertyLock) {
            val properties = loadProperties()
            val currentPending = requireNotNull(loadPending(properties)) {
                "迁移配置已不存在，拒绝提交数据位置。"
            }
            require(currentPending == pending) { "迁移配置已发生变化，拒绝提交数据位置。" }
            when (val cleanup = loadCleanupRootState(properties)) {
                CleanupRootState.None -> Unit
                is CleanupRootState.InvalidPath -> clearInvalidCleanupRoot(properties, cleanup)
                is CleanupRootState.InvalidPhase -> throw UiTextException(cleanup.text)
                is CleanupRootState.InvalidIdentity -> throw UiTextException(cleanup.text)
                is CleanupRootState.Valid -> error("旧数据目录尚未清理完成，拒绝覆盖清理记录。")
            }
            properties.setProperty(KEY_ACTIVE_ROOT, target.absolutePath)
            properties.setProperty(KEY_ACTIVE_ROOT_ID, pending.requireTargetRootId())
            clearPending(properties)
            if (cleanupRoot != null) {
                properties.setProperty(KEY_CLEANUP_ROOT, cleanupRoot.absolutePath)
                properties.setProperty(KEY_CLEANUP_PHASE, JvmCleanupPhase.Pending.configValue)
                properties.setProperty(KEY_CLEANUP_ROOT_ID, pending.requireSourceRootId())
                properties.setProperty(KEY_CLEANUP_OPERATION_ID, pending.requireOperationId())
            } else {
                properties.remove(KEY_CLEANUP_ROOT)
                properties.remove(KEY_CLEANUP_PHASE)
                properties.remove(KEY_CLEANUP_ROOT_ID)
                properties.remove(KEY_CLEANUP_OPERATION_ID)
            }
            persist(properties)
        }
        runCatching {
            removeOperationMarkerIfMatching(target, pending, setOf(JvmDataOperationRole.Target))
        }
    }

    private fun retryCleanupAtStartup() {
        runCatching { finishPendingCleanup() }.onFailure { cleanupWarning = cleanupFailureMessage(it) }
    }

    private fun finishCleanupOrRecordWarning() {
        runCatching { finishPendingCleanup() }.onFailure { cleanupWarning = cleanupFailureMessage(it) }
    }

    private fun finishPendingCleanup() {
        val cleanupState = synchronized(propertyLock) { loadCleanupRootState(loadProperties()) }
        val cleanup = when (cleanupState) {
            CleanupRootState.None -> return
            is CleanupRootState.InvalidPath -> {
                synchronized(propertyLock) {
                    val properties = loadProperties()
                    clearInvalidCleanupRoot(properties, cleanupState)
                }
                return
            }
            is CleanupRootState.InvalidPhase -> throw UiTextException(cleanupState.text)
            is CleanupRootState.InvalidIdentity -> {
                if (!cleanupState.directory.existsNoFollow()) {
                    synchronized(propertyLock) {
                        val properties = loadProperties()
                        clearCleanup(properties)
                        persist(properties)
                    }
                    return
                }
                throw UiTextException(cleanupState.text)
            }
            is CleanupRootState.Valid -> cleanupState
        }
        val committedCleanup = if (cleanup.directory.existsNoFollow()) {
            deleteRecordedCleanupDirectory(cleanup)
        } else {
            cleanup
        }
        synchronized(propertyLock) {
            val properties = loadProperties()
            require(loadCleanupRootState(properties) == committedCleanup) {
                "旧数据清理配置已发生变化，拒绝清除新记录。"
            }
            clearCleanup(properties)
            persist(properties)
        }
    }

    private fun deleteRecordedCleanupDirectory(cleanup: CleanupRootState.Valid): CleanupRootState.Valid {
        val candidate = cleanup.directory.normalized()
        rejectRootLink(candidate)
        val active = currentRootDirectory().normalized()
        require(candidate != active && candidate.canonicalFile != active.canonicalFile) {
            "拒绝删除当前活动数据目录。"
        }
        require(candidate.parentFile != null && candidate.toPath() != candidate.toPath().root) { "拒绝删除磁盘根目录。" }
        when (cleanup.phase) {
            JvmCleanupPhase.Pending -> {
                requireCleanupIdentity(candidate, cleanup)
                val deleting = cleanup.copy(phase = JvmCleanupPhase.Deleting)
                updateCleanupPhase(cleanup, deleting)
                deleteCleanupDirectory(candidate, deleting)
                return deleting
            }
            JvmCleanupPhase.Deleting -> {
                deleteCleanupDirectory(candidate, cleanup)
                return cleanup
            }
        }
    }

    private fun requireCleanupIdentity(directory: File, cleanup: CleanupRootState.Valid) {
        requireRootIdentity(directory, cleanup.rootId, "旧数据目录身份与清理记录不一致。")
        val marker = readOperationMarker(directory)
            ?: error("旧数据目录缺少 cleanup operation 标记。")
        require(marker.operationId == cleanup.operationId && marker.rootId == cleanup.rootId) {
            "旧数据目录不属于当前清理任务。"
        }
        require(marker.role == JvmDataOperationRole.Cleanup || marker.role == JvmDataOperationRole.Tombstone) {
            "旧数据目录的清理角色无效。"
        }
    }

    private fun deleteCleanupDirectory(directory: File, cleanup: CleanupRootState.Valid) {
        val operationMarker = readOperationMarker(directory)
        val rootMarker = readRootMarker(directory)
        when {
            operationMarker != null && rootMarker != null -> {
                requireCleanupIdentity(directory, cleanup)
                safeDeleteOperationTree(directory.toPath())
            }

            operationMarker != null -> {
                require(
                    operationMarker.operationId == cleanup.operationId &&
                        operationMarker.rootId == cleanup.rootId &&
                        directory.listFiles().orEmpty().all { it.name == OPERATION_MARKER_NAME },
                ) { "清理目录标记不完整且目录非空，拒绝继续删除。" }
                Files.deleteIfExists(File(directory, OPERATION_MARKER_NAME).toPath())
                Files.deleteIfExists(directory.toPath())
            }

            else -> deleteEmptyAuthorizedDirectory(directory)
        }
    }

    private fun validatePendingDirectoryOwnership(
        directory: File,
        pending: PendingChange,
        allowedRoles: Set<JvmDataOperationRole>,
    ) {
        if (!directory.existsNoFollow()) return
        rejectRootLink(directory)
        require(Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS)) {
            "迁移目标不是普通目录：${directory.absolutePath}"
        }
        val children = directory.listFiles() ?: error("无法读取迁移目录：${directory.absolutePath}")
        if (children.isEmpty()) return
        val marker = requireOperationMarker(directory, pending, allowedRoles)
        val rootMarker = readRootMarker(directory)
        if (rootMarker == null) {
            require(children.all { it.name == OPERATION_MARKER_NAME }) {
                "迁移目录缺少根身份且目录非空：${directory.absolutePath}"
            }
        } else {
            requireRootIdentity(
                directory,
                marker.rootId,
                "迁移目录根身份与本次操作不一致：${directory.absolutePath}",
            )
        }
    }

    private fun deletePendingDirectory(
        directory: File,
        pending: PendingChange,
        allowedRoles: Set<JvmDataOperationRole>,
    ) {
        val candidate = directory.normalized()
        val active = configuredActiveRootDirectory().normalized()
        require(!sameLocation(candidate, active)) { "拒绝删除当前活动数据目录。" }
        require(candidate.parentFile != null && candidate.toPath() != candidate.toPath().root) { "拒绝删除磁盘根目录。" }
        if (!candidate.existsNoFollow()) return
        validatePendingDirectoryOwnership(candidate, pending, allowedRoles)
        val operationMarker = readOperationMarker(candidate)
        val rootMarker = readRootMarker(candidate)
        when {
            operationMarker != null && rootMarker != null -> safeDeleteOperationTree(candidate.toPath())
            operationMarker != null -> {
                require(
                    operationMarker.operationId == pending.requireOperationId() &&
                        operationMarker.role in allowedRoles &&
                        operationMarker.rootId == expectedRootId(pending, operationMarker.role) &&
                        candidate.listFiles().orEmpty().all { it.name == OPERATION_MARKER_NAME },
                ) { "迁移目录标记不完整且目录非空，拒绝继续删除。" }
                Files.deleteIfExists(File(candidate, OPERATION_MARKER_NAME).toPath())
                Files.deleteIfExists(candidate.toPath())
            }
            else -> deleteEmptyAuthorizedDirectory(candidate)
        }
    }

    private fun deleteEmptyAuthorizedDirectory(directory: File) {
        rejectRootLink(directory)
        val attributes = Files.readAttributes(directory.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        require(attributes.isDirectory && !attributes.isSymbolicLink && !attributes.isOther) {
            "拒绝删除不是普通目录的迁移残留。"
        }
        require(directory.listFiles()?.isEmpty() == true) {
            "迁移残留缺少 LynMusic 所有权标记且目录非空，拒绝自动删除。"
        }
        Files.delete(directory.toPath())
    }

    private fun updateCleanupPhase(
        currentCleanup: CleanupRootState.Valid,
        updatedCleanup: CleanupRootState.Valid,
    ) {
        synchronized(propertyLock) {
            val properties = loadProperties()
            val state = loadCleanupRootState(properties)
            require(state == currentCleanup) {
                "旧数据清理配置已发生变化。"
            }
            properties.setProperty(KEY_CLEANUP_PHASE, updatedCleanup.phase.configValue)
            persist(properties)
        }
    }

    private fun ensureRootMarker(directory: File, rootId: String) {
        val existing = readRootMarker(directory)
        require(existing?.rootId == null || existing.rootId == rootId) {
            "LynMusic 数据目录身份不匹配：${directory.absolutePath}"
        }
        writeRootMarker(directory, rootId)
    }

    private fun readRootMarker(directory: File): RootMarker? {
        val marker = File(directory, OWNERSHIP_MARKER_NAME).toPath()
        if (!Files.exists(marker, NOFOLLOW_LINKS)) return null
        require(
            Files.isRegularFile(marker, NOFOLLOW_LINKS) &&
                !isJvmLinkOrReparsePoint(marker),
        ) { "LynMusic 所有权标记不是普通文件。" }
        val properties = Properties().apply { Files.newInputStream(marker).use(::load) }
        return RootMarker(properties.getProperty(MARKER_ROOT_ID)?.let(::parseIdentity))
    }

    private fun writeRootMarker(directory: File, rootId: String) {
        if (!directory.existsNoFollow()) Files.createDirectories(directory.toPath())
        rejectRootLink(directory)
        val properties = Properties().apply {
            setProperty(MARKER_FORMAT, MARKER_FORMAT_VERSION)
            setProperty(MARKER_ROOT_ID, parseIdentity(rootId))
        }
        persistPropertiesFile(
            target = File(directory, OWNERSHIP_MARKER_NAME),
            properties = properties,
            comment = "LynMusic application data root",
        )
    }

    private fun writeOperationMarker(
        directory: File,
        pending: PendingChange,
        role: JvmDataOperationRole,
    ) {
        val properties = Properties().apply {
            setProperty(MARKER_FORMAT, MARKER_FORMAT_VERSION)
            setProperty(MARKER_OPERATION_ID, pending.requireOperationId())
            setProperty(MARKER_ROOT_ID, expectedRootId(pending, role))
            setProperty(MARKER_OPERATION_ROLE, role.configValue)
        }
        persistPropertiesFile(
            target = File(directory, OPERATION_MARKER_NAME),
            properties = properties,
            comment = "LynMusic data location operation",
        )
    }

    private fun readOperationMarker(directory: File): OperationMarker? {
        val marker = File(directory, OPERATION_MARKER_NAME).toPath()
        if (!Files.exists(marker, NOFOLLOW_LINKS)) return null
        require(
            Files.isRegularFile(marker, NOFOLLOW_LINKS) &&
                !isJvmLinkOrReparsePoint(marker),
        ) { "迁移操作标记不是普通文件：${directory.absolutePath}" }
        val properties = Properties().apply { Files.newInputStream(marker).use(::load) }
        val roleValue = properties.getProperty(MARKER_OPERATION_ROLE)
            ?: error("迁移操作标记缺少 role：${directory.absolutePath}")
        return OperationMarker(
            operationId = properties.getProperty(MARKER_OPERATION_ID)?.let(::parseIdentity)
                ?: error("迁移操作标记缺少 operation ID：${directory.absolutePath}"),
            rootId = properties.getProperty(MARKER_ROOT_ID)?.let(::parseIdentity)
                ?: error("迁移操作标记缺少 root ID：${directory.absolutePath}"),
            role = JvmDataOperationRole.entries.firstOrNull { it.configValue == roleValue }
                ?: error("迁移操作标记包含非法 role=$roleValue：${directory.absolutePath}"),
        )
    }

    private fun requireOperationMarker(
        directory: File,
        pending: PendingChange,
        allowedRoles: Set<JvmDataOperationRole>,
    ): OperationMarker {
        val marker = readOperationMarker(directory)
            ?: error("迁移目录缺少本次操作标记：${directory.absolutePath}")
        require(marker.operationId == pending.requireOperationId()) {
            "迁移目录不属于本次操作：${directory.absolutePath}"
        }
        require(marker.role in allowedRoles) {
            "迁移目录角色不符合当前阶段：${directory.absolutePath}"
        }
        require(marker.rootId == expectedRootId(pending, marker.role)) {
            "迁移目录身份不符合当前记录：${directory.absolutePath}"
        }
        return marker
    }

    private fun removeOperationMarkerIfMatching(
        directory: File,
        pending: PendingChange,
        allowedRoles: Set<JvmDataOperationRole> = JvmDataOperationRole.entries.toSet(),
    ) {
        if (!directory.existsNoFollow()) return
        val marker = readOperationMarker(directory) ?: return
        require(marker.operationId == pending.requireOperationId() && marker.role in allowedRoles) {
            "拒绝移除不属于本次操作的迁移标记：${directory.absolutePath}"
        }
        Files.deleteIfExists(File(directory, OPERATION_MARKER_NAME).toPath())
    }

    private fun expectedRootId(pending: PendingChange, role: JvmDataOperationRole): String = when (role) {
        JvmDataOperationRole.Staging,
        JvmDataOperationRole.Target,
        -> pending.requireTargetRootId()

        JvmDataOperationRole.Tombstone,
        JvmDataOperationRole.Cleanup,
        -> pending.requireSourceRootId()
    }

    private fun requireEmptyOperationDirectory(
        directory: File,
        pending: PendingChange,
        role: JvmDataOperationRole,
    ) {
        requireRootIdentity(directory, expectedRootId(pending, role), "新的数据目录不可用，无法恢复操作。")
        requireOperationMarker(directory, pending, setOf(role))
        require(
            directory.listFiles().orEmpty().all {
                it.name == OWNERSHIP_MARKER_NAME || it.name == OPERATION_MARKER_NAME
            },
        ) { "新的数据目录包含意外文件，无法恢复操作。" }
    }

    private fun resolveCleanupRootDirectory(): File? = synchronized(propertyLock) {
        val properties = loadProperties()
        when (val state = loadCleanupRootState(properties)) {
            is CleanupRootState.InvalidPath -> {
                clearInvalidCleanupRoot(properties, state)
                null
            }
            CleanupRootState.None -> null
            is CleanupRootState.Valid -> state.directory
            is CleanupRootState.InvalidPhase -> {
                cleanupWarning = state.text
                state.directory
            }
            is CleanupRootState.InvalidIdentity -> {
                cleanupWarning = state.text
                state.directory
            }
        }
    }

    private fun loadCleanupRootState(properties: Properties): CleanupRootState {
        val rawValue = properties.getProperty(KEY_CLEANUP_ROOT) ?: return CleanupRootState.None
        val directory = rawValue.trim().takeIf { it.isNotEmpty() }?.let(::safeAbsoluteFile)
            ?: return CleanupRootState.InvalidPath(rawValue)
        val phaseValue = properties.getProperty(KEY_CLEANUP_PHASE)
        val phase = phaseValue?.let { value ->
            JvmCleanupPhase.entries.firstOrNull { it.configValue == value }
                ?: return CleanupRootState.InvalidPhase(
                    directory = directory,
                    rawValue = value,
                )
        } ?: JvmCleanupPhase.Pending
        val rootIdValue = properties.getProperty(KEY_CLEANUP_ROOT_ID)
        val operationIdValue = properties.getProperty(KEY_CLEANUP_OPERATION_ID)
        if (rootIdValue == null || operationIdValue == null) {
            return CleanupRootState.InvalidIdentity(
                directory = directory,
                detail = uiText(Res.string.startup_cleanup_identity_missing),
            )
        }
        val rootId = runCatching { parseIdentity(rootIdValue) }.getOrElse {
            return CleanupRootState.InvalidIdentity(directory, uiText(Res.string.startup_cleanup_identity_value_invalid, "cleanup_root_id", rootIdValue))
        }
        val operationId = runCatching { parseIdentity(operationIdValue) }.getOrElse {
            return CleanupRootState.InvalidIdentity(directory, uiText(Res.string.startup_cleanup_identity_value_invalid, "cleanup_operation_id", operationIdValue))
        }
        return CleanupRootState.Valid(directory, phase, rootId, operationId)
    }

    private fun clearInvalidCleanupRoot(properties: Properties, state: CleanupRootState.InvalidPath) {
        clearCleanup(properties)
        persist(properties)
        cleanupWarning = uiText(Res.string.startup_cleanup_record_ignored, UiText.Raw(state.rawValue))
    }

    private fun clearCleanup(properties: Properties) {
        properties.remove(KEY_CLEANUP_ROOT)
        properties.remove(KEY_CLEANUP_PHASE)
        properties.remove(KEY_CLEANUP_ROOT_ID)
        properties.remove(KEY_CLEANUP_OPERATION_ID)
    }

    private fun requireOwnedDirectory(directory: File, message: String = "拒绝操作没有 LynMusic 所有权标记的目录。") {
        rejectRootLink(directory)
        require(directory.isOwnedDirectory()) { message }
    }

    private fun loadPending(properties: Properties): PendingChange? {
        if (PENDING_KEYS.none(properties::containsKey)) return null
        val source = properties.getProperty(KEY_PENDING_SOURCE)
            ?.let(::safeAbsoluteFile)
            ?: throw UiTextException(uiText(Res.string.startup_pending_path_invalid, "source"))
        val target = properties.getProperty(KEY_PENDING_TARGET)
            ?.let(::safeAbsoluteFile)
            ?: throw UiTextException(uiText(Res.string.startup_pending_path_invalid, "target"))
        val modeValue = properties.getProperty(KEY_PENDING_MODE)
            ?: throw UiTextException(uiText(Res.string.startup_pending_field_missing, "mode"))
        val mode = AppDataLocationChangeMode.entries.firstOrNull { it.name == modeValue }
            ?: throw UiTextException(uiText(Res.string.startup_pending_field_invalid, "mode", modeValue))
        val defaultStrategy = if (mode == AppDataLocationChangeMode.Discard) {
            JvmDataMigrationStrategy.Discard
        } else {
            JvmDataMigrationStrategy.Copy
        }
        val strategy = properties.getProperty(KEY_PENDING_STRATEGY)?.let { value ->
            JvmDataMigrationStrategy.entries.firstOrNull { it.configValue == value }
                ?: throw UiTextException(uiText(Res.string.startup_pending_field_invalid, "strategy", value))
        } ?: defaultStrategy
        val phase = properties.getProperty(KEY_PENDING_PHASE)?.let { value ->
            JvmDataMigrationPhase.entries.firstOrNull { it.configValue == value }
                ?: throw UiTextException(uiText(Res.string.startup_pending_field_invalid, "phase", value))
        } ?: JvmDataMigrationPhase.Prepared
        val identityValues = listOf(
            properties.getProperty(KEY_PENDING_ID),
            properties.getProperty(KEY_PENDING_SOURCE_ROOT_ID),
            properties.getProperty(KEY_PENDING_TARGET_ROOT_ID),
        )
        requireUi(identityValues.all { it == null } || identityValues.all { it != null }) {
            uiText(Res.string.startup_pending_identity_incomplete)
        }
        return PendingChange(
            source = source,
            target = target,
            mode = mode,
            strategy = strategy,
            phase = phase,
            operationId = identityValues[0]?.let(::parseIdentity),
            sourceRootId = identityValues[1]?.let(::parseIdentity),
            targetRootId = identityValues[2]?.let(::parseIdentity),
        )
    }

    private fun safelyClearCorruptPending(error: Throwable) {
        synchronized(propertyLock) {
            val properties = loadProperties()
            clearPending(properties)
            persist(properties)
        }
        pendingSafetyWarning = uiText(Res.string.startup_pending_record_cancelled, error.uiFailureTextOrNull() ?: UiText.Raw(error.message ?: error.toString()))
    }

    private fun loadProperties(): Properties {
        val configPath = configFile.toPath()
        val attributes = try {
            Files.readAttributes(configPath, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            return Properties()
        } catch (error: Exception) {
            throw IllegalStateException(
                "无法读取数据位置配置：${configFile.absolutePath}",
                error,
            )
        }
        check(attributes.isRegularFile) {
            "数据位置配置不是普通文件：${configFile.absolutePath}"
        }
        return Properties().also { properties ->
            try {
                Files.newInputStream(configPath).use(properties::load)
            } catch (error: Exception) {
                throw IllegalStateException(
                    "无法读取数据位置配置：${configFile.absolutePath}",
                    error,
                )
            }
        }
    }

    private fun persist(properties: Properties) {
        configFile.parentFile?.mkdirs()
        val temporary = File.createTempFile("${configFile.name}.", ".tmp", configFile.parentFile)
        try {
            temporary.outputStream().use { properties.store(it, "LynMusic data location") }
            moveFileReplacing(temporary.toPath(), configFile.toPath())
        } finally {
            temporary.delete()
        }
    }

    private fun clearPending(properties: Properties) {
        properties.remove(KEY_PENDING_SOURCE)
        properties.remove(KEY_PENDING_TARGET)
        properties.remove(KEY_PENDING_MODE)
        properties.remove(KEY_PENDING_STRATEGY)
        properties.remove(KEY_PENDING_PHASE)
        properties.remove(KEY_PENDING_ID)
        properties.remove(KEY_PENDING_SOURCE_ROOT_ID)
        properties.remove(KEY_PENDING_TARGET_ROOT_ID)
    }

    private data class PendingChange(
        val source: File,
        val target: File,
        val mode: AppDataLocationChangeMode,
        val strategy: JvmDataMigrationStrategy,
        val phase: JvmDataMigrationPhase,
        val operationId: String?,
        val sourceRootId: String?,
        val targetRootId: String?,
    ) {
        val hasOperationIdentity: Boolean
            get() = operationId != null && sourceRootId != null && targetRootId != null

        fun requireOperationId(): String = requireNotNull(operationId) { "迁移记录缺少 operation ID。" }
        fun requireSourceRootId(): String = requireNotNull(sourceRootId) { "迁移记录缺少源目录 ID。" }
        fun requireTargetRootId(): String = requireNotNull(targetRootId) { "迁移记录缺少目标目录 ID。" }
    }

    private data class ConfiguredActiveRoot(
        val directory: File,
        val rootId: String?,
    )

    private data class RootMarker(val rootId: String?)

    private data class OperationMarker(
        val operationId: String,
        val rootId: String,
        val role: JvmDataOperationRole,
    )

    private sealed interface CleanupRootState {
        data object None : CleanupRootState
        data class Valid(
            val directory: File,
            val phase: JvmCleanupPhase,
            val rootId: String,
            val operationId: String,
        ) : CleanupRootState
        data class InvalidPath(val rawValue: String) : CleanupRootState
        data class InvalidPhase(val directory: File, val rawValue: String) : CleanupRootState {
            val text: UiText get() = uiText(Res.string.startup_cleanup_phase_invalid, rawValue)
        }
        data class InvalidIdentity(val directory: File, val detail: UiText) : CleanupRootState {
            val text: UiText get() = uiText(Res.string.startup_cleanup_identity_invalid, detail)
        }
    }
}

internal class JvmAppDataLocationPlatformService(
    private val manager: JvmDataLocationManager,
) : AppDataLocationPlatformService {
    override val currentDataRootPath: String
        get() = manager.currentRootDirectory().absolutePath
    override val pendingCleanupRootPath: String?
        get() = manager.pendingCleanupRootPath()

    override suspend fun pickTargetDataRoot(): Result<String?> = runCatching {
        val parent = JvmNativeFilePicker.pickDirectory(resolveUiString(Res.string.desktop_picker_data_location_title)) ?: return@runCatching null
        parent.resolve(TARGET_DIRECTORY_NAME).toAbsolutePath().normalize().toString()
    }

    override suspend fun scheduleChange(
        targetDataRootPath: String,
        mode: AppDataLocationChangeMode,
    ): Result<Unit> = runCatching { manager.scheduleChange(File(targetDataRootPath), mode) }

    override suspend fun retryPendingCleanup(): Result<Unit> = manager.retryPendingCleanup()
}

internal fun isJvmWindowsOs(osName: String): Boolean = osName.trim().startsWith("Windows", ignoreCase = true)

private fun rewriteMovedDatabasePaths(databaseRoot: File, sourceRoot: File, targetRoot: File) {
    val databaseFile = File(databaseRoot, DATABASE_FILE_NAME)
    if (!databaseFile.isFile) return
    val database = openLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = databaseFile.absolutePath),
    ).getOrThrow()
    try {
        runBlocking {
            database.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    val pathRelocations = listOf(
                        (sourceRoot.absolutePath + File.separator) to
                            (targetRoot.absolutePath + File.separator),
                        (sourceRoot.canonicalPath + File.separator) to
                            (targetRoot.canonicalPath + File.separator),
                    ).distinct()
                    database.importSourceDao().getAll().forEach { row ->
                        database.importSourceDao().upsert(
                            row.copy(
                                rootReference = relocateDataRootPath(row.rootReference, pathRelocations),
                                directoryPath = row.directoryPath?.let { relocateDataRootPath(it, pathRelocations) },
                            ),
                        )
                    }
                    database.trackDao().getAll().let { tracks ->
                        database.trackDao().upsertAll(
                            tracks.map { row ->
                                row.copy(
                                    mediaLocator = relocateDataRootPath(row.mediaLocator, pathRelocations),
                                    artworkLocator = row.artworkLocator?.let {
                                        relocateDataRootPath(it, pathRelocations)
                                    },
                                )
                            },
                        )
                    }
                    database.offlineDownloadDao().getAll().forEach { row ->
                        database.offlineDownloadDao().upsert(
                            row.copy(
                                localMediaLocator = row.localMediaLocator?.let {
                                    relocateDataRootPath(it, pathRelocations)
                                },
                            ),
                        )
                    }
                    listOf(
                        "UPDATE import_track_stage SET mediaLocator = REPLACE(mediaLocator, ?, ?) WHERE instr(mediaLocator, ?) = 1",
                        "UPDATE import_track_stage SET artworkLocator = REPLACE(artworkLocator, ?, ?) WHERE artworkLocator IS NOT NULL AND instr(artworkLocator, ?) = 1",
                        "UPDATE lyrics_cache SET artworkLocator = REPLACE(artworkLocator, ?, ?) WHERE artworkLocator IS NOT NULL AND instr(artworkLocator, ?) = 1",
                        "UPDATE offline_download SET localMediaLocator = REPLACE(localMediaLocator, ?, ?) WHERE localMediaLocator IS NOT NULL AND instr(localMediaLocator, ?) = 1",
                    ).forEach { sql ->
                        pathRelocations.forEach { (sourcePrefix, targetPrefix) ->
                            execSql(sql, sourcePrefix, targetPrefix, sourcePrefix)
                        }
                    }
                    pathRelocations.forEach { (sourcePrefix, targetPrefix) ->
                        val sourceJsonPrefix = sourcePrefix.replace("\\", "\\\\")
                        val targetJsonPrefix = targetPrefix.replace("\\", "\\\\")
                        execSql(
                            "UPDATE playback_queue_snapshot SET queueTracksJson = REPLACE(queueTracksJson, ?, ?), orderedQueueTracksJson = REPLACE(orderedQueueTracksJson, ?, ?)",
                            sourceJsonPrefix,
                            targetJsonPrefix,
                            sourceJsonPrefix,
                            targetJsonPrefix,
                        )
                    }
                }
            }
        }
    } finally {
        database.close()
    }
}

private fun relocateDataRootPath(value: String, relocations: List<Pair<String, String>>): String {
    return relocations.fold(value) { current, (sourcePrefix, targetPrefix) ->
        if (current.startsWith(sourcePrefix)) targetPrefix + current.removePrefix(sourcePrefix) else current
    }
}

private data class DirectoryStats(val files: Long, val bytes: Long)

private fun directoryStats(root: File, ignoredRootNames: Set<String> = emptySet()): DirectoryStats {
    val sizes = relativeFileSizes(root, ignoredRootNames)
    return DirectoryStats(sizes.size.toLong(), sizes.values.sum())
}

private fun relativeFileSizes(root: File, ignoredRootNames: Set<String>): Map<String, Long> {
    if (!root.existsNoFollow()) return emptyMap()
    val rootPath = root.toPath()
    val result = linkedMapOf<String, Long>()
    Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            rejectLink(dir, attrs)
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            rejectLink(file, attrs)
            val relative = rootPath.relativize(file)
            if (!(relative.nameCount == 1 && relative.fileName.toString() in ignoredRootNames)) {
                result[relative.toString()] = attrs.size()
            }
            return FileVisitResult.CONTINUE
        }
    })
    return result
}

private fun databaseFileSize(root: File): Long = DATABASE_FILE_NAMES.sumOf { File(root, it).takeIf(File::isFile)?.length() ?: 0L }

private fun rejectLinksInTree(root: File) {
    relativeFileSizes(root, emptySet())
}

private fun rejectRootLink(root: File) {
    if (!root.existsNoFollow()) return
    val attrs = Files.readAttributes(root.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    rejectLink(root.toPath(), attrs)
}

private fun rejectLink(path: Path, attrs: BasicFileAttributes) {
    if (isJvmLinkOrReparsePoint(path, attrs)) {
        val absolutePath = path.toAbsolutePath().toString()
        throw UiTextArgumentException(
            text = uiText(Res.string.data_location_links_unsupported, absolutePath),
            diagnosticMessage = "数据目录中包含不受支持的链接或 Junction：$absolutePath",
        )
    }
}

internal fun safeDeleteTree(root: Path) {
    if (!Files.exists(root, NOFOLLOW_LINKS)) return
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (isJvmLinkOrReparsePoint(dir, attrs)) {
                Files.deleteIfExists(dir)
                return FileVisitResult.SKIP_SUBTREE
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.deleteIfExists(file)
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw exc

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            if (exc != null) throw exc
            Files.deleteIfExists(dir)
            return FileVisitResult.CONTINUE
        }
    })
}

internal fun safeDeleteOwnedTree(
    root: Path,
    deletePath: (Path) -> Unit = { path -> Files.deleteIfExists(path) },
) {
    if (!Files.exists(root, NOFOLLOW_LINKS)) return
    val rootAttributes = Files.readAttributes(root, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    require(rootAttributes.isDirectory && !isJvmLinkOrReparsePoint(root, rootAttributes)) {
        "拒绝删除不是普通目录的 LynMusic 数据目录。"
    }
    val marker = root.resolve(OWNERSHIP_MARKER_NAME)
    require(Files.isRegularFile(marker, NOFOLLOW_LINKS) && !isJvmLinkOrReparsePoint(marker)) {
        "拒绝删除没有 LynMusic 所有权标记的目录。"
    }
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (isJvmLinkOrReparsePoint(dir, attrs)) {
                deletePath(dir)
                return FileVisitResult.SKIP_SUBTREE
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (file != marker) deletePath(file)
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw exc

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            if (exc != null) throw exc
            if (dir != root) deletePath(dir)
            return FileVisitResult.CONTINUE
        }
    })
    deletePath(marker)
    deletePath(root)
}

private fun safeDeleteOperationTree(root: Path) {
    val rootAttributes = Files.readAttributes(root, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    require(rootAttributes.isDirectory && !isJvmLinkOrReparsePoint(root, rootAttributes)) {
        "拒绝删除不是普通目录的迁移目录。"
    }
    val rootMarker = root.resolve(OWNERSHIP_MARKER_NAME)
    val operationMarker = root.resolve(OPERATION_MARKER_NAME)
    require(Files.isRegularFile(rootMarker, NOFOLLOW_LINKS) && !isJvmLinkOrReparsePoint(rootMarker)) {
        "拒绝删除没有 LynMusic 根目录标记的迁移目录。"
    }
    require(Files.isRegularFile(operationMarker, NOFOLLOW_LINKS) && !isJvmLinkOrReparsePoint(operationMarker)) {
        "拒绝删除没有 operation 标记的迁移目录。"
    }
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (isJvmLinkOrReparsePoint(dir, attrs)) {
                Files.deleteIfExists(dir)
                return FileVisitResult.SKIP_SUBTREE
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (file != rootMarker && file != operationMarker) Files.deleteIfExists(file)
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw exc

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            if (exc != null) throw exc
            if (dir != root) Files.deleteIfExists(dir)
            return FileVisitResult.CONTINUE
        }
    })
    Files.deleteIfExists(rootMarker)
    Files.deleteIfExists(operationMarker)
    Files.deleteIfExists(root)
}

internal fun isJvmLinkOrReparsePoint(path: Path): Boolean {
    if (!Files.exists(path, NOFOLLOW_LINKS)) return false
    val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    return isJvmLinkOrReparsePoint(path, attrs)
}

private fun isJvmLinkOrReparsePoint(path: Path, attrs: BasicFileAttributes): Boolean =
    attrs.isSymbolicLink || attrs.isOther || Files.isSymbolicLink(path) || isWindowsReparsePoint(path)

private fun isWindowsReparsePoint(path: Path): Boolean {
    if (!isJvmWindowsOs(System.getProperty("os.name").orEmpty())) return false
    val attributes = Kernel32.INSTANCE.GetFileAttributes(path.toAbsolutePath().toString())
    return attributes != INVALID_FILE_ATTRIBUTES &&
        attributes and WinNT.FILE_ATTRIBUTE_REPARSE_POINT != 0
}

private fun areOnSameFileStore(source: File, targetParent: File): Boolean? = runCatching {
    Files.getFileStore(source.toPath()) == Files.getFileStore(targetParent.toPath())
}.getOrNull()

private fun moveDirectory(source: Path, target: Path) {
    try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target)
    }
}

private fun moveFileReplacing(source: Path, target: Path) {
    try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun persistPropertiesFile(
    target: File,
    properties: Properties,
    comment: String,
) {
    val targetDirectory = requireNotNull(target.parentFile) { "标记文件缺少父目录。" }
    targetDirectory.mkdirs()
    val temporaryDirectory = targetDirectory.parentFile ?: targetDirectory
    val temporary = File.createTempFile(
        "${targetDirectory.name}-${target.name}.",
        ".tmp",
        temporaryDirectory,
    )
    try {
        temporary.outputStream().use { properties.store(it, comment) }
        moveFileReplacing(temporary.toPath(), target.toPath())
    } finally {
        temporary.delete()
    }
}

private fun safeAbsoluteFile(value: String): File? = runCatching {
    File(value).takeIf { it.isAbsolute }?.normalized()
}.getOrNull()

private fun parseIdentity(value: String): String = UUID.fromString(value.trim()).toString()

private fun newIdentity(): String = UUID.randomUUID().toString()

private fun File.normalized(): File = toPath().toAbsolutePath().normalize().toFile()

private fun sameLocation(first: File, second: File): Boolean {
    val normalizedFirst = first.normalized()
    val normalizedSecond = second.normalized()
    return normalizedFirst == normalizedSecond || normalizedFirst.canonicalFile == normalizedSecond.canonicalFile
}

private fun File.existsNoFollow(): Boolean = Files.exists(toPath(), NOFOLLOW_LINKS)

private fun File.isOwnedDirectory(): Boolean {
    if (!existsNoFollow()) return false
    val attrs = Files.readAttributes(toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    if (!attrs.isDirectory || attrs.isSymbolicLink || attrs.isOther) return false
    val marker = File(this, OWNERSHIP_MARKER_NAME).toPath()
    return Files.isRegularFile(marker, NOFOLLOW_LINKS) && !isJvmLinkOrReparsePoint(marker)
}

private fun stagingDirectory(target: File) = File(target.parentFile, ".${target.name}.migrating").normalized()

private fun tombstoneDirectory(source: File) = File(source.parentFile, ".${source.name}.discarding").normalized()

private fun isRootDatabaseFile(relative: Path): Boolean =
    relative.nameCount == 1 && relative.fileName.toString() in DATABASE_FILE_NAMES

private fun isRootMigrationMetadata(relative: Path): Boolean =
    relative.nameCount == 1 && relative.fileName.toString() in MIGRATION_METADATA_FILE_NAMES

private fun cleanupFailureMessage(error: Throwable): UiText =
    uiText(Res.string.startup_cleanup_failed, error.uiFailureTextOrNull() ?: UiText.Raw(error.message ?: error.toString()))

private fun ((JvmDataLocationProgress) -> Unit).report(progress: JvmDataLocationProgress) {
    runCatching { invoke(progress) }
}

private fun SQLiteConnection.exec(sql: String) {
    prepare(sql).use { it.step() }
}

private fun SQLiteConnection.singleLong(sql: String): Long {
    prepare(sql).use { statement ->
        check(statement.step()) { "查询未返回结果：$sql" }
        return statement.getLong(0)
    }
}

private suspend fun PooledConnection.execSql(sql: String, vararg args: Any?) {
    usePrepared(sql) { statement ->
        args.forEachIndexed { index, value -> statement.bindValue(index + 1, value) }
        statement.step()
    }
}

private fun SQLiteStatement.bindValue(index: Int, value: Any?) {
    when (value) {
        null -> bindNull(index)
        is String -> bindText(index, value)
        is Long -> bindLong(index, value)
        is Int -> bindLong(index, value.toLong())
        is Double -> bindDouble(index, value)
        is Float -> bindDouble(index, value.toDouble())
        is ByteArray -> bindBlob(index, value)
        is Boolean -> bindLong(index, if (value) 1L else 0L)
        else -> error("Unsupported SQLite argument type: ${value::class}")
    }
}

private const val TARGET_DIRECTORY_NAME = "LynMusic"
private const val OWNERSHIP_MARKER_NAME = ".lynmusic-data-root"
private const val OPERATION_MARKER_NAME = ".lynmusic-data-operation"
private const val MARKER_FORMAT = "format"
private const val MARKER_FORMAT_VERSION = "1"
private const val MARKER_ROOT_ID = "root_id"
private const val MARKER_OPERATION_ID = "operation_id"
private const val MARKER_OPERATION_ROLE = "role"
private val MIGRATION_METADATA_FILE_NAMES = setOf(OWNERSHIP_MARKER_NAME, OPERATION_MARKER_NAME)
private const val DATABASE_FILE_NAME = "lynmusic.db"
private val DATABASE_FILE_NAMES = setOf(
    DATABASE_FILE_NAME,
    "$DATABASE_FILE_NAME-wal",
    "$DATABASE_FILE_NAME-shm",
    "$DATABASE_FILE_NAME-journal",
    "$DATABASE_FILE_NAME.lck",
)
private const val MINIMUM_FREE_SPACE_RESERVE_BYTES = 16L * 1024L * 1024L
private const val INVALID_FILE_ATTRIBUTES = -1
private const val KEY_ACTIVE_ROOT = "active_data_root"
private const val KEY_ACTIVE_ROOT_ID = "active_root_id"
private const val KEY_PENDING_SOURCE = "pending_source_root"
private const val KEY_PENDING_TARGET = "pending_target_root"
private const val KEY_PENDING_MODE = "pending_mode"
private const val KEY_PENDING_STRATEGY = "pending_strategy"
private const val KEY_PENDING_PHASE = "pending_phase"
private const val KEY_PENDING_ID = "pending_id"
private const val KEY_PENDING_SOURCE_ROOT_ID = "pending_source_root_id"
private const val KEY_PENDING_TARGET_ROOT_ID = "pending_target_root_id"
private const val KEY_CLEANUP_ROOT = "cleanup_root"
private const val KEY_CLEANUP_PHASE = "cleanup_phase"
private const val KEY_CLEANUP_ROOT_ID = "cleanup_root_id"
private const val KEY_CLEANUP_OPERATION_ID = "cleanup_operation_id"
private val PENDING_KEYS = setOf(
    KEY_PENDING_SOURCE,
    KEY_PENDING_TARGET,
    KEY_PENDING_MODE,
    KEY_PENDING_STRATEGY,
    KEY_PENDING_PHASE,
    KEY_PENDING_ID,
    KEY_PENDING_SOURCE_ROOT_ID,
    KEY_PENDING_TARGET_ROOT_ID,
)
