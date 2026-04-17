package top.iwesley.lyn.music.platform

import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

internal class JvmFileExtensionFilter(
    val description: String,
    rawExtensions: List<String>,
) {
    val extensions: List<String> = rawExtensions
        .map(::normalizeJvmFilePickerExtension)
        .filter { it.isNotBlank() }
        .distinct()

    fun accepts(path: Path): Boolean {
        if (extensions.isEmpty()) return true
        val fileName = path.fileName?.toString().orEmpty()
        return extensions.any { extension ->
            fileName.endsWith(".$extension", ignoreCase = true)
        }
    }

    fun toSwingFilter(): FileNameExtensionFilter? {
        if (extensions.isEmpty()) return null
        return FileNameExtensionFilter(description, *extensions.toTypedArray())
    }

    fun toFilenameFilter(): FilenameFilter? {
        if (extensions.isEmpty()) return null
        return FilenameFilter { directory, name ->
            val child = File(directory, name)
            child.isDirectory || extensions.any { extension ->
                name.endsWith(".$extension", ignoreCase = true)
            }
        }
    }
}

internal object JvmNativeFilePicker {
    suspend fun pickOpenFile(
        title: String,
        extensionFilter: JvmFileExtensionFilter? = null,
    ): Path? {
        val selectedPath = withContext(Dispatchers.Swing) {
            if (isJvmNativeFilePickerMacOs()) {
                pickMacFile(
                    title = title,
                    mode = FileDialog.LOAD,
                    chooseDirectories = false,
                    suggestedName = null,
                    extensionFilter = extensionFilter,
                )
            } else {
                pickSwingOpenFile(title, extensionFilter)
            }
        } ?: return null
        return selectedPath
            .toAbsolutePath()
            .normalize()
            .takeIf { Files.isRegularFile(it) }
            ?.takeIf { extensionFilter?.accepts(it) ?: true }
    }

    suspend fun pickDirectory(title: String): Path? {
        val selectedPath = withContext(Dispatchers.Swing) {
            if (isJvmNativeFilePickerMacOs()) {
                pickMacFile(
                    title = title,
                    mode = FileDialog.LOAD,
                    chooseDirectories = true,
                    suggestedName = null,
                    extensionFilter = null,
                )
            } else {
                pickSwingDirectory(title)
            }
        } ?: return null
        return selectedPath
            .toAbsolutePath()
            .normalize()
            .takeIf { Files.isDirectory(it) }
    }

    suspend fun pickFileOrDirectory(title: String): Path? {
        val selectedPath = withContext(Dispatchers.Swing) {
            if (isJvmNativeFilePickerMacOs()) {
                pickMacFile(
                    title = title,
                    mode = FileDialog.LOAD,
                    chooseDirectories = true,
                    suggestedName = null,
                    extensionFilter = null,
                )
            } else {
                pickSwingFileOrDirectory(title)
            }
        } ?: return null
        return selectedPath
            .toAbsolutePath()
            .normalize()
            .takeIf { Files.isRegularFile(it) || Files.isDirectory(it) }
    }

    suspend fun pickSaveFile(
        title: String,
        suggestedName: String,
        defaultExtension: String,
    ): Path? {
        val selectedPath = withContext(Dispatchers.Swing) {
            if (isJvmNativeFilePickerMacOs()) {
                pickMacFile(
                    title = title,
                    mode = FileDialog.SAVE,
                    chooseDirectories = false,
                    suggestedName = suggestedName,
                    extensionFilter = null,
                )
            } else {
                pickSwingSaveFile(title, suggestedName)
            }
        } ?: return null
        return appendJvmFilePickerDefaultExtension(selectedPath, defaultExtension)
    }

    private fun pickMacFile(
        title: String,
        mode: Int,
        chooseDirectories: Boolean,
        suggestedName: String?,
        extensionFilter: JvmFileExtensionFilter?,
    ): Path? {
        return withMacFileDialogDirectoryProperty(chooseDirectories) {
            val dialog = createFileDialog(title, mode)
            try {
                dialog.isMultipleMode = false
                extensionFilter?.toFilenameFilter()?.let(dialog::setFilenameFilter)
                suggestedName?.takeIf { it.isNotBlank() }?.let { configureSuggestedFile(dialog, it) }
                dialog.isVisible = true
                resolveJvmFileDialogSelection(dialog.directory, dialog.file)
            } finally {
                dialog.dispose()
            }
        }
    }

    private fun pickSwingOpenFile(
        title: String,
        extensionFilter: JvmFileExtensionFilter?,
    ): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = true
            extensionFilter?.toSwingFilter()?.let { fileFilter = it }
        }
        return if (chooser.showOpenDialog(activeWindow()) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.toPath()
        } else {
            null
        }
    }

    private fun pickSwingDirectory(title: String): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(activeWindow()) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.toPath()
        } else {
            null
        }
    }

    private fun pickSwingFileOrDirectory(title: String): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
            isAcceptAllFileFilterUsed = true
        }
        return if (chooser.showOpenDialog(activeWindow()) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.toPath()
        } else {
            null
        }
    }

    private fun pickSwingSaveFile(
        title: String,
        suggestedName: String,
    ): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            selectedFile = File(suggestedName)
        }
        return if (chooser.showSaveDialog(activeWindow()) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.toPath()
        } else {
            null
        }
    }

    private fun createFileDialog(title: String, mode: Int): FileDialog {
        return when (val owner = activeWindow()) {
            is Frame -> FileDialog(owner, title, mode)
            is Dialog -> FileDialog(owner, title, mode)
            else -> FileDialog(null as Frame?, title, mode)
        }
    }

    private fun configureSuggestedFile(dialog: FileDialog, suggestedName: String) {
        val file = File(suggestedName)
        file.parentFile?.let { dialog.directory = it.absolutePath }
        dialog.file = file.name
    }

    private fun activeWindow(): Window? {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    }
}

internal fun isJvmNativeFilePickerMacOs(osName: String = System.getProperty("os.name").orEmpty()): Boolean {
    return osName.contains("mac", ignoreCase = true)
}

internal fun appendJvmFilePickerDefaultExtension(
    path: Path,
    defaultExtension: String,
): Path {
    val normalizedPath = path.toAbsolutePath().normalize()
    val extension = normalizeJvmFilePickerExtension(defaultExtension)
    if (extension.isBlank()) return normalizedPath
    val fileName = normalizedPath.fileName?.toString() ?: return normalizedPath
    if (fileName.endsWith(".$extension", ignoreCase = true)) return normalizedPath
    val extendedFileName = "$fileName.$extension"
    return normalizedPath.parent?.resolve(extendedFileName)
        ?: Path.of(extendedFileName).toAbsolutePath().normalize()
}

internal fun resolveJvmFileDialogSelection(
    directory: String?,
    file: String?,
): Path? {
    val selectedFile = file?.takeIf { it.isNotBlank() } ?: return null
    val directoryPath = directory?.takeIf { it.isNotBlank() }?.let(Path::of)
    return (directoryPath?.resolve(selectedFile) ?: Path.of(selectedFile))
        .toAbsolutePath()
        .normalize()
}

private fun <T> withMacFileDialogDirectoryProperty(
    chooseDirectories: Boolean,
    block: () -> T,
): T {
    synchronized(MAC_FILE_DIALOG_LOCK) {
        val previousValue = System.getProperty(MAC_FILE_DIALOG_DIRECTORIES_KEY)
        System.setProperty(MAC_FILE_DIALOG_DIRECTORIES_KEY, chooseDirectories.toString())
        try {
            return block()
        } finally {
            if (previousValue == null) {
                System.clearProperty(MAC_FILE_DIALOG_DIRECTORIES_KEY)
            } else {
                System.setProperty(MAC_FILE_DIALOG_DIRECTORIES_KEY, previousValue)
            }
        }
    }
}

private fun normalizeJvmFilePickerExtension(extension: String): String {
    return extension.trim().removePrefix(".").lowercase()
}

private const val MAC_FILE_DIALOG_DIRECTORIES_KEY = "apple.awt.fileDialogForDirectories"
private val MAC_FILE_DIALOG_LOCK = Any()
