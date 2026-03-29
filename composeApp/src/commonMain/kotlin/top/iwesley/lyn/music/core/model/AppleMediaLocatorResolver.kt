package top.iwesley.lyn.music.core.model

sealed interface AppleResolvedMediaLocator {
    data class FileUrl(val url: String) : AppleResolvedMediaLocator
    data class RemoteUrl(val url: String) : AppleResolvedMediaLocator
    data class AbsolutePath(val path: String) : AppleResolvedMediaLocator
    data class Unsupported(val message: String) : AppleResolvedMediaLocator
}

object AppleMediaLocatorResolver {
    fun resolve(locator: String): AppleResolvedMediaLocator {
        val value = locator.trim()
        if (value.isBlank()) {
            return AppleResolvedMediaLocator.Unsupported("Apple 平台无法播放空的媒体定位符。")
        }
        return when {
            value.startsWith("file://", ignoreCase = true) ->
                AppleResolvedMediaLocator.FileUrl(value)

            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) ->
                AppleResolvedMediaLocator.RemoteUrl(value)

            value.startsWith("/") ->
                AppleResolvedMediaLocator.AbsolutePath(value)

            value.startsWith("content://", ignoreCase = true) ->
                AppleResolvedMediaLocator.Unsupported("Apple 平台暂不支持 Android content URI。")

            parseSambaLocator(value) != null ->
                AppleResolvedMediaLocator.Unsupported("Apple 平台 v1 暂不支持 Samba locator。")

            parseWebDavLocator(value) != null ->
                AppleResolvedMediaLocator.Unsupported("Apple 平台 v1 暂不支持 WebDAV locator。")

            else ->
                AppleResolvedMediaLocator.Unsupported("Apple 平台暂不支持当前媒体定位符。")
        }
    }
}
