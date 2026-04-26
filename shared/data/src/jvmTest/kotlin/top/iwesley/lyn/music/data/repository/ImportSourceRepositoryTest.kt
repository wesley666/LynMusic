package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

class ImportSourceRepositoryTest {

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

        assertEquals("音乐源名称已存在。", result.exceptionOrNull()?.message)
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

        assertEquals("该本地文件夹已导入。", result.exceptionOrNull()?.message)
        assertEquals(1, database.importSourceDao().getAll().size)
        assertEquals(0, gateway.localFolderScanCount)
    }

    @Test
    fun `import local folder rejects duplicate name even when path differs`() = runTest {
        val database = createImportTestDatabase()
        database.importSourceDao().upsert(
            importSourceEntity(
                id = "dav-1",
                type = ImportSourceType.WEBDAV,
                label = " 下载目录 ",
                rootReference = "https://dav.example.com/music",
            ),
        )
        val repository = createRepository(
            database = database,
            gateway = RecordingImportSourceGateway(
                nextLocalFolderSelection = LocalFolderSelection(
                    label = "下载目录",
                    persistentReference = "folder://new-downloads",
                ),
            ),
        )

        val result = repository.importLocalFolder()

        assertEquals("音乐源名称已存在。", result.exceptionOrNull()?.message)
        assertEquals(1, database.importSourceDao().getAll().size)
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

        assertEquals("音乐源名称已存在。", result.exceptionOrNull()?.message)
        assertEquals(1, database.importSourceDao().getAll().size)
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
        assertEquals(1, gateway.navidromeScanCount)
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
        assertEquals(1, gateway.navidromeScanCount)
        assertEquals("old-password", gateway.lastNavidromeScanDraft?.password)
        val updated = assertNotNull(database.importSourceDao().getById("nav-1"))
        assertEquals("https://nav2.example.com", updated.rootReference)
        assertEquals("demo2", updated.username)
        assertEquals("old-password", credentials.get("credential-nav-1"))
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
        assertEquals(1, gateway.navidromeScanCount)
        assertEquals("secret", gateway.lastNavidromeScanDraft?.password)
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
): RoomImportSourceRepository {
    return RoomImportSourceRepository(
        database = database,
        gateway = gateway,
        secureCredentialStore = ImportTestSecureCredentialStore(),
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

private class RecordingImportSourceGateway(
    var nextLocalFolderSelection: LocalFolderSelection? = null,
    private val scanReport: ImportScanReport = ImportScanReport(tracks = emptyList()),
) : ImportSourceGateway {
    var localFolderScanCount: Int = 0
    var sambaTestCount: Int = 0
    var sambaScanCount: Int = 0
    var webDavTestCount: Int = 0
    var webDavScanCount: Int = 0
    var navidromeTestCount: Int = 0
    var navidromeScanCount: Int = 0
    var lastNavidromeScanDraft: NavidromeSourceDraft? = null

    override suspend fun pickLocalFolder(): LocalFolderSelection? = nextLocalFolderSelection

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        localFolderScanCount += 1
        return scanReport
    }

    override suspend fun testSamba(draft: SambaSourceDraft) {
        sambaTestCount += 1
    }

    override suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport {
        sambaScanCount += 1
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

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        navidromeScanCount += 1
        lastNavidromeScanDraft = draft
        return scanReport
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
): top.iwesley.lyn.music.data.db.TrackEntity {
    return top.iwesley.lyn.music.data.db.TrackEntity(
        id = id,
        sourceId = sourceId,
        title = title,
        artistId = null,
        artistName = null,
        albumId = null,
        albumTitle = null,
        durationMs = 0L,
        trackNumber = null,
        discNumber = null,
        mediaLocator = "file:///tmp/$title.mp3",
        relativePath = "$title.mp3",
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
    )
}
