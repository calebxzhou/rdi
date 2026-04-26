package calebxzhou.rdi.mc.server.rcmd;

import calebxzhou.rdi.mc.rcmd.RcmdSource;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import java.util.UUID;

public final class RcmdCommandSenderSource112 implements RcmdSource {
    private final ICommandSender sender;

    public RcmdCommandSenderSource112(ICommandSender sender) {
        this.sender = sender;
    }

    public EntityPlayerMP getPlayer() {
        Entity entity = sender.getCommandSenderEntity();
        return entity instanceof EntityPlayerMP player ? player : null;
    }

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public UUID playerId() {
        EntityPlayerMP player = getPlayer();
        return player == null ? RcmdSource.NO_PLAYER_ID : player.getUniqueID();
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.canUseCommand(4, permission);
    }

    @Override
    public void sendFeedback(String message) {
        sender.sendMessage(new TextComponentString(message));
    }

    @Override
    public void sendError(String message) {
        sender.sendMessage(new TextComponentString("[rcmd] " + message));
    }
}
