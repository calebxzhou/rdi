package calebxzhou.rdi.mc.server;

import calebxzhou.rdi.mc.common.RDI;
import calebxzhou.rdi.mc.common.WebSocketClient;
import calebxzhou.rdi.mc.common2.chat.ChatRange;
import calebxzhou.rdi.mc.common2.chat.PlayerChatRangeState;
import calebxzhou.rdi.mc.common2.tpa.TpaService;
import calebxzhou.rdi.mc.server.network.RdiServerNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * calebxzhou @ 2026-01-06 13:41
 */
@Mod("rdi")
@Mod.EventBusSubscriber(modid = "rdi")
public class RDIMain  {
    private static final Logger lgr = LogManager.getLogger("rdi");

    public RDIMain() {
        RdiServerNetwork.register();
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        WebSocketClient.start(new WsHandler201((DedicatedServer)e.getServer()));
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
    public static void stopped(ServerStoppedEvent e) {
        PlayerChatRangeState.clear();
        TpaService.clear();
        WebSocketClient.stop();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e){
        var player = (ServerPlayer) e.getEntity();
        if (RDI.isAllOp()) {
            player.server.getPlayerList().op(player.getGameProfile());
        }
        ChatRange range = PlayerChatRangeState.get(player.getUUID());
        player.sendSystemMessage(Component.literal("当前聊天范围：" + range.getDisplayName() + "，输入\\chat range host或\\chat range global切换"));
        RdiServerNetwork.sendLastTo(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        var player = (ServerPlayer) e.getEntity();
        PlayerChatRangeState.remove(player.getUUID());
        TpaService.removeRelated(player.getUUID());
    }
}
