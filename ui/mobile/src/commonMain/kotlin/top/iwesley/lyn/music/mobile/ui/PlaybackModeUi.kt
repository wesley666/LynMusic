package top.iwesley.lyn.music.mobile.ui
import top.iwesley.lyn.music.uiString
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.PlaybackMode

@Composable
fun modeLabel(mode: PlaybackMode): String {
    return when (mode) {
        PlaybackMode.ORDER -> uiString(Res.string.player_sequential_mode)
        PlaybackMode.SHUFFLE -> uiString(Res.string.player_shuffle_mode)
        PlaybackMode.REPEAT_ONE -> uiString(Res.string.player_repeat_one_mode)
    }
}

fun playbackModeIcon(mode: PlaybackMode): ImageVector {
    return when (mode) {
        PlaybackMode.ORDER -> Icons.Rounded.Repeat
        PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle
        PlaybackMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
    }
}
