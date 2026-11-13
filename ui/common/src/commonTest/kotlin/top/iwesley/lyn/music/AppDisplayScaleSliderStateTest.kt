package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset

class AppDisplayScaleSliderStateTest {
    @Test
    fun defaultStartsAtMiddleTick() {
        val state = AppDisplayScaleSliderState(AppDisplayScalePreset.Default)

        assertEquals(2f, state.previewPosition)
        assertEquals(AppDisplayScalePreset.Default, state.previewPreset)
        assertNull(state.finishSelection())
    }

    @Test
    fun draggingOnlyPreviewsUntilSelectionFinishes() {
        val state = AppDisplayScaleSliderState(AppDisplayScalePreset.Default)

        state.preview(1f)
        state.preview(4f)

        assertEquals(AppDisplayScalePreset.ExtraLarge, state.previewPreset)
        assertEquals(AppDisplayScalePreset.Default, state.selectedPreset)
        assertEquals(AppDisplayScalePreset.ExtraLarge, state.finishSelection())
        assertEquals(AppDisplayScalePreset.ExtraLarge, state.selectedPreset)
    }

    @Test
    fun repeatedSelectionDoesNotEmitAgainBeforeSavedStateArrives() {
        val state = AppDisplayScaleSliderState(AppDisplayScalePreset.Default)

        state.preview(0f)
        assertEquals(AppDisplayScalePreset.ExtraCompact, state.finishSelection())
        assertNull(state.finishSelection())
        state.preview(0f)
        assertNull(state.finishSelection())
    }

    @Test
    fun fractionalPositionsRoundToNearestTickAndClampAtBothEnds() {
        val state = AppDisplayScaleSliderState(AppDisplayScalePreset.Default)

        state.preview(3.6f)
        assertEquals(AppDisplayScalePreset.ExtraLarge, state.finishSelection())
        assertEquals(4f, state.previewPosition)
        state.preview(-5f)
        assertEquals(AppDisplayScalePreset.ExtraCompact, state.finishSelection())
        assertEquals(0f, state.previewPosition)
        state.preview(99f)
        assertEquals(AppDisplayScalePreset.ExtraLarge, state.finishSelection())
        assertEquals(4f, state.previewPosition)
    }

    @Test
    fun externalSavedPresetReplacesPendingPreviewWithoutEmitting() {
        val state = AppDisplayScaleSliderState(AppDisplayScalePreset.Default)
        state.preview(0f)

        state.synchronize(AppDisplayScalePreset.Large)

        assertEquals(AppDisplayScalePreset.Large, state.selectedPreset)
        assertEquals(AppDisplayScalePreset.Large, state.previewPreset)
        assertEquals(3f, state.previewPosition)
        assertNull(state.finishSelection())
    }

    @Test
    fun acknowledgementOfPreviousSelectionKeepsNewDragPreview() {
        val state = AppDisplayScaleSliderState(AppDisplayScalePreset.Default)
        state.preview(1f)
        assertEquals(AppDisplayScalePreset.Compact, state.finishSelection())
        state.preview(4f)

        state.synchronize(AppDisplayScalePreset.Compact)

        assertEquals(AppDisplayScalePreset.ExtraLarge, state.previewPreset)
        assertEquals(AppDisplayScalePreset.ExtraLarge, state.finishSelection())
    }

    @Test
    fun invalidPositionsReturnToSavedPreset() {
        val state = AppDisplayScaleSliderState(AppDisplayScalePreset.Compact)

        for (position in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            state.preview(4f)
            state.preview(position)
            assertEquals(AppDisplayScalePreset.Compact, state.previewPreset)
            assertNull(state.finishSelection())
        }
    }
}
