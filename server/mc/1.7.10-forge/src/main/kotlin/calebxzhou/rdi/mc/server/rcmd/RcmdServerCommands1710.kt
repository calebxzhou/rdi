package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.firmsection.FirmSectionKey
import calebxzhou.rdi.mc.firmsection.FirmSectionLimits
import calebxzhou.rdi.mc.firmsection.FirmSectionSetStatus
import calebxzhou.rdi.mc.rcmd.chat.ChatRange
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState
import calebxzhou.rdi.mc.rcmd.home.HomeResult
import calebxzhou.rdi.mc.rcmd.home.HomeService
import calebxzhou.rdi.mc.rcmd.tpa.TpaResult
import calebxzhou.rdi.mc.rcmd.tpa.TpaService
import calebxzhou.rdi.mc.rcmd.*
import calebxzhou.rdi.mc.rcmd.RcmdResult.Companion.error
import calebxzhou.rdi.mc.rcmd.RcmdResult.Companion.ok
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService1710
import calebxzhou.rdi.mc.server.home.HomePlayer1710
import calebxzhou.rdi.mc.server.network.RServerNetwork
import calebxzhou.rdi.mc.server.tpa.TpaPlayer1710
import calebxzhou.rdi.mc.server.tpa.TpaPlayerLookup1710
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.dedicated.DedicatedServer
import java.util.Locale
import java.util.UUID

object RcmdServerCommands1710 : RcmdServerCommandHandler {
    private val DISPATCHER = RcmdDispatcher()
    private var server: DedicatedServer? = null

    init {
        RcmdCommonServerCommands.register(DISPATCHER, this, false)
    }

    @JvmStatic
    fun init(dedicatedServer: DedicatedServer) {
        server = dedicatedServer
    }

    @JvmStatic
    fun dispatcher(): RcmdDispatcher {
        return DISPATCHER
    }

    @JvmStatic
    fun reply(source: RcmdSource, result: RcmdResult?) {
        if (result == null || result.message().isEmpty()) {
            return
        }
        for (message in result.message().split("\\R".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (!message.isEmpty()) {
                if (result.success()) {
                    source.sendFeedback(message)
                } else {
                    source.sendError(message)
                }
            }
        }
    }

    @JvmStatic
    fun getChatRange(playerId: UUID): String {
        return PlayerChatRangeState.get(playerId)?.name?.lowercase(Locale.ROOT) ?: ChatRange.GLOBAL.displayName
    }

    override fun ping(context: RcmdContext): RcmdResult {
        return ok("pong，rcmd正常")
    }

    override fun setChatRange(context: RcmdContext): RcmdResult {
        val source = context.source
        if (!source.isPlayer) {
            return error("此rcmd命令只能由玩家执行")
        }
        val chatRange = ChatRange.fromRcmdValue(context.getString("range"))
        PlayerChatRangeState.set(source.playerId(), chatRange)
        return ok("聊天范围已切换为" + chatRange.displayName)
    }

    override fun requestTpa(context: RcmdContext): RcmdResult {
        val requester = playerOrNull(context.source)
        if (requester == null) {
            return error("此rcmd命令只能由玩家执行")
        }
        val dedicatedServer = server ?: return error("服务器尚未初始化")
        val result =
            TpaService.request(TpaPlayer1710(requester), context.getString("playerName"), TpaPlayerLookup1710(dedicatedServer))
        return toRcmdResult(result)
    }

    override fun acceptTpa(context: RcmdContext): RcmdResult {
        val target = playerOrNull(context.source)
        if (target == null) {
            return error("此rcmd命令只能由玩家执行")
        }
        val dedicatedServer = server ?: return error("服务器尚未初始化")
        val result = TpaService.accept(TpaPlayer1710(target), TpaPlayerLookup1710(dedicatedServer))
        return toRcmdResult(result)
    }

    override fun setHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
        if (player == null) {
            return error("此rcmd命令只能由玩家执行")
        }
        val dedicatedServer = server ?: return error("服务器尚未初始化")
        val result = HomeService.setHome(HomePlayer1710(dedicatedServer, player), context.getString("name"))
        return toRcmdResult(result)
    }

    override fun goHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
        if (player == null) {
            return error("此rcmd命令只能由玩家执行")
        }
        val dedicatedServer = server ?: return error("服务器尚未初始化")
        val result = HomeService.goHome(HomePlayer1710(dedicatedServer, player), context.getString("name"))
        return toRcmdResult(result)
    }

    override fun listHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
        if (player == null) {
            return error("此rcmd命令只能由玩家执行")
        }
        val dedicatedServer = server ?: return error("服务器尚未初始化")
        val result = HomeService.listHomes(HomePlayer1710(dedicatedServer, player))
        return toRcmdResult(result)
    }

    override fun deleteHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
        if (player == null) {
            return error("此rcmd命令只能由玩家执行")
        }
        val dedicatedServer = server ?: return error("服务器尚未初始化")
        val result = HomeService.deleteHome(HomePlayer1710(dedicatedServer, player), context.getString("name"))
        return toRcmdResult(result)
    }

    override fun togglePosLock(context: RcmdContext): RcmdResult {
        return error("1.7.10暂不支持位置锁定")
    }

    override fun setFirmSection(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService1710.set(player)
        val label = firmSectionLabel(result.key)
        return when (result.status) {
            FirmSectionSetStatus.ADDED -> {
                val dedicatedServer = server
                if (dedicatedServer != null) {
                    RServerNetwork.sendFirmSectionsToAll(dedicatedServer)
                }
                ok("已持久当前子区块：$label ${firmSectionCountLabel(result.playerCount, result.total)}")
            }

            FirmSectionSetStatus.ALREADY_PRESENT ->
                ok("当前子区块已经持久了")

            FirmSectionSetStatus.OCCUPIED_BY_OTHER ->
                error("当前子区块已被其他玩家持久了")

            FirmSectionSetStatus.PLAYER_LIMIT_REACHED ->
                error("你持久的子区块已达到个人上限${FirmSectionLimits.maxPerson}个")

            FirmSectionSetStatus.TOTAL_LIMIT_REACHED ->
                error("持久子区块已达到全世界上限${FirmSectionLimits.maxTotal}个")
        }
    }

    override fun unsetFirmSection(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService1710.unset(player)
        val label = firmSectionLabel(result.key)
        return if (result.removed) {
            val dedicatedServer = server
            if (dedicatedServer != null) {
                RServerNetwork.sendFirmSectionsToAll(dedicatedServer)
            }
            ok("已取消持久当前子区块：$label ${firmSectionCountLabel(result.playerCount, result.total)}")
        } else {
            ok("当前子区块尚未持久")
        }
    }

    override fun listFirmSections(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService1710.list(player)
        if (result.sections.isEmpty()) {
            return ok("你还没有持久子区块。${firmSectionCountLabel(result.playerCount, result.total)}")
        }
        val lines = mutableListOf("持久子区块数量：${firmSectionCountLabel(result.playerCount, result.total)}")
        result.sections.groupBy { it.dimensionId }.forEach { (dimensionId, sections) ->
            lines += "$dimensionId : ${sections.map { firmSectionPositionLabel(it) }}"
        }
        return ok(lines.joinToString("\n"))
    }

    override fun setFirmSectionAutoSet(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return error("此rcmd命令只能由玩家执行")
        val enabled = context.getBool("enabled")
        FirmSectionService1710.setAutoSetEnabled(player, enabled)
        return ok("放置方块实体时自动设置持久子区块已${if (enabled) "开启" else "关闭"}")
    }

    override fun testEntity(context: RcmdContext): RcmdResult {
        return error("1.7.10暂不支持testentity")
    }

    private fun toRcmdResult(result: TpaResult): RcmdResult {
        return if (result.success) ok(result.message) else error(result.message)
    }

    private fun toRcmdResult(result: HomeResult): RcmdResult {
        return if (result.success) ok(result.message) else error(result.message)
    }

    private fun playerOrNull(source: RcmdSource): EntityPlayerMP? {
        if (source is RcmdServerSource1710) {
            return source.player
        }
        return null
    }

    private fun firmSectionLabel(key: FirmSectionKey): String =
        "${key.dimensionId},${key.chunkX},${key.sectionY},${key.chunkZ}"

    private fun firmSectionPositionLabel(key: FirmSectionKey): String =
        "${key.chunkX},${key.sectionY},${key.chunkZ}"

    private fun firmSectionCountLabel(playerCount: Int, total: Int): String =
        if (FirmSectionLimits.maxPerson > 0) {
            "你：${playerCount}/${FirmSectionLimits.maxPerson}，全世界：${total}/${FirmSectionLimits.maxTotal}"
        } else {
            "你：${playerCount}个，全世界：${total}/${FirmSectionLimits.maxTotal}"
        }
}
