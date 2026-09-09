package calebxzhou.rdi.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.bson.types.ObjectId

class ModpackUploadValidationTest {
    @Test
    fun `allow uploader ids json supports omitted null and populated values`() {
        val ownerId = ObjectId()
        val listedId = ObjectId()
        val pack = Modpack(
            name = "Upload permissions",
            authorId = ownerId,
            mcVer = McVersion.V211,
            modloader = ModLoader.neoforge,
            allowUploaderIds = listOf(listedId),
        )
        val encoded = serdesJson.encodeToString(pack)
        assertTrue(encoded.contains("allowUploaderIds"))
        assertEquals(
            listOf(listedId),
            serdesJson.decodeFromString<Modpack>(encoded).allowUploaderIds,
        )

        val fieldsWithoutPermission = serdesJson.parseToJsonElement(encoded).jsonObject.toMutableMap()
        fieldsWithoutPermission.remove("allowUploaderIds")
        assertEquals(
            null,
            serdesJson.decodeFromString<Modpack>(JsonObject(fieldsWithoutPermission).toString()).allowUploaderIds,
        )
        val explicitNull = JsonObject(fieldsWithoutPermission + ("allowUploaderIds" to JsonNull))
        assertEquals(
            null,
            serdesJson.decodeFromString<Modpack>(explicitNull.toString()).allowUploaderIds,
        )
    }

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
