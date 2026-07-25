package calebxzhou.rdi.mc.server

import calebxzhou.rdi.mc.common.RGlobalPlayerList
import calebxzhou.rdi.mc.common.WebSocketClient
import calebxzhou.rdi.mc.common.WsMessage
import calebxzhou.rdi.mc.common.WsMessageHandler
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState
import calebxzhou.rdi.mc.rcmd.chat.RChatMessage
import calebxzhou.rdi.mc.server.network.RServerNetwork
import com.google.gson.JsonElement
import net.minecraft.network.rcon.RConConsoleSource
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.util.text.TextComponentString

/**
 * calebxzhou @ 2026-04-18 17:57
 */
class WsHandler1122(private val server: DedicatedServer) : WsMessageHandler {
    private val console: RConConsoleSource = RConConsoleSource(server)

    public override fun onMessage(msg: WsMessage<JsonElement>) {
        when (msg.getChannel()) {
            WsMessage.Channel.Command -> {
                val resp = runCommand(msg.getData().getAsString())
                WebSocketClient.sendMessage<String>(msg.getId(), WsMessage.Channel.Response, resp)
            }

            WsMessage.Channel.Chat -> {
                val chatMessage: RChatMessage =
                    WebSocketClient.fromJson<RChatMessage>(msg.getData(), RChatMessage::class.java)
                val component = TextComponentString("[公共] " + chatMessage.playerName + ": " + chatMessage.content)
                server.playerList.players
                    .filter { PlayerChatRangeState.isGlobal(it.uniqueID) }
                    .forEach { it.sendMessage(component) }
            }

            WsMessage.Channel.PlayerList -> {
                val playerList: RGlobalPlayerList? =
                    WebSocketClient.fromJson<RGlobalPlayerList?>(msg.getData(), RGlobalPlayerList::class.java)
                RServerNetwork.sendToAll(server, playerList)
            }

            else -> {}
        }
    }

    private fun runCommand(cmd: String?): String {
        console.resetLog()
        server.commandManager.executeCommand(console, cmd)
        val output = console.getLogContents()
        return if (output == null || output.isEmpty()) "OK" else output
    }
}
