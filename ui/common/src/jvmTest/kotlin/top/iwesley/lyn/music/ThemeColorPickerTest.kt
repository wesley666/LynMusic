package top.iwesley.lyn.music

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeColorPickerTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `argb and hsv conversion round trips preset colors`() {
        listOf(
            0xFFE03131.toInt(),
            0xFF2F8F5B.toInt(),
            0xFFC97A2B.toInt(),
            0xFF102030.toInt(),
        ).forEach { argb ->
            assertEquals(argb, themePickerHsvToArgb(argbToThemePickerHsv(argb)))
        }
    }

    @Test
    fun `primary red converts to expected hsv`() {
        val hsv = argbToThemePickerHsv(0xFFFF0000.toInt())

        assertTrue(abs(hsv.hue - 0f) < 0.0001f)
        assertTrue(abs(hsv.saturation - 1f) < 0.0001f)
        assertTrue(abs(hsv.value - 1f) < 0.0001f)
    }

    @Test
    fun `hue slider mapping clamps to slider bounds`() {
        assertEquals(0f, themePickerHueFromSliderFraction(-0.2f))
        assertEquals(180f, themePickerHueFromSliderFraction(0.5f))
        assertEquals(360f, themePickerHueFromSliderFraction(1.8f))
        assertEquals(0f, themePickerSliderFractionFromHue(-20f))
        assertEquals(0.5f, themePickerSliderFractionFromHue(180f))
        assertEquals(1f, themePickerSliderFractionFromHue(540f))
    }

    @Test
    fun `panel coordinate mapping clamps within valid selection range`() {
        val lowSelection = themePickerPanelSelection(
            x = -48f,
            y = 180f,
            width = 120f,
            height = 90f,
        )
        val highSelection = themePickerPanelSelection(
            x = 260f,
            y = -24f,
            width = 120f,
            height = 90f,
        )
        val coordinates = themePickerPanelCoordinates(
            saturation = 1.4f,
            value = -0.3f,
            width = 120f,
            height = 90f,
        )

        assertEquals(0f, lowSelection.saturation)
        assertEquals(0f, lowSelection.value)
        assertEquals(1f, highSelection.saturation)
        assertEquals(1f, highSelection.value)
        assertTrue(coordinates.x in 0f..119f)
        assertTrue(coordinates.y in 0f..89f)
    }
}
