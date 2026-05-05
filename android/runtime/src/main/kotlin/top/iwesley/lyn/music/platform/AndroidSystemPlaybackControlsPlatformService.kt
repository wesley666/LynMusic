package top.iwesley.lyn.music.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SystemAudioFocusChange
import top.iwesley.lyn.music.core.model.SystemAudioFocusCommand
import top.iwesley.lyn.music.core.model.SystemAudioFocusState
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.resolveSystemAudioFocusChange
import top.iwesley.lyn.music.core.model.shouldKeepAudioFocusWhilePausedForResume
import top.iwesley.lyn.music.core.model.shouldKeepPlaybackNotificationForeground

fun createAndroidSystemPlaybackControlsPlatformService(
    context: Context,
    artworkCacheStore: ArtworkCacheStore,
): SystemPlaybackControlsPlatformService {
    return AndroidSystemPlaybackControlsPlatformService(
        context = context.applicationContext,
        artworkCacheStore = artworkCacheStore,
    )
}

private class AndroidSystemPlaybackControlsPlatformService(
    private val context: Context,
    private val artworkCacheStore: ArtworkCacheStore,
) : SystemPlaybackControlsPlatformService {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSession = MediaSession(context, MEDIA_SESSION_TAG)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var callbacks = SystemPlaybackControlCallbacks()
    private var latestSnapshot = PlaybackSnapshot()
    private var lastNotificationKey: AndroidNotificationKey? = null
    private var lastArtworkKey: String? = null
    private var lastArtworkBitmap: Bitmap? = null
    private var observedArtworkCacheKey: String? = null
    private var artworkVersionJob: Job? = null
    private var isNoisyReceiverRegistered = false
    private var hasAudioFocus = false
    private var audioFocusState = SystemAudioFocusState()
    private var audioFocusRequest: AudioFocusRequest? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && latestSnapshot.isPlaying) {
                serviceScope.launch { callbacks.pause() }
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val change = focusChange.toSystemAudioFocusChange() ?: return@OnAudioFocusChangeListener
        val result = resolveSystemAudioFocusChange(
            state = audioFocusState,
            change = change,
            isPlaying = latestSnapshot.isPlaying,
            hasCurrentTrack = latestSnapshot.currentTrack != null,
        )
        audioFocusState = result.state
        if (change == SystemAudioFocusChange.Gain) {
            hasAudioFocus = true
        }
        when (result.command) {
            SystemAudioFocusCommand.Play -> serviceScope.launch { callbacks.play() }
            SystemAudioFocusCommand.Pause -> serviceScope.launch { callbacks.pause() }
            SystemAudioFocusCommand.None -> Unit
        }
    }

    init {
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() {
                    serviceScope.launch { callbacks.play() }
                }

                override fun onPause() {
                    serviceScope.launch { callbacks.pause() }
                }

                override fun onSkipToNext() {
                    serviceScope.launch { callbacks.skipNext() }
                }

                override fun onSkipToPrevious() {
                    serviceScope.launch { callbacks.skipPrevious() }
                }

                override fun onSeekTo(pos: Long) {
                    if (!latestSnapshot.canSeek) return
                    serviceScope.launch { callbacks.seekTo(pos) }
                }
            },
        )
        AndroidPlaybackServiceRegistry.controller = this
    }

    override fun bind(callbacks: SystemPlaybackControlCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun updateSnapshot(snapshot: PlaybackSnapshot) {
        val previous = latestSnapshot
        val previousArtworkKey = lastArtworkKey
        val previousArtworkBitmap = lastArtworkBitmap
        latestSnapshot = snapshot
        val artworkLookup = AndroidNotificationArtworkLookup.from(snapshot)
        lastArtworkBitmap = resolveArtworkBitmap(artworkLookup)
        observeArtworkVersion(artworkLookup?.cacheKey)
        val artworkStateChanged = previousArtworkKey != lastArtworkKey || previousArtworkBitmap !== lastArtworkBitmap
        updateMediaSession(snapshot)
        updateAudioFocus(snapshot)
        updateNoisyReceiver(snapshot)
        if (snapshot.currentTrack == null) {
            observeArtworkVersion(null)
            lastNotificationKey = null
            AndroidPlaybackNotificationService.stop(context)
            return
        }
        if (shouldRefreshNotification(previous, snapshot) || artworkStateChanged) {
            requestNotificationSync(snapshot)
        }
    }

    override suspend fun close() {
        observeArtworkVersion(null)
        updateNoisyReceiver(PlaybackSnapshot())
        audioFocusState = SystemAudioFocusState()
        abandonAudioFocus()
        mediaSession.isActive = false
        mediaSession.release()
        AndroidPlaybackServiceRegistry.controller = null
        AndroidPlaybackNotificationService.stop(context)
    }

    fun handleServiceAction(action: String?) {
        when (action) {
            AndroidPlaybackNotificationService.ACTION_PLAY -> serviceScope.launch { callbacks.play() }
            AndroidPlaybackNotificationService.ACTION_PAUSE -> serviceScope.launch { callbacks.pause() }
            AndroidPlaybackNotificationService.ACTION_SKIP_NEXT -> serviceScope.launch { callbacks.skipNext() }
            AndroidPlaybackNotificationService.ACTION_SKIP_PREVIOUS -> serviceScope.launch { callbacks.skipPrevious() }
        }
    }

    fun buildNotificationState(): AndroidNotificationState {
        val track = latestSnapshot.currentTrack ?: return AndroidNotificationState(null, false)
        val keepForeground = shouldKeepPlaybackNotificationForeground(
            isPlaying = latestSnapshot.isPlaying,
            audioFocusState = audioFocusState,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, AndroidPlaybackNotificationService.CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        val notification = builder
            .setContentTitle(latestSnapshot.currentDisplayTitle.ifBlank { track.title })
            .setContentText(
                listOfNotNull(
                    latestSnapshot.currentDisplayArtistName,
                    latestSnapshot.currentDisplayAlbumTitle,
                ).joinToString(" · ").ifBlank { track.artistName.orEmpty() },
            )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(lastArtworkBitmap)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(keepForeground)
            .setContentIntent(buildContentIntent())
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "上一首",
                    buildServicePendingIntent(AndroidPlaybackNotificationService.ACTION_SKIP_PREVIOUS),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    if (latestSnapshot.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (latestSnapshot.isPlaying) "暂停" else "播放",
                    buildServicePendingIntent(
                        if (latestSnapshot.isPlaying) {
                            AndroidPlaybackNotificationService.ACTION_PAUSE
                        } else {
                            AndroidPlaybackNotificationService.ACTION_PLAY
                        },
                    ),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "下一首",
                    buildServicePendingIntent(AndroidPlaybackNotificationService.ACTION_SKIP_NEXT),
                ).build(),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
        return AndroidNotificationState(notification, keepForeground)
    }

    private suspend fun resolveArtworkBitmap(artworkLookup: AndroidNotificationArtworkLookup?): Bitmap? {
        if (artworkLookup == null) {
            lastArtworkKey = null
            lastArtworkBitmap = null
            return null
        }
        val cacheVersion = artworkCacheStore.observeVersion(artworkLookup.cacheKey).first()
        val artworkKey = artworkLookup.bitmapKey(cacheVersion)
        if (artworkKey == lastArtworkKey && lastArtworkBitmap != null) return lastArtworkBitmap
        val resolvedBitmap = resolveAndroidNotificationArtworkBitmap(
            locator = artworkLookup.locator,
            artworkCacheKey = artworkLookup.cacheKey,
            artworkCacheStore = artworkCacheStore,
        )
        if (resolvedBitmap == null) {
            lastArtworkKey = null
            lastArtworkBitmap = null
            return null
        }
        lastArtworkKey = artworkKey
        lastArtworkBitmap = resolvedBitmap
        return lastArtworkBitmap
    }

    private fun observeArtworkVersion(cacheKey: String?) {
        if (cacheKey == observedArtworkCacheKey) return
        artworkVersionJob?.cancel()
        artworkVersionJob = null
        observedArtworkCacheKey = cacheKey
        if (cacheKey == null) return
        artworkVersionJob = serviceScope.launch {
            artworkCacheStore.observeVersion(cacheKey)
                .drop(1)
                .collect {
                    refreshArtworkForLatestSnapshot(cacheKey)
                }
        }
    }

    private suspend fun refreshArtworkForLatestSnapshot(observedCacheKey: String) {
        val snapshot = latestSnapshot
        val artworkLookup = AndroidNotificationArtworkLookup.from(snapshot) ?: return
        if (artworkLookup.cacheKey != observedCacheKey) return
        val previousArtworkKey = lastArtworkKey
        val previousArtworkBitmap = lastArtworkBitmap
        lastArtworkBitmap = resolveArtworkBitmap(artworkLookup)
        val artworkStateChanged = previousArtworkKey != lastArtworkKey || previousArtworkBitmap !== lastArtworkBitmap
        updateMediaSession(snapshot)
        if (artworkStateChanged) {
            requestNotificationSync(snapshot)
        }
    }

    private fun requestNotificationSync(snapshot: PlaybackSnapshot) {
        if (snapshot.currentTrack == null) return
        lastNotificationKey = AndroidNotificationKey.from(snapshot)
        val keepForeground = shouldKeepPlaybackNotificationForeground(
            isPlaying = snapshot.isPlaying,
            audioFocusState = audioFocusState,
        )
        val syncStarted = AndroidPlaybackNotificationService.requestSync(
            context = context,
            promoteToForeground = keepForeground,
        )
        if (!syncStarted && keepForeground && snapshot.isPlaying) {
            serviceScope.launch { callbacks.pause() }
        }
    }

    private fun updateMediaSession(snapshot: PlaybackSnapshot) {
        val track = snapshot.currentTrack
        mediaSession.isActive = track != null
        if (track == null) {
            mediaSession.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(0L)
                    .setState(PlaybackState.STATE_NONE, 0L, 0f)
                    .build(),
            )
            mediaSession.setMetadata(null)
            return
        }
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, snapshot.currentDisplayTitle.ifBlank { track.title })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, snapshot.currentDisplayArtistName ?: track.artistName.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, snapshot.currentDisplayAlbumTitle ?: track.albumTitle.orEmpty())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, snapshot.durationMs.coerceAtLeast(track.durationMs))
                .apply {
                    lastArtworkBitmap?.let { bitmap ->
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                        putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
                        putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, bitmap)
                    }
                }
                .build(),
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(playbackActions(snapshot))
                .setState(
                    if (snapshot.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    snapshot.positionMs.coerceAtLeast(0L),
                    if (snapshot.isPlaying) 1f else 0f,
                )
                .build(),
        )
    }

    private fun playbackActions(snapshot: PlaybackSnapshot): Long {
        var actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_PLAY_PAUSE
        if (snapshot.canSeek) {
            actions = actions or PlaybackState.ACTION_SEEK_TO
        }
        return actions
    }

    private fun updateAudioFocus(snapshot: PlaybackSnapshot) {
        if (snapshot.isPlaying) {
            if (!requestAudioFocus()) {
                serviceScope.launch { callbacks.pause() }
            }
        } else if (shouldKeepAudioFocusWhilePausedForResume(audioFocusState)) {
            return
        } else {
            audioFocusState = SystemAudioFocusState()
            abandonAudioFocus()
        }
    }

    private fun updateNoisyReceiver(snapshot: PlaybackSnapshot) {
        if (snapshot.isPlaying && !isNoisyReceiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            isNoisyReceiverRegistered = true
        } else if (!snapshot.isPlaying && isNoisyReceiverRegistered) {
            runCatching { context.unregisterReceiver(noisyReceiver) }
            isNoisyReceiverRegistered = false
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(false)
                .build()
                .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        hasAudioFocus = granted
        return granted
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    private fun shouldRefreshNotification(previous: PlaybackSnapshot, current: PlaybackSnapshot): Boolean {
        return AndroidNotificationKey.from(previous) != AndroidNotificationKey.from(current)
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = (
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
                ?: Intent().setPackage(context.packageName)
            ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(context, AndroidPlaybackNotificationService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Int.toSystemAudioFocusChange(): SystemAudioFocusChange? {
        return when (this) {
            AudioManager.AUDIOFOCUS_GAIN -> SystemAudioFocusChange.Gain
            AudioManager.AUDIOFOCUS_LOSS -> SystemAudioFocusChange.Loss
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> SystemAudioFocusChange.LossTransient
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> SystemAudioFocusChange.LossTransientCanDuck
            else -> null
        }
    }
}

internal class AndroidPlaybackNotificationService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val requiresForegroundStart = intent?.getBooleanExtra(EXTRA_REQUIRE_FOREGROUND, false) == true
        if (requiresForegroundStart) {
            // A service started via startForegroundService() must enter the foreground promptly,
            // even if playback is paused or cleared before we finish syncing the real notification.
            startForeground(NOTIFICATION_ID, buildBootstrapNotification())
        }
        val controller = AndroidPlaybackServiceRegistry.controller
        if (controller == null) {
            stopForegroundCompat(removeNotification = true)
            stopSelf()
            return START_NOT_STICKY
        }
        controller.handleServiceAction(intent?.action)
        val state = controller.buildNotificationState()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (state.notification == null) {
            stopForegroundCompat(removeNotification = true)
            notificationManager.cancel(NOTIFICATION_ID)
            stopSelf()
            return START_NOT_STICKY
        }
        if (state.promoteToForeground || requiresForegroundStart) {
            startForeground(NOTIFICATION_ID, state.notification)
            if (!state.promoteToForeground) {
                stopForegroundCompat(removeNotification = false)
                notificationManager.notify(NOTIFICATION_ID, state.notification)
            }
        } else {
            stopForegroundCompat(removeNotification = false)
            notificationManager.notify(NOTIFICATION_ID, state.notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "播放控制",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示当前播放歌曲和系统级播放控制。"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildBootstrapNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Lyn Music")
            .setContentText("正在同步播放状态")
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "lynmusic.playback"
        const val ACTION_SYNC = "top.iwesley.lyn.music.action.SYNC_PLAYBACK"
        const val ACTION_PLAY = "top.iwesley.lyn.music.action.PLAY"
        const val ACTION_PAUSE = "top.iwesley.lyn.music.action.PAUSE"
        const val ACTION_SKIP_NEXT = "top.iwesley.lyn.music.action.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "top.iwesley.lyn.music.action.SKIP_PREVIOUS"
        private const val EXTRA_REQUIRE_FOREGROUND = "top.iwesley.lyn.music.extra.REQUIRE_FOREGROUND"
        private const val NOTIFICATION_ID = 3107

        fun requestSync(context: Context, promoteToForeground: Boolean): Boolean {
            val intent = Intent(context, AndroidPlaybackNotificationService::class.java)
                .setAction(ACTION_SYNC)
                .putExtra(EXTRA_REQUIRE_FOREGROUND, promoteToForeground)
            return runCatching {
                if (promoteToForeground) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            runCatching {
                context.stopService(Intent(context, AndroidPlaybackNotificationService::class.java))
            }
        }
    }
}

private data class AndroidNotificationState(
    val notification: Notification?,
    val promoteToForeground: Boolean,
)

private data class AndroidNotificationKey(
    val trackId: String?,
    val isPlaying: Boolean,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkLocator: String?,
) {
    companion object {
        fun from(snapshot: PlaybackSnapshot): AndroidNotificationKey {
            return AndroidNotificationKey(
                trackId = snapshot.currentTrack?.id,
                isPlaying = snapshot.isPlaying,
                title = snapshot.currentDisplayTitle,
                artist = snapshot.currentDisplayArtistName,
                album = snapshot.currentDisplayAlbumTitle,
                artworkLocator = snapshot.currentDisplayArtworkLocator,
            )
        }
    }
}

private object AndroidPlaybackServiceRegistry {
    @Volatile
    var controller: AndroidSystemPlaybackControlsPlatformService? = null
}

private const val MEDIA_SESSION_TAG = "LynMusicPlaybackSession"
