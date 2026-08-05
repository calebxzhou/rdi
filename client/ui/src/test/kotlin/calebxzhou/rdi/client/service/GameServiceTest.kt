package calebxzhou.rdi.client.service

import calebxzau.rdi.mclaunch.model.MojangDownloadArtifact
import calebxzau.rdi.mclaunch.model.MojangLibrary
import calebxzau.rdi.mclaunch.model.MojangLibraryDownloads
import calebxzau.rdi.mclaunch.model.MojangVersionManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GameServiceTest {
    @Test
    fun onlyAddsUniversalInstallerLibraryToLaunchManifest() {
        val universalLibrary = MojangLibrary(
            name = "net.neoforged:neoforge:21.1.248:universal",
            downloads = MojangLibraryDownloads(
                artifact = MojangDownloadArtifact(
                    path = "net/neoforged/neoforge/21.1.248/neoforge-21.1.248-universal.jar",
                ),
            ),
        )
        val installerOnlyLibrary = MojangLibrary(name = "net.neoforged:neoform:1.21.1@zip")
        val runtimeLibrary = MojangLibrary(name = "cpw.mods:bootstraplauncher:2.0.2")

        val result = GameService.mergeLoaderManifestLibraries(
            loaderManifest = MojangVersionManifest(
                id = "neoforge-21.1.248",
                libraries = listOf(runtimeLibrary),
            ),
            installProfileLibraries = listOf(universalLibrary, installerOnlyLibrary),
        )

        assertEquals(listOf(universalLibrary, runtimeLibrary), result.libraries)
        assertFalse(result.libraries.contains(installerOnlyLibrary))
    }
}
