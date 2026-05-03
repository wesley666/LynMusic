package top.iwesley.lyn.music

import coil3.PlatformContext
import java.io.File
import okio.Path
import okio.Path.Companion.toPath

internal actual fun lynCoilDiskCacheDirectory(context: PlatformContext): Path {
    val directory = File(File(System.getProperty("user.home")), ".lynmusic/coil-image-cache").apply {
        mkdirs()
    }
    return directory.absolutePath.toPath(normalize = true)
}
