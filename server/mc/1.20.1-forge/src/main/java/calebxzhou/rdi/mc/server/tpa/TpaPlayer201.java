package calebxzhou.rdi.mc.server.tpa;

import calebxzhou.rdi.mc.common2.tpa.TpaPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class TpaPlayer201 implements TpaPlayer {
    private final ServerPlayer player;

    public TpaPlayer201(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer unwrap() {
        return player;
    }

    @Override
    public UUID id() {
        return player.getUUID();
    }

    @Override
    public String name() {
        return player.getGameProfile().getName();
    }

    @Override
    public void sendMessage(String message) {
        player.sendSystemMessage(Component.literal(message));
    }
}
