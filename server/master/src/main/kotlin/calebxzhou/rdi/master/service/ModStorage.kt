package calebxzhou.rdi.master.service

import java.io.File

internal object ModStorage {
    fun prepareDirectories(modDir: File, clientModDir: File): Result<Unit> = runCatching {
        val normalizedModDir = prepareDirectory(modDir)
        val normalizedClientModDir = prepareDirectory(clientModDir)
        require(normalizedModDir != normalizedClientModDir) {
            "dlModsDir与dlModsClientDir不能是同一目录: $normalizedModDir"
        }
    }

    fun findDownloadFile(filename: String, modDir: File, clientModDir: File): Result<File?> = runCatching {
        listOf(modDir, clientModDir)
            .asSequence()
            .map { it.resolve(filename) }
            .firstOrNull { it.isFile }
    }

    private fun prepareDirectory(dir: File): File {
        if (!dir.exists()) {
            check(dir.mkdirs()) { "无法创建Mod存储目录: ${dir.absolutePath}" }
        }
        require(dir.isDirectory) { "Mod存储路径不是目录: ${dir.absolutePath}" }
        require(dir.canWrite()) { "Mod存储目录不可写: ${dir.absolutePath}" }
        return dir.canonicalFile
    }
}
