package top.iwesley.lyn.music.data.repository

import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
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
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.AlbumEntity
import top.iwesley.lyn.music.data.db.DailyRecommendationEntity
import top.iwesley.lyn.music.data.db.FavoriteTrackEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.TrackPlaybackStatsEntity
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.normalizeNavidromeBaseUrl
import top.iwesley.lyn.music.domain.requestNavidromeJson

interface MyRepository {
    val recentTracks: Flow<List<RecentTrack>>
    val recentAlbums: Flow<List<RecentAlbum>>
    val dailyRecommendation: Flow<List<Track>>

    suspend fun refreshNavidromeRecentPlays(): Result<Unit>
    suspend fun ensureDailyRecommendation(): Result<Unit>
}

class RoomMyRepository(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val httpClient: LyricsHttpClient,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    private val recentTrackLimit: Int = DEFAULT_RECENT_ITEM_LIMIT,
    private val recentAlbumLimit: Int = DEFAULT_RECENT_ITEM_LIMIT,
    private val dailyRecommendationDateKeyProvider: DailyRecommendationDateKeyProvider =
        UtcDailyRecommendationDateKeyProvider,
    private val dailyRecommendationLimit: Int = DEFAULT_DAILY_RECOMMENDATION_LIMIT,
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

    override val dailyRecommendation: Flow<List<Track>> = combine(
        database.dailyRecommendationDao().observeAll(),
        database.trackDao().observeAll(),
        database.importSourceDao().observeAll(),
        database.lyricsCacheDao().observeArtworkLocators(),
    ) { recommendationRows, tracks, sources, artworkRows ->
        val today = dailyRecommendationDateKeyProvider.currentDateKey()
        val recommendation = recommendationRows.firstOrNull { it.dateKey == today }
            ?: return@combine emptyList()
        val enabledSourceIds = sources.enabledSourceIds()
        val artworkOverrides = effectiveArtworkOverridesByTrackId(artworkRows)
        val tracksById = tracks
            .asSequence()
            .filter { it.sourceId in enabledSourceIds }
            .associateBy { it.id }
        decodeDailyRecommendationTrackIds(recommendation.trackIds)
            .mapNotNull { trackId -> tracksById[trackId] }
            .map { track -> track.toDomain(artworkOverrides[track.id]) }
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

    override suspend fun ensureDailyRecommendation(): Result<Unit> {
        return runCatching {
            val dateKey = dailyRecommendationDateKeyProvider.currentDateKey()
            if (database.dailyRecommendationDao().getByDateKey(dateKey) != null) return@runCatching
            val generatedAt = Clock.System.now().toEpochMilliseconds()
            val trackIds = generateDailyRecommendationTrackIds(
                dateKey = dateKey,
                generatedAt = generatedAt,
            )
            database.dailyRecommendationDao().upsert(
                DailyRecommendationEntity(
                    dateKey = dateKey,
                    generatedAt = generatedAt,
                    trackIds = encodeDailyRecommendationTrackIds(trackIds),
                ),
            )
        }
    }

    private suspend fun generateDailyRecommendationTrackIds(
        dateKey: String,
        generatedAt: Long,
    ): List<String> {
        val enabledSourceIds = database.importSourceDao()
            .getAll()
            .enabledSourceIds()
        val tracks = database.trackDao()
            .getAll()
            .filter { it.sourceId in enabledSourceIds }
        if (tracks.isEmpty()) return emptyList()
        val trackIds = tracks.map { it.id }
        val trackStats = database.trackPlaybackStatsDao()
            .getByTrackIds(trackIds)
            .associateBy { it.trackId }
        val favoriteTracks = database.favoriteTrackDao()
            .getAll()
            .filter { it.sourceId in enabledSourceIds }
        val recentRecommendationTrackIds = database.dailyRecommendationDao()
            .getRecentBefore(
                dateKey = dateKey,
                sinceGeneratedAt = generatedAt - RECENT_RECOMMENDATION_PENALTY_WINDOW_MS,
            )
            .flatMap { decodeDailyRecommendationTrackIds(it.trackIds) }
            .toSet()
        return rankDailyRecommendationTrackIds(
            tracks = tracks,
            favoriteTracks = favoriteTracks,
            trackStats = trackStats,
            recentRecommendationTrackIds = recentRecommendationTrackIds,
            dateKey = dateKey,
            nowMs = generatedAt,
            limit = dailyRecommendationLimit,
        )
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

internal fun rankDailyRecommendationTrackIds(
    tracks: List<TrackEntity>,
    favoriteTracks: List<FavoriteTrackEntity>,
    trackStats: Map<String, TrackPlaybackStatsEntity>,
    recentRecommendationTrackIds: Set<String>,
    dateKey: String,
    nowMs: Long,
    limit: Int = DEFAULT_DAILY_RECOMMENDATION_LIMIT,
): List<String> {
    if (tracks.isEmpty() || limit <= 0) return emptyList()
    val favoriteTrackIds = favoriteTracks.map { it.trackId }.toSet()
    val tracksById = tracks.associateBy { it.id }
    val favoriteArtistKeys = favoriteTrackIds
        .mapNotNull { tracksById[it]?.artistName?.recommendationTextKey() }
        .toSet()
    val favoriteAlbumKeys = favoriteTrackIds
        .mapNotNull { tracksById[it]?.albumId?.recommendationTextKey() ?: tracksById[it]?.albumTitle?.recommendationTextKey() }
        .toSet()
    val frequentTracks = tracks
        .filter { track -> (trackStats[track.id]?.playCount ?: 0) > 0 }
        .sortedByDescending { track -> trackStats[track.id]?.playCount ?: 0 }
        .take(FREQUENT_PREFERENCE_SAMPLE_SIZE)
    val frequentArtistKeys = frequentTracks.mapNotNull { it.artistName.recommendationTextKey() }.toSet()
    val frequentAlbumKeys = frequentTracks.mapNotNull { it.albumId.recommendationTextKey() ?: it.albumTitle.recommendationTextKey() }
        .toSet()
    val maxPlayCount = max(1, trackStats.values.maxOfOrNull { it.playCount } ?: 0)
    val scored = tracks
        .map { track ->
            val stats = trackStats[track.id]
            val artistKey = track.artistName.recommendationTextKey()
            val albumKey = track.albumId.recommendationTextKey() ?: track.albumTitle.recommendationTextKey()
            val titleKey = track.title.recommendationTextKey()
            val playCount = stats?.playCount ?: 0
            val seededHash = stableRecommendationHash(dateKey, track.id)
            var score = 0.0
            if (track.id in favoriteTrackIds) score += 0.25
            score += (playCount.toDouble() / maxPlayCount.toDouble()) * 0.25
            if (artistKey != null && (artistKey in favoriteArtistKeys || artistKey in frequentArtistKeys)) score += 0.12
            if (albumKey != null && (albumKey in favoriteAlbumKeys || albumKey in frequentAlbumKeys)) score += 0.10
            val lastPlayedAt = stats?.lastPlayedAt
            if (lastPlayedAt != null) {
                val ageMs = nowMs - lastPlayedAt
                if (ageMs >= THIRTY_DAYS_MS) score += 0.12
                if (ageMs in 0..THREE_DAYS_MS) score -= 0.30
            }
            val addedAgeMs = nowMs - track.addedAt
            if (addedAgeMs in 0..THIRTY_DAYS_MS && playCount <= 1) score += 0.10
            if (track.id in recentRecommendationTrackIds) score -= 0.45
            score += stableRecommendationFraction(seededHash) * 0.08
            DailyRecommendationCandidate(
                track = track,
                score = score,
                seededHash = seededHash,
                artistKey = artistKey,
                albumKey = albumKey,
                titleKey = titleKey,
            )
        }
        .sortedWith(
            compareByDescending<DailyRecommendationCandidate> { it.score }
                .thenBy { it.seededHash }
                .thenBy { it.track.title.trim().lowercase() }
                .thenBy { it.track.id },
        )
    val selected = mutableListOf<DailyRecommendationCandidate>()
    val selectedIds = mutableSetOf<String>()
    val selectedTitleKeys = mutableSetOf<String>()
    val artistCounts = mutableMapOf<String, Int>()
    val albumCounts = mutableMapOf<String, Int>()
    scored.forEach { candidate ->
        if (selected.size >= limit) return@forEach
        if (candidate.titleKey != null && candidate.titleKey in selectedTitleKeys) return@forEach
        val artistCount = candidate.artistKey?.let { artistCounts[it] } ?: 0
        val albumCount = candidate.albumKey?.let { albumCounts[it] } ?: 0
        if (artistCount >= DAILY_RECOMMENDATION_ARTIST_LIMIT) return@forEach
        if (albumCount >= DAILY_RECOMMENDATION_ALBUM_LIMIT) return@forEach
        selected += candidate
        selectedIds += candidate.track.id
        candidate.titleKey?.let(selectedTitleKeys::add)
        candidate.artistKey?.let { artistCounts[it] = artistCount + 1 }
        candidate.albumKey?.let { albumCounts[it] = albumCount + 1 }
    }
    if (selected.size < limit) {
        scored.forEach { candidate ->
            if (selected.size >= limit) return@forEach
            if (candidate.track.id !in selectedIds &&
                (candidate.titleKey == null || candidate.titleKey !in selectedTitleKeys)
            ) {
                selected += candidate
                selectedIds += candidate.track.id
                candidate.titleKey?.let(selectedTitleKeys::add)
            }
        }
    }
    if (selected.size < limit) {
        scored.forEach { candidate ->
            if (selected.size >= limit) return@forEach
            if (candidate.track.id !in selectedIds) {
                selected += candidate
                selectedIds += candidate.track.id
            }
        }
    }
    return selected.take(limit).map { it.track.id }
}

private fun encodeDailyRecommendationTrackIds(trackIds: List<String>): String {
    return JsonArray(trackIds.map(::JsonPrimitive)).toString()
}

private fun decodeDailyRecommendationTrackIds(payload: String): List<String> {
    return runCatching {
        (Json.parseToJsonElement(payload) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
    }.getOrElse {
        payload.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) }
    }
}

private fun String?.recommendationTextKey(): String? {
    return this?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
}

private fun stableRecommendationFraction(hash: Long): Double {
    val positive = hash and Long.MAX_VALUE
    return (positive % 10_000L).toDouble() / 10_000.0
}

private fun stableRecommendationHash(vararg parts: String): Long {
    var hash = -0x340d631b7bdddcdbL
    parts.forEach { part ->
        part.forEach { char ->
            hash = hash xor char.code.toLong()
            hash *= 0x100000001b3L
        }
        hash = hash xor 31L
        hash *= 0x100000001b3L
    }
    return hash
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

private data class DailyRecommendationCandidate(
    val track: TrackEntity,
    val score: Double,
    val seededHash: Long,
    val artistKey: String?,
    val albumKey: String?,
    val titleKey: String?,
)

private const val DEFAULT_RECENT_ITEM_LIMIT = 20
private const val DEFAULT_DAILY_RECOMMENDATION_LIMIT = 30
private const val NAVIDROME_RECENT_ALBUM_FETCH_SIZE = 50
private const val THREE_DAYS_MS = 3L * 24L * 60L * 60L * 1_000L
private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1_000L
private const val RECENT_RECOMMENDATION_PENALTY_WINDOW_MS = 7L * 24L * 60L * 60L * 1_000L
private const val FREQUENT_PREFERENCE_SAMPLE_SIZE = 12
private const val DAILY_RECOMMENDATION_ARTIST_LIMIT = 2
private const val DAILY_RECOMMENDATION_ALBUM_LIMIT = 2
private const val MY_LOG_TAG = "My"
