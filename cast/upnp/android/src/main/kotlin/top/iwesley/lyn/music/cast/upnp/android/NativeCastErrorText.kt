package top.iwesley.lyn.music.cast.upnp.android

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText

private const val NATIVE_CAST_ERROR_PREFIX = "lyn_ui:"
private val nativeCastErrorResources = mapOf(
    "cast_control_uninitialized" to Res.string.cast_control_uninitialized,
    "cast_discovery_failed" to Res.string.cast_discovery_failed,
    "cast_device_not_found" to Res.string.cast_device_not_found,
    "cast_send_uri_failed" to Res.string.cast_send_uri_failed,
    "cast_start_failed" to Res.string.cast_start_failed,
    "cast_resume_failed" to Res.string.cast_resume_failed,
    "cast_pause_failed" to Res.string.cast_pause_failed,
    "cast_seek_failed" to Res.string.cast_seek_failed,
    "cast_stop_failed" to Res.string.cast_stop_failed,
)

/** Native application errors carry stable IDs; unknown external details stay verbatim. */
internal fun nativeCastErrorText(message: String, fallback: UiText = uiText(Res.string.cast_failed)): UiText {
    if (message.startsWith(NATIVE_CAST_ERROR_PREFIX)) {
        val key = message.removePrefix(NATIVE_CAST_ERROR_PREFIX)
        nativeCastErrorResources[key]?.let { return uiText(it) }
    }
    return if (message.isBlank()) fallback else uiText(Res.string.ui_error_with_context, fallback, UiText.Raw(message))
}

internal fun connectingText(deviceName: String?): UiText =
    deviceName?.let { uiText(Res.string.cast_connecting_device, it) } ?: uiText(Res.string.cast_connecting)

internal fun castingText(deviceName: String?): UiText =
    deviceName?.let { uiText(Res.string.cast_connected_device, it) } ?: uiText(Res.string.cast_casting)

internal fun discoveryText(deviceCount: Int): UiText =
    if (deviceCount == 0) uiText(Res.string.cast_searching) else uiPlural(Res.plurals.cast_devices_found, (deviceCount).toInt(), deviceCount)
