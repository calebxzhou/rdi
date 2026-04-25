package calebxzhou.rdi.mc.server;

import calebxzhou.rdi.mc.common.RDI;
import calebxzhou.rdi.mc.common.WebSocketClient;
import calebxzhou.rdi.mc.common2.chat.ChatRange;
import calebxzhou.rdi.mc.common2.chat.PlayerChatRangeState;
import calebxzhou.rdi.mc.common2.tpa.TpaService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * calebxzhou @ 2026-01-07 11:00
 */
@Mod("rdi")
@EventBusSubscriber(modid = "rdi")
public class RDIMain {
    private static final Logger lgr = LogManager.getLogger("rdi");

    public RDIMain() {

    }
    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        WebSocketClient.start(new WsHandler211((DedicatedServer)e.getServer()));
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        PlayerChatRangeState.clear();
        TpaService.clear();
        WebSocketClient.stop();
    }

    @SubscribeEvent
    public static void starting(ServerStartingEvent e) {
        var server = (DedicatedServer) e.getServer();

        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                String gameRuleEnv = System.getenv("GAME_RULE_" + key.getId());

                if (gameRuleEnv != null) {
                    GameRules.Value<?> rule = server.getGameRules().getRule(key);

                    if (rule instanceof GameRules.BooleanValue booleanValue) {
                        booleanValue.set(Boolean.parseBoolean(gameRuleEnv), server);
                        lgr.info("SET GAME RULE {}={}  B", key, gameRuleEnv);
                    } else if (rule instanceof GameRules.IntegerValue integerValue) {
                        integerValue.set(Integer.parseInt(gameRuleEnv), server);
                        lgr.info("SET GAME RULE {}={}  I", key, gameRuleEnv);
                    }
                }
            }
        });
    }
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e){
        var player = (ServerPlayer) e.getEntity();
        if (RDI.isAllOp()) {
            player.server.getPlayerList().op(player.getGameProfile());
        }
        ChatRange range = PlayerChatRangeState.get(player.getUUID());
        player.sendSystemMessage(Component.literal("当前聊天范围：" + range.getDisplayName() + "，输入\\chat range host或\\chat range global切换"));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        var player = (ServerPlayer) e.getEntity();
        PlayerChatRangeState.remove(player.getUUID());
        TpaService.removeRelated(player.getUUID());
    }

}
