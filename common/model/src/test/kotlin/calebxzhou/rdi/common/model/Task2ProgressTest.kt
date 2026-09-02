package calebxzhou.rdi.common.model

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Task2ProgressTest {
    @Test
    fun `compact text includes item count human bytes and speed`() {
        val text = Task2Progress(
            message = "下载中",
            fraction = 0.5f,
            completedBytes = 2L * 1024 * 1024,
            totalBytes = 4L * 1024 * 1024,
            bytesPerSecond = 1.5 * 1024 * 1024,
            completedItems = 2,
            totalItems = 5
        ).compactText()

        assertContains(text, "2/5")
        assertContains(text, "2.0MB/4.0MB")
        assertContains(text, "1.5MB/s")
    }

    @Test
    fun `compact text keeps useful metadata when totals are unknown`() {
        assertEquals(
            "3 · 1.0KB",
            Task2Progress(
                message = "下载中",
                completedBytes = 1024,
                completedItems = 3
            ).compactText()
        )
    }

    @Test
    fun `snapshot keeps legacy fields alongside structured root progress`() {
        val snapshot = Task2Snapshot(
            currentMessage = "旧消息",
            currentFraction = 0.25f,
            currentProgress = Task2Progress("结构化消息", 0.5f, completedItems = 1, totalItems = 2)
        )

        assertEquals("旧消息", snapshot.currentMessage)
        assertEquals(0.25f, snapshot.currentFraction)
        assertEquals(1, snapshot.currentProgress?.completedItems)
        assertNull(snapshot.currentProgress?.bytesPerSecond)
    }
}
