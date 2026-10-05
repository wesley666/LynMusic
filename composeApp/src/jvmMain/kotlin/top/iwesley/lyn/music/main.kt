package top.iwesley.lyn.music

import androidx.compose.runtime.Composable

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.*

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Desktop
import java.awt.Dimension
import java.awt.desktop.AppReopenedListener
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.iwesley.lyn.music.platform.JvmAppInstanceLock
import top.iwesley.lyn.music.platform.JvmDataLocationManager
import top.iwesley.lyn.music.platform.JvmDataLocationProgress
import top.iwesley.lyn.music.platform.createJvmAppComponent
import top.iwesley.lyn.music.platform.initializeJvmAppLanguage
import top.iwesley.lyn.music.platform.isJvmWindowsOs

@OptIn(ExperimentalComposeUiApi::class)
suspend fun main() {
    initializeJvmAppLanguage()
    installJvmUncaughtExceptionHandler()
    val osName = System.getProperty("os.name").orEmpty()
    val instanceLock = if (isJvmWindowsOs(osName)) {
        runCatching { JvmAppInstanceLock.tryAcquire() }.getOrElse { error ->
            showDesktopStartupMessage(uiText(Res.string.startup_failed_with_details, error.message ?: error))
            return
        } ?: run {
            showDesktopStartupMessage(uiText(Res.string.startup_already_running))
            return
        }
    } else {
        null
    }
    try {
        application {
            ProvideUiLanguage {
                val dataLocationManager = remember { JvmDataLocationManager() }
                val startupAutoOpenGate = remember { StartupAutoOpenGate() }
                val applicationScope = rememberCoroutineScope()
                var startupAttempt by remember { mutableIntStateOf(0) }
                var requiresDataLocationOperation by remember { mutableStateOf(false) }
                var startupState by remember {
                    mutableStateOf(initialJvmDesktopStartupState())
                }
                val restartStartup = {
                    requiresDataLocationOperation = false
                    startupState = initialJvmDesktopStartupState()
                    startupAttempt += 1
                }
                LaunchedEffect(startupAttempt) {
                    val initializedStartup = initializeJvmDesktopStartup(dataLocationManager)
                    startupState = initializedStartup.state
                    requiresDataLocationOperation = initializedStartup.requiresDataLocationOperation
                }
                LaunchedEffect(startupAttempt, requiresDataLocationOperation) {
                    if (!requiresDataLocationOperation) return@LaunchedEffect
                    startupState = JvmDesktopStartupState.Preparing(
                        JvmDataLocationProgress(uiText(Res.string.startup_reading_location)),
                    )
                    val progressUpdates = Channel<JvmDataLocationProgress>(Channel.CONFLATED)
                    val progressJob = launch {
                        for (progress in progressUpdates) {
                            startupState = JvmDesktopStartupState.Preparing(progress)
                        }
                    }
                    val locationResult = dataLocationManager.applyPendingChange { progress ->
                        progressUpdates.trySend(progress)
                        Unit
                    }.onFailure { error ->
                        logJvmStartupFailure(stage = "data-location", error = error)
                    }
                    progressUpdates.close()
                    progressJob.join()
                    startupState = locationResult.fold(
                        onSuccess = {
                            withContext(Dispatchers.IO) {
                                createJvmDesktopComponentState(dataLocationManager)
                            }
                        },
                        onFailure = { error ->
                            JvmDesktopStartupState.DataLocationFailed(
                                error = error,
                                canCancelChange = withContext(Dispatchers.IO) {
                                    dataLocationManager.hasPendingChangeSafely()
                                },
                            )
                        },
                    )
                    requiresDataLocationOperation = false
                }

                val latestStartupState by rememberUpdatedState(startupState)
                val componentForDisposal = (startupState as? JvmDesktopStartupState.Ready)?.component
                DisposableEffect(componentForDisposal) {
                    onDispose { componentForDisposal?.dispose() }
                }
                val desktopWindowChrome = remember {
                    defaultDesktopWindowChrome(System.getProperty("os.name").orEmpty())
                }
                val windowState = rememberWindowState(
                    size = DpSize(1440.dp, 900.dp),
                )
                SwingWindow(
                    onCloseRequest = closeRequest@{
                        val currentStartupState = latestStartupState
                        if (!shouldAllowDesktopWindowClose(currentStartupState)) {
                            return@closeRequest
                        }
                        val currentComponent = (currentStartupState as? JvmDesktopStartupState.Ready)?.component
                        val settingsStore = currentComponent?.settingsStore
                        val supportsMacOsWindowCloseBehavior =
                            currentComponent?.platform?.capabilities?.supportsMacOsWindowCloseBehavior == true
                        val persistenceCompleted = if (supportsMacOsWindowCloseBehavior && settingsStore != null) {
                            runBlocking {
                                withTimeoutOrNull(WINDOW_CLOSE_PREFERENCE_FLUSH_TIMEOUT_MILLIS) {
                                    settingsStore.awaitMinimizeWindowOnClosePersistence()
                                    true
                                } ?: false
                            }
                        } else {
                            true
                        }
                        val minimizeWindowOnClose = resolveMinimizeWindowOnClosePreference(
                            persistenceCompleted = persistenceCompleted,
                            currentValue = settingsStore?.state?.value?.minimizeWindowOnClose == true,
                            persistedValue = settingsStore?.persistedMinimizeWindowOnClose == true,
                        )
                        val shouldMinimize = shouldMinimizeDesktopWindowOnClose(
                            supportsMacOsWindowCloseBehavior = supportsMacOsWindowCloseBehavior,
                            minimizeWindowOnClose = minimizeWindowOnClose,
                        )
                        if (shouldMinimize) {
                            windowState.isMinimized = true
                        } else {
                            try {
                                currentComponent?.dispose()
                            } finally {
                                exitApplication()
                            }
                        }
                    },
                    title = "LynMusic",
                    state = windowState,
                    icon = painterResource("desktop-icon.png"),
                    init = { composeWindow ->
                        composeWindow.minimumSize = Dimension(1200, 720)
                        applyDesktopWindowChrome(composeWindow, desktopWindowChrome)
                    },
                ) {
                    if (isJvmMacOs(osName) && Desktop.isDesktopSupported()) {
                        DisposableEffect(window, windowState) {
                            val desktop = Desktop.getDesktop()
                            val reopenListener = if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
                                AppReopenedListener {
                                    SwingUtilities.invokeLater {
                                        if (window.isDisplayable) {
                                            windowState.isMinimized = false
                                            window.isMinimized = false
                                            window.isVisible = true
                                            window.toFront()
                                            window.requestFocus()
                                        }
                                    }
                                }.also { desktop.addAppEventListener(it) }
                            } else {
                                null
                            }
                            onDispose {
                                if (reopenListener != null) {
                                    desktop.removeAppEventListener(reopenListener)
                                }
                            }
                        }
                    }
                    when (val current = startupState) {
                        JvmDesktopStartupState.Starting ->
                            JvmDesktopStartingScreen()

                        is JvmDesktopStartupState.Preparing ->
                            StartupDataLocationProgressScreen(
                                message = current.progress.message.displayText(),
                                fraction = current.progress.fraction,
                            )

                        is JvmDesktopStartupState.DataLocationFailed ->
                            StartupDataLocationErrorScreen(
                                error = current.error,
                                canCancelChange = current.canCancelChange,
                                onRetry = retry@{
                                    if (startupState !is JvmDesktopStartupState.DataLocationFailed) return@retry
                                    restartStartup()
                                },
                                onCancelChange = cancel@{
                                    if (startupState !is JvmDesktopStartupState.DataLocationFailed) return@cancel
                                    startupState = JvmDesktopStartupState.Preparing(
                                        JvmDataLocationProgress(uiText(Res.string.startup_canceling_location_change)),
                                    )
                                    applicationScope.launch {
                                        dataLocationManager.cancelPendingChange().fold(
                                            onSuccess = {
                                                restartStartup()
                                            },
                                            onFailure = { error ->
                                                startupState = JvmDesktopStartupState.DataLocationFailed(
                                                    error = error,
                                                    canCancelChange = withContext(Dispatchers.IO) {
                                                        dataLocationManager.hasPendingChangeSafely()
                                                    },
                                                )
                                            },
                                        )
                                    }
                                },
                                onExitApplication = { exitApplication() },
                            )

                        is JvmDesktopStartupState.Ready ->
                            App(
                                component = current.component,
                                startupAutoOpenGate = startupAutoOpenGate,
                                desktopWindowChrome = desktopWindowChrome,
                                onExitApplicationRequest = {
                                    try {
                                        current.component.dispose()
                                    } finally {
                                        exitApplication()
                                    }
                                },
                                startupWarning = current.startupWarning,
                            )

                        is JvmDesktopStartupState.ComponentFailed ->
                            StartupDatabaseErrorScreen(
                                error = current.error,
                                showDetails = true,
                            )
                    }
                }


            }
}
    } finally {
        instanceLock?.close()
    }
}

private data class JvmDesktopStartupInitialization(
    val requiresDataLocationOperation: Boolean,
    val state: JvmDesktopStartupState,
)

private suspend fun initializeJvmDesktopStartup(
    dataLocationManager: JvmDataLocationManager,
): JvmDesktopStartupInitialization = withContext(Dispatchers.IO) {
    runCatching {
        dataLocationManager.requiresStartupDataLocationOperation()
    }.fold(
        onSuccess = { requiresDataLocationOperation ->
            JvmDesktopStartupInitialization(
                requiresDataLocationOperation = requiresDataLocationOperation,
                state = resolveJvmDesktopStartupAfterLocationCheck(requiresDataLocationOperation) {
                    createJvmDesktopComponentState(dataLocationManager)
                },
            )
        },
        onFailure = { error ->
            logJvmStartupFailure(stage = "data-location-config", error = error)
            JvmDesktopStartupInitialization(
                requiresDataLocationOperation = false,
                state = JvmDesktopStartupState.DataLocationFailed(
                    error = error,
                    canCancelChange = false,
                ),
            )
        },
    )
}

private fun JvmDataLocationManager.hasPendingChangeSafely(): Boolean =
    runCatching { hasPendingChange() }.getOrDefault(false)

private fun createJvmDesktopComponentState(
    dataLocationManager: JvmDataLocationManager,
): JvmDesktopStartupState = runCatching {
    createJvmAppComponent(dataLocationManager)
}.fold(
    onSuccess = { component ->
        JvmDesktopStartupState.Ready(
            component = component,
            startupWarning = dataLocationManager.cleanupWarning,
        )
    },
    onFailure = { error ->
        logJvmStartupFailure(stage = "app-component", error = error)
        JvmDesktopStartupState.ComponentFailed(error)
    },
)

private suspend fun showDesktopStartupMessage(description: UiText) {
    val message = resolveUiText(description, AppLanguageRuntime.effectiveLanguage.value)
    runCatching {
        JOptionPane.showMessageDialog(null, message, "LynMusic", JOptionPane.INFORMATION_MESSAGE)
    }.onFailure {
        System.err.println(message)
    }
}

internal sealed interface JvmDesktopStartupState {
    data object Starting : JvmDesktopStartupState
    data class Preparing(val progress: JvmDataLocationProgress) : JvmDesktopStartupState
    data class DataLocationFailed(
        val error: Throwable,
        val canCancelChange: Boolean,
    ) : JvmDesktopStartupState
    data class Ready(
        val component: LynMusicAppComponent,
        val startupWarning: top.iwesley.lyn.music.core.model.UiText?,
    ) : JvmDesktopStartupState
    data class ComponentFailed(val error: Throwable) : JvmDesktopStartupState
}

internal fun initialJvmDesktopStartupState(): JvmDesktopStartupState =
    JvmDesktopStartupState.Starting

internal fun resolveJvmDesktopStartupAfterLocationCheck(
    requiresDataLocationOperation: Boolean,
    createComponentState: () -> JvmDesktopStartupState,
): JvmDesktopStartupState = if (requiresDataLocationOperation) {
    JvmDesktopStartupState.Preparing(
        JvmDataLocationProgress(uiText(Res.string.startup_reading_location)),
    )
} else {
    createComponentState()
}

private const val WINDOW_CLOSE_PREFERENCE_FLUSH_TIMEOUT_MILLIS = 2_000L

internal fun resolveMinimizeWindowOnClosePreference(
    persistenceCompleted: Boolean,
    currentValue: Boolean,
    persistedValue: Boolean,
): Boolean {
    return if (persistenceCompleted) currentValue else persistedValue
}

internal fun shouldMinimizeDesktopWindowOnClose(
    supportsMacOsWindowCloseBehavior: Boolean,
    minimizeWindowOnClose: Boolean,
): Boolean {
    return supportsMacOsWindowCloseBehavior && minimizeWindowOnClose
}

internal fun shouldAllowDesktopWindowClose(startupState: JvmDesktopStartupState): Boolean = when (startupState) {
    JvmDesktopStartupState.Starting,
    is JvmDesktopStartupState.Preparing,
    -> false

    else -> true
}

internal fun defaultDesktopWindowChrome(osName: String): DesktopWindowChrome {
    return if (isJvmMacOs(osName)) {
        DesktopWindowChrome(
            immersiveTitleBarEnabled = true,
            topInset = 40.dp,
            dragRegionHeight = 40.dp,
        )
    } else {
        DesktopWindowChrome()
    }
}

internal fun isJvmMacOs(osName: String): Boolean {
    return osName.contains("mac", ignoreCase = true)
}

internal fun macOsImmersiveAwtClientProperties(): Map<String, Any> {
    return linkedMapOf(
        "apple.awt.fullWindowContent" to true,
        "apple.awt.transparentTitleBar" to true,
        "apple.awt.windowTitleVisible" to false,
    )
}

internal fun applyDesktopWindowChrome(
    window: java.awt.Window,
    desktopWindowChrome: DesktopWindowChrome,
) {
    if (!desktopWindowChrome.immersiveTitleBarEnabled || window !is javax.swing.RootPaneContainer) return
    macOsImmersiveAwtClientProperties().forEach { (key, value) ->
        window.rootPane.putClientProperty(key, value)
    }
}
