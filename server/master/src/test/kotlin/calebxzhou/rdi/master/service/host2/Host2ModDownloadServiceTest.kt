package calebxzhou.rdi.master.service.host2

import calebxzhou.rdi.common.model.Mod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class Host2ModDownloadServiceTest {
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
