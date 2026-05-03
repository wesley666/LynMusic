package top.iwesley.lyn.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import top.iwesley.lyn.music.ArtworkDecodeSize

@Composable
expect fun rememberPlatformArtworkBitmap(
    locator: String?,
    cacheRemote: Boolean = true,
    maxDecodeSizePx: Int = ArtworkDecodeSize.Player,
): ImageBitmap?
