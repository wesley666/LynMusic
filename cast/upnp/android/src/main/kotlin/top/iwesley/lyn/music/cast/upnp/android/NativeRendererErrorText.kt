package top.iwesley.lyn.music.cast.upnp.android

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText

private const val NATIVE_RENDERER_ERROR_PREFIX = "lyn_ui:"
private val nativeRendererErrorResources = mapOf(
    "renderer_initialize_failed" to Res.string.renderer_initialize_failed,
    "renderer_register_failed" to Res.string.renderer_register_failed,
    "renderer_start_failed" to Res.string.renderer_start_failed,
    "renderer_uninitialized" to Res.string.renderer_uninitialized,
)

/** Only explicit native IDs identify application errors; external details remain verbatim. */
internal fun nativeRendererErrorText(message: String): UiText {
    if (message.startsWith(NATIVE_RENDERER_ERROR_PREFIX)) {
        val key = message.removePrefix(NATIVE_RENDERER_ERROR_PREFIX)
        nativeRendererErrorResources[key]?.let { return uiText(it) }
    }
    val fallback = uiText(Res.string.renderer_start_failed)
    return if (message.isBlank()) fallback else uiText(Res.string.ui_error_with_context, fallback, UiText.Raw(message))
}
