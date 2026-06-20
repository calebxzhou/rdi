package calebxzhou.rdi.mc.server

import calebxzhou.rdi.mc.common.RGlobalPlayerList
import calebxzhou.rdi.mc.common.WebSocketClient
import calebxzhou.rdi.mc.common.WsMessage
import calebxzhou.rdi.mc.common.WsMessageHandler
import calebxzhou.rdi.mc.rcmd.chat.RChatMessage
import calebxzhou.rdi.mc.server.network.RServerNetwork
import com.google.gson.JsonElement
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.util.ChatComponentText

class WsHandler1710(private val server: DedicatedServer) : WsMessageHandler {
    override fun onMessage(msg: WsMessage<JsonElement?>) {
        when (msg.channel) {
            WsMessage.Channel.Command -> WebSocketClient.sendMessage(
                msg.id,
                WsMessage.Channel.Response,
                runCommand(msg.getData()!!.getAsString())
            )

            WsMessage.Channel.Chat -> {
                val chatMessage = WebSocketClient.fromJson<RChatMessage>(msg.getData(), RChatMessage::class.java)
                server.configurationManager
                    .sendChatMsg(ChatComponentText("[公共] " + chatMessage.playerName + ": " + chatMessage.content))
            }

            WsMessage.Channel.PlayerList -> {
                val playerList =
                    WebSocketClient.fromJson(msg.getData(), RGlobalPlayerList::class.java)
                RServerNetwork.sendToAll(server, playerList)
            }

            else -> {}
        }
    }

    private fun runCommand(cmd: String): String {
        val output = server.handleRConCommand(cmd)
        return if (output == null || output.isEmpty()) "OK" else output
    }
}
