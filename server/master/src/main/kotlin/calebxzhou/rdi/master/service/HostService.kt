package calebxzhou.rdi.master.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.jarResource
import calebxzhou.mykotutils.std.readAllString
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.isExcludedConfigPath
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.model.ModrinthVersionInfo
import calebxzhou.rdi.common.service.CurseForgeService
import calebxzhou.rdi.common.service.McServerPinger
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.service.ModrinthService
import calebxzhou.rdi.common.service.TaczGunpackValidator
import calebxzhou.rdi.common.service.runInline
import calebxzhou.rdi.common.util.ioScope
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.common.util.validateName
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.master.HOSTS_DIR
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.model.RChatMessage
import calebxzhou.rdi.master.model.RGlobalPlayerList
import calebxzhou.rdi.master.model.WsMessage
import calebxzhou.rdi.master.net.*
import calebxzhou.rdi.master.service.HostService.addDisabledMods
import calebxzhou.rdi.master.service.HostService.addExtraMods
import calebxzhou.rdi.master.service.HostService.addMember
import calebxzhou.rdi.master.service.HostService.changeOptions
import calebxzhou.rdi.master.service.HostService.changeVersion
import calebxzhou.rdi.master.service.HostService.createHost
import calebxzhou.rdi.master.service.HostService.delMember
import calebxzhou.rdi.master.service.HostService.delete
import calebxzhou.rdi.master.service.HostService.deleteDisabledMods
import calebxzhou.rdi.master.service.HostService.listConfigFiles
import calebxzhou.rdi.master.service.HostService.listHostFiles
import calebxzhou.rdi.master.service.HostService.readConfigFile
import calebxzhou.rdi.master.service.HostService.deleteExtraMods
import calebxzhou.rdi.master.service.HostService.deleteHostFile
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
import calebxzhou.rdi.master.service.HostService.saveConfigFile
import calebxzhou.rdi.master.service.HostService.setRole
import calebxzhou.rdi.master.service.HostService.start
import calebxzhou.rdi.master.service.HostService.status
import calebxzhou.rdi.master.service.HostService.toDetailVo
import calebxzhou.rdi.master.service.HostService.transferOwnership
import calebxzhou.rdi.master.service.HostService.uploadHostFile
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
import io.ktor.client.call.body
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.sse.*
import io.ktor.websocket.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException as KxCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.io.Closeable
import java.io.File
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val Host.dir get() = HOSTS_DIR.resolve(_id.str)

// ---------- Routing DSL (mirrors teamRoutes style) ----------
fun Route.hostRoutes() = route("/host") {
    get("/online-player-ids") {
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
            response(data = ctx.sendCommand(param("command"), waitForResponse = true))
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
            call.hostContext().needOwner.delete(call.receiveNullable<Host.DeleteDto>() ?: Host.DeleteDto())
            ok()
        }
        get {
            HostService.getById(idParam("hostId"))?.let {
                response(data = it)
            } ?: err("无此房间")
        }
        get("detail") {
            HostService.getById(idParam("hostId"))?.let {
                response(data = it.toDetailVo())
            } ?: err("无此房间")
        }
        route("/mods") {
            route("/extra"){
                post {
                    val ctx = call.hostContext().needAdmin
                    ctx.addExtraMods(call.receive())
                    ok()
                }
                delete {
                    val ctx = call.hostContext().needAdmin
                    response(data = ctx.deleteExtraMods(call.receive()))
                }
                get {
                    response(data = call.hostContext().host.extraMods)
                }
            }
            route("/disabled") {
                post {
                    val ctx = call.hostContext().needAdmin
                    response(data = ctx.addDisabledMods(call.receive()))
                }
                delete {
                    val ctx = call.hostContext().needAdmin
                    response(data = ctx.deleteDisabledMods(call.receive()))
                }
                get {
                    response(data = call.hostContext().host.disabledMods)
                }
            }
        }
        route("/config") {
            get("/files") {
                val ctx = call.hostContext().needAdmin
                response(data = ctx.listConfigFiles())
            }
            get("/file") {
                val ctx = call.hostContext().needAdmin
                response(data = ctx.readConfigFile(param("path")))
            }
            put("/file") {
                val ctx = call.hostContext().needAdmin
                response(data = ctx.saveConfigFile(call.receive()))
            }
        }
        route("/files") {
            get {
                val ctx = call.hostContext().needAdmin
                response(data = ctx.listHostFiles(paramNull("path") ?: ""))
            }
            post("/file") {
                val ctx = call.hostContext().needAdmin
                response(data = ctx.uploadHostFile(call))
            }
            delete("/file") {
                val ctx = call.hostContext().needAdmin
                ctx.deleteHostFile(call.receive())
                ok()
            }
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
                    send(ServerSentEvent(event = "error", data = "此房间已被删除"))
                    return@sse
                } catch (err: RequestError) {
                    send(ServerSentEvent(event = "error", data = err.message ?: "unknown"))
                    return@sse
                }
                ctx.listenLogs(this)

            }
        }
        put("/quit") {
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

//单独拿出来是为了不走authentication  proxy和mc要用
fun Route.hostPlayRoutes() = route("/host") {
    get("/status") {
        val port = param("port").toInt()
        val host = HostService.getByPort(port) ?: throw RequestError("无此房间")
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
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "未知房间"))
            return@webSocket
        }

        if (!HostService.registerPlayableSession(hostId, this)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "重复连接"))
            return@webSocket
        }

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    HostService.handlePlayableMessage(hostId, frame.readText())
                }
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
    val targetMember get() = targetMemberNull ?: throw ParamError("玩家${player.name}不是此房间的受邀成员")
    suspend fun getTargetPlayer() =
        PlayerService.getById(targetMember.id) ?: throw ParamError("玩家${player.name}不存在")
}

object HostService {
    private val lgr by Loggers
    private val isWindowsHost: Boolean = System.getProperty("os.name").contains("windows", ignoreCase = true)
    private val dockerDesktopPrefix: String =
        System.getenv("DOCKER_DESKTOP_PATH_PREFIX")?.trimEnd('/') ?: "/run/desktop/mnt/host"

    val dbcl = DB.getCollection<Host>("host")

    private val hostStates = ConcurrentHashMap<ObjectId, HostState>()
    private val skipWorldSizeUpdate = ConcurrentHashMap.newKeySet<ObjectId>()
    private val onlinePlayersCache = ConcurrentHashMap<ObjectId, OnlinePlayersCacheEntry>()
    private val onlinePlayersRefreshJobs = ConcurrentHashMap<ObjectId, Job>()
    private val memberMutationLocks = ConcurrentHashMap<String, Mutex>()
    private val staleCleanupJob: Job
    private val globalPlayerListPollJob: Job
    @Volatile
    private var lastGlobalPlayerListFingerprint: String = ""
    @Volatile
    private var lastGlobalPlayerList: RGlobalPlayerList? = null

    private const val PORT_START = 50000
    private const val PORT_END_EXCLUSIVE = 60000
    private const val SHUTDOWN_THRESHOLD = 20
    private const val HOSTS_PER_PAGE = 100
    private const val HOST_WORKDIR_LIMIT_BYTES: Long = 1L * 1024 * 1024 * 1024
    private const val ONLINE_PLAYERS_CACHE_TTL_MS = 15_000L
    private const val COMMAND_RESPONSE_TIMEOUT_MS = 10_000L

    private fun createHostTaskKey(hostId: ObjectId): String =
        "server-host-create:${hostId.toHexString()}"

    private fun addExtraModsTaskKey(hostId: ObjectId, mods: List<Mod>): String {
        val modKeys = mods.map(::projectIdentity).sorted().joinToString(",")
        return "server-host-add-extra-mods:${hostId.toHexString()}:$modKeys"
    }
    private const val HOST_CONFIG_FILE_MAX_BYTES: Long = 8 * 1024
    private const val HOST_CONFIG_FILE_LIST_MAX_BYTES: Long = 8 * 1024
    private const val HOST_FILE_MAX_BYTES: Long = 64L * 1024 * 1024
    private const val TACZ_FILE_MAX_BYTES: Long = 100L * 1024 * 1024
    private const val TACZ_MAX_ZIP_FILES = 10
    private val editableConfigExtensions = setOf("json", "toml", "txt", "json5", "properties","yaml","yml")
    private val allowFileOprDir = setOf("tacz","kubejs")
    private val idleMonitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleMonitorJob: Job? = null

    private data class HostState(
        var shutFlag: Int = 0,
        var session: DefaultWebSocketServerSession? = null,
        var shutdownJob: Job? = null,
        val nextCommandReqId: AtomicInteger = AtomicInteger(0),
        val pendingCommands: ConcurrentHashMap<Int, CompletableDeferred<String>> = ConcurrentHashMap()
    )

    private data class OnlinePlayersCacheEntry(
        val playerIds: List<ObjectId>,
        val updatedAt: Long
    )

    private suspend fun <T> withMemberMutationLocks(
        vararg keys: String,
        block: suspend () -> T
    ): T {
        val locks = keys.distinct()
            .sorted()
            .map { memberMutationLocks.computeIfAbsent(it) { Mutex() } }

        suspend fun acquire(index: Int): T {
            if (index >= locks.size) return block()
            return locks[index].withLock {
                acquire(index + 1)
            }
        }

        return acquire(0)
    }


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
        globalPlayerListPollJob = idleMonitorScope.launch {
            while (isActive) {
                runCatching { pollAndBroadcastGlobalPlayerListIfChanged() }
                    .onFailure { error ->
                        if (error is KxCancellationException) throw error
                        lgr.warn { "轮询全局玩家列表失败: ${error.message}" }
                    }
                delay(1.minutes)
            }
        }
    }

    fun shutdown() {
        lgr.info { "Shutting down HostService..." }
        stopIdleMonitor()
        staleCleanupJob.cancel()
        globalPlayerListPollJob.cancel()
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
        onlinePlayersRefreshJobs.values.forEach(Job::cancel)
        onlinePlayersRefreshJobs.clear()
        onlinePlayersCache.clear()
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


    private fun Host.containerEnv(
        mcv: McVersion,
        loaderVersion: ModLoader.Version,
        lwjgl3ifyRuntime: Lwjgl3ifyServerSupport.PreparedRuntime?
    ): MutableList<String> {
        val serverArgs = when (mcv) {
            McVersion.V182,
            McVersion.V192,
            McVersion.V201,
            McVersion.V211 -> listOf(loaderVersion.serverArgsPath(true))

            McVersion.V165 -> McVersion.V165.plusJvmArgs + listOf("-jar", loaderVersion.serverJarName)
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
        return mutableListOf(
            "HOST_ID=${_id.str}",
            "GAME_PORT=${port}",
            "ALL_OP=${if (allowCheats) "true" else "false"}",
            "START_PARAMS=${(listOf("-Xmx8G") + serverArgs + noguiArg).joinToString(" ")}"
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

    private val ModLoader.Version.legacyForgeUniversalJarName: String
        get() {
            val artifactVersion = dirName
                .removePrefix("${McVersion.V071.mcVer}-Forge")
                .takeIf { it != dirName && it.isNotBlank() }
                ?.let { "${McVersion.V071.mcVer}-$it" }
                ?: id
            return "forge-$artifactVersion-universal.jar"
        }

    private fun Host.writeServerProperties() {
        dir.resolve("allowed_symlinks.txt").writeText("[regex].*")
        dir.resolve("eula.txt").writeText("eula=true")
        syncAllOpMarkers()
        "server.properties".run {
            this.jarResource(this).readAllString()
                .replace("#{port}", port.toString())
                .replace(
                    "#{difficulty}", getDifficultyText(difficulty))
                .replace("#{level-type}", levelType)
                .replace(
                    "#{gamemode}",getGameModeText(gameMode)).let {
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

    private fun Host.deleteTransientStartupDirs() {
        listOf("tacz_backup", "dynamic-data-pack-cache").forEach { dirName ->
            val targetDir = dir.resolve(dirName)
            if (!targetDir.exists()) return@forEach
            runCatching { targetDir.deleteRecursivelyNoSymlink() }
                .onFailure { err -> throw RequestError("删除${dirName}失败: ${err.message}") }
        }
    }

    private fun Host.syncAllOpMarkers() {
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

    private fun Host.ensureWorkdirQuota() {
        if (!dir.exists()) return
        val totalSize = dir.walkTopDown()
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            .sumOf { it.length() }
        if (totalSize > HOST_WORKDIR_LIMIT_BYTES) {
            throw RequestError("房间目录超过 3GB (${totalSize.humanFileSize}MB)，请删除不必要文件后再启动") }
    }

    val HostContext.needMember get() = requireRole(Role.MEMBER)
    val HostContext.needAdmin get() = requireRole(Role.ADMIN)
    val HostContext.needOwner get() = requireRole(Role.OWNER)
    fun HostContext.requireRole(level: Role): HostContext {
        member.let {
            var allowed = when (level) {
                Role.MEMBER -> member.role != Role.GUEST || host.isPublic
                Role.ADMIN -> member.role.level <= Role.ADMIN.level
                Role.OWNER -> member.role == Role.OWNER
                else -> false
            }
            if(player.isDav){
                allowed=true
            }
            if (!allowed) throw RequestError("无权限")
        }
        return this
    }


    suspend fun ApplicationCall.hostContext(): HostContext {
        val player = player()
        val requesterId = player._id
        val host = HostService.getById(idPathParam("hostId")) ?: throw RequestError("无此房间")
        val reqMem = host.members.firstOrNull { it.id == requesterId } ?: run {
            if (player.isDav) {
                Host.Member(id = requesterId, role = Role.ADMIN)
            } else if (!host.whitelist) {
                Host.Member(id = requesterId, role = Role.GUEST)
            } else throw RequestError("不是房间受邀成员")
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
            failPendingCommands(state, RequestError("房间连接已重建"))
            previous.launch {
                runCatching { previous.close(CloseReason(CloseReason.Codes.NORMAL, "新的连接建立")) }
            }
        }
        state.session = session
        //DockerService.limitCpuCores(hostId.str,4.0)
        lgr.info { "host $hostId gameplay 通道已连接" }
        lastGlobalPlayerList?.let { sendGlobalPlayerListToSession(session, it) }
        return true
    }

    fun unregisterPlayableSession(hostId: ObjectId, session: DefaultWebSocketServerSession) {
        hostStates[hostId]?.let { state ->
            if (state.session === session) {
                state.session = null
                failPendingCommands(state, RequestError("房间连接已断开"))
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
                                lgr.warn { "Host $hostId 通道断开后停止容器失败: ${error.message + "\n" + error}" }
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

    suspend fun handlePlayableMessage(hostId: ObjectId, text: String) {
        val message = runCatching {
            serdesJson.decodeFromString<WsMessage<JsonElement>>(text)
        }.getOrElse { error ->
            lgr.warn { "解析host $hostId gameplay消息失败: ${error.message}, raw=$text" }
            return
        }
        when (message.channel) {
            WsMessage.Channel.Response -> {
                val output = runCatching { message.data.jsonPrimitive.content }.getOrElse { message.data.toString() }
                val pending = hostStates[hostId]?.pendingCommands?.remove(message.id)
                if (pending != null) {
                    pending.complete(output.ifBlank { "OK" })
                } else {
                    lgr.debug { "host $hostId 返回了未知命令响应 id=${message.id}: $output" }
                }
            }

            WsMessage.Channel.Command -> {
                val output = runCatching { message.data.jsonPrimitive.content }.getOrElse { message.data.toString() }
                val pending = hostStates[hostId]?.pendingCommands?.remove(message.id)
                if (pending != null) {
                    pending.complete(output.ifBlank { "OK" })
                } else {
                    lgr.debug { "忽略host $hostId 主动发来的Command消息: $text" }
                }
            }

            WsMessage.Channel.Chat -> {
                val chatMessage = runCatching {
                    serdesJson.decodeFromJsonElement<RChatMessage>(message.data)
                }.getOrElse { error ->
                    lgr.warn { "解析host $hostId 聊天消息失败: ${error.message}, raw=$text" }
                    return
                }
                lgr.info { chatMessage.playerName+": "+chatMessage.content }
                ChatService.recordGameChat(chatMessage)
                if (chatMessage.global) {
                    broadcastChatMessage(message.id, chatMessage.copy(sourceHostId = hostId.toHexString()))
                }
            }

            WsMessage.Channel.PlayerList -> {
                lgr.debug { "忽略host $hostId 主动发来的玩家列表消息" }
            }
        }
    }

    private fun broadcastChatMessage(id: Int, chatMessage: RChatMessage) {
        val message = WsMessage(
            id = id,
            channel = WsMessage.Channel.Chat,
            data = chatMessage
        )
        hostStates.forEach { (hostId, state) ->
            if (hostId.toHexString() == chatMessage.sourceHostId) return@forEach
            val session = state.session ?: return@forEach
            session.launch {
                runCatching { session.send(Frame.Text(message.json)) }
                    .onFailure { error -> lgr.warn { "广播全局聊天到host失败: ${error.message}" } }
            }
        }
    }

    private fun sendGlobalPlayerListToSession(session: DefaultWebSocketServerSession, playerList: RGlobalPlayerList) {
        val message = WsMessage(
            id = 0,
            channel = WsMessage.Channel.PlayerList,
            data = playerList
        )
        session.launch {
            runCatching { session.send(Frame.Text(message.json)) }
                .onFailure { error -> lgr.warn { "发送全局玩家列表到host失败: ${error.message}" } }
        }
    }

    private fun broadcastGlobalPlayerList(playerList: RGlobalPlayerList) {
        hostStates.values.forEach { state ->
            val session = state.session ?: return@forEach
            sendGlobalPlayerListToSession(session, playerList)
        }
    }

    private suspend fun pollAndBroadcastGlobalPlayerListIfChanged() {
        val hosts = collectGlobalPlayerListHosts()
        val fingerprint = hosts.json
       // if (fingerprint == lastGlobalPlayerListFingerprint) return

        lastGlobalPlayerListFingerprint = fingerprint
        val playerList = RGlobalPlayerList(
            generatedAt = System.currentTimeMillis(),
            hosts = hosts
        )
        lastGlobalPlayerList = playerList
        broadcastGlobalPlayerList(playerList)
        lgr.info { "全局玩家列表已变化，广播${hosts.sumOf { it.players.size }}个玩家/${hosts.size}个房间" }
    }

    private suspend fun collectGlobalPlayerListHosts(): List<RGlobalPlayerList.HostEntry> = coroutineScope {
        getPlayables()
            .map { host -> async { host.fetchGlobalPlayerListHostEntry() } }
            .awaitAll()
            .filterNotNull()
            .sortedWith(compareBy<RGlobalPlayerList.HostEntry> { it.hostName }.thenBy { it.hostId })
    }

    private suspend fun Host.fetchGlobalPlayerListHostEntry(): RGlobalPlayerList.HostEntry? {
        if (status != HostStatus.PLAYABLE) return null
        val players = runCatching {
            McServerPinger.ping(port, timeoutMillis = 1_000).players?.sample.orEmpty()
        }.getOrElse { error ->
            if (error is KxCancellationException) throw error
            lgr.warn { "轮询host ${_id} 玩家列表失败: ${error.message}" }
            return null
        }
            .mapNotNull { sample ->
                val playerId = sample.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RGlobalPlayerList.PlayerEntry(
                    playerId = playerId,
                    playerName = sample.name?.takeIf { it.isNotBlank() } ?: playerId
                )
            }
            .distinctBy { it.playerId }
            .sortedWith(compareBy<RGlobalPlayerList.PlayerEntry> { it.playerName }.thenBy { it.playerId })
        if (players.isEmpty()) return null
        val modpackName = runCatching { ModpackService.getById(modpackId)?.name }
            .getOrElse { error ->
                if (error is KxCancellationException) throw error
                lgr.warn { "读取host ${_id} 整合包名称失败: ${error.message}" }
                null
            } ?: "未知整合包"
        return RGlobalPlayerList.HostEntry(
            hostId = _id.toHexString(),
            hostName = name,
            modpackName = modpackName,
            packVer = packVer,
            players = players
        )
    }

    private fun failPendingCommands(state: HostState, cause: Throwable) {
        state.pendingCommands.values.forEach { it.completeExceptionally(cause) }
        state.pendingCommands.clear()
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
                            )
                ) {
                    if (triggered.compareAndSet(false, true)) {
                        timeoutJob.cancel()
                        closeListener()
                        ioScope.launch {
                            lgr.warn { "Host ${_id} 启动失败，停止房间" }
                            markSkipWorldSizeUpdate(_id)
                            runCatching { DockerService.forceStop(_id.str) }
                                .onFailure { err ->
                                    lgr.warn(err) { "Host ${name} 停止失败" }
                                }
                        }
                    }
                }
            },
            onError = { err ->
                if (!triggered.get()) {
                    lgr.warn(err) { "Host ${name} 监听日志失败" }
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
        if(DEBUG){
            return@withContext dbcl.find(`in`("_id", hostStates.keys().toList())).toList()
        }
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
            if (host.fetchOnlinePlayersNow().isEmpty()) {
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
                } catch (cancel: KxCancellationException) {
                    throw cancel
                } catch (t: Throwable) {
                    lgr.warn { "Idle monitor tick failed: ${t.message + "\n" + t}" }
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
        } catch (cancel: KxCancellationException) {
            throw cancel
        } catch (t: Throwable) {
            lgr.warn { "Failed to fetch running hosts: ${t.message + "\n" + t}" }
            return
        }
        if (runningHosts.isEmpty()) return

        for (host in runningHosts) {
            val onlinePlayers = try {
                host.fetchOnlinePlayersNow()
            } catch (cancel: KxCancellationException) {
                throw cancel
            }.mapNotNull {
                if (it == ObjectId("000000000000000000000000")) RAccount.DEFAULT else PlayerService.getById(
                    it
                )
            }
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
            lgr.warn { "Failed to stop host ${name + "\n" + it}: ${it.message}" }
        }
        clearShutFlag(_id)
    }

    // ---------- Core Logic (no ApplicationCall side-effects) ----------
    private suspend fun Host.fetchOnlinePlayersNow(): List<ObjectId> {
        return try {
            if (status != HostStatus.PLAYABLE)
                return emptyList()
            val players = McServerPinger.ping(port, timeoutMillis = 1_000).players
            val playerIds = players?.let { players ->
                lgr.info { "get online players for ${this.name} = ${players}" }
                players.sample.map { UUID.fromString(it.id).objectId }
            } ?: emptyList()
            onlinePlayersCache[_id] = OnlinePlayersCacheEntry(playerIds, System.currentTimeMillis())
            playerIds
        } catch (cancel: KxCancellationException) {
            throw cancel
        } catch (t: Throwable) {
            lgr.warn { "Failed to ping host ${this._id}: ${t.message}" }
            emptyList()
        }
    }

    private fun Host.refreshOnlinePlayersInBackground() {
        val existing = onlinePlayersRefreshJobs[_id]
        if (existing?.isActive == true) return
        onlinePlayersRefreshJobs[_id] = ioScope.launch {
            try {
                fetchOnlinePlayersNow()
            } finally {
                onlinePlayersRefreshJobs.remove(_id)
            }
        }
    }

    suspend fun Host.getOnlinePlayers(): List<ObjectId> {
        if (status != HostStatus.PLAYABLE) return emptyList()
        val cached = onlinePlayersCache[_id]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.updatedAt <= ONLINE_PLAYERS_CACHE_TTL_MS) {
            return cached.playerIds
        }
        refreshOnlinePlayersInBackground()
        return cached?.playerIds ?: emptyList()
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
        if (getByOwner(playerId).size > 3 && !this.isDav) {
            throw RequestError("最多只可创建3张房间")
        }
        if (host.name.contains("公共") && !this.isDav) {
            throw RequestError("无权创建公共房间")
        }
        if (findByOwnerAndModpack(playerId, host.modpackId) != null) {
            throw RequestError("同一个整合包只能创建一张房间")
        }
        val world = resolveWorld(host.saveWorld, host.worldId, host.modpackId)
        val modpack = ModpackService.getById(host.modpackId) ?: throw RequestError("无此包")
        val version = modpack.getVersion(host.packVer) ?: throw RequestError("无此版本")
        if (version.status != Modpack.Status.OK) {
            throw RequestError("此整合包版本未准备好，请等待构建完成后再创建房间")
        }
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
            MailService.sendSystemMail(playerId, "房间创建中", "${host.name}正在创建中，请稍等几分钟...")._id
        dbcl.insertOne(host)
        startCreateHost(host, modpack, version, mailId)


    }

    private fun startCreateHost(
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

                    clearShutFlag(host._id)
                }.onFailure {
                    lgr.error { it }
                    it.printStackTrace()
                    MailService.changeMail(mailId, failureTitle, newContent = "无法创建房间，错误：${it}")
                    throw it
                }.onSuccess {
                    MailService.changeMail(mailId, successTitle, newContent = successContent)
                }
            },
            dedupeKey = createHostTaskKey(host._id)
        )
    }

    private suspend fun Host.makeContainer(
        worldId: ObjectId?,
        modpack: Modpack,
        version: Modpack.Version
    ) {
        DockerService.deleteContainer(_id.str)
        ensureWorkdirQuota()

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
            //装入mod
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
                    if (source == null) {
                        throw RequestError("房间附加Mod文件缺失:${mod.slug} 请重新上传")
                    }
                    this += Mount()
                        .withType(MountType.BIND)
                        .withSource(source.absolutePath)
                        .withTarget("/opt/server/mods/${mod.fileName}")
                }
            // 注释掉GTNH缺失support mod时的自动补装逻辑，改为仅使用整合包自身与显式extra mods。
            // val mountedSupportSlugs = mutableSetOf<String>()
            // extractedModsDir.listFiles()
            //     ?.asSequence()
            //     ?.mapNotNull(Lwjgl3ifyServerSupport::supportSlug)
            //     ?.forEach(mountedSupportSlugs::add)
            // lwjgl3ifyRuntime?.supportMods?.forEach { supportJar ->
            //     val supportSlug = Lwjgl3ifyServerSupport.supportSlug(supportJar) ?: supportJar.nameWithoutExtension.lowercase()
            //     if (supportSlug !in mountedSupportSlugs) {
            //         this += Mount()
            //             .withType(MountType.BIND)
            //             .withSource(supportJar.absolutePath)
            //             .withTarget("/opt/server/mods/${supportJar.name}")
            //     }
            // }
            //1.16.5以下装入核心
            if (listOf(McVersion.V165,McVersion.V122 ,McVersion.V071).any{it == modpack.mcVer}) {
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
                //使用存档
                this += Mount()
                    .withType(MountType.BIND)
                    .withSource(WorldService.getLevelDir(worldId).absolutePath)
                    .withTarget("/opt/server/world")
            } else {
                //不存档
                this += Mount()
                    .withType(MountType.TMPFS)
                    .withTarget("/opt/server/world")
                    .withTmpfsOptions(TmpfsOptions().withSizeBytes(512 * 1024 * 1024))
            }
        }
        val image = if (lwjgl3ifyRuntime != null) "rdi:j25" else "rdi:j${modpack.mcVer.jreSupport}"
        modpack.mcVer.loaderVersions[modpack.modloader]?.let { modLoaderVersion ->
            DockerService.createContainer(
                port,
                this._id.str,
                mounts,
                image,
                containerEnv(modpack.mcVer, modLoaderVersion, lwjgl3ifyRuntime)
            )
        } ?: throw RequestError("不支持的mod加载器")
    }

    suspend fun HostContext.delete(payload: Host.DeleteDto = Host.DeleteDto()) {
        if (host.status == HostStatus.PLAYABLE) {
            graceStop()
        }
        val worldIdToDelete = host.worldId.takeIf { payload.deleteWorld }
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
        worldIdToDelete?.let { WorldService.delete(player._id, it) }
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
            "房间整合包切换中",
            "你的房间《${host.name}》正在切换到整合包版本 $resolvedVer ，请稍等几分钟..."
        )._id

        DockerService.deleteContainer(hostIdStr)
        clearShutFlag(host._id)
        dbcl.updateOne(
            eq("_id", host._id), combine(
                set(Host::packVer.name, resolvedVer),
            )
        )
        host.packVer = resolvedVer
        startCreateHost(
            host = host,
            modpack = modpack,
            version = modpackVer,
            mailId = mailId,
            runningTitle = "房间整合包切换中",
            successTitle = "房间整合包版本切换完成",
            successContent = "好了",
            failureTitle = "房间整合包版本切换失败"
        )
    }

    suspend fun HostContext.changeOptions(payload: Host.OptionsDto) {

        if (payload.packVer != null && payload.modpackId == null) {
            changeVersion(payload.packVer)
        }
        val updates = mutableListOf<Bson>()
        payload.gameRules?.let { rules ->
            updates += set(Host::gameRules.name, rules)
            if(host.playable){
                rules.forEach { (k, v) -> sendCommand("gamerule $k $v") }
            }
        }
        payload.name?.let {
            it.validateName()
            updates += set(Host::name.name, it)
        }
        payload.packVer?.let { updates += set(Host::packVer.name, it) }
        payload.difficulty?.let {
            updates += set(Host::difficulty.name, it)
            if(host.playable){
                val modeStr = getDifficultyText(it)
                sendCommand("difficulty ${modeStr}")
            }
        }
        payload.gameMode?.let {
            updates += set(Host::gameMode.name, it)
            if(host.playable){
                val modeStr = getGameModeText(it)
                sendCommand("defaultgamemode ${modeStr}")
                sendCommand("gamemode ${modeStr} @a")
            }
        }
        payload.levelType?.takeIf { it.isNotBlank() }?.let { updates += set(Host::levelType.name, it) }
        payload.whitelist?.let { updates += set(Host::whitelist.name, it) }
        payload.allowCheats?.let {
            updates += set(Host::allowCheats.name, it)
            if (host.playable)
                sendCommand("${if (it) "op" else "deop"} @a")
        }
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
        val isPublicHost = current.isPublic || !current.whitelist
        if (!isPublicHost && !isMember && !player.isDav) {
            throw RequestError("私有房间仅成员可启动")
        }
        if (DockerService.isStarted(current._id.str)) {
            clearShutFlag(current._id)
            return
        }
        val modpack = ModpackService.getById(current.modpackId) ?: throw RequestError("无此整合包")
        val version = modpack.getVersion(current.packVer) ?: throw RequestError("无此版本")
        DockerService.deleteContainer(current._id.str)
        current.writeServerProperties()
        current.deleteTransientStartupDirs()
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

    suspend fun HostContext.sendCommand(command: String, waitForResponse: Boolean = false): String {
        val hostId = host._id
        val normalized = command.trimEnd()
        if (normalized.isBlank()) throw RequestError("命令不能为空")

        val state = hostStates[hostId] ?: throw RequestError("房间未处于游玩状态")
        val session = state.session ?: throw RequestError("房间未处于游玩状态")
        val requestId = state.nextCommandReqId.getAndIncrement()
        val pendingResponse = if (waitForResponse) CompletableDeferred<String>() else null
        pendingResponse?.let { state.pendingCommands[requestId] = it }

        val message = WsMessage(
            requestId,
            channel = WsMessage.Channel.Command,
            data = normalized
        )

        try {
            session.send(Frame.Text(message.json))
            if (!waitForResponse) return "OK"
            return withTimeout(COMMAND_RESPONSE_TIMEOUT_MS) {
                pendingResponse!!.await()
            }
        } catch (timeout: TimeoutCancellationException) {
            throw RequestError("命令已发送，但${COMMAND_RESPONSE_TIMEOUT_MS / 1000}秒内未收到返回")
        } catch (cancel: KxCancellationException) {
            hostStates[hostId]?.let { state ->
                if (state.session === session) {
                    state.session = null
                    failPendingCommands(state, RequestError("房间连接已断开"))
                    if (state.shutFlag <= 0) {
                        hostStates.remove(hostId, state)
                    }
                }
            }
            throw cancel
        } catch (requestError: RequestError) {
            throw requestError
        } catch (t: Throwable) {
            hostStates[hostId]?.let { state ->
                if (state.session === session) {
                    state.session = null
                    failPendingCommands(state, RequestError("房间连接已断开"))
                    if (state.shutFlag <= 0) {
                        hostStates.remove(hostId, state)
                    }
                }
            }
            lgr.warn { "发送命令到 $hostId 失败: ${t.message + "\n" + t}" }
            throw RequestError("发送命令失败: ${t.message ?: "未知错误"}")
        } finally {
            state.pendingCommands.remove(requestId)
        }
    }

    val Host.status: HostStatus
        get() {
            if(DEBUG && hostStates[_id]?.session != null) return HostStatus.PLAYABLE
            val status = DockerService.getContainerStatus(_id.str)
            if (status == HostStatus.STARTED && hostStates[_id]?.session != null) {
                return HostStatus.PLAYABLE
            }
            return status
        }
    val Host.playable get() = status == HostStatus.PLAYABLE
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
                } catch (t: KxCancellationException) {
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
        } catch (cancel: KxCancellationException) {
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
                    val onlinePlayers =host.getOnlinePlayers()
                    val isMember = host.ownerId == requesterId || host.members.any { it.id == requesterId }
                    val playable = when {
                        isMember -> true
                        host.isPublic -> true
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

    suspend fun findByOwnerAndModpack(uid: ObjectId, modpackId: ObjectId): Host? =
        dbcl.find(
            and(
                eq("ownerId", uid),
                eq("modpackId", modpackId)
            )
        ).firstOrNull()


    suspend fun getByPort(port: Int): Host? = dbcl.find(eq("port", port)).firstOrNull()

    suspend fun findByWorld(worldId: ObjectId): Host? =
        dbcl.find(eq("worldId", worldId)).firstOrNull()

    suspend fun getById(id: ObjectId): Host? = dbcl.find(eq("_id", id)).firstOrNull()

    private fun Host.configDir(): File = dir.resolve("config")

    private fun File.isEditableConfigFile(): Boolean =
        isFile &&
                !Files.isSymbolicLink(toPath()) &&
                extension.lowercase() in editableConfigExtensions

    private fun Host.resolveConfigFile(relativePath: String): File {
        val normalizedPath = relativePath.trim().replace('\\', '/')
        if (normalizedPath.isBlank()) throw RequestError("配置文件路径不能为空")
        if (normalizedPath.startsWith('/')) throw RequestError("非法配置文件路径")

        val configRoot = configDir().toPath().normalize()
        val target = configRoot.resolve(normalizedPath).normalize()
        if (!target.startsWith(configRoot)) throw RequestError("非法配置文件路径")

        val targetFile = target.toFile()
        if (targetFile.extension.lowercase() !in editableConfigExtensions) {
            throw RequestError("仅支持编辑json toml txt json5 properties文件")
        }
        if (targetFile.exists() && Files.isSymbolicLink(targetFile.toPath())) {
            throw RequestError("不允许编辑软链接配置文件")
        }
        return targetFile
    }

    private fun File.checkConfigFileSize() {
        if (length() > HOST_CONFIG_FILE_MAX_BYTES) {
            throw RequestError("配置文件过大，最大允许1MB")
        }
    }

    private fun File.canListAsConfigFile(): Boolean =
        isEditableConfigFile() && length() <= HOST_CONFIG_FILE_LIST_MAX_BYTES

    suspend fun HostContext.listConfigFiles(): List<Host.ConfigFileEntry> {
        val configDir = host.configDir()
        if (!configDir.exists()) return emptyList()
        if (!configDir.isDirectory) throw RequestError("主机配置目录异常")

        return configDir.walkTopDown()
            .map { file ->
                val relativePath = file.relativeTo(configDir).invariantSeparatorsPath
                relativePath to file
            }
            .filterNot { (relativePath, _) -> relativePath.isExcludedConfigPath() }
            .filter { (_, file) -> file.canListAsConfigFile() }
            .map { (relativePath, file) ->
                Host.ConfigFileEntry(
                    path = relativePath,
                    size = file.length(),
                    updateTime = file.lastModified()
                )
            }
            .sortedBy { it.path.lowercase() }
            .toList()
    }

    suspend fun HostContext.readConfigFile(path: String): Host.ConfigFileContentVo {
        val file = host.resolveConfigFile(path)
        if (!file.exists() || !file.isFile) throw RequestError("配置文件不存在")
        if (!file.isEditableConfigFile()) throw RequestError("该文件不支持编辑")
        file.checkConfigFileSize()

        return Host.ConfigFileContentVo(
            path = file.relativeTo(host.configDir()).invariantSeparatorsPath,
            content = file.readText(),
            size = file.length(),
            updateTime = file.lastModified()
        )
    }

    suspend fun HostContext.saveConfigFile(payload: Host.ConfigFileSaveDto): Host.ConfigFileContentVo {
        val file = host.resolveConfigFile(payload.path)
        val bytes = payload.content.toByteArray(Charsets.UTF_8)
        if (bytes.size > HOST_CONFIG_FILE_MAX_BYTES) {
            throw RequestError("配置文件内容过大，最大允许1MB")
        }

        file.parentFile?.mkdirs()
        file.writeText(payload.content)

        return Host.ConfigFileContentVo(
            path = file.relativeTo(host.configDir()).invariantSeparatorsPath,
            content = payload.content,
            size = file.length(),
            updateTime = file.lastModified()
        )
    }

    private data class ResolvedHostFile(
        val path: String,
        val file: File
    )

    private val java.nio.file.Path.invariantSeparatorsPath: String
        get() = toString().replace('\\', '/')

    private fun Host.resolveHostFile(relativePath: String, allowAllowedRoot: Boolean = false): ResolvedHostFile {
        val normalizedPath = relativePath.trim().replace('\\', '/')
        if (normalizedPath.isBlank()) throw RequestError("文件路径不能为空")
        if (normalizedPath.startsWith('/')) throw RequestError("非法文件路径")

        val hostRoot = dir.toPath().toAbsolutePath().normalize()
        val target = hostRoot.resolve(normalizedPath).normalize()
        if (!target.startsWith(hostRoot)) throw RequestError("非法文件路径")

        val resolvedPath = hostRoot.relativize(target).invariantSeparatorsPath
        val rootDir = resolvedPath.substringBefore('/')
        if (rootDir !in allowFileOprDir) throw RequestError("只能操作${allowFileOprDir.joinToString()}目录")
        if (!allowAllowedRoot && resolvedPath == rootDir) throw RequestError("不能直接操作目录根")

        checkNoHostFileSymlink(hostRoot, target)
        return ResolvedHostFile(resolvedPath, target.toFile())
    }

    private fun checkNoHostFileSymlink(hostRoot: java.nio.file.Path, target: java.nio.file.Path) {
        var current = hostRoot
        for (segment in hostRoot.relativize(target)) {
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) throw RequestError("不允许操作软链接文件")
        }
    }

    private fun File.toHostFileEntry(root: File): Host.FileEntry {
        val absoluteRoot = root.absoluteFile
        val absoluteFile = this.absoluteFile
        return Host.FileEntry(
            path = absoluteFile.relativeTo(absoluteRoot).invariantSeparatorsPath,
            name = name,
            directory = isDirectory,
            size = if (isDirectory) 0 else length(),
            updateTime = lastModified()
        )
    }

    suspend fun HostContext.listHostFiles(path: String): List<Host.FileEntry> {
        if (path.isBlank()) {
            return allowFileOprDir.sorted().map { dirName ->
                val file = host.dir.resolve(dirName)
                Host.FileEntry(
                    path = dirName,
                    name = dirName,
                    directory = true,
                    size = 0,
                    updateTime = file.takeIf(File::exists)?.lastModified() ?: 0L
                )
            }
        }

        val resolved = host.resolveHostFile(path, allowAllowedRoot = true)
        if (resolved.path.startsWith("tacz/")) throw RequestError("TaCZ枪包不允许子目录操作")
        val dir = resolved.file
        if (!dir.exists()) return emptyList()
        if (!dir.isDirectory) throw RequestError("目标不是目录")

        val hostRoot = host.dir.absoluteFile
        return dir.listFiles()
            ?.asSequence()
            ?.filterNot { Files.isSymbolicLink(it.toPath()) }
            ?.filterNot { it.name.startsWith(".rdi-upload-") }
            ?.filter { resolved.path != "tacz" || (it.isFile && it.extension.equals("zip", ignoreCase = true)) }
            ?.map { it.toHostFileEntry(hostRoot) }
            ?.sortedWith(compareBy<Host.FileEntry> { !it.directory }.thenBy { it.name.lowercase() })
            ?.toList()
            ?: emptyList()
    }

    suspend fun HostContext.uploadHostFile(call: ApplicationCall): Host.FileUploadVo {
        val multipart = call.receiveMultipart(formFieldLimit = TACZ_FILE_MAX_BYTES)
        var targetPath = call.request.queryParameters["path"]
        var uploadTemp: File? = null
        var uploadedSize = 0L

        fun uploadLimit(): Long =
            if (targetPath?.trim()?.replace('\\', '/')?.startsWith("tacz/") == true) {
                TACZ_FILE_MAX_BYTES
            } else {
                HOST_FILE_MAX_BYTES
            }

        try {
            while (true) {
                val part = multipart.readPart() ?: break
                try {
                    when (part) {
                        is PartData.FormItem -> if (part.name == "path") {
                            targetPath = part.value
                        }

                        is PartData.FileItem -> if (part.name == "file") {
                            uploadTemp?.delete()
                            uploadTemp = host.createHostUploadTempFile()
                            uploadedSize = receiveHostUploadFile(part.provider(), uploadTemp!!, uploadLimit())
                        }

                        is PartData.BinaryItem -> if (part.name == "file") {
                            uploadTemp?.delete()
                            uploadTemp = host.createHostUploadTempFile()
                            uploadedSize = receiveHostUploadFile(part.provider(), uploadTemp!!, uploadLimit())
                        }

                        else -> {}
                    }
                } finally {
                    part.dispose()
                }
            }

            val normalizedTargetPath = targetPath
                ?.takeIf { it.isNotBlank() }
                ?.normalizeHostUploadTargetPath()
                ?: throw ParamError("缺少路径")
            val tempFile = uploadTemp ?: throw ParamError("缺少文件")
            val resolved = host.resolveHostFile(normalizedTargetPath)
            val target = resolved.file
            if (target.exists() && target.isDirectory) throw RequestError("目标是目录")
            host.validateHostUploadTarget(resolved.path, target, tempFile)
            target.parentFile?.let { parent ->
                if (parent.exists() && !parent.isDirectory) throw RequestError("目标目录异常")
                parent.mkdirs()
            }

            host.resolveHostFile(normalizedTargetPath)
            host.checkHostFileQuota(tempFile, target, uploadedSize)
            moveHostUploadFile(tempFile, target)
            uploadTemp = null

            lgr.info { "Host ${host._id} 用户${player._id}上传房间文件 ${resolved.path} ${uploadedSize.humanFileSize}" }
            return Host.FileUploadVo(
                path = resolved.path,
                size = target.length(),
                updateTime = target.lastModified()
            )
        } catch (error: Throwable) {
            uploadTemp?.delete()
            throw error
        }
    }

    private fun String.normalizeHostUploadTargetPath(): String {
        val normalizedPath = trim().replace('\\', '/').trim('/')
        if (normalizedPath.isBlank()) throw RequestError("文件路径不能为空")
        val parent = normalizedPath.substringBeforeLast('/', "")
        val rawName = normalizedPath.substringAfterLast('/').trim()
        val dotIndex = rawName.lastIndexOf('.')
        val rawBaseName = if (dotIndex > 0) rawName.substring(0, dotIndex) else rawName
        val rawExtension = if (dotIndex > 0 && dotIndex < rawName.lastIndex) rawName.substring(dotIndex + 1) else ""
        val cleanBaseName = rawBaseName.filter { it.isAllowedHostUploadFileNameChar() }.ifBlank { "file" }
        val cleanExtension = rawExtension.filter { it.isLetterOrDigit() }
        val cleanName = if (cleanExtension.isBlank()) cleanBaseName else "$cleanBaseName.$cleanExtension"
        return if (parent.isBlank()) cleanName else "$parent/$cleanName"
    }

    private fun Char.isAllowedHostUploadFileNameChar(): Boolean =
        this == '-' || this == '_' || isDigit() || this in 'a'..'z' || this in 'A'..'Z' || this in '\u4e00'..'\u9fff'

    private fun String.isUnderTaczPath(): Boolean =
        this == "tacz" || startsWith("tacz/")

    private fun String.isDirectTaczFilePath(): Boolean {
        if (!startsWith("tacz/")) return false
        val childPath = removePrefix("tacz/")
        return childPath.isNotBlank() && '/' !in childPath
    }

    private fun Host.createHostUploadTempFile(): File {
        dir.mkdirs()
        return Files.createTempFile(dir.toPath(), ".rdi-upload-", ".tmp").toFile()
    }

    private suspend fun receiveHostUploadFile(channel: ByteReadChannel, target: File, maxBytes: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        Files.newOutputStream(
            target.toPath(),
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        ).use { output ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) continue
                total += read
                if (total > maxBytes) throw RequestError("文件过大，最大允许${maxBytes.toHostUploadLimitText()}")
                output.write(buffer, 0, read)
            }
        }
        return total
    }

    private fun receiveHostUploadFile(source: Source, target: File, maxBytes: Long): Long {
        val bytes = source.buffered().readByteArray()
        if (bytes.size > maxBytes) throw RequestError("文件过大，最大允许${maxBytes.toHostUploadLimitText()}")
        target.writeBytes(bytes)
        return bytes.size.toLong()
    }

    private fun Long.toHostUploadLimitText(): String =
        if (this % (1024L * 1024L) == 0L) "${this / 1024L / 1024L}MB" else humanFileSize

    private fun Host.validateHostUploadTarget(path: String, target: File, uploadTemp: File) {
        if (!path.isUnderTaczPath()) return
        if (!path.isDirectTaczFilePath()) {
            throw RequestError("TaCZ枪包只能上传到tacz根目录")
        }
        if (!target.extension.equals("zip", ignoreCase = true)) {
            throw RequestError("TaCZ枪包只允许上传zip文件")
        }
        TaczGunpackValidator.validate(uploadTemp)
            .getOrElse { throw RequestError(it.message ?: "TaCZ枪包格式无效") }
        val taczRoot = dir.resolve("tacz")
        val targetPath = target.toPath().toAbsolutePath().normalize()
        val currentZipCount = taczRoot.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            ?.filter { it.extension.equals("zip", ignoreCase = true) }
            ?.filterNot { it.toPath().toAbsolutePath().normalize() == targetPath }
            ?.count()
            ?: 0
        if (currentZipCount >= TACZ_MAX_ZIP_FILES) {
            throw RequestError("TaCZ枪包最多只能上传${TACZ_MAX_ZIP_FILES}个zip文件")
        }
    }

    private fun Host.checkHostFileQuota(uploadTemp: File, target: File, newSize: Long) {
        val ignoredPaths = setOf(uploadTemp, target)
            .map { it.toPath().toAbsolutePath().normalize() }
            .toSet()
        val totalSize = dir.walkTopDown()
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            .filterNot { it.toPath().toAbsolutePath().normalize() in ignoredPaths }
            .sumOf { it.length() }
        if (totalSize + newSize > HOST_WORKDIR_LIMIT_BYTES) {
            throw RequestError("房间目录超过限制，请删除不必要文件后再上传")
        }
    }

    private fun moveHostUploadFile(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    suspend fun HostContext.deleteHostFile(payload: Host.FileDeleteDto) {
        val resolved = host.resolveHostFile(payload.path)
        val target = resolved.file
        if (resolved.path.isUnderTaczPath()) {
            if (!resolved.path.isDirectTaczFilePath()) throw RequestError("TaCZ枪包不允许子目录操作")
            if (target.isDirectory) throw RequestError("TaCZ枪包不允许目录操作")
        }
        if (!target.exists()) throw RequestError("文件不存在")
        if (target.isDirectory && (target.list()?.isNotEmpty() == true)) throw RequestError("目录不为空")

        Files.deleteIfExists(target.toPath())
        lgr.info { "Host ${host._id} 用户${player._id}删除房间文件 ${resolved.path}" }
    }

    private fun modIdentity(mod: Mod): String {
        return buildString {
            append(mod.platform)
            append(':')
            append(mod.projectId)
            append(':')
            append(mod.fileId)
            append(':')
            append(mod.hash)
            append(':')
            append(mod.slug)
        }
    }

    private fun isServerInstalledMod(mod: Mod): Boolean =
        mod.side != Mod.Side.CLIENT &&
            mod.side != Mod.Side.UNKNOWN &&
            !mod.fileName.startsWith(CLIENT_ONLY_MARK_PREFIX)

    private fun Host.isDisabledMod(mod: Mod): Boolean =
        disabledMods.any { sameMod(it, mod) }

    private fun List<Mod>.distinctBySameMod(): List<Mod> = buildList {
        this@distinctBySameMod.forEach { mod ->
            if (none { sameMod(it, mod) }) add(mod)
        }
    }

    private fun Host.effectiveBaseMods(baseVersion: Modpack.Version): List<Mod> =
        baseVersion.mods.filterNot{isDisabledMod(it)}

    private fun projectIdentity(mod: Mod): String = mod.normalizedProjectId

    private fun slugIdentity(mod: Mod): String = mod.normalizedSlug

    private fun modLabel(mod: Mod): String = mod.displaySlugOrProject

    private fun duplicateRequestSlugLabels(mods: List<Mod>): List<String> = mods
        .groupBy(::slugIdentity)
        .filterKeys { it.isNotBlank() }
        .values
        .filter { it.size > 1 }
        .map { modLabel(it.first()) }
        .distinct()

    private fun duplicateExistingSlugLabels(candidateMods: List<Mod>, existingMods: List<Mod>): List<String> {
        val existingSlugs = existingMods.map(::slugIdentity)
            .filter { it.isNotBlank() }
            .toSet()
        return candidateMods
            .filter { slugIdentity(it).isNotBlank() && slugIdentity(it) in existingSlugs }
            .map(::modLabel)
            .distinct()
    }

    private fun String.isValidDownloadUrl(): Boolean {
        return runCatching {
            val uri = URI(this)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    private suspend fun validateExtraMod(mod: Mod) {
        if (mod.projectId.isBlank()) throw RequestError("Mod projectId不能为空")
        if (mod.fileId.isBlank()) throw RequestError("Mod fileId不能为空")
        if (mod.slug.isBlank()) throw RequestError("Mod slug不能为空")

        when (mod.platform.lowercase()) {
            "mr" -> {
                if (mod.downloadUrls.none { it.isValidDownloadUrl() }) {
                    throw RequestError("Modrinth Mod ${mod.slug} 缺少有效下载链接")
                }
                val projects = ModrinthService.getMultipleProjects(listOf(mod.projectId))
                if (projects.isEmpty()) {
                    throw RequestError("Modrinth不存在此项目: ${mod.projectId}")
                }
                val version = runCatching {
                    ModrinthService.mrreq("version/${mod.fileId}").body<ModrinthVersionInfo>()
                }.getOrElse {
                    throw RequestError("Modrinth不存在此版本: ${mod.fileId}")
                }
                if (version.projectId != mod.projectId) {
                    throw RequestError("Modrinth版本${mod.fileId}不属于项目${mod.projectId}")
                }
            }

            "cf" -> {
                val projectId = mod.projectId.toIntOrNull()
                    ?: throw RequestError("CurseForge projectId无效: ${mod.projectId}")
                val fileId = mod.fileId.toIntOrNull()
                    ?: throw RequestError("CurseForge fileId无效: ${mod.fileId}")
                val project = CurseForgeService.getModsInfo(listOf(projectId)).firstOrNull { it.id == projectId }
                    ?: throw RequestError("CurseForge不存在此项目: ${mod.projectId}")
                val fileInfo = CurseForgeService.getModFileInfo(projectId, fileId)
                    ?: throw RequestError("CurseForge不存在此文件: ${mod.fileId}")
                if (fileInfo.realDownloadUrl.isBlank() || !fileInfo.realDownloadUrl.isValidDownloadUrl()) {
                    throw RequestError("CurseForge Mod ${project.slug} 缺少有效下载链接")
                }
            }

            "github" -> {
                if (mod.hash.isBlank()) {
                    throw RequestError("GitHub Mod ${mod.slug} SHA1不能为空")
                }
                if (mod.downloadUrls.none { it.isValidDownloadUrl() }) {
                    throw RequestError("GitHub Mod ${mod.slug} 缺少有效下载链接")
                }
            }

            else -> throw RequestError("不支持的Mod平台: ${mod.platform}")
        }
    }

    suspend fun HostContext.addExtraMods(mods: List<Mod>) {
        if (mods.isEmpty()) throw RequestError("附加Mod列表不能为空")
        val duplicateRequestIds = mods.groupBy(::projectIdentity)
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateRequestIds.isNotEmpty()) {
            throw RequestError("请求中包含重复Mod项目: ${duplicateRequestIds.joinToString()}")
        }
        val duplicateRequestSlugs = duplicateRequestSlugLabels(mods)
        if (duplicateRequestSlugs.isNotEmpty()) {
            throw RequestError("请求中包含重复Mod名: ${duplicateRequestSlugs.joinToString()}")
        }

        val modpack = ModpackService.getById(host.modpackId) ?: throw RequestError("无此整合包")
        val baseVersion = modpack.getVersion(host.packVer) ?: throw RequestError("无此整合包版本: ${host.packVer}")
        val activeBaseMods = host.effectiveBaseMods(baseVersion)
        val existingProjectIds = (host.extraMods + activeBaseMods).map(::projectIdentity).toSet()
        val duplicateExistingIds = mods.map(::projectIdentity).filter { it in existingProjectIds }
        if (duplicateExistingIds.isNotEmpty()) {
            val duplicateExistingSlugs = mods
                .filter { projectIdentity(it) in duplicateExistingIds }
                .map { it.slug.trim().ifBlank { projectIdentity(it) } }
                .distinct()
            throw RequestError("房间已有这些mod: ${duplicateExistingSlugs.joinToString()}")
        }
        val duplicateExistingSlugs = duplicateExistingSlugLabels(mods, host.extraMods + activeBaseMods)
        if (duplicateExistingSlugs.isNotEmpty()) {
            throw RequestError("房间已有这些同名mod: ${duplicateExistingSlugs.joinToString()}")
        }

        val mailId = MailService.sendSystemMail(
            player._id,
            "附加Mod添加中",
            "开始为房间${host.name}添加${mods.size}个附加Mod"
        )._id
        enqueueAddExtraMods(host._id, host.name, host.modpackId, host.packVer, mods, mailId)
    }

    suspend fun HostContext.addDisabledMods(mods: List<Mod>): List<Mod> {
        if (mods.isEmpty()) throw RequestError("禁用Mod列表不能为空")
        val modpack = ModpackService.getById(host.modpackId) ?: throw RequestError("无此整合包")
        val baseVersion = modpack.getVersion(host.packVer) ?: throw RequestError("无此整合包版本: ${host.packVer}")
        val matchedMods = mods.map { requestMod ->
            baseVersion.mods.firstOrNull { sameMod(it, requestMod) }
                ?: throw RequestError("整合包未安装此Mod: ${requestMod.displaySlugOrProject}")
        }.distinctBySameMod()
        val alreadyDisabled = matchedMods.filter { matched ->
            host.disabledMods.any { sameMod(it, matched) }
        }
        if (alreadyDisabled.isNotEmpty()) {
            throw RequestError(
                "这些Mod已被禁用: ${alreadyDisabled.map { it.displaySlugOrProject }.distinct().joinToString()}"
            )
        }
        val updatedMods = (host.disabledMods + matchedMods).distinctBySameMod()
        dbcl.updateOne(
            eq("_id", host._id),
            set(Host::disabledMods.name, updatedMods)
        )
        host.disabledMods = updatedMods
        deleteMountedDisabledModFiles(host, matchedMods)
        return updatedMods
    }

    private fun enqueueAddExtraMods(
        hostId: ObjectId,
        hostName: String,
        modpackId: ObjectId,
        packVer: String,
        mods: List<Mod>,
        mailId: ObjectId
    ) {
        ServerTaskManager.submit(
            task = Task2.Leaf("为房间添加附加Mod $hostName") { ctx ->
                runCatching {
                    MailService.changeMail(mailId, newContent = "开始校验Mod信息")
                    ctx.emit(LoadProgress.Phase("开始校验Mod信息"))
                    val currentHost = getById(hostId) ?: throw RequestError("无此房间")
                    val modpack = ModpackService.getById(modpackId) ?: throw RequestError("无此整合包")
                    val baseVersion = modpack.getVersion(packVer) ?: throw RequestError("无此整合包版本: $packVer")
                    mods.forEachIndexed { index, mod ->
                        validateExtraMod(mod)
                        val message = "已校验 ${index + 1}/${mods.size}: ${mod.slug}"
                        MailService.changeMail(mailId, newContent = message)
                        ctx.emit(
                            LoadProgress.Percent(
                                message,
                                (index + 1).toFloat() / mods.size.coerceAtLeast(1)
                            )
                        )
                    }

                    val activeBaseMods = currentHost.effectiveBaseMods(baseVersion)
                    val existingProjectIds = (currentHost.extraMods + activeBaseMods).map(::projectIdentity).toSet()
                    val duplicateExistingMods = mods.filter { projectIdentity(it) in existingProjectIds }
                    if (duplicateExistingMods.isNotEmpty()) {
                        val duplicateSlugs = duplicateExistingMods
                            .map { it.slug.trim().ifBlank { projectIdentity(it) } }
                            .distinct()
                        throw RequestError("主机已有这些mod: ${duplicateSlugs.joinToString()}")
                    }
                    val duplicateExistingSlugMods =
                        duplicateExistingSlugLabels(mods, currentHost.extraMods + activeBaseMods)
                    if (duplicateExistingSlugMods.isNotEmpty()) {
                        throw RequestError("主机已有这些同slug mod: ${duplicateExistingSlugMods.joinToString()}")
                    }

                    ModService.downloadModsTask2(mods).runInline(
                        Task2Context { progress ->
                            val percentText = progress.fraction?.let { fraction ->
                                " ${(fraction.coerceIn(0f, 1f) * 100).toInt()}%"
                            }.orEmpty()
                            MailService.changeMail(mailId, newContent = "${progress.message}$percentText")
                            ctx.emit(progress)
                        }
                    )

                    val latestHost = getById(hostId) ?: throw RequestError("无此房间")
                    val latestModpack = ModpackService.getById(latestHost.modpackId) ?: throw RequestError("无此整合包")
                    val latestBaseVersion = latestModpack.getVersion(latestHost.packVer)
                        ?: throw RequestError("无此整合包版本: ${latestHost.packVer}")
                    val latestActiveBaseMods = latestHost.effectiveBaseMods(latestBaseVersion)
                    val latestExistingProjectIds =
                        (latestHost.extraMods + latestActiveBaseMods).map(::projectIdentity).toSet()
                    val latestExistingSlugs = (latestHost.extraMods + latestActiveBaseMods)
                        .map(::slugIdentity)
                        .filter { it.isNotBlank() }
                        .toSet()
                    val modsToAppend = mods.filterNot {
                        projectIdentity(it) in latestExistingProjectIds ||
                                (slugIdentity(it).isNotBlank() && slugIdentity(it) in latestExistingSlugs)
                    }
                    if (modsToAppend.isEmpty()) {
                        throw RequestError("这些mod在任务执行期间已被添加到主机")
                    }

                    val updatedMods = (latestHost.extraMods + modsToAppend)
                        .distinctBy(::modIdentity)
                        .toList()
                    dbcl.updateOne(
                        eq("_id", hostId),
                        set(Host::extraMods.name, updatedMods)
                    )
                    val skippedCount = mods.size - modsToAppend.size
                    MailService.changeMail(
                        mailId,
                        newTitle = "主机附加Mod添加完成",
                        newContent = buildString {
                            append("已为房间")
                            append(hostName)
                            append("添加")
                            append(modsToAppend.size)
                            append("个附加Mod")
                            if (skippedCount > 0) {
                                append("，另有")
                                append(skippedCount)
                                append("个mod因执行期间已存在而跳过")
                            }
                        }
                    )
                }.onFailure { error ->
                    lgr.error { "添加主机附加Mod失败 host=$hostId\n$error" }
                    MailService.changeMail(
                        mailId,
                        newTitle = "主机附加Mod添加失败",
                        newContent = "无法为房间${hostName}添加附加Mod: ${error.message ?: error}"
                    )
                    throw error
                }
            },
            dedupeKey = addExtraModsTaskKey(hostId, mods)
        )
    }

    suspend fun HostContext.deleteExtraMods(projectIds: List<String>): List<Mod> {
        val normalizedProjectIds = projectIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (normalizedProjectIds.isEmpty()) throw RequestError("projectIds不能为空")

        val removedMods = host.extraMods
            .filter { projectIdentity(it) in normalizedProjectIds }
            .toList()
        val foundProjectIds = removedMods.map(::projectIdentity).toSet()
        val missingProjectIds = normalizedProjectIds - foundProjectIds
        if (missingProjectIds.isNotEmpty()) {
            throw RequestError("这些Mod不在附加Mod列表中: ${missingProjectIds.joinToString()}")
        }
        val updatedMods = host.extraMods
            .filterNot { projectIdentity(it) in normalizedProjectIds }
            .toList()
        dbcl.updateOne(
            eq("_id", host._id),
            set(Host::extraMods.name, updatedMods)
        )
        removedMods.forEach { mod ->
            mod.fileNames.forEach { fileName ->
                val hostModPath = host.dir.resolve("mods").resolve(fileName).toPath()
                runCatching { Files.deleteIfExists(hostModPath) }
                    .onSuccess { deleted ->
                        if (deleted) {
                            lgr.info { "Host ${host._id} 删除附加Mod文件: ${hostModPath.fileName}" }
                        }
                    }
                    .onFailure { err ->
                        lgr.warn { "Host ${host._id} 删除附加Mod文件失败 ${hostModPath.fileName}: ${err.message}" }
                    }
            }
        }
        host.extraMods = updatedMods
        return updatedMods
    }

    suspend fun HostContext.deleteDisabledMods(mods: List<Mod>): List<Mod> {
        if (mods.isEmpty()) throw RequestError("禁用Mod列表不能为空")
        val modsToReEnable = mods.filter { requestMod ->
            host.disabledMods.any { sameMod(it, requestMod) }
        }.distinctBySameMod()
        if (modsToReEnable.isEmpty()) {
            throw RequestError("这些Mod未被禁用")
        }
        val conflictWithExtra = modsToReEnable.filter { reenableMod ->
            host.extraMods.any { sameMod(it, reenableMod) }
        }
        if (conflictWithExtra.isNotEmpty()) {
            throw RequestError(
                "这些Mod已在附加Mod中存在，请先移除附加Mod再重新启用: ${
                    conflictWithExtra.map { it.displaySlugOrProject }.distinct().joinToString()
                }"
            )
        }
        val updatedMods = host.disabledMods
            .filterNot { disabledMod -> modsToReEnable.any { sameMod(it, disabledMod) } }
            .toList()
        dbcl.updateOne(
            eq("_id", host._id),
            set(Host::disabledMods.name, updatedMods)
        )
        host.disabledMods = updatedMods
        return updatedMods
    }

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
            disabledMods = disabledMods,
            onlinePlayerIds = onlinePlayers
        )
    }

    private fun deleteMountedDisabledModFiles(host: Host, disabledMods: List<Mod>) {
        if (disabledMods.isEmpty()) return
        disabledMods.forEach { mod ->
            mod.fileNames.forEach { fileName ->
                val hostModFile = host.dir.resolve("mods").resolve(fileName).toPath()
                runCatching { Files.deleteIfExists(hostModFile) }
                    .onSuccess { deleted ->
                        if (deleted) {
                            lgr.info { "Host ${host._id} 删除已停用Mod占位文件: ${hostModFile.fileName}" }
                        }
                    }
                    .onFailure { err ->
                        lgr.warn { "Host ${host._id} 删除已停用Mod占位文件失败 ${hostModFile.fileName}: ${err.message}" }
                    }
            }
        }
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
                        lgr.warn { "Host ${name} 更新存档大小失败: ${err.message}" }
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
        val current = getById(host._id) ?: throw RequestError("无此房间")
        val recipient = targetMember
        if (current.ownerId == recipient.id) throw RequestError("不能转给自己")
        if (!getTargetPlayer().hasMsid) throw RequestError("找不到对方的微软账号")
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
        val target = PlayerService.getByQQ(qq) ?: throw RequestError("无此账号")
        withMemberMutationLocks("host:${host._id}", "player:${target._id}") {
            val current = getById(host._id) ?: throw RequestError("无此房间")
            if (current.hasMember(target._id)) {
                throw RequestError("该用户已是成员")
            }
            if (!current.isPublic && current.members.size >= 10) {
                throw RequestError("该房间最多只能有10名成员")
            }
            val joinedCount = dbcl.countDocuments(eq("${Host::members.name}.${Host.Member::id.name}", target._id))
            if (joinedCount >= 10) {
                throw RequestError("该用户已加入9张房间，无法继续加入")
            }
            dbcl.updateOne(
                eq("_id", current._id),
                Updates.push(Host::members.name, Host.Member(target._id, Role.MEMBER))
            )
        }
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
            throw RequestError("你不是此房间成员")
        }
        if (host.ownerId == player._id) {
            throw RequestError("拥有者无法退出房间")
        }
        dbcl.updateOne(
            eq("_id", host._id),
            Updates.pull(Host::members.name, Document(Host.Member::id.name, player._id))
        )
    }
}


fun getGameModeText(modeId: Int): String {
    return when (modeId) {
        0 -> "survival"
        1 -> "creative"
        2 -> "adventure"
        else -> "survival"
    }
}
fun getDifficultyText(diffId: Int): String {
    return when (diffId) {
        0 -> "peaceful"
        1 -> "easy"
        2 -> "normal"
        3 -> "hard"
        else -> "normal"
    }
}
