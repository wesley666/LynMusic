package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.db.AlbumEntity
import top.iwesley.lyn.music.data.db.ArtistEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.TrackEntity

data class MusicTagSaveResult(
    val track: Track,
    val snapshot: AudioTagSnapshot,
)

interface MusicTagsRepository {
    val localTracks: Flow<List<Track>>

    suspend fun canEdit(track: Track): Boolean
    suspend fun canWrite(track: Track): Boolean
    suspend fun readTags(track: Track): Result<AudioTagSnapshot>
    suspend fun refreshTags(track: Track): Result<MusicTagSaveResult>
    suspend fun saveTags(track: Track, patch: AudioTagPatch): Result<MusicTagSaveResult>
}

class RoomMusicTagsRepository(
    private val database: LynMusicDatabase,
    private val audioTagGateway: AudioTagGateway,
) : MusicTagsRepository {
    override val localTracks: Flow<List<Track>> = combine(
        database.trackDao().observeAll(),
        database.importSourceDao().observeAll(),
    ) { tracks, sources ->
        val localSourceIds = sources
            .filter { it.type == ImportSourceType.LOCAL_FOLDER.name }
            .mapTo(linkedSetOf()) { it.id }
        tracks.filter { it.sourceId in localSourceIds }.map(TrackEntity::toDomain)
    }

    override suspend fun canEdit(track: Track): Boolean = audioTagGateway.canEdit(track)

    override suspend fun canWrite(track: Track): Boolean = audioTagGateway.canWrite(track)

    override suspend fun readTags(track: Track): Result<AudioTagSnapshot> = audioTagGateway.read(track)

    override suspend fun refreshTags(track: Track): Result<MusicTagSaveResult> {
        return runCatching {
            if (!audioTagGateway.canEdit(track)) {
                error("当前歌曲不支持标签读取。")
            }
            val snapshot = audioTagGateway.read(track).getOrThrow()
            val updatedTrack = persistTrackSnapshot(track, snapshot)
            MusicTagSaveResult(track = updatedTrack, snapshot = snapshot)
        }
    }

    override suspend fun saveTags(track: Track, patch: AudioTagPatch): Result<MusicTagSaveResult> {
        return runCatching {
            if (!audioTagGateway.canWrite(track)) {
                error("当前平台暂不支持本地标签写回。")
            }
            val snapshot = audioTagGateway.write(track, patch).getOrThrow()
            val updatedTrack = persistTrackSnapshot(track, snapshot)
            MusicTagSaveResult(track = updatedTrack, snapshot = snapshot)
        }
    }

    private suspend fun persistTrackSnapshot(
        track: Track,
        snapshot: AudioTagSnapshot,
    ): Track {
        val artistName = snapshot.artistName?.trim()?.takeIf { it.isNotBlank() }
        val albumTitle = snapshot.albumTitle?.trim()?.takeIf { it.isNotBlank() }
        val updatedAt = Clock.System.now().toEpochMilliseconds()
        database.trackDao().updateLibraryMetadata(
            trackId = track.id,
            title = snapshot.title,
            artistId = artistName?.let(::artistIdForLibraryMetadata),
            artistName = artistName,
            albumId = albumTitle?.let { albumIdForLibraryMetadata(artistName, it) },
            albumTitle = albumTitle,
            trackNumber = snapshot.trackNumber,
            discNumber = snapshot.discNumber,
            artworkLocator = snapshot.artworkLocator,
            modifiedAt = updatedAt,
        )
        rebuildLibrarySummaries(database)
        return database.trackDao().getByIds(listOf(track.id)).firstOrNull()?.toDomain()
            ?: track.copy(
                title = snapshot.title,
                artistName = artistName,
                albumTitle = albumTitle,
                trackNumber = snapshot.trackNumber,
                discNumber = snapshot.discNumber,
                artworkLocator = snapshot.artworkLocator,
                modifiedAt = updatedAt,
            )
    }
}

internal suspend fun rebuildLibrarySummaries(database: LynMusicDatabase) {
    val tracks = database.trackDao().getAll()
    database.artistDao().deleteAll()
    database.albumDao().deleteAll()

    val artistEntities = tracks
        .mapNotNull { track -> track.artistName?.takeIf { it.isNotBlank() } }
        .groupingBy { it }
        .eachCount()
        .map { (artistName, trackCount) ->
            ArtistEntity(
                id = artistIdForLibraryMetadata(artistName),
                name = artistName,
                trackCount = trackCount,
            )
        }

    val albumEntities = tracks
        .mapNotNull { track ->
            track.albumTitle?.takeIf { it.isNotBlank() }?.let { albumTitle ->
                Triple(
                    albumIdForLibraryMetadata(track.artistName, albumTitle),
                    albumTitle,
                    track.artistName,
                )
            }
        }
        .groupBy { it.first }
        .map { (albumId, items) ->
            val first = items.first()
            AlbumEntity(
                id = albumId,
                title = first.second,
                artistName = first.third,
                trackCount = items.size,
            )
        }

    if (artistEntities.isNotEmpty()) {
        database.artistDao().upsertAll(artistEntities)
    }
    if (albumEntities.isNotEmpty()) {
        database.albumDao().upsertAll(albumEntities)
    }
}

internal fun artistIdForLibraryMetadata(name: String): String = "artist:${name.trim().lowercase()}"

internal fun albumIdForLibraryMetadata(artistName: String?, albumTitle: String): String {
    return "album:${artistName.orEmpty().trim().lowercase()}:${albumTitle.trim().lowercase()}"
}
