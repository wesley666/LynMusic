package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppDisplayScalePresetTest {
    @Test
    fun fivePresetsStayOrderedFromEightyToOneHundredTwentyPercent() {
        assertEquals(
            listOf("ExtraCompact", "Compact", "Default", "Large", "ExtraLarge"),
            AppDisplayScalePreset.entries.map { it.name },
        )
        assertEquals(
            listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f),
            AppDisplayScalePreset.entries.map { it.scale },
        )
    }

    @Test
    fun legacyStoredNamesKeepTheirOriginalPresetsAndScales() {
        val legacyPresets = mapOf(
            "Compact" to 0.9f,
            "Default" to 1f,
            "Large" to 1.1f,
        )
        for ((name, scale) in legacyPresets) {
            val preset = appDisplayScalePresetOrDefault(name)
            assertEquals(name, preset.name)
            assertEquals(scale, preset.scale)
        }
    }

    @Test
    fun everyPresetRoundTripsThroughItsStoredName() {
        for (preset in AppDisplayScalePreset.entries) {
            assertEquals(preset, appDisplayScalePresetOrDefault(preset.name))
        }
    }

    @Test
    fun missingAndUnknownStoredNamesFallBackToDefault() {
        for (name in listOf(null, "", "unknown", "80%", "0")) {
            assertEquals(AppDisplayScalePreset.Default, appDisplayScalePresetOrDefault(name))
        }
    }
}
