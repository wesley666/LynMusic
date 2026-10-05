package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.EmbyCredential
import top.iwesley.lyn.music.core.model.EmbySourceDraft
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportScanWarning
import top.iwesley.lyn.music.core.model.ImportStreamingScanReport
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.ImportTrackBatchSink
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.NavidromeLibraryProbe
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.core.model.SubsonicSourceDraft
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.buildIosLocalFolderReference
import top.iwesley.lyn.music.core.model.buildIosLocalMediaLocator
import top.iwesley.lyn.music.core.model.buildEmbySongLocator
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.data.db.FavoriteTrackEntity
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.EMBY_DEVICE_ID_CREDENTIAL_KEY
import top.iwesley.lyn.music.domain.serializeEmbyCredential
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.lastErrorUiText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.resources.*

class ImportSourceRepositoryTest {
    @Test
    fun `normal and streaming warnings keep save-time language after reopening without repeating scans`() = runTest {
        val previous = AppLanguageRuntime.appLanguage.value
        try {
            for (language in listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)) {
                for (streaming in listOf(false, true)) {
                    AppLanguageRuntime.update(language)
                    val dbPath = Files.createTempFile("lynmusic-scan-warnings", ".db")
                    fun openDatabase() = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = dbPath.absolutePathString()))
                    var database = openDatabase()
                    try {
                        val id = if (streaming) "nav-1" else "local-1"
                        val credentials = ImportTestSecureCredentialStore()
                        credentials.put("credential", "password")
                        val source = if (streaming) {
                            navidromeSourceEntity(id, "https://server.example", username = "u", credentialKey = "credential")
                        } else importSourceEntity(id, ImportSourceType.LOCAL_FOLDER, "用户来源", "/用户目录")
                        database.importSourceDao().upsert(source)
                        val rawDetail = "服务端中文详情 %1\$s"
                        val description = uiText(Res.string.source_account_no_syncable_tracks, "Navidrome")
                        val warnings = listOf(
                            ImportScanWarning("当前 Navidrome 账号下没有可同步的歌曲。", description),
                            ImportScanWarning(rawDetail),
                        )
                        val expected = resolveUiText(description, language) + "\n" + rawDetail
                        val report = ImportScanReport(
                            tracks = listOf(ImportedTrackCandidate("用户歌曲", mediaLocator = "file:///用户目录/Song.flac", relativePath = "Song.flac")),
                            warnings = warnings, discoveredAudioFileCount = 2,
                        )
                        val gateway = RecordingImportSourceGateway(scanReport = report)
                        val repository = createRepository(database, gateway, credentials)
                        val summary = assertNotNull(repository.rescanSource(id).getOrThrow())
                        assertEquals(2, summary.discoveredAudioFileCount)
                        assertEquals(1, summary.importedTrackCount)
                        assertEquals(expected, database.importIndexStateDao().getBySourceId(id)?.lastError)
                        val scanCalls = gateway.localFolderScanCount + gateway.navidromeStreamingScanCount
                        assertEquals(1, scanCalls)
                        database.close()
                        database = openDatabase()
                        val reopened = createRepository(database, secureCredentialStore = credentials)
                        val state = assertNotNull(reopened.observeSources().first().single().indexState)
                        assertEquals(expected, state.lastError)
                        val text = assertNotNull(state.lastErrorUiText())
                        for (next in listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese, AppLanguage.English)) {
                            AppLanguageRuntime.update(next)
                            assertEquals(expected, resolveUiText(text, next))
                            assertEquals(expected, database.importIndexStateDao().getBySourceId(id)?.lastError)
                            assertEquals(1, database.trackDao().count())
                            assertEquals(scanCalls, gateway.localFolderScanCount + gateway.navidromeStreamingScanCount)
                        }
                        assertTrue(reopened.rescanSource(id).isSuccess)
                        assertNull(reopened.observeSources().first().single().indexState?.lastError)
                    } finally {
                        database.close()
                        Files.deleteIfExists(dbPath)
                    }
                }
            }
        } finally { AppLanguageRuntime.update(previous) }
    }

    @Test
    fun `scan error keeps save-time language after reopening database and clears after success`() = runTest {
        val previous = AppLanguageRuntime.appLanguage.value
        try {
            for (language in listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)) {
                AppLanguageRuntime.update(language)
                val dbPath = Files.createTempFile("lynmusic-scan-error", ".db")
                fun openDatabase() = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = dbPath.absolutePathString()))
                var database = openDatabase()
                try {
                    val path = "/用户目录/missing %1\$s"
                    database.importSourceDao().upsert(importSourceEntity("local-1", ImportSourceType.LOCAL_FOLDER, "用户来源", path))
                    database.trackDao().upsertAll(listOf(trackEntity("old-track", "local-1", "用户歌曲")))
                    database.importIndexStateDao().upsert(top.iwesley.lyn.music.data.db.ImportIndexStateEntity(
                        sourceId = "local-1", trackCount = 7, remoteTrackCount = 9, lastScannedAt = 42L, lastError = null,
                    ))
                    val description = uiText(Res.string.source_folder_missing, path)
                    val expected = resolveUiText(description, language)
                    val failure = IllegalStateException("wrapper diagnostic", UiTextException(description))
                    val repository = createRepository(database, RecordingImportSourceGateway(localFolderScanHandler = { _, _ -> throw failure }))
                    assertSame(failure, repository.rescanSource("local-1").exceptionOrNull())
                    assertEquals(listOf("old-track"), database.trackDao().getBySourceId("local-1").map { it.id })
                    assertEquals(expected, database.importIndexStateDao().getBySourceId("local-1")?.lastError)
                    database.close()
                    database = openDatabase()
                    val reopened = createRepository(database)
                    val state = assertNotNull(reopened.observeSources().first().single().indexState)
                    assertEquals(expected, state.lastError)
                    assertEquals(7, state.trackCount)
                    assertEquals(9, state.remoteTrackCount)
                    assertEquals(42L, state.lastScannedAt)
                    val text = assertNotNull(state.lastErrorUiText())
                    for (next in listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese, AppLanguage.English)) {
                        AppLanguageRuntime.update(next)
                        assertEquals(expected, resolveUiText(text, next))
                        assertEquals(expected, database.importIndexStateDao().getBySourceId("local-1")?.lastError)
                    }
                    assertTrue(reopened.rescanSource("local-1").isSuccess)
                    val successful = assertNotNull(reopened.observeSources().first().single().indexState)
                    assertNull(successful.lastError)
                    assertNull(successful.lastErrorUiText())
                } finally {
                    database.close()
                    Files.deleteIfExists(dbPath)
                }
            }
        } finally { AppLanguageRuntime.update(previous) }
    }

    @Test
    fun `historical scan diagnostics are read without modifying database content`() = runTest {
        val database = createImportTestDatabase()
        try {
            val original = "历史诊断 source_folder_missing(arg1=/用户目录)"
            database.importSourceDao().upsert(importSourceEntity("local-1", ImportSourceType.LOCAL_FOLDER, "用户来源", "/music"))
            database.importIndexStateDao().upsert(top.iwesley.lyn.music.data.db.ImportIndexStateEntity(
                sourceId = "local-1", trackCount = 4, remoteTrackCount = null, lastScannedAt = 11L, lastError = original,
            ))
            val state = assertNotNull(createRepository(database).observeSources().first().single().indexState)
            for (language in listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)) {
                assertEquals(original, resolveUiText(assertNotNull(state.lastErrorUiText()), language))
            }
            assertEquals(original, database.importIndexStateDao().getBySourceId("local-1")?.lastError)
        } finally { database.close() }
    }


    @Test
    fun `add source rejects duplicate names ignoring case and whitespace`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity(
                id = "local-1",
                type = ImportSourceType.LOCAL_FOLDER,
                label = " 我的音乐源 ",
                rootReference = "folder://music",
            ),
        )
        val repository = createRepository(database = database)

        val result = repository.addWebDavSource(
            WebDavSourceDraft(
                label = "我的音乐源",
                rootUrl = "https://dav.example.com/music",
                username = "",
                password = "",
            ),
        )

        assertEquals("source_name_already_exists", result.exceptionOrNull()?.message)
        assertEquals(1, database.importSourceDao().getAll().size)
    }

    @Test
    fun `import local folder rejects duplicate persistent reference`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity(
                id = "local-1",
                type = ImportSourceType.LOCAL_FOLDER,
                label = "下载目录",
                rootReference = "folder://downloads",
            ),
        )
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                label = "另一个目录",
                persistentReference = "folder://downloads",
            ),
        )
        val repository = createRepository(database = database, gateway = gateway)

        val result = repository.importLocalFolder()

        assertEquals("source_local_folder_already_imported", result.exceptionOrNull()?.message)
        assertEquals(1, database.importSourceDao().getAll().size)
        assertEquals(0, gateway.localFolderScanCount)
    }

    @Test
    fun `iOS folder duplicate detection uses identity instead of bookmark bytes`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity(
                id = "local-1",
                type = ImportSourceType.LOCAL_FOLDER,
                label = "音乐",
                rootReference = buildIosLocalFolderReference("file:///Music", byteArrayOf(1, 2)),
            ),
        )
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                label = "音乐",
                persistentReference = buildIosLocalFolderReference("file:///Music", byteArrayOf(8, 9)),
            ),
        )

        val result = createRepository(database, gateway).importLocalFolder()

        assertEquals("source_local_folder_already_imported", result.exceptionOrNull()?.message)
        assertEquals(0, gateway.localFolderScanCount)
    }

    @Test
    fun `reauthorizing iOS folder preserves source id and label and atomically replaces reference and index`() = runTest {
        val database = createImportTestDatabase()
        val oldReference = buildIosLocalFolderReference("file:///Music", byteArrayOf(1))
        val selectedReference = buildIosLocalFolderReference("file:///Music", byteArrayOf(2))
        val refreshedReference = buildIosLocalFolderReference("file:///Music", byteArrayOf(3))
        database.importSourceDao().upsert(
            importSourceEntity("local-1", ImportSourceType.LOCAL_FOLDER, "音乐 (2)", oldReference),
        )
        database.trackDao().upsertAll(
            listOf(
                trackEntity(
                    id = "track:local-1:old.mp3",
                    sourceId = "local-1",
                    title = "Old",
                    mediaLocator = buildIosLocalMediaLocator("local-1", "old.mp3"),
                    relativePath = "old.mp3",
                ),
            ),
        )
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection("新目录", selectedReference),
            scanReport = ImportScanReport(
                tracks = listOf(
                    ImportedTrackCandidate(
                        title = "New",
                        mediaLocator = buildIosLocalMediaLocator("local-1", "嵌套/new.mp3"),
                        relativePath = "嵌套/new.mp3",
                    ),
                ),
                refreshedPersistentReference = refreshedReference,
            ),
        )

        val summary = createRepository(database, gateway).reauthorizeLocalFolder("local-1").getOrThrow()

        assertEquals("local-1", summary?.sourceId)
        val source = assertNotNull(database.importSourceDao().getById("local-1"))
        assertEquals("音乐 (2)", source.label)
        assertEquals(refreshedReference, source.rootReference)
        val tracks = database.trackDao().getBySourceId("local-1")
        assertEquals(listOf("New"), tracks.map { it.title })
        assertEquals("track:local-1:嵌套/new.mp3", tracks.single().id)
    }

    @Test
    fun `reauthorizing iOS folder cancellation keeps old source and index`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity("local-1", ImportSourceType.LOCAL_FOLDER, "旧目录", "old-reference"),
        )
        database.trackDao().upsertAll(listOf(trackEntity("track-1", "local-1", "Old")))

        val summary = createRepository(database).reauthorizeLocalFolder("local-1").getOrThrow()

        assertNull(summary)
        assertEquals("old-reference", database.importSourceDao().getById("local-1")?.rootReference)
        assertEquals(listOf("Old"), database.trackDao().getBySourceId("local-1").map { it.title })
    }

    @Test
    fun `reauthorizing iOS folder rejects identity used by another source`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity("local-1", ImportSourceType.LOCAL_FOLDER, "旧目录", "old-reference"),
        )
        database.importSourceDao().upsert(
            importSourceEntity(
                "local-2",
                ImportSourceType.LOCAL_FOLDER,
                "冲突目录",
                buildIosLocalFolderReference("file:///Conflict", byteArrayOf(1)),
            ),
        )
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                "新目录",
                buildIosLocalFolderReference("file:///Conflict", byteArrayOf(9)),
            ),
        )

        val result = createRepository(database, gateway).reauthorizeLocalFolder("local-1")

        assertEquals("source_local_folder_already_imported", result.exceptionOrNull()?.message)
        assertEquals("old-reference", database.importSourceDao().getById("local-1")?.rootReference)
        assertEquals(0, gateway.localFolderScanCount)
    }

    @Test
    fun `reauthorizing iOS folder rejects a different identity and keeps old source and index`() = runTest {
        val database = createImportTestDatabase()
        val oldReference = buildIosLocalFolderReference("file:///OldMusic", byteArrayOf(1))
        database.importSourceDao().upsert(
            importSourceEntity("local-1", ImportSourceType.LOCAL_FOLDER, "旧目录", oldReference),
        )
        database.trackDao().upsertAll(
            listOf(trackEntity("track:local-1:same.mp3", "local-1", "Old")),
        )
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                "新目录",
                buildIosLocalFolderReference("file:///NewMusic", byteArrayOf(2)),
            ),
        )

        val result = createRepository(database, gateway).reauthorizeLocalFolder("local-1")

        assertEquals("source_local_folder_mismatch",
            result.exceptionOrNull()?.message,
        )
        val source = assertNotNull(database.importSourceDao().getById("local-1"))
        assertEquals("旧目录", source.label)
        assertEquals(oldReference, source.rootReference)
        assertEquals(listOf("Old"), database.trackDao().getBySourceId("local-1").map { it.title })
        assertEquals(0, gateway.localFolderScanCount)
    }

    @Test
    fun `reauthorizing iOS folder scan failure rolls back source and old index`() = runTest {
        val database = createImportTestDatabase()
        val oldReference = buildIosLocalFolderReference("file:///Music", byteArrayOf(1))
        val selectedReference = buildIosLocalFolderReference("file:///Music", byteArrayOf(2))
        database.importSourceDao().upsert(
            importSourceEntity("local-1", ImportSourceType.LOCAL_FOLDER, "旧目录", oldReference),
        )
        database.trackDao().upsertAll(listOf(trackEntity("track-1", "local-1", "Old")))
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection("新目录", selectedReference),
            localFolderScanHandler = { _, _ -> error("权限失效") },
        )

        val result = createRepository(database, gateway).reauthorizeLocalFolder("local-1")

        assertEquals("权限失效", result.exceptionOrNull()?.message)
        val source = assertNotNull(database.importSourceDao().getById("local-1"))
        assertEquals("旧目录", source.label)
        assertEquals(oldReference, source.rootReference)
        assertEquals(listOf("Old"), database.trackDao().getBySourceId("local-1").map { it.title })
    }

    @Test
    fun `rescanning iOS folder persists refreshed bookmark reference`() = runTest {
        val database = createImportTestDatabase()
        val oldReference = buildIosLocalFolderReference("file:///Music", byteArrayOf(1))
        val refreshedReference = buildIosLocalFolderReference("file:///Music", byteArrayOf(2))
        database.importSourceDao().upsert(
            importSourceEntity("local-1", ImportSourceType.LOCAL_FOLDER, "音乐", oldReference),
        )
        val gateway = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = emptyList(),
                refreshedPersistentReference = refreshedReference,
            ),
        )

        createRepository(database, gateway).rescanSource("local-1").getOrThrow()

        assertEquals(refreshedReference, database.importSourceDao().getById("local-1")?.rootReference)
    }

    @Test
    fun `import local folder disambiguates duplicate name when path differs`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity(
                id = "local-1",
                type = ImportSourceType.LOCAL_FOLDER,
                label = " 下载目录 ",
                rootReference = "folder://downloads",
            ),
        )
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                label = "下载目录",
                persistentReference = "folder://new-downloads",
            ),
        )
        val repository = createRepository(
            database = database,
            gateway = gateway,
        )

        val result = repository.importLocalFolder()

        assertTrue(result.isSuccess)
        val sources = database.importSourceDao().getAll()
        assertEquals(2, sources.size)
        val imported = assertNotNull(sources.firstOrNull { it.rootReference == "folder://new-downloads" })
        assertEquals("下载目录 (2)", imported.label)
        assertEquals(1, gateway.localFolderScanCount)
    }

    @Test
    fun `import local folder disambiguates names matching remote sources`() = runTest {
        val database = createImportTestDatabase()
        listOf(
            importSourceEntity(
                id = "smb-1",
                type = ImportSourceType.SAMBA,
                label = "下载目录",
                rootReference = "Media/Music",
            ),
            importSourceEntity(
                id = "dav-1",
                type = ImportSourceType.WEBDAV,
                label = " 下载目录 ",
                rootReference = "https://dav.example.com/music",
            ),
            importSourceEntity(
                id = "nav-1",
                type = ImportSourceType.NAVIDROME,
                label = "下载目录",
                rootReference = "https://nav.example.com",
            ),
        ).forEach { database.importSourceDao().upsert(it) }
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                label = "下载目录",
                persistentReference = "folder://downloads",
            ),
        )
        val repository = createRepository(database = database, gateway = gateway)

        val result = repository.importLocalFolder()

        assertTrue(result.isSuccess)
        val sources = database.importSourceDao().getAll()
        assertEquals(4, sources.size)
        val imported = assertNotNull(sources.firstOrNull { it.type == ImportSourceType.LOCAL_FOLDER.name })
        assertEquals("下载目录 (2)", imported.label)
        assertEquals(1, gateway.localFolderScanCount)
    }

    @Test
    fun `import local folder advances past existing numeric suffixes ignoring case and whitespace`() = runTest {
        val database = createImportTestDatabase()
        listOf(
            importSourceEntity(
                id = "local-1",
                type = ImportSourceType.LOCAL_FOLDER,
                label = " Music ",
                rootReference = "folder://music-1",
            ),
            importSourceEntity(
                id = "local-2",
                type = ImportSourceType.LOCAL_FOLDER,
                label = "MUSIC (2)",
                rootReference = "folder://music-2",
            ),
        ).forEach { database.importSourceDao().upsert(it) }
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                label = "music",
                persistentReference = "folder://music-3",
            ),
        )

        val result = createRepository(database = database, gateway = gateway).importLocalFolder()

        assertTrue(result.isSuccess)
        val imported = assertNotNull(
            database.importSourceDao().getAll().firstOrNull { it.rootReference == "folder://music-3" },
        )
        assertEquals("music (3)", imported.label)
        assertEquals(1, gateway.localFolderScanCount)
    }

    @Test
    fun `local folder path conflict only checks local folder sources`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity(
                id = "dav-1",
                type = ImportSourceType.WEBDAV,
                label = "云端曲库",
                rootReference = "folder://downloads",
            ),
        )
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                label = "下载目录",
                persistentReference = "folder://downloads",
            ),
        )
        val repository = createRepository(database = database, gateway = gateway)

        val result = repository.importLocalFolder()

        assertTrue(result.isSuccess)
        assertEquals(2, database.importSourceDao().getAll().size)
        assertEquals(1, gateway.localFolderScanCount)
    }

    @Test
    fun `non navidrome scan returns current summary without persisting scan counters`() = runTest {
        val database = createImportTestDatabase()
        val scanReport = ImportScanReport(
            tracks = listOf(
                ImportedTrackCandidate(
                    title = "Good Song",
                    mediaLocator = "file:///music/good.mp3",
                    relativePath = "good.mp3",
                ),
            ),
            discoveredAudioFileCount = 2,
            failures = listOf(ImportScanFailure(relativePath = "bad.mp3", reason = "读取失败。")),
        )
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                label = "下载目录",
                persistentReference = "folder://downloads",
            ),
            scanReport = scanReport,
        )
        val repository = createRepository(database = database, gateway = gateway)

        val summary = repository.importLocalFolder().getOrThrow()

        assertNotNull(summary)
        assertEquals(2, summary.discoveredAudioFileCount)
        assertEquals(1, summary.importedTrackCount)
        assertEquals(listOf("bad.mp3"), summary.failures.map { it.relativePath })
        val indexState = assertNotNull(database.importIndexStateDao().getBySourceId(summary.sourceId))
        assertEquals(1, indexState.trackCount)
        assertEquals(1, database.trackDao().count())
        val storedTrack = database.trackDao().getAll().single()
        assertNull(storedTrack.bitDepth)
        assertNull(storedTrack.samplingRate)
        assertNull(storedTrack.bitRate)
        assertNull(storedTrack.channelCount)
    }

    @Test
    fun `different names and local folder paths can both be added`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway(
            nextLocalFolderSelection = LocalFolderSelection(
                label = "下载目录",
                persistentReference = "folder://downloads",
            ),
        )
        val repository = createRepository(database = database, gateway = gateway)

        assertTrue(repository.importLocalFolder().isSuccess)
        assertTrue(
            repository.addSambaSource(
                SambaSourceDraft(
                    label = "家庭 NAS",
                    server = "nas.local",
                    path = "Media/Music",
                    username = "",
                    password = "",
                ),
            ).isSuccess,
        )

        assertEquals(2, database.importSourceDao().getAll().size)
        assertEquals(1, gateway.localFolderScanCount)
        assertEquals(1, gateway.sambaScanCount)
    }

    @Test
    fun `blank label sources validate against generated fallback labels`() = runTest {
        val database = createImportTestDatabase()
        val normalizedRootUrl = normalizeWebDavRootUrl("https://dav.example.com/music/")
        database.importSourceDao().upsert(
            importSourceEntity(
                id = "local-1",
                type = ImportSourceType.LOCAL_FOLDER,
                label = normalizedRootUrl,
                rootReference = "folder://downloads",
            ),
        )
        val repository = createRepository(database = database)

        val result = repository.addWebDavSource(
            WebDavSourceDraft(
                label = " ",
                rootUrl = "https://dav.example.com/music/",
                username = "",
                password = "",
            ),
        )

        assertEquals("source_name_already_exists", result.exceptionOrNull()?.message)
        assertEquals(1, database.importSourceDao().getAll().size)
    }

    @Test
    fun `samba source rooted at the server keeps its selected folders for rescans`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway()
        val repository = createRepository(database = database, gateway = gateway)

        repository.addSambaSource(
            SambaSourceDraft(
                label = "",
                server = "nas.local",
                username = "",
                password = "",
                selectedDirectories = listOf("Media/Music", "Backup/Audio", "Media/Music/Live"),
            ),
        ).getOrThrow()
        val stored = database.importSourceDao().getAll().single()
        repository.rescanSource(stored.id).getOrThrow()

        assertEquals(null, stored.directoryPath)
        val sources = repository.observeSources().first()
        assertEquals(listOf("Backup/Audio", "Media/Music"), sources.single().source.selectedDirectories)
        assertEquals(null, sources.single().source.path)
        assertEquals(listOf("Backup/Audio", "Media/Music"), gateway.sambaScanDrafts.last().selectedDirectories)
        assertEquals("", gateway.sambaScanDrafts.last().path)
    }

    @Test
    fun `an unreadable selected folder keeps its tracks favorites and embedded lyrics`() = runTest {
        val database = createImportTestDatabase()
        fun candidate(path: String, lyrics: String? = null) = ImportedTrackCandidate(
            title = path.substringAfterLast('/'),
            mediaLocator = "lynmusic-smb://placeholder/$path",
            relativePath = path,
            embeddedLyrics = lyrics,
        )
        val firstScan = RecordingImportSourceGateway(
            scanReport = ImportScanReport(tracks = listOf(candidate("Media/Music/a.mp3"), candidate("Backup/Live/b.mp3", "[00:01]live"))),
        )
        createRepository(database = database, gateway = firstScan).addSambaSource(
            SambaSourceDraft(
                label = "NAS",
                server = "nas.local",
                username = "",
                password = "",
                selectedDirectories = listOf("Media/Music", "Backup/Live"),
            ),
        ).getOrThrow()
        val sourceId = database.importSourceDao().getAll().single().id
        val liveTrack = database.trackDao().getBySourceId(sourceId).single { it.relativePath == "Backup/Live/b.mp3" }
        database.favoriteTrackDao().upsert(FavoriteTrackEntity(liveTrack.id, sourceId, remoteSongId = null, favoritedAt = 1L))

        val rescan = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = listOf(candidate("Media/Music/a.mp3"), candidate("Media/Music/c.mp3")),
                unreadableFolders = listOf("Backup/Live"),
            ),
        )
        createRepository(database = database, gateway = rescan).rescanSource(sourceId).getOrThrow()

        val paths = database.trackDao().getBySourceId(sourceId).map { it.relativePath }.sorted()
        assertEquals(listOf("Backup/Live/b.mp3", "Media/Music/a.mp3", "Media/Music/c.mp3"), paths)
        assertEquals(liveTrack.id, database.favoriteTrackDao().getByTrackId(liveTrack.id)?.trackId)
        assertEquals("[00:01]live", database.lyricsCacheDao().getByTrackIdAndSourceId(liveTrack.id, "embedded-tag")?.rawPayload)
        assertEquals(3, database.importIndexStateDao().getBySourceId(sourceId)?.trackCount)
    }

    @Test
    fun `moving a source to another server drops tracks of folders it cannot read`() = runTest {
        val database = createImportTestDatabase()
        val firstScan = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = listOf(
                    ImportedTrackCandidate(title = "b", mediaLocator = "lynmusic-smb://x/Backup/Live/b.mp3", relativePath = "Backup/Live/b.mp3"),
                ),
            ),
        )
        createRepository(database = database, gateway = firstScan).addSambaSource(
            SambaSourceDraft(label = "NAS", server = "nas.local", username = "", password = "", selectedDirectories = listOf("Backup/Live")),
        ).getOrThrow()
        val sourceId = database.importSourceDao().getAll().single().id

        val moved = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = listOf(
                    ImportedTrackCandidate(title = "a", mediaLocator = "lynmusic-smb://x/Media/a.mp3", relativePath = "Media/a.mp3"),
                ),
                unreadableFolders = listOf("Backup/Live"),
            ),
        )
        createRepository(database = database, gateway = moved).updateSambaSource(
            sourceId = sourceId,
            draft = SambaSourceDraft(
                label = "NAS",
                server = "other-nas.local",
                username = "",
                password = "",
                selectedDirectories = listOf("Backup/Live", "Media"),
            ),
        ).getOrThrow()

        assertEquals(listOf("Media/a.mp3"), database.trackDao().getBySourceId(sourceId).map { it.relativePath })
    }

    @Test
    fun `legacy samba rows with an old root reference still keep unreadable folders on edit`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            ImportSourceEntity(
                id = "smb-old",
                type = "SAMBA",
                label = "NAS",
                // Pre-port schema: the share sat in shareName and rootReference held something else.
                rootReference = "Media",
                server = "nas.local",
                shareName = "Media",
                directoryPath = "Music",
                username = "",
                credentialKey = null,
                allowInsecureTls = false,
                lastScannedAt = null,
                createdAt = 1L,
            ),
        )
        database.trackDao().upsertAll(
            listOf(
                TrackEntity(
                    id = "track:smb-old:live/b.mp3",
                    sourceId = "smb-old",
                    title = "b",
                    artistId = null,
                    artistName = null,
                    albumId = null,
                    albumTitle = null,
                    durationMs = 0L,
                    trackNumber = null,
                    discNumber = null,
                    mediaLocator = "lynmusic-smb://smb-old/Live/b.mp3",
                    relativePath = "Live/b.mp3",
                    artworkLocator = null,
                    sizeBytes = 0L,
                    modifiedAt = 0L,
                ),
            ),
        )
        val gateway = RecordingImportSourceGateway(
            scanReport = ImportScanReport(tracks = emptyList(), unreadableFolders = listOf("Live")),
        )

        createRepository(database = database, gateway = gateway).updateSambaSource(
            sourceId = "smb-old",
            draft = SambaSourceDraft(
                label = "NAS renamed",
                server = "nas.local",
                path = "Media/Music",
                username = "",
                password = "",
                selectedDirectories = listOf("Live", "Studio"),
            ),
        ).getOrThrow()

        assertEquals(listOf("Live/b.mp3"), database.trackDao().getBySourceId("smb-old").map { it.relativePath })
    }

    @Test
    fun `filling in the default port does not count as moving the source`() = runTest {
        val database = createImportTestDatabase()
        val firstScan = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = listOf(
                    ImportedTrackCandidate(title = "b", mediaLocator = "lynmusic-smb://x/Backup/b.mp3", relativePath = "Backup/b.mp3"),
                ),
            ),
        )
        createRepository(database = database, gateway = firstScan).addSambaSource(
            SambaSourceDraft(label = "NAS", server = "nas.local", username = "", password = "", selectedDirectories = listOf("Backup", "Media")),
        ).getOrThrow()
        val sourceId = database.importSourceDao().getAll().single().id

        val rescan = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = listOf(
                    ImportedTrackCandidate(title = "a", mediaLocator = "lynmusic-smb://x/Media/a.mp3", relativePath = "Media/a.mp3"),
                ),
                unreadableFolders = listOf("Backup"),
            ),
        )
        createRepository(database = database, gateway = rescan).updateSambaSource(
            sourceId = sourceId,
            draft = SambaSourceDraft(
                label = "NAS",
                server = "nas.local",
                port = 445,
                username = "",
                password = "",
                selectedDirectories = listOf("Backup", "Media"),
            ),
        ).getOrThrow()

        assertEquals(
            listOf("Backup/b.mp3", "Media/a.mp3"),
            database.trackDao().getBySourceId(sourceId).map { it.relativePath }.sorted(),
        )
    }

    @Test
    fun `legacy samba rows keep their root and scan it whole`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway()
        val repository = createRepository(database = database, gateway = gateway)
        database.importSourceDao().upsert(
            ImportSourceEntity(
                id = "smb-legacy",
                type = "SAMBA",
                label = "NAS",
                rootReference = "Media/Music",
                server = "nas.local",
                shareName = "445",
                directoryPath = "Media/Music",
                username = "",
                credentialKey = null,
                allowInsecureTls = false,
                lastScannedAt = null,
                createdAt = 1L,
            ),
        )

        repository.rescanSource("smb-legacy").getOrThrow()

        val draft = gateway.sambaScanDrafts.single()
        assertEquals("Media/Music", draft.path)
        assertEquals(emptyList(), draft.selectedDirectories)
    }

    @Test
    fun `testing samba source does not persist source rows`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway()
        val repository = createRepository(database = database, gateway = gateway)

        val result = repository.testSambaSource(
            SambaSourceDraft(
                label = "家庭 NAS",
                server = "nas.local",
                path = "Media/Music",
                username = "",
                password = "",
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals(1, gateway.sambaTestCount)
        assertEquals(0, gateway.sambaScanCount)
        assertTrue(database.importSourceDao().getAll().isEmpty())
    }

    @Test
    fun `adding navidrome source returns scan summary with failures`() = runTest {
        val database = createImportTestDatabase()
        val scanReport = ImportScanReport(
            tracks = listOf(
                ImportedTrackCandidate(
                    title = "Blue",
                    mediaLocator = "lynmusic-navidrome://navidrome-1/song-1",
                    relativePath = "Artist A/Album A/Blue.flac",
                    bitDepth = 24,
                    samplingRate = 96_000,
                    bitRate = 2_810,
                    channelCount = 2,
                ),
            ),
            discoveredAudioFileCount = 2,
            failures = listOf(
                ImportScanFailure(
                    relativePath = "Artist A/Album A/Bad.ogg",
                    reason = "当前平台暂不支持导入该音频格式。",
                ),
            ),
        )
        val gateway = RecordingImportSourceGateway(scanReport = scanReport)
        val repository = createRepository(database = database, gateway = gateway)

        val summary = repository.addNavidromeSource(
            NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://nav.example.com",
                username = "demo",
                password = "secret",
            ),
        ).getOrThrow()

        assertEquals(2, summary.discoveredAudioFileCount)
        assertEquals(1, summary.importedTrackCount)
        assertEquals(listOf("Artist A/Album A/Bad.ogg"), summary.failures.map { it.relativePath })
        assertEquals(1, gateway.navidromeStreamingScanCount)
        assertNotNull(database.importSourceDao().getById(summary.sourceId))
        assertEquals(1, database.trackDao().count())
        val storedTrack = database.trackDao().getAll().single()
        assertEquals(24, storedTrack.bitDepth)
        assertEquals(96_000, storedTrack.samplingRate)
        assertEquals(2_810, storedTrack.bitRate)
        assertEquals(2, storedTrack.channelCount)
        val domainTrack = storedTrack.toDomain()
        assertEquals(24, domainTrack.bitDepth)
        assertEquals(96_000, domainTrack.samplingRate)
        assertEquals(2_810, domainTrack.bitRate)
        assertEquals(2, domainTrack.channelCount)
    }

    @Test
    fun `adding navidrome source forwards scan progress and reports persisting phase`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = listOf(
                    ImportedTrackCandidate(
                        title = "Blue",
                        mediaLocator = "lynmusic-navidrome://navidrome-1/song-1",
                        relativePath = "Artist A/Album A/Blue.flac",
                    ),
                ),
            ),
        )
        val repository = createRepository(database = database, gateway = gateway)
        val progressEvents = mutableListOf<ImportScanProgress>()

        repository.addNavidromeSource(
            NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://nav.example.com",
                username = "demo",
                password = "secret",
            ),
            ImportScanProgressSink { progressEvents += it },
        ).getOrThrow()

        assertEquals(
            listOf(ImportScanPhase.Scanning, ImportScanPhase.Persisting),
            progressEvents.map { it.phase },
        )
        assertEquals(1, progressEvents.first().importedTrackCount)
        assertEquals(1, progressEvents.last().importedTrackCount)
        assertEquals(1, gateway.navidromeStreamingScanCount)
    }

    @Test
    fun `updating navidrome source with blank password keeps existing credential`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = listOf(
                    ImportedTrackCandidate(
                        title = "Blue",
                        mediaLocator = "lynmusic-navidrome://nav-1/song-1",
                        relativePath = "Artist A/Album A/Blue.flac",
                    ),
                ),
                discoveredAudioFileCount = 2,
                failures = listOf(
                    ImportScanFailure(
                        relativePath = "Artist A/Album A/Bad.ogg",
                        reason = "当前平台暂不支持导入该音频格式。",
                    ),
                ),
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "old-password"))
        database.importSourceDao().upsert(
            ImportSourceEntity(
                id = "nav-1",
                type = ImportSourceType.NAVIDROME.name,
                label = "Navidrome",
                rootReference = "https://nav.example.com",
                server = null,
                shareName = null,
                directoryPath = null,
                username = "demo",
                credentialKey = "credential-nav-1",
                allowInsecureTls = false,
                enabled = true,
                lastScannedAt = null,
                createdAt = 1L,
            ),
        )
        val repository = RoomImportSourceRepository(
            database = database,
            gateway = gateway,
            secureCredentialStore = credentials,
        )

        val result = repository.updateNavidromeSource(
            sourceId = "nav-1",
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://nav2.example.com",
                username = "demo2",
                password = "",
            ),
        )

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(2, summary.discoveredAudioFileCount)
        assertEquals(1, summary.importedTrackCount)
        assertEquals(listOf("Artist A/Album A/Bad.ogg"), summary.failures.map { it.relativePath })
        assertEquals(1, gateway.navidromeStreamingScanCount)
        assertEquals("old-password", gateway.lastNavidromeScanDraft?.password)
        val updated = assertNotNull(database.importSourceDao().getById("nav-1"))
        assertEquals("https://nav2.example.com", updated.rootReference)
        assertEquals("demo2", updated.username)
        assertEquals("old-password", credentials.get("credential-nav-1"))
    }

    @Test
    fun `adding navidrome source online persists source and index state`() = runTest {
        val database = createImportTestDatabase()
        val credentials = ImportTestSecureCredentialStore()
        val repository = createRepository(database = database, secureCredentialStore = credentials)

        val summary = repository.addNavidromeSourceOnline(
            draft = NavidromeSourceDraft(
                label = "Navidrome Online",
                baseUrl = "https://nav.example.com",
                username = "demo",
                password = "secret",
            ),
            remoteTrackCount = 42,
        ).getOrThrow()

        val stored = assertNotNull(database.importSourceDao().getById(summary.sourceId))
        assertEquals(ImportSourceIndexMode.ONLINE.name, stored.indexMode)
        assertEquals("https://nav.example.com", stored.rootReference)
        assertEquals("demo", stored.username)
        assertEquals("secret", credentials.get(stored.credentialKey.orEmpty()))
        val indexState = assertNotNull(database.importIndexStateDao().getBySourceId(summary.sourceId))
        assertEquals(0, indexState.trackCount)
        assertEquals(42, indexState.remoteTrackCount)
        assertEquals(42, summary.discoveredAudioFileCount)
        assertEquals(0, summary.importedTrackCount)
    }

    @Test
    fun `updating online navidrome source writes new credential key after db commit`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            navidromeSourceEntity(
                id = "nav-1",
                rootReference = "https://old.example.com",
                username = "demo",
                credentialKey = "credential-nav-1",
                indexMode = ImportSourceIndexMode.ONLINE.name,
            ),
        )
        database.importIndexStateDao().upsert(
            top.iwesley.lyn.music.data.db.ImportIndexStateEntity(
                sourceId = "nav-1",
                trackCount = 7,
                remoteTrackCount = 11,
                lastScannedAt = 1L,
                lastError = "old error",
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "old-password"))
        val gateway = RecordingImportSourceGateway(navidromeProbe = NavidromeLibraryProbe(totalTrackCount = 77))
        val repository = createRepository(database = database, gateway = gateway, secureCredentialStore = credentials)

        val summary = repository.updateNavidromeSource(
            sourceId = "nav-1",
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://new.example.com",
                username = "demo2",
                password = "new-password",
            ),
            keepExistingCredentialWhenBlankPassword = true,
        ).getOrThrow()

        val stored = assertNotNull(database.importSourceDao().getById("nav-1"))
        val newCredentialKey = assertNotNull(stored.credentialKey)
        assertTrue(newCredentialKey != "credential-nav-1")
        assertEquals(ImportSourceIndexMode.ONLINE.name, stored.indexMode)
        assertEquals("https://new.example.com", stored.rootReference)
        assertEquals("demo2", stored.username)
        assertNull(credentials.get("credential-nav-1"))
        assertEquals("new-password", credentials.get(newCredentialKey))
        val indexState = assertNotNull(database.importIndexStateDao().getBySourceId("nav-1"))
        assertEquals(7, indexState.trackCount)
        assertEquals(77, indexState.remoteTrackCount)
        assertNull(indexState.lastError)
        assertEquals(1, gateway.navidromeProbeCount)
        assertEquals("new-password", gateway.lastNavidromeProbeDraft?.password)
        assertEquals(77, summary.discoveredAudioFileCount)
    }

    @Test
    fun `updating online navidrome source rejects unsupported online paging without writing state`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            navidromeSourceEntity(
                id = "nav-1",
                rootReference = "https://old.example.com",
                username = "demo",
                credentialKey = "credential-nav-1",
                indexMode = ImportSourceIndexMode.ONLINE.name,
            ),
        )
        database.importIndexStateDao().upsert(
            top.iwesley.lyn.music.data.db.ImportIndexStateEntity(
                sourceId = "nav-1",
                trackCount = 7,
                remoteTrackCount = 11,
                lastScannedAt = 1L,
                lastError = "old error",
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "old-password"))
        val gateway = RecordingImportSourceGateway(
            navidromeProbe = NavidromeLibraryProbe(
                totalTrackCount = null,
                supportsOnlineLibraryPaging = false,
            ),
        )
        val repository = createRepository(database = database, gateway = gateway, secureCredentialStore = credentials)

        val result = repository.updateNavidromeSource(
            sourceId = "nav-1",
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://new.example.com",
                username = "demo2",
                password = "new-password",
            ),
            keepExistingCredentialWhenBlankPassword = true,
        )

        assertTrue(result.isFailure)
        assertEquals("source_online_paging_required",
            result.exceptionOrNull()?.message,
        )
        val stored = assertNotNull(database.importSourceDao().getById("nav-1"))
        assertEquals("https://old.example.com", stored.rootReference)
        assertEquals("demo", stored.username)
        assertEquals("credential-nav-1", stored.credentialKey)
        assertEquals("old-password", credentials.get("credential-nav-1"))
        val indexState = assertNotNull(database.importIndexStateDao().getBySourceId("nav-1"))
        assertEquals(7, indexState.trackCount)
        assertEquals(11, indexState.remoteTrackCount)
        assertEquals("old error", indexState.lastError)
    }

    @Test
    fun `updating online navidrome source accepts online paging without remote count`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            navidromeSourceEntity(
                id = "nav-1",
                rootReference = "https://old.example.com",
                username = "demo",
                credentialKey = "credential-nav-1",
                indexMode = ImportSourceIndexMode.ONLINE.name,
            ),
        )
        database.importIndexStateDao().upsert(
            top.iwesley.lyn.music.data.db.ImportIndexStateEntity(
                sourceId = "nav-1",
                trackCount = 7,
                remoteTrackCount = 11,
                lastScannedAt = 1L,
                lastError = "old error",
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "old-password"))
        val gateway = RecordingImportSourceGateway(
            navidromeProbe = NavidromeLibraryProbe(
                totalTrackCount = null,
                supportsOnlineLibraryPaging = true,
            ),
        )
        val repository = createRepository(database = database, gateway = gateway, secureCredentialStore = credentials)

        val summary = repository.updateNavidromeSource(
            sourceId = "nav-1",
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://new.example.com",
                username = "demo2",
                password = "",
            ),
            keepExistingCredentialWhenBlankPassword = true,
        ).getOrThrow()

        val stored = assertNotNull(database.importSourceDao().getById("nav-1"))
        assertEquals(ImportSourceIndexMode.ONLINE.name, stored.indexMode)
        assertEquals("https://new.example.com", stored.rootReference)
        assertEquals("demo2", stored.username)
        assertEquals("credential-nav-1", stored.credentialKey)
        assertEquals("old-password", credentials.get("credential-nav-1"))
        val indexState = assertNotNull(database.importIndexStateDao().getBySourceId("nav-1"))
        assertEquals(7, indexState.trackCount)
        assertNull(indexState.remoteTrackCount)
        assertNull(indexState.lastError)
        assertEquals(0, summary.discoveredAudioFileCount)
    }

    @Test
    fun `rescanning online navidrome source rejects unsupported online paging without refreshing state`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            navidromeSourceEntity(
                id = "nav-1",
                rootReference = "https://old.example.com",
                username = "demo",
                credentialKey = "credential-nav-1",
                indexMode = ImportSourceIndexMode.ONLINE.name,
            ),
        )
        database.importIndexStateDao().upsert(
            top.iwesley.lyn.music.data.db.ImportIndexStateEntity(
                sourceId = "nav-1",
                trackCount = 7,
                remoteTrackCount = 11,
                lastScannedAt = 1L,
                lastError = "old error",
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "old-password"))
        val gateway = RecordingImportSourceGateway(
            navidromeProbe = NavidromeLibraryProbe(
                totalTrackCount = null,
                supportsOnlineLibraryPaging = false,
            ),
        )
        val repository = createRepository(database = database, gateway = gateway, secureCredentialStore = credentials)

        val result = repository.rescanSource("nav-1")

        assertTrue(result.isFailure)
        assertEquals("source_online_paging_required",
            result.exceptionOrNull()?.message,
        )
        val stored = assertNotNull(database.importSourceDao().getById("nav-1"))
        assertEquals(ImportSourceIndexMode.ONLINE.name, stored.indexMode)
        assertNull(stored.lastScannedAt)
        val indexState = assertNotNull(database.importIndexStateDao().getBySourceId("nav-1"))
        assertEquals(7, indexState.trackCount)
        assertEquals(11, indexState.remoteTrackCount)
        assertEquals(1L, indexState.lastScannedAt)
        assertEquals("old error", indexState.lastError)
        assertEquals("old-password", credentials.get("credential-nav-1"))
    }

    @Test
    fun `updating subsonic source requires credential when auth mode changes`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway()
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-sub-1" to "old-password"))
        database.importSourceDao().upsert(
            ImportSourceEntity(
                id = "sub-1",
                type = ImportSourceType.SUBSONIC.name,
                label = "Subsonic",
                rootReference = "https://sub.example.com",
                server = null,
                shareName = null,
                directoryPath = null,
                username = "demo",
                credentialKey = "credential-sub-1",
                allowInsecureTls = false,
                enabled = true,
                lastScannedAt = null,
                createdAt = 1L,
                authMode = SubsonicAuthMode.PASSWORD.name,
            ),
        )
        val repository = RoomImportSourceRepository(
            database = database,
            gateway = gateway,
            secureCredentialStore = credentials,
        )

        val result = repository.updateSubsonicSource(
            sourceId = "sub-1",
            draft = SubsonicSourceDraft(
                label = "Subsonic",
                baseUrl = "https://sub.example.com",
                credential = "",
                authMode = SubsonicAuthMode.API_KEY,
            ),
            keepExistingCredentialWhenBlankCredential = true,
        )

        assertEquals("source_authentication_changed_api_key", result.exceptionOrNull()?.message)
        assertEquals(0, gateway.subsonicScanCount)
        assertNull(gateway.lastSubsonicScanDraft)
        val unchanged = assertNotNull(database.importSourceDao().getById("sub-1"))
        assertEquals(SubsonicAuthMode.PASSWORD.name, unchanged.authMode)
        assertEquals("old-password", credentials.get("credential-sub-1"))
    }

    @Test
    fun `adding subsonic api key source does not persist username`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway()
        val credentials = ImportTestSecureCredentialStore()
        val repository = RoomImportSourceRepository(
            database = database,
            gateway = gateway,
            secureCredentialStore = credentials,
        )

        val summary = repository.addSubsonicSource(
            SubsonicSourceDraft(
                label = "Subsonic",
                baseUrl = "https://sub.example.com",
                username = "demo",
                credential = " api-key ",
                authMode = SubsonicAuthMode.API_KEY,
            ),
        ).getOrThrow()

        assertEquals(1, gateway.subsonicScanCount)
        assertEquals("", gateway.lastSubsonicScanDraft?.username)
        assertEquals("api-key", gateway.lastSubsonicScanDraft?.credential)
        val stored = assertNotNull(database.importSourceDao().getById(summary.sourceId))
        assertNull(stored.username)
        assertEquals(SubsonicAuthMode.API_KEY.name, stored.authMode)
        assertEquals("api-key", credentials.get("credential-${summary.sourceId}"))
    }

    @Test
    fun `adding emby source stores token credential and imports tracks`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway(
            embyScanReportFactory = { sourceId ->
                ImportScanReport(
                    tracks = listOf(
                        ImportedTrackCandidate(
                            title = "Emby Song",
                            mediaLocator = buildEmbySongLocator(sourceId, "song-1"),
                            relativePath = "Artist/Album/Emby Song.flac",
                        ),
                    ),
                    discoveredAudioFileCount = 1,
                )
            },
        )
        val credentials = ImportTestSecureCredentialStore()
        val repository = RoomImportSourceRepository(
            database = database,
            gateway = gateway,
            secureCredentialStore = credentials,
        )

        val summary = repository.addEmbySource(
            EmbySourceDraft(
                label = " Emby ",
                baseUrl = "https://emby.example.com/",
                username = " demo ",
                password = "secret",
            ),
        ).getOrThrow()

        assertEquals(1, gateway.embyTestCount)
        assertEquals(1, gateway.embyScanCount)
        assertEquals("https://emby.example.com", gateway.lastEmbyScanDraft?.baseUrl)
        assertEquals(EmbyCredential(userId = "user-1", accessToken = "emby-token"), gateway.lastEmbyScanCredential)
        assertNotNull(gateway.lastEmbyTestDeviceId)
        assertEquals(gateway.lastEmbyTestDeviceId, gateway.lastEmbyScanDeviceId)
        assertEquals(gateway.lastEmbyTestDeviceId, credentials.get(EMBY_DEVICE_ID_CREDENTIAL_KEY))
        val stored = assertNotNull(database.importSourceDao().getById(summary.sourceId))
        assertEquals(ImportSourceType.EMBY.name, stored.type)
        assertEquals("Emby", stored.label)
        assertEquals("https://emby.example.com", stored.rootReference)
        assertEquals("demo", stored.username)
        assertEquals(
            serializeEmbyCredential(EmbyCredential(userId = "user-1", accessToken = "emby-token")),
            credentials.get("credential-${summary.sourceId}"),
        )
        val track = database.trackDao().getAll().single()
        assertEquals("track:${summary.sourceId}:emby:song-1", track.id)
        assertEquals(buildEmbySongLocator(summary.sourceId, "song-1"), track.mediaLocator)
    }

    @Test
    fun `testing updated emby source with retained credential validates token against updated draft`() = runTest {
        val database = createImportTestDatabase()
        val storedCredential = EmbyCredential(userId = "user-1", accessToken = "old-token")
        val credentials = ImportTestSecureCredentialStore(
            mutableMapOf("credential-emby-1" to serializeEmbyCredential(storedCredential)),
        )
        database.importSourceDao().upsert(
            ImportSourceEntity(
                id = "emby-1",
                type = ImportSourceType.EMBY.name,
                label = "Emby",
                rootReference = "https://old.example.com",
                server = null,
                shareName = null,
                directoryPath = null,
                username = "demo",
                credentialKey = "credential-emby-1",
                allowInsecureTls = false,
                lastScannedAt = null,
                createdAt = 1L,
            ),
        )
        val gateway = RecordingImportSourceGateway()
        val repository = createRepository(
            database = database,
            gateway = gateway,
            secureCredentialStore = credentials,
        )

        val result = repository.testUpdatedEmbySource(
            sourceId = "emby-1",
            draft = EmbySourceDraft(
                label = " Emby ",
                baseUrl = " https://new.example.com/base/ ",
                username = " demo ",
                password = "",
            ),
            keepExistingCredentialWhenBlankPassword = true,
        )

        assertTrue(result.isSuccess)
        assertEquals(0, gateway.embyTestCount)
        assertEquals(1, gateway.embyCredentialTestCount)
        assertEquals("https://new.example.com/base", gateway.lastEmbyCredentialTestDraft?.baseUrl)
        assertEquals(storedCredential, gateway.lastEmbyCredentialTestCredential)
        assertEquals(credentials.get(EMBY_DEVICE_ID_CREDENTIAL_KEY), gateway.lastEmbyCredentialTestDeviceId)
    }

    @Test
    fun `rescanning navidrome source returns scan summary with failures`() = runTest {
        val database = createImportTestDatabase()
        val gateway = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = listOf(
                    ImportedTrackCandidate(
                        title = "Blue",
                        mediaLocator = "lynmusic-navidrome://nav-1/song-1",
                        relativePath = "Artist A/Album A/Blue.flac",
                    ),
                ),
                discoveredAudioFileCount = 2,
                failures = listOf(
                    ImportScanFailure(
                        relativePath = "Artist A/Album A/Bad.ogg",
                        reason = "当前平台暂不支持导入该音频格式。",
                    ),
                ),
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "secret"))
        database.importSourceDao().upsert(
            ImportSourceEntity(
                id = "nav-1",
                type = ImportSourceType.NAVIDROME.name,
                label = "Navidrome",
                rootReference = "https://nav.example.com",
                server = null,
                shareName = null,
                directoryPath = null,
                username = "demo",
                credentialKey = "credential-nav-1",
                allowInsecureTls = false,
                enabled = true,
                lastScannedAt = null,
                createdAt = 1L,
            ),
        )
        val repository = RoomImportSourceRepository(
            database = database,
            gateway = gateway,
            secureCredentialStore = credentials,
        )

        val summary = repository.rescanSource("nav-1").getOrThrow()

        assertNotNull(summary)
        assertEquals(2, summary.discoveredAudioFileCount)
        assertEquals(1, summary.importedTrackCount)
        assertEquals(listOf("Artist A/Album A/Bad.ogg"), summary.failures.map { it.relativePath })
        assertEquals(1, gateway.navidromeStreamingScanCount)
        assertEquals("secret", gateway.lastNavidromeScanDraft?.password)
    }

    @Test
    fun `rescanning navidrome source stages batches and replaces stale tracks`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            navidromeSourceEntity(
                id = "nav-1",
                rootReference = "https://nav.example.com",
                username = "demo",
                credentialKey = "credential-nav-1",
            ),
        )
        database.trackDao().upsertAll(
            listOf(
                trackEntity(
                    id = navidromeTrackIdFor("nav-1", "song-1"),
                    sourceId = "nav-1",
                    title = "Old Blue",
                    mediaLocator = "lynmusic-navidrome://nav-1/song-1",
                    relativePath = "Old Blue.flac",
                    addedAt = 10L,
                ),
                trackEntity(
                    id = navidromeTrackIdFor("nav-1", "stale"),
                    sourceId = "nav-1",
                    title = "Stale",
                    mediaLocator = "lynmusic-navidrome://nav-1/stale",
                    relativePath = "Stale.flac",
                    addedAt = 11L,
                ),
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "secret"))
        val gateway = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = emptyList(),
                discoveredAudioFileCount = 2,
                totalTrackCount = 2,
            ),
            navidromeStreamingBatches = listOf(
                listOf(
                    ImportedTrackCandidate(
                        title = "Blue",
                        mediaLocator = "lynmusic-navidrome://nav-1/song-1",
                        relativePath = "Artist A/Album A/Blue.flac",
                    ),
                ),
                listOf(
                    ImportedTrackCandidate(
                        title = "Green",
                        mediaLocator = "lynmusic-navidrome://nav-1/song-2",
                        relativePath = "Artist A/Album A/Green.flac",
                    ),
                ),
            ),
        )
        val repository = createRepository(database = database, gateway = gateway, secureCredentialStore = credentials)
        val progressEvents = mutableListOf<ImportScanProgress>()

        val summary = repository.rescanSource(
            "nav-1",
            ImportScanProgressSink { progressEvents += it },
        ).getOrThrow()

        assertEquals(2, summary?.importedTrackCount)
        val tracks = database.trackDao().getBySourceId("nav-1").sortedBy { it.title }
        assertEquals(listOf("Blue", "Green"), tracks.map { it.title })
        assertEquals(10L, tracks.first { it.title == "Blue" }.addedAt)
        assertTrue(tracks.none { it.title == "Stale" })
        assertEquals(0, database.importTrackStageDao().countBySourceId("nav-1"))
        assertEquals(
            listOf(1, 2, 2),
            progressEvents.map { it.importedTrackCount },
        )
        assertEquals(
            listOf(ImportScanPhase.Scanning, ImportScanPhase.Scanning, ImportScanPhase.Persisting),
            progressEvents.map { it.phase },
        )
    }

    @Test
    fun `rescanning navidrome source aggregates albums by normalized album id`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            navidromeSourceEntity(
                id = "nav-1",
                rootReference = "https://nav.example.com",
                username = "demo",
                credentialKey = "credential-nav-1",
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "secret"))
        val gateway = RecordingImportSourceGateway(
            scanReport = ImportScanReport(
                tracks = emptyList(),
                discoveredAudioFileCount = 2,
            ),
            navidromeStreamingBatches = listOf(
                listOf(
                    ImportedTrackCandidate(
                        title = "Alpha",
                        artistName = "Artist",
                        albumTitle = "Best",
                        mediaLocator = "lynmusic-navidrome://nav-1/song-1",
                        relativePath = "Artist/Best/Alpha.flac",
                    ),
                    ImportedTrackCandidate(
                        title = "Beta",
                        artistName = " artist ",
                        albumTitle = " best ",
                        mediaLocator = "lynmusic-navidrome://nav-1/song-2",
                        relativePath = "artist/best/Beta.flac",
                    ),
                ),
            ),
        )
        val repository = createRepository(database = database, gateway = gateway, secureCredentialStore = credentials)

        repository.rescanSource("nav-1").getOrThrow()

        val albums = database.albumDao().observeAll().first()
        assertEquals(1, albums.size)
        assertEquals("album:artist:best", albums.single().id)
        assertEquals(2, albums.single().trackCount)
    }

    @Test
    fun `library summaries ignore whitespace only artist and album titles`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity(
                id = "local-1",
                type = ImportSourceType.LOCAL_FOLDER,
                label = "Local",
                rootReference = "folder://music",
            ),
        )
        database.trackDao().upsertAll(
            listOf(
                trackEntity(
                    id = "track-blank-metadata",
                    sourceId = "local-1",
                    title = "Blank Metadata",
                    artistName = "   ",
                    albumId = "album::blank",
                    albumTitle = "   ",
                ),
            ),
        )
        val repository = createRepository(database = database)

        val result = repository.setSourceEnabled("local-1", enabled = true)

        assertTrue(result.isSuccess)
        assertEquals(0, database.artistDao().observeAll().first().size)
        assertEquals(0, database.albumDao().observeAll().first().size)
    }

    @Test
    fun `rescanning navidrome source keeps old tracks and clears stage when streaming fails`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            navidromeSourceEntity(
                id = "nav-1",
                rootReference = "https://nav.example.com",
                username = "demo",
                credentialKey = "credential-nav-1",
            ),
        )
        database.trackDao().upsertAll(
            listOf(
                trackEntity(
                    id = navidromeTrackIdFor("nav-1", "old"),
                    sourceId = "nav-1",
                    title = "Old",
                    mediaLocator = "lynmusic-navidrome://nav-1/old",
                    relativePath = "Old.flac",
                    addedAt = 10L,
                ),
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "secret"))
        val gateway = RecordingImportSourceGateway(
            navidromeStreamingBatches = listOf(
                listOf(
                    ImportedTrackCandidate(
                        title = "New",
                        mediaLocator = "lynmusic-navidrome://nav-1/new",
                        relativePath = "New.flac",
                    ),
                ),
            ),
            navidromeStreamingError = IllegalStateException("native page failed"),
        )
        val repository = createRepository(database = database, gateway = gateway, secureCredentialStore = credentials)

        assertFailsWith<IllegalStateException> {
            repository.rescanSource("nav-1").getOrThrow()
        }

        assertEquals(listOf("Old"), database.trackDao().getBySourceId("nav-1").map { it.title })
        assertEquals(0, database.importTrackStageDao().countBySourceId("nav-1"))
        assertEquals("native page failed", database.importIndexStateDao().getBySourceId("nav-1")?.lastError)
    }

    @Test
    fun `rescanning navidrome source clears partial stage before address fallback retry`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            navidromeSourceEntity(
                id = "nav-1",
                rootReference = "https://lan.nav.example.com",
                wanRootReference = "https://wan.nav.example.com",
                username = "demo",
                credentialKey = "credential-nav-1",
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "secret"))
        val gateway = RecordingImportSourceGateway(
            navidromeStreamingHandler = { draft, _, _, trackBatchSink ->
                if (draft.baseUrl.contains("lan.nav.example.com")) {
                    trackBatchSink.onBatch(
                        listOf(
                            ImportedTrackCandidate(
                                title = "Partial",
                                mediaLocator = "lynmusic-navidrome://nav-1/partial",
                                relativePath = "Partial.flac",
                            ),
                        ),
                    )
                    throw IllegalStateException("Navidrome native 歌曲分页失败，HTTP 500")
                }
                trackBatchSink.onBatch(
                    listOf(
                        ImportedTrackCandidate(
                            title = "Final",
                            mediaLocator = "lynmusic-navidrome://nav-1/final",
                            relativePath = "Final.flac",
                        ),
                    ),
                )
                ImportStreamingScanReport(
                    discoveredAudioFileCount = 1,
                    importedTrackCount = 1,
                )
            },
        )
        val repository = createRepository(database = database, gateway = gateway, secureCredentialStore = credentials)

        val summary = repository.rescanSource("nav-1").getOrThrow()

        assertEquals(1, summary?.importedTrackCount)
        assertEquals(2, gateway.navidromeStreamingScanCount)
        assertEquals(listOf("Final"), database.trackDao().getBySourceId("nav-1").map { it.title })
        assertEquals(0, database.importTrackStageDao().countBySourceId("nav-1"))
    }

    @Test
    fun `concurrent navidrome rescans for same source are serialized`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            navidromeSourceEntity(
                id = "nav-1",
                rootReference = "https://nav.example.com",
                username = "demo",
                credentialKey = "credential-nav-1",
            ),
        )
        val credentials = ImportTestSecureCredentialStore(mutableMapOf("credential-nav-1" to "secret"))
        val firstBatchWritten = CompletableDeferred<Unit>()
        val allowFirstFailure = CompletableDeferred<Unit>()
        val secondScanStarted = CompletableDeferred<Unit>()
        val allowSecondReturn = CompletableDeferred<Unit>()
        var scanCallCount = 0
        var activeScanCount = 0
        var maxActiveScanCount = 0
        val gateway = RecordingImportSourceGateway(
            navidromeStreamingHandler = { _, _, _, trackBatchSink ->
                scanCallCount += 1
                activeScanCount += 1
                maxActiveScanCount = maxOf(maxActiveScanCount, activeScanCount)
                try {
                    when (scanCallCount) {
                        1 -> {
                            trackBatchSink.onBatch(
                                listOf(
                                    ImportedTrackCandidate(
                                        title = "Partial",
                                        mediaLocator = "lynmusic-navidrome://nav-1/partial",
                                        relativePath = "Partial.flac",
                                    ),
                                ),
                            )
                            firstBatchWritten.complete(Unit)
                            allowFirstFailure.await()
                            throw IllegalStateException("first scan failed")
                        }

                        2 -> {
                            secondScanStarted.complete(Unit)
                            trackBatchSink.onBatch(
                                listOf(
                                    ImportedTrackCandidate(
                                        title = "Final",
                                        mediaLocator = "lynmusic-navidrome://nav-1/final",
                                        relativePath = "Final.flac",
                                    ),
                                ),
                            )
                            allowSecondReturn.await()
                            ImportStreamingScanReport(
                                discoveredAudioFileCount = 1,
                                importedTrackCount = 1,
                            )
                        }

                        else -> error("Unexpected Navidrome scan call: $scanCallCount")
                    }
                } finally {
                    activeScanCount -= 1
                }
            },
        )
        val repository = createRepository(database = database, gateway = gateway, secureCredentialStore = credentials)

        val firstRescan = async {
            runCatching { repository.rescanSource("nav-1").getOrThrow() }
        }
        firstBatchWritten.await()
        val secondRescan = async {
            repository.rescanSource("nav-1").getOrThrow()
        }
        yield()

        assertEquals(false, secondScanStarted.isCompleted)
        assertEquals(1, gateway.navidromeStreamingScanCount)

        allowFirstFailure.complete(Unit)
        assertTrue(firstRescan.await().isFailure)
        secondScanStarted.await()
        allowSecondReturn.complete(Unit)
        val secondSummary = secondRescan.await()

        assertEquals(1, secondSummary?.importedTrackCount)
        assertEquals(2, gateway.navidromeStreamingScanCount)
        assertEquals(1, maxActiveScanCount)
        assertEquals(listOf("Final"), database.trackDao().getBySourceId("nav-1").map { it.title })
        assertEquals(0, database.importTrackStageDao().countBySourceId("nav-1"))
    }

    @Test
    fun `set source enabled toggles source without deleting tracks`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity(
                id = "local-1",
                type = ImportSourceType.LOCAL_FOLDER,
                label = "下载目录",
                rootReference = "folder://downloads",
            ),
        )
        database.trackDao().upsertAll(
            listOf(
                trackEntity(
                    id = "track-1",
                    sourceId = "local-1",
                    title = "Song",
                ),
            ),
        )
        val repository = createRepository(database = database)

        val result = repository.setSourceEnabled("local-1", enabled = false)

        assertTrue(result.isSuccess)
        assertEquals(false, database.importSourceDao().getById("local-1")?.enabled)
        assertEquals(1, database.trackDao().count())
    }
}

private fun createRepository(
    database: LynMusicDatabase,
    gateway: RecordingImportSourceGateway = RecordingImportSourceGateway(),
    secureCredentialStore: SecureCredentialStore = ImportTestSecureCredentialStore(),
): RoomImportSourceRepository {
    return RoomImportSourceRepository(
        database = database,
        gateway = gateway,
        secureCredentialStore = secureCredentialStore,
    )
}

private fun createImportTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-import-sources", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private fun importSourceEntity(
    id: String,
    type: ImportSourceType,
    label: String,
    rootReference: String,
): ImportSourceEntity {
    return ImportSourceEntity(
        id = id,
        type = type.name,
        label = label,
        rootReference = rootReference,
        server = null,
        shareName = null,
        directoryPath = null,
        username = null,
        credentialKey = null,
        allowInsecureTls = false,
        lastScannedAt = null,
        createdAt = 1L,
    )
}

private fun navidromeSourceEntity(
    id: String,
    rootReference: String,
    wanRootReference: String? = null,
    username: String,
    credentialKey: String,
    indexMode: String = ImportSourceIndexMode.LOCAL_INDEX.name,
): ImportSourceEntity {
    return ImportSourceEntity(
        id = id,
        type = ImportSourceType.NAVIDROME.name,
        label = "Navidrome",
        rootReference = rootReference,
        server = null,
        shareName = null,
        directoryPath = null,
        username = username,
        credentialKey = credentialKey,
        allowInsecureTls = false,
        enabled = true,
        lastScannedAt = null,
        createdAt = 1L,
        wanRootReference = wanRootReference,
        indexMode = indexMode,
    )
}

private class RecordingImportSourceGateway(
    var nextLocalFolderSelection: LocalFolderSelection? = null,
    private val scanReport: ImportScanReport = ImportScanReport(tracks = emptyList()),
    private val localFolderScanHandler: (suspend (LocalFolderSelection, String) -> ImportScanReport)? = null,
    private val navidromeStreamingBatches: List<List<ImportedTrackCandidate>>? = null,
    private val navidromeStreamingError: Throwable? = null,
    private val navidromeProbe: NavidromeLibraryProbe = NavidromeLibraryProbe(totalTrackCount = null),
    private val navidromeStreamingHandler: (suspend (
        NavidromeSourceDraft,
        String,
        ImportScanProgressSink,
        ImportTrackBatchSink,
    ) -> ImportStreamingScanReport)? = null,
    private val embyScanReportFactory: ((String) -> ImportScanReport)? = null,
) : ImportSourceGateway {
    var localFolderScanCount: Int = 0
    var sambaTestCount: Int = 0
    var sambaScanCount: Int = 0
    val sambaScanDrafts = mutableListOf<SambaSourceDraft>()
    var webDavTestCount: Int = 0
    var webDavScanCount: Int = 0
    var navidromeTestCount: Int = 0
    var navidromeScanCount: Int = 0
    var navidromeProgressAwareScanCount: Int = 0
    var navidromeStreamingScanCount: Int = 0
    var navidromeProbeCount: Int = 0
    var lastNavidromeScanDraft: NavidromeSourceDraft? = null
    var lastNavidromeProbeDraft: NavidromeSourceDraft? = null
    var subsonicTestCount: Int = 0
    var subsonicScanCount: Int = 0
    var lastSubsonicScanDraft: SubsonicSourceDraft? = null
    var embyTestCount: Int = 0
    var embyCredentialTestCount: Int = 0
    var embyScanCount: Int = 0
    var lastEmbyTestDraft: EmbySourceDraft? = null
    var lastEmbyTestDeviceId: String? = null
    var lastEmbyCredentialTestDraft: EmbySourceDraft? = null
    var lastEmbyCredentialTestCredential: EmbyCredential? = null
    var lastEmbyCredentialTestDeviceId: String? = null
    var lastEmbyScanDraft: EmbySourceDraft? = null
    var lastEmbyScanCredential: EmbyCredential? = null
    var lastEmbyScanDeviceId: String? = null

    override suspend fun pickLocalFolder(): LocalFolderSelection? = nextLocalFolderSelection

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        localFolderScanCount += 1
        return localFolderScanHandler?.invoke(selection, sourceId) ?: scanReport
    }

    override suspend fun testSamba(draft: SambaSourceDraft) {
        sambaTestCount += 1
    }

    override suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport {
        sambaScanCount += 1
        sambaScanDrafts += draft
        return scanReport
    }

    override suspend fun testWebDav(draft: WebDavSourceDraft) {
        webDavTestCount += 1
    }

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        webDavScanCount += 1
        return scanReport
    }

    override suspend fun testNavidrome(draft: NavidromeSourceDraft) {
        navidromeTestCount += 1
    }

    override suspend fun probeNavidrome(draft: NavidromeSourceDraft): NavidromeLibraryProbe {
        navidromeProbeCount += 1
        lastNavidromeProbeDraft = draft
        return navidromeProbe
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        navidromeScanCount += 1
        lastNavidromeScanDraft = draft
        return scanReport
    }

    override suspend fun scanNavidrome(
        draft: NavidromeSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        navidromeProgressAwareScanCount += 1
        progressSink.onProgress(
            ImportScanProgress(
                sourceId = sourceId,
                phase = ImportScanPhase.Scanning,
                importedTrackCount = scanReport.tracks.size,
                totalTrackCount = scanReport.totalTrackCount,
            ),
        )
        return scanNavidrome(draft, sourceId)
    }

    override suspend fun scanNavidromeStreaming(
        draft: NavidromeSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
        trackBatchSink: ImportTrackBatchSink,
    ): ImportStreamingScanReport {
        navidromeStreamingScanCount += 1
        lastNavidromeScanDraft = draft
        navidromeStreamingHandler?.let { handler ->
            return handler(draft, sourceId, progressSink, trackBatchSink)
        }
        var importedTrackCount = 0
        val batches = navidromeStreamingBatches ?: listOf(scanReport.tracks)
        batches.forEach { batch ->
            trackBatchSink.onBatch(batch)
            importedTrackCount += batch.size
            progressSink.onProgress(
                ImportScanProgress(
                    sourceId = sourceId,
                    phase = ImportScanPhase.Scanning,
                    importedTrackCount = importedTrackCount,
                    totalTrackCount = scanReport.totalTrackCount,
                ),
            )
        }
        navidromeStreamingError?.let { throw it }
        return ImportStreamingScanReport(
            discoveredAudioFileCount = scanReport.discoveredAudioFileCount,
            importedTrackCount = importedTrackCount,
            warnings = scanReport.warnings,
            failures = scanReport.failures,
            totalTrackCount = scanReport.totalTrackCount,
        )
    }

    override suspend fun testSubsonic(draft: SubsonicSourceDraft) {
        subsonicTestCount += 1
    }

    override suspend fun scanSubsonic(draft: SubsonicSourceDraft, sourceId: String): ImportScanReport {
        subsonicScanCount += 1
        lastSubsonicScanDraft = draft
        return scanReport
    }

    override suspend fun testEmby(draft: EmbySourceDraft, deviceId: String): EmbyCredential {
        embyTestCount += 1
        lastEmbyTestDraft = draft
        lastEmbyTestDeviceId = deviceId
        return EmbyCredential(userId = "user-1", accessToken = "emby-token")
    }

    override suspend fun testEmbyCredential(
        draft: EmbySourceDraft,
        credential: EmbyCredential,
        deviceId: String,
    ) {
        embyCredentialTestCount += 1
        lastEmbyCredentialTestDraft = draft
        lastEmbyCredentialTestCredential = credential
        lastEmbyCredentialTestDeviceId = deviceId
    }

    override suspend fun scanEmby(
        draft: EmbySourceDraft,
        credential: EmbyCredential,
        sourceId: String,
        deviceId: String,
    ): ImportScanReport {
        embyScanCount += 1
        lastEmbyScanDraft = draft
        lastEmbyScanCredential = credential
        lastEmbyScanDeviceId = deviceId
        return embyScanReportFactory?.invoke(sourceId) ?: scanReport
    }
}

private class ImportTestSecureCredentialStore(
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

private fun trackEntity(
    id: String,
    sourceId: String,
    title: String,
    artistName: String? = null,
    albumId: String? = null,
    albumTitle: String? = null,
    mediaLocator: String = "file:///tmp/$title.mp3",
    relativePath: String = "$title.mp3",
    addedAt: Long = 0L,
): top.iwesley.lyn.music.data.db.TrackEntity {
    return top.iwesley.lyn.music.data.db.TrackEntity(
        id = id,
        sourceId = sourceId,
        title = title,
        artistId = null,
        artistName = artistName,
        albumId = albumId,
        albumTitle = albumTitle,
        durationMs = 0L,
        trackNumber = null,
        discNumber = null,
        mediaLocator = mediaLocator,
        relativePath = relativePath,
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
        addedAt = addedAt,
    )
}
