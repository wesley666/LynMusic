package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.testing.deviceInfoDensityValue
import top.iwesley.lyn.music.testing.deviceInfoDpResolutionValue
import top.iwesley.lyn.music.testing.deviceInfoFontScaleValue

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDeviceInfoFormattingTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `dp resolution uses px width height and density`() = runTest {
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
    fun `dp resolution returns loading when dimensions are missing`() = runTest {
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
    fun `app and system density can yield different dp resolutions`() = runTest {
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
    fun `dp resolution returns unavailable when density is invalid`() = runTest {
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
    fun `density and font scale values trim trailing zeros`() = runTest {
        assertEquals("3 px/dp", deviceInfoDensityValue(3f, loading = false))
        assertEquals("2.75 px/dp", deviceInfoDensityValue(2.75f, loading = false))
        assertEquals("1x", deviceInfoFontScaleValue(1f))
        assertEquals("1.15x", deviceInfoFontScaleValue(1.15f))
    }

    @Test
    fun `density value returns unavailable when missing and not loading`() = runTest {
        assertEquals("不可用", deviceInfoDensityValue(null, loading = false))
    }
}
