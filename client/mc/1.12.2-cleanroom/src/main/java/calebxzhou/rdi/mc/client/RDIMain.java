package calebxzhou.rdi.mc.client;

import calebxzhou.rdi.mc.common.RDI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;

import static calebxzhou.rdi.mc.common.RDI.GAME_IP;

/**
 * calebxzhou @ 2026-02-03 21:25
 */
@Mod(modid = "rdi", name = "rdi", version = "1")
public class RDIMain {
    public static int JOIN_BUTTON_ID = 666;
    public static GuiButton JOIN_BUTTON = new GuiButton(JOIN_BUTTON_ID,100, 0, 200, 20, "进入地图：" + RDI.HOST_NAME);
    public static void onJoinRDI(){
        Minecraft.getMinecraft().displayGuiScreen(
                new GuiConnecting(
                        new GuiMainMenu(),
                        Minecraft.getMinecraft(),
                        new ServerData("rdi", GAME_IP, false)
                ));
    }
    public static net.minecraft.util.ResourceLocation BG_RES = new ResourceLocation("rdi", "textures/bg/1.jpg");

    public RDIMain() {
        LogManager.getLogger("rdi").info("❄❄❄❄❄❄❄❄RDI客户端核心模块已加载❄❄❄❄❄❄❄❄");
    }
}