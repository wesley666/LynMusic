package top.iwesley.lyn.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPlatformImageBitmap(bytes: ByteArray?, maxDecodeSizePx: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, bytes, maxDecodeSizePx) {
        value = loadPlatformPreviewBitmap(bytes, maxDecodeSizePx)
    }
    return bitmap
}

private suspend fun loadPlatformPreviewBitmap(bytes: ByteArray?, maxDecodeSizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    val payload = bytes ?: return@withContext null
    runCatching {
        decodeJvmArtworkImageBitmap(payload, maxDecodeSizePx)
    }.getOrNull()
}
