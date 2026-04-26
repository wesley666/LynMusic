package top.iwesley.lyn.music.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandEvent
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService

internal fun createIosSystemPlaybackControlsPlatformService(): SystemPlaybackControlsPlatformService {
    return IosSystemPlaybackControlsPlatformService()
}

@OptIn(ExperimentalForeignApi::class)
private class IosSystemPlaybackControlsPlatformService : SystemPlaybackControlsPlatformService {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val artworkCacheStore = createIosArtworkCacheStore()
    private val nowPlayingInfoCenter = MPNowPlayingInfoCenter.defaultCenter()
    private val remoteCommandCenter = MPRemoteCommandCenter.sharedCommandCenter()
    private var callbacks = SystemPlaybackControlCallbacks()
    private var latestSnapshot = PlaybackSnapshot()
    private var lastArtworkKey: String? = null
    private var lastArtwork: MPMediaItemArtwork? = null
    private var shouldResumeAfterInterruption = false
    private var interruptionObserver: Any? = null
    private var routeChangeObserver: Any? = null
    private val commandTargets = mutableListOf<Pair<MPRemoteCommand, Any>>()

    init {
        installRemoteCommands()
        installSessionObservers()
    }

    override fun bind(callbacks: SystemPlaybackControlCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun updateSnapshot(snapshot: PlaybackSnapshot) {
        latestSnapshot = snapshot
        if (snapshot.currentTrack != null) {
            AppleAudioSessionCoordinator.configureForPlayback()
        }
        updateCommandAvailability(snapshot)
        lastArtwork = resolveArtwork(snapshot.currentDisplayArtworkLocator)
        nowPlayingInfoCenter.nowPlayingInfo = buildNowPlayingInfo(snapshot)
    }

    override suspend fun close() {
        commandTargets.forEach { (command, target) -> command.removeTarget(target) }
        commandTargets.clear()
        interruptionObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        routeChangeObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        interruptionObserver = null
        routeChangeObserver = null
        nowPlayingInfoCenter.nowPlayingInfo = null
    }

    private fun installRemoteCommands() {
        register(remoteCommandCenter.playCommand) {
            serviceScope.launch { callbacks.play() }
            MPRemoteCommandHandlerStatusSuccess
        }
        register(remoteCommandCenter.pauseCommand) {
            serviceScope.launch { callbacks.pause() }
            MPRemoteCommandHandlerStatusSuccess
        }
        register(remoteCommandCenter.togglePlayPauseCommand) {
            serviceScope.launch { callbacks.togglePlayPause() }
            MPRemoteCommandHandlerStatusSuccess
        }
        register(remoteCommandCenter.nextTrackCommand) {
            serviceScope.launch { callbacks.skipNext() }
            MPRemoteCommandHandlerStatusSuccess
        }
        register(remoteCommandCenter.previousTrackCommand) {
            serviceScope.launch { callbacks.skipPrevious() }
            MPRemoteCommandHandlerStatusSuccess
        }
        register(remoteCommandCenter.changePlaybackPositionCommand) { event ->
            if (!latestSnapshot.canSeek) return@register MPRemoteCommandHandlerStatusCommandFailed
            val positionEvent = event as? MPChangePlaybackPositionCommandEvent
                ?: return@register MPRemoteCommandHandlerStatusCommandFailed
            serviceScope.launch {
                callbacks.seekTo((positionEvent.positionTime * 1_000.0).toLong())
            }
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun installSessionObservers() {
        interruptionObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = AVAudioSession.sharedInstance(),
            queue = NSOperationQueue.mainQueue,
        ) { notification ->
            val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)
                ?.unsignedIntegerValue
                ?.toULong()
            if (type == AVAudioSessionInterruptionTypeBegan) {
                shouldResumeAfterInterruption = latestSnapshot.isPlaying
                if (latestSnapshot.isPlaying) {
                    serviceScope.launch { callbacks.pause() }
                }
                return@addObserverForName
            }
            val options = (notification?.userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)
                ?.unsignedIntegerValue
                ?.toULong()
                ?: 0uL
            val shouldResume = (options and AVAudioSessionInterruptionOptionShouldResume) != 0uL
            if (shouldResumeAfterInterruption && shouldResume && latestSnapshot.currentTrack != null) {
                shouldResumeAfterInterruption = false
                AppleAudioSessionCoordinator.configureForPlayback()
                serviceScope.launch { callbacks.play() }
            } else {
                shouldResumeAfterInterruption = false
            }
        }
        routeChangeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = AVAudioSession.sharedInstance(),
            queue = NSOperationQueue.mainQueue,
        ) { notification ->
            val reason = (notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)
                ?.unsignedIntegerValue
                ?.toULong()
            if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable && latestSnapshot.isPlaying) {
                serviceScope.launch { callbacks.pause() }
            }
        }
    }

    private suspend fun resolveArtwork(locator: String?): MPMediaItemArtwork? {
        val normalized = locator?.trim().orEmpty().ifBlank { null }
        if (normalized == lastArtworkKey) return lastArtwork
        lastArtworkKey = normalized
        val image = withContext(Dispatchers.Default) {
            val path = normalized?.let { artworkCacheStore.cache(it, it) } ?: return@withContext null
            UIImage.imageWithContentsOfFile(path)
        }
        if (image == null) {
            lastArtwork = null
            return null
        }
        return MPMediaItemArtwork(boundsSize = image.size) { _ -> image }.also { artwork ->
            lastArtwork = artwork
        }
    }

    private fun buildNowPlayingInfo(snapshot: PlaybackSnapshot): Map<Any?, Any?>? {
        val track = snapshot.currentTrack ?: return null
        return buildMap {
            put(MPMediaItemPropertyTitle, snapshot.currentDisplayTitle.ifBlank { track.title })
            put(MPMediaItemPropertyArtist, snapshot.currentDisplayArtistName ?: track.artistName.orEmpty())
            put(MPMediaItemPropertyAlbumTitle, snapshot.currentDisplayAlbumTitle ?: track.albumTitle.orEmpty())
            put(MPMediaItemPropertyPlaybackDuration, (snapshot.durationMs.coerceAtLeast(track.durationMs)).toDouble() / 1_000.0)
            put(MPNowPlayingInfoPropertyElapsedPlaybackTime, snapshot.positionMs.coerceAtLeast(0L).toDouble() / 1_000.0)
            put(MPNowPlayingInfoPropertyPlaybackRate, if (snapshot.isPlaying) 1.0 else 0.0)
            lastArtwork?.let { artwork ->
                put(MPMediaItemPropertyArtwork, artwork)
            }
        }
    }

    private fun updateCommandAvailability(snapshot: PlaybackSnapshot) {
        val hasTrack = snapshot.currentTrack != null
        val hasQueue = snapshot.queue.size > 1
        remoteCommandCenter.playCommand.enabled = hasTrack
        remoteCommandCenter.pauseCommand.enabled = hasTrack
        remoteCommandCenter.togglePlayPauseCommand.enabled = hasTrack
        remoteCommandCenter.nextTrackCommand.enabled = hasQueue
        remoteCommandCenter.previousTrackCommand.enabled = hasQueue
        remoteCommandCenter.changePlaybackPositionCommand.enabled = hasTrack && snapshot.canSeek
    }

    private fun register(
        command: MPRemoteCommand,
        handler: (MPRemoteCommandEvent?) -> Long,
    ) {
        val target = command.addTargetWithHandler { event -> handler(event) }
        commandTargets += command to target
    }
}
