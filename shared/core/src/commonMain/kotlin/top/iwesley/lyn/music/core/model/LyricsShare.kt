package top.iwesley.lyn.music.core.model

enum class LyricsShareTemplate {
    NOTE,
    ARTWORK_TINT,
}

enum class LyricsShareFontKind {
    SYSTEM,
    IMPORTED,
}

data class LyricsShareFontOption(
    val fontKey: String,
    val displayName: String,
    val previewText: String = displayName,
    val isPrioritized: Boolean = false,
    val kind: LyricsShareFontKind = LyricsShareFontKind.SYSTEM,
    val fontFilePath: String? = null,
)

data class LyricsShareCardModel(
    val title: String,
    val artistName: String? = null,
    val artworkLocator: String? = null,
    val template: LyricsShareTemplate = LyricsShareTemplate.NOTE,
    val artworkTintTheme: ArtworkTintTheme? = null,
    val artworkBackgroundPalette: PlaybackArtworkBackgroundPalette? = null,
    val lyricsLines: List<String>,
    val fontKey: String? = null,
)

data class LyricsShareSaveResult(
    val message: String,
)

interface LyricsSharePlatformService {
    suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray>
    suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult>
    suspend fun copyImage(pngBytes: ByteArray): Result<Unit>
    suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>>
}

interface LyricsShareFontLibraryPlatformService {
    suspend fun listImportedFonts(): Result<List<LyricsShareFontOption>>
    suspend fun importFont(): Result<LyricsShareFontOption?>
    suspend fun deleteImportedFont(fontKey: String): Result<Unit>
    suspend fun resolveImportedFontPath(fontKey: String): Result<String?>
}

object UnsupportedLyricsSharePlatformService : LyricsSharePlatformService {
    private val error = IllegalStateException("当前平台暂不支持歌词分享图片。")
    private val fontError = IllegalStateException("当前平台暂不支持读取系统字体。")

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = Result.failure(error)

    override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> = Result.failure(error)

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> = Result.failure(error)

    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> = Result.failure(fontError)
}

object UnsupportedLyricsShareFontLibraryPlatformService : LyricsShareFontLibraryPlatformService {
    private val error = IllegalStateException("当前平台暂不支持歌词分享字体导入。")

    override suspend fun listImportedFonts(): Result<List<LyricsShareFontOption>> = Result.success(emptyList())

    override suspend fun importFont(): Result<LyricsShareFontOption?> = Result.failure(error)

    override suspend fun deleteImportedFont(fontKey: String): Result<Unit> = Result.failure(error)

    override suspend fun resolveImportedFontPath(fontKey: String): Result<String?> = Result.success(null)
}

const val DEFAULT_LYRICS_SHARE_FONT_KEY: String = "Serif"
const val DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT: String = "你好 Hello"
const val LYRICS_SHARE_IMPORTED_FONT_KEY_PREFIX: String = "imported:"

fun buildLyricsShareImportedFontKey(contentHash: String): String {
    return "$LYRICS_SHARE_IMPORTED_FONT_KEY_PREFIX${contentHash.trim().lowercase()}"
}

fun parseLyricsShareImportedFontHash(fontKey: String): String? {
    return fontKey
        .trim()
        .removePrefix(LYRICS_SHARE_IMPORTED_FONT_KEY_PREFIX)
        .takeIf { fontKey.startsWith(LYRICS_SHARE_IMPORTED_FONT_KEY_PREFIX) && it.isNotBlank() }
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
    const val LYRICS_PREVIEW_LINE_GAP_DP: Int = 12
    const val LYRICS_JVM_LINE_GAP_PX: Int = 50
    const val LYRICS_ANDROID_LINE_GAP_PX: Float = 40f
    const val LYRICS_IOS_LINE_GAP_PX: Float = 50f
    const val LYRICS_MIN_FONT_SCALE: Float = 0.4f
    const val LYRICS_FONT_SHRINK_STEP: Float = 0.92f
    const val META_FONT_SIZE_PX: Float = 36f
    const val TITLE_FONT_SIZE_PX: Float = 58f
    const val BRAND_FONT_SIZE_PX: Float = 30f
    const val PAPER_BACKGROUND_ARGB: Int = 0xFFF7EEDC.toInt()
    const val CANVAS_BACKGROUND_ARGB: Int = 0xFFF0E5D5.toInt()
    const val PAPER_SHADOW_ARGB: Int = 0x1F000000
    const val TAPE_ARGB: Int = 0x9FFFF6D8.toInt()
    const val TEXT_PRIMARY_ARGB: Int = 0xFF3C2E24.toInt()
    const val TEXT_FOOTER_ARGB: Int = 0xD93C2E24.toInt()
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

object LyricsShareArtworkTintSpec {
    const val IMAGE_WIDTH_PX: Int = 1080
    const val IMAGE_MIN_HEIGHT_PX: Int = 1280
    const val IMAGE_MAX_HEIGHT_PX: Int = 1960
    const val OUTER_PADDING_PX: Int = 86
    const val ARTWORK_SIZE_PX: Int = 196
    const val ARTWORK_RADIUS_PX: Int = 42
    const val ARTWORK_TOP_GAP_PX: Int = 82
    const val LYRICS_TOP_GAP_PX: Int = 74
    const val FOOTER_TOP_GAP_PX: Int = 72
    const val BRAND_TOP_GAP_PX: Int = 52
    const val LYRICS_FONT_SIZE_PX: Float = 64f
    const val LYRICS_PREVIEW_LINE_GAP_DP: Int = 14
    const val LYRICS_JVM_LINE_GAP_PX: Int = 50
    const val LYRICS_ANDROID_LINE_GAP_PX: Float = 40f
    const val LYRICS_IOS_LINE_GAP_PX: Float = 50f
    const val LYRICS_MIN_FONT_SCALE: Float = 0.4f
    const val LYRICS_FONT_SHRINK_STEP: Float = 0.92f
    const val TITLE_FONT_SIZE_PX: Float = 54f
    const val META_FONT_SIZE_PX: Float = 34f
    const val BRAND_FONT_SIZE_PX: Float = 30f
    const val DEFAULT_BACKGROUND_ARGB: Int = 0xFF232325.toInt()
    const val TEXT_PRIMARY_ARGB: Int = 0xFFFFFFFF.toInt()
    const val TEXT_FOOTER_ARGB: Int = 0xD9FFFFFF.toInt()
    const val TEXT_SECONDARY_ARGB: Int = 0x99FFFFFF.toInt()
    const val PLACEHOLDER_ARGB: Int = 0x22FFFFFF
    const val ARTWORK_SHADOW_ARGB: Int = 0x33000000

    fun estimateImageHeightPx(lyricsLines: List<String>): Int {
        val estimatedRows = lyricsLines
            .map { line ->
                val length = line.trim().length.coerceAtLeast(1)
                (length + CHARS_PER_ROW_ESTIMATE - 1) / CHARS_PER_ROW_ESTIMATE
            }
            .sum()
            .coerceAtLeast(1)
        val contentHeight =
            OUTER_PADDING_PX +
                ARTWORK_TOP_GAP_PX +
                ARTWORK_SIZE_PX +
                LYRICS_TOP_GAP_PX +
                estimatedRows * LYRICS_ROW_HEIGHT_PX +
                FOOTER_TOP_GAP_PX +
                FOOTER_HEIGHT_PX +
                BRAND_TOP_GAP_PX +
                BRAND_HEIGHT_PX +
                OUTER_PADDING_PX
        return contentHeight.coerceIn(IMAGE_MIN_HEIGHT_PX, IMAGE_MAX_HEIGHT_PX)
    }

    private const val CHARS_PER_ROW_ESTIMATE: Int = 12
    private const val LYRICS_ROW_HEIGHT_PX: Int = 82
    private const val FOOTER_HEIGHT_PX: Int = 128
    private const val BRAND_HEIGHT_PX: Int = 38
}

fun buildLyricsShareSuggestedName(title: String): String {
    val normalized = title.trim()
        .replace(ILLEGAL_FILE_NAME_CHARS, "_")
        .trim('_', ' ')
        .ifBlank { "lynmusic" }
    return "$normalized-lyrics-share.png"
}

fun buildLyricsShareTitleArtistLine(
    title: String,
    artistName: String?,
): String {
    val resolvedTitle = title.trim().ifBlank { "当前歌曲" }
    val resolvedArtist = artistName?.trim().orEmpty().ifBlank { "未知艺人" }
    return "$resolvedTitle · $resolvedArtist"
}

private val ILLEGAL_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|]+""")
