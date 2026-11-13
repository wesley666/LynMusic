package top.iwesley.lyn.music.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset

class AndroidDisplayDensityTest {
    @Test
    fun compactOn1080PixelPhoneUsesCurrentSystemDensity() {
        val density = androidAppDisplayDensity(
            systemDensity = 3f,
            preset = AppDisplayScalePreset.Compact,
        )

        assertEquals(2.7f, density, 0.0001f)
        assertEquals(400f, 1080f / density, 0.001f)
        assertEquals(880f, 2376f / density, 0.001f)
    }

    @Test
    fun compactOn1440PixelPhoneFollowsSystemDensityWithoutStableDensityCap() {
        val density = androidAppDisplayDensity(
            systemDensity = 4.5f,
            preset = AppDisplayScalePreset.Compact,
        )

        assertEquals(4.05f, density, 0.0001f)
        assertEquals(355.55556f, 1440f / density, 0.001f)
        assertEquals(782.2222f, 3168f / density, 0.001f)
    }

    @Test
    fun everyPresetScalesCurrentSystemDensity() {
        val expectedDensities = listOf(
            Triple(3f, AppDisplayScalePreset.ExtraCompact, 2.4f),
            Triple(3f, AppDisplayScalePreset.Compact, 2.7f),
            Triple(3f, AppDisplayScalePreset.Default, 3f),
            Triple(3f, AppDisplayScalePreset.Large, 3.3f),
            Triple(3f, AppDisplayScalePreset.ExtraLarge, 3.6f),
            Triple(4.5f, AppDisplayScalePreset.ExtraCompact, 3.6f),
            Triple(4.5f, AppDisplayScalePreset.Compact, 4.05f),
            Triple(4.5f, AppDisplayScalePreset.Default, 4.5f),
            Triple(4.5f, AppDisplayScalePreset.Large, 4.95f),
            Triple(4.5f, AppDisplayScalePreset.ExtraLarge, 5.4f),
        )

        for ((systemDensity, preset, expectedDensity) in expectedDensities) {
            assertEquals(
                expectedDensity,
                androidAppDisplayDensity(systemDensity, preset),
                0.0001f,
                "systemDensity=$systemDensity, preset=$preset",
            )
        }
    }

    @Test
    fun defaultPresetPreservesCurrentSystemDensityWithoutDpiRounding() {
        for (systemDensity in listOf(0.75f, 1f, 1.25f, 2.625f, 3.123f, 4f, 4.5f)) {
            assertEquals(
                systemDensity,
                androidAppDisplayDensity(systemDensity, AppDisplayScalePreset.Default),
            )
        }
    }

    @Test
    fun invalidSystemDensityUsesUnitBaselineBeforeApplyingPreset() {
        val expectedDensities = mapOf(
            AppDisplayScalePreset.ExtraCompact to 0.8f,
            AppDisplayScalePreset.Compact to 0.9f,
            AppDisplayScalePreset.Default to 1f,
            AppDisplayScalePreset.Large to 1.1f,
            AppDisplayScalePreset.ExtraLarge to 1.2f,
        )

        for (systemDensity in invalidDensities) {
            for ((preset, expectedDensity) in expectedDensities) {
                assertEquals(
                    expectedDensity,
                    androidAppDisplayDensity(systemDensity, preset),
                )
            }
        }
    }

    private val invalidDensities = listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
}
