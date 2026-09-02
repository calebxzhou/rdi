package calebxzhou.rdi.master.service.host2

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class Host2ModDownloadServiceTest {
    @Test
    fun `github rejects historical sha1 metadata before downloading`(): Unit = runBlocking {
        val mod = Mod(
            platform = "github",
            projectId = "owner/repo",
            fileId = "release/asset.jar",
            slug = "example",
            hash = "a".repeat(40),
            side = Mod.Side.SERVER,
        )

        assertFailsWith<RequestError> {
            Host2ModDownloadService().download(
                mod = mod,
                mcVersion = McVersion.V211,
                modLoader = ModLoader.neoforge,
                targetDir = Files.createTempDirectory("host2-github-hash").toFile(),
            )
        }
    }

    @Test
    fun `managed mod filename only contains slug and hash`() {
        val mod = Mod(
            platform = "mr",
            projectId = "project",
            fileId = "file",
            slug = "My Mod",
            hash = "ABC123",
            side = Mod.Side.BOTH
        )

        assertEquals("my_mod_abc123.jar", mod.host2FileName)
    }

    @Test
    fun `invalid slug is rejected`() {
        val mod = Mod(
            platform = "cf",
            projectId = "1",
            fileId = "2",
            slug = "中文",
            hash = "123",
            side = Mod.Side.SERVER
        )

        assertFails { mod.host2FileName }
    }
}
