package top.iwesley.lyn.music.tv

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil3.compose.rememberAsyncImagePainter
import kotlin.math.roundToInt
import top.iwesley.lyn.music.LynMusicAppComponent
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId
import top.iwesley.lyn.music.tv.ui.TvMainTheme
import top.iwesley.lyn.music.tv.ui.TvMediaBrowserMode

internal enum class TvMediaDetailSource {
    Library,
    Favorites,
}

class TvMediaDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        val args = intent.parseTvMediaDetailArgs()

        setContent {
            val component = TvAppComponentHolder.current()
            if (component == null || args == null) {
                TvMainTheme {
                    TvMediaDetailErrorScreen(
                        message = "详情页不可用，请返回主界面重新打开。",
                        onBack = ::finish,
                    )
                }
                return@setContent
            }

            val appDisplayScalePreset by component.appDisplayScalePreset.collectAsState()
            ProvideTvMediaDetailDensity(appDisplayScalePreset = appDisplayScalePreset) {
                TvMediaDetailApp(
                    component = component,
                    args = args,
                    onBack = ::finish,
                    onOpenPlayer = {
                        startActivity(TvPlayerActivity.createIntent(this@TvMediaDetailActivity))
                    },
                )
            }
        }
    }

    companion object {
        internal fun createIntent(
            context: Context,
            source: TvMediaDetailSource,
            mode: TvMediaBrowserMode,
            id: String,
            title: String,
            subtitle: String?,
        ): Intent {
            return Intent(context, TvMediaDetailActivity::class.java).apply {
                putExtra(EXTRA_SOURCE, source.name)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
        }
    }
}

@Composable
private fun TvMediaDetailApp(
    component: LynMusicAppComponent,
    args: TvMediaDetailArgs,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val libraryState by component.libraryStore.state.collectAsState()
    val favoritesState by component.favoritesStore.state.collectAsState()

    LaunchedEffect(component, args.source) {
        component.playerStore.startHydration()
        component.favoritesStore.ensureContentStarted()
        when (args.source) {
            TvMediaDetailSource.Library -> component.libraryStore.ensureStarted()
            TvMediaDetailSource.Favorites -> Unit
        }
    }

    val baseTracks = when (args.source) {
        TvMediaDetailSource.Library -> libraryState.filteredTracks
        TvMediaDetailSource.Favorites -> favoritesState.filteredTracks
    }
    val isLoading = when (args.source) {
        TvMediaDetailSource.Library -> libraryState.isLoadingContent
        TvMediaDetailSource.Favorites -> favoritesState.isLoadingContent
    }
    val tracks = remember(args, baseTracks) {
        when (args.mode) {
            TvMediaBrowserMode.Albums -> baseTracks.filter { track -> matchesTvAlbumDetail(track, args.id) }
            TvMediaBrowserMode.Artists -> baseTracks.filter { track -> matchesTvArtistDetail(track, args.id) }
            TvMediaBrowserMode.Tracks -> emptyList()
        }
    }

    TvMainTheme {
        TvMediaDetailScreen(
            args = args,
            isLoading = isLoading,
            tracks = tracks,
            favoriteTrackIds = favoritesState.favoriteTrackIds,
            artworkCacheStore = component.artworkCacheStore,
            onPlayTracks = { playTracks, index ->
                component.playerStore.dispatch(PlayerIntent.PlayTracks(playTracks, index))
                onOpenPlayer()
            },
            onToggleFavorite = { track ->
                component.favoritesStore.dispatch(FavoritesIntent.ToggleFavorite(track))
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun TvMediaDetailScreen(
    args: TvMediaDetailArgs,
    isLoading: Boolean,
    tracks: List<Track>,
    favoriteTrackIds: Set<String>,
    artworkCacheStore: ArtworkCacheStore,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val firstTrackFocusRequester = remember(args) { FocusRequester() }
    val backFocusRequester = remember(args) { FocusRequester() }

    LaunchedEffect(isLoading, tracks) {
        if (!isLoading) {
            runCatching {
                if (tracks.isNotEmpty()) {
                    firstTrackFocusRequester.requestFocus()
                } else {
                    backFocusRequester.requestFocus()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 64.dp, top = 42.dp, end = 64.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.focusRequester(backFocusRequester),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = args.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                args.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = "${tracks.size} 首歌曲",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        when {
            isLoading -> TvMediaDetailLoadingPanel(modifier = Modifier.fillMaxSize())
            tracks.isEmpty() -> TvMediaDetailEmptyPanel(modifier = Modifier.fillMaxSize())
            else -> TvMediaDetailTrackList(
                tracks = tracks,
                favoriteTrackIds = favoriteTrackIds,
                artworkCacheStore = artworkCacheStore,
                firstTrackFocusRequester = firstTrackFocusRequester,
                firstTrackUpFocusRequester = backFocusRequester,
                onPlayTracks = onPlayTracks,
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TvMediaDetailTrackList(
    tracks: List<Track>,
    favoriteTrackIds: Set<String>,
    artworkCacheStore: ArtworkCacheStore,
    firstTrackFocusRequester: FocusRequester,
    firstTrackUpFocusRequester: FocusRequester,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.focusGroup(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TvMediaDetailTrackRow(
                track = track,
                isFavorite = track.id in favoriteTrackIds,
                artworkCacheStore = artworkCacheStore,
                focusRequester = firstTrackFocusRequester.takeIf { index == 0 },
                upFocusRequester = firstTrackUpFocusRequester.takeIf { index == 0 },
                onPlay = { onPlayTracks(tracks, index) },
                onToggleFavorite = { onToggleFavorite(track) },
            )
        }
    }
}

@Composable
private fun TvMediaDetailTrackRow(
    track: Track,
    isFavorite: Boolean,
    artworkCacheStore: ArtworkCacheStore,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        onClick = onPlay,
        onLongClick = onToggleFavorite,
        scale = CardDefaults.scale(focusedScale = 1.01f, pressedScale = 1.01f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .then(
                if (focusRequester != null && upFocusRequester != null) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusProperties { up = upFocusRequester }
                } else {
                    Modifier
                },
            ),
        shape = CardDefaults.shape(
            shape = TvDetailCardShape,
            focusedShape = TvDetailCardShape,
            pressedShape = TvDetailCardShape,
        ),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = TvDetailCardShape),
            pressedBorder = Border(BorderStroke(2.dp, Color.White), shape = TvDetailCardShape),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TvMediaDetailArtworkImage(
                track = track,
                artworkCacheStore = artworkCacheStore,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = track.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(track.artistName, track.albumTitle).joinToString(" / ").ifBlank { "未知艺人" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatTvMediaDetailDuration(track.durationMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TvMediaDetailArtworkImage(
    track: Track,
    artworkCacheStore: ArtworkCacheStore,
    modifier: Modifier = Modifier,
) {
    val model by produceState<String?>(initialValue = null, track.artworkLocator, track.id, artworkCacheStore) {
        val normalized = normalizedArtworkCacheLocator(track.artworkLocator)
        val cacheKey = trackArtworkCacheKey(track)
        value = when {
            normalized == null -> null
            cacheKey != null -> artworkCacheStore.cache(normalized, cacheKey)
                ?: resolveArtworkCacheTarget(normalized)
            else -> resolveArtworkCacheTarget(normalized)
        }
    }
    val painter = rememberAsyncImagePainter(model = model)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TvMediaDetailLoadingPanel(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TvMediaDetailEmptyPanel(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TvDetailPanelShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("这里没有歌曲", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("当前专辑或艺人没有可显示的歌曲。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TvMediaDetailErrorScreen(
    message: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TvDetailPanelShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("无法打开详情", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("返回")
            }
        }
    }
}

@Composable
private fun ProvideTvMediaDetailDensity(
    appDisplayScalePreset: AppDisplayScalePreset,
    content: @Composable () -> Unit,
) {
    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.density, currentDensity.fontScale, appDisplayScalePreset) {
        Density(
            density = effectiveAppDisplayDensity(
                tvMediaDetailStableDensityScale(currentDensity.density),
                appDisplayScalePreset,
            ),
            fontScale = currentDensity.fontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        content()
    }
}

private data class TvMediaDetailArgs(
    val source: TvMediaDetailSource,
    val mode: TvMediaBrowserMode,
    val id: String,
    val title: String,
    val subtitle: String?,
)

private fun Intent.parseTvMediaDetailArgs(): TvMediaDetailArgs? {
    val source = enumValueOrNull<TvMediaDetailSource>(getStringExtra(EXTRA_SOURCE)) ?: return null
    val mode = enumValueOrNull<TvMediaBrowserMode>(getStringExtra(EXTRA_MODE)) ?: return null
    if (mode == TvMediaBrowserMode.Tracks) return null
    val id = getStringExtra(EXTRA_ID)?.takeIf { it.isNotBlank() } ?: return null
    val title = getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return null
    return TvMediaDetailArgs(
        source = source,
        mode = mode,
        id = id,
        title = title,
        subtitle = getStringExtra(EXTRA_SUBTITLE),
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? {
    return enumValues<T>().firstOrNull { it.name == value }
}

private fun matchesTvAlbumDetail(track: Track, albumId: String): Boolean {
    val directId = track.albumId?.takeIf { it.isNotBlank() }?.let { "album:${track.sourceId}:$it" }
    val visibleId = track.albumTitle
        ?.takeIf { it.isNotBlank() }
        ?.let { libraryAlbumId(track.artistName, it) }
    return directId == albumId || visibleId == albumId
}

private fun matchesTvArtistDetail(track: Track, artistId: String): Boolean {
    val artistName = track.artistName?.takeIf { it.isNotBlank() } ?: return false
    return libraryArtistId(artistName) == artistId
}

private fun formatTvMediaDetailDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun tvMediaDetailStableDensityScale(fallbackDensity: Float): Float {
    val fallbackDpi = (fallbackDensity.takeIf { it > 0f } ?: 1f) * DisplayMetrics.DENSITY_DEFAULT
    val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        DisplayMetrics.DENSITY_DEVICE_STABLE
    } else {
        fallbackDpi.roundToInt()
    }.takeIf { it > 0 } ?: fallbackDpi.roundToInt()
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}

private val TvDetailPanelShape = RoundedCornerShape(18.dp)
private val TvDetailCardShape = RoundedCornerShape(14.dp)

private const val EXTRA_SOURCE = "top.iwesley.lyn.music.tv.extra.MEDIA_DETAIL_SOURCE"
private const val EXTRA_MODE = "top.iwesley.lyn.music.tv.extra.MEDIA_DETAIL_MODE"
private const val EXTRA_ID = "top.iwesley.lyn.music.tv.extra.MEDIA_DETAIL_ID"
private const val EXTRA_TITLE = "top.iwesley.lyn.music.tv.extra.MEDIA_DETAIL_TITLE"
private const val EXTRA_SUBTITLE = "top.iwesley.lyn.music.tv.extra.MEDIA_DETAIL_SUBTITLE"
