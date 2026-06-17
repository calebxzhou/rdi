package calebxzhou.rdi.mc.server.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class RdiGlobalPlayerListPacket implements IMessage {
    private static final int MAX_JSON_LENGTH = 262_144;
    private String json = "";

    public RdiGlobalPlayerListPacket() {
    }

    public RdiGlobalPlayerListPacket(String json) {
        this.json = json == null ? "" : json;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        json = ByteBufUtils.readUTF8String(buf);
        if (json.length() > MAX_JSON_LENGTH) {
            json = "";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, json);
    }

    public static class Handler implements IMessageHandler<RdiGlobalPlayerListPacket, IMessage> {
        @Override
        public IMessage onMessage(RdiGlobalPlayerListPacket message, MessageContext ctx) {
            return null;
        }
    }
}
