package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import io.ktor.http.parseUrl
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.PlaylistTrackEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

class PlaylistsRepositoryTest {

    @Test
    fun `create playlist and add local track persists membership`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(listOf(localTrackEntity()))
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(),
            httpClient = RecordingPlaylistsHttpClient(),
            logger = NoopDiagnosticLogger,
        )

        val playlist = repository.createPlaylist("晨跑").getOrThrow()
        repository.addTrackToPlaylist(playlist.id, localTrack()).getOrThrow()

        val detail = repository.observePlaylistDetail(playlist.id).first()
        assertNotNull(detail)
        assertEquals(listOf(localTrack().id), detail.tracks.map { it.track.id })
        assertEquals(setOf(localTrack().id), repository.playlists.first().first().memberTrackIds)
    }

    @Test
    fun `playlist summary artwork uses newest visible playlist track`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(
            listOf(
                localTrackEntity(id = "track-old", title = "Old", artworkLocator = "/art/old.jpg"),
                localTrackEntity(id = "track-new", title = "New", artworkLocator = "/art/new.jpg"),
            ),
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("封面测试").getOrThrow()
        database.playlistTrackDao().upsertAll(
            listOf(
                playlistTrackEntity(playlist.id, "track-old", addedAt = 10L, localOrdinal = 0),
                playlistTrackEntity(playlist.id, "track-new", addedAt = 20L, localOrdinal = 1),
            ),
        )

        val summary = repository.playlists.first().single()

        assertEquals("/art/new.jpg", summary.artworkLocator)
    }

    @Test
    fun `playlist summary artwork ignores disabled sources and missing tracks`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity(sourceId = "enabled"))
        database.importSourceDao().upsert(localSourceEntity(sourceId = "disabled", enabled = false))
        database.trackDao().upsertAll(
            listOf(
                localTrackEntity(id = "track-enabled", sourceId = "enabled", artworkLocator = "/art/enabled.jpg"),
                localTrackEntity(id = "track-disabled", sourceId = "disabled", artworkLocator = "/art/disabled.jpg"),
            ),
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("过滤测试").getOrThrow()
        database.playlistTrackDao().upsertAll(
            listOf(
                playlistTrackEntity(playlist.id, "track-enabled", sourceId = "enabled", addedAt = 10L),
                playlistTrackEntity(playlist.id, "track-disabled", sourceId = "disabled", addedAt = 30L),
                playlistTrackEntity(playlist.id, "track-missing", sourceId = "enabled", addedAt = 40L),
            ),
        )

        val summary = repository.playlists.first().single()

        assertEquals(1, summary.trackCount)
        assertEquals("/art/enabled.jpg", summary.artworkLocator)
    }

    @Test
    fun `playlist summary artwork uses artwork override`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(
            listOf(localTrackEntity(id = "track-override", artworkLocator = "/art/original.jpg")),
        )
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = "track-override",
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = "",
                updatedAt = 100L,
                artworkLocator = "/art/manual.jpg",
            ),
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("覆盖测试").getOrThrow()
        database.playlistTrackDao().upsert(
            playlistTrackEntity(playlist.id, "track-override", addedAt = 10L),
        )

        val summary = repository.playlists.first().single()

        assertEquals("/art/manual.jpg", summary.artworkLocator)
    }

    @Test
    fun `playlist summary artwork resolves same time by newer ordinal`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(
            listOf(
                localTrackEntity(id = "track-low", title = "Low", artworkLocator = "/art/low.jpg"),
                localTrackEntity(id = "track-high", title = "High", artworkLocator = "/art/high.jpg"),
            ),
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("顺序测试").getOrThrow()
        database.playlistTrackDao().upsertAll(
            listOf(
                playlistTrackEntity(playlist.id, "track-low", addedAt = 10L, localOrdinal = 1),
                playlistTrackEntity(playlist.id, "track-high", addedAt = 10L, localOrdinal = null, remoteOrdinal = 2),
            ),
        )

        val summary = repository.playlists.first().single()

        assertEquals("/art/high.jpg", summary.artworkLocator)
    }

    @Test
    fun `adding navidrome track creates remote binding and syncs membership`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(database, sourceId = "nav-a", username = "alpha", credentialKey = "cred-a", label = "Alpha")
        database.trackDao().upsertAll(listOf(navidromeTrackEntity(sourceId = "nav-a", songId = "song-a1")))
        val httpClient = RecordingPlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "alpha" to linkedMapOf(),
            ),
        )
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-a" to "pass-a")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val playlist = repository.createPlaylist("Road Trip").getOrThrow()
        repository.addTrackToPlaylist(
            playlistId = playlist.id,
            track = navidromeTrack(sourceId = "nav-a", songId = "song-a1"),
        ).getOrThrow()

        val detail = repository.observePlaylistDetail(playlist.id).first()
        assertNotNull(detail)
        assertEquals(listOf(navidromeTrack(sourceId = "nav-a", songId = "song-a1").id), detail.tracks.map { it.track.id })
        assertNotNull(database.playlistRemoteBindingDao().getByPlaylistIdAndSourceId(playlist.id, "nav-a"))
        assertTrue(httpClient.requestedEndpoints.contains("createPlaylist"))
        assertTrue(httpClient.requestedEndpoints.contains("updatePlaylist"))
        assertTrue(httpClient.requestedEndpoints.contains("getPlaylist"))
    }

    @Test
    fun `refresh merges same-name remote playlists across sources`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(database, sourceId = "nav-a", username = "alpha", credentialKey = "cred-a", label = "Alpha")
        seedNavidromeSource(database, sourceId = "nav-b", username = "beta", credentialKey = "cred-b", label = "Beta")
        database.trackDao().upsertAll(
            listOf(
                navidromeTrackEntity(sourceId = "nav-a", songId = "song-a1"),
                navidromeTrackEntity(sourceId = "nav-b", songId = "song-b1"),
            ),
        )
        val httpClient = RecordingPlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "alpha" to linkedMapOf(
                    "pa" to RemotePlaylistState(id = "pa", name = "Chill", songIds = mutableListOf("song-a1")),
                ),
                "beta" to linkedMapOf(
                    "pb" to RemotePlaylistState(id = "pb", name = "Chill", songIds = mutableListOf("song-b1")),
                ),
            ),
        )
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("cred-a" to "pass-a", "cred-b" to "pass-b"),
            ),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        repository.refreshNavidromePlaylists().getOrThrow()

        val playlists = repository.playlists.first()
        assertEquals(1, playlists.size)
        assertEquals("Chill", playlists.first().name)
        assertEquals(2, playlists.first().trackCount)
        val detail = repository.observePlaylistDetail(playlists.first().id).first()
        assertNotNull(detail)
        assertEquals(
            setOf(
                navidromeTrack(sourceId = "nav-a", songId = "song-a1").id,
                navidromeTrack(sourceId = "nav-b", songId = "song-b1").id,
            ),
            detail.tracks.mapTo(linkedSetOf()) { it.track.id },
        )
    }

    @Test
    fun `refresh replaces only the changed remote source subset`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        seedNavidromeSource(database, sourceId = "nav-a", username = "alpha", credentialKey = "cred-a", label = "Alpha")
        seedNavidromeSource(database, sourceId = "nav-b", username = "beta", credentialKey = "cred-b", label = "Beta")
        database.trackDao().upsertAll(
            listOf(
                localTrackEntity(),
                navidromeTrackEntity(sourceId = "nav-a", songId = "song-a1"),
                navidromeTrackEntity(sourceId = "nav-a", songId = "song-a2"),
                navidromeTrackEntity(sourceId = "nav-b", songId = "song-b1"),
            ),
        )
        val httpClient = RecordingPlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "alpha" to linkedMapOf(
                    "pa" to RemotePlaylistState(id = "pa", name = "Focus", songIds = mutableListOf("song-a1")),
                ),
                "beta" to linkedMapOf(
                    "pb" to RemotePlaylistState(id = "pb", name = "Focus", songIds = mutableListOf("song-b1")),
                ),
            ),
        )
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("cred-a" to "pass-a", "cred-b" to "pass-b"),
            ),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val localPlaylist = repository.createPlaylist("Focus").getOrThrow()
        repository.addTrackToPlaylist(localPlaylist.id, localTrack()).getOrThrow()
        repository.refreshNavidromePlaylists().getOrThrow()

        httpClient.remotePlaylistsByUser.getValue("alpha").getValue("pa").songIds.apply {
            clear()
            add("song-a2")
        }
        repository.refreshNavidromePlaylists().getOrThrow()

        val detail = repository.observePlaylistDetail(localPlaylist.id).first()
        assertNotNull(detail)
        assertEquals(
            listOf(
                localTrack().id,
                navidromeTrack(sourceId = "nav-a", songId = "song-a2").id,
                navidromeTrack(sourceId = "nav-b", songId = "song-b1").id,
            ),
            detail.tracks.map { it.track.id },
        )
    }

    @Test
    fun `refresh keeps updatedAt stable when remote playlist is unchanged`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(database, sourceId = "nav-a", username = "alpha", credentialKey = "cred-a", label = "Alpha")
        database.trackDao().upsertAll(listOf(navidromeTrackEntity(sourceId = "nav-a", songId = "song-a1")))
        val httpClient = RecordingPlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "alpha" to linkedMapOf(
                    "pa" to RemotePlaylistState(id = "pa", name = "Focus", songIds = mutableListOf("song-a1")),
                ),
            ),
        )
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-a" to "pass-a")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        repository.refreshNavidromePlaylists().getOrThrow()
        val firstPlaylist = repository.playlists.first().single()
        while (now() <= firstPlaylist.updatedAt) {
            Thread.sleep(1L)
        }

        repository.refreshNavidromePlaylists().getOrThrow()

        val secondPlaylist = repository.playlists.first().single()
        assertEquals(firstPlaylist.id, secondPlaylist.id)
        assertEquals(firstPlaylist.updatedAt, secondPlaylist.updatedAt)
        assertEquals(firstPlaylist.trackCount, secondPlaylist.trackCount)
    }

    @Test
    fun `delete playlist removes local playlist and relations`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(listOf(localTrackEntity()))
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(),
            httpClient = RecordingPlaylistsHttpClient(),
            logger = NoopDiagnosticLogger,
        )

        val playlist = repository.createPlaylist("晨跑").getOrThrow()
        repository.addTrackToPlaylist(playlist.id, localTrack()).getOrThrow()

        repository.deletePlaylist(playlist.id).getOrThrow()

        assertNull(database.playlistDao().getById(playlist.id))
        assertTrue(database.playlistTrackDao().getByPlaylistId(playlist.id).isEmpty())
        assertTrue(database.playlistRemoteBindingDao().getByPlaylistId(playlist.id).isEmpty())
        assertTrue(repository.playlists.first().isEmpty())
    }

    @Test
    fun `delete playlist removes remote playlist before local cleanup`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(database, sourceId = "nav-a", username = "alpha", credentialKey = "cred-a", label = "Alpha")
        database.trackDao().upsertAll(listOf(navidromeTrackEntity(sourceId = "nav-a", songId = "song-a1")))
        val httpClient = RecordingPlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "alpha" to linkedMapOf(),
            ),
        )
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-a" to "pass-a")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val playlist = repository.createPlaylist("Road Trip").getOrThrow()
        repository.addTrackToPlaylist(
            playlistId = playlist.id,
            track = navidromeTrack(sourceId = "nav-a", songId = "song-a1"),
        ).getOrThrow()
        val binding = database.playlistRemoteBindingDao().getByPlaylistIdAndSourceId(playlist.id, "nav-a")

        repository.deletePlaylist(playlist.id).getOrThrow()

        assertNotNull(binding)
        assertFalse(httpClient.remotePlaylistsByUser.getValue("alpha").containsKey(binding.remotePlaylistId))
        assertTrue(httpClient.requestedEndpoints.contains("deletePlaylist"))
        assertNull(database.playlistDao().getById(playlist.id))
        assertTrue(database.playlistRemoteBindingDao().getByPlaylistId(playlist.id).isEmpty())
    }

    @Test
    fun `delete playlist removes all remote bindings for merged playlist`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(database, sourceId = "nav-a", username = "alpha", credentialKey = "cred-a", label = "Alpha")
        seedNavidromeSource(database, sourceId = "nav-b", username = "beta", credentialKey = "cred-b", label = "Beta")
        database.trackDao().upsertAll(
            listOf(
                navidromeTrackEntity(sourceId = "nav-a", songId = "song-a1"),
                navidromeTrackEntity(sourceId = "nav-b", songId = "song-b1"),
            ),
        )
        val httpClient = RecordingPlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "alpha" to linkedMapOf(
                    "pa" to RemotePlaylistState(id = "pa", name = "Chill", songIds = mutableListOf("song-a1")),
                ),
                "beta" to linkedMapOf(
                    "pb" to RemotePlaylistState(id = "pb", name = "Chill", songIds = mutableListOf("song-b1")),
                ),
            ),
        )
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("cred-a" to "pass-a", "cred-b" to "pass-b"),
            ),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        repository.refreshNavidromePlaylists().getOrThrow()
        val playlist = repository.playlists.first().single()

        repository.deletePlaylist(playlist.id).getOrThrow()

        assertTrue(httpClient.remotePlaylistsByUser.getValue("alpha").isEmpty())
        assertTrue(httpClient.remotePlaylistsByUser.getValue("beta").isEmpty())
        assertEquals(2, httpClient.requestedEndpoints.count { it == "deletePlaylist" })
        assertNull(database.playlistDao().getById(playlist.id))
    }

    @Test
    fun `delete playlist failure keeps local playlist intact`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(database, sourceId = "nav-a", username = "alpha", credentialKey = "cred-a", label = "Alpha")
        database.trackDao().upsertAll(listOf(navidromeTrackEntity(sourceId = "nav-a", songId = "song-a1")))
        val httpClient = RecordingPlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "alpha" to linkedMapOf(),
            ),
            failingDeletePlaylistIds = mutableSetOf("pl-alpha-1"),
        )
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-a" to "pass-a")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val playlist = repository.createPlaylist("Road Trip").getOrThrow()
        repository.addTrackToPlaylist(
            playlistId = playlist.id,
            track = navidromeTrack(sourceId = "nav-a", songId = "song-a1"),
        ).getOrThrow()

        val result = repository.deletePlaylist(playlist.id)

        assertTrue(result.isFailure)
        assertNotNull(database.playlistDao().getById(playlist.id))
        assertTrue(database.playlistTrackDao().getByPlaylistId(playlist.id).isNotEmpty())
        assertTrue(database.playlistRemoteBindingDao().getByPlaylistId(playlist.id).isNotEmpty())
        assertTrue(httpClient.remotePlaylistsByUser.getValue("alpha").containsKey("pl-alpha-1"))
    }
}

private fun createPlaylistTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-playlists", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private fun playlistRepository(database: LynMusicDatabase): RoomPlaylistRepository {
    return RoomPlaylistRepository(
        database = database,
        secureCredentialStore = MapPlaylistSecureCredentialStore(),
        httpClient = RecordingPlaylistsHttpClient(),
        logger = NoopDiagnosticLogger,
    )
}

private suspend fun seedNavidromeSource(
    database: LynMusicDatabase,
    sourceId: String,
    username: String,
    credentialKey: String,
    label: String,
) {
    database.importSourceDao().upsert(
        ImportSourceEntity(
            id = sourceId,
            type = "NAVIDROME",
            label = label,
            rootReference = "https://$username.example.com/navidrome",
            server = null,
            shareName = null,
            directoryPath = null,
            username = username,
            credentialKey = credentialKey,
            allowInsecureTls = false,
            lastScannedAt = null,
            createdAt = 1L,
        ),
    )
}

private fun localSourceEntity(
    sourceId: String = "local-1",
    enabled: Boolean = true,
): ImportSourceEntity {
    return ImportSourceEntity(
        id = sourceId,
        type = "LOCAL_FOLDER",
        label = "下载目录",
        rootReference = "folder://downloads",
        server = null,
        shareName = null,
        directoryPath = null,
        username = null,
        credentialKey = null,
        allowInsecureTls = false,
        enabled = enabled,
        lastScannedAt = null,
        createdAt = 1L,
    )
}

private fun localTrack(): Track {
    return Track(
        id = "track:local-1:artist a/morning light.mp3",
        sourceId = "local-1",
        title = "Morning Light",
        artistName = "Artist A",
        albumTitle = "Album One",
        durationMs = 210_000L,
        mediaLocator = "file:///music/morning-light.mp3",
        relativePath = "Artist A/Morning Light.mp3",
    )
}

private fun localTrackEntity(
    id: String = localTrack().id,
    sourceId: String = "local-1",
    title: String = "Morning Light",
    artworkLocator: String? = null,
): TrackEntity {
    return TrackEntity(
        id = id,
        sourceId = sourceId,
        title = title,
        artistId = "artist:artist a",
        artistName = "Artist A",
        albumId = "album:artist a:album one",
        albumTitle = "Album One",
        durationMs = 210_000L,
        trackNumber = 1,
        discNumber = 1,
        mediaLocator = "file:///music/$id.mp3",
        relativePath = "Artist A/$title.mp3",
        artworkLocator = artworkLocator,
        sizeBytes = 0L,
        modifiedAt = 0L,
    )
}

private fun playlistTrackEntity(
    playlistId: String,
    trackId: String,
    sourceId: String = "local-1",
    addedAt: Long,
    localOrdinal: Int? = 0,
    remoteOrdinal: Int? = null,
): PlaylistTrackEntity {
    return PlaylistTrackEntity(
        playlistId = playlistId,
        trackId = trackId,
        sourceId = sourceId,
        addedAt = addedAt,
        localOrdinal = localOrdinal,
        remoteOrdinal = remoteOrdinal,
    )
}

private fun navidromeTrack(sourceId: String, songId: String): Track {
    return Track(
        id = navidromeTrackIdFor(sourceId, songId),
        sourceId = sourceId,
        title = "Song $songId",
        artistName = "Artist $sourceId",
        albumTitle = "Album $sourceId",
        durationMs = 215_000L,
        mediaLocator = buildNavidromeSongLocator(sourceId, songId),
        relativePath = "Artist $sourceId/Album $sourceId/Song $songId.flac",
    )
}

private fun navidromeTrackEntity(sourceId: String, songId: String): TrackEntity {
    return TrackEntity(
        id = navidromeTrackIdFor(sourceId, songId),
        sourceId = sourceId,
        title = "Song $songId",
        artistId = "artist:$sourceId",
        artistName = "Artist $sourceId",
        albumId = "album:$sourceId",
        albumTitle = "Album $sourceId",
        durationMs = 215_000L,
        trackNumber = 1,
        discNumber = 1,
        mediaLocator = buildNavidromeSongLocator(sourceId, songId),
        relativePath = "Artist $sourceId/Album $sourceId/Song $songId.flac",
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
    )
}

private class RecordingPlaylistsHttpClient(
    val remotePlaylistsByUser: MutableMap<String, LinkedHashMap<String, RemotePlaylistState>> = mutableMapOf(),
    private val failingDeletePlaylistIds: MutableSet<String> = mutableSetOf(),
) : LyricsHttpClient {
    val requestedEndpoints = mutableListOf<String>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        val endpoint = url.encodedPath.substringAfterLast('/')
        val username = url.parameters["u"].orEmpty()
        requestedEndpoints += endpoint
        val playlists = remotePlaylistsByUser.getOrPut(username) { linkedMapOf() }
        return Result.success(
            when (endpoint) {
                "getPlaylists" -> LyricsHttpResponse(200, getPlaylistsBody(playlists.values.toList()))
                "getPlaylist" -> {
                    val playlistId = url.parameters["id"].orEmpty()
                    LyricsHttpResponse(200, getPlaylistBody(playlists.getValue(playlistId)))
                }

                "createPlaylist" -> {
                    val name = url.parameters["name"].orEmpty()
                    val existing = playlists.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    if (existing == null) {
                        val id = "pl-${username}-${playlists.size + 1}"
                        playlists[id] = RemotePlaylistState(id = id, name = name, songIds = mutableListOf())
                    }
                    LyricsHttpResponse(200, okBody())
                }

                "updatePlaylist" -> {
                    val playlistId = url.parameters["playlistId"].orEmpty()
                    val playlist = playlists.getValue(playlistId)
                    url.parameters["songIdToAdd"]?.let { playlist.songIds += it }
                    url.parameters["songIndexToRemove"]?.toIntOrNull()?.let { index ->
                        if (index in playlist.songIds.indices) {
                            playlist.songIds.removeAt(index)
                        }
                    }
                    LyricsHttpResponse(200, okBody())
                }

                "deletePlaylist" -> {
                    val playlistId = url.parameters["id"].orEmpty()
                    if (playlistId in failingDeletePlaylistIds) {
                        LyricsHttpResponse(200, errorBody("delete failed"))
                    } else {
                        playlists.remove(playlistId)
                        LyricsHttpResponse(200, okBody())
                    }
                }

                else -> error("Unexpected request endpoint: $endpoint")
            },
        )
    }

    private fun okBody(): String {
        return """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
    }

    private fun errorBody(message: String): String {
        return """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"message":"$message"}}}"""
    }

    private fun getPlaylistsBody(playlists: List<RemotePlaylistState>): String {
        val items = playlists.joinToString(",") { playlist ->
            """{"id":"${playlist.id}","name":"${playlist.name}"}"""
        }
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "playlists": {
                  "playlist": [$items]
                }
              }
            }
        """.trimIndent()
    }

    private fun getPlaylistBody(playlist: RemotePlaylistState): String {
        val entries = playlist.songIds.joinToString(",") { songId ->
            """{"id":"$songId"}"""
        }
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "playlist": {
                  "id": "${playlist.id}",
                  "name": "${playlist.name}",
                  "entry": [$entries]
                }
              }
            }
        """.trimIndent()
    }
}

private data class RemotePlaylistState(
    val id: String,
    val name: String,
    val songIds: MutableList<String>,
)

private class MapPlaylistSecureCredentialStore(
    private val values: MutableMap<String, String> = linkedMapOf(),
) : SecureCredentialStore {
    override suspend fun put(key: String, value: String) {
        values[key] = value
    }

    override suspend fun get(key: String): String? = values[key]

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
