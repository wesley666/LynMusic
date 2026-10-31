package top.iwesley.lyn.music.platform

import android.media.MediaFormat
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.US_ASCII
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodec
import org.robolectric.shadows.ShadowMediaCodecList
import top.iwesley.lyn.music.domain.NavidromeTranscodeOffsetSupport
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Real HTTP/extraction/retry; only the Android codec is supplied by Robolectric. */
@UnstableApi
internal class NavidromeMp3RetryFixture(
    private val failRecoveryOpens: Boolean = false,
    private val pauseDuringRecovery: Boolean = false,
    private val nativeSeekableMp3: Boolean = false,
    private val acceptRanges: String? = "none",
    private val offsetAcceptRanges: String? = acceptRanges,
    private val failRangeProbe: Boolean = false,
    offsetSupport: NavidromeTranscodeOffsetSupport = NavidromeTranscodeOffsetSupport.Supported,
    private val backups: List<BackupResource> = emptyList(),
    deferCapability: Boolean = false,
) : Closeable {
    private companion object {
        const val SAMPLE_RATE = 44_100
        const val SAMPLES_PER_FRAME = 1_152
        const val FIRST_RESPONSE_FRAMES = 240
        const val FRAME_SIZE = 417
    }

    data class BackupResource(val cached: Boolean = false, val failOpens: Boolean = false)
    data class Request(val id: Int, val offsetSeconds: Long, val range: String?, val path: String)
    data class Jump(val beforeWholeMs: Long, val afterWholeMs: Long)

    val requests = CopyOnWriteArrayList<Request>()
    val loadErrors = mutableListOf<IOException>()
    val loadErrorEndTimesMs = mutableListOf<Long>()
    val internalDiscontinuities = mutableListOf<Jump>()
    val recoveryTargets = mutableListOf<Long>()
    val recoveryFailures = mutableListOf<Throwable>()
    val terminalFailures = mutableListOf<NavidromeRecoveryFailure>()
    private val events = CopyOnWriteArrayList<String>()
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val serverFailures = CopyOnWriteArrayList<Throwable>()
    private val stopServer = CountDownLatch(1)
    private val workers = Executors.newCachedThreadPool { task ->
        Thread(task, "navidrome-mp3-test-http").apply { isDaemon = true }
    }
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val nextRequestId = AtomicInteger()
    private val nativeMp3Body by lazy { mp3WithXing() }
    @Volatile private var currentSocket: Socket? = null
    @Volatile private var resetSocket: Socket? = null
    @Volatile private var closed = false
    private var backupIndex = -1
    private lateinit var recoveryFailureHandler: NavidromeRecoveryFailureHandler

    val player: ExoPlayer
    val session: AndroidNavidromePlaybackSession
    var completedStreams = 0
        private set
    var endedCount = 0
        private set
    var minimumWholePositionAfterReset: Long? = null
        private set
    var firstReadFailurePositionMs: Long? = null
        private set
    var minimumWholePositionAfterReadFailure: Long? = null
        private set

    init {
        installSilentMp3Codec()
        workers.execute {
            while (!closed) {
                try {
                    val socket = server.accept()
                    sockets += socket
                    workers.execute { serve(socket) }
                } catch (error: IOException) {
                    if (!closed) serverFailures += error
                }
            }
        }
        player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setUsePlatformDiagnostics(false).build()
        session = AndroidNavidromePlaybackSession(
            ExoNavidromePlaybackDriver(player),
            "http://127.0.0.1:${server.localPort}/rest/stream?id=silent&maxBitRate=128&format=mp3",
            durationMs = 120_000L,
            playWhenReady = false,
            startPositionMs = 0L,
            changed = { record("session-change") },
            recoveryFailed = { recoveryFailureHandler.handle(it) },
        )
        recoveryFailureHandler = NavidromeRecoveryFailureHandler(session::acceptsRecoveryFailure,
            tryNextAddress = { nextBackup() }, publishFailure = {
                terminalFailures += it
                recoveryFailures += it.error
            })
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val target = session.positionMs
                if (resetSocket != null && firstReadFailurePositionMs == null) firstReadFailurePositionMs = target
                val result = session.recoverReadFailure(error)
                if (pauseDuringRecovery) session.setPlaying(false)
                if (result == NavidromeReadRecoveryResult.Recovering) {
                    recoveryTargets += target
                } else {
                    session.onFailure()
                    recoveryFailureHandler.handle(session.recoveryFailure(error, result))
                }
                record("player-error recovery=$result target=$target")
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                session.onPlayWhenReadyChanged(playWhenReady, reason)
            }

            override fun onEvents(player: Player, playerEvents: Player.Events) {
                session.reconcile()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                session.reconcile()
                if (playbackState == Player.STATE_ENDED) endedCount++
                record("state=$playbackState")
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_INTERNAL) {
                    internalDiscontinuities += Jump(
                        session.originMs + oldPosition.positionMs,
                        session.originMs + newPosition.positionMs,
                    )
                }
                record("discontinuity reason=$reason oldSegment=${oldPosition.positionMs} newSegment=${newPosition.positionMs}")
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onLoadError(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
                error: IOException,
                wasCanceled: Boolean,
            ) {
                loadErrors += error
                loadErrorEndTimesMs += mediaLoadData.mediaEndTimeMs
                record("load-error bytePosition=${loadEventInfo.dataSpec.position} endTime=${mediaLoadData.mediaEndTimeMs} " +
                    "canceled=$wasCanceled ${error.javaClass.simpleName}: ${error.message}")
            }

            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
            ) {
                completedStreams++
                record("load-completed")
            }
        })
        if (!deferCapability) session.updateSupport(offsetSupport)
        session.start()
    }

    fun awaitReady(originMs: Long = 0L, requireCanSeek: Boolean = true) = await("READY with origin=$originMs") {
        player.playbackState == Player.STATE_READY && session.originMs == originMs && (!requireCanSeek || session.canSeek)
    }

    fun awaitPlayingPast(segmentPositionMs: Long) = await("playing past segment=$segmentPositionMs") {
        player.isPlaying && player.currentPosition >= segmentPositionMs
    }

    fun resetCurrentConnection(): Long {
        val socket = checkNotNull(currentSocket)
        val beforeReset = session.positionMs
        resetSocket = socket
        record("inject TCP reset")
        socket.setSoLinger(true, 0)
        socket.close()
        return beforeReset
    }

    fun awaitReadRetry(previousRequests: Int) = await("read error, HTTP retry and playback beyond the first response") {
        // READY can still describe the old buffered data while a retry is opening. The first
        // response contains only 240 MP3 frames, so this position requires retry data to be read.
        loadErrors.isNotEmpty() && requests.size > previousRequests &&
            player.playbackState == Player.STATE_READY && player.isPlaying &&
            player.currentPosition > FIRST_RESPONSE_FRAMES * SAMPLES_PER_FRAME * 1_000L / SAMPLE_RATE + 1_000L
    }

    fun awaitPausedReadRecovery(previousRequests: Int) = await("paused offset recovery READY") {
        loadErrors.isNotEmpty() && requests.size > previousRequests && player.playbackState == Player.STATE_READY &&
            !player.playWhenReady && !session.awaitingPosition
    }

    fun awaitTerminalReadFailure() = await("terminal read recovery failure", allowRecoveryFailure = true) {
        recoveryFailures.isNotEmpty()
    }

    fun awaitCapabilityWait() = await("read recovery waiting for capability") {
        loadErrors.isNotEmpty() && session.isRecoveringRead && !player.playWhenReady
    }

    fun awaitFallbackReady(index: Int = 0) = await("backup $index positioned and READY") {
        backupIndex == index && player.playbackState == Player.STATE_READY && !session.awaitingPosition &&
            (if (session.wantsPlayback) player.isPlaying else !player.playWhenReady)
    }

    fun awaitReadFailureOrRollback() = await("read error followed by a final failure or an internal rollback", allowRecoveryFailure = true) {
        loadErrors.isNotEmpty() && (recoveryFailures.isNotEmpty() ||
            internalDiscontinuities.any { it.afterWholeMs < it.beforeWholeMs - 999L })
    }

    fun assertUnknownLengthMp3() {
        assertEquals(C.TIME_UNSET, player.duration, diagnostics())
        assertTrue(player.currentTracks.groups.any { it.mediaTrackGroup.getFormat(0).sampleMimeType == "audio/mpeg" }, diagnostics())
        assertTrue(requests.all { it.range == null }, diagnostics())
        assertTrue(serverFailures.isEmpty(), diagnostics())
    }

    fun diagnostics(): String = buildString {
        appendLine("requests=$requests")
        appendLine("serverFailures=$serverFailures")
        appendLine("loadErrors=${loadErrors.map { "${it.javaClass.simpleName}: ${it.message}" }}")
        appendLine("loadErrorEndTimesMs=$loadErrorEndTimesMs")
        appendLine("recoveryTargets=$recoveryTargets recoveryFailures=$recoveryFailures")
        appendLine("internalDiscontinuities=$internalDiscontinuities completed=$completedStreams ended=$endedCount " +
            "minimumWholePositionAfterReset=$minimumWholePositionAfterReset")
        appendLine("firstReadFailurePositionMs=$firstReadFailurePositionMs minimumWholePositionAfterReadFailure=$minimumWholePositionAfterReadFailure")
        events.forEach { appendLine(it) }
        append("now origin=${session.originMs} segment=${player.currentPosition} whole=${session.positionMs} " +
            "nativeDuration=${player.duration} wholeDuration=${session.durationMs} state=${player.playbackState} error=${player.playerError}")
    }

    private fun await(description: String, allowRecoveryFailure: Boolean = false, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition()) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            if (!allowRecoveryFailure && (recoveryFailures.isNotEmpty() || (player.playerError != null && !session.isRecoveringRead))) {
                fail("Player failed while waiting for $description.\n${diagnostics()}")
            }
            if (serverFailures.isNotEmpty()) fail("HTTP fixture failed while waiting for $description.\n${diagnostics()}")
            if (System.nanoTime() >= deadline) fail("Timed out waiting for $description; this is not a reproduced rollback.\n${diagnostics()}")
            Thread.sleep(10)
        }
    }

    private fun record(event: String) {
        val wholePositionMs = session.positionMs
        if (resetSocket != null) {
            minimumWholePositionAfterReset = minimumWholePositionAfterReset?.coerceAtMost(wholePositionMs) ?: wholePositionMs
        }
        if (firstReadFailurePositionMs != null) {
            minimumWholePositionAfterReadFailure = minimumWholePositionAfterReadFailure?.coerceAtMost(wholePositionMs) ?: wholePositionMs
        }
        events += "$event origin=${session.originMs} segment=${player.currentPosition} whole=$wholePositionMs " +
            "mediaId=${player.currentMediaItem?.mediaId}"
    }

    private fun nextBackup(): Boolean {
        while (++backupIndex < backups.size) {
            val url = "http://127.0.0.1:${server.localPort}/backup/$backupIndex/rest/stream?id=silent&maxBitRate=128&format=mp3"
            if (session.canReload(url)) return session.reload(url)
        }
        return false
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 5_000
            val input = socket.getInputStream().bufferedReader(US_ASCII)
            val requestLine = input.readLine() ?: return
            val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }.toList()
            val target = URI(requestLine.split(' ')[1])
            val offset = Regex("(?:^|&)timeOffset=(\\d+)").find(target.rawQuery.orEmpty())?.groupValues?.get(1)?.toLong() ?: 0L
            val range = headers.firstOrNull { it.startsWith("Range:", ignoreCase = true) }?.substringAfter(':')?.trim()
            val request = Request(nextRequestId.incrementAndGet(), offset, range, target.path)
            requests += request
            val backup = Regex("/backup/(\\d+)/rest/stream").matchEntire(target.path)?.groupValues?.get(1)?.toInt()
                ?.let { backups[it] }
            val output = socket.getOutputStream()
            if (backup?.failOpens == true || (backup == null && failRecoveryOpens && resetSocket != null)) {
                events += "HTTP $request response=503"
                output.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(US_ASCII))
                output.flush()
                return
            }
            if ((backup == null && nativeSeekableMp3) || backup?.cached == true) {
                serveNativeMp3(socket, request, cached = backup?.cached == true)
                return
            }
            if (backup == null && failRangeProbe && range == "bytes=0-0") {
                events += "HTTP $request response=503 (failed range probe)"
                output.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(US_ASCII))
                output.flush()
                return
            }
            val responseRanges = if (backup != null) "none" else if (offset > 0L) offsetAcceptRanges else acceptRanges
            events += "HTTP $request response=200 chunked Accept-Ranges=${responseRanges ?: "absent"} Content-Length=absent"
            val rangeHeader = responseRanges?.let { "Accept-Ranges: $it\r\n" }.orEmpty()
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\n$rangeHeader" +
                "Transfer-Encoding: chunked\r\nConnection: close\r\nX-Content-Duration: 120\r\n\r\n").toByteArray(US_ASCII))
            val body = silentMp3Frames(if (resetSocket == null) FIRST_RESPONSE_FRAMES else FIRST_RESPONSE_FRAMES * 2)
            output.write("${body.size.toString(16)}\r\n".toByteArray(US_ASCII))
            output.write(body)
            output.write("\r\n".toByteArray(US_ASCII))
            output.flush()
            currentSocket = socket
            // Do not send the final HTTP chunk: reset must be an I/O error, never a clean EOF.
            stopServer.await()
        } catch (error: Exception) {
            if (!closed && socket !== resetSocket) serverFailures += error
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun serveNativeMp3(socket: Socket, request: Request, cached: Boolean) {
        val output = socket.getOutputStream()
        if (!cached && request.range == "bytes=0-0") {
            events += "HTTP $request response=503 (failed range probe)"
            output.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(US_ASCII))
            output.flush()
            return
        }
        val body = nativeMp3Body
        val bounds = request.range?.let { checkNotNull(Regex("bytes=(\\d+)-(\\d*)").matchEntire(it)) }
        val start = bounds?.groupValues?.get(1)?.toInt() ?: 0
        val end = bounds?.groupValues?.get(2)?.toIntOrNull() ?: body.lastIndex
        check(start in body.indices) { "Unexpected native range: ${request.range}" }
        val partial = request.range != null
        val contentRange = if (partial) "Content-Range: bytes $start-$end/${body.size}\r\n" else ""
        val status = if (partial) "206 Partial Content" else "200 OK"
        // The first response has a Xing frame and six seconds of audio. A byte-range retry
        // serves the rest of the same full file, so progress beyond that point needs retry data.
        val count = if (cached) end - start + 1 else if (resetSocket == null) (FIRST_RESPONSE_FRAMES + 1) * FRAME_SIZE else body.size - start
        val lengthHeaders = if (cached) "Content-Length: $count\r\nAccept-Ranges: bytes\r\n" else "Transfer-Encoding: chunked\r\n"
        events += "HTTP $request response=$status cached=$cached start=$start count=$count"
        output.write(("HTTP/1.1 $status\r\nContent-Type: audio/mpeg\r\n$contentRange$lengthHeaders" +
            "Connection: close\r\nX-Content-Duration: 120\r\n\r\n").toByteArray(US_ASCII))
        if (!cached) output.write("${count.toString(16)}\r\n".toByteArray(US_ASCII))
        output.write(body, start, count)
        if (!cached) output.write("\r\n".toByteArray(US_ASCII))
        output.flush()
        currentSocket = socket
        stopServer.await()
    }

    override fun close() {
        closed = true
        session.close()
        stopServer.countDown()
        server.close()
        sockets.forEach { runCatching { it.close() } }
        try {
            player.release()
        } finally {
            workers.shutdownNow()
            check(workers.awaitTermination(5, TimeUnit.SECONDS)) { "HTTP fixture workers did not stop" }
            MediaCodecUtil.clearDecoderInfoCache()
        }
    }

    private fun installSilentMp3Codec() {
        val format = MediaFormat.createAudioFormat("audio/mpeg", SAMPLE_RATE, 2)
        val codecName = "c2.android.mp3.decoder"
        ShadowMediaCodecList.addCodec(
            MediaCodecInfoBuilder.newBuilder().setName(codecName).setIsEncoder(false)
                .setCapabilities(MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder().setMediaFormat(format).build())
                .build(),
        )
        // Each fixture frame has 1,152 silent samples per channel. Robolectric supplies PCM
        // instead of merely echoing compressed bytes, preserving the real extractor timestamps.
        ShadowMediaCodec.addDecoder(codecName, ShadowMediaCodec.CodecConfig(16_384, 16_384) { input, output ->
            if (input.hasRemaining()) {
                input.position(input.limit())
                output.put(ByteArray(SAMPLES_PER_FRAME * 2 * 2))
            }
        })
        MediaCodecUtil.clearDecoderInfoCache()
    }

    private fun silentMp3Frames(count: Int): ByteArray {
        // MPEG-1 Layer III, 128 kbit/s, 44.1 kHz stereo, no CRC/padding/reservoir or metadata.
        // Zero side information encodes empty granules; the remaining main data is ancillary.
        val frame = ByteArray(FRAME_SIZE)
        frame[0] = 0xff.toByte()
        frame[1] = 0xfb.toByte()
        frame[2] = 0x90.toByte()
        return ByteArray(frame.size * count).also { bytes ->
            repeat(count) { frame.copyInto(bytes, it * frame.size) }
        }
    }

    private fun mp3WithXing(): ByteArray {
        val audioFrames = FIRST_RESPONSE_FRAMES * 2
        val body = silentMp3Frames(audioFrames + 1)
        // MPEG-1 stereo has 32 bytes of side information after the four-byte header.
        // Xing's frame count, file size and TOC provide duration and native seeking without
        // relying on HTTP length or the deliberately different X-Content-Duration value.
        ByteBuffer.wrap(body).apply {
            position(36)
            put("Xing".toByteArray(US_ASCII))
            putInt(7)
            putInt(audioFrames)
            putInt(body.size)
            repeat(100) { put((it * 256 / 100).toByte()) }
        }
        return body
    }
}
