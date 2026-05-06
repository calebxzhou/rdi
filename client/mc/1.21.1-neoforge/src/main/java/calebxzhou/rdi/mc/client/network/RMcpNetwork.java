package calebxzhou.rdi.mc.client.network;

import com.google.gson.Gson;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "rdi", value = Dist.CLIENT)
public final class RMcpNetwork {
    private static final Gson GSON = new Gson();
    private static final Map<String, CompletableFuture<RMcpPayload>> PENDING = new ConcurrentHashMap<>();

    private RMcpNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .optional()
                .playBidirectional(RMcpPayload.TYPE, RMcpPayload.STREAM_CODEC, RMcpNetwork::handlePayload);
    }

    public static String nextRequestId() {
        return UUID.randomUUID().toString();
    }

    public static CompletableFuture<RMcpPayload> registerPending(String requestId) {
        var future = new CompletableFuture<RMcpPayload>();
        PENDING.put(requestId, future);
        return future;
    }

    public static void removePending(String requestId) {
        PENDING.remove(requestId);
    }

    private static void handlePayload(RMcpPayload payload, IPayloadContext context) {
        if ("response".equals(payload.kind())) {
            var future = PENDING.remove(payload.requestId());
            if (future != null) {
                future.complete(payload);
            }
            return;
        }
    }
}
