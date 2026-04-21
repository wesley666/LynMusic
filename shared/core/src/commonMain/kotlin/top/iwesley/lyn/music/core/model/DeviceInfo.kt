package top.iwesley.lyn.music.core.model

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
)

interface DeviceInfoGateway {
    suspend fun loadDeviceInfoSnapshot(): Result<DeviceInfoSnapshot>
}

object UnsupportedDeviceInfoGateway : DeviceInfoGateway {
    private val error = IllegalStateException("当前平台暂不支持关于本机。")

    override suspend fun loadDeviceInfoSnapshot(): Result<DeviceInfoSnapshot> = Result.failure(error)
}
