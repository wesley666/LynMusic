package top.iwesley.lyn.music.data.repository

import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.RecentAlbum
import top.iwesley.lyn.music.core.model.RecentTrack
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.AlbumEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.normalizeNavidromeBaseUrl
import top.iwesley.lyn.music.domain.requestNavidromeJson

interface MyRepository {
    val recentTracks: Flow<List<RecentTrack>>
    val recentAlbums: Flow<List<RecentAlbum>>

    suspend fun refreshNavidromeRecentPlays(): Result<Unit>
}

class RoomMyRepository(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val httpClient: LyricsHttpClient,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    private val recentTrackLimit: Int = DEFAULT_RECENT_ITEM_LIMIT,
    private val recentAlbumLimit: Int = DEFAULT_RECENT_ITEM_LIMIT,
) : MyRepository {
    override val recentTracks: Flow<List<RecentTrack>> = combine(
        database.trackPlaybackStatsDao().observeAllByRecent(),
        database.trackDao().observeAll(),
        database.importSourceDao().observeAll(),
        database.lyricsCacheDao().observeArtworkLocators(),
    ) { stats, tracks, sources, artworkRows ->
        val enabledSourceIds = sources.enabledSourceIds()
        val artworkOverrides = effectiveArtworkOverridesByTrackId(artworkRows)
        val tracksById = tracks
            .asSequence()
            .filter { it.sourceId in enabledSourceIds }
            .associateBy { it.id }
        stats
            .mapNotNull { stat ->
                val track = tracksById[stat.trackId] ?: return@mapNotNull null
                RecentTrack(
                    track = track.toDomain(artworkOverrides[track.id]),
                    playCount = stat.playCount,
                    lastPlayedAt = stat.lastPlayedAt,
                )
            }
            .take(recentTrackLimit)
    }

    override val recentAlbums: Flow<List<RecentAlbum>> = combine(
        database.albumPlaybackStatsDao().observeAllByRecent(),
        database.albumDao().observeAll(),
        database.trackDao().observeAll(),
        database.importSourceDao().observeAll(),
        database.lyricsCacheDao().observeArtworkLocators(),
    ) { stats, albums, tracks, sources, artworkRows ->
        val enabledSourceIds = sources.enabledSourceIds()
        val artworkOverrides = effectiveArtworkOverridesByTrackId(artworkRows)
        val albumsById = albums.associateBy { it.id }
        val enabledAlbumIds = tracks
            .asSequence()
            .filter { it.sourceId in enabledSourceIds }
            .mapNotNull { it.albumId?.takeIf(String::isNotBlank) }
            .toSet()
        val artworkByAlbumId = tracks
            .asSequence()
            .filter { it.sourceId in enabledSourceIds }
            .filter { !it.albumId.isNullOrBlank() }
            .groupBy { it.albumId.orEmpty() }
            .mapValues { (_, albumTracks) ->
                albumTracks.firstNotNullOfOrNull { track ->
                    artworkOverrides[track.id]?.takeIf { it.isNotBlank() } ?: track.artworkLocator?.takeIf { it.isNotBlank() }
                }
            }
        stats
            .mapNotNull { stat ->
                if (stat.albumId !in enabledAlbumIds) return@mapNotNull null
                val album = albumsById[stat.albumId] ?: return@mapNotNull null
                RecentAlbum(
                    album = album.toDomain(),
                    playCount = stat.playCount,
                    lastPlayedAt = stat.lastPlayedAt,
                    artworkLocator = artworkByAlbumId[stat.albumId],
                )
            }
            .take(recentAlbumLimit)
    }

    override suspend fun refreshNavidromeRecentPlays(): Result<Unit> {
        return runCatching {
            val failures = mutableListOf<Throwable>()
            database.importSourceDao().getAll()
                .filter { it.type == ImportSourceType.NAVIDROME.name && it.enabled }
                .forEach { source ->
                    runCatching {
                        refreshNavidromeRecentPlays(source)
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        failures += throwable
                        logger.warn(MY_LOG_TAG) {
                            "navidrome-recent-refresh-failed source=${source.id} " +
                                "cause=${throwable.message.orEmpty()}"
                        }
                    }
                }
            if (failures.isNotEmpty()) {
                error("Navidrome 最近播放同步失败，已显示本地统计。")
            }
        }
    }

    private suspend fun refreshNavidromeRecentPlays(source: ImportSourceEntity) {
        val resolved = source.toNavidromeResolvedSource()
            ?: error("Navidrome 来源缺少有效账号或密码。")
        val recentAlbums = fetchRecentNavidromeAlbums(resolved)
        val albumDetails = recentAlbums.mapNotNull { recentAlbum ->
            runCatching {
                fetchNavidromeAlbumDetail(
                    source = resolved,
                    albumId = recentAlbum.albumId,
                    fallback = recentAlbum,
                )
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                logger.warn(MY_LOG_TAG) {
                    "navidrome-recent-album-fetch-failed source=${source.id} album=${recentAlbum.albumId} " +
                        "cause=${throwable.message.orEmpty()}"
                }
            }.getOrNull()
        }
        if (albumDetails.isEmpty()) return
        val allSongTrackIds = albumDetails
            .flatMap { detail -> detail.songs.map { song -> navidromeTrackIdFor(source.id, song.songId) } }
            .distinct()
        val localTracksById = if (allSongTrackIds.isEmpty()) {
            emptyMap()
        } else {
            database.trackDao()
                .getByIds(allSongTrackIds)
                .associateBy { it.id }
        }
        val existingTrackStatsById = if (localTracksById.isEmpty()) {
            emptyMap()
        } else {
            database.trackPlaybackStatsDao()
                .getByTrackIds(localTracksById.keys.toList())
                .associateBy { it.trackId }
        }
        val candidateAlbumIds = albumDetails
            .flatMap { detail ->
                detail.songs.mapNotNull { song ->
                    localTracksById[navidromeTrackIdFor(source.id, song.songId)]?.albumId
                }
            }
            .filter { it.isNotBlank() }
            .distinct()
        val existingAlbumStatsById = if (candidateAlbumIds.isEmpty()) {
            emptyMap()
        } else {
            database.albumPlaybackStatsDao()
                .getByAlbumIds(candidateAlbumIds)
                .associateBy { it.albumId }
        }

        albumDetails.forEach { detail ->
            detail.songs.forEach { song ->
                val playedAt = song.playedAt ?: return@forEach
                val trackId = navidromeTrackIdFor(source.id, song.songId)
                val localTrack = localTracksById[trackId] ?: return@forEach
                database.trackPlaybackStatsDao().setPlayStats(
                    trackId = localTrack.id,
                    sourceId = localTrack.sourceId,
                    playCount = resolveSyncedPlayCount(
                        remotePlayCount = song.playCount,
                        existingPlayCount = existingTrackStatsById[trackId]?.playCount,
                    ),
                    lastPlayedAt = playedAt,
                )
            }

            val albumPlayedAt = detail.playedAt
                ?: detail.songs.mapNotNull { it.playedAt }.maxOrNull()
                ?: return@forEach
            val localAlbumIds = detail.songs
                .mapNotNull { song -> localTracksById[navidromeTrackIdFor(source.id, song.songId)]?.albumId }
                .filter { it.isNotBlank() }
                .distinct()
            localAlbumIds.forEach { albumId ->
                database.albumPlaybackStatsDao().setPlayStats(
                    albumId = albumId,
                    playCount = resolveSyncedPlayCount(
                        remotePlayCount = detail.playCount ?: detail.songs.mapNotNull { it.playCount }.maxOrNull(),
                        existingPlayCount = existingAlbumStatsById[albumId]?.playCount,
                    ),
                    lastPlayedAt = albumPlayedAt,
                )
            }
        }
    }

    private suspend fun fetchRecentNavidromeAlbums(
        source: NavidromeResolvedSource,
    ): List<NavidromeRecentAlbumPayload> {
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getAlbumList2",
            parameters = mapOf(
                "type" to "recent",
                "size" to NAVIDROME_RECENT_ALBUM_FETCH_SIZE.toString(),
            ),
            logger = logger,
        )
        return (
            payload["albumList2"].asJsonObjectOrNull()?.get("album")
                ?: payload["albumList"].asJsonObjectOrNull()?.get("album")
            )
            .asJsonObjectList()
            .mapNotNull { album ->
                val albumId = album.string("id") ?: return@mapNotNull null
                NavidromeRecentAlbumPayload(
                    albumId = albumId,
                    playedAt = album.string("played")?.let(::parseNavidromeTimestampMillis),
                    playCount = album.int("playCount"),
                )
            }
    }

    private suspend fun fetchNavidromeAlbumDetail(
        source: NavidromeResolvedSource,
        albumId: String,
        fallback: NavidromeRecentAlbumPayload,
    ): NavidromeAlbumDetailPayload {
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = source,
            endpoint = "getAlbum",
            parameters = mapOf("id" to albumId),
            logger = logger,
            logContext = "albumId=$albumId",
        )
        val album = payload["album"].asJsonObjectOrNull()
            ?: return NavidromeAlbumDetailPayload(
                albumId = albumId,
                playedAt = fallback.playedAt,
                playCount = fallback.playCount,
                songs = emptyList(),
            )
        return NavidromeAlbumDetailPayload(
            albumId = album.string("id") ?: albumId,
            playedAt = album.string("played")?.let(::parseNavidromeTimestampMillis) ?: fallback.playedAt,
            playCount = album.int("playCount") ?: fallback.playCount,
            songs = album["song"].asJsonObjectList().mapNotNull { song ->
                val songId = song.string("id") ?: return@mapNotNull null
                NavidromeRecentSongPayload(
                    songId = songId,
                    playedAt = song.string("played")?.let(::parseNavidromeTimestampMillis),
                    playCount = song.int("playCount"),
                )
            },
        )
    }

    private suspend fun ImportSourceEntity.toNavidromeResolvedSource(): NavidromeResolvedSource? {
        val username = username?.trim().orEmpty()
        val password = credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        if (username.isBlank() || password.isBlank()) return null
        return NavidromeResolvedSource(
            baseUrl = normalizeNavidromeBaseUrl(rootReference),
            username = username,
            password = password,
        )
    }
}

private fun List<ImportSourceEntity>.enabledSourceIds(): Set<String> {
    return asSequence()
        .filter { it.enabled }
        .map { it.id }
        .toSet()
}

private fun AlbumEntity.toDomain(): Album {
    return Album(
        id = id,
        title = title,
        artistName = artistName,
        trackCount = trackCount,
    )
}

private fun resolveSyncedPlayCount(remotePlayCount: Int?, existingPlayCount: Int?): Int {
    return (remotePlayCount ?: existingPlayCount ?: 1).coerceAtLeast(1)
}

private fun parseNavidromeTimestampMillis(value: String): Long? {
    return runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()
}

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asJsonObjectList(): List<JsonObject> {
    return when (val element = this) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(element)
        else -> emptyList()
    }
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.int(key: String): Int? {
    return string(key)?.toIntOrNull()
}

private data class NavidromeRecentAlbumPayload(
    val albumId: String,
    val playedAt: Long?,
    val playCount: Int?,
)

private data class NavidromeAlbumDetailPayload(
    val albumId: String,
    val playedAt: Long?,
    val playCount: Int?,
    val songs: List<NavidromeRecentSongPayload>,
)

private data class NavidromeRecentSongPayload(
    val songId: String,
    val playedAt: Long?,
    val playCount: Int?,
)

private const val DEFAULT_RECENT_ITEM_LIMIT = 20
private const val NAVIDROME_RECENT_ALBUM_FETCH_SIZE = 50
private const val MY_LOG_TAG = "My"
