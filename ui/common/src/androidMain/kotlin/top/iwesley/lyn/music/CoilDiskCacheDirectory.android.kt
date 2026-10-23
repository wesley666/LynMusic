package top.iwesley.lyn.music

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toPath

internal actual fun lynCoilDiskCacheDirectory(context: PlatformContext): Path {
    val directory = context.cacheDir.resolve("coil-image-cache").apply { mkdirs() }
    return directory.absolutePath.toPath(normalize = true)
}
