package calebxzhou.rdi.master.service

import java.io.File
import java.nio.file.Files

internal class ModpackUploadTempStorage(private val dir: File) {
    fun createTempFile(): Result<File> = runCatching {
        prepareDirectory()
        Files.createTempFile(dir.toPath(), FILE_PREFIX, FILE_SUFFIX).toFile()
    }

    fun deleteTempFile(file: File): Result<Unit> = runCatching {
        if (file.exists()) {
            check(file.delete()) { "无法删除上传暂存文件: ${file.absolutePath}" }
        }
    }

    fun cleanupStaleFiles(): Result<Int> = runCatching {
        prepareDirectory()
        val staleFiles = requireNotNull(dir.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        }) { "无法读取上传暂存目录: ${dir.absolutePath}" }
        staleFiles.forEach { deleteTempFile(it).getOrThrow() }
        staleFiles.size
    }

    private fun prepareDirectory() {
        if (!dir.exists()) {
            check(dir.mkdirs()) { "无法创建上传暂存目录: ${dir.absolutePath}" }
        }
        require(dir.isDirectory) { "上传暂存路径不是目录: ${dir.absolutePath}" }
        require(dir.canWrite()) { "上传暂存目录不可写: ${dir.absolutePath}" }
    }

    private companion object {
        const val FILE_PREFIX = "modpack-upload-"
        const val FILE_SUFFIX = ".tmp"
    }
}
