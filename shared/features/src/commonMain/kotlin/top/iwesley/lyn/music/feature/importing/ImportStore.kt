package top.iwesley.lyn.music.feature.importing

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.domain.RemoteSourceAddressTestException
import org.jetbrains.compose.resources.StringResource
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.plus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.EmbySourceDraft
import top.iwesley.lyn.music.core.model.FnMusicConnectionMode
import top.iwesley.lyn.music.core.model.FnMusicSourceDraft
import top.iwesley.lyn.music.domain.FN_MUSIC_NAME
import top.iwesley.lyn.music.domain.fnMusicConnectionModeOf
import top.iwesley.lyn.music.domain.fnMusicIdOf
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderPickerMode
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.RemoteDirectoryEntry
import top.iwesley.lyn.music.core.model.effectiveSelectedDirectories
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.core.model.SubsonicSourceDraft
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.displayWebDavRootUrl
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import kotlin.time.Clock

data class RemoteSourceEditorState(
    val sourceId: String,
    val type: ImportSourceType,
    val label: String = "",
    val server: String = "",
    val port: String = "",
    /** Samba source root (`share/sub` for sources created before folder browsing, blank otherwise); not editable. */
    val path: String = "",
    val rootUrl: String = "",
    val wanRootUrl: String = "",
    val username: String = "",
    val password: String = "",
    val allowInsecureTls: Boolean = false,
    val subsonicAuthMode: SubsonicAuthMode = SubsonicAuthMode.PASSWORD,
    val hasStoredCredential: Boolean = false,
    val keepExistingCredential: Boolean = true,
    val fnMusicConnectionMode: FnMusicConnectionMode = FnMusicConnectionMode.ADDRESS,
    val fnMusicId: String = "",
    /** A new FN Connect access code; blank keeps the saved one. */
    val fnMusicAccessCode: String = "",
)

sealed interface PendingLargeNavidromeAction {
    data class Create(val draft: NavidromeSourceDraft) : PendingLargeNavidromeAction
    data class Rescan(val sourceId: String, val sourceLabel: String) : PendingLargeNavidromeAction
}

data class PendingLargeNavidromeImport(
    val action: PendingLargeNavidromeAction,
    val remoteTrackCount: Int,
)

sealed interface ImportScanOperation {
    data object CreateLocalFolder : ImportScanOperation
    data class CreateRemote(val type: ImportSourceType) : ImportScanOperation
    data class RescanSource(val sourceId: String) : ImportScanOperation
    data class ReauthorizeLocalFolder(val sourceId: String) : ImportScanOperation
    data class UpdateRemote(val sourceId: String) : ImportScanOperation
}

data class ImportState(
    val capabilities: PlatformCapabilities,
    val sources: List<SourceWithStatus> = emptyList(),
    val sambaLabel: String = "",
    val sambaServer: String = "",
    val sambaPort: String = "",
    val sambaUsername: String = "",
    val sambaPassword: String = "",
    val webDavLabel: String = "",
    val webDavRootUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val webDavAllowInsecureTls: Boolean = false,
    val navidromeLabel: String = "",
    val navidromeBaseUrl: String = "",
    val navidromeWanBaseUrl: String = "",
    val navidromeUsername: String = "",
    val navidromePassword: String = "",
    val subsonicLabel: String = "",
    val subsonicBaseUrl: String = "",
    val subsonicWanBaseUrl: String = "",
    val subsonicUsername: String = "",
    val subsonicCredential: String = "",
    val subsonicAuthMode: SubsonicAuthMode = SubsonicAuthMode.PASSWORD,
    val embyLabel: String = "",
    val embyBaseUrl: String = "",
    val embyWanBaseUrl: String = "",
    val embyUsername: String = "",
    val embyPassword: String = "",
    val fnMusicLabel: String = "",
    val fnMusicConnectionMode: FnMusicConnectionMode = FnMusicConnectionMode.ADDRESS,
    val fnMusicBaseUrl: String = "",
    val fnMusicWanBaseUrl: String = "",
    val fnMusicId: String = "",
    val fnMusicUsername: String = "",
    val fnMusicPassword: String = "",
    val fnMusicAccessCode: String = "",
    val creatingSourceType: ImportSourceType? = null,
    val editingSource: RemoteSourceEditorState? = null,
    /** Folder picker of the open Samba/WebDAV add or edit dialog. */
    val remoteFolderTree: RemoteFolderTreeState? = null,
    /** Last [RemoteFolderTreeState.instanceId] handed out, so every opened dialog gets a tree id never used before. */
    val lastRemoteFolderTreeId: Long = 0L,
    val isWorking: Boolean = false,
    val activeScanOperation: ImportScanOperation? = null,
    val scanProgress: ImportScanProgress? = null,
    val latestScanSummariesBySourceId: Map<String, ImportScanSummary> = emptyMap(),
    val pendingLargeNavidromeImport: PendingLargeNavidromeImport? = null,
    val message: UiText? = null,
    val testMessage: UiText? = null,
)

sealed interface ImportIntent {
    data object ImportLocalFolder : ImportIntent
    data class ImportLocalFolderWithPickerMode(val mode: LocalFolderPickerMode) : ImportIntent
    data class ImportSelectedLocalFolder(val selection: LocalFolderSelection) : ImportIntent
    data object TestSambaSource : ImportIntent
    data object AddSambaSource : ImportIntent
    data object TestWebDavSource : ImportIntent
    data object AddWebDavSource : ImportIntent
    data object TestNavidromeSource : ImportIntent
    data object AddNavidromeSource : ImportIntent
    data object ConfirmLargeNavidromeOnlineMode : ImportIntent
    data object ConfirmLargeNavidromeFullImport : ImportIntent
    data object DismissLargeNavidromeChoice : ImportIntent
    data object TestSubsonicSource : ImportIntent
    data object AddSubsonicSource : ImportIntent
    data object TestEmbySource : ImportIntent
    data object AddEmbySource : ImportIntent
    data object TestFnMusicSource : ImportIntent
    data object AddFnMusicSource : ImportIntent
    data class OpenRemoteSourceCreator(val type: ImportSourceType) : ImportIntent
    data object DismissRemoteSourceCreator : ImportIntent
    data class OpenRemoteSourceEditor(val sourceId: String) : ImportIntent
    data object DismissRemoteSourceEditor : ImportIntent
    data object TestRemoteSource : ImportIntent
    data object SaveRemoteSource : ImportIntent
    data class RescanSource(val sourceId: String) : ImportIntent
    data class ReauthorizeLocalFolder(val sourceId: String) : ImportIntent
    data class ToggleSourceEnabled(val sourceId: String, val enabled: Boolean) : ImportIntent
    data class DeleteSource(val sourceId: String) : ImportIntent
    data class SambaLabelChanged(val value: String) : ImportIntent
    data class SambaServerChanged(val value: String) : ImportIntent
    data class SambaPortChanged(val value: String) : ImportIntent
    data class SambaUsernameChanged(val value: String) : ImportIntent
    data class SambaPasswordChanged(val value: String) : ImportIntent
    data class WebDavLabelChanged(val value: String) : ImportIntent
    data class WebDavRootUrlChanged(val value: String) : ImportIntent
    data class WebDavUsernameChanged(val value: String) : ImportIntent
    data class WebDavPasswordChanged(val value: String) : ImportIntent
    data class WebDavAllowInsecureTlsChanged(val value: Boolean) : ImportIntent
    data class NavidromeLabelChanged(val value: String) : ImportIntent
    data class NavidromeBaseUrlChanged(val value: String) : ImportIntent
    data class NavidromeWanBaseUrlChanged(val value: String) : ImportIntent
    data class NavidromeUsernameChanged(val value: String) : ImportIntent
    data class NavidromePasswordChanged(val value: String) : ImportIntent
    data class SubsonicLabelChanged(val value: String) : ImportIntent
    data class SubsonicBaseUrlChanged(val value: String) : ImportIntent
    data class SubsonicWanBaseUrlChanged(val value: String) : ImportIntent
    data class SubsonicUsernameChanged(val value: String) : ImportIntent
    data class SubsonicCredentialChanged(val value: String) : ImportIntent
    data class SubsonicAuthModeChanged(val value: SubsonicAuthMode) : ImportIntent
    data class EmbyLabelChanged(val value: String) : ImportIntent
    data class EmbyBaseUrlChanged(val value: String) : ImportIntent
    data class EmbyWanBaseUrlChanged(val value: String) : ImportIntent
    data class EmbyUsernameChanged(val value: String) : ImportIntent
    data class EmbyPasswordChanged(val value: String) : ImportIntent
    data class FnMusicLabelChanged(val value: String) : ImportIntent
    data class FnMusicConnectionModeChanged(val value: FnMusicConnectionMode) : ImportIntent
    data class FnMusicBaseUrlChanged(val value: String) : ImportIntent
    data class FnMusicWanBaseUrlChanged(val value: String) : ImportIntent
    data class FnMusicIdChanged(val value: String) : ImportIntent
    data class FnMusicUsernameChanged(val value: String) : ImportIntent
    data class FnMusicPasswordChanged(val value: String) : ImportIntent
    data class FnMusicAccessCodeChanged(val value: String) : ImportIntent
    data class RemoteSourceFnMusicConnectionModeChanged(val value: FnMusicConnectionMode) : ImportIntent
    data class RemoteSourceFnMusicIdChanged(val value: String) : ImportIntent
    data class RemoteSourceFnMusicAccessCodeChanged(val value: String) : ImportIntent
    data class RemoteSourceLabelChanged(val value: String) : ImportIntent
    data class RemoteSourceServerChanged(val value: String) : ImportIntent
    data class RemoteSourcePortChanged(val value: String) : ImportIntent
    data class RemoteSourceRootUrlChanged(val value: String) : ImportIntent
    data class RemoteSourceWanRootUrlChanged(val value: String) : ImportIntent
    data class RemoteSourceUsernameChanged(val value: String) : ImportIntent
    data class RemoteSourcePasswordChanged(val value: String) : ImportIntent
    data class RemoteSourceAllowInsecureTlsChanged(val value: Boolean) : ImportIntent
    data class RemoteSourceSubsonicAuthModeChanged(val value: SubsonicAuthMode) : ImportIntent
    data class LoadRemoteFolder(val path: String = RemoteFolderTreeState.ROOT) : ImportIntent
    data class ToggleRemoteFolderExpanded(val path: String) : ImportIntent
    data class ToggleRemoteFolderSelected(val path: String) : ImportIntent
    data class AddManualSambaShare(val name: String) : ImportIntent
    data object ClearMessage : ImportIntent
    data object ClearTestMessage : ImportIntent
}

sealed interface ImportEffect

class ImportStore(
    private val repository: ImportSourceRepository,
    capabilities: PlatformCapabilities,
    scope: CoroutineScope,
) : BaseStore<ImportState, ImportIntent, ImportEffect>(
    initialState = ImportState(capabilities = capabilities),
    scope = scope,
) {
    init {
        scope.launch {
            repository.observeSources().collect { sources ->
                updateState { state ->
                    val sourceIds = sources.mapTo(mutableSetOf()) { it.source.id }
                    val editingSource = state.editingSource?.takeIf { editing ->
                        sources.any { it.source.id == editing.sourceId && it.source.type == editing.type }
                    }
                    // The edited source is gone: its dialog closes, so its folder tree goes with it.
                    val editorClosed = state.editingSource != null && editingSource == null
                    state.copy(
                        sources = sources,
                        editingSource = editingSource,
                        remoteFolderTree = if (editorClosed) null else state.remoteFolderTree,
                        latestScanSummariesBySourceId = state.latestScanSummariesBySourceId.filterKeys { it in sourceIds },
                    )
                }
            }
        }
    }

    override suspend fun handleIntent(intent: ImportIntent) {
        when (intent) {
            ImportIntent.ImportLocalFolder -> {
                importLocalFolder(LocalFolderPickerMode.Automatic)
            }

            is ImportIntent.ImportLocalFolderWithPickerMode -> {
                importLocalFolder(intent.mode)
            }

            is ImportIntent.ImportSelectedLocalFolder -> runScanningImport(ImportScanOperation.CreateLocalFolder) { progressSink ->
                repository.importSelectedLocalFolder(intent.selection, progressSink)
                    .onSuccess { summary ->
                        recordScanSummary(summary)
                        setMessage(scanSuccessMessage(uiText(Res.string.source_local_folder_imported), summary))
                    }
                    .onFailure { setMessage(uiText(Res.string.source_local_folder_import_failed, it.uiErrorDetail())) }
            }

            ImportIntent.TestSambaSource -> {
                val draft = sambaDraftOrNull(state.value, requireSelection = false) ?: return
                runImport {
                    repository.testSambaSource(draft)
                        .onSuccess { setTestMessage(uiText(Res.string.source_samba_connection_succeeded)) }
                        .onFailure { setTestMessage(uiText(Res.string.source_samba_connection_failed, it.uiErrorDetail())) }
                }
            }

            ImportIntent.AddSambaSource -> {
                val currentState = state.value
                val draft = sambaDraftOrNull(currentState, requireSelection = true) ?: return
                runScanningImport(ImportScanOperation.CreateRemote(ImportSourceType.SAMBA)) { progressSink ->
                    repository.addSambaSource(draft, progressSink)
                        .onSuccess { summary ->
                            updateState {
                                it.copy(
                                    creatingSourceType = null,
                                    remoteFolderTree = null,
                                    sambaLabel = "",
                                    sambaServer = "",
                                    sambaPort = "",
                                    sambaUsername = "",
                                    sambaPassword = "",
                                    testMessage = null,
                                )
                            }
                            recordScanSummary(summary)
                            setMessage(scanSuccessMessage(uiText(Res.string.source_samba_imported), summary))
                        }
                        .onFailure { setCreateOrPageMessage(ImportSourceType.SAMBA, uiText(Res.string.source_samba_import_failed, it.uiErrorDetail())) }
                }
            }

            ImportIntent.TestWebDavSource -> {
                val draft = webDavDraftOrNull(state.value, allowBlankPassword = true, requireSelection = false) ?: return
                runImport {
                    repository.testWebDavSource(draft)
                        .onSuccess { setTestMessage(uiText(Res.string.source_webdav_connection_succeeded)) }
                        .onFailure { setTestMessage(uiText(Res.string.source_webdav_connection_failed, it.uiErrorDetail())) }
                }
            }

            ImportIntent.AddWebDavSource -> {
                val draft = webDavDraftOrNull(state.value, allowBlankPassword = true, requireSelection = true) ?: return
                runScanningImport(ImportScanOperation.CreateRemote(ImportSourceType.WEBDAV)) { progressSink ->
                    repository.addWebDavSource(draft, progressSink)
                        .onSuccess { summary ->
                            updateState {
                                it.copy(
                                    creatingSourceType = null,
                                    remoteFolderTree = null,
                                    webDavLabel = "",
                                    webDavRootUrl = "",
                                    webDavUsername = "",
                                    webDavPassword = "",
                                    webDavAllowInsecureTls = false,
                                    testMessage = null,
                                )
                            }
                            recordScanSummary(summary)
                            setMessage(scanSuccessMessage(uiText(Res.string.source_webdav_imported), summary))
                        }
                        .onFailure { setCreateOrPageMessage(ImportSourceType.WEBDAV, uiText(Res.string.source_webdav_import_failed, it.uiErrorDetail())) }
                }
            }

            ImportIntent.TestNavidromeSource -> {
                val draft = navidromeDraftOrNull(
                    label = state.value.navidromeLabel,
                    baseUrl = state.value.navidromeBaseUrl,
                    wanBaseUrl = state.value.navidromeWanBaseUrl,
                    username = state.value.navidromeUsername,
                    password = state.value.navidromePassword,
                    allowBlankPassword = false,
                ) ?: return
                runImport {
                    repository.testNavidromeSource(draft)
                        .onSuccess { setTestMessage(uiText(Res.string.source_navidrome_connection_succeeded)) }
                        .onFailure { setTestMessage(remoteConnectionTestFailureText(it, "Navidrome", Res.string.source_navidrome_connection_failed)) }
                }
            }

            ImportIntent.AddNavidromeSource -> {
                val draft = navidromeDraftOrNull(
                    label = state.value.navidromeLabel,
                    baseUrl = state.value.navidromeBaseUrl,
                    wanBaseUrl = state.value.navidromeWanBaseUrl,
                    username = state.value.navidromeUsername,
                    password = state.value.navidromePassword,
                    allowBlankPassword = false,
                ) ?: return
                runImport(ImportScanOperation.CreateRemote(ImportSourceType.NAVIDROME)) {
                    repository.probeNavidromeSource(draft)
                        .onSuccess { probe ->
                            val totalTrackCount = probe.totalTrackCount
                            if (
                                probe.supportsOnlineLibraryPaging &&
                                totalTrackCount != null &&
                                totalTrackCount > LARGE_NAVIDROME_LIBRARY_TRACK_THRESHOLD
                            ) {
                                updateState {
                                    it.copy(
                                        pendingLargeNavidromeImport = PendingLargeNavidromeImport(
                                            action = PendingLargeNavidromeAction.Create(draft),
                                            remoteTrackCount = totalTrackCount,
                                        ),
                                        testMessage = null,
                                    )
                                }
                            } else {
                                importNavidromeFull(draft)
                            }
                        }
                        .onFailure { setCreateOrPageMessage(ImportSourceType.NAVIDROME, uiText(Res.string.source_navidrome_import_failed, it.uiErrorDetail())) }
                }
            }

            ImportIntent.ConfirmLargeNavidromeOnlineMode -> {
                val pending = state.value.pendingLargeNavidromeImport ?: return
                when (val action = pending.action) {
                    is PendingLargeNavidromeAction.Create -> importNavidromeOnline(pending)
                    is PendingLargeNavidromeAction.Rescan -> switchNavidromeRescanToOnline(
                        sourceId = action.sourceId,
                        sourceLabel = action.sourceLabel,
                        remoteTrackCount = pending.remoteTrackCount,
                    )
                }
            }

            ImportIntent.ConfirmLargeNavidromeFullImport -> {
                val pending = state.value.pendingLargeNavidromeImport ?: return
                updateState { it.copy(pendingLargeNavidromeImport = null) }
                when (val action = pending.action) {
                    is PendingLargeNavidromeAction.Create -> importNavidromeFull(action.draft)
                    is PendingLargeNavidromeAction.Rescan -> rescanSourceFull(action.sourceId)
                }
            }

            ImportIntent.DismissLargeNavidromeChoice -> updateState { it.copy(pendingLargeNavidromeImport = null) }

            ImportIntent.TestSubsonicSource -> {
                val draft = subsonicDraftOrNull(
                    label = state.value.subsonicLabel,
                    baseUrl = state.value.subsonicBaseUrl,
                    wanBaseUrl = state.value.subsonicWanBaseUrl,
                    username = state.value.subsonicUsername,
                    credential = state.value.subsonicCredential,
                    authMode = state.value.subsonicAuthMode,
                    allowBlankCredential = false,
                ) ?: return
                runImport {
                    repository.testSubsonicSource(draft)
                        .onSuccess { setTestMessage(uiText(Res.string.source_subsonic_connection_succeeded)) }
                        .onFailure { setTestMessage(remoteConnectionTestFailureText(it, "Subsonic", Res.string.source_subsonic_connection_failed)) }
                }
            }

            ImportIntent.AddSubsonicSource -> {
                val draft = subsonicDraftOrNull(
                    label = state.value.subsonicLabel,
                    baseUrl = state.value.subsonicBaseUrl,
                    wanBaseUrl = state.value.subsonicWanBaseUrl,
                    username = state.value.subsonicUsername,
                    credential = state.value.subsonicCredential,
                    authMode = state.value.subsonicAuthMode,
                    allowBlankCredential = false,
                ) ?: return
                runScanningImport(ImportScanOperation.CreateRemote(ImportSourceType.SUBSONIC)) { progressSink ->
                    repository.addSubsonicSource(draft, progressSink)
                        .onSuccess { summary ->
                            updateState {
                                it.copy(
                                    creatingSourceType = null,
                                    subsonicLabel = "",
                                    subsonicBaseUrl = "",
                                    subsonicWanBaseUrl = "",
                                    subsonicUsername = "",
                                    subsonicCredential = "",
                                    subsonicAuthMode = SubsonicAuthMode.PASSWORD,
                                    testMessage = null,
                                )
                            }
                            recordScanSummary(summary)
                            setMessage(scanSuccessMessage(uiText(Res.string.source_subsonic_imported), summary))
                        }
                        .onFailure { setCreateOrPageMessage(ImportSourceType.SUBSONIC, uiText(Res.string.source_subsonic_import_failed, it.uiErrorDetail())) }
                }
            }

            ImportIntent.TestEmbySource -> {
                val draft = embyDraftOrNull(
                    label = state.value.embyLabel,
                    baseUrl = state.value.embyBaseUrl,
                    wanBaseUrl = state.value.embyWanBaseUrl,
                    username = state.value.embyUsername,
                    password = state.value.embyPassword,
                    allowBlankPassword = false,
                ) ?: return
                runImport {
                    repository.testEmbySource(draft)
                        .onSuccess { setTestMessage(uiText(Res.string.source_emby_connection_succeeded)) }
                        .onFailure { setTestMessage(remoteConnectionTestFailureText(it, "Emby", Res.string.source_emby_connection_failed)) }
                }
            }

            ImportIntent.AddEmbySource -> {
                val draft = embyDraftOrNull(
                    label = state.value.embyLabel,
                    baseUrl = state.value.embyBaseUrl,
                    wanBaseUrl = state.value.embyWanBaseUrl,
                    username = state.value.embyUsername,
                    password = state.value.embyPassword,
                    allowBlankPassword = false,
                ) ?: return
                runScanningImport(ImportScanOperation.CreateRemote(ImportSourceType.EMBY)) { progressSink ->
                    repository.addEmbySource(draft, progressSink)
                        .onSuccess { summary ->
                            updateState {
                                it.copy(
                                    creatingSourceType = null,
                                    embyLabel = "",
                                    embyBaseUrl = "",
                                    embyWanBaseUrl = "",
                                    embyUsername = "",
                                    embyPassword = "",
                                    testMessage = null,
                                )
                            }
                            recordScanSummary(summary)
                            setMessage(scanSuccessMessage(uiText(Res.string.source_emby_imported), summary))
                        }
                        .onFailure { setCreateOrPageMessage(ImportSourceType.EMBY, uiText(Res.string.source_emby_import_failed, it.uiErrorDetail())) }
                }
            }

            ImportIntent.TestFnMusicSource -> {
                val draft = createFnMusicDraftOrNull(state.value) ?: return
                runImport {
                    repository.testFnMusicSource(draft)
                        .onSuccess { setTestMessage(uiText(Res.string.source_fn_music_connection_succeeded)) }
                        .onFailure { setTestMessage(remoteConnectionTestFailureText(it, FN_MUSIC_NAME, Res.string.source_fn_music_connection_failed)) }
                }
            }

            ImportIntent.AddFnMusicSource -> {
                val draft = createFnMusicDraftOrNull(state.value) ?: return
                runScanningImport(ImportScanOperation.CreateRemote(ImportSourceType.FN_MUSIC)) { progressSink ->
                    repository.addFnMusicSource(draft, progressSink)
                        .onSuccess { summary ->
                            updateState {
                                it.clearCreateDraft(ImportSourceType.FN_MUSIC).copy(
                                    creatingSourceType = null,
                                    testMessage = null,
                                )
                            }
                            recordScanSummary(summary)
                            setMessage(scanSuccessMessage(uiText(Res.string.source_fn_music_imported), summary))
                        }
                        .onFailure {
                            setCreateOrPageMessage(ImportSourceType.FN_MUSIC, uiText(Res.string.source_fn_music_import_failed, it.uiErrorDetail()))
                        }
                }
            }

            is ImportIntent.OpenRemoteSourceCreator -> {
                if (intent.type == ImportSourceType.LOCAL_FOLDER) return
                updateState { state ->
                    state.clearCreateDraft(intent.type).copy(
                        creatingSourceType = intent.type,
                        editingSource = null,
                        pendingLargeNavidromeImport = null,
                        testMessage = null,
                    ).withNewFolderTree(newRemoteFolderTree(intent.type, sambaRootPath = ""))
                }
            }

            ImportIntent.DismissRemoteSourceCreator -> updateState { state ->
                val type = state.creatingSourceType
                if (type == null) {
                    state.copy(pendingLargeNavidromeImport = null, testMessage = null)
                } else {
                    state.clearCreateDraft(type).copy(
                        creatingSourceType = null,
                        remoteFolderTree = null,
                        pendingLargeNavidromeImport = null,
                        testMessage = null,
                    )
                }
            }

            is ImportIntent.OpenRemoteSourceEditor -> {
                val source = state.value.sources.firstOrNull { it.source.id == intent.sourceId }?.source ?: return
                if (source.type == ImportSourceType.LOCAL_FOLDER) return
                updateState { state ->
                    state.copy(
                        editingSource = RemoteSourceEditorState(
                            sourceId = source.id,
                            type = source.type,
                            label = source.label,
                            server = source.server.orEmpty(),
                            port = source.port?.toString().orEmpty(),
                            path = source.path.orEmpty(),
                            rootUrl = when {
                                source.type == ImportSourceType.WEBDAV -> displayWebDavRootUrl(source.rootReference)
                                source.type == ImportSourceType.FN_MUSIC && fnMusicIdOf(source.rootReference) != null -> ""
                                else -> source.rootReference
                            },
                            wanRootUrl = source.wanRootReference.orEmpty(),
                            username = source.username.orEmpty(),
                            password = "",
                            allowInsecureTls = source.allowInsecureTls,
                            subsonicAuthMode = source.subsonicAuthMode,
                            hasStoredCredential = source.credentialKey != null,
                            keepExistingCredential = true,
                            fnMusicConnectionMode = fnMusicConnectionModeOf(source.rootReference),
                            fnMusicId = fnMusicIdOf(source.rootReference).orEmpty(),
                        ),
                    ).withNewFolderTree(
                        newRemoteFolderTree(source.type, sambaRootPath = source.path.orEmpty())
                            ?.copy(selected = effectiveSelectedDirectories(source.selectedDirectories).toSet()),
                    )
                }
            }

            ImportIntent.DismissRemoteSourceEditor -> updateState { it.copy(editingSource = null, remoteFolderTree = null) }

            ImportIntent.TestRemoteSource -> {
                val editor = state.value.editingSource ?: return
                when (editor.type) {
                    ImportSourceType.SAMBA -> {
                        val draft = editingSambaDraftOrNull(editor, requireSelection = false) ?: return
                        runImport {
                            repository.testUpdatedSambaSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess {
                                setTestMessage(uiText(Res.string.source_samba_connection_succeeded))
                            }.onFailure {
                                setTestMessage(uiText(Res.string.source_samba_connection_failed, it.uiErrorDetail()))
                            }
                        }
                    }

                    ImportSourceType.WEBDAV -> {
                        val draft = editingWebDavDraftOrNull(editor, requireSelection = false) ?: return
                        runImport {
                            repository.testUpdatedWebDavSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess {
                                setTestMessage(uiText(Res.string.source_webdav_connection_succeeded))
                            }.onFailure {
                                setTestMessage(uiText(Res.string.source_webdav_connection_failed, it.uiErrorDetail()))
                            }
                        }
                    }

                    ImportSourceType.NAVIDROME -> {
                        val draft = editingNavidromeDraftOrNull(editor) ?: return
                        runImport {
                            repository.testUpdatedNavidromeSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess {
                                setTestMessage(uiText(Res.string.source_navidrome_connection_succeeded))
                            }.onFailure {
                                setTestMessage(remoteConnectionTestFailureText(it, "Navidrome", Res.string.source_navidrome_connection_failed))
                            }
                        }
                    }

                    ImportSourceType.SUBSONIC -> {
                        val draft = editingSubsonicDraftOrNull(editor) ?: return
                        runImport {
                            repository.testUpdatedSubsonicSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankCredential = editor.keepExistingCredential,
                            ).onSuccess {
                                setTestMessage(uiText(Res.string.source_subsonic_connection_succeeded))
                            }.onFailure {
                                setTestMessage(remoteConnectionTestFailureText(it, "Subsonic", Res.string.source_subsonic_connection_failed))
                            }
                        }
                    }

                    ImportSourceType.EMBY -> {
                        val draft = editingEmbyDraftOrNull(editor) ?: return
                        runImport {
                            repository.testUpdatedEmbySource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess {
                                setTestMessage(uiText(Res.string.source_emby_connection_succeeded))
                            }.onFailure {
                                setTestMessage(remoteConnectionTestFailureText(it, "Emby", Res.string.source_emby_connection_failed))
                            }
                        }
                    }

                    ImportSourceType.FN_MUSIC -> {
                        val draft = editingFnMusicDraftOrNull(editor) ?: return
                        runImport {
                            repository.testUpdatedFnMusicSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess {
                                setTestMessage(uiText(Res.string.source_fn_music_connection_succeeded))
                            }.onFailure {
                                setTestMessage(remoteConnectionTestFailureText(it, FN_MUSIC_NAME, Res.string.source_fn_music_connection_failed))
                            }
                        }
                    }

                    ImportSourceType.LOCAL_FOLDER -> Unit
                }
            }

            ImportIntent.SaveRemoteSource -> {
                val editor = state.value.editingSource ?: return
                when (editor.type) {
                    ImportSourceType.SAMBA -> {
                        val draft = editingSambaDraftOrNull(editor, requireSelection = true) ?: return
                        runScanningImport(ImportScanOperation.UpdateRemote(editor.sourceId)) { progressSink ->
                            repository.updateSambaSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                                progressSink = progressSink,
                            ).onSuccess { summary ->
                                updateState { it.copy(editingSource = null, remoteFolderTree = null) }
                                recordScanSummary(summary)
                                setMessage(scanSuccessMessage(uiText(Res.string.source_updated_and_rescanned), summary))
                            }.onFailure {
                                setMessage(uiText(Res.string.source_update_failed, it.uiErrorDetail()))
                            }
                        }
                    }

                    ImportSourceType.WEBDAV -> {
                        val draft = editingWebDavDraftOrNull(editor, requireSelection = true) ?: return
                        runScanningImport(ImportScanOperation.UpdateRemote(editor.sourceId)) { progressSink ->
                            repository.updateWebDavSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                                progressSink = progressSink,
                            ).onSuccess { summary ->
                                updateState { it.copy(editingSource = null, remoteFolderTree = null) }
                                recordScanSummary(summary)
                                setMessage(scanSuccessMessage(uiText(Res.string.source_updated_and_rescanned), summary))
                            }.onFailure {
                                setMessage(uiText(Res.string.source_update_failed, it.uiErrorDetail()))
                            }
                        }
                    }

                    ImportSourceType.NAVIDROME -> {
                        val draft = editingNavidromeDraftOrNull(editor) ?: return
                        runScanningImport(ImportScanOperation.UpdateRemote(editor.sourceId)) { progressSink ->
                            repository.updateNavidromeSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                                progressSink = progressSink,
                            ).onSuccess { summary ->
                                updateState { it.copy(editingSource = null, remoteFolderTree = null) }
                                recordScanSummary(summary)
                                setMessage(scanSuccessMessage(uiText(Res.string.source_updated_and_rescanned), summary))
                            }.onFailure {
                                setMessage(uiText(Res.string.source_update_failed, it.uiErrorDetail()))
                            }
                        }
                    }

                    ImportSourceType.SUBSONIC -> {
                        val draft = editingSubsonicDraftOrNull(editor) ?: return
                        runScanningImport(ImportScanOperation.UpdateRemote(editor.sourceId)) { progressSink ->
                            repository.updateSubsonicSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankCredential = editor.keepExistingCredential,
                                progressSink = progressSink,
                            ).onSuccess { summary ->
                                updateState { it.copy(editingSource = null, remoteFolderTree = null) }
                                recordScanSummary(summary)
                                setMessage(scanSuccessMessage(uiText(Res.string.source_updated_and_rescanned), summary))
                            }.onFailure {
                                setMessage(uiText(Res.string.source_update_failed, it.uiErrorDetail()))
                            }
                        }
                    }

                    ImportSourceType.EMBY -> {
                        val draft = editingEmbyDraftOrNull(editor) ?: return
                        runScanningImport(ImportScanOperation.UpdateRemote(editor.sourceId)) { progressSink ->
                            repository.updateEmbySource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                                progressSink = progressSink,
                            ).onSuccess { summary ->
                                updateState { it.copy(editingSource = null, remoteFolderTree = null) }
                                recordScanSummary(summary)
                                setMessage(scanSuccessMessage(uiText(Res.string.source_updated_and_rescanned), summary))
                            }.onFailure {
                                setMessage(uiText(Res.string.source_update_failed, it.uiErrorDetail()))
                            }
                        }
                    }

                    ImportSourceType.FN_MUSIC -> {
                        val draft = editingFnMusicDraftOrNull(editor) ?: return
                        runScanningImport(ImportScanOperation.UpdateRemote(editor.sourceId)) { progressSink ->
                            repository.updateFnMusicSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                                progressSink = progressSink,
                            ).onSuccess { summary ->
                                updateState { it.copy(editingSource = null, remoteFolderTree = null) }
                                recordScanSummary(summary)
                                setMessage(scanSuccessMessage(uiText(Res.string.source_updated_and_rescanned), summary))
                            }.onFailure {
                                setMessage(uiText(Res.string.source_update_failed, it.uiErrorDetail()))
                            }
                        }
                    }

                    ImportSourceType.LOCAL_FOLDER -> Unit
                }
            }

            is ImportIntent.RescanSource -> rescanSourceWithLargeNavidromeCheck(intent.sourceId)

            is ImportIntent.ReauthorizeLocalFolder -> {
                runScanningImport(ImportScanOperation.ReauthorizeLocalFolder(intent.sourceId)) { progressSink ->
                    repository.reauthorizeLocalFolder(intent.sourceId, progressSink)
                        .onSuccess { summary ->
                            summary?.let {
                                recordScanSummary(it)
                                setMessage(scanSuccessMessage(uiText(Res.string.source_local_folder_authorized_and_scanned), it))
                            }
                        }
                        .onFailure { setMessage(uiText(Res.string.source_local_folder_authorization_failed, it.uiErrorDetail())) }
                }
            }

            is ImportIntent.ToggleSourceEnabled -> runImport {
                repository.setSourceEnabled(intent.sourceId, intent.enabled)
                    .onSuccess {
                        setMessage(if (intent.enabled) uiText(Res.string.source_enabled_notice) else uiText(Res.string.source_disabled_notice))
                    }
                    .onFailure { setMessage(uiText(Res.string.source_status_update_failed, it.uiErrorDetail())) }
            }

            is ImportIntent.DeleteSource -> runImport {
                repository.deleteSource(intent.sourceId)
                    .onSuccess {
                        clearScanSummary(intent.sourceId)
                        setMessage(uiText(Res.string.source_deleted))
                    }
                    .onFailure { setMessage(uiText(Res.string.source_delete_failed, it.uiErrorDetail())) }
            }

            is ImportIntent.SambaLabelChanged -> updateState { it.copy(sambaLabel = intent.value) }
            is ImportIntent.SambaServerChanged -> updateState {
                it.copy(sambaServer = intent.value).resetFolderTreeIf(it.sambaServer != intent.value, keepSelection = false)
            }
            is ImportIntent.SambaPortChanged -> updateState {
                it.copy(sambaPort = intent.value).resetFolderTreeIf(it.sambaPort != intent.value, keepSelection = false)
            }
            is ImportIntent.SambaUsernameChanged -> updateState {
                it.copy(sambaUsername = intent.value).resetFolderTreeIf(it.sambaUsername != intent.value, keepSelection = true)
            }
            is ImportIntent.SambaPasswordChanged -> updateState {
                it.copy(sambaPassword = intent.value).resetFolderTreeIf(it.sambaPassword != intent.value, keepSelection = true)
            }
            is ImportIntent.WebDavLabelChanged -> updateState { it.copy(webDavLabel = intent.value) }
            is ImportIntent.WebDavRootUrlChanged -> updateState {
                it.copy(webDavRootUrl = intent.value).resetFolderTreeIf(it.webDavRootUrl != intent.value, keepSelection = false)
            }
            is ImportIntent.WebDavUsernameChanged -> updateState {
                it.copy(webDavUsername = intent.value).resetFolderTreeIf(it.webDavUsername != intent.value, keepSelection = true)
            }
            is ImportIntent.WebDavPasswordChanged -> updateState {
                it.copy(webDavPassword = intent.value).resetFolderTreeIf(it.webDavPassword != intent.value, keepSelection = true)
            }
            is ImportIntent.WebDavAllowInsecureTlsChanged -> updateState {
                it.copy(webDavAllowInsecureTls = intent.value)
                    .resetFolderTreeIf(it.webDavAllowInsecureTls != intent.value, keepSelection = true)
            }
            is ImportIntent.NavidromeLabelChanged -> updateState { it.copy(navidromeLabel = intent.value) }
            is ImportIntent.NavidromeBaseUrlChanged -> updateState { it.copy(navidromeBaseUrl = intent.value) }
            is ImportIntent.NavidromeWanBaseUrlChanged -> updateState { it.copy(navidromeWanBaseUrl = intent.value) }
            is ImportIntent.NavidromeUsernameChanged -> updateState { it.copy(navidromeUsername = intent.value) }
            is ImportIntent.NavidromePasswordChanged -> updateState { it.copy(navidromePassword = intent.value) }
            is ImportIntent.SubsonicLabelChanged -> updateState { it.copy(subsonicLabel = intent.value) }
            is ImportIntent.SubsonicBaseUrlChanged -> updateState { it.copy(subsonicBaseUrl = intent.value) }
            is ImportIntent.SubsonicWanBaseUrlChanged -> updateState { it.copy(subsonicWanBaseUrl = intent.value) }
            is ImportIntent.SubsonicUsernameChanged -> updateState { it.copy(subsonicUsername = intent.value) }
            is ImportIntent.SubsonicCredentialChanged -> updateState { it.copy(subsonicCredential = intent.value) }
            is ImportIntent.SubsonicAuthModeChanged -> updateState {
                if (it.subsonicAuthMode == intent.value) {
                    it
                } else {
                    it.copy(
                        subsonicAuthMode = intent.value,
                        subsonicUsername = if (intent.value == SubsonicAuthMode.API_KEY) "" else it.subsonicUsername,
                        subsonicCredential = "",
                    )
                }
            }
            is ImportIntent.EmbyLabelChanged -> updateState { it.copy(embyLabel = intent.value) }
            is ImportIntent.EmbyBaseUrlChanged -> updateState { it.copy(embyBaseUrl = intent.value) }
            is ImportIntent.EmbyWanBaseUrlChanged -> updateState { it.copy(embyWanBaseUrl = intent.value) }
            is ImportIntent.EmbyUsernameChanged -> updateState { it.copy(embyUsername = intent.value) }
            is ImportIntent.EmbyPasswordChanged -> updateState { it.copy(embyPassword = intent.value) }
            is ImportIntent.FnMusicLabelChanged -> updateState { it.copy(fnMusicLabel = intent.value) }
            is ImportIntent.FnMusicConnectionModeChanged -> updateState { it.copy(fnMusicConnectionMode = intent.value) }
            is ImportIntent.FnMusicBaseUrlChanged -> updateState { it.copy(fnMusicBaseUrl = intent.value) }
            is ImportIntent.FnMusicWanBaseUrlChanged -> updateState { it.copy(fnMusicWanBaseUrl = intent.value) }
            is ImportIntent.FnMusicIdChanged -> updateState { it.copy(fnMusicId = intent.value) }
            is ImportIntent.FnMusicUsernameChanged -> updateState { it.copy(fnMusicUsername = intent.value) }
            is ImportIntent.FnMusicPasswordChanged -> updateState { it.copy(fnMusicPassword = intent.value) }
            is ImportIntent.FnMusicAccessCodeChanged -> updateState { it.copy(fnMusicAccessCode = intent.value) }
            is ImportIntent.RemoteSourceFnMusicConnectionModeChanged -> updateEditingSource { it.copy(fnMusicConnectionMode = intent.value) }
            is ImportIntent.RemoteSourceFnMusicIdChanged -> updateEditingSource { it.copy(fnMusicId = intent.value) }
            is ImportIntent.RemoteSourceFnMusicAccessCodeChanged -> updateEditingSource { it.copy(fnMusicAccessCode = intent.value) }
            is ImportIntent.RemoteSourceLabelChanged -> updateEditingSource { it.copy(label = intent.value) }
            is ImportIntent.RemoteSourceServerChanged -> updateEditingSourceAndFolders(
                resetFolders = { it.server != intent.value },
                keepSelection = false,
            ) { it.copy(server = intent.value) }
            is ImportIntent.RemoteSourcePortChanged -> updateEditingSourceAndFolders(
                resetFolders = { it.port != intent.value },
                keepSelection = false,
            ) { it.copy(port = intent.value) }
            is ImportIntent.RemoteSourceRootUrlChanged -> updateEditingSourceAndFolders(
                resetFolders = { it.rootUrl != intent.value },
                keepSelection = false,
            ) {
                if (it.type == ImportSourceType.EMBY && it.rootUrl != intent.value) {
                    it.copy(rootUrl = intent.value, password = "", keepExistingCredential = false)
                } else {
                    it.copy(rootUrl = intent.value)
                }
            }
            is ImportIntent.RemoteSourceWanRootUrlChanged -> updateEditingSource { it.copy(wanRootUrl = intent.value) }
            is ImportIntent.RemoteSourceUsernameChanged -> updateEditingSourceAndFolders(
                resetFolders = { it.username != intent.value },
                keepSelection = true,
            ) {
                // The stored secret belongs to the old account: a new username needs its own password.
                val accountBound = it.type == ImportSourceType.EMBY || it.type == ImportSourceType.FN_MUSIC
                if (accountBound && it.username != intent.value) {
                    it.copy(username = intent.value, password = "", keepExistingCredential = false)
                } else {
                    it.copy(username = intent.value)
                }
            }
            is ImportIntent.RemoteSourcePasswordChanged -> updateEditingSourceAndFolders(
                resetFolders = { it.password != intent.value },
                keepSelection = true,
            ) {
                it.copy(
                    password = intent.value,
                    keepExistingCredential = intent.value.isBlank(),
                )
            }
            is ImportIntent.RemoteSourceAllowInsecureTlsChanged -> updateEditingSourceAndFolders(
                resetFolders = { it.allowInsecureTls != intent.value },
                keepSelection = true,
            ) { it.copy(allowInsecureTls = intent.value) }
            is ImportIntent.LoadRemoteFolder -> loadRemoteFolder(intent.path)
            is ImportIntent.ToggleRemoteFolderExpanded -> {
                val tree = state.value.remoteFolderTree ?: return
                val expanding = intent.path !in tree.expanded
                updateFolderTree { it.toggleExpanded(intent.path) }
                if (expanding && tree.needsLoading(intent.path)) loadRemoteFolder(intent.path)
            }
            is ImportIntent.ToggleRemoteFolderSelected -> updateFolderTree { it.toggleSelected(intent.path) }
            is ImportIntent.AddManualSambaShare -> updateFolderTree { it.withManualShare(intent.name) }
            is ImportIntent.RemoteSourceSubsonicAuthModeChanged -> updateEditingSource {
                if (it.subsonicAuthMode == intent.value) {
                    it
                } else {
                    it.copy(
                        subsonicAuthMode = intent.value,
                        username = if (intent.value == SubsonicAuthMode.API_KEY) "" else it.username,
                        password = "",
                        keepExistingCredential = false,
                    )
                }
            }
            ImportIntent.ClearMessage -> updateState { it.copy(message = null) }
            ImportIntent.ClearTestMessage -> updateState { it.copy(testMessage = null) }
        }
    }

    private suspend fun importLocalFolder(mode: LocalFolderPickerMode) {
        runScanningImport(ImportScanOperation.CreateLocalFolder) { progressSink ->
            repository.importLocalFolder(mode, progressSink)
                .onSuccess { summary ->
                    summary?.let {
                        recordScanSummary(it)
                        setMessage(scanSuccessMessage(uiText(Res.string.source_local_folder_imported), it))
                    }
                }
                .onFailure { setMessage(uiText(Res.string.source_local_folder_import_failed, it.uiErrorDetail())) }
        }
    }

    private suspend fun importNavidromeFull(draft: NavidromeSourceDraft) {
        runScanningImport(ImportScanOperation.CreateRemote(ImportSourceType.NAVIDROME)) { progressSink ->
            repository.addNavidromeSource(draft, progressSink)
                .onSuccess { summary ->
                    clearNavidromeCreator()
                    recordScanSummary(summary)
                    setMessage(scanSuccessMessage(uiText(Res.string.source_navidrome_imported), summary))
                }
                .onFailure { setCreateOrPageMessage(ImportSourceType.NAVIDROME, uiText(Res.string.source_navidrome_import_failed, it.uiErrorDetail())) }
        }
    }

    private suspend fun importNavidromeOnline(pending: PendingLargeNavidromeImport) {
        runImport(ImportScanOperation.CreateRemote(ImportSourceType.NAVIDROME)) {
            val draft = (pending.action as? PendingLargeNavidromeAction.Create)?.draft
                ?: return@runImport
            repository.addNavidromeSourceOnline(
                draft = draft,
                remoteTrackCount = pending.remoteTrackCount,
            ).onSuccess { summary ->
                clearNavidromeCreator()
                recordScanSummary(summary)
                setMessage(uiPlural(Res.plurals.source_navidrome_online_library_hint, (pending.remoteTrackCount).toInt(), pending.remoteTrackCount))
            }.onFailure {
                updateState { state -> state.copy(pendingLargeNavidromeImport = null) }
                setCreateOrPageMessage(ImportSourceType.NAVIDROME, uiText(Res.string.source_navidrome_online_mode_save_failed, it.uiErrorDetail()))
            }
        }
    }

    private suspend fun rescanSourceWithLargeNavidromeCheck(sourceId: String) {
        val source = state.value.sources.firstOrNull { it.source.id == sourceId }?.source
        if (source?.type == ImportSourceType.NAVIDROME && source.indexMode == ImportSourceIndexMode.LOCAL_INDEX) {
            runImport(ImportScanOperation.RescanSource(sourceId)) {
                repository.probeExistingNavidromeSource(sourceId)
                    .onSuccess { probe ->
                        val totalTrackCount = probe.totalTrackCount
                        if (
                            probe.supportsOnlineLibraryPaging &&
                            totalTrackCount != null &&
                            totalTrackCount > LARGE_NAVIDROME_LIBRARY_TRACK_THRESHOLD
                        ) {
                            updateState {
                                it.copy(
                                    pendingLargeNavidromeImport = PendingLargeNavidromeImport(
                                        action = PendingLargeNavidromeAction.Rescan(
                                            sourceId = sourceId,
                                            sourceLabel = source.label,
                                        ),
                                        remoteTrackCount = totalTrackCount,
                                    ),
                                    testMessage = null,
                                )
                            }
                        } else {
                            rescanSourceFull(sourceId)
                        }
                    }
                    .onFailure { setMessage(uiText(Res.string.source_rescan_failed, it.uiErrorDetail())) }
            }
        } else {
            rescanSourceFull(sourceId)
        }
    }

    private suspend fun rescanSourceFull(sourceId: String) {
        runScanningImport(ImportScanOperation.RescanSource(sourceId)) { progressSink ->
            repository.rescanSource(sourceId, progressSink)
                .onSuccess { summary ->
                    summary?.let(::recordScanSummary)
                    setMessage(scanSuccessMessage(uiText(Res.string.source_rescanned), summary))
                }
                .onFailure { setMessage(uiText(Res.string.source_rescan_failed, it.uiErrorDetail())) }
        }
    }

    private suspend fun switchNavidromeRescanToOnline(
        sourceId: String,
        sourceLabel: String,
        remoteTrackCount: Int,
    ) {
        runImport(ImportScanOperation.RescanSource(sourceId)) {
            repository.switchNavidromeSourceToOnline(
                sourceId = sourceId,
                remoteTrackCount = remoteTrackCount,
            ).onSuccess { summary ->
                updateState { it.copy(pendingLargeNavidromeImport = null) }
                recordScanSummary(summary)
                setMessage(uiPlural(Res.plurals.source_navidrome_online_switch_summary, remoteTrackCount, sourceLabel, remoteTrackCount))
            }.onFailure {
                updateState { state -> state.copy(pendingLargeNavidromeImport = null) }
                setMessage(uiText(Res.string.source_online_mode_switch_failed, it.uiErrorDetail()))
            }
        }
    }

    private fun clearNavidromeCreator() {
        updateState {
            it.copy(
                creatingSourceType = null,
                navidromeLabel = "",
                navidromeBaseUrl = "",
                navidromeWanBaseUrl = "",
                navidromeUsername = "",
                navidromePassword = "",
                pendingLargeNavidromeImport = null,
                testMessage = null,
            )
        }
    }

    private fun sambaDraftOrNull(state: ImportState, requireSelection: Boolean): SambaSourceDraft? {
        val port = state.sambaPort.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
        if (state.sambaServer.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.SAMBA, uiText(Res.string.source_samba_address_required))
            return null
        }
        if (state.sambaPort.isNotBlank() && port == null) {
            setCreateOrPageMessage(ImportSourceType.SAMBA, uiText(Res.string.source_port_invalid))
            return null
        }
        val selectedDirectories = state.remoteFolderTree?.selectedDirectories.orEmpty()
        if (requireSelection && selectedDirectories.isEmpty()) {
            setCreateOrPageMessage(ImportSourceType.SAMBA, uiText(Res.string.source_folder_selection_required))
            return null
        }
        return SambaSourceDraft(
            label = state.sambaLabel,
            server = state.sambaServer,
            port = port,
            username = state.sambaUsername,
            password = state.sambaPassword,
            selectedDirectories = selectedDirectories,
        )
    }

    private fun webDavDraftOrNull(
        state: ImportState,
        allowBlankPassword: Boolean,
        requireSelection: Boolean,
    ): WebDavSourceDraft? {
        if (state.webDavRootUrl.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.WEBDAV, uiText(Res.string.source_webdav_root_url_required))
            return null
        }
        if (!allowBlankPassword && state.webDavPassword.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.WEBDAV, uiText(Res.string.source_webdav_password_required))
            return null
        }
        if (state.webDavPassword.isNotBlank() && state.webDavUsername.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.WEBDAV, uiText(Res.string.webdav_password_username_required))
            return null
        }
        val selectedDirectories = state.remoteFolderTree?.selectedDirectories.orEmpty()
        if (requireSelection && selectedDirectories.isEmpty()) {
            setCreateOrPageMessage(ImportSourceType.WEBDAV, uiText(Res.string.source_folder_selection_required))
            return null
        }
        return WebDavSourceDraft(
            label = state.webDavLabel,
            rootUrl = state.webDavRootUrl,
            username = state.webDavUsername,
            password = state.webDavPassword,
            allowInsecureTls = state.webDavAllowInsecureTls,
            selectedDirectories = selectedDirectories,
        )
    }

    private fun navidromeDraftOrNull(
        label: String,
        baseUrl: String,
        wanBaseUrl: String,
        username: String,
        password: String,
        allowBlankPassword: Boolean,
    ): NavidromeSourceDraft? {
        if (baseUrl.isBlank() && wanBaseUrl.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.NAVIDROME, uiText(Res.string.source_server_address_missing))
            return null
        }
        if (username.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.NAVIDROME, uiText(Res.string.source_navidrome_username_required))
            return null
        }
        if (!allowBlankPassword && password.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.NAVIDROME, uiText(Res.string.source_navidrome_password_required))
            return null
        }
        return NavidromeSourceDraft(
            label = label,
            baseUrl = baseUrl,
            wanBaseUrl = wanBaseUrl,
            username = username,
            password = password,
        )
    }

    private fun subsonicDraftOrNull(
        label: String,
        baseUrl: String,
        wanBaseUrl: String,
        username: String,
        credential: String,
        authMode: SubsonicAuthMode,
        allowBlankCredential: Boolean,
    ): SubsonicSourceDraft? {
        if (baseUrl.isBlank() && wanBaseUrl.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.SUBSONIC, uiText(Res.string.source_server_address_missing))
            return null
        }
        if (authMode == SubsonicAuthMode.PASSWORD && username.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.SUBSONIC, uiText(Res.string.source_subsonic_username_required))
            return null
        }
        if (!allowBlankCredential && credential.isBlank()) {
            val labelText = if (authMode == SubsonicAuthMode.API_KEY) "API Key" else uiText(Res.string.common_password)
            setCreateOrPageMessage(ImportSourceType.SUBSONIC, uiText(Res.string.source_subsonic_credential_required, labelText))
            return null
        }
        return SubsonicSourceDraft(
            label = label,
            baseUrl = baseUrl,
            wanBaseUrl = wanBaseUrl,
            username = username,
            credential = credential,
            authMode = authMode,
        )
    }

    private fun embyDraftOrNull(
        label: String,
        baseUrl: String,
        wanBaseUrl: String,
        username: String,
        password: String,
        allowBlankPassword: Boolean,
    ): EmbySourceDraft? {
        if (baseUrl.isBlank() && wanBaseUrl.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.EMBY, uiText(Res.string.source_server_address_missing))
            return null
        }
        if (username.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.EMBY, uiText(Res.string.source_emby_username_required))
            return null
        }
        if (!allowBlankPassword && password.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.EMBY, uiText(Res.string.source_emby_password_required))
            return null
        }
        return EmbySourceDraft(
            label = label,
            baseUrl = baseUrl,
            wanBaseUrl = wanBaseUrl,
            username = username,
            password = password,
        )
    }

    private fun createFnMusicDraftOrNull(state: ImportState): FnMusicSourceDraft? = fnMusicDraftOrNull(
        label = state.fnMusicLabel,
        connectionMode = state.fnMusicConnectionMode,
        baseUrl = state.fnMusicBaseUrl,
        wanBaseUrl = state.fnMusicWanBaseUrl,
        fnId = state.fnMusicId,
        username = state.fnMusicUsername,
        password = state.fnMusicPassword,
        accessCode = state.fnMusicAccessCode,
        allowBlankPassword = false,
    )

    private fun fnMusicDraftOrNull(
        label: String,
        connectionMode: FnMusicConnectionMode,
        baseUrl: String,
        wanBaseUrl: String,
        fnId: String,
        username: String,
        password: String,
        accessCode: String,
        allowBlankPassword: Boolean,
    ): FnMusicSourceDraft? {
        val type = ImportSourceType.FN_MUSIC
        if (connectionMode == FnMusicConnectionMode.ADDRESS && baseUrl.isBlank() && wanBaseUrl.isBlank()) {
            setCreateOrPageMessage(type, uiText(Res.string.source_server_address_missing))
            return null
        }
        if (connectionMode == FnMusicConnectionMode.FN_CONNECT && fnId.isBlank()) {
            setCreateOrPageMessage(type, uiText(Res.string.fn_music_fn_id_required))
            return null
        }
        if (username.isBlank()) {
            setCreateOrPageMessage(type, uiText(Res.string.source_fn_music_username_required))
            return null
        }
        if (!allowBlankPassword && password.isBlank()) {
            setCreateOrPageMessage(type, uiText(Res.string.source_fn_music_password_required))
            return null
        }
        return FnMusicSourceDraft(
            label = label,
            connectionMode = connectionMode,
            baseUrl = if (connectionMode == FnMusicConnectionMode.ADDRESS) baseUrl else "",
            wanBaseUrl = if (connectionMode == FnMusicConnectionMode.ADDRESS) wanBaseUrl else "",
            fnId = if (connectionMode == FnMusicConnectionMode.FN_CONNECT) fnId else "",
            username = username,
            password = password,
            accessCode = if (connectionMode == FnMusicConnectionMode.FN_CONNECT) accessCode else "",
        )
    }

    private fun editingFnMusicDraftOrNull(editor: RemoteSourceEditorState): FnMusicSourceDraft? {
        val canReuseStoredCredential = editor.keepExistingCredential && editor.hasStoredCredential
        return fnMusicDraftOrNull(
            label = editor.label,
            connectionMode = editor.fnMusicConnectionMode,
            baseUrl = editor.rootUrl,
            wanBaseUrl = editor.wanRootUrl,
            fnId = editor.fnMusicId,
            username = editor.username,
            password = editor.password,
            accessCode = editor.fnMusicAccessCode,
            allowBlankPassword = canReuseStoredCredential,
        )
    }

    /** Problems are shown inside the edit dialog, which would hide the page banner. */
    private fun editingSambaDraftOrNull(editor: RemoteSourceEditorState, requireSelection: Boolean): SambaSourceDraft? {
        val port = editor.port.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
        if (editor.server.isBlank()) {
            setTestMessage(uiText(Res.string.source_samba_address_required))
            return null
        }
        if (editor.port.isNotBlank() && port == null) {
            setTestMessage(uiText(Res.string.source_port_invalid))
            return null
        }
        if (requireSelection && !hasSelectedFolders()) return null
        return SambaSourceDraft(
            label = editor.label,
            server = editor.server,
            port = port,
            path = editor.path,
            username = editor.username,
            password = editor.password,
            selectedDirectories = state.value.remoteFolderTree?.selectedDirectories.orEmpty(),
        )
    }

    /** Problems are shown inside the edit dialog, which would hide the page banner. */
    private fun editingWebDavDraftOrNull(editor: RemoteSourceEditorState, requireSelection: Boolean): WebDavSourceDraft? {
        if (editor.rootUrl.isBlank()) {
            setTestMessage(uiText(Res.string.source_webdav_root_url_required))
            return null
        }
        if (!editor.keepExistingCredential && editor.password.isBlank()) {
            setTestMessage(uiText(Res.string.source_webdav_password_required))
            return null
        }
        if (editor.password.isNotBlank() && editor.username.isBlank()) {
            setTestMessage(uiText(Res.string.webdav_password_username_required))
            return null
        }
        if (requireSelection && !hasSelectedFolders()) return null
        return WebDavSourceDraft(
            label = editor.label,
            rootUrl = editor.rootUrl,
            username = editor.username,
            password = editor.password,
            allowInsecureTls = editor.allowInsecureTls,
            selectedDirectories = state.value.remoteFolderTree?.selectedDirectories.orEmpty(),
        )
    }

    /** Saving must scan at least one folder: an empty selection would be stored as "the whole root". */
    private fun hasSelectedFolders(): Boolean {
        if (state.value.remoteFolderTree?.selectedDirectories.orEmpty().isNotEmpty()) return true
        setTestMessage(uiText(Res.string.source_folder_selection_required))
        return false
    }

    private fun editingNavidromeDraftOrNull(editor: RemoteSourceEditorState): NavidromeSourceDraft? {
        val canReuseStoredPassword = editor.keepExistingCredential && editor.hasStoredCredential
        return navidromeDraftOrNull(
            label = editor.label,
            baseUrl = editor.rootUrl,
            wanBaseUrl = editor.wanRootUrl,
            username = editor.username,
            password = editor.password,
            allowBlankPassword = canReuseStoredPassword,
        )
    }

    private fun editingSubsonicDraftOrNull(editor: RemoteSourceEditorState): SubsonicSourceDraft? {
        val canReuseStoredCredential = editor.keepExistingCredential && editor.hasStoredCredential
        return subsonicDraftOrNull(
            label = editor.label,
            baseUrl = editor.rootUrl,
            wanBaseUrl = editor.wanRootUrl,
            username = editor.username,
            credential = editor.password,
            authMode = editor.subsonicAuthMode,
            allowBlankCredential = canReuseStoredCredential,
        )
    }

    private fun editingEmbyDraftOrNull(editor: RemoteSourceEditorState): EmbySourceDraft? {
        val canReuseStoredCredential = editor.keepExistingCredential && editor.hasStoredCredential
        return embyDraftOrNull(
            label = editor.label,
            baseUrl = editor.rootUrl,
            wanBaseUrl = editor.wanRootUrl,
            username = editor.username,
            password = editor.password,
            allowBlankPassword = canReuseStoredCredential,
        )
    }

    private fun updateEditingSource(transform: (RemoteSourceEditorState) -> RemoteSourceEditorState) {
        updateState { state ->
            state.copy(editingSource = state.editingSource?.let(transform))
        }
    }

    /** Edits the open editor and drops browsed folders when a connection detail it depends on changed. */
    private fun updateEditingSourceAndFolders(
        resetFolders: (RemoteSourceEditorState) -> Boolean,
        keepSelection: Boolean,
        transform: (RemoteSourceEditorState) -> RemoteSourceEditorState,
    ) {
        updateState { state ->
            val editor = state.editingSource ?: return@updateState state
            state.copy(editingSource = transform(editor))
                .resetFolderTreeIf(resetFolders(editor), keepSelection)
        }
    }

    private fun ImportState.resetFolderTreeIf(changed: Boolean, keepSelection: Boolean): ImportState =
        if (changed && remoteFolderTree != null) copy(remoteFolderTree = remoteFolderTree.reset(keepSelection)) else this

    private fun updateFolderTree(transform: (RemoteFolderTreeState) -> RemoteFolderTreeState) {
        updateState { state -> state.copy(remoteFolderTree = state.remoteFolderTree?.let(transform)) }
    }

    /** Installs the folder tree of a newly opened dialog under a fresh id, so listings of earlier dialogs can't land in it. */
    private fun ImportState.withNewFolderTree(tree: RemoteFolderTreeState?): ImportState {
        val instanceId = lastRemoteFolderTreeId + 1
        return copy(remoteFolderTree = tree?.copy(instanceId = instanceId), lastRemoteFolderTreeId = instanceId)
    }

    private fun newRemoteFolderTree(type: ImportSourceType, sambaRootPath: String): RemoteFolderTreeState? = when (type) {
        ImportSourceType.SAMBA -> RemoteFolderTreeState(rootSelectable = sambaRootPath.isNotBlank())
        ImportSourceType.WEBDAV -> RemoteFolderTreeState(rootSelectable = true)
        else -> null
    }

    /**
     * Lists one folder of the open add/edit dialog's source. Results are dropped when the dialog was closed or replaced
     * (another tree id) or its connection details changed meanwhile (another generation).
     */
    private suspend fun loadRemoteFolder(path: String) {
        val current = state.value
        val tree = current.remoteFolderTree ?: return
        val editor = current.editingSource
        val creatingType = current.creatingSourceType
        val listing: suspend () -> Result<List<RemoteDirectoryEntry>> = when {
            editor?.type == ImportSourceType.SAMBA -> {
                val draft = editingSambaDraftOrNull(editor, requireSelection = false) ?: return
                suspend {
                    repository.listUpdatedSambaDirectories(editor.sourceId, draft, editor.keepExistingCredential, path)
                }
            }
            editor?.type == ImportSourceType.WEBDAV -> {
                val draft = editingWebDavDraftOrNull(editor, requireSelection = false) ?: return
                suspend {
                    repository.listUpdatedWebDavDirectories(editor.sourceId, draft, editor.keepExistingCredential, path)
                }
            }
            editor == null && creatingType == ImportSourceType.SAMBA -> {
                val draft = sambaDraftOrNull(current, requireSelection = false) ?: return
                suspend { repository.listSambaDirectories(draft, path) }
            }
            editor == null && creatingType == ImportSourceType.WEBDAV -> {
                val draft = webDavDraftOrNull(current, allowBlankPassword = true, requireSelection = false) ?: return
                suspend { repository.listWebDavDirectories(draft, path) }
            }
            else -> return
        }
        val isSameTree = { candidate: RemoteFolderTreeState ->
            candidate.instanceId == tree.instanceId && candidate.generation == tree.generation
        }
        updateFolderTree { if (isSameTree(it)) it.loading(path) else it }
        val result = listing()
        updateFolderTree { latest ->
            if (isSameTree(latest)) {
                latest.loaded(path, result) { uiText(Res.string.source_folder_tree_load_failed, it.uiErrorDetail()) }
            } else {
                latest
            }
        }
    }

    private fun ImportState.clearCreateDraft(type: ImportSourceType): ImportState {
        return when (type) {
            ImportSourceType.SAMBA -> copy(
                sambaLabel = "",
                sambaServer = "",
                sambaPort = "",
                sambaUsername = "",
                sambaPassword = "",
            )

            ImportSourceType.WEBDAV -> copy(
                webDavLabel = "",
                webDavRootUrl = "",
                webDavUsername = "",
                webDavPassword = "",
                webDavAllowInsecureTls = false,
            )

            ImportSourceType.NAVIDROME -> copy(
                navidromeLabel = "",
                navidromeBaseUrl = "",
                navidromeWanBaseUrl = "",
                navidromeUsername = "",
                navidromePassword = "",
            )

            ImportSourceType.SUBSONIC -> copy(
                subsonicLabel = "",
                subsonicBaseUrl = "",
                subsonicWanBaseUrl = "",
                subsonicUsername = "",
                subsonicCredential = "",
                subsonicAuthMode = SubsonicAuthMode.PASSWORD,
            )

            ImportSourceType.EMBY -> copy(
                embyLabel = "",
                embyBaseUrl = "",
                embyWanBaseUrl = "",
                embyUsername = "",
                embyPassword = "",
            )

            ImportSourceType.FN_MUSIC -> copy(
                fnMusicLabel = "",
                fnMusicConnectionMode = FnMusicConnectionMode.ADDRESS,
                fnMusicBaseUrl = "",
                fnMusicWanBaseUrl = "",
                fnMusicId = "",
                fnMusicUsername = "",
                fnMusicPassword = "",
                fnMusicAccessCode = "",
            )

            ImportSourceType.LOCAL_FOLDER -> this
        }
    }

    private fun recordScanSummary(summary: ImportScanSummary) {
        updateState { state ->
            state.copy(
                latestScanSummariesBySourceId = state.latestScanSummariesBySourceId + (summary.sourceId to summary),
            )
        }
    }

    private fun clearScanSummary(sourceId: String) {
        updateState { state ->
            state.copy(latestScanSummariesBySourceId = state.latestScanSummariesBySourceId - sourceId)
        }
    }

    private suspend fun runImport(
        scanOperation: ImportScanOperation? = null,
        block: suspend () -> Unit,
    ) {
        updateState { it.copy(isWorking = true, activeScanOperation = scanOperation) }
        runCatching { block() }
        updateState { it.copy(isWorking = false, activeScanOperation = null) }
    }

    private suspend fun runScanningImport(
        scanOperation: ImportScanOperation,
        block: suspend (ImportScanProgressSink) -> Unit,
    ) {
        val progressSink = ThrottledImportScanProgressSink(emit = { progress ->
            updateState { state -> state.copy(scanProgress = progress) }
        })
        updateState {
            it.copy(
                isWorking = true,
                activeScanOperation = scanOperation,
                scanProgress = null,
            )
        }
        runCatching { block(progressSink) }
        updateState { it.copy(isWorking = false, activeScanOperation = null, scanProgress = null) }
    }

    private fun setMessage(message: UiText) {
        updateState { it.copy(message = message) }
    }

    /** A test that reached the source through only one of its addresses reads as partial success, not failure. */
    private fun remoteConnectionTestFailureText(
        throwable: Throwable,
        sourceLabel: Any,
        failedMessage: StringResource,
    ): UiText {
        val detail = throwable.uiErrorDetail()
        return if ((throwable as? RemoteSourceAddressTestException)?.anySucceeded == true) {
            uiText(Res.string.source_connection_partially_succeeded, sourceLabel, detail)
        } else {
            uiText(failedMessage, detail)
        }
    }

    private fun setTestMessage(message: UiText) {
        updateState { it.copy(testMessage = message) }
    }

    private fun setCreateOrPageMessage(type: ImportSourceType, message: UiText) {
        if (state.value.creatingSourceType == type) {
            setTestMessage(message)
        } else {
            setMessage(message)
        }
    }
}

private class ThrottledImportScanProgressSink(
    private val emit: (ImportScanProgress) -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ImportScanProgressSink {
    private var lastEmittedAt: Long = 0L

    override fun onProgress(progress: ImportScanProgress) {
        val currentTime = now()
        if (progress.phase == ImportScanPhase.Persisting || lastEmittedAt == 0L || currentTime - lastEmittedAt >= 200L) {
            emit(progress)
            lastEmittedAt = currentTime
        }
    }
}

fun formatImportScanSummary(summary: ImportScanSummary): UiText {
    return uiText(Res.string.import_scan_summary,
        uiPlural(Res.plurals.import_audio_file_count, (summary.discoveredAudioFileCount).toInt(), summary.discoveredAudioFileCount),
        uiPlural(Res.plurals.common_track_count_short, (summary.importedTrackCount).toInt(), summary.importedTrackCount),
        uiPlural(Res.plurals.import_failure_count, (summary.failedAudioFileCount).toInt(), summary.failedAudioFileCount),
    )
}

const val LARGE_NAVIDROME_LIBRARY_TRACK_THRESHOLD: Int = 100_000

private fun scanSuccessMessage(prefix: UiText, summary: ImportScanSummary?): UiText {
    return summary?.let { uiText(Res.string.import_scan_success, prefix, formatImportScanSummary(it)) } ?: prefix
}
