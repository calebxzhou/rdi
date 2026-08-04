package calebxzhou.rdi.client.service

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MinecraftInstallationValidatorTest {
    @Test
    fun `case insensitive minecraft name and empty required directories are valid`() {
        val root = Files.createTempDirectory("minecraft-validator")
        val candidate = root.resolve(".MINECRAFT")
        candidate.resolve("assets").createDirectories()
        candidate.resolve("libraries").createDirectories()
        candidate.resolve("versions").createDirectories()
        val validator = DefaultMinecraftInstallationValidator { true }

        val validation = validator.validateDiscoveredCandidate(candidate, listOf(root))

        assertEquals(candidate.toRealPath(), assertIs<MinecraftInstallationValidation.Valid>(validation).realPath)
    }

    @Test
    fun `non minecraft discovered candidate is invalid`() {
        val root = Files.createTempDirectory("minecraft-validator")
        val validator = DefaultMinecraftInstallationValidator { true }

        assertEquals(
            MinecraftInstallationValidation.Invalid,
            validator.validateDiscoveredCandidate(root.resolve("game"), listOf(root))
        )
    }

    @Test
    fun `stored real path does not need minecraft name`() {
        val root = Files.createTempDirectory("minecraft-validator").resolve("custom-root")
        root.resolve("assets").createDirectories()
        root.resolve("libraries").createDirectories()
        root.resolve("versions").createDirectories()
        val validator = DefaultMinecraftInstallationValidator { true }

        val validation = validator.validateStoredRealPath(root, listOf(root.parent))

        assertEquals(root.toRealPath(), assertIs<MinecraftInstallationValidation.Valid>(validation).realPath)
    }

    @Test
    fun `all required directories are mandatory`() {
        val root = Files.createTempDirectory("minecraft-validator")
        val candidate = root.resolve(".minecraft")
        candidate.resolve("assets").createDirectories()
        candidate.resolve("libraries").createDirectories()
        val validator = DefaultMinecraftInstallationValidator { true }

        assertEquals(
            MinecraftInstallationValidation.Missing,
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
        val validator = DefaultMinecraftInstallationValidator { it.toRealPath() in fixedPaths }

        assertEquals(
            MinecraftInstallationValidation.Invalid,
            validator.validateDiscoveredCandidate(candidate, listOf(root))
        )
    }
}
