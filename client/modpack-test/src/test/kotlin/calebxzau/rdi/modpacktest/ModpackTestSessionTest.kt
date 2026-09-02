package calebxzau.rdi.modpacktest

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzau.rdi.client.packproc.LoadedLocalModpack
import calebxzau.rdi.client.packproc.LocalModpackSourceType
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ModpackTestSessionTest {
    @Test
    fun `server success line marks session passed`() = runBlocking {
        val fixture = TestFixture(serverLine = "Done (3.25s)! For help")
        val session = fixture.session(ModpackTestTarget.SERVER)
        try {
            session.start(emptyList()).getOrThrow()
            val state = withTimeout(2_000) {
                session.state.filter { it.status == ModpackTestStatus.PASSED }.first()
            }

            assertEquals("3.25", state.passSeconds)
        } finally {
            session.close()
            fixture.close()
        }
    }

    @Test
    fun `client success marker marks session passed`() = runBlocking {
        val fixture = TestFixture(clientLine = CLIENT_TEST_SUCCESS_MARKER)
        val session = fixture.session(ModpackTestTarget.CLIENT)
        try {
            session.start(emptyList()).getOrThrow()
            val state = withTimeout(2_000) {
                session.state.filter { it.status == ModpackTestStatus.PASSED }.first()
            }

            assertEquals(ModpackTestStatus.PASSED, state.status)
        } finally {
            session.close()
            fixture.close()
        }
    }
}

private class TestFixture(
    clientLine: String? = null,
    serverLine: String? = null,
) : AutoCloseable {
    private val root = Files.createTempDirectory("modpack-test-").toFile()
    private val sourceDir = root.resolve("source").apply { mkdirs() }
    private val launcher = FakeModpackTestLauncher(clientLine, serverLine)
    private val environment = ModpackTestEnvironment(
        paths = ModpackTestPaths(
            workDir = root.resolve("work"),
            librariesDir = root.resolve("libraries"),
        ),
        launcher = launcher,
    )

    fun session(target: ModpackTestTarget): ModpackTestSession = ModpackTestSession(
        loadedModpack = LoadedLocalModpack(
            sourceType = LocalModpackSourceType.CURSEFORGE,
            sourceDir = sourceDir,
            packName = "test",
            packVersion = "1",
            mcVersion = McVersion.V201,
            modloader = ModLoader.forge,
            mods = emptyList(),
        ),
        target = target,
        environment = environment,
        modSourceResolver = ModpackTestModSourceResolver { _, testDir ->
            Result.success(testDir.resolve(".rdi-mod-sources"))
        },
    )

    override fun close() {
        root.deleteRecursively()
    }
}

private class FakeModpackTestLauncher(
    private val clientLine: String?,
    private val serverLine: String?,
) : ModpackTestLauncher {
    override suspend fun prepareClientLoader(
        mcVersion: McVersion,
        loader: ModLoader,
        onProgress: (String) -> Unit,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun prepareClientLibraries(
        mcVersion: McVersion,
        versionId: String,
        versionDir: File,
        onProgress: (String) -> Unit,
    ): Result<Unit> = Result.success(Unit)

    override fun launchClient(
        mcVersion: McVersion,
        versionId: String,
        versionDir: File,
        onLine: (String) -> Unit,
    ): Result<ModpackTestProcess> {
        clientLine?.let(onLine)
        return Result.success(FakeModpackTestProcess())
    }

    override fun launchServer(
        mcVersion: McVersion,
        loaderVersion: ModLoader.Version,
        workDir: File,
        onLine: (String) -> Unit,
    ): Result<ModpackTestProcess> {
        serverLine?.let(onLine)
        return Result.success(FakeModpackTestProcess())
    }
}

private class FakeModpackTestProcess : ModpackTestProcess {
    private var alive = true

    override fun isAlive(): Boolean = alive

    override fun stop(): Result<Unit> = runCatching { alive = false }

    override suspend fun waitFor(): Result<Int> = Result.success(0)
}
