package top.iwesley.lyn.music.domain

import io.ktor.http.encodeURLParameter
import kotlin.io.encoding.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowLyricsConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsStepConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsTransform
import top.iwesley.lyn.music.core.model.WorkflowOptionalFields
import top.iwesley.lyn.music.core.model.WorkflowRequestConfig
import top.iwesley.lyn.music.core.model.WorkflowSearchConfig
import top.iwesley.lyn.music.core.model.WorkflowSelectionConfig
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate

private val workflowJson = Json {
    ignoreUnknownKeys = false
    isLenient = true
}

private val prettyWorkflowJson = Json {
    ignoreUnknownKeys = false
    isLenient = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

private const val WORKFLOW_KIND = "workflow"
private val TOP_LEVEL_KEYS = setOf("id", "name", "kind", "enabled", "priority", "search", "selection", "lyrics", "optionalFields")
private val SEARCH_KEYS = setOf("method", "url", "queryTemplate", "bodyTemplate", "headersTemplate", "responseFormat", "resultPath", "mapping")
private val SELECTION_KEYS = setOf("titleWeight", "artistWeight", "albumWeight", "durationWeight", "durationToleranceSeconds", "minScore", "maxCandidates")
private val LYRICS_KEYS = setOf("steps")
private val STEP_KEYS = setOf("method", "url", "queryTemplate", "bodyTemplate", "headersTemplate", "responseFormat", "capture", "payloadPath", "fallbackPayloadPath", "format", "transforms")
private val OPTIONAL_FIELDS_KEYS = setOf("coverUrlField")
private val REQUIRED_SEARCH_MAPPING_KEYS = setOf("id", "title", "artists")
private val TRACK_TEMPLATE_VARIABLES = setOf(
    "title",
    "artist",
    "album",
    "duration_ms",
    "duration_seconds",
    "duration",
    "path",
    "track_id",
    "source_id",
)

fun parseWorkflowLyricsSourceConfig(rawJson: String): WorkflowLyricsSourceConfig {
    val root = workflowJson.parseToJsonElement(rawJson.trim()).jsonObjectOrThrow("workflow root")
    ensureAllowedKeys(root, TOP_LEVEL_KEYS, "workflow")
    val kind = root.requiredString("kind", "workflow")
    require(kind == WORKFLOW_KIND) { "workflow.kind 必须为 \"$WORKFLOW_KIND\"。" }
    val search = root.requiredObject("search", "workflow").toWorkflowSearchConfig()
    val selection = root["selection"]?.jsonObjectOrThrow("workflow.selection")?.toWorkflowSelectionConfig() ?: WorkflowSelectionConfig()
    val lyrics = root.requiredObject("lyrics", "workflow").toWorkflowLyricsConfig()
    val optionalFields = root["optionalFields"]?.jsonObjectOrThrow("workflow.optionalFields")?.toWorkflowOptionalFields()
        ?: WorkflowOptionalFields()

    val config = WorkflowLyricsSourceConfig(
        id = root.requiredString("id", "workflow"),
        name = root.requiredString("name", "workflow"),
        enabled = root.booleanOrDefault("enabled", true),
        priority = root.intOrDefault("priority", 0),
        search = search,
        selection = selection,
        lyrics = lyrics,
        optionalFields = optionalFields,
        rawJson = prettyWorkflowJson.encodeToString(JsonElement.serializer(), root),
    )
    validateWorkflowLyricsSourceConfig(config)
    return config
}

fun validateWorkflowLyricsSourceConfig(config: WorkflowLyricsSourceConfig) {
    require(config.id.isNotBlank()) { "workflow.id 不能为空。" }
    require(config.name.isNotBlank()) { "workflow.name 不能为空。" }
    require(config.search.request.url.isNotBlank()) { "workflow.search.url 不能为空。" }
    require(config.search.resultPath.isNotBlank()) { "workflow.search.resultPath 不能为空。" }
    require(config.search.mapping.isNotEmpty()) { "workflow.search.mapping 不能为空。" }
    val missingKeys = REQUIRED_SEARCH_MAPPING_KEYS - config.search.mapping.keys
    require(missingKeys.isEmpty()) { "workflow.search.mapping 缺少字段: ${missingKeys.joinToString(", ")}。" }
    require(config.lyrics.steps.isNotEmpty()) { "workflow.lyrics.steps 至少需要一个步骤。" }
    val lastStep = config.lyrics.steps.last()
    require(lastStep.payloadPath?.isNotBlank() == true || lastStep.fallbackPayloadPath?.isNotBlank() == true) {
        "workflow.lyrics.steps 最后一步必须提供 payloadPath 或 fallbackPayloadPath。"
    }
    validateWorkflowTemplateVariables(config)
}

fun buildWorkflowRequest(
    request: WorkflowRequestConfig,
    variables: Map<String, String>,
): LyricsRequest {
    val url = appendWorkflowQuery(
        interpolateWorkflowTemplate(request.url, variables, encode = true),
        interpolateWorkflowTemplate(request.queryTemplate, variables, encode = true),
    )
    val headers = parseWorkflowHeaders(interpolateWorkflowTemplate(request.headersTemplate, variables, encode = false))
    val body = interpolateWorkflowTemplate(request.bodyTemplate, variables, encode = false).ifBlank { null }
    return LyricsRequest(
        method = request.method,
        url = url,
        headers = headers,
        body = body,
    )
}

fun workflowTrackVariables(track: Track): Map<String, String> {
    val durationSeconds = ((track.durationMs + 500L) / 1_000L).toString()
    return mapOf(
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
}

fun workflowCandidateVariables(candidate: WorkflowSongCandidate): Map<String, String> {
    val artistsJoined = candidate.artists.joinToString(" / ")
    return buildMap {
        put("candidate.id", candidate.id)
        put("candidate.title", candidate.title)
        put("candidate.artists", artistsJoined)
        put("candidate.album", candidate.album.orEmpty())
        put("candidate.durationSeconds", candidate.durationSeconds?.toString().orEmpty())
        put("candidate.imageUrl", candidate.imageUrl.orEmpty())
        candidate.extraFields.forEach { (key, value) ->
            put("candidate.$key", value)
        }
    }
}

fun extractWorkflowSongCandidates(
    config: WorkflowLyricsSourceConfig,
    payload: String,
): List<WorkflowSongCandidate> {
    require(config.search.request.responseFormat == LyricsResponseFormat.JSON) {
        "workflow.search.responseFormat 当前仅支持 JSON。"
    }
    val root = workflowJson.parseToJsonElement(payload)
    val items = extractJsonValues(root, config.search.resultPath)
        .flatMap { value ->
            when (value) {
                is JsonArray -> value.toList()
                else -> listOf(value)
            }
        }
        .mapNotNull { it as? JsonObject }
    if (items.isEmpty()) return emptyList()
    return items.mapNotNull { item ->
        val mappedStrings = config.search.mapping.mapValues { (_, path) ->
            extractJsonStrings(item, path)
        }
        val id = mappedStrings["id"].orEmpty().firstOrNull().orEmpty().trim()
        val title = mappedStrings["title"].orEmpty().firstOrNull().orEmpty().trim()
        val artists = mappedStrings["artists"].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        if (id.isBlank() || title.isBlank() || artists.isEmpty()) return@mapNotNull null
        val album = mappedStrings["album"].orEmpty().firstOrNull()?.trim().takeIf { !it.isNullOrBlank() }
        val durationSeconds = mappedStrings["durationSeconds"].orEmpty().firstOrNull()?.trim()?.toIntOrNull()
        val imageUrlField = config.optionalFields.coverUrlField
        val imageUrl = imageUrlField
            ?.let { mappedStrings[it].orEmpty().firstOrNull()?.trim() }
            ?.takeIf { !it.isNullOrBlank() }
        val coreFields = setOf("id", "title", "artists", "album", "durationSeconds")
        val extraFields = mappedStrings
            .filterKeys { it !in coreFields }
            .mapValues { (_, values) -> values.joinToString(" / ").trim() }
            .filterValues { it.isNotBlank() }
        WorkflowSongCandidate(
            sourceId = config.id,
            sourceName = config.name,
            id = id,
            title = title,
            artists = artists,
            album = album,
            durationSeconds = durationSeconds,
            imageUrl = imageUrl,
            extraFields = extraFields,
        )
    }
}

fun extractWorkflowStepCapture(
    step: WorkflowLyricsStepConfig,
    payload: String,
): Map<String, String> {
    if (step.capture.isEmpty()) return emptyMap()
    require(step.request.responseFormat == LyricsResponseFormat.JSON) {
        "workflow.lyrics.steps.capture 当前仅支持 JSON 响应。"
    }
    val root = workflowJson.parseToJsonElement(payload)
    return step.capture.mapValues { (_, path) ->
        extractJsonStrings(root, path).joinToString(" / ").trim()
    }.filterValues { it.isNotBlank() }
}

fun extractWorkflowLyricsPayload(
    step: WorkflowLyricsStepConfig,
    payload: String,
): String? {
    val extracted = when (step.request.responseFormat) {
        LyricsResponseFormat.JSON -> {
            val root = workflowJson.parseToJsonElement(payload)
            extractJsonStrings(root, step.payloadPath.orEmpty()).firstOrNull()
                ?: extractJsonStrings(root, step.fallbackPayloadPath.orEmpty()).firstOrNull()
        }

        LyricsResponseFormat.XML -> {
            extractXmlPath(payload, step.payloadPath.orEmpty()) ?: extractXmlPath(payload, step.fallbackPayloadPath.orEmpty())
        }

        LyricsResponseFormat.LRC,
        LyricsResponseFormat.TEXT,
            -> payload
    } ?: return null
    return applyWorkflowTransforms(extracted, step.transforms)
        .takeIf { it.isNotBlank() }
}

fun scoreWorkflowSongCandidate(
    track: Track,
    candidate: WorkflowSongCandidate,
    selection: WorkflowSelectionConfig,
): Double {
    val titleScore = normalizedTextSimilarity(track.title, candidate.title)
    val artistScore = normalizedTextSimilarity(track.artistName.orEmpty(), candidate.artists.joinToString(" / "))
    val albumScore = normalizedTextSimilarity(track.albumTitle.orEmpty(), candidate.album.orEmpty())
    val durationScore = normalizedDurationSimilarity(
        expectedSeconds = ((track.durationMs + 500L) / 1_000L).toInt(),
        candidateSeconds = candidate.durationSeconds,
        toleranceSeconds = selection.durationToleranceSeconds,
    )
    return titleScore * selection.titleWeight +
        artistScore * selection.artistWeight +
        albumScore * selection.albumWeight +
        durationScore * selection.durationWeight
}

fun selectBestWorkflowSongCandidate(
    track: Track,
    candidates: List<WorkflowSongCandidate>,
    selection: WorkflowSelectionConfig,
): WorkflowSongCandidate? {
    return candidates
        .take(selection.maxCandidates.coerceAtLeast(1))
        .map { it to scoreWorkflowSongCandidate(track, it, selection) }
        .filter { (_, score) -> score >= selection.minScore }
        .maxByOrNull { it.second }
        ?.first
}

private fun JsonObject.toWorkflowSearchConfig(): WorkflowSearchConfig {
    ensureAllowedKeys(this, SEARCH_KEYS, "workflow.search")
    return WorkflowSearchConfig(
        request = toWorkflowRequestConfig("workflow.search"),
        resultPath = requiredString("resultPath", "workflow.search"),
        mapping = requiredStringMap("mapping", "workflow.search"),
    )
}

private fun JsonObject.toWorkflowSelectionConfig(): WorkflowSelectionConfig {
    ensureAllowedKeys(this, SELECTION_KEYS, "workflow.selection")
    return WorkflowSelectionConfig(
        titleWeight = doubleOrDefault("titleWeight", 0.7),
        artistWeight = doubleOrDefault("artistWeight", 0.2),
        albumWeight = doubleOrDefault("albumWeight", 0.05),
        durationWeight = doubleOrDefault("durationWeight", 0.05),
        durationToleranceSeconds = intOrDefault("durationToleranceSeconds", 3),
        minScore = doubleOrDefault("minScore", 0.4),
        maxCandidates = intOrDefault("maxCandidates", 10),
    )
}

private fun JsonObject.toWorkflowLyricsConfig(): WorkflowLyricsConfig {
    ensureAllowedKeys(this, LYRICS_KEYS, "workflow.lyrics")
    val steps = requiredArray("steps", "workflow.lyrics")
        .mapIndexed { index, item ->
            item.jsonObjectOrThrow("workflow.lyrics.steps[$index]").toWorkflowLyricsStepConfig(index)
        }
    return WorkflowLyricsConfig(steps = steps)
}

private fun JsonObject.toWorkflowLyricsStepConfig(index: Int): WorkflowLyricsStepConfig {
    ensureAllowedKeys(this, STEP_KEYS, "workflow.lyrics.steps[$index]")
    return WorkflowLyricsStepConfig(
        request = toWorkflowRequestConfig("workflow.lyrics.steps[$index]"),
        capture = stringMapOrEmpty("capture", "workflow.lyrics.steps[$index]"),
        payloadPath = stringOrNull("payloadPath"),
        fallbackPayloadPath = stringOrNull("fallbackPayloadPath"),
        format = enumOrDefault("format", LyricsResponseFormat.LRC),
        transforms = enumListOrEmpty("transforms"),
    )
}

private fun JsonObject.toWorkflowOptionalFields(): WorkflowOptionalFields {
    ensureAllowedKeys(this, OPTIONAL_FIELDS_KEYS, "workflow.optionalFields")
    return WorkflowOptionalFields(
        coverUrlField = stringOrNull("coverUrlField"),
    )
}

private fun JsonObject.toWorkflowRequestConfig(context: String): WorkflowRequestConfig {
    return WorkflowRequestConfig(
        method = enumOrDefault("method", RequestMethod.GET),
        url = requiredString("url", context),
        queryTemplate = stringOrDefault("queryTemplate", ""),
        bodyTemplate = stringOrDefault("bodyTemplate", ""),
        headersTemplate = stringOrDefault("headersTemplate", ""),
        responseFormat = enumOrDefault("responseFormat", LyricsResponseFormat.JSON),
    )
}

private fun validateWorkflowTemplateVariables(config: WorkflowLyricsSourceConfig) {
    validatePlaceholders(
        context = "workflow.search",
        templates = listOf(
            config.search.request.url,
            config.search.request.queryTemplate,
            config.search.request.bodyTemplate,
            config.search.request.headersTemplate,
        ),
        allowedVariables = TRACK_TEMPLATE_VARIABLES,
    )

    val candidateKeys = buildSet {
        addAll(config.search.mapping.keys.map { "candidate.$it" })
        add("candidate.imageUrl")
    }
    val stepCaptureKeys = mutableSetOf<String>()
    config.lyrics.steps.forEachIndexed { index, step ->
        validatePlaceholders(
            context = "workflow.lyrics.steps[$index]",
            templates = listOf(
                step.request.url,
                step.request.queryTemplate,
                step.request.bodyTemplate,
                step.request.headersTemplate,
            ),
            allowedVariables = TRACK_TEMPLATE_VARIABLES + candidateKeys + stepCaptureKeys,
        )
        step.capture.keys.forEach { key ->
            stepCaptureKeys += "step$index.$key"
        }
    }
}

private fun validatePlaceholders(
    context: String,
    templates: List<String>,
    allowedVariables: Set<String>,
) {
    val unknownVariables = templates
        .flatMap(::extractTemplateVariables)
        .distinct()
        .filter { it !in allowedVariables }
    require(unknownVariables.isEmpty()) {
        "$context 使用了未定义模板变量: ${unknownVariables.joinToString(", ")}。"
    }
}

private fun extractTemplateVariables(template: String): List<String> {
    return Regex("""\{([^}]+)}""")
        .findAll(template)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .toList()
}

private fun appendWorkflowQuery(url: String, query: String): String {
    if (query.isBlank()) return url
    val separator = if (url.contains('?')) "&" else "?"
    return "$url$separator$query"
}

private fun parseWorkflowHeaders(headersTemplate: String): Map<String, String> {
    return headersTemplate.lineSequence()
        .map { it.trim() }
        .filter { it.contains(':') }
        .associate { line ->
            val parts = line.split(':', limit = 2)
            parts[0].trim() to parts[1].trim()
        }
}

private fun interpolateWorkflowTemplate(
    template: String,
    variables: Map<String, String>,
    encode: Boolean,
): String {
    if (template.isBlank()) return ""
    return variables.entries.fold(template) { acc, entry ->
        val replacement = if (encode) entry.value.encodeURLParameter() else entry.value
        acc.replace("{${entry.key}}", replacement)
    }
}

private fun applyWorkflowTransforms(
    payload: String,
    transforms: List<WorkflowLyricsTransform>,
): String {
    return transforms.fold(payload) { acc, transform ->
        when (transform) {
            WorkflowLyricsTransform.BASE64_DECODE -> runCatching {
                Base64.decode(acc).decodeToString()
            }.getOrElse { acc }

            WorkflowLyricsTransform.JSON_UNESCAPE -> runCatching {
                (workflowJson.parseToJsonElement("\"${acc.escapeJsonString()}\"") as? JsonPrimitive)
                    ?.contentOrNull
                    ?: acc
            }.getOrElse { acc }

            WorkflowLyricsTransform.TRIM -> acc.trim()

            WorkflowLyricsTransform.JOIN_ARRAY_WITH_DELIMITER -> runCatching {
                workflowJson.parseToJsonElement(acc)
                    .let { it as? JsonArray }
                    ?.mapNotNull { item -> (item as? JsonPrimitive)?.contentOrNull?.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.joinToString(" / ")
                    ?: acc
            }.getOrElse { acc }
        }
    }
}

private fun normalizedTextSimilarity(expected: String, actual: String): Double {
    val left = normalizeComparableText(expected)
    val right = normalizeComparableText(actual)
    if (left.isBlank() || right.isBlank()) return 0.0
    if (left == right) return 1.0
    if (left.contains(right) || right.contains(left)) {
        val minLength = minOf(left.length, right.length).toDouble()
        val maxLength = maxOf(left.length, right.length).toDouble()
        return minLength / maxLength
    }
    val distance = levenshteinDistance(left, right)
    val maxLength = maxOf(left.length, right.length).coerceAtLeast(1)
    return (1.0 - distance.toDouble() / maxLength.toDouble()).coerceIn(0.0, 1.0)
}

private fun normalizedDurationSimilarity(
    expectedSeconds: Int,
    candidateSeconds: Int?,
    toleranceSeconds: Int,
): Double {
    if (expectedSeconds <= 0 || candidateSeconds == null || candidateSeconds <= 0) return 0.0
    val delta = kotlin.math.abs(expectedSeconds - candidateSeconds)
    if (delta <= toleranceSeconds) return 1.0
    val window = (toleranceSeconds * 4).coerceAtLeast(1)
    return (1.0 - delta.toDouble() / window.toDouble()).coerceIn(0.0, 1.0)
}

private fun normalizeComparableText(value: String): String {
    return value
        .lowercase()
        .replace(Regex("""[\s\p{Punct}（）()【】\[\]·•]+"""), "")
}

private fun levenshteinDistance(left: String, right: String): Int {
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    val previous = IntArray(right.length + 1) { it }
    val current = IntArray(right.length + 1)
    left.forEachIndexed { i, leftChar ->
        current[0] = i + 1
        right.forEachIndexed { j, rightChar ->
            val cost = if (leftChar == rightChar) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost,
            )
        }
        current.copyInto(previous)
    }
    return previous[right.length]
}

private fun extractJsonStrings(root: JsonElement, path: String): List<String> {
    if (path.isBlank()) {
        return jsonElementToStrings(root)
    }
    return extractJsonValues(root, path)
        .flatMap(::jsonElementToStrings)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun extractJsonValues(root: JsonElement, path: String): List<JsonElement> {
    if (path.isBlank()) return listOf(root)
    val segments = normalizeJsonPath(path)
        .split('.')
        .filter { it.isNotBlank() }
    return extractJsonValues(root, segments)
}

private fun extractJsonValues(
    element: JsonElement,
    segments: List<String>,
): List<JsonElement> {
    if (segments.isEmpty()) return listOf(element)
    val head = segments.first()
    val tail = segments.drop(1)
    return when (element) {
        is JsonObject -> element[head]?.let { extractJsonValues(it, tail) }.orEmpty()
        is JsonArray -> {
            head.toIntOrNull()?.let { index ->
                element.getOrNull(index)?.let { extractJsonValues(it, tail) }.orEmpty()
            } ?: element.flatMap { item -> extractJsonValues(item, segments) }
        }

        else -> emptyList()
    }
}

private fun jsonElementToStrings(element: JsonElement): List<String> {
    return when (element) {
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonArray -> element.flatMap(::jsonElementToStrings)
        is JsonObject -> emptyList()
    }
}

private fun normalizeJsonPath(path: String): String {
    return Regex("""\[(\d+)]""")
        .replace(path.trim()) { match -> ".${match.groupValues[1]}" }
        .removePrefix(".")
}

private fun extractXmlPath(xml: String, path: String): String? {
    if (path.isBlank()) return null
    return path
        .split('.')
        .filter { it.isNotBlank() }
        .fold(xml as String?) { current, tag ->
            current?.let { extractXmlTagValue(it, tag) }
        }
}

private fun extractXmlTagValue(xml: String, tag: String): String? {
    val regex = Regex(
        "<$tag(?:\\s+[^>]*)?>(.*?)</$tag>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    return regex.find(xml)?.groupValues?.getOrNull(1)
}

private fun JsonElement.jsonObjectOrThrow(context: String): JsonObject {
    return this as? JsonObject ?: error("$context 必须是对象。")
}

private fun JsonObject.requiredObject(key: String, context: String): JsonObject {
    return getValue(key).jsonObjectOrThrow("$context.$key")
}

private fun JsonObject.requiredArray(key: String, context: String): JsonArray {
    return getValue(key) as? JsonArray ?: error("$context.$key 必须是数组。")
}

private fun JsonObject.requiredString(key: String, context: String): String {
    return stringOrNull(key)?.takeIf { it.isNotBlank() } ?: error("$context.$key 不能为空。")
}

private fun JsonObject.stringOrNull(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull
}

private fun JsonObject.stringOrDefault(key: String, default: String): String {
    return stringOrNull(key) ?: default
}

private inline fun <reified T : Enum<T>> JsonObject.enumOrDefault(key: String, default: T): T {
    val raw = stringOrNull(key) ?: return default
    return enumValues<T>().firstOrNull { enumMatchesAlias(it.name, raw) }
        ?: error("字段 $key 的值 \"$raw\" 不合法。")
}

private inline fun <reified T : Enum<T>> JsonObject.enumListOrEmpty(key: String): List<T> {
    val array = this[key] as? JsonArray ?: return emptyList()
    return array.map { item ->
        val raw = (item as? JsonPrimitive)?.contentOrNull ?: error("$key 中存在非字符串值。")
        enumValues<T>().firstOrNull { enumMatchesAlias(it.name, raw) }
            ?: error("字段 $key 中的值 \"$raw\" 不合法。")
    }
}

private fun JsonObject.booleanOrDefault(key: String, default: Boolean): Boolean {
    return (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: default
}

private fun JsonObject.intOrDefault(key: String, default: Int): Int {
    return (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: default
}

private fun JsonObject.doubleOrDefault(key: String, default: Double): Double {
    return (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: default
}

private fun JsonObject.requiredStringMap(key: String, context: String): Map<String, String> {
    return stringMapOrEmpty(key, context).takeIf { it.isNotEmpty() } ?: error("$context.$key 不能为空。")
}

private fun JsonObject.stringMapOrEmpty(key: String, context: String): Map<String, String> {
    val obj = this[key] as? JsonObject ?: return emptyMap()
    return obj.mapValues { (field, value) ->
        (value as? JsonPrimitive)?.contentOrNull ?: error("$context.$key.$field 必须是字符串。")
    }
}

private fun ensureAllowedKeys(
    jsonObject: JsonObject,
    allowedKeys: Set<String>,
    context: String,
) {
    val unexpected = jsonObject.keys - allowedKeys
    require(unexpected.isEmpty()) {
        "$context 包含未知字段: ${unexpected.joinToString(", ")}。"
    }
}

private fun enumMatchesAlias(enumName: String, raw: String): Boolean {
    return normalizeEnumAlias(enumName) == normalizeEnumAlias(raw)
}

private fun normalizeEnumAlias(value: String): String {
    return value
        .trim()
        .lowercase()
        .replace(Regex("""[^a-z0-9]+"""), "")
}

private fun String.escapeJsonString(): String {
    return buildString(length + 8) {
        for (char in this@escapeJsonString) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
