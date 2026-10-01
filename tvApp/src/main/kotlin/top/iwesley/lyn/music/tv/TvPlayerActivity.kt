package top.iwesley.lyn.music.tv

import top.iwesley.lyn.music.core.model.ProvideUiLanguage

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.playbackErrorText
import top.iwesley.lyn.music.displayText
import top.iwesley.lyn.music.uiDisplayText
import top.iwesley.lyn.music.uiString

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.LynMusicAppComponent
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlaybackAudioFormat
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.tv.ui.TvMainTheme

class TvPlayerActivity : TvComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        var appComponentResult by mutableStateOf(tvAppComponentResult())

        setContent {
            ProvideUiLanguage {
                val component = appComponentResult.getOrNull()
                if (component == null) {
                    TvMainTheme {
                        TvPlayerUnavailableScreen(
                            message = uiString(Res.string.startup_component_initialization_failed),
                            onRetry = {
                                appComponentResult = tvAppComponentResult()
                            },
                            onBack = ::finish,
                        )
                    }
                    return@ProvideUiLanguage
                }

                val appDisplayScalePreset by component.appDisplayScalePreset.collectAsState()
                ProvideTvPlayerDensity(appDisplayScalePreset = appDisplayScalePreset) {
                    TvPlayerApp(
                        component = component,
                        onBack = ::finish,
                    )
                }


            }
}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        internal fun createIntent(context: Context): Intent {
            return Intent(context, TvPlayerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}

@Composable
private fun TvPlayerApp(
    component: LynMusicAppComponent,
    onBack: () -> Unit,
) {
    ConfigureTvImageLoader()

    val playerState by component.playerStore.state.collectAsState()
    val favoritesState by component.favoritesStore.state.collectAsState()
    val playbackToastRawMessage = playerState.message
    val playbackToastTrackId = playerState.snapshot.currentTrack?.id
    val isPlaybackErrorToast = playbackToastRawMessage != null && playbackToastRawMessage == playerState.snapshot.playbackErrorText()
    var playbackToastMessage by remember { mutableStateOf<top.iwesley.lyn.music.core.model.UiText?>(null) }

    LaunchedEffect(playbackToastTrackId, playbackToastRawMessage, isPlaybackErrorToast) {
        if (playbackToastRawMessage == null) {
            playbackToastMessage = null
            return@LaunchedEffect
        }
        playbackToastMessage = playbackToastRawMessage
        Log.w(
            TV_PLAYER_LOG_TAG,
            "tv-player-toast track=${playbackToastTrackId.orEmpty()} playbackError=$isPlaybackErrorToast " +
                "message=${playbackToastRawMessage.toString().take(TV_PLAYER_LOG_MESSAGE_LIMIT)}",
        )
        kotlinx.coroutines.delay(TV_PLAYER_TOAST_DURATION_MS)
        if (playbackToastMessage == playbackToastRawMessage) {
            playbackToastMessage = null
        }
        if (playerState.message == playbackToastRawMessage) {
            component.playerStore.dispatch(PlayerIntent.ClearMessage)
        }
    }

    LaunchedEffect(component) {
        component.playerStore.startHydration()
        component.favoritesStore.ensureContentStarted()
    }

    TvMainTheme {
        TvPlayerScreen(
            state = playerState,
            favoriteTrackIds = favoritesState.favoriteTrackIds,
            artworkCacheStore = component.artworkCacheStore,
            onPlayerIntent = component.playerStore::dispatch,
            onToggleFavorite = { track ->
                component.favoritesStore.dispatch(FavoritesIntent.ToggleFavorite(track))
            },
            toastMessage = playbackToastMessage?.displayText(),
            onBack = onBack,
        )
    }
}

@Composable
private fun TvPlayerScreen(
    state: PlayerState,
    favoriteTrackIds: Set<String>,
    artworkCacheStore: ArtworkCacheStore,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    toastMessage: String?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val snapshot = state.snapshot
    val track = snapshot.currentTrack
    if (track == null) {
        TvPlayerUnavailableScreen(
            message = uiString(Res.string.player_no_current_track),
            onBack = onBack,
        )
        return
    }

    val artwork = rememberTvPlayerArtworkModel(
        artworkLocator = snapshot.currentDisplayArtworkLocator,
        artworkCacheKey = trackArtworkCacheKey(track),
        artworkCacheStore = artworkCacheStore,
    )
    val backgroundColors = rememberTvPlaybackBackgroundColors(
        artworkModel = artwork.target,
        artworkCacheVersion = artwork.cacheVersion,
    )
    val lyricsTitle = run {
        val artistName = snapshot.currentDisplayArtistName
            ?.takeIf { it.isNotBlank() }
            ?: uiString(Res.string.common_unknown_artist)
        val title = snapshot.currentDisplayTitle.ifBlank { track.title }
        "$artistName $title"
    }
    val playPauseFocusRequester = remember { FocusRequester() }
    val lyricsSearchButtonFocusRequester = remember { FocusRequester() }
    val playbackModeFocusRequester = remember { FocusRequester() }
    val queueButtonFocusRequester = remember { FocusRequester() }
    var initialPlayPauseFocusRequested by remember { mutableStateOf(false) }
    var lyricsSearchWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isQueueVisible, state.isManualLyricsSearchVisible) {
        if (!initialPlayPauseFocusRequested && !state.isQueueVisible && !state.isManualLyricsSearchVisible) {
            withFrameNanos { }
            playPauseFocusRequester.requestFocus()
            initialPlayPauseFocusRequested = true
        }
    }
    LaunchedEffect(state.isManualLyricsSearchVisible) {
        if (state.isManualLyricsSearchVisible) {
            lyricsSearchWasVisible = true
        } else if (lyricsSearchWasVisible) {
            withFrameNanos { }
            lyricsSearchButtonFocusRequester.requestFocus()
            lyricsSearchWasVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TvPlaybackArtworkBackground(
            artworkModel = artwork.target,
            artworkMemoryCacheKey = artwork.memoryCacheKey,
            colors = backgroundColors,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, top = 46.dp, end = 56.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(38.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvPlayerInfoPane(
                    snapshot = snapshot,
                    track = track,
                    artworkModel = artwork.target,
                    artworkMemoryCacheKey = artwork.memoryCacheKey,
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight(),
                )
                TvPlayerLyricsPane(
                    title = lyricsTitle,
                    state = state,
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight(),
                )
            }
            TvPlayerBottomControls(
                snapshot = snapshot,
                isFavorite = track.id in favoriteTrackIds,
                playPauseFocusRequester = playPauseFocusRequester,
                lyricsSearchButtonFocusRequester = lyricsSearchButtonFocusRequester,
                playbackModeFocusRequester = playbackModeFocusRequester,
                queueButtonFocusRequester = queueButtonFocusRequester,
                onPlayerIntent = onPlayerIntent,
                onToggleFavorite = { onToggleFavorite(track) },
            )
        }
        TvPlayerQueueDrawer(
            state = state,
            queueButtonFocusRequester = queueButtonFocusRequester,
            onPlayerIntent = onPlayerIntent,
            modifier = Modifier.fillMaxSize(),
        )
        if (state.isManualLyricsSearchVisible) {
            TvLyricsSearchOverlay(
                state = state,
                artworkCacheStore = artworkCacheStore,
                onPlayerIntent = onPlayerIntent,
            )
        }
        TvPlayerToast(
            message = toastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.58f)
                .padding(bottom = 128.dp),
        )
    }
}

@Composable
private fun TvPlayerInfoPane(
    snapshot: PlaybackSnapshot,
    track: Track,
    artworkModel: String?,
    artworkMemoryCacheKey: String?,
    modifier: Modifier = Modifier,
) {
    val audioQuality = formatTvCurrentPlaybackAudioQuality(
        track = track,
        audioFormat = snapshot.currentPlaybackAudioFormat,
        navidromeQuality = snapshot.currentNavidromeAudioQuality,
    ) ?: formatTvTrackAudioQuality(track)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvPlayerArtwork(
            artworkModel = artworkModel,
            artworkMemoryCacheKey = artworkMemoryCacheKey,
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .aspectRatio(1f),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = snapshot.currentDisplayTitle.ifBlank { track.title },
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot.currentDisplayArtistName ?: uiString(Res.string.common_unknown_artist),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot.currentDisplayAlbumTitle ?: uiString(Res.string.common_unknown_album),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            audioQuality?.let { quality ->
                Text(
                    text = quality,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvPlayerArtwork(
    artworkModel: String?,
    artworkMemoryCacheKey: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(TvPlayerArtworkShape),
    ) {
        TvPlayerArtworkImage(
            model = artworkModel,
            memoryCacheKey = artworkMemoryCacheKey,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TvPlayerLyricsPane(
    title: String,
    state: PlayerState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.94f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TvLyricsContent(
            lyrics = state.lyrics,
            highlightedLineIndex = state.highlightedLineIndex,
            isLyricsLoading = state.isLyricsLoading,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun TvLyricsContent(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
    isLyricsLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    TvCenteredLyricsList(
        lyrics = lyrics,
        highlightedLineIndex = highlightedLineIndex,
        isLoading = isLyricsLoading,
        modifier = modifier,
        listModifier = Modifier.focusGroup(),
        messageContent = { message, contentModifier ->
            TvPlayerMessagePanel(message = message, modifier = contentModifier)
        },
    )
}

@Composable
private fun TvPlayerBottomControls(
    snapshot: PlaybackSnapshot,
    isFavorite: Boolean,
    playPauseFocusRequester: FocusRequester,
    lyricsSearchButtonFocusRequester: FocusRequester,
    playbackModeFocusRequester: FocusRequester,
    queueButtonFocusRequester: FocusRequester,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvPlayerProgressArea(
            snapshot = snapshot,
            onPlayerIntent = onPlayerIntent,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = { onPlayerIntent(PlayerIntent.OpenManualLyricsSearch) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .focusRequester(lyricsSearchButtonFocusRequester)
                    .focusProperties { right = playbackModeFocusRequester },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = uiString(Res.string.lyrics_search_action),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onPlayerIntent(PlayerIntent.CycleMode) },
                    modifier = Modifier
                        .focusRequester(playbackModeFocusRequester)
                        .focusProperties { left = lyricsSearchButtonFocusRequester },
                ) {
                    Icon(
                        imageVector = tvPlaybackModeIcon(snapshot.mode),
                        contentDescription = uiString(Res.string.player_playback_mode_label),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = uiString(Res.string.player_previous_track))
                }
                IconButton(
                    onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) },
                    modifier = Modifier.focusRequester(playPauseFocusRequester),
                ) {
                    Icon(
                        imageVector = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (snapshot.isPlaying) uiString(Res.string.player_pause) else uiString(Res.string.player_play),
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = uiString(Res.string.player_next_track))
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) uiString(Res.string.favorites_remove_track) else uiString(Res.string.favorites_likes_title),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(true)) },
                    modifier = Modifier.focusRequester(queueButtonFocusRequester),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = uiString(Res.string.player_playback_list_title),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvPlayerProgressArea(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var pendingSeekPositionMs by remember(snapshot.currentTrack?.id, snapshot.durationMs, snapshot.canSeek) {
        mutableStateOf<Long?>(null)
    }
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val displayPositionMs = (pendingSeekPositionMs ?: snapshot.positionMs).coerceIn(0L, duration)
    val progressFraction = tvPlaybackProgress(displayPositionMs, duration)
    val progressColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.94f),
        label = "tv-player-progress-color",
    )
    val trackColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.16f),
        label = "tv-player-progress-track-color",
    )
    val timeColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.68f),
        label = "tv-player-progress-time-color",
    )
    fun updatePendingSeek(deltaMs: Long): Boolean {
        if (!snapshot.canSeek || snapshot.durationMs <= 0L) return false
        pendingSeekPositionMs = ((pendingSeekPositionMs ?: snapshot.positionMs) + deltaMs)
            .coerceIn(0L, snapshot.durationMs)
        return true
    }
    fun commitPendingSeek(): Boolean {
        val target = pendingSeekPositionMs ?: return false
        pendingSeekPositionMs = null
        onPlayerIntent(PlayerIntent.SeekTo(target.coerceIn(0L, snapshot.durationMs)))
        return true
    }

    Column(
        modifier = modifier
            .onFocusChanged { isFocused = it.hasFocus || it.isFocused }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft -> when (event.type) {
                        KeyEventType.KeyDown -> updatePendingSeek(-TV_PLAYER_SEEK_STEP_MS)
                        KeyEventType.KeyUp -> commitPendingSeek()
                        else -> false
                    }
                    Key.DirectionRight -> when (event.type) {
                        KeyEventType.KeyDown -> updatePendingSeek(TV_PLAYER_SEEK_STEP_MS)
                        KeyEventType.KeyUp -> commitPendingSeek()
                        else -> false
                    }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTvPlayerDuration(displayPositionMs),
                color = timeColor,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatTvPlayerDuration(snapshot.durationMs),
                color = timeColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            val trackHeightPx = with(LocalDensity.current) { 5.dp.toPx() }
            TvRoundedSliderTrack(
                progressFraction = progressFraction,
                progressColor = progressColor,
                trackColor = trackColor,
                trackHeightPx = trackHeightPx,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
            Slider(
                value = displayPositionMs.toFloat(),
                onValueChange = { value ->
                    if (snapshot.canSeek && snapshot.durationMs > 0L) {
                        pendingSeekPositionMs = value.toLong().coerceIn(0L, snapshot.durationMs)
                    }
                },
                onValueChangeFinished = { commitPendingSeek() },
                valueRange = 0f..duration.toFloat(),
                enabled = snapshot.canSeek && snapshot.durationMs > 0L,
                colors = tvPlayerTransparentSliderColors(progressColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .graphicsLayer(scaleY = 0.44f),
            )
        }
    }
}

@Composable
private fun TvRoundedSliderTrack(
    progressFraction: Float,
    progressColor: Color,
    trackColor: Color,
    trackHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val trackWidth = size.width.coerceAtLeast(0f)
        if (trackWidth <= 0f || trackHeightPx <= 0f) return@Canvas
        val top = (size.height - trackHeightPx) / 2f
        val radius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(trackWidth, trackHeightPx),
            cornerRadius = radius,
        )
        val activeWidth = (trackWidth * progressFraction.coerceIn(0f, 1f)).coerceIn(0f, trackWidth)
        if (activeWidth > 0f) {
            drawRoundRect(
                color = progressColor,
                topLeft = Offset(0f, top),
                size = Size(activeWidth, trackHeightPx),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun tvPlayerTransparentSliderColors(thumbColor: Color) = SliderDefaults.colors(
    thumbColor = thumbColor,
    activeTrackColor = Color.Transparent,
    inactiveTrackColor = Color.Transparent,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
    disabledThumbColor = Color.Transparent,
    disabledActiveTrackColor = Color.Transparent,
    disabledInactiveTrackColor = Color.Transparent,
)

@Composable
private fun TvPlayerQueueDrawer(
    state: PlayerState,
    queueButtonFocusRequester: FocusRequester,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val listState = rememberLazyListState()
    val closeFocusRequester = remember { FocusRequester() }
    val queueItemFocusRequester = remember { FocusRequester() }
    var queueWasVisible by remember { mutableStateOf(false) }
    val focusTargetIndex = remember(state.isQueueVisible, snapshot.currentIndex, snapshot.queue.size) {
        when {
            !state.isQueueVisible || snapshot.queue.isEmpty() -> -1
            snapshot.currentIndex in snapshot.queue.indices -> snapshot.currentIndex
            else -> 0
        }
    }
    LaunchedEffect(state.isQueueVisible, focusTargetIndex, snapshot.queue.size) {
        if (!state.isQueueVisible) return@LaunchedEffect
        if (focusTargetIndex >= 0) {
            listState.scrollToItem((focusTargetIndex - 2).coerceAtLeast(0))
            withFrameNanos { }
            queueItemFocusRequester.requestFocus()
        } else {
            withFrameNanos { }
            closeFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(state.isQueueVisible) {
        if (state.isQueueVisible) {
            queueWasVisible = true
        } else if (queueWasVisible) {
            withFrameNanos { }
            queueButtonFocusRequester.requestFocus()
            queueWasVisible = false
        }
    }
    if (state.isQueueVisible) {
        BackHandler(onBack = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) })
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = state.isQueueVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.44f))
                    .clickable { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) },
            )
        }
        AnimatedVisibility(
            visible = state.isQueueVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(durationMillis = 160)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing),
            ) + fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            TvPlayerQueuePanel(
                snapshot = snapshot,
                listState = listState,
                focusTargetIndex = focusTargetIndex,
                queueItemFocusRequester = queueItemFocusRequester,
                closeFocusRequester = closeFocusRequester,
                onClose = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) },
                onPlayQueueIndex = { index -> onPlayerIntent(PlayerIntent.PlayQueueIndex(index)) },
            )
        }
    }
}

@Composable
private fun TvPlayerQueuePanel(
    snapshot: PlaybackSnapshot,
    listState: LazyListState,
    focusTargetIndex: Int,
    queueItemFocusRequester: FocusRequester,
    closeFocusRequester: FocusRequester,
    onClose: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(430.dp)
            .clip(TvPlayerQueuePanelShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = uiString(Res.string.player_playback_list_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = uiString(Res.plurals.player_queue_summary, (snapshot.queue.size).toInt(), snapshot.queue.size, tvPlaybackModeLabel(snapshot.mode)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.focusRequester(closeFocusRequester),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = uiString(Res.string.player_close_queue),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (snapshot.queue.isEmpty()) {
            TvPlayerMessagePanel(
                message = uiString(Res.string.player_queue_empty_title),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(snapshot.queue, key = { _, track -> track.id }) { index, track ->
                    TvPlayerQueueTrackRow(
                        track = track,
                        index = index,
                        isCurrent = index == snapshot.currentIndex,
                        isPlaying = index == snapshot.currentIndex && snapshot.isPlaying,
                        focusRequester = queueItemFocusRequester.takeIf { index == focusTargetIndex },
                        onClick = { onPlayQueueIndex(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvPlayerQueueTrackRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White.copy(alpha = 0.18f)
            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            else -> Color.White.copy(alpha = 0.07f)
        },
        label = "tv-player-queue-row-container",
    )
    val contentColor = if (isFocused) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(TvPlayerQueueRowShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.60f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(30.dp),
        )
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.58f),
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = track.title,
                color = contentColor,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName?.takeIf { it.isNotBlank() } ?: uiString(Res.string.common_unknown_artist),
                color = Color.White.copy(alpha = if (isFocused) 0.82f else 0.62f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatTvPlayerDuration(track.durationMs),
            color = Color.White.copy(alpha = if (isFocused) 0.82f else 0.60f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun TvPlayerArtworkImage(
    model: String?,
    memoryCacheKey: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val imageRequest = remember(context, model, memoryCacheKey) {
        model?.let { target ->
            ImageRequest.Builder(context)
                .data(target)
                .memoryCacheKey(memoryCacheKey)
                .size(TV_PLAYER_ARTWORK_MAX_DECODE_SIZE_PX)
                .build()
        }
    }
    val painter = rememberAsyncImagePainter(model = imageRequest)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(82.dp),
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

private data class TvPlayerArtworkModel(
    val target: String?,
    val cacheVersion: Long,
    val memoryCacheKey: String?,
)

@Composable
private fun rememberTvPlayerArtworkModel(
    artworkLocator: String?,
    artworkCacheKey: String?,
    artworkCacheStore: ArtworkCacheStore,
): TvPlayerArtworkModel {
    val normalized = remember(artworkLocator) { normalizedArtworkCacheLocator(artworkLocator) }
    val requestCacheKey = remember(artworkCacheKey, normalized) {
        artworkCacheKey?.trim()?.takeIf { it.isNotBlank() } ?: normalized
    }
    val cacheVersion by remember(artworkCacheStore, requestCacheKey) {
        requestCacheKey?.let(artworkCacheStore::observeVersion) ?: flowOf(0L)
    }.collectAsState(initial = 0L)
    val requestToken = remember(requestCacheKey, normalized, cacheVersion) {
        TvPlayerArtworkRequestToken(
            cacheKey = requestCacheKey,
            artworkLocator = normalized,
            cacheVersion = cacheVersion,
        )
    }
    val resolution by produceState<TvPlayerArtworkResolution?>(
        initialValue = null,
        requestToken,
        artworkCacheStore,
    ) {
        val target = withContext(Dispatchers.IO) {
            when {
                normalized == null -> null
                requestCacheKey != null -> artworkCacheStore.cache(normalized, requestCacheKey)
                    ?: resolveArtworkCacheTarget(normalized)
                else -> resolveArtworkCacheTarget(normalized)
            }
        }
        value = TvPlayerArtworkResolution(
            requestToken = requestToken,
            target = target,
        )
    }
    val model = resolution.targetFor(requestToken)
    return TvPlayerArtworkModel(
        target = model,
        cacheVersion = cacheVersion,
        memoryCacheKey = tvPlayerArtworkMemoryCacheKey(
            artworkIdentity = requestToken.memoryCacheIdentity(),
            artworkCacheVersion = cacheVersion,
        ),
    )
}

@Composable
private fun TvPlayerMessagePanel(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun TvPlayerToast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 140)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)),
    ) {
        Box(
            modifier = Modifier
                .clip(TvPlayerToastShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message.orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvPlayerUnavailableScreen(
    message: String,
    onRetry: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .clip(TvPlayerPanelShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = uiString(Res.string.player_open_failed),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRetry != null) {
                    TvButton(onClick = onRetry) {
                        Text(uiString(Res.string.common_retry))
                    }
                }
                TvOutlinedButton(onClick = onBack) {
                    Text(uiString(Res.string.common_back))
                }
            }
        }
    }
}

@Composable
private fun formatTvPlaybackErrorToast(message: String): String {
    val normalized = message.trim()
    if (normalized.isBlank()) return uiString(Res.string.player_playback_failed)
    return normalized
//    if (normalized.startsWith("播放失败")) {
//        return normalized.take(TV_PLAYER_TOAST_DETAIL_LIMIT)
//    }
//    return when {
//        normalized.contains("FileNotFoundException", ignoreCase = true) ||
//            normalized.contains("ENOENT", ignoreCase = true) ->
//            "播放失败：文件不存在或无法访问。"
//        normalized.contains("Permission denied", ignoreCase = true) ||
//            normalized.contains("EACCES", ignoreCase = true) ->
//            "播放失败：没有读取该文件的权限。"
//        else -> "播放失败：${normalized.lineSequence().first().take(TV_PLAYER_TOAST_DETAIL_LIMIT)}"
//    }
}

@Composable
private fun ProvideTvPlayerDensity(
    appDisplayScalePreset: AppDisplayScalePreset,
    content: @Composable () -> Unit,
) {
    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.density, currentDensity.fontScale, appDisplayScalePreset) {
        Density(
            density = effectiveAppDisplayDensity(tvPlayerStableDensityScale(currentDensity.density), appDisplayScalePreset),
            fontScale = currentDensity.fontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        content()
    }
}

private fun tvPlaybackModeIcon(mode: PlaybackMode): ImageVector {
    return when (mode) {
        PlaybackMode.ORDER -> Icons.Rounded.Repeat
        PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle
        PlaybackMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
    }
}

@Composable
private fun tvPlaybackModeLabel(mode: PlaybackMode): String {
    return when (mode) {
        PlaybackMode.ORDER -> uiString(Res.string.player_sequential_mode)
        PlaybackMode.SHUFFLE -> uiString(Res.string.player_shuffle_mode)
        PlaybackMode.REPEAT_ONE -> uiString(Res.string.player_repeat_one_mode)
    }
}

private fun tvPlaybackProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun formatTvCurrentPlaybackAudioQuality(
    track: Track,
    audioFormat: PlaybackAudioFormat?,
    navidromeQuality: NavidromeAudioQuality?,
): String? {
    val navidromeFallbackBitRate = navidromeQuality
        ?.takeIf { parseSubsonicCompatibleSongLocator(track.mediaLocator) != null }
        ?.let { formatTvNavidromeBitRateFallback(it) }
    return listOfNotNull(
        audioFormat?.samplingRateHz?.takeIf { it > 0 }?.let(::formatTvSamplingRate),
        audioFormat?.bitRateBps?.takeIf { it > 0 }?.let(::formatTvPlaybackBitRate) ?: navidromeFallbackBitRate,
        audioFormat?.channelCount?.takeIf { it > 0 }?.let { "${it}ch" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatTvTrackAudioQuality(track: Track): String? {
    return listOfNotNull(
        track.bitDepth?.takeIf { it > 0 }?.let { "${it}bit" },
        track.samplingRate?.takeIf { it > 0 }?.let(::formatTvSamplingRate),
        track.bitRate?.takeIf { it > 0 }?.let { "${it}kbps" },
        track.channelCount?.takeIf { it > 0 }?.let { "${it}ch" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun formatTvNavidromeBitRateFallback(quality: NavidromeAudioQuality): String {
    return quality.maxBitRateKbps?.let { "${it}kbps" } ?: uiString(Res.string.common_original)
}

private fun formatTvSamplingRate(samplingRateHz: Int): String {
    if (samplingRateHz % 1_000 == 0) return "${samplingRateHz / 1_000}kHz"
    val rounded = (samplingRateHz / 100.0).roundToInt() / 10.0
    return "${rounded}kHz"
}

private fun formatTvPlaybackBitRate(bitRateBps: Int): String {
    return "${(bitRateBps / 1_000.0).roundToInt()}kbps"
}

private fun formatTvPlayerDuration(durationMs: Long): String {
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

private fun tvPlayerStableDensityScale(fallbackDensity: Float): Float {
    val fallbackDpi = (fallbackDensity.takeIf { it > 0f } ?: 1f) * DisplayMetrics.DENSITY_DEFAULT
    val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        DisplayMetrics.DENSITY_DEVICE_STABLE
    } else {
        fallbackDpi.roundToInt()
    }.takeIf { it > 0 } ?: fallbackDpi.roundToInt()
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}

private val TvPlayerPanelShape = RoundedCornerShape(22.dp)
private val TvPlayerArtworkShape = RoundedCornerShape(0.dp)
private val TvPlayerQueuePanelShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
private val TvPlayerQueueRowShape = RoundedCornerShape(16.dp)
private val TvPlayerToastShape = RoundedCornerShape(12.dp)
private const val TV_PLAYER_SEEK_STEP_MS = 10_000L
private const val TV_PLAYER_TOAST_DURATION_MS = 3_000L
private const val TV_PLAYER_TOAST_DETAIL_LIMIT = 96
private const val TV_PLAYER_LOG_MESSAGE_LIMIT = 240
private const val TV_PLAYER_LOG_TAG = "LynMusic"
