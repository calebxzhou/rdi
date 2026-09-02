package calebxzau.rdi.mcinstall

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class McInstallationValidatorTest {
    @Test
    fun `case insensitive minecraft name and empty required directories are valid`() {
        val root = Files.createTempDirectory("minecraft-validator")
        val candidate = root.resolve(".MINECRAFT")
        candidate.resolve("assets").createDirectories()
        candidate.resolve("libraries").createDirectories()
        candidate.resolve("versions").createDirectories()
        val validator = DefaultMcInstallationValidator { true }

        val validation = validator.validateDiscoveredCandidate(candidate, listOf(root))

        assertEquals(candidate.toRealPath(), assertIs<McInstallationValidation.Valid>(validation).realPath)
    }

    @Test
    fun `non minecraft discovered candidate is invalid`() {
        val root = Files.createTempDirectory("minecraft-validator")
        val validator = DefaultMcInstallationValidator { true }

        assertEquals(
            McInstallationValidation.Invalid,
            validator.validateDiscoveredCandidate(root.resolve("game"), listOf(root))
        )
    }

    @Test
    fun `stored real path does not need minecraft name`() {
        val root = Files.createTempDirectory("minecraft-validator").resolve("custom-root")
        root.resolve("assets").createDirectories()
        root.resolve("libraries").createDirectories()
        root.resolve("versions").createDirectories()
        val validator = DefaultMcInstallationValidator { true }

        val validation = validator.validateStoredRealPath(root, listOf(root.parent))

        assertEquals(root.toRealPath(), assertIs<McInstallationValidation.Valid>(validation).realPath)
    }

    @Test
    fun `all required directories are mandatory`() {
        val root = Files.createTempDirectory("minecraft-validator")
        val candidate = root.resolve(".minecraft")
        candidate.resolve("assets").createDirectories()
        candidate.resolve("libraries").createDirectories()
        val validator = DefaultMcInstallationValidator { true }

        assertEquals(
            McInstallationValidation.Missing,
            validator.validateDiscoveredCandidate(candidate, listOf(root))
        )
    }

    @Test
    fun `fixed volume check rejects a required directory on another volume`() {
        val root = Files.createTempDirectory("minecraft-validator")
        val candidate = root.resolve(".minecraft")
        candidate.resolve("assets").createDirectories()
        candidate.resolve("libraries").createDirectories()
        candidate.resolve("versions").createDirectories()
        val fixedPaths = mutableSetOf<Path>(candidate.toRealPath(), candidate.resolve("assets").toRealPath(), candidate.resolve("libraries").toRealPath())
        val validator = DefaultMcInstallationValidator { it.toRealPath() in fixedPaths }

        assertEquals(
            McInstallationValidation.Invalid,
            validator.validateDiscoveredCandidate(candidate, listOf(root))
        )
    }
}
