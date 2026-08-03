package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.Continuation
import kotlinx.coroutines.Job
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal object GameKotlinRuntime {
    private const val CACHE_SCHEMA = "v1"
    private const val KFF_METADATA = "META-INF/jarjar/metadata.json"
    private const val KFF_GROUP = "thedarkcolour"
    private val kffArtifacts = setOf("kfflang", "kfflib", "kffmod")

    fun prepare(
        mcVersion: McVersion,
        modsDir: File,
        cacheRoot: File = ClientDirs.toolsDir.resolve("kotlin-runtime")
    ): Result<List<File>> = runCatching {
        check(mcVersion == McVersion.V201 || mcVersion == McVersion.V211) {
            "Kotlin运行库只支持MC1.20.1和MC1.21.1"
        }
        val runtime = resolveRuntimeClasspath()
        val kffArchives = detectKffArchives(modsDir)
        check(kffArchives.size <= 1) {
            "发现多个Kotlin for Forge文件: ${kffArchives.joinToString { it.file.name }}"
        }
        kffArchives.singleOrNull()?.let { normalizeKff(it, cacheRoot) }
        runtime
    }

    private fun resolveRuntimeClasspath(): List<File> {
        val libraries = listOf(
            "kotlin-stdlib" to classSource(Continuation::class.java),
            "kotlin-reflect" to classSource("kotlin.reflect.jvm.ReflectJvmMapping"),
            "kotlinx-coroutines-core" to classSource(Job::class.java),
            "kotlinx-serialization-core" to classSource(KSerializer::class.java),
            "kotlinx-serialization-json" to classSource(Json::class.java)
        )
        libraries.forEach { (name, file) ->
            check(file.isFile) { "RDI${name}运行库不存在: ${file.absolutePath}" }
            check(file.extension.equals("jar", ignoreCase = true)) {
                "RDI${name}运行库不是JAR: ${file.absolutePath}"
            }
        }
        check(libraries.map { it.second.absolutePath }.distinct().size == libraries.size) {
            "RDI Kotlin运行库必须由${libraries.size}个独立JAR提供: " +
                libraries.joinToString { (name, file) -> "$name=${file.name}" }
        }
        return libraries.map { it.second }
    }

    private fun classSource(type: Class<*>): File =
        File(type.protectionDomain.codeSource.location.toURI()).absoluteFile

    private fun classSource(className: String): File =
        classSource(Class.forName(className))

    private data class KffArchive(
        val file: File,
        val metadata: JsonObject,
        val nestedEntries: Set<String>,
        val needsNormalization: Boolean
    )

    private data class KffDependency(
        val path: String,
        val group: String,
        val artifact: String
    )

    private fun detectKffArchives(modsDir: File): List<KffArchive> {
        if (!modsDir.isDirectory) return emptyList()
        return modsDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            .mapNotNull { file -> inspectKff(file) }
            .toList()
    }

    private fun inspectKff(file: File): KffArchive? = runCatching {
        ZipFile(file).use { zip ->
            val metadataEntry = zip.getEntry(KFF_METADATA) ?: return@use null
            val metadata = serdesJson.parseToJsonElement(
                zip.getInputStream(metadataEntry).bufferedReader(StandardCharsets.UTF_8).readText()
            ).jsonObject
            val dependencies = metadata.dependencies()
            val kffDependencies = dependencies.filter { it.group == KFF_GROUP && it.artifact in kffArtifacts }
            if (kffDependencies.size != kffArtifacts.size || kffDependencies.map { it.artifact }.toSet() != kffArtifacts) {
                return@use null
            }
            if (kffDependencies.any { zip.getEntry(it.path) == null }) return@use null
            val hasRuntimeDependencies = dependencies.any { it !in kffDependencies }
            val hasShadedRuntime = zip.entries().asSequence().any { entry ->
                !entry.isDirectory && isRuntimeEntry(entry.name)
            }
            KffArchive(
                file = file,
                metadata = metadata,
                nestedEntries = kffDependencies.mapTo(linkedSetOf()) { it.path },
                needsNormalization = hasRuntimeDependencies || hasShadedRuntime
            )
        }
    }.getOrNull()

    private fun JsonObject.dependencies(): List<KffDependency> =
        (this["jars"] as? JsonArray).orEmpty().mapNotNull { element ->
            val jar = element as? JsonObject ?: return@mapNotNull null
            val identifier = jar["identifier"] as? JsonObject ?: return@mapNotNull null
            val path = jar["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val group = identifier["group"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val artifact = identifier["artifact"]?.jsonPrimitive?.contentOrNull.orEmpty()
            KffDependency(path, group, artifact)
        }

    private fun normalizeKff(archive: KffArchive, cacheRoot: File) {
        if (!archive.needsNormalization) return
        val sourceSha1 = archive.file.sha1.lowercase()
        val root = cacheRoot.resolve("thin-kff").apply { mkdirs() }
        val originalRoot = cacheRoot.resolve("original-kff").apply { mkdirs() }
        val normalized = root.resolve("$CACHE_SCHEMA-$sourceSha1.jar")
        val original = originalRoot.resolve("$sourceSha1.jar")
        if (!original.isFile || !original.sha1.equals(sourceSha1, ignoreCase = true)) {
            Files.copy(archive.file.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (!normalized.isFile || !isThinKff(normalized)) {
            val temporary = Files.createTempFile(root.toPath(), "${normalized.name}-", ".tmp").toFile()
            try {
                writeThinKff(archive, temporary)
                check(isThinKff(temporary)) { "生成的thin KFF校验失败: ${temporary.absolutePath}" }
                moveAtomically(temporary, normalized)
            } finally {
                Files.deleteIfExists(temporary.toPath())
            }
        }
        val parent = archive.file.parentFile ?: error("KFF文件缺少父目录: ${archive.file.absolutePath}")
        val replacement = Files.createTempFile(parent.toPath(), "${archive.file.name}.rdi-kff-", ".tmp").toFile()
        try {
            Files.copy(normalized.toPath(), replacement.toPath(), StandardCopyOption.REPLACE_EXISTING)
            moveAtomically(replacement, archive.file)
        } finally {
            Files.deleteIfExists(replacement.toPath())
        }
    }

    private fun writeThinKff(archive: KffArchive, target: File) {
        target.parentFile?.mkdirs()
        ZipFile(archive.file).use { source ->
            ZipOutputStream(target.outputStream().buffered()).use { output ->
                val written = HashSet<String>()
                source.entries().asSequence()
                    .filter { entry -> shouldPreserve(entry.name, archive.nestedEntries) }
                    .forEach { entry ->
                        writeEntry(source, entry, output, written)
                    }
                val filteredMetadata = buildJsonObject {
                    archive.metadata.forEach { (key, value) ->
                        put(key, if (key == "jars") {
                            JsonArray(
                                archive.metadata.dependencies()
                                    .filter { it.path in archive.nestedEntries }
                                    .mapNotNull { dependency ->
                                        archive.metadata["jars"]?.jsonArray?.firstOrNull { element ->
                                            element.jsonObject["path"]?.jsonPrimitive?.contentOrNull == dependency.path
                                        }
                                    }
                            )
                        } else {
                            value
                        })
                    }
                }
                val metadataName = KFF_METADATA
                if (written.add(metadataName)) {
                    output.putNextEntry(ZipEntry(metadataName))
                    output.write(filteredMetadata.toString().toByteArray(StandardCharsets.UTF_8))
                    output.closeEntry()
                }
            }
        }
    }

    private fun shouldPreserve(name: String, nestedEntries: Set<String>): Boolean = when {
        name == "META-INF/MANIFEST.MF" -> true
        name == KFF_METADATA -> false
        name.startsWith("META-INF/jarjar/") -> name in nestedEntries
        name.startsWith("META-INF/services/") -> false
        name.startsWith("META-INF/") && name.substringAfterLast('/').substringAfterLast('.', "").uppercase() in setOf("SF", "RSA", "DSA") -> false
        isRuntimeEntry(name) -> false
        else -> true
    }

    private fun isRuntimeEntry(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        return normalized.startsWith("kotlin/") ||
            normalized.startsWith("kotlinx/") ||
            normalized.startsWith("_COROUTINE/") ||
            normalized.startsWith("META-INF/versions/") ||
            normalized.endsWith(".kotlin_module") ||
            normalized.startsWith("META-INF/kotlin")
    }

    private fun writeEntry(
        source: ZipFile,
        entry: ZipEntry,
        output: ZipOutputStream,
        written: MutableSet<String>
    ) {
        if (entry.isDirectory || !written.add(entry.name)) return
        output.putNextEntry(ZipEntry(entry.name))
        source.getInputStream(entry).use { it.copyTo(output) }
        output.closeEntry()
    }

    private fun isThinKff(file: File): Boolean = runCatching {
        ZipFile(file).use { zip ->
            val metadataEntry = zip.getEntry(KFF_METADATA) ?: return@use false
            val metadata = serdesJson.parseToJsonElement(
                zip.getInputStream(metadataEntry).bufferedReader(StandardCharsets.UTF_8).readText()
            ).jsonObject
            val dependencies = metadata.dependencies()
            dependencies.size == kffArtifacts.size &&
                dependencies.all { it.group == KFF_GROUP && it.artifact in kffArtifacts } &&
                dependencies.map { it.artifact }.toSet() == kffArtifacts &&
                dependencies.all { zip.getEntry(it.path) != null } &&
                zip.entries().asSequence().none { entry ->
                    !entry.isDirectory && isRuntimeEntry(entry.name)
                }
        }
    }.getOrDefault(false)

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

}
