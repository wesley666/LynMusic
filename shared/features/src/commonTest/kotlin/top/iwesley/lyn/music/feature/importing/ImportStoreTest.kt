package top.iwesley.lyn.music.feature.importing

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.resources.*

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
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import top.iwesley.lyn.music.testing.assertLocalizedEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.EmbySourceDraft
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderPickerMode
import top.iwesley.lyn.music.core.model.NavidromeLibraryProbe
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.core.model.SubsonicSourceDraft
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.RemoteDirectoryEntry
import top.iwesley.lyn.music.data.repository.ImportSourceRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ImportStoreTest {
    @Test fun iosSambaRecommendationFlowsThroughTestToastAndKeepsEnteredDraft() = runTest {
        val repository = FakeImportSourceRepository().apply {
            pendingResult = CompletableDeferred(Result.failure(top.iwesley.lyn.music.core.model.UiTextException(top.iwesley.lyn.music.core.model.uiText(Res.string.ios_samba_files_recommended))))
        }
        val harness = createStore(repository)
        try {
            val store = harness.store
            store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
            advanceUntilIdle()
            store.dispatch(ImportIntent.TestSambaSource)
            advanceUntilIdle()
            val message = kotlin.test.assertNotNull(store.state.value.testMessage)
            kotlin.test.assertEquals("Samba connection failed: Use Files to connect to SMB in this iOS build.", top.iwesley.lyn.music.core.model.resolveUiText(message, top.iwesley.lyn.music.core.model.AppLanguage.English))
            kotlin.test.assertEquals("Samba 連接測試失敗: 目前 iOS 版本建議透過 Files 連線至 SMB。", top.iwesley.lyn.music.core.model.resolveUiText(message, top.iwesley.lyn.music.core.model.AppLanguage.TraditionalChinese))
            kotlin.test.assertEquals("nas.local", repository.lastTestSambaDraft?.server)
            assertNull(store.state.value.message)
        } finally { harness.close() }
    }

    @Test
    fun `name conflict failure is surfaced through existing samba error message`() = runTest {
        val repository = FakeImportSourceRepository(
            sambaResult = Result.failure(IllegalStateException("音乐源名称已存在。")),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        store.dispatch(ImportIntent.ToggleRemoteFolderSelected("Media/Music"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddSambaSource)
        advanceUntilIdle()

        assertLocalizedEquals("Samba 导入失败: 音乐源名称已存在。", store.state.value.testMessage)
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

        assertLocalizedEquals("导入本地文件夹失败: 该本地文件夹已导入。", store.state.value.message)
        harness.close()
    }

    @Test
    fun `local folder import uses requested automatic picker mode`() = runTest {
        assertLocalFolderImportUsesRequestedPickerMode(LocalFolderPickerMode.Automatic)
    }

    @Test
    fun `local folder import uses requested built in picker mode`() = runTest {
        assertLocalFolderImportUsesRequestedPickerMode(LocalFolderPickerMode.BuiltIn)
    }

    @Test
    fun `local folder reauthorization records summary and success message`() = runTest {
        val repository = FakeImportSourceRepository()
        val harness = createStore(repository)

        harness.store.dispatch(ImportIntent.ReauthorizeLocalFolder("local-1"))
        advanceUntilIdle()

        assertLocalizedEquals("local-1", repository.lastReauthorizedLocalFolderSourceId)
        assertLocalizedEquals(
            "本地文件夹已重新授权并扫描。发现 1 个音频文件，成功导入 1 首，0 个失败。",
            harness.store.state.value.message,
        )
        assertLocalizedEquals(testScanSummary("local-1"), harness.store.state.value.latestScanSummariesBySourceId["local-1"])
        harness.close()
    }

    private suspend fun TestScope.assertLocalFolderImportUsesRequestedPickerMode(mode: LocalFolderPickerMode) {
        val repository = FakeImportSourceRepository()
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.ImportLocalFolderWithPickerMode(mode))
        advanceUntilIdle()

        assertLocalizedEquals(mode, repository.lastLocalFolderMode)
        assertLocalizedEquals("本地音乐源已导入。发现 1 个音频文件，成功导入 1 首，0 个失败。", store.state.value.message)
        harness.close()
    }

    @Test
    fun `scan progress updates while import is running and clears after completion`() = runTest {
        val pendingResult = CompletableDeferred<Result<Unit>>()
        val repository = FakeImportSourceRepository().also {
            it.pendingResult = pendingResult
            it.progressToEmit = ImportScanProgress(
                sourceId = "nav-1",
                phase = ImportScanPhase.Scanning,
                importedTrackCount = 3,
            )
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.NavidromeBaseUrlChanged("https://nav.example.com"))
        store.dispatch(ImportIntent.NavidromeUsernameChanged("demo"))
        store.dispatch(ImportIntent.NavidromePasswordChanged("secret"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddNavidromeSource)
        advanceUntilIdle()

        assertLocalizedEquals(
            ImportScanProgress(
                sourceId = "nav-1",
                phase = ImportScanPhase.Scanning,
                importedTrackCount = 3,
            ),
            store.state.value.scanProgress,
        )

        pendingResult.complete(Result.success(Unit))
        advanceUntilIdle()

        assertNull(store.state.value.scanProgress)
        assertLocalizedEquals("Navidrome 音乐源已导入。发现 1 个音频文件，成功导入 1 首，0 个失败。", store.state.value.message)
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
        assertLocalizedEquals("家庭 NAS", editing.label)
        assertLocalizedEquals("nas.local", editing.server)
        assertLocalizedEquals("445", editing.port)
        assertLocalizedEquals("Media/Music", editing.path)
        assertLocalizedEquals("lyn", editing.username)
        assertLocalizedEquals("", editing.password)
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
        assertLocalizedEquals("https://dav.example.com/中文 音乐/", editing.rootUrl)
        assertLocalizedEquals("云端曲库", editing.label)
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

        assertLocalizedEquals("dav-1", repository.lastUpdatedWebDavSourceId)
        assertLocalizedEquals("https://dav.example.com/中文 音乐/", repository.lastUpdatedWebDavDraft?.rootUrl)
        assertTrue(repository.lastUpdatedWebDavKeepExisting)
        harness.close()
    }

    @Test
    fun `testing new samba source calls repository and surfaces toast message`() = runTest {
        val repository = FakeImportSourceRepository()
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.TestSambaSource)
        advanceUntilIdle()

        assertLocalizedEquals(
            SambaSourceDraft(
                label = "",
                server = "nas.local",
                port = null,
                username = "",
                password = "",
            ),
            repository.lastTestSambaDraft,
        )
        assertLocalizedEquals("Samba 连接测试成功。", store.state.value.testMessage)
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

        assertLocalizedEquals("nav-1", repository.lastUpdatedNavidromeSourceId)
        assertLocalizedEquals(
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
        assertLocalizedEquals("来源已更新并重新扫描。发现 1 个音频文件，成功导入 1 首，0 个失败。", store.state.value.message)
        assertLocalizedEquals(testScanSummary("nav-1"), store.state.value.latestScanSummariesBySourceId["nav-1"])
        harness.close()
    }

    @Test
    fun `saving edited remote source preserves hidden wan address`() = runTest {
        val navidromeRepository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "nav-1",
                    type = ImportSourceType.NAVIDROME,
                    label = "Navidrome",
                    rootReference = "https://nav.lan",
                    wanRootReference = "https://nav.wan",
                    username = "demo",
                    credentialKey = "credential-nav-1",
                ),
            ),
        )
        val navidromeHarness = createStore(navidromeRepository)
        advanceUntilIdle()
        navidromeHarness.store.dispatch(ImportIntent.OpenRemoteSourceEditor("nav-1"))
        advanceUntilIdle()
        navidromeHarness.store.dispatch(ImportIntent.RemoteSourceRootUrlChanged("https://nav2.lan"))
        advanceUntilIdle()
        navidromeHarness.store.dispatch(ImportIntent.SaveRemoteSource)
        advanceUntilIdle()

        assertLocalizedEquals("https://nav.wan", navidromeRepository.lastUpdatedNavidromeDraft?.wanBaseUrl)
        navidromeHarness.close()

        val subsonicRepository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "sub-1",
                    type = ImportSourceType.SUBSONIC,
                    label = "Subsonic",
                    rootReference = "https://sub.lan",
                    wanRootReference = "https://sub.wan",
                    username = "demo",
                    credentialKey = "credential-sub-1",
                    subsonicAuthMode = SubsonicAuthMode.PASSWORD,
                ),
            ),
        )
        val subsonicHarness = createStore(subsonicRepository)
        advanceUntilIdle()
        subsonicHarness.store.dispatch(ImportIntent.OpenRemoteSourceEditor("sub-1"))
        advanceUntilIdle()
        subsonicHarness.store.dispatch(ImportIntent.RemoteSourceRootUrlChanged("https://sub2.lan"))
        advanceUntilIdle()
        subsonicHarness.store.dispatch(ImportIntent.SaveRemoteSource)
        advanceUntilIdle()

        assertLocalizedEquals("https://sub.wan", subsonicRepository.lastUpdatedSubsonicDraft?.wanBaseUrl)
        subsonicHarness.close()

        val embyRepository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "emby-1",
                    type = ImportSourceType.EMBY,
                    label = "Emby",
                    rootReference = "https://emby.lan",
                    wanRootReference = "https://emby.wan",
                    username = "demo",
                    credentialKey = "credential-emby-1",
                ),
            ),
        )
        val embyHarness = createStore(embyRepository)
        advanceUntilIdle()
        embyHarness.store.dispatch(ImportIntent.OpenRemoteSourceEditor("emby-1"))
        advanceUntilIdle()
        embyHarness.store.dispatch(ImportIntent.RemoteSourceRootUrlChanged("https://emby2.lan"))
        advanceUntilIdle()
        embyHarness.store.dispatch(ImportIntent.RemoteSourcePasswordChanged("secret"))
        advanceUntilIdle()
        embyHarness.store.dispatch(ImportIntent.SaveRemoteSource)
        advanceUntilIdle()

        val updatedEmbyDraft = assertNotNull(
            embyRepository.lastUpdatedEmbyDraft,
            embyHarness.store.state.value.message?.let { top.iwesley.lyn.music.core.model.resolveUiText(it, top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese) },
        )
        assertLocalizedEquals("https://emby.wan", updatedEmbyDraft.wanBaseUrl)
        embyHarness.close()
    }

    @Test
    fun `saving edited subsonic source after auth mode change requires new credential`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "sub-1",
                    type = ImportSourceType.SUBSONIC,
                    label = "Subsonic",
                    rootReference = "https://sub.example.com",
                    username = "demo",
                    credentialKey = "credential-sub-1",
                    subsonicAuthMode = SubsonicAuthMode.PASSWORD,
                ),
            ),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("sub-1"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.RemoteSourceSubsonicAuthModeChanged(SubsonicAuthMode.API_KEY))
        advanceUntilIdle()
        store.dispatch(ImportIntent.SaveRemoteSource)
        advanceUntilIdle()

        val editing = assertNotNull(store.state.value.editingSource)
        assertLocalizedEquals(SubsonicAuthMode.API_KEY, editing.subsonicAuthMode)
        assertLocalizedEquals("", editing.username)
        assertLocalizedEquals(false, editing.keepExistingCredential)
        assertLocalizedEquals("", editing.password)
        assertLocalizedEquals("请先填写 Subsonic API Key。", store.state.value.message)
        assertNull(repository.lastUpdatedSubsonicSourceId)
        assertNull(repository.lastUpdatedSubsonicDraft)
        harness.close()
    }

    @Test
    fun `adding subsonic api key source does not send hidden username`() = runTest {
        val repository = FakeImportSourceRepository()
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.SubsonicBaseUrlChanged("https://sub.example.com"))
        store.dispatch(ImportIntent.SubsonicUsernameChanged("demo"))
        store.dispatch(ImportIntent.SubsonicCredentialChanged("password"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.SubsonicAuthModeChanged(SubsonicAuthMode.API_KEY))
        advanceUntilIdle()
        store.dispatch(ImportIntent.SubsonicCredentialChanged("api-key"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddSubsonicSource)
        advanceUntilIdle()

        assertLocalizedEquals("", store.state.value.subsonicUsername)
        assertLocalizedEquals(
            SubsonicSourceDraft(
                label = "",
                baseUrl = "https://sub.example.com",
                username = "",
                credential = "api-key",
                authMode = SubsonicAuthMode.API_KEY,
            ),
            repository.lastAddedSubsonicDraft,
        )
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

        assertLocalizedEquals("来源已禁用。", store.state.value.message)
        assertLocalizedEquals(false, store.state.value.sources.first().source.enabled)
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

        assertLocalizedEquals(ImportScanOperation.CreateLocalFolder, store.state.value.activeScanOperation)
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
            store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
            store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
            store.dispatch(ImportIntent.ToggleRemoteFolderSelected("Media/Music"))
        }
        assertRemoteCreateScanOperation(
            expectedOperation = ImportScanOperation.CreateRemote(ImportSourceType.WEBDAV),
            intent = ImportIntent.AddWebDavSource,
        ) { store ->
            store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.WEBDAV))
            store.dispatch(ImportIntent.WebDavRootUrlChanged("https://dav.example.com/music"))
            store.dispatch(ImportIntent.ToggleRemoteFolderSelected(""))
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

        assertLocalizedEquals(ImportScanOperation.RescanSource("dav-1"), store.state.value.activeScanOperation)

        pendingResult.complete(Result.success(Unit))
        advanceUntilIdle()

        assertNull(store.state.value.activeScanOperation)
        harness.close()
    }

    @Test
    fun `successful samba import stores scan summary and appends message`() = runTest {
        val summary = ImportScanSummary(
            sourceId = "smb-1",
            discoveredAudioFileCount = 3,
            importedTrackCount = 2,
            failures = listOf(ImportScanFailure(relativePath = "bad.mp3", reason = "读取失败。")),
        )
        val repository = FakeImportSourceRepository(
            sambaResult = Result.success(summary),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        store.dispatch(ImportIntent.ToggleRemoteFolderSelected("Media/Music"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddSambaSource)
        advanceUntilIdle()

        assertLocalizedEquals("Samba 音乐源已导入。发现 3 个音频文件，成功导入 2 首，1 个失败。", store.state.value.message)
        assertLocalizedEquals(summary, store.state.value.latestScanSummariesBySourceId["smb-1"])
        harness.close()
    }

    @Test
    fun `successful navidrome import stores scan summary and appends message`() = runTest {
        val summary = ImportScanSummary(
            sourceId = "nav-1",
            discoveredAudioFileCount = 3,
            importedTrackCount = 2,
            failures = listOf(
                ImportScanFailure(
                    relativePath = "Artist A/Album A/Bad.ogg",
                    reason = "当前平台暂不支持导入该音频格式。",
                ),
            ),
        )
        val repository = FakeImportSourceRepository(
            navidromeResult = Result.success(summary),
        )
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.NavidromeBaseUrlChanged("https://nav.example.com"))
        store.dispatch(ImportIntent.NavidromeUsernameChanged("demo"))
        store.dispatch(ImportIntent.NavidromePasswordChanged("secret"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddNavidromeSource)
        advanceUntilIdle()

        assertLocalizedEquals("Navidrome 音乐源已导入。发现 3 个音频文件，成功导入 2 首，1 个失败。", store.state.value.message)
        assertLocalizedEquals(summary, store.state.value.latestScanSummariesBySourceId["nav-1"])
        harness.close()
    }

    @Test
    fun `failed navidrome online import clears pending large library choice`() = runTest {
        val repository = FakeImportSourceRepository(
            navidromeResult = Result.failure(IllegalStateException("save failed")),
        ).also {
            it.navidromeProbeResult = Result.success(NavidromeLibraryProbe(totalTrackCount = 150_000))
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.NAVIDROME))
        store.dispatch(ImportIntent.NavidromeBaseUrlChanged("https://nav.example.com"))
        store.dispatch(ImportIntent.NavidromeUsernameChanged("demo"))
        store.dispatch(ImportIntent.NavidromePasswordChanged("secret"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddNavidromeSource)
        advanceUntilIdle()

        assertNotNull(store.state.value.pendingLargeNavidromeImport)

        store.dispatch(ImportIntent.ConfirmLargeNavidromeOnlineMode)
        advanceUntilIdle()

        assertNull(store.state.value.pendingLargeNavidromeImport)
        assertLocalizedEquals("Navidrome 在线模式保存失败: save failed", store.state.value.testMessage)
        harness.close()
    }

    @Test
    fun `large navidrome import without online paging support uses full import`() = runTest {
        val repository = FakeImportSourceRepository().also {
            it.navidromeProbeResult = Result.success(
                NavidromeLibraryProbe(
                    totalTrackCount = 150_000,
                    supportsOnlineLibraryPaging = false,
                ),
            )
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.NAVIDROME))
        store.dispatch(ImportIntent.NavidromeBaseUrlChanged("https://nav.example.com"))
        store.dispatch(ImportIntent.NavidromeUsernameChanged("demo"))
        store.dispatch(ImportIntent.NavidromePasswordChanged("secret"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddNavidromeSource)
        advanceUntilIdle()

        assertNull(store.state.value.pendingLargeNavidromeImport)
        assertLocalizedEquals("Navidrome 音乐源已导入。发现 1 个音频文件，成功导入 1 首，0 个失败。", store.state.value.message)
        assertLocalizedEquals(testScanSummary("nav-1"), store.state.value.latestScanSummariesBySourceId["nav-1"])
        harness.close()
    }

    @Test
    fun `testing navidrome source only updates test message without scan summary`() = runTest {
        val harness = createStore(FakeImportSourceRepository())
        val store = harness.store

        store.dispatch(ImportIntent.NavidromeBaseUrlChanged("https://nav.example.com"))
        store.dispatch(ImportIntent.NavidromeUsernameChanged("demo"))
        store.dispatch(ImportIntent.NavidromePasswordChanged("secret"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.TestNavidromeSource)
        advanceUntilIdle()

        assertLocalizedEquals("Navidrome 连接测试成功。", store.state.value.testMessage)
        assertNull(store.state.value.message)
        assertTrue(store.state.value.latestScanSummariesBySourceId.isEmpty())
        harness.close()
    }

    @Test
    fun `rescanning navidrome source stores summary and appends message`() = runTest {
        val summary = ImportScanSummary(
            sourceId = "nav-1",
            discoveredAudioFileCount = 2,
            importedTrackCount = 1,
            failures = listOf(
                ImportScanFailure(
                    relativePath = "Artist A/Album A/Bad.ogg",
                    reason = "当前平台暂不支持导入该音频格式。",
                ),
            ),
        )
        val repository = FakeImportSourceRepository(
            navidromeResult = Result.success(summary),
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

        store.dispatch(ImportIntent.RescanSource("nav-1"))
        advanceUntilIdle()

        assertLocalizedEquals("音乐源已重新扫描。发现 2 个音频文件，成功导入 1 首，1 个失败。", store.state.value.message)
        assertLocalizedEquals(summary, store.state.value.latestScanSummariesBySourceId["nav-1"])
        harness.close()
    }

    @Test
    fun `failed navidrome online rescan clears pending large library choice`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "nav-1",
                    type = ImportSourceType.NAVIDROME,
                    label = "Navidrome",
                    rootReference = "https://nav.example.com",
                    username = "demo",
                    credentialKey = "credential-nav-1",
                    indexMode = ImportSourceIndexMode.LOCAL_INDEX,
                ),
            ),
            switchNavidromeOnlineResult = Result.failure(IllegalStateException("switch failed")),
        ).also {
            it.existingNavidromeProbeResult = Result.success(NavidromeLibraryProbe(totalTrackCount = 150_000))
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.RescanSource("nav-1"))
        advanceUntilIdle()

        assertNotNull(store.state.value.pendingLargeNavidromeImport)

        store.dispatch(ImportIntent.ConfirmLargeNavidromeOnlineMode)
        advanceUntilIdle()

        assertNull(store.state.value.pendingLargeNavidromeImport)
        assertLocalizedEquals("切换在线模式失败: switch failed", store.state.value.message)
        harness.close()
    }

    @Test
    fun `large navidrome rescan without online paging support uses full rescan`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "nav-1",
                    type = ImportSourceType.NAVIDROME,
                    label = "Navidrome",
                    rootReference = "https://nav.example.com",
                    username = "demo",
                    credentialKey = "credential-nav-1",
                    indexMode = ImportSourceIndexMode.LOCAL_INDEX,
                ),
            ),
        ).also {
            it.existingNavidromeProbeResult = Result.success(
                NavidromeLibraryProbe(
                    totalTrackCount = 150_000,
                    supportsOnlineLibraryPaging = false,
                ),
            )
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.RescanSource("nav-1"))
        advanceUntilIdle()

        assertNull(store.state.value.pendingLargeNavidromeImport)
        assertLocalizedEquals("音乐源已重新扫描。发现 1 个音频文件，成功导入 1 首，0 个失败。", store.state.value.message)
        assertLocalizedEquals(testScanSummary("nav-1"), store.state.value.latestScanSummariesBySourceId["nav-1"])
        harness.close()
    }

    @Test
    fun `deleting source clears current session scan summary`() = runTest {
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

        store.dispatch(ImportIntent.RescanSource("dav-1"))
        advanceUntilIdle()
        assertNotNull(store.state.value.latestScanSummariesBySourceId["dav-1"])

        store.dispatch(ImportIntent.DeleteSource("dav-1"))
        advanceUntilIdle()

        assertNull(store.state.value.latestScanSummariesBySourceId["dav-1"])
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

        assertLocalizedEquals(ImportScanOperation.UpdateRemote("nav-1"), store.state.value.activeScanOperation)

        pendingResult.complete(Result.failure(IllegalStateException("连接失败")))
        advanceUntilIdle()

        assertNull(store.state.value.activeScanOperation)
        assertNotNull(store.state.value.editingSource)
        assertLocalizedEquals("更新来源失败: 连接失败", store.state.value.message)
        harness.close()
    }

    @Test
    fun `testing sources does not mark scan operation while running`() = runTest {
        assertTestIntentHasNoScanOperation(ImportIntent.TestSambaSource) { store ->
            store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
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

    @Test
    fun `browsing samba shares and ticking folders sends them with the new source`() = runTest {
        val repository = FakeImportSourceRepository().apply {
            folderListings[""] = Result.success(listOf(RemoteDirectoryEntry("Media", "Media"), RemoteDirectoryEntry("Backup", "Backup")))
            folderListings["Media"] = Result.success(listOf(RemoteDirectoryEntry("Music", "Media/Music")))
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()
        store.dispatch(ImportIntent.ToggleRemoteFolderExpanded("Media"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.ToggleRemoteFolderSelected("Media/Music"))
        store.dispatch(ImportIntent.ToggleRemoteFolderSelected("Backup"))
        advanceUntilIdle()

        val rows = store.state.value.remoteFolderTree!!.visibleRows(rootName = "nas.local")
        assertEquals(listOf("Media", "Media/Music", "Backup"), rows.filterIsInstance<RemoteFolderRow.Folder>().map { it.path })
        assertEquals(listOf("", "Media"), repository.listedFolders)

        store.dispatch(ImportIntent.AddSambaSource)
        advanceUntilIdle()

        assertEquals("", repository.lastAddedSambaDraft?.path)
        assertEquals(listOf("Backup", "Media/Music"), repository.lastAddedSambaDraft?.selectedDirectories)
        assertNull(store.state.value.creatingSourceType)
        assertNull(store.state.value.remoteFolderTree)
        harness.close()
    }

    @Test
    fun `adding a samba source without a ticked folder is refused in the dialog`() = runTest {
        val repository = FakeImportSourceRepository()
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddSambaSource)
        advanceUntilIdle()

        assertNull(repository.lastAddedSambaDraft)
        assertLocalizedEquals("请至少选择一个要扫描的文件夹。", store.state.value.testMessage)
        harness.close()
    }

    @Test
    fun `ticking a folder locks its sub-folders and changing the server clears the selection`() = runTest {
        val repository = FakeImportSourceRepository().apply {
            folderListings[""] = Result.success(listOf(RemoteDirectoryEntry("Media", "Media")))
            folderListings["Media"] = Result.success(listOf(RemoteDirectoryEntry("Music", "Media/Music")))
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()
        store.dispatch(ImportIntent.ToggleRemoteFolderExpanded("Media"))
        store.dispatch(ImportIntent.ToggleRemoteFolderSelected("Media/Music"))
        store.dispatch(ImportIntent.ToggleRemoteFolderSelected("Media"))
        advanceUntilIdle()

        val tree = store.state.value.remoteFolderTree!!
        assertEquals(setOf("Media"), tree.selected)
        val child = tree.visibleRows("nas.local").filterIsInstance<RemoteFolderRow.Folder>().single { it.path == "Media/Music" }
        assertTrue(child.isChecked)
        assertTrue(child.isLockedByAncestor)

        store.dispatch(ImportIntent.SambaServerChanged("nas2.local"))
        advanceUntilIdle()

        val reset = store.state.value.remoteFolderTree!!
        assertTrue(reset.selected.isEmpty())
        assertFalse(reset.isBrowsed)
        harness.close()
    }

    @Test
    fun `editing a legacy samba source keeps its root and starts with the whole root ticked`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "smb-legacy",
                    type = ImportSourceType.SAMBA,
                    label = "NAS",
                    rootReference = "Media/Music",
                    server = "nas.local",
                    path = "Media/Music",
                ),
            ),
        )
        val harness = createStore(repository)
        val store = harness.store
        advanceUntilIdle()

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("smb-legacy"))
        advanceUntilIdle()

        val tree = store.state.value.remoteFolderTree!!
        assertTrue(tree.rootSelectable)
        assertEquals(setOf(""), tree.selected)

        store.dispatch(ImportIntent.SaveRemoteSource)
        advanceUntilIdle()

        assertEquals("Media/Music", repository.lastUpdatedSambaDraft?.path)
        assertEquals(listOf(""), repository.lastUpdatedSambaDraft?.selectedDirectories)
        harness.close()
    }

    @Test
    fun `listing results for an outdated tree are dropped`() = runTest {
        val pending = CompletableDeferred<Result<List<RemoteDirectoryEntry>>>()
        val repository = object : FakeImportSourceRepository() {
            override suspend fun listWebDavDirectories(
                draft: WebDavSourceDraft,
                relativePath: String,
            ): Result<List<RemoteDirectoryEntry>> = pending.await()
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.WEBDAV))
        store.dispatch(ImportIntent.WebDavRootUrlChanged("https://dav.example.com/"))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()
        store.dispatch(ImportIntent.WebDavRootUrlChanged("https://dav2.example.com/"))
        advanceUntilIdle()
        pending.complete(Result.success(listOf(RemoteDirectoryEntry("Music", "Music"))))
        advanceUntilIdle()

        assertFalse(store.state.value.remoteFolderTree!!.isBrowsed)
        harness.close()
    }

    @Test
    fun `listing of a closed dialog does not land in the next dialog`() = runTest {
        val pending = CompletableDeferred<Result<List<RemoteDirectoryEntry>>>()
        var calls = 0
        val repository = object : FakeImportSourceRepository() {
            override suspend fun listWebDavDirectories(
                draft: WebDavSourceDraft,
                relativePath: String,
            ): Result<List<RemoteDirectoryEntry>> {
                calls += 1
                return if (calls == 1) pending.await() else CompletableDeferred<Result<List<RemoteDirectoryEntry>>>().await()
            }
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.WEBDAV))
        store.dispatch(ImportIntent.WebDavRootUrlChanged("https://a.example.com/"))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()
        store.dispatch(ImportIntent.DismissRemoteSourceCreator)
        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.WEBDAV))
        store.dispatch(ImportIntent.WebDavRootUrlChanged("https://b.example.com/"))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()
        // Both trees now sit at the same generation; only the dialog id tells them apart.
        pending.complete(Result.success(listOf(RemoteDirectoryEntry("FromA", "FromA"))))
        advanceUntilIdle()

        val tree = store.state.value.remoteFolderTree!!
        assertTrue(tree.nodes[""]?.isLoading == true)
        assertNull(tree.nodes[""]?.children)
        harness.close()
    }

    @Test
    fun `hidden shares can be typed in even when the share list loaded`() = runTest {
        val repository = FakeImportSourceRepository().apply {
            folderListings[""] = Result.success(listOf(RemoteDirectoryEntry("Media", "Media")))
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()
        assertTrue(store.state.value.remoteFolderTree!!.canAddManualShare)

        store.dispatch(ImportIntent.AddManualSambaShare("Music$"))
        store.dispatch(ImportIntent.AddManualSambaShare("Media"))
        store.dispatch(ImportIntent.AddManualSambaShare("media"))
        store.dispatch(ImportIntent.AddManualSambaShare("MUSIC$"))
        advanceUntilIdle()

        val paths = store.state.value.remoteFolderTree!!.visibleRows("nas.local")
            .filterIsInstance<RemoteFolderRow.Folder>()
            .map { it.path }
        assertEquals(listOf("Media", "Music$"), paths)
        harness.close()
    }

    @Test
    fun `several shares can be typed in after listing fails and the error stays visible`() = runTest {
        val repository = FakeImportSourceRepository().apply {
            folderListings[""] = Result.failure(IllegalStateException("IPC$ denied"))
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddManualSambaShare("Media"))
        advanceUntilIdle()

        val tree = store.state.value.remoteFolderTree!!
        assertTrue(tree.canAddManualShare)
        store.dispatch(ImportIntent.AddManualSambaShare("Backup"))
        advanceUntilIdle()

        val rows = store.state.value.remoteFolderTree!!.visibleRows("nas.local")
        assertTrue((rows.first() as RemoteFolderRow.Message).isError)
        assertEquals(listOf("Media", "Backup"), rows.filterIsInstance<RemoteFolderRow.Folder>().map { it.path })
        harness.close()
    }

    @Test
    fun `typed shares survive reloading the share list but not a server change`() = runTest {
        val repository = FakeImportSourceRepository().apply {
            folderListings[""] = Result.success(listOf(RemoteDirectoryEntry("Media", "Media")))
        }
        val harness = createStore(repository)
        val store = harness.store

        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
        store.dispatch(ImportIntent.SambaServerChanged("nas.local"))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()
        store.dispatch(ImportIntent.AddManualSambaShare("Music$"))
        store.dispatch(ImportIntent.ToggleRemoteFolderSelected("Music$"))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()

        val reloaded = store.state.value.remoteFolderTree!!
        assertEquals(listOf("Media", "Music$"), reloaded.visibleRows("nas.local").filterIsInstance<RemoteFolderRow.Folder>().map { it.path })
        assertTrue(reloaded.isChecked("Music$"))

        store.dispatch(ImportIntent.SambaServerChanged("nas2.local"))
        advanceUntilIdle()
        assertTrue(store.state.value.remoteFolderTree!!.manualShares.isEmpty())
        harness.close()
    }

    @Test
    fun `saving an edited source with no ticked folder is refused in the dialog`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "smb-legacy",
                    type = ImportSourceType.SAMBA,
                    label = "NAS",
                    rootReference = "Media/Music",
                    server = "nas.local",
                    path = "Media/Music",
                ),
            ),
        )
        val harness = createStore(repository)
        val store = harness.store
        advanceUntilIdle()

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("smb-legacy"))
        store.dispatch(ImportIntent.ToggleRemoteFolderSelected(""))
        advanceUntilIdle()
        store.dispatch(ImportIntent.SaveRemoteSource)
        advanceUntilIdle()

        assertNull(repository.lastUpdatedSambaDraft)
        assertLocalizedEquals("请至少选择一个要扫描的文件夹。", store.state.value.testMessage)
        assertNull(store.state.value.message)
        harness.close()
    }

    @Test
    fun `browsing from the edit dialog reports missing server inside the dialog`() = runTest {
        val repository = FakeImportSourceRepository(
            sources = listOf(
                source(
                    sourceId = "smb-1",
                    type = ImportSourceType.SAMBA,
                    label = "NAS",
                    rootReference = "",
                    server = "nas.local",
                ),
            ),
        )
        val harness = createStore(repository)
        val store = harness.store
        advanceUntilIdle()

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("smb-1"))
        store.dispatch(ImportIntent.RemoteSourceServerChanged(""))
        store.dispatch(ImportIntent.LoadRemoteFolder())
        advanceUntilIdle()

        assertTrue(repository.listedFolders.isEmpty())
        assertTrue(store.state.value.testMessage != null)
        assertNull(store.state.value.message)
        harness.close()
    }

    @Test
    fun `deleting the edited source drops its folder tree but not the add dialog tree`() = runTest {
        val sources = MutableStateFlow(
            listOf(
                source(sourceId = "dav-1", type = ImportSourceType.WEBDAV, label = "Cloud", rootReference = "https://dav.example.com/"),
            ),
        )
        val repository = object : FakeImportSourceRepository() {
            override fun observeSources(): Flow<List<SourceWithStatus>> = sources
        }
        val harness = createStore(repository)
        val store = harness.store
        advanceUntilIdle()

        store.dispatch(ImportIntent.OpenRemoteSourceEditor("dav-1"))
        advanceUntilIdle()
        assertNotNull(store.state.value.remoteFolderTree)
        sources.value = emptyList()
        advanceUntilIdle()
        assertNull(store.state.value.editingSource)
        assertNull(store.state.value.remoteFolderTree)

        sources.value = listOf(
            source(sourceId = "dav-2", type = ImportSourceType.WEBDAV, label = "Other", rootReference = "https://dav2.example.com/"),
        )
        store.dispatch(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.WEBDAV))
        advanceUntilIdle()
        sources.value = emptyList()
        advanceUntilIdle()
        assertNotNull(store.state.value.remoteFolderTree)
        harness.close()
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

        assertLocalizedEquals(expectedOperation, store.state.value.activeScanOperation)

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
    wanRootReference: String? = null,
    server: String? = null,
    port: Int? = null,
    path: String? = null,
    username: String? = null,
    credentialKey: String? = null,
    subsonicAuthMode: SubsonicAuthMode = SubsonicAuthMode.PASSWORD,
    indexMode: ImportSourceIndexMode = ImportSourceIndexMode.LOCAL_INDEX,
    enabled: Boolean = true,
): SourceWithStatus {
    return SourceWithStatus(
        source = ImportSource(
            id = sourceId,
            type = type,
            label = label,
            rootReference = rootReference,
            wanRootReference = wanRootReference,
            server = server,
            port = port,
            path = path,
            username = username,
            credentialKey = credentialKey,
            subsonicAuthMode = subsonicAuthMode,
            indexMode = indexMode,
            enabled = enabled,
            createdAt = 1L,
        ),
    )
}

private open class FakeImportSourceRepository(
    localFolderResult: Result<ImportScanSummary?> = Result.success(testScanSummary("local-1")),
    sambaResult: Result<ImportScanSummary> = Result.success(testScanSummary("smb-1")),
    private val webDavResult: Result<ImportScanSummary> = Result.success(testScanSummary("dav-1")),
    private val navidromeResult: Result<ImportScanSummary> = Result.success(testScanSummary("nav-1")),
    private val switchNavidromeOnlineResult: Result<ImportScanSummary>? = null,
    private val subsonicResult: Result<ImportScanSummary> = Result.success(testScanSummary("sub-1")),
    sources: List<SourceWithStatus> = emptyList(),
) : ImportSourceRepository {
    private val mutableSources = MutableStateFlow(sources)
    private val localFolderResult = localFolderResult
    private val sambaResult = sambaResult
    var pendingResult: CompletableDeferred<Result<Unit>>? = null

    var lastTestSambaDraft: SambaSourceDraft? = null
    var lastAddedSambaDraft: SambaSourceDraft? = null
    var lastUpdatedSambaDraft: SambaSourceDraft? = null
    val folderListings = mutableMapOf<String, Result<List<RemoteDirectoryEntry>>>()
    val listedFolders = mutableListOf<String>()
    var lastUpdatedWebDavSourceId: String? = null
    var lastUpdatedWebDavDraft: WebDavSourceDraft? = null
    var lastUpdatedWebDavKeepExisting: Boolean = false
    var lastUpdatedNavidromeSourceId: String? = null
    var lastUpdatedNavidromeDraft: NavidromeSourceDraft? = null
    var lastUpdatedNavidromeKeepExisting: Boolean = false
    var lastAddedSubsonicDraft: SubsonicSourceDraft? = null
    var lastUpdatedSubsonicSourceId: String? = null
    var lastUpdatedSubsonicDraft: SubsonicSourceDraft? = null
    var lastUpdatedSubsonicKeepExisting: Boolean = false
    var lastUpdatedEmbySourceId: String? = null
    var lastUpdatedEmbyDraft: EmbySourceDraft? = null
    var lastUpdatedEmbyKeepExisting: Boolean = false
    var lastLocalFolderMode: LocalFolderPickerMode? = null
    var lastReauthorizedLocalFolderSourceId: String? = null
    var navidromeProbeResult: Result<NavidromeLibraryProbe> =
        Result.success(NavidromeLibraryProbe(totalTrackCount = null))
    var existingNavidromeProbeResult: Result<NavidromeLibraryProbe> =
        Result.success(NavidromeLibraryProbe(totalTrackCount = null))
    var progressToEmit: ImportScanProgress? = null

    override fun observeSources(): Flow<List<SourceWithStatus>> = mutableSources.asStateFlow()

    override suspend fun importLocalFolder(): Result<ImportScanSummary?> {
        lastLocalFolderMode = LocalFolderPickerMode.Automatic
        return importLocalFolderResult()
    }

    override suspend fun importLocalFolder(mode: LocalFolderPickerMode): Result<ImportScanSummary?> {
        lastLocalFolderMode = mode
        return importLocalFolderResult()
    }

    override suspend fun importLocalFolder(
        mode: LocalFolderPickerMode,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary?> {
        progressToEmit?.let(progressSink::onProgress)
        return importLocalFolder(mode)
    }

    private suspend fun importLocalFolderResult(): Result<ImportScanSummary?> {
        return pendingResult?.await()?.map { testScanSummary("local-1") } ?: localFolderResult
    }

    override suspend fun reauthorizeLocalFolder(sourceId: String): Result<ImportScanSummary?> {
        lastReauthorizedLocalFolderSourceId = sourceId
        return Result.success(testScanSummary(sourceId))
    }

    override suspend fun reauthorizeLocalFolder(
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary?> {
        progressToEmit?.let(progressSink::onProgress)
        return reauthorizeLocalFolder(sourceId)
    }

    override suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit> {
        lastTestSambaDraft = draft
        return pendingResult?.await() ?: Result.success(Unit)
    }

    override suspend fun listSambaDirectories(
        draft: SambaSourceDraft,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> {
        listedFolders += relativePath
        return folderListings[relativePath] ?: Result.success(emptyList())
    }

    override suspend fun listUpdatedSambaDirectories(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> = listSambaDirectories(draft, relativePath)

    override suspend fun listWebDavDirectories(
        draft: WebDavSourceDraft,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> {
        listedFolders += relativePath
        return folderListings[relativePath] ?: Result.success(emptyList())
    }

    override suspend fun listUpdatedWebDavDirectories(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
        relativePath: String,
    ): Result<List<RemoteDirectoryEntry>> = listWebDavDirectories(draft, relativePath)

    override suspend fun testUpdatedSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = pendingResult?.await() ?: Result.success(Unit)

    override suspend fun addSambaSource(draft: SambaSourceDraft): Result<ImportScanSummary> {
        lastAddedSambaDraft = draft
        return pendingResult?.await()?.map { testScanSummary("smb-1") } ?: sambaResult
    }

    override suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        lastUpdatedSambaDraft = draft
        return pendingResult?.await()?.map { testScanSummary(sourceId) } ?: Result.success(testScanSummary(sourceId))
    }

    override suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit> {
        return pendingResult?.await() ?: Result.success(Unit)
    }

    override suspend fun testUpdatedWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = pendingResult?.await() ?: Result.success(Unit)

    override suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<ImportScanSummary> {
        return pendingResult?.await()?.map { testScanSummary("dav-1") } ?: webDavResult
    }

    override suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        lastUpdatedWebDavSourceId = sourceId
        lastUpdatedWebDavDraft = draft
        lastUpdatedWebDavKeepExisting = keepExistingCredentialWhenBlankPassword
        return pendingResult?.await()?.map { testScanSummary(sourceId) } ?: Result.success(testScanSummary(sourceId))
    }

    override suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> {
        return pendingResult?.await() ?: Result.success(Unit)
    }

    override suspend fun probeNavidromeSource(draft: NavidromeSourceDraft): Result<NavidromeLibraryProbe> {
        return navidromeProbeResult
    }

    override suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = pendingResult?.await() ?: Result.success(Unit)

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary> {
        return pendingResult?.await()?.map { testScanSummary("nav-1") } ?: navidromeResult
    }

    override suspend fun addNavidromeSourceOnline(
        draft: NavidromeSourceDraft,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> {
        return pendingResult?.await()?.map { testScanSummary("nav-1") } ?: navidromeResult
    }

    override suspend fun probeExistingNavidromeSource(sourceId: String): Result<NavidromeLibraryProbe> {
        return existingNavidromeProbeResult
    }

    override suspend fun switchNavidromeSourceToOnline(
        sourceId: String,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> {
        return pendingResult?.await()?.map { testScanSummary(sourceId) }
            ?: switchNavidromeOnlineResult
            ?: Result.success(testScanSummary(sourceId))
    }

    override suspend fun addNavidromeSource(
        draft: NavidromeSourceDraft,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary> {
        progressToEmit?.let(progressSink::onProgress)
        return addNavidromeSource(draft)
    }

    override suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        lastUpdatedNavidromeSourceId = sourceId
        lastUpdatedNavidromeDraft = draft
        lastUpdatedNavidromeKeepExisting = keepExistingCredentialWhenBlankPassword
        return pendingResult?.await()?.map { testScanSummary(sourceId) } ?: Result.success(testScanSummary(sourceId))
    }

    override suspend fun testSubsonicSource(draft: SubsonicSourceDraft): Result<Unit> {
        return pendingResult?.await() ?: Result.success(Unit)
    }

    override suspend fun testUpdatedSubsonicSource(
        sourceId: String,
        draft: SubsonicSourceDraft,
        keepExistingCredentialWhenBlankCredential: Boolean,
    ): Result<Unit> = pendingResult?.await() ?: Result.success(Unit)

    override suspend fun addSubsonicSource(draft: SubsonicSourceDraft): Result<ImportScanSummary> {
        lastAddedSubsonicDraft = draft
        return pendingResult?.await()?.map { testScanSummary("sub-1") } ?: subsonicResult
    }

    override suspend fun updateSubsonicSource(
        sourceId: String,
        draft: SubsonicSourceDraft,
        keepExistingCredentialWhenBlankCredential: Boolean,
    ): Result<ImportScanSummary> {
        lastUpdatedSubsonicSourceId = sourceId
        lastUpdatedSubsonicDraft = draft
        lastUpdatedSubsonicKeepExisting = keepExistingCredentialWhenBlankCredential
        return pendingResult?.await()?.map { testScanSummary(sourceId) } ?: Result.success(testScanSummary(sourceId))
    }

    override suspend fun updateEmbySource(
        sourceId: String,
        draft: EmbySourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> {
        lastUpdatedEmbySourceId = sourceId
        lastUpdatedEmbyDraft = draft
        lastUpdatedEmbyKeepExisting = keepExistingCredentialWhenBlankPassword
        return pendingResult?.await()?.map { testScanSummary(sourceId) } ?: Result.success(testScanSummary(sourceId))
    }

    override suspend fun rescanSource(sourceId: String): Result<ImportScanSummary?> {
        return pendingResult?.await()?.map { testScanSummary(sourceId) } ?: when (sourceId) {
            "nav-1" -> navidromeResult.map { it }
            "sub-1" -> subsonicResult.map { it }
            "dav-1" -> webDavResult.map { it }
            "smb-1" -> sambaResult.map { it }
            else -> Result.success(testScanSummary(sourceId))
        }
    }

    override suspend fun rescanSource(
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): Result<ImportScanSummary?> {
        progressToEmit?.let(progressSink::onProgress)
        return rescanSource(sourceId)
    }

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

private fun testScanSummary(sourceId: String): ImportScanSummary {
    return ImportScanSummary(
        sourceId = sourceId,
        discoveredAudioFileCount = 1,
        importedTrackCount = 1,
    )
}
