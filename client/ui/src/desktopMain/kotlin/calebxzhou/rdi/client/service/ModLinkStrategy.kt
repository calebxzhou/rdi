package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.exception.ModpackError
import java.io.File
import java.nio.file.Files

actual fun linkOrCopyMod(source: File, target: File) {
    val dst = target.toPath()
    Files.deleteIfExists(dst)
    runCatching {
        Files.createSymbolicLink(dst, source.toPath())
    }.onFailure {
        throw ModpackError("无管理员权限 无法创建Mod链接")
    }
}
