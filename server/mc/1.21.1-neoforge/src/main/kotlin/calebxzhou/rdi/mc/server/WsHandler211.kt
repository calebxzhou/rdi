package calebxzhou.rdi.mc.server

import calebxzhou.rdi.mc.common.RGlobalPlayerList
import calebxzhou.rdi.mc.common.WebSocketClient
import calebxzhou.rdi.mc.common.WsMessage
import calebxzhou.rdi.mc.common.WsMessageHandler
import calebxzhou.rdi.mc.rcmd.chat.RChatMessage
import calebxzhou.rdi.mc.server.network.RServerNetwork
import com.google.gson.JsonElement
import net.minecraft.network.chat.Component
import net.minecraft.server.dedicated.DedicatedServer

/**
 * calebxzhou @ 2026-01-12 19:54
 */
class WsHandler211(private val server: DedicatedServer) : WsMessageHandler {
    override fun onMessage(msg: WsMessage<JsonElement>) {
        when (msg.getChannel()) {
            WsMessage.Channel.Command -> {
                val cmd = msg.getData()!!.getAsString()
                val resp = server.runCommand(cmd)
                WebSocketClient.sendMessage<String?>(msg.getId(), WsMessage.Channel.Response, resp)
            }

            WsMessage.Channel.Chat -> {
                val chatMessage = WebSocketClient.fromJson<RChatMessage>(msg.getData(), RChatMessage::class.java)
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[公共] " + chatMessage.playerName + ": " + chatMessage.content),
                    false
                )
            }

            WsMessage.Channel.PlayerList -> {
                val playerList =
                    WebSocketClient.fromJson<RGlobalPlayerList?>(msg.getData(), RGlobalPlayerList::class.java)
                RServerNetwork.sendToAll(server, playerList)
            }

            else -> {}
        }
    }
}