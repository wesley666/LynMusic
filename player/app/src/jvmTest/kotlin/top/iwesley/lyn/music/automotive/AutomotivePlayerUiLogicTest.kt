package top.iwesley.lyn.music.automotive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.shouldExitAutomotivePureModeOnBack

class AutomotivePlayerUiLogicTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `pure mode hides top and playback controls without changing their slots`() {
        val normal = resolveAutomotivePureModePresentation(isPureMode = false)
        val pure = resolveAutomotivePureModePresentation(isPureMode = true)

        assertEquals(true, normal.showTopControls)
        assertEquals(true, normal.showPlaybackControls)
        assertEquals(false, pure.showTopControls)
        assertEquals(false, pure.showPlaybackControls)
    }

    @Test
    fun `top controls fit three safe touch targets without changing regular car size`() {
        assertEquals(48.dp, resolveAutomotiveTopControlButtonSize(144.dp))
        assertEquals(60.dp, resolveAutomotiveTopControlButtonSize(180.dp))
        assertEquals(64.dp, resolveAutomotiveTopControlButtonSize(192.dp))
        assertEquals(64.dp, resolveAutomotiveTopControlButtonSize(532.dp))
    }

    @Test
    fun `artwork tap toggles playback only in pure mode`() {
        assertNull(resolveAutomotiveArtworkTapIntent(isPureMode = false))
        assertEquals(
            PlayerIntent.TogglePlayPause,
            resolveAutomotiveArtworkTapIntent(isPureMode = true),
        )
    }

    @Test
    fun `back exits pure mode only for automotive player`() {
        assertEquals(
            true,
            shouldExitAutomotivePureModeOnBack(
                useAutomotiveLandscapePlayer = true,
                isPureMode = true,
            ),
        )
        assertEquals(
            false,
            shouldExitAutomotivePureModeOnBack(
                useAutomotiveLandscapePlayer = true,
                isPureMode = false,
            ),
        )
        assertEquals(
            false,
            shouldExitAutomotivePureModeOnBack(
                useAutomotiveLandscapePlayer = false,
                isPureMode = true,
            ),
        )
    }

    @Test
    fun `two finger tap toggles pure mode in both directions`() {
        assertEquals(true, resolveAutomotivePureModeAfterTwoFingerTap(isPureMode = false))
        assertEquals(false, resolveAutomotivePureModeAfterTwoFingerTap(isPureMode = true))
    }

    @Test
    fun `two finger tap toggles pure mode at timing and movement boundaries`() {
        assertEquals(
            true,
            isAutomotivePureModeToggleTwoFingerTap(
                firstDownTimeMillis = 1_000L,
                secondDownTimeMillis = 1_150L,
                lastUpTimeMillis = 1_300L,
                distinctPointerCount = 2,
                hadTwoPointersPressed = true,
                maximumMovementPx = 12f,
                touchSlopPx = 12f,
            ),
        )
    }

    @Test
    fun `two finger tap rejects incomplete delayed moved and extra pointer gestures`() {
        val validArguments = {
            secondDownTimeMillis: Long?,
            lastUpTimeMillis: Long,
            distinctPointerCount: Int,
            hadTwoPointersPressed: Boolean,
            maximumMovementPx: Float,
            ->
            isAutomotivePureModeToggleTwoFingerTap(
                firstDownTimeMillis = 1_000L,
                secondDownTimeMillis = secondDownTimeMillis,
                lastUpTimeMillis = lastUpTimeMillis,
                distinctPointerCount = distinctPointerCount,
                hadTwoPointersPressed = hadTwoPointersPressed,
                maximumMovementPx = maximumMovementPx,
                touchSlopPx = 12f,
            )
        }

        assertEquals(false, validArguments(null, 1_200L, 1, false, 0f))
        assertEquals(false, validArguments(1_151L, 1_200L, 2, true, 0f))
        assertEquals(false, validArguments(1_100L, 1_301L, 2, true, 0f))
        assertEquals(false, validArguments(1_100L, 1_200L, 3, true, 0f))
        assertEquals(false, validArguments(1_100L, 1_200L, 2, false, 0f))
        assertEquals(false, validArguments(1_100L, 1_200L, 2, true, 12.1f))
    }

    @Test
    fun `landscape frame layout switches padding and gap at 900 dp`() {
        val belowBoundary = resolveAutomotiveLandscapeFrameLayout(899.dp)
        val atBoundary = resolveAutomotiveLandscapeFrameLayout(900.dp)

        assertEquals(28.dp, belowBoundary.horizontalPadding)
        assertEquals(28.dp, belowBoundary.verticalPadding)
        assertEquals(24.dp, belowBoundary.paneGap)
        assertEquals(44.dp, atBoundary.horizontalPadding)
        assertEquals(28.dp, atBoundary.verticalPadding)
        assertEquals(36.dp, atBoundary.paneGap)
    }

    @Test
    fun `display presets resolve the same default scale playback pane reference`() {
        val defaultReference = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = 1280.dp,
            overlayMaxHeight = 752.dp,
            appDisplayScalePreset = AppDisplayScalePreset.Default,
        )
        val compactReference = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = 1280.dp / AppDisplayScalePreset.Compact.scale,
            overlayMaxHeight = 752.dp / AppDisplayScalePreset.Compact.scale,
            appDisplayScalePreset = AppDisplayScalePreset.Compact,
        )
        val largeReference = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = 1280.dp / AppDisplayScalePreset.Large.scale,
            overlayMaxHeight = 752.dp / AppDisplayScalePreset.Large.scale,
            appDisplayScalePreset = AppDisplayScalePreset.Large,
        )

        assertEquals(531.76f, defaultReference.maxWidth.value, 0.001f)
        assertEquals(696.dp, defaultReference.maxHeight)
        assertEquals(defaultReference.maxWidth.value, compactReference.maxWidth.value, 0.001f)
        assertEquals(defaultReference.maxHeight.value, compactReference.maxHeight.value, 0.001f)
        assertEquals(defaultReference.maxWidth.value, largeReference.maxWidth.value, 0.001f)
        assertEquals(defaultReference.maxHeight.value, largeReference.maxHeight.value, 0.001f)
    }

    @Test
    fun `default 520 dp playback pane remains in large tier with large display preset`() {
        val defaultOverlayWidth = 520.dp / 0.46f + 44.dp * 2 + 36.dp
        val largeOverlayWidth = defaultOverlayWidth / AppDisplayScalePreset.Large.scale
        val largeActualPane = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = largeOverlayWidth,
            overlayMaxHeight = 600.dp,
            appDisplayScalePreset = AppDisplayScalePreset.Default,
        )
        val largeReferencePane = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = largeOverlayWidth,
            overlayMaxHeight = 600.dp,
            appDisplayScalePreset = AppDisplayScalePreset.Large,
        )
        val layout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = largeActualPane.maxWidth,
            maxHeight = largeActualPane.maxHeight,
            referenceMaxWidth = largeReferencePane.maxWidth,
            referenceMaxHeight = largeReferencePane.maxHeight,
            appDisplayScalePreset = AppDisplayScalePreset.Large,
        )

        assertEquals(520f, largeReferencePane.maxWidth.value, 0.001f)
        assertEquals(true, layout.showSecondaryControls)
        assertTrue(layout.actionButtonSize > 56.dp)
        assertTrue(layout.skipButtonSize > 64.dp)
        assertTrue(layout.playButtonSize > 84.dp)
    }

    @Test
    fun `display scale preset keeps current car pane in large controls tier`() {
        val defaultActualPane = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = 1280.dp,
            overlayMaxHeight = 752.dp,
            appDisplayScalePreset = AppDisplayScalePreset.Default,
        )
        val compactOverlayWidth = 1280.dp / AppDisplayScalePreset.Compact.scale
        val compactOverlayHeight = 752.dp / AppDisplayScalePreset.Compact.scale
        val compactActualPane = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = compactOverlayWidth,
            overlayMaxHeight = compactOverlayHeight,
            appDisplayScalePreset = AppDisplayScalePreset.Default,
        )
        val compactReferencePane = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = compactOverlayWidth,
            overlayMaxHeight = compactOverlayHeight,
            appDisplayScalePreset = AppDisplayScalePreset.Compact,
        )
        val largeOverlayWidth = 1280.dp / AppDisplayScalePreset.Large.scale
        val largeOverlayHeight = 752.dp / AppDisplayScalePreset.Large.scale
        val largeActualPane = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = largeOverlayWidth,
            overlayMaxHeight = largeOverlayHeight,
            appDisplayScalePreset = AppDisplayScalePreset.Default,
        )
        val largeReferencePane = resolveAutomotivePlaybackPaneReferenceConstraints(
            overlayMaxWidth = largeOverlayWidth,
            overlayMaxHeight = largeOverlayHeight,
            appDisplayScalePreset = AppDisplayScalePreset.Large,
        )
        val defaultLayout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = defaultActualPane.maxWidth,
            maxHeight = defaultActualPane.maxHeight,
            referenceMaxWidth = defaultActualPane.maxWidth,
            referenceMaxHeight = defaultActualPane.maxHeight,
            appDisplayScalePreset = AppDisplayScalePreset.Default,
        )
        val compactLayout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = compactActualPane.maxWidth,
            maxHeight = compactActualPane.maxHeight,
            referenceMaxWidth = compactReferencePane.maxWidth,
            referenceMaxHeight = compactReferencePane.maxHeight,
            appDisplayScalePreset = AppDisplayScalePreset.Compact,
        )
        val largeLayout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = largeActualPane.maxWidth,
            maxHeight = largeActualPane.maxHeight,
            referenceMaxWidth = largeReferencePane.maxWidth,
            referenceMaxHeight = largeReferencePane.maxHeight,
            appDisplayScalePreset = AppDisplayScalePreset.Large,
        )

        assertEquals(80.dp, defaultLayout.actionButtonSize)
        assertEquals(88.dp, defaultLayout.skipButtonSize)
        assertEquals(108.dp, defaultLayout.playButtonSize)
        assertEquals(80.dp, compactLayout.actionButtonSize)
        assertEquals(88.dp, compactLayout.skipButtonSize)
        assertEquals(108.dp, compactLayout.playButtonSize)
        assertEquals(true, largeLayout.showSecondaryControls)
        assertTrue(largeLayout.totalWidth <= largeActualPane.maxWidth)
        assertTrue(largeLayout.actionButtonSize * AppDisplayScalePreset.Large.scale > defaultLayout.actionButtonSize)
        assertTrue(largeLayout.skipButtonSize * AppDisplayScalePreset.Large.scale > defaultLayout.skipButtonSize)
        assertTrue(largeLayout.playButtonSize * AppDisplayScalePreset.Large.scale > defaultLayout.playButtonSize)
    }

    @Test
    fun `large preset keeps primary controls until five controls preserve physical size`() {
        listOf(240.dp, 246.dp).forEach { maxWidth ->
            val layout = resolveAutomotivePlaybackControlsLayout(
                maxWidth = maxWidth,
                maxHeight = 600.dp,
                referenceMaxWidth = 268.dp,
                referenceMaxHeight = 600.dp,
                appDisplayScalePreset = AppDisplayScalePreset.Large,
            )

            assertEquals(false, layout.showSecondaryControls)
            assertEquals(60.dp, layout.playButtonSize)
            assertTrue(layout.playButtonSize * AppDisplayScalePreset.Large.scale > 60.dp)
        }
        val firstFiveControlsLayout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = 247.dp,
            maxHeight = 600.dp,
            referenceMaxWidth = 268.dp,
            referenceMaxHeight = 600.dp,
            appDisplayScalePreset = AppDisplayScalePreset.Large,
        )

        assertEquals(true, firstFiveControlsLayout.showSecondaryControls)
        assertTrue(firstFiveControlsLayout.totalWidth <= 247.dp)
        assertTrue(firstFiveControlsLayout.playButtonSize * AppDisplayScalePreset.Large.scale >= 60.dp)
    }

    @Test
    fun `primary controls fit down to minimum supported width`() {
        listOf(144.dp, 150.dp, 200.dp).forEach { maxWidth ->
            val layout = resolveAutomotivePlaybackControlsLayout(
                maxWidth = maxWidth,
                maxHeight = 600.dp,
                appDisplayScalePreset = AppDisplayScalePreset.Default,
            )

            assertEquals(false, layout.showSecondaryControls)
            assertTrue(layout.totalWidth <= maxWidth)
            assertTrue(layout.skipButtonSize >= 48.dp)
            assertTrue(layout.playButtonSize >= 48.dp)
        }
    }

    @Test
    fun `playback controls hide secondary actions below 252 dp width`() {
        listOf(200.dp, 251.dp).forEach { maxWidth ->
            val layout = resolveAutomotivePlaybackControlsLayout(
                maxWidth = maxWidth,
                maxHeight = 800.dp,
            )

            assertEquals(false, layout.showSecondaryControls)
            assertEquals(48.dp, layout.skipButtonSize)
            assertEquals(60.dp, layout.playButtonSize)
            assertEquals(26.dp, layout.skipIconSize)
            assertEquals(46.dp, layout.playIconSize)
            assertEquals(4.dp, layout.controlGap)
            assertEquals(164.dp, layout.totalWidth)
            assertTrue(layout.totalWidth <= maxWidth)
        }
    }

    @Test
    fun `playback controls show safe compact five button layout from 252 through 287 dp`() {
        listOf(252.dp, 287.dp).forEach { maxWidth ->
            val layout = resolveAutomotivePlaybackControlsLayout(
                maxWidth = maxWidth,
                maxHeight = 800.dp,
            )

            assertEquals(true, layout.showSecondaryControls)
            assertEquals(48.dp, layout.actionButtonSize)
            assertEquals(48.dp, layout.skipButtonSize)
            assertEquals(60.dp, layout.playButtonSize)
            assertEquals(22.dp, layout.actionIconSize)
            assertEquals(26.dp, layout.skipIconSize)
            assertEquals(46.dp, layout.playIconSize)
            assertEquals(0.dp, layout.controlGap)
            assertEquals(252.dp, layout.totalWidth)
            assertTrue(layout.totalWidth <= maxWidth)
        }
    }

    @Test
    fun `playback controls switch to enlarged narrow layout at 288 dp`() {
        val layout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = 288.dp,
            maxHeight = 800.dp,
        )

        assertEquals(true, layout.showSecondaryControls)
        assertEquals(48.dp, layout.actionButtonSize)
        assertEquals(56.dp, layout.skipButtonSize)
        assertEquals(72.dp, layout.playButtonSize)
        assertEquals(2.dp, layout.controlGap)
    }

    @Test
    fun `playback controls use enlarged narrow layout below 400 dp`() {
        val layout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = 399.dp,
            maxHeight = 800.dp,
        )

        assertEquals(true, layout.showSecondaryControls)
        assertEquals(48.dp, layout.actionButtonSize)
        assertEquals(56.dp, layout.skipButtonSize)
        assertEquals(72.dp, layout.playButtonSize)
        assertEquals(26.dp, layout.actionIconSize)
        assertEquals(32.dp, layout.skipIconSize)
        assertEquals(56.dp, layout.playIconSize)
        assertEquals(2.dp, layout.controlGap)
    }

    @Test
    fun `playback controls use compact layout from 400 through 519 dp`() {
        listOf(400.dp, 519.dp).forEach { maxWidth ->
            val layout = resolveAutomotivePlaybackControlsLayout(
                maxWidth = maxWidth,
                maxHeight = 800.dp,
            )

            assertEquals(true, layout.showSecondaryControls)
            assertEquals(56.dp, layout.actionButtonSize)
            assertEquals(64.dp, layout.skipButtonSize)
            assertEquals(84.dp, layout.playButtonSize)
            assertEquals(30.dp, layout.actionIconSize)
            assertEquals(36.dp, layout.skipIconSize)
            assertEquals(64.dp, layout.playIconSize)
            assertEquals(6.dp, layout.controlGap)
        }
    }

    @Test
    fun `playback controls use extra compact layout for wide short car pane`() {
        val layout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = 520.dp,
            maxHeight = 424.dp,
        )

        assertEquals(true, layout.showSecondaryControls)
        assertEquals(48.dp, layout.actionButtonSize)
        assertEquals(48.dp, layout.skipButtonSize)
        assertEquals(60.dp, layout.playButtonSize)
        assertEquals(22.dp, layout.actionIconSize)
        assertEquals(26.dp, layout.skipIconSize)
        assertEquals(46.dp, layout.playIconSize)
        assertEquals(0.dp, layout.controlGap)
    }

    @Test
    fun `playback controls use compact layout below 520 dp height`() {
        val layout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = 520.dp,
            maxHeight = 480.dp,
        )

        assertEquals(true, layout.showSecondaryControls)
        assertEquals(56.dp, layout.actionButtonSize)
        assertEquals(64.dp, layout.skipButtonSize)
        assertEquals(84.dp, layout.playButtonSize)
        assertEquals(30.dp, layout.actionIconSize)
        assertEquals(36.dp, layout.skipIconSize)
        assertEquals(64.dp, layout.playIconSize)
        assertEquals(6.dp, layout.controlGap)
    }

    @Test
    fun `playback controls switch to large layout at 520 dp width and height`() {
        val layout = resolveAutomotivePlaybackControlsLayout(
            maxWidth = 520.dp,
            maxHeight = 520.dp,
        )

        assertEquals(true, layout.showSecondaryControls)
        assertEquals(80.dp, layout.actionButtonSize)
        assertEquals(88.dp, layout.skipButtonSize)
        assertEquals(108.dp, layout.playButtonSize)
        assertEquals(36.dp, layout.actionIconSize)
        assertEquals(44.dp, layout.skipIconSize)
        assertEquals(76.dp, layout.playIconSize)
        assertEquals(10.dp, layout.controlGap)
    }

    @Test
    fun `playback controls never expose a button below minimum touch size`() {
        val layouts = listOf(
            resolveAutomotivePlaybackControlsLayout(maxWidth = 200.dp, maxHeight = 800.dp),
            resolveAutomotivePlaybackControlsLayout(maxWidth = 252.dp, maxHeight = 800.dp),
            resolveAutomotivePlaybackControlsLayout(maxWidth = 288.dp, maxHeight = 800.dp),
            resolveAutomotivePlaybackControlsLayout(maxWidth = 399.dp, maxHeight = 800.dp),
            resolveAutomotivePlaybackControlsLayout(maxWidth = 400.dp, maxHeight = 800.dp),
            resolveAutomotivePlaybackControlsLayout(maxWidth = 519.dp, maxHeight = 800.dp),
            resolveAutomotivePlaybackControlsLayout(maxWidth = 520.dp, maxHeight = 424.dp),
            resolveAutomotivePlaybackControlsLayout(maxWidth = 520.dp, maxHeight = 520.dp),
        )

        layouts.forEach { layout ->
            assertTrue(layout.skipButtonSize >= 48.dp)
            assertTrue(layout.playButtonSize >= 48.dp)
            if (layout.showSecondaryControls) {
                assertTrue(layout.actionButtonSize >= 48.dp)
            }
        }
    }

    @Test
    fun `track and progress layout caps normal artwork at larger car size`() {
        val layout = resolveAutomotiveTrackAndProgressLayout(
            maxWidth = 900.dp,
            maxHeight = 800.dp,
        )

        assertEquals(false, layout.compactVertical)
        assertEquals(360.dp, layout.artworkSize)
        assertEquals(360.dp, layout.artworkMaximumSize)
        assertEquals(20.dp, layout.artworkTitleGap)
        assertEquals(44.dp, layout.progressTopGap)
        assertEquals(6.dp, layout.bottomPadding)
        assertEquals(0.86f, layout.progressWidthFraction)
    }

    @Test
    fun `track and progress layout keeps compact car height conservative`() {
        val layout = resolveAutomotiveTrackAndProgressLayout(
            maxWidth = 500.dp,
            maxHeight = 400.dp,
        )

        assertEquals(true, layout.compactVertical)
        assertEquals(192.dp, layout.artworkSize)
        assertEquals(250.dp, layout.artworkMaximumSize)
        assertEquals(12.dp, layout.artworkTitleGap)
        assertEquals(26.dp, layout.progressTopGap)
        assertEquals(8.dp, layout.bottomPadding)
        assertEquals(0.9f, layout.progressWidthFraction)
    }

    @Test
    fun `track and progress layout reduces vertical chrome for ultra compact height`() {
        val layout = resolveAutomotiveTrackAndProgressLayout(
            maxWidth = 520.dp,
            maxHeight = 300.dp,
        )

        assertEquals(true, layout.compactVertical)
        assertEquals(140.dp, layout.artworkSize)
        assertEquals(220.dp, layout.artworkMaximumSize)
        assertEquals(8.dp, layout.artworkTitleGap)
        assertEquals(2.dp, layout.metadataGap)
        assertEquals(16.dp, layout.progressTopGap)
        assertEquals(4.dp, layout.bottomPadding)
        assertEquals(0.92f, layout.progressWidthFraction)
    }

    @Test
    fun `artwork display spec defaults to vinyl with existing sizing`() {
        val spec = resolveAutomotiveArtworkDisplaySpec(artworkSize = 320.dp)

        assertEquals(PlayerArtworkStyle.VINYL, spec.style)
        assertEquals(320.dp, spec.artworkSize)
        assertEquals(0.76f, spec.vinylArtworkDiameterFraction)
        assertEquals(0.72f, spec.vinylInnerGlowDiameterFraction)
    }

    @Test
    fun `artwork display spec passes selected style without changing layout size`() {
        PlayerArtworkStyle.entries.forEach { style ->
            val spec = resolveAutomotiveArtworkDisplaySpec(
                artworkSize = 320.dp,
                playerArtworkStyle = style,
            )

            assertEquals(style, spec.style)
            assertEquals(320.dp, spec.artworkSize)
        }
    }

    @Test
    fun `progress fraction clamps to playback bounds`() {
        assertEquals(
            0.25f,
            resolveAutomotivePlayerProgressFraction(
                PlaybackSnapshot(positionMs = 25_000L, durationMs = 100_000L),
            ),
        )
        assertEquals(
            1f,
            resolveAutomotivePlayerProgressFraction(
                PlaybackSnapshot(positionMs = 120_000L, durationMs = 100_000L),
            ),
        )
        assertEquals(
            0f,
            resolveAutomotivePlayerProgressFraction(
                PlaybackSnapshot(positionMs = 10_000L, durationMs = 0L),
            ),
        )
    }

    @Test
    fun `seek position resolves only when playback is seekable`() {
        val seekableSnapshot = PlaybackSnapshot(
            durationMs = 100_000L,
            canSeek = true,
        )

        assertEquals(50_000L, resolveAutomotivePlayerSeekPositionMs(0.5f, seekableSnapshot))
        assertEquals(0L, resolveAutomotivePlayerSeekPositionMs(-1f, seekableSnapshot))
        assertEquals(100_000L, resolveAutomotivePlayerSeekPositionMs(2f, seekableSnapshot))
        assertNull(resolveAutomotivePlayerSeekPositionMs(null, seekableSnapshot))
        assertNull(
            resolveAutomotivePlayerSeekPositionMs(
                0.5f,
                seekableSnapshot.copy(canSeek = false),
            ),
        )
        assertNull(
            resolveAutomotivePlayerSeekPositionMs(
                0.5f,
                seekableSnapshot.copy(durationMs = 0L),
            ),
        )
    }

    @Test
    fun `artwork swipe resolves skip intent by threshold and direction`() {
        assertEquals(
            PlayerIntent.SkipNext,
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = -72f,
                swipeThresholdPx = 72f,
            ),
        )
        assertEquals(
            PlayerIntent.SkipPrevious,
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = 72f,
                swipeThresholdPx = 72f,
            ),
        )
        assertNull(
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = -71.9f,
                swipeThresholdPx = 72f,
            ),
        )
        assertNull(
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = 71.9f,
                swipeThresholdPx = 72f,
            ),
        )
        assertNull(
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = 100f,
                swipeThresholdPx = 0f,
            ),
        )
    }

    @Test
    fun `artwork swipe threshold adapts to available visual travel`() {
        val compactMaxOffset = resolveAutomotiveArtworkMaxVisualOffset(140.dp)
        val mediumMaxOffset = resolveAutomotiveArtworkMaxVisualOffset(200.dp)
        val regularMaxOffset = resolveAutomotiveArtworkMaxVisualOffset(250.dp)
        val largeMaxOffset = resolveAutomotiveArtworkMaxVisualOffset(300.dp)

        assertEquals(44.8.dp, compactMaxOffset)
        assertEquals(40.32.dp, resolveAutomotiveArtworkSwipeThreshold(compactMaxOffset))
        assertEquals(64.dp, mediumMaxOffset)
        assertEquals(57.6.dp, resolveAutomotiveArtworkSwipeThreshold(mediumMaxOffset))
        assertEquals(80.dp, regularMaxOffset)
        assertEquals(72.dp, resolveAutomotiveArtworkSwipeThreshold(regularMaxOffset))
        assertEquals(96.dp, largeMaxOffset)
        assertEquals(72.dp, resolveAutomotiveArtworkSwipeThreshold(largeMaxOffset))
    }

    @Test
    fun `artwork swipe threshold never exceeds constrained visual travel`() {
        assertEquals(0.dp, resolveAutomotiveArtworkSwipeThreshold(0.dp))
        assertEquals(20.dp, resolveAutomotiveArtworkSwipeThreshold(20.dp))
        assertEquals(132.dp, resolveAutomotiveArtworkMaxVisualOffset(500.dp))
        assertEquals(
            72.dp,
            resolveAutomotiveArtworkSwipeThreshold(
                resolveAutomotiveArtworkMaxVisualOffset(500.dp),
            ),
        )
    }

    @Test
    fun `artwork drag offset is clamped to visual bounds`() {
        assertEquals(
            40f,
            resolveAutomotiveArtworkDragOffsetPx(
                currentOffsetPx = 25f,
                dragAmountPx = 15f,
                maxVisualOffsetPx = 80f,
            ),
        )
        assertEquals(
            80f,
            resolveAutomotiveArtworkDragOffsetPx(
                currentOffsetPx = 70f,
                dragAmountPx = 20f,
                maxVisualOffsetPx = 80f,
            ),
        )
        assertEquals(
            -80f,
            resolveAutomotiveArtworkDragOffsetPx(
                currentOffsetPx = -70f,
                dragAmountPx = -20f,
                maxVisualOffsetPx = 80f,
            ),
        )
        assertEquals(
            0f,
            resolveAutomotiveArtworkDragOffsetPx(
                currentOffsetPx = 20f,
                dragAmountPx = 10f,
                maxVisualOffsetPx = 0f,
            ),
        )
    }
}
