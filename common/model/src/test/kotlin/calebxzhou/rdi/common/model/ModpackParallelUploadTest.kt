package calebxzhou.rdi.common.model

import calebxzhou.rdi.common.serdesJson
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ModpackParallelUploadTest {
    @Test
    fun `upload session round trips UUID and sha1`() {
        val session = ModpackUploadSessionVo(
            id = UUID.fromString("019fe723-72c0-7000-8000-000000000001"),
            fileName = "pack.zip",
            size = 12,
            sha1 = "0123456789abcdef0123456789abcdef01234567",
            partSize = 4,
            partCount = 3,
            uploadedParts = listOf(0, 2),
            ready = false,
            expiresAt = 1_786_406_400_000
        )

        val decoded = serdesJson.decodeFromString<ModpackUploadSessionVo>(
            serdesJson.encodeToString(session)
        )

        assertEquals(session, decoded)
        assertEquals(8, decoded.maxParallelParts)
    }
}
