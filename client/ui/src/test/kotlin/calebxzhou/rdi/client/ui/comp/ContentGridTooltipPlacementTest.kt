package calebxzhou.rdi.client.ui.comp

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentGridTooltipPlacementTest {
    private val container = IntSize(300, 200)
    private val tooltip = IntSize(80, 24)

    @Test
    fun `tooltip is centered above anchor when there is room`() {
        assertEquals(
            IntOffset(110, 42),
            contentGridTooltipPosition(Rect(130f, 70f, 170f, 110f), tooltip, container, 4),
        )
    }

    @Test
    fun `tooltip falls below anchor when above does not fit`() {
        assertEquals(
            IntOffset(110, 34),
            contentGridTooltipPosition(Rect(130f, 10f, 170f, 30f), tooltip, container, 4),
        )
    }

    @Test
    fun `tooltip is clamped at the left edge`() {
        assertEquals(
            IntOffset(0, 42),
            contentGridTooltipPosition(Rect(5f, 70f, 25f, 110f), tooltip, container, 4),
        )
    }

    @Test
    fun `tooltip is clamped at the right edge`() {
        assertEquals(
            IntOffset(220, 42),
            contentGridTooltipPosition(Rect(275f, 70f, 295f, 110f), tooltip, container, 4),
        )
    }

    @Test
    fun `tooltip is clamped at the bottom when below placement overflows`() {
        assertEquals(
            IntOffset(110, 176),
            contentGridTooltipPosition(Rect(130f, 10f, 170f, 190f), tooltip, container, 4),
        )
    }

    @Test
    fun `oversized tooltip is clamped to the container origin`() {
        assertEquals(
            IntOffset.Zero,
            contentGridTooltipPosition(
                Rect(130f, 70f, 170f, 110f),
                IntSize(340, 220),
                container,
                4,
            ),
        )
    }
}
