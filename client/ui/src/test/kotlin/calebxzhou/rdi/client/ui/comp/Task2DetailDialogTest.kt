package calebxzhou.rdi.client.ui.comp

import calebxzhou.rdi.common.model.Task2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Task2DetailDialogTest {
    @Test
    fun `detail tree omits root but keeps child indentation`() {
        val root = Task2.Group(
            title = "根任务",
            id = "root",
            children = listOf(
                Task2.Leaf(title = "文件", id = "file") { },
                Task2.Group(
                    title = "子组",
                    id = "group",
                    children = listOf(Task2.Leaf(title = "子文件", id = "nested") { })
                )
            )
        )

        val rows = buildTask2Rows(
            task = root,
            path = listOf(root.id),
            level = 0,
            expandState = emptyMap(),
            includeRoot = false
        )

        assertEquals(
            listOf("root / file#0", "root / group#1", "root / group#1 / nested#0"),
            rows.map { it.key }
        )
        assertEquals(listOf(1, 1, 2), rows.map { it.level })
    }

    @Test
    fun `root leaf does not produce an empty tree`() {
        val root = Task2.Leaf(title = "单文件", id = "leaf") { }

        val rows = buildTask2Rows(
            task = root,
            path = listOf(root.id),
            level = 0,
            expandState = emptyMap(),
            includeRoot = false
        )

        assertTrue(rows.isEmpty())
    }
}
