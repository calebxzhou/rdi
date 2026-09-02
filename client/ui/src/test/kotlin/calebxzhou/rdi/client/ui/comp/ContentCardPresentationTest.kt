package calebxzhou.rdi.client.ui.comp

import calebxzhou.rdi.common.model.Mod
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentCardPresentationTest {
    @Test
    fun `card metadata wins while status remains visible`() {
        val presentation = ContentGridItem.ContentItem(
            key = "content:mods/example.jar",
            type = ContentGridType.MOD,
            name = "example",
            fileName = "example.jar",
            targetPath = "mods/example.jar",
            required = true,
            card = Mod.CardVo(
                name = "Example Mod",
                nameCn = "示例模组",
                intro = "平台简介",
                iconUrls = listOf("https://example/icon.png"),
            ),
            status = "已启用",
        ).toCardPresentation()

        assertEquals("示例模组", presentation.name)
        assertEquals("已启用 · 平台简介", presentation.description)
        assertEquals(listOf("https://example/icon.png"), presentation.iconUrls)

        val item = ContentGridItem.ContentItem(
            key = "content:mods/example.jar",
            type = ContentGridType.MOD,
            name = "example",
            fileName = "example.jar",
            targetPath = "mods/example.jar",
            required = true,
            card = Mod.CardVo(name = "Example Mod", nameCn = "示例模组", intro = "平台简介"),
        )
        assertEquals(true, item.searchText.contains("示例模组"))
        assertEquals(true, item.searchText.contains("平台简介"))
    }

    @Test
    fun `unmatched item falls back to file identity and status`() {
        val presentation = ContentGridItem.ContentItem(
            key = "content:mods/example.jar",
            type = ContentGridType.MOD,
            name = "example.jar",
            fileName = "example.jar",
            targetPath = "mods/example.jar",
            required = false,
            status = "未知内容 · 已启用",
        ).toCardPresentation()

        assertEquals("example.jar", presentation.name)
        assertEquals("未知内容 · 已启用", presentation.description)
        assertEquals(emptyList(), presentation.iconUrls)
    }
}
