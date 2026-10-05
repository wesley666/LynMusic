package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.resources.*

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock
import kotlin.time.Instant
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull
import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.parseEmbySongLocator
import top.iwesley.lyn.music.core.model.parseFnMusicSongLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.FavoriteTrackEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.domain.fetchEmbyFavorites
import top.iwesley.lyn.music.domain.FN_MUSIC_NAME
import top.iwesley.lyn.music.domain.fetchFnMusicFavorites
import top.iwesley.lyn.music.domain.resolveFnMusicSource
import top.iwesley.lyn.music.domain.setFnMusicFavorite
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.isSubsonicCompatibleSourceType
import top.iwesley.lyn.music.domain.normalizeSubsonicBaseUrl
import top.iwesley.lyn.music.domain.requestNavidromeJson
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.resolveEmbySource
import top.iwesley.lyn.music.domain.setEmbyFavorite
import top.iwesley.lyn.music.domain.toSubsonicAuthMode

interface FavoritesRepository {
    val favoriteTrackIds: Flow<Set<String>>
    val favoriteTracks: Flow<List<Track>>
    val favoriteTrackMetadata: Flow<Map<String, FavoriteTrackMetadata>>

    suspend fun toggleFavorite(track: Track): Result<Boolean>
    suspend fun setFavorite(track: Track, favorite: Boolean): Result<Boolean>
    suspend fun refreshNavidromeFavorites(): Result<Unit>
}

data class FavoriteTrackMetadata(
    val trackId: String,
    val favoritedAt: Long,
)

class RoomFavoritesRepository(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val httpClient: LyricsHttpClient,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    private val addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
) : FavoritesRepository {
    private val favoriteRows = database.favoriteTrackDao().observeAll()

    override val favoriteTrackIds: Flow<Set<String>> = favoriteRows
        .map { rows -> rows.mapTo(linkedSetOf()) { it.trackId } }

    override val favoriteTrackMetadata: Flow<Map<String, FavoriteTrackMetadata>> = favoriteRows
        .map { rows ->
            rows.associate { row ->
                row.trackId to FavoriteTrackMetadata(
                    trackId = row.trackId,
                    favoritedAt = row.favoritedAt,
                )
            }
        }

    override val favoriteTracks: Flow<List<Track>> = combine(
        favoriteRows,
        database.trackDao().observeAll(),
        database.importSourceDao().observeAll(),
        database.lyricsCacheDao().observeArtworkLocators(),
    ) { favorites, tracks, sources, artworkRows ->
        val enabledSourceIds = sources.asSequence()
            .filter { it.isLocalIndexedEnabled() }
            .map { it.id }
            .toSet()
        val artworkOverrides = effectiveArtworkOverridesByTrackId(artworkRows)
        val trackById = tracks
            .filter { it.sourceId in enabledSourceIds }
            .associate { track ->
                track.id to track.toDomain(artworkOverrides[track.id])
            }
        favorites.mapNotNull { trackById[it.trackId] }
    }

    override suspend fun toggleFavorite(track: Track): Result<Boolean> {
        val favorite = database.favoriteTrackDao().getByTrackId(track.id) == null
        return setFavorite(track, favorite)
    }

    override suspend fun setFavorite(track: Track, favorite: Boolean): Result<Boolean> {
        return runCatching {
            val existing = database.favoriteTrackDao().getByTrackId(track.id)
            if ((existing != null) == favorite) {
                return@runCatching favorite
            }
            val subsonicSong = parseSubsonicCompatibleSongLocator(track.mediaLocator)
                ?.takeIf { it.sourceId == track.sourceId }
            if (subsonicSong != null) {
                setSubsonicCompatibleFavorite(track, subsonicSong.itemId, existing, favorite)
            } else {
                val embySong = parseEmbySongLocator(track.mediaLocator)
                    ?.takeIf { it.first == track.sourceId }
                val fnMusicSong = parseFnMusicSongLocator(track.mediaLocator)
                    ?.takeIf { it.first == track.sourceId }
                if (embySong != null) {
                    setEmbyFavoriteTrack(track, embySong.second, favorite)
                } else if (fnMusicSong != null) {
                    setFnMusicFavoriteTrack(track, fnMusicSong.second, favorite)
                } else {
                    setLocalFavorite(track, existing, favorite)
                }
            }
        }
    }

    override suspend fun refreshNavidromeFavorites(): Result<Unit> {
        return runCatching {
            val failures = mutableListOf<UiText>()
            database.importSourceDao().getAll()
                .filter {
                    (it.subsonicCompatibleSourceType() != null || it.isEmbySource() || it.isFnMusicSource()) &&
                        it.isLocalIndexedEnabled()
                }
                .forEach { source ->
                    runCatching {
                        if (source.isEmbySource()) {
                            syncEmbyFavorites(source)
                        } else if (source.isFnMusicSource()) {
                            syncFnMusicFavorites(source)
                        } else {
                            syncSubsonicCompatibleFavorites(source)
                        }
                    }
                        .onFailure { throwable ->
                            failures += uiText(Res.string.favorites_source_error_detail,
                                source.label,
                                throwable.uiFailureTextOrNull() ?: throwable.uiErrorDetail(),
                            )
                            logger.error(FAVORITES_LOG_TAG, throwable) {
                                "refresh-failed source=${source.id} label=${source.label}"
                            }
                        }
                }
            if (failures.isNotEmpty()) {
                throw UiTextException(UiText.Joined(failures, separator = "\n"))
            }
        }
    }

    private suspend fun setEmbyFavoriteTrack(
        track: Track,
        remoteSongId: String,
        favorite: Boolean,
    ): Boolean {
        val resolvedSource = resolveEmbySource(database, secureCredentialStore, track.sourceId, addressSelector)
            ?: throw UiTextException(uiText(Res.string.favorites_source_unavailable_update, "Emby"))
        setEmbyFavorite(
            httpClient = httpClient,
            source = resolvedSource,
            itemId = remoteSongId,
            favorite = favorite,
            logger = logger,
        )
        return if (!favorite) {
            database.favoriteTrackDao().deleteByTrackId(track.id)
            logger.info(FAVORITES_LOG_TAG) { "unfavorite-emby track=${track.id} source=${track.sourceId} song=$remoteSongId" }
            false
        } else {
            database.favoriteTrackDao().upsert(
                FavoriteTrackEntity(
                    trackId = track.id,
                    sourceId = track.sourceId,
                    remoteSongId = remoteSongId,
                    favoritedAt = favoriteNow(),
                ),
            )
            logger.info(FAVORITES_LOG_TAG) { "favorite-emby track=${track.id} source=${track.sourceId} song=$remoteSongId" }
            true
        }
    }

    private suspend fun setFnMusicFavoriteTrack(
        track: Track,
        guid: String,
        favorite: Boolean,
    ): Boolean {
        val resolvedSource = resolveFnMusicSource(database, secureCredentialStore, track.sourceId)
            ?: throw UiTextException(uiText(Res.string.favorites_source_unavailable_update, FN_MUSIC_NAME))
        setFnMusicFavorite(
            httpClient = httpClient,
            source = resolvedSource,
            guid = guid,
            favorite = favorite,
            addressSelector = addressSelector,
            logger = logger,
        )
        return if (!favorite) {
            database.favoriteTrackDao().deleteByTrackId(track.id)
            logger.info(FAVORITES_LOG_TAG) { "unfavorite-fnmusic track=${track.id} source=${track.sourceId} song=$guid" }
            false
        } else {
            database.favoriteTrackDao().upsert(
                FavoriteTrackEntity(
                    trackId = track.id,
                    sourceId = track.sourceId,
                    remoteSongId = guid,
                    favoritedAt = favoriteNow(),
                ),
            )
            logger.info(FAVORITES_LOG_TAG) { "favorite-fnmusic track=${track.id} source=${track.sourceId} song=$guid" }
            true
        }
    }

    private suspend fun setLocalFavorite(
        track: Track,
        existing: FavoriteTrackEntity?,
        favorite: Boolean,
    ): Boolean {
        if (!favorite) {
            database.favoriteTrackDao().deleteByTrackId(track.id)
            logger.info(FAVORITES_LOG_TAG) { "unfavorite-local track=${track.id} source=${track.sourceId}" }
            return false
        }
        database.favoriteTrackDao().upsert(
            FavoriteTrackEntity(
                trackId = track.id,
                sourceId = track.sourceId,
                remoteSongId = null,
                favoritedAt = favoriteNow(),
            ),
        )
        logger.info(FAVORITES_LOG_TAG) { "favorite-local track=${track.id} source=${track.sourceId}" }
        return true
    }

    private suspend fun setSubsonicCompatibleFavorite(
        track: Track,
        remoteSongId: String,
        existing: FavoriteTrackEntity?,
        favorite: Boolean,
    ): Boolean {
        val resolvedSource = resolveSubsonicCompatibleSource(track.sourceId)
            ?: throw UiTextException(uiText(Res.string.favorites_source_unavailable_update, "Subsonic-compatible"))
        val endpoint = if (favorite) "star" else "unstar"
        requestNavidromeJson(
            httpClient = httpClient,
            source = resolvedSource,
            endpoint = endpoint,
            parameters = mapOf("id" to remoteSongId),
        )
        return if (!favorite) {
            database.favoriteTrackDao().deleteByTrackId(track.id)
            logger.info(FAVORITES_LOG_TAG) { "unfavorite-remote track=${track.id} source=${track.sourceId} song=$remoteSongId type=${resolvedSource.sourceType}" }
            false
        } else {
            database.favoriteTrackDao().upsert(
                FavoriteTrackEntity(
                    trackId = track.id,
                    sourceId = track.sourceId,
                    remoteSongId = remoteSongId,
                    favoritedAt = favoriteNow(),
                ),
            )
            logger.info(FAVORITES_LOG_TAG) { "favorite-remote track=${track.id} source=${track.sourceId} song=$remoteSongId type=${resolvedSource.sourceType}" }
            true
        }
    }

    private suspend fun syncEmbyFavorites(source: ImportSourceEntity) {
        val resolved = resolveEmbySource(database, secureCredentialStore, source.id, addressSelector)
            ?: throw UiTextException(uiText(Res.string.source_credentials_missing, "Emby"))
        val existingRows = database.favoriteTrackDao().getBySourceId(source.id)
        val existingByRemoteSongId = existingRows
            .mapNotNull { entity -> entity.remoteSongId?.let { it to entity } }
            .toMap()
        val syncedItems = fetchEmbyFavorites(
            httpClient = httpClient,
            source = resolved,
            logger = logger,
        )
        val newSongIds = syncedItems
            .map { it.itemId }
            .filterNot(existingByRemoteSongId::containsKey)
        val maxExistingFavoritedAt = existingRows.maxOfOrNull { it.favoritedAt }
        var nextNewFavoritedAt = maxOf(
            favoriteNow(),
            (maxExistingFavoritedAt ?: Long.MIN_VALUE) + newSongIds.size.toLong(),
        )
        val favoriteRows = syncedItems.map { item ->
            FavoriteTrackEntity(
                trackId = embyTrackIdFor(source.id, item.itemId),
                sourceId = source.id,
                remoteSongId = item.itemId,
                favoritedAt = item.favoritedAt
                    ?: existingByRemoteSongId[item.itemId]?.favoritedAt
                    ?: nextNewFavoritedAt--,
            )
        }
        database.immediateWriteTransaction {
            database.favoriteTrackDao().deleteBySourceId(source.id)
            if (favoriteRows.isNotEmpty()) {
                database.favoriteTrackDao().upsertAll(favoriteRows)
            }
        }
        logger.info(FAVORITES_LOG_TAG) { "refresh-emby-complete source=${source.id} favorites=${favoriteRows.size}" }
    }

    private suspend fun syncFnMusicFavorites(source: ImportSourceEntity) {
        val resolved = resolveFnMusicSource(database, secureCredentialStore, source.id)
            ?: throw UiTextException(uiText(Res.string.source_credentials_missing, FN_MUSIC_NAME))
        val existingRows = database.favoriteTrackDao().getBySourceId(source.id)
        val existingByRemoteSongId = existingRows
            .mapNotNull { entity -> entity.remoteSongId?.let { it to entity } }
            .toMap()
        val syncedGuids = fetchFnMusicFavorites(
            httpClient = httpClient,
            source = resolved,
            addressSelector = addressSelector,
            logger = logger,
        ).map { it.guid }
        val newSongIds = syncedGuids.filterNot(existingByRemoteSongId::containsKey)
        val maxExistingFavoritedAt = existingRows.maxOfOrNull { it.favoritedAt }
        var nextNewFavoritedAt = maxOf(
            favoriteNow(),
            (maxExistingFavoritedAt ?: Long.MIN_VALUE) + newSongIds.size.toLong(),
        )
        val favoriteRows = syncedGuids.map { guid ->
            FavoriteTrackEntity(
                trackId = fnMusicTrackIdFor(source.id, guid),
                sourceId = source.id,
                remoteSongId = guid,
                favoritedAt = existingByRemoteSongId[guid]?.favoritedAt ?: nextNewFavoritedAt--,
            )
        }
        database.immediateWriteTransaction {
            database.favoriteTrackDao().deleteBySourceId(source.id)
            if (favoriteRows.isNotEmpty()) {
                database.favoriteTrackDao().upsertAll(favoriteRows)
            }
        }
        logger.info(FAVORITES_LOG_TAG) { "refresh-fnmusic-complete source=${source.id} favorites=${favoriteRows.size}" }
    }

    private suspend fun syncSubsonicCompatibleFavorites(source: ImportSourceEntity) {
        val sourceType = source.subsonicCompatibleSourceType()
            ?: error("Subsonic-compatible 来源类型无效，无法同步喜欢。")
        val resolved = source.toSubsonicCompatibleResolvedSource()
            ?: throw UiTextException(uiText(Res.string.source_credentials_missing, resolvedSourceLabel(sourceType)))
        val payload = requestNavidromeJson(
            httpClient = httpClient,
            source = resolved,
            endpoint = "getStarred2",
        )
        val existingRows = database.favoriteTrackDao().getBySourceId(source.id)
        val existingByRemoteSongId = existingRows
            .mapNotNull { entity -> entity.remoteSongId?.let { it to entity } }
            .toMap()
        val syncedSongs = payload["starred2"].asJsonObjectOrNull()
            ?.get("song")
            .asJsonObjectList()
        val newSongIds = syncedSongs
            .mapNotNull { song -> song.string("id") }
            .filterNot(existingByRemoteSongId::containsKey)
        val maxExistingFavoritedAt = existingRows.maxOfOrNull { it.favoritedAt }
        var nextNewFavoritedAt = maxOf(
            favoriteNow(),
            (maxExistingFavoritedAt ?: Long.MIN_VALUE) + newSongIds.size.toLong(),
        )
        val favoriteRows = syncedSongs
            .mapNotNull { song ->
                val songId = song.string("id") ?: return@mapNotNull null
                FavoriteTrackEntity(
                    trackId = subsonicCompatibleTrackIdFor(source.id, songId, sourceType),
                    sourceId = source.id,
                    remoteSongId = songId,
                    favoritedAt = song.string("starred")?.let(::parseFavoriteTimestampMillis)
                        ?: existingByRemoteSongId[songId]?.favoritedAt
                        ?: nextNewFavoritedAt--,
                )
            }
        database.immediateWriteTransaction {
            database.favoriteTrackDao().deleteBySourceId(source.id)
            if (favoriteRows.isNotEmpty()) {
                database.favoriteTrackDao().upsertAll(favoriteRows)
            }
        }
        logger.info(FAVORITES_LOG_TAG) { "refresh-complete source=${source.id} favorites=${favoriteRows.size}" }
    }

    private suspend fun resolveSubsonicCompatibleSource(sourceId: String): NavidromeResolvedSource? {
        val source = database.importSourceDao().getById(sourceId)
            ?.takeIf { it.subsonicCompatibleSourceType() != null && it.isLocalIndexedEnabled() }
            ?: return null
        return source.toSubsonicCompatibleResolvedSource()
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

    private companion object {
        const val FAVORITES_LOG_TAG = "Favorites"
    }
}

private fun resolvedSourceLabel(sourceType: ImportSourceType): String {
    return if (sourceType == ImportSourceType.SUBSONIC) "Subsonic" else "Navidrome"
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

private fun parseFavoriteTimestampMillis(value: String): Long? {
    return runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()
}

private fun favoriteNow(): Long = Clock.System.now().toEpochMilliseconds()
