package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.gui.RdiTabRow;
import calebxzhou.rdi.mc.client.network.GlobalPlayerListState;
import calebxzhou.rdi.mc.client.skin.GlobalPlayerSkinCache;
import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.network.NetworkManager;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * calebxzhou @ 2026-02-03 23:06
 */
@Mixin(GuiPlayerTabOverlay.class)
public class mTabOverlay {
    private static final int TITLE_COLOR = 0xFFFFD86B;
    private static final int TEXT_COLOR = 0xFFEFEFEF;

    //永远显示头像
    @Redirect(method = "renderPlayerlist",
            at = @At(value = "INVOKE",target = "Lnet/minecraft/network/NetworkManager;isEncrypted()Z"))
    private boolean alwaysDisplayAvatar(NetworkManager instance){
        return true;
    }

    @Inject(method = "renderPlayerlist", at = @At("TAIL"))
    private void renderRdiGlobalPlayers(int width, Scoreboard scoreboard, ScoreObjective objective, CallbackInfo ci) {
        RGlobalPlayerList playerList = GlobalPlayerListState.current();
        List<RGlobalPlayerList.HostEntry> hosts = playerList.hosts();
        if (hosts.isEmpty()) {
            return;
        }
        ArrayList<RdiTabRow> rows = new ArrayList<>();
        rows.add(new RdiTabRow("RDI在线玩家", null, TITLE_COLOR));
        for (RGlobalPlayerList.HostEntry host : hosts) {
            if (host.players().isEmpty()) {
                continue;
            }
            rows.add(new RdiTabRow(host.hostName() + " · " + host.modpackName() + " " + host.packVer(), null, TEXT_COLOR));
            for (RGlobalPlayerList.PlayerEntry player : host.players()) {
                rows.add(new RdiTabRow(player.playerName(), parseUuid(player.playerId()), TEXT_COLOR));
            }
        }
        if (rows.size() <= 1) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int maxTextWidth = 0;
        for (RdiTabRow row : rows) {
            maxTextWidth = Math.max(maxTextWidth, mc.fontRenderer.getStringWidth(row.text()) + (row.isPlayer() ? 12 : 0));
        }
        int panelWidth = Math.min(maxTextWidth + 12, 260);
        int lineHeight = 10;
        int x = Math.max(4, width - panelWidth - 8);
        int y = vanillaPlayerListBottomY(mc);
        int panelHeight = rows.size() * lineHeight + 8;
        Gui.drawRect(x - 4, y - 4, x + panelWidth + 4, y + panelHeight, 0x90000000);
        for (int i = 0; i < rows.size(); i++) {
            RdiTabRow row = rows.get(i);
            int rowY = y + i * lineHeight;
            int textX = x;
            if (row.isPlayer()) {
                drawPlayerFace(mc, row.playerId(), row.text(), x, rowY);
                textX += 11;
            }
            mc.fontRenderer.drawStringWithShadow(row.text(), textX, rowY, row.color());
        }
    }

    private int vanillaPlayerListBottomY(Minecraft mc) {
        if (mc.getConnection() == null) {
            return 8;
        }
        int count = Math.min(mc.getConnection().getPlayerInfoMap().size(), 80);
        int columns = 1;
        int rows = count;
        while (rows > 20) {
            columns++;
            rows = (count + columns - 1) / columns;
        }
        return 10 + rows * 9 + 12;
    }

    private void drawPlayerFace(Minecraft mc, UUID playerId, String playerName, int x, int y) {
        ResourceLocation skin = GlobalPlayerSkinCache.skin(playerId, playerName);
        mc.getTextureManager().bindTexture(skin);
        Gui.drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, 8, 8, 64.0F, 64.0F);
        Gui.drawScaledCustomSizeModalRect(x, y, 40.0F, 8.0F, 8, 8, 8, 8, 64.0F, 64.0F);
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return new UUID(0L, raw == null ? 0L : raw.hashCode());
        }
    }
}
