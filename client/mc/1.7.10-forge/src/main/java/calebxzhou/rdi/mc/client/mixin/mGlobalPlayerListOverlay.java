package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.gui.RdiGlobalTabRow;
import calebxzhou.rdi.mc.client.network.GlobalPlayerListState;
import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = GuiIngameForge.class, remap = false)
public class mGlobalPlayerListOverlay {
    private static final int TITLE_COLOR = 0xFFFFD86B;
    private static final int TEXT_COLOR = 0xFFEFEFEF;

    @Inject(method = "renderPlayerList", at = @At("TAIL"), remap = false)
    private void renderRdiGlobalPlayers(int width, int height, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.gameSettings.keyBindPlayerList.getIsKeyPressed()) {
            return;
        }
        RGlobalPlayerList playerList = GlobalPlayerListState.current();
        List<RGlobalPlayerList.HostEntry> hosts = playerList.hosts();
        if (hosts.isEmpty()) {
            return;
        }

        ArrayList<RdiGlobalTabRow> rows = new ArrayList<>();
        rows.add(new RdiGlobalTabRow("RDI在线玩家", TITLE_COLOR));
        for (RGlobalPlayerList.HostEntry host : hosts) {
            if (host.players().isEmpty()) {
                continue;
            }
            rows.add(new RdiGlobalTabRow(host.hostName() + " · " + host.modpackName() + " " + host.packVer(), TEXT_COLOR));
            for (RGlobalPlayerList.PlayerEntry player : host.players()) {
                rows.add(new RdiGlobalTabRow("  " + player.playerName(), TEXT_COLOR));
            }
        }
        if (rows.size() <= 1) {
            return;
        }

        FontRenderer font = mc.fontRenderer;
        int maxTextWidth = 0;
        for (RdiGlobalTabRow row : rows) {
            maxTextWidth = Math.max(maxTextWidth, font.getStringWidth(row.text));
        }
        int panelWidth = Math.min(maxTextWidth + 12, 260);
        int lineHeight = 10;
        int[] vanillaBounds = vanillaPlayerListBounds(mc, width);
        int x = rdiPanelX(width, panelWidth, vanillaBounds);
        int y = vanillaBounds[1];
        int panelHeight = rows.size() * lineHeight + 8;

        Gui.drawRect(x - 4, y - 4, x + panelWidth + 4, y + panelHeight, 0x90000000);
        for (int i = 0; i < rows.size(); i++) {
            RdiGlobalTabRow row = rows.get(i);
            font.drawStringWithShadow(row.text, x, y + i * lineHeight, row.color);
        }
    }

    private int[] vanillaPlayerListBounds(Minecraft mc, int width) {
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            return new int[]{width / 2, 8, width / 2};
        }
        NetHandlerPlayClient handler = mc.thePlayer.sendQueue;
        int maxPlayers = Math.max(handler.currentServerMaxPlayers, handler.playerInfoList.size());
        maxPlayers = Math.max(1, maxPlayers);
        int columns = 1;
        int rows = maxPlayers;
        while (rows > 20) {
            columns++;
            rows = (maxPlayers + columns - 1) / columns;
        }
        int columnWidth = 300 / columns;
        if (columnWidth > 150) {
            columnWidth = 150;
        }
        int left = (width - columns * columnWidth) / 2;
        return new int[]{left, 10, left + columns * columnWidth};
    }

    private int rdiPanelX(int width, int panelWidth, int[] vanillaBounds) {
        int margin = 8;
        int left = vanillaBounds[0] - panelWidth - margin;
        if (left >= 4) {
            return left;
        }
        int right = vanillaBounds[2] + margin;
        if (right + panelWidth + 4 <= width) {
            return right;
        }
        return Math.max(4, width - panelWidth - 8);
    }
}
