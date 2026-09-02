package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.common.model.Modpack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteModpackPresentationTest {
    @Test
    fun legacyReferencesHaveStableSourceQualifiedKeys() {
        val legacy = RemoteModpackRef(RemoteModpackSource.Legacy, "abc")

        assertEquals("legacy:abc", legacy.key)
    }

    @Test
    fun legacySourceUsesKeywordAndCategoryFiltering() {
        val legacy = Modpack.BriefVo(
            name = "Legacy Tech",
            info = "old launcher",
            categories = listOf(Modpack.Category.TECH),
            playCount = 42,
            lastUpdatedTime = 1L,
        )

        val mixed = mergeRemoteModpackPresentations(
            legacy = listOf(legacy),
            keyword = "TECH",
            selectedCategory = Modpack.Category.TECH,
        )

        assertEquals(1, mixed.size)
        assertEquals(RemoteModpackSource.Legacy, mixed[0].ref.source)
        assertEquals("42", mixed[0].card.activityText)
        assertTrue(mixed[0].card.updatedTimeText?.isNotBlank() == true)
        assertFalse(mixed[0].card.updatedTimeText.orEmpty().contains(":"))

        val filtered = mergeRemoteModpackPresentations(
            legacy = listOf(legacy),
            keyword = "modern",
            selectedCategory = Modpack.Category.TECH,
        )
        assertTrue(filtered.isEmpty())
    }
}
