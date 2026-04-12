package top.iwesley.lyn.music.domain

const val PRESET_OIAPI_QQMUSIC_SOURCE_ID = "custom-oiapi-qqmusic"
const val PRESET_OIAPI_QQMUSIC_SOURCE_NAME = "OIAPI Lyrics"
const val PRESET_OIAPI_QQMUSIC_SOURCE_PRIORITY = 80

fun buildPresetOiapiQqMusicWorkflowJson(): String {
    return parseWorkflowLyricsSourceConfig(
        """
        {
          "id": "$PRESET_OIAPI_QQMUSIC_SOURCE_ID",
          "name": "$PRESET_OIAPI_QQMUSIC_SOURCE_NAME",
          "kind": "workflow",
          "enabled": true,
          "priority": $PRESET_OIAPI_QQMUSIC_SOURCE_PRIORITY,
          "search": {
            "method": "GET",
            "url": "https://oiapi.net/api/QQMusicLyric",
            "queryTemplate": "keyword={title} {artist}&page=1&limit=10&type=json",
            "responseFormat": "JSON",
            "resultPath": "data",
            "mapping": {
              "id": "id",
              "title": "name",
              "artists": "singer",
              "album": "album",
              "durationSeconds": "duration",
              "mid": "mid",
              "coverUrl": "image"
            }
          },
          "selection": {
            "titleWeight": 0.7,
            "artistWeight": 0.2,
            "albumWeight": 0.05,
            "durationWeight": 0.05,
            "durationToleranceSeconds": 3,
            "minScore": 0.9,
            "maxCandidates": 10
          },
          "lyrics": {
            "steps": [
              {
                "method": "GET",
                "url": "https://oiapi.net/api/QQMusicLyric",
                "queryTemplate": "id={candidate.id}&format=lrc&type=json",
                "responseFormat": "JSON",
                "payloadPath": "data.content",
                "fallbackPayloadPath": "message",
                "format": "LRC",
                "transforms": ["trim"]
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
