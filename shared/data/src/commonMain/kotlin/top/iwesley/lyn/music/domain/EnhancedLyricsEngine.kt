package top.iwesley.lyn.music.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine

data class EnhancedLyricsSegment(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
)

data class EnhancedLyricsDisplayLine(
    val text: String,
    val lineStartTimeMs: Long?,
    val lineEndTimeMs: Long? = null,
    val segments: List<EnhancedLyricsSegment> = emptyList(),
    val translationText: String? = null,
)

data class EnhancedLyricsPresentation(
    val lines: List<EnhancedLyricsDisplayLine>,
    val offsetMs: Long = 0L,
)

internal data class ParsedStructuredLyricsPayload(
    val lines: List<LyricsLine>,
    val offsetMs: Long,
    val displayLines: List<EnhancedLyricsDisplayLine>,
    val hasEnhancedSegments: Boolean,
)

fun parseEnhancedLyricsPresentation(
    rawPayload: String,
    fallbackDocument: LyricsDocument,
): EnhancedLyricsPresentation? {
    val parsed = parseStructuredLyricsPayload(rawPayload) ?: return null
    if (!parsed.hasEnhancedSegments) return null
    if (parsed.lines.size != fallbackDocument.lines.size) return null
    val matchesFallback = parsed.lines.zip(fallbackDocument.lines).all { (parsedLine, fallbackLine) ->
        parsedLine.timestampMs == fallbackLine.timestampMs &&
            parsedLine.text == fallbackLine.text
    }
    if (!matchesFallback) return null
    return EnhancedLyricsPresentation(
        lines = parsed.displayLines,
        offsetMs = fallbackDocument.offsetMs,
    )
}

internal fun parseStructuredLyricsPayload(rawPayload: String): ParsedStructuredLyricsPayload? {
    if (rawPayload.isBlank()) return null
    parseNavidromeStructuredLyricsPayload(rawPayload)?.let { return it }
    val hasStructuredSyntax = rawPayload.lineSequence()
        .map { it.trim() }
        .any { line ->
            line.isNotEmpty() && (
                ENHANCED_LYRICS_OFFSET_REGEX.matches(line) ||
                    ENHANCED_LYRICS_METADATA_REGEX.matches(line) ||
                    ENHANCED_LYRICS_LINE_TIMESTAMP_REGEX.containsMatchIn(line) ||
                    ENHANCED_LYRICS_INLINE_TIMESTAMP_REGEX.containsMatchIn(line)
                )
        }
    if (!hasStructuredSyntax) return null

    var offsetMs = 0L
    val documentLines = mutableListOf<LyricsLine>()
    val displayLineDrafts = mutableListOf<ParsedDisplayLineDraft>()

    rawPayload.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { rawLine ->
            val offsetMatch = ENHANCED_LYRICS_OFFSET_REGEX.matchEntire(rawLine)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: offsetMs
                return@forEach
            }
            if (ENHANCED_LYRICS_METADATA_REGEX.matches(rawLine)) {
                return@forEach
            }

            parseEslrcDisplayLineDraft(rawLine)?.let { eslrcLine ->
                documentLines += LyricsLine(
                    timestampMs = eslrcLine.lineStartTimeMs,
                    text = eslrcLine.text,
                )
                displayLineDrafts += eslrcLine
                return@forEach
            }

            val lineTimestampMatches = ENHANCED_LYRICS_LINE_TIMESTAMP_REGEX.findAll(rawLine).toList()
            val lineTimestamps = lineTimestampMatches.mapNotNull(::parseStructuredTimestampMatch)
            val content = rawLine.replace(ENHANCED_LYRICS_LINE_TIMESTAMP_REGEX, "")
            val segmentDrafts = parseEnhancedSegmentDrafts(content)

            if (segmentDrafts != null) {
                val visibleText = segmentDrafts.joinToString(separator = "") { it.text }.ifBlank { "..." }
                val lineStartTimeMs = lineTimestamps.firstOrNull() ?: segmentDrafts.firstOrNull()?.startTimeMs
                documentLines += LyricsLine(
                    timestampMs = lineStartTimeMs,
                    text = visibleText,
                )
                displayLineDrafts += ParsedDisplayLineDraft(
                    text = visibleText,
                    lineStartTimeMs = lineStartTimeMs,
                    segments = segmentDrafts,
                    source = ParsedDisplayLineSource.EnhancedInline,
                )
                return@forEach
            }

            if (lineTimestamps.isNotEmpty()) {
                val lyricText = content.trim().ifBlank { "..." }
                lineTimestamps.forEach { timestampMs ->
                    documentLines += LyricsLine(
                        timestampMs = timestampMs,
                        text = lyricText,
                    )
                    displayLineDrafts += ParsedDisplayLineDraft(
                        text = lyricText,
                        lineStartTimeMs = timestampMs,
                        source = ParsedDisplayLineSource.LineTimestamp,
                    )
                }
                return@forEach
            }

            val lyricText = content.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            documentLines += LyricsLine(
                timestampMs = null,
                text = lyricText,
            )
            displayLineDrafts += ParsedDisplayLineDraft(
                text = lyricText,
                lineStartTimeMs = null,
                source = ParsedDisplayLineSource.Plain,
            )
        }

    if (documentLines.isEmpty()) return null
    val mergedEntries = mergeAdjacentTranslatedEslrcLines(documentLines.zip(displayLineDrafts))
    val orderedEntries = mergedEntries
        .withIndex()
        .let { indexedEntries ->
            if (mergedEntries.all { it.first.timestampMs != null }) {
                indexedEntries.sortedWith(
                    compareBy<IndexedValue<Pair<LyricsLine, ParsedDisplayLineDraft>>> {
                        it.value.first.timestampMs ?: Long.MAX_VALUE
                    }.thenBy { it.index },
                )
            } else {
                indexedEntries
            }
        }
        .map { it.value }
    val orderedDocumentLines = orderedEntries.map { it.first }
    val orderedDisplayLineDrafts = orderedEntries.map { it.second }
    val displayLines = finalizeDisplayLines(orderedDisplayLineDrafts)
    return ParsedStructuredLyricsPayload(
        lines = orderedDocumentLines,
        offsetMs = offsetMs,
        displayLines = displayLines,
        hasEnhancedSegments = displayLines.any { it.segments.isNotEmpty() },
    )
}

private data class ParsedDisplayLineDraft(
    val text: String,
    val lineStartTimeMs: Long?,
    val lineEndTimeMs: Long? = null,
    val segments: List<ParsedSegmentDraft> = emptyList(),
    val translationText: String? = null,
    val source: ParsedDisplayLineSource = ParsedDisplayLineSource.Plain,
)

private data class ParsedSegmentDraft(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
)

private enum class ParsedDisplayLineSource {
    Eslrc,
    EnhancedInline,
    LineTimestamp,
    Plain,
}

private fun mergeAdjacentTranslatedEslrcLines(
    entries: List<Pair<LyricsLine, ParsedDisplayLineDraft>>,
): List<Pair<LyricsLine, ParsedDisplayLineDraft>> {
    val mergedEntries = mutableListOf<Pair<LyricsLine, ParsedDisplayLineDraft>>()
    var index = 0
    while (index < entries.size) {
        val current = entries[index]
        val next = entries.getOrNull(index + 1)
        if (next != null && shouldMergeTranslatedEslrcPair(current.second, next.second)) {
            mergedEntries += current.first to current.second.copy(
                translationText = next.second.text.trim(),
            )
            index += 2
        } else {
            mergedEntries += current
            index += 1
        }
    }
    return mergedEntries
}

private fun shouldMergeTranslatedEslrcPair(
    primary: ParsedDisplayLineDraft,
    translation: ParsedDisplayLineDraft,
): Boolean {
    val primaryStartTimeMs = primary.lineStartTimeMs ?: return false
    return primary.source == ParsedDisplayLineSource.Eslrc &&
        translation.source == ParsedDisplayLineSource.Eslrc &&
        translation.lineStartTimeMs == primaryStartTimeMs &&
        hasUsableEslrcWordTiming(primary) &&
        isLikelyNonCjkPrimaryLine(primary.text) &&
        isLikelyCjkTranslationLine(translation.text)
}

private fun hasUsableEslrcWordTiming(line: ParsedDisplayLineDraft): Boolean {
    if (line.segments.size < 2) return false
    return line.segments.any { segment ->
        segment.endTimeMs?.let { endTimeMs -> endTimeMs > segment.startTimeMs } == true
    } || line.segments.zipWithNext().any { (current, next) ->
        next.startTimeMs > current.startTimeMs
    }
}

private fun isLikelyNonCjkPrimaryLine(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank() || normalized == "...") return false
    if (normalized.any(::isCjkCharacter)) return false
    return normalized.any { it.isLetterOrDigit() }
}

private fun isLikelyCjkTranslationLine(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank() || normalized == "...") return false
    return normalized.any(::isCjkCharacter)
}

private fun isCjkCharacter(char: Char): Boolean {
    return char in '\u3400'..'\u4DBF' ||
        char in '\u4E00'..'\u9FFF' ||
        char in '\uF900'..'\uFAFF'
}

private fun finalizeDisplayLines(drafts: List<ParsedDisplayLineDraft>): List<EnhancedLyricsDisplayLine> {
    return drafts.mapIndexed { index, draft ->
        val nextLineStartTimeMs = drafts.asSequence()
            .drop(index + 1)
            .mapNotNull { it.lineStartTimeMs }
            .firstOrNull()
            ?.takeIf { nextStart -> draft.lineStartTimeMs == null || nextStart > draft.lineStartTimeMs }
        val resolvedLineEndTimeMs = draft.lineEndTimeMs
            ?.takeIf { lineEnd -> draft.lineStartTimeMs == null || lineEnd > draft.lineStartTimeMs }
            ?: nextLineStartTimeMs
        EnhancedLyricsDisplayLine(
            text = draft.text,
            lineStartTimeMs = draft.lineStartTimeMs,
            lineEndTimeMs = resolvedLineEndTimeMs,
            translationText = draft.translationText,
            segments = draft.segments.mapIndexed { segmentIndex, segment ->
                val nextSegmentStartTimeMs = draft.segments
                    .getOrNull(segmentIndex + 1)
                    ?.startTimeMs
                    ?.takeIf { nextStart -> nextStart > segment.startTimeMs }
                EnhancedLyricsSegment(
                    text = segment.text,
                    startTimeMs = segment.startTimeMs,
                    endTimeMs = segment.endTimeMs
                        ?.takeIf { segmentEnd -> segmentEnd > segment.startTimeMs }
                        ?: nextSegmentStartTimeMs
                        ?: resolvedLineEndTimeMs,
                )
            },
        )
    }
}

private fun parseEnhancedSegmentDrafts(content: String): List<ParsedSegmentDraft>? {
    val matches = ENHANCED_LYRICS_INLINE_TIMESTAMP_REGEX.findAll(content).toList()
    if (matches.isEmpty()) return null
    val leadingText = content.substring(0, matches.first().range.first)
    val segments = buildList {
        matches.forEachIndexed { index, match ->
            val segmentStartTimeMs = parseStructuredTimestampMatch(match) ?: return@forEachIndexed
            val segmentTextStart = match.range.last + 1
            val segmentTextEnd = matches.getOrNull(index + 1)?.range?.first ?: content.length
            val segmentText = buildString {
                if (index == 0) {
                    append(leadingText)
                }
                append(content.substring(segmentTextStart, segmentTextEnd))
            }
            if (segmentText.isNotEmpty()) {
                add(
                    ParsedSegmentDraft(
                        text = segmentText,
                        startTimeMs = segmentStartTimeMs,
                        endTimeMs = null,
                    ),
                )
            }
        }
    }
    return segments
}

private fun parseEslrcDisplayLineDraft(rawLine: String): ParsedDisplayLineDraft? {
    var content = rawLine
    val lineStart = parseStructuredTimestampPrefix(content) ?: return null
    content = content.substring(lineStart.length)
    if (content.trim().isEmpty()) return null

    val segments = buildList {
        var currentStartTimeMs = lineStart.timeMs
        var remaining = content
        while (remaining.trim().isNotEmpty()) {
            val nextTimestampIndex = remaining.indexOf('[')
            if (nextTimestampIndex <= 0) {
                return null
            }
            val nextTimestamp = parseStructuredTimestampPrefix(
                remaining.substring(nextTimestampIndex),
            ) ?: return null
            add(
                ParsedSegmentDraft(
                    text = remaining.substring(0, nextTimestampIndex),
                    startTimeMs = currentStartTimeMs,
                    endTimeMs = nextTimestamp.timeMs,
                ),
            )
            remaining = remaining.substring(nextTimestampIndex + nextTimestamp.length)
            currentStartTimeMs = nextTimestamp.timeMs
        }
    }
    if (segments.isEmpty()) return null
    val visibleText = segments.joinToString(separator = "") { it.text }.ifBlank { "..." }
    return ParsedDisplayLineDraft(
        text = visibleText,
        lineStartTimeMs = lineStart.timeMs,
        lineEndTimeMs = segments.last().endTimeMs,
        segments = segments,
        source = ParsedDisplayLineSource.Eslrc,
    )
}

internal fun parseNavidromeStructuredLyricsPayload(rawPayload: String): ParsedStructuredLyricsPayload? {
    if (rawPayload.isBlank()) return null
    val root = runCatching { structuredLyricsJson.parseToJsonElement(rawPayload) }.getOrNull() as? JsonObject
        ?: return null
    return parseNavidromeStructuredLyricsPayload(root)
}

internal fun parseNavidromeStructuredLyricsPayload(rootPayload: JsonObject): ParsedStructuredLyricsPayload? {
    val payload = rootPayload["subsonic-response"].asJsonObjectOrNull() ?: rootPayload
    val lyricsList = payload["lyricsList"].asJsonObjectOrNull() ?: return null
    val entries = lyricsList["structuredLyrics"].asJsonObjectList()
    if (entries.isEmpty()) return null
    val chosen = entries
        .mapNotNull(::parseNavidromeStructuredLyricsEntry)
        .filter { entry -> entry.kind == null || entry.kind.equals("main", ignoreCase = true) }
        .sortedWith(
            compareByDescending<ParsedNavidromeStructuredLyricsEntry> { it.hasEnhancedSegments }
                .thenByDescending { it.synced }
                .thenByDescending { it.lines.size },
        )
        .firstOrNull()
        ?: return null
    return ParsedStructuredLyricsPayload(
        lines = chosen.lines,
        offsetMs = chosen.offsetMs,
        displayLines = chosen.displayLines,
        hasEnhancedSegments = chosen.hasEnhancedSegments,
    )
}

private data class ParsedNavidromeStructuredLyricsEntry(
    val kind: String?,
    val synced: Boolean,
    val offsetMs: Long,
    val lines: List<LyricsLine>,
    val displayLines: List<EnhancedLyricsDisplayLine>,
    val hasEnhancedSegments: Boolean,
)

private data class ParsedNavidromeLineDraft(
    val index: Int,
    val text: String,
    val startTimeMs: Long?,
)

private data class ParsedNavidromeCueLineDraft(
    val index: Int,
    val text: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val segments: List<ParsedSegmentDraft>,
    val agentId: String? = null,
)

private fun parseNavidromeStructuredLyricsEntry(entry: JsonObject): ParsedNavidromeStructuredLyricsEntry? {
    val kind = entry.string("kind")
    val synced = entry.boolean("synced") ?: false
    val offsetMs = entry.long("offset") ?: 0L
    val lineDrafts = entry["line"].asJsonObjectList()
        .mapIndexedNotNull { index, line ->
            val text = line.string("value")?.trim().orEmpty()
            if (text.isBlank()) return@mapIndexedNotNull null
            ParsedNavidromeLineDraft(
                index = index,
                text = text,
                startTimeMs = if (synced) line.long("start") else null,
            )
        }
    if (lineDrafts.isEmpty()) return null

    val cueLinesByIndex = if (synced) {
        val mainAgentId = entry["agents"].asJsonObjectList()
            .firstOrNull { agent -> agent.string("role")?.equals("main", ignoreCase = true) == true }
            ?.string("id")
        buildMap {
            entry["cueLine"].asJsonObjectList()
            .mapNotNull(::parseNavidromeCueLineDraft)
            .filter { cueLine ->
                mainAgentId == null ||
                    cueLine.agentId == null ||
                    cueLine.agentId == mainAgentId
            }
                .forEach { cueLine ->
                    putIfAbsent(cueLine.index, cueLine)
                }
        }
    } else {
        emptyMap()
    }

    val displayLineDrafts = lineDrafts.map { line ->
        val cueLine = cueLinesByIndex[line.index]
        ParsedDisplayLineDraft(
            text = cueLine?.text ?: line.text,
            lineStartTimeMs = line.startTimeMs ?: cueLine?.startTimeMs,
            lineEndTimeMs = cueLine?.endTimeMs,
            segments = cueLine?.segments.orEmpty(),
        )
    }
    return ParsedNavidromeStructuredLyricsEntry(
        kind = kind,
        synced = synced,
        offsetMs = offsetMs,
        lines = lineDrafts.map { line ->
            LyricsLine(
                timestampMs = line.startTimeMs,
                text = line.text,
            )
        },
        displayLines = finalizeDisplayLines(displayLineDrafts),
        hasEnhancedSegments = displayLineDrafts.any { it.segments.isNotEmpty() },
    )
}

private fun parseNavidromeCueLineDraft(entry: JsonObject): ParsedNavidromeCueLineDraft? {
    val value = entry.string("value") ?: return null
    val index = entry.int("index") ?: return null
    val startTimeMs = entry.long("start")
    val endTimeMs = entry.long("end")
    val cueDrafts = parseNavidromeCueDrafts(
        text = value,
        cueEntries = entry["cue"].asJsonObjectList(),
    )
    if (cueDrafts.isEmpty()) return null
    return ParsedNavidromeCueLineDraft(
        index = index,
        text = value,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        segments = cueDrafts,
        agentId = entry.string("agentId"),
    )
}

private fun parseNavidromeCueDrafts(
    text: String,
    cueEntries: List<JsonObject>,
): List<ParsedSegmentDraft> {
    if (cueEntries.isEmpty()) return emptyList()
    val textBytes = text.encodeToByteArray()
    var consumedByteEndExclusive = 0
    val drafts = mutableListOf<ParsedSegmentDraft>()
    cueEntries.forEach { cue ->
        val cueStartTimeMs = cue.long("start") ?: return@forEach
        val cueEndTimeMs = cue.long("end")
        val byteEndInclusive = cue.int("byteEnd")
        val resolvedEndExclusive = byteEndInclusive
            ?.plus(1)
            ?.coerceIn(0, textBytes.size)
            ?: textBytes.size
        val resolvedStartInclusive = consumedByteEndExclusive.coerceIn(0, resolvedEndExclusive)
        val segmentText = if (resolvedStartInclusive < resolvedEndExclusive) {
            textBytes.copyOfRange(resolvedStartInclusive, resolvedEndExclusive).decodeToString()
        } else {
            cue.string("value").orEmpty()
        }
        if (segmentText.isEmpty()) {
            consumedByteEndExclusive = resolvedEndExclusive
            return@forEach
        }
        drafts += ParsedSegmentDraft(
            text = segmentText,
            startTimeMs = cueStartTimeMs,
            endTimeMs = cueEndTimeMs,
        )
        consumedByteEndExclusive = resolvedEndExclusive
    }
    if (drafts.isEmpty()) return emptyList()
    if (consumedByteEndExclusive < textBytes.size) {
        val trailingText = textBytes.copyOfRange(consumedByteEndExclusive, textBytes.size).decodeToString()
        if (trailingText.isNotEmpty()) {
            val lastDraft = drafts.last()
            drafts[drafts.lastIndex] = lastDraft.copy(text = lastDraft.text + trailingText)
        }
    }
    return drafts
}

private fun parseStructuredTimestampMatch(match: MatchResult): Long? {
    val minute = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
    val second = match.groupValues.getOrNull(2)?.toLongOrNull() ?: return null
    val fraction = match.groupValues.getOrElse(3) { "" }
    val millis = when (fraction.length) {
        0 -> 0L
        1 -> fraction.toLong() * 100L
        2 -> fraction.toLong() * 10L
        else -> fraction.take(3).toLong()
    }
    return minute * 60_000L + second * 1_000L + millis
}

private fun parseStructuredTimestampPrefix(src: String): ParsedTimestampPrefix? {
    val match = ENHANCED_LYRICS_LINE_TIMESTAMP_REGEX.find(src)
        ?.takeIf { it.range.first == 0 }
        ?: return null
    return ParsedTimestampPrefix(
        timeMs = parseStructuredTimestampMatch(match) ?: return null,
        length = match.value.length,
    )
}

private data class ParsedTimestampPrefix(
    val timeMs: Long,
    val length: Int,
)

private val ENHANCED_LYRICS_LINE_TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val ENHANCED_LYRICS_INLINE_TIMESTAMP_REGEX = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")
private val ENHANCED_LYRICS_OFFSET_REGEX = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
private val ENHANCED_LYRICS_METADATA_REGEX = Regex(
    """^\[(ti|ar|al|by|re|ve|length|lang|language):.*]$""",
    RegexOption.IGNORE_CASE,
)
private val structuredLyricsJson = Json { ignoreUnknownKeys = true }

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asJsonObjectList(): List<JsonObject> {
    return when (val element = this) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(element)
        else -> emptyList()
    }
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull
}

private fun JsonObject.boolean(key: String): Boolean? {
    return string(key)?.lowercase()?.let { value ->
        when (value) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
}

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()
