package calebxzhou.rdi.common.util

import calebxzhou.rdi.common.exception.ModpackError
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

fun hardLinkFile(source: File, target: File): Result<Unit> = runCatching {
    check(source.isFile) { "源文件不存在: ${source.absolutePath}" }
    val sourcePath = source.toPath()
    val targetPath = target.toPath()
    if (!Files.isSymbolicLink(targetPath) && Files.exists(targetPath) && Files.isSameFile(sourcePath, targetPath)) {
        return@runCatching
    }
    Files.deleteIfExists(targetPath)
    try {
        Files.createLink(targetPath, sourcePath)
    } catch (exception: Exception) {
        throw ModpackError("无法创建文件硬链接: ${exception.message ?: target.absolutePath}")
    }
}

fun hardLinkDirectory(source: File, target: File): Result<Unit> = runCatching {
    check(source.isDirectory) { "源目录不存在: ${source.absolutePath}" }
    Files.walk(source.toPath()).use { paths ->
        paths.forEach { sourcePath ->
            val targetPath = target.toPath().resolve(source.toPath().relativize(sourcePath))
            when {
                Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS) -> Files.createDirectories(targetPath)
                Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS) ->
                    hardLinkFile(sourcePath.toFile(), targetPath.toFile()).getOrThrow()
                else -> error("不支持链接此文件: $sourcePath")
            }
        }
    }
}
