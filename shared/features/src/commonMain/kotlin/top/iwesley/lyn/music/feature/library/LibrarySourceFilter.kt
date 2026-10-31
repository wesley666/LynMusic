package top.iwesley.lyn.music.feature.library

import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.resources.*

sealed interface LibrarySourceFilter {
    data object ALL : LibrarySourceFilter
    data object LOCAL_FOLDER : LibrarySourceFilter
    data object DOWNLOADED : LibrarySourceFilter
    data class Source(val sourceId: String) : LibrarySourceFilter
}

data class LibrarySourceFilterOption(
    val filter: LibrarySourceFilter,
    val label: UiText,
)

fun buildLibrarySourceFilterOptions(sources: List<ImportSource> = emptyList()): List<LibrarySourceFilterOption> {
    val indexedSources = sources.filter { it.isLocalIndexedEnabled() }
    return buildList {
        add(LibrarySourceFilterOption(LibrarySourceFilter.ALL, uiText(Res.string.common_all)))
        if (indexedSources.any { it.type == ImportSourceType.LOCAL_FOLDER }) {
            add(LibrarySourceFilterOption(LibrarySourceFilter.LOCAL_FOLDER, uiText(Res.string.source_local_folder_label)))
        }
        ImportSourceType.entries.filter { it != ImportSourceType.LOCAL_FOLDER }.forEach { type ->
            indexedSources.filter { it.type == type }.forEach { source ->
                add(
                    LibrarySourceFilterOption(
                        filter = LibrarySourceFilter.Source(source.id),
                        label = UiText.Joined(
                            listOf(type.filterLabel(), UiText.Raw(source.label.trim().ifBlank { source.id })),
                        ),
                    ),
                )
            }
        }
        add(LibrarySourceFilterOption(LibrarySourceFilter.DOWNLOADED, uiText(Res.string.offline_downloaded_status)))
    }
}

fun librarySourceFilterButtonLabel(
    filter: LibrarySourceFilter,
    options: List<LibrarySourceFilterOption> = emptyList(),
): UiText = when (filter) {
    LibrarySourceFilter.ALL -> uiText(Res.string.source_filter_all)
    LibrarySourceFilter.LOCAL_FOLDER -> uiText(Res.string.source_local_folder_label)
    LibrarySourceFilter.DOWNLOADED -> uiText(Res.string.offline_downloaded_status)
    is LibrarySourceFilter.Source -> options.firstOrNull { it.filter == filter }?.label ?: UiText.Raw(filter.sourceId)
}

internal fun LibrarySourceFilter.availableOrAll(
    options: List<LibrarySourceFilterOption>,
    sourceTypesById: Map<String, ImportSourceType>,
): LibrarySourceFilter {
    val normalized = if (this is LibrarySourceFilter.Source && sourceTypesById[sourceId] == ImportSourceType.LOCAL_FOLDER) {
        LibrarySourceFilter.LOCAL_FOLDER
    } else {
        this
    }
    return normalized.takeIf { filter -> options.any { it.filter == filter } } ?: LibrarySourceFilter.ALL
}

fun encodeLibrarySourceFilter(filter: LibrarySourceFilter): String = when (filter) {
    LibrarySourceFilter.ALL -> "ALL"
    LibrarySourceFilter.LOCAL_FOLDER -> "LOCAL_FOLDER"
    LibrarySourceFilter.DOWNLOADED -> "DOWNLOADED"
    is LibrarySourceFilter.Source -> "source:${filter.sourceId}"
}

fun decodeLibrarySourceFilter(value: String?): LibrarySourceFilter = when {
    value == "LOCAL_FOLDER" -> LibrarySourceFilter.LOCAL_FOLDER
    value == "DOWNLOADED" -> LibrarySourceFilter.DOWNLOADED
    value?.startsWith("source:") == true -> value.removePrefix("source:")
        .takeIf { it.isNotBlank() }
        ?.let { LibrarySourceFilter.Source(it) }
        ?: LibrarySourceFilter.ALL
    else -> LibrarySourceFilter.ALL
}

private fun ImportSourceType.filterLabel(): UiText = when (this) {
    ImportSourceType.LOCAL_FOLDER -> uiText(Res.string.source_local_folder_label)
    ImportSourceType.SAMBA -> UiText.Raw("Samba")
    ImportSourceType.WEBDAV -> UiText.Raw("WebDAV")
    ImportSourceType.NAVIDROME -> UiText.Raw("Navidrome")
    ImportSourceType.SUBSONIC -> UiText.Raw("Subsonic")
    ImportSourceType.EMBY -> UiText.Raw("Emby")
    ImportSourceType.FN_MUSIC -> uiText(Res.string.fn_music_name)
}
