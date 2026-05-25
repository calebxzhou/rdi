package calebxzhou.rdi.mc.client.rcmd;

import calebxzhou.rdi.mc.rcmd.RcmdSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class RcmdClientSource211 implements RcmdSource {
    private final Minecraft minecraft;

    public RcmdClientSource211(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public String name() {
        return minecraft.getUser().getName();
    }

    @Override
    public UUID playerId() {
        return minecraft.player == null ? NO_PLAYER_ID : minecraft.player.getUUID();
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public void sendFeedback(String message) {
        sendMessage(message);
    }

    @Override
    public void sendError(String message) {
        sendMessage("[rcmd] " + message);
    }

    private void sendMessage(String message) {
        var component = Component.literal(message);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(component, false);
            return;
        }
        minecraft.gui.getChat().addMessage(component);
    }
}
