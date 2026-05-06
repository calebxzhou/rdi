package calebxzhou.rdi.mc.server.network;

import calebxzhou.rdi.mc.server.mcp.RMcpServerDataCodec211;
import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = "rdi")
public final class RMcpServerNetwork {
    private static final Gson GSON = new Gson();

    private RMcpServerNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .optional()
                .playBidirectional(RMcpPayload.TYPE, RMcpPayload.STREAM_CODEC, RMcpServerNetwork::handlePayload);
    }

    private static void handlePayload(RMcpPayload payload, IPayloadContext context) {
        if ("request".equals(payload.kind())) {
            context.enqueueWork(() -> handleRequest(payload, context));
        }
    }

    private static void handleRequest(RMcpPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "no_player", ""));
            return;
        }
        try {
            switch (payload.action()) {
                case "blockentity" -> handleBlockEntity(payload, context, player);
                case "harvest-tool" -> handleHarvestTool(payload, context, player);
                default -> context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "bad_action", ""));
            }
        } catch (Exception e) {
            context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "internal_error", ""));
        }
    }

    private static void handleBlockEntity(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), BlockEntityRequest.class);
        if (request == null || request.dim() == null || request.dim().isBlank()) {
            context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "bad_request", ""));
            return;
        }
        var level = player.serverLevel();
        var dim = level.dimension().location().toString();
        if (!dim.equals(request.dim())) {
            context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "dim_not_loaded", ""));
            return;
        }
        var blockEntity = level.getBlockEntity(new BlockPos(request.x(), request.y(), request.z()));
        if (blockEntity == null) {
            context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "no_block_entity", ""));
            return;
        }
        var data = RMcpServerDataCodec211.blockEntityData(dim, blockEntity, level.registryAccess());
        context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "ok", GSON.toJson(data)));
    }

    private static void handleHarvestTool(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), HarvestToolRequest.class);
        if (request == null || ((request.blockId() == null || request.blockId().isBlank()) && request.dim() == null)) {
            context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "bad_request", ""));
            return;
        }
        var level = player.serverLevel();
        if (request.dim() != null && !request.dim().isBlank() && !level.dimension().location().toString().equals(request.dim())) {
            context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "dim_not_loaded", ""));
            return;
        }
        var data = RMcpServerDataCodec211.harvestToolData(level, player, request.blockId(), request.x(), request.y(), request.z());
        if (data == null) {
            context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "bad_block_id", ""));
            return;
        }
        context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "ok", GSON.toJson(data)));
    }

    private record BlockEntityRequest(String dim, int x, int y, int z) {
    }

    private record HarvestToolRequest(String blockId, String dim, Integer x, Integer y, Integer z) {
    }
}
