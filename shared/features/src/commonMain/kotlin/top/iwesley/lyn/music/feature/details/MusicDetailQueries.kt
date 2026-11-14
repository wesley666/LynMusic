package top.iwesley.lyn.music.feature.details

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.*
import top.iwesley.lyn.music.feature.library.*

/** Query context only; artist presentation comes from the navigation entry. */
data class ArtistDetailRequest(
    val artistId: String,
    val sourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    val onlineSourceId: String? = null,
)

data class AlbumDetailRequest(
    val albumId: String,
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

data class AlbumDetailData(
    val title: String,
    val artistName: String? = null,
    val artworkLocator: String? = null,
    val tracks: List<Track> = emptyList(),
    val artworkCacheKey: String? = null,
)

class MusicDetailUnavailableException : IllegalStateException("The detail source is unavailable")

interface MusicDetailQueries {
    fun observeArtistTracks(request: ArtistDetailRequest): Flow<List<Track>>
    fun observeArtistAlbums(request: ArtistDetailRequest): Flow<List<MusicDetailAlbum>>
    fun observeAlbum(request: AlbumDetailRequest): Flow<AlbumDetailData>
}

/** Business queries only. Callers own collection, cancellation, retry and loading state. */
class DefaultMusicDetailQueries(
    private val library: LibraryRepository,
    private val sources: ImportSourceRepository,
    private val online: NavidromeOnlineDataSource,
    private val downloads: OfflineDownloadRepository = NoopOfflineDownloadRepository,
) : MusicDetailQueries {
    override fun observeArtistTracks(request: ArtistDetailRequest): Flow<List<Track>> {
        require(request.artistId.isNotBlank())
        return request.onlineSourceId?.let { sourceId ->
            observeOnline(sourceId) { online.artistTracks(sourceId, request.artistId).sortedWith(artistTrackOrder) }
        } ?: observeLocalArtist(request)
    }

    override fun observeArtistAlbums(request: ArtistDetailRequest): Flow<List<MusicDetailAlbum>> {
        require(request.artistId.isNotBlank())
        return request.onlineSourceId?.let { sourceId ->
            observeOnline(sourceId) {
                online.artistAlbums(sourceId, request.artistId).map {
                    MusicDetailAlbum(it.album, it.artworkLocator,
                        albumArtworkCacheKey(sourceId, it.album.id, it.album.artistName, it.album.title))
                }
            }
        } ?: observeLocalArtist(request).map { tracks ->
            val byAlbum = tracks.groupBy { libraryAlbumId(it.artistName, it.albumTitle.orEmpty()) }
            deriveVisibleAlbums(tracks).map { album ->
                val artwork = byAlbum[album.id].orEmpty().artworkRepresentative()
                MusicDetailAlbum(album, artwork?.artworkLocator?.takeIf(String::isNotBlank),
                    artwork?.let(::trackArtworkCacheKey))
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    override fun observeAlbum(request: AlbumDetailRequest): Flow<AlbumDetailData> {
        require(request.albumId.isNotBlank())
        return request.onlineSourceId?.let { sourceId ->
            observeOnline(sourceId) {
                val tracks = online.albumTracks(sourceId, request.albumId).sortedWith(albumTrackOrder)
                AlbumDetailData(
                    title = request.title?.takeIf(String::isNotBlank)
                        ?: tracks.firstNotNullOfOrNull { it.albumTitle?.takeIf(String::isNotBlank) }.orEmpty(),
                    artistName = request.artistName?.takeIf(String::isNotBlank)
                        ?: tracks.firstNotNullOfOrNull { it.artistName?.takeIf(String::isNotBlank) },
                    artworkLocator = request.artworkLocator ?: tracks.firstNotNullOfOrNull { it.artworkLocator },
                    tracks = tracks,
                    artworkCacheKey = albumArtworkCacheKey(sourceId, request.albumId, request.artistName, request.title),
                )
            }
        } ?: observeLocalTracks(request.sourceFilter).map { scoped ->
            val tracks = scoped.filter { track ->
                track.albumTitle?.trim()?.takeIf(String::isNotBlank)?.let {
                    libraryAlbumId(track.artistName, it) == request.albumId
                } == true
            }.sortedWith(albumTrackOrder)
            val album = deriveVisibleAlbums(tracks).firstOrNull()
            val artwork = tracks.artworkRepresentative()
            AlbumDetailData(
                title = album?.title ?: request.title.orEmpty(),
                artistName = album?.artistName ?: request.artistName,
                artworkLocator = artwork?.artworkLocator?.takeIf(String::isNotBlank) ?: request.artworkLocator,
                tracks = tracks,
                artworkCacheKey = artwork?.let(::trackArtworkCacheKey),
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    private fun observeLocalArtist(request: ArtistDetailRequest): Flow<List<Track>> =
        observeLocalTracks(request.sourceFilter).map { scoped ->
            scoped.filter { track ->
                track.artistName?.trim()?.takeIf(String::isNotBlank)?.let {
                    libraryArtistId(it) == request.artistId
                } == true
            }.sortedWith(artistTrackOrder)
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private fun observeLocalTracks(filter: LibrarySourceFilter): Flow<List<Track>> =
        combine(library.tracks, sources.observeSources(), downloads.downloads) { tracks, sourceItems, offline ->
            val enabled = sourceItems.map { it.source }.filter {
                it.enabled && it.indexMode == ImportSourceIndexMode.LOCAL_INDEX
            }
            if (filter is LibrarySourceFilter.Source && enabled.none { it.id == filter.sourceId }) {
                throw MusicDetailUnavailableException()
            }
            val types = enabled.associate { it.id to it.type }
            tracks.filter { it.sourceId in types && matchesLibrarySourceFilter(it, filter, types, offline) }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observeOnline(sourceId: String, load: suspend () -> T): Flow<T> =
        sources.observeSources().map { items -> items.firstOrNull { it.source.id == sourceId }?.source }
            .distinctUntilChanged().flatMapLatest { source ->
                flow {
                    if (source == null || !source.enabled || source.indexMode != ImportSourceIndexMode.ONLINE) {
                        throw MusicDetailUnavailableException()
                    }
                    emit(load())
                }
            }.flowOn(Dispatchers.Default)

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
