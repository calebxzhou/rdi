package calebxzau.rdi.client.ui.viewmodel

import calebxzhou.rdi.common.model.DownloadQuota
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @Test
    fun `initialization backfills saved settings and memory`() = runBlocking {
        val savedDraft = SettingsDraft(
            preferModMirror = false,
            preferMcMirror = true,
            maxMemoryText = "8192",
            proxyEnabled = true,
            proxySystem = false,
            proxyHost = "10.0.0.2",
            proxyPortText = "7890",
            proxyUsr = "user",
            proxyPwd = "password",
            solidWindow = true,
        )
        val viewModel = SettingsViewModel(
            FakeSettingsGateway(initialData = SettingsInitialData(savedDraft, totalMemoryMb = 32768))
        )

        val state = awaitInitialized(viewModel)

        assertEquals(savedDraft, state.draft)
        assertEquals(32768, state.totalMemoryMb)
        assertNull(state.errorMessage)
    }

    @Test
    fun `invalid settings are rejected before save`() = runBlocking {
        val gateway = FakeSettingsGateway()
        val viewModel = SettingsViewModel(gateway)
        awaitInitialized(viewModel)
        viewModel.updateDraft(viewModel.uiState.value.draft.copy(maxMemoryText = "4096"))

        viewModel.save()

        assertEquals("最大内存必须大于4096MB", viewModel.uiState.value.errorMessage)
        assertTrue(gateway.savedDrafts.isEmpty())
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `valid settings are saved and announced`() = runBlocking {
        val gateway = FakeSettingsGateway()
        val viewModel = SettingsViewModel(gateway)
        awaitInitialized(viewModel)
        val draft = viewModel.uiState.value.draft.copy(
            maxMemoryText = "8192",
            proxyPortText = "10809",
            solidWindow = true,
        )
        viewModel.updateDraft(draft)

        viewModel.save()

        assertEquals(SettingsEvent.ShowSnackbar("设置已保存"), viewModel.events.first())
        assertEquals(listOf(draft), gateway.savedDrafts)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `node switching ignores duplicates and emits the result`() = runBlocking {
        val switchGate = CompletableDeferred<Result<String>>()
        val gateway = FakeSettingsGateway(switchGate = switchGate)
        val viewModel = SettingsViewModel(gateway)
        awaitInitialized(viewModel)

        viewModel.switchNode(gameBackup = true)
        viewModel.uiState.filter { it.switchingNode }.first()
        gateway.awaitNodeSwitchCount(1)
        viewModel.switchNode(forceMain = true)

        assertEquals(1, gateway.nodeSwitches.size)
        assertEquals(NodeSwitchRequest(gameBackup = true, forceMain = false), gateway.nodeSwitches.single())
        switchGate.complete(Result.success("备用节点A"))
        assertEquals(SettingsEvent.ShowSnackbar("已临时切换到备用节点A"), viewModel.events.first())
        assertFalse(viewModel.uiState.value.switchingNode)
    }

    @Test
    fun `quota success is displayed and can be refreshed`() = runBlocking {
        val firstQuota = quota(remainingBytes = 700)
        val refreshedQuota = quota(remainingBytes = 500)
        val gateway = FakeSettingsGateway(quotaResults = ArrayDeque(listOf(Result.success(firstQuota), Result.success(refreshedQuota))))
        val viewModel = SettingsViewModel(gateway)

        assertEquals(firstQuota, awaitQuotaLoaded(viewModel).downloadQuota)

        viewModel.refreshDownloadQuota()

        assertEquals(refreshedQuota, awaitQuotaLoaded(viewModel, refreshedQuota).downloadQuota)
        assertEquals(2, gateway.quotaLoadCount)
        assertNull(viewModel.uiState.value.downloadQuotaError)
    }

    @Test
    fun `quota failure is visible and refresh can recover`() = runBlocking {
        val initialQuota = quota(remainingBytes = 800)
        val recoveredQuota = quota(remainingBytes = 400)
        val gateway = FakeSettingsGateway(
            quotaResults = ArrayDeque(
                listOf(
                    Result.success(initialQuota),
                    Result.failure(IllegalStateException("网络不可用")),
                    Result.success(recoveredQuota),
                )
            )
        )
        val viewModel = SettingsViewModel(gateway)
        awaitQuotaLoaded(viewModel, initialQuota)

        viewModel.refreshDownloadQuota()
        withTimeout(5_000) { gateway.awaitQuotaLoadCount(2) }
        val failed = withTimeout(5_000) {
            viewModel.uiState.filter {
                !it.downloadQuotaLoading && it.downloadQuotaError != null
            }.first()
        }
        assertEquals("网络不可用", failed.downloadQuotaError)
        assertEquals(initialQuota, failed.downloadQuota)

        viewModel.refreshDownloadQuota()

        val recovered = awaitQuotaLoaded(viewModel, recoveredQuota)
        assertEquals(recoveredQuota, recovered.downloadQuota)
        assertNull(recovered.downloadQuotaError)
    }

    @Test
    fun `download cache cleanup submits task and announces progress location`() = runBlocking {
        val gateway = FakeSettingsGateway()
        val viewModel = SettingsViewModel(gateway)
        awaitInitialized(viewModel)

        viewModel.clearDownloadCache()
        viewModel.clearDownloadCache()
        assertEquals(2, gateway.clearCount)

        assertEquals(
            SettingsEvent.ShowSnackbar("清除下载缓存任务已提交，可在任务中查看进度"),
            viewModel.events.first()
        )
    }

    private suspend fun awaitInitialized(viewModel: SettingsViewModel): SettingsUiState =
        viewModel.uiState.filter { !it.loading }.first()

    private suspend fun awaitQuotaLoaded(
        viewModel: SettingsViewModel,
        expected: DownloadQuota.Vo? = null,
    ): SettingsUiState = viewModel.uiState.filter {
        !it.downloadQuotaLoading && it.downloadQuota != null && (expected == null || it.downloadQuota == expected)
    }.first()

    private class FakeSettingsGateway(
        private val initialData: SettingsInitialData = SettingsInitialData(
            draft = SettingsDraft(),
            totalMemoryMb = 16384,
        ),
        private val saveResult: Result<Unit> = Result.success(Unit),
        private val switchGate: CompletableDeferred<Result<String>>? = null,
        private val quotaResults: ArrayDeque<Result<DownloadQuota.Vo>> = ArrayDeque(
            listOf(Result.success(quota(remainingBytes = 800)))
        ),
    ) : SettingsGateway {
        val savedDrafts = mutableListOf<SettingsDraft>()
        val nodeSwitches = mutableListOf<NodeSwitchRequest>()
        private val nodeSwitchCount = kotlinx.coroutines.flow.MutableStateFlow(0)
        var quotaLoadCount = 0
        private val quotaLoads = kotlinx.coroutines.flow.MutableStateFlow(0)
        var clearCount = 0

        override suspend fun loadSettings(): Result<SettingsInitialData> = Result.success(initialData)

        override suspend fun saveSettings(draft: SettingsDraft): Result<Unit> {
            savedDrafts += draft
            return saveResult
        }

        override suspend fun switchNode(request: NodeSwitchRequest): Result<String> {
            nodeSwitches += request
            nodeSwitchCount.value = nodeSwitches.size
            return switchGate?.await() ?: Result.success("测试节点")
        }

        suspend fun awaitNodeSwitchCount(expected: Int) {
            nodeSwitchCount.filter { it >= expected }.first()
        }

        override suspend fun loadDownloadQuota(): Result<DownloadQuota.Vo> {
            quotaLoadCount++
            quotaLoads.value = quotaLoadCount
            return quotaResults.removeFirstOrNull()
                ?: Result.failure(IllegalStateException("没有测试额度结果"))
        }

        override fun clearDownloadCache(): Result<String> {
            clearCount++
            return Result.success("run-$clearCount")
        }

        suspend fun awaitQuotaLoadCount(expected: Int) {
            quotaLoads.filter { it >= expected }.first()
        }
    }

    private companion object {
        fun quota(remainingBytes: Long) = DownloadQuota.Vo(
            limitBytes = 1_000,
            usedBytes = 1_000 - remainingBytes,
            remainingBytes = remainingBytes,
            day = "2026-08-12",
        )
    }
}
