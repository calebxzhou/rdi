package calebxzau.rdi.mcinstall

import calebxzhou.rdi.common.net.LocalArtifactHashAlgorithm
import calebxzhou.rdi.common.net.LocalArtifactRequest
import calebxzhou.rdi.common.util.sha256
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalMcArtifactFileReuserTest {
    @Test
    fun `sha256 candidate is copied under a different filename`() = runBlocking {
        val root = Files.createTempDirectory("local-artifact-sha256")
        val source = root.resolve("instance/mods/renamed.jar")
        source.parent.createDirectories()
        source.writeText("hello")
        val target = root.resolve("rdi/downloaded.jar")
        val reuser = LocalMcArtifactFileReuser(
            sourceProvider = { LocalArtifactSources(searchFiles = listOf(source)) }
        )

        val reusedFrom = reuser.reuse(
            LocalArtifactRequest(
                algorithm = LocalArtifactHashAlgorithm.SHA256,
                hash = source.toFile().sha256,
            ),
            target,
        ).getOrThrow()

        assertEquals(source, reusedFrom)
        assertEquals("hello", target.readText())
    }

    @Test
    fun `sha1 candidate is copied under a different filename`() = runBlocking {
        val root = Files.createTempDirectory("local-artifact-sha1")
        val source = root.resolve("instance/mods/renamed.jar")
        source.parent.createDirectories()
        source.writeText("hello")
        val target = root.resolve("rdi/downloaded.jar")
        val reuser = LocalMcArtifactFileReuser(
            sourceProvider = { LocalArtifactSources(searchFiles = listOf(source)) }
        )

        val reusedFrom = reuser.reuse(
            LocalArtifactRequest(
                algorithm = LocalArtifactHashAlgorithm.SHA1,
                hash = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"
            ),
            target
        ).getOrThrow()

        assertEquals(source, reusedFrom)
        assertEquals("hello", target.readText())
    }

    @Test
    fun `curseforge candidate uses murmur2 and stale hash cache is invalidated`() = runBlocking {
        val root = Files.createTempDirectory("local-artifact-murmur2")
        val source = root.resolve("instance/mods/example.jar")
        source.parent.createDirectories()
        source.writeText("hello")
        val reuser = LocalMcArtifactFileReuser(
            sourceProvider = { LocalArtifactSources(searchFiles = listOf(source)) }
        )

        reuser.reuse(
            LocalArtifactRequest(LocalArtifactHashAlgorithm.CURSEFORGE_MURMUR2, "2788266382"),
            root.resolve("first.jar")
        ).getOrThrow()
        source.writeText("world!")
        val secondTarget = root.resolve("second.jar")

        val reusedFrom = reuser.reuse(
            LocalArtifactRequest(LocalArtifactHashAlgorithm.CURSEFORGE_MURMUR2, "678054497"),
            secondTarget
        ).getOrThrow()

        assertEquals(source, reusedFrom)
        assertEquals("world!", secondTarget.readText())
    }

    @Test
    fun `invalid local candidate falls through to the next matching file`() = runBlocking {
        val root = Files.createTempDirectory("local-artifact-fallback")
        val invalid = root.resolve("first.jar").also { it.writeText("wrong") }
        val valid = root.resolve("second.jar").also { it.writeText("hello") }
        val target = root.resolve("target.jar")
        val reuser = LocalMcArtifactFileReuser(
            sourceProvider = { LocalArtifactSources(searchFiles = listOf(invalid, valid)) }
        )

        val reusedFrom = reuser.reuse(
            LocalArtifactRequest(
                LocalArtifactHashAlgorithm.SHA1,
                "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"
            ),
            target
        ).getOrThrow()

        assertEquals(valid, reusedFrom)
    }

    @Test
    fun `runtime artifact is reused from the same relative path`() = runBlocking {
        val root = Files.createTempDirectory("local-runtime-source")
        val source = root.resolve("libraries/example/example.jar")
        source.parent.createDirectories()
        source.writeText("hello")
        val target = Files.createTempDirectory("local-runtime-target").resolve("example.jar")
        val reuser = LocalMcArtifactFileReuser(
            sourceProvider = { LocalArtifactSources(runtimeRoots = listOf(root)) }
        )

        val reusedFrom = reuser.reuse(
            LocalArtifactRequest(
                LocalArtifactHashAlgorithm.SHA1,
                "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d",
                relativePaths = listOf("libraries/example/example.jar")
            ),
            target
        ).getOrThrow()

        assertEquals(source, reusedFrom)
    }
}
