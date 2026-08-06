package calebxzau.rdi.mclaunch

import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzau.rdi.mclaunch.model.MojangDownloadArtifact
import calebxzau.rdi.mclaunch.model.MojangLibrary
import calebxzau.rdi.mclaunch.model.MojangLibraryDownloads
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class MinecraftLaunchLibraryPreparerTest {
    @Test
    fun repairsMissingLoaderRuntimeLibrary() = runBlocking {
        val root = Files.createTempDirectory("mclaunch-loader-library").toFile()
        try {
            val librariesDir = root.resolve("libraries")
            val artifactPath = "net/neoforged/fancymodloader/loader/4.0.43/loader-4.0.43.jar"
            val loaderLibrary = MojangLibrary(
                name = "net.neoforged.fancymodloader:loader:4.0.43",
                downloads = MojangLibraryDownloads(
                    artifact = MojangDownloadArtifact(path = artifactPath),
                ),
            )
            val preparer = MinecraftLaunchLibraryPreparer(
                librariesDir = librariesDir,
                downloader = MinecraftArtifactDownloader { _, _, target, _ ->
                    target.parentFile.mkdirs()
                    ZipOutputStream(target.outputStream()).use { zip ->
                        zip.putNextEntry(ZipEntry("marker"))
                        zip.write(1)
                        zip.closeEntry()
                    }
                    Result.success(target)
                },
            )

            preparer.ensure(
                baseLibraries = emptyList(),
                overrideLibraries = listOf(loaderLibrary),
                onProgress = {},
            ).getOrThrow()

            assertTrue(librariesDir.resolve(artifactPath).isFile)
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }
}
