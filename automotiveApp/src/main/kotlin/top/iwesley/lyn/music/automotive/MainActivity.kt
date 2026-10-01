package top.iwesley.lyn.music.automotive

import top.iwesley.lyn.music.core.model.ProvideUiLanguage

import top.iwesley.lyn.music.resources.*

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import top.iwesley.lyn.music.ANDROID_AUTOMOTIVE_PLATFORM_NAME
import kotlin.math.min
import top.iwesley.lyn.music.App
import top.iwesley.lyn.music.LynMusicAppComponent
import top.iwesley.lyn.music.StartupAutoOpenGate
import top.iwesley.lyn.music.StartupDatabaseErrorScreen
import top.iwesley.lyn.music.buildPlayerAppComponent
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.platform.AndroidExternalAudioOpenSupport
import top.iwesley.lyn.music.platform.createAndroidRuntimeGraph
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val startupAutoOpenViewModel by viewModels<StartupAutoOpenViewModel>()
    private val externalAudioOpenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appComponent: LynMusicAppComponent? = null
    private var pendingExternalAudioOpenIntent: Intent? = null
    private var externalAudioOpenJob: Job? = null
    private var externalAudioOpenRequestId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        //enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        val appComponentResult = runCatching {
            val runtimeGraph = createAndroidRuntimeGraph(this, platformName = ANDROID_AUTOMOTIVE_PLATFORM_NAME)
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

private fun ComponentActivity.isTabletIgnoringDisplaySize(): Boolean {
    val (widthPx, heightPx) = currentDisplayPx()
    val stableDensity = androidStableDensityScale(resources.displayMetrics.density)
    if (widthPx == null || heightPx == null || stableDensity <= 0f) return false
    val smallestWidthDp = min(widthPx, heightPx) / stableDensity
    return smallestWidthDp >= 600f
}

private fun ComponentActivity.currentDisplayPx(): Pair<Int?, Int?> {
    val displayMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.mode
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.mode
    }
    val width = displayMode?.physicalWidth?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.takeIf { it > 0 }
    val height = displayMode?.physicalHeight?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels.takeIf { it > 0 }
    return width to height
}

private fun androidStableDensityScale(fallbackDensity: Float): Float {
    val fallbackDpi = (fallbackDensity.takeIf { it > 0f } ?: 1f) * DisplayMetrics.DENSITY_DEFAULT
    val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        DisplayMetrics.DENSITY_DEVICE_STABLE
    } else {
        fallbackDpi.roundToInt()
    }.takeIf { it > 0 } ?: fallbackDpi.roundToInt()
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}
