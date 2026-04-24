package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.master.GAME_LIBS_DIR
import java.io.File
import java.util.Locale

internal object Lwjgl3ifyServerSupport {
    private const val GTNH_GAME_LIB_DIR = "1.7.10-forge"
    private const val LAUNCHER_JAR_NAME = "lwjgl3ify-forgePatches.jar"
    private const val JAVA9_ARGS_NAME = "java9args.txt"
    private const val MINECRAFT_SERVER_JAR_NAME = "minecraft_server.1.7.10.jar"

    private val supportMods = listOf("lwjgl3ify", "unimixins")

    data class PreparedRuntime(
        val rootDir: File,
        val librariesDir: File,
        val modsDir: File,
        val launcherJar: File,
        val java9ArgsFile: File,
        val forgeUniversalJar: File,
        val minecraftServerJar: File,
        val supportMods: List<File>,
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

    fun prepare(modpack: Modpack): PreparedRuntime {
        require(shouldEnable(modpack)) { "仅1.7.10 Forge需要LWJGL3ify服务端运行时" }
        val rootDir = GAME_LIBS_DIR.resolve(GTNH_GAME_LIB_DIR)
        val librariesDir = rootDir.requireDir("libraries")
        val modsDir = rootDir.requireDir("mods")
        val resolvedMods = supportMods.map { expectedSlug ->
            modsDir.listFiles()
                ?.firstOrNull { file -> file.isFile && supportSlug(file) == expectedSlug }
                ?: throw RequestError("GTNH服务端运行库缺少${expectedSlug}: ${modsDir.absolutePath}")
        }
        return PreparedRuntime(
            rootDir = rootDir,
            librariesDir = librariesDir,
            modsDir = modsDir,
            launcherJar = rootDir.requireFile(LAUNCHER_JAR_NAME),
            java9ArgsFile = rootDir.requireFile(JAVA9_ARGS_NAME),
            forgeUniversalJar = rootDir.findForgeUniversalJar(),
            minecraftServerJar = rootDir.requireFile(MINECRAFT_SERVER_JAR_NAME),
            supportMods = resolvedMods
        )
    }

    fun supportSlug(mod: Mod): String? =
        supportSlug(mod.slug, mod.fileName)

    fun supportSlug(file: File): String? =
        supportSlug(file.nameWithoutExtension, file.name)

    private fun supportSlug(slug: String, fileName: String): String? {
        val slugLower = slug.trim().lowercase(Locale.ROOT)
        val fileLower = fileName.lowercase(Locale.ROOT)
        return when {
            "lwjgl3ify" in slugLower || "lwjgl3ify" in fileLower -> "lwjgl3ify"
            "unimixins" in slugLower || "unimixin" in slugLower ||
                "unimixins" in fileLower || "unimixin" in fileLower -> "unimixins"
            else -> null
        }
    }

    private fun File.requireFile(name: String): File =
        resolve(name).takeIf { it.isFile }
            ?: throw RequestError("GTNH服务端运行库缺少文件: ${resolve(name).absolutePath}")

    private fun File.requireDir(name: String): File =
        resolve(name).takeIf { it.isDirectory }
            ?: throw RequestError("GTNH服务端运行库缺少目录: ${resolve(name).absolutePath}")

    private fun File.findForgeUniversalJar(): File =
        listFiles()
            ?.firstOrNull { file ->
                file.isFile &&
                    file.name.startsWith("forge-1.7.10-", ignoreCase = true) &&
                    file.name.endsWith("-universal.jar", ignoreCase = true)
            }
            ?: throw RequestError("GTNH服务端运行库缺少Forge universal jar: ${absolutePath}")

}
