package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.ArtworkWritePolicy
import top.iwesley.lyn.music.core.model.ArtworkCacheResult

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
import androidx.compose.runtime.staticCompositionLocalOf
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
import kotlinx.coroutines.flow.flowOf
import lynmusic.player.app.generated.resources.Res
import lynmusic.player.app.generated.resources.default_cover
import org.jetbrains.compose.resources.painterResource
import top.iwesley.lyn.music.core.model.ArtworkCachedTarget
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.isIosArtworkCacheBackedLocator
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseEmbyCoverLocator
import top.iwesley.lyn.music.core.model.parseFnMusicCoverLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleCoverLocator

internal object ArtworkDecodeSize {
    const val Thumbnail: Int = 256
    const val Card: Int = 768
    const val Preview: Int = 1024
    const val Player: Int = 1536
    const val Palette: Int = 768
}

internal data class LynArtworkModel(
    val locator: String?,
    val cacheKey: String?,
    val target: String?,
    val targetVersion: String?,
    val isLocalFileTarget: Boolean,
    val cacheRemote: Boolean,
    val maxDecodeSizePx: Int,
    val cacheVersion: Long,
    val targetPending: Boolean,
)

internal data class LynArtworkTargetRequestKey(
    val normalized: String?,
    val requestCacheKey: String?,
    val cacheRemote: Boolean,
    val cacheVersion: Long,
    val artworkCacheStore: ArtworkCacheStore,
)

internal data class LynArtworkTargetState(
    val requestKey: LynArtworkTargetRequestKey,
    val target: LynResolvedArtworkTarget?,
    val resolved: Boolean,
)

internal data class LynResolvedArtworkTarget(
    val locator: String,
    val target: String,
    val version: String? = null,
    val isLocalFile: Boolean = false,
)

private object PassthroughArtworkCacheStore : ArtworkCacheStore {
    override suspend fun cache(locator: String, cacheKey: String, policy: ArtworkWritePolicy): ArtworkCacheResult? = ArtworkCacheResult(locator, true)
}

internal val LocalArtworkCacheStore = staticCompositionLocalOf<ArtworkCacheStore> {
    PassthroughArtworkCacheStore
}

@Composable
internal fun ConfigureLynArtworkImageLoader() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .diskCache(null)
            .maxBitmapSize(Size(ArtworkDecodeSize.Player, ArtworkDecodeSize.Player))
            .build()
    }
}

@Composable
internal fun LynArtworkImage(
    artworkLocator: String?,
    contentDescription: String?,
    artworkCacheKey: String? = null,
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
        artworkCacheKey = artworkCacheKey,
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
        targetPending = model.targetPending,
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
internal fun rememberLynArtworkModel(
    artworkLocator: String?,
    artworkCacheKey: String?,
    cacheRemote: Boolean,
    maxDecodeSizePx: Int,
): LynArtworkModel {
    val artworkCacheStore = LocalArtworkCacheStore.current
    val normalized = remember(artworkLocator) { normalizedArtworkCacheLocator(artworkLocator) }
    val requestCacheKey = remember(artworkCacheKey, normalized) {
        artworkCacheKey
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: normalized
    }
    val cacheVersion by remember(artworkCacheStore, requestCacheKey) {
        requestCacheKey?.let(artworkCacheStore::observeVersion) ?: flowOf(0L)
    }.collectAsState(initial = 0L)
    return rememberLynArtworkModel(
        normalized = normalized,
        requestCacheKey = requestCacheKey,
        cacheRemote = cacheRemote,
        maxDecodeSizePx = maxDecodeSizePx,
        cacheVersion = cacheVersion,
        artworkCacheStore = artworkCacheStore,
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
    if (data == null) {
        val displayPainter = lastSuccessPainter
            .takeIf { retainPreviousWhileLoading && targetPending }
            ?: fallbackPainter
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
        return
    }
    val request = remember(context, data, memoryCacheKey, placeholderMemoryCacheKey, diskCacheKey, cacheRemote, maxDecodeSizePx) {
        val shouldUseDiskCache = false
        ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey(memoryCacheKey)
            .placeholderMemoryCacheKey(placeholderMemoryCacheKey)
            .diskCacheKey(diskCacheKey?.takeIf { shouldUseDiskCache })
            .diskCachePolicy(if (shouldUseDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
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
        dataMissing = false,
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

internal fun resolveDisplayedArtworkPainter(
    state: AsyncImagePainter.State,
    fallbackPainter: Painter,
    lastSuccessPainter: Painter?,
    retainPreviousWhileLoading: Boolean,
    targetPending: Boolean,
    dataMissing: Boolean,
): Painter {
    val retainedPainter = lastSuccessPainter.takeIf { retainPreviousWhileLoading }
    return when {
        dataMissing && !targetPending -> fallbackPainter
        dataMissing && targetPending && retainedPainter != null -> retainedPainter
        state is AsyncImagePainter.State.Success -> state.painter
        state is AsyncImagePainter.State.Error -> fallbackPainter
        state is AsyncImagePainter.State.Loading -> state.painter ?: retainedPainter ?: fallbackPainter
        state is AsyncImagePainter.State.Empty -> retainedPainter ?: fallbackPainter
        else -> fallbackPainter
    }
}

internal expect suspend fun resolveLynArtworkTarget(
    locator: String?,
    cacheKey: String?,
    cacheRemote: Boolean,
    artworkCacheStore: ArtworkCacheStore,
): LynResolvedArtworkTarget?

@Composable
private fun rememberLynArtworkModel(
    normalized: String?,
    requestCacheKey: String?,
    cacheRemote: Boolean,
    maxDecodeSizePx: Int,
    cacheVersion: Long,
    artworkCacheStore: ArtworkCacheStore,
): LynArtworkModel {
    val requestKey = remember(normalized, requestCacheKey, cacheRemote, cacheVersion, artworkCacheStore) {
        LynArtworkTargetRequestKey(
            normalized = normalized,
            requestCacheKey = requestCacheKey,
            cacheRemote = cacheRemote,
            cacheVersion = cacheVersion,
            artworkCacheStore = artworkCacheStore,
        )
    }
    val initialTarget = remember(requestKey) {
        initialLynArtworkTarget(
            normalized = normalized,
            requestCacheKey = requestCacheKey,
            cacheRemote = cacheRemote,
            artworkCacheStore = artworkCacheStore,
        )
    }
    val initialTargetState = remember(requestKey, initialTarget) {
        initialLynArtworkTargetState(requestKey = requestKey, initialTarget = initialTarget)
    }
    val producedTargetState by produceState(
        initialValue = initialTargetState,
        requestKey,
    ) {
        val resolvedTarget = resolveLynArtworkTarget(
            locator = normalized,
            cacheKey = requestCacheKey,
            cacheRemote = cacheRemote,
            artworkCacheStore = artworkCacheStore,
        )
        value = LynArtworkTargetState(
            requestKey = requestKey,
            target = resolvedTarget ?: initialTarget,
            resolved = true,
        )
    }
    val resolvedTargetState = selectLynArtworkTargetState(
        currentRequestKey = requestKey,
        initialTargetState = initialTargetState,
        producedTargetState = producedTargetState,
    )
    return remember(normalized, requestCacheKey, resolvedTargetState, cacheRemote, maxDecodeSizePx, cacheVersion) {
        val resolvedTarget = resolvedTargetState.target
        LynArtworkModel(
            locator = normalized,
            cacheKey = requestCacheKey,
            target = resolvedTarget?.target,
            targetVersion = resolvedTarget?.version,
            isLocalFileTarget = resolvedTarget?.isLocalFile == true,
            cacheRemote = cacheRemote,
            maxDecodeSizePx = maxDecodeSizePx,
            cacheVersion = cacheVersion,
            targetPending = normalized != null && resolvedTarget == null && !resolvedTargetState.resolved,
        )
    }
}

internal fun initialLynArtworkTargetState(
    requestKey: LynArtworkTargetRequestKey,
    initialTarget: LynResolvedArtworkTarget?,
): LynArtworkTargetState {
    return LynArtworkTargetState(
        requestKey = requestKey,
        target = initialTarget,
        resolved = requestKey.normalized == null || initialTarget != null,
    )
}

internal fun selectLynArtworkTargetState(
    currentRequestKey: LynArtworkTargetRequestKey,
    initialTargetState: LynArtworkTargetState,
    producedTargetState: LynArtworkTargetState,
): LynArtworkTargetState {
    return producedTargetState.takeIf { it.requestKey == currentRequestKey } ?: initialTargetState
}

internal fun initialLynArtworkTarget(
    normalized: String?,
    requestCacheKey: String?,
    cacheRemote: Boolean,
    artworkCacheStore: ArtworkCacheStore,
): LynResolvedArtworkTarget? {
    if (cacheRemote) {
        requestCacheKey
            ?.let(artworkCacheStore::peekCachedTarget)
            ?.let { cachedTarget ->
                return cachedTarget.toLynResolvedArtworkTarget(locator = normalized ?: cachedTarget.target)
            }
    }
    return normalized
        ?.takeIf { shouldUseInitialArtworkTarget(it, cacheRemote) }
        ?.let { target ->
            LynResolvedArtworkTarget(
                locator = target,
                target = target,
                version = null,
                isLocalFile = false,
            )
        }
}

private fun ArtworkCachedTarget.toLynResolvedArtworkTarget(
    locator: String,
): LynResolvedArtworkTarget {
    return LynResolvedArtworkTarget(
        locator = locator,
        target = target,
        version = version,
        isLocalFile = isLocalFile,
    )
}

private fun shouldUseInitialArtworkTarget(
    normalizedLocator: String,
    cacheRemote: Boolean,
): Boolean {
    if (parseSubsonicCompatibleCoverLocator(normalizedLocator) != null) return false
    if (parseEmbyCoverLocator(normalizedLocator) != null) return false
    if (parseFnMusicCoverLocator(normalizedLocator) != null) return false
    if (isIosArtworkCacheBackedLocator(normalizedLocator)) return false
    if (!cacheRemote) return true
    return !normalizedLocator.startsWith("http://", ignoreCase = true) &&
        !normalizedLocator.startsWith("https://", ignoreCase = true)
}

internal fun lynArtworkMemoryCacheKey(model: LynArtworkModel): String? {
    val base = model.cacheKey ?: model.locator ?: model.target ?: return null
    return buildLynArtworkCacheKey(
        base = base,
        sizePx = model.maxDecodeSizePx,
        version = model.targetVersion,
        cacheVersion = model.cacheVersion,
    )
}

internal fun lynArtworkMemoryPlaceholderKey(model: LynArtworkModel): String? {
    val base = model.cacheKey ?: model.locator ?: model.target ?: return null
    val placeholderSize = when {
        model.maxDecodeSizePx > ArtworkDecodeSize.Card -> ArtworkDecodeSize.Card
        model.maxDecodeSizePx > ArtworkDecodeSize.Thumbnail -> ArtworkDecodeSize.Thumbnail
        else -> return null
    }
    return buildLynArtworkCacheKey(
        base = base,
        sizePx = placeholderSize,
        version = model.targetVersion,
        cacheVersion = model.cacheVersion,
    )
}

private fun lynArtworkDiskCacheKey(model: LynArtworkModel): String? {
    if (model.isLocalFileTarget) return null
    val base = model.cacheKey ?: model.locator ?: model.target ?: return null
    return "lyn-artwork:$base"
}

internal fun buildLynArtworkCacheKey(
    base: String,
    sizePx: Int,
    version: String?,
    cacheVersion: Long = 0L,
): String {
    return buildString {
        append("lyn-artwork:")
        append(base)
        append(':')
        append(sizePx)
        if (!version.isNullOrBlank()) {
            append(':')
            append(version)
        }
        if (cacheVersion > 0L) {
            append(":v")
            append(cacheVersion)
        }
    }
}

internal expect fun coilArtworkData(target: String): String

private fun ByteArray.stableContentHash(): String {
    var hash = 14695981039346656037uL
    forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL
    }
    return hash.toString(16).padStart(16, '0')
}
