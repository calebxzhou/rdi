package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.archive.PackArchiveFormat
import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.archive.detectArchiveFormat
import calebxzhou.rdi.common.archive.forEachArchiveEntry
import calebxzhou.rdi.common.archive.listArchiveEntries
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.client.service.ModpackService.startInstallTask2
import calebxzhou.rdi.client.ui.pickAwtSaveFile
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Progress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual fun selectRdiPackFiles(): List<File>? {
    val owner = Frame()
    try {
        val dialog = FileDialog(owner, "选择MC版本包", FileDialog.LOAD).apply {
            isMultipleMode = true
            directory = File(System.getProperty("user.home"), "Downloads").absolutePath
            file = "*.rdimcpack"
            filenameFilter = java.io.FilenameFilter { dir, name ->
                val target = File(dir, name)
                target.isDirectory || name.endsWith(".rdimcpack", ignoreCase = true)
            }
        }
        dialog.isVisible = true
        val files = dialog.files
            ?.toList()
            ?.filter { it.exists() && it.isFile && it.name.endsWith(".rdimcpack", ignoreCase = true) }
            ?: emptyList()
        return files.takeIf { it.isNotEmpty() }
    } finally {
        owner.dispose()
    }
}

private fun selectRdiModpackFile(): File? {
    val owner = Frame()
    try {
        val dialog = FileDialog(owner, "选择RDI整合包", FileDialog.LOAD).apply {
            directory = File(System.getProperty("user.home"), "Downloads").absolutePath
            file = "*.rdimodpack"
            filenameFilter = java.io.FilenameFilter { dir, name ->
                val target = File(dir, name)
                target.isDirectory || name.endsWith(".rdimodpack", ignoreCase = true)
            }
        }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(dir, name)
            .takeIf { it.exists() && it.isFile && it.name.endsWith(".rdimodpack", ignoreCase = true) }
    } finally {
        owner.dispose()
    }
}

actual fun buildImportPackTask2(packFile: File): Task2 {
    return Task2.Leaf("导入 ${packFile.name}") { ctx ->
        if (!packFile.name.endsWith(".rdimcpack", ignoreCase = true)) {
            throw IllegalStateException("仅支持.rdimcpack文件")
        }
        if (packFile.detectArchiveFormat() != PackArchiveFormat.TAR_ZST) {
            throw IllegalStateException(".rdimcpack必须是tar.zst格式")
        }
        val targetRoot = ClientDirs.mcDir.canonicalFile
        val totalFiles = listArchiveEntries(packFile).count { !it.isDirectory }.coerceAtLeast(1)
        var processed = 0
        forEachArchiveEntry(packFile) { entry ->
            val name = entry.path.replace('\\', '/').trimStart('/')
            if (name.isEmpty()) return@forEachArchiveEntry
            val outFile = targetRoot.resolve(name)
            val normalized = outFile.canonicalFile
            if (!normalized.path.startsWith(targetRoot.path)) return@forEachArchiveEntry
            if (entry.isDirectory) {
                normalized.mkdirs()
                return@forEachArchiveEntry
            }
            normalized.parentFile?.mkdirs()
            Files.newOutputStream(
                normalized.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { output ->
                output.write(entry.bytes ?: byteArrayOf())
            }
            processed += 1
            ctx.emit(
                Task2Progress("导入 ${entry.path}", processed.toFloat() / totalFiles)
            )
        }
        ctx.emit(Task2Progress("完成", 1f))
    }
}

private fun findExportableModpackArchive(packdir: ModpackLocalDir): File? {
    val baseName = "${packdir.vo.id}_${packdir.verName}"
    return ClientDirs.dlPacksDir.resolve("$baseName.tar.zst").takeIf(File::exists)
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

actual suspend fun exportRdiModpack(
    packdir: ModpackLocalDir,
    onProgress: (String) -> Unit
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val packArchive = findExportableModpackArchive(packdir)
            ?: throw IllegalStateException("整合包文件不存在，请先下载")
        val version = server.makeRequest<Modpack.Version>(
            "modpack/${packdir.vo.id}/version/${packdir.verName}"
        ).data ?: throw IllegalStateException("无法获取整合包版本信息")

        val safeName = packdir.vo.name.ifBlank { "modpack" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val outputFile = pickRdiModpackSaveFile("${safeName}_${packdir.verName}.rdimodpack")
            ?: return@runCatching

        val missingMods = mutableListOf<String>()
        val total = version.mods.size + 1
        var processed = 0
        TarZstArchiveWriter(outputFile).use { archive ->
            archive.addFile(packArchive.name, packArchive.readBytes(), packArchive.lastModified())
            processed += 1
            onProgress("导出整合包 ${processed}/${total}")

            version.mods.filterNot { it.side== Mod.Side.SERVER }.forEach { mod ->
                val modFile = mod.candidateFiles.firstOrNull { it.exists() }
                if (modFile == null) {
                    missingMods += mod.fileName
                    return@forEach
                }
                archive.addFile("mods/${mod.fileName}", modFile.readBytes(), modFile.lastModified())
                processed += 1
                onProgress("导出MOD ${processed}/${total}")
            }
        }

        if (missingMods.isNotEmpty()) {
            runCatching { outputFile.delete() }
            throw IllegalStateException("缺少 ${missingMods.size} 个MOD文件：${missingMods.take(3).joinToString()}${if (missingMods.size > 3) "..." else ""}")
        }
    }
}

actual suspend fun importRdiModpackTask2(
    onProgress: (String) -> Unit
): Task2 = withContext(Dispatchers.IO) {
    val file = selectRdiModpackFile() ?: throw IllegalStateException("未选择整合包文件")
    var packArchiveName = ""
    val total = (listArchiveEntries(file).count { !it.isDirectory && it.path.startsWith("mods/") } + 1).coerceAtLeast(1)
    var processed = 0
    var foundPackArchive = false
    forEachArchiveEntry(file) { entry ->
        if (entry.isDirectory || entry.bytes == null) return@forEachArchiveEntry
        val normalizedPath = entry.path.replace('\\', '/').trimStart('/')
        when {
            !normalizedPath.startsWith("mods/") && normalizedPath.endsWith(".tar.zst", ignoreCase = true) -> {
                packArchiveName = File(normalizedPath).name
                val packTarget = ClientDirs.dlPacksDir.resolve(packArchiveName)
                packTarget.parentFile?.mkdirs()
                Files.newOutputStream(
                    packTarget.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                ).use { output -> output.write(entry.bytes) }
                processed += 1
                foundPackArchive = true
                onProgress("导入整合包 ${processed}/${total}")
            }

            normalizedPath.startsWith("mods/") -> {
                val filename = normalizedPath.substringAfter("mods/").trim()
                if (filename.isBlank()) return@forEachArchiveEntry
                val target = DL_MOD_DIR.resolve(filename)
                target.parentFile?.mkdirs()
                Files.newOutputStream(
                    target.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                ).use { output -> output.write(entry.bytes) }
                processed += 1
                onProgress("导入MOD ${processed}/${total}")
            }
        }
    }
    if (!foundPackArchive || packArchiveName.isBlank()) {
        throw IllegalStateException("整合包内未找到modpack.tar.zst")
    }

    val (modpackId, verName) = parseImportedModpackArchiveName(packArchiveName)
    val modpackVo = server.makeRequest<Modpack.BriefVo>("modpack/${modpackId}/brief").data
        ?: throw IllegalStateException("未找到整合包信息")
    val version = server.makeRequest<Modpack.Version>("modpack/${modpackId}/version/${verName}").data
        ?: throw IllegalStateException("未找到整合包版本信息")
    version.startInstallTask2(modpackVo.mcVer, modpackVo.modloader, modpackVo.name)
}

actual suspend fun exportLogsPack(packdir: ModpackLocalDir): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val sourceDirs = listOf("logs", "crash-reports")
            .map { packdir.dir.resolve(it) }
            .filter { it.exists() && it.isDirectory }
        if (sourceDirs.isEmpty()) {
            throw IllegalStateException("没有可导出的日志目录")
        }

        val safeName = packdir.vo.name.ifBlank { "modpack" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
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
