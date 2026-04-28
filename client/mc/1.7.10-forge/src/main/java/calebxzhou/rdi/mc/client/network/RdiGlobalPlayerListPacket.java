package calebxzhou.rdi.mc.client.network;

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import com.google.gson.Gson;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;

public class RdiGlobalPlayerListPacket implements IMessage {
    private static final int MAX_JSON_LENGTH = 262_144;
    private static final Gson GSON = new Gson();
    private String json = "";

    public RdiGlobalPlayerListPacket() {
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
            Minecraft.getMinecraft().func_152344_a(() ->
                    GlobalPlayerListState.update(GSON.fromJson(message.json, RGlobalPlayerList.class)));
            return null;
        }
    }
}
