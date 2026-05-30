package calebxzhou.rdi.master.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.master.service.host.HostService
import kotlinx.coroutines.flow.toList
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object UnusedModPurgeService {
    private val lgr by Loggers
    private val trashTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    suspend fun purgeOnStartup() {
        runCatching {
            purgeUnusedMods()
        }.onFailure {
            lgr.warn(it) { "dl-mods unused mod清理失败" }
        }
    }

    private suspend fun purgeUnusedMods() {
        val modDir = DL_MOD_DIR
        if (!modDir.exists()) {
            lgr.info { "dl-mods目录不存在，跳过unused mod清理: ${modDir.absolutePath}" }
            return
        }

        val trashDir = modDir.parentFile?.resolve("dl-mods-trash") ?: File("dl-mods-trash")
        trashDir.mkdirs()

        val usedFileNames = collectUsedModFileNames()
        val purgeCandidates = modDir.listFiles()
            ?.filter { it.isFile }
            ?.filter { it.extension.equals("jar", ignoreCase = true) || it.extension.equals("downloading", ignoreCase = true) }
            ?.filterNot { it.name.startsWith("rdi-5-mc-client-") }
            .orEmpty()
        val unusedFiles = purgeCandidates.filter { it.extension.equals("downloading", ignoreCase = true) || it.name !in usedFileNames }

        if (unusedFiles.isEmpty()) {
            lgr.info {
                "dl-mods清理：扫描${purgeCandidates.size}个候选文件，引用${usedFileNames.size}个文件名，没有unused mod"
            }
            return
        }

        lgr.info {
            "dl-mods清理：扫描${purgeCandidates.size}个候选文件，引用${usedFileNames.size}个文件名，准备移动${unusedFiles.size}个unused mod到${trashDir.absolutePath}"
        }

        var movedCount = 0
        var failedCount = 0
        unusedFiles.forEach { source ->
            runCatching {
                Files.move(source.toPath(), nextTrashFile(trashDir, source.name).toPath())
                movedCount++
            }.onFailure { error ->
                failedCount++
                lgr.warn(error) { "移动unused mod失败: ${source.absolutePath}" }
            }
        }

        lgr.info {
            "dl-mods清理完成：移动${movedCount}个，失败${failedCount}个，trash=${trashDir.absolutePath}"
        }
    }

    private suspend fun collectUsedModFileNames(): Set<String> = buildSet {
        ModpackService.dbcl.find().toList()
            .flatMap { it.versions }
            .flatMap { it.mods }
            .forEach { addAll(it.fileNames) }

        HostService.dbcl.find().toList()
            .flatMap { it.extraMods + it.disabledMods }
            .forEach { addAll(it.fileNames) }
    }

    private fun nextTrashFile(trashDir: File, fileName: String): File {
        val directTarget = trashDir.resolve(fileName)
        if (!directTarget.exists()) return directTarget

        val timestamp = LocalDateTime.now().format(trashTimestampFormatter)
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            .orEmpty()
        var index = 1
        while (true) {
            val candidate = trashDir.resolve("$baseName.$timestamp.$index$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }
}
