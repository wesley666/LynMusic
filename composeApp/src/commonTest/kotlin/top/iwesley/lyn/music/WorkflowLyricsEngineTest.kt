package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.WorkflowLyricsStepConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsTransform
import top.iwesley.lyn.music.core.model.WorkflowRequestConfig
import top.iwesley.lyn.music.domain.MANAGED_MUSICMATCH_SOURCE_ID
import top.iwesley.lyn.music.domain.MANAGED_MUSICMATCH_SOURCE_NAME
import top.iwesley.lyn.music.domain.MUSICMATCH_LYRICS_EXTRACTOR
import top.iwesley.lyn.music.domain.buildManagedMusicmatchWorkflowJson
import top.iwesley.lyn.music.domain.extractManagedMusicmatchUserToken
import top.iwesley.lyn.music.domain.extractWorkflowLyricsPayload
import top.iwesley.lyn.music.domain.extractWorkflowSongCandidates
import top.iwesley.lyn.music.domain.mergeWorkflowCandidateCapture
import top.iwesley.lyn.music.domain.parseWorkflowLyricsDocument
import top.iwesley.lyn.music.domain.parseWorkflowLyricsSourceConfig

class WorkflowLyricsEngineTest {

    @Test
    fun `oiapi workflow json parses and extracts candidates`() {
        val config = parseWorkflowLyricsSourceConfig(
            """
            {
              "id": "custom-oiapi-qqmusic",
              "name": "OIAPI QQMusic",
              "kind": "workflow",
              "enabled": true,
              "priority": 80,
              "search": {
                "method": "GET",
                "url": "https://oiapi.net/api/QQMusicLyric",
                "queryTemplate": "keyword={title} {artist}&page=1&limit=10&type=json",
                "responseFormat": "json",
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
                "minScore": 0.4,
                "maxCandidates": 10
              },
              "lyrics": {
                "steps": [
                  {
                    "method": "GET",
                    "url": "https://oiapi.net/api/QQMusicLyric",
                    "queryTemplate": "id={candidate.id}&format=lrc&type=json",
                    "responseFormat": "json",
                    "payloadPath": "data.content",
                    "fallbackPayloadPath": "message",
                    "format": "lrc",
                    "transforms": ["trim"]
                  }
                ]
              },
              "optionalFields": {
                "coverUrlField": "coverUrl"
              }
            }
            """.trimIndent(),
        )

        val candidates = extractWorkflowSongCandidates(config, OIAPI_SEARCH_JSON)

        assertEquals("custom-oiapi-qqmusic", config.id)
        assertEquals(RequestMethod.GET, config.search.request.method)
        assertEquals(1, candidates.first().id.toInt())
        assertEquals("晴天", candidates.first().title)
        assertEquals(listOf("周杰伦"), candidates.first().artists)
        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T002R800x800M000001O06fF2b3W8P.jpg?max_age=2592000",
            candidates.first().imageUrl,
        )
    }

    @Test
    fun `oiapi lrc payload is extracted from final step`() {
        val config = parseWorkflowLyricsSourceConfig(OIAPI_WORKFLOW_JSON)

        val payload = extractWorkflowLyricsPayload(config.lyrics.steps.last(), OIAPI_LRC_JSON)

        assertTrue(payload?.contains("[ti:晴天]") == true)
    }

    @Test
    fun `musicmatch managed workflow parses token and json subtitle lines`() {
        val rawJson = buildManagedMusicmatchWorkflowJson("token-123")
        val config = parseWorkflowLyricsSourceConfig(rawJson)

        val payload = extractWorkflowLyricsPayload(config.lyrics.steps.last(), MUSICMATCH_SUBTITLE_JSON)
        val document = payload?.let {
            parseWorkflowLyricsDocument(
                sourceId = config.id,
                sourceName = config.name,
                step = config.lyrics.steps.last(),
                payload = it,
            )
        }

        assertEquals(MANAGED_MUSICMATCH_SOURCE_ID, config.id)
        assertEquals(MANAGED_MUSICMATCH_SOURCE_NAME, config.name)
        assertEquals("token-123", extractManagedMusicmatchUserToken(rawJson))
        assertEquals(LyricsResponseFormat.JSON, config.lyrics.steps.last().format)
        assertEquals(MUSICMATCH_LYRICS_EXTRACTOR, config.lyrics.steps.last().extractor)
        assertEquals(2, document?.lines?.size)
        assertEquals(30_880, document?.lines?.first()?.timestampMs)
        assertEquals("對這個世界 如果你有太多的抱怨", document?.lines?.first()?.text)
    }

    @Test
    fun `musicmatch managed workflow also accepts lrc subtitle payload`() {
        val rawJson = buildManagedMusicmatchWorkflowJson("token-123")
        val config = parseWorkflowLyricsSourceConfig(rawJson)

        val document = parseWorkflowLyricsDocument(
            sourceId = config.id,
            sourceName = config.name,
            step = config.lyrics.steps.last(),
            payload = """
                [00:30.88] 對這個世界 如果你有太多的抱怨
                [00:34.24] 跌倒了就不敢繼續往前走
            """.trimIndent(),
        )

        assertEquals(2, document?.lines?.size)
        assertEquals(30_880, document?.lines?.first()?.timestampMs)
        assertEquals("對這個世界 如果你有太多的抱怨", document?.lines?.first()?.text)
    }

    @Test
    fun `workflow enrichment capture also normalizes gtimg artwork url`() {
        val merged = mergeWorkflowCandidateCapture(
            candidate = extractWorkflowSongCandidates(parseWorkflowLyricsSourceConfig(OIAPI_WORKFLOW_JSON), OIAPI_SEARCH_JSON).first(),
            capture = mapOf(
                "imageUrl" to "https://y.gtimg.cn/music/photo_new/T002R800x800M000001O06fF2b3W8Pjpg?max_age=2592000",
            ),
        )

        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T002R800x800M000001O06fF2b3W8P.jpg?max_age=2592000",
            merged.imageUrl,
        )
    }

    @Test
    fun `netease workflow mapping extracts artist names from object arrays`() {
        val config = parseWorkflowLyricsSourceConfig(
            """
            {
              "id": "custom-netease",
              "name": "Netease",
              "kind": "workflow",
              "enabled": true,
              "priority": 70,
              "search": {
                "url": "https://music.163.com/api/cloudsearch/pc",
                "queryTemplate": "s={title} {artist}&type=1&offset=0&limit=10",
                "responseFormat": "json",
                "resultPath": "result.songs",
                "mapping": {
                  "id": "id",
                  "title": "name",
                  "artists": "ar.name",
                  "album": "al.name"
                }
              },
              "lyrics": {
                "steps": [
                  {
                    "url": "https://music.163.com/api/song/lyric",
                    "queryTemplate": "id={candidate.id}&lv=1&tv=1",
                    "responseFormat": "json",
                    "payloadPath": "lrc.lyric",
                    "format": "lrc"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val candidates = extractWorkflowSongCandidates(
            config,
            """
            {
              "result": {
                "songs": [
                  {
                    "id": 1001,
                    "name": "夜曲",
                    "ar": [{"name": "周杰伦"}, {"name": "Guest"}],
                    "al": {"name": "十一月的萧邦"}
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf("周杰伦", "Guest"), candidates.single().artists)
        assertEquals("十一月的萧邦", candidates.single().album)
    }

    @Test
    fun `kugou final step payload can base64 decode`() {
        val payload = extractWorkflowLyricsPayload(
            WorkflowLyricsStepConfig(
                request = WorkflowRequestConfig(
                    method = RequestMethod.GET,
                    url = "https://lyrics.kugou.com/download",
                ),
                payloadPath = "content",
                format = top.iwesley.lyn.music.core.model.LyricsResponseFormat.LRC,
                transforms = listOf(WorkflowLyricsTransform.BASE64_DECODE, WorkflowLyricsTransform.TRIM),
            ),
            """
            {
              "content": "WzAwOjAxLjAwXeesrOS4gOWPpQ=="
            }
            """.trimIndent(),
        )

        assertEquals("[00:01.00]第一句", payload)
    }

    @Test
    fun `kugou workflow json accepts camel case transform aliases`() {
        val config = parseWorkflowLyricsSourceConfig(
            """
            {
              "id": "custom-kugou",
              "name": "Kugou",
              "kind": "workflow",
              "enabled": false,
              "priority": 70,
              "search": {
                "method": "GET",
                "url": "http://mobilecdn.kugou.com/api/v3/search/song",
                "queryTemplate": "format=json&keyword={title} {artist}&page=1&pagesize=10&showtype=1",
                "responseFormat": "json",
                "resultPath": "data.info",
                "mapping": {
                  "id": "hash",
                  "title": "songname",
                  "artists": "singername",
                  "album": "album_name",
                  "durationSeconds": "duration"
                }
              },
              "selection": {
                "titleWeight": 0.7,
                "artistWeight": 0.2,
                "albumWeight": 0.05,
                "durationWeight": 0.05,
                "durationToleranceSeconds": 3,
                "minScore": 0.2,
                "maxCandidates": 10
              },
              "lyrics": {
                "steps": [
                  {
                    "method": "GET",
                    "url": "https://krcs.kugou.com/search",
                    "queryTemplate": "ver=1&man=yes&client=pc&keyword={candidate.title} - {candidate.artists}&hash={candidate.id}",
                    "responseFormat": "json",
                    "capture": {
                      "id": "candidates[0].id",
                      "accessKey": "candidates[0].accesskey"
                    }
                  },
                  {
                    "method": "GET",
                    "url": "http://lyrics.kugou.com/download",
                    "queryTemplate": "ver=1&client=pc&id={step0.id}&accesskey={step0.accessKey}&fmt=lrc&charset=utf8",
                    "responseFormat": "json",
                    "payloadPath": "content",
                    "format": "lrc",
                    "transforms": ["base64Decode", "trim"]
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(WorkflowLyricsTransform.BASE64_DECODE, WorkflowLyricsTransform.TRIM),
            config.lyrics.steps.last().transforms,
        )
    }

    @Test
    fun `workflow import rejects unknown top level fields`() {
        assertFailsWith<IllegalArgumentException> {
            parseWorkflowLyricsSourceConfig(
                """
                {
                  "id": "bad-workflow",
                  "name": "Bad",
                  "kind": "workflow",
                  "enabled": true,
                  "priority": 1,
                  "search": {
                    "url": "https://example.com",
                    "resultPath": "data",
                    "mapping": {
                      "id": "id",
                      "title": "title",
                      "artists": "artists"
                    }
                  },
                  "lyrics": {
                    "steps": [
                      {
                        "url": "https://example.com/lrc",
                        "payloadPath": "lyrics"
                      }
                    ]
                  },
                  "extra": true
                }
                """.trimIndent(),
            )
        }
    }
}

private const val OIAPI_WORKFLOW_JSON = """
{
  "id": "custom-oiapi-qqmusic",
  "name": "OIAPI QQMusic",
  "kind": "workflow",
  "enabled": true,
  "priority": 80,
  "search": {
    "method": "GET",
    "url": "https://oiapi.net/api/QQMusicLyric",
    "queryTemplate": "keyword={title} {artist}&page=1&limit=10&type=json",
    "responseFormat": "json",
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
    "minScore": 0.4,
    "maxCandidates": 10
  },
  "lyrics": {
    "steps": [
      {
        "method": "GET",
        "url": "https://oiapi.net/api/QQMusicLyric",
        "queryTemplate": "id={candidate.id}&format=lrc&type=json",
        "responseFormat": "json",
        "payloadPath": "data.content",
        "fallbackPayloadPath": "message",
        "format": "lrc",
        "transforms": ["trim"]
      }
    ]
  },
  "optionalFields": {
    "coverUrlField": "coverUrl"
  }
}
"""

private const val OIAPI_SEARCH_JSON = """
{
  "code": 1,
  "message": "1. 晴天 - 周杰伦",
  "data": [
    {
      "name": "晴天",
      "singer": ["周杰伦"],
      "album": "叶惠美",
      "mid": "0039MnYb0qxYhV",
      "id": 1,
      "duration": 269,
      "image": "https://y.gtimg.cn/music/photo_new/T002R800x800M000001O06fF2b3W8Pjpg?max_age=2592000"
    }
  ]
}
"""

private const val OIAPI_LRC_JSON = """
{
  "code": 1,
  "message": "[ti:晴天]",
  "data": {
    "content": "[ti:晴天]\n[00:01.00]第一句",
    "format": "lrc"
  }
}
"""

private const val MUSICMATCH_SUBTITLE_JSON = """
{
  "message": {
    "header": {
      "status_code": 200
    },
    "body": {
      "subtitle": {
        "subtitle_body": "[{\"text\":\"對這個世界 如果你有太多的抱怨\",\"time\":{\"total\":30.88,\"minutes\":0,\"seconds\":30,\"hundredths\":88}},{\"text\":\"跌倒了就不敢繼續往前走\",\"time\":{\"total\":34.24,\"minutes\":0,\"seconds\":34,\"hundredths\":24}}]"
      }
    }
  }
}
"""
