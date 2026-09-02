package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.common.model.Task2Progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McPlayProgressTest {
    @Test
    fun `structured progress includes message and compact fields`() {
        val progress = Task2Progress(
            message = "modernfix",
            completedItems = 12,
            totalItems = 449,
            completedBytes = 32L * 1024 * 1024,
            totalBytes = 1_200L * 1024 * 1024,
            bytesPerSecond = 8.0 * 1024 * 1024,
            fraction = 0.42f,
        )

        assertEquals(
            "modernfix · 12/449 · 32.0MB/1.2GB · 8.0MB/s · 42%",
            formatSyncProgress(progress),
        )
        assertEquals(
            formatSyncProgress(progress),
            SyncProgressFilter().accept(progress),
        )
        assertNull(SyncProgressFilter().let { filter ->
            filter.accept(progress)
            filter.accept(progress)
        })
    }

    @Test
    fun `fraction changes and completed items changes are emitted`() {
        val filter = SyncProgressFilter()
        assertEquals("download · 0%", filter.accept(Task2Progress("download", fraction = 0f)))
        assertNull(filter.accept(Task2Progress("download", fraction = 0.005f)))
        assertEquals("download · 1%", filter.accept(Task2Progress("download", fraction = 0.01f)))
        assertEquals(
            "download · 1 · 1%",
            filter.accept(Task2Progress("download", fraction = 0.015f, completedItems = 1)),
        )
        assertNull(filter.accept(Task2Progress("download", fraction = 0.015f, completedItems = 1)))
        assertEquals(
            "download · 2 · 1%",
            filter.accept(Task2Progress("download", fraction = 0.015f, completedItems = 2)),
        )
    }

    @Test
    fun `bytes without fraction use eight mebibyte buckets`() {
        val filter = SyncProgressFilter()
        assertEquals("download · 1.0MB", filter.accept(Task2Progress("download", completedBytes = 1L * 1024 * 1024)))
        assertNull(filter.accept(Task2Progress("download", completedBytes = 7L * 1024 * 1024)))
        assertEquals("download · 9.0MB", filter.accept(Task2Progress("download", completedBytes = 9L * 1024 * 1024)))
    }

    @Test
    fun `message-only progress emits changed messages once`() {
        val filter = SyncProgressFilter()
        assertEquals("准备中", filter.accept(Task2Progress("准备中")))
        assertNull(filter.accept(Task2Progress("准备中")))
        assertEquals("已完成", filter.accept(Task2Progress("已完成")))
        assertNull(filter.accept(Task2Progress("已完成")))
    }

    @Test
    fun `completion snapshot is emitted only once`() {
        val filter = SyncProgressFilter()
        val complete = Task2Progress("download", fraction = 1f, completedItems = 1, totalItems = 1)
        assertEquals("download · 1/1 · 100%", filter.accept(complete))
        assertNull(filter.accept(complete))
    }
}
