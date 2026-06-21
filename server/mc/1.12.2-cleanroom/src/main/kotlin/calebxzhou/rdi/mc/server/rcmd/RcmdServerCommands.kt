package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.firmsection.FirmSectionKey
import calebxzhou.rdi.mc.firmsection.FirmSectionLimits
import calebxzhou.rdi.mc.firmsection.FirmSectionSetStatus
import calebxzhou.rdi.mc.rcmd.RcmdCommonServerCommands
import calebxzhou.rdi.mc.rcmd.RcmdContext
import calebxzhou.rdi.mc.rcmd.RcmdDispatcher
import calebxzhou.rdi.mc.rcmd.RcmdResult
import calebxzhou.rdi.mc.rcmd.RcmdServerCommandHandler
import calebxzhou.rdi.mc.rcmd.RcmdSource
import calebxzhou.rdi.mc.rcmd.chat.ChatRange
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState
import calebxzhou.rdi.mc.rcmd.home.HomeResult
import calebxzhou.rdi.mc.rcmd.home.HomeService
import calebxzhou.rdi.mc.rcmd.tpa.TpaResult
import calebxzhou.rdi.mc.rcmd.tpa.TpaService
import calebxzhou.rdi.mc.server.home.HomePlayer112
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService112
import calebxzhou.rdi.mc.server.tpa.TpaPlayer112
import calebxzhou.rdi.mc.server.tpa.TpaPlayerLookup112
import net.minecraft.entity.player.EntityPlayerMP
import java.util.Locale
import java.util.UUID

object RcmdServerCommands : RcmdServerCommandHandler {
    private val dispatcher = RcmdDispatcher()

    init {
        RcmdCommonServerCommands.register(dispatcher, this, false)
    }

    @JvmStatic
    fun dispatcher(): RcmdDispatcher = dispatcher

    @JvmStatic
    fun reply(source: RcmdSource, result: RcmdResult?) {
        if (result == null || result.message.isEmpty()) {
            return
        }
        result.message
            .lineSequence()
            .filter(String::isNotEmpty)
            .forEach {
                if (result.success) {
                    source.sendFeedback(it)
                } else {
                    source.sendError(it)
                }
            }
    }

    @JvmStatic
    fun getChatRange(playerId: UUID): String =
        PlayerChatRangeState.get(playerId).name.lowercase(Locale.ROOT)

    override fun ping(context: RcmdContext): RcmdResult =
        RcmdResult.ok("pong，rcmd正常")

    override fun setChatRange(context: RcmdContext): RcmdResult {
        val source = context.source
        if (!source.isPlayer) {
            return RcmdResult.error("此rcmd命令只能由玩家执行")
        }
        val chatRange = ChatRange.fromRcmdValue(context.getString("range"))
        PlayerChatRangeState.set(source.playerId(), chatRange)
        return RcmdResult.ok("聊天范围已切换为${chatRange.displayName}")
    }

    override fun requestTpa(context: RcmdContext): RcmdResult {
        val requester = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return TpaService.request(
            TpaPlayer112(requester),
            context.getString("playerName"),
            TpaPlayerLookup112(requester.server)
        ).toRcmdResult()
    }

    override fun acceptTpa(context: RcmdContext): RcmdResult {
        val target = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return TpaService.accept(TpaPlayer112(target), TpaPlayerLookup112(target.server)).toRcmdResult()
    }

    override fun setHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return HomeService.setHome(HomePlayer112(player), context.getString("name")).toRcmdResult()
    }

    override fun goHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return HomeService.goHome(HomePlayer112(player), context.getString("name")).toRcmdResult()
    }

    override fun listHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return HomeService.listHomes(HomePlayer112(player)).toRcmdResult()
    }

    override fun deleteHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return HomeService.deleteHome(HomePlayer112(player), context.getString("name")).toRcmdResult()
    }

    override fun togglePosLock(context: RcmdContext): RcmdResult =
        RcmdResult.error("1.12.2暂不支持位置锁定")

    override fun setFirmSection(context: RcmdContext): RcmdResult =
        playerOrNull(context.source)?.let { player ->
            val result = FirmSectionService112.set(player)
            val label = firmSectionLabel(result.key)
            when (result.status) {
                FirmSectionSetStatus.ADDED ->
                    RcmdResult.ok("已持久当前子区块：$label ${firmSectionCountLabel(result.playerCount, result.total)}")

                FirmSectionSetStatus.ALREADY_PRESENT ->
                    RcmdResult.ok("当前子区块已经持久了")

                FirmSectionSetStatus.OCCUPIED_BY_OTHER ->
                    RcmdResult.error("当前子区块已被其他玩家持久了")

                FirmSectionSetStatus.PLAYER_LIMIT_REACHED ->
                    RcmdResult.error("你持久的子区块已达到个人上限${FirmSectionLimits.maxPerson}个")

                FirmSectionSetStatus.TOTAL_LIMIT_REACHED ->
                    RcmdResult.error("持久子区块已达到全世界上限${FirmSectionLimits.maxTotal}个")
            }
        } ?: RcmdResult.error("此rcmd命令只能由玩家执行")

    override fun unsetFirmSection(context: RcmdContext): RcmdResult =
        playerOrNull(context.source)?.let { player ->
            val result = FirmSectionService112.unset(player)
            val label = firmSectionLabel(result.key)
            if (result.removed) {
                RcmdResult.ok("已取消持久当前子区块：$label ${firmSectionCountLabel(result.playerCount, result.total)}")
            } else {
                RcmdResult.ok("当前子区块尚未持久")
            }
        } ?: RcmdResult.error("此rcmd命令只能由玩家执行")

    override fun listFirmSections(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService112.list(player)
        if (result.sections.isEmpty()) {
            return RcmdResult.ok("你还没有持久子区块。${firmSectionCountLabel(result.playerCount, result.total)}")
        }
        val lines = buildList {
            add("持久子区块数量：${firmSectionCountLabel(result.playerCount, result.total)}")
            result.sections.groupBy { it.dimensionId }.forEach { (dimensionId, sections) ->
                add("$dimensionId : ${sections.map { firmSectionPositionLabel(it) }}")
            }
        }
        return RcmdResult.ok(lines.joinToString("\n"))
    }

    override fun setFirmSectionAutoSet(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val enabled = context.getBool("enabled")
        FirmSectionService112.setAutoSetEnabled(player, enabled)
        return RcmdResult.ok("放置方块实体时自动设置持久子区块已${if (enabled) "开启" else "关闭"}")
    }

    override fun testEntity(context: RcmdContext): RcmdResult =
        RcmdResult.error("1.12.2暂不支持testentity")

    private fun playerOrNull(source: RcmdSource): EntityPlayerMP? = when (source) {
        is RcmdServerSource112 -> source.player
        is RcmdCommandSenderSource112 -> source.player
        else -> null
    }

    private fun TpaResult.toRcmdResult(): RcmdResult =
        if (success) RcmdResult.ok(message) else RcmdResult.error(message)

    private fun HomeResult.toRcmdResult(): RcmdResult =
        if (success) RcmdResult.ok(message) else RcmdResult.error(message)

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
