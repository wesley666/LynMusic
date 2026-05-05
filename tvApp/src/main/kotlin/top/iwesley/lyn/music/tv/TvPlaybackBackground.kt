package top.iwesley.lyn.music.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import java.io.File
import java.net.URI
import java.net.URL
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.derivePlaybackArtworkBackgroundPalette
import top.iwesley.lyn.music.core.model.resolveArtworkDecodeSampleSize

@Composable
internal fun TvPlaybackArtworkBackground(
    artworkModel: String?,
    colors: TvPlaybackBackgroundColors?,
    modifier: Modifier = Modifier,
) {
    val defaultBaseColor = MaterialTheme.colorScheme.background
    val baseColor by animateColorAsState(
        targetValue = colors?.baseColor ?: defaultBaseColor,
        label = "tv-playback-background-base",
    )
    val primaryColor by animateColorAsState(
        targetValue = colors?.primaryColor ?: Color.Transparent,
        label = "tv-playback-background-primary",
    )
    val secondaryColor by animateColorAsState(
        targetValue = colors?.secondaryColor ?: Color.Transparent,
        label = "tv-playback-background-secondary",
    )
    val tertiaryColor by animateColorAsState(
        targetValue = colors?.tertiaryColor ?: Color.Transparent,
        label = "tv-playback-background-tertiary",
    )

    Box(modifier = modifier.background(baseColor)) {
        if (!artworkModel.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(model = artworkModel),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.14f, scaleY = 1.14f)
                    .blur(82.dp)
                    .alpha(0.28f),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = maxOf(size.width, size.height)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.58f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.10f, size.height * 0.72f),
                    radius = radius * 0.80f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = 0.46f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.96f, size.height * 0.18f),
                    radius = radius * 0.76f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiaryColor.copy(alpha = 0.34f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.52f, size.height * 0.48f),
                    radius = radius * 0.64f,
                ),
            )
            drawRect(color = Color.Black.copy(alpha = 0.36f))
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.20f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.48f),
                    ),
                    startY = 0f,
                    endY = size.height,
                ),
            )
        }
    }
}

@Composable
internal fun rememberTvPlaybackBackgroundColors(
    artworkModel: String?,
): TvPlaybackBackgroundColors? {
    val colors by produceState<TvPlaybackBackgroundColors?>(initialValue = null, artworkModel) {
        value = withContext(Dispatchers.IO) {
            deriveTvPlaybackBackgroundColors(artworkModel)
        }
    }
    return colors
}

private fun deriveTvPlaybackBackgroundColors(artworkModel: String?): TvPlaybackBackgroundColors? {
    if (artworkModel.isNullOrBlank()) return null
    return runCatching {
        val bitmap = decodeTvPlaybackBackgroundBitmap(artworkModel, TV_PLAYBACK_PALETTE_DECODE_SIZE_PX)
            ?: return@runCatching null
        try {
            val palette = derivePlaybackArtworkBackgroundPalette(sampleTvPlaybackBitmapPixels(bitmap))
                ?: return@runCatching null
            TvPlaybackBackgroundColors(
                baseColor = composeTvPlaybackColorFromArgb(palette.baseColorArgb),
                primaryColor = composeTvPlaybackColorFromArgb(palette.primaryColorArgb),
                secondaryColor = composeTvPlaybackColorFromArgb(palette.secondaryColorArgb),
                tertiaryColor = composeTvPlaybackColorFromArgb(palette.tertiaryColorArgb),
            )
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()
}

private fun decodeTvPlaybackBackgroundBitmap(target: String, maxDecodeSizePx: Int): Bitmap? {
    return when {
        target.startsWith("http://", ignoreCase = true) ||
            target.startsWith("https://", ignoreCase = true) -> {
            val payload = URL(target).openStream().use { it.readBytes() }
            decodeTvPlaybackArtworkBytes(payload, maxDecodeSizePx)
        }

        target.startsWith("file://", ignoreCase = true) -> {
            val path = runCatching { File(URI(target)).absolutePath }
                .getOrElse { target.removePrefix("file://") }
            decodeTvPlaybackArtworkFile(path, maxDecodeSizePx)
        }

        target.startsWith("content://", ignoreCase = true) -> null
        else -> decodeTvPlaybackArtworkFile(target, maxDecodeSizePx)
    }
}

private fun decodeTvPlaybackArtworkBytes(
    payload: ByteArray,
    maxDecodeSizePx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(payload, 0, payload.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeByteArray(
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
    )
}

private fun decodeTvPlaybackArtworkFile(
    path: String,
    maxDecodeSizePx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = resolveArtworkDecodeSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetSize = maxDecodeSizePx.coerceAtLeast(1),
            )
        },
    )
}

private fun sampleTvPlaybackBitmapPixels(bitmap: Bitmap): List<Int> {
    val stepX = max(1, bitmap.width / 24)
    val stepY = max(1, bitmap.height / 24)
    return buildList {
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                add(bitmap.getPixel(x, y))
            }
        }
    }
}

private fun composeTvPlaybackColorFromArgb(argb: Int): Color {
    return Color(
        red = ((argb ushr 16) and 0xFF) / 255f,
        green = ((argb ushr 8) and 0xFF) / 255f,
        blue = (argb and 0xFF) / 255f,
        alpha = ((argb ushr 24) and 0xFF) / 255f,
    )
}

internal data class TvPlaybackBackgroundColors(
    val baseColor: Color,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
)

private const val TV_PLAYBACK_PALETTE_DECODE_SIZE_PX = 384
