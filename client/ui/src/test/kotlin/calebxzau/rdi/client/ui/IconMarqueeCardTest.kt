package calebxzau.rdi.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class IconMarqueeCardTest {
    @Test
    fun `splits icons into two lanes while preserving order`() {
        val icons = listOf(
            MarqueeIcon.Resource("a"),
            MarqueeIcon.Resource("b"),
            MarqueeIcon.Resource("c"),
            MarqueeIcon.Resource("d"),
            MarqueeIcon.Resource("e")
        )

        val lanes = splitMarqueeIcons(icons)

        assertEquals(
            listOf("a", "c", "e"),
            lanes[0].map { (it as MarqueeIcon.Resource).path }
        )
        assertEquals(
            listOf("b", "d"),
            lanes[1].map { (it as MarqueeIcon.Resource).path }
        )
    }

    @Test
    fun `uses one centered lane in a narrow viewport while preserving every icon`() {
        val icons = List(5) { index -> MarqueeIcon.Resource("icon-$index") }

        val lanes = selectMarqueeLanes(
            icons = icons,
            viewportHeightDp = 80f,
            iconSizeDp = 51.2f,
            itemGapDp = 12f
        )

        assertEquals(listOf(icons), lanes)
    }

    @Test
    fun `splits five icons into two lanes at half icon size`() {
        val icons = List(5) { index -> MarqueeIcon.Resource("icon-$index") }

        val lanes = selectMarqueeLanes(
            icons = icons,
            viewportHeightDp = 80f,
            iconSizeDp = 25.6f,
            itemGapDp = 12f
        )

        assertEquals(
            listOf(
                listOf("icon-0", "icon-2", "icon-4"),
                listOf("icon-1", "icon-3")
            ),
            lanes.map { lane -> lane.map { (it as MarqueeIcon.Resource).path } }
        )
    }

    @Test
    fun `keeps split lanes when the viewport can contain both lanes`() {
        val icons = List(5) { index -> MarqueeIcon.Resource("icon-$index") }

        val lanes = selectMarqueeLanes(
            icons = icons,
            viewportHeightDp = 120f,
            iconSizeDp = 51.2f,
            itemGapDp = 12f
        )

        assertEquals(
            listOf(
                listOf("icon-0", "icon-2", "icon-4"),
                listOf("icon-1", "icon-3")
            ),
            lanes.map { lane -> lane.map { (it as MarqueeIcon.Resource).path } }
        )
    }

    @Test
    fun `uses split lanes at the exact required height`() {
        val icons = listOf(
            MarqueeIcon.Resource("a"),
            MarqueeIcon.Resource("b")
        )

        val lanes = selectMarqueeLanes(
            icons = icons,
            viewportHeightDp = 114.4f,
            iconSizeDp = 51.2f,
            itemGapDp = 12f
        )

        assertEquals(2, lanes.size)
    }

    @Test
    fun `cycles a lane to the requested track size`() {
        val icons = listOf(
            MarqueeIcon.Resource("a"),
            MarqueeIcon.Resource("b")
        )

        val result = cycleMarqueeIcons(icons, count = 5)

        assertEquals(
            listOf("a", "b", "a", "b", "a"),
            result.map { (it as MarqueeIcon.Resource).path }
        )
    }

    @Test
    fun `adds one item pitch beyond the viewport`() {
        assertEquals(
            6,
            calculateMarqueeItemCount(
                viewportWidthDp = 600f,
                iconSizeDp = 100f,
                gapDp = 20f
            )
        )
    }

    @Test
    fun `keeps every source icon in the track`() {
        val icons = List(7) { index -> MarqueeIcon.Resource("icon-$index") }
        val trackItemCount = calculateMarqueeTrackItemCount(
            visibleItemCount = 3,
            lanes = listOf(icons)
        )

        assertEquals(7, trackItemCount)
        assertEquals(icons, cycleMarqueeIcons(icons, trackItemCount))
    }

    @Test
    fun `renders three complete copies of the canonical track`() {
        val icons = listOf(
            MarqueeIcon.Resource("a"),
            MarqueeIcon.Resource("b"),
            MarqueeIcon.Resource("c")
        )
        val canonicalTrack = cycleMarqueeIcons(icons, count = 7)

        assertEquals(
            canonicalTrack + canonicalTrack + canonicalTrack,
            buildMarqueeTrackIcons(icons, trackItemCount = 7)
        )
    }

    @Test
    fun `calculates duration from the fixed 24 dp per second speed`() {
        assertEquals(12_500, marqueeDurationMillis(trackWidthDp = 300f))
    }

    @Test
    fun `handles empty icon input`() {
        assertEquals(emptyList(), splitMarqueeIcons(emptyList()))
        assertEquals(emptyList(), cycleMarqueeIcons(emptyList(), count = 5))
        assertEquals(
            emptyList(),
            selectMarqueeLanes(
                icons = emptyList(),
                viewportHeightDp = 80f,
                iconSizeDp = 51.2f,
                itemGapDp = 12f
            )
        )
    }
}
