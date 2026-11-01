package top.iwesley.lyn.music.feature.details

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.*
import top.iwesley.lyn.music.feature.library.*

enum class MusicDetailKind { Album, Artist }

/** A query snapshot, independent of navigation, screen state, and the active library. */
data class MusicDetailRequest(
    val kind: MusicDetailKind,
    val entityId: String,
    val sourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    val onlineSourceId: String? = null,
    val title: String? = null,
    val artistName: String? = null,
    val artworkLocator: String? = null,
)

data class MusicDetailAlbum(
    val album: Album,
    val artworkLocator: String? = null,
    val artworkCacheKey: String? = null,
)

data class MusicDetailData(
    val title: String,
    val artistName: String? = null,
    val artworkLocator: String? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<MusicDetailAlbum> = emptyList(),
    val artworkCacheKey: String? = null,
)

class MusicDetailUnavailableException : IllegalStateException("The detail source is unavailable")

fun interface MusicDetailQueries {
    fun observe(request: MusicDetailRequest): Flow<MusicDetailData>
}

/** Contains only business queries. The caller owns collection, cancellation, retry, and UI state. */
class DefaultMusicDetailQueries(
    private val library: LibraryRepository,
    private val sources: ImportSourceRepository,
    private val online: NavidromeOnlineDataSource,
    private val downloads: OfflineDownloadRepository = NoopOfflineDownloadRepository,
) : MusicDetailQueries {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(request: MusicDetailRequest): Flow<MusicDetailData> {
        require(request.entityId.isNotBlank())
        val onlineSourceId = request.onlineSourceId
        if (onlineSourceId != null) {
            return sources.observeSources().map { items ->
                items.firstOrNull { it.source.id == onlineSourceId }?.source
            }.distinctUntilChanged().flatMapLatest { source ->
                flow {
                    if (source == null || !source.enabled || source.indexMode != ImportSourceIndexMode.ONLINE) {
                        throw MusicDetailUnavailableException()
                    }
                    emit(loadOnline(request, onlineSourceId))
                }
            }.flowOn(Dispatchers.Default)
        }
        return combine(library.tracks, sources.observeSources(), downloads.downloads) { tracks, sourceItems, offline ->
            val enabled = sourceItems.map { it.source }.filter {
                it.enabled && it.indexMode == ImportSourceIndexMode.LOCAL_INDEX
            }
            val filter = request.sourceFilter
            if (filter is LibrarySourceFilter.Source && enabled.none { it.id == filter.sourceId }) {
                throw MusicDetailUnavailableException()
            }
            val types = enabled.associate { it.id to it.type }
            val scoped = tracks.filter {
                it.sourceId in types && matchesLibrarySourceFilter(it, filter, types, offline)
            }
            when (request.kind) {
                MusicDetailKind.Album -> {
                    val selected = scoped.filter { track ->
                        track.albumTitle?.trim()?.takeIf(String::isNotBlank)?.let {
                            libraryAlbumId(track.artistName, it) == request.entityId
                        } == true
                    }.sortedWith(albumTrackOrder)
                    val album = deriveVisibleAlbums(selected).firstOrNull()
                    val artworkTrack = selected.artworkRepresentative()
                    MusicDetailData(
                        title = album?.title ?: request.title.orEmpty(),
                        artistName = album?.artistName ?: request.artistName,
                        artworkLocator = artworkTrack?.artworkLocator?.takeIf(String::isNotBlank) ?: request.artworkLocator,
                        tracks = selected,
                        artworkCacheKey = artworkTrack?.let(::trackArtworkCacheKey),
                    )
                }
                MusicDetailKind.Artist -> {
                    val selected = scoped.filter { track ->
                        track.artistName?.trim()?.takeIf(String::isNotBlank)?.let {
                            libraryArtistId(it) == request.entityId
                        } == true
                    }.sortedWith(artistTrackOrder)
                    val artworkTrack = selected.artworkRepresentative()
                    val tracksByAlbumId = selected.groupBy {
                        libraryAlbumId(it.artistName, it.albumTitle.orEmpty())
                    }
                    MusicDetailData(
                        title = deriveVisibleArtists(selected).firstOrNull()?.name ?: request.title.orEmpty(),
                        artworkLocator = artworkTrack?.artworkLocator?.takeIf(String::isNotBlank),
                        tracks = selected,
                        albums = deriveVisibleAlbums(selected).map { album ->
                            val albumArtworkTrack = tracksByAlbumId[album.id].orEmpty().artworkRepresentative()
                            MusicDetailAlbum(
                                album = album,
                                artworkLocator = albumArtworkTrack?.artworkLocator?.takeIf(String::isNotBlank),
                                artworkCacheKey = albumArtworkTrack?.let(::trackArtworkCacheKey),
                            )
                        },
                        artworkCacheKey = artworkTrack?.let(::trackArtworkCacheKey),
                    )
                }
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    private suspend fun loadOnline(request: MusicDetailRequest, sourceId: String): MusicDetailData =
        when (request.kind) {
            MusicDetailKind.Album -> {
                val tracks = online.albumTracks(sourceId, request.entityId).sortedWith(albumTrackOrder)
                MusicDetailData(
                    title = request.title?.takeIf(String::isNotBlank)
                        ?: tracks.firstNotNullOfOrNull { it.albumTitle?.takeIf(String::isNotBlank) }.orEmpty(),
                    artistName = request.artistName?.takeIf(String::isNotBlank)
                        ?: tracks.firstNotNullOfOrNull { it.artistName?.takeIf(String::isNotBlank) },
                    artworkLocator = request.artworkLocator ?: tracks.firstNotNullOfOrNull { it.artworkLocator },
                    tracks = tracks,
                    artworkCacheKey = albumArtworkCacheKey(sourceId, request.entityId, request.artistName, request.title),
                )
            }
            MusicDetailKind.Artist -> MusicDetailData(
                title = request.title.orEmpty(),
                albums = online.artistAlbums(sourceId, request.entityId).map {
                    MusicDetailAlbum(
                        album = it.album,
                        artworkLocator = it.artworkLocator,
                        artworkCacheKey = albumArtworkCacheKey(sourceId, it.album.id, it.album.artistName, it.album.title),
                    )
                },
            )
        }

    private fun List<Track>.artworkRepresentative(): Track? =
        firstOrNull { !it.artworkLocator.isNullOrBlank() } ?: firstOrNull()

    private companion object {
        val albumTrackOrder = compareBy<Track>(
            { it.discNumber ?: Int.MAX_VALUE }, { it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() },
        )
        val artistTrackOrder = compareBy<Track>(
            { it.albumTitle.orEmpty().lowercase() }, { it.discNumber ?: Int.MAX_VALUE },
            { it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() },
        )
    }
}
