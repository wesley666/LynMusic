package top.iwesley.lyn.music.domain

import top.iwesley.lyn.music.core.model.LyricsSourceDefinition

const val MANAGED_MUSICMATCH_SOURCE_ID = "managed-musicmatch"
const val MANAGED_MUSICMATCH_SOURCE_NAME = "Musicmatch"
const val MANAGED_MUSICMATCH_SOURCE_PRIORITY = 95
const val MUSICMATCH_LYRICS_EXTRACTOR = "json-lines:time.total,text"

private val MUSICMATCH_TOKEN_REGEX = Regex("""(?:^|&)usertoken=([^&]+)""")

fun isManagedMusicmatchSource(source: LyricsSourceDefinition): Boolean {
    return source.id == MANAGED_MUSICMATCH_SOURCE_ID
}

fun buildManagedMusicmatchWorkflowJson(usertoken: String): String {
    val normalizedToken = usertoken.trim()
    require(normalizedToken.isNotBlank()) { "请填写 Musicmatch usertoken。" }
    return parseWorkflowLyricsSourceConfig(
        """
        {
          "id": "$MANAGED_MUSICMATCH_SOURCE_ID",
          "name": "$MANAGED_MUSICMATCH_SOURCE_NAME",
          "kind": "workflow",
          "enabled": true,
          "priority": $MANAGED_MUSICMATCH_SOURCE_PRIORITY,
          "search": {
            "method": "GET",
            "url": "https://apic-desktop.musixmatch.com/ws/1.1/track.search",
            "queryTemplate": "app_id=web-desktop-app-v1.0&usertoken=$normalizedToken&q_track={title}&q_artist={artist}",
            "responseFormat": "JSON",
            "resultPath": "message.body.track_list",
            "mapping": {
              "id": "track.track_id",
              "title": "track.track_name",
              "artists": "track.artist_name",
              "album": "track.album_name",
              "durationSeconds": "track.track_length",
              "coverUrl": "track.album_coverart_350x350"
            }
          },
          "lyrics": {
            "steps": [
              {
                "method": "GET",
                "url": "https://apic-desktop.musixmatch.com/ws/1.1/track.subtitle.get",
                "queryTemplate": "app_id=web-desktop-app-v1.0&usertoken=$normalizedToken&track_id={candidate.id}",
                "responseFormat": "JSON",
                "payloadPath": "message.body.subtitle.subtitle_body",
                "format": "JSON",
                "extractor": "$MUSICMATCH_LYRICS_EXTRACTOR",
                "transforms": ["jsonUnescape", "trim"]
              }
            ]
          },
          "optionalFields": {
            "coverUrlField": "coverUrl"
          }
        }
        """.trimIndent(),
    ).rawJson
}

fun extractManagedMusicmatchUserToken(rawJson: String): String? {
    val workflow = runCatching { parseWorkflowLyricsSourceConfig(rawJson) }.getOrNull() ?: return null
    if (!isManagedMusicmatchSource(workflow)) return null
    return MUSICMATCH_TOKEN_REGEX.find(workflow.search.request.queryTemplate)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}
