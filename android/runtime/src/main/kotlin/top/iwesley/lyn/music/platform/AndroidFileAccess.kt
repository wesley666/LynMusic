package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.Settings
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.info
import java.io.File
import java.net.URI

internal data class AndroidStorageRoot(
    val label: String,
    val root: File,
    val isRemovable: Boolean,
    val uiLabel: UiText? = null,
)

internal fun hasManageAllFilesAccess(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
}

internal fun hasDirectLocalFileAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        hasManageAllFilesAccess(context)
    } else {
        hasLegacyExternalStorageReadWriteAccess(context)
    }
}

internal fun shouldRequestLegacyDirectLocalFileAccess(context: Context): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && !hasLegacyExternalStorageReadWriteAccess(context)
}

internal fun legacyDirectLocalFileAccessPermissions(): Array<String> {
    return arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )
}

internal fun legacyDirectLocalFileAccessGrantSummary(grants: Map<String, Boolean>): String {
    return "read=${grants[Manifest.permission.READ_EXTERNAL_STORAGE] == true} " +
        "write=${grants[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true}"
}

internal fun directLocalFileAccessPermissionLabel(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        "“管理所有文件”权限"
    } else {
        "存储读写权限"
    }
}

internal fun buildManageAllFilesAccessIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return Intent(Settings.ACTION_SETTINGS)
    }
    val appSpecificIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    return if (appSpecificIntent.resolveActivity(context.packageManager) != null) {
        appSpecificIntent
    } else {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
}

internal fun canResolveOpenDocumentTree(context: Context): Boolean {
    val packageName = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        .resolveActivity(context.packageManager)
        ?.packageName
    return isSupportedOpenDocumentTreePackage(packageName)
}

internal fun isSupportedOpenDocumentTreePackage(packageName: String?): Boolean {
    val normalized = packageName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return false
    return !normalized.equals(FRAMEWORK_PACKAGE_STUBS_PACKAGE, ignoreCase = true) &&
        !normalized.contains(FRAMEWORK_PACKAGE_STUBS_MARKER, ignoreCase = true)
}

@Suppress("DEPRECATION")
internal fun listAndroidStorageRoots(
    context: Context,
    logger: DiagnosticLogger = GlobalDiagnosticLogger,
): List<AndroidStorageRoot> {
    val primaryRoot = Environment.getExternalStorageDirectory()
    val normalizedPrimary = primaryRoot.canonicalFileOrSelf()
    val roots = linkedMapOf<String, AndroidStorageRoot>()

    fun addRoot(root: File, label: String, isRemovable: Boolean, source: String, uiLabel: UiText? = null) {
        if (!root.exists() || !root.isDirectory || !root.canRead()) {
            logger.info(LOCAL_IMPORT_LOG_TAG) {
                "storage-root-skip source=$source path=${root.absolutePath} exists=${root.exists()} " +
                    "directory=${root.isDirectory} readable=${root.canRead()}"
            }
            return
        }
        val normalizedRoot = root.canonicalFileOrSelf()
        if (!roots.containsKey(normalizedRoot.absolutePath)) {
            roots[normalizedRoot.absolutePath] = AndroidStorageRoot(
                label = label,
                uiLabel = uiLabel,
                root = normalizedRoot,
                isRemovable = isRemovable,
            )
            logger.info(LOCAL_IMPORT_LOG_TAG) {
                "storage-root-add source=$source label=$label path=${normalizedRoot.absolutePath} removable=$isRemovable"
            }
        } else {
            logger.info(LOCAL_IMPORT_LOG_TAG) {
                "storage-root-duplicate source=$source path=${normalizedRoot.absolutePath}"
            }
        }
    }

    logger.info(LOCAL_IMPORT_LOG_TAG) {
        "storage-root-enumerate-start sdk=${Build.VERSION.SDK_INT} " +
            "hasManageAllFilesAccess=${hasManageAllFilesAccess(context)} " +
            "hasDirectLocalFileAccess=${hasDirectLocalFileAccess(context)}"
    }

    addRoot(primaryRoot, label = "内置存储", isRemovable = false, source = "primary", uiLabel = uiText(Res.string.folder_picker_internal_storage))

    val storageVolumeRoots = listStorageVolumeRoots(context, logger)
    storageVolumeRoots.forEach { root ->
        addRoot(
            root = root.root,
            label = root.label,
            uiLabel = root.uiLabel,
            isRemovable = root.isRemovable,
            source = "storage-volume",
        )
    }

    val externalFilesDirs = context.getExternalFilesDirs(null).filterNotNull()
    logger.info(LOCAL_IMPORT_LOG_TAG) {
        "external-files-dirs ${externalFilesDirs.joinToString(separator = ";") { it.absolutePath }}"
    }
    externalFilesDirs
        .asSequence()
        .mapNotNull(File::findStorageVolumeRoot)
        .map(File::canonicalFileOrSelf)
        .filterNot { root ->
            root == normalizedPrimary ||
                normalizedPrimary.absolutePath.startsWith(root.absolutePath + File.separator)
        }
        .forEach { root ->
            addRoot(
                root = root,
                label = "U 盘 ${root.name}",
                uiLabel = uiText(Res.string.folder_picker_usb_drive_named, root.name),
                isRemovable = true,
                source = "external-files-dir",
            )
        }

    listMountedUsbFallbackRoots(logger).forEach { root ->
        addRoot(
            root = root,
            label = "U 盘 ${root.name}",
            uiLabel = uiText(Res.string.folder_picker_usb_drive_named, root.name),
            isRemovable = true,
            source = "mnt-usb-fallback",
        )
    }

    return roots.values.sortedWith(
        compareBy<AndroidStorageRoot> { it.isRemovable }
            .thenBy { it.label.lowercase() },
    ).also { sortedRoots ->
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "storage-root-enumerate-complete roots=${sortedRoots.joinToString(separator = ";") { root ->
                "${root.label}|${root.root.absolutePath}|removable=${root.isRemovable}"
            }}"
        }
    }
}

internal fun hasReadableAndroidUsbStorageRoot(
    context: Context,
    logger: DiagnosticLogger = GlobalDiagnosticLogger,
): Boolean {
    return listAndroidStorageRoots(context, logger).any { root ->
        root.isRemovable && root.root.exists() && root.root.isDirectory && root.root.canRead()
    }
}

internal fun isWithinReadableAndroidUsbStorageRoot(
    context: Context,
    file: File,
    logger: DiagnosticLogger = GlobalDiagnosticLogger,
): Boolean {
    val normalizedFile = file.canonicalFileOrSelf()
    val listedRootMatches = listAndroidStorageRoots(context, logger)
        .asSequence()
        .filter { root -> root.isRemovable && root.root.exists() && root.root.isDirectory && root.root.canRead() }
        .any { root -> normalizedFile.isWithinOrSame(root.root) }
    if (listedRootMatches) return true

    return listMountedUsbFallbackRoots(logger)
        .any { root -> normalizedFile.isWithinOrSame(root) }
}

internal fun resolveAndroidLocalTrackFile(locator: String): File? {
    val value = locator.trim()
    if (value.isBlank()) return null
    return runCatching {
        when {
            value.startsWith("file://", ignoreCase = true) -> File(URI(value))
            value.startsWith("/") -> File(value)
            Regex("^[A-Za-z]:[/\\\\].*").matches(value) -> File(value)
            else -> null
        }
    }.getOrNull()?.takeIf { it.isAbsolute }
}

internal fun resolveAndroidLocalTrackUri(locator: String): Uri? {
    val value = locator.trim()
    if (value.isBlank()) return null
    resolveAndroidLocalTrackFile(value)?.let { file ->
        return Uri.fromFile(file)
    }
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
        "content", "file" -> uri
        else -> null
    }
}

internal fun resolveTreeUriToDirectory(context: Context, treeUri: Uri): File? {
    if (!hasDirectLocalFileAccess(context)) return null
    if (treeUri.authority != EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY) return null
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
    val volumeId = documentId.substringBefore(':').trim()
    if (volumeId.isBlank()) return null
    val relativePath = documentId.substringAfter(':', "").trim('/')
    val volumeRoot = resolveStorageVolumeRoot(context, volumeId) ?: return null
    return if (relativePath.isBlank()) volumeRoot else File(volumeRoot, relativePath)
}

@Suppress("DEPRECATION")
private fun resolveStorageVolumeRoot(context: Context, volumeId: String): File? {
    if (volumeId.equals(PRIMARY_VOLUME_ID, ignoreCase = true)) {
        return Environment.getExternalStorageDirectory()
    }
    resolveStorageVolumeRootFromStorageManager(context, volumeId)?.let { return it }
    return context.getExternalFilesDirs(null)
        .asSequence()
        .filterNotNull()
        .mapNotNull(File::findStorageVolumeRoot)
        .firstOrNull { root -> root.name.equals(volumeId, ignoreCase = true) }
}

private fun listStorageVolumeRoots(
    context: Context,
    logger: DiagnosticLogger,
): List<AndroidStorageRoot> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return emptyList()
    val storageManager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
    return storageManager.storageVolumes.mapNotNull { volume ->
        val directory = volume.directoryCompat()
        val description = volume.descriptionCompat(context)
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "storage-volume uuid=${volume.uuid.orEmpty()} state=${volume.state.orEmpty()} " +
                "removable=${volume.isRemovable} emulated=${volume.isEmulated} " +
                "directory=${directory?.absolutePath ?: "null"} description=$description"
        }
        if (!isReadableStorageVolumeState(volume.state) || !volume.isRemovable) return@mapNotNull null
        val root = directory ?: return@mapNotNull null
        AndroidStorageRoot(
            label = description.ifBlank { "U 盘 ${root.name}" },
            uiLabel = if (description.isBlank()) uiText(Res.string.folder_picker_usb_drive_named, root.name) else null,
            root = root,
            isRemovable = true,
        )
    }
}

private fun resolveStorageVolumeRootFromStorageManager(context: Context, volumeId: String): File? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
    val storageManager = context.getSystemService(StorageManager::class.java) ?: return null
    return storageManager.storageVolumes.firstNotNullOfOrNull { volume ->
        val uuidMatches = volume.uuid?.equals(volumeId, ignoreCase = true) == true
        if (uuidMatches && isReadableStorageVolumeState(volume.state)) {
            volume.directoryCompat()
        } else {
            null
        }
    }
}

internal fun isReadableStorageVolumeState(state: String?): Boolean {
    return state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY
}

@TargetApi(Build.VERSION_CODES.N)
private fun StorageVolume.descriptionCompat(context: Context): String {
    return runCatching { getDescription(context).orEmpty() }.getOrDefault("")
}

private fun StorageVolume.directoryCompat(): File? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        directory
    } else {
        null
    }
}

private fun listMountedUsbFallbackRoots(logger: DiagnosticLogger): List<File> {
    val usbRoot = File(MNT_USB_ROOT_PATH)
    val roots = usbRoot.listFiles()
        .orEmpty()
        .filter { file -> file.exists() && file.isDirectory && file.canRead() }
        .map(File::canonicalFileOrSelf)
    logger.info(LOCAL_IMPORT_LOG_TAG) {
        "mnt-usb-fallback parent=$MNT_USB_ROOT_PATH exists=${usbRoot.exists()} directory=${usbRoot.isDirectory} " +
            "readable=${usbRoot.canRead()} roots=${roots.joinToString(separator = ";") { it.absolutePath }}"
    }
    return roots
}

private fun File.findStorageVolumeRoot(): File? {
    var current: File? = this
    while (current != null) {
        val parentPath = current.parentFile?.absolutePath
        if (parentPath == STORAGE_ROOT_PATH || parentPath == MNT_USB_ROOT_PATH) {
            return current
        }
        current = current.parentFile
    }
    return null
}

private fun File.isWithinOrSame(root: File): Boolean {
    val rootPath = root.canonicalFileOrSelf().absolutePath
    val filePath = canonicalFileOrSelf().absolutePath
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}

private fun File.canonicalFileOrSelf(): File {
    return runCatching { canonicalFile }.getOrDefault(absoluteFile)
}

private fun hasLegacyExternalStorageReadWriteAccess(context: Context): Boolean {
    return legacyDirectLocalFileAccessPermissions().all { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}

private const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"
private const val PRIMARY_VOLUME_ID = "primary"
private const val STORAGE_ROOT_PATH = "/storage"
private const val MNT_USB_ROOT_PATH = "/mnt/usb"
private const val FRAMEWORK_PACKAGE_STUBS_PACKAGE = "com.android.tv.frameworkpackagestubs"
private const val FRAMEWORK_PACKAGE_STUBS_MARKER = "frameworkpackagestubs"
private const val LOCAL_IMPORT_LOG_TAG = "LocalImport"
