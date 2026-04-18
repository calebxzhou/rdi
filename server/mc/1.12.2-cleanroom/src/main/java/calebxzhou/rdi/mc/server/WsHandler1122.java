package calebxzhou.rdi.mc.server;

import calebxzhou.rdi.mc.common.WebSocketClient;
import calebxzhou.rdi.mc.common.WsMessage;
import calebxzhou.rdi.mc.common.WsMessageHandler;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.dedicated.DedicatedServer;

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
    public void onMessage(WsMessage msg) {
        switch (msg.getChannel()) {
            case Command:
                String resp = runCommand(msg.getData());
                WebSocketClient.sendMessage(msg.getChannel(), resp);
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
