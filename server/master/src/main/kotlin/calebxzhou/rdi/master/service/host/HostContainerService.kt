package calebxzhou.rdi.master.service.host

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.FORGEGUARD_AGENT_FILE_NAME
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.sameMod
import calebxzhou.rdi.common.model.supportsForgeguard
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.master.WORLD_CACHE_DIR
import calebxzhou.rdi.master.GAME_LIBS_DIR
import calebxzhou.rdi.master.service.CLIENT_ONLY_MARK_PREFIX
import calebxzhou.rdi.master.service.DockerService
import calebxzhou.rdi.master.service.Lwjgl3ifyServerSupport
import calebxzhou.rdi.master.service.WorldService
import calebxzhou.rdi.master.service.host.HostInstallService.ensureWorkdirQuota
import calebxzhou.rdi.master.service.host.HostInstallService.legacyForgeUniversalJarName
import calebxzhou.rdi.master.service.libsDir
import com.github.dockerjava.api.model.Mount
import com.github.dockerjava.api.model.MountType
import com.github.dockerjava.api.model.TmpfsOptions
import org.bson.types.ObjectId
import java.io.File
import java.nio.file.Files

object HostContainerService {
    internal const val FORGEGUARD_CONTAINER_PATH = "/opt/forgeguard.jar"

    internal fun forgeguardMount(): Mount {
        val agentFile = GAME_LIBS_DIR.resolve(FORGEGUARD_AGENT_FILE_NAME)
        if (!agentFile.isFile) {
            throw RequestError("缺少Forgeguard服务端启动保护文件: ${agentFile.absolutePath}")
        }
        return Mount()
            .withType(MountType.BIND)
            .withSource(agentFile.absolutePath)
            .withTarget(FORGEGUARD_CONTAINER_PATH)
            .withReadOnly(true)
    }

    internal fun Host.containerEnv(
        mcv: McVersion,
        loaderVersion: ModLoader.Version,
        modpack: Modpack,
        lwjgl3ifyRuntime: Lwjgl3ifyServerSupport.PreparedRuntime?,
        worldId: ObjectId?
    ): MutableList<String> {
        val serverArgs = when (mcv) {
            McVersion.V201,
            McVersion.V211 -> listOf(loaderVersion.serverArgsPath(true))
            McVersion.V122 -> McVersion.V122.plusJvmArgs + listOf("-jar", loaderVersion.serverJarName)
            McVersion.V071 -> buildList {
                if (lwjgl3ifyRuntime != null) {
                    addAll(lwjgl3ifyRuntime.launchArgs)
                } else {
                    addAll(McVersion.V071.plusJvmArgs)
                    add("-jar")
                    add(loaderVersion.legacyForgeUniversalJarName)
                }
            }
        }
        val noguiArg = if (mcv == McVersion.V071) "nogui" else "--nogui"
        val totalArg = mutableListOf(

            "-Drdi.onlySaveFirmSections=true",
        ).apply {

            this.add("-XX:+UseCompactObjectHeaders")

            if(modpack.mcVer == McVersion.V071 || modpack.mcVer == McVersion.V122){
                this.add("-Dfml.queryResult=confirm")
            }
            this.add("-Drdi.firmSectionTotalMax=512")
            this.add("-Drdi.firmSectionPersonMax=512")
            this.add("-Xmx8G")
            if (modpack.mcVer.supportsForgeguard(modpack.modloader)) {
                this.add("-javaagent:$FORGEGUARD_CONTAINER_PATH")
            }
            if (worldId != null) {
                this.add("-Drdi.terrain.cache.path=/data/world/cache")
            }

        } + serverArgs + noguiArg
        return mutableListOf(
            "HOST_ID=${_id.str}",
            "GAME_PORT=${port}",
            "ALL_OP=${if (allowCheats) "true" else "false"}",
            "START_PARAMS=${totalArg.joinToString(" ")}"
        ).apply {
            gameRules.forEach { id, value ->
                this += "GAME_RULE_${id}=${value}"
            }
        }
    }

    internal fun Host.makeContainer(
        worldId: ObjectId?,
        modpack: Modpack,
        version: Modpack.Version
    ) {
        if (realVersion == 2 && worldId != null) {
            throw RequestError("v2房间不能使用外置存档")
        }
        if (realVersion != 1 && realVersion != 2) {
            throw RequestError("无效房间版本")
        }
        DockerService.deleteContainer(_id.str)
        ensureWorkdirQuota()
        cleanupDisabledModFilesBeforeContainerCreate(version)

        val sharedLibsDir = modpack.libsDir.canonicalFile.also { it.mkdirs() }
        val loaderVer = modpack.mcVer.loaderVersions[modpack.modloader] ?: throw RequestError("找不到对应版本的运行库")
        val lwjgl3ifyRuntime = if (Lwjgl3ifyServerSupport.shouldEnable(modpack)) {
            Lwjgl3ifyServerSupport.prepare(modpack, dir)
        } else {
            null
        }
        val rdiCore = "rdi-5-mc-server-${modpack.mcVer.mcVer}-${modpack.modloader}.jar"
        val sharedRdiCore = sharedLibsDir.resolve("mods").resolve(rdiCore)
        val rdiCoreSource = sharedRdiCore.takeIf { it.exists() }
            ?: sharedRdiCore
        val librariesSource = lwjgl3ifyRuntime?.librariesDir ?: sharedLibsDir.resolve("libraries")
        val mounts = mutableListOf(
            Mount()
                .withType(MountType.BIND)
                .withSource(dir.absolutePath)
                .withTarget("/opt/server"),
            Mount()
                .withType(MountType.BIND)
                .withSource(librariesSource.absolutePath)
                .withTarget("/opt/server/libraries"),
            Mount()
                .withType(MountType.BIND)
                .withSource(rdiCoreSource.absolutePath)
                .withTarget("/opt/server/mods/${rdiCore}"),
        ).apply {
            if (modpack.mcVer.supportsForgeguard(modpack.modloader)) {
                this += forgeguardMount()
            }
            version.mods
                .filter(::isServerInstalledMod)
                .filterNot { this@makeContainer.isDisabledMod(it) }
                .forEach { mod ->
                    val source = mod.candidateFiles.firstOrNull(File::exists)
                    if (source != null) {
                        this += Mount()
                            .withType(MountType.BIND)
                            .withSource(source.absolutePath)
                            .withTarget("/opt/server/mods/${mod.fileName}")
                    }
                }
            extraMods
                .filter(::isServerInstalledMod)
                .forEach { mod ->
                    val source = mod.candidateFiles.firstOrNull(File::exists)
                        ?: throw RequestError("房间附加Mod文件缺失:${mod.slug} 请重新上传")
                    this += Mount()
                        .withType(MountType.BIND)
                        .withSource(source.absolutePath)
                        .withTarget("/opt/server/mods/${mod.fileName}")
                }
            if (listOf(/*McVersion.V165, */McVersion.V122, McVersion.V071).any { it == modpack.mcVer }) {
                val loaderJar = lwjgl3ifyRuntime?.forgeUniversalJar
                    ?: sharedLibsDir.resolve(
                        if (modpack.mcVer == McVersion.V071 && modpack.modloader == ModLoader.forge) {
                            loaderVer.legacyForgeUniversalJarName
                        } else {
                            loaderVer.serverJarName
                        }
                    )
                val serverJar = lwjgl3ifyRuntime?.minecraftServerJar
                    ?: sharedLibsDir.resolve(modpack.mcVer.serverJarName)
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(loaderJar.absolutePath)
                    .withTarget("/opt/server/${loaderJar.name}")
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(serverJar.absolutePath)
                    .withTarget("/opt/server/${serverJar.name}")
            }
            if (realVersion == 2) {
                // v2 worlds live below the host root bind at /opt/server/world.
            } else if (worldId != null) {
                val worldCacheDir = prepareWorldCacheDir(worldId)
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(WorldService.getLevelDir(worldId).absolutePath)
                    .withTarget("/opt/server/world")
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(worldCacheDir.absolutePath)
                    .withTarget("/data/world/cache")
            } else {
                this += Mount()
                    .withType(MountType.TMPFS)
                    .withTarget("/opt/server/world")
                    .withTmpfsOptions(TmpfsOptions().withSizeBytes(512 * 1024 * 1024))
            }
        }
        val image = if (lwjgl3ifyRuntime != null) "rdi:j25" else "rdi:j${modpack.mcVer.jreSupport}"
        val cpu = when(modpack.mcVer){
            McVersion.V071, McVersion.V122 -> 2
            else -> 4
        }
        val memory = 8 * 1024 * 1024 * 1024L
        val memorySwap = 16 * 1024 * 1024 * 1024L

        modpack.mcVer.loaderVersions[modpack.modloader]?.let { modLoaderVersion ->
            DockerService.createContainer(
                port,
                this._id.str,
                cpu,
                memory,memorySwap,
                mounts,
                image,
                containerEnv(modpack.mcVer, modLoaderVersion, modpack, lwjgl3ifyRuntime, worldId)
            )
        } ?: throw RequestError("不支持的mod加载器")
    }

    private fun prepareWorldCacheDir(worldId: ObjectId): File {
        val dir = WorldService.getCacheDir(worldId).canonicalFile
        if (!dir.exists() && !dir.mkdirs()) {
            throw RequestError("世界缓存目录创建失败: ${dir.absolutePath}")
        }
        if (!dir.isDirectory) {
            throw RequestError("世界缓存路径不是目录: ${dir.absolutePath}")
        }
        return dir
    }

    internal fun isServerInstalledMod(mod: Mod): Boolean =
        mod.side != Mod.Side.CLIENT &&
                mod.side != Mod.Side.UNKNOWN &&
                !mod.fileName.startsWith(CLIENT_ONLY_MARK_PREFIX)

    internal fun Host.isDisabledMod(mod: Mod): Boolean =
        disabledMods.any { sameMod(it, mod) }

    private fun Host.cleanupDisabledModFilesBeforeContainerCreate(version: Modpack.Version) {
        val modsDir = dir.resolve("mods")
        if (!modsDir.exists() || !modsDir.isDirectory || disabledMods.isEmpty()) return
        val disabledServerMods = version.mods
            .filter(::isServerInstalledMod)
            .filter { isDisabledMod(it) }
        if (disabledServerMods.isEmpty()) return

        disabledServerMods
            .flatMap { it.fileNames }
            .distinct()
            .forEach { fileName ->
                deleteDisabledHostModFile(modsDir.resolve(fileName))
            }

        val disabledSlugs = disabledServerMods.map { it.slug.trim().lowercase() }
            .filter { it.isNotBlank() }
        val disabledHashes = disabledServerMods.map { it.hash.trim().lowercase() }
            .filter { it.isNotBlank() }
        modsDir.listFiles()
            ?.filter { it.isFile && it.length() == 0L && it.extension.equals("jar", ignoreCase = true) }
            ?.filter { file ->
                val lowerName = file.name.lowercase()
                disabledSlugs.any(lowerName::contains) || disabledHashes.any(lowerName::contains)
            }
            ?.forEach(::deleteDisabledHostModFile)
    }

    private fun deleteDisabledHostModFile(file: File) {
        runCatching { Files.deleteIfExists(file.toPath()) }
            .getOrElse { err ->
                throw RequestError("已禁用Mod文件清理失败: ${file.name} ${err.message ?: ""}".trim())
            }
    }
}
