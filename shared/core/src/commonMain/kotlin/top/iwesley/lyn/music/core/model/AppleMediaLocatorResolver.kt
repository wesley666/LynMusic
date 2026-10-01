package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*
import org.jetbrains.compose.resources.StringResource

sealed interface AppleResolvedMediaLocator {
    data class FileUrl(val url: String) : AppleResolvedMediaLocator
    data class RemoteUrl(val url: String) : AppleResolvedMediaLocator
    data class AbsolutePath(val path: String) : AppleResolvedMediaLocator
    data class Unsupported(val message: String, val messageText: UiText = UiText.Raw(message)) : AppleResolvedMediaLocator
}

object AppleMediaLocatorResolver {
    fun resolve(locator: String): AppleResolvedMediaLocator {
        val value = locator.trim()
        if (value.isBlank()) {
            return unsupported(Res.string.apple_media_empty)
        }
        return when {
            value.startsWith("file://", ignoreCase = true) ->
                AppleResolvedMediaLocator.FileUrl(value)

            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) ->
                AppleResolvedMediaLocator.RemoteUrl(value)

            value.startsWith("/") ->
                AppleResolvedMediaLocator.AbsolutePath(value)

            value.startsWith("content://", ignoreCase = true) ->
                unsupported(Res.string.apple_android_uri_unsupported)

            parseSambaLocator(value) != null ->
                unsupported(Res.string.apple_samba_unsupported)

            parseWebDavLocator(value) != null ->
                unsupported(Res.string.apple_webdav_unsupported)

            else ->
                unsupported(Res.string.apple_media_unsupported)
        }
    }

    private fun unsupported(resource: StringResource): AppleResolvedMediaLocator.Unsupported {
        val text = uiText(resource)
        return AppleResolvedMediaLocator.Unsupported((text).diagnosticMessage(), text)
    }
}
