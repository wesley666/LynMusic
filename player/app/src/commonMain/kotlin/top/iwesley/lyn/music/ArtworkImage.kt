package top.iwesley.lyn.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.size.Size
import lynmusic.player.app.generated.resources.Res
import lynmusic.player.app.generated.resources.default_cover
import org.jetbrains.compose.resources.painterResource
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget

internal object ArtworkDecodeSize {
    const val Thumbnail: Int = 256
    const val Card: Int = 768
    const val Preview: Int = 1024
    const val Player: Int = 1536
    const val Palette: Int = 768
}

internal data class LynArtworkModel(
    val locator: String?,
    val target: String?,
    val cacheRemote: Boolean,
    val maxDecodeSizePx: Int,
)

@Composable
internal fun ConfigureLynArtworkImageLoader() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .maxBitmapSize(Size(ArtworkDecodeSize.Player, ArtworkDecodeSize.Player))
            .build()
    }
}

@Composable
internal fun LynArtworkImage(
    artworkLocator: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    cacheRemote: Boolean = true,
    maxDecodeSizePx: Int = ArtworkDecodeSize.Thumbnail,
    retainPreviousWhileLoading: Boolean = false,
) {
    val model = rememberLynArtworkModel(
        artworkLocator = artworkLocator,
        cacheRemote = cacheRemote,
        maxDecodeSizePx = maxDecodeSizePx,
    )
    LynArtworkAsyncImage(
        data = model.target?.let(::coilArtworkData),
        memoryCacheKey = lynArtworkMemoryCacheKey(model),
        placeholderMemoryCacheKey = lynArtworkMemoryPlaceholderKey(model),
        diskCacheKey = lynArtworkDiskCacheKey(model),
        cacheRemote = cacheRemote,
        maxDecodeSizePx = maxDecodeSizePx,
        targetPending = model.locator != null && model.target == null,
        retainPreviousWhileLoading = retainPreviousWhileLoading,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        alpha = alpha,
        colorFilter = colorFilter,
    )
}

@Composable
internal fun LynArtworkImage(
    artworkBytes: ByteArray?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    maxDecodeSizePx: Int = ArtworkDecodeSize.Preview,
    retainPreviousWhileLoading: Boolean = false,
) {
    val cacheKey = remember(artworkBytes, maxDecodeSizePx) {
        artworkBytes?.let { bytes -> "lyn-artwork-bytes:${bytes.stableContentHash()}:$maxDecodeSizePx" }
    }
    LynArtworkAsyncImage(
        data = artworkBytes,
        memoryCacheKey = cacheKey,
        placeholderMemoryCacheKey = null,
        diskCacheKey = null,
        cacheRemote = false,
        maxDecodeSizePx = maxDecodeSizePx,
        targetPending = false,
        retainPreviousWhileLoading = retainPreviousWhileLoading,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        alpha = alpha,
        colorFilter = colorFilter,
    )
}

@Composable
private fun LynArtworkAsyncImage(
    data: Any?,
    memoryCacheKey: String?,
    placeholderMemoryCacheKey: String?,
    diskCacheKey: String?,
    cacheRemote: Boolean,
    maxDecodeSizePx: Int,
    targetPending: Boolean,
    retainPreviousWhileLoading: Boolean,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    alignment: Alignment,
    alpha: Float,
    colorFilter: ColorFilter?,
) {
    val context = LocalPlatformContext.current
    val fallbackPainter = painterResource(Res.drawable.default_cover)
    var lastSuccessPainter by remember { mutableStateOf<Painter?>(null) }
    val request = remember(context, data, memoryCacheKey, placeholderMemoryCacheKey, diskCacheKey, cacheRemote, maxDecodeSizePx) {
        ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey(memoryCacheKey)
            .placeholderMemoryCacheKey(placeholderMemoryCacheKey)
            .diskCacheKey(diskCacheKey?.takeIf { cacheRemote })
            .diskCachePolicy(if (cacheRemote) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .maxBitmapSize(Size(maxDecodeSizePx, maxDecodeSizePx))
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = request,
        onSuccess = { state -> lastSuccessPainter = state.painter },
        onError = { lastSuccessPainter = null },
        contentScale = contentScale,
    )
    val painterState by painter.state.collectAsState()
    val displayPainter = resolveDisplayedArtworkPainter(
        state = painterState,
        fallbackPainter = fallbackPainter,
        lastSuccessPainter = lastSuccessPainter,
        retainPreviousWhileLoading = retainPreviousWhileLoading,
        targetPending = targetPending,
        dataMissing = data == null,
    )
    Box(modifier = modifier, contentAlignment = alignment) {
        Image(
            painter = displayPainter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            alignment = alignment,
            alpha = alpha,
            colorFilter = colorFilter,
        )
    }
}

private fun resolveDisplayedArtworkPainter(
    state: AsyncImagePainter.State,
    fallbackPainter: Painter,
    lastSuccessPainter: Painter?,
    retainPreviousWhileLoading: Boolean,
    targetPending: Boolean,
    dataMissing: Boolean,
): Painter {
    val retainedPainter = lastSuccessPainter.takeIf { retainPreviousWhileLoading }
    return when {
        dataMissing && targetPending && retainedPainter != null -> retainedPainter
        state is AsyncImagePainter.State.Success -> state.painter
        state is AsyncImagePainter.State.Error -> fallbackPainter
        state is AsyncImagePainter.State.Loading -> state.painter ?: retainedPainter ?: fallbackPainter
        state is AsyncImagePainter.State.Empty -> retainedPainter ?: fallbackPainter
        else -> fallbackPainter
    }
}

@Composable
private fun rememberLynArtworkModel(
    artworkLocator: String?,
    cacheRemote: Boolean,
    maxDecodeSizePx: Int,
): LynArtworkModel {
    val normalized = remember(artworkLocator) { normalizedArtworkCacheLocator(artworkLocator) }
    val initialTarget = remember(normalized) {
        normalized?.takeIf { parseNavidromeCoverLocator(it) == null }
    }
    val target by produceState<String?>(initialValue = initialTarget, normalized, initialTarget) {
        value = resolveArtworkCacheTarget(normalized) ?: initialTarget
    }
    return remember(normalized, target, cacheRemote, maxDecodeSizePx) {
        LynArtworkModel(
            locator = normalized,
            target = target,
            cacheRemote = cacheRemote,
            maxDecodeSizePx = maxDecodeSizePx,
        )
    }
}

private fun lynArtworkMemoryCacheKey(model: LynArtworkModel): String? {
    val base = model.locator ?: model.target ?: return null
    return "lyn-artwork:$base:${model.maxDecodeSizePx}"
}

private fun lynArtworkMemoryPlaceholderKey(model: LynArtworkModel): String? {
    val base = model.locator ?: model.target ?: return null
    val placeholderSize = when {
        model.maxDecodeSizePx > ArtworkDecodeSize.Card -> ArtworkDecodeSize.Card
        model.maxDecodeSizePx > ArtworkDecodeSize.Thumbnail -> ArtworkDecodeSize.Thumbnail
        else -> return null
    }
    return "lyn-artwork:$base:$placeholderSize"
}

private fun lynArtworkDiskCacheKey(model: LynArtworkModel): String? {
    val base = model.locator ?: model.target ?: return null
    return "lyn-artwork:$base"
}

private fun coilArtworkData(target: String): String {
    val trimmed = target.trim()
    return when {
        trimmed.startsWith("/", ignoreCase = false) -> "file://$trimmed"
        else -> trimmed
    }
}

private fun ByteArray.stableContentHash(): String {
    var hash = 14695981039346656037uL
    forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL
    }
    return hash.toString(16).padStart(16, '0')
}
