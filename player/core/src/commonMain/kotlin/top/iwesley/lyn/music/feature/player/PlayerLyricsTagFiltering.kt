package top.iwesley.lyn.music.feature.player

/** Compatibility entry point shared by playback, sharing, and desktop lyrics. */
fun isPlayerLyricsStructureTagLine(text: String): Boolean =
    top.iwesley.lyn.music.core.model.isLyricsStructureTagLine(text)
