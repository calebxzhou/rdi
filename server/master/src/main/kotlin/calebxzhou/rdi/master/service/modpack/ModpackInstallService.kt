package calebxzhou.rdi.master.service.modpack

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.master.service.*
import calebxzhou.rdi.master.service.host.dir

/** Installs a built Legacy modpack version into a host. */
object ModpackInstallService {
    suspend fun Modpack.installToHost(verName: String, host: Host, onProgress: (String) -> Unit) {
        val version = versions.find { it.name == verName }
            ?: throw RequestError("整合包版本不存在: $verName")
        if (version.status != Modpack.Status.OK) {
            throw RequestError("此版本未准备好或构建失败")
        }
        val hostDir = host.dir.canonicalFile.apply { mkdirs() }
        if (!version.fullPackFile.exists()) {
            throw RequestError("版本压缩文件不存在: ${version.fullPackFile}")
        }
        onProgress("开始安装整合包..")
        ModpackArchiveService.unzipOverrides(
            version.fullPackFile,
            hostDir,
            includeClientOnlyMarkedMods = false,
            skipHostAssetFiles = true,
            skipRootWorld = host.realVersion == 2
        )
        hostDir.resolve("mods").listFiles()
            ?.filter {
                it.isFile &&
                    it.name.startsWith(CLIENT_ONLY_MARK_PREFIX) &&
                    it.extension.equals("jar", true)
            }
            ?.forEach { runCatching { it.delete() } }
        hostDir.resolve("mods").resolve("I18nUpdateMod.jar")
            .takeIf { it.exists() }
            ?.delete()
        ModpackArchiveService.cleanupDisabledInstalledMods(host, version, hostDir.resolve("mods"))
        (ModpackServiceKernel.testGameLibsDirOverride ?: libsDir).canonicalFile.also {
            if (!it.exists() || !it.isDirectory) {
                throw RequestError("整合包依赖目录缺失: $it")
            }
        }
        onProgress("安装成功")
    }
}
