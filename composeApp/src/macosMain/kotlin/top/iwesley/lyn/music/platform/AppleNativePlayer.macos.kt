package top.iwesley.lyn.music.platform

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

@OptIn(ExperimentalForeignApi::class)
internal actual class AppleNativePlayer actual constructor(
    private val platformLabel: String,
) {
    private val player = AVPlayer()
    private var periodicTimeObserver: Any? = null
    private var completionObserver: Any? = null
    private var failureObserver: Any? = null

    actual var onProgress: (() -> Unit)? = null
    actual var onCompleted: (() -> Unit)? = null
    actual var onFailed: ((String?) -> Unit)? = null

    init {
        installPeriodicTimeObserver()
    }

    actual fun load(locator: AppleResolvedMediaLocator) {
        val url = locator.toUrl()
        val item = AVPlayerItem.playerItemWithURL(url)
        clearCurrentItem()
        player.replaceCurrentItemWithPlayerItem(item)
        observeCurrentItem(item)
        onProgress?.invoke()
    }

    actual fun play() {
        player.play()
    }

    actual fun pause() {
        player.pause()
    }

    actual fun seekTo(positionMs: Long) {
        player.seekToTime(playbackTime(positionMs))
    }

    actual fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    actual fun isPlaying(): Boolean = player.timeControlStatus != AVPlayerTimeControlStatusPaused

    actual fun positionMs(): Long = player.currentTime().toMillis()

    actual fun durationMs(): Long? = player.currentItem?.duration?.toMillis()

    actual fun volume(): Float = player.volume

    actual fun errorMessage(): String? = player.currentItem?.error?.localizedDescription

    actual fun release() {
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
                onCompleted?.invoke()
            },
        )
        failureObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
            usingBlock = {
                onFailed?.invoke(item.error?.localizedDescription ?: "$platformLabel 播放失败。")
            },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AppleResolvedMediaLocator.toUrl(): NSURL {
    return when (this) {
        is AppleResolvedMediaLocator.FileUrl -> requireNotNull(NSURL.URLWithString(url))
        is AppleResolvedMediaLocator.RemoteUrl -> requireNotNull(NSURL.URLWithString(url))
        is AppleResolvedMediaLocator.AbsolutePath -> NSURL.fileURLWithPath(path)
        is AppleResolvedMediaLocator.Unsupported -> error(message)
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
