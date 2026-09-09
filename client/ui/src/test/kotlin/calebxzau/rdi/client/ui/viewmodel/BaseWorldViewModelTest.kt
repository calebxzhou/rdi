package calebxzau.rdi.client.ui.viewmodel

import calebxzau.rdi.common.model.BaseWorld
import calebxzau.rdi.client.service.BaseWorldUploadRequest
import calebxzau.rdi.client.service.validateBaseWorldLevelType
import calebxzau.rdi.client.service.validateBaseWorldName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseWorldViewModelTest {
    @Test
    fun `base world name validation matches modpack rules`() {
        listOf("", " ab", "ab ", "ab", "地图/模板").forEach { value ->
            assertTrue(validateBaseWorldName(value).isFailure, value)
        }
        listOf("Test World", "地图 World", "模板.v1_2-测试").forEach { value ->
            assertTrue(validateBaseWorldName(value).isSuccess, value)
        }
    }

    @Test
    fun `submit reports invalid raw name without trimming it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val directory = Files.createTempDirectory("base-world-name-submit").toFile()
        directory.resolve("level.dat").writeText("level")
        try {
            val submitter = RecordingSubmitter()
            val viewModel = BaseWorldUploadViewModel(
                picker = { directory },
                directoryValidator = ::validateBaseWorldDirectory,
                submitter = submitter,
                isRunActive = { true },
            )
            viewModel.setOwner("owner")
            viewModel.selectDirectory()
            advanceUntilIdle()
            viewModel.updateName(" Test World")
            viewModel.submit()

            assertEquals("地图模板名称只能包含字母、数字、汉字、空格或._-", viewModel.uiState.value.errorMessage)
            assertTrue(submitter.requests.isEmpty())
        } finally {
            directory.deleteRecursively()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `directory validation requires a non empty level dat`() {
        val directory = Files.createTempDirectory("base-world-validation").toFile()
        try {
            assertTrue(validateBaseWorldDirectory(directory).isFailure)
            directory.resolve("level.dat").writeText("level")
            assertTrue(validateBaseWorldDirectory(directory).isSuccess)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `generator settings must be a json object`() {
        assertTrue(validateBaseWorldGeneratorSettings("{\"seed\":1}").isSuccess)
        assertTrue(validateBaseWorldGeneratorSettings("[1]").isFailure)
        assertTrue(validateBaseWorldGeneratorSettings("not-json").isFailure)
        assertTrue(validateBaseWorldGeneratorSettings("").isSuccess)
    }

    @Test
    fun `level type validator accepts standard resource locations`() {
        listOf(
            "minecraft:normal",
            "minecraft:flat",
            "skyblockbuilder:skyblock",
            "example:world/types/void",
            "mod_name:some.path-value_1",
        ).forEach { value ->
            assertTrue(validateBaseWorldLevelType(value).isSuccess, value)
        }
    }

    @Test
    fun `level type validator rejects malformed resource locations`() {
        listOf(
            "normal",
            "Minecraft:normal",
            ":normal",
            "minecraft:",
            "minecraft:normal type",
            "minecraft:normal:extra",
            "mod/name:path",
            "minecraft:path?",
        ).forEach { value ->
            assertTrue(validateBaseWorldLevelType(value).isFailure, value)
        }
        assertEquals(
            "地形类型必须是foo:bar格式",
            validateBaseWorldLevelType("normal").exceptionOrNull()?.message,
        )
    }

    @Test
    fun `level type selection and custom editing preserve committed state on failure or cancel`() {
        val viewModel = BaseWorldUploadViewModel(
            submitter = RecordingSubmitter(),
            isRunActive = { true },
        )

        assertEquals("minecraft:normal", viewModel.uiState.value.levelType)
        assertEquals(BaseWorldLevelTypeChoice.Normal, viewModel.uiState.value.levelTypeChoice)

        viewModel.selectLevelType(BaseWorldLevelTypeChoice.Flat)
        assertEquals("minecraft:flat", viewModel.uiState.value.levelType)
        viewModel.selectLevelType(BaseWorldLevelTypeChoice.Skyblock)
        assertEquals("skyblockbuilder:skyblock", viewModel.uiState.value.levelType)

        viewModel.selectLevelType(BaseWorldLevelTypeChoice.Custom)
        assertEquals(BaseWorldLevelTypeChoice.Skyblock, viewModel.uiState.value.levelTypeChoice)
        assertEquals("skyblockbuilder:skyblock", viewModel.uiState.value.levelType)

        viewModel.beginCustomLevelType()
        viewModel.updateCustomLevelTypeText("bad value")
        assertFalse(viewModel.applyCustomLevelType())
        assertEquals(BaseWorldLevelTypeChoice.Skyblock, viewModel.uiState.value.levelTypeChoice)
        assertEquals("skyblockbuilder:skyblock", viewModel.uiState.value.levelType)
        assertEquals("地形类型必须是foo:bar格式", viewModel.uiState.value.customLevelTypeError)

        viewModel.cancelCustomLevelType()
        assertEquals(BaseWorldLevelTypeChoice.Skyblock, viewModel.uiState.value.levelTypeChoice)
        assertEquals("skyblockbuilder:skyblock", viewModel.uiState.value.levelType)
        assertNull(viewModel.uiState.value.customLevelTypeError)

        viewModel.beginCustomLevelType()
        viewModel.updateCustomLevelTypeText(" example:world/types/void ")
        assertTrue(viewModel.applyCustomLevelType())
        assertEquals(BaseWorldLevelTypeChoice.Custom, viewModel.uiState.value.levelTypeChoice)
        assertEquals("example:world/types/void", viewModel.uiState.value.levelType)
    }

    @Test
    fun `picker cancellation keeps the previous selection`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val first = Files.createTempDirectory("base-world-cancel-first").toFile()
        first.resolve("level.dat").writeText("level")
        val firstPicker = CompletableDeferred<File?>()
        val cancelPicker = CompletableDeferred<File?>()
        var pickerCount = 0
        try {
            val viewModel = BaseWorldUploadViewModel(
                picker = {
                    pickerCount++
                    if (pickerCount == 1) firstPicker.await() else cancelPicker.await()
                },
                directoryValidator = ::validateBaseWorldDirectory,
                submitter = RecordingSubmitter(),
                isRunActive = { true },
            )
            viewModel.setOwner("owner")
            viewModel.selectDirectory()
            runCurrent()
            firstPicker.complete(first)
            runCurrent()
            assertEquals(first, viewModel.uiState.value.directory)

            viewModel.selectDirectory()
            runCurrent()
            cancelPicker.complete(null)
            runCurrent()
            assertEquals(first, viewModel.uiState.value.directory)
        } finally {
            first.deleteRecursively()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `stale picker result after account change is ignored`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val selected = Files.createTempDirectory("base-world-stale-picker").toFile()
        selected.resolve("level.dat").writeText("level")
        val pickerResult = CompletableDeferred<File?>()
        try {
            val viewModel = BaseWorldUploadViewModel(
                picker = { pickerResult.await() },
                directoryValidator = ::validateBaseWorldDirectory,
                submitter = RecordingSubmitter(),
                isRunActive = { true },
            )
            viewModel.setOwner("owner-a")
            viewModel.selectDirectory()
            runCurrent()
            viewModel.setOwner("owner-b")
            pickerResult.complete(selected)
            runCurrent()

            assertEquals("owner-b", viewModel.uiState.value.ownerId)
            assertNull(viewModel.uiState.value.directory)
        } finally {
            selected.deleteRecursively()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `selection auto fills folder name and preserves manual name`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val first = Files.createTempDirectory("base-world-first").toFile()
        val second = Files.createTempDirectory("base-world-second").toFile()
        first.resolve("level.dat").writeText("level")
        second.resolve("level.dat").writeText("level")
        try {
            var next = first
            val viewModel = BaseWorldUploadViewModel(
                picker = { next },
                directoryValidator = ::validateBaseWorldDirectory,
                submitter = RecordingSubmitter(),
                isRunActive = { true },
            )
            viewModel.setOwner("owner")
            viewModel.selectDirectory()
            runCurrent()
            assertEquals(first.name, viewModel.uiState.value.name)

            viewModel.updateName("自定义名称")
            next = second
            viewModel.selectDirectory()
            runCurrent()
            assertEquals("自定义名称", viewModel.uiState.value.name)
        } finally {
            first.deleteRecursively()
            second.deleteRecursively()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `submission captures immutable request and deduplicates same active request`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val directory = Files.createTempDirectory("base-world-submit").toFile()
        directory.resolve("level.dat").writeText("level")
        try {
            val submitter = RecordingSubmitter()
            val viewModel = BaseWorldUploadViewModel(
                picker = { directory },
                directoryValidator = ::validateBaseWorldDirectory,
                submitter = submitter,
                isRunActive = { true },
            )
            viewModel.setOwner("owner")
            viewModel.selectDirectory()
            advanceUntilIdle()
            viewModel.updateName("模板")
            viewModel.selectLevelType(BaseWorldLevelTypeChoice.Flat)
            viewModel.updateGeneratorSettings("{\"seed\":1}")
            val firstRun = viewModel.submit()
            viewModel.updateName("后来改名")
            val secondRun = viewModel.submit()

            assertEquals("run-1", firstRun)
            assertEquals(firstRun, secondRun)
            assertEquals("模板", submitter.requests.single().name)
            assertEquals("minecraft:flat", submitter.requests.single().levelType)
            assertEquals("{\"seed\":1}", submitter.requests.single().generatorSettings)
        } finally {
            directory.deleteRecursively()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    private class RecordingSubmitter : BaseWorldUploadSubmitter {
        val requests = mutableListOf<BaseWorldUploadRequest>()

        override fun submit(ownerId: String, request: BaseWorldUploadRequest): Result<String> {
            requests += request
            return Result.success("run-1")
        }
    }
}

class BaseWorldListViewModelTest {
    @Test
    fun `failed load is visible and retry can recover`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val gateway = FakeListGateway()
        val viewModel = BaseWorldListViewModel(gateway)

        try {
            viewModel.refresh("owner")
            advanceUntilIdle()
            assertEquals("加载失败", viewModel.uiState.value.errorMessage)

            gateway.result = Result.success(listOf(testWorld()))
            viewModel.refresh("owner")
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.errorMessage)
            assertEquals("模板", viewModel.uiState.value.worlds.single().name)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `stale owner and refresh results cannot replace latest list`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val first = CompletableDeferred<Result<List<BaseWorld>>>()
        val second = CompletableDeferred<Result<List<BaseWorld>>>()
        val gateway = DeferredListGateway(mutableListOf(first, second))
        val viewModel = BaseWorldListViewModel(gateway)
        try {
            viewModel.refresh("owner-a")
            runCurrent()
            viewModel.refresh("owner-b")
            runCurrent()
            first.complete(Result.success(listOf(testWorld("旧列表"))))
            second.complete(Result.success(listOf(testWorld("最新列表"))))
            advanceUntilIdle()

            assertEquals("owner-b", viewModel.uiState.value.ownerId)
            assertEquals("最新列表", viewModel.uiState.value.worlds.single().name)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `successful deletion removes only the requested world`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val target = testWorld("要删除")
        val retained = testWorld("保留")
        val gateway = FakeListGateway().apply { result = Result.success(listOf(target, retained)) }
        val viewModel = BaseWorldListViewModel(gateway)
        try {
            viewModel.refresh("owner")
            advanceUntilIdle()
            viewModel.delete("owner", target.id)
            advanceUntilIdle()

            assertEquals(listOf(retained), viewModel.uiState.value.worlds)
            assertNull(viewModel.uiState.value.deletingWorldId)
            assertNull(viewModel.uiState.value.deletionErrorMessage)
            assertEquals(listOf(target.id), gateway.deletedWorlds)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed deletion preserves world and exposes error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val target = testWorld()
        val gateway = FakeListGateway().apply {
            result = Result.success(listOf(target))
            deleteResult = Result.failure(IllegalStateException("删除失败"))
        }
        val viewModel = BaseWorldListViewModel(gateway)
        try {
            viewModel.refresh("owner")
            advanceUntilIdle()
            viewModel.delete("owner", target.id)
            advanceUntilIdle()

            assertEquals(listOf(target), viewModel.uiState.value.worlds)
            assertNull(viewModel.uiState.value.deletingWorldId)
            assertEquals("删除失败", viewModel.uiState.value.deletionErrorMessage)

            viewModel.prepareDeletion()
            assertNull(viewModel.uiState.value.deletionErrorMessage)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `same owner refresh keeps deletion active and filters stale list after deletion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val target = testWorld("要删除")
        val retained = testWorld("保留")
        val staleList = CompletableDeferred<Result<List<BaseWorld>>>()
        val deletion = CompletableDeferred<Result<Unit>>()
        val gateway = SameOwnerRefreshGateway(
            initial = listOf(target, retained),
            staleList = staleList,
            deletion = deletion,
        )
        val viewModel = BaseWorldListViewModel(gateway)
        try {
            viewModel.refresh("owner")
            advanceUntilIdle()
            viewModel.delete("owner", target.id)
            runCurrent()

            viewModel.refresh("owner")
            runCurrent()
            assertEquals(target.id, viewModel.uiState.value.deletingWorldId)

            deletion.complete(Result.success(Unit))
            runCurrent()
            staleList.complete(Result.success(listOf(target, retained)))
            advanceUntilIdle()

            assertEquals(listOf(retained), viewModel.uiState.value.worlds)
            assertNull(viewModel.uiState.value.deletingWorldId)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `owner round trip keeps a pending deletion filtered`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val target = testWorld("待删除")
        val other = testWorld("其他账户地图")
        val deletion = CompletableDeferred<Result<Unit>>()
        val gateway = OwnerAwareGateway(
            lists = mapOf("owner-a" to listOf(target), "owner-b" to listOf(other)),
            deletion = deletion,
        )
        val viewModel = BaseWorldListViewModel(gateway)
        try {
            viewModel.refresh("owner-a")
            advanceUntilIdle()
            viewModel.delete("owner-a", target.id)
            runCurrent()

            viewModel.refresh("owner-b")
            advanceUntilIdle()
            viewModel.refresh("owner-a")
            advanceUntilIdle()
            assertEquals(listOf(target), viewModel.uiState.value.worlds)

            deletion.complete(Result.success(Unit))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.worlds.isEmpty())
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed deletion after same owner refresh preserves target and reports error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val target = testWorld("要删除")
        val staleList = CompletableDeferred<Result<List<BaseWorld>>>()
        val deletion = CompletableDeferred<Result<Unit>>()
        val gateway = SameOwnerRefreshGateway(
            initial = listOf(target),
            staleList = staleList,
            deletion = deletion,
        )
        val viewModel = BaseWorldListViewModel(gateway)
        try {
            viewModel.refresh("owner")
            advanceUntilIdle()
            viewModel.delete("owner", target.id)
            runCurrent()
            viewModel.refresh("owner")
            runCurrent()
            staleList.complete(Result.success(listOf(target)))
            advanceUntilIdle()

            deletion.complete(Result.failure(IllegalStateException("删除失败")))
            advanceUntilIdle()
            assertEquals(listOf(target), viewModel.uiState.value.worlds)
            assertEquals("删除失败", viewModel.uiState.value.deletionErrorMessage)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `duplicate deletion calls are suppressed while request is active`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val target = testWorld()
        val completion = CompletableDeferred<Result<Unit>>()
        val gateway = FakeListGateway().apply {
            result = Result.success(listOf(target))
            deleteResponse = completion
        }
        val viewModel = BaseWorldListViewModel(gateway)
        try {
            viewModel.refresh("owner")
            advanceUntilIdle()
            viewModel.delete("owner", target.id)
            viewModel.delete("owner", target.id)
            runCurrent()
            assertEquals(1, gateway.deleteCalls)

            completion.complete(Result.success(Unit))
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.worlds.contains(target))
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `stale deletion completion cannot mutate a refreshed owner list`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val target = testWorld("旧地图")
        val current = testWorld("新地图")
        val completion = CompletableDeferred<Result<Unit>>()
        val gateway = OwnerAwareGateway(
            lists = mapOf("owner-a" to listOf(target), "owner-b" to listOf(current)),
            deletion = completion,
        )
        val viewModel = BaseWorldListViewModel(gateway)
        try {
            viewModel.refresh("owner-a")
            advanceUntilIdle()
            viewModel.delete("owner-a", target.id)
            runCurrent()
            viewModel.refresh("owner-b")
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.deletingWorldId)
            assertTrue(viewModel.uiState.value.deletionInProgress)
            viewModel.delete("owner-b", current.id)
            runCurrent()
            assertEquals(1, gateway.deleteCalls)
            completion.complete(Result.success(Unit))
            advanceUntilIdle()

            assertEquals("owner-b", viewModel.uiState.value.ownerId)
            assertEquals(listOf(current), viewModel.uiState.value.worlds)
            assertNull(viewModel.uiState.value.deletionErrorMessage)
            assertTrue(!viewModel.uiState.value.deletionInProgress)

            viewModel.delete("owner-b", current.id)
            advanceUntilIdle()
            assertEquals(2, gateway.deleteCalls)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed deletion survives owner switch and can reappear after returning`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val target = testWorld("旧地图")
        val other = testWorld("新地图")
        val completion = CompletableDeferred<Result<Unit>>()
        val gateway = OwnerAwareGateway(
            lists = mapOf("owner-a" to listOf(target), "owner-b" to listOf(other)),
            deletion = completion,
        )
        val viewModel = BaseWorldListViewModel(gateway)
        try {
            viewModel.refresh("owner-a")
            advanceUntilIdle()
            viewModel.delete("owner-a", target.id)
            runCurrent()
            viewModel.refresh("owner-b")
            advanceUntilIdle()
            completion.complete(Result.failure(IllegalStateException("删除失败")))
            advanceUntilIdle()
            viewModel.refresh("owner-a")
            advanceUntilIdle()

            assertEquals(listOf(target), viewModel.uiState.value.worlds)
            assertNull(viewModel.uiState.value.deletionErrorMessage)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    private class FakeListGateway : BaseWorldListGateway {
        var result: Result<List<BaseWorld>> = Result.failure(IllegalStateException("加载失败"))
        var deleteResult: Result<Unit> = Result.success(Unit)
        var deleteResponse: CompletableDeferred<Result<Unit>>? = null
        var deleteCalls = 0
        val deletedWorlds = mutableListOf<java.util.UUID>()

        override suspend fun list(ownerId: String): Result<List<BaseWorld>> = result

        override suspend fun delete(ownerId: String, worldId: java.util.UUID): Result<Unit> {
            deleteCalls++
            deletedWorlds += worldId
            return deleteResponse?.await() ?: deleteResult
        }
    }

    private class DeferredListGateway(
        private val responses: MutableList<CompletableDeferred<Result<List<BaseWorld>>>>,
    ) : BaseWorldListGateway {
        override suspend fun list(ownerId: String): Result<List<BaseWorld>> =
            withContext(NonCancellable) { responses.removeAt(0).await() }

        override suspend fun delete(ownerId: String, worldId: java.util.UUID): Result<Unit> = Result.success(Unit)
    }

    private class OwnerAwareGateway(
        private val lists: Map<String, List<BaseWorld>>,
        private val deletion: CompletableDeferred<Result<Unit>>,
    ) : BaseWorldListGateway {
        var deleteCalls = 0

        override suspend fun list(ownerId: String): Result<List<BaseWorld>> =
            Result.success(lists.getValue(ownerId))

        override suspend fun delete(ownerId: String, worldId: java.util.UUID): Result<Unit> {
            deleteCalls++
            return withContext(NonCancellable) { deletion.await() }
        }
    }

    private class SameOwnerRefreshGateway(
        private val initial: List<BaseWorld>,
        private val staleList: CompletableDeferred<Result<List<BaseWorld>>>,
        private val deletion: CompletableDeferred<Result<Unit>>,
    ) : BaseWorldListGateway {
        private var listCalls = 0

        override suspend fun list(ownerId: String): Result<List<BaseWorld>> {
            listCalls++
            return if (listCalls == 1) Result.success(initial)
            else withContext(NonCancellable) { staleList.await() }
        }

        override suspend fun delete(ownerId: String, worldId: java.util.UUID): Result<Unit> =
            withContext(NonCancellable) { deletion.await() }
    }

    private fun testWorld(name: String = "模板") = BaseWorld(
        id = UUID.randomUUID(),
        ownerId = UUID.randomUUID(),
        name = name,
        levelType = "normal",
        generatorSettings = null,
        size = 1L,
    )
}
