package top.iwesley.lyn.music.platform

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.DeviceInfoGateway
import top.iwesley.lyn.music.core.model.DeviceInfoSnapshot

fun createAndroidDeviceInfoGateway(
    activity: ComponentActivity,
): DeviceInfoGateway = AndroidDeviceInfoGateway(activity)

private class AndroidDeviceInfoGateway(
    private val activity: ComponentActivity,
) : DeviceInfoGateway {
    override suspend fun loadDeviceInfoSnapshot(): Result<DeviceInfoSnapshot> = withContext(Dispatchers.Default) {
        runCatching {
            val (resolutionWidthPx, resolutionHeightPx) = androidResolutionPx(activity)
            DeviceInfoSnapshot(
                systemName = "Android",
                systemVersion = androidSystemVersion(),
                resolution = formatResolution(resolutionWidthPx, resolutionHeightPx),
                resolutionWidthPx = resolutionWidthPx,
                resolutionHeightPx = resolutionHeightPx,
                systemDensityScale = androidSystemDensityScale(activity),
                cpuDescription = androidCpuDescription(),
                totalMemoryBytes = androidTotalMemoryBytes(activity.applicationContext),
                deviceModel = androidDeviceModel(),
            )
        }
    }
}

private fun androidSystemVersion(): String {
    val release = Build.VERSION.RELEASE.orEmpty().trim()
    return if (release.isNotBlank()) {
        "$release (SDK ${Build.VERSION.SDK_INT})"
    } else {
        "SDK ${Build.VERSION.SDK_INT}"
    }
}

private fun androidResolutionPx(activity: ComponentActivity): Pair<Int?, Int?> {
    val displayMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display?.mode
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.mode
    } else {
        null
    }
    val width = displayMode?.physicalWidth?.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels.takeIf { it > 0 }
    val height = displayMode?.physicalHeight?.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels.takeIf { it > 0 }
    return width to height
}

private fun androidSystemDensityScale(activity: ComponentActivity): Float? {
    val configurationDensityDpi = activity.resources.configuration.densityDpi.takeIf { it > 0 }
    if (configurationDensityDpi != null) {
        return configurationDensityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
    }
    return activity.resources.displayMetrics.density.takeIf { it.isFinite() && it > 0f }
}

private fun androidCpuDescription(): String? {
    val model = readAndroidBuildField("SOC_MODEL")
        ?: normalizeAndroidValue(Build.HARDWARE)
        ?: normalizeAndroidValue(Build.BOARD)
    val abi = Build.SUPPORTED_ABIS.firstOrNull()?.let(::normalizeAndroidValue)
    val logicalCores = Runtime.getRuntime().availableProcessors().takeIf { it > 0 }?.let { "$it 核" }
    return listOfNotNull(model, abi, logicalCores).joinToString(" · ").takeIf { it.isNotBlank() }
}

private fun androidTotalMemoryBytes(context: Context): Long? {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return memoryInfo.totalMem.takeIf { it > 0L }
}

private fun androidDeviceModel(): String? {
    val manufacturer = normalizeAndroidValue(Build.MANUFACTURER)
    val model = normalizeAndroidValue(Build.MODEL)
    return when {
        manufacturer == null -> model
        model == null -> manufacturer
        model.startsWith(manufacturer, ignoreCase = true) -> model
        else -> "$manufacturer $model"
    }
}

private fun readAndroidBuildField(name: String): String? {
    return runCatching {
        Build::class.java.getField(name).get(null) as? String
    }.getOrNull()?.let(::normalizeAndroidValue)
}

private fun normalizeAndroidValue(value: String?): String? {
    val normalized = value.orEmpty().trim()
    return normalized.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
}

private fun formatResolution(
    width: Int?,
    height: Int?,
): String? {
    val resolvedWidth = width?.takeIf { it > 0 } ?: return null
    val resolvedHeight = height?.takeIf { it > 0 } ?: return null
    return "$resolvedWidth × $resolvedHeight px"
}
