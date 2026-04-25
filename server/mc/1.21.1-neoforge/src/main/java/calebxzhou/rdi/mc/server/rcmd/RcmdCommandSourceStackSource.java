package calebxzhou.rdi.mc.server.rcmd;

import calebxzhou.rdi.mc.rcmd.RcmdSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class RcmdCommandSourceStackSource implements RcmdSource {
    private final CommandSourceStack source;

    public RcmdCommandSourceStackSource(CommandSourceStack source) {
        this.source = source;
    }

    public ServerPlayer getPlayer() {
        return source.getPlayer();
    }

    @Override
    public String name() {
        return source.getTextName();
    }

    @Override
    public UUID playerId() {
        ServerPlayer player = getPlayer();
        return player == null ? RcmdSource.NO_PLAYER_ID : player.getUUID();
    }

    @Override
    public boolean hasPermission(String permission) {
        return source.hasPermission(4);
    }

    @Override
    public void sendFeedback(String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    @Override
    public void sendError(String message) {
        source.sendFailure(Component.literal("[rcmd] " + message));
    }
}
