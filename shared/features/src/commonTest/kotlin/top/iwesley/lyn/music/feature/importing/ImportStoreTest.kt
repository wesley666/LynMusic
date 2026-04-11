package top.iwesley.lyn.music.feature.importing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.data.repository.ImportSourceRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ImportStoreTest {

    @Test
    fun `name conflict failure is surfaced through existing samba error message`() = runTest {
        val repository = FakeImportSourceRepository(
            sambaResult = Result.failure(IllegalStateException("音乐源名称已存在。")),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        store.dispatch(ImportIntent.SambaPathChanged("Media/Music"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddSambaSource)
        advanceUntilIdle()

        assertEquals("Samba 导入失败: 音乐源名称已存在。", store.state.value.message)
        harness.close()
    }

    @Test
    fun `local folder path conflict is surfaced through existing import message`() = runTest {
        val repository = FakeImportSourceRepository(
            localFolderResult = Result.failure(IllegalStateException("该本地文件夹已导入。")),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.ImportLocalFolder)
        advanceUntilIdle()

        assertEquals("导入本地文件夹失败: 该本地文件夹已导入。", store.state.value.message)
        harness.close()
    }

    @Test
    fun `opening remote editor prefills fields and keeps password blank`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "smb-1",
                    type = ImportSourceType.SAMBA,
                    label = "家庭 NAS",
                    rootReference = "Media/Music",
                    server = "nas.local",
                    port = 445,
                    path = "Media/Music",
                    username = "lyn",
                    credentialKey = "credential-smb-1",
                ),
            ),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("smb-1"))
        advanceUntilIdle()

        val editing = assertNotNull(store.state.value.editingSource)
        assertEquals("家庭 NAS", editing.label)
        assertEquals("nas.local", editing.server)
        assertEquals("445", editing.port)
        assertEquals("Media/Music", editing.path)
        assertEquals("lyn", editing.username)
        assertEquals("", editing.password)
        assertTrue(editing.hasStoredCredential)
        assertTrue(editing.keepExistingCredential)
        harness.close()
    }

    @Test
    fun `opening webdav editor prefills decoded root url`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "dav-1",
                    type = ImportSourceType.WEBDAV,
                    label = "云端曲库",
                    rootReference = "https://dav.example.com/%E4%B8%AD%E6%96%87%20%E9%9F%B3%E4%B9%90/",
                    username = "lyn",
                    credentialKey = "credential-dav-1",
                ),
            ),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("dav-1"))
        advanceUntilIdle()

        val editing = assertNotNull(store.state.value.editingSource)
        assertEquals("https://dav.example.com/中文 音乐/", editing.rootUrl)
        assertEquals("云端曲库", editing.label)
        harness.close()
    }

    @Test
    fun `saving edited webdav source sends current root url to repository`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "dav-1",
                    type = ImportSourceType.WEBDAV,
                    label = "云端曲库",
                    rootReference = "https://dav.example.com/%E4%B8%AD%E6%96%87%20%E9%9F%B3%E4%B9%90/",
                    username = "lyn",
                    credentialKey = "credential-dav-1",
                ),
            ),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("dav-1"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.SaveRemoteSource)
        advanceUntilIdle()

        assertEquals("dav-1", repository.lastUpdatedWebDavSourceId)
        assertEquals("https://dav.example.com/中文 音乐/", repository.lastUpdatedWebDavDraft?.rootUrl)
        assertTrue(repository.lastUpdatedWebDavKeepExisting)
        harness.close()
    }

    @Test
    fun `testing new samba source calls repository and surfaces toast message`() = runTest {
        val repository = FakeImportSourceRepository()
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        store.dispatch(ImportIntent.SambaPathChanged("Media/Music"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.TestSambaSource)
        advanceUntilIdle()

        assertEquals(
            SambaSourceDraft(
                label = "",
                server = "nas.local",
                port = null,
                path = "Media/Music",
                username = "",
                password = "",
            ),
            repository.lastTestSambaDraft,
        )
        assertEquals("Samba 连接测试成功。", store.state.value.testMessage)
        assertNull(store.state.value.message)
        harness.close()
    }

    @Test
    fun `saving edited navidrome source keeps stored credential when password stays blank`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "nav-1",
                    type = ImportSourceType.NAVIDROME,
                    label = "Navidrome",
                    rootReference = "https://nav.example.com",
                    username = "demo",
                    credentialKey = "credential-nav-1",
                ),
            ),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("nav-1"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.RemoteSourceRootUrlChanged("https://nav2.example.com"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.SaveRemoteSource)
        advanceUntilIdle()

        assertEquals("nav-1", repository.lastUpdatedNavidromeSourceId)
        assertEquals(
            NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://nav2.example.com",
                username = "demo",
                password = "",
            ),
            repository.lastUpdatedNavidromeDraft,
        )
        assertTrue(repository.lastUpdatedNavidromeKeepExisting)
        assertNull(store.state.value.editingSource)
        assertEquals("来源已更新并重新扫描。", store.state.value.message)
        harness.close()
    }

    @Test
    fun `toggle source enabled updates state message`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "dav-1",
                    type = ImportSourceType.WEBDAV,
                    label = "云端曲库",
                    rootReference = "https://dav.example.com/music",
                ),
            ),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.ToggleSourceEnabled("dav-1", enabled = false))
        advanceUntilIdle()

        assertEquals("来源已禁用。", store.state.value.message)
        assertEquals(false, store.state.value.sources.first().source.enabled)
        harness.close()
    }

    @Test
    fun `local folder import marks scan operation while running`() = runTest {
        val pendingResult = CompletableDeferred<Result<Unit>>()
        val repository = FakeImportSourceRepository().also { it.pendingResult = pendingResult }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.ImportLocalFolder)
        advanceUntilIdle()

        assertEquals(ImportScanOperation.CreateLocalFolder, store.state.value.activeScanOperation)
        assertTrue(store.state.value.isWorking)

        pendingResult.complete(Result.success(Unit))
        advanceUntilIdle()

        assertNull(store.state.value.activeScanOperation)
        harness.close()
    }

    @Test
    fun `remote source creation marks scan operation while running`() = runTest {
        assertRemoteCreateScanOperation(
            expectedOperation = ImportScanOperation.CreateRemote(ImportSourceType.SAMBA),
            intent = ImportIntent.AddSambaSource,
        ) { store ->
            store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
            store.dispatch(ImportIntent.SambaPathChanged("Media/Music"))
        }
        assertRemoteCreateScanOperation(
            expectedOperation = ImportScanOperation.CreateRemote(ImportSourceType.WEBDAV),
            intent = ImportIntent.AddWebDavSource,
        ) { store ->
            store.dispatch(ImportIntent.WebDavRootUrlChanged("https://dav.example.com/music"))
        }
        assertRemoteCreateScanOperation(
            expectedOperation = ImportScanOperation.CreateRemote(ImportSourceType.NAVIDROME),
            intent = ImportIntent.AddNavidromeSource,
        ) { store ->
            store.dispatch(ImportIntent.NavidromeBaseUrlChanged("https://nav.example.com"))
            store.dispatch(ImportIntent.NavidromeUsernameChanged("demo"))
            store.dispatch(ImportIntent.NavidromePasswordChanged("secret"))
        }
    }

    @Test
    fun `rescan source marks only that source while running`() = runTest {
        val pendingResult = CompletableDeferred<Result<Unit>>()
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "dav-1",
                    type = ImportSourceType.WEBDAV,
                    label = "云端曲库",
                    rootReference = "https://dav.example.com/music",
                ),
            ),
        ).also { it.pendingResult = pendingResult }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.RescanSource("dav-1"))
        advanceUntilIdle()

        assertEquals(ImportScanOperation.RescanSource("dav-1"), store.state.value.activeScanOperation)

        pendingResult.complete(Result.success(Unit))
        advanceUntilIdle()

        assertNull(store.state.value.activeScanOperation)
        harness.close()
    }

    @Test
    fun `saving remote source marks update scan operation and clears it on failure`() = runTest {
        val pendingResult = CompletableDeferred<Result<Unit>>()
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "nav-1",
                    type = ImportSourceType.NAVIDROME,
                    label = "Navidrome",
                    rootReference = "https://nav.example.com",
                    username = "demo",
                    credentialKey = "credential-nav-1",
                ),
            ),
        ).also { it.pendingResult = pendingResult }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("nav-1"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.SaveRemoteSource)
        advanceUntilIdle()

        assertEquals(ImportScanOperation.UpdateRemote("nav-1"), store.state.value.activeScanOperation)

        pendingResult.complete(Result.failure(IllegalStateException("连接失败")))
        advanceUntilIdle()

        assertNull(store.state.value.activeScanOperation)
        assertNotNull(store.state.value.editingSource)
        assertEquals("更新来源失败: 连接失败", store.state.value.message)
        harness.close()
    }

    @Test
    fun `testing sources does not mark scan operation while running`() = runTest {
        assertTestIntentHasNoScanOperation(ImportIntent.TestSambaSource) { store ->
            store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
            store.dispatch(ImportIntent.SambaPathChanged("Media/Music"))
        }
        assertTestIntentHasNoScanOperation(ImportIntent.TestWebDavSource) { store ->
            store.dispatch(ImportIntent.WebDavRootUrlChanged("https://dav.example.com/music"))
        }
        assertTestIntentHasNoScanOperation(ImportIntent.TestNavidromeSource) { store ->
            store.dispatch(ImportIntent.NavidromeBaseUrlChanged("https://nav.example.com"))
            store.dispatch(ImportIntent.NavidromeUsernameChanged("demo"))
            store.dispatch(ImportIntent.NavidromePasswordChanged("secret"))
        }
    }

    private fun TestScope.createStore(repository: FakeImportSourceRepository): TestStoreHarness {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        return TestStoreHarness(
            store = ImportStore(
                repository = repository,
                capabilities = testPlatformCapabilities(),
                scope = scope,
            ),
            scope = scope,
        )
    }

    private suspend fun TestScope.assertRemoteCreateScanOperation(
        expectedOperation: ImportScanOperation.CreateRemote,
        intent: ImportIntent,
        configure: (ImportStore) -> Unit,
    ) {
        val pendingResult = CompletableDeferred<Result<Unit>>()
        val repository = FakeImportSourceRepository().also { it.pendingResult = pendingResult }
        val harness = createStore(repository)
        val store = harness.store

        configure(store)
        advanceUntilIdle()
        store.dispatch(intent)
        advanceUntilIdle()

        assertEquals(expectedOperation, store.state.value.activeScanOperation)

        pendingResult.complete(Result.success(Unit))
        advanceUntilIdle()

        assertNull(store.state.value.activeScanOperation)
        harness.close()
    }

    private suspend fun TestScope.assertTestIntentHasNoScanOperation(
        intent: ImportIntent,
        configure: (ImportStore) -> Unit,
    ) {
        val pendingResult = CompletableDeferred<Result<Unit>>()
        val repository = FakeImportSourceRepository().also { it.pendingResult = pendingResult }
        val harness = createStore(repository)
        val store = harness.store

        configure(store)
        advanceUntilIdle()
        store.dispatch(intent)
        advanceUntilIdle()

        assertTrue(store.state.value.isWorking)
        assertNull(store.state.value.activeScanOperation)

        pendingResult.complete(Result.success(Unit))
        advanceUntilIdle()

        assertNull(store.state.value.activeScanOperation)
        harness.close()
    }
}

private data class TestStoreHarness(
    val store: ImportStore,
    val scope: CoroutineScope,
) {
    fun close() {
        scope.cancel()
    }
}

private fun testPlatformCapabilities(): PlatformCapabilities {
    return PlatformCapabilities(
        supportsLocalFolderImport = true,
        supportsSambaImport = true,
        supportsWebDavImport = true,
        supportsNavidromeImport = true,
        supportsSystemMediaControls = true,
    )
}

private fun source(
    sourceId: String,
    type: ImportSourceType,
    label: String,
    rootReference: String,
    server: String? = null,
    port: Int? = null,
    path: String? = null,
    username: String? = null,
    credentialKey: String? = null,
    enabled: Boolean = true,
): SourceWithStatus {
    return SourceWithStatus(
        source = ImportSource(
            id = sourceId,
            type = type,
            label = label,
            rootReference = rootReference,
            server = server,
            port = port,
            path = path,
            username = username,
            credentialKey = credentialKey,
            enabled = enabled,
            createdAt = 1L,
        ),
    )
}

private class FakeImportSourceRepository(
    localFolderResult: Result<Unit> = Result.success(Unit),
    sambaResult: Result<Unit> = Result.success(Unit),
    private val webDavResult: Result<Unit> = Result.success(Unit),
    private val navidromeResult: Result<Unit> = Result.success(Unit),
    sources: List<SourceWithStatus> = emptyList(),
) : ImportSourceRepository {
    private val mutableSources = MutableStateFlow(sources)
    private val localFolderResult = localFolderResult
    private val sambaResult = sambaResult
    var pendingResult: CompletableDeferred<Result<Unit>>? = null

    var lastTestSambaDraft: SambaSourceDraft? = null
    var lastUpdatedWebDavSourceId: String? = null
    var lastUpdatedWebDavDraft: WebDavSourceDraft? = null
    var lastUpdatedWebDavKeepExisting: Boolean = false
    var lastUpdatedNavidromeSourceId: String? = null
    var lastUpdatedNavidromeDraft: NavidromeSourceDraft? = null
    var lastUpdatedNavidromeKeepExisting: Boolean = false

    override fun observeSources(): Flow<List<SourceWithStatus>> = mutableSources.asStateFlow()

    override suspend fun importLocalFolder(): Result<Unit> = pendingResult?.await() ?: localFolderResult

    override suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit> {
        lastTestSambaDraft = draft
        return pendingResult?.await() ?: Result.success(Unit)
    }

    override suspend fun testUpdatedSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = pendingResult?.await() ?: Result.success(Unit)

    override suspend fun addSambaSource(draft: SambaSourceDraft): Result<Unit> = pendingResult?.await() ?: sambaResult

    override suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = pendingResult?.await() ?: Result.success(Unit)

    override suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit> {
        return pendingResult?.await() ?: Result.success(Unit)
    }

    override suspend fun testUpdatedWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = pendingResult?.await() ?: Result.success(Unit)

    override suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<Unit> = pendingResult?.await() ?: webDavResult

    override suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> {
        lastUpdatedWebDavSourceId = sourceId
        lastUpdatedWebDavDraft = draft
        lastUpdatedWebDavKeepExisting = keepExistingCredentialWhenBlankPassword
        return pendingResult?.await() ?: Result.success(Unit)
    }

    override suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> {
        return pendingResult?.await() ?: Result.success(Unit)
    }

    override suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = pendingResult?.await() ?: Result.success(Unit)

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> {
        return pendingResult?.await() ?: navidromeResult
    }

    override suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> {
        lastUpdatedNavidromeSourceId = sourceId
        lastUpdatedNavidromeDraft = draft
        lastUpdatedNavidromeKeepExisting = keepExistingCredentialWhenBlankPassword
        return pendingResult?.await() ?: Result.success(Unit)
    }

    override suspend fun rescanSource(sourceId: String): Result<Unit> = pendingResult?.await() ?: Result.success(Unit)

    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): Result<Unit> {
        mutableSources.value = mutableSources.value.map { source ->
            if (source.source.id == sourceId) {
                source.copy(source = source.source.copy(enabled = enabled))
            } else {
                source
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteSource(sourceId: String): Result<Unit> = Result.success(Unit)
}
