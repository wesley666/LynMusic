package top.iwesley.lyn.music.feature.player

private val PLAYER_LYRICS_STRUCTURE_TAG_REGEX = Regex(
    pattern = """\[(intro|outro|verse|chorus|prechorus|postchorus|pre-chorus|post-chorus|bridge|hook|refrain|instrumental|solo|interlude)(\s+\d+)?]""",
    option = RegexOption.IGNORE_CASE,
)

fun isPlayerLyricsStructureTagLine(text: String): Boolean {
    return PLAYER_LYRICS_STRUCTURE_TAG_REGEX.matches(text.trim())
}
