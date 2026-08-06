package calebxzhou.rdi.common.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McVersionTest {
    @Test
    fun modpackUploadSupportsOnlyModernClientVersions() {
        assertTrue(McVersion.V201.supportsModpackUpload())
        assertTrue(McVersion.V211.supportsModpackUpload())
        assertFalse(McVersion.V122.supportsModpackUpload())
        assertFalse(McVersion.V071.supportsModpackUpload())
    }
}
