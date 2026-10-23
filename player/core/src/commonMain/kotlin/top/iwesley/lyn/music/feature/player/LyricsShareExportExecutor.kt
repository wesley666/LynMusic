package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.resources.*

/** Executes captured exports for the Store's lifetime, without reading or changing PlayerState. */
internal class LyricsShareExportExecutor(
    scope: CoroutineScope,
    private val platform: LyricsSharePlatformService,
    private val onCopyCompleted: (SubmittedLyricsCopy, UiText) -> Unit,
    private val onSaveCompleted: (SubmittedImageSave, UiText) -> Unit,
    private val onSaveFinished: (SubmittedImageSave) -> Unit,
) {
    private val copies = Channel<SubmittedLyricsCopy>(Channel.UNLIMITED)
    private val saves = Channel<SubmittedImageSave>(Channel.UNLIMITED)

    init {
        scope.launch {
            // Rendering is part of the clipboard queue, so text cannot overtake an image.
            for (request in copies) {
                currentCoroutineContext().ensureActive()
                onCopyCompleted(request, copy(request))
            }
        }.invokeOnCompletion { copies.cancel() }
        scope.launch {
            // A pending save dialog never blocks the clipboard worker.
            for (request in saves) {
                currentCoroutineContext().ensureActive()
                try {
                    onSaveCompleted(request, save(request))
                } finally {
                    onSaveFinished(request)
                }
            }
        }.invokeOnCompletion { saves.cancel() }
        // Completion hooks also discard buffered requests when the scope was cancelled before launch ran.
    }

    fun submitCopy(request: SubmittedLyricsCopy) {
        copies.trySend(request)
    }

    fun submitSave(request: SubmittedImageSave) {
        saves.trySend(request)
    }

    private suspend fun copy(request: SubmittedLyricsCopy): UiText {
        val image = request.content as? SubmittedLyricsCopyContent.Image
        val bytes = if (image != null) {
            try {
                image.bytes ?: platform.buildPreview(image.model).getOrThrow()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                return uiText(Res.string.lyrics_share_image_generation_failed, error.uiErrorDetail())
            }
        } else null
        // Even a platform renderer that ignores cancellation must not start a write after disposal.
        currentCoroutineContext().ensureActive()
        val message = try {
            when (val content = request.content) {
                is SubmittedLyricsCopyContent.Image -> platform.copyImage(requireNotNull(bytes))
                is SubmittedLyricsCopyContent.Text -> platform.copyText(content.value)
            }.getOrThrow()
            uiText(if (image != null) Res.string.lyrics_share_image_copied else Res.string.lyrics_share_text_copied)
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            uiText(if (image != null) Res.string.lyrics_share_copy_image_failed else Res.string.lyrics_share_copy_text_failed,
                error.uiErrorDetail())
        }
        currentCoroutineContext().ensureActive()
        return message
    }

    private suspend fun save(request: SubmittedImageSave): UiText {
        val bytes = try {
            request.bytes ?: platform.buildPreview(request.model).getOrThrow()
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            return uiText(Res.string.lyrics_share_image_generation_failed, error.uiErrorDetail())
        }
        currentCoroutineContext().ensureActive()
        val message = try {
            platform.saveImage(bytes, request.suggestedName).getOrThrow().message
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            uiText(Res.string.lyrics_share_save_image_failed, error.uiErrorDetail())
        }
        currentCoroutineContext().ensureActive()
        return message
    }
}

/** Content and identities are captured synchronously before enqueueing. */
internal data class SubmittedImageSave(
    val id: Long,
    val session: PlayerTaskSession,
    val track: Track,
    val model: LyricsShareCardModel,
    val bytes: ByteArray?,
    val suggestedName: String,
)

internal data class SubmittedLyricsCopy(
    val id: Long,
    val session: PlayerTaskSession,
    val track: Track,
    val content: SubmittedLyricsCopyContent,
)

internal sealed interface SubmittedLyricsCopyContent {
    data class Image(val model: LyricsShareCardModel, val bytes: ByteArray?) : SubmittedLyricsCopyContent
    data class Text(val value: String) : SubmittedLyricsCopyContent
}
