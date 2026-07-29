package calebxzhou.rdi.client.model

import calebxzhou.rdi.common.model.Mod
import kotlin.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UiModTest {
    @Test
    fun `stable key includes hash`() {
        val first = createMod(hash = "first").toUiMod()
        val second = createMod(hash = "second").toUiMod()

        assertNotEquals(first.key, second.key)
        assertEquals("mr:project:file:first", first.key)
    }

    @Test
    fun `exposes complete render data from hydrated card`() {
        val uiMod = UiMod(
            mod = createMod(),
            card = Mod.CardVo(
                name = "English Name",
                nameCn = "中文名",
                intro = "简介",
                iconData = byteArrayOf(1),
                iconUrls = listOf("https://example.com/icon.png")
            )
        )

        assertEquals("中文名", uiMod.primaryName)
        assertEquals("English Name", uiMod.secondaryName)
        assertEquals("简介", uiMod.intro)
        assertContentEquals(byteArrayOf(1), uiMod.iconData)
        assertEquals(listOf("https://example.com/icon.png"), uiMod.iconUrls)
    }

    @Test
    fun `precomputes searchable render data`() {
        val uiMod = UiMod(
            mod = createMod(slug = "Search-Slug"),
            card = Mod.CardVo(name = "SODIUM", nameCn = "钠")
        )

        assertTrue("sodium" in uiMod.searchText)
        assertTrue("钠" in uiMod.searchText)
        assertTrue("search-slug" in uiMod.searchText)
        assertTrue("project" in uiMod.searchText)
    }

    @Test
    fun `provides fallback render data without card`() {
        val uiMod = createMod(slug = "fallback-mod").toUiMod()

        assertEquals("fallback-mod", uiMod.primaryName)
        assertEquals(null, uiMod.secondaryName)
        assertEquals("", uiMod.intro)
        assertEquals(emptyList(), uiMod.iconUrls)
    }

    private fun createMod(
        slug: String = "slug",
        hash: String = "hash"
    ) = Mod(
        platform = "mr",
        projectId = "project",
        slug = slug,
        fileId = "file",
        hash = hash
    )
}
