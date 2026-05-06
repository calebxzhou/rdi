package calebxzhou.rdi.mc.common2.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.fusionauth.http.HTTPMethod;
import io.fusionauth.http.server.HTTPListenerConfiguration;
import io.fusionauth.http.server.HTTPRequest;
import io.fusionauth.http.server.HTTPResponse;
import io.fusionauth.http.server.HTTPServer;
import io.fusionauth.http.server.HTTPServerConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class RMcpHttpServer {
    public static final int DEFAULT_PORT = 65232;
    private static final Gson GSON = new GsonBuilder().create();
    private static HTTPServer server;
    private static RMcpGameConnector connector;

    private RMcpHttpServer() {
    }

    public static synchronized void start(RMcpGameConnector connector) {
        start(DEFAULT_PORT, connector);
    }

    public static synchronized void start(int port, RMcpGameConnector newConnector) {
        if (server != null) {
            connector = newConnector;
            return;
        }
        connector = newConnector;
        server = new HTTPServer()
                .withConfiguration(new HTTPServerConfiguration()
                        .withCompressByDefault(false)
                        .withHandler(RMcpHttpServer::handle)
                        .withListener(new HTTPListenerConfiguration(InetAddress.getLoopbackAddress(), port)))
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(RMcpHttpServer::stop, "rdi-mcp-http-stop"));
    }

    public static synchronized void stop() {
        if (server == null) {
            return;
        }
        server.close();
        server = null;
    }

    private static void handle(HTTPRequest request, HTTPResponse response) throws IOException {
        if (!request.getMethod().is(HTTPMethod.GET)) {
            writeJson(response, 405, RMcpResponse.error("method_not_allowed"));
            return;
        }
        if ("/prompts".equals(request.getPath()) || "/".equals(request.getPath())) {
            handlePrompts(response);
            return;
        }
        if ("/test".equals(request.getPath())) {
            handleTest(response);
            return;
        }

        if ("/pos".equals(request.getPath())) {
            handlePos(response);
            return;
        }
        if ("/mainhand".equals(request.getPath())) {
            handleMainHand(response);
            return;
        }
        if ("/screenshot".equals(request.getPath())) {
            handleScreenshot(response);
            return;
        }
        if ("/recipe".equals(request.getPath())) {
            handleRecipe(request, response);
            return;
        }
        if ("/chunk".equals(request.getPath())) {
            handleChunk(request, response);
            return;
        }
        if ("/section".equals(request.getPath())) {
            handleSection(request, response);
            return;
        }
        if ("/nearby-resources".equals(request.getPath())) {
            handleNearbyResources(request, response);
            return;
        }
        if ("/nearby-entities".equals(request.getPath())) {
            handleNearbyEntities(request, response);
            return;
        }
        if ("/staring-block".equals(request.getPath())) {
            handleStaringBlock(request, response);
            return;
        }
        if ("/blockstate".equals(request.getPath())) {
            handleBlockState(request, response);
            return;
        }
        if ("/blockentity".equals(request.getPath())) {
            handleBlockEntity(request, response);
            return;
        }
        if ("/harvest-tool".equals(request.getPath())) {
            handleHarvestTool(request, response);
            return;
        }
        if ("/staring-entity".equals(request.getPath())) {
            handleStaringEntity(response);
            return;
        }
        if ("/entity".equals(request.getPath())) {
            handleEntity(request, response);
            return;
        }
        if ("/player".equals(request.getPath())) {
            handlePlayer(request, response);
            return;
        }
        if ("/langkey".equals(request.getPath())) {
            handleLangKey(request, response);
            return;
        }
        if ("/langkey-search".equals(request.getPath())) {
            handleLangKeySearch(request, response);
            return;
        }
        writeJson(response, 404, RMcpResponse.error("not_found"));
    }

    private static void handleTest(HTTPResponse response) throws IOException {
        try {
            writeJson(response, 200, RMcpResponse.ok(connector.testData()));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handlePrompts(HTTPResponse response) throws IOException {
        try (var input = RMcpHttpServer.class.getClassLoader().getResourceAsStream("mcp_api_doc.md")) {
            if (input == null) {
                writeJson(response, 500, RMcpResponse.error("prompts_not_found"));
                return;
            }
            response.setStatus(200);
            response.setContentType("text/markdown; charset=utf-8");
            response.getWriter().write(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void handlePos(HTTPResponse response) throws IOException {
        try {
            var pos = connector.posData();
            if (pos == null) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(pos));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleMainHand(HTTPResponse response) throws IOException {
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(connector.mainHandItemData()));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleScreenshot(HTTPResponse response) throws IOException {
        try {
            var data = connector.screenshotPngData().get(5, TimeUnit.SECONDS);
            response.setStatus(200);
            response.setContentType("image/png");
            response.setContentLength(data.length);
            response.setHeader("Cache-Control", "no-store");
            response.getOutputStream().write(data);
        } catch (TimeoutException e) {
            writeJson(response, 504, RMcpResponse.error("screenshot_timeout"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeJson(response, 500, RMcpResponse.error("screenshot_failed"));
        } catch (ExecutionException e) {
            writeJson(response, 500, RMcpResponse.error("screenshot_failed"));
        }
    }

    private static void handleRecipe(HTTPRequest request, HTTPResponse response) throws IOException {
        var itemId = request.getURLParameter("itemId");
        if (itemId == null || itemId.isBlank()) {
            writeJson(response, 400, RMcpResponse.error("missing_item_id"));
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            var recipes = connector.recipeData(itemId.trim());
            if (recipes == null) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(recipes));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleChunk(HTTPRequest request, HTTPResponse response) throws IOException {
        var chunkX = readIntParam(request, response, "x", "missing_chunk_x", "bad_chunk_x");
        if (chunkX == null) {
            return;
        }
        var chunkZ = readIntParam(request, response, "z", "missing_chunk_z", "bad_chunk_z");
        if (chunkZ == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(connector.chunkData(chunkX, chunkZ)));
        } catch (RMcpEndpointException e) {
            writeJson(response, endpointStatus(e.code()), RMcpResponse.error(e.code()));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleSection(HTTPRequest request, HTTPResponse response) throws IOException {
        var chunkX = readIntParam(request, response, "x", "missing_chunk_x", "bad_chunk_x");
        if (chunkX == null) {
            return;
        }
        var sectionY = readIntParam(request, response, "y", "missing_section_y", "bad_section_y");
        if (sectionY == null) {
            return;
        }
        var chunkZ = readIntParam(request, response, "z", "missing_chunk_z", "bad_chunk_z");
        if (chunkZ == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(connector.sectionData(chunkX, sectionY, chunkZ)));
        } catch (RMcpEndpointException e) {
            writeJson(response, endpointStatus(e.code()), RMcpResponse.error(e.code()));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static Integer readIntParam(HTTPRequest request, HTTPResponse response, String name, String missingCode, String badCode) throws IOException {
        var text = request.getURLParameter(name);
        if (text == null || text.isBlank()) {
            writeJson(response, 400, RMcpResponse.error(missingCode));
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            writeJson(response, 400, RMcpResponse.error(badCode));
            return null;
        }
    }

    private static Integer readOptionalIntParam(HTTPRequest request, HTTPResponse response, String name, int defaultValue, String badCode) throws IOException {
        var text = request.getURLParameter(name);
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            writeJson(response, 400, RMcpResponse.error(badCode));
            return null;
        }
    }

    private static Double readOptionalDoubleParam(HTTPRequest request, HTTPResponse response, String name, double defaultValue, String badCode) throws IOException {
        var text = request.getURLParameter(name);
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            writeJson(response, 400, RMcpResponse.error(badCode));
            return null;
        }
    }

    private static void handleNearbyResources(HTTPRequest request, HTTPResponse response) throws IOException {
        var pos = readPos(request, response);
        if (pos == null) {
            return;
        }
        var chunkRadius = readOptionalIntParam(request, response, "chunkRadius", 2, "bad_chunk_radius");
        if (chunkRadius == null) {
            return;
        }
        var sectionRadius = readOptionalIntParam(request, response, "sectionRadius", 1, "bad_section_radius");
        if (sectionRadius == null) {
            return;
        }
        if (chunkRadius < 0 || chunkRadius > 4) {
            writeJson(response, 400, RMcpResponse.error("bad_chunk_radius"));
            return;
        }
        if (sectionRadius < 0 || sectionRadius > 4) {
            writeJson(response, 400, RMcpResponse.error("bad_section_radius"));
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            if (!playerPos.dim().equals(pos.dim())) {
                writeJson(response, 409, RMcpResponse.error("dim_not_loaded"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(connector.nearbyResourcesData(pos.dim(), pos.x(), pos.y(), pos.z(), chunkRadius, sectionRadius)));
        } catch (RMcpEndpointException e) {
            writeJson(response, endpointStatus(e.code()), RMcpResponse.error(e.code()));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleNearbyEntities(HTTPRequest request, HTTPResponse response) throws IOException {
        var posText = request.getURLParameter("pos");
        var pos = readOptionalPos(request, response);
        if (posText != null && !posText.isBlank() && pos == null) {
            return;
        }
        var radius = readOptionalDoubleParam(request, response, "radius", 64.0, "bad_radius");
        if (radius == null) {
            return;
        }
        if (!Double.isFinite(radius) || radius < 0.0 || radius > 128.0) {
            writeJson(response, 400, RMcpResponse.error("bad_radius"));
            return;
        }
        var limit = readOptionalIntParam(request, response, "limit", 64, "bad_limit");
        if (limit == null) {
            return;
        }
        if (limit < 1 || limit > 128) {
            writeJson(response, 400, RMcpResponse.error("bad_limit"));
            return;
        }
        var categories = readCategories(request.getURLParameter("category"));
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            if (pos == null) {
                pos = new ParsedPos(
                        playerPos.dim(),
                        (int) Math.floor(playerPos.x()),
                        (int) Math.floor(playerPos.y()),
                        (int) Math.floor(playerPos.z())
                );
            } else if (!playerPos.dim().equals(pos.dim())) {
                writeJson(response, 409, RMcpResponse.error("dim_not_loaded"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(connector.nearbyEntitiesData(pos.dim(), pos.x(), pos.y(), pos.z(), radius, categories, limit)));
        } catch (RMcpEndpointException e) {
            writeJson(response, endpointStatus(e.code()), RMcpResponse.error(e.code()));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static java.util.List<String> readCategories(String text) {
        if (text == null || text.isBlank()) {
            return java.util.List.of("monster", "animal");
        }
        var categories = new java.util.ArrayList<String>();
        for (var part : text.split(",")) {
            var category = part.trim().toLowerCase();
            if (!category.isEmpty() && !categories.contains(category)) {
                categories.add(category);
            }
        }
        return categories.isEmpty() ? java.util.List.of("monster", "animal") : java.util.List.copyOf(categories);
    }

    private static void handleStaringBlock(HTTPRequest request, HTTPResponse response) throws IOException {
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            var block = connector.staringBlockData("true".equals(request.getURLParameter("fluid")));
            if (block == null) {
                writeJson(response, 404, RMcpResponse.error("no_block"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(block));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleBlockState(HTTPRequest request, HTTPResponse response) throws IOException {
        var pos = readPos(request, response);
        if (pos == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            if (!playerPos.dim().equals(pos.dim())) {
                writeJson(response, 409, RMcpResponse.error("dim_not_loaded"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(connector.blockStateData(pos.dim(), pos.x(), pos.y(), pos.z())));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static ParsedPos readPos(HTTPRequest request, HTTPResponse response) throws IOException {
        var posText = request.getURLParameter("pos");
        if (posText == null || posText.isBlank()) {
            writeJson(response, 400, RMcpResponse.error("missing_pos"));
            return null;
        }
        var parts = posText.split(",");
        if (parts.length != 4) {
            writeJson(response, 400, RMcpResponse.error("bad_pos"));
            return null;
        }
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(parts[1].trim());
            y = Integer.parseInt(parts[2].trim());
            z = Integer.parseInt(parts[3].trim());
        } catch (NumberFormatException e) {
            writeJson(response, 400, RMcpResponse.error("bad_pos"));
            return null;
        }
        var dim = parts[0].trim();
        if (dim.isEmpty()) {
            writeJson(response, 400, RMcpResponse.error("bad_pos"));
            return null;
        }
        return new ParsedPos(dim, x, y, z);
    }

    private static ParsedPos readOptionalPos(HTTPRequest request, HTTPResponse response) throws IOException {
        var posText = request.getURLParameter("pos");
        if (posText == null || posText.isBlank()) {
            return null;
        }
        var parts = posText.split(",");
        if (parts.length != 4) {
            writeJson(response, 400, RMcpResponse.error("bad_pos"));
            return null;
        }
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(parts[1].trim());
            y = Integer.parseInt(parts[2].trim());
            z = Integer.parseInt(parts[3].trim());
        } catch (NumberFormatException e) {
            writeJson(response, 400, RMcpResponse.error("bad_pos"));
            return null;
        }
        var dim = parts[0].trim();
        if (dim.isEmpty()) {
            writeJson(response, 400, RMcpResponse.error("bad_pos"));
            return null;
        }
        return new ParsedPos(dim, x, y, z);
    }

    private static void handleBlockEntity(HTTPRequest request, HTTPResponse response) throws IOException {
        var pos = readPos(request, response);
        if (pos == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            if (!playerPos.dim().equals(pos.dim())) {
                writeJson(response, 409, RMcpResponse.error("dim_not_loaded"));
                return;
            }
            var blockEntity = connector.blockEntityData(pos.dim(), pos.x(), pos.y(), pos.z());
            if (blockEntity == null) {
                writeJson(response, 404, RMcpResponse.error("no_block_entity"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(blockEntity));
        } catch (RMcpEndpointException e) {
            writeJson(response, endpointStatus(e.code()), RMcpResponse.error(e.code()));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleHarvestTool(HTTPRequest request, HTTPResponse response) throws IOException {
        var blockId = request.getURLParameter("blockId");
        if (blockId != null) {
            blockId = blockId.trim();
        }
        var posText = request.getURLParameter("pos");
        if ((blockId == null || blockId.isBlank()) && (posText == null || posText.isBlank())) {
            writeJson(response, 400, RMcpResponse.error("missing_block_id"));
            return;
        }
        var pos = readOptionalPos(request, response);
        if (posText != null && !posText.isBlank() && pos == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            if (pos != null && !playerPos.dim().equals(pos.dim())) {
                writeJson(response, 409, RMcpResponse.error("dim_not_loaded"));
                return;
            }
            var data = connector.harvestToolData(
                    blockId == null || blockId.isBlank() ? null : blockId,
                    pos == null ? null : pos.dim(),
                    pos == null ? null : pos.x(),
                    pos == null ? null : pos.y(),
                    pos == null ? null : pos.z()
            );
            writeJson(response, 200, RMcpResponse.ok(data));
        } catch (RMcpEndpointException e) {
            writeJson(response, endpointStatus(e.code()), RMcpResponse.error(e.code()));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleStaringEntity(HTTPResponse response) throws IOException {
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            var entity = connector.staringEntityData();
            if (entity == null) {
                writeJson(response, 404, RMcpResponse.error("no_entity"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(entity));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleEntity(HTTPRequest request, HTTPResponse response) throws IOException {
        var uuidText = request.getURLParameter("uuid");
        if (uuidText == null || uuidText.isBlank()) {
            writeJson(response, 400, RMcpResponse.error("missing_uuid"));
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText.trim());
        } catch (IllegalArgumentException e) {
            writeJson(response, 400, RMcpResponse.error("bad_uuid"));
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            var entity = connector.entityData(uuid);
            if (entity == null) {
                writeJson(response, 404, RMcpResponse.error("no_entity"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(entity));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handlePlayer(HTTPRequest request, HTTPResponse response) throws IOException {
        var uuidText = request.getURLParameter("uuid");
        UUID uuid = null;
        if (uuidText != null && !uuidText.isBlank()) {
            try {
                uuid = UUID.fromString(uuidText.trim());
            } catch (IllegalArgumentException e) {
                writeJson(response, 400, RMcpResponse.error("bad_uuid"));
                return;
            }
        }
        try {
            if (!connector.playerInWorld()) {
                writeJson(response, 409, RMcpResponse.error("no_player"));
                return;
            }
            if ("true".equals(request.getURLParameter("detail"))) {
                var player = connector.playerDetailData(uuid);
                if (player == null) {
                    writeJson(response, 404, RMcpResponse.error("no_player_entity"));
                    return;
                }
                writeJson(response, 200, RMcpResponse.ok(player));
                return;
            }
            var player = connector.playerData(uuid);
            if (player == null) {
                writeJson(response, 404, RMcpResponse.error("no_player_entity"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(player));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleLangKeySearch(HTTPRequest request, HTTPResponse response) throws IOException {
        var text = request.getURLParameter("text");
        if (text == null || text.isBlank()) {
            writeJson(response, 400, RMcpResponse.error("missing_text"));
            return;
        }
        try {
            writeJson(response, 200, RMcpResponse.ok(connector.langKeyIndex().search(text)));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void handleLangKey(HTTPRequest request, HTTPResponse response) throws IOException {
        var key = request.getURLParameter("key");
        if (key == null || key.isBlank()) {
            writeJson(response, 400, RMcpResponse.error("missing_key"));
            return;
        }
        try {
            var data = connector.langKeyIndex().langKey(key.trim());
            if (data == null) {
                writeJson(response, 404, RMcpResponse.error("no_langkey"));
                return;
            }
            writeJson(response, 200, RMcpResponse.ok(data));
        } catch (Exception e) {
            writeJson(response, 500, RMcpResponse.error("internal_error"));
        }
    }

    private static void writeJson(HTTPResponse response, int status, RMcpResponse<?> data) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=utf-8");
        response.getWriter().write(GSON.toJson(data));
    }

    private static int endpointStatus(String code) {
        return switch (code) {
            case "no_block_entity", "chunk_not_loaded" -> 404;
            case "bad_block_id", "missing_block_id", "bad_request", "bad_radius", "bad_limit", "section_out_of_range" -> 400;
            case "dim_not_loaded", "server_mcp_unavailable" -> 409;
            case "server_timeout" -> 504;
            default -> 500;
        };
    }

    private record ParsedPos(String dim, int x, int y, int z) {
    }
}
