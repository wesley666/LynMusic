package top.iwesley.lyn.music

import coil3.PlatformContext
import okio.FileSystem
import okio.Path

internal actual fun lynCoilDiskCacheDirectory(context: PlatformContext): Path {
    return FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "lynmusic-coil-image-cache"
}
