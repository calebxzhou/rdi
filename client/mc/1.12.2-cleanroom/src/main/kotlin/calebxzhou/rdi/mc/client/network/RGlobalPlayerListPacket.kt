package calebxzhou.rdi.mc.client.network

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList
import com.google.gson.Gson
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

class RGlobalPlayerListPacket : IMessage {
    private var json = ""

    override fun fromBytes(buf: ByteBuf) {
        json = ByteBufUtils.readUTF8String(buf)
        if (json.length > MAX_JSON_LENGTH) {
            json = ""
        }
    }

    override fun toBytes(buf: ByteBuf) {
        ByteBufUtils.writeUTF8String(buf, json)
    }

    class Handler : IMessageHandler<RGlobalPlayerListPacket, IMessage?> {
        override fun onMessage(message: RGlobalPlayerListPacket, ctx: MessageContext): IMessage? {
            Minecraft.getMinecraft().addScheduledTask {
                GlobalPlayerListState.update(
                    GSON.fromJson<RGlobalPlayerList?>(
                        message.json,
                        RGlobalPlayerList::class.java
                    )
                )
            }
            return null
        }
    }

    companion object {
        private const val MAX_JSON_LENGTH = 262144
        private val GSON = Gson()
    }
}
