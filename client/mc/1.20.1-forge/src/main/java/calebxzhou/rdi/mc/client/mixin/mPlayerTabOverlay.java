package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.gui.RTabRow;
import calebxzhou.rdi.mc.client.network.GlobalPlayerListState;
import calebxzhou.rdi.mc.client.skin.GlobalPlayerSkinCache;
import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.Connection;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.UUID;

/**
 * calebxzhou @ 2024-05-23 12:23
 */

@Mixin(PlayerTabOverlay.class)
public class mPlayerTabOverlay {
    //永远显示头像
    @Redirect(method = "render",
            at = @At(value = "INVOKE",target = "Lnet/minecraft/network/Connection;isEncrypted()Z"))
    private boolean alwaysDisplayAvatar(Connection instance){
        return true;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void renderRdiGlobalPlayers(GuiGraphics guiGraphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        var playerList = GlobalPlayerListState.current();
        var hosts = playerList.hosts();
        if (hosts.isEmpty()) {
            return;
        }
        ci.cancel();
        var rows = new ArrayList<RTabRow>();
        rows.add(new RTabRow("RDI在线玩家", null, 0xFFFFD86B));
        for (RGlobalPlayerList.HostEntry host : hosts) {
            if (host.players().isEmpty()) {
                continue;
            }
            String title = host.modpackName().isEmpty() && host.packVer().isEmpty()
                    ? host.hostName()
                    : host.hostName() + " · " + host.modpackName() + " " + host.packVer();
            rows.add(new RTabRow(title, null, 0xFFEFEFEF));
            for (RGlobalPlayerList.PlayerEntry player : host.players()) {
                rows.add(new RTabRow(player.playerName(), parseUuid(player.playerId()), 0xFFEFEFEF));
            }
        }
        if (rows.size() <= 1) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int maxTextWidth = 0;
        for (RTabRow row : rows) {
            maxTextWidth = Math.max(maxTextWidth, font.width(row.text) + (row.isPlayer() ? 12 : 0));
        }
        int panelWidth = Math.min(maxTextWidth + 12, 260);
        int lineHeight = 10;
        int x = (width - panelWidth) / 2;
        int y = 10;
        int panelHeight = rows.size() * lineHeight + 8;
        guiGraphics.fill(x - 4, y - 4, x + panelWidth + 4, y + panelHeight, 0x90000000);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            int rowY = y + i * lineHeight;
            int textX = x;
            if (row.isPlayer()) {
                PlayerFaceRenderer.draw(guiGraphics, GlobalPlayerSkinCache.skin(row.playerId, row.text), x, rowY, 8);
                textX += 11;
            }
            guiGraphics.drawString(font, row.text, textX, rowY, row.color, false);
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return new UUID(0L, raw == null ? 0L : raw.hashCode());
        }
    }

}
