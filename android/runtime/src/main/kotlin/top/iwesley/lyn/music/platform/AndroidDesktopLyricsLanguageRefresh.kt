package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.PlaybackSnapshot

/** Only redraws the current snapshot; lyrics lookup and window state stay with the service. */
internal fun CoroutineScope.observeDesktopLyricsLanguage(
    latestSnapshot: () -> PlaybackSnapshot?,
    updateCloseDescription: (String) -> Unit,
    render: (PlaybackSnapshot) -> Unit,
): Job = launch {
    AppLanguageRuntime.effectiveLanguageRevision.collectLatest {
        updateCloseDescription(resolveNativeUiString(Res.string.desktop_lyrics_close_description))
        latestSnapshot()?.let(render)
    }
}
