package calebxzhou.rdi.master.service.host

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.util.ioScope
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.master.model.RChatMessage
import calebxzhou.rdi.master.model.RGlobalPlayerList
import calebxzhou.rdi.master.model.WsMessage
import calebxzhou.rdi.master.service.ChatService
import calebxzhou.rdi.master.service.DockerService
import calebxzhou.rdi.master.service.host.HostInstallService.refreshWorldSizeAfterStop
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.bson.types.ObjectId
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object HostRuntimeService {
    private val lgr by Loggers

    internal val hostStates = ConcurrentHashMap<ObjectId, HostState>()

    internal data class HostState(
        var shutFlag: Int = 0,
        var session: DefaultWebSocketServerSession? = null,
        var shutdownJob: Job? = null,
        val nextCommandReqId: AtomicInteger = AtomicInteger(0),
        val pendingCommands: ConcurrentHashMap<Int, CompletableDeferred<String>> = ConcurrentHashMap()
    )

    data class ActiveCommandTarget(
        val session: DefaultWebSocketServerSession,
        val requestId: Int
    )

    fun hasSession(hostId: ObjectId): Boolean = hostStates[hostId]?.session != null

    fun commandTarget(hostId: ObjectId, pendingResponse: CompletableDeferred<String>?): ActiveCommandTarget {
        val state = hostStates[hostId] ?: throw RequestError("房间未处于游玩状态")
        val session = state.session ?: throw RequestError("房间未处于游玩状态")
        val requestId = state.nextCommandReqId.getAndIncrement()
        pendingResponse?.let { state.pendingCommands[requestId] = it }
        return ActiveCommandTarget(session, requestId)
    }

    fun removePendingCommand(hostId: ObjectId, requestId: Int) {
        hostStates[hostId]?.pendingCommands?.remove(requestId)
    }

    fun markSessionDisconnected(hostId: ObjectId, session: DefaultWebSocketServerSession) {
        hostStates[hostId]?.let { state ->
            if (state.session === session) {
                state.session = null
                failPendingCommands(state, RequestError("房间连接已断开"))
                if (state.shutFlag <= 0) {
                    hostStates.remove(hostId, state)
                }
            }
        }
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
        lgr.info { "host $hostId gameplay 通道已连接" }
        HostPresenceService.lastGlobalPlayerList?.let { sendGlobalPlayerListToSession(session, it) }
        return true
    }

    fun unregisterPlayableSession(hostId: ObjectId, session: DefaultWebSocketServerSession) {
        hostStates[hostId]?.let { state ->
            if (state.session === session) {
                state.session = null
                failPendingCommands(state, RequestError("房间连接已断开"))
                lgr.info { "Host $hostId gameplay 通道已断开" }
                state.shutdownJob?.cancel()
                state.shutdownJob = HostService.idleMonitorScope.launch {
                    delay(3.seconds)
                    val currentSession = hostStates[hostId]?.session
                    if (currentSession != null) {
                        lgr.info { "Host $hostId 已在断开后重新连接，跳过自动停止" }
                        return@launch
                    }

                    runCatching { DockerService.stop(hostId.str) }
                        .onSuccess {
                            lgr.info { "Host $hostId 容器因通道断开已停止" }
                            HostQueryService.getById(hostId)?.refreshWorldSizeAfterStop(waitForStop = false)
                        }
                        .onFailure { error ->
                            if (error is RequestError && error.message == "早就停了") {
                                lgr.info { "Host $hostId 容器已处于停止状态" }
                            } else {
                                lgr.warn { "Host $hostId 通道断开后停止容器失败: ${error.message + "\n" + error}" }
                            }
                        }
                        .also {
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
                lgr.info { chatMessage.playerName + ": " + chatMessage.content }
                ioScope.launch {
                    runCatching { ChatService.recordGameChat(chatMessage) }
                        .onFailure { error -> lgr.warn { "保存游戏聊天记录失败: ${error.message}" } }
                }
                if (chatMessage.global) {
                    broadcastChatMessage(message.id, chatMessage.copy(sourceHostId = hostId.toHexString()))
                }
            }

            WsMessage.Channel.PlayerList -> {
                lgr.debug { "忽略host $hostId 主动发来的玩家列表消息" }
            }
        }
    }

    fun broadcastChatMessage(id: Int, chatMessage: RChatMessage) {
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

    fun sendGlobalPlayerListToSession(session: DefaultWebSocketServerSession, playerList: RGlobalPlayerList) {
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

    fun broadcastGlobalPlayerList(playerList: RGlobalPlayerList) {
        hostStates.values.forEach { state ->
            val session = state.session ?: return@forEach
            sendGlobalPlayerListToSession(session, playerList)
        }
    }

    internal fun failPendingCommands(state: HostState, cause: Throwable) {
        state.pendingCommands.values.forEach { it.completeExceptionally(cause) }
        state.pendingCommands.clear()
    }

    fun Host.listenCrashOnStart() {
        val triggered = AtomicBoolean(false)
        val listenerHolder = arrayOfNulls<Closeable>(1)
        val closeListener = { runCatching { listenerHolder[0]?.close() } }

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
                            HostService.markSkipWorldSizeUpdate(_id)
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
}
