package top.iwesley.lyn.music.cast.upnp.android

import top.iwesley.lyn.music.cast.CastMediaRequest
import top.iwesley.lyn.music.cast.directCastUriOrNull

internal fun buildUpnpDidl(request: CastMediaRequest): String {
    val duration = request.durationMs
        .takeIf { it > 0L }
        ?.let(::formatUpnpDuration)
    val artist = request.artistName?.takeIf { it.isNotBlank() }
    val album = request.albumTitle?.takeIf { it.isNotBlank() }
    val artworkUri = directCastUriOrNull(request.artworkUri)
    return buildString {
        append("""<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" """)
        append("""xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" """)
        append("""xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">""")
        append("""<item id="0" parentID="0" restricted="1">""")
        append("<dc:title>")
        append(escapeXml(request.title))
        append("</dc:title>")
        artist?.let {
            append("<upnp:artist>")
            append(escapeXml(it))
            append("</upnp:artist>")
        }
        album?.let {
            append("<upnp:album>")
            append(escapeXml(it))
            append("</upnp:album>")
        }
        artworkUri?.let {
            append("<upnp:albumArtURI>")
            append(escapeXml(it))
            append("</upnp:albumArtURI>")
        }
        append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
        append("""<res protocolInfo="http-get:*:""")
        append(escapeXmlAttribute(request.mimeType))
        append(""":*" """)
        duration?.let {
            append("""duration="""")
            append(it)
            append("""" """)
        }
        append(">")
        append(escapeXml(request.uri))
        append("</res>")
        append("</item></DIDL-Lite>")
    }
}

internal fun formatUpnpDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return "${hours.pad2()}:${minutes.pad2()}:${seconds.pad2()}"
}

private fun Long.pad2(): String = toString().padStart(2, '0')

private fun escapeXml(value: String): String {
    return buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(character)
            }
        }
    }
}

private fun escapeXmlAttribute(value: String): String = escapeXml(value)
