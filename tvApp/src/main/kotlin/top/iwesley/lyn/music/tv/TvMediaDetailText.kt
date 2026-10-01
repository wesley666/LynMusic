package top.iwesley.lyn.music.tv

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.tv.ui.TvMediaBrowserMode

internal enum class TvMediaDetailSource {
    Library,
    Favorites,
}

internal data class TvMediaDetailArgs(
    val source: TvMediaDetailSource,
    val mode: TvMediaBrowserMode,
    val id: String,
    val title: String,
    val subtitle: String?,
    val subtitleTrackCount: Int? = null,
)

/** Validate navigation data without resolving any user-visible wording. */
internal fun buildTvMediaDetailArgs(
    sourceName: String?,
    modeName: String?,
    id: String?,
    title: String?,
    subtitle: String?,
    subtitleTrackCount: Int? = null,
): TvMediaDetailArgs? {
    val source = TvMediaDetailSource.entries.firstOrNull { it.name == sourceName } ?: return null
    val mode = TvMediaBrowserMode.entries.firstOrNull { it.name == modeName } ?: return null
    if (mode == TvMediaBrowserMode.Tracks || id.isNullOrBlank() || title == null) return null
    return TvMediaDetailArgs(source, mode, id, title, subtitle, subtitleTrackCount)
}

internal fun tvMediaDetailTitleText(mode: TvMediaBrowserMode, title: String): UiText =
    if (title.isNotBlank()) UiText.Raw(title) else when (mode) {
        TvMediaBrowserMode.Artists -> uiText(Res.string.common_unknown_artist)
        TvMediaBrowserMode.Albums -> uiText(Res.string.common_unknown_album)
        TvMediaBrowserMode.Tracks -> UiText.Raw(title)
    }

internal fun tvMediaDetailSubtitleText(subtitle: String?, subtitleTrackCount: Int?): UiText? =
    if (subtitleTrackCount != null) uiPlural(Res.plurals.common_track_count, subtitleTrackCount, subtitleTrackCount)
    else subtitle?.takeIf { it.isNotBlank() }?.let(UiText::Raw)
