package top.iwesley.lyn.music.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.mobile.ui.platform.PlayerLyricsSettingsWindow
import top.iwesley.lyn.music.platform.lyricsSharePreviewFontFamily

@Composable
fun rememberPlayerLyricsFont(state: PlayerState, onIntent: (PlayerIntent) -> Unit): FontFamily? =
    rememberLyricsFont(
        fontKey = state.lyricsStyle.fontKey,
        filePath = state.lyricsFontPath,
        resolveFont = ::lyricsSharePreviewFontFamily,
        onFontFailed = { onIntent(PlayerIntent.LyricsFontFailed(it)) },
    )

@Composable
fun PlayerLyricsSettingsOverlay(
    state: PlayerState,
    mobile: Boolean,
    onIntent: (PlayerIntent) -> Unit,
    backdrop: PlayerLyricsBackdrop? = null,
) {
    val presentation = currentPlayerPresentation()
    SharedPlayerLyricsSettingsOverlay(
        state = state.toLyricsSettingsUiState(),
        mobile = mobile,
        onStyleChange = { onIntent(PlayerIntent.LyricsStyleChanged(it)) },
        onOffsetChange = { target, offset ->
            onIntent(PlayerIntent.LyricsOffsetChanged(target.trackId, target.lyricsVersion, offset))
        },
        onDismiss = { presentation.closeLyricsStyle() },
        resolveFont = ::lyricsSharePreviewFontFamily,
        windowHost = { onDismiss, content -> PlayerLyricsSettingsWindow(onDismiss, content) },
        backdrop = backdrop,
    )
}
