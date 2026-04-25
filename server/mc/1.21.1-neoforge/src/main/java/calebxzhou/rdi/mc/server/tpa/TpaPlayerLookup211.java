package calebxzhou.rdi.mc.server.tpa;

import calebxzhou.rdi.mc.common2.tpa.TpaPlayer;
import calebxzhou.rdi.mc.common2.tpa.TpaPlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class TpaPlayerLookup211 implements TpaPlayerLookup {
    private final MinecraftServer server;

    public TpaPlayerLookup211(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public TpaPlayer findByName(String name) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        return player == null ? null : new TpaPlayer211(player);
    }

    @Override
    public TpaPlayer findById(UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player == null ? null : new TpaPlayer211(player);
    }

    @Override
    public void teleportTo(TpaPlayer requester, TpaPlayer target) {
        ServerPlayer requesterPlayer = ((TpaPlayer211) requester).unwrap();
        ServerPlayer targetPlayer = ((TpaPlayer211) target).unwrap();
        requesterPlayer.teleportTo(
                targetPlayer.serverLevel(),
                targetPlayer.getX(),
                targetPlayer.getY(),
                targetPlayer.getZ(),
                targetPlayer.getYRot(),
                targetPlayer.getXRot()
        );
    }
}
