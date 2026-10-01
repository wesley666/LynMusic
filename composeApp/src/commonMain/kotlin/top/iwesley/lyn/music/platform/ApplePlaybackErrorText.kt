package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.uiText

internal fun applePlaybackFailureText(platformLabel: String, nativeDetails: String?): UiText {
    val fallback = uiText(Res.string.apple_playback_failed, platformLabel)
    return nativeDetails?.takeIf { it.isNotBlank() }
        ?.let { uiText(Res.string.ui_error_with_context, fallback, UiText.Raw(it)) } ?: fallback
}

internal fun applePlaybackLoadFailureText(platformLabel: String, throwable: Throwable): UiText =
    throwable.uiErrorText(uiText(Res.string.apple_playback_failed, platformLabel))

internal fun appleLocalAccessFailureText(platformLabel: String, throwable: Throwable): UiText =
    throwable.uiErrorText(uiText(Res.string.apple_local_access_failed, platformLabel))
