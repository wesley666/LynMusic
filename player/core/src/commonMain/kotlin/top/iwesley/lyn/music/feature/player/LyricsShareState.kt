package top.iwesley.lyn.music.feature.player

import top.iwesley.lyn.music.core.model.*

/** The complete render input is also the preview cache key. Playback position is not part of it. */
internal fun PlayerState.lyricsShareModel(): LyricsShareCardModel? = deriveLyricsShareCardModel(
    effectiveSnapshot, lyrics, selectedLyricsLineIndices, selectedLyricsShareTemplate, selectedLyricsShareFontKey,
)

internal fun deriveLyricsShareCardModel(
    snapshot: PlaybackSnapshot, lyrics: LyricsDocument?, selectedLineIndices: Set<Int>,
    template: LyricsShareTemplate, fontKey: String?,
): LyricsShareCardModel? {
    val lines = selectedLineIndices.sorted().mapNotNull { index ->
        lyrics?.lines?.getOrNull(index)?.text?.trim()?.takeIf(::isSelectableLyricsShareLine)
    }
    if (lines.isEmpty()) return null
    return LyricsShareCardModel(
        title = snapshot.currentDisplayTitle.ifBlank { snapshot.currentTrack?.title.orEmpty() },
        artistName = snapshot.currentDisplayArtistName ?: snapshot.currentTrack?.artistName,
        artworkLocator = snapshot.currentDisplayArtworkLocator,
        artworkCacheKey = snapshot.currentTrack?.let(::trackArtworkCacheKey),
        template = template, lyricsLines = lines, fontKey = fontKey,
    )
}

internal fun isSelectableLyricsShareLine(text: String): Boolean =
    text.isNotBlank() && !isPlayerLyricsStructureTagLine(text.trim())
