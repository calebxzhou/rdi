package calebxzhou.rdi.client.ui.comp

import calebxzhou.rdi.common.model.Modpack
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModpackCardPresentationTest {
    @Test
    fun formatsSupportingTextInOrderWithDoubleSpaceSeparators() {
        val presentation = ModpackCardPresentation(
            name = "Nirvana",
            iconUrl = null,
            intro = "  我爱我痛苦  ",
            activityText = " 0 ",
            updatedTimeText = " 8月5日 ",
        )

        assertEquals("\uDB80\uDE97  0  8月5日  我爱我痛苦", formatModpackCardSupportingText(presentation))
    }

    @Test
    fun omitsBlankSupportingValuesWithoutExtraSeparators() {
        val blankDatePresentation = ModpackCardPresentation(
            name = "Nirvana",
            iconUrl = null,
            intro = "简介",
            activityText = "  ",
            updatedTimeText = "  ",
        )
        val missingDatePresentation = blankDatePresentation.copy(updatedTimeText = null)

        assertEquals("\uDB80\uDE97  简介", formatModpackCardSupportingText(blankDatePresentation))
        assertEquals("\uDB80\uDE97  简介", formatModpackCardSupportingText(missingDatePresentation))
    }

    @Test
    fun fallsBackToNoDescriptionWhenIntroIsBlankOrMissing() {
        val blankIntro = ModpackCardPresentation("Nirvana", null, "  ", "1", null)
        val missingIntro = ModpackCardPresentation("Nirvana", null, null, "1", null)

        assertEquals("\uDB80\uDE97  1  暂无简介", formatModpackCardSupportingText(blankIntro))
        assertEquals("\uDB80\uDE97  1  暂无简介", formatModpackCardSupportingText(missingIntro))
    }

    @Test
    fun formatsUpdatedTimeAsClockOnlyForToday() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-08-30T00:30:00Z").toEpochMilli()
        val today = Instant.parse("2026-08-29T20:05:00Z").toEpochMilli()
        val yesterday = Instant.parse("2026-08-29T15:45:00Z").toEpochMilli()

        assertEquals("今天4:05", formatModpackUpdatedTime(today, now, zone))
        assertEquals("昨天", formatModpackUpdatedTime(yesterday, now, zone))
    }

    @Test
    fun legacyBriefCardPresentationUsesDateOnlyForOldUpdates() {
        val presentation = Modpack.BriefVo(lastUpdatedTime = 1L).toModpackCardPresentation()

        assertTrue(presentation.updatedTimeText?.isNotBlank() == true)
        assertFalse(presentation.updatedTimeText.orEmpty().contains(":"))
    }

    @Test
    fun formatsPlayTimeWithoutPretendingItIsAPlayCount() {
        assertEquals("0秒", formatPlayTime(0))
        assertEquals("59秒", formatPlayTime(59))
        assertEquals("1分0秒", formatPlayTime(60))
        assertEquals("1小时1分", formatPlayTime(3_661))
        assertEquals("0秒", formatPlayTime(-1))
    }
}
