package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.common.model.McVersion
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class GameKotlinRuntimeTest {
    @Test
    fun `normalizes shaded KFF and keeps source hash in SHA1 cache`(@TempDir tempDir: Path) {
        val modsDir = tempDir.resolve("mods").createDirectories().toFile()
        val source = modsDir.resolve("kotlinforforge.jar")
        writeKffArchive(source, shadedRuntime = true)
        val sourceSha1 = source.sha1

        val runtime = GameKotlinRuntime.prepare(
            mcVersion = McVersion.V201,
            modsDir = modsDir,
            cacheRoot = tempDir.resolve("cache").toFile()
        ).getOrThrow()

        assertEquals(5, runtime.size)
        assertEquals(5, runtime.distinctBy { it.absolutePath }.size)
        assertTrue(runtime.all { it.isFile && it.extension.equals("jar", ignoreCase = true) })
        assertTrue(tempDir.resolve("cache/original-kff/$sourceSha1.jar").toFile().isFile)
        assertTrue(tempDir.resolve("cache/thin-kff/v1-$sourceSha1.jar").toFile().isFile)
        assertFalse(source.sha1.equals(sourceSha1, ignoreCase = true))

        ZipFile(source).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertFalse(names.any { it.startsWith("kotlin/") })
            assertFalse(names.any { it.startsWith("kotlinx/") })
            assertFalse(names.any { it == "_COROUTINE/CoroutineDebuggingKt.class" })
            val metadata = zip.getInputStream(zip.getEntry("META-INF/jarjar/metadata.json"))
                .bufferedReader(StandardCharsets.UTF_8)
                .readText()
            assertTrue(metadata.contains("kfflang"))
            assertTrue(metadata.contains("kfflib"))
            assertTrue(metadata.contains("kffmod"))
            assertFalse(metadata.contains("kotlin-stdlib"))
            assertEquals(3, names.count { it.startsWith("META-INF/jarjar/") && it.endsWith(".jar") })
        }

        val normalizedSha1 = source.sha1
        GameKotlinRuntime.prepare(
            mcVersion = McVersion.V201,
            modsDir = modsDir,
            cacheRoot = tempDir.resolve("cache").toFile()
        ).getOrThrow()
        assertEquals(normalizedSha1, source.sha1)
    }

    private fun writeKffArchive(target: java.io.File, shadedRuntime: Boolean) {
        target.parentFile.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { output ->
            output.writeEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nFMLModType: LIBRARY\n")
            output.writeEntry("META-INF/jarjar/metadata.json", metadata(shadedRuntime))
            output.writeEntry(
                "META-INF/jarjar/kfflang-4.12.0.jar",
                nestedJar("META-INF/services/net.minecraftforge.forgespi.language.IModLanguageProvider")
            )
            output.writeEntry("META-INF/jarjar/kfflib-4.12.0.jar", nestedJar("thedarkcolour/Kff.class"))
            output.writeEntry("META-INF/jarjar/kffmod-4.12.0.jar", nestedJar("META-INF/mods.toml"))
            if (shadedRuntime) {
                output.writeEntry("kotlin/jvm/internal/Intrinsics.class", byteArrayOf(1))
                output.writeEntry("kotlinx/coroutines/Job.class", byteArrayOf(2))
                output.writeEntry("_COROUTINE/CoroutineDebuggingKt.class", byteArrayOf(3))
            }
        }
    }

    private fun metadata(shadedRuntime: Boolean): String {
        val runtime = if (shadedRuntime) {
            ",\n            {\"identifier\":{\"group\":\"org.jetbrains.kotlin\",\"artifact\":\"kotlin-stdlib\"},\"version\":{\"range\":\"[2.2.21,)\",\"artifactVersion\":\"2.2.21\"},\"path\":\"META-INF/jarjar/kotlin-stdlib-2.2.21.jar\",\"isObfuscated\":false}"
        } else {
            ""
        }
        return """
            {"jars":[
            {"identifier":{"group":"thedarkcolour","artifact":"kfflang"},"version":{"range":"[4.12.0,)","artifactVersion":"4.12.0"},"path":"META-INF/jarjar/kfflang-4.12.0.jar","isObfuscated":false},
            {"identifier":{"group":"thedarkcolour","artifact":"kfflib"},"version":{"range":"[4.12.0,)","artifactVersion":"4.12.0"},"path":"META-INF/jarjar/kfflib-4.12.0.jar","isObfuscated":false},
            {"identifier":{"group":"thedarkcolour","artifact":"kffmod"},"version":{"range":"[4.12.0,)","artifactVersion":"4.12.0"},"path":"META-INF/jarjar/kffmod-4.12.0.jar","isObfuscated":false}$runtime
            ]}
        """.trimIndent()
    }

    private fun nestedJar(entryName: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { output ->
            output.writeEntry(entryName, byteArrayOf(1, 2, 3))
        }
        return bytes.toByteArray()
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        writeEntry(name, content.toByteArray(StandardCharsets.UTF_8))
    }

    private fun ZipOutputStream.writeEntry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }
}
