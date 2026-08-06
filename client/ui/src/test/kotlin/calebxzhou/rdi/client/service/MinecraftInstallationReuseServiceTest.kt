package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.net.LocalArtifactHashAlgorithm
import calebxzhou.rdi.common.net.LocalArtifactRequest
import calebxzhou.rdi.common.net.LocalArtifactReuse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MinecraftInstallationReuseServiceTest {
    @Test
    fun `copies matching library from recorded installation`() = runBlocking {
        val root = installationRoot()
        library(root).writeText("hello")
        val service = MinecraftInstallationReuseService(MutableStateFlow(listOf(root)))
        service.start()
        try {
            val target = root.resolve("target/libraries/example/example/1.0/example-1.0.jar")
            val source = reuseFrom(
                root = root,
                target = target,
                hash = HELLO_SHA1,
                size = "hello".length.toLong()
            )

            assertEquals(library(root), source)
            assertEquals("hello", target.readText())
        } finally {
            service.close()
        }
    }

    @Test
    fun `sha1 mismatch falls back to network`() = runBlocking {
        val root = installationRoot()
        library(root).writeText("hello")
        val service = MinecraftInstallationReuseService(MutableStateFlow(listOf(root)))
        service.start()
        try {
            val target = root.resolve("target/library.jar")
            val source = reuseFrom(
                root = root,
                target = target,
                hash = "0000000000000000000000000000000000000000",
                size = "hello".length.toLong()
            )

            assertNull(source)
            assertFalse(target.exists())
        } finally {
            service.close()
        }
    }

    @Test
    fun `size mismatch is skipped`() = runBlocking {
        val root = installationRoot()
        library(root).writeText("hello")
        val service = MinecraftInstallationReuseService(MutableStateFlow(listOf(root)))
        service.start()
        try {
            val target = root.resolve("target/library.jar")
            val source = reuseFrom(
                root = root,
                target = target,
                hash = HELLO_SHA1,
                size = 999L
            )

            assertNull(source)
        } finally {
            service.close()
        }
    }

    @Test
    fun `missing source file is skipped`() = runBlocking {
        val root = installationRoot()
        val service = MinecraftInstallationReuseService(MutableStateFlow(listOf(root)))
        service.start()
        try {
            val target = root.resolve("target/library.jar")
            val source = reuseFrom(
                root = root,
                target = target,
                hash = HELLO_SHA1,
                size = 5L
            )

            assertNull(source)
        } finally {
            service.close()
        }
    }

    @Test
    fun `source equal to target is skipped instead of copied`() = runBlocking {
        val root = installationRoot()
        val target = library(root)
        target.writeText("wrong")
        val service = MinecraftInstallationReuseService(MutableStateFlow(listOf(root)))
        service.start()
        try {
            val source = reuseFrom(
                root = root,
                target = target,
                hash = HELLO_SHA1,
                size = "hello".length.toLong()
            )

            assertNull(source)
            assertEquals("wrong", target.readText())
        } finally {
            service.close()
        }
    }

    @Test
    fun `new installation emitted by flow becomes a copy source`() = runBlocking {
        val root = installationRoot()
        library(root).writeText("hello")
        val flow = MutableStateFlow<List<Path>>(emptyList())
        val service = MinecraftInstallationReuseService(flow)
        service.start()
        try {
            val target = root.resolve("target/library.jar")
            val before = reuseFrom(
                root = root,
                target = target,
                hash = HELLO_SHA1,
                size = "hello".length.toLong()
            )
            assertNull(before)

            flow.value = listOf(root)
            val after = reuseFrom(
                root = root,
                target = target,
                hash = HELLO_SHA1,
                size = "hello".length.toLong()
            )

            assertEquals(library(root), after)
            assertEquals("hello", target.readText())
        } finally {
            service.close()
        }
    }

    @Test
    fun `close restores no-op reuse`() = runBlocking {
        val root = installationRoot()
        library(root).writeText("hello")
        val service = MinecraftInstallationReuseService(MutableStateFlow(listOf(root)))
        service.start()
        service.close()

        val target = root.resolve("target/library.jar")
        val source = reuseFrom(
            root = root,
            target = target,
            hash = HELLO_SHA1,
            size = "hello".length.toLong()
        )

        assertNull(source)
        assertFalse(target.exists())
    }

    private fun installationRoot(): Path {
        val root = Files.createTempDirectory("mc-reuse")
        root.resolve("libraries").resolve("example/example/1.0").createDirectories()
        return root
    }

    private fun library(root: Path): Path =
        root.resolve("libraries/example/example/1.0/example-1.0.jar")

    private suspend fun reuseFrom(root: Path, target: Path, hash: String, size: Long): Path? =
        LocalArtifactReuse.reuse(
            LocalArtifactRequest(
                algorithm = LocalArtifactHashAlgorithm.SHA1,
                hash = hash,
                size = size,
                relativePaths = listOf("libraries/example/example/1.0/example-1.0.jar")
            ),
            target
        ).getOrNull()

    private companion object {
        const val HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"
    }
}
