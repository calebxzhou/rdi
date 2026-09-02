package calebxzhou.rdi.master.service.host

import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostInstallServiceTest {
    @Test
    fun `v2 install cleanup preserves only exact root world`() {
        val root = Files.createTempDirectory("host-v2-install").toFile()
        try {
            root.resolve("world").mkdirs()
            root.resolve("world").resolve("level.dat").writeText("keep")
            root.resolve("World").mkdirs()
            root.resolve("world_nether").mkdirs()
            root.resolve("server.properties").writeText("remove")

            HostInstallService.cleanForV2Install(root)

            assertTrue(root.resolve("world/level.dat").isFile)
            assertFalse(root.resolve("World").exists())
            assertFalse(root.resolve("world_nether").exists())
            assertFalse(root.resolve("server.properties").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `v2 archive filter is root-only and case-sensitive`() {
        assertTrue(calebxzhou.rdi.master.service.ModpackService.shouldSkipRootWorld("world"))
        assertTrue(calebxzhou.rdi.master.service.ModpackService.shouldSkipRootWorld("world/level.dat"))
        assertFalse(calebxzhou.rdi.master.service.ModpackService.shouldSkipRootWorld("World/level.dat"))
        assertFalse(calebxzhou.rdi.master.service.ModpackService.shouldSkipRootWorld("world_nether/level.dat"))
        assertFalse(calebxzhou.rdi.master.service.ModpackService.shouldSkipRootWorld("abc/world/level.dat"))
    }

    @Test
    fun `managed properties keep packaged default from changing world location`() {
        val server = Properties().apply {
            setProperty("server-port", "25565")
            setProperty("online-mode", "true")
        }
        val defaults = Properties().apply {
            setProperty("level-name", "myworld")
            setProperty("server-port", "9999")
        }
        HostInstallService.applyDefaultServerProperties(server, defaults)
        assertTrue(server.getProperty("level-name") == "world")
        assertTrue(server.getProperty("server-port") == "25565")
    }

    @Test
    fun `strict deletion removes dangling symlink without following it`() {
        val root = Files.createTempDirectory("host-v2-link").toFile()
        try {
            val link = root.resolve("dangling").toPath()
            Files.createSymbolicLink(link, root.resolve("missing-target").toPath())
            HostInstallService.deleteStrictNoSymlink(link.toFile())
            assertFalse(Files.exists(link))
            assertFalse(Files.isSymbolicLink(link))
        } finally {
            root.deleteRecursively()
        }
    }
}
