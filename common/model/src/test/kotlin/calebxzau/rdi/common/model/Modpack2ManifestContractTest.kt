package calebxzau.rdi.common.model

import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class Modpack2ManifestContractTest {
    @Test
    fun `manifest source bindings survive serialization`() {
        val contentKey = Modpack2ContentKeyDto(
            platform = ContentPlatform.CurseForge,
            type = ContentType.Mod,
            projectId = "123",
            fileId = "456",
            hash = "789",
            side = ContentSide.Client,
        )
        val contract = Modpack2VersionManifestDto(
            format = Modpack2ManifestFormat.CurseForge,
            manifestJson = "{\"minecraft\":{}}",
            bindings = listOf(
                Modpack2ContentBindingDto(
                    contentKey,
                    Modpack2ContentSourceDto.CurseForgeManifestEntry("123", "456"),
                ),
                Modpack2ContentBindingDto(
                    contentKey.copy(
                        platform = ContentPlatform.Modrinth,
                        projectId = "project",
                        fileId = "version",
                        hash = "a".repeat(40),
                        targetPath = "mods/example.jar",
                    ),
                    Modpack2ContentSourceDto.ModrinthManifestEntry(
                        path = "mods/example.jar",
                        sha1 = "a".repeat(40),
                    ),
                ),
                Modpack2ContentBindingDto(
                    contentKey.copy(
                        platform = ContentPlatform.GitHub,
                        projectId = "owner/repo",
                        fileId = "v1/example.jar",
                        hash = "b".repeat(64),
                    ),
                    Modpack2ContentSourceDto.RawSource(
                        root = Modpack2RawFileRoot.Shared,
                        path = "config/example.json",
                        sha1 = "c".repeat(40),
                        size = 12,
                    ),
                ),
            ),
        )

        assertEquals(contract, serdesJson.decodeFromString<Modpack2VersionManifestDto>(serdesJson.encodeToString(contract)))
    }
}
