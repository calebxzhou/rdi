package calebxzhou.rdi.mc.client.network;

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import com.google.gson.Gson;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "rdi",value = Dist.CLIENT)
public final class RClientNetwork {
    private static final Gson GSON = new Gson();

    private RClientNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .optional()
                .playToClient(RGlobalPlayerListPayload.TYPE, RGlobalPlayerListPayload.STREAM_CODEC, (payload, context) ->
                        GlobalPlayerListState.update(GSON.fromJson(payload.json(), RGlobalPlayerList.class)));
    }
}
