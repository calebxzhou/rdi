package calebxzhou.rdi.mc.server.tpa;

import calebxzhou.rdi.mc.common2.tpa.TpaPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import java.util.UUID;

public final class TpaPlayer112 implements TpaPlayer {
    private final EntityPlayerMP player;

    public TpaPlayer112(EntityPlayerMP player) {
        this.player = player;
    }

    public EntityPlayerMP unwrap() {
        return player;
    }

    @Override
    public UUID id() {
        return player.getUniqueID();
    }

    @Override
    public String name() {
        return player.getGameProfile().getName();
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(new TextComponentString(message));
    }
}
