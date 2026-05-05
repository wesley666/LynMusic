package top.iwesley.lyn.music.cast.upnp.android

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.cast.directCastUriOrNull

internal fun parseUpnpRendererMedia(currentUri: String, metadata: String): UpnpRendererMedia? {
    val didl = parseDidlMetadata(metadata)
    val uri = directCastUriOrNull(currentUri).orFrom(didl?.resourceUri) ?: return null
    val didlMimeType = didl?.mimeType?.takeIf { it.isNotBlank() }
    val mediaType = supportedRendererMediaType(uri, didlMimeType) ?: return null
    val mimeType = didlMimeType ?: inferRendererMimeType(uri, mediaType)
    return UpnpRendererMedia(
        uri = uri,
        title = didl?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle(uri, mediaType),
        mediaType = mediaType,
        artistName = didl?.artist?.takeIf { it.isNotBlank() },
        albumTitle = didl?.album?.takeIf { it.isNotBlank() },
        artworkUri = directCastUriOrNull(didl?.artworkUri),
        mimeType = mimeType,
        durationMs = didl?.durationMs ?: 0L,
        metadata = metadata,
    )
}

internal fun isSupportedRendererAudioUri(uri: String, mimeType: String?): Boolean {
    return supportedRendererMediaType(uri, mimeType) == UpnpRendererMediaType.Audio
}

internal fun isSupportedRendererVideoUri(uri: String, mimeType: String?): Boolean {
    return supportedRendererMediaType(uri, mimeType) == UpnpRendererMediaType.Video
}

internal fun supportedRendererMediaType(uri: String, mimeType: String?): UpnpRendererMediaType? {
    val normalizedUri = directCastUriOrNull(uri) ?: return null
    val normalizedMime = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    if (normalizedMime.isNotBlank()) {
        return when {
            normalizedMime.startsWith("audio/") -> UpnpRendererMediaType.Audio
            normalizedMime.startsWith("video/") -> UpnpRendererMediaType.Video
            else -> null
        }
    }
    val extension = normalizedUri
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/', missingDelimiterValue = "")
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
    return when (extension) {
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus" -> UpnpRendererMediaType.Audio
        "mp4", "m4v", "mov", "mkv", "webm", "avi", "wmv", "mpg", "mpeg", "m2ts", "ts" -> UpnpRendererMediaType.Video
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> null
        else -> if (extension.isBlank()) UpnpRendererMediaType.Audio else null
    }
}

private fun inferRendererMimeType(uri: String, mediaType: UpnpRendererMediaType): String {
    val path = uri.substringBefore('?').substringBefore('#').lowercase()
    return when (mediaType) {
        UpnpRendererMediaType.Audio -> when {
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".ogg") || path.endsWith(".oga") -> "audio/ogg"
            path.endsWith(".opus") -> "audio/opus"
            else -> "audio/mpeg"
        }
        UpnpRendererMediaType.Video -> when {
            path.endsWith(".m4v") -> "video/x-m4v"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".avi") -> "video/x-msvideo"
            path.endsWith(".wmv") -> "video/x-ms-wmv"
            path.endsWith(".mpg") || path.endsWith(".mpeg") -> "video/mpeg"
            path.endsWith(".m2ts") || path.endsWith(".ts") -> "video/mp2t"
            else -> "video/mp4"
        }
    }
}

internal fun parseUpnpDurationToMs(value: String?): Long? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val parts = trimmed.split(':')
    if (parts.size != 3) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val secondsPart = parts[2]
    val seconds = secondsPart.substringBefore('.').toLongOrNull() ?: return null
    val millis = secondsPart.substringAfter('.', missingDelimiterValue = "")
        .take(3)
        .padEnd(3, '0')
        .takeIf { it.isNotBlank() }
        ?.toLongOrNull()
        ?: 0L
    return (((hours * 60L + minutes) * 60L + seconds) * 1_000L + millis).coerceAtLeast(0L)
}

private fun String?.orFrom(fallback: String?): String? {
    return this ?: directCastUriOrNull(fallback)
}

private fun fallbackTitle(uri: String, mediaType: UpnpRendererMediaType): String {
    return uri.substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .takeIf { it.isNotBlank() }
        ?: when (mediaType) {
            UpnpRendererMediaType.Audio -> "外部投屏音乐"
            UpnpRendererMediaType.Video -> "外部投屏视频"
        }
}

private data class ParsedDidlMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val resourceUri: String?,
    val mimeType: String?,
    val durationMs: Long?,
)

private fun parseDidlMetadata(metadata: String): ParsedDidlMetadata? {
    val trimmed = metadata.trim()
    if (trimmed.isBlank()) return null
    return parseDidlMetadataXml(trimmed)
        ?: escapeBareXmlAmpersands(trimmed)
            .takeIf { it != trimmed }
            ?.let(::parseDidlMetadataXml)
}

private fun parseDidlMetadataXml(xml: String): ParsedDidlMetadata? {
    return runCatching {
        val document = parseXmlDocument(xml)
        val resource = document.firstElement("res")
        val protocolInfo = resource?.getAttribute("protocolInfo").orEmpty()
        ParsedDidlMetadata(
            title = document.firstText("title"),
            artist = document.firstText("artist"),
            album = document.firstText("album"),
            artworkUri = document.firstText("albumArtURI"),
            resourceUri = resource?.textContent?.trim()?.takeIf { it.isNotBlank() },
            mimeType = protocolInfo.split(':').getOrNull(2)?.takeIf { it.isNotBlank() && it != "*" },
            durationMs = parseUpnpDurationToMs(resource?.getAttribute("duration")),
        )
    }.onFailure { throwable ->
        GlobalDiagnosticLogger.info(RENDERER_DIDL_LOG_TAG) {
            "upnp-renderer-didl-parse-failed type=${throwable::class.simpleName} " +
                "message=${throwable.message.orEmpty().take(160)}"
        }
    }.getOrNull()
}

private fun parseXmlDocument(xml: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }
    val builder = factory.newDocumentBuilder().apply {
        setErrorHandler(
            object : org.xml.sax.ErrorHandler {
                override fun warning(exception: SAXParseException) = Unit
                override fun error(exception: SAXParseException) {
                    throw exception
                }

                override fun fatalError(exception: SAXParseException) {
                    throw exception
                }
            },
        )
    }
    return builder.parse(InputSource(StringReader(xml)))
}

private fun escapeBareXmlAmpersands(value: String): String {
    return value.replace(BARE_XML_AMPERSAND_PATTERN, "&amp;")
}

private fun Document.firstText(localName: String): String? {
    return firstElement(localName)
        ?.textContent
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun Document.firstElement(localName: String): Element? {
    val namespaceMatch = getElementsByTagNameNS("*", localName).item(0) as? Element
    if (namespaceMatch != null) return namespaceMatch
    val plainMatch = getElementsByTagName(localName).item(0) as? Element
    if (plainMatch != null) return plainMatch
    return documentElement?.findFirstElement(localName)
}

private fun Node.findFirstElement(localName: String): Element? {
    if (this is Element && matchesLocalName(localName)) return this
    val children = childNodes
    for (index in 0 until children.length) {
        val childMatch = children.item(index)?.findFirstElement(localName)
        if (childMatch != null) return childMatch
    }
    return null
}

private fun Node.matchesLocalName(localName: String): Boolean {
    return this.localName == localName ||
        nodeName == localName ||
        nodeName.substringAfter(':') == localName
}

private val BARE_XML_AMPERSAND_PATTERN = Regex(
    "&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)",
)

private const val RENDERER_DIDL_LOG_TAG = "CastRenderer"
