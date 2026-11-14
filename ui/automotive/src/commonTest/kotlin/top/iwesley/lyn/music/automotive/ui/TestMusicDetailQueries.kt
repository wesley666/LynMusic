package top.iwesley.lyn.music.automotive.ui

import kotlinx.coroutines.flow.*
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.details.*
import top.iwesley.lyn.music.feature.library.deriveVisibleAlbums

internal class TestMusicDetailQueries(
    private val album: (AlbumDetailRequest) -> Flow<AlbumDetailData> = { flowOf(AlbumDetailData(it.albumId)) },
    private val artistTracks: (ArtistDetailRequest) -> Flow<List<Track>> = { flowOf(emptyList()) },
    private val artistAlbums: (ArtistDetailRequest) -> Flow<List<MusicDetailAlbum>> = { flowOf(emptyList()) },
) : MusicDetailQueries {
    constructor(tracks: () -> List<Track>) : this(
        album = { request ->
            val items = tracks()
            val album = deriveVisibleAlbums(items).firstOrNull { it.id == request.albumId }
            flowOf(AlbumDetailData(request.title ?: album?.title ?: items.firstOrNull()?.albumTitle.orEmpty(),
                request.artistName ?: album?.artistName, tracks = items))
        },
        artistTracks = { flowOf(tracks()) },
        artistAlbums = { flowOf(deriveVisibleAlbums(tracks()).map { MusicDetailAlbum(it) }) },
    )
    override fun observeAlbum(request: AlbumDetailRequest) = album(request)
    override fun observeArtistTracks(request: ArtistDetailRequest) = artistTracks(request)
    override fun observeArtistAlbums(request: ArtistDetailRequest) = artistAlbums(request)
}
