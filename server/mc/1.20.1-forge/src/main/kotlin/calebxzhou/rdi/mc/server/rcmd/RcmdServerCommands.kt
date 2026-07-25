package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.common.RDI
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
import calebxzhou.rdi.mc.server.home.HomePlayer201
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService
import calebxzhou.rdi.mc.server.tpa.TpaPlayer201
import calebxzhou.rdi.mc.server.tpa.TpaPlayerLookup201
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.Locale
import java.util.UUID

@Mod.EventBusSubscriber(modid = "rdi")
object RcmdServerCommands : RcmdServerCommandHandler {
    private val dispatcher = RcmdDispatcher()
    private val posLocks = mutableMapOf<UUID, PosLockState>()
    private const val POS_LOCK_MAX_DISTANCE_SQR = 0.0001
    private const val TEST_ENTITY_COUNT = 65535
    private const val TEST_ENTITY_ITEM_COUNT = 32

    init {
        RcmdCommonServerCommands.register(dispatcher, this, RDI.DEBUG)
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

    @SubscribeEvent
    @JvmStatic
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }
        val player = event.player as? ServerPlayer ?: return
        keepPosLocked(player, posLocks[player.uuid] ?: return)
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        posLocks.remove(player.uuid)?.let { restorePosLockState(player, it) }
    }

    override fun ping(context: RcmdContext): RcmdResult =
        RcmdResult.ok("pong，rcmd正常")

    override fun setChatRange(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val chatRange = ChatRange.fromRcmdValue(context.getString("range"))
        PlayerChatRangeState.set(player.uuid, chatRange, PlayerNbtChatRangeStore(player))
        return RcmdResult.ok("聊天范围已切换为${chatRange.displayName}")
    }

    override fun requestTpa(context: RcmdContext): RcmdResult {
        val requester = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return TpaService.request(
            TpaPlayer201(requester),
            context.getString("playerName"),
            TpaPlayerLookup201(requester.server)
        ).toRcmdResult()
    }

    override fun acceptTpa(context: RcmdContext): RcmdResult {
        val target = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return TpaService.accept(TpaPlayer201(target), TpaPlayerLookup201(target.server)).toRcmdResult()
    }

    override fun setHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return HomeService.setHome(HomePlayer201(player), context.getString("name")).toRcmdResult()
    }

    override fun goHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return HomeService.goHome(HomePlayer201(player), context.getString("name")).toRcmdResult()
    }

    override fun listHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return HomeService.listHomes(HomePlayer201(player)).toRcmdResult()
    }

    override fun deleteHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        return HomeService.deleteHome(HomePlayer201(player), context.getString("name")).toRcmdResult()
    }

    override fun togglePosLock(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        posLocks.remove(player.uuid)?.let {
            restorePosLockState(player, it)
            return RcmdResult.ok("位置锁定已关闭")
        }
        posLocks[player.uuid] = PosLockState.from(player)
        player.setInvulnerable(true)
        player.setInvisible(true)
        stopPlayerMovement(player)
        return RcmdResult.ok("位置锁定已开启")
    }

    override fun setFirmSection(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService.set(player)
        val label = firmSectionLabel(result.key)
        return when (result.status) {
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
    }

    override fun unsetFirmSection(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService.unset(player)
        val label = firmSectionLabel(result.key)
        return if (result.removed) {
            RcmdResult.ok("已取消持久当前子区块：$label ${firmSectionCountLabel(result.playerCount, result.total)}")
        } else {
            RcmdResult.ok("当前子区块尚未持久")
        }
    }

    override fun listFirmSections(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService.list(player)
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
        FirmSectionService.setAutoSetEnabled(player, enabled)
        return RcmdResult.ok("放置方块实体时自动设置持久子区块已${if (enabled) "开启" else "关闭"}")
    }

    override fun testEntity(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source)
            ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        player.server.execute {
            val level = player.serverLevel()
            repeat(TEST_ENTITY_COUNT) {
                level.addFreshEntity(
                    ItemEntity(
                        level,
                        player.x,
                        player.y - 3.0,
                        player.z,
                        ItemStack(Items.DIAMOND_AXE, TEST_ENTITY_ITEM_COUNT)
                    )
                )
            }
        }
        return RcmdResult.ok()
    }

    private fun keepPosLocked(player: ServerPlayer, state: PosLockState) {
        player.setInvulnerable(true)
        player.setInvisible(true)
        stopPlayerMovement(player)
        val targetLevel = player.server.getLevel(state.dimension)
        if (targetLevel == null) {
            restorePosLockState(player, state)
            posLocks.remove(player.uuid)
            return
        }
        val moved = player.position().distanceToSqr(state.x, state.y, state.z) > POS_LOCK_MAX_DISTANCE_SQR
        if (moved || player.level().dimension() != state.dimension) {
            player.teleportTo(targetLevel, state.x, state.y, state.z, state.yaw, state.pitch)
            stopPlayerMovement(player)
        }
    }

    private fun restorePosLockState(player: ServerPlayer, state: PosLockState) {
        player.setInvulnerable(state.wasInvulnerable)
        player.setInvisible(state.wasInvisible)
        stopPlayerMovement(player)
    }

    private fun stopPlayerMovement(player: ServerPlayer) {
        player.deltaMovement = Vec3.ZERO
        player.resetFallDistance()
    }

    private fun playerOrNull(source: RcmdSource): ServerPlayer? = when (source) {
        is RcmdServerSource201 -> source.player
        is RcmdCommandSourceStackSource -> source.player
        else -> null
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

    private fun TpaResult.toRcmdResult(): RcmdResult =
        if (success) RcmdResult.ok(message) else RcmdResult.error(message)

    private fun HomeResult.toRcmdResult(): RcmdResult =
        if (success) RcmdResult.ok(message) else RcmdResult.error(message)

    data class PosLockState(
        val dimension: ResourceKey<Level>,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        val wasInvulnerable: Boolean,
        val wasInvisible: Boolean
    ) {
        companion object {
            fun from(player: ServerPlayer) = PosLockState(
                player.level().dimension(),
                player.x,
                player.y,
                player.z,
                player.yRot,
                player.xRot,
                player.isInvulnerable,
                player.isInvisible
            )
        }
    }
}
