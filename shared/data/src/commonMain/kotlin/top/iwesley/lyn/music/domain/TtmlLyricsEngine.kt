package top.iwesley.lyn.music.domain

import kotlin.math.roundToLong
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming
import top.iwesley.lyn.music.core.model.LyricsLine

private const val MAX_TTML_DOCUMENT_BYTES = 5 * 1_024 * 1_024
// Match the QName shape here; the XML reader validates names, including Unicode prefixes.
private val TTML_ROOT = Regex("""<(?:[^\s<>/:]+:)?tt(?=[\s/>]|$)""")
private val TTML_WHITESPACE = Regex("""\s+""")

/** Also recognizes incomplete XML declarations so malformed TTML cannot become plain lyrics. */
internal fun isTtmlLyricsPayload(payload: String): Boolean {
    val text = payload.trimStart().removePrefix("\uFEFF").trimStart()
    var position = 0
    while (position < text.length) {
        position = when {
            text.startsWith("<!--", position) -> {
                val end = text.indexOf("-->", position + 4)
                if (end < 0) return false
                end + 3
            }
            text.startsWith("<?", position) -> {
                if (text.regionMatches(position, "<?xml", 0, 5, ignoreCase = true)) return true
                val end = text.indexOf("?>", position + 2)
                if (end < 0) return false
                end + 2
            }
            else -> break
        }
        while (position < text.length && text[position].isWhitespace()) position++
    }
    return text.regionMatches(position, "<!DOCTYPE", 0, 9, ignoreCase = true) ||
        text.regionMatches(position, "<!ENTITY", 0, 8, ignoreCase = true) ||
        TTML_ROOT.matchAt(text, position) != null
}

internal fun parseTtmlLyricsPayload(rawPayload: String): ParsedStructuredLyricsPayload? {
    if (rawPayload.isBlank() || rawPayload.length > MAX_TTML_DOCUMENT_BYTES) return null
    if (rawPayload.encodeToByteArray().size > MAX_TTML_DOCUMENT_BYTES) return null
    if (rawPayload.contains("<!DOCTYPE", ignoreCase = true) || rawPayload.contains("<!ENTITY", ignoreCase = true)) return null
    if (!hasUniqueTtmlNamespaceDeclarations(rawPayload)) return null
    return try {
        val reader = xmlStreaming.newGenericReader(rawPayload.trimStart().removePrefix("\uFEFF").trimStart(), expandEntities = true)
        try {
            TtmlLyricsReader().read(reader)
        } finally {
            reader.close()
        }
    } catch (_: XmlException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        // XMLUtil also reports truncated XML as an IllegalStateException (Unexpected EOF).
        null
    }
}

/** XMLUtil removes namespace attributes and merges identical declarations before exposing elements. */
private fun hasUniqueTtmlNamespaceDeclarations(xml: String): Boolean {
    var position = 0
    while (true) {
        val tagStart = xml.indexOf('<', position)
        if (tagStart < 0) return true
        val terminator = when {
            xml.startsWith("<!--", tagStart) -> "-->"
            xml.startsWith("<![CDATA[", tagStart) -> "]]>"
            xml.startsWith("<?", tagStart) -> "?>"
            xml.startsWith("</", tagStart) -> ">"
            xml.startsWith("<!", tagStart) -> return false
            else -> null
        }
        if (terminator != null) {
            val end = xml.indexOf(terminator, tagStart + 2)
            if (end < 0) return false
            position = end + terminator.length
            continue
        }

        position = tagStart + 1
        while (position < xml.length && !xml[position].isWhitespace() && xml[position] != '/' && xml[position] != '>') position++
        val declarations = mutableSetOf<String>()
        while (position < xml.length) {
            while (position < xml.length && xml[position].isWhitespace()) position++
            if (position >= xml.length) return false
            if (xml[position] == '/' || xml[position] == '>') {
                position++
                break
            }
            val nameStart = position
            while (position < xml.length && !xml[position].isWhitespace() && xml[position] != '=' && xml[position] != '/' && xml[position] != '>') position++
            if (position == nameStart) return false
            val name = xml.substring(nameStart, position)
            if ((name == "xmlns" || name.startsWith("xmlns:")) && !declarations.add(name)) return false
            while (position < xml.length && xml[position].isWhitespace()) position++
            if (position >= xml.length || xml[position] != '=') return false
            position++
            while (position < xml.length && xml[position].isWhitespace()) position++
            if (position >= xml.length) return false
            val quote = xml[position]
            if (quote != '\'' && quote != '"') return false
            val valueEnd = xml.indexOf(quote, position + 1)
            if (valueEnd < 0) return false
            position = valueEnd + 1
        }
    }
}

private class TtmlLyricsReader {
    private val lines = mutableListOf<TtmlLineDraft>()
    private val translations = mutableMapOf<String, String>()
    private var currentLine: TtmlLineDraft? = null
    private var currentWord: TtmlWordDraft? = null
    private var capture: TtmlTranslationCapture? = null
    private var translationDepth: Int? = null
    private var ignoredDepth: Int? = null
    private var rootSeen = false
    private var rootClosed = false
    private var lineTiming = false
    private var originalLanguage: String? = null

    fun read(reader: XmlReader): ParsedStructuredLyricsPayload? {
        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.START_ELEMENT -> start(reader)
                EventType.END_ELEMENT -> end(reader)
                EventType.TEXT, EventType.CDSECT, EventType.IGNORABLE_WHITESPACE, EventType.ENTITY_REF -> append(reader.text)
                EventType.DOCDECL -> return null
                else -> Unit
            }
        }
        if (!rootSeen || !rootClosed) return null
        return document()
    }

    private fun start(reader: XmlReader) {
        // The generic XML reader does not reject duplicate expanded attribute names.
        if (reader.attributeCount > 1) {
            val attributeNames = mutableSetOf<Pair<String, String>>()
            for (index in 0 until reader.attributeCount) {
                require(attributeNames.add(reader.getAttributeNamespace(index) to reader.getAttributeLocalName(index)))
            }
        }
        val depth = reader.depth
        val element = reader.localName
        if (depth == 1) {
            require(!rootSeen && element == "tt")
            rootSeen = true
            lineTiming = reader.attribute("timing")?.equals("line", ignoreCase = true) == true
            originalLanguage = reader.attribute("lang", "http://www.w3.org/XML/1998/namespace")
        }
        if (ignoredDepth != null) return
        val role = reader.attribute("role")?.lowercase()
        if (element == "transliteration" || element == "span" && (
                role == "x-bg" || role == "x-roman" || reader.attribute("ruby")?.equals("text", ignoreCase = true) == true
            )
        ) {
            ignoredDepth = depth
            return
        }
        if (element == "br") {
            append(" ")
            return
        }
        if (capture != null) return
        when (element) {
            "translation" -> translationDepth = depth
            "text" -> if (translationDepth != null) {
                reader.attribute("for")?.let { key ->
                    capture = TtmlTranslationCapture(depth, key = key, inline = false)
                }
            }
            "p" -> {
                require(currentLine == null)
                currentLine = TtmlLineDraft(
                    depth = depth,
                    key = reader.attribute("key"),
                    startTimeMs = parseTtmlTime(reader.attribute("begin")),
                    endTimeMs = parseTtmlTime(reader.attribute("end")),
                )
            }
            "span" -> {
                val line = currentLine ?: return
                val rawStart = reader.attribute("begin")
                val rawEnd = reader.attribute("end")
                val wordStart = parseTtmlTime(rawStart)
                val wordEnd = parseTtmlTime(rawEnd)
                if (role == "x-translation") {
                    capture = TtmlTranslationCapture(depth, key = line.key, inline = true)
                } else if (currentWord == null && (rawStart != null || rawEnd != null)) {
                    if (rawStart != null && wordStart == null || wordStart == null && wordEnd == null) {
                        line.hasInvalidWordTiming = true
                    }
                    if (wordStart != null || wordEnd != null) {
                        currentWord = TtmlWordDraft(
                            depth = depth,
                            startTimeMs = wordStart ?: line.startTimeMs,
                            endTimeMs = wordEnd,
                            text = StringBuilder(line.pendingText.toString()),
                        )
                        line.pendingText.clear()
                    }
                }
            }
        }
    }

    private fun end(reader: XmlReader) {
        val depth = reader.depth
        ignoredDepth?.let {
            if (it == depth) ignoredDepth = null
            return
        }
        capture?.let {
            if (it.depth == depth) {
                val text = normalizeTtmlText(it.text.toString())
                if (text.isNotEmpty()) {
                    if (it.inline) {
                        currentLine?.let { line -> if (line.translation == null) line.translation = text }
                    } else {
                        it.key?.let { key -> if (key !in translations) translations[key] = text }
                    }
                }
                capture = null
            }
            return
        }
        currentWord?.let {
            if (it.depth == depth) {
                if (it.text.isNotEmpty()) currentLine?.words?.add(it)
                currentWord = null
            }
        }
        if (reader.localName == "p" && currentLine?.depth == depth) {
            currentLine?.let(lines::add)
            currentLine = null
        }
        if (translationDepth == depth) translationDepth = null
        if (depth == 1) rootClosed = true
    }

    private fun append(text: String) {
        if (ignoredDepth != null) return
        capture?.let { it.text.append(text); return }
        val line = currentLine ?: return
        // Indentation between XML elements is not a sung space.
        if (text.all(Char::isWhitespace) && text.any { it == '\n' || it == '\r' || it == '\t' }) return
        val value = TTML_WHITESPACE.replace(text, " ")
        line.text.append(value)
        val wordText = currentWord?.text ?: line.words.lastOrNull()?.text ?: line.pendingText
        wordText.append(value)
    }

    private fun document(): ParsedStructuredLyricsPayload? {
        val ordered = lines.mapIndexedNotNull { index, line ->
            val text = normalizeTtmlText(line.text.toString()).takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            val start = line.startTimeMs ?: line.words.firstOrNull()?.startTimeMs
            TtmlResolvedLine(index, line, text, start)
        }.sortedWith(compareBy<TtmlResolvedLine> { it.startTimeMs ?: Long.MAX_VALUE }.thenBy { it.sourceIndex })
        if (ordered.isEmpty()) return null
        val nextLineStarts = arrayOfNulls<Long>(ordered.size)
        var followingStart: Long? = null
        for (index in ordered.lastIndex - 1 downTo 0) {
            val start = ordered[index].startTimeMs
            val nextStart = ordered[index + 1].startTimeMs
            if (nextStart != null && (start == null || nextStart > start)) followingStart = nextStart
            nextLineStarts[index] = followingStart
        }
        val displayLines = ordered.mapIndexed { index, entry ->
            val line = entry.draft
            val text = entry.text
            val start = entry.startTimeMs
            val end = validTtmlEnd(line.endTimeMs, start)
                ?: validTtmlEnd(line.words.lastOrNull()?.endTimeMs, start)
                ?: nextLineStarts[index]
            val segmentTexts = normalizedTtmlWordTexts(line.words)
            val hasWordTiming = !lineTiming && !line.hasInvalidWordTiming && line.words.isNotEmpty() &&
                line.words.all { it.startTimeMs != null } &&
                line.words.zipWithNext().all { (left, right) -> left.startTimeMs!! <= right.startTimeMs!! } &&
                segmentTexts.joinToString("") == text
            val segments = if (!hasWordTiming) emptyList() else line.words.mapIndexedNotNull { wordIndex, word ->
                val wordText = segmentTexts[wordIndex].takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
                val wordStart = requireNotNull(word.startTimeMs)
                EnhancedLyricsSegment(
                    text = wordText,
                    startTimeMs = wordStart,
                    endTimeMs = validTtmlEnd(word.endTimeMs, wordStart)
                        ?: validTtmlEnd(line.words.getOrNull(wordIndex + 1)?.startTimeMs, wordStart)
                        ?: validTtmlEnd(end, wordStart),
                )
            }
            EnhancedLyricsDisplayLine(
                text = text,
                lineStartTimeMs = start,
                lineEndTimeMs = end,
                segments = segments,
                translationText = line.translation ?: line.key?.let(translations::get),
            )
        }
        return ParsedStructuredLyricsPayload(
            lines = displayLines.map { LyricsLine(timestampMs = it.lineStartTimeMs, text = it.text) },
            offsetMs = 0L,
            displayLines = displayLines,
            hasEnhancedSegments = displayLines.any { it.segments.isNotEmpty() },
            originalLanguage = originalLanguage,
        )
    }
}

private class TtmlResolvedLine(
    val sourceIndex: Int,
    val draft: TtmlLineDraft,
    val text: String,
    val startTimeMs: Long?,
)

private class TtmlLineDraft(
    val depth: Int,
    val key: String?,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val text: StringBuilder = StringBuilder(),
    val pendingText: StringBuilder = StringBuilder(),
    val words: MutableList<TtmlWordDraft> = mutableListOf(),
    var translation: String? = null,
    var hasInvalidWordTiming: Boolean = false,
)

private class TtmlWordDraft(
    val depth: Int,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val text: StringBuilder,
)

private class TtmlTranslationCapture(
    val depth: Int,
    val key: String?,
    val inline: Boolean,
    val text: StringBuilder = StringBuilder(),
)

private fun XmlReader.attribute(name: String, namespace: String? = null): String? = (0 until attributeCount)
    .firstOrNull { getAttributeLocalName(it) == name && (namespace == null || getAttributeNamespace(it) == namespace) }
    ?.let(::getAttributeValue)

private fun normalizeTtmlText(text: String): String = TTML_WHITESPACE.replace(text, " ").trim()

private fun normalizedTtmlWordTexts(words: List<TtmlWordDraft>): List<String> {
    val texts = words.map { TTML_WHITESPACE.replace(it.text.toString(), " ") }.toMutableList()
    var previous = -1
    for (index in texts.indices) {
        if (previous < 0 || texts[previous].endsWith(' ')) texts[index] = texts[index].trimStart(' ')
        if (texts[index].isNotEmpty()) previous = index
    }
    if (previous >= 0) texts[previous] = texts[previous].trimEnd(' ')
    return texts
}

private fun validTtmlEnd(end: Long?, start: Long?): Long? = end?.takeIf { start == null || it > start }

private fun parseTtmlTime(raw: String?): Long? {
    var value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val multiplier = if (value.endsWith("ms", ignoreCase = true)) {
        value = value.dropLast(2)
        1.0
    } else {
        if (value.endsWith('s', ignoreCase = true)) value = value.dropLast(1)
        1_000.0
    }
    val components = value.split(':').map { it.toDoubleOrNull()?.takeIf { number -> number.isFinite() && number >= 0 } ?: return null }
    val seconds = when (components.size) {
        1 -> components[0]
        2 -> {
            if (multiplier == 1.0 || components.any { it >= 60 }) return null
            components[0] * 60 + components[1]
        }
        3 -> {
            if (multiplier == 1.0 || components[1] >= 60 || components[2] >= 60) return null
            components[0] * 3_600 + components[1] * 60 + components[2]
        }
        else -> return null
    }
    val milliseconds = seconds * multiplier
    return milliseconds.takeIf { it.isFinite() && it < Long.MAX_VALUE.toDouble() }?.roundToLong()
}
