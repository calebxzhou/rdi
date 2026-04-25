package calebxzhou.rdi.mc.server;

import calebxzhou.rdi.mc.common.WebSocketClient;
import calebxzhou.rdi.mc.common.WsMessage;
import calebxzhou.rdi.mc.common.WsMessageHandler;
import com.google.gson.JsonElement;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * calebxzhou @ 2026-01-12 18:08
 */
public class WsHandler165 implements WsMessageHandler {
    private final DedicatedServer server;

    public WsHandler165(DedicatedServer server) {
        this.server = server;
    }

    @Override
    public void onMessage(WsMessage<JsonElement> msg) {
        switch (msg.getChannel()){
            case Command:
                String cmd = msg.getData().getAsString();
                String resp = server.runCommand(cmd);
                WebSocketClient.sendMessage(msg.getId(), WsMessage.Channel.Response, resp);
                break;
            default:
        }
    }
}
