package calebxzhou.rdi.client.ui.window

import calebxzau.rdi.client.ui.window.TaskHoverAction
import calebxzau.rdi.client.ui.window.taskMenuProgressFraction
import calebxzau.rdi.client.ui.window.sortTaskEntries
import calebxzau.rdi.client.ui.window.taskHoverAction
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Snapshot
import calebxzhou.rdi.common.model.Task2Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskMenuTest {
    @Test
    fun sortsRunningThenQueuedThenNewestTerminalTask() {
        val entries = listOf(
            entry("failed", Task2Status.FAILED, 200),
            entry("queued", Task2Status.QUEUED, 400),
            entry("done", Task2Status.DONE, 300),
            entry("running", Task2Status.RUNNING, 100)
        )

        assertEquals(
            listOf("running", "queued", "done", "failed"),
            sortTaskEntries(entries).map(Task2Entry::runId)
        )
    }

    @Test
    fun hoverActionMatchesTaskStatus() {
        assertEquals(TaskHoverAction.CANCEL, taskHoverAction(entry("running", Task2Status.RUNNING)))
        assertEquals(TaskHoverAction.REMOVE, taskHoverAction(entry("done", Task2Status.DONE)))
        assertEquals(TaskHoverAction.REMOVE, taskHoverAction(entry("failed", Task2Status.FAILED)))
        assertNull(taskHoverAction(entry("queued", Task2Status.QUEUED)))
    }

    @Test
    fun failedTaskMenuProgressIsAlwaysComplete() {
        assertEquals(1f, entry("failed", Task2Status.FAILED).taskMenuProgressFraction())
        assertEquals(1f, entry("failed", Task2Status.FAILED, fraction = 0.4f).taskMenuProgressFraction())
    }

    @Test
    fun doneTaskMenuProgressIsComplete() {
        assertEquals(1f, entry("done", Task2Status.DONE).taskMenuProgressFraction())
    }

    @Test
    fun taskMenuProgressPreservesIndeterminateAndClampedStates() {
        assertNull(entry("running", Task2Status.RUNNING).taskMenuProgressFraction())
        assertEquals(1f, entry("running", Task2Status.RUNNING, fraction = 1.4f).taskMenuProgressFraction())
    }

    private fun entry(
        runId: String,
        status: Task2Status,
        createdAt: Long = 0,
        fraction: Float? = null
    ) = Task2Entry(
        runId = runId,
        task = Task2.Leaf(title = runId, id = runId) { _ -> },
        snapshot = Task2Snapshot(status = status, currentFraction = fraction),
        createdAt = createdAt
    )
}
