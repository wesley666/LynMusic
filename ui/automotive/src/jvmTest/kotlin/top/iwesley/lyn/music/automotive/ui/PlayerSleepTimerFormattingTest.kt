package top.iwesley.lyn.music.automotive.ui

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.automotive.ui.testing.sleepTimerStatusText
import top.iwesley.lyn.music.feature.player.SleepTimerState

class PlayerSleepTimerFormattingTest {
    @BeforeTest fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }
    @Test
    fun `sleep timer custom minutes parser accepts only supported minute range`() = runTest {
        assertNull(parseSleepTimerCustomMinutes(""))
        assertNull(parseSleepTimerCustomMinutes("abc"))
        assertNull(parseSleepTimerCustomMinutes("0"))
        assertEquals(1, parseSleepTimerCustomMinutes("1"))
        assertEquals(999, parseSleepTimerCustomMinutes("999"))
        assertNull(parseSleepTimerCustomMinutes("1000"))
    }

    @Test
    fun `sleep timer remaining text uses minute and hour formats`() = runTest {
        assertEquals("00:59", formatSleepTimerRemaining(59_000L))
        assertEquals("15:00", formatSleepTimerRemaining(15 * 60_000L))
        assertEquals("1:00:00", formatSleepTimerRemaining(60 * 60_000L))
        assertEquals(
            "剩余 15:00",
            sleepTimerStatusText(SleepTimerState(durationMinutes = 15, remainingMs = 15 * 60_000L)),
        )
        assertEquals("未开启", sleepTimerStatusText(SleepTimerState()))
    }

}
