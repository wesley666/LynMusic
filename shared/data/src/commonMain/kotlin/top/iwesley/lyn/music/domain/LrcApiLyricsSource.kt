package top.iwesley.lyn.music.domain

import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.RequestMethod

const val MANAGED_LRCAPI_SOURCE_ID = "managed-lrcapi"
const val MANAGED_LRCAPI_SOURCE_NAME = "LrcAPI"
const val MANAGED_LRCAPI_SOURCE_PRIORITY = 110
const val LRCAPI_QUERY_TEMPLATE = "title={title}&artist={artist}"
const val LRCAPI_JSON_MAP_EXTRACTOR = "json-map:lyrics=lyrics|lrc,title=title,artist=artist,album=album,durationSeconds=duration,id=id,coverUrl=cover"

fun isManagedLrcApiSource(source: LyricsSourceDefinition): Boolean {
    return source.id == MANAGED_LRCAPI_SOURCE_ID
}

fun buildManagedLrcApiConfig(urlTemplate: String): LyricsSourceConfig {
    val normalizedUrl = urlTemplate.trim()
    require(normalizedUrl.isNotBlank()) { "请填写 LrcAPI 请求地址。" }
    return LyricsSourceConfig(
        id = MANAGED_LRCAPI_SOURCE_ID,
        name = MANAGED_LRCAPI_SOURCE_NAME,
        method = RequestMethod.GET,
        urlTemplate = normalizedUrl,
        queryTemplate = LRCAPI_QUERY_TEMPLATE,
        responseFormat = LyricsResponseFormat.JSON,
        extractor = LRCAPI_JSON_MAP_EXTRACTOR,
        priority = MANAGED_LRCAPI_SOURCE_PRIORITY,
        enabled = true,
    )
}

fun extractManagedLrcApiUrl(source: LyricsSourceDefinition): String? {
    val config = source as? LyricsSourceConfig ?: return null
    if (!isManagedLrcApiSource(config)) return null
    return config.urlTemplate.trim().takeIf(String::isNotBlank)
}
