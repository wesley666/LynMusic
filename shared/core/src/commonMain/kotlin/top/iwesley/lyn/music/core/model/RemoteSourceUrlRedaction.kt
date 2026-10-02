package top.iwesley.lyn.music.core.model

private val REMOTE_SOURCE_URL_SECRET_PARAMETERS = Regex("([?&](?:t|s|p|apiKey|api_key)=)[^&\\s\"']*")

/**
 * Masks credentials carried in Subsonic/Navidrome (`t`, `s`, `p`, `apiKey`) and Emby (`api_key`) URLs so the
 * text can be written to diagnostic logs, which users may export or share. Works on whole log messages too:
 * only the query parameter values are replaced.
 */
fun redactRemoteSourceUrlForLog(text: String): String =
    text.replace(REMOTE_SOURCE_URL_SECRET_PARAMETERS, "$1<redacted>")
