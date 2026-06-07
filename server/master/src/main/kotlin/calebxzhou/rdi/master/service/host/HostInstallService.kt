package calebxzhou.rdi.master.service.host

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.jarResource
import calebxzhou.mykotutils.std.readAllString
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.model.Host.Companion.getDifficultyText
import calebxzhou.rdi.common.model.Host.Companion.getGameModeText
import calebxzhou.rdi.common.util.ioScope
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.common.util.validateName
import calebxzhou.rdi.master.HOSTS_DIR
import calebxzhou.rdi.master.service.*
import calebxzhou.rdi.master.service.ModpackService.getVersion
import calebxzhou.rdi.master.service.ModpackService.installToHost
import calebxzhou.rdi.master.service.WorldService.createWorld
import calebxzhou.rdi.master.service.WorldService.updateWorldSize
import calebxzhou.rdi.master.service.host.HostContainerService.makeContainer
import calebxzhou.rdi.master.service.host.HostRuntimeService.listenCrashOnStart
import calebxzhou.rdi.model.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.bson.types.ObjectId
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.time.Duration.Companion.seconds

object HostInstallService {
    private val lgr by Loggers

    val ModLoader.Version.legacyForgeUniversalJarName: String
        get() {
            val artifactVersion = dirName
                .removePrefix("${McVersion.V071.mcVer}-Forge")
                .takeIf { it != dirName && it.isNotBlank() }
                ?.let { "${McVersion.V071.mcVer}-$it" }
                ?: id
            return "forge-$artifactVersion-universal.jar"
        }

    fun Host.overlayRootDir(): File = HOSTS_DIR.resolve(_id.str)

    fun Host.writeServerProperties() {
        dir.resolve("allowed_symlinks.txt").writeText("[regex].*")
        dir.resolve("eula.txt").writeText("eula=true")
        syncAllOpMarkers()
        "server.properties".run {
            var txt = this.jarResource(this).readAllString()
                .replace("#{port}", port.toString())
                .replace("#{difficulty}", getDifficultyText(difficulty))

                .replace("#{gamemode}", getGameModeText(gameMode))
            //水星迫降
            if (this@writeServerProperties.modpackId == ObjectId("6a211c654467027dd3ed3f1d")) {
                txt = txt.replace(
                    "#{gen-settings}",
                    "3;minecraft\\:bedrock,11*minecraft\\:stained_hardened_clay\\:7,4*minecraft\\:stained_hardened_clay\\:12,4*minecraft\\:stained_hardened_clay\\:14,4*minecraft\\:stained_hardened_clay\\:1,4*minecraft\\:gravel,9*minecraft\\:sand,58*minecraft\\:water;0;"
                ).replace("#{level-type}", "flat")
            } else {
                txt = txt.replace("#{gen-settings}", "{}").replace("#{level-type}", levelType)
            }
            dir.resolve(this).writeText(txt)
        }
        val defaultPropsFile = dir.resolve("default-server.properties")
        val serverPropsFile = dir.resolve("server.properties")
        if (defaultPropsFile.exists() && serverPropsFile.exists()) {
            val serverProps = Properties().apply {
                serverPropsFile.inputStream().use { load(it) }
            }
            val defaultProps = Properties().apply {
                defaultPropsFile.inputStream().use { load(it) }
            }
            defaultProps.forEach { key, value ->
                if (key.toString() != "server-port") {
                    lgr.info { "apply prop $key = $value" }
                    serverProps.setProperty(key.toString(), value.toString())
                }
            }
            serverPropsFile.outputStream().use { serverProps.store(it, null) }
        }
    }

    fun Host.deleteTransientStartupDirs() {
        listOf("tacz_backup", "dynamic-data-pack-cache").forEach { dirName ->
            val targetDir = dir.resolve(dirName)
            if (!targetDir.exists()) return@forEach
            runCatching { targetDir.deleteRecursivelyNoSymlink() }
                .onFailure { err -> throw RequestError("删除${dirName}失败: ${err.message}") }
        }
    }

    fun Host.syncAllOpMarkers() {
        val allOpMarker = dir.resolve("R_ALL_OP")
        val opsFile = dir.resolve("ops.json")
        if (allowCheats) {
            if (!allOpMarker.exists()) {
                allOpMarker.writeText("")
            }
            return
        }
        runCatching { Files.deleteIfExists(allOpMarker.toPath()) }
            .onFailure { err -> lgr.warn { "Host ${_id} 删除R_ALL_OP失败: ${err.message}" } }
        runCatching { Files.deleteIfExists(opsFile.toPath()) }
            .onFailure { err -> lgr.warn { "Host ${_id} 删除ops.json失败: ${err.message}" } }
    }

    fun Host.ensureWorkdirQuota() {
        if (!dir.exists()) return
        val totalSize = dir.walkTopDown()
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            .sumOf { it.length() }
        if (totalSize > HostService.HOST_WORKDIR_LIMIT_BYTES) {
            throw RequestError("房间目录超过 3GB (${totalSize.humanFileSize}MB)，请删除不必要文件后再启动")
        }
    }

    suspend fun allocateRoomPort(): Int {
        val used = HostService.dbcl.find().map { it.port }.toList().toSet()
        val candidates = (HostService.PORT_START until HostService.PORT_END_EXCLUSIVE).asSequence()
            .filter { it !in used }
            .toList()
        if (candidates.isEmpty()) {
            throw RequestError("没有可用端口，请联系管理员")
        }
        return candidates.random()
    }

    suspend fun RAccount.resolveWorld(
        saveWorld: Boolean,
        worldId: ObjectId?,
        modpackId: ObjectId,
        currentHostId: ObjectId? = null
    ): World? {
        if (!saveWorld) return null
        if (worldId == null) {
            return createWorld(_id, null, modpackId)
        }
        val occupyHost = HostQueryService.findByWorld(worldId)
        if (occupyHost != null && occupyHost._id != currentHostId) {
            throw RequestError("此存档数据已被房间“${occupyHost.name}”占用")
        }
        val world = WorldService.getById(worldId) ?: throw RequestError("无此存档")
        if (world.ownerId != _id) {
            throw RequestError("不是你的存档")
        }
        return world
    }

    suspend fun RAccount.createHost(host: Host.CreateDto) {
        host.name.validateName()
        val playerId = _id
        if (HostQueryService.getByOwner(playerId).size > 3 && !isDav) {
            throw RequestError("最多只可创建3张房间")
        }
        if (host.name.contains("公共") && !isDav) {
            throw RequestError("无权创建公共房间")
        }
        if (HostQueryService.findByOwnerAndModpack(playerId, host.modpackId) != null) {
            throw RequestError("同一个整合包只能创建一张房间")
        }
        val world = resolveWorld(host.saveWorld, host.worldId, host.modpackId)
        val modpack = ModpackService.getById(host.modpackId) ?: throw RequestError("无此包")
        val version = modpack.getVersion(host.packVer) ?: throw RequestError("无此版本")
        if (version.status != Modpack.Status.OK) {
            throw RequestError("此整合包版本未准备好，请等待构建完成后再创建房间")
        }
        val port = allocateRoomPort()
        val createdHost = Host(
            name = host.name,
            ownerId = playerId,
            modpackId = host.modpackId,
            packVer = host.packVer,
            worldId = world?._id,
            port = port,
            difficulty = host.difficulty,
            allowCheats = host.allowCheats,
            whitelist = host.whitelist,
            gameMode = host.gameMode,
            levelType = host.levelType,
            members = listOf(Host.Member(id = playerId, role = Role.OWNER)),
            gameRules = host.gameRules
        )
        val mailId =
            MailService.sendSystemMail(playerId, "房间创建中", "${createdHost.name}正在创建中，请稍等几分钟...")._id
        HostService.dbcl.insertOne(createdHost)
        startCreateHost(createdHost, modpack, version, mailId)
    }

    fun startCreateHost(
        host: Host,
        modpack: Modpack,
        version: Modpack.Version,
        mailId: ObjectId,
        runningTitle: String = "房间创建中",
        successTitle: String = "房间创建成功",
        successContent: String = "可以玩了",
        failureTitle: String = "房间创建失败"
    ) {
        ServerTaskManager.submit(
            task = Task2.Leaf("创建房间 ${host.name}") { ctx ->
                runCatching {
                    ctx.emit(LoadProgress.Phase("准备房间目录"))
                    MailService.changeMail(mailId, runningTitle, newContent = "准备房间目录")
                    if (host.dir.exists()) {
                        host.dir.deleteRecursivelyNoSymlink()
                    }
                    host.dir.mkdir()

                    ctx.emit(LoadProgress.Phase("准备运行库"))
                    MailService.changeMail(mailId, runningTitle, newContent = "准备运行库")
                    host.makeContainer(host.worldId, modpack, version)

                    modpack.installToHost(host.packVer, host) {
                        MailService.changeMail(mailId, runningTitle, newContent = it)
                        ctx.emit(LoadProgress.Phase(it))
                    }

                    ctx.emit(LoadProgress.Phase("写入房间配置"))
                    MailService.changeMail(mailId, runningTitle, newContent = "写入房间配置")
                    host.writeServerProperties()

                    ctx.emit(LoadProgress.Phase("清理启动前缓存"))
                    MailService.changeMail(mailId, runningTitle, newContent = "清理启动前缓存")
                    host.deleteTransientStartupDirs()

                    lgr.info { "installToHost returned. Proceeding to start Docker container for host ${host._id} (Logic Error Tracing)." }
                    ctx.emit(LoadProgress.Phase("启动房间"))
                    MailService.changeMail(mailId, runningTitle, newContent = "启动房间")
                    DockerService.start(host._id.str)
                    host.listenCrashOnStart()

                    HostControlService.clearShutFlag(host._id)
                }.onFailure {
                    lgr.error { it }
                    it.printStackTrace()
                    MailService.changeMail(mailId, failureTitle, newContent = "无法创建房间，错误：${it}")
                    throw it
                }.onSuccess {
                    MailService.changeMail(mailId, successTitle, newContent = successContent)
                }
            },
            dedupeKey = HostService.createHostTaskKey(host._id)
        )
    }

    fun Host.refreshWorldSizeAfterStop(waitForStop: Boolean) {
        val worldId = worldId ?: return
        ioScope.launch {
            if (HostService.skipWorldSizeUpdateOnce(_id)) {
                lgr.info { "Host ${_id} 跳过崩溃后的存档大小刷新" }
                return@launch
            }
            if (waitForStop) {
                repeat(60) {
                    if (!DockerService.isStarted(_id.str)) return@repeat
                    delay(2.seconds)
                }
            }
            if (!DockerService.isStarted(_id.str)) {
                runCatching { updateWorldSize(worldId) }
                    .onSuccess { size ->
                        lgr.info { "Host ${_id} world size updated: ${size} bytes" }
                    }
                    .onFailure { err ->
                        lgr.warn { "Host ${name} 更新存档大小失败: ${err.message}" }
                    }
            }
        }
    }
}
