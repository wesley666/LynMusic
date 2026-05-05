package top.iwesley.lyn.music

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Build
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.platform.createAndroidRuntimeGraph
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val isTablet = isTabletIgnoringDisplaySize()
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val appComponentResult = runCatching {
            val runtimeGraph = createAndroidRuntimeGraph(this)
            buildPlayerAppComponent(
                sharedGraph = runtimeGraph.sharedGraph,
                playerRuntimeServices = runtimeGraph.playerRuntimeServices,
            )
        }

        setContent {
            val appComponent = appComponentResult.getOrNull()
            if (appComponent != null) {
                val appDisplayScalePreset by appComponent.appDisplayScalePreset.collectAsState()
                ProvideFixedAndroidComposeDensity(appDisplayScalePreset = appDisplayScalePreset) {
                    App(appComponent)
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
