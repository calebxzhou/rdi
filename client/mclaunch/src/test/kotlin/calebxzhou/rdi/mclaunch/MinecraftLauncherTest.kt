package calebxzau.rdi.mclaunch

import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzau.rdi.mclaunch.model.MojangDownloadArtifact
import calebxzau.rdi.mclaunch.model.MojangLibrary
import calebxzau.rdi.mclaunch.model.MojangLibraryDownloads
import calebxzau.rdi.mclaunch.model.MojangVersionDownloads
import calebxzau.rdi.mclaunch.model.MojangVersionManifest
import calebxzhou.rdi.common.model.McVersion
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinecraftLauncherTest {
    @Test
    fun usesCurrentJava25Path() {
        val root = Files.createTempDirectory("mclaunch-java25").toFile()
        try {
            val command = captureLaunchCommand(root, currentJavaMajor = 25)
            assertEquals("/current/java", command.first())
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun rejectsNonJava25CurrentRuntime() {
        val root = Files.createTempDirectory("mclaunch-java25-required").toFile()
        try {
            var started = false
            val launcher = launcher(
                root = root,
                java = MinecraftJava25Config(
                    currentJavaPath = "/current/java",
                    currentJavaMajor = 21,
                    maxMemoryMb = 1024,
                ),
                onStart = { started = true },
            )
            val result = launcher.launch(request(root), onLine = {})
            assertTrue(result.isFailure)
            assertNull(result.getOrNull())
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Java25"))
            assertTrue(!started)
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun launchOverridesUsePackJavaAndMaxMemory() {
        val root = Files.createTempDirectory("mclaunch-pack-options").toFile()
        try {
            val command = captureLaunchCommand(
                root = root,
                currentJavaMajor = 25,
                launchOverrides = MinecraftLaunchOverrides(
                    javaPath = "/custom/jdk/bin/java.exe",
                    maxMemoryMb = 8192,
                ),
            )
            assertEquals("/custom/jdk/bin/java.exe", command.first())
            assertTrue(command.contains("-Xmx8192M"))
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun prepareRepairsMissingMinecraftClientJar() = runBlocking {
        val root = Files.createTempDirectory("mclaunch-client-jar").toFile()
        try {
            val launcher = launcher(
                root = root,
                java = MinecraftJava25Config(
                    currentJavaPath = "/current/java",
                    currentJavaMajor = 25,
                    maxMemoryMb = 1024,
                ),
                manifestId = "1.21.1",
                clientArtifact = MojangDownloadArtifact(url = "https://example.invalid/client.jar"),
            )

            launcher.prepare(request(root, mcVersion = McVersion.V211), onProgress = {}).getOrThrow()

            assertTrue(root.resolve("versions/1.21.1/1.21.1.jar").isFile)
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    private fun captureLaunchCommand(
        root: File,
        currentJavaMajor: Int,
        launchOverrides: MinecraftLaunchOverrides = MinecraftLaunchOverrides(),
    ): List<String> {
        var command: List<String>? = null
        val launcher = launcher(
            root = root,
            java = MinecraftJava25Config(
                currentJavaPath = "/current/java",
                currentJavaMajor = currentJavaMajor,
                maxMemoryMb = 1024,
            ),
            onCommand = { command = it },
        )
        launcher.launch(request(root, launchOverrides = launchOverrides), onLine = {}).getOrThrow()
        return command ?: error("未捕获Minecraft启动命令")
    }

    private fun launcher(
        root: File,
        java: MinecraftJava25Config,
        onCommand: (List<String>) -> Unit = {},
        onStart: () -> Unit = {},
        manifestId: String = "1.12.2",
        clientArtifact: MojangDownloadArtifact? = null,
    ): MinecraftLauncher {
        val directories = MinecraftDirectories(
            mcDir = root,
            versionsDir = root.resolve("versions"),
            librariesDir = root.resolve("libraries"),
            assetsDir = root.resolve("assets"),
            toolsDir = root.resolve("tools"),
        )
        directories.versionsDir.mkdirs()
        directories.librariesDir.mkdirs()
        val artifactPath = "test/library/1.0/library-1.0.jar"
        val artifactFile = directories.librariesDir.resolve(artifactPath)
        artifactFile.parentFile.mkdirs()
        ZipOutputStream(artifactFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("marker"))
            zip.write(0)
            zip.closeEntry()
        }
        val library = MojangLibrary(
            name = "test:library:1.0",
            downloads = MojangLibraryDownloads(
                artifact = MojangDownloadArtifact(path = artifactPath),
            ),
        )
        val manifest = MojangVersionManifest(
            id = manifestId,
            mainClass = "example.Main",
            downloads = clientArtifact?.let { MojangVersionDownloads(client = it) },
            libraries = listOf(library),
        )
        val loaderManifest = manifest.copy(
            id = "loader",
            minecraftArguments = "--username \${auth_player_name}",
        )
        val versionDir = directories.versionsDir.resolve("test-version").apply { mkdirs() }
        return MinecraftLauncher(
            MinecraftLaunchEnvironment(
                directories = directories,
                java = java,
                launcherBrand = "test",
                launcherVersion = "test",
                debug = false,
                manifestProvider = MinecraftManifestProvider { _, _, _ ->
                    Result.success(MinecraftManifestPair(manifest, loaderManifest))
                },
                artifactDownloader = MinecraftArtifactDownloader { _, _, target, _ ->
                    if (clientArtifact != null && target.name == "${manifest.id}.jar") {
                        target.parentFile.mkdirs()
                        ZipOutputStream(target.outputStream()).use { zip ->
                            zip.putNextEntry(ZipEntry("client-marker"))
                            zip.write(1)
                            zip.closeEntry()
                        }
                    }
                    Result.success(target)
                },
                processStarter = MinecraftProcessStarter { captured, _ ->
                    onCommand(captured)
                    onStart()
                    Result.success(FakeProcess())
                },
            )
        )
    }

    private fun request(
        root: File,
        mcVersion: McVersion = McVersion.V122,
        launchOverrides: MinecraftLaunchOverrides = MinecraftLaunchOverrides(),
    ): MinecraftLaunchRequest = MinecraftLaunchRequest(
        mcVersion = mcVersion,
        versionId = "test-version",
        versionDir = root.resolve("versions/test-version"),
        account = MinecraftAccount("player", "00000000-0000-0000-0000-000000000000", "token"),
        windowSize = MinecraftWindowSize(854, 480),
        launchOverrides = launchOverrides,
    )

    private class FakeProcess : Process() {
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun destroy() = Unit
        override fun isAlive(): Boolean = false
        override fun destroyForcibly(): Process = this
        override fun supportsNormalTermination(): Boolean = true
    }
}
