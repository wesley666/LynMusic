package top.iwesley.lyn.music.platform

import com.sun.management.OperatingSystemMXBean
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.lang.management.ManagementFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.DeviceInfoGateway
import top.iwesley.lyn.music.core.model.DeviceInfoSnapshot

fun createJvmDeviceInfoGateway(): DeviceInfoGateway = JvmDeviceInfoGateway()

internal class JvmDeviceInfoGateway(
    private val provider: JvmDeviceInfoProvider = DefaultJvmDeviceInfoProvider(),
) : DeviceInfoGateway {
    override suspend fun loadDeviceInfoSnapshot(): Result<DeviceInfoSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            jvmDeviceInfoSnapshot(provider.read())
        }
    }
}

internal data class JvmDeviceInfoRaw(
    val systemName: String,
    val systemVersion: String,
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null,
    val processorIdentifier: String? = null,
    val osArch: String? = null,
    val logicalCoreCount: Int? = null,
    val totalMemoryBytes: Long? = null,
)

internal interface JvmDeviceInfoProvider {
    fun read(): JvmDeviceInfoRaw
}

private class DefaultJvmDeviceInfoProvider : JvmDeviceInfoProvider {
    override fun read(): JvmDeviceInfoRaw {
        val (resolutionWidth, resolutionHeight) = currentJvmResolution() ?: (null to null)
        return JvmDeviceInfoRaw(
            systemName = System.getProperty("os.name").orEmpty(),
            systemVersion = System.getProperty("os.version").orEmpty(),
            resolutionWidth = resolutionWidth,
            resolutionHeight = resolutionHeight,
            processorIdentifier = System.getenv("PROCESSOR_IDENTIFIER"),
            osArch = System.getProperty("os.arch"),
            logicalCoreCount = Runtime.getRuntime().availableProcessors().takeIf { it > 0 },
            totalMemoryBytes = currentJvmTotalMemoryBytes(),
        )
    }
}

internal fun jvmDeviceInfoSnapshot(raw: JvmDeviceInfoRaw): DeviceInfoSnapshot {
    return DeviceInfoSnapshot(
        systemName = raw.systemName,
        systemVersion = raw.systemVersion,
        resolution = formatJvmResolution(raw.resolutionWidth, raw.resolutionHeight),
        resolutionWidthPx = raw.resolutionWidth?.takeIf { it > 0 },
        resolutionHeightPx = raw.resolutionHeight?.takeIf { it > 0 },
        cpuDescription = formatJvmCpuDescription(
            processorIdentifier = raw.processorIdentifier,
            osArch = raw.osArch,
        ),
        logicalCoreCount = raw.logicalCoreCount?.takeIf { it > 0 },
        totalMemoryBytes = raw.totalMemoryBytes?.takeIf { it > 0L },
    )
}

internal fun formatJvmCpuDescription(
    processorIdentifier: String?,
    osArch: String?,
): String? {
    val model = processorIdentifier?.trim()?.takeIf { it.isNotBlank() }
    val arch = osArch?.trim()?.takeIf { it.isNotBlank() }
    return when {
        model != null && arch != null && !model.contains(arch, ignoreCase = true) ->
            listOf(model, arch).joinToString(" · ")

        else -> model ?: arch
    }?.takeIf { it.isNotBlank() }
}

internal fun formatJvmResolution(
    width: Int?,
    height: Int?,
): String? {
    val resolvedWidth = width?.takeIf { it > 0 } ?: return null
    val resolvedHeight = height?.takeIf { it > 0 } ?: return null
    return "$resolvedWidth × $resolvedHeight px"
}

private fun currentJvmResolution(): Pair<Int, Int>? {
    return runCatching {
        val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
        mode.width to mode.height
    }.recoverCatching {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        screenSize.width to screenSize.height
    }.getOrNull()
}

private fun currentJvmTotalMemoryBytes(): Long? {
    val bean = ManagementFactory.getOperatingSystemMXBean()
    return (bean as? OperatingSystemMXBean)?.totalMemorySize?.takeIf { it > 0L }
}
