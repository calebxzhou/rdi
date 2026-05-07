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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

public final class RMHttpServer {
    private static final int BLOCK_BATCH_LIMIT = 512;
    private static final Gson GSON = new GsonBuilder().create();
    private static HTTPServer server;
    private static RMcpGameConnector connector;

    private RMHttpServer() {
    }

    private record Route(HTTPMethod method, String endpoint, boolean prefix,
                         BiConsumer<HTTPRequest, HTTPResponse> handler) {
        public static final List<Route> LIST;

        static {
            LIST = List.of(
                    get("/", RMHttpServer::handlePrompts),
                    get("/prompts", RMHttpServer::handlePrompts),
                    getPrefix("/apidoc/", RMHttpServer::handleApiDoc),
                    getPrefix("/errcode/", RMHttpServer::handleErrCode),
                    get("/test", RMHttpServer::handleTest),
                    get("/pos", RMHttpServer::handlePos),
                    get("/inventory", RMHttpServer::handleInventory),
                    get("/situation", RMHttpServer::handleSituation),
                    get("/mainhand", RMHttpServer::handleMainHand),
                    get("/screenshot", RMHttpServer::handleScreenshot),
                    get("/recipe", RMHttpServer::handleRecipe),
                    get("/chunk", RMHttpServer::handleChunk),
                    get("/section", RMHttpServer::handleSection),
                    get("/nearby-resources", RMHttpServer::handleNearbyResources),
                    get("/nearby-entities", RMHttpServer::handleNearbyEntities),
                    get("/staring-block", RMHttpServer::handleStaringBlock),
                    get("/blockstate", RMHttpServer::handleBlockState),
                    post("/blockstate/batch", RMHttpServer::handleBlockStateBatch),
                    get("/blockentity", RMHttpServer::handleBlockEntity),
                    get("/container", RMHttpServer::handleContainer),
                    get("/harvest-tool", RMHttpServer::handleHarvestTool),
                    get("/staring-entity", RMHttpServer::handleStaringEntity),
                    get("/entity", RMHttpServer::handleEntity),
                    get("/player", RMHttpServer::handlePlayer),
                    get("/langkey", RMHttpServer::handleLangKey),
                    get("/langkey-search", RMHttpServer::handleLangKeySearch),
                    post("/inventory/swap", RMHttpServer::handleInventorySwap),
                    post("/inventory/move", RMHttpServer::handleInventoryMove),
                    post("/crafting/open", RMHttpServer::handleCraftingOpen),
                    post("/craft", RMHttpServer::handleCraft),
                    post("/container/move", RMHttpServer::handleContainerMove),
                    post("/place", RMHttpServer::handlePlace),
                    post("/break", RMHttpServer::handleBreak),
                    post("/place/batch", RMHttpServer::handlePlaceBatch),
                    post("/break/batch", RMHttpServer::handleBreakBatch),
                    post("/place/box", RMHttpServer::handlePlaceBox),
                    post("/break/box", RMHttpServer::handleBreakBox)
            );
        }

        private static Route get(String endpoint, BiConsumer<HTTPRequest, HTTPResponse> handler) {
            return new Route(HTTPMethod.GET, endpoint, false, handler);
        }

        private static Route getPrefix(String endpoint, BiConsumer<HTTPRequest, HTTPResponse> handler) {
            return new Route(HTTPMethod.GET, endpoint, true, handler);
        }

        private static Route post(String endpoint, BiConsumer<HTTPRequest, HTTPResponse> handler) {
            return new Route(HTTPMethod.POST, endpoint, false, handler);
        }

        private boolean matches(HTTPRequest request) {
            return request.getMethod().is(method) && (prefix ? request.getPath().startsWith(endpoint) : endpoint.equals(request.getPath()));
        }
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
                        .withHandler(RMHttpServer::handle)
                        .withListener(new HTTPListenerConfiguration(InetAddress.getLoopbackAddress(), port)))
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(RMHttpServer::stop, "rdi-mcp-http-stop"));
    }

    public static synchronized void stop() {
        if (server == null) {
            return;
        }
        server.close();
        server = null;
    }

    private static void handle(HTTPRequest request, HTTPResponse response) {
        if (!request.getMethod().is(HTTPMethod.GET) && !request.getMethod().is(HTTPMethod.POST)) {
            err400(response, RErrorCode.METHOD_NOT_ALLOWED);
            return;
        }
        for (var route : Route.LIST) {
            if (route.matches(request)) {
                route.handler().accept(request, response);
                return;
            }
        }
        err404(response);
    }

    private static void handleTest(HTTPRequest request, HTTPResponse response) {
        try {
            ok(response, connector.testData());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handlePrompts(HTTPRequest request, HTTPResponse response) {
        var doc = readApiDoc();
        if (doc == null) {
            err400(response, RErrorCode.PROMPTS_NOT_FOUND);
            return;
        }
        writeMarkdown(response, doc);
    }

    private static void handleErrCode(HTTPRequest request, HTTPResponse response) {
        var prefix = "/errcode/";
        var path = request.getPath();
        if (path.length() <= prefix.length()) {
            err400(response, RErrorCode.MISSING_ERRCODE);
            return;
        }
        var code = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8).trim();
        if (code.isEmpty() || code.contains("/") || code.contains(" ") || code.contains("`")) {
            err400(response, RErrorCode.BAD_ERRCODE);
            return;
        }
        var errcode = RErrorCode.get(code);
        if (errcode == null) {
            err400(response, RErrorCode.UNKNOWN_ERRCODE);
            return;
        }
        ok(response, errcode);
    }

    private static void handleApiDoc(HTTPRequest request, HTTPResponse response) {
        var prefix = "/apidoc/";
        var path = request.getPath();
        if (path.length() <= prefix.length()) {
            err400(response, RErrorCode.MISSING_APIDOC);
            return;
        }
        var file = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8).trim();
        if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.contains("..") || !file.endsWith(".md")) {
            err400(response, RErrorCode.BAD_APIDOC);
            return;
        }
        var doc = readResourceText("mcp/apidoc/" + file);
        if (doc == null) {
            err400(response, RErrorCode.UNKNOWN_APIDOC);
            return;
        }
        writeMarkdown(response, doc);
    }

    private static String readApiDoc() {
        return readResourceText("mcp/summary.md");
    }


    private static String readResourceText(String path) {
        try (var input = RMHttpServer.class.getClassLoader().getResourceAsStream(path)) {
            return input == null ? null : new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeMarkdown(HTTPResponse response, String markdown) {
        try {
            response.setStatus(200);
            response.setContentType("text/markdown; charset=utf-8");
            response.getWriter().write(markdown);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handlePos(HTTPRequest request, HTTPResponse response) {
        try {
            var pos = connector.posData();
            if (pos == null) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, pos);
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleMainHand(HTTPRequest request, HTTPResponse response) {
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.mainHandItemData());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleInventory(HTTPRequest request, HTTPResponse response) {
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.inventoryData());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleInventorySwap(HTTPRequest request, HTTPResponse response) {
        var swapRequest = readInventorySwapRequest(request);
        if (swapRequest == null || swapRequest.from() == null || swapRequest.from().isBlank() || swapRequest.to() == null || swapRequest.to().isBlank()) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.swapInventorySlots(swapRequest.from().trim(), swapRequest.to().trim(), swapRequest.dryRun()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static InventorySwapRequest readInventorySwapRequest(HTTPRequest request) {
        try {
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    return GSON.fromJson(body, InventorySwapRequest.class);
                }
            }
            return new InventorySwapRequest(
                    request.getURLParameter("from"),
                    request.getURLParameter("to"),
                    Boolean.parseBoolean(String.valueOf(request.getURLParameter("dryRun")))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleInventoryMove(HTTPRequest request, HTTPResponse response) {
        var moveRequest = readInventoryMoveRequest(request);
        if (moveRequest == null || moveRequest.from() == null || moveRequest.from().isBlank() || moveRequest.to() == null || moveRequest.to().isBlank()) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.moveInventoryItems(moveRequest.from().trim(), moveRequest.to().trim(), moveRequest.count(), moveRequest.dryRun()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static InventoryMoveRequest readInventoryMoveRequest(HTTPRequest request) {
        try {
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    return GSON.fromJson(body, InventoryMoveRequest.class);
                }
            }
            return new InventoryMoveRequest(
                    request.getURLParameter("from"),
                    request.getURLParameter("to"),
                    Integer.parseInt(String.valueOf(request.getURLParameter("count"))),
                    Boolean.parseBoolean(String.valueOf(request.getURLParameter("dryRun")))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleCraftingOpen(HTTPRequest request, HTTPResponse response) {
        var openRequest = readCraftingOpenRequest(request);
        if (openRequest == null) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (openRequest.radius() < 0 || openRequest.radius() > 8) {
            err400(response, RErrorCode.BAD_CRAFTING_RADIUS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.openCrafting(openRequest.radius(), openRequest.dryRun()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static CraftingOpenRequest readCraftingOpenRequest(HTTPRequest request) {
        try {
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    var parsed = GSON.fromJson(body, CraftingOpenRequest.class);
                    if (parsed == null) {
                        return null;
                    }
                    return new CraftingOpenRequest(parsed.radius() == null ? 4 : parsed.radius(), parsed.dryRun());
                }
            }
            var radiusText = request.getURLParameter("radius");
            var radius = radiusText == null || radiusText.isBlank() ? 4 : Integer.parseInt(radiusText.trim());
            return new CraftingOpenRequest(
                    radius,
                    Boolean.parseBoolean(String.valueOf(request.getURLParameter("dryRun")))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleCraft(HTTPRequest request, HTTPResponse response) {
        var craftRequest = readCraftRequest(request);
        if (craftRequest == null) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (craftRequest.slots() == null || craftRequest.slots().isEmpty() || craftRequest.shape() == null || craftRequest.shape().isBlank()) {
            err400(response, RErrorCode.BAD_SHAPE);
            return;
        }
        if (craftRequest.outputSlot() == null) {
            err400(response, RErrorCode.BAD_SLOT);
            return;
        }
        if (craftRequest.times() <= 0 || craftRequest.times() > 64) {
            err400(response, RErrorCode.BAD_COUNT);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.craft(craftRequest.slots(), craftRequest.shape().trim(), craftRequest.outputSlot(), craftRequest.times(), craftRequest.dryRun()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static CraftRequest readCraftRequest(HTTPRequest request) {
        try {
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    var parsed = GSON.fromJson(body, CraftRequest.class);
                    if (parsed == null) {
                        return null;
                    }
                    return new CraftRequest(parsed.slots(), parsed.shape(), parsed.outputSlot(), parsed.times() == null ? 1 : parsed.times(), parsed.dryRun());
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void handlePlace(HTTPRequest request, HTTPResponse response) {
        var pos = readBlockActionRequest(request);
        if (pos == null) {
            err400(response, RErrorCode.BAD_POS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.placeBlock(pos.x(), pos.y(), pos.z()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleBreak(HTTPRequest request, HTTPResponse response) {
        var pos = readBlockActionRequest(request);
        if (pos == null) {
            err400(response, RErrorCode.BAD_POS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.breakBlock(pos.x(), pos.y(), pos.z()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static BlockActionRequest readBlockActionRequest(HTTPRequest request) {
        try {
            return new BlockActionRequest(
                    Integer.parseInt(String.valueOf(request.getURLParameter("x"))),
                    Integer.parseInt(String.valueOf(request.getURLParameter("y"))),
                    Integer.parseInt(String.valueOf(request.getURLParameter("z")))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static void handlePlaceBatch(HTTPRequest request, HTTPResponse response) {
        var batch = readBlockBatchActionRequest(request);
        if (batch == null || batch.positions() == null || batch.positions().isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS);
            return;
        }
        if (batch.positions().stream().anyMatch(Objects::isNull)) {
            err400(response, RErrorCode.BAD_POSITIONS);
            return;
        }
        if (batch.positions().size() > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.placeBlocks(batch.positions()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleBreakBatch(HTTPRequest request, HTTPResponse response) {
        var batch = readBlockBatchActionRequest(request);
        if (batch == null || batch.positions() == null || batch.positions().isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS);
            return;
        }
        if (batch.positions().size() > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.breakBlocks(batch.positions()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static BlockBatchActionRequest readBlockBatchActionRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            return GSON.fromJson(body, BlockBatchActionRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static void handlePlaceBox(HTTPRequest request, HTTPResponse response) {
        var box = readBlockBoxActionRequest(request);
        if (box == null || box.from() == null || box.to() == null) {
            err400(response, RErrorCode.BAD_BOX);
            return;
        }
        if (boxBlockCount(box.from(), box.to()) > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.placeBlockBox(box.from(), box.to()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleBreakBox(HTTPRequest request, HTTPResponse response) {
        var box = readBlockBoxActionRequest(request);
        if (box == null || box.from() == null || box.to() == null) {
            err400(response, RErrorCode.BAD_BOX);
            return;
        }
        if (boxBlockCount(box.from(), box.to()) > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.breakBlockBox(box.from(), box.to()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static BlockBoxActionRequest readBlockBoxActionRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            return GSON.fromJson(body, BlockBoxActionRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static long boxBlockCount(RMcpBlockPosData from, RMcpBlockPosData to) {
        return (long) (Math.abs(from.x() - to.x()) + 1)
                * (Math.abs(from.y() - to.y()) + 1)
                * (Math.abs(from.z() - to.z()) + 1);
    }

    private static void handleContainer(HTTPRequest request, HTTPResponse response) {
        var pos = request.getURLParameter("pos");
        if (pos == null || pos.isBlank()) {
            err400(response, RErrorCode.MISSING_POS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.containerData(pos.trim(), request.getURLParameter("side")));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleContainerMove(HTTPRequest request, HTTPResponse response) {
        var moveRequest = readContainerMoveRequest(request);
        if (moveRequest == null || moveRequest.from() == null || moveRequest.to() == null) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (moveRequest.from().pos() == null || moveRequest.from().pos().isBlank() || moveRequest.to().pos() == null || moveRequest.to().pos().isBlank()) {
            err400(response, RErrorCode.MISSING_POS);
            return;
        }
        if (moveRequest.from().slot() == null) {
            err400(response, RErrorCode.BAD_SLOT);
            return;
        }
        if (moveRequest.count() <= 0) {
            err400(response, RErrorCode.BAD_COUNT);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.moveContainerItems(
                    moveRequest.from().pos().trim(),
                    moveRequest.from().side(),
                    moveRequest.from().slot(),
                    moveRequest.to().pos().trim(),
                    moveRequest.to().side(),
                    moveRequest.to().slot(),
                    moveRequest.count(),
                    moveRequest.dryRun()
            ));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static ContainerMoveRequest readContainerMoveRequest(HTTPRequest request) {
        try {
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    return GSON.fromJson(body, ContainerMoveRequest.class);
                }
            }
            return new ContainerMoveRequest(
                    new ContainerEndpointRequest(
                            request.getURLParameter("fromPos"),
                            request.getURLParameter("fromSide"),
                            Integer.parseInt(String.valueOf(request.getURLParameter("fromSlot")))
                    ),
                    new ContainerEndpointRequest(
                            request.getURLParameter("toPos"),
                            request.getURLParameter("toSide"),
                            request.getURLParameter("toSlot") == null || request.getURLParameter("toSlot").isBlank() ? null : Integer.parseInt(request.getURLParameter("toSlot").trim())
                    ),
                    Integer.parseInt(String.valueOf(request.getURLParameter("count"))),
                    Boolean.parseBoolean(String.valueOf(request.getURLParameter("dryRun")))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleSituation(HTTPRequest request, HTTPResponse response) {
        var entityRadius = readOptionalDoubleParam(request, response, "entityRadius", 32.0, RErrorCode.BAD_RADIUS);
        if (entityRadius == null) {
            return;
        }
        if (!Double.isFinite(entityRadius) || entityRadius < 0.0 || entityRadius > 128.0) {
            err400(response, RErrorCode.BAD_RADIUS);
            return;
        }
        var resourceChunkRadius = readOptionalIntParam(request, response, "resourceChunkRadius", 1, RErrorCode.BAD_CHUNK_RADIUS);
        if (resourceChunkRadius == null) {
            return;
        }
        if (resourceChunkRadius < 0 || resourceChunkRadius > 4) {
            err400(response, RErrorCode.BAD_CHUNK_RADIUS);
            return;
        }
        var resourceSectionRadius = readOptionalIntParam(request, response, "resourceSectionRadius", 1, RErrorCode.BAD_SECTION_RADIUS);
        if (resourceSectionRadius == null) {
            return;
        }
        if (resourceSectionRadius < 0 || resourceSectionRadius > 4) {
            err400(response, RErrorCode.BAD_SECTION_RADIUS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.situationData(entityRadius, resourceChunkRadius, resourceSectionRadius));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleScreenshot(HTTPRequest request,HTTPResponse response) {
        try {
            var data = connector.screenshotPngData().get(5, TimeUnit.SECONDS);
            response.setStatus(200);
            response.setContentType("image/png");
            response.setContentLength(data.length);
            response.setHeader("Cache-Control", "no-store");
            response.getOutputStream().write(data);
        } catch (TimeoutException e) {
            err400(response, RErrorCode.SCREENSHOT_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err400(response, RErrorCode.SCREENSHOT_FAILED);
        } catch (ExecutionException e) {
            err400(response, RErrorCode.SCREENSHOT_FAILED);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void handleRecipe(HTTPRequest request, HTTPResponse response) {
        var itemId = request.getURLParameter("itemId");
        if (itemId == null || itemId.isBlank()) {
            err400(response, RErrorCode.MISSING_ITEM_ID);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var recipes = connector.recipeData(itemId.trim());
            if (recipes == null) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, recipes);
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleChunk(HTTPRequest request, HTTPResponse response) {
        var chunkX = readIntParam(request, response, "x", RErrorCode.MISSING_CHUNK_X, RErrorCode.BAD_CHUNK_X);
        if (chunkX == null) {
            return;
        }
        var chunkZ = readIntParam(request, response, "z", RErrorCode.MISSING_CHUNK_Z, RErrorCode.BAD_CHUNK_Z);
        if (chunkZ == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.chunkData(chunkX, chunkZ));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleSection(HTTPRequest request, HTTPResponse response) {
        var chunkX = readIntParam(request, response, "x", RErrorCode.MISSING_CHUNK_X, RErrorCode.BAD_CHUNK_X);
        if (chunkX == null) {
            return;
        }
        var sectionY = readIntParam(request, response, "y", RErrorCode.MISSING_SECTION_Y, RErrorCode.BAD_SECTION_Y);
        if (sectionY == null) {
            return;
        }
        var chunkZ = readIntParam(request, response, "z", RErrorCode.MISSING_CHUNK_Z, RErrorCode.BAD_CHUNK_Z);
        if (chunkZ == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.sectionData(chunkX, sectionY, chunkZ));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static Integer readIntParam(HTTPRequest request, HTTPResponse response, String name, RErrorCode missingCode, RErrorCode badCode) {
        var text = request.getURLParameter(name);
        if (text == null || text.isBlank()) {
            err400(response, missingCode);
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            err400(response, badCode);
            return null;
        }
    }

    private static Integer readOptionalIntParam(HTTPRequest request, HTTPResponse response, String name, int defaultValue, RErrorCode badCode) {
        var text = request.getURLParameter(name);
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            err400(response, badCode);
            return null;
        }
    }

    private static Double readOptionalDoubleParam(HTTPRequest request, HTTPResponse response, String name, double defaultValue, RErrorCode badCode) {
        var text = request.getURLParameter(name);
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            err400(response, badCode);
            return null;
        }
    }

    private static void handleNearbyResources(HTTPRequest request, HTTPResponse response) {
        var posText = request.getURLParameter("pos");
        var pos = readOptionalPos(request, response);
        if (posText != null && !posText.isBlank() && pos == null) {
            return;
        }
        var chunkRadius = readOptionalIntParam(request, response, "chunkRadius", 2, RErrorCode.BAD_CHUNK_RADIUS);
        if (chunkRadius == null) {
            return;
        }
        var sectionRadius = readOptionalIntParam(request, response, "sectionRadius", 1, RErrorCode.BAD_SECTION_RADIUS);
        if (sectionRadius == null) {
            return;
        }
        if (chunkRadius < 0 || chunkRadius > 4) {
            err400(response, RErrorCode.BAD_CHUNK_RADIUS);
            return;
        }
        if (sectionRadius < 0 || sectionRadius > 4) {
            err400(response, RErrorCode.BAD_SECTION_RADIUS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER);
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
                err400(response, RErrorCode.DIM_NOT_LOADED);
                return;
            }
            ok(response, connector.nearbyResourcesData(pos.dim(), pos.x(), pos.y(), pos.z(), chunkRadius, sectionRadius));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleNearbyEntities(HTTPRequest request, HTTPResponse response) {
        var posText = request.getURLParameter("pos");
        var pos = readOptionalPos(request, response);
        if (posText != null && !posText.isBlank() && pos == null) {
            return;
        }
        var radius = readOptionalDoubleParam(request, response, "radius", 64.0, RErrorCode.BAD_RADIUS);
        if (radius == null) {
            return;
        }
        if (!Double.isFinite(radius) || radius < 0.0 || radius > 128.0) {
            err400(response, RErrorCode.BAD_RADIUS);
            return;
        }
        var limit = readOptionalIntParam(request, response, "limit", 64, RErrorCode.BAD_LIMIT);
        if (limit == null) {
            return;
        }
        if (limit < 1 || limit > 128) {
            err400(response, RErrorCode.BAD_LIMIT);
            return;
        }
        var categories = readCategories(request.getURLParameter("category"));
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER);
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
                err400(response, RErrorCode.DIM_NOT_LOADED);
                return;
            }
            ok(response, connector.nearbyEntitiesData(pos.dim(), pos.x(), pos.y(), pos.z(), radius, categories, limit));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
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

    private static void handleStaringBlock(HTTPRequest request, HTTPResponse response) {
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var block = connector.staringBlockData("true".equals(request.getURLParameter("fluid")));
            if (block == null) {
                err400(response, RErrorCode.NO_BLOCK);
                return;
            }
            ok(response, block);
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleBlockState(HTTPRequest request, HTTPResponse response) {
        var pos = readPos(request, response);
        if (pos == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            if (!playerPos.dim().equals(pos.dim())) {
                err400(response, RErrorCode.DIM_NOT_LOADED);
                return;
            }
            ok(response, connector.blockStateData(pos.dim(), pos.x(), pos.y(), pos.z()));
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleBlockStateBatch(HTTPRequest request, HTTPResponse response) {
        var batch = readBlockStateBatchRequest(request);
        if (batch == null || batch.positions() == null || batch.positions().isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS);
            return;
        }
        if (batch.positions().size() > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var dim = batch.dim() == null || batch.dim().isBlank() ? playerPos.dim() : batch.dim().trim();
            if (!playerPos.dim().equals(dim)) {
                err400(response, RErrorCode.DIM_NOT_LOADED);
                return;
            }
            ok(response, connector.blockStateBatchData(dim, batch.positions()));
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static BlockStateBatchRequest readBlockStateBatchRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            return GSON.fromJson(body, BlockStateBatchRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static ParsedPos readPos(HTTPRequest request, HTTPResponse response) {
        var posText = request.getURLParameter("pos");
        if (posText == null || posText.isBlank()) {
            err400(response, RErrorCode.MISSING_POS);
            return null;
        }
        var parts = posText.split(",");
        if (parts.length != 4) {
            err400(response, RErrorCode.BAD_POS);
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
            err400(response, RErrorCode.BAD_POS);
            return null;
        }
        var dim = parts[0].trim();
        if (dim.isEmpty()) {
            err400(response, RErrorCode.BAD_POS);
            return null;
        }
        return new ParsedPos(dim, x, y, z);
    }

    private static ParsedPos readOptionalPos(HTTPRequest request, HTTPResponse response) {
        var posText = request.getURLParameter("pos");
        if (posText == null || posText.isBlank()) {
            return null;
        }
        var parts = posText.split(",");
        if (parts.length != 4) {
            err400(response, RErrorCode.BAD_POS);
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
            err400(response, RErrorCode.BAD_POS);
            return null;
        }
        var dim = parts[0].trim();
        if (dim.isEmpty()) {
            err400(response, RErrorCode.BAD_POS);
            return null;
        }
        return new ParsedPos(dim, x, y, z);
    }

    private static void handleBlockEntity(HTTPRequest request, HTTPResponse response) {
        var pos = readPos(request, response);
        if (pos == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            if (!playerPos.dim().equals(pos.dim())) {
                err400(response, RErrorCode.DIM_NOT_LOADED);
                return;
            }
            var blockEntity = connector.blockEntityData(pos.dim(), pos.x(), pos.y(), pos.z());
            if (blockEntity == null) {
                err400(response, RErrorCode.NO_BLOCK_ENTITY);
                return;
            }
            ok(response, blockEntity);
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleHarvestTool(HTTPRequest request, HTTPResponse response) {
        var blockId = request.getURLParameter("blockId");
        if (blockId != null) {
            blockId = blockId.trim();
        }
        var posText = request.getURLParameter("pos");
        if ((blockId == null || blockId.isBlank()) && (posText == null || posText.isBlank())) {
            err400(response, RErrorCode.MISSING_BLOCK_ID);
            return;
        }
        var pos = readOptionalPos(request, response);
        if (posText != null && !posText.isBlank() && pos == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var playerPos = connector.posData();
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            if (pos != null && !playerPos.dim().equals(pos.dim())) {
                err400(response, RErrorCode.DIM_NOT_LOADED);
                return;
            }
            var data = connector.harvestToolData(
                    blockId == null || blockId.isBlank() ? null : blockId,
                    pos == null ? null : pos.dim(),
                    pos == null ? null : pos.x(),
                    pos == null ? null : pos.y(),
                    pos == null ? null : pos.z()
            );
            ok(response, data);
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleStaringEntity(HTTPRequest request,HTTPResponse response) {
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var entity = connector.staringEntityData();
            if (entity == null) {
                err400(response, RErrorCode.NO_ENTITY);
                return;
            }
            ok(response, entity);
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleEntity(HTTPRequest request, HTTPResponse response) {
        var uuidText = request.getURLParameter("uuid");
        if (uuidText == null || uuidText.isBlank()) {
            err400(response, RErrorCode.MISSING_UUID);
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText.trim());
        } catch (IllegalArgumentException e) {
            err400(response, RErrorCode.BAD_UUID);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            var entity = connector.entityData(uuid);
            if (entity == null) {
                err400(response, RErrorCode.NO_ENTITY);
                return;
            }
            ok(response, entity);
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handlePlayer(HTTPRequest request, HTTPResponse response) {
        var uuidText = request.getURLParameter("uuid");
        UUID uuid = null;
        if (uuidText != null && !uuidText.isBlank()) {
            try {
                uuid = UUID.fromString(uuidText.trim());
            } catch (IllegalArgumentException e) {
                err400(response, RErrorCode.BAD_UUID);
                return;
            }
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            if ("true".equals(request.getURLParameter("detail"))) {
                var player = connector.playerDetailData(uuid);
                if (player == null) {
                    err400(response, RErrorCode.NO_PLAYER_ENTITY);
                    return;
                }
                ok(response, player);
                return;
            }
            var player = connector.playerData(uuid);
            if (player == null) {
                err400(response, RErrorCode.NO_PLAYER_ENTITY);
                return;
            }
            ok(response, player);
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleLangKeySearch(HTTPRequest request, HTTPResponse response) {
        var text = request.getURLParameter("text");
        if (text == null || text.isBlank()) {
            err400(response, RErrorCode.MISSING_TEXT);
            return;
        }
        try {
            ok(response, connector.langKeyIndex().search(text));
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleLangKey(HTTPRequest request, HTTPResponse response) {
        var key = request.getURLParameter("key");
        if (key == null || key.isBlank()) {
            err400(response, RErrorCode.MISSING_KEY);
            return;
        }
        try {
            var data = connector.langKeyIndex().langKey(key.trim());
            if (data == null) {
                err400(response, RErrorCode.NO_LANGKEY);
                return;
            }
            ok(response, data);
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void ok(HTTPResponse response, Object data) {
        writeJson(response, 200, RMcpResponse.ok(data));
    }

    private static void err404(HTTPResponse response) {
        writeJson(response, 404, RMcpResponse.error(RErrorCode.NOT_FOUND.id()));
    }

    private static void err400(HTTPResponse response, RErrorCode errcode) {
        writeJson(response, 400, RMcpResponse.error(errcode.id()));
    }

    private static void err400(HTTPResponse response, String errcode) {
        var known = RErrorCode.get(errcode);
        writeJson(response, 400, RMcpResponse.error(known == null ? RErrorCode.INTERNAL_ERROR.id() : known.id()));
    }

    private static void writeJson(HTTPResponse response, int status, RMcpResponse<?> data) {
        try {
            response.setStatus(status);
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(GSON.toJson(data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private record ParsedPos(String dim, int x, int y, int z) {
    }

    private record InventorySwapRequest(String from, String to, boolean dryRun) {
    }

    private record InventoryMoveRequest(String from, String to, int count, boolean dryRun) {
    }

    private record CraftingOpenRequest(Integer radius, boolean dryRun) {
    }

    private record CraftRequest(Map<String, Integer> slots, String shape, Integer outputSlot, Integer times,
                                boolean dryRun) {
    }

    private record BlockActionRequest(int x, int y, int z) {
    }

    private record BlockBatchActionRequest(List<RMcpBlockPosData> positions) {
    }

    private record BlockStateBatchRequest(String dim, List<RMcpBlockPosData> positions) {
    }

    private record BlockBoxActionRequest(RMcpBlockPosData from, RMcpBlockPosData to) {
    }

    private record ContainerMoveRequest(ContainerEndpointRequest from, ContainerEndpointRequest to, int count,
                                        boolean dryRun) {
    }

    private record ContainerEndpointRequest(String pos, String side, Integer slot) {
    }
}
