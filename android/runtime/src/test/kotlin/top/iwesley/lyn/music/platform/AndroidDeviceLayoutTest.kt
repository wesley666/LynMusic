package top.iwesley.lyn.music.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidDeviceLayoutTest {
    @Test
    fun phoneAndTabletClassificationDoesNotDependOnOrientation() {
        assertFalse(isAndroidTabletDisplay(1080, 2400, 480f))
        assertFalse(isAndroidTabletDisplay(2400, 1080, 480f))
        assertTrue(isAndroidTabletDisplay(1600, 2560, 320f))
        assertTrue(isAndroidTabletDisplay(2560, 1600, 320f))
    }

    @Test
    fun sixHundredDpBoundaryUsesStableDisplayDensity() {
        assertFalse(isAndroidTabletDisplay(1199, 2000, 320f))
        assertTrue(isAndroidTabletDisplay(1200, 2000, 320f))
        assertFalse(isAndroidTabletDisplay(0, 2000, 320f))
        assertFalse(isAndroidTabletDisplay(1200, 2000, Float.NaN))
    }
}
