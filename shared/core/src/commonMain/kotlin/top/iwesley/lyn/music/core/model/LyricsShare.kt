package top.iwesley.lyn.music.core.model

data class LyricsShareCardModel(
    val title: String,
    val artistName: String? = null,
    val artworkLocator: String? = null,
    val lyricsLines: List<String>,
)

data class LyricsShareSaveResult(
    val message: String,
)

interface LyricsSharePlatformService {
    suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray>
    suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult>
    suspend fun copyImage(pngBytes: ByteArray): Result<Unit>
}

object UnsupportedLyricsSharePlatformService : LyricsSharePlatformService {
    private val error = IllegalStateException("当前平台暂不支持歌词分享图片。")

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = Result.failure(error)

    override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> = Result.failure(error)

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> = Result.failure(error)
}

object LyricsShareCardSpec {
    const val BRAND_TEXT: String = "Via LynMusic"
    const val IMAGE_WIDTH_PX: Int = 1080
    const val IMAGE_MIN_HEIGHT_PX: Int = 1280
    const val IMAGE_MAX_HEIGHT_PX: Int = 1960
    const val OUTER_PADDING_PX: Int = 56
    const val PAPER_RADIUS_PX: Int = 54
    const val SHADOW_OFFSET_PX: Int = 14
    const val PAPER_PADDING_HORIZONTAL_PX: Int = 84
    const val PAPER_PADDING_TOP_PX: Int = 104
    const val PAPER_PADDING_BOTTOM_PX: Int = 88
    const val ARTWORK_SIZE_PX: Int = 188
    const val HEADER_GAP_PX: Int = 42
    const val LYRICS_TOP_GAP_PX: Int = 52
    const val FOOTER_TOP_GAP_PX: Int = 60
    const val BRAND_TOP_GAP_PX: Int = 56
    const val TAPE_WIDTH_PX: Int = 188
    const val TAPE_HEIGHT_PX: Int = 42
    const val LYRICS_FONT_SIZE_PX: Float = 60f
    const val META_FONT_SIZE_PX: Float = 36f
    const val TITLE_FONT_SIZE_PX: Float = 58f
    const val BRAND_FONT_SIZE_PX: Float = 30f
    const val PAPER_BACKGROUND_ARGB: Int = 0xFFF7EEDC.toInt()
    const val CANVAS_BACKGROUND_ARGB: Int = 0xFFF0E5D5.toInt()
    const val PAPER_SHADOW_ARGB: Int = 0x1F000000
    const val TAPE_ARGB: Int = 0x9FFFF6D8.toInt()
    const val TEXT_PRIMARY_ARGB: Int = 0xFF3C2E24.toInt()
    const val TEXT_SECONDARY_ARGB: Int = 0xA35A493D.toInt()
    const val PLACEHOLDER_ARGB: Int = 0xFFE3D1BC.toInt()

    fun estimateImageHeightPx(lyricsLines: List<String>): Int {
        val estimatedRows = lyricsLines
            .map { line ->
                val length = line.trim().length.coerceAtLeast(1)
                (length + CHARS_PER_ROW_ESTIMATE - 1) / CHARS_PER_ROW_ESTIMATE
            }
            .sum()
            .coerceAtLeast(1)
        val contentHeight =
            PAPER_PADDING_TOP_PX +
                ARTWORK_SIZE_PX +
                LYRICS_TOP_GAP_PX +
                estimatedRows * LYRICS_ROW_HEIGHT_PX +
                FOOTER_TOP_GAP_PX +
                FOOTER_HEIGHT_PX +
                BRAND_TOP_GAP_PX +
                BRAND_HEIGHT_PX +
                PAPER_PADDING_BOTTOM_PX
        return contentHeight.coerceIn(IMAGE_MIN_HEIGHT_PX, IMAGE_MAX_HEIGHT_PX)
    }

    private const val CHARS_PER_ROW_ESTIMATE: Int = 12
    private const val LYRICS_ROW_HEIGHT_PX: Int = 78
    private const val FOOTER_HEIGHT_PX: Int = 138
    private const val BRAND_HEIGHT_PX: Int = 40
}

fun buildLyricsShareSuggestedName(title: String): String {
    val normalized = title.trim()
        .replace(ILLEGAL_FILE_NAME_CHARS, "_")
        .trim('_', ' ')
        .ifBlank { "lynmusic" }
    return "$normalized-lyrics-share.png"
}

private val ILLEGAL_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|]+""")
