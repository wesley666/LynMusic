package top.iwesley.lyn.music.core.model

/**
 * Normalizes a folder path taken from a server listing: only surrounding slashes go. Names keep spaces and backslashes,
 * which are legitimate in WebDAV (and Samba on Linux) folder names.
 */
fun normalizeFolderPath(path: String): String = path.trim('/')

/** Normalizes folder paths relative to a source root: no duplicates, no folder already covered by a selected ancestor. */
fun normalizeSelectedDirectories(paths: Collection<String>): List<String> {
    val normalized = paths.map(::normalizeFolderPath).distinct().sorted()
    return normalized.filter { path ->
        normalized.none { other -> other != path && isSameOrAncestorDirectory(other, path) }
    }
}

/** Folders to scan for a source: a blank selection means the whole root, as stored by sources created before folder selection. */
fun effectiveSelectedDirectories(selected: List<String>): List<String> =
    normalizeSelectedDirectories(selected).ifEmpty { listOf("") }

/** True when [ancestor] is [path] itself or one of its parent folders; the blank root is the ancestor of everything. */
fun isSameOrAncestorDirectory(ancestor: String, path: String): Boolean {
    val normalizedAncestor = normalizeFolderPath(ancestor)
    val normalizedPath = normalizeFolderPath(path)
    return normalizedAncestor.isEmpty() ||
        normalizedPath == normalizedAncestor ||
        normalizedPath.startsWith("$normalizedAncestor/")
}

/**
 * Resolves a path relative to a Samba source root into share + remote path. Legacy sources keep `share/sub` as their root;
 * sources rooted at the server have a blank root and paths starting with the share name. Only the typed-in root is
 * normalized: [relativePath] comes from server listings, so names keep leading/trailing spaces (e.g. `" Live"`).
 */
fun resolveSambaRemoteFile(rootPath: String?, relativePath: String): SambaPath? {
    val fullPath = joinSambaPath(normalizeSambaPath(rootPath), normalizeFolderPath(relativePath))
    val shareName = fullPath.substringBefore('/')
    if (shareName.isBlank()) return null
    return SambaPath(shareName = shareName, directoryPath = fullPath.substringAfter('/', ""))
}

/** One selected Samba folder to walk: where it lives on the server, and its path relative to the source root. */
data class SambaScanTarget(
    val shareName: String,
    val directoryPath: String,
    val relativePrefix: String,
)

/** Turns a source's selected folders into scan targets; empty when nothing resolves to a share (e.g. a server root without selections). */
fun planSambaScanTargets(rootPath: String?, selectedDirectories: List<String>): List<SambaScanTarget> =
    effectiveSelectedDirectories(selectedDirectories).mapNotNull { selected ->
        resolveSambaRemoteFile(rootPath, selected)?.let { location ->
            SambaScanTarget(
                shareName = location.shareName,
                directoryPath = location.directoryPath,
                relativePrefix = normalizeFolderPath(selected),
            )
        }
    }

/** Outcome of [scanSelectedFolders]: audio files seen, and the selected folders that could not be read. */
data class SelectedFoldersScan(
    val discoveredAudioFileCount: Int,
    val unreadableFolders: List<String>,
)

/**
 * Walks each selected folder with [scanFolder]. A folder that can't be read (deleted, renamed, no access) becomes a scan
 * failure and is reported as unreadable, so the other folders still import while the library keeps its tracks; the scan
 * only fails when no folder could be read, so an unreachable server or bad login still reports an error.
 */
inline fun <T> scanSelectedFolders(
    folders: List<T>,
    failures: MutableList<ImportScanFailure>,
    folderPath: (T) -> String,
    scanFolder: (T) -> Int,
): SelectedFoldersScan {
    var discoveredAudioFileCount = 0
    var firstError: Throwable? = null
    val unreadableFolders = mutableListOf<String>()
    folders.forEach { folder ->
        try {
            discoveredAudioFileCount += scanFolder(folder)
        } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (firstError == null) firstError = throwable
            unreadableFolders += folderPath(folder)
            failures += audioImportFailure(folderPath(folder), throwable)
        }
    }
    if (unreadableFolders.size == folders.size) firstError?.let { throw it }
    return SelectedFoldersScan(discoveredAudioFileCount, unreadableFolders)
}
