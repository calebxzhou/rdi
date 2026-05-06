package calebxzhou.rdi.mc.client.network;

import calebxzhou.rdi.mc.common2.mcp.RMcpBlockEntityData;
import calebxzhou.rdi.mc.common2.mcp.RMcpEndpointException;
import calebxzhou.rdi.mc.common2.mcp.RMcpHarvestToolData;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

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

    private static <T> T requestServer(String action, Object request, Class<T> responseClass) {
        if (!FMLEnvironment.dist.isClient()) {
            throw new RMcpEndpointException("server_mcp_unavailable");
        }
        var minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        if (minecraft.player == null || connection == null || !connection.hasChannel(RMcpPayload.TYPE)) {
            throw new RMcpEndpointException("server_mcp_unavailable");
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
            throw new RMcpEndpointException("server_timeout");
        } catch (Exception e) {
            throw new RMcpEndpointException("server_mcp_unavailable");
        } finally {
            RMcpNetwork.removePending(requestId);
        }
    }

    private record BlockEntityRequest(String dim, int x, int y, int z) {
    }

    private record HarvestToolRequest(String blockId, String dim, Integer x, Integer y, Integer z) {
    }
}
