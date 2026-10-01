package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.uiString
import kotlin.io.encoding.Base64

private const val IOS_LOCAL_FOLDER_REFERENCE_PREFIX = "lynmusic-ios-folder://v1/"
private const val IOS_LOCAL_MEDIA_LOCATOR_PREFIX = "lynmusic-ios-local://"

data class IosLocalFolderReference(
    val identity: String,
    val bookmarkData: ByteArray,
) {
    override fun toString(): String =
        "IosLocalFolderReference(identity=<redacted>, bookmarkData=<redacted>)"
}

fun buildIosLocalFolderReference(identity: String, bookmarkData: ByteArray): String {
    require(identity.isNotBlank()) { "iOS 本地文件夹标识不能为空。" }
    require(bookmarkData.isNotEmpty()) { "iOS 本地文件夹授权数据不能为空。" }
    return buildString {
        append(IOS_LOCAL_FOLDER_REFERENCE_PREFIX)
        append(Base64.UrlSafe.encode(identity.encodeToByteArray()))
        append('/')
        append(Base64.UrlSafe.encode(bookmarkData))
    }
}

fun parseIosLocalFolderReference(reference: String): IosLocalFolderReference? {
    if (!reference.startsWith(IOS_LOCAL_FOLDER_REFERENCE_PREFIX)) return null
    val payload = reference.removePrefix(IOS_LOCAL_FOLDER_REFERENCE_PREFIX)
    val dividerIndex = payload.indexOf('/')
    if (dividerIndex <= 0 || dividerIndex == payload.lastIndex) return null
    return runCatching {
        IosLocalFolderReference(
            identity = Base64.UrlSafe.decode(payload.substring(0, dividerIndex)).decodeToString(),
            bookmarkData = Base64.UrlSafe.decode(payload.substring(dividerIndex + 1)),
        )
    }.getOrNull()?.takeIf { it.identity.isNotBlank() && it.bookmarkData.isNotEmpty() }
}

fun localFolderPersistentIdentity(reference: String): String {
    val iosIdentity = parseIosLocalFolderReference(reference)?.identity
    return iosIdentity?.let { "ios:$it" } ?: reference
}

fun displayLocalFolderReference(reference: String): UiText {
    return if (reference.startsWith(IOS_LOCAL_FOLDER_REFERENCE_PREFIX)) {
        uiText(Res.string.source_ios_files_index_label)
    } else {
        UiText.Raw(reference)
    }
}

fun buildIosLocalMediaLocator(sourceId: String, relativePath: String): String {
    require(sourceId.isNotBlank()) { "iOS 本地歌曲来源 ID 不能为空。" }
    require('/' !in sourceId) { "iOS 本地歌曲来源 ID 不能包含路径分隔符。" }
    val normalizedPath = relativePath.replace('\\', '/')
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/")
    require(normalizedPath.isNotBlank() && normalizedPath.split('/').none { it == "." || it == ".." }) {
        "iOS 本地歌曲相对路径无效。"
    }
    return IOS_LOCAL_MEDIA_LOCATOR_PREFIX + sourceId + "/" +
        Base64.UrlSafe.encode(normalizedPath.encodeToByteArray())
}

fun parseIosLocalMediaLocator(locator: String): Pair<String, String>? {
    if (!locator.startsWith(IOS_LOCAL_MEDIA_LOCATOR_PREFIX)) return null
    val payload = locator.removePrefix(IOS_LOCAL_MEDIA_LOCATOR_PREFIX)
    val dividerIndex = payload.indexOf('/')
    if (dividerIndex <= 0 || dividerIndex == payload.lastIndex) return null
    val sourceId = payload.substring(0, dividerIndex)
    val relativePath = runCatching {
        Base64.UrlSafe.decode(payload.substring(dividerIndex + 1)).decodeToString()
    }.getOrNull()
        ?.replace('\\', '/')
        ?.split('/')
        ?.filter { it.isNotBlank() }
        ?.joinToString("/")
    return if (
        sourceId.isNotBlank() &&
        '/' !in sourceId &&
        !relativePath.isNullOrBlank() &&
        relativePath.split('/').none { it == "." || it == ".." }
    ) {
        sourceId to relativePath
    } else {
        null
    }
}
