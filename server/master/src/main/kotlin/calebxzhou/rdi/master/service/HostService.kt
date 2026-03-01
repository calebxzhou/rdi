package calebxzhou.rdi.master.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.jarResource
import calebxzhou.mykotutils.std.readAllString
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.service.McServerPinger
import calebxzhou.rdi.common.util.ioScope
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.common.util.validateName
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.master.HOSTS_DIR
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.model.WsMessage
import calebxzhou.rdi.master.net.*
import calebxzhou.rdi.master.service.HostService.addMember
import calebxzhou.rdi.master.service.HostService.changeOptions
import calebxzhou.rdi.master.service.HostService.changeVersion
import calebxzhou.rdi.master.service.HostService.createHost
import calebxzhou.rdi.master.service.HostService.delMember
import calebxzhou.rdi.master.service.HostService.delete
import calebxzhou.rdi.master.service.HostService.forceStop
import calebxzhou.rdi.master.service.HostService.graceStop
import calebxzhou.rdi.master.service.HostService.hostContext
import calebxzhou.rdi.master.service.HostService.listAllHosts
import calebxzhou.rdi.master.service.HostService.listHostLobbyLegacy
import calebxzhou.rdi.master.service.HostService.listenLogs
import calebxzhou.rdi.master.service.HostService.needAdmin
import calebxzhou.rdi.master.service.HostService.needOwner
import calebxzhou.rdi.master.service.HostService.quit
import calebxzhou.rdi.master.service.HostService.restart
import calebxzhou.rdi.master.service.HostService.sendCommand
import calebxzhou.rdi.master.service.HostService.setRole
import calebxzhou.rdi.master.service.HostService.start
import calebxzhou.rdi.master.service.HostService.status
import calebxzhou.rdi.master.service.HostService.toDetailVo
import calebxzhou.rdi.master.service.HostService.transferOwnership
import calebxzhou.rdi.master.service.ModpackService.getVersion
import calebxzhou.rdi.master.service.ModpackService.installToHost
import calebxzhou.rdi.master.service.ModpackService.toBriefVo
import calebxzhou.rdi.master.service.WorldService.createWorld
import calebxzhou.rdi.master.service.WorldService.updateWorldSize
import calebxzhou.rdi.model.Role
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Mount
import com.github.dockerjava.api.model.MountType
import com.github.dockerjava.api.model.TmpfsOptions
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.sse.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val Host.dir get() = HOSTS_DIR.resolve(_id.str)

// ---------- Routing DSL (mirrors teamRoutes style) ----------
fun Route.hostRoutes() = route("/host") {
    get("/online-player-ids"){
        response(data = HostService.getAllHostsOnlinePlayerIds())
    }
    route("") {
        post("/v2") {
            call.player().createHost(call.receive())
            ok()
        }
        //前版本兼容
        get("/lobby/{page?}") {
            val hosts = call.player().listHostLobbyLegacy(paramNull("page")?.toInt() ?: 0)
            response(data = hosts)
        }
        get("/my/{page?}") {
            response(data = call.player().listAllHosts(paramNull("page")?.toInt() ?: 0, myOnly = true))
        }
        //新
        get("/list/{page?}") {
            val hosts = call.player().listAllHosts(paramNull("page")?.toInt() ?: 0, myOnly = false)
            response(data = hosts)
        }
        get("/search/modpack/{modpackId}/{verName}") {
            val hosts = HostService.findByModpackVersion(idParam("modpackId"), param("verName"))
            response(data = hosts)
        }

    }
    route("/{hostId}") {
        get("/status") {
            call.hostContext().host.status.let { response(data = it) }
        }
        post("/start") {
            call.hostContext().start()
            ok()
        }
        post("/stop") {
            call.hostContext().needAdmin.graceStop()
            ok()
        }
        post("/force-stop") {
            call.hostContext().needAdmin.forceStop()
            ok()
        }
        post("/command") {
            val ctx = call.hostContext().needAdmin
            ctx.sendCommand(param("command"))
            ok()
        }
        post("/restart") {
            call.hostContext().needAdmin.restart()
            ok()
        }
        put("/options") {
            val ctx = call.hostContext().needAdmin
            ctx.changeOptions(call.receive<Host.OptionsDto>())
            ok()
        }
        /*put("/gamerules") {
            call.hostContext().needAdmin.changeGameRules(call.paramT("data"))
            ok()
        }*/
        post("/update") {
            call.hostContext().needAdmin.changeVersion(paramNull("verName"))
            ok()
        }
        post("/transfer/{uid2}") {
            call.hostContext().needOwner.transferOwnership()
            ok()
        }
        delete {
            call.hostContext().needOwner.delete()
            ok()
        }
        get {
            HostService.getById(idParam("hostId"))?.let {
                response(data = it)
            } ?: err("无此地图")
        }
        get("detail") {
            HostService.getById(idParam("hostId"))?.let {
                response(data = it.toDetailVo())
            } ?: err("无此地图")
        }
        /*post("/modpack/{modpackId}/{verName}") {
            call.hostContext().needAdmin.changeModpack(idParam("modpackId"), param("verName"))
            ok()
        }*/

        route("/log") {
            sse("/stream") {
                val ctx = try {
                    call.hostContext()
                } catch (err: NotFoundException) {
                    send(ServerSentEvent(event = "error", data = "此地图已被删除"))
                    return@sse
                } catch (err: RequestError) {
                    send(ServerSentEvent(event = "error", data = err.message ?: "unknown"))
                    return@sse
                }
                ctx.listenLogs(this)

            }
        }
        put("/quit"){
            call.hostContext().quit()
            ok()
        }
        route("/member/{uid2}") {
            put("/role/{role}") {
                call.hostContext().needOwner.setRole(Role.valueOf(param("role")))
                ok()
            }
            delete {
                call.hostContext().needOwner.delMember()
                ok()
            }
        }
        post("/member/{qq}") {
            call.hostContext().needAdmin.addMember(param("qq"))
            ok()

        }
    }


}

//单独拿出来是为了不走authentication
fun Route.hostPlayRoutes() = route("/host") {
    get("/status") {
        val port = param("port").toInt()
        val host = HostService.getByPort(port) ?: throw RequestError("无此地图")
        response(data = host.status)
    }
    webSocket("/play/{hostId}") {
        val rawHostId = call.param("hostId")

        val hostId = runCatching { ObjectId(rawHostId) }.getOrElse {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "host无效"))
            return@webSocket
        }

        val host = HostService.getById(hostId)
        if (host == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "未知地图"))
            return@webSocket
        }

        if (!HostService.registerPlayableSession(hostId, this)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "重复连接"))
            return@webSocket
        }

        try {
            for (frame in incoming) {
                // 服务器端暂不处理来自 mod 的消息
            }
        } finally {
            HostService.unregisterPlayableSession(hostId, this)
        }
    }
}

data class HostContext(
    val host: Host,
    val player: RAccount,
    val member: Host.Member,
    val targetMemberNull: Host.Member?
) {
    var reqId = 0
    val targetMember get() = targetMemberNull ?: throw ParamError("玩家${player.name}不是此地图的受邀成员")
    suspend fun getTargetPlayer() = PlayerService.getById(targetMember.id) ?: throw ParamError("玩家${player.name}不存在")
}

object HostService {
    private val lgr by Loggers
    private val isWindowsHost: Boolean = System.getProperty("os.name").contains("windows", ignoreCase = true)
    private val dockerDesktopPrefix: String =
        System.getenv("DOCKER_DESKTOP_PATH_PREFIX")?.trimEnd('/') ?: "/run/desktop/mnt/host"

    val dbcl = DB.getCollection<Host>("host")

    private val hostStates = ConcurrentHashMap<ObjectId, HostState>()
    private val skipWorldSizeUpdate = ConcurrentHashMap.newKeySet<ObjectId>()
    private val staleCleanupJob: Job

    private const val PORT_START = 50000
    private const val PORT_END_EXCLUSIVE = 60000
    private const val SHUTDOWN_THRESHOLD = 20
    private const val HOSTS_PER_PAGE = 48
    private const val HOST_WORKDIR_LIMIT_BYTES: Long = 1L * 1024 * 1024 * 1024

    private val idleMonitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleMonitorJob: Job? = null

    private data class HostState(
        var shutFlag: Int = 0,
        var session: DefaultWebSocketServerSession? = null,
        var shutdownJob: Job? = null
    )


    init {
        runBlocking {
            dbcl.createIndex(Indexes.ascending("port"))
        }
        // Start periodic cleanup of stale entries
        staleCleanupJob = idleMonitorScope.launch {
            while (isActive) {
                delay(5.minutes)
                cleanupStaleEntries()
            }
        }
    }

    fun shutdown() {
        lgr.info { "Shutting down HostService..." }
        stopIdleMonitor()
        staleCleanupJob.cancel()
        idleMonitorScope.cancel()
        // Force close all sessions
        hostStates.values.forEach { state ->
            state.session?.let { session ->
                session.launch {
                    runCatching { session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server shutdown")) }
                }
            }
            state.shutdownJob?.cancel()
        }
        hostStates.clear()
        skipWorldSizeUpdate.clear()
        lgr.info { "HostService shutdown complete" }
    }

    private fun cleanupStaleEntries() {
        val staleHosts = mutableSetOf<ObjectId>()
        hostStates.forEach { (hostId, state) ->
            if (state.session == null && state.shutFlag <= 0 && state.shutdownJob?.isActive != true) {
                staleHosts.add(hostId)
            }
        }
        staleHosts.forEach { hostId ->
            hostStates.remove(hostId)
            lgr.debug { "Cleaned up stale HostState for $hostId" }
        }

        // Also cleanup skipWorldSizeUpdate for deleted hosts
        val allHostIds = runBlocking {
            dbcl.find().map { it._id }.toList().toSet()
        }
        val staleWorldSkips = skipWorldSizeUpdate.filter { it !in allHostIds }
        staleWorldSkips.forEach {
            skipWorldSizeUpdate.remove(it)
            lgr.debug { "Cleaned up stale skipWorldSizeUpdate for $it" }
        }
    }


    private val Host.containerEnv
        get() = {mcv: McVersion, loaderVersion: ModLoader.Version ->
            val serverArg = when(mcv){
                McVersion.V182,
                McVersion.V192,
                McVersion.V201,
                McVersion.V211 -> {
                    loaderVersion.serverArgsPath(true)
                }
                //1.16-
                else
                     //V122 V071
                         -> {
                    "-jar ${loaderVersion.serverJarName}"
                }
            }

            mutableListOf(
                "HOST_ID=${_id.str}",
                "GAME_PORT=${port}",
                "ALL_OP=${if (allowCheats) "true" else "false"}",
                "START_PARAMS=-Xmx8G $serverArg --universe /data --nogui"
            ).apply {
                gameRules.forEach { id, value ->
                    this += "GAME_RULE_${id}=${value}"
                }
            }
        }


    private data class OverlaySources(
        val libsDir: File,
        val versionDir: File
    )

    private fun Host.prepareOverlaySources(modpack: Modpack, version: Modpack.Version): OverlaySources {
        val libsDir = modpack.libsDir.canonicalFile.also {
            if (!it.exists()) throw RequestError("整合包依赖缺失: ${it}")
        }
        val versionDir = version.dir.canonicalFile.also {
            if (!it.exists()) throw RequestError("整合包版本目录缺失: ${it}")
        }
        val hostRoot = overlayRootDir().apply { mkdirs() }


        return OverlaySources(libsDir, versionDir)
    }

    private fun Host.writeServerProperties() {
        dir.resolve("allowed_symlinks.txt").writeText("[regex].*")
        dir.resolve("eula.txt").writeText("eula=true")
        "server.properties".run {
            this.jarResource(this).readAllString()
                .replace("#{port}", port.toString())
                .replace(
                    "#{difficulty}", when (difficulty) {
                        0 -> "peaceful"
                        1 -> "easy"
                        2 -> "normal"
                        3 -> "hard"
                        else -> "normal"
                    }
                )
                .replace("#{level-type}", levelType)
                .replace(
                    "#{gamemode}", when (gameMode) {
                        0 -> "survival"
                        1 -> "creative"
                        2 -> "adventure"
                        else -> "survival"
                    }
                ).let {
                    dir.resolve(this).writeText(it)
                }
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

    private fun Host.ensureWorkdirQuota() {
        if (!dir.exists()) return
        val totalSize = dir.walkTopDown()
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            .sumOf { it.length() }
        if (totalSize > HOST_WORKDIR_LIMIT_BYTES) {
            val sizeMb = totalSize / (1024 * 1024)
            throw RequestError("地图目录超过 1GB (${sizeMb}MB)，请删除不必要文件后再启动")
        }
    }

    val HostContext.needMember get() = requireRole(Role.MEMBER)
    val HostContext.needAdmin get() = requireRole(Role.ADMIN)
    val HostContext.needOwner get() = requireRole(Role.OWNER)
    fun HostContext.requireRole(level: Role): HostContext {
        member.let {
            val allowed = when (level) {
                Role.MEMBER -> true
                Role.ADMIN -> member.role.level <= Role.ADMIN.level
                Role.OWNER -> member.role == Role.OWNER
                else -> false
            }
            if (!allowed) throw RequestError("无权限")
        }
        return this
    }


    suspend fun ApplicationCall.hostContext(): HostContext {
        val player = player()
        val requesterId = player._id
        val host = HostService.getById(idPathParam("hostId")) ?: throw RequestError("无此地图")
        val reqMem = host.members.firstOrNull { it.id == requesterId } ?: run {
            if (player.isDav) {
                Host.Member(id = requesterId, role = Role.ADMIN)
            } else if (!host.whitelist) {
                Host.Member(id = requesterId, role = Role.GUEST)
            } else throw RequestError("不是地图受邀成员")
        }
        val tarMem = pathParamNull("uid2")?.let { rawId ->
            runCatching { ObjectId(rawId) }.getOrNull()
        }?.let { uid2 ->
            host.members.find { it.id == uid2 }
        }
        return HostContext(host, player(), reqMem, tarMem)
    }

    fun registerPlayableSession(hostId: ObjectId, session: DefaultWebSocketServerSession): Boolean {
        val state = hostStates.computeIfAbsent(hostId) { HostState() }
        val previous = state.session
        if (previous !== null && previous !== session) {
            previous.launch {
                runCatching { previous.close(CloseReason(CloseReason.Codes.NORMAL, "新的连接建立")) }
            }
        }
        state.session = session
        //DockerService.limitCpuCores(hostId.str,4.0)
        lgr.info { "host $hostId gameplay 通道已连接" }
        return true
    }

    fun unregisterPlayableSession(hostId: ObjectId, session: DefaultWebSocketServerSession) {
        hostStates[hostId]?.let { state ->
            if (state.session === session) {
                state.session = null
                lgr.info { "Host $hostId gameplay 通道已断开" }

                // Cancel any existing shutdown job to prevent accumulation
                state.shutdownJob?.cancel()

                // Launch new shutdown job and track it
                state.shutdownJob = idleMonitorScope.launch {
                    delay(3.seconds)
                    val currentSession = hostStates[hostId]?.session
                    if (currentSession != null) {
                        lgr.info { "Host $hostId 已在断开后重新连接，跳过自动停止" }
                        return@launch
                    }

                    runCatching { DockerService.stop(hostId.str) }
                        .onSuccess {
                            lgr.info { "Host $hostId 容器因通道断开已停止" }
                            getById(hostId)?.refreshWorldSizeAfterStop(waitForStop = false)
                        }
                        .onFailure { error ->
                            if (error is RequestError && error.message == "早就停了") {
                                lgr.info { "Host $hostId 容器已处于停止状态" }
                            } else {
                                lgr.warn(error) { "Host $hostId 通道断开后停止容器失败: ${error.message}" }
                            }
                        }
                        .also {
                            // Clean up after shutdown completes
                            if (state.shutFlag <= 0 && state.session == null) {
                                hostStates.remove(hostId, state)
                            }
                        }
                }
            }
        }
    }

    suspend fun Host.analyzeCrashReport(): Boolean {
        val crashDir = dir.resolve("crash-reports")
        val crashFile = crashDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash-") && it.extension.equals("txt", true) }
            ?.maxByOrNull { it.lastModified() }
            ?: run {
                lgr.info { "Host ${_id} 没有 crash 报告可分析" }
                return false
            }

        val content = runCatching { crashFile.readText() }.getOrElse {
            lgr.warn(it) { "Host ${_id} 读取 crash 报告失败: ${crashFile.absolutePath}" }
            return false
        }

        val clientClassRegex =
            Regex("""(NoClassDefFoundError|ClassNotFoundException).*net[./]minecraft[./]client""")
        val invalidDistRegex =
            Regex("""invalid dist\s+DEDICATED_SERVER""", RegexOption.IGNORE_CASE)
        val distCleanerRegex =
            Regex("""RuntimeDistCleaner""", RegexOption.IGNORE_CASE)
        val modLoadingFailedRegex =
            Regex("""Mod Loading has failed|Mod loading error has occurred""", RegexOption.IGNORE_CASE)
        if (
            !clientClassRegex.containsMatchIn(content) &&
            !invalidDistRegex.containsMatchIn(content) &&
            !distCleanerRegex.containsMatchIn(content) &&
            !modLoadingFailedRegex.containsMatchIn(content)
        ) {
            lgr.info { "Host ${_id} crash 报告未发现客户端侧加载错误, 跳过处理" }
            return false
        }

        val modIds = linkedSetOf<String>()
        val modFiles = linkedSetOf<String>()
        val modHashes = linkedSetOf<String>()
        val requiredModSlugs = linkedSetOf<String>()
        val lines = content.lines()
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-- MOD ")) {
                val slug = trimmed.removePrefix("-- MOD ").removeSuffix(" --").trim()
                if (slug.isNotBlank()) modIds += slug.lowercase()
            }
            if (trimmed.startsWith("Failure message:")) {
                Regex("""\(([^)]+)\)""").find(trimmed)?.groupValues?.getOrNull(1)?.let { slug ->
                    if (slug.isNotBlank()) modIds += slug.lowercase()
                }
            }
            if (trimmed.startsWith("Mod File:")) {
                val path = trimmed.removePrefix("Mod File:").trim()
                val name = path.substringAfterLast('/').substringAfterLast('\\').trim()
                if (name.endsWith(".jar", true)) modFiles += name
            }
            if (trimmed.startsWith("-- Mod loading issue for:")) {
                val slug = trimmed.removePrefix("-- Mod loading issue for:").trim()
                if (slug.isNotBlank()) modIds += slug.lowercase()
            }
            if (trimmed.startsWith("Failure message:", ignoreCase = true)) {
                val nextLine = lines.getOrNull(index + 1)?.trim().orEmpty()
                Regex("""Currently,\s*([^\s]+)\s+is\s+not\s+installed""", RegexOption.IGNORE_CASE)
                    .find(nextLine)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { slug -> requiredModSlugs += slug.lowercase() }
            }
        }

        // try extract slug from "slug_platform_hash.jar" pattern
        modFiles.forEach { filename ->
            val match = Regex("""^(.+?)_([a-z]+)_([0-9a-fA-F]+)\.jar$""").find(filename)
            val slug = match?.groupValues?.getOrNull(1)
            if (!slug.isNullOrBlank()) {
                modIds += slug.lowercase()
            }
            val hash = match?.groupValues?.getOrNull(3)
            if (!hash.isNullOrBlank()) {
                modHashes += hash.lowercase()
            }
        }

        // If crash report shows invalid dist, find nearest "Mod file" below and record its hash
        val invalidDistLineRegex =
            Regex("""Attempted to load class .* invalid dist\s+DEDICATED_SERVER""", RegexOption.IGNORE_CASE)
        lines.forEachIndexed { index, line ->
            if (invalidDistLineRegex.containsMatchIn(line)) {
                for (i in index + 1 until minOf(lines.size, index + 40)) {
                    val trimmed = lines[i].trim()
                    if (trimmed.startsWith("Mod file:", ignoreCase = true)) {
                        val path = trimmed.substringAfter(":", "").trim()
                        val name = path.substringAfterLast('/').substringAfterLast('\\').trim()
                        modFiles += name
                        Regex("""^(.+?)_([a-z]+)_([0-9a-fA-F]+)\.jar$""").find(name)
                            ?.groupValues
                            ?.getOrNull(3)
                            ?.let { modHashes += it.lowercase() }
                        break
                    }
                }
            }
        }

        if (modIds.isEmpty() && modFiles.isEmpty() && modHashes.isEmpty() && requiredModSlugs.isEmpty()) {
            lgr.info { "Host ${_id} crash 报告未解析出 mod 信息, 跳过处理" }
            return false
        }

        val modpack = ModpackService.getById(modpackId) ?: run {
            lgr.warn { "Host ${_id} 无法找到整合包 ${modpackId}" }
            return false
        }
        val version = modpack.getVersion(packVer) ?: run {
            lgr.warn { "Host ${_id} 无法找到版本 ${packVer}" }
            return false
        }

        var updated = false
        version.mods.forEach { mod ->
            val slugMatch = modIds.contains(mod.slug.lowercase())
            val fileMatch = modFiles.any {
                it.equals(mod.fileName, true) ||
                        it.contains(mod.slug, true) ||
                        it.contains(mod.hash, true)
            }
            val hashMatch = modHashes.contains(mod.hash.lowercase())
            val requireBoth = requiredModSlugs.contains(mod.slug.lowercase())
            if ((slugMatch || fileMatch || hashMatch) && mod.side != Mod.Side.CLIENT) {
                lgr.info { "Host ${_id} 标记客户端专用 mod: ${mod.slug}" }
                mod.side = Mod.Side.CLIENT
                updated = true
            } else if (requireBoth && mod.side == Mod.Side.CLIENT) {
                lgr.info { "Host ${_id} 修复依赖缺失，将 ${mod.slug} 设为 BOTH" }
                mod.side = Mod.Side.BOTH
                updated = true
            }
        }

        modFiles.forEach { name ->
            val hostModFile = dir.resolve("mods").resolve(name)
            if (hostModFile.exists()) {
                runCatching { hostModFile.delete() }
                    .onSuccess { lgr.info { "Host ${_id} 删除崩溃 mod 文件: $name" } }
                    .onFailure { lgr.warn(it) { "Host ${_id} 删除 mod 文件失败: $name" } }
            }
        }

        if (updated) {
            ModpackService.dbcl.updateOne(
                eq(Modpack::_id.name, modpack._id),
                Updates.set(
                    "${Modpack::versions.name}.$[elem].${Modpack.Version::mods.name}",
                    version.mods
                ),
                UpdateOptions().arrayFilters(listOf(Document("elem.name", version.name)))
            )
        }

        if (updated || modFiles.isNotEmpty()) {
            lgr.info { "Host ${_id} 已处理客户端 mod 崩溃，准备重启" }
            DockerService.deleteContainer(_id.str)
            writeServerProperties()
            makeContainer(worldId, modpack, version)
            DockerService.start(_id.str)
            listenCrashOnStart()
            clearShutFlag(_id)
            return true
        }
        return false
    }

    private fun Host.listenCrashOnStart() {
        val triggered = AtomicBoolean(false)
        val listenerHolder = arrayOfNulls<Closeable>(1)
        val closeListener = { runCatching { listenerHolder[0]?.close() } }

        // Add timeout to prevent listener leak
        val timeoutJob = ioScope.launch {
            delay(5.minutes)
            if (!triggered.get()) {
                lgr.info { "Host ${_id} crash listener timeout reached, closing" }
                closeListener()
            }
        }

        listenerHolder[0] = DockerService.listenLog(
            _id.str,
            onLine = { line ->
                if (!triggered.get() && (line.contains("Preparing crash report")
                            || line.contains("Failed to start the minecraft server")
                            || line.contains("Minecraft Crash Report")
                            || line.contains("Missing or unsupported mandatory dependencies")
                        )) {
                    if (triggered.compareAndSet(false, true)) {
                        timeoutJob.cancel()
                        closeListener()
                        ioScope.launch {
                            val ok = runCatching { analyzeCrashReport() }.getOrElse {
                                lgr.warn(it) { "Host ${_id} 分析崩溃报告失败" }
                                false
                            }
                            if (!ok) {
                                lgr.warn { "Host ${_id} 崩溃分析未能修复，强制停止" }
                                markSkipWorldSizeUpdate(_id)
                                runCatching { DockerService.forceStop(_id.str) }
                                    .onFailure { err ->
                                        lgr.warn(err) { "Host ${_id} 强制停止失败" }
                                    }
                            }
                        }
                    }
                }
            },
            onError = { err ->
                if (!triggered.get()) {
                    lgr.warn(err) { "Host ${_id} 监听日志失败" }
                    timeoutJob.cancel()
                }
            }
        )
    }

    // Pick a port in [50000, 60000) that isn't used by any existing room
    private suspend fun allocateRoomPort(): Int {
        val used = dbcl.find().map { it.port }.toList().toSet()
        val candidates = (PORT_START until PORT_END_EXCLUSIVE).asSequence()
            .filter { it !in used }
            .toList()
        if (candidates.isEmpty()) {
            throw RequestError("没有可用端口，请联系管理员")
        }
        return candidates.random()
    }

    suspend fun getPlayables(): List<Host> = withContext(Dispatchers.IO) {
        val runningIds = DockerService.listContainers(includeStopped = false)
            .mapNotNull { container ->
                val containerName = container.names?.firstOrNull()?.removePrefix("/") ?: return@mapNotNull null
                runCatching { ObjectId(containerName) }.getOrNull()
            }
            .distinct()

        if (runningIds.isEmpty()) {
            emptyList()
        } else {
            dbcl.find(`in`("_id", runningIds)).toList()
                .filter { host -> hostStates[host._id]?.session != null }
        }
    }

    suspend fun getIdles(): List<Host> {
        val result = mutableListOf<Host>()
        for (host in getPlayables()) {
            if (host.getOnlinePlayers().size == 0) {
                result += host
            }
        }
        return result
    }

    fun startIdleMonitor() {
        if (idleMonitorJob?.isActive == true) return
        idleMonitorJob = idleMonitorScope.launch {
            while (isActive) {
                try {
                    runIdleMonitorTick()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (t: Throwable) {
                    lgr.warn(t) { "Idle monitor tick failed: ${t.message}" }
                }
                delay(1.minutes)
            }
        }
    }

    fun stopIdleMonitor() {
        idleMonitorJob?.cancel()
        idleMonitorJob = null
        hostStates.values.forEach { state ->
            state.shutFlag = 0
            state.shutdownJob?.cancel()
        }
    }

    private suspend fun runIdleMonitorTick(forceStop: Boolean = false) {
        val runningHosts = try {
            getPlayables()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            lgr.warn(t) { "Failed to fetch running hosts: ${t.message}" }
            return
        }
        if (runningHosts.isEmpty()) return

        for (host in runningHosts) {
            val onlinePlayers = try {
                host.getOnlinePlayers()
            } catch (cancel: CancellationException) {
                throw cancel
            }.mapNotNull { if(it == ObjectId("000000000000000000000000")) RAccount.DEFAULT else PlayerService.getById(it) }
            lgr.info { "${host.name}在线：${onlinePlayers.map { it.name }}" }
            if (onlinePlayers.isEmpty()) {
                if (forceStop) {
                    clearShutFlag(host._id)
                    host.stop("forced idle shutdown")
                    continue
                }

                val current = hostStates[host._id]?.shutFlag ?: 0
                val newFlag = current + 1
                updateShutFlag(host._id, newFlag)
                if (newFlag >= SHUTDOWN_THRESHOLD) {
                    host.stop("idle for $newFlag consecutive minutes")
                    clearShutFlag(host._id)
                }
            } else {
                clearShutFlag(host._id)
            }
        }
    }

    private fun updateShutFlag(hostId: ObjectId, value: Int) {
        if (value <= 0) {
            clearShutFlag(hostId)
        } else {
            val state = hostStates.computeIfAbsent(hostId) { HostState() }
            state.shutFlag = value
            lgr.info { "upd shut flag $hostId $value" }
        }
    }

    private fun clearShutFlag(hostId: ObjectId) {
        hostStates[hostId]?.let { state ->
            state.shutFlag = 0
            state.shutdownJob?.cancel()
            if (state.session == null && state.shutdownJob?.isActive != true) {
                hostStates.remove(hostId, state)
            }
        }
        lgr.debug { "保持在线 $hostId" }
    }

    private fun Host.stop(reason: String) {
        runCatching {
            DockerService.stop(_id.str)
            lgr.info { "Stopped host $name ($reason)" }
            refreshWorldSizeAfterStop(waitForStop = false)
        }.onFailure {
            lgr.warn(it) { "Failed to stop host ${name}: ${it.message}" }
        }
        clearShutFlag(_id)
    }

    // ---------- Core Logic (no ApplicationCall side-effects) ----------
    suspend fun Host.getOnlinePlayers(): List<ObjectId> {
        return try {
            if (status != HostStatus.PLAYABLE)
                return emptyList()
            McServerPinger.ping(this.port).players?.let { players ->
                lgr.info { "get online players for ${this.name} = ${players}" }
                players.sample.map { UUID.fromString(it.id).objectId }
            } ?: emptyList()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            lgr.warn(t) { "Failed to ping host ${this._id}: ${t.message}" }
            emptyList()
        }
    }

    suspend fun getAllHostsOnlinePlayerIds(): List<ObjectId> = coroutineScope {
        val hosts = getPlayables()
        if (hosts.isEmpty()) return@coroutineScope emptyList()
        hosts.map { host ->
            async { host.getOnlinePlayers() }
        }.awaitAll()
            .flatten()
            .distinct()
    }

    private suspend fun RAccount.resolveWorld(
        saveWorld: Boolean,
        worldId: ObjectId?,
        modpackId: ObjectId,
        currentHostId: ObjectId? = null
    ): World? {
        if (!saveWorld) return null
        if (worldId == null) {
            return createWorld(_id, null, modpackId)
        }
        val occupyHost = findByWorld(worldId)
        if (occupyHost != null && occupyHost._id != currentHostId) {
            throw RequestError("此存档数据已被地图“${occupyHost.name}”占用")
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
        if (getByOwner(playerId).size > 3 && !this.isDav) {
            throw RequestError("最多只可创建3张地图")
        }
        if(host.name.contains("公测") && !this.isDav){
            throw RequestError("无权创建公测地图")
        }
        val world = resolveWorld(host.saveWorld, host.worldId, host.modpackId)
        val modpack = ModpackService.getById(host.modpackId) ?: throw RequestError("无此包")
        val version = modpack.getVersion(host.packVer) ?: throw RequestError("无此版本")
        val port = allocateRoomPort()
        val host = Host(
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
            MailService.sendSystemMail(playerId, "地图创建中", "${host.name}正在创建中，请稍等几分钟...")._id
        dbcl.insertOne(host)
        startCreateHost(host, modpack, version, mailId)


    }

    private fun startCreateHost(
        host: Host,
        modpack: Modpack,
        version: Modpack.Version,
        mailId: ObjectId
    ) {
        ioScope.launch {
            runCatching {
                if (host.dir.exists()) {
                    host.dir.deleteRecursivelyNoSymlink()
                }
                host.dir.mkdir()

                host.makeContainer(host.worldId, modpack, version)

                modpack.installToHost(host.packVer, host) {
                    MailService.changeMail(mailId, "地图创建中", newContent = it)
                }
                host.writeServerProperties()
                lgr.info { "installToHost returned. Proceeding to start Docker container for host ${host._id} (Logic Error Tracing)." }
                DockerService.start(host._id.str)
                host.listenCrashOnStart()

                clearShutFlag(host._id)
            }.onFailure {
                lgr.error { it }
                it.printStackTrace()
                MailService.changeMail(mailId, "地图创建失败", newContent = "无法创建地图，错误：${it}")
            }.onSuccess {
                MailService.changeMail(mailId, "地图创建成功", newContent = "可以玩了")
            }
        }
    }

    private fun Host.makeContainer(
        worldId: ObjectId?,
        modpack: Modpack,
        version: Modpack.Version
    ) {
        DockerService.deleteContainer(_id.str)
        ensureWorkdirQuota()

        val sharedLibsDir = modpack.libsDir.canonicalFile.also { it.mkdirs() }
        val loaderVer = modpack.mcVer.loaderVersions[modpack.modloader]?: throw RequestError("找不到对应版本的运行库")
        val rdiCore = "rdi-5-mc-server-${modpack.mcVer.mcVer}-${modpack.modloader}.jar"
        val mounts = mutableListOf(
            Mount()
                .withType(MountType.BIND)
                .withSource(dir.absolutePath)
                .withTarget("/opt/server"),
            Mount()
                .withType(MountType.BIND)
                .withSource(sharedLibsDir.resolve("libraries").absolutePath)
                .withTarget("/opt/server/libraries"),
            Mount()
                .withType(MountType.BIND)
                .withSource(sharedLibsDir.resolve("mods").resolve(rdiCore).absolutePath)
                .withTarget("/opt/server/mods/${rdiCore}"),
        ).apply {
            //装入mod
            version.mods
                .filter { it.side != Mod.Side.CLIENT && !it.fileName.startsWith(CLIENT_ONLY_MARK_PREFIX) }
                .forEach { mod ->
                    val source = DL_MOD_DIR.resolve(mod.fileName)
                    if (source.exists()) {
                        this += Mount()
                            .withType(MountType.BIND)
                            .withSource(source.absolutePath)
                            .withTarget("/opt/server/mods/${mod.fileName}")
                    }
                }
            //1.16.5以下装入核心
            if(modpack.mcVer == McVersion.V122 || modpack.mcVer == McVersion.V071){
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(sharedLibsDir.resolve(loaderVer.serverJarName).absolutePath)
                    .withTarget("/opt/server/${loaderVer.serverJarName}")
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(sharedLibsDir.resolve(modpack.mcVer.serverJarName).absolutePath)
                    .withTarget("/opt/server/${modpack.mcVer.serverJarName}")
            }

            if (worldId != null) {
                //使用存档
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(WorldService.getDataDir(worldId).absolutePath)
                    .withTarget("/data")
            } else {
                //不存档
                this += Mount()
                    .withType(MountType.TMPFS)
                    .withTarget("/data")
                    .withTmpfsOptions(TmpfsOptions().withSizeBytes(512 * 1024 * 1024))
            }
        }
        val image = "rdi:j${modpack.mcVer.jreVer}"
        modpack.mcVer.loaderVersions[modpack.modloader]?.let { modLoaderVersion ->
            DockerService.createContainer(
                port,
                this._id.str,
                mounts,
                image,
                containerEnv(modpack.mcVer,modLoaderVersion)
            )
        } ?: throw RequestError("不支持的mod加载器")
    }

    suspend fun HostContext.delete() {
        if (host.status == HostStatus.PLAYABLE) {
            graceStop()
        }
        host.dir.deleteRecursivelyNoSymlink()
        host.dir.delete()
        DockerService.deleteContainer(host._id.str)

        // Force cleanup all tracking structures to prevent memory leaks
        hostStates.remove(host._id)?.let { state ->
            state.shutdownJob?.cancel()
            state.session?.launch {
                runCatching { state.session?.close(CloseReason(CloseReason.Codes.NORMAL, "Host deleted")) }
            }
        }
        skipWorldSizeUpdate.remove(host._id)

        dbcl.deleteOne(eq("_id", host._id))
    }

    suspend fun HostContext.changeVersion(packVer: String?) {
        if (host.status != HostStatus.STOPPED) {
            throw RequestError("请先停止主机")
        }
        val modpack = ModpackService.getById(host.modpackId) ?: throw RequestError("无此整合包")
        val modpackVer = modpack.versions.find { it.name == packVer } ?: modpack.versions.lastOrNull()
        ?: throw RequestError("无可用版本")
        val resolvedVer = modpackVer.name
        val hostIdStr = host._id.str
        val mailId = MailService.sendSystemMail(
            player._id,
            "地图整合包切换中",
            "你的地图《${host.name}》正在切换到整合包版本 $resolvedVer ，请稍等几分钟..."
        )._id

        DockerService.deleteContainer(hostIdStr)
        clearShutFlag(host._id)
        dbcl.updateOne(
            eq("_id", host._id), combine(
                set(Host::packVer.name, resolvedVer),
            )
        )
        host.packVer = resolvedVer
        startCreateHost(host, modpack, modpackVer, mailId)
        MailService.changeMail(mailId, "地图整合包版本切换完成", "好了")
    }

    suspend fun HostContext.changeOptions(payload: Host.OptionsDto) {

        if (payload.packVer != null && payload.modpackId == null) {
            changeVersion(payload.packVer)
        }
        val updates = mutableListOf<Bson>()
        payload.gameRules?.let { rules ->
            updates += set(Host::gameRules.name, rules)
        }
        payload.name?.let {
            it.validateName()
            updates += set(Host::name.name, it)
        }
        payload.packVer?.let { updates += set(Host::packVer.name, it) }
        payload.difficulty?.let { updates += set(Host::difficulty.name, it) }
        payload.gameMode?.let { updates += set(Host::gameMode.name, it) }
        payload.levelType?.takeIf { it.isNotBlank() }?.let { updates += set(Host::levelType.name, it) }
        payload.whitelist?.let { updates += set(Host::whitelist.name, it) }
        payload.allowCheats?.let { updates += set(Host::allowCheats.name, it) }
        if (updates.isNotEmpty()) {
            val update = if (updates.size == 1) updates.first() else combine(updates)
            dbcl.updateOne(eq("_id", host._id), update)
        }
        //刷新数据
        getById(host._id)?.writeServerProperties()
    }

    suspend fun HostContext.start() {
        val current = host
        val isMember = member.role != Role.GUEST
        val isPublicHost = current.isPublicTest || !current.whitelist
        if (!isPublicHost && !isMember && !player.isDav) {
            throw RequestError("私有地图仅成员可启动")
        }
        if (DockerService.isStarted(current._id.str)) {
            throw RequestError("已经启动过了")
        }
        val modpack = ModpackService.getById(current.modpackId) ?: throw RequestError("无此整合包")
        val version = modpack.getVersion(current.packVer) ?: throw RequestError("无此版本")
        DockerService.deleteContainer(current._id.str)
        current.writeServerProperties()
        current.makeContainer(current.worldId, modpack, version)
        DockerService.start(current._id.str)
        current.listenCrashOnStart()
        clearShutFlag(current._id)
    }

    suspend fun HostContext.graceStop() {
        sendCommand("stop")
        clearShutFlag(host._id)
        host.refreshWorldSizeAfterStop(waitForStop = true)
    }

    suspend fun HostContext.forceStop() {
        clearShutFlag(host._id)
        DockerService.forceStop(host._id.str)
        host.refreshWorldSizeAfterStop(waitForStop = false)
    }

    suspend fun HostContext.restart() {
        sendCommand("stop")
        clearShutFlag(host._id)
        DockerService.restart(host._id.str)
    }

    suspend fun HostContext.sendCommand(command: String) {
        val hostId = host._id
        val normalized = command.trimEnd()
        if (normalized.isBlank()) throw RequestError("命令不能为空")

        val session = hostStates[hostId]?.session ?: throw RequestError("地图未处于游玩状态")

        val message = WsMessage(
            reqId,
            channel = WsMessage.Channel.Command,
            data = normalized
        )

        try {
            session.send(Frame.Text(message.json))
            reqId++
        } catch (cancel: CancellationException) {
            hostStates[hostId]?.let { state ->
                if (state.session === session) {
                    state.session = null
                    if (state.shutFlag <= 0) {
                        hostStates.remove(hostId, state)
                    }
                }
            }
            throw cancel
        } catch (t: Throwable) {
            hostStates[hostId]?.let { state ->
                if (state.session === session) {
                    state.session = null
                    if (state.shutFlag <= 0) {
                        hostStates.remove(hostId, state)
                    }
                }
            }
            lgr.warn(t) { "发送命令到 $hostId 失败: ${t.message}" }
            throw RequestError("发送命令失败: ${t.message ?: "未知错误"}")
        }
    }

    val Host.status: HostStatus
        get() {
            val status = DockerService.getContainerStatus(_id.str)
            if (status == HostStatus.STARTED && hostStates[_id]?.session != null) {
                return HostStatus.PLAYABLE
            }
            return status
        }


    suspend fun findByModpackVersion(modpackId: ObjectId, verName: String): List<Host> {
        return dbcl.find(
            and(
                eq("modpackId", modpackId),
                eq("packVer", verName)
            )
        ).toList()
    }

    suspend fun findByModpack(modpackId: ObjectId): List<Host> {
        return dbcl.find(
            and(
                eq("modpackId", modpackId),
            )
        ).toList()
    }

    // ---------- Streaming Helper (still needs ApplicationCall for SSE) ----------
    suspend fun HostContext.listenLogs(session: ServerSSESession) {
        val hostId = host._id
        val containerName = hostId.str
        session.heartbeat {
            period = 15.seconds
            event = ServerSentEvent(event = "heartbeat", data = "ping")
        }

        var sentFileTail = false
        var sentContainerTail = false
        try {
            while (currentCoroutineContext().isActive) {
                val hasContainer = DockerService.findContainer(containerName) != null
                val started = hasContainer && DockerService.isStarted(containerName)
                if (!started) {
                    if (!sentFileTail) {
                        runCatching { sendLatestLogTail(session, host.dir) }
                        sentFileTail = true
                    }
                    sentContainerTail = false
                    delay(2.seconds)
                    continue
                }

                if (!sentContainerTail) {
                    runCatching {
                        DockerService.getLog(containerName, startLine = 0, endLine = 200)
                            .lineSequence()
                            .map { it.trimEnd('\r') }
                            .filter { it.isNotBlank() }
                            .toList()
                            .asReversed()
                            .forEach { session.send(ServerSentEvent(data = it)) }
                    }
                    sentContainerTail = true
                    sentFileTail = false
                }

                val lines = Channel<String>(capacity = Channel.BUFFERED)
                val subscription = DockerService.listenLog(
                    containerName,
                    onLine = { lines.trySend(it).isSuccess },
                    onError = { err -> lines.close(err) },
                    onFinished = { lines.close() }
                )
                try {
                    for (payload in lines) {
                        payload.lineSequence()
                            .map { it.trimEnd('\r') }
                            .filter { it.isNotEmpty() }
                            .forEach { session.send(ServerSentEvent(data = it)) }
                    }
                } catch (t: CancellationException) {
                    throw t
                } catch (t: io.ktor.utils.io.ClosedWriteChannelException) {
                    return
                } catch (t: io.ktor.util.cio.ChannelWriteException) {
                    return
                } catch (t: NotFoundException) {
                    sentContainerTail = false
                } catch (t: Throwable) {
                    if (t.message?.contains("Cannot write to channel", ignoreCase = true) == true) {
                        return
                    }
                    throw t
                } finally {
                    runCatching { subscription.close() }
                    lines.cancel()
                }
            }
        } catch (cancel: CancellationException) {
            //"已取消载入日志"
        } catch (t: Throwable) {
            val ignore = t is io.ktor.utils.io.ClosedWriteChannelException ||
                    t is io.ktor.util.cio.ChannelWriteException ||
                    t.message?.contains("Cannot write to channel", ignoreCase = true) == true
            if (!ignore) {
                runCatching { session.send(ServerSentEvent(event = "error", data = t.message ?: "unknown")) }
            }
        }
    }

    private suspend fun sendLatestLogTail(
        session: ServerSSESession,
        hostDir: File,
        maxLines: Int = 200
    ) {
        val logFile = hostDir.resolve("logs").resolve("latest.log")
        if (!logFile.exists()) {
            session.send(ServerSentEvent(data = "没有日志"))
            return
        }
        val buffer = ArrayDeque<String>(maxLines)
        logFile.useLines(Charsets.UTF_8) { lines ->
            lines.forEach { line ->
                if (buffer.size == maxLines) {
                    buffer.removeFirst()
                }
                buffer.addLast(line)
            }
        }
        buffer.forEach { line ->
            if (line.isNotBlank()) {
                session.send(ServerSentEvent(data = line))
            }
        }
    }


    fun Host.hasMember(id: ObjectId): Boolean {
        return members.any { it.id == id }
    }

    suspend fun RAccount.ownHosts() = HostService.getByOwner(_id)


    suspend fun RAccount.listHostLobbyLegacy(
        page: Int,
        pageSize: Int = HOSTS_PER_PAGE,
        onlyShowMy: Boolean = false
    ): List<Host.BriefVo> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = pageSize.coerceIn(1, 100)
        val hosts = dbcl.find()
            .sort(com.mongodb.client.model.Sorts.descending("_id"))
            .skip(safePage * safeSize)
            .limit(safeSize)
            .toList()

        if (hosts.isEmpty()) return emptyList()

        val requesterId = _id
        val visibleHosts = hosts.filter { host ->
            val isMember = host.ownerId == requesterId || host.members.any { it.id == requesterId }
            //只显示受邀时
            if (onlyShowMy) {
                return@filter isMember
            }
            return@filter true
        }


        val results = coroutineScope {
            visibleHosts.map { host ->
                async {
                    val modpack = ModpackService.getById(host.modpackId)
                    val onlinePlayers = host.getOnlinePlayers()
                    val isMember = host.ownerId == requesterId || host.members.any { it.id == requesterId }
                    val playable = when {
                        isMember -> true
                        host.status == HostStatus.PLAYABLE && !host.whitelist -> true
                        else -> false
                    }
                    Host.BriefVo(
                        _id = host._id,
                        intro = host.intro,
                        name = host.name,
                        ownerId = host.ownerId,
                        modpackName = modpack?.name ?: "未知整合包",
                        iconUrl = modpack?.iconUrl,
                        packVer = host.packVer,
                        port = host.port,
                        playable = playable,
                        isMember = isMember,
                        onlinePlayerIds = onlinePlayers
                    )
                }
            }.awaitAll()
        }
        return results.sortedByDescending { it.onlinePlayerIds.size }
    }

    suspend fun RAccount.listAllHosts(
        page: Int,
        myOnly: Boolean,
        pageSize: Int = HOSTS_PER_PAGE
    ): List<Host.BriefVo> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = pageSize.coerceIn(1, 100)
        val hosts = dbcl.find()
            .sort(com.mongodb.client.model.Sorts.descending("_id"))
            .skip(safePage * safeSize)
            .limit(safeSize)
            .toList()

        if (hosts.isEmpty()) return emptyList()

        val requesterId = _id
        val (memberHosts, otherHosts) = hosts.partition { host ->
            host.ownerId == requesterId ||
                    host.members.any { it.id == requesterId }
        }
        val visibleHosts = if (myOnly) {
            memberHosts
        } else {
            memberHosts + otherHosts
        }

        return coroutineScope {
            visibleHosts.map { host ->
                async {
                    val modpack = ModpackService.getById(host.modpackId)
                    val onlinePlayers = host.getOnlinePlayers()
                    val isMember = host.ownerId == requesterId || host.members.any { it.id == requesterId }
                    val playable = when {
                        isMember -> true
                        host.isPublicTest -> true
                        host.status == HostStatus.PLAYABLE && !host.whitelist -> true
                        else -> false
                    }
                    Host.BriefVo(
                        _id = host._id,
                        intro = host.intro,
                        name = host.name,
                        ownerId = host.ownerId,
                        modpackName = modpack?.name ?: "未知整合包",
                        iconUrl = modpack?.iconUrl,
                        packVer = host.packVer,
                        port = host.port,
                        playable = playable,
                        isMember = isMember,
                        onlinePlayerIds = onlinePlayers
                    )
                }
            }.awaitAll()
        }
    }

    // List all hosts belonging to a team
    suspend fun getByOwner(uid: ObjectId): List<Host> =
        dbcl.find(eq("ownerId", uid)).toList()


    suspend fun getByPort(port: Int): Host? = dbcl.find(eq("port", port)).firstOrNull()

    suspend fun findByWorld(worldId: ObjectId): Host? =
        dbcl.find(eq("worldId", worldId)).firstOrNull()

    suspend fun getById(id: ObjectId): Host? = dbcl.find(eq("_id", id)).firstOrNull()
    suspend fun Host.toDetailVo(): Host.DetailVo {
        val modpack = ModpackService.getById(modpackId)
        val modpackVo = modpack?.toBriefVo()
            ?: Modpack.BriefVo(id = modpackId, name = "未知整合包")
        val onlinePlayers = runCatching { getOnlinePlayers() }.getOrElse { emptyList() }
        return Host.DetailVo(
            _id = _id,
            name = name,
            intro = intro,
            iconUrl = modpackVo.icon,
            ownerId = ownerId,
            modpack = modpackVo,
            packVer = packVer,
            worldId = worldId,
            port = port,
            difficulty = difficulty,
            gameMode = gameMode,
            levelType = levelType,
            gameRules = gameRules,
            whitelist = whitelist,
            allowCheats = allowCheats,
            members = members,
            extraMods = extraMods,
            onlinePlayerIds = onlinePlayers
        )
    }

    suspend fun stopIdleHosts() {
        runIdleMonitorTick(forceStop = true)
    }


    private fun Host.overlayRootDir(): File = HOSTS_DIR.resolve(_id.str)

    private fun markSkipWorldSizeUpdate(hostId: ObjectId) {
        skipWorldSizeUpdate.add(hostId)
    }

    private fun Host.refreshWorldSizeAfterStop(waitForStop: Boolean) {
        val worldId = worldId ?: return
        ioScope.launch {
            if (skipWorldSizeUpdate.remove(_id)) {
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
                        lgr.warn(err) { "Host ${_id} 更新存档大小失败: ${err.message}" }
                    }
            }
        }
    }


    suspend fun HostContext.delMember() {
        if (targetMember.role.level <= Role.ADMIN.level && this.member.role != Role.OWNER) {
            throw RequestError("无法踢出管理员")
        }
        dbcl.updateOne(
            eq("_id", host._id),
            Updates.pull(Host::members.name, eq("id", targetMember.id))
        )
    }

    suspend fun HostContext.transferOwnership() {
        val current = getById(host._id) ?: throw RequestError("无此地图")
        val recipient = targetMember
        if (current.ownerId == recipient.id) throw RequestError("不能转给自己")
        if(!getTargetPlayer().hasMsid) throw RequestError("找不到对方的微软账号")
        val previousOwner = current.members.find { it.id == current.ownerId }
            ?: throw RequestError("当前拥有者不在成员列表")
        val hasRecipient = current.members.any { it.id == recipient.id }
        if (!hasRecipient) throw RequestError("目标成员不在主机成员列表中")

        val updatedMembers = current.members.map { member ->
            when (member.id) {
                previousOwner.id -> member.copy(role = Role.ADMIN)
                recipient.id -> member.copy(role = Role.OWNER)
                else -> member
            }
        }

        dbcl.updateOne(
            eq("_id", current._id),
            combine(
                set(Host::ownerId.name, recipient.id),
                set(Host::members.name, updatedMembers)
            )
        )

    }

    suspend fun HostContext.addMember(qq: String) {
        val current = getById(host._id) ?: throw RequestError("无此地图")
        val target = PlayerService.getByQQ(qq) ?: throw RequestError("无此账号")
        if (current.hasMember(target._id)) {
            throw RequestError("该用户已是成员")
        }
        if (!current.isPublicTest && current.members.size >= 10) {
            throw RequestError("该地图最多只能有10名成员")
        }
        val joinedCount = dbcl.countDocuments(eq("${Host::members.name}.${Host.Member::id.name}", target._id))
        if (joinedCount >= 10) {
            throw RequestError("该用户已加入 9 张地图，无法继续加入")
        }
        dbcl.updateOne(
            eq("_id", current._id),
            Updates.push(Host::members.name, Host.Member(target._id, Role.MEMBER))
        )
    }

    suspend fun HostContext.setRole(role: Role) {
        if (targetMember.role == role) {
            throw RequestError("角色未更改")
        }
        if (targetMember.role == Role.OWNER) {
            throw RequestError("无法更改拥有者角色")
        }
        if (role == Role.OWNER) {
            throw RequestError("请使用转移拥有者来指定新的拥有者")
        }
        dbcl.updateOne(
            eq("_id", host._id),
            Updates.combine(
                Updates.set("${Host::members.name}.$[elem].${Host.Member::role.name}", role),
            ),
            UpdateOptions().arrayFilters(
                listOf(
                    Document("elem.id", targetMember.id),
                )
            )
        )
    }

    suspend fun HostContext.quit() {
        if (!host.hasMember(player._id)) {
            throw RequestError("你不是此地图成员")
        }
        if (host.ownerId == player._id) {
            throw RequestError("拥有者无法退出地图")
        }
        dbcl.updateOne(
            eq("_id", host._id),
            Updates.pull(Host::members.name, Document(Host.Member::id.name, player._id))
        )
    }
}

