package calebxzhou.rdi.mc.client.network;

import calebxzhou.rdi.mc.common.RDI;
import calebxzhou.rdi.mc.common.SectionPos;
import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import com.google.gson.Gson;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;

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
                        GlobalPlayerListState.update(GSON.fromJson(payload.json(), RGlobalPlayerList.class)))
                .playToClient(RFirmSectionsPayload.TYPE, RFirmSectionsPayload.STREAM_CODEC, (payload, context) ->
                        updateFirmSections(payload));
    }

    private static void updateFirmSections(RFirmSectionsPayload payload) {
        var sectionsByDimension = new HashMap<String, java.util.List<SectionPos>>();
        for (var entry : payload.entries()) {
            sectionsByDimension
                    .computeIfAbsent(entry.dimensionId(), key -> new ArrayList<>())
                    .add(new SectionPos(entry.chunkX(), entry.sectionY(), entry.chunkZ()));
        }
        RDI.FIRM_CHUNKS = sectionsByDimension;
    }
}
