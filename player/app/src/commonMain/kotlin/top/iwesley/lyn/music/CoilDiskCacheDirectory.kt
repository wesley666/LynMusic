package top.iwesley.lyn.music

import coil3.PlatformContext
import okio.Path

internal expect fun lynCoilDiskCacheDirectory(context: PlatformContext): Path
