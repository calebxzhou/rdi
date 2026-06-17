package calebxzhou.rdi.mc.server.tpa;

import calebxzhou.rdi.mc.common2.tpa.TpaPlayer;
import calebxzhou.rdi.mc.common2.tpa.TpaPlayerLookup;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class TpaPlayerLookup112 implements TpaPlayerLookup {
    private final MinecraftServer server;

    public TpaPlayerLookup112(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public TpaPlayer findByName(String name) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(name);
        return player == null ? null : new TpaPlayer112(player);
    }

    @Override
    public TpaPlayer findById(UUID id) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(id);
        return player == null ? null : new TpaPlayer112(player);
    }

    @Override
    public void teleportTo(TpaPlayer requester, TpaPlayer target) {
        if (requester instanceof TpaPlayer112 requester112 && target instanceof TpaPlayer112 target112) {
            EntityPlayerMP requesterPlayer = requester112.unwrap();
            EntityPlayerMP targetPlayer = target112.unwrap();
            if (requesterPlayer.dimension != targetPlayer.dimension) {
                requesterPlayer.server.getPlayerList().transferPlayerToDimension(requesterPlayer, targetPlayer.dimension, (world, entity, yaw) ->
                        entity.setLocationAndAngles(targetPlayer.posX, targetPlayer.posY, targetPlayer.posZ, targetPlayer.rotationYaw, targetPlayer.rotationPitch)
                );
            }
            requesterPlayer.connection.setPlayerLocation(targetPlayer.posX, targetPlayer.posY, targetPlayer.posZ, targetPlayer.rotationYaw, targetPlayer.rotationPitch);
        }
    }
}
