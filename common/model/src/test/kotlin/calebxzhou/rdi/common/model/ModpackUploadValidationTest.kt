package calebxzhou.rdi.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModpackUploadValidationTest {
    @Test
    fun `required upload metadata accepts valid values`() {
        val result = validateRequiredModpackUploadMetadata(
            info = "这是一个有效的整合包简介",
            iconUrl = "https://example.com/icon.png",
            categories = listOf(Modpack.Category.ADVENTURE),
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `intro length counts unicode code points`() {
        assertEquals(10, "中文简介123456".modpackInfoCharacterCount())
        assertTrue(
            validateRequiredModpackUploadMetadata(
                info = "a".repeat(MODPACK_INFO_MIN_CHARACTERS),
                iconUrl = "https://example.com/icon.png",
                categories = listOf(Modpack.Category.ADVENTURE),
            ).isSuccess
        )
        assertTrue(
            validateRequiredModpackUploadMetadata(
                info = "a".repeat(MODPACK_INFO_MAX_CHARACTERS),
                iconUrl = "https://example.com/icon.png",
                categories = listOf(Modpack.Category.ADVENTURE),
            ).isSuccess
        )
        assertTrue(
            validateRequiredModpackUploadMetadata(
                info = "短简介",
                iconUrl = "https://example.com/icon.png",
                categories = listOf(Modpack.Category.ADVENTURE),
            ).isFailure
        )
        assertTrue(
            validateRequiredModpackUploadMetadata(
                info = "a".repeat(MODPACK_INFO_MAX_CHARACTERS + 1),
                iconUrl = "https://example.com/icon.png",
                categories = listOf(Modpack.Category.ADVENTURE),
            ).isFailure
        )
    }

    @Test
    fun `icon and categories are required`() {
        val iconMissing = validateRequiredModpackUploadMetadata(
            info = "这是一个有效的整合包简介",
            iconUrl = "",
            categories = listOf(Modpack.Category.ADVENTURE),
        )
        val categoryMissing = validateRequiredModpackUploadMetadata(
            info = "这是一个有效的整合包简介",
            iconUrl = "https://example.com/icon.png",
            categories = emptyList(),
        )

        assertTrue(iconMissing.isFailure)
        assertTrue(categoryMissing.isFailure)
    }
}
