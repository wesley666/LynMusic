package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.platform.JvmDeviceInfoGateway
import top.iwesley.lyn.music.platform.JvmDeviceInfoProvider
import top.iwesley.lyn.music.platform.JvmDeviceInfoRaw
import top.iwesley.lyn.music.platform.formatJvmCpuDescription
import top.iwesley.lyn.music.platform.formatJvmResolution

class JvmDeviceInfoGatewayTest {
    @Test
    fun `gateway maps provider data into snapshot`() = runTest {
        val gateway = JvmDeviceInfoGateway(
            provider = FakeJvmDeviceInfoProvider(
                JvmDeviceInfoRaw(
                    systemName = "macOS",
                    systemVersion = "15.3.1",
                    resolutionWidth = 3024,
                    resolutionHeight = 1964,
                    processorIdentifier = "Apple M3",
                    osArch = "aarch64",
                    logicalCoreCount = 8,
                    totalMemoryBytes = 16L * 1024 * 1024 * 1024,
                ),
            ),
        )

        val snapshot = gateway.loadDeviceInfoSnapshot().getOrThrow()

        assertEquals("macOS", snapshot.systemName)
        assertEquals("15.3.1", snapshot.systemVersion)
        assertEquals("3024 × 1964 px", snapshot.resolution)
        assertEquals(3024, snapshot.resolutionWidthPx)
        assertEquals(1964, snapshot.resolutionHeightPx)
        assertNull(snapshot.systemDensityScale)
        assertEquals("Apple M3 · aarch64 · 8 核", snapshot.cpuDescription)
        assertEquals(16L * 1024 * 1024 * 1024, snapshot.totalMemoryBytes)
        assertNull(snapshot.deviceModel)
    }

    @Test
    fun `cpu description falls back to architecture and core count`() {
        assertEquals(
            "arm64 · 10 核",
            formatJvmCpuDescription(
                processorIdentifier = null,
                osArch = "arm64",
                logicalCoreCount = 10,
            ),
        )
    }

    @Test
    fun `resolution formatter returns null when data is incomplete`() {
        assertNull(formatJvmResolution(width = 1920, height = null))
    }
}

private class FakeJvmDeviceInfoProvider(
    private val raw: JvmDeviceInfoRaw,
) : JvmDeviceInfoProvider {
    override fun read(): JvmDeviceInfoRaw = raw
}
