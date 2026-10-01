package top.iwesley.lyn.music.feature.offline

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.plus

import kotlin.math.roundToInt
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.offlineDownloadSourceType

const val OFFLINE_DOWNLOAD_SPACE_RESERVE_BYTES: Long = 1024L * 1024L * 1024L

data class BatchDownloadSizeEstimate(
    val totalBytes: Long = 0L,
    val unknownCount: Int = 0,
    val skippedCount: Int = 0,
    val approximate: Boolean = false,
)

fun estimateBatchDownloadSize(
    tracks: List<Track>,
    downloadsByTrackId: Map<String, OfflineDownload>,
    quality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
): BatchDownloadSizeEstimate {
    var totalBytes = 0L
    var unknownCount = 0
    var skippedCount = 0
    var approximate = false
    tracks.distinctBy { it.id }.forEach { track ->
        val sourceType = offlineDownloadSourceType(track)
        val download = downloadsByTrackId[track.id]
        if (shouldSkipBatchDownloadEstimate(track, download, sourceType, quality)) {
            skippedCount += 1
            return@forEach
        }
        val sizeBytes = when {
            sourceType.supportsSubsonicAudioQuality() && quality != NavidromeAudioQuality.Original -> {
                approximate = true
                estimatedNavidromeTranscodedSizeBytes(track, quality)
            }

            else -> track.sizeBytes.takeIf { it > 0L }
        }
        if (sizeBytes == null) {
            unknownCount += 1
        } else {
            totalBytes = safeAdd(totalBytes, sizeBytes)
        }
    }
    return BatchDownloadSizeEstimate(
        totalBytes = totalBytes,
        unknownCount = unknownCount,
        skippedCount = skippedCount,
        approximate = approximate,
    )
}

fun batchDownloadSizeEstimateLabel(estimate: BatchDownloadSizeEstimate): UiText {
    val sizeLabel = estimate.totalBytes
        .takeIf { it > 0L }
        ?.let(::formatOfflineDownloadSizeLabel)
    return when {
        sizeLabel == null && estimate.unknownCount <= 0 -> uiText(Res.string.offline_nothing_to_download)
        sizeLabel == null && estimate.unknownCount == 1 -> uiText(Res.string.common_unknown)
        sizeLabel == null -> uiPlural(Res.plurals.offline_unknown_size_track_count, (estimate.unknownCount).toInt(), estimate.unknownCount)
        estimate.unknownCount > 0 -> {
            val prefix = if (estimate.approximate) uiText(Res.string.common_about) else ""
            uiPlural(Res.plurals.offline_size_estimate_with_unknown_tracks, estimate.unknownCount, prefix, sizeLabel, estimate.unknownCount)
        }

        estimate.approximate -> uiText(Res.string.about_named_app, sizeLabel)
        else -> UiText.Raw(sizeLabel)
    }
}

fun batchDownloadInsufficientSpaceMessage(
    estimate: BatchDownloadSizeEstimate,
    availableSpaceBytes: Long?,
    reserveBytes: Long = OFFLINE_DOWNLOAD_SPACE_RESERVE_BYTES,
): UiText? {
    if (availableSpaceBytes == null) return null
    if (estimate.totalBytes <= 0L && estimate.unknownCount <= 0) return null
    val requiredBytes = safeAdd(estimate.totalBytes, reserveBytes.coerceAtLeast(0L))
    if (availableSpaceBytes >= requiredBytes) return null
    return uiText(Res.string.offline_storage_insufficient_size, batchDownloadSizeEstimateLabel(estimate)) +
        uiText(Res.string.offline_storage_reserve_detail, formatOfflineDownloadSizeLabel(reserveBytes)) +
        uiText(Res.string.offline_available_space_detail, formatOfflineDownloadSizeLabel(availableSpaceBytes))
}

fun estimatedNavidromeTranscodedSizeBytes(
    track: Track,
    quality: NavidromeAudioQuality,
): Long? {
    val maxBitRateKbps = quality.maxBitRateKbps?.takeIf { it > 0 } ?: return null
    val durationMs = track.durationMs.takeIf { it > 0L } ?: return null
    return maxBitRateKbps.toLong() * durationMs * 125L / 1_000L
}

fun formatOfflineDownloadSizeLabel(sizeBytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = sizeBytes.toDouble().coerceAtLeast(0.0)
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${value.roundToInt()} ${units[unitIndex]}"
    } else {
        "${(value * 10).roundToInt() / 10.0} ${units[unitIndex]}"
    }
}

private fun shouldSkipBatchDownloadEstimate(
    track: Track,
    download: OfflineDownload?,
    sourceType: ImportSourceType?,
    quality: NavidromeAudioQuality,
): Boolean {
    if (sourceType == null || sourceType == ImportSourceType.LOCAL_FOLDER) return true
    if (download?.status != OfflineDownloadStatus.Completed || !download.hasLocalFileReference) {
        return false
    }
    return !sourceType.supportsSubsonicAudioQuality() || download.quality == quality
}

private fun safeAdd(left: Long, right: Long): Long {
    if (right <= 0L) return left
    return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}

private fun ImportSourceType?.supportsSubsonicAudioQuality(): Boolean {
    return this == ImportSourceType.NAVIDROME || this == ImportSourceType.SUBSONIC
}
