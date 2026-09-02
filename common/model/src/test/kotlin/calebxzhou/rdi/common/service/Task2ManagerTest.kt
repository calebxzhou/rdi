package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.model.Task2Status
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class Task2ManagerTest {
    @Test
    fun `completed task keeps byte and item counts but clears speed`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val manager = Task2Manager(scope = scope)
            val task = Task2.Leaf(title = "下载", id = "download") { context ->
                context.emit(
                    Task2Progress(
                        message = "下载中",
                        fraction = 0.5f,
                        completedBytes = 2L * 1024 * 1024,
                        totalBytes = 4L * 1024 * 1024,
                        bytesPerSecond = 1.5 * 1024 * 1024,
                        completedItems = 2,
                        totalItems = 5
                    )
                )
            }

            val runId = manager.submit(task)
            val completed = withTimeout(5_000) {
                manager.entries.first { entries ->
                    entries.any { entry -> entry.runId == runId && entry.status == Task2Status.DONE }
                }
            }.first { entry -> entry.runId == runId }
            val progress = completed.snapshot.currentProgress

            assertEquals("完成", progress?.message)
            assertEquals(1f, progress?.fraction)
            assertEquals(2L * 1024 * 1024, progress?.completedBytes)
            assertEquals(4L * 1024 * 1024, progress?.totalBytes)
            assertEquals(2, progress?.completedItems)
            assertEquals(5, progress?.totalItems)
            assertNull(progress?.bytesPerSecond)

            val nodeProgress = completed.snapshot.progressByPath.getValue("download")
            assertEquals(2L * 1024 * 1024, nodeProgress.completedBytes)
            assertEquals(4L * 1024 * 1024, nodeProgress.totalBytes)
            assertEquals(2, nodeProgress.completedItems)
            assertEquals(5, nodeProgress.totalItems)
            assertNull(nodeProgress.bytesPerSecond)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cancelAndJoin waits for the task job to finish`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        try {
            val manager = Task2Manager(scope = scope)
            val runId = manager.submit(
                Task2.Leaf(title = "取消", id = "cancel") {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            cleanupStarted.complete(Unit)
                            releaseCleanup.await()
                        }
                    }
                }
            )
            withTimeout(5_000) { started.await() }

            val cancelAndJoin = async { manager.cancelAndJoin(runId) }
            withTimeout(5_000) { cleanupStarted.await() }
            assertFalse(cancelAndJoin.isCompleted)

            releaseCleanup.complete(Unit)
            withTimeout(5_000) { cancelAndJoin.await() }
            assertEquals(Task2Status.CANCELLED, manager.entry(runId)?.status)
        } finally {
            releaseCleanup.complete(Unit)
            scope.cancel()
        }
    }
}
