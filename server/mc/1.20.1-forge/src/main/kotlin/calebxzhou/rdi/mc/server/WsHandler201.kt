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
import kotlin.jvm.java

/**
 * calebxzhou @ 2026-01-12 18:08
 */
class WsHandler201(private val server: DedicatedServer) : WsMessageHandler {

    override fun onMessage(msg: WsMessage<JsonElement>) {
        when (msg.channel) {
            WsMessage.Channel.Command -> {
                val cmd: String = msg.getData().asString
                val resp = server.runCommand(cmd)
                WebSocketClient.sendMessage(msg.id, WsMessage.Channel.Response, resp)
            }

            WsMessage.Channel.Chat -> {
                val chatMessage: RChatMessage =
                    WebSocketClient.fromJson(msg.getData(), RChatMessage::class.java)
                server.playerList.broadcastSystemMessage(
                    Component.literal("[公共] " + chatMessage.playerName + ": " + chatMessage.content),
                    false
                )
            }

            WsMessage.Channel.PlayerList -> {
                val playerList: RGlobalPlayerList =
                    WebSocketClient.fromJson(msg.getData(), RGlobalPlayerList::class.java)
                RServerNetwork.sendToAll(server, playerList)
            }

            else -> {}
        }
    }
}
