package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.ProvideUiLanguage

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.platform.nativeUiString as uiString

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.info
import java.io.File
import java.util.Locale

class AndroidLocalFolderPickerActivity : ComponentActivity() {
    private var refreshPermissionState: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideUiLanguage {
                AndroidLocalFolderPickerScreen(
                    onRefreshPermissionStateRegistered = { refresh ->
                        refreshPermissionState = refresh
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onSelectDirectory = { root, directory ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent()
                                .putExtra(EXTRA_SELECTED_PATH, directory.absolutePath)
                                .putExtra(EXTRA_SELECTED_LABEL, directory.selectionLabel(root)),
                        )
                        finish()
                    },
                )


            }
}
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState?.invoke()
    }

    override fun onDestroy() {
        refreshPermissionState = null
        super.onDestroy()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, AndroidLocalFolderPickerActivity::class.java)
        }

        suspend fun selectionFromResult(data: Intent?): LocalFolderSelection? {
            val path = data?.getStringExtra(EXTRA_SELECTED_PATH)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val label = data.getStringExtra(EXTRA_SELECTED_LABEL)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: File(path).name.ifBlank { resolveNativeUiString(Res.string.folder_picker_local_music_title) }
            return LocalFolderSelection(
                label = label,
                persistentReference = path,
            )
        }
    }
}

@Composable
private fun AndroidLocalFolderPickerScreen(
    onRefreshPermissionStateRegistered: (() -> Unit) -> Unit,
    onCancel: () -> Unit,
    onSelectDirectory: (AndroidStorageRoot, File) -> Unit,
) {
    val context = LocalContext.current
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    val directLocalFileAccess = hasDirectLocalFileAccess(context)
    val allRoots = remember(permissionRefreshKey) {
        listAndroidStorageRoots(context)
    }
    val browsableRoots = remember(permissionRefreshKey, directLocalFileAccess, allRoots) {
        if (directLocalFileAccess) {
            allRoots
        } else {
            allRoots.filter { root ->
                root.isRemovable && root.root.exists() && root.root.isDirectory && root.root.canRead()
            }
        }
    }
    val browserMode = when {
        directLocalFileAccess -> "all-roots"
        browsableRoots.isNotEmpty() -> "usb-only"
        else -> "permission-required"
    }
    val refreshPermissionState = remember {
        { permissionRefreshKey += 1 }
    }
    DisposableEffect(onRefreshPermissionStateRegistered) {
        onRefreshPermissionStateRegistered(refreshPermissionState)
        onDispose { onRefreshPermissionStateRegistered({}) }
    }
    LaunchedEffect(permissionRefreshKey, directLocalFileAccess, browsableRoots) {
        GlobalDiagnosticLogger.info(LOCAL_IMPORT_LOG_TAG) {
            "local-folder-picker-screen browserMode=$browserMode directLocalFileAccess=$directLocalFileAccess " +
                "allRootCount=${allRoots.size} browsableRootCount=${browsableRoots.size} " +
                "browsableRoots=${browsableRoots.joinToString(separator = ";") { it.root.absolutePath }}"
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (directLocalFileAccess || browsableRoots.isNotEmpty()) {
                AndroidLocalFolderBrowser(
                    roots = browsableRoots,
                    onCancel = onCancel,
                    onSelectDirectory = onSelectDirectory,
                )
            } else {
                AndroidLocalFolderPermissionScreen(
                    permissionRefreshKey = permissionRefreshKey,
                    onPermissionChanged = refreshPermissionState,
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
private fun AndroidLocalFolderPermissionScreen(
    permissionRefreshKey: Int,
    onPermissionChanged: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val manageAllFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        onPermissionChanged()
    }
    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onPermissionChanged()
    }
    LaunchedEffect(permissionRefreshKey) {
        // Keeps the composable keyed to the latest permission state after settings returns.
    }
    BackHandler(onBack = onCancel)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = uiString(Res.string.folder_picker_permission_required_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                uiString(Res.string.folder_picker_manage_files_permission_hint)
            } else {
                uiString(Res.string.folder_picker_storage_permission_hint)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        manageAllFilesLauncher.launch(buildManageAllFilesAccessIntent(context))
                    } else {
                        legacyStorageLauncher.launch(legacyDirectLocalFileAccessPermissions())
                    }
                },
            ) {
                Text(uiString(Res.string.folder_picker_grant_permission))
            }
            OutlinedButton(onClick = onCancel) {
                Text(uiString(Res.string.common_cancel))
            }
        }
    }
}

@Composable
private fun AndroidLocalFolderBrowser(
    roots: List<AndroidStorageRoot>,
    onCancel: () -> Unit,
    onSelectDirectory: (AndroidStorageRoot, File) -> Unit,
) {
    var selectedRoot by remember { mutableStateOf<AndroidStorageRoot?>(null) }
    var currentDirectory by remember { mutableStateOf<File?>(null) }
    val root = selectedRoot
    val directory = currentDirectory

    BackHandler {
        when {
            root != null && directory != null && directory != root.root -> {
                currentDirectory = directory.parentFile?.takeIf { parent ->
                    parent.isDirectory && parent.isWithinOrSame(root.root)
                } ?: root.root
            }

            root != null -> {
                selectedRoot = null
                currentDirectory = null
            }

            else -> onCancel()
        }
    }

    if (root == null || directory == null) {
        AndroidStorageRootList(
            roots = roots,
            onRootSelected = { storageRoot ->
                selectedRoot = storageRoot
                currentDirectory = storageRoot.root
            },
            onCancel = onCancel,
        )
    } else {
        AndroidDirectoryList(
            root = root,
            directory = directory,
            onNavigate = { currentDirectory = it },
            onNavigateUp = {
                currentDirectory = directory.parentFile?.takeIf { parent ->
                    parent.isDirectory && parent.isWithinOrSame(root.root)
                } ?: root.root
            },
            onSelectCurrent = { onSelectDirectory(root, directory) },
            onCancel = onCancel,
        )
    }
}

@Composable
private fun AndroidStorageRootList(
    roots: List<AndroidStorageRoot>,
    onRootSelected: (AndroidStorageRoot) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        AndroidPickerHeader(
            title = uiString(Res.string.folder_picker_select_storage),
            subtitle = uiString(Res.string.folder_picker_storage_selection_hint),
            onCancel = onCancel,
        )
        Spacer(Modifier.height(12.dp))
        if (roots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiString(Res.string.folder_picker_storage_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(roots, key = { it.root.absolutePath }) { root ->
                    AndroidStorageRootRow(
                        root = root,
                        onClick = { onRootSelected(root) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AndroidDirectoryList(
    root: AndroidStorageRoot,
    directory: File,
    onNavigate: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onSelectCurrent: () -> Unit,
    onCancel: () -> Unit,
) {
    var listing by remember(directory) {
        mutableStateOf(AndroidDirectoryListing(isLoading = true))
    }
    LaunchedEffect(directory) {
        listing = AndroidDirectoryListing(isLoading = true)
        listing = withContext(Dispatchers.IO) {
            directory.loadAndroidDirectoryListing()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        AndroidPickerHeader(
            title = directory.selectionUiLabel(root),
            subtitle = directory.absolutePath,
            onCancel = onCancel,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onNavigateUp,
                enabled = directory != root.root,
            ) {
                Text(uiString(Res.string.folder_picker_parent_folder))
            }
            Button(onClick = onSelectCurrent) {
                Text(uiString(Res.string.folder_picker_select_current_folder))
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            listing.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(uiString(Res.string.folder_picker_loading_folder))
                }
            }

            listing.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = listing.errorMessage?.nativeDisplayText().orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            listing.entries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiString(Res.string.folder_picker_folder_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                ) {
                    items(listing.entries, key = { it.file.absolutePath }) { entry ->
                        AndroidDirectoryEntryRow(
                            entry = entry,
                            onOpenDirectory = { onNavigate(entry.file) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidPickerHeader(
    title: String,
    subtitle: String,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onCancel) {
            Text(uiString(Res.string.common_cancel))
        }
    }
}

@Composable
private fun AndroidStorageRootRow(
    root: AndroidStorageRoot,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (root.isRemovable) uiString(Res.string.folder_picker_usb_drive_label) else uiString(Res.string.folder_picker_storage_label),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = root.uiLabel?.nativeDisplayText() ?: root.label,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = root.root.absolutePath,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AndroidDirectoryEntryRow(
    entry: AndroidDirectoryEntry,
    onOpenDirectory: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.isDirectory, onClick = onOpenDirectory)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                entry.isDirectory -> uiString(Res.string.folder_picker_folder_type)
                entry.isAudio -> uiString(Res.string.folder_picker_audio_type)
                else -> uiString(Res.string.folder_picker_file_type)
            },
            color = if (entry.isDirectory || entry.isAudio) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = entry.file.name.ifBlank { entry.file.absolutePath },
                fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.isDirectory) {
                    uiString(Res.string.folder_picker_open_folder)
                } else {
                    formatAndroidFileSize(entry.file.length())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class AndroidDirectoryListing(
    val entries: List<AndroidDirectoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
)

private data class AndroidDirectoryEntry(
    val file: File,
    val isDirectory: Boolean,
    val isAudio: Boolean,
)

private fun File.loadAndroidDirectoryListing(): AndroidDirectoryListing {
    return runCatching {
        val entries = listFiles()
            .orEmpty()
            .filter { it.canRead() }
            .map { file ->
                AndroidDirectoryEntry(
                    file = file,
                    isDirectory = file.isDirectory,
                    isAudio = file.isFile && file.isAndroidAudioFile(),
                )
            }
            .sortedWith(
                compareBy<AndroidDirectoryEntry> {
                    when {
                        it.isDirectory -> 0
                        it.isAudio -> 1
                        else -> 2
                    }
                }.thenBy { it.file.name.lowercase() },
            )
        AndroidDirectoryListing(entries = entries)
    }.getOrElse { throwable ->
        AndroidDirectoryListing(
            errorMessage = throwable.uiErrorText(uiText(Res.string.folder_picker_folder_unreadable)),
        )
    }
}

private fun File.isWithinOrSame(root: File): Boolean {
    val rootPath = root.canonicalPathOrAbsolute()
    val filePath = canonicalPathOrAbsolute()
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}

private fun File.selectionLabel(root: AndroidStorageRoot): String {
    return if (this == root.root) {
        root.label
    } else {
        name.ifBlank { root.label }
    }
}

private fun File.canonicalPathOrAbsolute(): String {
    return runCatching { canonicalPath }.getOrDefault(absolutePath)
}

private fun File.isAndroidAudioFile(): Boolean {
    return extension.lowercase() in androidAudioFileExtensions
}

private fun formatAndroidFileSize(size: Long): String {
    if (size < 1024L) return "$size B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = size / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

private val androidAudioFileExtensions = setOf(
    "aac",
    "aiff",
    "alac",
    "ape",
    "dff",
    "dsf",
    "flac",
    "m4a",
    "mka",
    "mp3",
    "mp4",
    "oga",
    "ogg",
    "opus",
    "wav",
    "wma",
)

private const val EXTRA_SELECTED_PATH = "top.iwesley.lyn.music.platform.extra.SELECTED_PATH"
private const val EXTRA_SELECTED_LABEL = "top.iwesley.lyn.music.platform.extra.SELECTED_LABEL"
private const val LOCAL_IMPORT_LOG_TAG = "LocalImport"

/** The label returned for a saved source stays independent from its UI translation. */
@Composable
private fun File.selectionUiLabel(root: AndroidStorageRoot): String =
    if (this == root.root || name.isBlank()) root.uiLabel?.nativeDisplayText() ?: root.label else name
