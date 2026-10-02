package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue
import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.uiText

@OptIn(ExperimentalForeignApi::class)
internal actual class AppleNativePlayer actual constructor(
    private val platformLabel: String,
) : ApplePlaybackEngine {
    private val player = AVPlayer()
    private var periodicTimeObserver: Any? = null
    private var completionObserver: Any? = null
    private var failureObserver: Any? = null

    actual override var onProgress: (() -> Unit)? = null
    actual override var onCompleted: (() -> Unit)? = null
    actual override var onFailed: ((String?) -> Unit)? = null

    init {
        installPeriodicTimeObserver()
    }

    actual override fun load(locator: AppleResolvedMediaLocator) {
        val url = locator.toUrl()
        val item = AVPlayerItem.playerItemWithURL(url)
        clearCurrentItem()
        player.replaceCurrentItemWithPlayerItem(item)
        observeCurrentItem(item)
        onProgress?.invoke()
    }

    actual override fun stopAndClear() {
        player.pause()
        clearCurrentItem()
    }

    actual override fun play() {
        player.play()
    }

    actual override fun pause() {
        player.pause()
    }

    actual override fun seekTo(positionMs: Long) {
        player.seekToTime(playbackTime(positionMs))
    }

    actual override fun canSeek(): Boolean {
        val item = player.currentItem ?: return false
        return item.duration.toMillis() > 0L || item.seekableTimeRanges.isNotEmpty()
    }

    actual override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    actual override fun isPlaying(): Boolean = player.timeControlStatus != AVPlayerTimeControlStatusPaused

    actual override fun positionMs(): Long = player.currentTime().toMillis()

    actual override fun durationMs(): Long? = player.currentItem?.duration?.toMillis()

    actual override fun volume(): Float = player.volume

    actual override fun errorMessage(): String? = player.currentItem?.error?.localizedDescription

    actual override fun release() {
        player.pause()
        clearCurrentItem()
        periodicTimeObserver?.let(player::removeTimeObserver)
        periodicTimeObserver = null
    }

    private fun installPeriodicTimeObserver() {
        if (periodicTimeObserver != null) return
        periodicTimeObserver = player.addPeriodicTimeObserverForInterval(
            interval = playbackTime(500L),
            queue = dispatch_get_main_queue(),
            usingBlock = { _: CValue<CMTime> ->
                onProgress?.invoke()
            },
        )
    }

    private fun clearCurrentItem() {
        completionObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        failureObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        completionObserver = null
        failureObserver = null
        player.replaceCurrentItemWithPlayerItem(null)
    }

    private fun observeCurrentItem(item: AVPlayerItem) {
        completionObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
            usingBlock = {
                // A notification queued before the item was replaced must not reach the new item's handlers.
                if (player.currentItem === item) onCompleted?.invoke()
            },
        )
        failureObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
            usingBlock = {
                if (player.currentItem === item) onFailed?.invoke(item.error?.localizedDescription)
            },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AppleResolvedMediaLocator.toUrl(): NSURL {
    return when (this) {
        is AppleResolvedMediaLocator.FileUrl -> NSURL.URLWithString(url) ?: throw UiTextException(uiText(Res.string.apple_invalid_media_url))
        is AppleResolvedMediaLocator.RemoteUrl -> NSURL.URLWithString(url) ?: throw UiTextException(uiText(Res.string.apple_invalid_media_url))
        is AppleResolvedMediaLocator.AbsolutePath -> NSURL.fileURLWithPath(path)
        is AppleResolvedMediaLocator.Unsupported -> throw UiTextException(messageText)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun playbackTime(positionMs: Long): CValue<CMTime> = CMTimeMakeWithSeconds(
    seconds = positionMs.coerceAtLeast(0L).toDouble() / 1_000.0,
    preferredTimescale = 600,
)

@OptIn(ExperimentalForeignApi::class)
private fun CValue<CMTime>.toMillis(): Long {
    val seconds = CMTimeGetSeconds(this)
    return if (seconds.isFinite() && seconds >= 0.0) {
        (seconds * 1_000.0).toLong()
    } else {
        0L
    }
}
