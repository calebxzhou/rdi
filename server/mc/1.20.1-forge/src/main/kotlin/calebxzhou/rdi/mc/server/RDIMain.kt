package calebxzhou.rdi.mc.server

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.WebSocketClient
import calebxzhou.rdi.mc.rcmd.chat.ChatRange
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState
import calebxzhou.rdi.mc.rcmd.tpa.TpaService
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService
import calebxzhou.rdi.mc.server.network.RServerNetwork
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameRules
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


/**
 * calebxzhou @ 2026-01-06 13:41
 */
@Mod("rdi")
@Mod.EventBusSubscriber(modid = "rdi")
class RDIMain {
    init {
        RServerNetwork.register()
    }

    companion object {
        private val lgr: Logger = LogManager.getLogger("rdi")

        @SubscribeEvent
        @JvmStatic
        fun started(e: ServerStartedEvent) {
            WebSocketClient.start(WsHandler201(e.getServer() as DedicatedServer))
        }

        @SubscribeEvent
        @JvmStatic
        fun starting(e: ServerStartingEvent) {
            val server: DedicatedServer = e.getServer() as DedicatedServer

            GameRules.visitGameRuleTypes(object : GameRules.GameRuleTypeVisitor {
                override fun <T : GameRules.Value<T>> visit(key: GameRules.Key<T>, type: GameRules.Type<T>) {
                    val gameRuleEnv = System.getenv("GAME_RULE_" + key.getId())

                    if (gameRuleEnv != null) {
                        val rule = server.gameRules.getRule<T>(key)

                        if (rule is GameRules.BooleanValue) {
                            rule.set(gameRuleEnv.toBoolean(), server)
                            lgr.info("SET GAME RULE {}={}  B", key, gameRuleEnv)
                        } else if (rule is GameRules.IntegerValue) {
                            rule.set(gameRuleEnv.toInt(), server)
                            lgr.info("SET GAME RULE {}={}  I", key, gameRuleEnv)
                        }
                    }
                }
            })
        }

        @SubscribeEvent @JvmStatic
        fun stopped(e: ServerStoppedEvent) {
            PlayerChatRangeState.clear()
            TpaService.clear()
            WebSocketClient.stop()
        }

        @SubscribeEvent @JvmStatic
        fun onPlayerJoin(e: PlayerEvent.PlayerLoggedInEvent) {
            val player: ServerPlayer = e.entity as ServerPlayer
            if (RDI.isAllOp()) {
                player.server.playerList.op(player.gameProfile)
            }
            val range: ChatRange = PlayerChatRangeState.get(player.getUUID())
            val result = FirmSectionService.list(player)
            player.sendSystemMessage(Component.literal("当前聊天范围：" + range.displayName + "，输入\\chat range host或\\chat range global切换"))
            player.sendSystemMessage(Component.literal("从6月16日起 只有“持久子区块”会永久保存 其余区域将在日后随机重新生成\n" +
                    "未来可以享受到定时定点回档、方块放置破坏日志等高级特性\n"+
                    "你设定了${result.playerCount}个 本存档已设定${result.total}个 详情阅读说明书"))
            player.sendSystemMessage(Component.literal("点此打开RDI说明书").withStyle(ChatFormatting.UNDERLINE).withStyle(
                Style.EMPTY.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL,"https://craftrdi.feishu.cn/wiki/U8LRwMpUliuxW5kZLvCcxonNnkd"))))

            RServerNetwork.sendLastTo(player)
            RServerNetwork.sendFirmSectionsTo(player)
        }

        @SubscribeEvent @JvmStatic
        fun onPlayerLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
            val player: ServerPlayer = e.entity as ServerPlayer
            PlayerChatRangeState.remove(player.getUUID())
            TpaService.removeRelated(player.getUUID())
        }
    }
}
