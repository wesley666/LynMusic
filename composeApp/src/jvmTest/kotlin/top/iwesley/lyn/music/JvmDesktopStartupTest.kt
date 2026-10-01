package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import top.iwesley.lyn.music.platform.JvmDataLocationProgress

class JvmDesktopStartupTest {
    @Test
    fun `desktop startup begins in starting state`() {
        assertIs<JvmDesktopStartupState.Starting>(initialJvmDesktopStartupState())
    }

    @Test
    fun `pending data location operation transitions to preparing without creating component`() {
        var componentCreationRequested = false

        val state = resolveJvmDesktopStartupAfterLocationCheck(
            requiresDataLocationOperation = true,
            createComponentState = {
                componentCreationRequested = true
                JvmDesktopStartupState.ComponentFailed(IllegalStateException("unexpected"))
            },
        )

        assertIs<JvmDesktopStartupState.Preparing>(state)
        assertFalse(componentCreationRequested)
    }

    @Test
    fun `normal startup preserves component creation outcome`() {
        val expected = JvmDesktopStartupState.ComponentFailed(IllegalStateException("database failed"))

        val state = resolveJvmDesktopStartupAfterLocationCheck(
            requiresDataLocationOperation = false,
            createComponentState = { expected },
        )

        assertSame(expected, state)
    }

    @Test
    fun `preparing state retains progress for startup screen`() {
        val progress = JvmDataLocationProgress(top.iwesley.lyn.music.core.model.UiText.Raw("正在迁移数据"), fraction = 0.5f)
        val state = JvmDesktopStartupState.Preparing(progress)

        assertSame(progress, state.progress)
    }
}
