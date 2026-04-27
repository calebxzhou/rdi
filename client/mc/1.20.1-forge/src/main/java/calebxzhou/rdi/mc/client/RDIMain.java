package calebxzhou.rdi.mc.client;

import calebxzhou.rdi.mc.client.network.RdiClientNetwork;
import com.google.common.net.HostAndPort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;

import static calebxzhou.rdi.mc.common.RDI.GAME_IP;
import static calebxzhou.rdi.mc.common.RDI.HOST_NAME;

/**
 * calebxzhou @ 2026-01-06 19:19
 */
@Mod("rdi")
public class RDIMain {
    public static Button JOIN_BUTTON = Button.builder(Component.literal("进入地图 · " + HOST_NAME),(btn)->{
        HostAndPort hp = HostAndPort.fromString(GAME_IP);
        ConnectScreen.startConnecting(
                new TitleScreen(),
                Minecraft.getInstance(),
                new ServerAddress(hp.getHost(), hp.getPort()),
                new ServerData("rdi", GAME_IP, false),
                false
        );
    }).bounds(100,0,200,50).build();

    public static void layoutJoinButton(int screenWidth) {
        JOIN_BUTTON.setX(screenWidth / 2 - 100);
        JOIN_BUTTON.setY(0);
        JOIN_BUTTON.setWidth(200);
        JOIN_BUTTON.setHeight(20);
    }

    public RDIMain() {
        RdiClientNetwork.register();
        LogManager.getLogger("rdi").info("❄❄❄❄❄❄❄❄RDI客户端核心模块已加载❄❄❄❄❄❄❄❄");
    }
}
