package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.plus
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText

import top.iwesley.lyn.music.core.model.uiPlural

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.iwesley.lyn.music.core.model.ArtworkTintTheme
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import top.iwesley.lyn.music.core.model.derivePlaybackArtworkBackgroundPalette
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DetailBackButton(
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(uiString(Res.string.common_back))
    }
}

@Composable
fun TrackArtworkThumbnail(
    artworkLocator: String?,
    artworkCacheKey: String? = null,
    modifier: Modifier = Modifier.size(52.dp),
) {
    val shellColors = mainShellColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(1.dp))
            .background(shellColors.cardContainer)
            .border(
                border = BorderStroke(1.dp, shellColors.cardBorder),
                shape = RoundedCornerShape(1.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        LynArtworkImage(
            artworkLocator = artworkLocator,
            contentDescription = null,
            artworkCacheKey = artworkCacheKey,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            maxDecodeSizePx = ArtworkDecodeSize.Thumbnail,
        )
    }
}

@Composable
fun MainShellElevatedCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
        border = BorderStroke(1.dp, shellColors.cardBorder),
        content = content,
    )
}

@Composable
fun MainShellAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val shellColors = mainShellColors
    AssistChip(
        onClick = onClick,
        label = label,
        leadingIcon = leadingIcon,
        enabled = enabled,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = shellColors.navContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = shellColors.cardContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    )
}

fun importScanProgressLabelText(progress: ImportScanProgress): UiText {
    if (progress.phase == ImportScanPhase.Persisting) return uiText(Res.string.startup_updating_library)
    val total = progress.totalTrackCount?.takeIf { it > 0 }
    return if (total == null) {
        uiPlural(Res.plurals.import_completed_track_count, (progress.importedTrackCount.coerceAtLeast(0)).toInt(), progress.importedTrackCount.coerceAtLeast(0))
    } else {
        uiText(Res.string.import_track_progress, progress.importedTrackCount.coerceAtLeast(0), total)
    }
}

@Composable
fun importScanProgressLabel(progress: ImportScanProgress): String = importScanProgressLabelText(progress).displayText()

fun importScanProgressFraction(progress: ImportScanProgress): Float? {
    if (progress.phase == ImportScanPhase.Persisting) return null
    val total = progress.totalTrackCount?.takeIf { it > 0 } ?: return null
    return progress.importedTrackCount.toFloat().coerceIn(0f, total.toFloat()) / total.toFloat()
}

@Composable
fun ButtonLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        modifier = modifier.size(16.dp),
        color = LocalContentColor.current,
        strokeWidth = 2.dp,
    )
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String,
) {
    val shellColors = mainShellColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = shellColors.secondaryText)
        }
    }
}

@Composable
fun BannerCard(
    message: String,
    onDismiss: () -> Unit,
) {
    val shellColors = mainShellColors
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.selectedContainer),
        border = BorderStroke(1.dp, shellColors.selectedBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(uiString(Res.string.common_close)) }
        }
    }
}

@Composable
fun ToastCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = shellColors.navContainer,
        ),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    body: String,
) {
    val shellColors = mainShellColors
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlayerArtworkDisplay(
    style: PlayerArtworkStyle,
    artworkSize: Dp,
    artworkBitmap: ImageBitmap? = null,
    artworkLocator: String? = null,
    artworkCacheKey: String? = null,
    spinning: Boolean = false,
    enableArtworkTint: Boolean = false,
    vinylArtworkDiameterFraction: Float = PLAYER_ARTWORK_DEFAULT_VINYL_ARTWORK_DIAMETER_FRACTION,
    vinylInnerGlowDiameterFraction: Float = PLAYER_ARTWORK_DEFAULT_VINYL_INNER_GLOW_DIAMETER_FRACTION,
    maxArtworkDecodeSizePx: Int = ArtworkDecodeSize.Thumbnail,
    retainPreviousArtworkWhileLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    when (style) {
        PlayerArtworkStyle.VINYL -> VinylPlaceholder(
            vinylSize = artworkSize,
            artworkBitmap = artworkBitmap,
            artworkLocator = artworkLocator,
            artworkCacheKey = artworkCacheKey,
            spinning = spinning,
            enableArtworkTint = enableArtworkTint,
            artworkDiameterFraction = vinylArtworkDiameterFraction,
            innerGlowDiameterFraction = vinylInnerGlowDiameterFraction,
            maxArtworkDecodeSizePx = maxArtworkDecodeSizePx,
            retainPreviousArtworkWhileLoading = retainPreviousArtworkWhileLoading,
            modifier = modifier,
        )

        PlayerArtworkStyle.HALF_RECORD -> HalfOutRecordArtwork(
            artworkSize = artworkSize,
            artworkBitmap = artworkBitmap,
            artworkLocator = artworkLocator,
            artworkCacheKey = artworkCacheKey,
            spinning = spinning,
            maxArtworkDecodeSizePx = maxArtworkDecodeSizePx,
            retainPreviousArtworkWhileLoading = retainPreviousArtworkWhileLoading,
            modifier = modifier,
        )

        PlayerArtworkStyle.MINIMAL_COVER -> MinimalCoverArtwork(
            artworkSize = artworkSize,
            artworkBitmap = artworkBitmap,
            artworkLocator = artworkLocator,
            artworkCacheKey = artworkCacheKey,
            maxArtworkDecodeSizePx = maxArtworkDecodeSizePx,
            retainPreviousArtworkWhileLoading = retainPreviousArtworkWhileLoading,
            modifier = modifier,
        )
    }
}

fun playerArtworkDisplayVisualWidthFactor(style: PlayerArtworkStyle): Float {
    return when (style) {
        PlayerArtworkStyle.HALF_RECORD -> 1.24f
        PlayerArtworkStyle.VINYL,
        PlayerArtworkStyle.MINIMAL_COVER -> 1f
    }
}

fun playerArtworkDisplayVisualHeightFactor(style: PlayerArtworkStyle): Float {
    return when (style) {
        PlayerArtworkStyle.VINYL,
        PlayerArtworkStyle.HALF_RECORD,
        PlayerArtworkStyle.MINIMAL_COVER -> 1f
    }
}

@Composable
private fun HalfOutRecordArtwork(
    artworkSize: Dp,
    artworkBitmap: ImageBitmap?,
    artworkLocator: String?,
    artworkCacheKey: String?,
    spinning: Boolean,
    maxArtworkDecodeSizePx: Int,
    retainPreviousArtworkWhileLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val visualWidth = artworkSize * playerArtworkDisplayVisualWidthFactor(PlayerArtworkStyle.HALF_RECORD)
    val sleeveSize = artworkSize * 0.82f
    val recordSize = artworkSize * 0.86f
    val sleeveRadius = artworkSize * 0.035f
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        while (true) {
            val start = rotation.value % 360f
            rotation.snapTo(start)
            rotation.animateTo(
                targetValue = start + 360f,
                animationSpec = tween(
                    durationMillis = 18_000,
                    easing = LinearEasing,
                ),
            )
        }
    }
    Box(
        modifier = modifier.size(artworkSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(visualWidth)
                .height(artworkSize),
            contentAlignment = Alignment.CenterStart,
        ) {
            HalfOutRecordDisc(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(recordSize)
                    .graphicsLayer { rotationZ = rotation.value },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(sleeveSize)
                    .clip(RoundedCornerShape(sleeveRadius))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(sleeveRadius),
                    ),
            ) {
                if (artworkBitmap != null && artworkLocator.isNullOrBlank()) {
                    Image(
                        bitmap = artworkBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    LynArtworkImage(
                        artworkLocator = artworkLocator,
                        contentDescription = null,
                        artworkCacheKey = artworkCacheKey,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        maxDecodeSizePx = maxArtworkDecodeSizePx,
                        retainPreviousWhileLoading = retainPreviousArtworkWhileLoading,
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.18f),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun HalfOutRecordDisc(
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val label = "LynMusic"
        val labelFontSize = if (maxWidth < 260.dp) 10.sp else 13.sp
        val labelTextStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = labelFontSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        val textMeasurer = rememberTextMeasurer(cacheSize = label.length)
        val labelCharacters = remember(label) { label.map { it.toString() } }
        val labelCharacterWidthsPx = remember(labelCharacters, labelTextStyle, textMeasurer) {
            labelCharacters.map { character ->
                textMeasurer.measure(
                    text = character,
                    style = labelTextStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width.toFloat()
            }
        }
        val density = LocalDensity.current
        val labelRadius = maxWidth * 0.40f
        val labelRadiusPx = with(density) { labelRadius.toPx() }
        val labelGapPx = with(density) { 1.dp.toPx() }
        val labelCenterAngle = -41f
        val labelTotalArcLengthPx = remember(labelCharacterWidthsPx, labelGapPx) {
            labelCharacterWidthsPx.sum() + labelGapPx * (labelCharacterWidthsPx.size - 1).coerceAtLeast(0)
        }
        val labelTotalAngle = if (labelRadiusPx > 0f) {
            labelTotalArcLengthPx / labelRadiusPx * 180f / PI.toFloat()
        } else {
            0f
        }
        val labelStartAngle = labelCenterAngle - labelTotalAngle / 2f
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF303238),
                        0.22f to Color(0xFF15161A),
                        0.70f to Color(0xFF07080A),
                        1.0f to Color(0xFF020304),
                    ),
                ),
                radius = radius,
            )
            repeat(28) { index ->
                val fraction = (index + 2) / 31f
                val ringRadius = radius * fraction
                val ringAlpha = 0.10f - fraction * 0.055f
                if (ringAlpha > 0f) {
                    drawCircle(
                        color = Color.White.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        style = Stroke(width = if (index % 5 == 0) 1.5f else 0.8f),
                    )
                }
            }
            val highlightCenter = Offset(center.x + radius * 0.34f, center.y - radius * 0.36f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = 0.24f),
                        0.48f to Color.White.copy(alpha = 0.10f),
                        1.0f to Color.Transparent,
                    ),
                    center = highlightCenter,
                    radius = radius * 0.42f,
                ),
                radius = radius * 0.42f,
                center = highlightCenter,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius * 0.14f,
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.36f),
                radius = radius * 0.052f,
            )
        }
        var consumedArcLengthPx = 0f
        labelCharacters.forEachIndexed { index, character ->
            val characterWidthPx = labelCharacterWidthsPx.getOrElse(index) { 0f }
            val characterCenterArcLengthPx = consumedArcLengthPx + characterWidthPx / 2f
            val angleOffset = if (labelRadiusPx > 0f) {
                characterCenterArcLengthPx / labelRadiusPx * 180f / PI.toFloat()
            } else {
                0f
            }
            val angle = labelStartAngle + angleOffset
            val radians = angle * PI.toFloat() / 180f
            Text(
                text = character,
                color = Color.White.copy(alpha = 0.16f),
                style = labelTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = labelRadius * cos(radians.toDouble()).toFloat(),
                        y = labelRadius * sin(radians.toDouble()).toFloat(),
                    )
                    .graphicsLayer { rotationZ = angle + 90f },
            )
            consumedArcLengthPx += characterWidthPx + labelGapPx
        }
    }
}

@Composable
private fun MinimalCoverArtwork(
    artworkSize: Dp,
    artworkBitmap: ImageBitmap?,
    artworkLocator: String?,
    artworkCacheKey: String?,
    maxArtworkDecodeSizePx: Int,
    retainPreviousArtworkWhileLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val radius = artworkSize * 0.05f
    Box(
        modifier = modifier
            .size(artworkSize)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(radius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkBitmap != null && artworkLocator.isNullOrBlank()) {
            Image(
                bitmap = artworkBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            LynArtworkImage(
                artworkLocator = artworkLocator,
                contentDescription = null,
                artworkCacheKey = artworkCacheKey,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                maxDecodeSizePx = maxArtworkDecodeSizePx,
                retainPreviousWhileLoading = retainPreviousArtworkWhileLoading,
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.05f),
                            0.62f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.16f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
fun VinylPlaceholder(
    vinylSize: Dp,
    artworkBitmap: ImageBitmap? = null,
    artworkLocator: String? = null,
    artworkCacheKey: String? = null,
    spinning: Boolean = false,
    enableArtworkTint: Boolean = false,
    artworkDiameterFraction: Float = DEFAULT_VINYL_ARTWORK_DIAMETER_FRACTION,
    innerGlowDiameterFraction: Float = DEFAULT_VINYL_INNER_GLOW_DIAMETER_FRACTION,
    maxArtworkDecodeSizePx: Int = ArtworkDecodeSize.Thumbnail,
    retainPreviousArtworkWhileLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val normalizedArtworkDiameterFraction = artworkDiameterFraction.coerceIn(0.2f, 1f)
    val normalizedInnerGlowDiameterFraction = innerGlowDiameterFraction
        .coerceIn(normalizedArtworkDiameterFraction, 1f)
    val resolvedArtworkBitmap = artworkBitmap
    val palette = rememberVinylArtworkPalette(
        artworkBitmap = resolvedArtworkBitmap,
        enabled = enableArtworkTint,
    )
    val animatedRimColor by animateColorAsState(
        targetValue = palette?.rimColor ?: Color.White.copy(alpha = 0.18f),
        label = "vinyl-rim-color",
    )
    val animatedGlowColor by animateColorAsState(
        targetValue = palette?.glowColor ?: Color.Transparent,
        label = "vinyl-glow-color",
    )
    val animatedInnerGlowColor by animateColorAsState(
        targetValue = palette?.innerGlowColor ?: Color.Transparent,
        label = "vinyl-inner-glow-color",
    )
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        while (true) {
            val start = rotation.value % 360f
            rotation.snapTo(start)
            rotation.animateTo(
                targetValue = start + 360f,
                animationSpec = tween(
                    durationMillis = 18_000,
                    easing = LinearEasing,
                ),
            )
        }
    }
    Box(
        modifier = modifier.size(vinylSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.56f to Color.Transparent,
                            0.82f to animatedGlowColor.copy(alpha = 0.22f),
                            1.0f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(vinylSize)
                .graphicsLayer { rotationZ = rotation.value },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = min(size.width, size.height) / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF292A2E),
                            0.42f to Color(0xFF141518),
                            0.78f to Color(0xFF090A0C),
                            1.0f to Color(0xFF040506),
                        ),
                    ),
                    radius = radius,
                )
                val ringEnd = radius * 0.94f
                val ringStart = (radius * normalizedArtworkDiameterFraction).coerceAtMost(ringEnd)
                if (ringStart < ringEnd) {
                    repeat(14) { index ->
                        val fraction = index / 13f
                        val ringRadius = ringStart + (ringEnd - ringStart) * fraction
                        val ringAlpha = 0.055f - fraction * 0.03f
                        if (ringAlpha > 0f) {
                            drawCircle(
                                color = Color.White.copy(alpha = ringAlpha),
                                radius = ringRadius,
                                style = Stroke(width = if (index % 4 == 0) 1.6f else 1.0f),
                            )
                        }
                    }
                }
                drawCircle(
                    color = animatedRimColor.copy(alpha = if (enableArtworkTint) 0.55f else 0.22f),
                    radius = radius - 2f,
                    style = Stroke(width = 3.5f),
                )
            }
            Box(
                modifier = Modifier
                    .size(vinylSize * normalizedInnerGlowDiameterFraction)
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to animatedInnerGlowColor.copy(alpha = 0.24f),
                                0.68f to animatedInnerGlowColor.copy(alpha = 0.08f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(vinylSize * normalizedArtworkDiameterFraction)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (resolvedArtworkBitmap != null && artworkLocator.isNullOrBlank()) {
                    Image(
                        bitmap = resolvedArtworkBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    LynArtworkImage(
                        artworkLocator = artworkLocator,
                        contentDescription = null,
                        artworkCacheKey = artworkCacheKey,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        maxDecodeSizePx = maxArtworkDecodeSizePx,
                        retainPreviousWhileLoading = retainPreviousArtworkWhileLoading,
                    )
                }
            }
        }
    }
}

private const val PLAYER_ARTWORK_DEFAULT_VINYL_ARTWORK_DIAMETER_FRACTION = 0.70f
private const val PLAYER_ARTWORK_DEFAULT_VINYL_INNER_GLOW_DIAMETER_FRACTION = 0.80f
private const val DEFAULT_VINYL_ARTWORK_DIAMETER_FRACTION = 0.70f
private const val DEFAULT_VINYL_INNER_GLOW_DIAMETER_FRACTION = 0.62f

data class VinylArtworkPalette(
    val rimColor: Color,
    val glowColor: Color,
    val innerGlowColor: Color,
)

data class PlaybackArtworkBackgroundColors(
    val baseColor: Color,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
)

fun VinylArtworkPalette.toArtworkTintTheme(): ArtworkTintTheme {
    return ArtworkTintTheme(
        rimColorArgb = rimColor.toArgbInt(),
        glowColorArgb = glowColor.toArgbInt(),
        innerGlowColorArgb = innerGlowColor.toArgbInt(),
    )
}

@Composable
fun rememberPlaybackArtworkBackgroundPalette(
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    enabled: Boolean,
): PlaybackArtworkBackgroundColors? {
    return remember(artworkBitmap, enabled) {
        if (!enabled || artworkBitmap == null) {
            null
        } else {
            derivePlaybackArtworkBackgroundColors(artworkBitmap)
        }
    }
}

private fun derivePlaybackArtworkBackgroundColors(
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap,
): PlaybackArtworkBackgroundColors? {
    val palette = derivePlaybackArtworkBackgroundPalette(sampleImageBitmapPixels(artworkBitmap)) ?: return null
    return PlaybackArtworkBackgroundColors(
        baseColor = composeColorFromArgb(palette.baseColorArgb),
        primaryColor = composeColorFromArgb(palette.primaryColorArgb),
        secondaryColor = composeColorFromArgb(palette.secondaryColorArgb),
        tertiaryColor = composeColorFromArgb(palette.tertiaryColorArgb),
    )
}

@Composable
fun rememberVinylArtworkPalette(
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    enabled: Boolean,
): VinylArtworkPalette? {
    return remember(artworkBitmap, enabled) {
        if (!enabled || artworkBitmap == null) {
            null
        } else {
            deriveVinylArtworkPalette(artworkBitmap)
        }
    }
}

private fun deriveVinylArtworkPalette(
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap,
): VinylArtworkPalette? {
    val theme = deriveArtworkTintTheme(sampleImageBitmapPixels(artworkBitmap)) ?: return null
    return VinylArtworkPalette(
        rimColor = composeColorFromArgb(theme.rimColorArgb),
        glowColor = composeColorFromArgb(theme.glowColorArgb),
        innerGlowColor = composeColorFromArgb(theme.innerGlowColorArgb),
    )
}

private fun sampleImageBitmapPixels(artworkBitmap: androidx.compose.ui.graphics.ImageBitmap): List<Int> {
    val pixelMap = artworkBitmap.toPixelMap()
    val stepX = max(1, pixelMap.width / 24)
    val stepY = max(1, pixelMap.height / 24)
    return buildList {
        for (y in 0 until pixelMap.height step stepY) {
            for (x in 0 until pixelMap.width step stepX) {
                add(pixelMap[x, y].toArgbInt())
            }
        }
    }
}

fun composeColorFromArgb(argb: Int): Color {
    return Color(
        red = ((argb ushr 16) and 0xFF) / 255f,
        green = ((argb ushr 8) and 0xFF) / 255f,
        blue = (argb and 0xFF) / 255f,
        alpha = ((argb ushr 24) and 0xFF) / 255f,
    )
}

private fun Color.toArgbInt(): Int {
    val alphaInt = (alpha * 255f).roundToInt().coerceIn(0, 255)
    val redInt = (red * 255f).roundToInt().coerceIn(0, 255)
    val greenInt = (green * 255f).roundToInt().coerceIn(0, 255)
    val blueInt = (blue * 255f).roundToInt().coerceIn(0, 255)
    return (alphaInt shl 24) or (redInt shl 16) or (greenInt shl 8) or blueInt
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Enum<T>> EnumSelector(
    label: String,
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    MainShellElevatedCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value ->
                    val active = value == selected
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) shellColors.selectedContainer else shellColors.navContainer,
                        modifier = Modifier.clickable { onSelected(value) },
                    ) {
                        Text(
                            value.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1_000).coerceAtLeast(0L)
    val minutesPart = seconds / 60
    val secondsPart = seconds % 60
    return minutesPart.toString().padStart(2, '0') + ":" + secondsPart.toString().padStart(2, '0')
}

fun formatLyricsCandidateDuration(durationSeconds: Int): String {
    val totalSeconds = durationSeconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }
}

@Composable
fun trackDisplayFormat(track: Track): String {
    return track.relativePath
        .substringAfterLast('.', "")
        .takeIf { it.isNotBlank() }
        ?.uppercase()
        ?: uiString(Res.string.common_unknown)
}

fun formatTrackAudioQuality(track: Track): String? {
    val bitDepth = track.bitDepth?.takeIf { it > 0 }?.let { "${it}bit" }
    val samplingRate = track.samplingRate?.takeIf { it > 0 }?.let(::formatSamplingRate)
    val bitDepthAndSamplingRate = listOfNotNull(bitDepth, samplingRate)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" / ")
    return listOfNotNull(
        bitDepthAndSamplingRate,
        track.bitRate?.takeIf { it > 0 }?.let { "${it}kbps" },
        track.channelCount?.takeIf { it > 0 }?.let { "${it}ch" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

fun formatTrackTechnicalSummaryText(track: Track): UiText = UiText.Joined(listOfNotNull(
    track.relativePath.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase()?.let(UiText::Raw) ?: uiText(Res.string.common_unknown),
    formatTrackAudioQuality(track)?.let(UiText::Raw),
    formatTrackSizeText(track.sizeBytes),
))
@Composable
fun formatTrackTechnicalSummary(track: Track): String = formatTrackTechnicalSummaryText(track).displayText()

private fun formatSamplingRate(samplingRateHz: Int): String {
    val decimals = if (samplingRateHz % 1_000 == 0) 0 else 1
    return "${roundTo(samplingRateHz / 1_000.0, decimals)}kHz"
}

fun formatTrackSizeText(sizeBytes: Long): UiText {
    if (sizeBytes <= 0L) return uiText(Res.string.common_unknown)
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val value = when {
        sizeBytes >= gb -> "${roundTo((sizeBytes / gb), 2)} GB"
        sizeBytes >= mb -> "${roundTo((sizeBytes / mb), 1)} MB"
        sizeBytes >= kb -> "${roundTo((sizeBytes / kb), 0)} KB"
        else -> "$sizeBytes B"
    }
    return UiText.Raw(value)
}

@Composable
fun formatTrackSize(sizeBytes: Long): String = formatTrackSizeText(sizeBytes).displayText()

private fun roundTo(value: Double, decimals: Int): String {
    if (decimals <= 0) return value.roundToInt().toString()
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToInt() / factor
    return rounded.toString()
}

@Composable
fun BannerCard(message: top.iwesley.lyn.music.core.model.UiText, onDismiss: () -> Unit) =
    BannerCard(message.displayText(), onDismiss)

@Composable
fun ToastCard(message: top.iwesley.lyn.music.core.model.UiText, modifier: Modifier = Modifier) =
    ToastCard(message.displayText(), modifier)
