package calebxzau.rdi.mcinstall

import calebxzau.rdi.mclaunch.model.MojangDownloadArtifact
import calebxzau.rdi.mclaunch.model.MojangLibrary
import calebxzau.rdi.mclaunch.model.MojangLibraryDownloads
import calebxzau.rdi.mclaunch.model.MojangVersionManifest
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Task2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McInstallTest {
    private val mcInstall = createTestMcInstall()

    @Test
    fun keepsInstallerLibrariesOutOfLaunchManifest() {
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

        val result = mcInstall.planLoaderLibraries(
            loaderManifest = MojangVersionManifest(
                id = "neoforge-21.1.248",
                libraries = listOf(runtimeLibrary),
            ),
            installProfileLibraries = listOf(universalLibrary, installerOnlyLibrary),
        )

        assertEquals(listOf(runtimeLibrary), result.launchManifest.libraries)
        assertFalse(result.launchManifest.libraries.contains(universalLibrary))
        assertFalse(result.launchManifest.libraries.contains(installerOnlyLibrary))
        assertEquals(listOf(universalLibrary, installerOnlyLibrary, runtimeLibrary), result.downloadLibraries)
    }

    @Test
    fun replacesPollutedManifestWithInstallerManifest() {
        val universalLibrary = MojangLibrary(name = "net.neoforged:neoforge:21.1.248:universal")
        val runtimeLibrary = MojangLibrary(name = "cpw.mods:bootstraplauncher:2.0.2")
        val installerManifest = MojangVersionManifest(
            id = "neoforge-21.1.248",
            libraries = listOf(runtimeLibrary),
        )
        val pollutedManifest = installerManifest.copy(libraries = listOf(universalLibrary, runtimeLibrary))

        val result = mcInstall.selectLoaderLaunchManifest(pollutedManifest, installerManifest)

        assertEquals(installerManifest, result)
        assertFalse(result.libraries.contains(universalLibrary))
    }

    @Test
    fun normalLoaderInstallDoesNotScheduleServerMappings() {
        val task = mcInstall.downloadLoaderTask2(McVersion.V211, ModLoader.neoforge) as Task2.Sequence

        assertTrue(task.children.any { it.title == "下载客户端Mojmap" })
        assertFalse(task.children.any { it.title == "下载服务端Mojmap" })
    }

    @Test
    fun testServerInstallSchedulesServerMappings() {
        val task = mcInstall.downloadTestServerTask2(McVersion.V211, ModLoader.neoforge) as Task2.Sequence

        assertTrue(task.children.any { it.title == "下载服务端Mojmap" })
    }
}
