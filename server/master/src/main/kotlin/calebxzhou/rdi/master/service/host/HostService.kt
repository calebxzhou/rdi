package calebxzhou.rdi.master.service.host

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.master.HOSTS_DIR
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.net.idPathParam
import calebxzhou.rdi.master.net.pathParamNull
import calebxzhou.rdi.master.service.*
import calebxzhou.rdi.master.service.host.HostQueryService.getById
import calebxzhou.rdi.master.service.host.HostRuntimeService.hostStates
import calebxzhou.rdi.model.Role
import com.github.dockerjava.api.exception.NotFoundException
import com.mongodb.client.model.Indexes
import io.ktor.server.application.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException as KxCancellationException

val Host.dir get() = HOSTS_DIR.resolve(_id.str)

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
    internal val lgr by Loggers

    internal val dbcl = DB.getCollection<Host>("host")

    internal val skipWorldSizeUpdate = ConcurrentHashMap.newKeySet<ObjectId>()
    internal val staleCleanupJob: Job

    internal const val PORT_START = 50000
    internal const val PORT_END_EXCLUSIVE = 60000
    internal const val SHUTDOWN_THRESHOLD = 10
    internal const val HOSTS_PER_PAGE = 100
    internal const val HOST_WORKDIR_LIMIT_BYTES: Long = 1L * 1024 * 1024 * 1024
    internal const val ONLINE_PLAYERS_CACHE_TTL_MS = 15_000L
    internal const val COMMAND_RESPONSE_TIMEOUT_MS = 10_000L

    internal fun createHostTaskKey(hostId: ObjectId): String =
        "server-host-create:${hostId.toHexString()}"

    internal val idleMonitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        HostPresenceService.startGlobalPlayerListPoll()
        HostPlayerDataBackupService.start()
    }

    fun shutdown() {
        lgr.info { "Shutting down HostService..." }
        HostPlayerDataBackupService.shutdown()
        HostPresenceService.shutdown()
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

    internal fun cleanupStaleEntries() {
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
        val host = getById(idPathParam("hostId")) ?: throw RequestError("无此房间")
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
                } catch (t: ClosedWriteChannelException) {
                    return
                } catch (t: ChannelWriteException) {
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
            val ignore = t is ClosedWriteChannelException ||
                    t is ChannelWriteException ||
                    t.message?.contains("Cannot write to channel", ignoreCase = true) == true
            if (!ignore) {
                runCatching { session.send(ServerSentEvent(event = "error", data = t.message ?: "unknown")) }
            }
        }
    }

    internal suspend fun sendLatestLogTail(
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


    internal fun markSkipWorldSizeUpdate(hostId: ObjectId) {
        skipWorldSizeUpdate.add(hostId)
    }

    internal fun skipWorldSizeUpdateOnce(hostId: ObjectId): Boolean = skipWorldSizeUpdate.remove(hostId)


}
