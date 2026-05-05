package top.iwesley.lyn.music.feature.importing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.displayWebDavRootUrl
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.ImportSourceRepository

data class RemoteSourceEditorState(
    val sourceId: String,
    val type: ImportSourceType,
    val label: String = "",
    val server: String = "",
    val port: String = "",
    val path: String = "",
    val rootUrl: String = "",
    val username: String = "",
    val password: String = "",
    val allowInsecureTls: Boolean = false,
    val hasStoredCredential: Boolean = false,
    val keepExistingCredential: Boolean = true,
)

sealed interface ImportScanOperation {
    data object CreateLocalFolder : ImportScanOperation
    data class CreateRemote(val type: ImportSourceType) : ImportScanOperation
    data class RescanSource(val sourceId: String) : ImportScanOperation
    data class UpdateRemote(val sourceId: String) : ImportScanOperation
}

data class ImportState(
    val capabilities: PlatformCapabilities,
    val sources: List<SourceWithStatus> = emptyList(),
    val sambaLabel: String = "",
    val sambaServer: String = "",
    val sambaPort: String = "",
    val sambaPath: String = "",
    val sambaUsername: String = "",
    val sambaPassword: String = "",
    val webDavLabel: String = "",
    val webDavRootUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val webDavAllowInsecureTls: Boolean = false,
    val navidromeLabel: String = "",
    val navidromeBaseUrl: String = "",
    val navidromeUsername: String = "",
    val navidromePassword: String = "",
    val creatingSourceType: ImportSourceType? = null,
    val editingSource: RemoteSourceEditorState? = null,
    val isWorking: Boolean = false,
    val activeScanOperation: ImportScanOperation? = null,
    val latestScanSummariesBySourceId: Map<String, ImportScanSummary> = emptyMap(),
    val message: String? = null,
    val testMessage: String? = null,
)

sealed interface ImportIntent {
    data object ImportLocalFolder : ImportIntent
    data class ImportSelectedLocalFolder(val selection: LocalFolderSelection) : ImportIntent
    data object TestSambaSource : ImportIntent
    data object AddSambaSource : ImportIntent
    data object TestWebDavSource : ImportIntent
    data object AddWebDavSource : ImportIntent
    data object TestNavidromeSource : ImportIntent
    data object AddNavidromeSource : ImportIntent
    data class OpenRemoteSourceCreator(val type: ImportSourceType) : ImportIntent
    data object DismissRemoteSourceCreator : ImportIntent
    data class OpenRemoteSourceEditor(val sourceId: String) : ImportIntent
    data object DismissRemoteSourceEditor : ImportIntent
    data object TestRemoteSource : ImportIntent
    data object SaveRemoteSource : ImportIntent
    data class RescanSource(val sourceId: String) : ImportIntent
    data class ToggleSourceEnabled(val sourceId: String, val enabled: Boolean) : ImportIntent
    data class DeleteSource(val sourceId: String) : ImportIntent
    data class SambaLabelChanged(val value: String) : ImportIntent
    data class SambaServerChanged(val value: String) : ImportIntent
    data class SambaPortChanged(val value: String) : ImportIntent
    data class SambaPathChanged(val value: String) : ImportIntent
    data class SambaUsernameChanged(val value: String) : ImportIntent
    data class SambaPasswordChanged(val value: String) : ImportIntent
    data class WebDavLabelChanged(val value: String) : ImportIntent
    data class WebDavRootUrlChanged(val value: String) : ImportIntent
    data class WebDavUsernameChanged(val value: String) : ImportIntent
    data class WebDavPasswordChanged(val value: String) : ImportIntent
    data class WebDavAllowInsecureTlsChanged(val value: Boolean) : ImportIntent
    data class NavidromeLabelChanged(val value: String) : ImportIntent
    data class NavidromeBaseUrlChanged(val value: String) : ImportIntent
    data class NavidromeUsernameChanged(val value: String) : ImportIntent
    data class NavidromePasswordChanged(val value: String) : ImportIntent
    data class RemoteSourceLabelChanged(val value: String) : ImportIntent
    data class RemoteSourceServerChanged(val value: String) : ImportIntent
    data class RemoteSourcePortChanged(val value: String) : ImportIntent
    data class RemoteSourcePathChanged(val value: String) : ImportIntent
    data class RemoteSourceRootUrlChanged(val value: String) : ImportIntent
    data class RemoteSourceUsernameChanged(val value: String) : ImportIntent
    data class RemoteSourcePasswordChanged(val value: String) : ImportIntent
    data class RemoteSourceAllowInsecureTlsChanged(val value: Boolean) : ImportIntent
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
                    state.copy(
                        sources = sources,
                        editingSource = state.editingSource?.takeIf { editing ->
                            sources.any { it.source.id == editing.sourceId && it.source.type == editing.type }
                        },
                        latestScanSummariesBySourceId = state.latestScanSummariesBySourceId.filterKeys { it in sourceIds },
                    )
                }
            }
        }
    }

    override suspend fun handleIntent(intent: ImportIntent) {
        when (intent) {
            ImportIntent.ImportLocalFolder -> runImport(ImportScanOperation.CreateLocalFolder) {
                repository.importLocalFolder()
                    .onSuccess { summary ->
                        summary?.let {
                            recordScanSummary(it)
                            setMessage(scanSuccessMessage("本地音乐源已导入。", it))
                        }
                    }
                    .onFailure { setMessage("导入本地文件夹失败: ${it.message}") }
            }

            is ImportIntent.ImportSelectedLocalFolder -> runImport(ImportScanOperation.CreateLocalFolder) {
                repository.importSelectedLocalFolder(intent.selection)
                    .onSuccess { summary ->
                        recordScanSummary(summary)
                        setMessage(scanSuccessMessage("本地音乐源已导入。", summary))
                    }
                    .onFailure { setMessage("导入本地文件夹失败: ${it.message}") }
            }

            ImportIntent.TestSambaSource -> {
                val draft = sambaDraftOrNull(state.value) ?: return
                runImport {
                    repository.testSambaSource(draft)
                        .onSuccess { setTestMessage("Samba 连接测试成功。") }
                        .onFailure { setTestMessage("Samba 连接测试失败: ${it.message}") }
                }
            }

            ImportIntent.AddSambaSource -> {
                val currentState = state.value
                val draft = sambaDraftOrNull(currentState) ?: return
                runImport(ImportScanOperation.CreateRemote(ImportSourceType.SAMBA)) {
                    repository.addSambaSource(draft)
                        .onSuccess { summary ->
                            updateState {
                                it.copy(
                                    creatingSourceType = null,
                                    sambaLabel = "",
                                    sambaServer = "",
                                    sambaPort = "",
                                    sambaPath = "",
                                    sambaUsername = "",
                                    sambaPassword = "",
                                    testMessage = null,
                                )
                            }
                            recordScanSummary(summary)
                            setMessage(scanSuccessMessage("Samba 音乐源已导入。", summary))
                        }
                        .onFailure { setCreateOrPageMessage(ImportSourceType.SAMBA, "Samba 导入失败: ${it.message}") }
                }
            }

            ImportIntent.TestWebDavSource -> {
                val draft = webDavDraftOrNull(state.value, allowBlankPassword = true) ?: return
                runImport {
                    repository.testWebDavSource(draft)
                        .onSuccess { setTestMessage("WebDAV 连接测试成功。") }
                        .onFailure { setTestMessage("WebDAV 连接测试失败: ${it.message}") }
                }
            }

            ImportIntent.AddWebDavSource -> {
                val draft = webDavDraftOrNull(state.value, allowBlankPassword = true) ?: return
                runImport(ImportScanOperation.CreateRemote(ImportSourceType.WEBDAV)) {
                    repository.addWebDavSource(draft)
                        .onSuccess { summary ->
                            updateState {
                                it.copy(
                                    creatingSourceType = null,
                                    webDavLabel = "",
                                    webDavRootUrl = "",
                                    webDavUsername = "",
                                    webDavPassword = "",
                                    webDavAllowInsecureTls = false,
                                    testMessage = null,
                                )
                            }
                            recordScanSummary(summary)
                            setMessage(scanSuccessMessage("WebDAV 音乐源已导入。", summary))
                        }
                        .onFailure { setCreateOrPageMessage(ImportSourceType.WEBDAV, "WebDAV 导入失败: ${it.message}") }
                }
            }

            ImportIntent.TestNavidromeSource -> {
                val draft = navidromeDraftOrNull(
                    label = state.value.navidromeLabel,
                    baseUrl = state.value.navidromeBaseUrl,
                    username = state.value.navidromeUsername,
                    password = state.value.navidromePassword,
                    allowBlankPassword = false,
                ) ?: return
                runImport {
                    repository.testNavidromeSource(draft)
                        .onSuccess { setTestMessage("Navidrome 连接测试成功。") }
                        .onFailure { setTestMessage("Navidrome 连接测试失败: ${it.message}") }
                }
            }

            ImportIntent.AddNavidromeSource -> {
                val draft = navidromeDraftOrNull(
                    label = state.value.navidromeLabel,
                    baseUrl = state.value.navidromeBaseUrl,
                    username = state.value.navidromeUsername,
                    password = state.value.navidromePassword,
                    allowBlankPassword = false,
                ) ?: return
                runImport(ImportScanOperation.CreateRemote(ImportSourceType.NAVIDROME)) {
                    repository.addNavidromeSource(draft)
                        .onSuccess { summary ->
                            updateState {
                                it.copy(
                                    creatingSourceType = null,
                                    navidromeLabel = "",
                                    navidromeBaseUrl = "",
                                    navidromeUsername = "",
                                    navidromePassword = "",
                                    testMessage = null,
                                )
                            }
                            recordScanSummary(summary)
                            setMessage(scanSuccessMessage("Navidrome 音乐源已导入。", summary))
                        }
                        .onFailure { setCreateOrPageMessage(ImportSourceType.NAVIDROME, "Navidrome 导入失败: ${it.message}") }
                }
            }

            is ImportIntent.OpenRemoteSourceCreator -> {
                if (intent.type == ImportSourceType.LOCAL_FOLDER) return
                updateState { state ->
                    state.clearCreateDraft(intent.type).copy(
                        creatingSourceType = intent.type,
                        editingSource = null,
                        testMessage = null,
                    )
                }
            }

            ImportIntent.DismissRemoteSourceCreator -> updateState { state ->
                val type = state.creatingSourceType
                if (type == null) {
                    state.copy(testMessage = null)
                } else {
                    state.clearCreateDraft(type).copy(creatingSourceType = null, testMessage = null)
                }
            }

            is ImportIntent.OpenRemoteSourceEditor -> {
                val source = state.value.sources.firstOrNull { it.source.id == intent.sourceId }?.source ?: return
                if (source.type == ImportSourceType.LOCAL_FOLDER) return
                updateState {
                    it.copy(
                        editingSource = RemoteSourceEditorState(
                            sourceId = source.id,
                            type = source.type,
                            label = source.label,
                            server = source.server.orEmpty(),
                            port = source.port?.toString().orEmpty(),
                            path = source.path.orEmpty(),
                            rootUrl = if (source.type == ImportSourceType.WEBDAV) {
                                displayWebDavRootUrl(source.rootReference)
                            } else {
                                source.rootReference
                            },
                            username = source.username.orEmpty(),
                            password = "",
                            allowInsecureTls = source.allowInsecureTls,
                            hasStoredCredential = source.credentialKey != null,
                            keepExistingCredential = true,
                        ),
                    )
                }
            }

            ImportIntent.DismissRemoteSourceEditor -> updateState { it.copy(editingSource = null) }

            ImportIntent.TestRemoteSource -> {
                val editor = state.value.editingSource ?: return
                when (editor.type) {
                    ImportSourceType.SAMBA -> {
                        val draft = editingSambaDraftOrNull(editor) ?: return
                        runImport {
                            repository.testUpdatedSambaSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess {
                                setTestMessage("Samba 连接测试成功。")
                            }.onFailure {
                                setTestMessage("Samba 连接测试失败: ${it.message}")
                            }
                        }
                    }

                    ImportSourceType.WEBDAV -> {
                        val draft = editingWebDavDraftOrNull(editor) ?: return
                        runImport {
                            repository.testUpdatedWebDavSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess {
                                setTestMessage("WebDAV 连接测试成功。")
                            }.onFailure {
                                setTestMessage("WebDAV 连接测试失败: ${it.message}")
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
                                setTestMessage("Navidrome 连接测试成功。")
                            }.onFailure {
                                setTestMessage("Navidrome 连接测试失败: ${it.message}")
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
                        val draft = editingSambaDraftOrNull(editor) ?: return
                        runImport(ImportScanOperation.UpdateRemote(editor.sourceId)) {
                            repository.updateSambaSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess { summary ->
                                updateState { it.copy(editingSource = null) }
                                recordScanSummary(summary)
                                setMessage(scanSuccessMessage("来源已更新并重新扫描。", summary))
                            }.onFailure {
                                setMessage("更新来源失败: ${it.message}")
                            }
                        }
                    }

                    ImportSourceType.WEBDAV -> {
                        val draft = editingWebDavDraftOrNull(editor) ?: return
                        runImport(ImportScanOperation.UpdateRemote(editor.sourceId)) {
                            repository.updateWebDavSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess { summary ->
                                updateState { it.copy(editingSource = null) }
                                recordScanSummary(summary)
                                setMessage(scanSuccessMessage("来源已更新并重新扫描。", summary))
                            }.onFailure {
                                setMessage("更新来源失败: ${it.message}")
                            }
                        }
                    }

                    ImportSourceType.NAVIDROME -> {
                        val draft = editingNavidromeDraftOrNull(editor) ?: return
                        runImport(ImportScanOperation.UpdateRemote(editor.sourceId)) {
                            repository.updateNavidromeSource(
                                sourceId = editor.sourceId,
                                draft = draft,
                                keepExistingCredentialWhenBlankPassword = editor.keepExistingCredential,
                            ).onSuccess { summary ->
                                updateState { it.copy(editingSource = null) }
                                recordScanSummary(summary)
                                setMessage(scanSuccessMessage("来源已更新并重新扫描。", summary))
                            }.onFailure {
                                setMessage("更新来源失败: ${it.message}")
                            }
                        }
                    }

                    ImportSourceType.LOCAL_FOLDER -> Unit
                }
            }

            is ImportIntent.RescanSource -> runImport(ImportScanOperation.RescanSource(intent.sourceId)) {
                repository.rescanSource(intent.sourceId)
                    .onSuccess { summary ->
                        summary?.let(::recordScanSummary)
                        setMessage(scanSuccessMessage("音乐源已重新扫描。", summary))
                    }
                    .onFailure { setMessage("重新扫描失败: ${it.message}") }
            }

            is ImportIntent.ToggleSourceEnabled -> runImport {
                repository.setSourceEnabled(intent.sourceId, intent.enabled)
                    .onSuccess {
                        setMessage(if (intent.enabled) "来源已启用。" else "来源已禁用。")
                    }
                    .onFailure { setMessage("更新来源状态失败: ${it.message}") }
            }

            is ImportIntent.DeleteSource -> runImport {
                repository.deleteSource(intent.sourceId)
                    .onSuccess {
                        clearScanSummary(intent.sourceId)
                        setMessage("音乐源已删除。")
                    }
                    .onFailure { setMessage("删除音乐源失败: ${it.message}") }
            }

            is ImportIntent.SambaLabelChanged -> updateState { it.copy(sambaLabel = intent.value) }
            is ImportIntent.SambaServerChanged -> updateState { it.copy(sambaServer = intent.value) }
            is ImportIntent.SambaPortChanged -> updateState { it.copy(sambaPort = intent.value) }
            is ImportIntent.SambaPathChanged -> updateState { it.copy(sambaPath = intent.value) }
            is ImportIntent.SambaUsernameChanged -> updateState { it.copy(sambaUsername = intent.value) }
            is ImportIntent.SambaPasswordChanged -> updateState { it.copy(sambaPassword = intent.value) }
            is ImportIntent.WebDavLabelChanged -> updateState { it.copy(webDavLabel = intent.value) }
            is ImportIntent.WebDavRootUrlChanged -> updateState { it.copy(webDavRootUrl = intent.value) }
            is ImportIntent.WebDavUsernameChanged -> updateState { it.copy(webDavUsername = intent.value) }
            is ImportIntent.WebDavPasswordChanged -> updateState { it.copy(webDavPassword = intent.value) }
            is ImportIntent.WebDavAllowInsecureTlsChanged -> updateState { it.copy(webDavAllowInsecureTls = intent.value) }
            is ImportIntent.NavidromeLabelChanged -> updateState { it.copy(navidromeLabel = intent.value) }
            is ImportIntent.NavidromeBaseUrlChanged -> updateState { it.copy(navidromeBaseUrl = intent.value) }
            is ImportIntent.NavidromeUsernameChanged -> updateState { it.copy(navidromeUsername = intent.value) }
            is ImportIntent.NavidromePasswordChanged -> updateState { it.copy(navidromePassword = intent.value) }
            is ImportIntent.RemoteSourceLabelChanged -> updateEditingSource { it.copy(label = intent.value) }
            is ImportIntent.RemoteSourceServerChanged -> updateEditingSource { it.copy(server = intent.value) }
            is ImportIntent.RemoteSourcePortChanged -> updateEditingSource { it.copy(port = intent.value) }
            is ImportIntent.RemoteSourcePathChanged -> updateEditingSource { it.copy(path = intent.value) }
            is ImportIntent.RemoteSourceRootUrlChanged -> updateEditingSource { it.copy(rootUrl = intent.value) }
            is ImportIntent.RemoteSourceUsernameChanged -> updateEditingSource { it.copy(username = intent.value) }
            is ImportIntent.RemoteSourcePasswordChanged -> updateEditingSource {
                it.copy(
                    password = intent.value,
                    keepExistingCredential = intent.value.isBlank(),
                )
            }
            is ImportIntent.RemoteSourceAllowInsecureTlsChanged -> updateEditingSource { it.copy(allowInsecureTls = intent.value) }
            ImportIntent.ClearMessage -> updateState { it.copy(message = null) }
            ImportIntent.ClearTestMessage -> updateState { it.copy(testMessage = null) }
        }
    }

    private fun sambaDraftOrNull(state: ImportState): SambaSourceDraft? {
        val port = state.sambaPort.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
        if (state.sambaServer.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.SAMBA, "请先填写 Samba 服务器地址。")
            return null
        }
        if (state.sambaPort.isNotBlank() && port == null) {
            setCreateOrPageMessage(ImportSourceType.SAMBA, "端口号格式不正确。")
            return null
        }
        if (state.sambaPath.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.SAMBA, "请填写路径，至少包含共享名，例如 Media 或 Media/Music。")
            return null
        }
        return SambaSourceDraft(
            label = state.sambaLabel,
            server = state.sambaServer,
            port = port,
            path = state.sambaPath,
            username = state.sambaUsername,
            password = state.sambaPassword,
        )
    }

    private fun webDavDraftOrNull(
        state: ImportState,
        allowBlankPassword: Boolean,
    ): WebDavSourceDraft? {
        if (state.webDavRootUrl.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.WEBDAV, "请先填写 WebDAV 根 URL。")
            return null
        }
        if (!allowBlankPassword && state.webDavPassword.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.WEBDAV, "请先填写 WebDAV 密码。")
            return null
        }
        if (state.webDavPassword.isNotBlank() && state.webDavUsername.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.WEBDAV, "WebDAV 使用密码时必须填写用户名。")
            return null
        }
        return WebDavSourceDraft(
            label = state.webDavLabel,
            rootUrl = state.webDavRootUrl,
            username = state.webDavUsername,
            password = state.webDavPassword,
            allowInsecureTls = state.webDavAllowInsecureTls,
        )
    }

    private fun navidromeDraftOrNull(
        label: String,
        baseUrl: String,
        username: String,
        password: String,
        allowBlankPassword: Boolean,
    ): NavidromeSourceDraft? {
        if (baseUrl.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.NAVIDROME, "请先填写 Navidrome 服务器地址。")
            return null
        }
        if (username.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.NAVIDROME, "请先填写 Navidrome 用户名。")
            return null
        }
        if (!allowBlankPassword && password.isBlank()) {
            setCreateOrPageMessage(ImportSourceType.NAVIDROME, "请先填写 Navidrome 密码。")
            return null
        }
        return NavidromeSourceDraft(
            label = label,
            baseUrl = baseUrl,
            username = username,
            password = password,
        )
    }

    private fun editingSambaDraftOrNull(editor: RemoteSourceEditorState): SambaSourceDraft? {
        val port = editor.port.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
        if (editor.server.isBlank()) {
            setMessage("请先填写 Samba 服务器地址。")
            return null
        }
        if (editor.port.isNotBlank() && port == null) {
            setMessage("端口号格式不正确。")
            return null
        }
        if (editor.path.isBlank()) {
            setMessage("请填写路径，至少包含共享名，例如 Media 或 Media/Music。")
            return null
        }
        return SambaSourceDraft(
            label = editor.label,
            server = editor.server,
            port = port,
            path = editor.path,
            username = editor.username,
            password = editor.password,
        )
    }

    private fun editingWebDavDraftOrNull(editor: RemoteSourceEditorState): WebDavSourceDraft? {
        if (editor.rootUrl.isBlank()) {
            setMessage("请先填写 WebDAV 根 URL。")
            return null
        }
        if (!editor.keepExistingCredential && editor.password.isBlank()) {
            setMessage("请先填写 WebDAV 密码。")
            return null
        }
        if (editor.password.isNotBlank() && editor.username.isBlank()) {
            setMessage("WebDAV 使用密码时必须填写用户名。")
            return null
        }
        return WebDavSourceDraft(
            label = editor.label,
            rootUrl = editor.rootUrl,
            username = editor.username,
            password = editor.password,
            allowInsecureTls = editor.allowInsecureTls,
        )
    }

    private fun editingNavidromeDraftOrNull(editor: RemoteSourceEditorState): NavidromeSourceDraft? {
        val canReuseStoredPassword = editor.keepExistingCredential && editor.hasStoredCredential
        return navidromeDraftOrNull(
            label = editor.label,
            baseUrl = editor.rootUrl,
            username = editor.username,
            password = editor.password,
            allowBlankPassword = canReuseStoredPassword,
        )
    }

    private fun updateEditingSource(transform: (RemoteSourceEditorState) -> RemoteSourceEditorState) {
        updateState { state ->
            state.copy(editingSource = state.editingSource?.let(transform))
        }
    }

    private fun ImportState.clearCreateDraft(type: ImportSourceType): ImportState {
        return when (type) {
            ImportSourceType.SAMBA -> copy(
                sambaLabel = "",
                sambaServer = "",
                sambaPort = "",
                sambaPath = "",
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
                navidromeUsername = "",
                navidromePassword = "",
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

    private fun setMessage(message: String) {
        updateState { it.copy(message = message) }
    }

    private fun setTestMessage(message: String) {
        updateState { it.copy(testMessage = message) }
    }

    private fun setCreateOrPageMessage(type: ImportSourceType, message: String) {
        if (state.value.creatingSourceType == type) {
            setTestMessage(message)
        } else {
            setMessage(message)
        }
    }
}

fun formatImportScanSummary(summary: ImportScanSummary): String {
    return "发现 ${summary.discoveredAudioFileCount} 个音频文件，" +
        "成功导入 ${summary.importedTrackCount} 首，" +
        "${summary.failedAudioFileCount} 个失败"
}

private fun scanSuccessMessage(prefix: String, summary: ImportScanSummary?): String {
    return summary?.let { "$prefix${formatImportScanSummary(it)}。" } ?: prefix
}
