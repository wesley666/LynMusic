package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import androidx.room.Room
import io.ktor.http.parseUrl
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.EmbyCredential
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextFailure
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.buildEmbySongLocator
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.PlaylistEntity
import top.iwesley.lyn.music.data.db.PlaylistRemoteBindingEntity
import top.iwesley.lyn.music.data.db.PlaylistTrackEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.serializeEmbyCredential

class PlaylistsRepositoryTest {

    @Test
    fun playlistValidationKeepsResourceDescriptionsAndSavedNames() = runTest {
        val database = createPlaylistTestDatabase()
        try {
            val repository = playlistRepository(database)
            val saved = repository.createPlaylist("用户保存的中文名称").getOrThrow()
            val failure = assertNotNull(repository.createPlaylist("用户保存的中文名称").exceptionOrNull())
            assertTrue(failure is UiTextFailure)
            val text = failure.uiErrorText()
            assertEquals("A playlist with this name already exists.", resolveUiText(text, AppLanguage.English))
            assertEquals("歌单已存在。", resolveUiText(text, AppLanguage.SimplifiedChinese))
            assertEquals("歌單已存在。", resolveUiText(text, AppLanguage.TraditionalChinese))
            assertEquals("playlist_already_exists", failure.message)
            assertEquals("用户保存的中文名称", database.playlistDao().getById(saved.id)?.name)
            assertEquals(1, database.playlistDao().getAll().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun renameAndEmptyNameFailuresStayLocalizedWithoutChangingPlaylists() = runTest {
        val database = createPlaylistTestDatabase()
        try {
            val repository = playlistRepository(database)
            val first = repository.createPlaylist("First").getOrThrow()
            val second = repository.createPlaylist("Second").getOrThrow()
            val duplicate = assertNotNull(repository.renamePlaylist(second.id, first.name).exceptionOrNull())
            assertEquals("A playlist with this name already exists.", resolveUiText(duplicate.uiErrorText(), AppLanguage.English))
            val empty = assertNotNull(repository.createPlaylist("  ").exceptionOrNull())
            assertEquals("Playlist name cannot be empty.", resolveUiText(empty.uiErrorText(), AppLanguage.English))
            val missing = assertNotNull(repository.deletePlaylist("missing-id").exceptionOrNull())
            assertEquals("Playlist does not exist.", resolveUiText(missing.uiErrorText(), AppLanguage.English))
            assertEquals("Second", database.playlistDao().getById(second.id)?.name)
            assertEquals(2, database.playlistDao().getAll().size)
        } finally {
            database.close()
        }
    }

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
    fun `import playlist text parses hyphen formats and reports malformed lines`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(
            listOf(
                localTrackEntity(id = "track-coffee", title = "咖啡恋曲", artistName = "旺福"),
                localTrackEntity(id = "track-night", title = "夜空中最亮的星", artistName = "逃跑计划"),
            ),
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("导入测试").getOrThrow()

        val report = repository.importPlaylistText(
            playlistId = playlist.id,
            text = """
                咖啡恋曲 - 旺福

                夜空中最亮的星-逃跑计划
                没有分隔符
                - 缺标题
                缺歌手 -
            """.trimIndent(),
        ).getOrThrow()

        assertEquals(2, report.addedCount)
        assertEquals(listOf(4, 5, 6), report.malformedLines.map { it.lineNumber })
        assertEquals(
            listOf("track-coffee", "track-night"),
            repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id },
        )
    }

    @Test
    fun `import playlist text supports hyphenated artists and spaced hyphens in titles`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(
            listOf(
                localTrackEntity(id = "track-tara", title = "Sugar Free", artistName = "T-ARA"),
                localTrackEntity(id = "track-title-hyphen", title = "Song - Title", artistName = "Artist"),
            ),
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("连字符测试").getOrThrow()

        val report = repository.importPlaylistText(
            playlistId = playlist.id,
            text = """
                Sugar Free - T-ARA
                Song - Title - Artist
            """.trimIndent(),
        ).getOrThrow()

        assertEquals(2, report.addedCount)
        assertEquals(emptyList(), report.malformedLines)
        assertEquals(emptyList(), report.notMatchedLines)
        assertEquals(
            listOf("track-tara", "track-title-hyphen"),
            repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id },
        )
    }

    @Test
    fun `import playlist text matches enabled exact title and artist only`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity(sourceId = "enabled"))
        database.importSourceDao().upsert(localSourceEntity(sourceId = "disabled", enabled = false))
        database.importSourceDao().upsert(localSourceEntity(sourceId = "other-enabled"))
        database.trackDao().upsertAll(
            listOf(
                localTrackEntity(
                    id = "track-enabled",
                    sourceId = "enabled",
                    title = " Morning Light ",
                    artistName = " Artist A ",
                ),
                localTrackEntity(id = "track-title-only", sourceId = "enabled", title = "Morning Light", artistName = "Artist B"),
                localTrackEntity(id = "track-artist-only", sourceId = "enabled", title = "Other Song", artistName = "Artist A"),
                localTrackEntity(id = "track-disabled", sourceId = "disabled", title = "Ghost", artistName = "Artist A"),
                localTrackEntity(id = "track-same-1", sourceId = "enabled", title = "Same Song", artistName = "Artist A"),
                localTrackEntity(
                    id = "track-same-2",
                    sourceId = "other-enabled",
                    title = "Same Song",
                    artistName = "Artist A",
                ),
            ),
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("匹配测试").getOrThrow()

        val report = repository.importPlaylistText(
            playlistId = playlist.id,
            text = """
                morning light - artist a
                Ghost - Artist A
                Same Song - Artist A
            """.trimIndent(),
        ).getOrThrow()

        assertEquals(1, report.addedCount)
        assertEquals(listOf(2), report.notMatchedLines.map { it.lineNumber })
        assertEquals(listOf(3), report.ambiguousLines.map { it.lineNumber })
        assertEquals(2, report.ambiguousLines.single().matchCount)
        assertEquals(
            listOf("track-enabled"),
            repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id },
        )
    }

    @Test
    fun `import playlist text ignores unrelated large library candidates`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(
            buildList {
                add(localTrackEntity(id = "track-target", title = "Needle", artistName = "Singer"))
                repeat(250) { index ->
                    add(
                        localTrackEntity(
                            id = "track-unrelated-$index",
                            title = "Unrelated $index",
                            artistName = "Other Artist $index",
                        ),
                    )
                }
            },
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("大曲库测试").getOrThrow()

        val report = repository.importPlaylistText(
            playlistId = playlist.id,
            text = "Needle - Singer",
        ).getOrThrow()

        assertEquals(1, report.addedCount)
        assertFalse(report.hasIssues)
        assertEquals(
            listOf("track-target"),
            repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id },
        )
    }

    @Test
    fun `import playlist text batches candidate lookup for many distinct lines`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(
            List(520) { index ->
                localTrackEntity(
                    id = "track-bulk-$index",
                    title = "Bulk Song $index",
                    artistName = "Bulk Artist $index",
                )
            },
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("批量导入测试").getOrThrow()

        val report = repository.importPlaylistText(
            playlistId = playlist.id,
            text = (0 until 520).joinToString("\n") { index ->
                "Bulk Song $index - Bulk Artist $index"
            },
        ).getOrThrow()

        assertEquals(520, report.addedCount)
        assertFalse(report.hasIssues)
        assertEquals(
            (0 until 520).map { "track-bulk-$it" },
            repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id },
        )
    }

    @Test
    fun `import playlist text skips existing and duplicate input tracks`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(
            listOf(
                localTrackEntity(),
                localTrackEntity(id = "track-second", title = "Second Song", artistName = "Artist B"),
            ),
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("重复测试").getOrThrow()
        repository.addTrackToPlaylist(playlist.id, localTrack()).getOrThrow()

        val report = repository.importPlaylistText(
            playlistId = playlist.id,
            text = """
                Morning Light - Artist A
                Second Song - Artist B
                Second Song - Artist B
                Morning Light - Artist A
            """.trimIndent(),
        ).getOrThrow()

        assertEquals(1, report.addedCount)
        assertEquals(1, report.alreadyExistsCount)
        assertEquals(2, report.duplicateInputCount)
        assertEquals(
            listOf(localTrack().id, "track-second"),
            repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id },
        )
    }

    @Test
    fun `import playlist text syncs navidrome tracks through remote playlist api`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(database, sourceId = "nav", username = "alpha", credentialKey = "cred-a", label = "Alpha")
        database.trackDao().upsertAll(listOf(navidromeTrackEntity(sourceId = "nav", songId = "song-a1")))
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

        val report = repository.importPlaylistText(
            playlistId = playlist.id,
            text = "Song song-a1 - Artist nav",
        ).getOrThrow()

        assertEquals(1, report.addedCount)
        assertTrue(httpClient.requestedEndpoints.contains("createPlaylist"))
        assertTrue(httpClient.requestedEndpoints.contains("updatePlaylist"))
        assertTrue(httpClient.requestedEndpoints.contains("getPlaylist"))
        assertEquals(
            listOf(navidromeTrack(sourceId = "nav", songId = "song-a1").id),
            repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id },
        )
    }

    @Test
    fun `online playlist text import searches navidrome and reports exact outcomes`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online",
            username = "online",
            credentialKey = "cred-online",
            label = "Online",
            indexMode = "ONLINE",
        )
        val httpClient = RecordingOnlinePlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "online" to linkedMapOf(
                    "pl-online-1" to RemotePlaylistState(
                        id = "pl-online-1",
                        name = "Remote Mix",
                        songIds = mutableListOf("song-existing"),
                    ),
                ),
            ),
            searchResultsByQuery = mutableMapOf(
                "Unique Artist A" to listOf(OnlineSearchSongState("song-unique", "Unique", "Artist A")),
                "Existing Artist A" to listOf(OnlineSearchSongState("song-existing", "Existing", "Artist A")),
                "Duplicate Artist A" to listOf(OnlineSearchSongState("song-duplicate", "Duplicate", "Artist A")),
                "Missing Artist A" to listOf(OnlineSearchSongState("song-mismatch", "Other", "Artist A")),
                "Ambiguous Artist A" to listOf(
                    OnlineSearchSongState("song-ambiguous-1", "Ambiguous", "Artist A"),
                    OnlineSearchSongState("song-ambiguous-2", "Ambiguous", "Artist A"),
                ),
                "Fail Artist A" to listOf(OnlineSearchSongState("song-fail", "Fail", "Artist A")),
            ),
            failingUpdateSongIds = mutableSetOf("song-fail"),
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-online" to "pass-online")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val report = repository.importPlaylistText(
            sourceId = "nav-online",
            playlistId = "pl-online-1",
            text = """
                Unique - Artist A
                Existing - Artist A
                Duplicate - Artist A
                Duplicate - Artist A
                Missing - Artist A
                Ambiguous - Artist A
                Fail - Artist A
                bad line
            """.trimIndent(),
        )

        assertEquals(2, report.addedCount)
        assertEquals(1, report.alreadyExistsCount)
        assertEquals(1, report.duplicateInputCount)
        assertEquals(listOf(8), report.malformedLines.map { it.lineNumber })
        assertEquals(listOf(5), report.notMatchedLines.map { it.lineNumber })
        assertEquals(listOf(6), report.ambiguousLines.map { it.lineNumber })
        assertEquals(2, report.ambiguousLines.single().matchCount)
        assertEquals(listOf(7), report.failedLines.map { it.lineNumber })
        assertEquals(
            listOf("song-existing", "song-unique", "song-duplicate"),
            httpClient.remotePlaylistsByUser.getValue("online").getValue("pl-online-1").songIds,
        )
        assertEquals(
            listOf(
                listOf("song-unique", "song-duplicate", "song-fail"),
                listOf("song-unique"),
                listOf("song-duplicate"),
                listOf("song-fail"),
            ),
            httpClient.requestedUpdateBatches,
        )
        assertTrue(httpClient.requestedEndpoints.contains("search3"))
        assertTrue(httpClient.requestedEndpoints.contains("updatePlaylist"))
    }

    @Test
    fun `online playlist text import writes additions in batches of fifty`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-bulk",
            username = "bulk",
            credentialKey = "cred-bulk",
            label = "Bulk",
            indexMode = "ONLINE",
        )
        val searchResults = (0 until 120).associate { index ->
            val title = "Bulk Song $index"
            val artist = "Bulk Artist $index"
            "$title $artist" to listOf(
                OnlineSearchSongState(
                    id = "song-bulk-$index",
                    title = title,
                    artist = artist,
                ),
            )
        }.toMutableMap()
        val httpClient = RecordingOnlinePlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "bulk" to linkedMapOf(
                    "pl-bulk-1" to RemotePlaylistState(
                        id = "pl-bulk-1",
                        name = "Bulk Mix",
                        songIds = mutableListOf(),
                    ),
                ),
            ),
            searchResultsByQuery = searchResults,
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-bulk" to "pass-bulk")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val report = repository.importPlaylistText(
            sourceId = "nav-online-bulk",
            playlistId = "pl-bulk-1",
            text = (0 until 120).joinToString("\n") { index ->
                "Bulk Song $index - Bulk Artist $index"
            },
        )

        assertEquals(120, report.addedCount)
        assertFalse(report.hasIssues)
        assertEquals(listOf(50, 50, 20), httpClient.requestedUpdateBatches.map { it.size })
        assertEquals(
            (0 until 120).map { "song-bulk-$it" },
            httpClient.remotePlaylistsByUser.getValue("bulk").getValue("pl-bulk-1").songIds,
        )
    }

    @Test
    fun `online favorite tracks filters full starred set before pagination`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-favorites",
            username = "favorites",
            credentialKey = "cred-favorites",
            label = "Favorites",
            indexMode = "ONLINE",
        )
        val songs = (0 until 105).map { index ->
            OnlineFavoriteSongState(
                id = "song-fav-$index",
                title = when (index) {
                    2 -> "Needle First"
                    102 -> "Needle Second"
                    104 -> "Needle Third"
                    else -> "Other $index"
                },
                artist = "Artist $index",
                album = "Album $index",
            )
        }
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-favorites" to "pass-favorites")),
            httpClient = RecordingOnlineFavoritesHttpClient(starredSongsByUser = mapOf("favorites" to songs)),
            logger = NoopDiagnosticLogger,
        )

        val firstPage = repository.favoriteTracks(
            sourceId = "nav-online-favorites",
            offset = 0,
            limit = 2,
            query = "needle",
        )
        val secondPage = repository.favoriteTracks(
            sourceId = "nav-online-favorites",
            offset = 2,
            limit = 2,
            query = "needle",
        )

        assertEquals(listOf("Needle First", "Needle Second"), firstPage.items.map { it.title })
        assertEquals(listOf(true, true), firstPage.items.map { it.remoteFavoriteHint })
        assertEquals(3, firstPage.totalCount)
        assertTrue(firstPage.hasMore)
        assertEquals(listOf("Needle Third"), secondPage.items.map { it.title })
        assertEquals(listOf(true), secondPage.items.map { it.remoteFavoriteHint })
        assertEquals(3, secondPage.totalCount)
        assertFalse(secondPage.hasMore)
    }

    @Test
    fun `online search tracks expose remote favorite hints from starred field`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-search-favorites",
            username = "search-favorites",
            credentialKey = "cred-search-favorites",
            label = "Search Favorites",
            indexMode = "ONLINE",
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("cred-search-favorites" to "pass-search-favorites"),
            ),
            httpClient = RecordingOnlineSearchHttpClient(
                songs = listOf(
                    OnlineSearchSongState(
                        id = "song-starred",
                        title = "Starred",
                        artist = "Artist A",
                        starred = "2026-06-09T12:00:00Z",
                    ),
                    OnlineSearchSongState(
                        id = "song-missing-starred",
                        title = "Missing Starred",
                        artist = "Artist A",
                    ),
                    OnlineSearchSongState(
                        id = "song-false-starred",
                        title = "False Starred",
                        artist = "Artist A",
                        starred = "false",
                    ),
                ),
                albums = emptyList(),
                artists = emptyList(),
            ),
            logger = NoopDiagnosticLogger,
        )

        val page = repository.searchTracks(
            sourceId = "nav-online-search-favorites",
            query = "Artist A",
            offset = 0,
            limit = 10,
        )

        assertEquals(
            listOf(true, null, false),
            page.items.map { it.remoteFavoriteHint },
        )
    }

    @Test
    fun `online albums expose cover art from album list`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-albums",
            username = "albums",
            credentialKey = "cred-albums",
            label = "Albums",
            indexMode = "ONLINE",
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-albums" to "pass-albums")),
            httpClient = RecordingOnlineAlbumsHttpClient(
                albums = listOf(
                    OnlineSearchAlbumState(
                        id = "album-covered",
                        name = "Covered Album",
                        artist = "Album Artist",
                        coverArt = "cover-album-1",
                    ),
                    OnlineSearchAlbumState(
                        id = "album-plain",
                        name = "Plain Album",
                        artist = "Album Artist",
                    ),
                ),
            ),
            logger = NoopDiagnosticLogger,
        )

        val page = repository.albums(
            sourceId = "nav-online-albums",
            offset = 0,
            limit = 10,
        )

        assertEquals(listOf("Covered Album", "Plain Album"), page.items.map { it.album.title })
        assertEquals(buildNavidromeCoverLocator("nav-online-albums", "cover-album-1"), page.items.first().artworkLocator)
        assertNull(page.items[1].artworkLocator)
    }

    @Test
    fun `online playlists expose cover art from playlist list`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-playlists-cover",
            username = "playlists-cover",
            credentialKey = "cred-playlists-cover",
            label = "Playlists Cover",
            indexMode = "ONLINE",
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("cred-playlists-cover" to "pass-playlists-cover"),
            ),
            httpClient = RecordingPlaylistsHttpClient(
                remotePlaylistsByUser = mutableMapOf(
                    "playlists-cover" to linkedMapOf(
                        "playlist-covered" to RemotePlaylistState(
                            id = "playlist-covered",
                            name = "Covered Playlist",
                            songIds = mutableListOf(),
                            coverArt = "playlist-cover-1",
                        ),
                        "playlist-plain" to RemotePlaylistState(
                            id = "playlist-plain",
                            name = "Plain Playlist",
                            songIds = mutableListOf(),
                        ),
                    ),
                ),
            ),
            logger = NoopDiagnosticLogger,
        )

        val playlists = repository.playlists("nav-online-playlists-cover")

        assertEquals(listOf("Covered Playlist", "Plain Playlist"), playlists.map { it.name })
        assertEquals(
            buildNavidromeCoverLocator("nav-online-playlists-cover", "playlist-cover-1"),
            playlists[0].artworkLocator,
        )
        assertNull(playlists[1].artworkLocator)
    }

    @Test
    fun `online artists expose nullable remote counts`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-artists",
            username = "artists",
            credentialKey = "cred-artists",
            label = "Artists",
            indexMode = "ONLINE",
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-artists" to "pass-artists")),
            httpClient = RecordingOnlineArtistsHttpClient(
                artists = listOf(
                    OnlineSearchArtistState(
                        id = "artist-counted",
                        name = "Counted Artist",
                        songCount = 31,
                        albumCount = 7,
                    ),
                    OnlineSearchArtistState(
                        id = "artist-unknown",
                        name = "Unknown Artist",
                    ),
                ),
            ),
            logger = NoopDiagnosticLogger,
        )

        val page = repository.artists(
            sourceId = "nav-online-artists",
            offset = 0,
            limit = 10,
        )

        assertEquals(listOf("Counted Artist", "Unknown Artist"), page.items.map { it.artist.name })
        assertEquals(31, page.items[0].trackCount)
        assertEquals(7, page.items[0].albumCount)
        assertNull(page.items[1].trackCount)
        assertNull(page.items[1].albumCount)
    }

    @Test
    fun `online artists load more reuses cached full artist list`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-artists-cache",
            username = "artists-cache",
            credentialKey = "cred-artists-cache",
            label = "Artists Cache",
            indexMode = "ONLINE",
        )
        val httpClient = RecordingOnlineArtistsHttpClient(
            artists = listOf(
                OnlineSearchArtistState(id = "artist-1", name = "Artist 1"),
                OnlineSearchArtistState(id = "artist-2", name = "Artist 2"),
                OnlineSearchArtistState(id = "artist-3", name = "Artist 3"),
            ),
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("cred-artists-cache" to "pass-artists-cache"),
            ),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val firstPage = repository.artists(
            sourceId = "nav-online-artists-cache",
            offset = 0,
            limit = 1,
        )
        val secondPage = repository.artists(
            sourceId = "nav-online-artists-cache",
            offset = 1,
            limit = 1,
        )

        assertEquals(listOf("Artist 1"), firstPage.items.map { it.artist.name })
        assertEquals(listOf("Artist 2"), secondPage.items.map { it.artist.name })
        assertEquals(1, httpClient.requestedEndpoints.count { it == "getArtists" })

        val refreshedFirstPage = repository.artists(
            sourceId = "nav-online-artists-cache",
            offset = 0,
            limit = 1,
        )

        assertEquals(listOf("Artist 1"), refreshedFirstPage.items.map { it.artist.name })
        assertEquals(2, httpClient.requestedEndpoints.count { it == "getArtists" })
    }

    @Test
    fun `online artist detail exposes albums with cover artwork locators`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-artist-detail",
            username = "artist-detail",
            credentialKey = "cred-artist-detail",
            label = "Artist Detail",
            indexMode = "ONLINE",
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("cred-artist-detail" to "pass-artist-detail"),
            ),
            httpClient = RecordingOnlineArtistsHttpClient(
                albumsByArtistId = mapOf(
                    "artist-detail-1" to listOf(
                        OnlineSearchAlbumState(
                            id = "album-artist-covered",
                            name = "Artist Covered Album",
                            artist = "Artist Detail",
                            coverArt = "cover-artist-album",
                        ),
                        OnlineSearchAlbumState(
                            id = "album-artist-plain",
                            name = "Artist Plain Album",
                            artist = "Artist Detail",
                        ),
                    ),
                ),
            ),
            logger = NoopDiagnosticLogger,
        )

        val albums = repository.artistAlbums(
            sourceId = "nav-online-artist-detail",
            artistId = "artist-detail-1",
        )

        assertEquals(listOf("Artist Covered Album", "Artist Plain Album"), albums.map { it.album.title })
        assertEquals(
            buildNavidromeCoverLocator("nav-online-artist-detail", "cover-artist-album"),
            albums.first().artworkLocator,
        )
        assertNull(albums[1].artworkLocator)
    }

    @Test
    fun `online search pages tracks albums and artists with independent offsets`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-search",
            username = "search",
            credentialKey = "cred-search",
            label = "Search",
            indexMode = "ONLINE",
        )
        val httpClient = RecordingOnlineSearchHttpClient(
            songs = (0 until 120).map { index ->
                OnlineSearchSongState(
                    id = "song-search-$index",
                    title = "Song $index",
                    artist = "Artist $index",
                )
            },
            albums = (0 until 12).map { index ->
                OnlineSearchAlbumState(
                    id = "album-search-$index",
                    name = "Album $index",
                    artist = "Album Artist $index",
                    coverArt = if (index == 5) "cover-search-$index" else null,
                )
            },
            artists = (0 until 8).map { index ->
                OnlineSearchArtistState(
                    id = "artist-search-$index",
                    name = "Artist $index",
                )
            },
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-search" to "pass-search")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val firstTracksPage = repository.searchTracks(
            sourceId = "nav-online-search",
            query = "needle",
            offset = 0,
            limit = 100,
        )
        val secondTracksPage = repository.searchTracks(
            sourceId = "nav-online-search",
            query = "needle",
            offset = 100,
            limit = 100,
        )
        val albumsPage = repository.searchAlbums(
            sourceId = "nav-online-search",
            query = "needle",
            offset = 5,
            limit = 2,
        )
        val artistsPage = repository.searchArtists(
            sourceId = "nav-online-search",
            query = "needle",
            offset = 3,
            limit = 2,
        )

        assertEquals(100, firstTracksPage.items.size)
        assertTrue(firstTracksPage.hasMore)
        assertEquals((100 until 120).map { "Song $it" }, secondTracksPage.items.map { it.title })
        assertFalse(secondTracksPage.hasMore)
        assertEquals(listOf("Album 5", "Album 6"), albumsPage.items.map { it.album.title })
        assertEquals(buildNavidromeCoverLocator("nav-online-search", "cover-search-5"), albumsPage.items.first().artworkLocator)
        assertNull(albumsPage.items[1].artworkLocator)
        assertEquals(listOf("Artist 3", "Artist 4"), artistsPage.items.map { it.artist.name })
        assertNull(artistsPage.items.first().trackCount)
        assertNull(artistsPage.items.first().albumCount)
        assertEquals(
            listOf(
                RecordedSearch3Request(
                    query = "needle",
                    songOffset = 0,
                    songCount = 100,
                    albumOffset = 0,
                    albumCount = 0,
                    artistOffset = 0,
                    artistCount = 0,
                ),
                RecordedSearch3Request(
                    query = "needle",
                    songOffset = 100,
                    songCount = 100,
                    albumOffset = 0,
                    albumCount = 0,
                    artistOffset = 0,
                    artistCount = 0,
                ),
                RecordedSearch3Request(
                    query = "needle",
                    songOffset = 0,
                    songCount = 0,
                    albumOffset = 5,
                    albumCount = 2,
                    artistOffset = 0,
                    artistCount = 0,
                ),
                RecordedSearch3Request(
                    query = "needle",
                    songOffset = 0,
                    songCount = 0,
                    albumOffset = 0,
                    albumCount = 0,
                    artistOffset = 3,
                    artistCount = 2,
                ),
            ),
            httpClient.searchRequests,
        )
    }

    @Test
    fun `online add track to playlist rejects existing remote song`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online-existing",
            username = "existing",
            credentialKey = "cred-existing",
            label = "Existing",
            indexMode = "ONLINE",
        )
        val httpClient = RecordingOnlinePlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "existing" to linkedMapOf(
                    "pl-existing-1" to RemotePlaylistState(
                        id = "pl-existing-1",
                        name = "Existing Mix",
                        songIds = mutableListOf("song-existing"),
                    ),
                ),
            ),
            searchResultsByQuery = mutableMapOf(),
        )
        val repository = NavidromeOnlineRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-existing" to "pass-existing")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val error = assertFailsWith<IllegalStateException> {
            repository.addTrackToPlaylist(
                sourceId = "nav-online-existing",
                playlistId = "pl-existing-1",
                track = navidromeTrack(sourceId = "nav-online-existing", songId = "song-existing"),
            )
        }

        assertEquals("playlist_track_already_present", error.message)
        assertEquals(emptyList(), httpClient.requestedUpdateBatches)
        assertEquals(
            listOf("song-existing"),
            httpClient.remotePlaylistsByUser.getValue("existing").getValue("pl-existing-1").songIds,
        )
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
        assertEquals("album:local-1:album:artist a:album one", summary.artworkCacheKey)
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
    fun `playlist summary artwork skips newer tracks without artwork`() = runTest {
        val database = createPlaylistTestDatabase()
        database.importSourceDao().upsert(localSourceEntity())
        database.trackDao().upsertAll(
            listOf(
                localTrackEntity(id = "track-covered", title = "Covered", artworkLocator = "/art/covered.jpg"),
                localTrackEntity(id = "track-empty", title = "Empty", artworkLocator = null),
            ),
        )
        val repository = playlistRepository(database)
        val playlist = repository.createPlaylist("空封面回退").getOrThrow()
        database.playlistTrackDao().upsertAll(
            listOf(
                playlistTrackEntity(playlist.id, "track-covered", addedAt = 10L, localOrdinal = 0),
                playlistTrackEntity(playlist.id, "track-empty", addedAt = 20L, localOrdinal = 1),
            ),
        )

        val summary = repository.playlists.first().single()

        assertEquals("/art/covered.jpg", summary.artworkLocator)
        assertEquals("album:local-1:album:artist a:album one", summary.artworkCacheKey)
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
    fun `local playlist browser hides remote playlists backed only by online source`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online",
            username = "online",
            credentialKey = "cred-online",
            label = "Online",
            indexMode = "ONLINE",
        )
        seedNavidromeSource(
            database = database,
            sourceId = "nav-local-index",
            username = "local-index",
            credentialKey = "cred-local-index",
            label = "Local Index",
        )
        database.playlistDao().upsertAll(
            listOf(
                playlistEntity(
                    id = "playlist-online-only",
                    name = "Online Only",
                    createdLocally = false,
                    updatedAt = 30L,
                ),
                playlistEntity(
                    id = "playlist-local-indexed",
                    name = "Local Indexed",
                    createdLocally = false,
                    updatedAt = 20L,
                ),
                playlistEntity(
                    id = "playlist-local-created",
                    name = "Local Created",
                    createdLocally = true,
                    updatedAt = 10L,
                ),
            ),
        )
        database.playlistRemoteBindingDao().upsertAll(
            listOf(
                playlistRemoteBindingEntity(
                    playlistId = "playlist-online-only",
                    sourceId = "nav-online",
                    remotePlaylistId = "pl-online",
                ),
                playlistRemoteBindingEntity(
                    playlistId = "playlist-local-indexed",
                    sourceId = "nav-local-index",
                    remotePlaylistId = "pl-local-index",
                ),
            ),
        )
        val repository = playlistRepository(database)

        val playlists = repository.playlists.first()

        assertEquals(
            setOf("playlist-local-indexed", "playlist-local-created"),
            playlists.mapTo(linkedSetOf()) { it.id },
        )
        assertNull(repository.observePlaylistDetail("playlist-online-only").first())
        assertNotNull(repository.observePlaylistDetail("playlist-local-indexed").first())
        assertNotNull(repository.observePlaylistDetail("playlist-local-created").first())
    }

    @Test
    fun `local playlist operations skip online mode remote bindings`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online",
            username = "online",
            credentialKey = "cred-online",
            label = "Online",
            indexMode = "ONLINE",
        )
        val onlineTrack = navidromeTrack(sourceId = "nav-online", songId = "song-online")
        database.trackDao().upsertAll(
            listOf(navidromeTrackEntity(sourceId = "nav-online", songId = "song-online")),
        )
        database.playlistDao().upsert(
            playlistEntity(
                id = "playlist-online-old",
                name = "Remote Online",
                createdLocally = false,
            ),
        )
        database.playlistRemoteBindingDao().upsert(
            playlistRemoteBindingEntity(
                playlistId = "playlist-online-old",
                sourceId = "nav-online",
                remotePlaylistId = "pl-online",
            ),
        )
        database.playlistTrackDao().upsert(
            playlistTrackEntity(
                playlistId = "playlist-online-old",
                trackId = onlineTrack.id,
                sourceId = "nav-online",
                addedAt = 10L,
                localOrdinal = null,
                remoteOrdinal = 0,
            ),
        )
        val httpClient = RecordingPlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "online" to linkedMapOf(
                    "pl-online" to RemotePlaylistState(
                        id = "pl-online",
                        name = "Remote Online",
                        songIds = mutableListOf("song-online"),
                    ),
                ),
            ),
        )
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(mutableMapOf("cred-online" to "pass-online")),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        repository.renamePlaylist("playlist-online-old", "Local Rename").getOrThrow()
        repository.removeTrackFromPlaylist("playlist-online-old", onlineTrack.id).getOrThrow()
        repository.deletePlaylist("playlist-online-old").getOrThrow()

        assertEquals(emptyList(), httpClient.requestedEndpoints)
        assertEquals("Remote Online", httpClient.remotePlaylistsByUser.getValue("online").getValue("pl-online").name)
        assertEquals(listOf("song-online"), httpClient.remotePlaylistsByUser.getValue("online").getValue("pl-online").songIds)
        assertNull(database.playlistDao().getById("playlist-online-old"))
        assertTrue(database.playlistTrackDao().getByPlaylistId("playlist-online-old").isEmpty())
        assertTrue(database.playlistRemoteBindingDao().getByPlaylistId("playlist-online-old").isEmpty())
    }

    @Test
    fun `local created playlist keeps online mode remote rename and delete sync`() = runTest {
        val database = createPlaylistTestDatabase()
        seedNavidromeSource(
            database = database,
            sourceId = "nav-online",
            username = "online",
            credentialKey = "cred-online",
            label = "Online",
            indexMode = "ONLINE",
        )
        database.playlistDao().upsert(
            playlistEntity(
                id = "playlist-local-created",
                name = "Local Synced",
                createdLocally = true,
            ),
        )
        database.playlistRemoteBindingDao().upsert(
            playlistRemoteBindingEntity(
                playlistId = "playlist-local-created",
                sourceId = "nav-online",
                remotePlaylistId = "pl-online",
                remoteName = "Local Synced",
            ),
        )
        val httpClient = RecordingPlaylistsHttpClient(
            remotePlaylistsByUser = mutableMapOf(
                "online" to linkedMapOf(
                    "pl-online" to RemotePlaylistState(
                        id = "pl-online",
                        name = "Local Synced",
                        songIds = mutableListOf(),
                    ),
                ),
            ),
        )
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("cred-online" to "pass-online"),
            ),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        repository.renamePlaylist("playlist-local-created", "Renamed Local").getOrThrow()

        assertEquals(
            "Renamed Local",
            httpClient.remotePlaylistsByUser.getValue("online").getValue("pl-online").name,
        )
        assertEquals(
            "Renamed Local",
            database.playlistRemoteBindingDao()
                .getByPlaylistIdAndSourceId("playlist-local-created", "nav-online")
                ?.remoteName,
        )

        repository.deletePlaylist("playlist-local-created").getOrThrow()

        assertFalse(httpClient.remotePlaylistsByUser.getValue("online").containsKey("pl-online"))
        assertTrue(httpClient.requestedEndpoints.contains("updatePlaylist"))
        assertTrue(httpClient.requestedEndpoints.contains("deletePlaylist"))
        assertNull(database.playlistDao().getById("playlist-local-created"))
        assertTrue(database.playlistRemoteBindingDao().getByPlaylistId("playlist-local-created").isEmpty())
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
    fun `adding and removing emby track writes remote playlist membership`() = runTest {
        val database = createPlaylistTestDatabase()
        seedEmbySource(database)
        database.trackDao().upsertAll(listOf(embyTrackEntity(itemId = "song-e1")))
        val httpClient = RecordingEmbyPlaylistsHttpClient()
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("emby-cred" to serializeEmbyCredential(EmbyCredential("user-1", "token"))),
            ),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val playlist = repository.createPlaylist("Road Trip").getOrThrow()
        repository.addTrackToPlaylist(playlist.id, embyTrack(itemId = "song-e1")).getOrThrow()

        assertNotNull(database.playlistRemoteBindingDao().getByPlaylistIdAndSourceId(playlist.id, "emby-source"))
        assertEquals(listOf(embyTrack(itemId = "song-e1").id), repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id })
        assertTrue(httpClient.requestPaths.contains("/emby/Playlists/pl-1/Items"))

        repository.removeTrackFromPlaylist(playlist.id, embyTrack(itemId = "song-e1").id).getOrThrow()

        assertEquals(emptyList(), repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id })
        assertTrue(httpClient.requestPaths.contains("/emby/Playlists/pl-1/Items/Delete"))
        assertEquals(emptyList(), httpClient.playlistItemIds)
    }

    @Test
    fun `removing emby track verifies stale remote ordinal before deleting`() = runTest {
        val database = createPlaylistTestDatabase()
        seedEmbySource(database)
        database.trackDao().upsertAll(
            listOf(
                embyTrackEntity(itemId = "song-e1"),
                embyTrackEntity(itemId = "song-e2"),
            ),
        )
        val httpClient = RecordingEmbyPlaylistsHttpClient()
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("emby-cred" to serializeEmbyCredential(EmbyCredential("user-1", "token"))),
            ),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val playlist = repository.createPlaylist("Road Trip").getOrThrow()
        repository.addTrackToPlaylist(playlist.id, embyTrack(itemId = "song-e1")).getOrThrow()
        repository.addTrackToPlaylist(playlist.id, embyTrack(itemId = "song-e2")).getOrThrow()
        httpClient.playlistItemIds.apply {
            clear()
            addAll(listOf("song-e2", "song-e1"))
        }

        repository.removeTrackFromPlaylist(playlist.id, embyTrack(itemId = "song-e1").id).getOrThrow()

        assertEquals(listOf("song-e2"), httpClient.playlistItemIds)
        assertEquals(
            listOf(embyTrack(itemId = "song-e2").id),
            repository.observePlaylistDetail(playlist.id).first()?.tracks?.map { it.track.id },
        )
    }

    @Test
    fun `renaming emby playlist writes remote item name`() = runTest {
        val database = createPlaylistTestDatabase()
        seedEmbySource(database)
        database.trackDao().upsertAll(listOf(embyTrackEntity(itemId = "song-e1")))
        val httpClient = RecordingEmbyPlaylistsHttpClient()
        val repository = RoomPlaylistRepository(
            database = database,
            secureCredentialStore = MapPlaylistSecureCredentialStore(
                mutableMapOf("emby-cred" to serializeEmbyCredential(EmbyCredential("user-1", "token"))),
            ),
            httpClient = httpClient,
            logger = NoopDiagnosticLogger,
        )

        val playlist = repository.createPlaylist("Road Trip").getOrThrow()
        repository.addTrackToPlaylist(playlist.id, embyTrack(itemId = "song-e1")).getOrThrow()
        repository.renamePlaylist(playlist.id, "Renamed Trip").getOrThrow()

        assertEquals("Renamed Trip", httpClient.playlistName)
        assertTrue(httpClient.requestPaths.contains("/emby/Users/user-1/Items/pl-1"))
        assertTrue(httpClient.requestPaths.contains("/emby/Items/pl-1"))
        assertEquals("Renamed Trip", repository.observePlaylistDetail(playlist.id).first()?.name)
        assertEquals(
            "Renamed Trip",
            database.playlistRemoteBindingDao()
                .getByPlaylistIdAndSourceId(playlist.id, "emby-source")
                ?.remoteName,
        )
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

class PlaylistImportRepositoryUiTextTest {
    @Test fun onlineBatchAndSingleRetryFailuresKeepResourceAndImportResults() = runTest {
        val database = createPlaylistTestDatabase()
        try {
            seedNavidromeSource(database, "nav-online", "online", "cred", "用户来源", indexMode = "ONLINE")
            val delegate = RecordingOnlinePlaylistsHttpClient(
                remotePlaylistsByUser = mutableMapOf("online" to linkedMapOf(
                    "pl-1" to RemotePlaylistState("pl-1", "用户歌单", mutableListOf()),
                )),
                searchResultsByQuery = mutableMapOf(
                    "成功歌曲 歌手" to listOf(OnlineSearchSongState("song-good", "成功歌曲", "歌手")),
                    "失败歌曲 歌手" to listOf(OnlineSearchSongState("song-bad", "失败歌曲", "歌手")),
                ),
            )
            val failedBatches = mutableListOf<List<String>>()
            val client = object : LyricsHttpClient {
                override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
                    val url = requireNotNull(parseUrl(request.url))
                    val songs = url.parameters.getAll("songIdToAdd").orEmpty()
                    return if (url.encodedPath.endsWith("/updatePlaylist") && "song-bad" in songs) {
                        failedBatches += songs
                        Result.success(LyricsHttpResponse(403, ""))
                    } else delegate.request(request)
                }
            }
            val repository = NavidromeOnlineRepository(database, MapPlaylistSecureCredentialStore(mutableMapOf("cred" to "pass")), client)
            val report = repository.importPlaylistText("nav-online", "pl-1", "成功歌曲 - 歌手\n失败歌曲 - 歌手")
            assertEquals(1, report.addedCount)
            val issue = report.failedLines.single()
            assertEquals(2, issue.lineNumber)
            assertEquals("失败歌曲 - 歌手", issue.rawText)
            assertEquals("Navidrome updatePlaylist 失败，HTTP 403", issue.message)
            assertEquals("Navidrome updatePlaylist failed, HTTP 403", resolveUiText(issue.messageUiText(), AppLanguage.English))
            assertEquals("Navidrome updatePlaylist 失敗，HTTP 403", resolveUiText(issue.messageUiText(), AppLanguage.TraditionalChinese))
            assertEquals(listOf(listOf("song-good", "song-bad"), listOf("song-bad")), failedBatches)
            assertEquals(listOf(listOf("song-good")), delegate.requestedUpdateBatches)
            assertEquals(listOf("song-good"), delegate.remotePlaylistsByUser.getValue("online").getValue("pl-1").songIds)
            assertEquals(0, report.alreadyExistsCount)
            assertEquals(0, report.duplicateInputCount)
        } finally { database.close() }
    }

    @Test fun localImportKeepsApplicationErrorWithoutAddingFailedTrack() = runTest {
        val database = createPlaylistTestDatabase()
        try {
            seedNavidromeSource(database, "nav", "alpha", "cred", "用户来源")
            database.trackDao().upsertAll(listOf(navidromeTrackEntity("nav", "song-a1")))
            val delegate = RecordingPlaylistsHttpClient(remotePlaylistsByUser = mutableMapOf("alpha" to linkedMapOf()))
            val client = object : LyricsHttpClient {
                override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> =
                    if (parseUrl(request.url)?.encodedPath?.endsWith("/updatePlaylist") == true) {
                        Result.success(LyricsHttpResponse(503, ""))
                    } else delegate.request(request)
            }
            val repository = RoomPlaylistRepository(database, MapPlaylistSecureCredentialStore(mutableMapOf("cred" to "pass")), client)
            val playlist = repository.createPlaylist("用户歌单").getOrThrow()
            val report = repository.importPlaylistText(playlist.id, "Song song-a1 - Artist nav").getOrThrow()
            val issue = report.failedLines.single()
            assertEquals("Navidrome updatePlaylist failed, HTTP 503", resolveUiText(issue.messageUiText(), AppLanguage.English))
            assertEquals("Navidrome updatePlaylist 失敗，HTTP 503", resolveUiText(issue.messageUiText(), AppLanguage.TraditionalChinese))
            assertEquals("Song song-a1 - Artist nav", issue.rawText)
            assertEquals(1, issue.lineNumber)
            assertEquals(0, report.addedCount)
            assertTrue(database.playlistTrackDao().getByPlaylistId(playlist.id).isEmpty())
        } finally { database.close() }
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
    indexMode: String = "LOCAL_INDEX",
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
            indexMode = indexMode,
        ),
    )
}

private suspend fun seedEmbySource(database: LynMusicDatabase) {
    database.importSourceDao().upsert(
        ImportSourceEntity(
            id = "emby-source",
            type = "EMBY",
            label = "Emby",
            rootReference = "https://emby.example.com/emby",
            server = null,
            shareName = null,
            directoryPath = null,
            username = "demo",
            credentialKey = "emby-cred",
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
    artistName: String = "Artist A",
    artworkLocator: String? = null,
): TrackEntity {
    return TrackEntity(
        id = id,
        sourceId = sourceId,
        title = title,
        artistId = "artist:${artistName.trim().lowercase()}",
        artistName = artistName,
        albumId = "album:${artistName.trim().lowercase()}:album one",
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

private fun playlistEntity(
    id: String,
    name: String,
    createdLocally: Boolean,
    updatedAt: Long = 10L,
): PlaylistEntity {
    return PlaylistEntity(
        id = id,
        name = name,
        normalizedName = name.trim().lowercase(),
        createdLocally = createdLocally,
        createdAt = 1L,
        updatedAt = updatedAt,
    )
}

private fun playlistRemoteBindingEntity(
    playlistId: String,
    sourceId: String,
    remotePlaylistId: String,
    remoteName: String = remotePlaylistId,
): PlaylistRemoteBindingEntity {
    return PlaylistRemoteBindingEntity(
        playlistId = playlistId,
        sourceId = sourceId,
        remotePlaylistId = remotePlaylistId,
        remoteName = remoteName,
        lastSyncedAt = 1L,
    )
}

private fun embyTrack(itemId: String): Track {
    return Track(
        id = embyTrackIdFor("emby-source", itemId),
        sourceId = "emby-source",
        title = "Song $itemId",
        artistName = "Artist Emby",
        albumTitle = "Album Emby",
        durationMs = 215_000L,
        mediaLocator = buildEmbySongLocator("emby-source", itemId),
        relativePath = "Artist Emby/Album Emby/Song $itemId.flac",
    )
}

private fun embyTrackEntity(itemId: String): TrackEntity {
    return TrackEntity(
        id = embyTrackIdFor("emby-source", itemId),
        sourceId = "emby-source",
        title = "Song $itemId",
        artistId = "artist:emby",
        artistName = "Artist Emby",
        albumId = "album:emby",
        albumTitle = "Album Emby",
        durationMs = 215_000L,
        trackNumber = 1,
        discNumber = 1,
        mediaLocator = buildEmbySongLocator("emby-source", itemId),
        relativePath = "Artist Emby/Album Emby/Song $itemId.flac",
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
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
                    url.parameters["name"]?.let { playlist.name = it }
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
            val coverArtField = playlist.coverArt?.let { ",\"coverArt\":\"$it\"" }.orEmpty()
            """{"id":"${playlist.id}","name":"${playlist.name}","songCount":${playlist.songIds.size}$coverArtField}"""
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

private class RecordingOnlinePlaylistsHttpClient(
    val remotePlaylistsByUser: MutableMap<String, LinkedHashMap<String, RemotePlaylistState>>,
    private val searchResultsByQuery: MutableMap<String, List<OnlineSearchSongState>>,
    private val failingUpdateSongIds: MutableSet<String> = mutableSetOf(),
) : LyricsHttpClient {
    val requestedEndpoints = mutableListOf<String>()
    val requestedUpdateBatches = mutableListOf<List<String>>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        val endpoint = url.encodedPath.substringAfterLast('/')
        val username = url.parameters["u"].orEmpty()
        requestedEndpoints += endpoint
        val playlists = remotePlaylistsByUser.getOrPut(username) { linkedMapOf() }
        return Result.success(
            when (endpoint) {
                "getPlaylist" -> {
                    val playlistId = url.parameters["id"].orEmpty()
                    LyricsHttpResponse(200, getPlaylistBody(playlists.getValue(playlistId)))
                }

                "search3" -> {
                    val query = url.parameters["query"].orEmpty()
                    val songCount = url.parameters["songCount"]?.toIntOrNull() ?: 10
                    LyricsHttpResponse(200, search3Body(searchResultsByQuery[query].orEmpty().take(songCount)))
                }

                "updatePlaylist" -> {
                    val playlistId = url.parameters["playlistId"].orEmpty()
                    val songIds = url.parameters.getAll("songIdToAdd").orEmpty()
                    requestedUpdateBatches += songIds
                    if (songIds.any { it in failingUpdateSongIds }) {
                        LyricsHttpResponse(200, errorBody("add failed"))
                    } else {
                        playlists.getValue(playlistId).songIds += songIds
                        LyricsHttpResponse(200, okBody())
                    }
                }

                else -> error("Unexpected online playlist request endpoint: $endpoint")
            },
        )
    }

    private fun okBody(): String {
        return """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
    }

    private fun errorBody(message: String): String {
        return """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"message":"$message"}}}"""
    }

    private fun getPlaylistBody(playlist: RemotePlaylistState): String {
        val entries = playlist.songIds.joinToString(",") { songId ->
            """{"id":"$songId","title":"Song $songId","artist":"Artist A","album":"Album A"}"""
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

    private fun search3Body(songs: List<OnlineSearchSongState>): String {
        val items = songs.joinToString(",") { song ->
            """{"id":"${song.id}","title":"${song.title}","artist":"${song.artist}","album":"Album A"}"""
        }
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "searchResult3": {
                  "song": [$items]
                }
              }
            }
        """.trimIndent()
    }
}

private class RecordingEmbyPlaylistsHttpClient : LyricsHttpClient {
    val requestPaths = mutableListOf<String>()
    val playlistItemIds = mutableListOf<String>()
    var playlistName: String = "Road Trip"

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        requestPaths += url.encodedPath
        val body = when (url.encodedPath) {
            "/emby/Users/user-1/Items" -> """{"Items":[],"TotalRecordCount":0}"""
            "/emby/Users/user-1/Items/pl-1" -> """{"Id":"pl-1","Name":"$playlistName","Type":"Playlist"}"""
            "/emby/Playlists" -> {
                playlistName = url.parameters["Name"] ?: playlistName
                """{"Id":"pl-1","Name":"$playlistName"}"""
            }

            "/emby/Playlists/pl-1/Items" -> when (request.method.name) {
                "POST" -> {
                    playlistItemIds += url.parameters["Ids"].orEmpty()
                    ""
                }

                "GET" -> {
                    val items = playlistItemIds.mapIndexed { index, itemId ->
                        """{"Id":"$itemId","PlaylistItemId":"entry-$index"}"""
                    }.joinToString(",")
                    """{"Items":[$items],"TotalRecordCount":${playlistItemIds.size}}"""
                }

                else -> error("Unexpected Emby playlist method: ${request.method}")
            }

            "/emby/Playlists/pl-1/Items/Delete" -> {
                val index = url.parameters["EntryIds"].orEmpty().removePrefix("entry-").toInt()
                playlistItemIds.removeAt(index)
                ""
            }

            "/emby/Items/pl-1" -> when (request.method.name) {
                "POST" -> {
                    playlistName = request.body
                        ?.substringAfter("\"Name\":\"", missingDelimiterValue = playlistName)
                        ?.substringBefore('"')
                        ?: playlistName
                    ""
                }

                "DELETE" -> ""
                else -> error("Unexpected Emby playlist item method: ${request.method}")
            }

            else -> error("Unexpected Emby playlist request: ${request.method} ${request.url}")
        }
        return Result.success(LyricsHttpResponse(200, body))
    }
}

private data class RemotePlaylistState(
    val id: String,
    var name: String,
    val songIds: MutableList<String>,
    val coverArt: String? = null,
)

private data class OnlineSearchSongState(
    val id: String,
    val title: String,
    val artist: String,
    val starred: String? = null,
)

private data class OnlineSearchAlbumState(
    val id: String,
    val name: String,
    val artist: String,
    val coverArt: String? = null,
)

private data class OnlineSearchArtistState(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val albumCount: Int? = null,
)

private class RecordingOnlineAlbumsHttpClient(
    private val albums: List<OnlineSearchAlbumState>,
) : LyricsHttpClient {
    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        val endpoint = url.encodedPath.substringAfterLast('/')
        check(endpoint == "getAlbumList2") { "Unexpected online albums request endpoint: $endpoint" }
        val offset = url.parameters["offset"]?.toIntOrNull() ?: 0
        val size = url.parameters["size"]?.toIntOrNull() ?: albums.size
        return Result.success(
            LyricsHttpResponse(
                statusCode = 200,
                body = albumListBody(albums.drop(offset).take(size)),
            ),
        )
    }

    private fun albumListBody(albums: List<OnlineSearchAlbumState>): String {
        val albumItems = albums.joinToString(",") { album ->
            val coverArtField = album.coverArt?.let { ",\"coverArt\":\"$it\"" }.orEmpty()
            """{"id":"${album.id}","name":"${album.name}","artist":"${album.artist}","songCount":10$coverArtField}"""
        }
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "albumList2": {
                  "album": [$albumItems]
                }
              }
            }
        """.trimIndent()
    }
}

private class RecordingOnlineArtistsHttpClient(
    private val artists: List<OnlineSearchArtistState> = emptyList(),
    private val albumsByArtistId: Map<String, List<OnlineSearchAlbumState>> = emptyMap(),
) : LyricsHttpClient {
    val requestedEndpoints = mutableListOf<String>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        val endpoint = url.encodedPath.substringAfterLast('/')
        requestedEndpoints += endpoint
        return Result.success(
            when (endpoint) {
                "getArtists" -> LyricsHttpResponse(
                    statusCode = 200,
                    body = getArtistsBody(artists),
                )

                "getArtist" -> LyricsHttpResponse(
                    statusCode = 200,
                    body = getArtistBody(
                        artistId = url.parameters["id"].orEmpty(),
                        albums = albumsByArtistId[url.parameters["id"].orEmpty()].orEmpty(),
                    ),
                )

                else -> error("Unexpected online artists request endpoint: $endpoint")
            },
        )
    }

    private fun getArtistsBody(artists: List<OnlineSearchArtistState>): String {
        val artistItems = artists.joinToString(",") { artist -> artistJson(artist) }
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "artists": {
                  "index": [
                    {
                      "name": "A",
                      "artist": [$artistItems]
                    }
                  ]
                }
              }
            }
        """.trimIndent()
    }

    private fun getArtistBody(
        artistId: String,
        albums: List<OnlineSearchAlbumState>,
    ): String {
        val albumItems = albums.joinToString(",") { album -> albumJson(album) }
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "artist": {
                  "id": "$artistId",
                  "name": "Artist $artistId",
                  "album": [$albumItems]
                }
              }
            }
        """.trimIndent()
    }
}

private data class RecordedSearch3Request(
    val query: String,
    val songOffset: Int,
    val songCount: Int,
    val albumOffset: Int,
    val albumCount: Int,
    val artistOffset: Int,
    val artistCount: Int,
)

private data class OnlineFavoriteSongState(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
)

private class RecordingOnlineSearchHttpClient(
    private val songs: List<OnlineSearchSongState>,
    private val albums: List<OnlineSearchAlbumState>,
    private val artists: List<OnlineSearchArtistState>,
) : LyricsHttpClient {
    val searchRequests = mutableListOf<RecordedSearch3Request>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        val endpoint = url.encodedPath.substringAfterLast('/')
        check(endpoint == "search3") { "Unexpected online search request endpoint: $endpoint" }
        val recordedRequest = RecordedSearch3Request(
            query = url.parameters["query"].orEmpty(),
            songOffset = url.parameters["songOffset"]?.toIntOrNull() ?: 0,
            songCount = url.parameters["songCount"]?.toIntOrNull() ?: 0,
            albumOffset = url.parameters["albumOffset"]?.toIntOrNull() ?: 0,
            albumCount = url.parameters["albumCount"]?.toIntOrNull() ?: 0,
            artistOffset = url.parameters["artistOffset"]?.toIntOrNull() ?: 0,
            artistCount = url.parameters["artistCount"]?.toIntOrNull() ?: 0,
        )
        searchRequests += recordedRequest
        return Result.success(
            LyricsHttpResponse(
                statusCode = 200,
                body = search3Body(
                    songs = songs.drop(recordedRequest.songOffset).take(recordedRequest.songCount),
                    albums = albums.drop(recordedRequest.albumOffset).take(recordedRequest.albumCount),
                    artists = artists.drop(recordedRequest.artistOffset).take(recordedRequest.artistCount),
                ),
            ),
        )
    }

    private fun search3Body(
        songs: List<OnlineSearchSongState>,
        albums: List<OnlineSearchAlbumState>,
        artists: List<OnlineSearchArtistState>,
    ): String {
        val songItems = songs.joinToString(",") { song ->
            val starredField = song.starred?.let { ",\"starred\":\"$it\"" }.orEmpty()
            """{"id":"${song.id}","title":"${song.title}","artist":"${song.artist}","album":"Album A"$starredField}"""
        }
        val albumItems = albums.joinToString(",") { album ->
            albumJson(album)
        }
        val artistItems = artists.joinToString(",") { artist ->
            artistJson(artist)
        }
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "searchResult3": {
                  "song": [$songItems],
                  "album": [$albumItems],
                  "artist": [$artistItems]
                }
              }
            }
        """.trimIndent()
    }
}

private fun albumJson(album: OnlineSearchAlbumState): String {
    val coverArtField = album.coverArt?.let { ",\"coverArt\":\"$it\"" }.orEmpty()
    return """{"id":"${album.id}","name":"${album.name}","artist":"${album.artist}","songCount":10$coverArtField}"""
}

private fun artistJson(artist: OnlineSearchArtistState): String {
    val songCountField = artist.songCount?.let { ",\"songCount\":$it" }.orEmpty()
    val albumCountField = artist.albumCount?.let { ",\"albumCount\":$it" }.orEmpty()
    return """{"id":"${artist.id}","name":"${artist.name}"$songCountField$albumCountField}"""
}

private class RecordingOnlineFavoritesHttpClient(
    private val starredSongsByUser: Map<String, List<OnlineFavoriteSongState>>,
) : LyricsHttpClient {
    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        val endpoint = url.encodedPath.substringAfterLast('/')
        val username = url.parameters["u"].orEmpty()
        return Result.success(
            when (endpoint) {
                "getStarred2" -> LyricsHttpResponse(200, getStarred2Body(starredSongsByUser[username].orEmpty()))
                else -> error("Unexpected online favorites request endpoint: $endpoint")
            },
        )
    }

    private fun getStarred2Body(songs: List<OnlineFavoriteSongState>): String {
        val items = songs.joinToString(",") { song ->
            """{"id":"${song.id}","title":"${song.title}","artist":"${song.artist}","album":"${song.album}"}"""
        }
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "starred2": {
                  "song": [$items]
                }
              }
            }
        """.trimIndent()
    }
}

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
