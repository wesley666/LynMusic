@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package top.iwesley.lyn.music.feature.details

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.*
import top.iwesley.lyn.music.feature.library.*

class MusicDetailQueriesTest {
    @Test fun localAlbumUsesCompleteLibraryAndCapturedSourceFilter() = runTest {
        val a = track("a", "one"); val b = track("b", "two")
        val fixture = Fixture(listOf(a,b))
        val id = libraryAlbumId("Artist", "Album")
        assertEquals(listOf(a,b), fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album, id)).first().tracks)
        assertEquals(listOf(a), fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album, id, LibrarySourceFilter.Source("one"))).first().tracks)
        assertEquals(listOf(b), fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album, id, LibrarySourceFilter.Source("two"))).first().tracks)
        assertTrue(fixture.online.calls.isEmpty())
    }
    @Test fun localArtistContainsAllAlbumsAndOrdersSongs() = runTest {
        val a = track("a").copy(albumTitle="First", discNumber=1, trackNumber=2)
        val b = track("b").copy(albumTitle="First", discNumber=1, trackNumber=1)
        val c = track("c").copy(albumTitle="Second")
        val fixture=Fixture(listOf(c,a,b,track("other").copy(artistName="Other")))
        val data=fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Artist, libraryArtistId("Artist"))).first()
        assertEquals(listOf(b,a,c),data.tracks)
        assertEquals(setOf("First","Second"),data.albums.map { it.album.title }.toSet())
        assertEquals("Artist",data.title)
    }
    @Test fun albumSortsDiscBeforeTrackNumber() = runTest {
        val a=track("a").copy(discNumber=2,trackNumber=1)
        val b=track("b").copy(discNumber=1,trackNumber=2)
        val c=track("c").copy(discNumber=1,trackNumber=1)
        val fixture=Fixture(listOf(a,b,c))
        assertEquals(listOf(c,b,a),fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album,libraryAlbumId("Artist","Album"))).first().tracks)
    }
    @Test fun disabledAndOnlineSourcesAreExcludedFromLocalDetail() = runTest {
        val fixture=Fixture(listOf(track("a"),track("b","two"),track("c","remote")))
        fixture.sources.updateSources(listOf(source("one"),source("two",enabled=false),source("remote",online=true)))
        assertEquals(listOf("a"),fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Artist,libraryArtistId("Artist"))).first().tracks.map { it.id })
    }
    @Test fun missingLocalSourceNeverFallsBackToAllSources() = runTest {
        val fixture=Fixture(listOf(track("a","two")))
        fixture.sources.updateSources(listOf(source("two")))
        assertFailsWith<MusicDetailUnavailableException> {
            fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album,libraryAlbumId("Artist","Album"),LibrarySourceFilter.Source("one"))).first()
        }
    }
    @Test fun localQueryObservesContentChanges() = runTest {
        val fixture=Fixture(listOf(track("a")))
        val received = Channel<MusicDetailData>(Channel.UNLIMITED)
        backgroundScope.launch {
            fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Artist,libraryArtistId("Artist"))).collect { received.send(it) }
        }
        assertEquals(1, received.receive().tracks.size)
        fixture.library.tracks.value=listOf(track("a"),track("b"))
        assertEquals(2, received.receive().tracks.size)
    }
    @Test fun onlineAlbumsUseExplicitSourceAndKeepAlbumArtistSeed() = runTest {
        val fixture=Fixture(emptyList())
        fixture.online.tracks={source,id -> listOf(track(id,source).copy(artistName="Song artist"))}
        for (id in listOf("remote","second")) {
            val data=fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album,"same-id",onlineSourceId=id,title="Compilation",artistName="Various artists")).first()
            assertEquals("Various artists",data.artistName); assertEquals("Compilation",data.title)
            assertEquals(id,data.tracks.single().sourceId)
        }
        assertEquals(listOf("remote" to "same-id","second" to "same-id"),fixture.online.calls)
    }
    @Test fun onlineArtistReadsOnlyItsAlbumsAndAllowsMissingName() = runTest {
        val fixture=Fixture(emptyList())
        fixture.online.albums={_,_ -> listOf(OnlineAlbumItem(Album("album","Album","Artist")))}
        val data=fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Artist,"artist-id",onlineSourceId="remote")).first()
        assertEquals("album",data.albums.single().album.id)
        assertTrue(data.tracks.isEmpty());assertEquals(listOf("remote" to "artist-id"),fixture.online.calls)
    }
    @Test fun missingOnlineSourceDoesNotRequestAnotherSource() = runTest {
        val fixture=Fixture(emptyList())
        assertFailsWith<MusicDetailUnavailableException> {
            fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album,"id",onlineSourceId="missing")).first()
        }
        assertTrue(fixture.online.calls.isEmpty())
    }
    @Test fun sourceRemovalInvalidatesAnObservedOnlineDetail() = runTest {
        val fixture=Fixture(emptyList())
        val loaded = CompletableDeferred<Unit>()
        val failure = CompletableDeferred<Throwable>()
        backgroundScope.launch {
            fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album,"id",onlineSourceId="remote"))
                .catch { failure.complete(it) }.collect { loaded.complete(Unit) }
        }
        loaded.await()
        fixture.sources.updateSources(listOf(source("second",online=true)))
        assertIs<MusicDetailUnavailableException>(failure.await())
    }
    @Test fun emptyResultsRemainSuccessful() = runTest {
        val fixture=Fixture(emptyList())
        assertTrue(fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album,"id",onlineSourceId="remote")).first().tracks.isEmpty())
        assertTrue(fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Artist,"id",onlineSourceId="remote")).first().albums.isEmpty())
    }
    @Test fun downloadedFilterUsesExistingOfflineRules() = runTest {
        val fixture=Fixture(listOf(track("a"),track("b","two")))
        val data=fixture.queries.observe(MusicDetailRequest(MusicDetailKind.Album,libraryAlbumId("Artist","Album"),LibrarySourceFilter.DOWNLOADED)).first()
        assertEquals(listOf("a"),data.tracks.map { it.id })
    }
    private class Fixture(tracks:List<Track>) {
        val library=MemoryLibrary(tracks)
        val sources=FakeImportSourceRepository(listOf(source("one"),source("two"),source("remote",online=true),source("second",online=true)))
        val online=DetailOnlineSource()
        val queries=DefaultMusicDetailQueries(library,sources,online)
    }
}
private fun track(id:String,sourceId:String="one")=Track(id,sourceId,"Track $id","Artist","Album",mediaLocator="/$id",relativePath=id)
private fun source(id:String,enabled:Boolean=true,online:Boolean=false)=SourceWithStatus(ImportSource(id,if(id=="one") ImportSourceType.LOCAL_FOLDER else ImportSourceType.NAVIDROME,id,"https://example.invalid",enabled=enabled,indexMode=if(online) ImportSourceIndexMode.ONLINE else ImportSourceIndexMode.LOCAL_INDEX))
private class MemoryLibrary(initial:List<Track>):LibraryRepository {
    override val tracks=MutableStateFlow(initial)
    override val albums=tracks.map(::deriveVisibleAlbums)
    override val artists=tracks.map(::deriveVisibleArtists)
    override suspend fun getTracksByIds(trackIds:List<String>)=tracks.value.filter { it.id in trackIds }
}
private class DetailOnlineSource:NavidromeOnlineDataSource {
    val calls=mutableListOf<Pair<String,String>>()
    var tracks:suspend (String,String)->List<Track> = {_,_->emptyList()}
    var albums:suspend (String,String)->List<OnlineAlbumItem> = {_,_->emptyList()}
    override suspend fun tracks(sourceId: String, offset: Int, limit: Int): OnlinePage<Track> = error("Unexpected non-detail query")
    override suspend fun albums(sourceId: String, offset: Int, limit: Int): OnlinePage<OnlineAlbumItem> = error("Unexpected non-detail query")
    override suspend fun artists(sourceId: String, offset: Int, limit: Int): OnlinePage<OnlineArtistItem> = error("Unexpected non-detail query")
    override suspend fun search(sourceId: String, query: String, offset: Int, limit: Int): OnlineLibrarySearchResult = error("Unexpected non-detail query")
    override suspend fun searchTracks(sourceId: String, query: String, offset: Int, limit: Int): OnlinePage<Track> = error("Unexpected non-detail query")
    override suspend fun searchAlbums(sourceId: String, query: String, offset: Int, limit: Int): OnlinePage<OnlineAlbumItem> = error("Unexpected non-detail query")
    override suspend fun searchArtists(sourceId: String, query: String, offset: Int, limit: Int): OnlinePage<OnlineArtistItem> = error("Unexpected non-detail query")
    override suspend fun artistAlbums(sourceId: String, artistId: String): List<OnlineAlbumItem> { calls += sourceId to artistId; return albums(sourceId, artistId) }
    override suspend fun albumTracks(sourceId: String, albumId: String): List<Track> { calls += sourceId to albumId; return tracks(sourceId, albumId) }
    override suspend fun favoriteTracks(sourceId: String, offset: Int, limit: Int, query: String): OnlinePage<Track> = error("Unexpected non-detail query")
    override suspend fun setFavorite(sourceId: String, track: Track, favorite: Boolean) = error("Unexpected non-detail query")
    override suspend fun playlists(sourceId: String): List<PlaylistSummary> = error("Unexpected non-detail query")
    override suspend fun playlistDetail(sourceId: String, playlistId: String): PlaylistDetail? = error("Unexpected non-detail query")
    override suspend fun createPlaylist(sourceId: String, name: String): PlaylistSummary = error("Unexpected non-detail query")
    override suspend fun renamePlaylist(sourceId: String, playlistId: String, name: String) = error("Unexpected non-detail query")
    override suspend fun deletePlaylist(sourceId: String, playlistId: String) = error("Unexpected non-detail query")
    override suspend fun addTrackToPlaylist(sourceId: String, playlistId: String, track: Track) = error("Unexpected non-detail query")
    override suspend fun importPlaylistText(sourceId: String, playlistId: String, text: String): PlaylistImportReport = error("Unexpected non-detail query")
    override suspend fun removeTrackFromPlaylist(sourceId: String, playlistId: String, index: Int) = error("Unexpected non-detail query")
}
private class FakeImportSourceRepository(
    sources: List<SourceWithStatus> = emptyList(),
) : ImportSourceRepository {
    private val mutableSources = MutableStateFlow(sources)

    fun updateSources(sources: List<SourceWithStatus>) {
        mutableSources.value = sources
    }

    override fun observeSources(): Flow<List<SourceWithStatus>> = mutableSources.asStateFlow()

    override suspend fun importLocalFolder(): Result<ImportScanSummary?> = Result.success(testScanSummary())

    override suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun testUpdatedSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addSambaSource(draft: SambaSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun testUpdatedWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun probeNavidromeSource(draft: NavidromeSourceDraft): Result<NavidromeLibraryProbe> {
        return Result.success(NavidromeLibraryProbe(totalTrackCount = null))
    }

    override suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun addNavidromeSourceOnline(
        draft: NavidromeSourceDraft,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun probeExistingNavidromeSource(sourceId: String): Result<NavidromeLibraryProbe> {
        return Result.success(NavidromeLibraryProbe(totalTrackCount = null))
    }

    override suspend fun switchNavidromeSourceToOnline(
        sourceId: String,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun rescanSource(sourceId: String): Result<ImportScanSummary?> = Result.success(testScanSummary(sourceId))

    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun deleteSource(sourceId: String): Result<Unit> = Result.success(Unit)
}
private fun testScanSummary(sourceId: String = "source-1"): ImportScanSummary {
    return ImportScanSummary(
        sourceId = sourceId,
        discoveredAudioFileCount = 1,
        importedTrackCount = 1,
    )
}
