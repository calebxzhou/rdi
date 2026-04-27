package calebxzhou.rdi.mc.server.rcmd;

import calebxzhou.rdi.mc.rcmd.RcmdSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class RcmdServerSource201 implements RcmdSource {
    private final ServerPlayer player;

    public RcmdServerSource201(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    @Override
    public String name() {
        return player.getGameProfile().getName();
    }

    @Override
    public UUID playerId() {
        return player.getUUID();
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermissions(4);
    }

    @Override
    public void sendFeedback(String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public void sendError(String message) {
        player.sendSystemMessage(Component.literal("[rcmd] " + message));
    }
}
