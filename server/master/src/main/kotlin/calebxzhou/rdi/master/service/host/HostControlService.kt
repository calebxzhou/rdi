package calebxzhou.rdi.master.service.host

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.isDav
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.master.model.WsMessage
import calebxzhou.rdi.master.service.DockerService
import calebxzhou.rdi.master.service.ModpackService
import calebxzhou.rdi.master.service.ModpackService.getVersion
import calebxzhou.rdi.master.service.host.HostContainerService.makeContainer
import calebxzhou.rdi.master.service.host.HostContainerService.requireGtoGuardAgent
import calebxzhou.rdi.master.service.host.HostInstallService.deleteTransientStartupDirs
import calebxzhou.rdi.master.service.host.HostInstallService.refreshWorldSizeAfterStop
import calebxzhou.rdi.master.service.host.HostInstallService.writeServerProperties
import calebxzhou.rdi.master.service.host.HostRuntimeService.listenCrashOnStart
import calebxzhou.rdi.model.Role
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.bson.types.ObjectId
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException as KxCancellationException

object HostControlService {
    private val lgr by Loggers

    fun updateShutFlag(hostId: ObjectId, value: Int) {
        if (value <= 0) {
            clearShutFlag(hostId)
        } else {
            val state = HostRuntimeService.hostStates.computeIfAbsent(hostId) { HostRuntimeService.HostState() }
            state.shutFlag = value
            lgr.info { "upd shut flag $hostId $value" }
        }
    }

    fun clearShutFlag(hostId: ObjectId) {
        HostRuntimeService.hostStates[hostId]?.let { state ->
            state.shutFlag = 0
            state.shutdownJob?.cancel()
            if (state.session == null && state.shutdownJob?.isActive != true) {
                HostRuntimeService.hostStates.remove(hostId, state)
            }
        }
        lgr.debug { "保持在线 $hostId" }
    }

    fun Host.stop(reason: String) {
        runCatching {
            DockerService.stop(_id.str)
            lgr.info { "Stopped host $name ($reason)" }
            refreshWorldSizeAfterStop(waitForStop = false)
        }.onFailure {
            lgr.warn { "Failed to stop host ${name + "\n" + it}: ${it.message}" }
        }
        clearShutFlag(_id)
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
        current.requireGtoGuardAgent(modpack)
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

        val pendingResponse = if (waitForResponse) CompletableDeferred<String>() else null
        val target = HostRuntimeService.commandTarget(hostId, pendingResponse)

        val message = WsMessage(
            target.requestId,
            channel = WsMessage.Channel.Command,
            data = normalized
        )

        try {
            target.session.send(Frame.Text(message.json))
            if (!waitForResponse) return "OK"
            return withTimeout(HostService.COMMAND_RESPONSE_TIMEOUT_MS.milliseconds) {
                pendingResponse!!.await()
            }
        } catch (timeout: TimeoutCancellationException) {
            throw RequestError("命令已发送，但${HostService.COMMAND_RESPONSE_TIMEOUT_MS / 1000}秒内未收到返回")
        } catch (cancel: KxCancellationException) {
            HostRuntimeService.markSessionDisconnected(hostId, target.session)
            throw cancel
        } catch (requestError: RequestError) {
            throw requestError
        } catch (t: Throwable) {
            HostRuntimeService.markSessionDisconnected(hostId, target.session)
            lgr.warn { "发送命令到 $hostId 失败: ${t.message + "\n" + t}" }
            throw RequestError("发送命令失败: ${t.message ?: "未知错误"}")
        } finally {
            HostRuntimeService.removePendingCommand(hostId, target.requestId)
        }
    }

    val Host.status: HostStatus
        get() {
            if (DEBUG && HostRuntimeService.hasSession(_id)) return HostStatus.PLAYABLE
            val status = DockerService.getContainerStatus(_id.str)
            if (status == HostStatus.STARTED && HostRuntimeService.hasSession(_id)) {
                return HostStatus.PLAYABLE
            }
            return status
        }

    val Host.playable get() = status == HostStatus.PLAYABLE
}
