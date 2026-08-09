package calebxzau.rdi.client.ui.viewmodel

import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.service.TestStatus
import calebxzhou.rdi.client.service.toUiMods
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task2Entry
import calebxzau.rdi.client.packproc.LoadedLocalModpack
import calebxzau.rdi.client.packproc.LoadedServerPackResult
import calebxzau.rdi.client.packproc.LocalModpackSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModpackUploadViewModelTest {
    @Test
    fun `loading client pack enters edit mode with normalized draft`() = runBlocking {
        val prepared = PreparedClientPack(testPack(), listOf(UiMod(testMod())))
        val gateway = FakeModpackUploadGateway(preparedClientPack = prepared)
        val viewModel = ModpackUploadViewModel(gateway)

        viewModel.loadClientPack(File("client-pack.mrpack"))

        val event = assertIs<ModpackUploadEvent.ClientPackLoaded>(viewModel.events.first())
        val state = viewModel.uiState.filter { !it.loading && it.editMode }.first()
        assertEquals(0, event.initialTab)
        assertEquals("测试包", state.draft.name)
        assertEquals("1_0", state.draft.versionName)
        assertEquals("1.21.1", state.mcVersionText)
        assertEquals("neoforge", state.modloaderText)
        assertEquals(1, state.uiMods.size)
        assertEquals(TestStatus.NOT_RUN, state.clientTestStatus)
        assertEquals(TestStatus.NOT_RUN, state.serverTestStatus)
    }

    @Test
    fun `version draft replaces spaces with underscores`() {
        val viewModel = ModpackUploadViewModel(FakeModpackUploadGateway())

        viewModel.updateVersionName("release candidate 1")

        assertEquals("release_candidate_1", viewModel.uiState.value.draft.versionName)
    }

    @Test
    fun `submitting before client pack reports error without queueing`() = runBlocking {
        val gateway = FakeModpackUploadGateway()
        val viewModel = ModpackUploadViewModel(gateway)

        viewModel.submitUpload()

        val state = viewModel.uiState.filter { it.errorMessage != null }.first()
        assertEquals("请先选择整合包文件", state.errorMessage)
        assertNull(gateway.submission)
    }

    @Test
    fun `valid upload emits submitted task id`() = runBlocking {
        val gateway = FakeModpackUploadGateway(
            preparedClientPack = PreparedClientPack(testPack(), testPack().mods.toUiMods()),
            ignoreModpackTestInitially = true,
            uploadRunId = "upload-9",
        )
        val viewModel = ModpackUploadViewModel(gateway)
        viewModel.loadClientPack(File("client-pack.mrpack"))
        viewModel.events.first()
        viewModel.updateDraft(
            viewModel.uiState.value.draft.copy(
                name = "新整合包",
                versionName = "2.0",
            )
        )

        viewModel.submitUpload()

        val event = assertIs<ModpackUploadEvent.UploadSubmitted>(viewModel.events.first())
        assertEquals("upload-9", event.runId)
        assertEquals("新整合包", gateway.submission?.draft?.name)
        assertEquals("2.0", gateway.submission?.draft?.versionName)
    }

    @Test
    fun `missing mods require confirmation before client test`() = runBlocking {
        val missing = testMod()
        val gateway = FakeModpackUploadGateway(
            preparedClientPack = PreparedClientPack(testPack(), listOf(UiMod(missing))),
            missingMods = listOf(missing),
        )
        val viewModel = ModpackUploadViewModel(gateway)
        viewModel.loadClientPack(File("client-pack.mrpack"))
        viewModel.events.first()

        viewModel.startClientTest()

        val state = viewModel.uiState.filter { it.pendingMissingModDownload != null }.first()
        assertEquals("客户端测试", state.pendingMissingModDownload?.usage)
        assertEquals(listOf(missing), state.pendingMissingModDownload?.mods)
        assertEquals(TestStatus.NOT_RUN, state.clientTestStatus)
    }

    @Test
    fun `selecting update target copies its name and categories`() {
        val viewModel = ModpackUploadViewModel(FakeModpackUploadGateway())
        val target = Modpack.BriefVo(
            id = ObjectId("66a000000000000000000001"),
            name = "已有包",
            categories = listOf(Modpack.Category.TECH),
        )

        viewModel.chooseUpdateMode(target)

        val state = viewModel.uiState.value
        assertEquals(ModpackUploadMode.UPDATE, state.uploadMode)
        assertEquals(target, state.selectedUpdateTarget)
        assertEquals("已有包", state.draft.name)
        assertEquals(listOf(Modpack.Category.TECH), state.draft.categories)
    }

    @Test
    fun `loading server pack updates merged mods and summary`() = runBlocking {
        val mergedMod = testMod().copy(side = Mod.Side.BOTH)
        val gateway = FakeModpackUploadGateway(
            preparedClientPack = PreparedClientPack(testPack(), testPack().mods.toUiMods()),
            preparedServerPack = PreparedServerPack(
                pack = LoadedServerPackResult(
                    mods = listOf(mergedMod),
                    serverExtraFiles = emptyList(),
                ),
                uiMods = listOf(UiMod(mergedMod)),
            ),
        )
        val viewModel = ModpackUploadViewModel(gateway)
        viewModel.loadClientPack(File("client-pack.mrpack"))
        viewModel.events.first()

        viewModel.loadServerPack(File("server"))

        val state = viewModel.uiState.filter { !it.loading && it.serverPackName != null }.first()
        assertEquals(Mod.Side.BOTH, state.uiMods.single().side)
        assertEquals("已选择服务端(1个mod)", state.serverPackName)
    }

    @Test
    fun `closing screen cleans workspace before navigation`() = runBlocking {
        val gateway = FakeModpackUploadGateway()
        val viewModel = ModpackUploadViewModel(gateway)

        viewModel.closeScreen()

        assertIs<ModpackUploadEvent.NavigateBack>(viewModel.events.first())
        assertTrue(gateway.cleanupCalled)
    }

    private class FakeModpackUploadGateway(
        private val preparedClientPack: PreparedClientPack? = null,
        private val preparedServerPack: PreparedServerPack? = null,
        private val missingMods: List<Mod> = emptyList(),
        override val ignoreModpackTestInitially: Boolean = false,
        private val uploadRunId: String = "upload-1",
    ) : ModpackUploadGateway {
        override val taskEntries: StateFlow<List<Task2Entry>> = MutableStateFlow(emptyList())
        var submission: ModpackUploadSubmission? = null
        var cleanupCalled = false

        override suspend fun loadUploadedModpacks(): Result<List<Modpack.BriefVo>> =
            Result.success(emptyList())

        override suspend fun prepareClientPack(
            file: File,
            onProgress: (calebxzhou.rdi.common.model.LoadProgress) -> Unit,
        ): Result<PreparedClientPack> = preparedClientPack?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("missing prepared pack"))

        override suspend fun prepareServerPack(
            directory: File,
            clientUiMods: List<UiMod>,
            onProgress: (calebxzhou.rdi.common.model.LoadProgress) -> Unit,
        ): Result<PreparedServerPack> = preparedServerPack?.let { Result.success(it) }
            ?: Result.failure(UnsupportedOperationException())

        override suspend fun validateRuntime(mcVersion: McVersion): Result<Unit> = Result.success(Unit)

        override suspend fun findMissingMods(mods: List<Mod>): Result<List<Mod>> =
            Result.success(missingMods)

        override fun queueMissingModDownload(mods: List<Mod>): Result<String> = Result.success("download-1")

        override fun queueTestServer(pack: LoadedLocalModpack): Result<String> = Result.success("server-1")

        override fun queueUpload(submission: ModpackUploadSubmission): Result<String> {
            this.submission = submission
            return Result.success(uploadRunId)
        }

        override fun enableIgnoreModpackTest(): Result<Unit> = Result.success(Unit)

        override suspend fun cleanup(): Result<Unit> {
            cleanupCalled = true
            return Result.success(Unit)
        }
    }

    private companion object {
        fun testPack() = LoadedLocalModpack(
            sourceType = LocalModpackSourceType.MODRINTH,
            sourceDir = File("source"),
            packName = "测试包",
            packVersion = "1 0",
            mcVersion = McVersion.V211,
            modloader = ModLoader.neoforge,
            mods = listOf(testMod()),
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
