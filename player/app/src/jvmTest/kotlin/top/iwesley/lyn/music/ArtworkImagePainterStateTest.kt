package top.iwesley.lyn.music

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import kotlin.test.Test
import kotlin.test.assertSame

class ArtworkImagePainterStateTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `confirmed missing data ignores retained painter`() {
        assertSame(
            expected = FallbackPainter,
            actual = resolveDisplayedArtworkPainter(
                state = AsyncImagePainter.State.Empty,
                fallbackPainter = FallbackPainter,
                lastSuccessPainter = PreviousPainter,
                retainPreviousWhileLoading = true,
                targetPending = false,
                dataMissing = true,
            ),
        )
    }

    @Test
    fun `pending missing data can retain previous painter`() {
        assertSame(
            expected = PreviousPainter,
            actual = resolveDisplayedArtworkPainter(
                state = AsyncImagePainter.State.Empty,
                fallbackPainter = FallbackPainter,
                lastSuccessPainter = PreviousPainter,
                retainPreviousWhileLoading = true,
                targetPending = true,
                dataMissing = true,
            ),
        )
    }

    @Test
    fun `loading data can retain previous painter`() {
        assertSame(
            expected = PreviousPainter,
            actual = resolveDisplayedArtworkPainter(
                state = AsyncImagePainter.State.Loading(painter = null),
                fallbackPainter = FallbackPainter,
                lastSuccessPainter = PreviousPainter,
                retainPreviousWhileLoading = true,
                targetPending = false,
                dataMissing = false,
            ),
        )
    }
}

private object FallbackPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() = Unit
}

private object PreviousPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() = Unit
}
