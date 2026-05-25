package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.common2.chat.ChatRange
import calebxzhou.rdi.mc.common2.chat.PlayerChatRangeState
import calebxzhou.rdi.mc.common2.home.HomeResult
import calebxzhou.rdi.mc.common2.home.HomeService
import calebxzhou.rdi.mc.common2.tpa.TpaResult
import calebxzhou.rdi.mc.common2.tpa.TpaService
import calebxzhou.rdi.mc.rcmd.*
import calebxzhou.rdi.mc.server.firmsection.FirmSectionKey
import calebxzhou.rdi.mc.server.firmsection.FirmSectionSavedData
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService
import calebxzhou.rdi.mc.server.firmsection.FirmSectionSetStatus
import calebxzhou.rdi.mc.server.home.HomePlayer211
import calebxzhou.rdi.mc.server.tpa.TpaPlayer211
import calebxzhou.rdi.mc.server.tpa.TpaPlayerLookup211
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import java.util.Locale
import java.util.UUID

@EventBusSubscriber(modid = "rdi")
object RcmdServerCommands {
    private val DISPATCHER = RcmdDispatcher()
    private val POS_LOCKS = mutableMapOf<UUID, PosLockState>()
    private const val POS_LOCK_MAX_DISTANCE_SQR = 0.0001

    init {
        DISPATCHER.register(
            RcmdCommandSpec.builder("ping")
                .description("Test rcmd availability")
                .command { RcmdResult.ok("pong，rcmd正常") }
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("chat", "range")
                .description("Set chat range")
                .argument("range", RcmdArgumentTypes.enumOf("host", "global"))
                .command { context ->
                    val range = context.getString("range")
                    val source = context.source
                    if (!source.isPlayer()) {
                        RcmdResult.error("此rcmd命令只能由玩家执行")
                    } else {
                        val chatRange = ChatRange.fromRcmdValue(range)
                        PlayerChatRangeState.set(source.playerId(), chatRange)
                        RcmdResult.ok("聊天范围已切换为" + chatRange.getDisplayName())
                    }
                }
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("tpa")
                .description("Request teleport to another player")
                .argument("playerName", RcmdArgumentTypes.STRING)
                .command(::handleTpa)
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("tpok")
                .description("Accept pending teleport request")
                .command(::handleTpok)
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("sethome")
                .description("Save current player position as a home")
                .argument("name", RcmdArgumentTypes.STRING)
                .command(::handleSetHome)
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("home")
                .description("Teleport player to a saved home")
                .argument("name", RcmdArgumentTypes.STRING)
                .command(::handleHome)
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("listhome")
                .description("List saved player homes")
                .command(::handleListHome)
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("delhome")
                .description("Delete a saved player home")
                .argument("name", RcmdArgumentTypes.STRING)
                .command(::handleDeleteHome)
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("poslock")
                .description("Toggle current player position lock")
                .command(::handlePosLock)
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("firmsection", "set")
                .description("Save current player section")
                .command(::handleFirmSectionSet)
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("firmsection", "unset")
                .description("Forget current player section")
                .command(::handleFirmSectionUnset)
                .build()
        )
        DISPATCHER.register(
            RcmdCommandSpec.builder("firmsection", "list")
                .description("List saved firm sections")
                .command(::handleFirmSectionList)
                .build()
        )
    }

    @JvmStatic
    fun dispatcher(): RcmdDispatcher = DISPATCHER

    @JvmStatic
    fun reply(source: RcmdSource, result: RcmdResult?) {
        if (result == null || result.message.isEmpty()) {
            return
        }
        result.message
            .lineSequence()
            .filter { it.isNotEmpty() }
            .forEach { replyLine(source, result.success, it) }
    }

    private fun replyLine(source: RcmdSource, success: Boolean, message: String) {
        if (success) {
            source.sendFeedback(message)
        } else {
            source.sendError(message)
        }
    }

    fun getChatRange(playerId: UUID): String {
        return PlayerChatRangeState.get(playerId).name.lowercase(Locale.ROOT)
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        val state = POS_LOCKS[player.uuid] ?: return
        keepPosLocked(player, state)
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerLogout(event: PlayerLoggedOutEvent) {
        val player = event.entity
        if (player is ServerPlayer) {
            val state = POS_LOCKS.remove(player.uuid)
            if (state != null) {
                restorePosLockState(player, state)
            }
        }
    }

    private fun handlePosLock(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val playerId = player.uuid
        val removed = POS_LOCKS.remove(playerId)
        if (removed != null) {
            restorePosLockState(player, removed)
            return RcmdResult.ok("位置锁定已关闭")
        }
        POS_LOCKS[playerId] = PosLockState.from(player)
        player.isInvulnerable = true
        player.isInvisible = true
        stopPlayerMovement(player)
        return RcmdResult.ok("位置锁定已开启")
    }

    private fun handleFirmSectionSet(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService.set(player)
        val label = firmSectionLabel(result.key)
        return when (result.status) {
            FirmSectionSetStatus.ADDED ->
                RcmdResult.ok("已固定当前子区块：$label (${result.total}/${FirmSectionSavedData.MAX_SECTIONS})")

            FirmSectionSetStatus.ALREADY_PRESENT ->
                RcmdResult.ok("当前子区块已经固定了")

            FirmSectionSetStatus.LIMIT_REACHED ->
                RcmdResult.error("固定子区块已达到上限${FirmSectionSavedData.MAX_SECTIONS}个")
        }
    }

    private fun handleFirmSectionUnset(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService.unset(player)
        val label = firmSectionLabel(result.key)
        val count = "(${result.total}/${FirmSectionSavedData.MAX_SECTIONS})"
        return if (result.removed) {
            RcmdResult.ok("已取消固定当前子区块：$label $count")
        } else {
            RcmdResult.ok("当前子区块尚未固定")
        }
    }

    private fun handleFirmSectionList(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = FirmSectionService.list(player)
        if (result.sections.isEmpty()) {
            return RcmdResult.ok("你还没有固定子区块。全世界：${result.total}/${FirmSectionSavedData.MAX_SECTIONS}")
        }
        val lines = buildList {
            add("你的固定子区块：${result.playerCount}个，全世界：${result.total}/${FirmSectionSavedData.MAX_SECTIONS}")
            result.sections.groupBy { it.dimensionId }.forEach { (dimensionId, sections) ->
                add("$dimensionId : ${sections.map { firmSectionPositionLabel(it) }}")
            }
        }
        return RcmdResult.ok(lines.joinToString("\n"))
    }

    private fun handleTpa(context: RcmdContext): RcmdResult {
        val requester = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val targetName = context.getString("playerName")
        val result = TpaService.request(TpaPlayer211(requester), targetName, TpaPlayerLookup211(requester.server))
        return toRcmdResult(result)
    }

    private fun handleTpok(context: RcmdContext): RcmdResult {
        val target = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = TpaService.accept(TpaPlayer211(target), TpaPlayerLookup211(target.server))
        return toRcmdResult(result)
    }

    private fun handleSetHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = HomeService.setHome(HomePlayer211(player), context.getString("name"))
        return toRcmdResult(result)
    }

    private fun handleHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = HomeService.goHome(HomePlayer211(player), context.getString("name"))
        return toRcmdResult(result)
    }

    private fun handleListHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = HomeService.listHomes(HomePlayer211(player))
        return toRcmdResult(result)
    }

    private fun handleDeleteHome(context: RcmdContext): RcmdResult {
        val player = playerOrNull(context.source) ?: return RcmdResult.error("此rcmd命令只能由玩家执行")
        val result = HomeService.deleteHome(HomePlayer211(player), context.getString("name"))
        return toRcmdResult(result)
    }

    private fun toRcmdResult(result: TpaResult): RcmdResult =
        if (result.success) RcmdResult.ok(result.message) else RcmdResult.error(result.message)

    private fun toRcmdResult(result: HomeResult): RcmdResult =
        if (result.success) RcmdResult.ok(result.message) else RcmdResult.error(result.message)

    private fun keepPosLocked(player: ServerPlayer, state: PosLockState) {
        player.setInvulnerable(true)
        player.setInvisible(true)
        stopPlayerMovement(player)
        val targetLevel = player.server.getLevel(state.dimension)
        if (targetLevel == null) {
            restorePosLockState(player, state)
            POS_LOCKS.remove(player.uuid)
            return
        }
        val currentPos = player.position()
        val moved = currentPos.distanceToSqr(state.x, state.y, state.z) > POS_LOCK_MAX_DISTANCE_SQR
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
        player.setDeltaMovement(Vec3.ZERO)
        player.resetFallDistance()
    }

    private fun playerOrNull(source: RcmdSource): ServerPlayer? =
        when (source) {
            is RcmdServerSource211 -> source.player
            is RcmdCommandSourceStackSource -> source.player
            else -> null
        }

    private fun firmSectionLabel(key: FirmSectionKey): String =
        "${key.dimensionId},${key.chunkX},${key.sectionY},${key.chunkZ}"

    private fun firmSectionPositionLabel(key: FirmSectionKey): String =
        "${key.chunkX},${key.sectionY},${key.chunkZ}"

    @JvmRecord
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
