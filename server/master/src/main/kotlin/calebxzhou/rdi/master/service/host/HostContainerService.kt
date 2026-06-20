package calebxzhou.rdi.master.service.host

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.GTO_GUARD_AGENT_FILE_NAME
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.isGtoModpackName
import calebxzhou.rdi.common.model.sameMod
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.master.WORLD_CACHE_DIR
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
    internal fun Host.containerEnv(
        mcv: McVersion,
        loaderVersion: ModLoader.Version,
        modpack: Modpack,
        lwjgl3ifyRuntime: Lwjgl3ifyServerSupport.PreparedRuntime?,
        worldId: ObjectId?
    ): MutableList<String> {
        val serverArgs = when (mcv) {
            //McVersion.V182,
            McVersion.V192,
            McVersion.V201,
            McVersion.V211 -> listOf(loaderVersion.serverArgsPath(true))

            //McVersion.V165 -> McVersion.V165.plusJvmArgs + listOf("-jar", loaderVersion.serverJarName)
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
        val totalArg = mutableListOf("").apply {
            if(modpack.mcVer == McVersion.V211
                || modpack.mcVer == McVersion.V201 || modpack.mcVer == McVersion.V071
            ){
                this.add("-Drdi.onlySaveFirmSections=true")
            }
            if(modpack.mcVer == McVersion.V071 || modpack.mcVer == McVersion.V122){
                this.add("-Dfml.queryResult=confirm")
            }
            if (this@containerEnv.isPublic) {
                this.add("-Drdi.firmSectionTotalMax=65536")
                this.add("-Drdi.firmSectionPersonMax=256")
            }
            if (this@containerEnv._id == ObjectId("69da4ec7015319d405bbb3be")) {
                this.add("-Xmx12G")
            } else {
                this.add("-Xmx8G")
            }
            if (modpack.name.isGtoModpackName()) {
                this.add("-javaagent:/opt/server/$GTO_GUARD_AGENT_FILE_NAME")
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
        DockerService.deleteContainer(_id.str)
        ensureWorkdirQuota()
        cleanupDisabledModFilesBeforeContainerCreate(version)

        val sharedLibsDir = modpack.libsDir.canonicalFile.also { it.mkdirs() }
        val loaderVer = modpack.mcVer.loaderVersions[modpack.modloader] ?: throw RequestError("找不到对应版本的运行库")
        val lwjgl3ifyRuntime = if (Lwjgl3ifyServerSupport.shouldEnable(modpack)) {
            Lwjgl3ifyServerSupport.prepare(modpack)
        } else {
            null
        }
        val rdiCore = "rdi-5-mc-server-${modpack.mcVer.mcVer}-${modpack.modloader}.jar"
        val sharedRdiCore = sharedLibsDir.resolve("mods").resolve(rdiCore)
        val rdiCoreSource = sharedRdiCore.takeIf { it.exists() }
            ?: lwjgl3ifyRuntime?.modsDir?.resolve(rdiCore)?.takeIf { it.exists() }
            ?: if (lwjgl3ifyRuntime != null) {
                throw RequestError("GTNH服务端运行库缺少RDI核心Mod: ${lwjgl3ifyRuntime.modsDir.resolve(rdiCore).absolutePath}")
            } else {
                sharedRdiCore
            }
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
            lwjgl3ifyRuntime?.let { runtime ->
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(runtime.launcherJar.absolutePath)
                    .withTarget("/opt/server/${runtime.launcherJar.name}")
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(runtime.java9ArgsFile.absolutePath)
                    .withTarget("/opt/server/${runtime.java9ArgsFile.name}")
            }

            if (worldId != null) {
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
        val memory = if(isPublic) 12*1024*1024*1024L else 8*1024*1024*1024L
        val memorySwap = if(isPublic) 30*1024*1024*1024L else 16*1024*1024*1024L

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
        val dir = WORLD_CACHE_DIR.resolve(worldId.str).canonicalFile
        if (!dir.exists() && !dir.mkdirs()) {
            throw RequestError("世界缓存目录创建失败: ${dir.absolutePath}")
        }
        if (!dir.isDirectory) {
            throw RequestError("世界缓存路径不是目录: ${dir.absolutePath}")
        }
        return dir
    }

    internal fun Host.requireGtoGuardAgent(modpack: Modpack) {
        if (!modpack.name.isGtoModpackName()) return
        val agentFile = dir.resolve(GTO_GUARD_AGENT_FILE_NAME)
        if (!agentFile.isFile) {
            throw RequestError("缺少GTO服务端启动保护文件: ${agentFile.absolutePath}")
        }
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
