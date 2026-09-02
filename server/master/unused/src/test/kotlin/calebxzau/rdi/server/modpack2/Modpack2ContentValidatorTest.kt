package calebxzau.rdi.server.modpack2

import calebxzhou.rdi.common.exception.RequestError
import calebxzau.rdi.server.modpack.Modpack2ContentValidator
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.master.service.host2.validateGameAndLoader
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.Modpack2Loader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Modpack2ContentValidatorTest {
    @Test
    fun `CurseForge ids must be positive integers`() {
        assertEquals(123 to 456, Modpack2ContentValidator.parseCurseForgeIds("123", "456"))
        assertFailsWith<RequestError> {
            Modpack2ContentValidator.parseCurseForgeIds("0", "456")
        }
        assertFailsWith<RequestError> {
            Modpack2ContentValidator.parseCurseForgeIds("123", "file")
        }
    }

    @Test
    fun `GitHub ids contain exactly two non-empty parts`() {
        assertEquals("owner" to "repo", Modpack2ContentValidator.parseGitHubProjectId("owner/repo"))
        assertEquals("tag" to "asset.jar", Modpack2ContentValidator.parseGitHubFileId("tag/asset.jar"))
        assertFailsWith<RequestError> {
            Modpack2ContentValidator.parseGitHubProjectId("owner/repo/extra")
        }
        assertFailsWith<RequestError> {
            Modpack2ContentValidator.parseGitHubFileId("tag/")
        }
    }

    @Test
    fun `NeoForge only CurseForge metadata is rejected for Forge`() {
        assertFailsWith<RequestError> {
            validateGameAndLoader(
                declarations = listOf("1.21.1", "neoforge"),
                mcVersion = McVersion.V201,
                modLoader = ModLoader.forge,
                slug = "example",
            )
        }
    }

    @Test
    fun `CurseForge loader labels apply only to mods`() {
        assertFailsWith<RequestError> {
            Modpack2ContentValidator.validateCurseForgeEnvironment(
                declarations = listOf("1.20.1", "neoforge"),
                expectedMc = 20,
                expectedLoader = Modpack2Loader.Forge,
                contentType = ContentType.Mod,
                slug = "example",
            )
        }
        Modpack2ContentValidator.validateCurseForgeEnvironment(
            declarations = listOf("1.20.1", "neoforge"),
            expectedMc = 20,
            expectedLoader = Modpack2Loader.Forge,
            contentType = ContentType.ResPack,
            slug = "example",
        )
    }
}
