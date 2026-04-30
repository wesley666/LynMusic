package top.iwesley.lyn.music.domain

import kotlin.math.abs
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSelectionConfig

internal val DEFAULT_DIRECT_LYRICS_SELECTION = WorkflowSelectionConfig()
internal const val AUTO_DIRECT_LYRICS_SYNCED_BONUS = 0.03

internal data class ScoredDirectLyricsCandidate(
    val candidate: ParsedLyricsPayload,
    val score: Double,
    val originalIndex: Int,
)

internal fun scoreDirectLyricsCandidate(
    track: Track,
    candidate: ParsedLyricsPayload,
    selection: WorkflowSelectionConfig = DEFAULT_DIRECT_LYRICS_SELECTION,
    syncedBonus: Double = 0.0,
): Double {
    val titleScore = normalizedTextSimilarity(track.title, candidate.title.orEmpty())
    val artistScore = normalizedTextSimilarity(track.artistName.orEmpty(), candidate.artistName.orEmpty())
    val albumScore = normalizedTextSimilarity(track.albumTitle.orEmpty(), candidate.albumTitle.orEmpty())
    val durationScore = normalizedDurationSimilarity(
        expectedSeconds = ((track.durationMs + 500L) / 1_000L).toInt(),
        candidateSeconds = candidate.durationSeconds,
        toleranceSeconds = selection.durationToleranceSeconds,
    )
    val metadataScore = titleScore * selection.titleWeight +
        artistScore * selection.artistWeight +
        albumScore * selection.albumWeight +
        durationScore * selection.durationWeight
    return metadataScore + if (candidate.document.isSynced) syncedBonus else 0.0
}

internal fun rankDirectLyricsCandidates(
    track: Track,
    candidates: List<ParsedLyricsPayload>,
    selection: WorkflowSelectionConfig = DEFAULT_DIRECT_LYRICS_SELECTION,
    syncedBonus: Double = 0.0,
): List<ScoredDirectLyricsCandidate> {
    return candidates
        .mapIndexed { index, candidate ->
            ScoredDirectLyricsCandidate(
                candidate = candidate,
                score = scoreDirectLyricsCandidate(track, candidate, selection, syncedBonus),
                originalIndex = index,
            )
        }
        .sortedWith(compareByDescending<ScoredDirectLyricsCandidate> { it.score }.thenBy { it.originalIndex })
}

internal fun normalizedTextSimilarity(expected: String, actual: String): Double {
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

internal fun normalizedDurationSimilarity(
    expectedSeconds: Int,
    candidateSeconds: Int?,
    toleranceSeconds: Int,
): Double {
    if (expectedSeconds <= 0 || candidateSeconds == null || candidateSeconds <= 0) return 0.0
    val delta = abs(expectedSeconds - candidateSeconds)
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
