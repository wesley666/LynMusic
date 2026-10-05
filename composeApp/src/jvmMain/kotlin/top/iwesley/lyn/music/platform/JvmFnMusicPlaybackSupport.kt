package top.iwesley.lyn.music.platform

import java.io.IOException
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SignedRemoteStream
import top.iwesley.lyn.music.core.model.SignedRemoteStreamInputStream
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.resolveFnMusicStreamSpec
import uk.co.caprica.vlcj.media.callback.seekable.SeekableCallbackMedia

private const val FN_MUSIC_LOG_TAG = "FnMusic"

/**
 * FN Music signs every request with a timestamped header VLC cannot add, so playback goes through a callback
 * media whose every open and seek goes through a [SignedRemoteStream].
 */
internal suspend fun resolveJvmFnMusicPlaybackTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    httpClient: LyricsHttpClient,
    addressSelector: RemoteSourceAddressSelector,
    logger: DiagnosticLogger,
): JvmWebDavPlaybackTarget? {
    val stream = resolveFnMusicStreamSpec(database, secureCredentialStore, locator, httpClient, addressSelector)
        ?.let { SignedRemoteStream(it.candidates, it.hooks) }
        ?: return null
    // No Range on the size probe: a NAS that ignores Range would otherwise be downloaded whole into a temporary file
    // before the load even reaches VLC. Only the headers are read; closing drops the rest of the body.
    val size = try {
        stream.openStream(startByte = 0L).use { it.totalLength } ?: 0L
    } catch (throwable: Throwable) {
        stream.close()
        throw throwable
    }
    logger.info(FN_MUSIC_LOG_TAG) { "play-start locator=$locator address=${stream.currentCandidate.addressKind} size=$size" }
    return JvmWebDavPlaybackTarget(
        media = fnMusicCallbackMedia(stream, size, logger),
        requestUrl = stream.currentCandidate.value,
        release = stream::close,
    )
}

/** VLC callbacks over a resumable reader, so a read that breaks mid-song continues from the same byte on another address. */
private fun fnMusicCallbackMedia(
    stream: SignedRemoteStream,
    size: Long,
    logger: DiagnosticLogger,
): SeekableCallbackMedia = object : SeekableCallbackMedia() {
    private var reader: SignedRemoteStreamInputStream? = null

    override fun onGetSize(): Long = size.coerceAtLeast(0L)

    override fun onOpen(): Boolean {
        return runCatching {
            // An open without a close in between must not leave the previous connection behind.
            reader?.close()
            reader = SignedRemoteStreamInputStream(stream).also { it.ensureOpen() }
            true
        }.getOrElse { throwable ->
            logger.warn(FN_MUSIC_LOG_TAG) { "play-open-failed reason=${throwable.message.orEmpty()}" }
            false
        }
    }

    /** -1 means a real end of the song to vlcj; a failure must be thrown so it surfaces as a playback error. */
    override fun onRead(buffer: ByteArray, bufferSize: Int): Int {
        val current = reader ?: throw IOException("FN Music stream is not open.")
        return try {
            current.read(buffer, 0, bufferSize)
        } catch (failure: Throwable) {
            logger.warn(FN_MUSIC_LOG_TAG) {
                "play-read-failed position=${current.position} reason=${failure.message.orEmpty()}"
            }
            throw failure as? IOException ?: IOException(failure.message, failure)
        }
    }

    override fun onSeek(offset: Long): Boolean {
        if (offset < 0L) return false
        val current = reader ?: return false
        return runCatching {
            current.seekTo(offset)
            true
        }.getOrElse { throwable ->
            logger.warn(FN_MUSIC_LOG_TAG) { "play-seek-failed offset=$offset reason=${throwable.message.orEmpty()}" }
            false
        }
    }

    override fun onClose() {
        reader?.close()
        reader = null
        // Drops the temporary copy kept when the NAS ignored Range; a later open downloads it again.
        stream.close()
    }
}
