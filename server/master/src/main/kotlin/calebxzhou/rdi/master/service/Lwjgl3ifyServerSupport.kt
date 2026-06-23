package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.master.service.host.HostInstallService.legacyForgeUniversalJarName
import calebxzhou.rdi.master.service.libsDir
import java.io.File

internal object Lwjgl3ifyServerSupport {
    private const val LAUNCHER_JAR_NAME = "lwjgl3ify-forgePatches.jar"
    private const val JAVA9_ARGS_NAME = "java9args.txt"
    private const val MINECRAFT_SERVER_JAR_NAME = "minecraft_server.1.7.10.jar"

    data class PreparedRuntime(
        val librariesDir: File,
        val launcherJar: File,
        val java9ArgsFile: File,
        val forgeUniversalJar: File,
        val minecraftServerJar: File,
    ) {
        val launchArgs: List<String>
            get() = listOf(
                "-Dfml.readTimeout=180",
                "@${java9ArgsFile.name}",
                "-jar",
                launcherJar.name
            )
    }

    fun shouldEnable(modpack: Modpack): Boolean =
        modpack.mcVer == McVersion.V071 && modpack.modloader == ModLoader.forge

    fun prepare(modpack: Modpack, hostDir: File): PreparedRuntime {
        require(shouldEnable(modpack)) { "仅1.7.10 Forge需要LWJGL3ify服务端运行时" }
        val rootDir = hostDir.canonicalFile
        val sharedLibsDir = modpack.libsDir.canonicalFile
        val loaderVersion = modpack.mcVer.loaderVersions[modpack.modloader]
            ?: throw RequestError("找不到对应版本的运行库")
        return PreparedRuntime(
            librariesDir = sharedLibsDir.requireDir("libraries"),
            launcherJar = rootDir.requireFile(LAUNCHER_JAR_NAME),
            java9ArgsFile = rootDir.requireFile(JAVA9_ARGS_NAME),
            forgeUniversalJar = sharedLibsDir.requireFile(loaderVersion.legacyForgeUniversalJarName),
            minecraftServerJar = sharedLibsDir.requireFile(MINECRAFT_SERVER_JAR_NAME),
        )
    }

    private fun File.requireFile(name: String): File =
        resolve(name).takeIf { it.isFile }
            ?: throw RequestError("GTNH服务端运行库缺少文件: ${resolve(name).absolutePath}")

    private fun File.requireDir(name: String): File =
        resolve(name).takeIf { it.isDirectory }
            ?: throw RequestError("GTNH服务端运行库缺少目录: ${resolve(name).absolutePath}")

}
