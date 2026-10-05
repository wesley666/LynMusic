package top.iwesley.lyn.music.feature.importing

import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.RemoteDirectoryEntry
import top.iwesley.lyn.music.core.model.buildWebDavDirectoryUrl
import top.iwesley.lyn.music.core.model.displayWebDavRootUrl
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.plus
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.isSameOrAncestorDirectory
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.normalizeFolderPath
import top.iwesley.lyn.music.core.model.normalizeSelectedDirectories
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.resources.*

/** Children of one folder in a remote folder tree; `children == null` until it has been listed. */
data class RemoteFolderNode(
    val children: List<RemoteDirectoryEntry>? = null,
    val isLoading: Boolean = false,
    val error: UiText? = null,
)

/**
 * Lazily browsed folders of a Samba/WebDAV source plus the folders picked for scanning. Paths are relative to the source
 * root and `""` is the root itself, which is only selectable when the root is a folder (WebDAV, or a legacy Samba source
 * whose root is `share/sub`); a Samba source rooted at the server lists shares under it instead.
 */
data class RemoteFolderTreeState(
    val rootSelectable: Boolean,
    val nodes: Map<String, RemoteFolderNode> = emptyMap(),
    val expanded: Set<String> = setOf(ROOT),
    val selected: Set<String> = emptySet(),
    /**
     * Shares typed by hand on a server-rooted Samba tree, e.g. hidden `Music$` shares the listing skips or any share when
     * listing fails. Kept apart from listed nodes so reloading the share list does not drop them.
     */
    val manualShares: List<String> = emptyList(),
    /** Identifies the dialog this tree belongs to; unique per opened dialog. */
    val instanceId: Long = 0L,
    /** Bumped whenever connection details change, so listings started before that are dropped. */
    val generation: Int = 0,
) {
    val isBrowsed: Boolean get() = ROOT in nodes

    /** Shares can be typed in once a server-rooted Samba tree has been browsed, whether or not listing worked. */
    val canAddManualShare: Boolean get() = !rootSelectable && isBrowsed && nodes[ROOT]?.isLoading != true

    val selectedDirectories: List<String> get() = normalizeSelectedDirectories(selected)

    fun isLockedByAncestor(path: String): Boolean =
        selected.any { selectedPath -> selectedPath != path && isSameOrAncestorDirectory(selectedPath, path) }

    fun isChecked(path: String): Boolean = path in selected || isLockedByAncestor(path)

    fun toggleSelected(path: String): RemoteFolderTreeState {
        val normalized = normalizeFolderPath(path)
        if (normalized == ROOT && !rootSelectable) return this
        if (isLockedByAncestor(normalized)) return this
        val updated = if (normalized in selected) selected - normalized else selected + normalized
        return copy(selected = normalizeSelectedDirectories(updated).toSet())
    }

    fun toggleExpanded(path: String): RemoteFolderTreeState =
        copy(expanded = if (path in expanded) expanded - path else expanded + path)

    fun needsLoading(path: String): Boolean = nodes[path]?.let { it.children == null && !it.isLoading } ?: true

    fun loading(path: String): RemoteFolderTreeState =
        copy(nodes = nodes + (path to RemoteFolderNode(isLoading = true)))

    fun loaded(path: String, result: Result<List<RemoteDirectoryEntry>>, errorText: (Throwable) -> UiText): RemoteFolderTreeState =
        copy(
            nodes = nodes + (
                path to result.fold(
                    onSuccess = { RemoteFolderNode(children = it) },
                    onFailure = { RemoteFolderNode(error = errorText(it)) },
                )
                ),
        )

    /** Adds a share typed by hand; shares already listed or added are ignored, ignoring case as SMB does. */
    fun withManualShare(name: String): RemoteFolderTreeState {
        if (rootSelectable) return this
        val share = normalizeSambaPath(name).substringBefore('/')
        if (share.isBlank()) return this
        val known = manualShares + nodes[ROOT]?.children.orEmpty().map { it.relativePath }
        if (known.any { it.equals(share, ignoreCase = true) }) return this
        return copy(manualShares = manualShares + share)
    }

    /** Connection details changed: forget listings and, when the server itself changed, the selection too. */
    fun reset(keepSelection: Boolean): RemoteFolderTreeState = copy(
        nodes = emptyMap(),
        expanded = setOf(ROOT),
        selected = if (keepSelection) selected else emptySet(),
        manualShares = if (keepSelection) manualShares else emptyList(),
        generation = generation + 1,
    )

    /** Flattens the tree in display order for the expanded folders; the root row is shown only when selectable. */
    fun visibleRows(rootName: String): List<RemoteFolderRow> = buildList {
        if (!isBrowsed) return@buildList
        if (rootSelectable) {
            add(folderRow(ROOT, rootName, depth = 0))
            if (ROOT in expanded) addChildren(ROOT, depth = 1)
        } else {
            addChildren(ROOT, depth = 0)
        }
    }

    private fun MutableList<RemoteFolderRow>.addChildren(path: String, depth: Int) {
        val node = nodes[path] ?: return
        val listed = node.children.orEmpty()
        // Hand-typed shares sit next to the listed ones, and stay visible when listing failed.
        val manual = if (path == ROOT && !rootSelectable) {
            manualShares
                .filter { share -> listed.none { it.relativePath.equals(share, ignoreCase = true) } }
                .map { RemoteDirectoryEntry(it, it) }
        } else {
            emptyList()
        }
        node.error?.let {
            add(RemoteFolderRow.Message(parentPath = path, depth = depth, text = it, isError = true))
        }
        if (node.error == null && node.children == null) return
        val children = listed + manual
        if (children.isEmpty()) {
            if (node.error == null) {
                add(RemoteFolderRow.Message(parentPath = path, depth = depth, text = uiText(Res.string.source_folder_tree_empty), isError = false))
            }
            return
        }
        children.forEach { child ->
            add(folderRow(child.relativePath, child.name, depth))
            if (child.relativePath in expanded) addChildren(child.relativePath, depth + 1)
        }
    }

    private fun folderRow(path: String, name: String, depth: Int): RemoteFolderRow.Folder =
        RemoteFolderRow.Folder(
            path = path,
            name = name,
            depth = depth,
            isExpanded = path in expanded,
            isLoading = nodes[path]?.isLoading == true,
            isChecked = isChecked(path),
            isLockedByAncestor = isLockedByAncestor(path),
        )

    companion object {
        const val ROOT = ""
    }
}

/** One line of a flattened [RemoteFolderTreeState]: a folder, or a note (empty / failed) under an expanded folder. */
sealed interface RemoteFolderRow {
    val depth: Int

    data class Folder(
        val path: String,
        val name: String,
        override val depth: Int,
        val isExpanded: Boolean,
        val isLoading: Boolean,
        val isChecked: Boolean,
        val isLockedByAncestor: Boolean,
    ) : RemoteFolderRow

    data class Message(
        val parentPath: String,
        override val depth: Int,
        val text: UiText,
        val isError: Boolean,
    ) : RemoteFolderRow
}

/** Endpoint shown for a Samba/WebDAV source: its only scanned folder, or the root plus how many folders are scanned. */
fun ImportSource.folderSourceEndpointText(): UiText {
    val selected = normalizeSelectedDirectories(selectedDirectories)
    val shownFolder = selected.singleOrNull().orEmpty()
    val endpoint = when (type) {
        ImportSourceType.SAMBA -> formatSambaEndpoint(server, port, joinSambaPath(path.orEmpty(), shownFolder))
        ImportSourceType.WEBDAV -> displayWebDavRootUrl(
            runCatching { buildWebDavDirectoryUrl(rootReference, shownFolder) }.getOrDefault(rootReference),
        )
        else -> rootReference
    }
    return if (selected.size > 1) {
        UiText.Raw(endpoint) + " · " + uiPlural(Res.plurals.source_folder_count, selected.size, selected.size)
    } else {
        UiText.Raw(endpoint)
    }
}
