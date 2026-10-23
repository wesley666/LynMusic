package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.resources.*

data class OnlineSourceOption(
    val sourceId: String,
    val label: UiText,
)

fun ImportState.onlineNavidromeSourceOptions(): List<OnlineSourceOption> {
    return sources
        .asSequence()
        .map { it.source }
        .filter {
            it.enabled &&
                it.type == ImportSourceType.NAVIDROME &&
                it.indexMode == ImportSourceIndexMode.ONLINE
        }
        .map { source ->
            OnlineSourceOption(
                sourceId = source.id,
                label = UiText.Raw(source.label.trim().ifBlank { source.id }) + uiText(Res.string.source_online_badge),
            )
        }
        .toList()
}
