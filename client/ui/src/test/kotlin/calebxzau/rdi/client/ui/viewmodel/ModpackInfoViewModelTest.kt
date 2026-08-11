package calebxzau.rdi.client.ui.viewmodel

import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.service.toUiMods
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModpackInfoViewModelTest {
    @Test
    fun `loading detail exposes fallback mods and finishes hydration`() = runBlocking {
        val pack = testPack(mods = mutableListOf(testMod()))
        val gateway = FakeModpackInfoGateway(detail = pack)
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)

        val state = viewModel.uiState.filter { !it.loading && !it.modsLoading }.first()

        assertEquals(pack, state.pack)
        assertEquals("example-mod-hydrated", state.mods.single().mod.slug)
        assertTrue(gateway.hydrateCalled)
    }

    @Test
    fun `failed hydration keeps fallback mods`() = runBlocking {
        val pack = testPack(mods = mutableListOf(testMod()))
        val gateway = FakeModpackInfoGateway(
            detail = pack,
            hydrationFailure = IllegalStateException("catalog unavailable"),
        )
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)

        val state = viewModel.uiState.filter { !it.loading && !it.modsLoading }.first()

        assertEquals(pack.versions.single().mods.toUiMods(), state.mods)
        assertNull(state.errorMessage)
    }

    @Test
    fun `detail without versions exposes an empty mod list`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack().copy(versions = emptyList()))
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)

        val state = awaitLoaded(viewModel)

        assertTrue(state.mods.isEmpty())
        assertFalse(state.modsLoading)
        assertFalse(gateway.hydrateCalled)
    }

    @Test
    fun `invalid edit is rejected before reaching gateway`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack())
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)
        awaitLoaded(viewModel)

        viewModel.beginEdit()
        viewModel.updateEditDraft(viewModel.uiState.value.editDraft.copy(iconUrl = "not-a-url"))
        viewModel.saveEdit()

        val state = viewModel.uiState.filter { it.errorMessage != null && !it.savingEdit }.first()
        assertFalse(state.savingEdit)
        assertTrue(gateway.mutations.isEmpty())
    }

    @Test
    fun `invalid name is rejected before reaching gateway`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack())
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)
        awaitLoaded(viewModel)

        viewModel.beginEdit()
        viewModel.updateEditDraft(viewModel.uiState.value.editDraft.copy(name = "!"))
        viewModel.saveEdit()

        viewModel.uiState.filter { it.errorMessage != null && !it.savingEdit }.first()
        assertTrue(gateway.mutations.isEmpty())
    }

    @Test
    fun `saving edit updates metadata and reports completion`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack())
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)
        awaitLoaded(viewModel)

        viewModel.beginEdit()
        viewModel.updateEditDraft(viewModel.uiState.value.editDraft.copy(name = "新名称"))
        viewModel.saveEdit()

        val event = viewModel.events.first()
        val mutation = assertIs<ModpackInfoMutation.UpdateOptions>(gateway.mutations.single())
        assertEquals("新名称", mutation.options.name)
        assertIs<ModpackInfoEvent.EditSaved>(event)
        viewModel.uiState.filter { !it.loading && gateway.loadCount >= 2 }.first()
    }

    @Test
    fun `deleting version uses its name and reloads detail`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack())
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)
        awaitLoaded(viewModel)

        viewModel.deleteVersion("1.0")

        assertIs<ModpackInfoEvent.ShowSnackbar>(viewModel.events.first())
        viewModel.uiState.filter { !it.loading && gateway.loadCount >= 2 }.first()
        assertEquals(ModpackInfoMutation.DeleteVersion("1.0"), gateway.mutations.single())
    }

    @Test
    fun `deleting pack emits navigation event`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack())
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)
        awaitLoaded(viewModel)

        viewModel.deletePack()

        assertIs<ModpackInfoEvent.PackDeleted>(viewModel.events.first())
        assertEquals(ModpackInfoMutation.DeletePack, gateway.mutations.single())
    }

    @Test
    fun `rebuilding version uses its name`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack())
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)
        awaitLoaded(viewModel)

        viewModel.rebuildVersion("1.0")

        assertIs<ModpackInfoEvent.ShowSnackbar>(viewModel.events.first())
        assertEquals(ModpackInfoMutation.RebuildVersion("1.0"), gateway.mutations.single())
    }

    @Test
    fun `installing version emits queued task id`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack(), runId = "run-7")
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)
        awaitLoaded(viewModel)

        viewModel.installVersion("1.0")

        val event = assertIs<ModpackInfoEvent.InstallQueued>(viewModel.events.first())
        assertEquals("run-7", event.runId)
    }

    @Test
    fun `installed version requests redownload confirmation`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack(), versionInstalled = true)
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)
        awaitLoaded(viewModel)

        viewModel.requestDownload("1.0")

        val event = assertIs<ModpackInfoEvent.ConfirmRedownload>(viewModel.events.first())
        assertEquals("1.0", event.versionName)
    }

    @Test
    fun `missing local version requests download method`() = runBlocking {
        val gateway = FakeModpackInfoGateway(detail = testPack())
        val viewModel = ModpackInfoViewModel(MODPACK_ID, gateway)
        awaitLoaded(viewModel)

        viewModel.requestDownload("1.0")

        val event = assertIs<ModpackInfoEvent.SelectDownloadMethod>(viewModel.events.first())
        assertEquals("1.0", event.versionName)
    }

    private suspend fun awaitLoaded(viewModel: ModpackInfoViewModel): ModpackInfoUiState =
        viewModel.uiState.filter { !it.loading }.first()

    private class FakeModpackInfoGateway(
        var detail: Modpack.DetailVo?,
        private val hydrationFailure: Throwable? = null,
        private val runId: String = "run-1",
        private val versionInstalled: Boolean = false,
    ) : ModpackInfoGateway {
        val mutations = mutableListOf<ModpackInfoMutation>()
        var hydrateCalled = false
        var loadCount = 0

        override suspend fun loadDetail(modpackId: String): Result<Modpack.DetailVo?> {
            loadCount++
            return Result.success(detail)
        }

        override suspend fun mutate(modpackId: String, mutation: ModpackInfoMutation): Result<Unit> {
            mutations += mutation
            return Result.success(Unit)
        }

        override fun hydrateMods(mods: List<Mod>) = flow {
            hydrateCalled = true
            hydrationFailure?.let { throw it }
            emit(mods.toUiMods())
            emit(mods.map { it.copy(slug = "${it.slug}-hydrated") }.toUiMods())
        }

        override suspend fun isVersionInstalled(
            pack: Modpack.DetailVo,
            version: Modpack.Version,
        ): Result<Boolean> = Result.success(versionInstalled)

        override fun queueInstall(pack: Modpack.DetailVo, version: Modpack.Version): Result<String> =
            Result.success(runId)
    }

    private companion object {
        const val MODPACK_ID = "66a000000000000000000001"

        fun testPack(mods: MutableList<Mod> = mutableListOf()) = Modpack.DetailVo(
            _id = ObjectId(MODPACK_ID),
            name = "测试整合包",
            authorId = ObjectId("66a000000000000000000002"),
            modCount = mods.size,
            modloader = ModLoader.neoforge,
            mcVer = McVersion.V211,
            versions = listOf(
                Modpack.Version(
                    time = 1L,
                    modpackId = ObjectId(MODPACK_ID),
                    name = "1.0",
                    changelog = "",
                    status = Modpack.Status.OK,
                    mods = mods,
                )
            ),
        )

        fun testMod() = Mod(
            platform = "mr",
            projectId = "project",
            slug = "example-mod",
            fileId = "file",
            hash = "abc",
        )
    }
}
