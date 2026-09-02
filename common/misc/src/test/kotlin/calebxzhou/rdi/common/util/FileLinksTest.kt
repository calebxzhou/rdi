package calebxzhou.rdi.common.util

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileLinksTest {
    @Test
    fun `mod files are hard linked`() {
        val root = Files.createTempDirectory("mod-hard-link").toFile()
        try {
            val source = root.resolve("cache.jar").apply { writeText("mod") }
            val target = root.resolve("mods/mod.jar")
            target.parentFile.mkdirs()

            hardLinkFile(source, target).getOrThrow()

            assertTrue(Files.isSameFile(source.toPath(), target.toPath()))
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `existing mod target is lazily replaced with a hard link`() {
        val root = Files.createTempDirectory("mod-hard-link-migration").toFile()
        try {
            val source = root.resolve("cache.jar").apply { writeText("current") }
            val target = root.resolve("mods/mod.jar").apply {
                parentFile.mkdirs()
                writeText("legacy")
            }

            hardLinkFile(source, target).getOrThrow()

            assertTrue(Files.isSameFile(source.toPath(), target.toPath()))
            assertEquals("current", target.readText())
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `directory files are recursively hard linked`() {
        val root = Files.createTempDirectory("directory-hard-link").toFile()
        try {
            val source = root.resolve("source")
            val sourceFile = source.resolve("group/artifact.jar").apply {
                parentFile.mkdirs()
                writeText("library")
            }
            val target = root.resolve("target")

            hardLinkDirectory(source, target).getOrThrow()

            assertTrue(Files.isSameFile(sourceFile.toPath(), target.resolve("group/artifact.jar").toPath()))
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }
}
