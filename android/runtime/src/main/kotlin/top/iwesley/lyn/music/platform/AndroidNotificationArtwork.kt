package top.iwesley.lyn.music.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.resolveArtworkDecodeSampleSize
import top.iwesley.lyn.music.core.model.resolveArtworkSquareCropRect
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey

private const val NOTIFICATION_ARTWORK_SIZE_PX = 512
private const val OPAQUE_SAMPLE_ALPHA_THRESHOLD = 16

internal data class AndroidNotificationArtworkLookup(
    val locator: String,
    val cacheKey: String,
) {
    fun bitmapKey(cacheVersion: Long): String = "$locator\u0000$cacheKey\u0000$cacheVersion"

    companion object {
        fun from(snapshot: PlaybackSnapshot): AndroidNotificationArtworkLookup? {
            val track = snapshot.currentTrack ?: return null
            val normalizedLocator = snapshot.currentDisplayArtworkLocator
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val cacheKey = trackArtworkCacheKey(track)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: normalizedLocator
            return AndroidNotificationArtworkLookup(
                locator = normalizedLocator,
                cacheKey = cacheKey,
            )
        }
    }
}

internal suspend fun resolveAndroidNotificationArtworkBitmap(
    locator: String?,
    artworkCacheKey: String?,
    artworkCacheStore: ArtworkCacheStore,
): Bitmap? = withContext(Dispatchers.IO) {
    val normalized = locator?.trim().orEmpty().ifBlank { null } ?: return@withContext null
    val cacheKey = artworkCacheKey?.trim()?.takeIf { it.isNotEmpty() } ?: normalized
    val target = artworkCacheStore.cache(normalized, cacheKey) ?: return@withContext null
    decodeAndroidNotificationArtworkBitmap(target)
}

private fun decodeAndroidNotificationArtworkBitmap(
    target: String,
    targetSizePx: Int = NOTIFICATION_ARTWORK_SIZE_PX,
): Bitmap? {
    if (targetSizePx <= 0) return null
    val path = File(target).absolutePath
    val payload = runCatching { File(path).readBytes() }.getOrNull() ?: return null
    if (!isCompleteArtworkPayload(payload)) return null
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val source = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = resolveArtworkDecodeSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetSize = targetSizePx,
            )
        },
    ) ?: return null
    return runCatching {
        renderAndroidNotificationArtworkBitmap(source, targetSizePx)
    }.also {
        if (!source.isRecycled) {
            source.recycle()
        }
    }.getOrNull()
}

private fun renderAndroidNotificationArtworkBitmap(
    source: Bitmap,
    targetSizePx: Int,
): Bitmap? {
    val crop = resolveArtworkSquareCropRect(source.width, source.height) ?: return null
    val output = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(resolveAverageOpaqueColor(source) ?: Color.rgb(42, 42, 42))
    canvas.drawBitmap(
        source,
        Rect(crop.left, crop.top, crop.right, crop.bottom),
        Rect(0, 0, targetSizePx, targetSizePx),
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        },
    )
    return output
}

private fun resolveAverageOpaqueColor(source: Bitmap): Int? {
    if (source.width <= 0 || source.height <= 0) return null
    val stepX = maxOf(1, source.width / 32)
    val stepY = maxOf(1, source.height / 32)
    var count = 0L
    var red = 0L
    var green = 0L
    var blue = 0L
    for (y in 0 until source.height step stepY) {
        for (x in 0 until source.width step stepX) {
            val color = source.getPixel(x, y)
            val alpha = (color ushr 24) and 0xFF
            if (alpha <= OPAQUE_SAMPLE_ALPHA_THRESHOLD) continue
            red += (color ushr 16) and 0xFF
            green += (color ushr 8) and 0xFF
            blue += color and 0xFF
            count += 1
        }
    }
    if (count == 0L) return null
    return Color.rgb(
        (red / count).toInt(),
        (green / count).toInt(),
        (blue / count).toInt(),
    )
}
