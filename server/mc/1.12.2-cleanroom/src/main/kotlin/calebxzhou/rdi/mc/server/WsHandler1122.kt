package calebxzhou.rdi.mc.server;

import calebxzhou.rdi.mc.common.WebSocketClient;
import calebxzhou.rdi.mc.common.WsMessage;
import calebxzhou.rdi.mc.common.WsMessageHandler;
import calebxzhou.rdi.mc.common2.chat.RChatMessage;
import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import calebxzhou.rdi.mc.server.network.RdiServerNetwork;
import com.google.gson.JsonElement;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.text.TextComponentString;

/**
 * calebxzhou @ 2026-04-18 17:57
 */
public class WsHandler1122 implements WsMessageHandler {
    private final DedicatedServer server;
    private final RConConsoleSource console;

    public WsHandler1122(DedicatedServer server) {
        this.server = server;
        this.console = new RConConsoleSource(server);
    }

    @Override
    public void onMessage(WsMessage<JsonElement> msg) {
        switch (msg.getChannel()) {
            case Command:
                String resp = runCommand(msg.getData().getAsString());
                WebSocketClient.sendMessage(msg.getId(), WsMessage.Channel.Response, resp);
                break;
            case Chat:
                RChatMessage chatMessage = WebSocketClient.fromJson(msg.getData(), RChatMessage.class);
                server.getPlayerList().sendMessage(new TextComponentString("[公共] " + chatMessage.playerName() + ": " + chatMessage.content()), false);
                break;
            case PlayerList:
                RGlobalPlayerList playerList = WebSocketClient.fromJson(msg.getData(), RGlobalPlayerList.class);
                RdiServerNetwork.sendToAll(server, playerList);
                break;
            default:
        }
    }

    private String runCommand(String cmd) {
        console.resetLog();
        server.commandManager.executeCommand(console, cmd);
        String output = console.getLogContents();
        return output == null || output.isEmpty() ? "OK" : output;
    }
}
