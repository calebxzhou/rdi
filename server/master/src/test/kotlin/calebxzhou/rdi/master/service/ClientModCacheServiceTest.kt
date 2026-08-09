package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.common.service.runInline
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientModCacheServiceTest {
    @Test
    fun `one failed client mod does not stop the remaining downloads`() = runTest {
        val attempted = ConcurrentHashMap.newKeySet<String>()
        val targetDirs = ConcurrentHashMap.newKeySet<File>()
        val targetDir = File("client-mod-cache")
        val service = ClientModCacheService(targetDir) { mods, actualTargetDir ->
            Task2.Leaf("fake download") {
                val mod = mods.single()
                attempted += mod.slug
                targetDirs += actualTargetDir
                if (mod.slug == "broken") error("download failed")
            }
        }
        val mods = listOf(clientMod("broken"), clientMod("working"))

        service.downloadTask(mods).runInline(Task2Context {})

        assertEquals(setOf("broken", "working"), attempted.toSet())
        assertEquals(setOf(targetDir), targetDirs.toSet())
    }

    private fun clientMod(slug: String) = Mod(
        platform = "mr",
        projectId = slug,
        slug = slug,
        fileId = slug,
        hash = slug,
        side = Mod.Side.CLIENT,
        downloadUrls = listOf("https://example.invalid/$slug.jar")
    )
}
