package calebxzhou.rdi.client.service

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateServiceTest {
    @Test
    fun `updater still runs when MC core update fails`() = runBlocking {
        var updaterRan = false
        val statuses = mutableListOf<String>()

        val result = UpdateService.startUpdateFlow(
            onStatus = statuses::add,
            onDetail = {},
            updateMcCores = { _, _ -> Result.failure(IllegalStateException("MC failed")) },
            updateUpdater = { _, _ ->
                updaterRan = true
                Result.success(UpdaterUpdateResult.UP_TO_DATE)
            }
        )

        assertTrue(updaterRan)
        assertTrue(result.mcCore.isFailure)
        assertTrue(result.updater.isSuccess)
        assertEquals("MC核心更新失败，启动程序检查已完成", statuses.last())
    }

    @Test
    fun `updated launcher is reported independently from unchanged MC cores`() = runBlocking {
        val result = UpdateService.startUpdateFlow(
            onStatus = {},
            onDetail = {},
            updateMcCores = { _, _ -> Result.success(McCoreUpdateResult(3, 0)) },
            updateUpdater = { _, _ -> Result.success(UpdaterUpdateResult.UPDATED) }
        )

        assertEquals("启动程序更新完成，下次启动生效", result.statusText)
    }

    @Test
    fun `both failures produce a combined status`() = runBlocking {
        val result = UpdateService.startUpdateFlow(
            onStatus = {},
            onDetail = {},
            updateMcCores = { _, _ -> Result.failure(IllegalStateException("MC failed")) },
            updateUpdater = { _, _ -> Result.failure(IllegalStateException("updater failed")) }
        )

        assertEquals("核心和启动程序更新失败", result.statusText)
    }
}
