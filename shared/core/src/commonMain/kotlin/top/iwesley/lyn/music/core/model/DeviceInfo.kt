package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*

data class DeviceInfoSnapshot(
    val systemName: String,
    val systemVersion: String,
    val resolution: String? = null,
    val resolutionWidthPx: Int? = null,
    val resolutionHeightPx: Int? = null,
    val systemDensityScale: Float? = null,
    val cpuDescription: String? = null,
    val totalMemoryBytes: Long? = null,
    val deviceModel: String? = null,
    val logicalCoreCount: Int? = null,
) {
    /** Cached hardware data stays independent of the language selected at display time. */
    val cpuDescriptionText: UiText?
        get() = listOfNotNull(
            cpuDescription?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) },
            logicalCoreCount?.takeIf { it > 0 }?.let { uiPlural(Res.plurals.device_cpu_core_count, (it).toInt(), it) },
        ).takeIf { it.isNotEmpty() }?.let { UiText.Joined(it) }
}

interface DeviceInfoGateway {
    suspend fun loadDeviceInfoSnapshot(): Result<DeviceInfoSnapshot>
}

object UnsupportedDeviceInfoGateway : DeviceInfoGateway {
    private val error = UiTextException(uiText(Res.string.device_info_platform_unsupported))

    override suspend fun loadDeviceInfoSnapshot(): Result<DeviceInfoSnapshot> = Result.failure(error)
}
