package calebxzhou.rdi.mc.client.network;

import calebxzhou.rdi.mc.common2.mcp.RMcpBlockEntityData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockActionData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockBatchActionData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockPosData;
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerData;
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerMoveData;
import calebxzhou.rdi.mc.common2.mcp.RMcpCraftData;
import calebxzhou.rdi.mc.common2.mcp.RMcpCraftingOpenData;
import calebxzhou.rdi.mc.common2.mcp.RMcpEndpointException;
import calebxzhou.rdi.mc.common2.mcp.RErrorCode;
import calebxzhou.rdi.mc.common2.mcp.RMcpHarvestToolData;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class RMcpClientBridge {
    private static final Gson GSON = new Gson();
    private static final int REQUEST_TIMEOUT_SECONDS = 3;

    private RMcpClientBridge() {
    }

    public static RMcpBlockEntityData requestBlockEntity(String dim, int x, int y, int z) {
        return requestServer("blockentity", new BlockEntityRequest(dim, x, y, z), RMcpBlockEntityData.class);
    }

    public static RMcpHarvestToolData requestHarvestTool(String blockId, String dim, Integer x, Integer y, Integer z) {
        return requestServer("harvest-tool", new HarvestToolRequest(blockId, dim, x, y, z), RMcpHarvestToolData.class);
    }

    public static RMcpCraftingOpenData requestOpenCrafting(int radius, boolean dryRun) {
        return requestServer("open-crafting", new OpenCraftingRequest(radius, dryRun), RMcpCraftingOpenData.class);
    }

    public static RMcpCraftData requestCraft(Map<String, Integer> slots, String shape, int outputSlot, int times, boolean dryRun) {
        return requestServer("craft", new CraftRequest(slots, shape, outputSlot, times, dryRun), RMcpCraftData.class);
    }

    public static RMcpContainerData requestContainer(String pos, String side) {
        return requestServer("container", new ContainerRequest(pos, side), RMcpContainerData.class);
    }

    public static RMcpContainerMoveData requestContainerMove(String fromPos, String fromSide, int fromSlot, String toPos, String toSide, Integer toSlot, int count, boolean dryRun) {
        return requestServer(
                "container-move",
                new ContainerMoveRequest(
                        new ContainerEndpointRequest(fromPos, fromSide, fromSlot),
                        new ContainerEndpointRequest(toPos, toSide, toSlot),
                        count,
                        dryRun
                ),
                RMcpContainerMoveData.class
        );
    }

    public static RMcpBlockActionData requestPlaceBlock(int x, int y, int z) {
        return requestServer("place-block", new BlockActionRequest(x, y, z), RMcpBlockActionData.class);
    }

    public static RMcpBlockActionData requestBreakBlock(int x, int y, int z) {
        return requestServer("break-block", new BlockActionRequest(x, y, z), RMcpBlockActionData.class);
    }

    public static RMcpBlockBatchActionData requestPlaceBlocks(List<RMcpBlockPosData> positions) {
        return requestServer("place-block-batch", new BlockBatchActionRequest(positions), RMcpBlockBatchActionData.class);
    }

    public static RMcpBlockBatchActionData requestBreakBlocks(List<RMcpBlockPosData> positions) {
        return requestServer("break-block-batch", new BlockBatchActionRequest(positions), RMcpBlockBatchActionData.class);
    }

    public static RMcpBlockBatchActionData requestPlaceBlockBox(RMcpBlockPosData from, RMcpBlockPosData to) {
        return requestServer("place-block-box", new BlockBoxActionRequest(from, to), RMcpBlockBatchActionData.class);
    }

    public static RMcpBlockBatchActionData requestBreakBlockBox(RMcpBlockPosData from, RMcpBlockPosData to) {
        return requestServer("break-block-box", new BlockBoxActionRequest(from, to), RMcpBlockBatchActionData.class);
    }

    private static <T> T requestServer(String action, Object request, Class<T> responseClass) {
        if (!FMLEnvironment.dist.isClient()) {
            throw new RMcpEndpointException(RErrorCode.SERVER_MCP_UNAVAILABLE);
        }
        var minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        if (minecraft.player == null || connection == null || !connection.hasChannel(RMcpPayload.TYPE)) {
            throw new RMcpEndpointException(RErrorCode.SERVER_MCP_UNAVAILABLE);
        }

        var requestId = RMcpNetwork.nextRequestId();
        var future = RMcpNetwork.registerPending(requestId);
        try {
            PacketDistributor.sendToServer(new RMcpPayload(requestId, "request", action, "", GSON.toJson(request)));
            var response = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!"ok".equals(response.code())) {
                throw new RMcpEndpointException(response.code());
            }
            return GSON.fromJson(response.json(), responseClass);
        } catch (RMcpEndpointException e) {
            throw e;
        } catch (TimeoutException e) {
            throw new RMcpEndpointException(RErrorCode.SERVER_TIMEOUT);
        } catch (Exception e) {
            throw new RMcpEndpointException(RErrorCode.SERVER_MCP_UNAVAILABLE);
        } finally {
            RMcpNetwork.removePending(requestId);
        }
    }

    private record BlockEntityRequest(String dim, int x, int y, int z) {
    }

    private record HarvestToolRequest(String blockId, String dim, Integer x, Integer y, Integer z) {
    }

    private record OpenCraftingRequest(int radius, boolean dryRun) {
    }

    private record CraftRequest(Map<String, Integer> slots, String shape, int outputSlot, int times, boolean dryRun) {
    }

    private record ContainerRequest(String pos, String side) {
    }

    private record ContainerMoveRequest(ContainerEndpointRequest from, ContainerEndpointRequest to, int count, boolean dryRun) {
    }

    private record ContainerEndpointRequest(String pos, String side, Integer slot) {
    }

    private record BlockActionRequest(int x, int y, int z) {
    }

    private record BlockBatchActionRequest(List<RMcpBlockPosData> positions) {
    }

    private record BlockBoxActionRequest(RMcpBlockPosData from, RMcpBlockPosData to) {
    }
}
