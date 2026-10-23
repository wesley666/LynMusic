package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.Track

data class LibraryFolderKey(
    val sourceId: String,
    val path: String,
) {
    val stableId: String
        get() = "${sourceId.length}:$sourceId:$path"
}

data class LibraryFolderNode(
    val key: LibraryFolderKey,
    val name: String,
    val sourceLabel: String,
    val sourceId: String,
    val path: String,
    val trackCount: Int,
    val directTrackCount: Int,
    val childFolderCount: Int,
)

data class LibraryFolderTree(
    val rootFolders: List<LibraryFolderNode>,
    val nodesByKey: Map<LibraryFolderKey, LibraryFolderNode>,
    val childFoldersByKey: Map<LibraryFolderKey, List<LibraryFolderNode>>,
    val directTracksByKey: Map<LibraryFolderKey, List<Track>>,
) {
    val folderCount: Int = nodesByKey.size
}

fun deriveLibraryFolderTree(
    tracks: List<Track>,
    sourceLabelsById: Map<String, String>,
): LibraryFolderTree {
    val statsByKey = linkedMapOf<LibraryFolderKey, MutableLibraryFolderStats>()
    val childPathsByKey = linkedMapOf<LibraryFolderKey, MutableSet<String>>()
    val directTracksByKey = linkedMapOf<LibraryFolderKey, MutableList<Track>>()
    tracks.forEach { track ->
        val sourceId = track.sourceId
        val rootKey = LibraryFolderKey(sourceId = sourceId, path = "")
        val pathSegments = normalizedLibraryFolderPathSegments(track.relativePath)
        val parentSegments = pathSegments.dropLast(1)
        statsByKey.getOrPut(rootKey) { MutableLibraryFolderStats() }.trackCount += 1
        if (parentSegments.isEmpty()) {
            statsByKey.getOrPut(rootKey) { MutableLibraryFolderStats() }.directTrackCount += 1
            directTracksByKey.getOrPut(rootKey) { mutableListOf() }.add(track)
        } else {
            childPathsByKey.getOrPut(rootKey) { linkedSetOf() }.add(parentSegments.first())
            parentSegments.indices.forEach { index ->
                val folderPath = parentSegments.take(index + 1).joinToString("/")
                val folderKey = LibraryFolderKey(sourceId = sourceId, path = folderPath)
                statsByKey.getOrPut(folderKey) { MutableLibraryFolderStats() }.trackCount += 1
                if (index == parentSegments.lastIndex) {
                    statsByKey.getOrPut(folderKey) { MutableLibraryFolderStats() }.directTrackCount += 1
                    directTracksByKey.getOrPut(folderKey) { mutableListOf() }.add(track)
                } else {
                    val childPath = parentSegments.take(index + 2).joinToString("/")
                    childPathsByKey.getOrPut(folderKey) { linkedSetOf() }.add(childPath)
                }
            }
        }
    }
    val nodesByKey = statsByKey.mapValues { (key, stats) ->
        val sourceLabel = sourceLabelsById[key.sourceId]?.trim()?.takeIf { it.isNotBlank() } ?: key.sourceId
        LibraryFolderNode(
            key = key,
            name = if (key.path.isBlank()) sourceLabel else key.path.substringAfterLast('/'),
            sourceLabel = sourceLabel,
            sourceId = key.sourceId,
            path = key.path,
            trackCount = stats.trackCount,
            directTrackCount = stats.directTrackCount,
            childFolderCount = childPathsByKey[key]?.size ?: 0,
        )
    }
    val childFoldersByKey = childPathsByKey.mapValues { (key, childPaths) ->
        childPaths.mapNotNull { childPath ->
            nodesByKey[LibraryFolderKey(sourceId = key.sourceId, path = childPath)]
        }.sortedWith(LIBRARY_FOLDER_NODE_COMPARATOR)
    }
    return LibraryFolderTree(
        rootFolders = nodesByKey.values
            .filter { it.path.isBlank() }
            .sortedWith(LIBRARY_FOLDER_NODE_COMPARATOR),
        nodesByKey = nodesByKey,
        childFoldersByKey = childFoldersByKey,
        directTracksByKey = directTracksByKey.mapValues { (_, tracks) ->
            tracks.sortedWith(LIBRARY_FOLDER_TRACK_COMPARATOR)
        },
    )
}

fun normalizedLibraryFolderPathSegments(relativePath: String): List<String> {
    return relativePath
        .replace('\\', '/')
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private data class MutableLibraryFolderStats(
    var trackCount: Int = 0,
    var directTrackCount: Int = 0,
)

private val LIBRARY_FOLDER_NODE_COMPARATOR = compareBy<LibraryFolderNode> { it.name.lowercase() }
    .thenBy { it.sourceLabel.lowercase() }
    .thenBy { it.sourceId }
    .thenBy { it.path.lowercase() }

private val LIBRARY_FOLDER_TRACK_COMPARATOR = compareBy<Track> {
    normalizedLibraryFolderPathSegments(it.relativePath).lastOrNull().orEmpty().lowercase()
}.thenBy { it.title.lowercase() }.thenBy { it.id }
