package calebxzhou.rdi.mc.server

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.WebSocketClient
import calebxzhou.rdi.mc.rcmd.chat.ChatRange
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState
import calebxzhou.rdi.mc.rcmd.tpa.TpaService
import calebxzhou.rdi.mc.server.network.RServerNetwork
import net.minecraft.network.chat.Component
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
        fun started(e: ServerStartedEvent) {
            WebSocketClient.start(WsHandler201(e.getServer() as DedicatedServer))
        }

        @SubscribeEvent
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

        @SubscribeEvent
        fun stopped(e: ServerStoppedEvent) {
            PlayerChatRangeState.clear()
            TpaService.clear()
            WebSocketClient.stop()
        }

        @SubscribeEvent
        fun onPlayerJoin(e: PlayerEvent.PlayerLoggedInEvent) {
            val player: ServerPlayer = e.entity as ServerPlayer
            if (RDI.isAllOp()) {
                player.server.playerList.op(player.gameProfile)
            }
            val range: ChatRange = PlayerChatRangeState.get(player.getUUID())
            player.sendSystemMessage(Component.literal("当前聊天范围：" + range.displayName + "，输入\\chat range host或\\chat range global切换"))
            RServerNetwork.sendLastTo(player)
        }

        @SubscribeEvent
        fun onPlayerLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
            val player: ServerPlayer = e.entity as ServerPlayer
            PlayerChatRangeState.remove(player.getUUID())
            TpaService.removeRelated(player.getUUID())
        }
    }
}
