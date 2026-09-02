package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.NodeRefreshCoordinator
import calebxzhou.rdi.client.service.ModpackService.startInstallTask2
import calebxzhou.rdi.client.service.prepareRdiPack2ImportTask
import calebxzhou.rdi.client.service.rdiPack2ExportTask
import calebxzhou.rdi.client.service.PreparedRdiPack2Task
import calebxzhou.rdi.client.service.content.ClientContentStore
import calebxzhou.rdi.client.service.content.ContentDigest
import calebxzhou.rdi.client.service.content.ContentDigestAlgorithm
import calebxzhou.rdi.client.service.content.ContentRequest
import calebxzhou.rdi.client.service.content.ContentSource
import calebxzhou.rdi.client.service.content.toClientContentRequest
import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.archive.forEachArchiveEntry
import calebxzhou.rdi.common.archive.listArchiveEntries
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.client.ui.pickAwtSaveFile
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.service.murmur2
import calebxzhou.rdi.common.service.runInline
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.util.sha1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun selectRdiModpackFile(): File? {
    val owner = Frame()
    try {
        val dialog = FileDialog(owner, "选择RDI整合包", FileDialog.LOAD).apply {
            directory = File(System.getProperty("user.home"), "Downloads").absolutePath
            file = "*.rdi*"
            filenameFilter = java.io.FilenameFilter { dir, name ->
                val target = File(dir, name)
                target.isDirectory || name.endsWith(".rdimodpack", ignoreCase = true) || name.endsWith(".rdipack2", ignoreCase = true)
            }
        }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(dir, name)
            .takeIf { it.exists() && it.isFile && (it.name.endsWith(".rdimodpack", ignoreCase = true) || it.name.endsWith(".rdipack2", ignoreCase = true)) }
    } finally {
        owner.dispose()
    }
}

private fun parseImportedModpackArchiveName(packArchiveName: String): Pair<org.bson.types.ObjectId, String> {
    val normalizedName = File(packArchiveName).name
    val suffix = ".tar.zst"
    val baseName = normalizedName
        .takeIf { it.endsWith(suffix, ignoreCase = true) }
        ?.substring(0, normalizedName.length - suffix.length)
        ?: throw IllegalStateException("整合包文件名无效：$normalizedName")
    val sepIndex = baseName.indexOf('_')
    if (sepIndex <= 0 || sepIndex >= baseName.lastIndex) {
        throw IllegalStateException("整合包文件名无效：$normalizedName")
    }
    val modpackId = org.bson.types.ObjectId(baseName.substring(0, sepIndex))
    val verName = baseName.substring(sepIndex + 1)
    return modpackId to verName
}

private fun exportPackContentRequest(
    packdir: ModpackLocalDir,
    sha1: String,
): ContentRequest {
    val modpackId = requireNotNull(packdir.vo).id
    val archiveName = "${modpackId}_${packdir.verName}.tar.zst"
    return ModpackService.clientPackContentRequest(modpackId, packdir.verName, sha1)
        .copy(relativePath = archiveName)
}

private fun exportModContentRequest(mod: Mod): ContentRequest =
    mod.toClientContentRequest(targetRelativePath = mod.fileName)

private fun pickRdiModpackSaveFile(defaultName: String): File? {
    val owner = Frame()
    try {
        val dialog = FileDialog(owner, "选择导出位置", FileDialog.SAVE).apply {
            directory = File(System.getProperty("user.home")).absolutePath
            file = defaultName
            filenameFilter = java.io.FilenameFilter { _, name ->
                name.endsWith(".rdimodpack", ignoreCase = true)
            }
        }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        val selected = File(dir, name)
        return if (selected.name.endsWith(".rdimodpack", ignoreCase = true)) {
            selected
        } else {
            File(selected.parentFile, "${selected.name}.rdimodpack")
        }
    } finally {
        owner.dispose()
    }
}
suspend fun exportRdiModpack2(
    packdir: ModpackLocalDir,
): Result<PreparedRdiPack2Task?> = withContext(Dispatchers.IO) {
    runCatching {
        val safeName = packdir.name.ifBlank { "modpack" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val output = pickAwtSaveFile(
            title = "选择整合包保存位置",
            defaultFileName = "${safeName}_${packdir.verName}.rdipack2",
            defaultDirectory = File(System.getProperty("user.home")),
            requiredExtension = "rdipack2",
            filenameFilter = { _, name -> name.endsWith(".rdipack2", ignoreCase = true) },
        ) ?: return@runCatching null
        rdiPack2ExportTask(packdir, output)
    }
}

suspend fun importRdiModpack2(): Result<PreparedRdiPack2Task?> = withContext(Dispatchers.IO) {
    runCatching {
        val file = selectRdiModpackFile() ?: return@runCatching null
        require(file.name.endsWith(".rdipack2", ignoreCase = true)) { "所选文件不是.rdipack2" }
        prepareRdiPack2ImportTask(file)
    }
}

suspend fun exportRdiModpack(
    packdir: ModpackLocalDir,
    onProgress: (String) -> Unit
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val modpackId = requireNotNull(packdir.vo).id
        val version = server.makeRequest<Modpack.Version>(
            "modpack/$modpackId/version/${packdir.verName}"
        ).data ?: throw IllegalStateException("无法获取整合包版本信息")

        val safeName = packdir.name.ifBlank { "modpack" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val outputFile = pickRdiModpackSaveFile("${safeName}_${packdir.verName}.rdimodpack")
            ?: return@runCatching

        val clientPackSha1 = server.makeRequest<String>(
            "modpack/$modpackId/version/${packdir.verName}/client/hash"
        ).data ?: throw IllegalStateException("客户端包hash为空")
        val packRequest = exportPackContentRequest(packdir, clientPackSha1)
        val packArchiveName = packRequest.relativePath

        val missingMods = mutableListOf<String>()
        val total = version.mods.size + 1
        var processed = 0
        val packResult = ClientContentStore.shared.use(
            requests = listOf(packRequest),
            onProgress = { progress -> onProgress(progress.message) },
        ) { paths ->
            val packArchive = paths.getValue(packRequest.id).toFile()
            TarZstArchiveWriter(outputFile).use { archive ->
                archive.addFile(packArchiveName, packArchive.readBytes(), packArchive.lastModified())
                processed += 1
                onProgress("导出整合包 ${processed}/${total}")

                version.mods.filterNot { it.side == Mod.Side.SERVER }.forEach { mod ->
                    val request = exportModContentRequest(mod)
                    val modResult = ClientContentStore.shared.use(
                        requests = listOf(request),
                        onProgress = { progress -> onProgress(progress.message) },
                    ) { modPaths ->
                        val modFile = modPaths.getValue(request.id).toFile()
                        archive.addFile(
                            "mods/${mod.fileName}",
                            modFile.readBytes(),
                            modFile.lastModified(),
                        )
                    }
                    if (modResult.isFailure) {
                        missingMods += mod.fileName
                        return@forEach
                    }
                    processed += 1
                    onProgress("导出MOD ${processed}/${total}")
                }
            }
        }
        packResult.getOrElse { cause ->
            throw IllegalStateException("整合包文件不存在，请先下载", cause)
        }

        if (missingMods.isNotEmpty()) {
            runCatching { outputFile.delete() }
            throw IllegalStateException("缺少 ${missingMods.size} 个MOD文件：${missingMods.take(3).joinToString()}${if (missingMods.size > 3) "..." else ""}")
        }
    }
}

private data class ImportedModEntry(
    val relativePath: String,
    val source: File,
)

private data class ImportedTrustedMod(
    val mod: Mod,
    val entry: ImportedModEntry,
    val digest: ContentDigest,
)

private val importedSha1Pattern = Regex("^[0-9a-fA-F]{40}$")

private fun trustedImportedDigest(mod: Mod): ContentDigest? {
    val value = mod.hash.trim()
    return if (mod.platform.equals("cf", ignoreCase = true)) {
        value.toULongOrNull()
            ?.takeIf { it <= UInt.MAX_VALUE.toULong() }
            ?.let { ContentDigest(ContentDigestAlgorithm.MURMUR2, it.toString()) }
    } else {
        value.takeIf { importedSha1Pattern.matches(it) }
            ?.let { ContentDigest(ContentDigestAlgorithm.SHA1, it.lowercase()) }
    }
}

private fun trustedImportedSha1(value: String?): ContentDigest? = value
    ?.trim()
    ?.takeIf { importedSha1Pattern.matches(it) }
    ?.let { ContentDigest(ContentDigestAlgorithm.SHA1, it.lowercase()) }

private fun importedDigestValue(file: File, digest: ContentDigest): String = when (digest.algorithm) {
    ContentDigestAlgorithm.SHA1 -> file.sha1
    ContentDigestAlgorithm.MURMUR2 -> file.toPath().murmur2.toULong().toString()
    ContentDigestAlgorithm.SHA256 -> error("导入整合包不支持SHA-256摘要")
}

private fun requireImportedDigest(file: File, digest: ContentDigest, displayName: String) {
    val actual = importedDigestValue(file, digest)
    check(actual.equals(digest.normalizedValue, ignoreCase = true)) {
        "${displayName}摘要不匹配：期望${digest.normalizedValue}，实际$actual"
    }
}

private fun importedContentRequest(
    id: String,
    entry: ImportedModEntry,
    digest: ContentDigest,
): ContentRequest = ContentRequest(
    id = id,
    relativePath = entry.relativePath,
    size = entry.source.length(),
    digests = listOf(digest),
    sources = listOf(
        ContentSource(
            knownSize = entry.source.length(),
            name = entry.relativePath,
            localOnly = true,
            downloader = { target, _ ->
                runCatching {
                    Files.copy(
                        entry.source.toPath(),
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            },
        )
    ),
    allowNetwork = false,
    displayName = entry.relativePath,
)

private fun importedEntryTarget(root: Path, relativePath: String): Path {
    val normalizedRoot = root.toAbsolutePath().normalize()
    val target = normalizedRoot.resolve(relativePath).normalize()
    require(target.startsWith(normalizedRoot)) { "导入整合包路径越界：$relativePath" }
    return target
}

private fun writeImportedEntry(root: Path, relativePath: String, bytes: ByteArray): File {
    val target = importedEntryTarget(root, relativePath)
    target.parent?.let(Files::createDirectories)
    Files.newOutputStream(
        target,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
    ).use { output -> output.write(bytes) }
    return target.toFile()
}

private fun ImportedModEntry.matches(mod: Mod): Boolean {
    val fileName = File(relativePath).name
    return mod.fileNames.any { it == fileName }
}

private fun buildImportedInstallTask(
    transactionDir: File,
    packArchiveName: String,
    packFile: File,
    packIsTrusted: Boolean,
    modpackVo: Modpack.BriefVo,
    version: Modpack.Version,
    trustedMods: List<Mod>,
    untrustedMods: List<ImportedModEntry>,
): Task2 {
    val normalInstallTask = if (untrustedMods.isEmpty() && packIsTrusted) {
        version.startInstallTask2(modpackVo.mcVer, modpackVo.modloader, modpackVo.name)
    } else {
        Task2.Leaf("本地安装整合包") { ctx ->
            ctx.emit(Task2Progress("正在刷新节点...", 0f))
            NodeRefreshCoordinator.refreshCurrent()
                .onSuccess { ctx.emit(Task2Progress("节点已刷新: ${it.nodeName}", 1f)) }
                .onFailure { ctx.emit(Task2Progress("节点刷新失败，继续使用当前节点", 1f)) }

            ModpackService.installBuiltClientZipTask2(
                mcVersion = modpackVo.mcVer,
                modLoader = modpackVo.modloader,
                modpackId = version.modpackId,
                verName = version.name,
                mods = trustedMods,
                clientPackFile = packFile,
                modpackName = modpackVo.name,
            ).runInline(ctx)

            if (untrustedMods.isNotEmpty()) {
                val modsDir = ModpackService.getVersionDir(version.modpackId, version.name)
                    .resolve("mods")
                    .also { it.mkdirs() }
                untrustedMods.forEachIndexed { index, entry ->
                    val target = importedEntryTarget(modsDir.toPath(), entry.relativePath)
                    target.parent?.let(Files::createDirectories)
                    Files.copy(
                        entry.source.toPath(),
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    ctx.emit(
                        Task2Progress(
                            "安装未校验MOD ${index + 1}/${untrustedMods.size}",
                            (index + 1).toFloat() / untrustedMods.size,
                        )
                    )
                }
            }
        }
    }

    return Task2.Leaf("导入 ${packArchiveName}") { ctx ->
        try {
            normalInstallTask.runInline(ctx)
        } finally {
            runCatching { transactionDir.deleteRecursivelyNoSymlink() }
        }
    }
}

suspend fun importLegacyRdiModpackTask2(
    file: File,
    onProgress: (String) -> Unit
): Task2 = withContext(Dispatchers.IO) {
    val transactionDir = Files.createTempDirectory(
        ClientDirs.packProcDir.toPath(),
        "rdimodpack-import-",
    ).toFile()
    try {
        val packStageDir = transactionDir.resolve("pack").also { it.mkdirs() }
        val modsStageDir = transactionDir.resolve("mods").also { it.mkdirs() }
        var packArchiveName = ""
        var packFile: File? = null
        val importedMods = mutableListOf<ImportedModEntry>()
        val total = (listArchiveEntries(file).count { !it.isDirectory && it.path.startsWith("mods/") } + 1)
            .coerceAtLeast(1)
        var processed = 0
        forEachArchiveEntry(file) { entry ->
            if (entry.isDirectory) return@forEachArchiveEntry
            val bytes = entry.bytes ?: return@forEachArchiveEntry
            val normalizedPath = entry.path.replace('\\', '/').trimStart('/')
            when {
                !normalizedPath.startsWith("mods/") && normalizedPath.endsWith(".tar.zst", ignoreCase = true) -> {
                    check(packFile == null) { "整合包内包含多个客户端整合包" }
                    packArchiveName = File(normalizedPath).name
                    check(packArchiveName.isNotBlank()) { "客户端整合包文件名为空" }
                    packFile = writeImportedEntry(packStageDir.toPath(), packArchiveName, bytes)
                    processed += 1
                    onProgress("导入整合包 ${processed}/${total}")
                }

                normalizedPath.startsWith("mods/") -> {
                    val relativePath = normalizedPath.substringAfter("mods/").trim()
                    if (relativePath.isBlank()) return@forEachArchiveEntry
                    val source = writeImportedEntry(modsStageDir.toPath(), relativePath, bytes)
                    importedMods += ImportedModEntry(relativePath, source)
                    processed += 1
                    onProgress("导入MOD ${processed}/${total}")
                }
            }
        }
        val stagedPack = packFile ?: throw IllegalStateException("整合包内未找到modpack.tar.zst")
        val (modpackId, verName) = parseImportedModpackArchiveName(packArchiveName)
        val modpackVo = server.makeRequest<Modpack.BriefVo>("modpack/${modpackId}/brief").data
            ?: throw IllegalStateException("未找到整合包信息")
        val version = server.makeRequest<Modpack.Version>("modpack/${modpackId}/version/${verName}").data
            ?: throw IllegalStateException("未找到整合包版本信息")
        val clientPackSha1 = server.makeRequest<String>(
            "modpack/${modpackId}/version/${verName}/client/hash"
        ).data
        val trustedPackDigest = trustedImportedSha1(clientPackSha1)
        trustedPackDigest?.let { requireImportedDigest(stagedPack, it, packArchiveName) }

        val installableMods = version.mods.filter {
            it.side != Mod.Side.SERVER && it.side != Mod.Side.UNKNOWN
        }
        val matchedMods = importedMods.mapNotNull { entry ->
            installableMods.firstOrNull { entry.matches(it) }?.let { it to entry }
        }
        val trustedImportedMods = matchedMods.mapNotNull { (mod, entry) ->
            trustedImportedDigest(mod)?.let { digest ->
                requireImportedDigest(entry.source, digest, entry.relativePath)
                ImportedTrustedMod(mod, entry, digest)
            }
        }
        val untrustedImportedMods = matchedMods
            .filter { (mod, _) -> trustedImportedDigest(mod) == null }
            .map { (_, entry) -> entry }
        val missingUntrustedMods = installableMods
            .filter { trustedImportedDigest(it) == null }
            .filterNot { mod -> importedMods.any { it.matches(mod) } }
        check(missingUntrustedMods.isEmpty()) {
            "导入整合包缺少未校验MOD：${missingUntrustedMods.take(3).joinToString { it.fileName }}"
        }

        val materializedPack = if (trustedPackDigest == null) {
            stagedPack
        } else {
            ClientContentStore.shared.materialize(
                requests = listOf(
                    importedContentRequest(
                        id = "rdimodpack-pack:$packArchiveName",
                        entry = ImportedModEntry(packArchiveName, stagedPack),
                        digest = trustedPackDigest,
                    )
                ),
                targetRoot = transactionDir.resolve("pack-content").toPath(),
                onProgress = { progress -> onProgress(progress.message) },
            ).getOrThrow().single()
                .toFile()
        }
        val materializedTrustedMods = if (trustedImportedMods.isNotEmpty()) {
            val materializedPaths = ClientContentStore.shared.materialize(
                requests = trustedImportedMods.map { imported ->
                    importedContentRequest(
                        id = "rdimodpack-mod:${imported.mod.platform}:${imported.mod.projectId}:${imported.mod.fileId}",
                        entry = imported.entry,
                        digest = imported.digest,
                    )
                },
                targetRoot = transactionDir.resolve("trusted-mods").toPath(),
                onProgress = { progress -> onProgress(progress.message) },
            ).getOrThrow()
            trustedImportedMods.zip(materializedPaths).map { (imported, path) ->
                imported.copy(entry = imported.entry.copy(source = path.toFile()))
            }
        } else {
            emptyList()
        }

        val trustedMods = installableMods.filter { trustedImportedDigest(it) != null }
        buildImportedInstallTask(
            transactionDir = transactionDir,
            packArchiveName = packArchiveName,
            packFile = materializedPack,
            packIsTrusted = trustedPackDigest != null,
            modpackVo = modpackVo,
            version = version,
            trustedMods = trustedMods,
            untrustedMods = untrustedImportedMods,
        )
    } catch (cause: Throwable) {
        runCatching { transactionDir.deleteRecursivelyNoSymlink() }
        throw cause
    }
}

data class PreparedRdiModpackImport(val task: Task2, val dedupeKey: String?)

/** Unified Legacy picker. Null means the user cancelled the picker. */
suspend fun prepareRdiModpackImportTask2(
    onProgress: (String) -> Unit,
): PreparedRdiModpackImport? = withContext(Dispatchers.IO) {
    val file = selectRdiModpackFile() ?: return@withContext null
    if (file.name.endsWith(".rdipack2", ignoreCase = true)) {
        val prepared = prepareRdiPack2ImportTask(file)
        return@withContext PreparedRdiModpackImport(prepared.task, prepared.dedupeKey)
    }
    PreparedRdiModpackImport(importLegacyRdiModpackTask2(file, onProgress), null)
}

suspend fun importRdiModpackTask2(onProgress: (String) -> Unit): Task2? =
    prepareRdiModpackImportTask2(onProgress)?.task

/** Format-specific prepared-task seam for callers that already selected a file. */
fun importRdiModpack2(file: File): PreparedRdiPack2Task = prepareRdiPack2ImportTask(file)

suspend fun exportLogsPack(packdir: ModpackLocalDir): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val sourceDirs = listOf("logs", "crash-reports")
            .map { packdir.dir.resolve(it) }
            .filter { it.exists() && it.isDirectory }
        if (sourceDirs.isEmpty()) {
            throw IllegalStateException("没有可导出的日志目录")
        }

        val safeName = packdir.name.ifBlank { "modpack" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val defaultName = "${safeName}_${packdir.verName}_logs.zip"
        val outputFile = pickAwtSaveFile(
            title = "选择日志保存位置",
            defaultFileName = defaultName,
            defaultDirectory = File(System.getProperty("user.home")),
            requiredExtension = "zip",
            filenameFilter = { _, name -> name.endsWith(".zip", ignoreCase = true) }
        ) ?: return@runCatching

        ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
            sourceDirs.forEach { dir ->
                val baseName = dir.name
                dir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val relative = file.relativeTo(dir).invariantSeparatorsPath
                        val entryName = "$baseName/$relative"
                        zipOut.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
            }
        }
    }
}
