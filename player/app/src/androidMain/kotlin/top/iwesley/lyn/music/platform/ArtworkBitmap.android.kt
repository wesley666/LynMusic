package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.readRemoteSourceCandidateBytes
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.resolveArtworkDecodeSampleSize
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTargets
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash
import top.iwesley.lyn.music.domain.readRemotePlaybackUrlCandidateWithFallback
import top.iwesley.lyn.music.rememberLynArtworkModel
import kotlin.math.roundToInt

@Composable
actual fun rememberPlatformArtworkBitmap(
    locator: String?,
    artworkCacheKey: String?,
    cacheRemote: Boolean,
    maxDecodeSizePx: Int,
): ImageBitmap? {
    val context = LocalContext.current
    val fallbackBitmap = rememberBundledDefaultCoverBitmap()
    if (locator.isNullOrBlank()) return fallbackBitmap
    val model = rememberLynArtworkModel(
        artworkLocator = locator,
        artworkCacheKey = artworkCacheKey,
        cacheRemote = cacheRemote,
        maxDecodeSizePx = maxDecodeSizePx,
    )
    val target = model.target
    val bitmap by produceState<ImageBitmap?>(
        initialValue = fallbackBitmap,
        target,
        model.targetVersion,
        model.cacheVersion,
        model.cacheKey,
        cacheRemote,
        maxDecodeSizePx,
        fallbackBitmap,
    ) {
        value = target?.let { loadAndroidArtworkBitmap(context, it, cacheRemote, maxDecodeSizePx) }
    }
    return bitmap ?: fallbackBitmap
}

private suspend fun loadAndroidArtworkBitmap(
    context: Context,
    locator: String?,
    cacheRemote: Boolean,
    maxDecodeSizePx: Int,
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val normalizedLocator = normalizedArtworkCacheLocator(locator) ?: return@runCatching null
        val targets = resolveArtworkCacheTargets(normalizedLocator)
        val target = targets.firstOrNull()?.value ?: return@runCatching null
        val bitmap = when {
            isRemoteArtworkTarget(target) -> {
                val cacheDirectory = File(context.cacheDir, "artwork-cache").apply { mkdirs() }
                val cacheKey = normalizedLocator.stableArtworkCacheHash()
                val existingCacheFile = findValidAndroidArtworkCacheFile(cacheDirectory, cacheKey)
                if (existingCacheFile != null) {
                    decodeAndroidArtworkFile(existingCacheFile.absolutePath, maxDecodeSizePx)
                } else {
                    val (remoteTarget, payload) = readAndroidRemoteArtworkPayload(targets) ?: return@runCatching null
                    if (cacheRemote) {
                        writeAndroidArtworkCacheFileAtomically(
                            directory = cacheDirectory,
                            fileName = "$cacheKey${inferArtworkFileExtension(locator = remoteTarget.value, bytes = payload)}",
                            payload = payload,
                        )
                    }
                    NavidromeLocatorRuntime.markResolvedUrlSuccess(remoteTarget)
                    decodeAndroidArtworkBytes(payload, maxDecodeSizePx)
                }
            }
            target.startsWith("file://", ignoreCase = true) ->
                decodeAndroidArtworkFile(
                    runCatching { File(URI(target)).absolutePath }.getOrElse { target.removePrefix("file://") },
                    maxDecodeSizePx,
                )

            else -> decodeAndroidArtworkFile(target, maxDecodeSizePx)
        }
        bitmap?.asImageBitmap()
    }.getOrNull()
}

internal fun decodeAndroidArtworkBytes(
    payload: ByteArray,
    maxDecodeSizePx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(payload, 0, payload.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val decoded = BitmapFactory.decodeByteArray(
        payload,
        0,
        payload.size,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = resolveArtworkDecodeSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetSize = maxDecodeSizePx.coerceAtLeast(1),
            )
        },
    ) ?: return null
    return decoded.scaleDownAndroidArtworkBitmap(maxDecodeSizePx)
}

private fun decodeAndroidArtworkFile(
    path: String,
    maxDecodeSizePx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val decoded = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = resolveArtworkDecodeSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetSize = maxDecodeSizePx.coerceAtLeast(1),
            )
        },
    ) ?: return null
    return decoded.scaleDownAndroidArtworkBitmap(maxDecodeSizePx)
}

private fun Bitmap.scaleDownAndroidArtworkBitmap(maxDecodeSizePx: Int): Bitmap {
    val maxSize = maxDecodeSizePx.coerceAtLeast(1)
    val currentMax = maxOf(width, height)
    if (currentMax <= maxSize) return this
    val scale = maxSize.toFloat() / currentMax.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    if (scaled !== this) {
        recycle()
    }
    return scaled
}

private fun isRemoteArtworkTarget(target: String): Boolean {
    return target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
}

private suspend fun readAndroidRemoteArtworkPayload(
    targets: List<RemotePlaybackUrlCandidate>,
): Pair<RemotePlaybackUrlCandidate, ByteArray>? {
    return readRemotePlaybackUrlCandidateWithFallback(
        candidates = targets,
        isRemoteUrl = ::isRemoteArtworkTarget,
        read = { target -> readRemoteSourceCandidateBytes(target) },
        isValidPayload = ::isCompleteArtworkPayload,
    )
}

private fun findValidAndroidArtworkCacheFile(directory: File, cacheKey: String): File? {
    return directory.listFiles()
        ?.asSequence()
        ?.filter { file ->
            file.isFile &&
                file.name.startsWith(cacheKey) &&
                !file.name.contains(ANDROID_ARTWORK_CACHE_TEMP_MARKER) &&
                file.length() > 0L
        }
        ?.firstOrNull { file ->
            val valid = runCatching { isCompleteArtworkPayload(file.readBytes()) }.getOrDefault(false)
            if (!valid) {
                runCatching { file.delete() }
            }
            valid
        }
}

private fun writeAndroidArtworkCacheFileAtomically(
    directory: File,
    fileName: String,
    payload: ByteArray,
): File? {
    if (!isCompleteArtworkPayload(payload)) return null
    val output = directory.resolve(fileName)
    if (output.exists() && output.length() > 0L) {
        if (runCatching { isCompleteArtworkPayload(output.readBytes()) }.getOrDefault(false)) {
            return output
        }
        runCatching { output.delete() }
    }
    val temporary = directory.resolve("$fileName$ANDROID_ARTWORK_CACHE_TEMP_MARKER${System.nanoTime()}")
    return runCatching {
        temporary.writeBytes(payload)
        if (temporary.length() != payload.size.toLong()) {
            return@runCatching null
        }
        if (output.exists() && runCatching { isCompleteArtworkPayload(output.readBytes()) }.getOrDefault(false)) {
            return@runCatching output
        }
        runCatching { output.delete() }
        if (!temporary.renameTo(output)) {
            temporary.copyTo(output, overwrite = true)
            temporary.delete()
        }
        output.takeIf {
            it.exists() &&
                it.length() > 0L &&
                runCatching { isCompleteArtworkPayload(it.readBytes()) }.getOrDefault(false)
        }
    }.also {
        if (temporary.exists()) {
            runCatching { temporary.delete() }
        }
    }.getOrNull()
}

private const val ANDROID_ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
