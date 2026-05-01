package top.iwesley.lyn.music.core.model

data class ArtworkSquareCropRect(
    val left: Int,
    val top: Int,
    val size: Int,
) {
    val right: Int
        get() = left + size

    val bottom: Int
        get() = top + size
}

fun resolveArtworkSquareCropRect(
    sourceWidth: Int,
    sourceHeight: Int,
): ArtworkSquareCropRect? {
    if (sourceWidth <= 0 || sourceHeight <= 0) return null
    val cropSize = minOf(sourceWidth, sourceHeight)
    return ArtworkSquareCropRect(
        left = (sourceWidth - cropSize) / 2,
        top = (sourceHeight - cropSize) / 2,
        size = cropSize,
    )
}

fun resolveArtworkDecodeSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetSize: Int,
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetSize <= 0) return 1
    val minDimension = minOf(sourceWidth, sourceHeight)
    var sampleSize = 1
    while (
        sampleSize <= Int.MAX_VALUE / 2 &&
        minDimension / (sampleSize * 2) >= targetSize
    ) {
        sampleSize *= 2
    }
    return sampleSize
}
