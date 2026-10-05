package top.iwesley.lyn.music.data.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import top.iwesley.lyn.music.core.model.normalizeSelectedDirectories

private val selectedDirectoriesSerializer = ListSerializer(String.serializer())

/** Encodes folders selected under a source root for [ImportSourceEntity.selectedDirectories]; no selection is stored blank. */
fun encodeSelectedDirectories(directories: List<String>): String {
    val normalized = normalizeSelectedDirectories(directories)
    return if (normalized.isEmpty()) "" else Json.encodeToString(selectedDirectoriesSerializer, normalized)
}

/** Decodes [ImportSourceEntity.selectedDirectories]; blank or unreadable values mean the whole root. */
fun decodeSelectedDirectories(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return runCatching { normalizeSelectedDirectories(Json.decodeFromString(selectedDirectoriesSerializer, raw)) }
        .getOrDefault(emptyList())
}
