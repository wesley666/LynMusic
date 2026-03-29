package top.iwesley.lyn.music.feature.importing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.ImportSourceRepository

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
    val isWorking: Boolean = false,
    val message: String? = null,
)

sealed interface ImportIntent {
    data object ImportLocalFolder : ImportIntent
    data object AddSambaSource : ImportIntent
    data object AddWebDavSource : ImportIntent
    data object AddNavidromeSource : ImportIntent
    data class RescanSource(val sourceId: String) : ImportIntent
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
    data object ClearMessage : ImportIntent
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
                updateState { it.copy(sources = sources) }
            }
        }
    }

    override suspend fun handleIntent(intent: ImportIntent) {
        when (intent) {
            ImportIntent.ImportLocalFolder -> runImport {
                repository.importLocalFolder()
                    .onSuccess { setMessage("本地音乐源已导入。") }
                    .onFailure { setMessage("导入本地文件夹失败: ${it.message}") }
            }

            ImportIntent.AddSambaSource -> {
                val state = state.value
                val port = state.sambaPort.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
                if (state.sambaServer.isBlank()) {
                    setMessage("请先填写 Samba 服务器地址。")
                    return
                }
                if (state.sambaPort.isNotBlank() && port == null) {
                    setMessage("端口号格式不正确。")
                    return
                }
                if (state.sambaPath.isBlank()) {
                    setMessage("请填写路径，至少包含共享名，例如 Media 或 Media/Music。")
                    return
                }
                runImport {
                    repository.addSambaSource(
                        SambaSourceDraft(
                            label = state.sambaLabel,
                            server = state.sambaServer,
                            port = port,
                            path = state.sambaPath,
                            username = state.sambaUsername,
                            password = state.sambaPassword,
                        ),
                    ).onSuccess {
                        updateState {
                            it.copy(
                                sambaLabel = "",
                                sambaServer = "",
                                sambaPort = "",
                                sambaPath = "",
                                sambaUsername = "",
                                sambaPassword = "",
                            )
                        }
                        setMessage("Samba 音乐源已导入。")
                    }.onFailure {
                        setMessage("Samba 导入失败: ${it.message}")
                    }
                }
            }

            ImportIntent.AddWebDavSource -> {
                val state = state.value
                if (state.webDavRootUrl.isBlank()) {
                    setMessage("请先填写 WebDAV 根 URL。")
                    return
                }
                if (state.webDavPassword.isNotBlank() && state.webDavUsername.isBlank()) {
                    setMessage("WebDAV 使用密码时必须填写用户名。")
                    return
                }
                runImport {
                    repository.addWebDavSource(
                        WebDavSourceDraft(
                            label = state.webDavLabel,
                            rootUrl = state.webDavRootUrl,
                            username = state.webDavUsername,
                            password = state.webDavPassword,
                            allowInsecureTls = state.webDavAllowInsecureTls,
                        ),
                    ).onSuccess {
                        updateState {
                            it.copy(
                                webDavLabel = "",
                                webDavRootUrl = "",
                                webDavUsername = "",
                                webDavPassword = "",
                                webDavAllowInsecureTls = false,
                            )
                        }
                        setMessage("WebDAV 音乐源已导入。")
                    }.onFailure {
                        setMessage("WebDAV 导入失败: ${it.message}")
                    }
                }
            }

            ImportIntent.AddNavidromeSource -> {
                val state = state.value
                if (state.navidromeBaseUrl.isBlank()) {
                    setMessage("请先填写 Navidrome 服务器地址。")
                    return
                }
                if (state.navidromeUsername.isBlank()) {
                    setMessage("请先填写 Navidrome 用户名。")
                    return
                }
                if (state.navidromePassword.isBlank()) {
                    setMessage("请先填写 Navidrome 密码。")
                    return
                }
                runImport {
                    repository.addNavidromeSource(
                        NavidromeSourceDraft(
                            label = state.navidromeLabel,
                            baseUrl = state.navidromeBaseUrl,
                            username = state.navidromeUsername,
                            password = state.navidromePassword,
                        ),
                    ).onSuccess {
                        updateState {
                            it.copy(
                                navidromeLabel = "",
                                navidromeBaseUrl = "",
                                navidromeUsername = "",
                                navidromePassword = "",
                            )
                        }
                        setMessage("Navidrome 音乐源已导入。")
                    }.onFailure {
                        setMessage("Navidrome 导入失败: ${it.message}")
                    }
                }
            }

            is ImportIntent.RescanSource -> runImport {
                repository.rescanSource(intent.sourceId)
                    .onSuccess { setMessage("音乐源已重新扫描。") }
                    .onFailure { setMessage("重新扫描失败: ${it.message}") }
            }

            is ImportIntent.DeleteSource -> runImport {
                repository.deleteSource(intent.sourceId)
                    .onSuccess { setMessage("音乐源已删除。") }
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
            ImportIntent.ClearMessage -> updateState { it.copy(message = null) }
        }
    }

    private suspend fun runImport(block: suspend () -> Unit) {
        updateState { it.copy(isWorking = true) }
        runCatching { block() }
        updateState { it.copy(isWorking = false) }
    }

    private fun setMessage(message: String) {
        updateState { it.copy(message = message) }
    }
}
