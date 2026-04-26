package calebxzhou.rdi.mc.server.rcmd;

import calebxzhou.rdi.mc.rcmd.RcmdSource;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import java.util.UUID;

public final class RcmdServerSource112 implements RcmdSource {
    private final EntityPlayerMP player;

    public RcmdServerSource112(EntityPlayerMP player) {
        this.player = player;
    }

    public EntityPlayerMP getPlayer() {
        return player;
    }

    @Override
    public String name() {
        return player.getGameProfile().getName();
    }

    @Override
    public UUID playerId() {
        return player.getUniqueID();
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.server.getPlayerList().canSendCommands(player.getGameProfile());
    }

    @Override
    public void sendFeedback(String message) {
        player.sendMessage(new TextComponentString(message));
    }

    @Override
    public void sendError(String message) {
        player.sendMessage(new TextComponentString("[rcmd] " + message));
    }
}
