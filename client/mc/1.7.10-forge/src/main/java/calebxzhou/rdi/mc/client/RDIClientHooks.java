package calebxzhou.rdi.mc.client;

import calebxzhou.rdi.mc.common.RDI;
import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ResourceLocation;

public final class RDIClientHooks {
    public static final int JOIN_BUTTON_ID = 666;
    public static final int JOIN_BUTTON_WIDTH = 200;
    public static final int JOIN_BUTTON_HEIGHT = 20;
    public static final ResourceLocation BG_RES = new ResourceLocation("rdi", "textures/bg/1.jpg");

    private RDIClientHooks() {
    }

    public static GuiButton createJoinButton(int x, int y) {
        return new GuiButton(
            JOIN_BUTTON_ID, x, y, JOIN_BUTTON_WIDTH, JOIN_BUTTON_HEIGHT, "进入地图：" + RDI.HOST_NAME);
    }

    public static boolean isJoinButton(GuiButton button) {
        return button != null && button.id == JOIN_BUTTON_ID;
    }

    public static void joinHost(GuiScreen parentScreen) {
        FMLClientHandler handler = FMLClientHandler.instance();
        handler.setupServerList();
        handler.connectToServer(parentScreen, new ServerData("rdi", RDI.GAME_IP, false));
    }
}
