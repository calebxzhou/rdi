package calebxzhou.rdi.master.service

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class UnusedModPurgeServiceTest {
    @Test
    fun `only unreferenced client cache files are purge candidates`() {
        val root = createTempDirectory("client-mod-purge-test").toFile()
        try {
            val used = root.resolve("used.jar").apply { writeText("used") }
            val unused = root.resolve("unused.jar").apply { writeText("unused") }
            val partial = root.resolve("partial.downloading").apply { writeText("partial") }
            root.resolve("ignored.txt").writeText("ignored")

            assertEquals(
                setOf(unused, partial),
                UnusedModPurgeService.findPurgeCandidates(root, setOf(used.name)).toSet()
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
