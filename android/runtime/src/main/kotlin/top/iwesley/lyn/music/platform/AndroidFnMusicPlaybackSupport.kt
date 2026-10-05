package top.iwesley.lyn.music.platform

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.IOException
import java.io.InputStream
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SignedHttpStream
import top.iwesley.lyn.music.core.model.RemoteStreamTruncatedException
import top.iwesley.lyn.music.core.model.SignedRemoteStream
import top.iwesley.lyn.music.core.model.SignedRemoteStreamInputStream
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.resolveFnMusicStreamSpec

private const val FN_MUSIC_LOG_TAG = "FnMusic"

/** ExoPlayer cannot sign requests itself, so FN Music plays through a data source backed by a [SignedRemoteStream]. */
internal class AndroidFnMusicPlaybackTarget(val stream: SignedRemoteStream) {
    /** A media source reading through [stream]; built again when the stream moves to another address. */
    @OptIn(UnstableApi::class)
    fun mediaSource(): MediaSource = ProgressiveMediaSource.Factory(FnMusicDataSourceFactory(stream))
        .createMediaSource(MediaItem.fromUri(Uri.parse(stream.currentCandidate.value)))
}

internal suspend fun resolveAndroidFnMusicPlaybackTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    httpClient: LyricsHttpClient,
    addressSelector: RemoteSourceAddressSelector,
    logger: DiagnosticLogger,
): AndroidFnMusicPlaybackTarget? {
    val stream = resolveFnMusicStreamSpec(database, secureCredentialStore, locator, httpClient, addressSelector)
        ?.let { SignedRemoteStream(it.candidates, it.hooks) }
        ?: return null
    // No probe request: ExoPlayer's first open already skips unreachable addresses, and if every address fails the
    // error reaches the gateway's recovery instead of failing the load here.
    logger.info(FN_MUSIC_LOG_TAG) { "stream-ready locator=$locator candidates=${stream.candidates.size}" }
    return AndroidFnMusicPlaybackTarget(stream)
}

@UnstableApi
private class FnMusicDataSourceFactory(private val stream: SignedRemoteStream) : DataSource.Factory {
    override fun createDataSource(): DataSource = FnMusicDataSource(stream)
}

@UnstableApi
private class FnMusicDataSource(private val stream: SignedRemoteStream) : BaseDataSource(true) {
    private var response: SignedHttpStream? = null
    private var currentUri: Uri? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var openedGeneration = stream.generation

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val endByte = dataSpec.length.takeIf { it != C.LENGTH_UNSET.toLong() }?.let { dataSpec.position + it - 1 }
        val result = stream.open(startByte = dataSpec.position, endByteInclusive = endByte)
        openedGeneration = stream.generation
        response = result
        currentUri = Uri.parse(result.finalUrl)
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            result.statusCode == 206 -> result.contentLength ?: C.LENGTH_UNSET.toLong()
            else -> result.contentLength?.minus(dataSpec.position)?.coerceAtLeast(0L) ?: C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), bytesRemaining).toInt()
        val read = try {
            response?.inputStream?.read(buffer, offset, toRead) ?: C.RESULT_END_OF_INPUT
        } catch (failure: IOException) {
            leaveFailedAddress(failure)
            throw failure
        }
        if (read < 0) {
            // A dropped connection reads as a normal end; with bytes still owed, make ExoPlayer retry instead of
            // finishing the song early.
            val current = response
            if (current != null && bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0L) {
                val end = current.endExclusive
                val failure = if (end != null) {
                    RemoteStreamTruncatedException(position = end - bytesRemaining, expectedEnd = end)
                } else {
                    RemoteStreamTruncatedException(position = -1L, expectedEnd = bytesRemaining)
                }
                leaveFailedAddress(failure)
                throw failure
            }
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read.toLong()
        bytesTransferred(read)
        return read
    }

    /**
     * ExoPlayer retries a failed load by opening this data source again; point the stream at the next address first
     * so the retry does not land on the same stalled one. The generation check keeps the playback gateway's own
     * recovery from moving a second time for the same failure.
     */
    private fun leaveFailedAddress(failure: IOException) {
        if (failure is RemoteStreamTruncatedException || stream.isAddressFallbackAllowed(failure)) {
            stream.moveToNextCandidate(expectedGeneration = openedGeneration)
        }
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        response?.close()
        response = null
        currentUri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}

/** Serves an FN Music track to a cast receiver, which cannot send FN Music's signed headers itself. */
internal class FnMusicCastProxyResource private constructor(
    private val stream: SignedRemoteStream,
    override val mimeType: String,
    override val length: Long?,
) : AndroidCastProxyResource {
    /**
     * Resumable from where it broke; the proxy's own copy limit caps the length. Bound to the proxy's response
     * coroutine, so when it is cancelled a read blocked on the NAS is aborted instead of waiting for its timeout.
     */
    override suspend fun open(start: Long, length: Long?): InputStream {
        val reader = SignedRemoteStreamInputStream(stream, startByte = start)
        try {
            reader.bindToCurrentCoroutine()
            reader.ensureOpenSuspending()
        } catch (throwable: Throwable) {
            reader.close()
            throw throwable
        }
        return reader
    }

    /** Deletes the temporary copy kept when the NAS ignored Range. */
    override fun close() {
        stream.close()
    }

    companion object {
        suspend fun create(
            database: LynMusicDatabase,
            secureCredentialStore: SecureCredentialStore,
            track: Track,
            mimeType: String,
            httpClient: LyricsHttpClient,
            addressSelector: RemoteSourceAddressSelector,
            logger: DiagnosticLogger,
        ): FnMusicCastProxyResource? {
            val stream = resolveFnMusicStreamSpec(database, secureCredentialStore, track.mediaLocator, httpClient, addressSelector)
                ?.let { SignedRemoteStream(it.candidates, it.hooks) }
                ?: return null
            // No Range: a NAS that ignores Range would otherwise be downloaded whole just to learn the length.
            val length = try {
                stream.openStream(startByte = 0L).use { it.totalLength }
            } catch (throwable: Throwable) {
                stream.close()
                throw throwable
            }
            logger.debug("CastProxy") {
                "fnmusic-resource source=${track.sourceId} address=${stream.currentCandidate.addressKind} length=${length ?: -1L}"
            }
            return FnMusicCastProxyResource(stream, mimeType, length)
        }
    }
}
