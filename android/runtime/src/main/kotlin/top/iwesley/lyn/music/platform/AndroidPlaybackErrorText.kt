package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.diagnosticMessage
import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull
import top.iwesley.lyn.music.core.model.uiText

/** Keeps diagnostics separate from descriptions when Media3 wraps a data source failure. */
internal fun PlaybackGatewayState.withAndroidPlaybackFailure(error: Throwable): PlaybackGatewayState {
    val visited = mutableListOf<Throwable>()
    val details = mutableListOf<String>()
    var current: Throwable? = error
    while (current != null && visited.none { it === current }) {
        val name = current::class.simpleName ?: current::class.qualifiedName ?: "Throwable"
        val message = current.message?.takeIf { it.isNotBlank() }
        details += if (message == null) name else "$name: $message"
        visited += current
        current = current.cause
    }
    val diagnostic = details.distinct().joinToString(" -> ")
    val fallback = uiText(Res.string.playback_media_failed)
    return copy(
        canSeek = false,
        errorMessage = diagnostic.ifBlank { (fallback).diagnosticMessage() },
        errorText = error.uiFailureTextOrNull()
            ?: diagnostic.takeIf { it.isNotBlank() }?.let { uiText(Res.string.ui_error_with_details, UiText.Raw(it)) }
            ?: fallback,
        errorRevision = errorRevision + 1L,
    )
}
