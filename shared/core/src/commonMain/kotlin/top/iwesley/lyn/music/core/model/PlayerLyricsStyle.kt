package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerLyricsStyle(
    val highlightArgb: Int = -1,
    val fontScale: Float = 1f,
    val fontKey: String? = null,
    val showTranslation: Boolean = true,
) {
    fun normalized(): PlayerLyricsStyle = copy(
        highlightArgb = highlightArgb or 0xff000000.toInt(),
        fontScale = if (fontScale.isFinite()) fontScale.coerceIn(0.75f, 1.75f) else 1f,
        fontKey = fontKey?.takeIf { parseLyricsShareImportedFontHash(it) != null },
    )
}

interface PlayerLyricsStylePreferencesStore {
    val playerLyricsStyle: StateFlow<PlayerLyricsStyle>
    suspend fun setPlayerLyricsStyle(value: PlayerLyricsStyle)
}

class InMemoryPlayerLyricsStylePreferencesStore : PlayerLyricsStylePreferencesStore {
    override val playerLyricsStyle = MutableStateFlow(PlayerLyricsStyle())
    override suspend fun setPlayerLyricsStyle(value: PlayerLyricsStyle) {
        playerLyricsStyle.value = value.normalized()
    }
}

fun encodePlayerLyricsStyle(value: PlayerLyricsStyle): String = value.normalized().let {
    "${it.highlightArgb}|${it.fontScale}|${it.fontKey.orEmpty()}|${it.showTranslation}"
}

fun decodePlayerLyricsStyle(value: String?): PlayerLyricsStyle {
    val parts = value?.split('|') ?: return PlayerLyricsStyle()
    return PlayerLyricsStyle(
        highlightArgb = parts.getOrNull(0)?.toIntOrNull() ?: -1,
        fontScale = parts.getOrNull(1)?.toFloatOrNull() ?: 1f,
        fontKey = parts.getOrNull(2),
        showTranslation = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: true,
    ).normalized()
}

const val MAX_USER_LYRICS_OFFSET_MS: Long = 30_000L

fun normalizeUserLyricsOffset(value: Long): Long =
    value.coerceIn(-MAX_USER_LYRICS_OFFSET_MS, MAX_USER_LYRICS_OFFSET_MS)
