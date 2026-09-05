package calebxzhou.rdi.master.service

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.master.DL_MODS_CLIENT_DIR
import calebxzhou.rdi.master.service.host.HostService
import calebxzhou.rdi.master.service.modpack.ModpackServiceKernel
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
        val usedFileNames = collectUsedModFileNames()
        purgeDirectory(
            modDir = DL_MOD_DIR,
            trashDirName = "dl-mods-trash",
            usedFileNames = usedFileNames.all,
            protectedFilePrefixes = setOf("rdi-5-mc-client-")
        )
        purgeDirectory(
            modDir = DL_MODS_CLIENT_DIR,
            trashDirName = "dl-mods-client-trash",
            usedFileNames = usedFileNames.client
        )
    }

    private fun purgeDirectory(
        modDir: File,
        trashDirName: String,
        usedFileNames: Set<String>,
        protectedFilePrefixes: Set<String> = emptySet()
    ) {
        if (!modDir.exists()) {
            lgr.info { "Mod目录不存在，跳过unused mod清理: ${modDir.absolutePath}" }
            return
        }

        val trashDir = modDir.parentFile?.resolve(trashDirName) ?: File(trashDirName)
        trashDir.mkdirs()

        val purgeCandidates = findPurgeCandidates(modDir, usedFileNames, protectedFilePrefixes)

        if (purgeCandidates.isEmpty()) {
            lgr.info {
                "Mod缓存清理：${modDir.absolutePath}引用${usedFileNames.size}个文件名，没有unused mod"
            }
            return
        }

        lgr.info {
            "Mod缓存清理：${modDir.absolutePath}引用${usedFileNames.size}个文件名，准备移动${purgeCandidates.size}个unused mod到${trashDir.absolutePath}"
        }

        var movedCount = 0
        var failedCount = 0
        purgeCandidates.forEach { source ->
            runCatching {
                Files.move(source.toPath(), nextTrashFile(trashDir, source.name).toPath())
                movedCount++
            }.onFailure { error ->
                failedCount++
                lgr.warn(error) { "移动unused mod失败: ${source.absolutePath}" }
            }
        }

        lgr.info {
            "Mod缓存清理完成：移动${movedCount}个，失败${failedCount}个，trash=${trashDir.absolutePath}"
        }
    }

    internal fun findPurgeCandidates(
        modDir: File,
        usedFileNames: Set<String>,
        protectedFilePrefixes: Set<String> = emptySet()
    ): List<File> = modDir.listFiles()
        ?.filter { it.isFile }
        ?.filter {
            it.extension.equals("jar", ignoreCase = true) ||
                it.extension.equals("downloading", ignoreCase = true)
        }
        ?.filterNot { file -> protectedFilePrefixes.any { prefix -> file.name.startsWith(prefix) } }
        ?.filter {
            it.extension.equals("downloading", ignoreCase = true) || it.name !in usedFileNames
        }
        .orEmpty()

    private suspend fun collectUsedModFileNames(): UsedModFileNames {
        val versions = ModpackServiceKernel.dbcl.find().toList()
            .flatMap { it.versions }
        val all = buildSet {
            versions.flatMap { it.mods }.forEach { addAll(it.fileNames) }

            HostService.dbcl.find().toList()
                .flatMap { it.extraMods + it.disabledMods }
                .forEach { addAll(it.fileNames) }
        }
        val client = buildSet {
            versions
                .flatMap { it.mods }
                .filter { it.side == Mod.Side.CLIENT }
                .forEach { addAll(it.fileNames) }
        }
        return UsedModFileNames(all, client)
    }

    private data class UsedModFileNames(
        val all: Set<String>,
        val client: Set<String>
    )

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
