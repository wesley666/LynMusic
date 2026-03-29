package top.iwesley.lyn.music.domain

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.Track

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun buildLyricsRequest(config: LyricsSourceConfig, track: Track): LyricsRequest {
    val url = appendQuery(
        interpolateTemplate(config.urlTemplate, track, encode = true),
        interpolateTemplate(config.queryTemplate, track, encode = true),
    )
    val headers = parseHeaders(interpolateTemplate(config.headersTemplate, track, encode = false))
    val body = interpolateTemplate(config.bodyTemplate, track, encode = false).ifBlank { null }
    return LyricsRequest(
        method = config.method,
        url = url,
        headers = headers,
        body = body,
    )
}

fun parseLyricsPayload(
    config: LyricsSourceConfig,
    payload: String,
): LyricsDocument? {
    val lines = when (config.responseFormat) {
        LyricsResponseFormat.LRC -> parseLrc(payload)
        LyricsResponseFormat.TEXT -> parsePlainText(payload)
        LyricsResponseFormat.JSON -> parseJsonPayload(config.extractor, payload)
        LyricsResponseFormat.XML -> parseXmlPayload(config.extractor, payload)
    }
    if (lines.isEmpty()) {
        return null
    }
    return LyricsDocument(
        lines = lines,
        offsetMs = 0L,
        sourceId = config.id,
        rawPayload = payload,
    )
}

fun serializeLyricsDocument(document: LyricsDocument): String {
    return buildString {
        document.lines.forEach { line ->
            val timestamp = line.timestampMs?.let { formatTimestamp(it + document.offsetMs) }.orEmpty()
            append(timestamp)
            append(line.text)
            append('\n')
        }
    }.trim()
}

fun parseCachedLyrics(sourceId: String, rawPayload: String): LyricsDocument? {
    val lrcLines = parseLrc(rawPayload)
    val lines = if (lrcLines.isNotEmpty()) lrcLines else parsePlainText(rawPayload)
    if (lines.isEmpty()) return null
    return LyricsDocument(
        lines = lines,
        offsetMs = 0L,
        sourceId = sourceId,
        rawPayload = rawPayload,
    )
}

private fun parseJsonPayload(extractor: String, payload: String): List<LyricsLine> {
    val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return emptyList()
    val normalized = extractor.removePrefix("json:").removePrefix("json-lines:")
    return if (extractor.startsWith("json-lines:")) {
        val (arrayPath, mapping) = normalized.split('|', limit = 2).let {
            it.firstOrNull().orEmpty() to it.getOrElse(1) { "time,text" }
        }
        val fields = mapping.split(',').map { it.trim() }
        val timeField = fields.getOrNull(0).orEmpty()
        val textField = fields.getOrNull(1).orEmpty()
        extractJsonArray(root, arrayPath).mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val text = obj[textField]?.jsonPrimitiveContent() ?: return@mapNotNull null
            LyricsLine(
                timestampMs = parseFlexibleTimestamp(obj[timeField]?.jsonPrimitiveContent()),
                text = text,
            )
        }
    } else {
        val extracted = when {
            normalized.isBlank() || normalized == "text" -> root.jsonPrimitiveContent()
            else -> extractJsonValue(root, normalized)?.jsonPrimitiveContent()
        }.orEmpty()
        parseLrc(extracted).ifEmpty { parsePlainText(extracted) }
    }
}

private fun parseXmlPayload(extractor: String, payload: String): List<LyricsLine> {
    val normalized = extractor.removePrefix("xml:").removePrefix("xml-lines:")
    return if (extractor.startsWith("xml-lines:")) {
        val (itemTag, mapping) = normalized.split('|', limit = 2).let {
            it.firstOrNull().orEmpty() to it.getOrElse(1) { "time,text" }
        }
        val fields = mapping.split(',').map { it.trim() }
        val timeTag = fields.getOrNull(0).orEmpty()
        val textTag = fields.getOrNull(1).orEmpty()
        extractXmlTagItems(payload, itemTag).mapNotNull { node ->
            val text = extractXmlTagValue(node, textTag) ?: return@mapNotNull null
            LyricsLine(
                timestampMs = parseFlexibleTimestamp(extractXmlTagValue(node, timeTag)),
                text = text.trim(),
            )
        }
    } else {
        val extracted = when {
            normalized.isBlank() || normalized == "text" -> payload
            else -> extractXmlPath(payload, normalized.split('.')).orEmpty()
        }
        parseLrc(extracted).ifEmpty { parsePlainText(extracted) }
    }
}

fun parseLrc(text: String): List<LyricsLine> {
    if (text.isBlank()) return emptyList()
    val lines = mutableListOf<LyricsLine>()
    val timestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { rawLine ->
            val matches = timestampRegex.findAll(rawLine).toList()
            val lyricText = rawLine.replace(timestampRegex, "").trim().ifBlank { "..." }
            if (matches.isEmpty()) {
                lines += LyricsLine(timestampMs = null, text = lyricText)
            } else {
                matches.forEach { match ->
                    val minute = match.groupValues[1].toLongOrNull() ?: 0L
                    val second = match.groupValues[2].toLongOrNull() ?: 0L
                    val fraction = match.groupValues.getOrElse(3) { "" }
                    val millis = when (fraction.length) {
                        0 -> 0L
                        1 -> fraction.toLong() * 100L
                        2 -> fraction.toLong() * 10L
                        else -> fraction.take(3).toLong()
                    }
                    lines += LyricsLine(
                        timestampMs = minute * 60_000L + second * 1_000L + millis,
                        text = lyricText,
                    )
                }
            }
        }
    return lines.sortedBy { it.timestampMs ?: Long.MAX_VALUE }
}

fun parsePlainText(text: String): List<LyricsLine> {
    return text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { LyricsLine(timestampMs = null, text = it) }
        .toList()
}

private fun interpolateTemplate(template: String, track: Track, encode: Boolean): String {
    if (template.isBlank()) return ""
    val durationSeconds = ((track.durationMs + 500L) / 1_000L).toString()
    val replacements = mapOf(
        "title" to track.title,
        "artist" to track.artistName.orEmpty(),
        "album" to track.albumTitle.orEmpty(),
        "duration_ms" to track.durationMs.toString(),
        "duration_seconds" to durationSeconds,
        "duration" to durationSeconds,
        "path" to track.relativePath,
        "track_id" to track.id,
        "source_id" to track.sourceId,
    )
    return replacements.entries.fold(template) { acc, entry ->
        val replacement = if (encode) entry.value.encodeURLParameter() else entry.value
        acc.replace("{${entry.key}}", replacement)
    }
}

private fun appendQuery(url: String, query: String): String {
    if (query.isBlank()) return url
    val separator = if (url.contains('?')) "&" else "?"
    return "$url$separator$query"
}

private fun parseHeaders(headersTemplate: String): Map<String, String> {
    return headersTemplate.lineSequence()
        .map { it.trim() }
        .filter { it.contains(':') }
        .associate { line ->
            val parts = line.split(':', limit = 2)
            parts[0].trim() to parts[1].trim()
        }
}

private fun extractJsonValue(root: JsonElement, path: String): JsonElement? {
    if (path.isBlank()) return root
    return normalizeJsonPath(path)
        .split('.')
        .filter { it.isNotBlank() }
        .fold(root as JsonElement?) { current, segment ->
            when (current) {
                is JsonObject -> current[segment]
                is JsonArray -> segment.toIntOrNull()?.let { index -> current.getOrNull(index) }
                else -> null
            }
        }
}

private fun extractJsonArray(root: JsonElement, path: String): JsonArray {
    return extractJsonValue(root, path) as? JsonArray ?: JsonArray(emptyList())
}

private fun normalizeJsonPath(path: String): String {
    return Regex("""\[(\d+)]""").replace(path.trim()) { match ->
        "." + match.groupValues[1]
    }.removePrefix(".")
}

private fun JsonElement.jsonPrimitiveContent(): String? {
    return (this as? JsonPrimitive)?.contentOrNull
}

private fun extractXmlPath(xml: String, path: List<String>): String? {
    return path.fold(xml as String?) { current, tag ->
        current?.let { extractXmlTagValue(it, tag) }
    }
}

private fun extractXmlTagValue(xml: String, tag: String): String? {
    if (tag.isBlank()) return null
    val regex = Regex("<$tag(?:\\s+[^>]*)?>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    return regex.find(xml)?.groupValues?.getOrNull(1)
}

private fun extractXmlTagItems(xml: String, tag: String): List<String> {
    if (tag.isBlank()) return emptyList()
    val regex = Regex("<$tag(?:\\s+[^>]*)?>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    return regex.findAll(xml).map { it.groupValues[1] }.toList()
}

private fun parseFlexibleTimestamp(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val normalized = value.trim()
    normalized.toLongOrNull()?.let { return it }
    return when {
        normalized.contains(':') -> {
            val parts = normalized.split(':')
            val minutes = parts.getOrNull(0)?.toLongOrNull() ?: return null
            val seconds = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
            (minutes * 60_000 + (seconds * 1_000)).toLong()
        }
        normalized.contains('.') -> (normalized.toDoubleOrNull()?.times(1_000))?.toLong()
        else -> null
    }
}

private fun formatTimestamp(milliseconds: Long): String {
    val clamped = milliseconds.coerceAtLeast(0L)
    val minute = clamped / 60_000
    val second = (clamped % 60_000) / 1_000
    val fraction = (clamped % 1_000) / 10
    return "[" +
        minute.toString().padStart(2, '0') +
        ":" +
        second.toString().padStart(2, '0') +
        "." +
        fraction.toString().padStart(2, '0') +
        "]"
}
