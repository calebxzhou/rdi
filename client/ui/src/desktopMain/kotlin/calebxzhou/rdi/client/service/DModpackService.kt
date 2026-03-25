package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.*
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.ui.loadResourceBytes
import calebxzhou.rdi.common.exception.ModpackException
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.service.CurseForgeService.loadInfoCurseForge
import calebxzhou.rdi.common.service.CurseForgeService.mapMods
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.service.ModrinthService
import calebxzhou.rdi.common.service.ModrinthService.mapModrinthVersions
import calebxzhou.rdi.common.service.ModrinthService.toCardVo
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import kotlinx.io.buffered
import org.bson.types.ObjectId
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

private val lgr by Loggers

// ==================== Upload-only code (desktop-only) ====================

data class UploadPayload(
    val sourceDir: File,
    var mods: MutableList<Mod>,
    val mcVersion: McVersion,
    val modloader: ModLoader,
    val sourceName: String,
    val sourceVersion: String
)

data class ParsedUploadPayload(
    val payload: UploadPayload
)

suspend fun parseUploadPayload(
    file: File,
    onProgress: (String) -> Unit,
    onError: (String) -> Unit
): ParsedUploadPayload? {
    val prepared = try {
        prepareModpackSource(file)
    } catch (e: Exception) {
        lgr.warn { "处理整合包失败: ${file.absolutePath + "\n" + e }" }
        onError(e.message ?: "处理整合包失败")
        return null
    }
    val packType = detectPackType(prepared.rootDir)
    if (packType != PackType.UNKNOWN && !hasOverridesDir(prepared.rootDir)) {
        val msg = "无效的整合包文件：缺少 overrides 目录"
        onError(msg)
        onProgress(msg)
        prepared.rootDir.deleteRecursivelyNoSymlink()
        return null
    }
    val embeddedMods = collectEmbeddedModFiles(prepared.rootDir)
    val embeddedMatches = try {
        matchEmbeddedModsAll(
            files = embeddedMods,
            onProgress = onProgress
        )
    } catch (e: Exception) {
        lgr.warn { "匹配整合包内置mod失败: ${file.absolutePath + "\n" + e }" }
        onError(e.message ?: "匹配整合包内置mod失败")
        prepared.rootDir.deleteRecursivelyNoSymlink()
        return null
    }
    if (embeddedMatches.removeFiles.isNotEmpty()) {
        embeddedMatches.removeFiles.forEach { it.delete() }
    }
    if (packType == PackType.MODRINTH) {
        val loaded = try {
            ModrinthService.loadModpack(prepared.rootDir).getOrThrow()
        } catch (e: Exception) {
            lgr.warn { "解析Modrinth整合包失败: ${file.absolutePath + "\n" + e }" }
            onError(e.message ?: "解析整合包失败")
            prepared.rootDir.deleteRecursivelyNoSymlink()
            return null
        }
        val mcVersion = loaded.mcVersion
        val modloader = loaded.modloader
        val versionName = loaded.index.versionId.ifBlank { "1.0" }
        val mods = (loaded.mods + embeddedMatches.mods)
            .distinctBy { "${it.platform}:${it.projectId}:${it.fileId}:${it.hash}" }
            .toMutableList()
        ModService.run { mods.postProcessModSides() }
        return ParsedUploadPayload(
            UploadPayload(
                sourceDir = prepared.rootDir,
                mods = mods,
                mcVersion = mcVersion,
                modloader = modloader,
                sourceName = loaded.index.name,
                sourceVersion = versionName
            )
        )
    }

    if (packType != PackType.CURSEFORGE) {
        onError("无效的整合包文件：缺少 manifest.json 或 modrinth.index.json")
        onProgress("无效的整合包文件：缺少 manifest.json 或 modrinth.index.json")
        prepared.rootDir.deleteRecursivelyNoSymlink()
        return null
    }
    val modpackData = try {
        loadCurseForgeFromDir(prepared.rootDir)
    } catch (e: Exception) {
        lgr.warn { "解析CurseForge整合包失败: ${file.absolutePath + "\n" + e }" }
        onError(e.message ?: "解析整合包失败")
        onProgress(e.message ?: "解析整合包失败")
        prepared.rootDir.deleteRecursivelyNoSymlink()
        return null
    }

    return try {
        val baseMods = modpackData.manifest.files.mapMods()
        val mods = (baseMods + embeddedMatches.mods)
            .distinctBy { "${it.platform}:${it.projectId}:${it.fileId}:${it.hash}" }
            .toMutableList()
        ModService.run { mods.postProcessModSides() }
        val mcVersion = McVersion.from(modpackData.manifest.minecraft.version)
        if (mcVersion == null) {
            onError("不支持的MC版本: ${modpackData.manifest.minecraft.version}")
            onProgress("不支持的MC版本: ${modpackData.manifest.minecraft.version}")
            prepared.rootDir.deleteRecursivelyNoSymlink()
            return null
        }
        val modloader = ModLoader.from(modpackData.manifest.minecraft.modLoaders.firstOrNull()?.id.orEmpty())
        if (modloader == null) {
            onError("不支持的Mod加载器: ${modpackData.manifest.minecraft.modLoaders.firstOrNull()?.id.orEmpty()}")
            onProgress("不支持的Mod加载器: ${modpackData.manifest.minecraft.modLoaders.firstOrNull()?.id.orEmpty()}")
            prepared.rootDir.deleteRecursivelyNoSymlink()
            return null
        }
        ParsedUploadPayload(
            UploadPayload(
                sourceDir = prepared.rootDir,
                mods = mods,
                mcVersion = mcVersion,
                modloader = modloader,
                sourceName = modpackData.manifest.name,
                sourceVersion = modpackData.manifest.version.ifBlank { "1.0" }
            )
        )
    } catch (e: Exception) {
        lgr.warn { "解析CurseForge整合包mod列表失败: ${file.absolutePath + "\n" + e }" }
        onError("解析整合包失败: ${e.message}")
        onProgress("解析整合包失败: ${e.message}")
        prepared.rootDir.deleteRecursivelyNoSymlink()
        null
    }
}

private data class EmbeddedMatchResult(
    val mods: List<Mod>,
    val removeFiles: Set<File>
)

private data class EmbeddedMergedResult(
    val mods: List<Mod>,
    val removeFiles: Set<File>
)

private suspend fun matchEmbeddedModsCF(
    files: List<File>
): EmbeddedMatchResult {
    if (files.isEmpty()) return EmbeddedMatchResult(emptyList(), emptySet())
    val result = files.loadInfoCurseForge()
    val matched = result.matched
    if (matched.isEmpty()) return EmbeddedMatchResult(emptyList(), emptySet())
    val removeFiles = matched.mapNotNull { it.file }.toSet()
    result.matched.forEach { it.file = null }
    return EmbeddedMatchResult(matched, removeFiles)
}

private suspend fun matchEmbeddedModsMR(
    files: List<File>
): EmbeddedMatchResult {
    if (files.isEmpty()) return EmbeddedMatchResult(emptyList(), emptySet())
    val hashToVersion = files.mapModrinthVersions()
    if (hashToVersion.isEmpty()) return EmbeddedMatchResult(emptyList(), emptySet())
    val projectIds = hashToVersion.values.map { it.projectId }.distinct()
    val projectMap = ModrinthService.getMultipleProjects(projectIds).associateBy { it.id }
    val matched = mutableListOf<Mod>()
    val removeFiles = mutableSetOf<File>()
    files.forEach { file ->
        val sha1 = file.sha1
        val version = hashToVersion[sha1] ?: return@forEach
        val project = projectMap[version.projectId]
        val slug = project?.slug?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension.ifBlank { version.projectId }
        val side = project?.run {
            if (serverSide == "unsupported") {
                return@run Mod.Side.CLIENT
            }
            if (clientSide == "unsupported") {
                return@run Mod.Side.SERVER
            }
            Mod.Side.BOTH
        } ?: Mod.Side.BOTH
        val fileInfo = version.files.firstOrNull { it.hashes["sha1"] == sha1 }
        val downloadUrls = fileInfo?.url?.let { listOf(it) } ?: emptyList()
        val mod = Mod(
            platform = "mr",
            projectId = version.projectId,
            slug = slug,
            fileId = version.id,
            hash = sha1,
            side = side,
            downloadUrls = downloadUrls
        ).apply {
            this.file = null
            this.vo = project?.toCardVo(file)?.copy(side = side)
        }
        matched += mod
        removeFiles += file
    }
    return EmbeddedMatchResult(matched, removeFiles)
}

private suspend fun matchEmbeddedModsAll(
    files: List<File>,
    onProgress: (String) -> Unit
): EmbeddedMergedResult {
    if (files.isEmpty()) return EmbeddedMergedResult(emptyList(), emptySet())
    onProgress("发现整合包内置mod: ${files.size} 个，先匹配Modrinth")
    val mrResult = try {
        matchEmbeddedModsMR(files)
    } catch (e: Exception) {
        lgr.warn { "Modrinth匹配内置mod失败" + "\n" + e }
        throw ModpackException("Modrinth匹配内置mod失败: ${e.message}")
    }
    val remaining = files.filterNot { it in mrResult.removeFiles }
    onProgress("Modrinth匹配完成：${mrResult.mods.size} 个，开始匹配CurseForge")
    val cfResult = try {
        matchEmbeddedModsCF(remaining)
    } catch (e: Exception) {
        lgr.warn { "CurseForge匹配内置mod失败" + "\n" + e }
        throw ModpackException("CurseForge匹配内置mod失败: ${e.message}")
    }
    onProgress("匹配完成：MR ${mrResult.mods.size} 个，CF ${cfResult.mods.size} 个，处理结果中，请等一分钟...")
    val mergedMods = (mrResult.mods + cfResult.mods)
        .distinctBy { "${it.platform}:${it.projectId}:${it.fileId}:${it.hash}" }
    val removeFiles = mrResult.removeFiles + cfResult.removeFiles
    return EmbeddedMergedResult(mergedMods, removeFiles)
}

private data class PreparedModpack(
    val rootDir: File,
    val name: String
)

private fun prepareModpackSource(input: File): PreparedModpack {
    val tempDir = Files.createTempDirectory(ClientDirs.packProcDir.toPath(), "pack-").toFile()
    if (input.isDirectory) {
        input.copyRecursively(tempDir, overwrite = true)
        return PreparedModpack(tempDir, input.name)
    }
    input.openChineseZip().use { zip ->
        zip.entries().asSequence().forEach { entry ->
            val name = entry.name.replace('\\', '/').trimStart('/')
            if (name.isBlank()) return@forEach
            val outFile = tempDir.resolve(name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { inputStream ->
                    outFile.outputStream().use { output -> inputStream.copyTo(output) }
                }
            }
        }
    }
    return PreparedModpack(tempDir, input.nameWithoutExtension)
}

private fun detectPackType(rootDir: File): PackType {
    var hasMrIndex = false
    var hasCfManifest = false
    rootDir.walkTopDown().forEach { file ->
        if (!file.isFile) return@forEach
        when (file.name) {
            "modrinth.index.json" -> hasMrIndex = true
            "manifest.json" -> hasCfManifest = true
        }
        if (hasMrIndex || hasCfManifest) return@forEach
    }
    return when {
        hasMrIndex -> PackType.MODRINTH
        hasCfManifest -> PackType.CURSEFORGE
        else -> PackType.UNKNOWN
    }
}

private fun findFile(rootDir: File, fileName: String): File? {
    return rootDir.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
}

private fun loadCurseForgeFromDir(rootDir: File): CurseForgeModpackData {
    val manifestFile = findFile(rootDir, "manifest.json")
        ?: throw ModpackException("整合包缺少文件：manifest.json")
    if (!hasOverridesDir(rootDir)) {
        throw ModpackException("整合包缺少目录：overrides")
    }
    val manifestJson = manifestFile.readText(Charsets.UTF_8)
    val manifest = try {
        calebxzhou.rdi.common.serdesJson.decodeFromString<CurseForgePackManifest>(manifestJson)
    } catch (e: Exception) {
        lgr.warn { "manifest.json解析失败: ${manifestFile.absolutePath + "\n" + e }" }
        throw ModpackException("manifest.json 解析失败: ${e.message}")
    }
    return CurseForgeModpackData(
        manifest = manifest,
        file = rootDir
    )
}

private fun collectEmbeddedModFiles(rootDir: File): List<File> {
    return rootDir.walkTopDown()
        .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
        .filter { it.invariantSeparatorsPath.contains("/mods/") }
        .toList()
}

private fun hasOverridesDir(rootDir: File): Boolean {
    return rootDir.walkTopDown().any { it.isDirectory && it.name.equals("overrides", ignoreCase = true) }
}

private fun buildZipFromDir(rootDir: File, baseName: String): File {
    val safeName = baseName.ifBlank { "modpack" }
    val target = ClientDirs.packProcDir.resolve("${safeName}_${System.currentTimeMillis()}.zip")
    val addedDirs = mutableSetOf<String>()
    ZipOutputStream(target.outputStream()).use { out ->
        rootDir.walkTopDown().forEach { file ->
            if (file == rootDir) return@forEach
            val relative = file.relativeTo(rootDir).invariantSeparatorsPath
            if (relative.isBlank()) return@forEach
            val relativeLower = relative.lowercase()
            val topLevel = relative.substringBefore('/', relative)
            writeProcessedEntry(
                relative = relative,
                relativeLower = relativeLower,
                isDirectory = file.isDirectory,
                lastModified = file.lastModified(),
                size = file.length(),
                topLevel = topLevel,
                out = out,
                addedDirs = addedDirs,
                readAllBytes = { file.readBytes() },
                copyToOut = { output -> file.inputStream().use { it.copyTo(output) } },
                resourcepackBytes = { readResourcepackFile(file, relativeLower) },
                nestedZipBytes = if (relativeLower.endsWith(".zip") || relativeLower.endsWith(".jar")) {
                    { processNestedZip(file) }
                } else null
            )
        }
    }
    return target
}

private fun processNestedZip(zipFile: File): ByteArray {
    return ByteArrayOutputStream().use { baos ->
        ZipOutputStream(baos).use { out ->
            val addedDirs = mutableSetOf<String>()
            zipFile.openChineseZip().use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val relative = entry.name.replace('\\', '/').trimStart('/')
                    if (relative.isBlank()) return@forEach
                    val relativeLower = relative.lowercase()
                    val topLevel = relative.substringBefore('/', relative)
                    val isNestedJarEntry = zipFile.extension.equals("jar", ignoreCase = true)
                    writeProcessedEntry(
                        relative = relative,
                        relativeLower = relativeLower,
                        isDirectory = entry.isDirectory,
                        lastModified = entry.time,
                        size = entry.size.takeIf { it != -1L },
                        topLevel = topLevel,
                        out = out,
                        addedDirs = addedDirs,
                        readAllBytes = { zip.getInputStream(entry).use { it.readBytes() } },
                        copyToOut = { output -> zip.getInputStream(entry).use { it.copyTo(output) } },
                        resourcepackBytes = { readResourcepackEntry(zip, entry, relativeLower) },
                        nestedZipBytes = null,
                        skipCacheDirectory = !isNestedJarEntry
                    )
                }
            }
        }
        baos.toByteArray()
    }
}

private fun shouldSkipEntry(
    relativeLower: String,
    isDirectory: Boolean,
    skipCacheDirectory: Boolean = true
): Boolean {
    if (disallowedClientPaths.any { relativeLower.startsWith(it) }) return true
    if (relativeLower.startsWith("kubejs/probe/")) return true
    if (skipCacheDirectory && containsCacheDirectory(relativeLower)) return true
    if (relativeLower.contains("yes_steve_model") || relativeLower.contains("史蒂夫模型")) return true
    if (relativeLower.endsWith(".mca") && relativeLower.contains("/saves/")) return true
    if (isQuestLangEntryDisallowed(relativeLower, isDirectory)) return true
    return false
}

private fun containsCacheDirectory(relativeLower: String): Boolean {
    val normalized = relativeLower.replace('\\', '/').trim('/')
    if (normalized.isEmpty()) return false
    return normalized.split('/').any { it.equals("cache", ignoreCase = true) }
}

private fun writeProcessedEntry(
    relative: String,
    relativeLower: String,
    isDirectory: Boolean,
    lastModified: Long,
    size: Long?,
    topLevel: String,
    out: ZipOutputStream,
    addedDirs: MutableSet<String>,
    readAllBytes: () -> ByteArray,
    copyToOut: (OutputStream) -> Unit,
    resourcepackBytes: (() -> ByteArray?)?,
    nestedZipBytes: (() -> ByteArray)?,
    skipCacheDirectory: Boolean = true
) {
    if (shouldSkipEntry(relativeLower, isDirectory, skipCacheDirectory = skipCacheDirectory)) return

    if (isDirectory) {
        addDirectoryEntry(relative, out, addedDirs)
        return
    }

    if (topLevel == "resourcepacks") {
        val bytes = resourcepackBytes?.invoke() ?: return
        ensureZipParents(relative, out, addedDirs)
        val entry = ZipEntry(relative).apply { time = lastModified }
        out.putNextEntry(entry)
        out.write(bytes)
        out.closeEntry()
        return
    }

    ensureZipParents(relative, out, addedDirs)
    val entry = ZipEntry(relative).apply { time = lastModified }
    out.putNextEntry(entry)
    when {
        relativeLower.endsWith(".class") -> {
            copyToOut(out)
        }
        nestedZipBytes != null -> {
            out.write(nestedZipBytes())
        }
        relativeLower.endsWith(".png") -> {
            val processed = compressPngIfNeeded(readAllBytes())
            out.write(processed)
        }
        relativeLower.endsWith(".ogg") -> {
            val bytes = if (size != null) {
                if (size > OGG_MAX_SIZE_BYTES) emptyOggBytes else readAllBytes()
            } else {
                val raw = readAllBytes()
                if (raw.size > OGG_MAX_SIZE_BYTES) emptyOggBytes else raw
            }
            out.write(bytes)
        }
        else -> copyToOut(out)
    }
    out.closeEntry()
}

private fun readResourcepackEntry(source: java.util.zip.ZipFile, entry: ZipEntry, relativeLower: String): ByteArray? {
    if (entry.size != -1L && entry.size > RESOURCEPACK_MAX_SIZE_BYTES) {
        return null
    }
    val rawBytes = source.getInputStream(entry).use { input ->
        when {
            entry.size == -1L -> input.readBytes()
            entry.size > Int.MAX_VALUE -> return null
            entry.size > RESOURCEPACK_MAX_SIZE_BYTES -> return null
            else -> input.readNBytes(entry.size.toInt())
        }
    }
    if (relativeLower.endsWith(".ogg") && rawBytes.size > OGG_MAX_SIZE_BYTES) return emptyOggBytes
    val processed = if (relativeLower.endsWith(".png")) compressPngIfNeeded(rawBytes) else rawBytes
    if (processed.size > RESOURCEPACK_MAX_SIZE_BYTES) return null
    return processed
}

private val disallowedClientPaths = setOf("shaderpacks")
private val allowedQuestLangFiles = setOf("en_us.snbt", "zh_cn.snbt")
private const val QUEST_LANG_PREFIX = "config/ftbquests/quests/lang/"
private const val RESOURCEPACK_MAX_SIZE_BYTES = 1024L * 1024
private const val OGG_MAX_SIZE_BYTES = 512L * 1024
private const val PNG_COMPRESSION_THRESHOLD_BYTES = 50 * 1024
private const val PNG_COMPRESSION_JPEG_QUALITY = 0.5f
private val emptyOggBytes by lazy { loadResourceBytes("assets/empty.ogg") }

private fun isQuestLangEntryDisallowed(relativeLower: String, isDirectory: Boolean): Boolean {
    if (!relativeLower.startsWith(QUEST_LANG_PREFIX)) return false
    val remainder = relativeLower.removePrefix(QUEST_LANG_PREFIX)
    if (remainder.isEmpty()) return false
    if (isDirectory) return true
    if (remainder.contains('/')) return true
    return remainder !in allowedQuestLangFiles
}

private fun readResourcepackFile(file: File, relativeLower: String): ByteArray? {
    if (file.length() > RESOURCEPACK_MAX_SIZE_BYTES) return null
    if (relativeLower.endsWith(".ogg") && file.length() > OGG_MAX_SIZE_BYTES) return emptyOggBytes
    val rawBytes = file.inputStream().use { input ->
        when {
            file.length() > Int.MAX_VALUE -> return null
            else -> input.readBytes()
        }
    }
    val processed = if (relativeLower.endsWith(".png")) compressPngIfNeeded(rawBytes) else rawBytes
    if (processed.size > RESOURCEPACK_MAX_SIZE_BYTES) return null
    return processed
}

private fun addDirectoryEntry(
    rawPath: String,
    output: ZipOutputStream,
    addedDirs: MutableSet<String>
) {
    val sanitized = rawPath.trim('/').ifEmpty { return }
    ensureZipParents(sanitized, output, addedDirs)
    val dirEntry = "$sanitized/"
    if (addedDirs.add(dirEntry)) {
        output.putNextEntry(ZipEntry(dirEntry))
        output.closeEntry()
    }
}

private fun ensureZipParents(path: String, output: ZipOutputStream, addedDirs: MutableSet<String>) {
    val normalized = path.trim('/').ifEmpty { return }
    val parts = normalized.split('/')
    if (parts.size <= 1) return
    var current = ""
    for (i in 0 until parts.size - 1) {
        val part = parts[i]
        if (part.isEmpty()) continue
        current = if (current.isEmpty()) part else "$current/$part"
        val dirEntry = "$current/"
        if (addedDirs.add(dirEntry)) {
            output.putNextEntry(ZipEntry(dirEntry))
            output.closeEntry()
        }
    }
}

private fun compressPngIfNeeded(bytes: ByteArray): ByteArray {
    if (bytes.size <= PNG_COMPRESSION_THRESHOLD_BYTES) return bytes
    return try {
        val original = ImageIO.read(ByteArrayInputStream(bytes)) ?: return bytes
        val scaled = if (original.height > 720) {
            val targetHeight = 720
            val scale = targetHeight.toDouble() / original.height.toDouble()
            val targetWidth = (original.width * scale).toInt().coerceAtLeast(1)
            val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
            val g = resized.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, resized.width, resized.height)
            g.drawImage(original, 0, 0, targetWidth, targetHeight, null)
            g.dispose()
            resized
        } else {
            original
        }
        val rgbImage = if (scaled.type == BufferedImage.TYPE_INT_RGB) scaled else {
            val converted = BufferedImage(scaled.width, scaled.height, BufferedImage.TYPE_INT_RGB)
            val graphics = converted.createGraphics()
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, converted.width, converted.height)
            graphics.drawImage(scaled, 0, 0, null)
            graphics.dispose()
            converted
        }
        val writerIterator = ImageIO.getImageWritersByFormatName("jpg")
        if (!writerIterator.hasNext()) return bytes
        val writer = writerIterator.next()
        try {
            val params = writer.defaultWriteParam
            if (params.canWriteCompressed()) {
                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = PNG_COMPRESSION_JPEG_QUALITY
            }
            ByteArrayOutputStream().use { baos ->
                val imageOut = ImageIO.createImageOutputStream(baos) ?: return bytes
                imageOut.use { outputStream ->
                    writer.output = outputStream
                    writer.write(null, IIOImage(rgbImage, null, null), params)
                }
                baos.toByteArray()
            }
        } finally {
            writer.dispose()
        }
    } catch (e: Exception) {
        lgr.warn { "压缩资源包PNG失败" + "\n" + e }
        throw e
    }
}

suspend fun uploadModpack(
    payload: UploadPayload,
    mods: List<Mod>,
    modpackName: String,
    versionName: String,
    iconUrl: String?,
    sourceUrl: String?,
    info: String?,
    updateModpackId: ObjectId?,
    onProgress: (String) -> Unit,
    onError: (String) -> Unit,
    onDone: (String) -> Unit
) {
    onProgress("正在打包整合包...请等一两分钟")
    val uploadZip = try {
        buildZipFromDir(payload.sourceDir, payload.sourceName)
    } catch (e: Exception) {
        lgr.warn { "打包整合包失败: ${payload.sourceDir.absolutePath + "\n" + e }" }
        payload.sourceDir.deleteRecursivelyNoSymlink()
        onError("打包失败: ${e.message}")
        return
    }

    val totalBytes = uploadZip.length()
    val startTime = System.nanoTime()
    var lastProgressUpdate = 0L

    try {
        if (updateModpackId != null) {
            uploadNewVersion(
                modpackId = updateModpackId,
                versionName = versionName,
                mods = mods,
                uploadZip = uploadZip,
                totalBytes = totalBytes,
                startTime = startTime,
                lastProgressUpdate = lastProgressUpdate,
                onProgress = onProgress,
                onError = onError,
                onDone = onDone
            )
        } else {
            uploadNewModpack(
                modpackName = modpackName,
                versionName = versionName,
                mcVersion = payload.mcVersion,
                modloader = payload.modloader,
                mods = mods,
                iconUrl = iconUrl,
                sourceUrl = sourceUrl,
                info = info,
                uploadZip = uploadZip,
                totalBytes = totalBytes,
                startTime = startTime,
                lastProgressUpdate = lastProgressUpdate,
                onProgress = onProgress,
                onError = onError,
                onDone = onDone
            )
        }
    } catch (e: Exception) {
        lgr.warn { "上传整合包失败: ${payload.sourceName + "\n" + e } $versionName" }
        onError("上传失败: ${e.message ?: "未知错误"}")
    } finally {
        uploadZip.delete()
        payload.sourceDir.deleteRecursivelyNoSymlink()
    }
}

private suspend fun uploadNewModpack(
    modpackName: String,
    versionName: String,
    mcVersion: McVersion,
    modloader: ModLoader,
    mods: List<Mod>,
    iconUrl: String?,
    sourceUrl: String?,
    info: String?,
    uploadZip: File,
    totalBytes: Long,
    startTime: Long,
    lastProgressUpdate: Long,
    onProgress: (String) -> Unit,
    onError: (String) -> Unit,
    onDone: (String) -> Unit
) {
    onProgress("创建新整合包 $modpackName...")

    val dto = Modpack.CreateWithVersionDto(
        name = modpackName,
        verName = versionName,
        mcVer = mcVersion,
        modLoader = modloader,
        iconUrl = iconUrl?.trim()?.ifBlank { null },
        sourceUrl = sourceUrl?.trim()?.ifBlank { null },
        info = info?.trim()?.ifBlank { null },
        mods = mods.toMutableList()
    )
    val dtoJson = calebxzhou.rdi.common.serdesJson.encodeToString(dto)

    var lastUpdate = lastProgressUpdate
    val multipartContent = MultiPartFormDataContent(
        formData {
            append(
                key = "dto",
                value = dtoJson,
                headers = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            )
            append(
                key = "file",
                value = InputProvider { uploadZip.inputStream().asInput().buffered() },
                headers = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"${uploadZip.name}\"")
                }
            )
        }
    )

    val createResp = server.makeRequest<Unit>(
        path = "modpack",
        method = HttpMethod.Post,
    ) {
        timeout {
            requestTimeoutMillis = 60 * 60 * 1000L
            socketTimeoutMillis = 60 * 60 * 1000L
        }
        setBody(multipartContent)
        onUpload { bytesSentTotal, contentLength ->
            val now = System.nanoTime()
            val shouldUpdate = contentLength != null && bytesSentTotal == contentLength ||
                now - lastUpdate > 75_000_000L
            if (shouldUpdate) {
                lastUpdate = now
                val elapsedSeconds = (now - startTime) / 1_000_000_000.0
                val total = contentLength?.takeIf { it > 0 } ?: totalBytes
                val percent = if (total <= 0) 100 else ((bytesSentTotal * 100) / total).toInt()
                val speed = if (elapsedSeconds <= 0) 0.0 else bytesSentTotal / elapsedSeconds
                onProgress(
                    buildString {
                        appendLine("正在上传整合包 $modpackName...")
                        appendLine(
                            "进度：${
                                percent.coerceIn(0, 100)
                            }% (${bytesSentTotal.humanFileSize}/${total.humanFileSize})"
                        )
                        appendLine("速度：${speed.humanSpeed}")
                    }
                )
            }
        }
    }

    if (!createResp.ok) {
        onError(createResp.msg)
        return
    }

    val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
    val speed = if (elapsedSeconds <= 0) 0.0 else totalBytes / elapsedSeconds
    onDone(
        buildString {
            appendLine("文件大小: ${totalBytes.humanFileSize}")
            appendLine("平均速度: ${speed.humanSpeed}")
            appendLine("耗时: ${"%.1f".format(elapsedSeconds)}秒")
            appendLine("传完了 服务器要开始构建 等5分钟 结果发你信箱里")
        }
    )
}

private suspend fun uploadNewVersion(
    modpackId: ObjectId,
    versionName: String,
    mods: List<Mod>,
    uploadZip: File,
    totalBytes: Long,
    startTime: Long,
    lastProgressUpdate: Long,
    onProgress: (String) -> Unit,
    onError: (String) -> Unit,
    onDone: (String) -> Unit
) {
    onProgress("上传新版本 $versionName...")

    val modpackIdStr = modpackId.toHexString()
    val versionEncoded = versionName.urlEncoded
    val modsJson = calebxzhou.rdi.common.serdesJson.encodeToString(mods.toMutableList())

    var lastUpdate = lastProgressUpdate
    val multipartContent = MultiPartFormDataContent(
        formData {
            append(
                key = "mods",
                value = modsJson,
                headers = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            )
            append(
                key = "file",
                value = InputProvider { uploadZip.inputStream().asInput().buffered() },
                headers = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"${uploadZip.name}\"")
                }
            )
        }
    )

    val createVersionResp = server.makeRequest<Unit>(
        path = "modpack/$modpackIdStr/version/$versionEncoded",
        method = HttpMethod.Post,
    ) {
        timeout {
            requestTimeoutMillis = 60 * 60 * 1000L
            socketTimeoutMillis = 60 * 60 * 1000L
        }
        setBody(multipartContent)
        onUpload { bytesSentTotal, contentLength ->
            val now = System.nanoTime()
            val shouldUpdate = contentLength != null && bytesSentTotal == contentLength ||
                now - lastUpdate > 75_000_000L
            if (shouldUpdate) {
                lastUpdate = now
                val elapsedSeconds = (now - startTime) / 1_000_000_000.0
                val total = contentLength?.takeIf { it > 0 } ?: totalBytes
                val percent = if (total <= 0) 100 else ((bytesSentTotal * 100) / total).toInt()
                val speed = if (elapsedSeconds <= 0) 0.0 else bytesSentTotal / elapsedSeconds
                onProgress(
                    buildString {
                        appendLine("正在上传版本 ${versionName}...")
                        appendLine(
                            "进度：${
                                percent.coerceIn(0, 100)
                            }% (${bytesSentTotal.humanFileSize}/${total.humanFileSize})"
                        )
                        appendLine("速度：${speed.humanSpeed}")
                    }
                )
            }
        }
    }

    if (!createVersionResp.ok) {
        onError(createVersionResp.msg)
        return
    }

    val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
    val speed = if (elapsedSeconds <= 0) 0.0 else totalBytes / elapsedSeconds
    onDone(
        buildString {
            appendLine("文件大小: ${totalBytes.humanFileSize}")
            appendLine("平均速度: ${speed.humanSpeed}")
            appendLine("耗时: ${"%.1f".format(elapsedSeconds)}秒")
            appendLine("传完了 服务器要开始构建 等5分钟 结果发你信箱里")
        }
    )
}

private enum class PackType { MODRINTH, CURSEFORGE, UNKNOWN }

