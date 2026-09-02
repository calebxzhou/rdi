package calebxzau.rdi.server.modpack2

import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.Modpack2Content
import calebxzau.rdi.common.model.Modpack2ContentBindingDto
import calebxzau.rdi.common.model.Modpack2ContentKeyDto
import calebxzau.rdi.common.model.Modpack2ContentSourceDto
import calebxzau.rdi.common.model.Modpack2Loader
import calebxzau.rdi.common.model.Modpack2ManifestFormat
import calebxzau.rdi.common.model.Modpack2RawFile
import calebxzau.rdi.common.model.Modpack2RawFileRoot
import calebxzau.rdi.common.model.Modpack2VersionManifestDto
import calebxzau.rdi.server.modpack.Modpack2ManifestValidator
import calebxzhou.rdi.common.exception.RequestError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class Modpack2ManifestValidatorTest {
    @Test
    fun `curseforge required entry must be bound`() {
        val content = curseContent(required = true)
        val manifest = """
            {"name":"Pack","version":"1","minecraft":{"version":"1.20.1","modLoaders":[{"id":"forge-47.4.20"}]},"files":[{"projectID":123,"fileID":456,"required":true}]}
        """.trimIndent()
        assertFailsWith<RequestError> {
            Modpack2ManifestValidator.validate(
                Modpack2VersionManifestDto(Modpack2ManifestFormat.CurseForge, manifest, emptyList()),
                listOf(content),
                emptyList(),
                20,
                Modpack2Loader.Forge,
            )
        }
    }

    @Test
    fun `curseforge optional entry may be omitted`() {
        val manifest = """
            {"name":"Pack","version":"1","minecraft":{"version":"1.20.1","modLoaders":[{"id":"forge-47.4.20"}]},"files":[{"projectID":123,"fileID":456,"required":false}]}
        """.trimIndent()
        val result = Modpack2ManifestValidator.validate(
            Modpack2VersionManifestDto(Modpack2ManifestFormat.CurseForge, manifest, emptyList()),
            emptyList<Modpack2Content>(),
            emptyList(),
            20,
            Modpack2Loader.Forge,
        )
        assertEquals(0, result.bindings.size)
    }

    @Test
    fun `modrinth required entry and raw evidence are validated`() {
        val hash = "a".repeat(40)
        val path = "mods/example.jar"
        val manifest = """
            {"formatVersion":1,"game":"minecraft","versionId":"pack","name":"Pack","files":[{"path":"$path","hashes":{"sha1":"$hash","sha512":"${"b".repeat(128)}"},"fileSize":42}],"dependencies":{"minecraft":"1.20.1","forge":"47.4.20"}}
        """.trimIndent()
        val content = Modpack2Content(
            versionId = java.util.UUID(0, 0),
            platform = ContentPlatform.Modrinth,
            type = ContentType.Mod,
            projectId = "project",
            fileId = "version",
            slug = "example",
            hash = hash,
            targetPath = path,
            side = ContentSide.Client,
            fileSize = 42,
        )
        val key = Modpack2ContentKeyDto(
            platform = content.platform,
            type = content.type,
            projectId = content.projectId,
            fileId = content.fileId,
            hash = content.hash,
            targetPath = content.targetPath,
            side = content.side,
        )
        val raw = Modpack2RawFile(Modpack2RawFileRoot.Client, "mods/a.jar", "c".repeat(40), 42)
        val extraContent = content.copy(
            platform = ContentPlatform.GitHub,
            projectId = "owner/repo",
            fileId = "v1/a.jar",
            hash = "d".repeat(64),
            targetPath = "mods/a.jar",
            fileSize = 42,
        )
        val result = Modpack2ManifestValidator.validate(
            Modpack2VersionManifestDto(
                Modpack2ManifestFormat.Modrinth,
                manifest,
                listOf(
                    Modpack2ContentBindingDto(
                        key,
                        Modpack2ContentSourceDto.ModrinthManifestEntry(path, hash),
                    ),
                    Modpack2ContentBindingDto(
                        key.copy(
                            platform = ContentPlatform.GitHub,
                            projectId = "owner/repo",
                            fileId = "v1/a.jar",
                            hash = "d".repeat(64),
                            targetPath = "mods/a.jar",
                            side = ContentSide.Client,
                        ),
                        Modpack2ContentSourceDto.RawSource(raw.root, raw.path, raw.sha1, raw.size),
                    ),
                ),
            ),
            listOf(content, extraContent),
            listOf(raw),
            20,
            Modpack2Loader.Forge,
        )
        assertEquals(2, result.bindings.size)
    }

    @Test
    fun `modrinth path and sha mismatch is rejected`() {
        val hash = "a".repeat(40)
        val manifest = """
            {"formatVersion":1,"game":"minecraft","versionId":"pack","name":"Pack","files":[{"path":"mods/example.jar","hashes":{"sha1":"$hash","sha512":"${"b".repeat(128)}"},"fileSize":42}],"dependencies":{"minecraft":"1.20.1","forge":"47.4.20"}}
        """.trimIndent()
        val content = curseContent(required = true).copy(
            platform = ContentPlatform.Modrinth,
            projectId = "project",
            fileId = "version",
            hash = hash,
            targetPath = "mods/wrong.jar",
            fileSize = 42,
        )
        val binding = Modpack2ContentBindingDto(
            Modpack2ContentKeyDto(ContentPlatform.Modrinth, ContentType.Mod, "project", "version", hash, "mods/wrong.jar", ContentSide.Client),
            Modpack2ContentSourceDto.ModrinthManifestEntry("mods/wrong.jar", hash),
        )
        assertFailsWith<RequestError> {
            Modpack2ManifestValidator.validate(
                Modpack2VersionManifestDto(Modpack2ManifestFormat.Modrinth, manifest, listOf(binding)),
                listOf(content),
                emptyList(),
                20,
                Modpack2Loader.Forge,
            )
        }
    }

    private fun curseContent(required: Boolean) = Modpack2Content(
        versionId = java.util.UUID(0, 0),
        platform = ContentPlatform.CurseForge,
        type = ContentType.Mod,
        projectId = "123",
        fileId = "456",
        slug = "example",
        hash = "123",
        side = ContentSide.Client,
        required = required,
        fileSize = 1,
    )
}
