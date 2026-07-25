package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.exception.ModpackError
import java.io.File
import java.nio.file.Files

fun linkOrCopyMod(source: File, target: File) {
    val dst = target.toPath()
    Files.deleteIfExists(dst)
    runCatching {
        Files.createSymbolicLink(dst, source.toPath())
    }.onFailure {
        throw ModpackError("无法创建mod软链接 请进入系统设置打开开发人员模式")
    }
}
