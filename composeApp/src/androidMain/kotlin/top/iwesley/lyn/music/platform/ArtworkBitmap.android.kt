package top.iwesley.lyn.music.platform

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator

@Composable
actual fun rememberPlatformArtworkBitmap(locator: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, locator) {
        value = loadAndroidArtworkBitmap(locator)
    }
    return bitmap
}

private suspend fun loadAndroidArtworkBitmap(locator: String?): ImageBitmap? = withContext(Dispatchers.IO) {
    val target = normalizeArtworkLocator(locator)?.trim().orEmpty()
    if (target.isBlank()) return@withContext null
    runCatching {
        val bitmap = when {
            target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                URL(target).openStream().use { BitmapFactory.decodeStream(it) }

            target.startsWith("file://", ignoreCase = true) ->
                BitmapFactory.decodeFile(target.removePrefix("file://"))

            else -> BitmapFactory.decodeFile(target)
        }
        bitmap?.asImageBitmap()
    }.getOrNull()
}
