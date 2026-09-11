package calebxzau.rdi.client.packproc

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.service.murmur2
import calebxzhou.rdi.common.util.sha1
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModContentIdentityTest {
    @Test
    fun `same curseforge fingerprint is a content match`() = runBlocking {
        val fingerprint = "1805207383"
        val first = Mod("cf", "project", "mod", "first", fingerprint)
        val second = Mod("cf", "project", "mod", "second", fingerprint)

        assertTrue(sameModContent(first, null, second, null))
    }

    @Test
    fun `same modrinth sha1 is matched case insensitively`() = runBlocking {
        val hash = "a".repeat(40)
        val first = Mod("mr", "project", "mod", "first", hash.uppercase())
        val second = Mod("mr", "project", "mod", "second", hash)

        assertTrue(sameModContent(first, null, second, null))
    }

    @Test
    fun `missing or zero fingerprints cannot prove content`() = runBlocking {
        val missing = Mod("cf", "project", "mod", "first", "")
        val zero = Mod("cf", "project", "mod", "second", "0")
        val valid = Mod("cf", "project", "mod", "third", "1805207383")

        assertFalse(sameModContent(missing, null, valid, null))
        assertFalse(sameModContent(missing, null, missing.copy(fileId = "other"), null))
        assertFalse(sameModContent(zero, null, valid, null))
        assertFalse(sameModContent(zero, null, zero.copy(fileId = "other"), null))
    }

    @Test
    fun `different platform digests cannot match without local files`() = runBlocking {
        val curseForge = Mod("cf", "project", "mod", "first", "1805207383")
        val modrinth = Mod("mr", "project", "mod", "second", "a".repeat(40))

        assertFalse(sameModContent(curseForge, null, modrinth, null))
    }

    @Test
    fun `different platform digests require matching verified local bytes`() = runBlocking {
        val root = Files.createTempDirectory("mod-content-identity").toFile()
        try {
            val firstFile = root.resolve("first.jar").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
            val secondFile = root.resolve("second.jar").also { it.writeBytes(firstFile.readBytes()) }
            val differentFile = root.resolve("different.jar").also { it.writeBytes(byteArrayOf(4, 5, 6)) }
            val curseForge = Mod(
                "cf", "project", "mod", "first", firstFile.murmur2.toULong().toString()
            )
            val modrinth = Mod("mr", "project", "mod", "second", firstFile.sha1)
            val mismatchedModrinth = Mod("mr", "project", "mod", "third", differentFile.sha1)

            assertTrue(sameModContent(curseForge, firstFile, modrinth, secondFile))
            assertFalse(sameModContent(curseForge, firstFile, mismatchedModrinth, differentFile))
        } finally {
            root.deleteRecursively()
        }
    }
}
