package calebxzhou.rdi.master.service.host

import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.Host.Companion.getDifficultyText
import calebxzhou.rdi.common.model.Host.Companion.getGameModeText
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.common.util.validateName
import calebxzhou.rdi.master.service.DockerService
import calebxzhou.rdi.master.service.MailService
import calebxzhou.rdi.master.service.ModpackService
import calebxzhou.rdi.master.service.WorldService
import calebxzhou.rdi.master.service.host.HostControlService.clearShutFlag
import calebxzhou.rdi.master.service.host.HostControlService.graceStop
import calebxzhou.rdi.master.service.host.HostControlService.playable
import calebxzhou.rdi.master.service.host.HostControlService.sendCommand
import calebxzhou.rdi.master.service.host.HostControlService.status
import calebxzhou.rdi.master.service.host.HostInstallService.startCreateHost
import calebxzhou.rdi.master.service.host.HostInstallService.writeServerProperties
import calebxzhou.rdi.master.service.host.HostQueryService.getById
import calebxzhou.rdi.master.service.host.HostRuntimeService.hostStates
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.launch
import org.bson.conversions.Bson

object HostLifecycleService {
    private val dbcl get() = HostService.dbcl

    suspend fun HostContext.delete(payload: Host.DeleteDto = Host.DeleteDto()) {
        if (host.status == HostStatus.PLAYABLE) {
            graceStop()
        }
        val worldIdToDelete = host.worldId.takeIf { payload.deleteWorld }
        host.dir.deleteRecursivelyNoSymlink()
        host.dir.delete()
        DockerService.deleteContainer(host._id.str)

        hostStates.remove(host._id)?.let { state ->
            state.shutdownJob?.cancel()
            state.session?.launch {
                runCatching { state.session?.close(CloseReason(CloseReason.Codes.NORMAL, "Host deleted")) }
            }
        }
        HostService.skipWorldSizeUpdate.remove(host._id)

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
            if (host.playable) {
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
            if (host.playable) {
                val modeStr = getDifficultyText(it)
                sendCommand("difficulty ${modeStr}")
            }
        }
        payload.gameMode?.let {
            updates += set(Host::gameMode.name, it)
            if (host.playable) {
                val modeStr = getGameModeText(it)
                sendCommand("defaultgamemode ${modeStr}")
                sendCommand("gamemode ${modeStr} @a")
            }
        }
        payload.levelType?.takeIf { it.isNotBlank() }?.let { updates += set(Host::levelType.name, it) }
        payload.whitelist?.let { updates += set(Host::whitelist.name, it) }
        payload.allowCheats?.let {
            updates += set(Host::allowCheats.name, it)
            if (host.playable) {
                sendCommand("${if (it) "op" else "deop"} @a")
            }
        }
        if (updates.isNotEmpty()) {
            val update = if (updates.size == 1) updates.first() else combine(updates)
            dbcl.updateOne(eq("_id", host._id), update)
        }
        getById(host._id)?.writeServerProperties()
    }
}

