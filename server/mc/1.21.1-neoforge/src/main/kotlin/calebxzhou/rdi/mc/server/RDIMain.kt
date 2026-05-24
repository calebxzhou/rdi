package calebxzhou.rdi.mc.server

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.WebSocketClient
import calebxzhou.rdi.mc.common2.chat.PlayerChatRangeState
import calebxzhou.rdi.mc.common2.tpa.TpaService
import calebxzhou.rdi.mc.server.network.RServerNetwork
import net.minecraft.network.chat.Component
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameRules
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * calebxzhou @ 2026-01-07 11:00
 */
@Mod("rdi")
@EventBusSubscriber(modid = "rdi")
class RDIMain {

    companion object {
        private val lgr: Logger = LogManager.getLogger("rdi")


        @SubscribeEvent
        @JvmStatic
        fun started(e: ServerStartedEvent) {
            WebSocketClient.start(WsHandler211(e.getServer() as DedicatedServer))
        }

        @SubscribeEvent @JvmStatic
        fun stopped(e: ServerStoppedEvent) {
            PlayerChatRangeState.clear()
            TpaService.clear()
            WebSocketClient.stop()
        }

        @SubscribeEvent @JvmStatic
        fun starting(e: ServerStartingEvent) {
            val server = e.getServer() as DedicatedServer

            GameRules.visitGameRuleTypes(object : GameRules.GameRuleTypeVisitor {
                override fun <T : GameRules.Value<T>> visit(key: GameRules.Key<T>, type: GameRules.Type<T>) {
                    val gameRuleEnv = System.getenv("GAME_RULE_" + key.getId())

                    if (gameRuleEnv != null) {
                        val rule: GameRules.Value<*> = server.gameRules.getRule<T>(key)

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
        fun onPlayerJoin(e: PlayerEvent.PlayerLoggedInEvent) {
            val player = e.entity as ServerPlayer
            if (RDI.isAllOp()) {
                player.server.playerList.op(player.gameProfile)
            }
            val range = PlayerChatRangeState.get(player.getUUID())
            player.sendSystemMessage(Component.literal("当前聊天范围：" + range.displayName + "，输入\\chat range host或\\chat range global切换"))
            RServerNetwork.sendLastTo(player)
        }

        @SubscribeEvent @JvmStatic
        fun onPlayerLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
            val player = e.entity as ServerPlayer
            PlayerChatRangeState.remove(player.getUUID())
            TpaService.removeRelated(player.getUUID())
        }
    }
}