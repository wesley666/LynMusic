package top.iwesley.lyn.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lynmusic.player.app.generated.resources.Res
import lynmusic.player.app.generated.resources.default_cover
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.imageResource
import kotlin.concurrent.Volatile

private val bundledDefaultCoverBytesMutex = Mutex()
@Volatile
private var bundledDefaultCoverBytesCache: ByteArray? = null

@Composable
internal fun rememberBundledDefaultCoverBitmap(): ImageBitmap? {
    return imageResource(Res.drawable.default_cover)
        .takeUnless { it.width <= 1 && it.height <= 1 }
}

suspend fun loadBundledDefaultCoverBytes(): ByteArray? {
    bundledDefaultCoverBytesCache?.let { return it }
    return bundledDefaultCoverBytesMutex.withLock {
        bundledDefaultCoverBytesCache?.let { return@withLock it }
        runCatching {
            getDrawableResourceBytes(
                environment = getSystemResourceEnvironment(),
                resource = Res.drawable.default_cover,
            )
        }.getOrNull()?.also { bundledDefaultCoverBytesCache = it }
    }
}
