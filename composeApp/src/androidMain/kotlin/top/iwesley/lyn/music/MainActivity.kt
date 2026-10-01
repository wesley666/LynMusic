package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.ProvideUiLanguage

import top.iwesley.lyn.music.resources.*

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.platform.isAndroidTabletIgnoringDisplaySize
import top.iwesley.lyn.music.platform.androidStableDensityScale
import top.iwesley.lyn.music.platform.AndroidExternalAudioOpenSupport
import top.iwesley.lyn.music.platform.createAndroidRuntimeGraph

class MainActivity : ComponentActivity() {
    private val startupAutoOpenViewModel by viewModels<StartupAutoOpenViewModel>()
    private val externalAudioOpenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appComponent: LynMusicAppComponent? = null
    private var pendingExternalAudioOpenIntent: Intent? = null
    private var externalAudioOpenJob: Job? = null
    private var externalAudioOpenRequestId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        val isTablet = isTabletIgnoringDisplaySize()
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val appComponentResult = runCatching {
            val runtimeGraph = createAndroidRuntimeGraph(this)
            try {
                buildPlayerAppComponent(
                    sharedGraph = runtimeGraph.sharedGraph,
                    playerRuntimeServices = runtimeGraph.playerRuntimeServices,
                )
            } catch (error: Throwable) {
                runtimeGraph.disposeAfterComponentBuildFailure()
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
                throw error
            }
        }
        appComponent = appComponentResult.getOrNull()
        handleExternalAudioOpenIntent(intent)

        setContent {
            ProvideUiLanguage {
                val appComponent = appComponentResult.getOrNull()
                if (appComponent != null) {
                    val appDisplayScalePreset by appComponent.appDisplayScalePreset.collectAsState()
                    ProvideFixedAndroidComposeDensity(appDisplayScalePreset = appDisplayScalePreset) {
                        AndroidMainShellSystemBars(appComponent)
                        App(
                            component = appComponent,
                            startupAutoOpenGate = startupAutoOpenViewModel.gate,
                        )
                    }
                } else {
                    StartupDatabaseErrorScreen(
                        error = appComponentResult.exceptionOrNull(),
                        showDetails = false,
                    )
                }


            }
}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalAudioOpenIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        appComponent?.settingsStore?.dispatch(SettingsIntent.RecheckDesktopLyricsPermission)
    }

    override fun onDestroy() {
        super.onDestroy()
        externalAudioOpenScope.cancel()
    }

    private fun handleExternalAudioOpenIntent(intent: Intent?) {
        if (!AndroidExternalAudioOpenSupport.isExternalAudioOpenIntent(intent)) return
        val component = appComponent
        if (component == null) {
            pendingExternalAudioOpenIntent = intent?.let(::Intent)
            return
        }
        val intentSnapshot = intent?.let(::Intent) ?: pendingExternalAudioOpenIntent ?: return
        pendingExternalAudioOpenIntent = null
        val requestId = ++externalAudioOpenRequestId
        externalAudioOpenJob?.cancel()
        externalAudioOpenJob = externalAudioOpenScope.launch {
            val tracks = AndroidExternalAudioOpenSupport.tracksFromIntent(
                context = this@MainActivity,
                intent = intentSnapshot,
                logger = component.logger,
            )
            if (requestId != externalAudioOpenRequestId) return@launch
            if (tracks.isEmpty()) {
                Toast.makeText(this@MainActivity, top.iwesley.lyn.music.platform.resolveNativeUiString(Res.string.playback_audio_open_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            component.playerStore.dispatch(PlayerIntent.PlayTransientTracks(tracks, 0))
        }
    }
}

internal class StartupAutoOpenViewModel : ViewModel() {
    val gate = StartupAutoOpenGate()
}

@Composable
private fun MainActivity.AndroidMainShellSystemBars(
    appComponent: LynMusicAppComponent,
) {
    val settingsState by appComponent.settingsStore.state.collectAsState()
    val playerState by appComponent.playerStore.state.collectAsState()
    val textPalette = remember(settingsState.selectedTheme, settingsState.textPalettePreferences) {
        resolveAppThemeTextPalette(
            themeId = settingsState.selectedTheme,
            preferences = settingsState.textPalettePreferences,
        )
    }
    val useDarkSystemBarIcons = !playerState.isExpanded && textPalette == AppThemeTextPalette.Black

    SideEffect {
        val transparent = Color.TRANSPARENT
        val systemBarStyle = if (useDarkSystemBarIcons) {
            SystemBarStyle.light(transparent, transparent)
        } else {
            SystemBarStyle.dark(transparent)
        }
        enableEdgeToEdge(
            statusBarStyle = systemBarStyle,
            navigationBarStyle = systemBarStyle,
        )
    }
}

@Composable
private fun ProvideFixedAndroidComposeDensity(
    appDisplayScalePreset: AppDisplayScalePreset,
    content: @Composable () -> Unit,
) {
    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.density, currentDensity.fontScale, appDisplayScalePreset) {
        Density(
            density = effectiveAppDisplayDensity(androidStableDensityScale(currentDensity.density), appDisplayScalePreset),
            fontScale = currentDensity.fontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        content()
    }
}

private fun ComponentActivity.isTabletIgnoringDisplaySize(): Boolean =
    isAndroidTabletIgnoringDisplaySize()
