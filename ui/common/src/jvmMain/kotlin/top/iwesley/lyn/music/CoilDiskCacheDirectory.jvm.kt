package top.iwesley.lyn.music

import coil3.PlatformContext
import java.io.File
import okio.Path
import okio.Path.Companion.toPath
import top.iwesley.lyn.music.core.model.JvmAppDataDirectory

internal actual fun lynCoilDiskCacheDirectory(context: PlatformContext): Path {
    val directory = JvmAppDataDirectory.resolve("coil-image-cache").apply {
        mkdirs()
    }
    return directory.absolutePath.toPath(normalize = true)
}
