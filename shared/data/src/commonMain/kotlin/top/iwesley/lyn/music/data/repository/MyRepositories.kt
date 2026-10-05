package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.resources.*

import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.data.db.AlbumEntity
import top.iwesley.lyn.music.data.db.DailyRecommendationEntity
import top.iwesley.lyn.music.data.db.FavoriteTrackEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.TrackPlaybackStatsEntity
import top.iwesley.lyn.music.domain.fetchEmbyRecentTracks
import top.iwesley.lyn.music.domain.fetchFnMusicRecentTracks
import top.iwesley.lyn.music.domain.resolveFnMusicSource
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.isSubsonicCompatibleSourceType
import top.iwesley.lyn.music.domain.normalizeSubsonicBaseUrl
import top.iwesley.lyn.music.domain.requestNavidromeJson
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.resolveEmbySource
import top.iwesley.lyn.music.domain.toSubsonicAuthMode

interface MyRepository {
    val recentTracks: Flow<List<RecentTrack>>
    val recentAlbums: Flow<List<RecentAlbum>>
    val dailyRecommendationDateKey: Flow<String>
    val dailyRecommendation: Flow<List<Track>>
    val hasDailyRecommendationCandidates: Flow<Boolean>

    fun refreshDailyRecommendationDateKey()
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
    private val dailyRecommendationDateChangeNotifier: DailyRecommendationDateChangeNotifier =
        DefaultDailyRecommendationDateChangeNotifier(dailyRecommendationDateKeyProvider),
    private val dailyRecommendationLimit: Int = DEFAULT_DAILY_RECOMMENDATION_LIMIT,
    private val addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
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

    override val dailyRecommendationDateKey: Flow<String> =
        dailyRecommendationDateChangeNotifier.dateKeys.distinctUntilChanged()

    override fun refreshDailyRecommendationDateKey() {
        dailyRecommendationDateChangeNotifier.refreshCurrentDateKey()
    }

    override val dailyRecommendation: Flow<List<Track>> = combine(
        dailyRecommendationDateKey,
        database.dailyRecommendationDao().observeAll(),
        database.trackDao().observeAll(),
        database.importSourceDao().observeAll(),
        database.lyricsCacheDao().observeArtworkLocators(),
    ) { today, recommendationRows, tracks, sources, artworkRows ->
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

    override val hasDailyRecommendationCandidates: Flow<Boolean> = combine(
        database.trackDao().observeAll(),
        database.importSourceDao().observeAll(),
    ) { tracks, sources ->
        val enabledSourceIds = sources.enabledSourceIds()
        tracks.any { track -> track.sourceId in enabledSourceIds }
    }.distinctUntilChanged()

    override suspend fun refreshNavidromeRecentPlays(): Result<Unit> {
        return runCatching {
            val failures = mutableListOf<Throwable>()
            database.importSourceDao().getAll()
                .filter { (it.subsonicCompatibleSourceType() != null || it.isEmbySource() || it.isFnMusicSource()) && it.isLocalIndexedEnabled() }
                .forEach { source ->
                    runCatching {
                        if (source.isEmbySource()) {
                            refreshEmbyRecentPlays(source)
                        } else if (source.isFnMusicSource()) {
                            refreshFnMusicRecentPlays(source)
                        } else {
                            refreshSubsonicCompatibleRecentPlays(source)
                        }
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
                throw UiTextException(
                    uiText(Res.string.recent_remote_sync_failed_fallback),
                )
            }
        }
    }

    override suspend fun ensureDailyRecommendation(): Result<Unit> {
        return runCatching {
            val dateKey = dailyRecommendationDateKeyProvider.currentDateKey()
            val existing = database.dailyRecommendationDao().getByDateKey(dateKey)
            val existingTrackIds = existing
                ?.let { decodeDailyRecommendationTrackIds(it.trackIds) }
                .orEmpty()
            if (hasVisibleDailyRecommendationTracks(existingTrackIds)) {
                return@runCatching
            }
            val generatedAt = Clock.System.now().toEpochMilliseconds()
            val trackIds = generateDailyRecommendationTrackIds(
                dateKey = dateKey,
                generatedAt = generatedAt,
            )
            if (trackIds.isEmpty()) return@runCatching
            database.dailyRecommendationDao().upsert(
                DailyRecommendationEntity(
                    dateKey = dateKey,
                    generatedAt = generatedAt,
                    trackIds = encodeDailyRecommendationTrackIds(trackIds),
                ),
            )
        }
    }

    private suspend fun hasVisibleDailyRecommendationTracks(trackIds: List<String>): Boolean {
        if (trackIds.isEmpty()) return false
        val enabledSourceIds = database.importSourceDao()
            .getAll()
            .enabledSourceIds()
        if (enabledSourceIds.isEmpty()) return false
        val trackIdSet = trackIds.toSet()
        return database.trackDao()
            .getAll()
            .any { track -> track.id in trackIdSet && track.sourceId in enabledSourceIds }
    }

    private suspend fun generateDailyRecommendationTrackIds(
        dateKey: String,
        generatedAt: Long,
    ): List<String> {
        val enabledSourceIds = database.importSourceDao()
            .getAll()
            .enabledSourceIds()
        val allTracks = database.trackDao().getAll()
        val tracks = allTracks
            .filter { it.sourceId in enabledSourceIds }
        if (tracks.isEmpty()) return emptyList()
        val trackIds = tracks.map { it.id }
        val trackStats = database.trackPlaybackStatsDao()
            .getByTrackIds(trackIds)
            .associateBy { it.trackId }
        val favoriteTracks = database.favoriteTrackDao()
            .getAll()
            .filter { it.sourceId in enabledSourceIds }
        val tracksById = allTracks.associateBy { it.id }
        val recentRecommendationExposures = database.dailyRecommendationDao()
            .getRecentBefore(
                dateKey = dateKey,
                sinceGeneratedAt = generatedAt - DAILY_RECOMMENDATION_HISTORY_WINDOW_MS,
            )
            .flatMap { recommendation ->
                decodeDailyRecommendationTrackIds(recommendation.trackIds).map { trackId ->
                    DailyRecommendationExposure(
                        trackId = trackId,
                        track = tracksById[trackId],
                        recommendedAt = recommendation.generatedAt,
                    )
                }
            }
        val ranking = rankDailyRecommendationTracks(
            tracks = tracks,
            favoriteTracks = favoriteTracks,
            trackStats = trackStats,
            recentRecommendationExposures = recentRecommendationExposures,
            dateKey = dateKey,
            nowMs = generatedAt,
            limit = dailyRecommendationLimit,
        )
        logger.info(MY_LOG_TAG) {
            "daily-recommendation-generated date=$dateKey candidates=${ranking.candidateCount} " +
                "hardExcluded=${ranking.hardExcludedCount} softPenalized=${ranking.softPenalizedCount} " +
                "backfilled=${ranking.backfilledCount} selected=${ranking.trackIds.size}"
        }
        return ranking.trackIds
    }

    private suspend fun refreshEmbyRecentPlays(source: ImportSourceEntity) {
        val resolved = resolveEmbySource(database, secureCredentialStore, source.id, addressSelector)
            ?: error("Emby 来源缺少有效凭据。")
        val recentItems = fetchEmbyRecentTracks(
            httpClient = httpClient,
            source = resolved,
            limit = max(recentTrackLimit, recentAlbumLimit).coerceAtLeast(DEFAULT_RECENT_ITEM_LIMIT) * 2,
            logger = logger,
        )
        applyRemoteRecentPlays(
            recentItems.map { item ->
                RemoteRecentPlaySync(
                    trackId = embyTrackIdFor(source.id, item.itemId),
                    playedAt = item.playedAt,
                    playCount = item.playCount,
                )
            },
        )
    }

    private suspend fun refreshFnMusicRecentPlays(source: ImportSourceEntity) {
        val resolved = resolveFnMusicSource(database, secureCredentialStore, source.id)
            ?: error("飞牛音乐来源缺少有效凭据。")
        val recentItems = fetchFnMusicRecentTracks(
            httpClient = httpClient,
            source = resolved,
            limit = max(recentTrackLimit, recentAlbumLimit).coerceAtLeast(DEFAULT_RECENT_ITEM_LIMIT) * 2,
            addressSelector = addressSelector,
            logger = logger,
        )
        applyRemoteRecentPlays(
            recentItems.map { item ->
                RemoteRecentPlaySync(
                    trackId = fnMusicTrackIdFor(source.id, item.guid),
                    playedAt = item.playedAt,
                    playCount = null,
                )
            },
        )
    }

    /** Writes play stats for remote recent plays whose tracks are in the local index. */
    private suspend fun applyRemoteRecentPlays(recentItems: List<RemoteRecentPlaySync>) {
        if (recentItems.isEmpty()) return
        val trackIds = recentItems
            .map { item -> item.trackId }
            .distinct()
        val localTracksById = database.trackDao()
            .getByIds(trackIds)
            .associateBy { it.id }
        if (localTracksById.isEmpty()) return
        val existingTrackStatsById = database.trackPlaybackStatsDao()
            .getByTrackIds(localTracksById.keys.toList())
            .associateBy { it.trackId }
        val candidateAlbumIds = localTracksById.values
            .mapNotNull { it.albumId?.takeIf(String::isNotBlank) }
            .distinct()
        val existingAlbumStatsById = if (candidateAlbumIds.isEmpty()) {
            emptyMap()
        } else {
            database.albumPlaybackStatsDao()
                .getByAlbumIds(candidateAlbumIds)
                .associateBy { it.albumId }
        }
        val albumUpdates = mutableMapOf<String, EmbyAlbumRecentSync>()
        recentItems.forEach { item ->
            val playedAt = item.playedAt ?: return@forEach
            val trackId = item.trackId
            val localTrack = localTracksById[trackId] ?: return@forEach
            database.trackPlaybackStatsDao().setPlayStats(
                trackId = localTrack.id,
                sourceId = localTrack.sourceId,
                playCount = resolveSyncedPlayCount(
                    remotePlayCount = item.playCount,
                    existingPlayCount = existingTrackStatsById[trackId]?.playCount,
                ),
                lastPlayedAt = playedAt,
            )
            val albumId = localTrack.albumId?.takeIf(String::isNotBlank) ?: return@forEach
            val current = albumUpdates[albumId]
            albumUpdates[albumId] = EmbyAlbumRecentSync(
                lastPlayedAt = maxOf(current?.lastPlayedAt ?: Long.MIN_VALUE, playedAt),
                playCount = listOfNotNull(current?.playCount, item.playCount).maxOrNull(),
            )
        }
        albumUpdates.forEach { (albumId, update) ->
            database.albumPlaybackStatsDao().setPlayStats(
                albumId = albumId,
                playCount = resolveSyncedPlayCount(
                    remotePlayCount = update.playCount,
                    existingPlayCount = existingAlbumStatsById[albumId]?.playCount,
                ),
                lastPlayedAt = update.lastPlayedAt,
            )
        }
    }

    private suspend fun refreshSubsonicCompatibleRecentPlays(source: ImportSourceEntity) {
        val sourceType = source.subsonicCompatibleSourceType()
            ?: error("Subsonic-compatible 来源类型无效。")
        val resolved = source.toSubsonicCompatibleResolvedSource()
            ?: error("远程来源缺少有效凭据。")
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
            .flatMap { detail -> detail.songs.map { song -> subsonicCompatibleTrackIdFor(source.id, song.songId, sourceType) } }
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
                    localTracksById[subsonicCompatibleTrackIdFor(source.id, song.songId, sourceType)]?.albumId
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
                val trackId = subsonicCompatibleTrackIdFor(source.id, song.songId, sourceType)
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
                .mapNotNull { song -> localTracksById[subsonicCompatibleTrackIdFor(source.id, song.songId, sourceType)]?.albumId }
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

    private suspend fun ImportSourceEntity.toSubsonicCompatibleResolvedSource(): NavidromeResolvedSource? {
        val sourceType = subsonicCompatibleSourceType() ?: return null
        val authMode = authMode.toSubsonicAuthMode()
        val username = username?.trim().orEmpty()
        val credential = credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        if (authMode == SubsonicAuthMode.PASSWORD && (username.isBlank() || credential.isBlank())) return null
        if (authMode == SubsonicAuthMode.API_KEY && credential.isBlank()) return null
        return NavidromeResolvedSource(
            baseUrl = rootReference,
            wanBaseUrl = wanRootReference,
            sourceId = id,
            addressSelector = addressSelector,
            username = username,
            password = credential,
            authMode = authMode,
            sourceType = sourceType,
        )
    }

    private fun ImportSourceEntity.subsonicCompatibleSourceType(): ImportSourceType? {
        val sourceType = runCatching { ImportSourceType.valueOf(type) }.getOrNull() ?: return null
        return sourceType.takeIf(::isSubsonicCompatibleSourceType)
    }

    private fun ImportSourceEntity.isEmbySource(): Boolean {
        return type == ImportSourceType.EMBY.name
    }

    private fun ImportSourceEntity.isFnMusicSource(): Boolean {
        return type == ImportSourceType.FN_MUSIC.name
    }
}

private fun List<ImportSourceEntity>.enabledSourceIds(): Set<String> {
    return asSequence()
        .filter { it.isLocalIndexedEnabled() }
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

internal data class DailyRecommendationExposure(
    val trackId: String,
    val track: TrackEntity?,
    val recommendedAt: Long,
)

internal data class DailyRecommendationSongIdentity(
    val trackId: String,
    val titleKey: String?,
    val artistKey: String?,
    val albumKey: String?,
    val durationMs: Long,
)

internal data class DailyRecommendationExposureSummary(
    val exposureCount: Int = 0,
    val lastRecommendedAt: Long? = null,
) {
    fun isHardExcluded(nowMs: Long): Boolean {
        val recommendedAt = lastRecommendedAt ?: return false
        return recommendationAgeMs(nowMs, recommendedAt) < DAILY_RECOMMENDATION_HARD_EXCLUSION_WINDOW_MS
    }
}

internal data class DailyRecommendationRankingResult(
    val trackIds: List<String>,
    val candidateCount: Int,
    val hardExcludedCount: Int,
    val softPenalizedCount: Int,
    val backfilledCount: Int,
)

internal fun dailyRecommendationSongIdentity(track: TrackEntity): DailyRecommendationSongIdentity {
    return DailyRecommendationSongIdentity(
        trackId = track.id,
        titleKey = track.title.recommendationIdentityTextKey(),
        artistKey = track.artistName.recommendationIdentityTextKey(),
        albumKey = track.albumTitle.recommendationIdentityTextKey(),
        durationMs = track.durationMs.coerceAtLeast(0L),
    )
}

internal fun areSameDailyRecommendationSong(
    left: DailyRecommendationSongIdentity,
    right: DailyRecommendationSongIdentity,
): Boolean {
    if (left.trackId == right.trackId) return true
    if (left.titleKey == null || left.artistKey == null) return false
    if (left.titleKey != right.titleKey || left.artistKey != right.artistKey) return false
    if (left.durationMs > 0L && right.durationMs > 0L) {
        return kotlin.math.abs(left.durationMs - right.durationMs) <= DAILY_RECOMMENDATION_DURATION_TOLERANCE_MS
    }
    return left.albumKey != null && left.albumKey == right.albumKey
}

internal fun dailyRecommendationHistoryPenalty(
    summary: DailyRecommendationExposureSummary,
    nowMs: Long,
): Double {
    val recommendedAt = summary.lastRecommendedAt ?: return 0.0
    if (summary.exposureCount <= 0) return 0.0
    val ageMs = recommendationAgeMs(nowMs, recommendedAt)
    if (ageMs > DAILY_RECOMMENDATION_HISTORY_WINDOW_MS) return 0.0
    val softWindowMs =
        DAILY_RECOMMENDATION_HISTORY_WINDOW_MS - DAILY_RECOMMENDATION_HARD_EXCLUSION_WINDOW_MS
    val recencyFactor = (DAILY_RECOMMENDATION_HISTORY_WINDOW_MS - ageMs)
        .coerceIn(0L, softWindowMs)
        .toDouble() / softWindowMs.toDouble()
    val frequencyPenalty = (summary.exposureCount * DAILY_RECOMMENDATION_FREQUENCY_PENALTY_STEP)
        .coerceAtMost(DAILY_RECOMMENDATION_MAX_FREQUENCY_PENALTY)
    return DAILY_RECOMMENDATION_MAX_RECENCY_PENALTY * recencyFactor + frequencyPenalty
}

internal fun rankDailyRecommendationTrackIds(
    tracks: List<TrackEntity>,
    favoriteTracks: List<FavoriteTrackEntity>,
    trackStats: Map<String, TrackPlaybackStatsEntity>,
    recentRecommendationExposures: List<DailyRecommendationExposure>,
    dateKey: String,
    nowMs: Long,
    limit: Int = DEFAULT_DAILY_RECOMMENDATION_LIMIT,
): List<String> {
    return rankDailyRecommendationTracks(
        tracks = tracks,
        favoriteTracks = favoriteTracks,
        trackStats = trackStats,
        recentRecommendationExposures = recentRecommendationExposures,
        dateKey = dateKey,
        nowMs = nowMs,
        limit = limit,
    ).trackIds
}

internal fun rankDailyRecommendationTracks(
    tracks: List<TrackEntity>,
    favoriteTracks: List<FavoriteTrackEntity>,
    trackStats: Map<String, TrackPlaybackStatsEntity>,
    recentRecommendationExposures: List<DailyRecommendationExposure>,
    dateKey: String,
    nowMs: Long,
    limit: Int = DEFAULT_DAILY_RECOMMENDATION_LIMIT,
): DailyRecommendationRankingResult {
    if (tracks.isEmpty() || limit <= 0) {
        return DailyRecommendationRankingResult(
            trackIds = emptyList(),
            candidateCount = tracks.size,
            hardExcludedCount = 0,
            softPenalizedCount = 0,
            backfilledCount = 0,
        )
    }
    val historyIndex = DailyRecommendationHistoryIndex(recentRecommendationExposures, nowMs)
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
            val playCount = stats?.playCount ?: 0
            val seededHash = stableRecommendationHash(dateKey, track.id)
            var baseScore = 0.0
            if (track.id in favoriteTrackIds) baseScore += 0.25
            baseScore += (playCount.toDouble() / maxPlayCount.toDouble()) * 0.25
            if (artistKey != null && (artistKey in favoriteArtistKeys || artistKey in frequentArtistKeys)) baseScore += 0.12
            if (albumKey != null && (albumKey in favoriteAlbumKeys || albumKey in frequentAlbumKeys)) baseScore += 0.10
            val lastPlayedAt = stats?.lastPlayedAt
            if (lastPlayedAt != null) {
                val ageMs = nowMs - lastPlayedAt
                if (ageMs >= THIRTY_DAYS_MS) baseScore += 0.12
                if (ageMs in 0..THREE_DAYS_MS) baseScore -= 0.30
            }
            val addedAgeMs = nowMs - track.addedAt
            if (addedAgeMs in 0..THIRTY_DAYS_MS && playCount <= 1) baseScore += 0.10
            baseScore += stableRecommendationFraction(seededHash) * 0.08
            val songIdentity = dailyRecommendationSongIdentity(track)
            val exposureSummary = historyIndex.summary(track, songIdentity)
            val historyPenalty = dailyRecommendationHistoryPenalty(exposureSummary, nowMs)
            DailyRecommendationCandidate(
                track = track,
                adjustedScore = baseScore - historyPenalty,
                seededHash = seededHash,
                artistKey = artistKey,
                albumKey = albumKey,
                songIdentity = songIdentity,
                exposureSummary = exposureSummary,
                hardExcluded = exposureSummary.isHardExcluded(nowMs),
            )
        }
    val regularComparator = compareByDescending<DailyRecommendationCandidate> { it.adjustedScore }
        .thenBy { it.seededHash }
        .thenBy { it.track.title.trim().lowercase() }
        .thenBy { it.track.id }
    val eligible = scored.filterNot { it.hardExcluded }.sortedWith(regularComparator)
    val hardExcluded = scored.filter { it.hardExcluded }.sortedWith(
        compareBy<DailyRecommendationCandidate> { it.exposureSummary.exposureCount }
            .thenBy { it.exposureSummary.lastRecommendedAt ?: Long.MIN_VALUE }
            .thenByDescending { it.adjustedScore }
            .thenBy { it.seededHash }
            .thenBy { it.track.id },
    )
    val selected = mutableListOf<DailyRecommendationCandidate>()
    val artistCounts = mutableMapOf<String, Int>()
    val albumCounts = mutableMapOf<String, Int>()

    fun addCandidate(candidate: DailyRecommendationCandidate, enforceArtistAndAlbumLimits: Boolean): Boolean {
        if (selected.size >= limit) return false
        if (selected.any { areSameDailyRecommendationSong(it.songIdentity, candidate.songIdentity) }) return false
        val artistCount = candidate.artistKey?.let { artistCounts[it] } ?: 0
        val albumCount = candidate.albumKey?.let { albumCounts[it] } ?: 0
        if (enforceArtistAndAlbumLimits && artistCount >= DAILY_RECOMMENDATION_ARTIST_LIMIT) return false
        if (enforceArtistAndAlbumLimits && albumCount >= DAILY_RECOMMENDATION_ALBUM_LIMIT) return false
        selected += candidate
        candidate.artistKey?.let { artistCounts[it] = artistCount + 1 }
        candidate.albumKey?.let { albumCounts[it] = albumCount + 1 }
        return true
    }

    eligible.forEach { candidate ->
        addCandidate(candidate, enforceArtistAndAlbumLimits = true)
    }
    if (selected.size < limit) {
        eligible.forEach { candidate ->
            if (selected.size >= limit) return@forEach
            addCandidate(candidate, enforceArtistAndAlbumLimits = false)
        }
    }
    var backfilledCount = 0
    if (selected.size < limit) {
        hardExcluded.forEach { candidate ->
            if (selected.size >= limit) return@forEach
            if (addCandidate(candidate, enforceArtistAndAlbumLimits = false)) {
                backfilledCount += 1
            }
        }
    }
    return DailyRecommendationRankingResult(
        trackIds = selected.take(limit).map { it.track.id },
        candidateCount = tracks.size,
        hardExcludedCount = hardExcluded.size,
        softPenalizedCount = eligible.count { it.exposureSummary.exposureCount > 0 },
        backfilledCount = backfilledCount,
    )
}

private class DailyRecommendationHistoryIndex(
    exposures: List<DailyRecommendationExposure>,
    nowMs: Long,
) {
    private val recentExposures = exposures.filter { exposure ->
        recommendationAgeMs(nowMs, exposure.recommendedAt) <= DAILY_RECOMMENDATION_HISTORY_WINDOW_MS
    }
    private val byTrackId = recentExposures.groupBy { it.trackId }
    private val byLogicalBase = recentExposures
        .mapNotNull { exposure ->
            val identity = exposure.track?.let(::dailyRecommendationSongIdentity) ?: return@mapNotNull null
            identity.logicalBaseKey()?.let { key -> key to exposure }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })

    fun summary(
        track: TrackEntity,
        identity: DailyRecommendationSongIdentity = dailyRecommendationSongIdentity(track),
    ): DailyRecommendationExposureSummary {
        val candidates = buildSet {
            addAll(byTrackId[track.id].orEmpty())
            identity.logicalBaseKey()?.let { key -> addAll(byLogicalBase[key].orEmpty()) }
        }
        val matching = candidates.filter { exposure ->
            exposure.trackId == track.id || exposure.track
                ?.let(::dailyRecommendationSongIdentity)
                ?.let { exposedIdentity -> areSameDailyRecommendationSong(identity, exposedIdentity) }
                ?: false
        }
        return DailyRecommendationExposureSummary(
            exposureCount = matching.size,
            lastRecommendedAt = matching.maxOfOrNull { it.recommendedAt },
        )
    }
}

private data class DailyRecommendationLogicalBaseKey(
    val titleKey: String,
    val artistKey: String,
)

private fun DailyRecommendationSongIdentity.logicalBaseKey(): DailyRecommendationLogicalBaseKey? {
    val title = titleKey ?: return null
    val artist = artistKey ?: return null
    return DailyRecommendationLogicalBaseKey(title, artist)
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

private fun String?.recommendationIdentityTextKey(): String? {
    val value = this?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return buildString {
        var separatorPending = false
        value.forEach { char ->
            if (char.isLetterOrDigit()) {
                if (separatorPending && isNotEmpty()) append(' ')
                append(char)
                separatorPending = false
            } else if (isNotEmpty()) {
                separatorPending = true
            }
        }
    }.takeIf { it.isNotBlank() }
}

private fun recommendationAgeMs(nowMs: Long, recommendedAt: Long): Long {
    return (nowMs - recommendedAt).coerceAtLeast(0L)
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

private data class RemoteRecentPlaySync(
    val trackId: String,
    val playedAt: Long?,
    val playCount: Int?,
)

private data class EmbyAlbumRecentSync(
    val lastPlayedAt: Long,
    val playCount: Int?,
)

private data class DailyRecommendationCandidate(
    val track: TrackEntity,
    val adjustedScore: Double,
    val seededHash: Long,
    val artistKey: String?,
    val albumKey: String?,
    val songIdentity: DailyRecommendationSongIdentity,
    val exposureSummary: DailyRecommendationExposureSummary,
    val hardExcluded: Boolean,
)

private const val DEFAULT_RECENT_ITEM_LIMIT = 20
private const val DEFAULT_DAILY_RECOMMENDATION_LIMIT = 30
private const val NAVIDROME_RECENT_ALBUM_FETCH_SIZE = 50
private const val THREE_DAYS_MS = 3L * 24L * 60L * 60L * 1_000L
private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1_000L
private const val DAILY_RECOMMENDATION_HARD_EXCLUSION_WINDOW_MS = 7L * 24L * 60L * 60L * 1_000L
private const val DAILY_RECOMMENDATION_HISTORY_WINDOW_MS = 30L * 24L * 60L * 60L * 1_000L
private const val DAILY_RECOMMENDATION_DURATION_TOLERANCE_MS = 5_000L
private const val DAILY_RECOMMENDATION_MAX_RECENCY_PENALTY = 0.40
private const val DAILY_RECOMMENDATION_FREQUENCY_PENALTY_STEP = 0.10
private const val DAILY_RECOMMENDATION_MAX_FREQUENCY_PENALTY = 0.30
private const val FREQUENT_PREFERENCE_SAMPLE_SIZE = 12
private const val DAILY_RECOMMENDATION_ARTIST_LIMIT = 2
private const val DAILY_RECOMMENDATION_ALBUM_LIMIT = 2
private const val MY_LOG_TAG = "My"
