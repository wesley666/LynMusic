package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator

fun lyricsSearchApplyModes(artworkLocator: String?): List<LyricsSearchApplyMode> {
    return buildList {
        add(LyricsSearchApplyMode.FULL)
        add(LyricsSearchApplyMode.LYRICS_ONLY)
        if (!normalizeArtworkLocator(artworkLocator).isNullOrBlank()) {
            add(LyricsSearchApplyMode.ARTWORK_ONLY)
        }
    }
}
