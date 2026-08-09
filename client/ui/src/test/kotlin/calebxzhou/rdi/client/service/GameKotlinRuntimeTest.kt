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

        assertEquals(6, runtime.size)
        assertEquals(6, runtime.distinctBy { it.absolutePath }.size)
        assertTrue(runtime.all { it.isFile && it.extension.equals("jar", ignoreCase = true) })
        val zstdRuntime = java.io.File(
            Class.forName("com.github.luben.zstd.Zstd").protectionDomain.codeSource.location.toURI()
        ).absoluteFile
        assertTrue(zstdRuntime in runtime)
        assertTrue(tempDir.resolve("cache/original-kotlin-mod/$sourceSha1.jar").toFile().isFile)
        assertTrue(tempDir.resolve("cache/thin-kotlin-mod/v2-$sourceSha1.jar").toFile().isFile)
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
            assertFalse(metadata.contains("kotlinx.serialization.core"))
            assertFalse(metadata.contains("kotlinx.serialization.json"))
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

    @Test
    fun `normalizes non KFF Kotlin jarjar while preserving mod dependencies and services`(@TempDir tempDir: Path) {
        val modsDir = tempDir.resolve("mods").createDirectories().toFile()
        val source = modsDir.resolve("pandalib.jar")
        writePandaLibArchive(source)
        val sourceSha1 = source.sha1

        GameKotlinRuntime.prepare(
            mcVersion = McVersion.V211,
            modsDir = modsDir,
            cacheRoot = tempDir.resolve("cache").toFile()
        ).getOrThrow()

        assertFalse(source.sha1.equals(sourceSha1, ignoreCase = true))
        ZipFile(source).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertFalse(names.contains("META-INF/jars/kotlin-reflect-2.3.0.jar"))
            assertFalse(names.contains("META-INF/jars/kotlin-stdlib-2.3.0.jar"))
            assertFalse(names.contains("META-INF/jars/kotlin-stdlib-jdk7-2.3.0.jar"))
            assertFalse(names.contains("META-INF/jars/kotlin-stdlib-jdk8-2.3.0.jar"))
            assertFalse(names.contains("META-INF/jars/kotlinx-coroutines-core-jvm-1.10.2.jar"))
            assertFalse(names.contains("META-INF/jars/kotlinx-coroutines-jdk8-1.10.2.jar"))
            assertFalse(names.contains("META-INF/jars/kotlinx-serialization-core-jvm-1.9.0.jar"))
            assertFalse(names.contains("META-INF/jars/kotlinx-serialization-json-jvm-1.9.0.jar"))
         /*   assertFalse(names.contains("META-INF/jars/kotaml-jvm-0.108.0.jar"))
            assertFalse(names.contains("META-INF/jars/okio-jvm-3.17.0.jar"))
            assertFalse(names.contains("META-INF/jars/snakeyaml-engine-kmp-jvm-4.0.1.jar"))
            assertFalse(names.contains("META-INF/jars/urlencoder-lib-jvm-1.6.0.jar"))
            assertFalse(names.contains("META-INF/jars/java-http-1.4.0.jar"))
         */   assertTrue(names.contains("META-INF/jars/kotlinx-datetime-jvm-0.7.1.jar"))
            assertTrue(names.contains("META-INF/jars/kotlinx-serialization-cbor-jvm-1.9.0.jar"))
            assertTrue(names.contains("META-INF/jars/universal-serializer-0.1.0-SNAPSHOT.jar"))
            assertTrue(names.contains("META-INF/services/dev.pandasystems.pandalib.registry.RegistriesPlatform"))
            assertTrue(names.contains("META-INF/pandalib-common-1.21.1.kotlin_module"))

            val metadata = zip.getInputStream(zip.getEntry("META-INF/jarjar/metadata.json"))
                .bufferedReader(StandardCharsets.UTF_8)
                .readText()
            assertFalse(metadata.contains("kotlin-reflect"))
            assertFalse(metadata.contains("kotlin-stdlib"))
            assertFalse(metadata.contains("kotlinx-coroutines-core-jvm"))
            assertFalse(metadata.contains("kotlinx-coroutines-jdk8"))
            assertFalse(metadata.contains("kotlinx-serialization-core-jvm"))
            assertFalse(metadata.contains("kotlinx-serialization-json-jvm"))
          /*  assertFalse(metadata.contains("kotaml-jvm"))
            assertFalse(metadata.contains("okio-jvm"))
            assertFalse(metadata.contains("snakeyaml-engine-kmp-jvm"))
            assertFalse(metadata.contains("urlencoder-lib-jvm"))
            assertFalse(metadata.contains("java-http"))
          */  assertTrue(metadata.contains("kotlinx-datetime-jvm"))
            assertTrue(metadata.contains("kotlinx-serialization-cbor-jvm"))
            assertTrue(metadata.contains("universal-serializer"))
        }

        val normalizedSha1 = source.sha1
        GameKotlinRuntime.prepare(
            mcVersion = McVersion.V211,
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
            output.writeEntry("META-INF/jarjar/kotlinx-serialization-core-jvm-1.9.0.jar", nestedJar("kotlinx/serialization/Core.class"))
            output.writeEntry("META-INF/jarjar/kotlinx-serialization-json-jvm-1.9.0.jar", nestedJar("kotlinx/serialization/json/Json.class"))
            if (shadedRuntime) {
                output.writeEntry("META-INF/jarjar/kotlin-stdlib-2.2.21.jar", nestedJar("kotlin/jvm/internal/Intrinsics.class"))
                output.writeEntry("kotlin/jvm/internal/Intrinsics.class", byteArrayOf(1))
                output.writeEntry("kotlinx/coroutines/Job.class", byteArrayOf(2))
                output.writeEntry("_COROUTINE/CoroutineDebuggingKt.class", byteArrayOf(3))
            }
        }
    }

    private fun writePandaLibArchive(target: java.io.File) {
        target.parentFile.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { output ->
            output.writeEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
            output.writeEntry("META-INF/jarjar/metadata.json", pandaLibMetadata())
            output.writeEntry("META-INF/jars/kotlin-reflect-2.3.0.jar", byteArrayOf(1))
            output.writeEntry("META-INF/jars/kotlin-stdlib-2.3.0.jar", byteArrayOf(2))
            output.writeEntry("META-INF/jars/kotlin-stdlib-jdk7-2.3.0.jar", byteArrayOf(3))
            output.writeEntry("META-INF/jars/kotlin-stdlib-jdk8-2.3.0.jar", byteArrayOf(4))
            output.writeEntry("META-INF/jars/kotlinx-coroutines-core-jvm-1.10.2.jar", byteArrayOf(5))
            output.writeEntry("META-INF/jars/kotlinx-coroutines-jdk8-1.10.2.jar", byteArrayOf(6))
            output.writeEntry("META-INF/jars/kotlinx-datetime-jvm-0.7.1.jar", byteArrayOf(7))
            output.writeEntry("META-INF/jars/kotlinx-serialization-core-jvm-1.9.0.jar", byteArrayOf(8))
            output.writeEntry("META-INF/jars/kotlinx-serialization-json-jvm-1.9.0.jar", byteArrayOf(9))
         /*   output.writeEntry("META-INF/jars/kotaml-jvm-0.108.0.jar", byteArrayOf(10))
            output.writeEntry("META-INF/jars/okio-jvm-3.17.0.jar", byteArrayOf(11))
            output.writeEntry("META-INF/jars/snakeyaml-engine-kmp-jvm-4.0.1.jar", byteArrayOf(12))
            output.writeEntry("META-INF/jars/urlencoder-lib-jvm-1.6.0.jar", byteArrayOf(13))
            output.writeEntry("META-INF/jars/java-http-1.4.0.jar", byteArrayOf(14))
         */   output.writeEntry("META-INF/jars/kotlinx-serialization-cbor-jvm-1.9.0.jar", byteArrayOf(15))
            output.writeEntry("META-INF/jars/universal-serializer-0.1.0-SNAPSHOT.jar", byteArrayOf(16))
            output.writeEntry(
                "META-INF/services/dev.pandasystems.pandalib.registry.RegistriesPlatform",
                "dev.pandasystems.pandalib.registry.RegistriesPlatformImpl\n"
            )
            output.writeEntry("META-INF/pandalib-common-1.21.1.kotlin_module", byteArrayOf(17))
        }
    }

    private fun pandaLibMetadata(): String = """
        {"jars":[
        {"identifier":{"group":"org.jetbrains.kotlin","artifact":"kotlin-reflect"},"version":{"artifactVersion":"2.3.0"},"path":"META-INF/jars/kotlin-reflect-2.3.0.jar"},
        {"identifier":{"group":"org.jetbrains.kotlin","artifact":"kotlin-stdlib"},"version":{"artifactVersion":"2.3.0"},"path":"META-INF/jars/kotlin-stdlib-2.3.0.jar"},
        {"identifier":{"group":"org.jetbrains.kotlin","artifact":"kotlin-stdlib-jdk7"},"version":{"artifactVersion":"2.3.0"},"path":"META-INF/jars/kotlin-stdlib-jdk7-2.3.0.jar"},
        {"identifier":{"group":"org.jetbrains.kotlin","artifact":"kotlin-stdlib-jdk8"},"version":{"artifactVersion":"2.3.0"},"path":"META-INF/jars/kotlin-stdlib-jdk8-2.3.0.jar"},
        {"identifier":{"group":"org.jetbrains.kotlinx","artifact":"kotlinx-coroutines-core-jvm"},"version":{"artifactVersion":"1.10.2"},"path":"META-INF/jars/kotlinx-coroutines-core-jvm-1.10.2.jar"},
        {"identifier":{"group":"org.jetbrains.kotlinx","artifact":"kotlinx-coroutines-jdk8"},"version":{"artifactVersion":"1.10.2"},"path":"META-INF/jars/kotlinx-coroutines-jdk8-1.10.2.jar"},
        {"identifier":{"group":"org.jetbrains.kotlinx","artifact":"kotlinx-datetime-jvm"},"version":{"artifactVersion":"0.7.1"},"path":"META-INF/jars/kotlinx-datetime-jvm-0.7.1.jar"},
        {"identifier":{"group":"org.jetbrains.kotlinx","artifact":"kotlinx-serialization-core-jvm"},"version":{"artifactVersion":"1.9.0"},"path":"META-INF/jars/kotlinx-serialization-core-jvm-1.9.0.jar"},
        {"identifier":{"group":"org.jetbrains.kotlinx","artifact":"kotlinx-serialization-json-jvm"},"version":{"artifactVersion":"1.9.0"},"path":"META-INF/jars/kotlinx-serialization-json-jvm-1.9.0.jar"},
     /*   {"identifier":{"group":"io.heapy.kotaml","artifact":"kotaml-jvm"},"version":{"artifactVersion":"0.108.0"},"path":"META-INF/jars/kotaml-jvm-0.108.0.jar"},
        {"identifier":{"group":"com.squareup.okio","artifact":"okio-jvm"},"version":{"artifactVersion":"3.17.0"},"path":"META-INF/jars/okio-jvm-3.17.0.jar"},
        {"identifier":{"group":"it.krzeminski","artifact":"snakeyaml-engine-kmp-jvm"},"version":{"artifactVersion":"4.0.1"},"path":"META-INF/jars/snakeyaml-engine-kmp-jvm-4.0.1.jar"},
        {"identifier":{"group":"net.thauvin.erik.urlencoder","artifact":"urlencoder-lib-jvm"},"version":{"artifactVersion":"1.6.0"},"path":"META-INF/jars/urlencoder-lib-jvm-1.6.0.jar"},
        {"identifier":{"group":"io.fusionauth","artifact":"java-http"},"version":{"artifactVersion":"1.4.0"},"path":"META-INF/jars/java-http-1.4.0.jar"},
     */   {"identifier":{"group":"org.jetbrains.kotlinx","artifact":"kotlinx-serialization-cbor-jvm"},"version":{"artifactVersion":"1.9.0"},"path":"META-INF/jars/kotlinx-serialization-cbor-jvm-1.9.0.jar"},
        {"identifier":{"group":"dev.pandasystems","artifact":"universal-serializer"},"version":{"artifactVersion":"0.1.0-SNAPSHOT"},"path":"META-INF/jars/universal-serializer-0.1.0-SNAPSHOT.jar"}
        ]}
    """.trimIndent()

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
            {"identifier":{"group":"thedarkcolour","artifact":"kffmod"},"version":{"range":"[4.12.0,)","artifactVersion":"4.12.0"},"path":"META-INF/jarjar/kffmod-4.12.0.jar","isObfuscated":false},
            {"identifier":{"group":"","artifact":"kotlinx.serialization.core"},"version":{"artifactVersion":"test"},"path":"META-INF/jarjar/kotlinx-serialization-core-jvm-1.9.0.jar","isObfuscated":false},
            {"identifier":{"group":"","artifact":"kotlinx.serialization.json"},"version":{"artifactVersion":"test"},"path":"META-INF/jarjar/kotlinx-serialization-json-jvm-1.9.0.jar","isObfuscated":false}$runtime
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
