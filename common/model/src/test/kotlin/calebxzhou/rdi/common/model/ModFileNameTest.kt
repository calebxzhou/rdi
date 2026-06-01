package calebxzhou.rdi.common.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ModFileNameTest {
    @Test
    fun trueEndingUsesAliasedFileName() {
        val mod = Mod(
            platform = "cf",
            projectId = "123",
            slug = "true-ending",
            fileId = "456",
            hash = "abcdef"
        )

        assertEquals("true-ending", mod.slug)
        assertEquals("trueending", mod.fileSlug)
        assertEquals("trueending_cf_abcdef.jar", mod.fileName)
        assertEquals("true-ending_cf_abcdef.jar", mod.legacyFileName)
    }

    @Test
    fun nonAliasedSlugKeepsFileName() {
        val mod = Mod(
            platform = "mr",
            projectId = "abc",
            slug = "sodium",
            fileId = "def",
            hash = "123456"
        )

        assertEquals("sodium", mod.fileSlug)
        assertEquals("sodium_mr_123456.jar", mod.fileName)
        assertEquals(listOf("sodium_mr_123456.jar"), mod.fileNames)
    }
}
