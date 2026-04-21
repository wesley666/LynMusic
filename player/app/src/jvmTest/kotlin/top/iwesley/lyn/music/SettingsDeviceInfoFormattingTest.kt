package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDeviceInfoFormattingTest {
    @Test
    fun `dp resolution uses px width height and density`() {
        assertEquals(
            "360 × 800 dp",
            deviceInfoDpResolutionValue(
                widthPx = 1080,
                heightPx = 2400,
                density = 3f,
                loading = false,
            ),
        )
    }

    @Test
    fun `dp resolution returns loading when dimensions are missing`() {
        assertEquals(
            "正在读取...",
            deviceInfoDpResolutionValue(
                widthPx = null,
                heightPx = 2400,
                density = 3f,
                loading = true,
            ),
        )
    }

    @Test
    fun `app and system density can yield different dp resolutions`() {
        assertEquals(
            "360 × 800 dp",
            deviceInfoDpResolutionValue(
                widthPx = 1080,
                heightPx = 2400,
                density = 3f,
                loading = false,
            ),
        )
        assertEquals(
            "432 × 960 dp",
            deviceInfoDpResolutionValue(
                widthPx = 1080,
                heightPx = 2400,
                density = 2.5f,
                loading = false,
            ),
        )
    }

    @Test
    fun `dp resolution returns unavailable when density is invalid`() {
        assertEquals(
            "不可用",
            deviceInfoDpResolutionValue(
                widthPx = 1080,
                heightPx = 2400,
                density = 0f,
                loading = false,
            ),
        )
    }

    @Test
    fun `density and font scale values trim trailing zeros`() {
        assertEquals("3 px/dp", deviceInfoDensityValue(3f, loading = false))
        assertEquals("2.75 px/dp", deviceInfoDensityValue(2.75f, loading = false))
        assertEquals("1x", deviceInfoFontScaleValue(1f))
        assertEquals("1.15x", deviceInfoFontScaleValue(1.15f))
    }

    @Test
    fun `density value returns unavailable when missing and not loading`() {
        assertEquals("不可用", deviceInfoDensityValue(null, loading = false))
    }
}
