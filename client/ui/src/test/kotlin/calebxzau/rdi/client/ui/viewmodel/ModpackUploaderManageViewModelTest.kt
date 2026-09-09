package calebxzau.rdi.client.ui.viewmodel

import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.RAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.bson.types.ObjectId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModpackUploaderManageViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @AfterTest
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load maps null empty and nonempty policies`() = runTest {
        Dispatchers.setMain(dispatcher)
        for ((policy, expected) in listOf(
            Modpack.UploaderPolicyVo(null, emptyList()) to ModpackUploaderMode.EVERYONE,
            Modpack.UploaderPolicyVo(emptyList(), emptyList()) to ModpackUploaderMode.AUTHOR_ONLY,
            Modpack.UploaderPolicyVo(listOf(ID_A), listOf(account(ID_A))) to ModpackUploaderMode.SELECTED_PLAYERS,
        )) {
            val gateway = FakeGateway(Result.success(policy))
            val viewModel = ModpackUploaderManageViewModel(MODPACK_ID, gateway)
            advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.mode)
            assertEquals(policy.allowUploaderIds.orEmpty(), viewModel.uiState.value.selectedIds)
        }
    }

    @Test
    fun `switching away from selected mode retains draft`() = runTest {
        Dispatchers.setMain(dispatcher)
        val gateway = FakeGateway(Result.success(Modpack.UploaderPolicyVo(listOf(ID_A), listOf(account(ID_A)))))
        val viewModel = ModpackUploaderManageViewModel(MODPACK_ID, gateway)
        advanceUntilIdle()

        viewModel.setMode(ModpackUploaderMode.AUTHOR_ONLY)
        viewModel.setMode(ModpackUploaderMode.SELECTED_PLAYERS)

        assertEquals(listOf(ID_A), viewModel.uiState.value.selectedIds)
        assertEquals(ModpackUploaderMode.SELECTED_PLAYERS, viewModel.uiState.value.mode)
    }

    @Test
    fun `resolve adds exact account and keeps duplicate out of draft`() = runTest {
        Dispatchers.setMain(dispatcher)
        val account = account(ID_A)
        val gateway = FakeGateway(
            loadResult = Result.success(Modpack.UploaderPolicyVo(emptyList(), emptyList())),
            resolveResult = Result.success(account),
        )
        val viewModel = ModpackUploaderManageViewModel(MODPACK_ID, gateway)
        advanceUntilIdle()
        viewModel.setMode(ModpackUploaderMode.SELECTED_PLAYERS)
        viewModel.resolveAndAdd("ExactPlayer")
        advanceUntilIdle()
        viewModel.resolveAndAdd("ExactPlayer")
        advanceUntilIdle()

        assertEquals(listOf(ID_A), viewModel.uiState.value.selectedIds)
        assertEquals(1, viewModel.uiState.value.uploaders.size)
    }

    @Test
    fun `remove removes selected account and each policy saves expected wire value`() = runTest {
        Dispatchers.setMain(dispatcher)
        val gateway = FakeGateway(Result.success(Modpack.UploaderPolicyVo(listOf(ID_A), listOf(account(ID_A)))))
        val viewModel = ModpackUploaderManageViewModel(MODPACK_ID, gateway)
        advanceUntilIdle()
        viewModel.remove(ID_A)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())

        viewModel.setMode(ModpackUploaderMode.AUTHOR_ONLY)
        viewModel.save()
        advanceUntilIdle()
        assertEquals(emptyList(), gateway.savedValues.last())

        viewModel.setMode(ModpackUploaderMode.EVERYONE)
        viewModel.save()
        advanceUntilIdle()
        assertEquals(null, gateway.savedValues.last())
    }

    @Test
    fun `selected empty policy is rejected before gateway save`() = runTest {
        Dispatchers.setMain(dispatcher)
        val gateway = FakeGateway(Result.success(Modpack.UploaderPolicyVo(emptyList(), emptyList())))
        val viewModel = ModpackUploaderManageViewModel(MODPACK_ID, gateway)
        advanceUntilIdle()
        viewModel.setMode(ModpackUploaderMode.SELECTED_PLAYERS)
        viewModel.save()

        assertEquals("请至少添加一名玩家", viewModel.uiState.value.errorMessage)
        assertTrue(gateway.savedValues.isEmpty())
    }

    @Test
    fun `load and save failures are visible and success emits event`() = runTest {
        Dispatchers.setMain(dispatcher)
        val failingLoad = FakeGateway(
            loadResult = Result.failure(IllegalStateException("offline")),
        )
        val loadViewModel = ModpackUploaderManageViewModel(MODPACK_ID, failingLoad)
        advanceUntilIdle()
        assertTrue(loadViewModel.uiState.value.errorMessage!!.contains("offline"))

        val gateway = FakeGateway(
            loadResult = Result.success(Modpack.UploaderPolicyVo(null, emptyList())),
            saveResult = Result.failure(IllegalStateException("save failed")),
        )
        val viewModel = ModpackUploaderManageViewModel(MODPACK_ID, gateway)
        advanceUntilIdle()
        viewModel.setMode(ModpackUploaderMode.AUTHOR_ONLY)
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("save failed"))

        gateway.saveResult = Result.success(Unit)
        viewModel.save()
        advanceUntilIdle()
        assertIs<ModpackUploaderManageEvent.Saved>(viewModel.events.first())
        assertFalse(viewModel.uiState.value.dirty)
    }

    @Test
    fun `stale load result cannot overwrite newer reload`() = runTest {
        Dispatchers.setMain(dispatcher)
        val first = CompletableDeferred<Modpack.UploaderPolicyVo>()
        val gateway = DeferredLoadGateway(first)
        val viewModel = ModpackUploaderManageViewModel(MODPACK_ID, gateway)
        runCurrent()
        viewModel.reload()
        gateway.nextPolicy = Modpack.UploaderPolicyVo(emptyList(), emptyList())
        gateway.releaseNext()
        advanceUntilIdle()
        assertEquals(ModpackUploaderMode.AUTHOR_ONLY, viewModel.uiState.value.mode)

        first.complete(Modpack.UploaderPolicyVo(null, emptyList()))
        advanceUntilIdle()
        assertEquals(ModpackUploaderMode.AUTHOR_ONLY, viewModel.uiState.value.mode)
    }

    private class FakeGateway(
        private val loadResult: Result<Modpack.UploaderPolicyVo>,
        private val resolveResult: Result<RAccount.Dto> = Result.failure(IllegalStateException("no resolve")),
        var saveResult: Result<Unit> = Result.success(Unit),
    ) : ModpackUploaderManageGateway {
        val savedValues = mutableListOf<List<ObjectId>?>()

        override suspend fun load(modpackId: String): Result<Modpack.UploaderPolicyVo> = loadResult

        override suspend fun resolve(modpackId: String, playerNameOrQq: String): Result<RAccount.Dto> = resolveResult

        override suspend fun save(modpackId: String, allowUploaderIds: List<ObjectId>?): Result<Unit> {
            savedValues += allowUploaderIds
            return saveResult
        }
    }

    private class DeferredLoadGateway(
        private val first: CompletableDeferred<Modpack.UploaderPolicyVo>,
    ) : ModpackUploaderManageGateway {
        var nextPolicy: Modpack.UploaderPolicyVo = Modpack.UploaderPolicyVo(null, emptyList())
        private var nextDeferred: CompletableDeferred<Modpack.UploaderPolicyVo>? = null
        private var calls = 0

        override suspend fun load(modpackId: String): Result<Modpack.UploaderPolicyVo> {
            calls++
            val deferred = if (calls == 1) first else CompletableDeferred<Modpack.UploaderPolicyVo>().also { nextDeferred = it }
            return Result.success(deferred.await())
        }

        fun releaseNext() {
            nextDeferred?.complete(nextPolicy)
        }

        override suspend fun resolve(modpackId: String, playerNameOrQq: String): Result<RAccount.Dto> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun save(modpackId: String, allowUploaderIds: List<ObjectId>?): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val MODPACK_ID = "68b314bbadaf52ddab96b5ed"
        val ID_A = ObjectId("68c901f07c76a32fa7dc270a")

        fun account(id: ObjectId) = RAccount.Dto(id, "player-${id.toHexString().take(4)}", RAccount.Cloth())
    }
}
