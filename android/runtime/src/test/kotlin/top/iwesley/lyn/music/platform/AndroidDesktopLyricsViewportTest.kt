package top.iwesley.lyn.music.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.DesktopLyricsViewport
import top.iwesley.lyn.music.core.model.DesktopLyricsWindowLocation
import top.iwesley.lyn.music.core.model.DesktopLyricsPosition
import top.iwesley.lyn.music.core.model.calculateDesktopLyricsPosition
import top.iwesley.lyn.music.core.model.calculateDesktopLyricsWindowLocation

class AndroidDesktopLyricsViewportTest {
    @Test
    fun screenAndWindowCoordinatesRoundTripWithSystemBarOffsets() {
        val coordinates = AndroidDesktopLyricsWindowCoordinates.fromLayout(
            screen = DesktopLyricsWindowLocation(180, 372),
            appliedOffset = DesktopLyricsWindowLocation(100, 300),
        )
        val screen = DesktopLyricsWindowLocation(480, 972)

        assertEquals(DesktopLyricsWindowLocation(400, 900), coordinates.toWindow(screen))
        assertEquals(screen, coordinates.toScreen(coordinates.toWindow(screen)))
    }

    @Test
    fun savedPositionAndResizedWindowStayWithinScreenSafeBounds() {
        val viewport = DesktopLyricsViewport(left = 80, top = 72, width = 1_000, height = 1_800)
        val coordinates = AndroidDesktopLyricsWindowCoordinates.fromLayout(
            screen = DesktopLyricsWindowLocation(80, 72),
            appliedOffset = DesktopLyricsWindowLocation(0, 0),
        )
        for (position in listOf(DesktopLyricsPosition(0f, 0f), DesktopLyricsPosition(1f, 1f))) {
            for (width in listOf(240, 800)) {
                val desired = calculateDesktopLyricsWindowLocation(position, width, 100, viewport)
                val displayed = coordinates.toScreen(coordinates.toWindow(desired))
                assertEquals(desired, displayed)
                assertTrue(displayed.x >= viewport.left)
                assertTrue(displayed.y >= viewport.top)
                assertTrue(displayed.x + width <= viewport.left + viewport.width)
                assertTrue(displayed.y + 100 <= viewport.top + viewport.height)
                val saved = calculateDesktopLyricsPosition(displayed.x, displayed.y, width, 100, viewport)
                assertEquals(displayed, calculateDesktopLyricsWindowLocation(saved, width, 100, viewport))
            }
        }
    }

    @Test
    fun newLayoutRecalibratesOriginAfterSystemBarsChange() {
        val offset = DesktopLyricsWindowLocation(300, 400)
        val before = AndroidDesktopLyricsWindowCoordinates.fromLayout(
            screen = DesktopLyricsWindowLocation(300, 472),
            appliedOffset = offset,
        )
        val after = AndroidDesktopLyricsWindowCoordinates.fromLayout(
            screen = DesktopLyricsWindowLocation(380, 400),
            appliedOffset = offset,
        )
        val desired = DesktopLyricsWindowLocation(500, 600)
        assertEquals(DesktopLyricsWindowLocation(500, 528), before.toWindow(desired))
        assertEquals(DesktopLyricsWindowLocation(420, 600), after.toWindow(desired))
        assertEquals(desired, after.toScreen(after.toWindow(desired)))
    }

    @Test
    fun positionConfirmationDoesNotRequestAnotherCorrection() {
        val state = AndroidDesktopLyricsLayoutState()
        state.requestLayout()
        assertEquals(AndroidDesktopLyricsLayoutRequest(false), state.consumeAfterLayout())
        state.requestPositionConfirmation()
        assertTrue(state.shouldObserveNextLayout)
        assertEquals(AndroidDesktopLyricsLayoutRequest(false, false), state.consumeAfterLayout())
        assertFalse(state.shouldObserveNextLayout)
    }

    @Test
    fun freshInsetsOverrideConfirmationAndWaitForDragToFinish() {
        val state = AndroidDesktopLyricsLayoutState()
        state.requestPositionConfirmation()
        state.beginDrag()
        state.requestLayout(refreshViewport = true)
        assertNull(state.consumeAfterLayout())
        state.finishDrag()
        assertEquals(AndroidDesktopLyricsLayoutRequest(true, true), state.consumeAfterLayout())
        state.requestPositionConfirmation()
        state.clear()
        assertNull(state.consumeAfterLayout())
    }

    @Test
    fun subtractsSystemInsetsFromDisplayBounds() {
        val viewport = calculateAndroidDesktopLyricsViewport(
            AndroidDesktopLyricsDisplayGeometry(
                left = 100,
                top = 200,
                width = 1_000,
                height = 2_000,
                insetLeft = 20,
                insetTop = 40,
                insetRight = 30,
                insetBottom = 80,
            ),
        )

        assertEquals(
            DesktopLyricsViewport(left = 120, top = 240, width = 950, height = 1_880),
            viewport,
        )
    }

    @Test
    fun acceptsRotatedDisplayDimensions() {
        val viewport = calculateAndroidDesktopLyricsViewport(
            AndroidDesktopLyricsDisplayGeometry(
                left = 0,
                top = 0,
                width = 2_400,
                height = 1_080,
                insetLeft = 24,
                insetRight = 24,
                insetBottom = 48,
            ),
        )

        assertEquals(
            DesktopLyricsViewport(left = 24, top = 0, width = 2_352, height = 1_032),
            viewport,
        )
    }

    @Test
    fun oversizedInsetsNeverProduceNegativeDimensions() {
        val viewport = calculateAndroidDesktopLyricsViewport(
            AndroidDesktopLyricsDisplayGeometry(
                left = 10,
                top = 20,
                width = 100,
                height = 50,
                insetLeft = 150,
                insetTop = 100,
                insetRight = 75,
                insetBottom = 75,
            ),
        )

        assertEquals(
            DesktopLyricsViewport(left = 110, top = 70, width = 0, height = 0),
            viewport,
        )
    }

    @Test
    fun placesDefaultWindowBottomCenterInsideSafeViewport() {
        val location = calculateAndroidDesktopLyricsBottomCenterLocation(
            viewport = DesktopLyricsViewport(left = 10, top = 20, width = 1_000, height = 600),
            windowWidth = 400,
            windowHeight = 100,
            bottomMargin = 96,
        )

        assertEquals(DesktopLyricsWindowLocation(x = 310, y = 424), location)
    }

    @Test
    fun clampsOversizedDefaultWindowToSafeViewportOrigin() {
        val location = calculateAndroidDesktopLyricsBottomCenterLocation(
            viewport = DesktopLyricsViewport(left = 10, top = 20, width = 100, height = 80),
            windowWidth = 300,
            windowHeight = 200,
            bottomMargin = 96,
        )

        assertEquals(DesktopLyricsWindowLocation(x = 10, y = 20), location)
    }

    @Test
    fun textWidthNeverExceedsAvailableViewportWidthOrBecomesNonPositive() {
        assertEquals(
            904,
            calculateAndroidDesktopLyricsTextMaxWidth(
                viewport = DesktopLyricsViewport(left = 0, top = 0, width = 1_000, height = 600),
                reservedHorizontalSpace = 96,
            ),
        )
        assertEquals(
            1,
            calculateAndroidDesktopLyricsTextMaxWidth(
                viewport = DesktopLyricsViewport(left = 0, top = 0, width = 80, height = 600),
                reservedHorizontalSpace = 96,
            ),
        )
    }

    @Test
    fun overlayWidthIsCappedByTheSafeViewportEvenWhenContentMarginsAreWider() {
        val narrowViewport = DesktopLyricsViewport(left = 10, top = 20, width = 40, height = 600)

        assertEquals(
            1,
            calculateAndroidDesktopLyricsTextMaxWidth(
                viewport = narrowViewport,
                reservedHorizontalSpace = 96,
            ),
        )
        assertEquals(40, calculateAndroidDesktopLyricsOverlayMaxWidth(narrowViewport))
        assertEquals(
            1,
            calculateAndroidDesktopLyricsOverlayMaxWidth(
                DesktopLyricsViewport(left = 0, top = 0, width = 0, height = 600),
            ),
        )
    }

    @Test
    fun conservativeLegacyViewportReservesUnknownDecorOnBothEdges() {
        val metrics = AndroidDesktopLyricsLegacyDisplayMetrics(
            realWidth = 1_080,
            realHeight = 1_920,
            appWidth = 1_080,
            appHeight = 1_728,
        )
        val viewport = DesktopLyricsViewport(
            left = 0,
            top = 192,
            width = 1_080,
            height = 1_536,
        )

        assertEquals(
            viewport,
            calculateAndroidDesktopLyricsConservativeLegacyViewportOrNull(metrics),
        )
        assertEquals(
            AndroidDesktopLyricsViewportResolution(
                viewport = viewport,
                isConfirmed = true,
            ),
            resolveAndroidDesktopLyricsLegacyViewportOrNull(metrics),
        )
    }

    @Test
    fun conservativeLegacyViewportSupportsRotatedSideDecor() {
        assertEquals(
            DesktopLyricsViewport(
                left = 120,
                top = 72,
                width = 2_160,
                height = 936,
            ),
            calculateAndroidDesktopLyricsConservativeLegacyViewportOrNull(
                AndroidDesktopLyricsLegacyDisplayMetrics(
                    realWidth = 2_400,
                    realHeight = 1_080,
                    appWidth = 2_280,
                    appHeight = 1_008,
                ),
            ),
        )
    }

    @Test
    fun conservativeLegacyViewportUsesTheWholeDisplayWithoutDecor() {
        assertEquals(
            DesktopLyricsViewport(left = 0, top = 0, width = 1_080, height = 1_920),
            calculateAndroidDesktopLyricsConservativeLegacyViewportOrNull(
                AndroidDesktopLyricsLegacyDisplayMetrics(
                    realWidth = 1_080,
                    realHeight = 1_920,
                    appWidth = 1_080,
                    appHeight = 1_920,
                ),
            ),
        )
    }

    @Test
    fun oversizedAppMetricsFallBackToUnconfirmedPhysicalDisplay() {
        val oversizedAppMetrics = AndroidDesktopLyricsLegacyDisplayMetrics(
            realWidth = 1_080,
            realHeight = 1_920,
            appWidth = 1_081,
            appHeight = 1_920,
        )
        assertNull(
            calculateAndroidDesktopLyricsConservativeLegacyViewportOrNull(
                oversizedAppMetrics,
            ),
        )
        assertEquals(
            AndroidDesktopLyricsViewportResolution(
                viewport = DesktopLyricsViewport(left = 0, top = 0, width = 1_080, height = 1_920),
                isConfirmed = false,
            ),
            resolveAndroidDesktopLyricsLegacyViewportOrNull(oversizedAppMetrics),
        )
    }

    @Test
    fun emptyConservativeAreaFallsBackToUnconfirmedPhysicalDisplay() {
        val metrics = AndroidDesktopLyricsLegacyDisplayMetrics(
            realWidth = 100,
            realHeight = 100,
            appWidth = 40,
            appHeight = 100,
        )

        assertNull(calculateAndroidDesktopLyricsConservativeLegacyViewportOrNull(metrics))
        assertEquals(
            AndroidDesktopLyricsViewportResolution(
                viewport = DesktopLyricsViewport(left = 0, top = 0, width = 100, height = 100),
                isConfirmed = false,
            ),
            resolveAndroidDesktopLyricsLegacyViewportOrNull(metrics),
        )
    }

    @Test
    fun unavailableAppMetricsFallBackToUnconfirmedPhysicalDisplay() {
        val metrics = AndroidDesktopLyricsLegacyDisplayMetrics(
            realWidth = 1_080,
            realHeight = 1_920,
            appWidth = 0,
            appHeight = 0,
        )

        assertEquals(
            AndroidDesktopLyricsViewportResolution(
                viewport = DesktopLyricsViewport(left = 0, top = 0, width = 1_080, height = 1_920),
                isConfirmed = false,
            ),
            resolveAndroidDesktopLyricsLegacyViewportOrNull(metrics),
        )
    }

    @Test
    fun invalidRealMetricsCannotProduceLegacyResolution() {
        assertNull(
            resolveAndroidDesktopLyricsLegacyViewportOrNull(
                AndroidDesktopLyricsLegacyDisplayMetrics(
                    realWidth = 0,
                    realHeight = 1_920,
                    appWidth = 1_080,
                    appHeight = 1_920,
                ),
            ),
        )
    }

    @Test
    fun viewportCacheDoesNotResolveAgainUntilRefreshedOrCleared() {
        val firstViewport = DesktopLyricsViewport(left = 0, top = 0, width = 1_080, height = 1_920)
        val refreshedViewport = DesktopLyricsViewport(left = 24, top = 72, width = 1_032, height = 1_824)
        val afterClearViewport = DesktopLyricsViewport(left = 0, top = 48, width = 1_920, height = 1_032)
        val cache = AndroidDesktopLyricsViewportCache()
        var resolutionCount = 0

        assertEquals(
            firstViewport,
            cache.current {
                resolutionCount += 1
                AndroidDesktopLyricsViewportResolution(
                    viewport = firstViewport,
                    isConfirmed = false,
                )
            },
        )
        assertFalse(cache.hasConfirmedViewport)
        assertEquals(
            firstViewport,
            cache.current {
                resolutionCount += 1
                error("cached viewport should be reused")
            },
        )
        assertEquals(1, resolutionCount)

        assertEquals(
            refreshedViewport,
            cache.refresh {
                resolutionCount += 1
                AndroidDesktopLyricsViewportResolution(
                    viewport = refreshedViewport,
                    isConfirmed = true,
                )
            },
        )
        assertTrue(cache.hasConfirmedViewport)
        assertEquals(refreshedViewport, cache.current { error("refreshed viewport should be reused") })
        assertEquals(2, resolutionCount)

        cache.clear()
        assertFalse(cache.hasConfirmedViewport)
        assertEquals(
            afterClearViewport,
            cache.current {
                resolutionCount += 1
                AndroidDesktopLyricsViewportResolution(
                    viewport = afterClearViewport,
                    isConfirmed = false,
                )
            },
        )
        assertEquals(3, resolutionCount)
    }

    @Test
    fun layoutRequestsAreCoalescedAndPreserveViewportRefresh() {
        val state = AndroidDesktopLyricsLayoutState()

        state.requestLayout()
        state.requestLayout(refreshViewport = true)
        state.requestLayout()

        assertTrue(state.shouldObserveNextLayout)
        assertEquals(
            AndroidDesktopLyricsLayoutRequest(refreshViewport = true),
            state.consumeAfterLayout(),
        )
        assertFalse(state.shouldObserveNextLayout)
        assertNull(state.consumeAfterLayout())
    }

    @Test
    fun layoutCorrectionWaitsUntilDragFinishes() {
        val state = AndroidDesktopLyricsLayoutState()

        state.requestLayout()
        state.beginDrag()
        state.requestLayout(refreshViewport = true)

        assertFalse(state.shouldObserveNextLayout)
        assertNull(state.consumeAfterLayout())

        state.finishDrag()

        assertTrue(state.shouldObserveNextLayout)
        assertEquals(
            AndroidDesktopLyricsLayoutRequest(refreshViewport = true),
            state.consumeAfterLayout(),
        )
    }
}
