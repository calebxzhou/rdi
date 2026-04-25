package calebxzhou.rdi.mc.server;

import calebxzhou.rdi.mc.common.WebSocketClient;
import calebxzhou.rdi.mc.common.WsMessage;
import calebxzhou.rdi.mc.common.WsMessageHandler;
import calebxzhou.rdi.mc.common2.chat.RChatMessage;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * calebxzhou @ 2026-01-12 19:54
 */
public class WsHandler211 implements WsMessageHandler {
    private final DedicatedServer server;

    public WsHandler211(DedicatedServer server) {
        this.server = server;
    }

    @Override
    public void onMessage(WsMessage<JsonElement> msg) {
        switch (msg.getChannel()){
            case Command -> {
                var cmd = msg.getData().getAsString();
                var resp = server.runCommand(cmd);
                WebSocketClient.sendMessage(msg.getId(), WsMessage.Channel.Response, resp);
            }
            case Chat -> {
                var chatMessage = WebSocketClient.fromJson(msg.getData(), RChatMessage.class);
                server.getPlayerList().broadcastSystemMessage(Component.literal("[全局] " + chatMessage.playerName() + ": " + chatMessage.content()), false);
            }
            default -> {}
        }
    }
}
