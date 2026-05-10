package calebxzhou.rdi.mc.common2.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import calebxzhou.rdi.mc.common2.schem.SchemLegacyBlockMap;
import calebxzhou.rdi.mc.common2.schem.SchemReader;
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

public final class RMHttpServer {
    private static final int BLOCK_BATCH_LIMIT = 512;
    private static final int ITEM_PICKUP_LIMIT = 2048;
    private static final int CONTAINER_BATCH_LIMIT = 64;
    private static final char[] BUILDING_SYMBOLS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%&()*+,-/:;<=>?@[]^_{|}~".toCharArray();
    private static final Gson GSON = new GsonBuilder().create();
    private static HTTPServer server;
    private static RMcpGameConnector connector;

    private RMHttpServer() {
    }

    @FunctionalInterface
    private interface RouteHandler {
        void handle(HTTPRequest request, HTTPResponse response) throws RMError;
    }

    private record Route(HTTPMethod method, String endpoint, boolean prefix, RouteHandler handler) {
        public static final List<Route> LIST;

        static {
            LIST = List.of(
                    get("/", RMHttpServer::handlePrompts),
                    get("/prompts", RMHttpServer::handlePrompts),
                    get("/buildings", RMHttpServer::handleBuildings),
                    getPrefix("/buildings/", RMHttpServer::handleBuilding),
                    getPrefix("/apidoc/", RMHttpServer::handleApiDoc),
                    getPrefix("/errcode/", RMHttpServer::handleErrCode),
                    get("/test", RMHttpServer::handleTest),
                    get("/mods", RMHttpServer::handleMods),
                    get("/quest/chapter-list", RMHttpServer::handleQuestChapterList),
                    get("/quest/reachable", RMHttpServer::handleReachableQuests),
                    getPrefix("/quest/chapter/", RMHttpServer::handleQuestChapter),
                    get("/pos", RMHttpServer::handlePos),
                    get("/inventory", RMHttpServer::handleInventory),
                    get("/menu", RMHttpServer::handleMenu),
                    get("/situation", RMHttpServer::handleSituation),
                    get("/mainhand", RMHttpServer::handleMainHand),
                    get("/screenshot", RMHttpServer::handleScreenshot),
                    get("/recipe", RMHttpServer::handleRecipe),
                    get("/chunk", RMHttpServer::handleChunk),
                    get("/section", RMHttpServer::handleSection),
                    get("/blockmap/slice", RMHttpServer::handleBlockMapSlice),
                    get("/blockmap/walkable", RMHttpServer::handleBlockMapWalkable),
                    get("/terrain/profile", RMHttpServer::handleTerrainProfile),
                    post("/blocks/find", RMHttpServer::handleBlocksFind),
                    get("/nearby-resources", RMHttpServer::handleNearbyResources),
                    get("/nearby-entities", RMHttpServer::handleNearbyEntities),
                    get("/staring-block", RMHttpServer::handleStaringBlock),
                    get("/blockstate", RMHttpServer::handleBlockState),
                    post("/blockstate/batch", RMHttpServer::handleBlockStateBatch),
                    get("/blockentity", RMHttpServer::handleBlockEntity),
                    get("/sign/text", RMHttpServer::handleGetSignText),
                    post("/sign/text", RMHttpServer::handleSignText),
                    get("/container", RMHttpServer::handleContainer),
                    get("/harvest-tool", RMHttpServer::handleHarvestTool),
                    get("/staring-entity", RMHttpServer::handleStaringEntity),
                    get("/entity", RMHttpServer::handleEntity),
                    get("/player", RMHttpServer::handlePlayer),
                    get("/langkey", RMHttpServer::handleLangKey),
                    get("/langkey-search", RMHttpServer::handleLangKeySearch),
                    post("/inventory/swap", RMHttpServer::handleInventorySwap),
                    post("/inventory/move", RMHttpServer::handleInventoryMove),
                    post("/hotbar/select", RMHttpServer::handleHotbarSelect),
                    post("/menu/drop", RMHttpServer::handleMenuDrop),
                    post("/craft", RMHttpServer::handleCraft),
                    post("/container/put", RMHttpServer::handleContainerPut),
                    post("/container/put/batch", RMHttpServer::handleContainerPutBatch),
                    post("/container/take", RMHttpServer::handleContainerTake),
                    post("/container/take/batch", RMHttpServer::handleContainerTakeBatch),
                    post("/container/move", RMHttpServer::handleContainerMove),
                    post("/container/move/batch", RMHttpServer::handleContainerMoveBatch),
                    post("/move", RMHttpServer::handleMove),
                    post("/respawn", RMHttpServer::handleRespawn),
                    post("/entity/pickup-item", RMHttpServer::handleEntityPickupItem),
                    post("/place", RMHttpServer::handlePlace),
                    post("/break", RMHttpServer::handleBreak),
                    post("/place/batch", RMHttpServer::handlePlaceBatch),
                    post("/place/discrete", RMHttpServer::handlePlaceDiscrete),
                    post("/place/palette", RMHttpServer::handlePlacePalette),
                    post("/break/batch", RMHttpServer::handleBreakBatch),
                    post("/place/box", RMHttpServer::handlePlaceBox),
                    post("/break/box", RMHttpServer::handleBreakBox)
            );
        }

        private static Route get(String endpoint, RouteHandler handler) {
            return new Route(HTTPMethod.GET, endpoint, false, handler);
        }

        private static Route getPrefix(String endpoint, RouteHandler handler) {
            return new Route(HTTPMethod.GET, endpoint, true, handler);
        }

        private static Route post(String endpoint, RouteHandler handler) {
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
                try {
                    route.handler().handle(request, response);
                } catch (RMError e) {
                    err400(response, e.errorCode());
                } catch (RMcpEndpointException e) {
                    err400(response, e.code());
                } catch (Exception e) {
                    if(e instanceof ClassNotFoundException){
                        err400(response, RErrorCode.MOD_CLASS_NOT_FOUND, e.getMessage());
                    }else{
                        err400(response, RErrorCode.INTERNAL_ERROR);
                    }
                }
                return;
            }
        }
        err404(response);
    }

    private static void requirePlayerInWorld() throws RMError {
        if (!connector.playerInWorld()) {
            throw new RMError(RErrorCode.NO_PLAYER);
        }
    }

    private static void handleTest(HTTPRequest request, HTTPResponse response) {
        ok(response, connector.testData());
    }

    private static void handleMods(HTTPRequest request, HTTPResponse response) throws RMError {
        var id = request.getURLParameter("id");
        if (id == null) {
            ok(response, connector.modIds());
            return;
        }
        id = id.trim();
        if (id.isEmpty() || id.contains("/") || id.contains("\\") || id.contains(" ") || id.contains("`")) {
            throw new RMError(RErrorCode.BAD_MOD_ID);
        }
        var mod = connector.modData(id);
        if (mod == null) {
            throw new RMError(RErrorCode.NO_MOD);
        }
        ok(response, mod);
    }

    private static void handleQuestChapterList(HTTPRequest request, HTTPResponse response) throws RMError {
        requirePlayerInWorld();
        var data = connector.questChapterList();
        if (data == null) {
            throw new RMError(RErrorCode.QUEST_DATA_NOT_LOADED);
        }
        ok(response, data);
    }

    private static void handleReachableQuests(HTTPRequest request, HTTPResponse response) throws RMError {
        requirePlayerInWorld();
        var data = connector.reachableQuests();
        if (data == null) {
            throw new RMError(RErrorCode.QUEST_DATA_NOT_LOADED);
        }
        ok(response, data);
    }

    private static void handleQuestChapter(HTTPRequest request, HTTPResponse response) throws RMError {
        var prefix = "/quest/chapter/";
        var path = request.getPath();
        if (path.length() <= prefix.length()) {
            throw new RMError(RErrorCode.MISSING_QUEST_CHAPTER_ID);
        }
        var id = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8).trim();
        if (id.isEmpty() || id.contains("/") || id.contains("\\") || id.contains(" ") || id.contains("`")) {
            throw new RMError(RErrorCode.BAD_QUEST_CHAPTER_ID);
        }
        requirePlayerInWorld();
        var data = connector.questChapter(id);
        if (data == null) {
            throw new RMError(RErrorCode.NO_QUEST_CHAPTER);
        }
        ok(response, data);
    }

    private static void handlePrompts(HTTPRequest request, HTTPResponse response) throws RMError {
        var doc = readApiDoc();
        if (doc == null) {
            throw new RMError(RErrorCode.PROMPTS_NOT_FOUND);
        }
        writeMarkdown(response, doc);
    }

    private static void handleBuildings(HTTPRequest request, HTTPResponse response) throws RMError {
        var doc = readResourceText("mcp/buildings/index.md");
        if (doc == null) {
            throw new RMError(RErrorCode.BUILDINGS_NOT_FOUND);
        }
        writeMarkdown(response, doc);
    }

    private static void handleBuilding(HTTPRequest request, HTTPResponse response) throws RMError {
        var prefix = "/buildings/";
        var path = request.getPath();
        if (path.length() <= prefix.length()) {
            throw new RMError(RErrorCode.BAD_BUILDING_ID);
        }
        var id = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8).trim();
        if (!isValidBuildingId(id)) {
            throw new RMError(RErrorCode.BAD_BUILDING_ID);
        }
        var resource = "mcp/buildings/" + id + ".schem";
        try (var input = RMHttpServer.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new RMError(RErrorCode.UNKNOWN_BUILDING);
            }
            var schematic = SchemReader.read(input);
            var layer = readBuildingLayer(request, schematic);
            if (layer == null && request.getURLParameter("layer") != null) {
                throw new RMError(RErrorCode.BAD_BUILDING_LAYER);
            }
            writeMarkdown(response, describeBuilding(id, schematic, layer));
        } catch (RMError e) {
            throw e;
        } catch (Exception e) {
            throw new RMError(RErrorCode.BAD_BUILDING_SCHEMATIC, e);
        }
    }

    private static void handleErrCode(HTTPRequest request, HTTPResponse response) throws RMError {
        var prefix = "/errcode/";
        var path = request.getPath();
        if (path.length() <= prefix.length()) {
            throw new RMError(RErrorCode.MISSING_ERRCODE);
        }
        var code = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8).trim();
        if (code.isEmpty() || code.contains("/") || code.contains(" ") || code.contains("`")) {
            throw new RMError(RErrorCode.BAD_ERRCODE);
        }
        var errcode = RErrorCode.get(code);
        if (errcode == null) {
            throw new RMError(RErrorCode.UNKNOWN_ERRCODE);
        }
        ok(response, errcode);
    }

    private static void handleApiDoc(HTTPRequest request, HTTPResponse response) throws RMError {
        var prefix = "/apidoc/";
        var path = request.getPath();
        if (path.length() <= prefix.length()) {
            throw new RMError(RErrorCode.MISSING_APIDOC);
        }
        var file = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8).trim();
        if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.contains("..") || !file.endsWith(".md")) {
            throw new RMError(RErrorCode.BAD_APIDOC);
        }
        var doc = readResourceText("mcp/apidoc/" + file);
        if (doc == null) {
            throw new RMError(RErrorCode.UNKNOWN_APIDOC);
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

    private static void handlePos(HTTPRequest request, HTTPResponse response) throws RMError {
        var pos = connector.posData();
        if (pos == null) {
            throw new RMError(RErrorCode.NO_PLAYER);
        }
        ok(response, pos);
    }

    private static void handleMainHand(HTTPRequest request, HTTPResponse response) throws RMError {
        requirePlayerInWorld();
        ok(response, connector.mainHandItemData());
    }

    private static void handleInventory(HTTPRequest request, HTTPResponse response) throws RMError {
        requirePlayerInWorld();
        ok(response, connector.inventoryData());
    }

    private static void handleMenu(HTTPRequest request, HTTPResponse response) throws RMError {
        requirePlayerInWorld();
        ok(response, connector.menuData());
    }

    private static void handleMenuDrop(HTTPRequest request, HTTPResponse response) throws RMError {
        var dropRequest = readMenuDropRequest(request);
        if (dropRequest == null || dropRequest.slot() == null || dropRequest.count() == null || dropRequest.count() <= 0) {
            throw new RMError(RErrorCode.BAD_REQUEST);
        }
        requirePlayerInWorld();
        ok(response, connector.dropMenuItem(dropRequest.slot(), dropRequest.count(), dropRequest.dryRun()));
    }

    private static MenuDropRequest readMenuDropRequest(HTTPRequest request) {
        try {
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    return GSON.fromJson(body, MenuDropRequest.class);
                }
            }
            return new MenuDropRequest(
                    Integer.parseInt(String.valueOf(request.getURLParameter("slot"))),
                    Integer.parseInt(String.valueOf(request.getURLParameter("count"))),
                    Boolean.parseBoolean(String.valueOf(request.getURLParameter("dryRun")))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleInventorySwap(HTTPRequest request, HTTPResponse response) throws RMError {
        var swapRequest = readInventorySwapRequest(request);
        if (swapRequest == null || swapRequest.from() == null || swapRequest.from().isBlank() || swapRequest.to() == null || swapRequest.to().isBlank()) {
            throw new RMError(RErrorCode.BAD_REQUEST);
        }
        requirePlayerInWorld();
        ok(response, connector.swapInventorySlots(swapRequest.from().trim(), swapRequest.to().trim(), swapRequest.dryRun()));
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

    private static void handleInventoryMove(HTTPRequest request, HTTPResponse response) throws RMError {
        var moveRequest = readInventoryMoveRequest(request);
        if (moveRequest == null || moveRequest.from() == null || moveRequest.from().isBlank() || moveRequest.to() == null || moveRequest.to().isBlank()) {
            throw new RMError(RErrorCode.BAD_REQUEST);
        }
        requirePlayerInWorld();
        ok(response, connector.moveInventoryItems(moveRequest.from().trim(), moveRequest.to().trim(), moveRequest.count(), moveRequest.dryRun()));
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

    private static void handleHotbarSelect(HTTPRequest request, HTTPResponse response) throws RMError {
        var selectRequest = readHotbarSelectRequest(request);
        if (selectRequest == null || selectRequest.slot() == null) {
            throw new RMError(RErrorCode.BAD_REQUEST);
        }
        requirePlayerInWorld();
        ok(response, connector.selectHotbarSlot(selectRequest.slot(), selectRequest.dryRun()));
    }

    private static HotbarSelectRequest readHotbarSelectRequest(HTTPRequest request) {
        try {
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    return GSON.fromJson(body, HotbarSelectRequest.class);
                }
            }
            return new HotbarSelectRequest(
                    Integer.parseInt(String.valueOf(request.getURLParameter("slot"))),
                    Boolean.parseBoolean(String.valueOf(request.getURLParameter("dryRun")))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleCraft(HTTPRequest request, HTTPResponse response) throws RMError {
        var craftRequest = readCraftRequest(request);
        if (craftRequest == null) {
            throw new RMError(RErrorCode.BAD_REQUEST);
        }
        if (craftRequest.slots() == null || craftRequest.slots().isEmpty() || craftRequest.shape() == null || craftRequest.shape().isBlank()) {
            throw new RMError(RErrorCode.BAD_SHAPE);
        }
        if (craftRequest.outputSlot() == null) {
            throw new RMError(RErrorCode.BAD_SLOT);
        }
        if (craftRequest.times() <= 0 || craftRequest.times() > 64) {
            throw new RMError(RErrorCode.BAD_COUNT);
        }
        requirePlayerInWorld();
        ok(response, connector.craft(craftRequest.slots(), craftRequest.shape().trim(), craftRequest.outputSlot(), craftRequest.times(), craftRequest.dryRun()));
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

    private static void handlePlace(HTTPRequest request, HTTPResponse response) throws RMError {
        var pos = readBlockActionRequest(request);
        if (pos == null) {
            throw new RMError(RErrorCode.BAD_POS);
        }
        requirePlayerInWorld();
        ok(response, connector.placeBlock(pos.x(), pos.y(), pos.z(), pos.face()));
    }

    private static void handleMove(HTTPRequest request, HTTPResponse response) throws RMError {
        var pos = readPlayerMoveRequest(request);
        if (pos == null) {
            throw new RMError(RErrorCode.BAD_POS);
        }
        requirePlayerInWorld();
        ok(response, connector.movePlayer(pos.x(), pos.y(), pos.z()));
    }

    private static void handleRespawn(HTTPRequest request, HTTPResponse response) throws RMError {
        requirePlayerInWorld();
        ok(response, connector.respawnPlayer());
    }

    private static void handleEntityPickupItem(HTTPRequest request, HTTPResponse response) throws RMError {
        var pickup = readItemPickupRequest(request);
        if (pickup.error() != null) {
            throw new RMError(pickup.error());
        }
        requirePlayerInWorld();
        ok(response, connector.pickupItemEntities(pickup.ids(), pickup.radius(), pickup.limit()));
    }

    private static ItemPickupParams readItemPickupRequest(HTTPRequest request) {
        try {
            List<String> texts = null;
            Double radius = null;
            Integer limit = null;
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    var parsed = GSON.fromJson(body, ItemPickupRequest.class);
                    if (parsed != null) {
                        texts = parsed.ids();
                        radius = parsed.radius();
                        limit = parsed.limit();
                    }
                }
            }
            var idsText = request.getURLParameter("ids");
            if (idsText != null && !idsText.isBlank()) {
                texts = Arrays.asList(idsText.split(","));
            }
            var radiusText = request.getURLParameter("radius");
            if (radiusText != null && !radiusText.isBlank()) {
                try {
                    radius = Double.parseDouble(radiusText.trim());
                } catch (NumberFormatException e) {
                    return ItemPickupParams.error(RErrorCode.BAD_RADIUS);
                }
            }
            var limitText = request.getURLParameter("limit");
            if (limitText != null && !limitText.isBlank()) {
                try {
                    limit = Integer.parseInt(limitText.trim());
                } catch (NumberFormatException e) {
                    return ItemPickupParams.error(RErrorCode.BAD_LIMIT);
                }
            }
            radius = radius == null ? 64.0D : radius;
            limit = limit == null ? 256 : limit;
            if (!Double.isFinite(radius) || radius < 1.0D || radius > 64.0D) {
                return ItemPickupParams.error(RErrorCode.BAD_RADIUS);
            }
            if (limit < 0 || limit > ITEM_PICKUP_LIMIT) {
                return ItemPickupParams.error(RErrorCode.BAD_LIMIT);
            }
            var ids = new ArrayList<UUID>();
            if (texts != null) {
                if (texts.size() > ITEM_PICKUP_LIMIT) {
                    return ItemPickupParams.error(RErrorCode.BAD_IDS);
                }
                for (var text : texts) {
                    if (text == null || text.isBlank()) {
                        return ItemPickupParams.error(RErrorCode.BAD_IDS);
                    }
                    var id = UUID.fromString(text.trim());
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
            }
            return new ItemPickupParams(List.copyOf(ids), radius, limit, null);
        } catch (Exception e) {
            return ItemPickupParams.error(RErrorCode.BAD_IDS);
        }
    }

    private static PlayerMoveRequest readPlayerMoveRequest(HTTPRequest request) {
        try {
            PlayerMoveRequest move = null;
            var xText = request.getURLParameter("x");
            var yText = request.getURLParameter("y");
            var zText = request.getURLParameter("z");
            if (xText != null || yText != null || zText != null) {
                move = new PlayerMoveRequest(
                        Double.parseDouble(String.valueOf(xText)),
                        Double.parseDouble(String.valueOf(yText)),
                        Double.parseDouble(String.valueOf(zText))
                );
            } else if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    move = GSON.fromJson(body, PlayerMoveRequest.class);
                }
            }
            if (move == null || move.x() == null || move.y() == null || move.z() == null) {
                return null;
            }
            var x = move.x();
            var y = move.y();
            var z = move.z();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return null;
            }
            return new PlayerMoveRequest(x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleBreak(HTTPRequest request, HTTPResponse response) throws RMError {
        var pos = readBlockActionRequest(request);
        if (pos == null) {
            throw new RMError(RErrorCode.BAD_POS);
        }
        requirePlayerInWorld();
        ok(response, connector.breakBlock(pos.x(), pos.y(), pos.z()));
    }

    private static BlockActionRequest readBlockActionRequest(HTTPRequest request) {
        try {
            return new BlockActionRequest(
                    Integer.parseInt(String.valueOf(request.getURLParameter("x"))),
                    Integer.parseInt(String.valueOf(request.getURLParameter("y"))),
                    Integer.parseInt(String.valueOf(request.getURLParameter("z"))),
                    request.getURLParameter("face")
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

    private static void handlePlaceDiscrete(HTTPRequest request, HTTPResponse response) {
        var discrete = readPlaceDiscreteRequest(request);
        if (discrete == null || discrete.targets() == null || discrete.targets().isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS);
            return;
        }
        if (discrete.blockId() == null || discrete.blockId().isBlank()) {
            err400(response, RErrorCode.BAD_BLOCK_ID);
            return;
        }
        if (discrete.targets().size() > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.placeBlocksDiscrete(discrete));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static RMcpPlaceDiscreteRequest readPlaceDiscreteRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            return GSON.fromJson(body, RMcpPlaceDiscreteRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static void handlePlacePalette(HTTPRequest request, HTTPResponse response) {
        var palette = readPlacePaletteRequest(request);
        if (palette == null || palette.palette() == null || palette.palette().isEmpty()) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (palette.targets() == null || palette.targets().isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS);
            return;
        }
        if (palette.targets().size() > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.placeBlocksPalette(palette));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static RMcpPlacePaletteRequest readPlacePaletteRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            return GSON.fromJson(body, RMcpPlacePaletteRequest.class);
        } catch (Exception e) {
            return null;
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
        var box = readPlaceBoxRequest(request);
        if (box == null || box.startPos() == null || box.endOffset() == null) {
            err400(response, RErrorCode.BAD_BOX);
            return;
        }
        if (box.blockId() == null || box.blockId().isBlank()) {
            err400(response, RErrorCode.BAD_BLOCK_ID);
            return;
        }
        if (offsetBoxBlockCount(box.endOffset()) > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.placeBlockBox(box));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static RMcpPlaceBoxRequest readPlaceBoxRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            return GSON.fromJson(body, RMcpPlaceBoxRequest.class);
        } catch (Exception e) {
            return null;
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

    private static long offsetBoxBlockCount(RMcpBlockPosData endOffset) {
        return (long) (Math.abs(endOffset.x()) + 1)
                * (Math.abs(endOffset.y()) + 1)
                * (Math.abs(endOffset.z()) + 1);
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

    private static void handleContainerPut(HTTPRequest request, HTTPResponse response) {
        var putRequest = readContainerPutRequest(request);
        if (putRequest == null || putRequest.to() == null || putRequest.fromInventorySlot() == null) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (putRequest.to().pos() == null || putRequest.to().pos().isBlank()) {
            err400(response, RErrorCode.MISSING_POS);
            return;
        }
        if (putRequest.count() <= 0) {
            err400(response, RErrorCode.BAD_COUNT);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.putInventoryItemIntoContainer(
                    putRequest.fromInventorySlot(),
                    putRequest.to().pos().trim(),
                    putRequest.to().side(),
                    putRequest.to().slot(),
                    putRequest.count(),
                    putRequest.dryRun()
            ));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleContainerTake(HTTPRequest request, HTTPResponse response) {
        var takeRequest = readContainerTakeRequest(request);
        if (takeRequest == null || takeRequest.from() == null || takeRequest.from().slot() == null) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (takeRequest.from().pos() == null || takeRequest.from().pos().isBlank()) {
            err400(response, RErrorCode.MISSING_POS);
            return;
        }
        if (takeRequest.count() <= 0) {
            err400(response, RErrorCode.BAD_COUNT);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.takeContainerItemToInventory(
                    takeRequest.from().pos().trim(),
                    takeRequest.from().side(),
                    takeRequest.from().slot(),
                    takeRequest.toInventorySlot(),
                    takeRequest.count(),
                    takeRequest.dryRun()
            ));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleContainerPutBatch(HTTPRequest request, HTTPResponse response) {
        var batchRequest = readContainerPutBatchRequest(request);
        if (batchRequest == null || batchRequest.moves() == null || batchRequest.moves().isEmpty()) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (batchRequest.moves().size() > CONTAINER_BATCH_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT);
            return;
        }
        for (var move : batchRequest.moves()) {
            if (move == null || move.fromInventorySlot() == null || move.to() == null) {
                err400(response, RErrorCode.BAD_REQUEST);
                return;
            }
            if (move.to().pos() == null || move.to().pos().isBlank()) {
                err400(response, RErrorCode.MISSING_POS);
                return;
            }
            if (move.count() <= 0) {
                err400(response, RErrorCode.BAD_COUNT);
                return;
            }
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.putInventoryItemsIntoContainerBatch(batchRequest));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleContainerTakeBatch(HTTPRequest request, HTTPResponse response) {
        var batchRequest = readContainerTakeBatchRequest(request);
        if (batchRequest == null || batchRequest.moves() == null || batchRequest.moves().isEmpty()) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (batchRequest.moves().size() > CONTAINER_BATCH_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT);
            return;
        }
        for (var move : batchRequest.moves()) {
            if (move == null || move.from() == null || move.from().slot() == null) {
                err400(response, RErrorCode.BAD_REQUEST);
                return;
            }
            if (move.from().pos() == null || move.from().pos().isBlank()) {
                err400(response, RErrorCode.MISSING_POS);
                return;
            }
            if (move.count() <= 0) {
                err400(response, RErrorCode.BAD_COUNT);
                return;
            }
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.takeContainerItemsToInventoryBatch(batchRequest));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleContainerMoveBatch(HTTPRequest request, HTTPResponse response) {
        var batchRequest = readContainerMoveBatchRequest(request);
        if (batchRequest == null || batchRequest.moves() == null || batchRequest.moves().isEmpty()) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (batchRequest.moves().size() > CONTAINER_BATCH_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT);
            return;
        }
        for (var move : batchRequest.moves()) {
            if (move == null || move.from() == null || move.to() == null) {
                err400(response, RErrorCode.BAD_REQUEST);
                return;
            }
            if (move.from().pos() == null || move.from().pos().isBlank() || move.to().pos() == null || move.to().pos().isBlank()) {
                err400(response, RErrorCode.MISSING_POS);
                return;
            }
            if (move.from().slot() == null) {
                err400(response, RErrorCode.BAD_SLOT);
                return;
            }
            if (move.count() <= 0) {
                err400(response, RErrorCode.BAD_COUNT);
                return;
            }
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.moveContainerItemsBatch(batchRequest));
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

    private static RMcpContainerMoveBatchRequest readContainerMoveBatchRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            return body.isBlank() ? null : GSON.fromJson(body, RMcpContainerMoveBatchRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static RMcpContainerPutBatchRequest readContainerPutBatchRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            return body.isBlank() ? null : GSON.fromJson(body, RMcpContainerPutBatchRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static RMcpContainerTakeBatchRequest readContainerTakeBatchRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            return body.isBlank() ? null : GSON.fromJson(body, RMcpContainerTakeBatchRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static ContainerPutRequest readContainerPutRequest(HTTPRequest request) {
        try {
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    return GSON.fromJson(body, ContainerPutRequest.class);
                }
            }
            return new ContainerPutRequest(
                    Integer.parseInt(String.valueOf(request.getURLParameter("fromInventorySlot"))),
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

    private static ContainerTakeRequest readContainerTakeRequest(HTTPRequest request) {
        try {
            if (request.hasBody()) {
                var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    return GSON.fromJson(body, ContainerTakeRequest.class);
                }
            }
            return new ContainerTakeRequest(
                    new ContainerEndpointRequest(
                            request.getURLParameter("fromPos"),
                            request.getURLParameter("fromSide"),
                            Integer.parseInt(String.valueOf(request.getURLParameter("fromSlot")))
                    ),
                    request.getURLParameter("toInventorySlot") == null || request.getURLParameter("toInventorySlot").isBlank() ? null : Integer.parseInt(request.getURLParameter("toInventorySlot").trim()),
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

    private static void handleBlockMapSlice(HTTPRequest request, HTTPResponse response) {
        var mapRequest = readBlockMapRequest(request, response);
        if (mapRequest == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.blockMapSliceData(mapRequest.x(), mapRequest.y(), mapRequest.z(), mapRequest.radius()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleBlockMapWalkable(HTTPRequest request, HTTPResponse response) {
        var mapRequest = readBlockMapRequest(request, response);
        if (mapRequest == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.blockMapWalkableData(mapRequest.x(), mapRequest.y(), mapRequest.z(), mapRequest.radius()));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleTerrainProfile(HTTPRequest request, HTTPResponse response) {
        var profileRequest = readTerrainProfileRequest(request, response);
        if (profileRequest == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.terrainProfileData(
                    profileRequest.axis(),
                    profileRequest.x(),
                    profileRequest.y(),
                    profileRequest.z(),
                    profileRequest.length(),
                    profileRequest.verticalRadius()
            ));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static TerrainProfileRequest readTerrainProfileRequest(HTTPRequest request, HTTPResponse response) {
        var axis = request.getURLParameter("axis");
        if (axis == null || axis.isBlank()) {
            err400(response, RErrorCode.BAD_AXIS);
            return null;
        }
        axis = axis.trim().toLowerCase(Locale.ROOT);
        if (!"x".equals(axis) && !"z".equals(axis)) {
            err400(response, RErrorCode.BAD_AXIS);
            return null;
        }
        var length = readOptionalIntParam(request, response, "length", 17, RErrorCode.BAD_TERRAIN_LENGTH);
        if (length == null) {
            return null;
        }
        if (length < 1 || length > 33 || length % 2 == 0) {
            err400(response, RErrorCode.BAD_TERRAIN_LENGTH);
            return null;
        }
        var verticalRadius = readOptionalIntParam(request, response, "verticalRadius", 16, RErrorCode.BAD_VERTICAL_RADIUS);
        if (verticalRadius == null) {
            return null;
        }
        if (verticalRadius < 1 || verticalRadius > 64) {
            err400(response, RErrorCode.BAD_VERTICAL_RADIUS);
            return null;
        }
        var xText = request.getURLParameter("x");
        var yText = request.getURLParameter("y");
        var zText = request.getURLParameter("z");
        var anyPos = (xText != null && !xText.isBlank()) || (yText != null && !yText.isBlank()) || (zText != null && !zText.isBlank());
        if (!anyPos) {
            return new TerrainProfileRequest(axis, null, null, null, length, verticalRadius);
        }
        if (xText == null || xText.isBlank() || yText == null || yText.isBlank() || zText == null || zText.isBlank()) {
            err400(response, RErrorCode.BAD_POS);
            return null;
        }
        try {
            return new TerrainProfileRequest(
                    axis,
                    Integer.parseInt(xText.trim()),
                    Integer.parseInt(yText.trim()),
                    Integer.parseInt(zText.trim()),
                    length,
                    verticalRadius
            );
        } catch (NumberFormatException e) {
            err400(response, RErrorCode.BAD_POS);
            return null;
        }
    }

    private static BlockMapRequest readBlockMapRequest(HTTPRequest request, HTTPResponse response) {
        var radius = readOptionalIntParam(request, response, "radius", 8, RErrorCode.BAD_BLOCKMAP_RADIUS);
        if (radius == null) {
            return null;
        }
        if (radius < 0 || radius > 16) {
            err400(response, RErrorCode.BAD_BLOCKMAP_RADIUS);
            return null;
        }
        var xText = request.getURLParameter("x");
        var yText = request.getURLParameter("y");
        var zText = request.getURLParameter("z");
        var anyPos = (xText != null && !xText.isBlank()) || (yText != null && !yText.isBlank()) || (zText != null && !zText.isBlank());
        if (!anyPos) {
            return new BlockMapRequest(null, null, null, radius);
        }
        if (xText == null || xText.isBlank() || yText == null || yText.isBlank() || zText == null || zText.isBlank()) {
            err400(response, RErrorCode.BAD_POS);
            return null;
        }
        try {
            return new BlockMapRequest(
                    Integer.parseInt(xText.trim()),
                    Integer.parseInt(yText.trim()),
                    Integer.parseInt(zText.trim()),
                    radius
            );
        } catch (NumberFormatException e) {
            err400(response, RErrorCode.BAD_POS);
            return null;
        }
    }

    private static void handleBlocksFind(HTTPRequest request, HTTPResponse response) {
        var findRequest = readBlocksFindRequest(request);
        if (findRequest == null) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        var ids = normalizedBlockIds(findRequest.id(), findRequest.ids());
        if (ids == null) {
            err400(response, RErrorCode.BAD_BLOCK_IDS);
            return;
        }
        var chunkRadius = findRequest.chunkRadius() == null ? 2 : findRequest.chunkRadius();
        if (chunkRadius < 0 || chunkRadius > 4) {
            err400(response, RErrorCode.BAD_CHUNK_RADIUS);
            return;
        }
        var scanMode = findRequest.scanMode() == null || findRequest.scanMode().isBlank() ? "nearby_sections" : findRequest.scanMode().trim();
        if (!"nearby_sections".equals(scanMode) && !"chunk".equals(scanMode)) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        Integer sectionRadius = "chunk".equals(scanMode) ? null : findRequest.sectionRadius() == null ? 1 : findRequest.sectionRadius();
        if (sectionRadius != null && (sectionRadius < 0 || sectionRadius > 4)) {
            err400(response, RErrorCode.BAD_SECTION_RADIUS);
            return;
        }
        var limit = findRequest.limit() == null ? 64 : findRequest.limit();
        if (limit < 1 || limit > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.blocksFindData(new RMcpBlocksFindRequest(null, ids, chunkRadius, sectionRadius, scanMode, limit, findRequest.includeState())));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static RMcpBlocksFindRequest readBlocksFindRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            return GSON.fromJson(body, RMcpBlocksFindRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> normalizedBlockIds(String id, List<String> ids) {
        var normalized = new ArrayList<String>();
        if (id != null && !id.isBlank()) {
            var text = id.trim();
            if (!isValidResourceId(text)) {
                return null;
            }
            normalized.add(text);
        }
        if (ids == null) {
            return normalized.isEmpty() ? null : List.copyOf(normalized);
        }
        for (var _id : ids) {
            if (_id == null) {
                return null;
            }
            var text = _id.trim();
            if (!isValidResourceId(text)) {
                return null;
            }
            if (!normalized.contains(text)) {
                normalized.add(text);
                if (normalized.size() > 16) {
                    return null;
                }
            }
        }
        return normalized.isEmpty() ? null : List.copyOf(normalized);
    }

    private static boolean isValidResourceId(String id) {
        var separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1 || id.indexOf(':', separator + 1) >= 0) {
            return false;
        }
        for (int i = 0; i < separator; i++) {
            if (!isValidNamespaceChar(id.charAt(i))) {
                return false;
            }
        }
        for (int i = separator + 1; i < id.length(); i++) {
            if (!isValidPathChar(id.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidNamespaceChar(char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '_' || ch == '-' || ch == '.';
    }

    private static boolean isValidPathChar(char ch) {
        return isValidNamespaceChar(ch) || ch == '/';
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
        if (limit < 1 || limit > 1024) {
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
        var pos = readXyzRequest(request, response);
        if (pos == null) {
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.blockStateData(pos.x(), pos.y(), pos.z()));
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
            ok(response, connector.blockStateBatchData(batch.positions()));
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

    private static BlockActionRequest readXyzRequest(HTTPRequest request, HTTPResponse response) {
        var xText = request.getURLParameter("x");
        var yText = request.getURLParameter("y");
        var zText = request.getURLParameter("z");
        if (xText == null || xText.isBlank() || yText == null || yText.isBlank() || zText == null || zText.isBlank()) {
            err400(response, RErrorCode.MISSING_POS);
            return null;
        }
        try {
            return new BlockActionRequest(
                    Integer.parseInt(xText.trim()),
                    Integer.parseInt(yText.trim()),
                    Integer.parseInt(zText.trim()),
                    null
            );
        } catch (NumberFormatException e) {
            err400(response, RErrorCode.BAD_POS);
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

    private static void handleSignText(HTTPRequest request, HTTPResponse response) {
        var signRequest = readSignTextRequest(request);
        if (signRequest == null || signRequest.pos() == null) {
            err400(response, RErrorCode.BAD_REQUEST);
            return;
        }
        if (signRequest.text() == null || signRequest.text().isBlank()) {
            err400(response, RErrorCode.BAD_SIGN_TEXT);
            return;
        }
        if (signRequest.text().replace("\r", "").split("\n", -1).length > 4) {
            err400(response, RErrorCode.BAD_SIGN_TEXT);
            return;
        }
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.setSignText(signRequest));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleGetSignText(HTTPRequest request, HTTPResponse response) {
        var pos = readXyzRequest(request, response);
        if (pos == null) {
            return;
        }
        var side = request.getURLParameter("side");
        try {
            if (!connector.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER);
                return;
            }
            ok(response, connector.signTextData(pos.x(), pos.y(), pos.z(), side));
        } catch (RMcpEndpointException e) {
            err400(response, e.code());
        } catch (Exception e) {
            err400(response, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static RMcpSignTextRequest readSignTextRequest(HTTPRequest request) {
        try {
            if (!request.hasBody()) {
                return null;
            }
            var body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            return body.isBlank() ? null : GSON.fromJson(body, RMcpSignTextRequest.class);
        } catch (Exception e) {
            return null;
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

    private static boolean isValidBuildingId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            var ch = id.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-')) {
                return false;
            }
        }
        return true;
    }

    private static Integer readBuildingLayer(HTTPRequest request, SchemReader.Schematic schematic) {
        var layerText = request.getURLParameter("layer");
        if (layerText == null) {
            return null;
        }
        try {
            var layer = Integer.parseInt(layerText.trim());
            return layer >= 0 && layer < schematic.height() ? layer : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String describeBuilding(String id, SchemReader.Schematic schematic, Integer layer) {
        var counts = new LinkedHashMap<BuildingBlockKey, Integer>();
        var nonAirBlocks = 0;
        for (var block : schematic.blocks()) {
            if (block.blockId() == 0) {
                continue;
            }
            nonAirBlocks++;
            counts.merge(new BuildingBlockKey(block.blockId(), block.data()), 1, Integer::sum);
        }
        var palette = new ArrayList<>(counts.entrySet());
        palette.sort(Comparator
                .<Map.Entry<BuildingBlockKey, Integer>>comparingInt(entry -> entry.getValue()).reversed()
                .thenComparingInt(entry -> entry.getKey().blockId())
                .thenComparingInt(entry -> entry.getKey().data()));

        var symbols = new LinkedHashMap<BuildingBlockKey, Character>();
        for (int i = 0; i < palette.size() && i < BUILDING_SYMBOLS.length; i++) {
            symbols.put(palette.get(i).getKey(), BUILDING_SYMBOLS[i]);
        }

        var markdown = new StringBuilder();
        if (layer != null) {
            markdown.append("# Building Layer: ").append(id).append(" y=").append(layer).append("\n\n");
            markdown.append("Each row is z. Each character is x. `.` means air. Read this layer carefully before placing blocks. Use the palette from `GET /buildings/")
                    .append(id)
                    .append("` without `layer`.\n\n");
            markdown.append("```text\n");
            for (int z = 0; z < schematic.length(); z++) {
                appendBuildingLayerRow(markdown, schematic, symbols, layer, z);
            }
            markdown.append("```\n");
            return markdown.toString();
        }

        markdown.append("# Building: ").append(id).append("\n\n");
        markdown.append("## Summary\n");
        markdown.append("- id: `").append(id).append("`\n");
        markdown.append("- size: `").append(schematic.width()).append(" x ").append(schematic.height()).append(" x ").append(schematic.length()).append("`\n");
        markdown.append("- materials: `").append(schematic.materials().isBlank() ? "unknown" : schematic.materials()).append("`\n");
        markdown.append("- nonAirBlocks: `").append(nonAirBlocks).append("`\n");
        markdown.append("- entities: `").append(schematic.entities().size()).append("`\n");
        markdown.append("- tileEntities: `").append(schematic.tileEntities().size()).append("`\n");
        var placement = schematic.worldEditPlacement();
        if (placement.originX() != null || placement.originY() != null || placement.originZ() != null) {
            markdown.append("- origin: `").append(formatNullableVec(placement.originX(), placement.originY(), placement.originZ())).append("`\n");
        }
        if (placement.offsetX() != null || placement.offsetY() != null || placement.offsetZ() != null) {
            markdown.append("- offset: `").append(formatNullableVec(placement.offsetX(), placement.offsetY(), placement.offsetZ())).append("`\n");
        }
        markdown.append("- layerRange: `0..").append(schematic.height() - 1).append("`\n");
        markdown.append("- layer: `not included; call /buildings/").append(id).append("?layer=Y`\n");
        markdown.append("\n## Palette\n");
        markdown.append("- `.`: air, skipped while building\n");
        for (var entry : palette) {
            var key = entry.getKey();
            var symbol = symbols.getOrDefault(key, '?');
            var legacyBlock = SchemLegacyBlockMap.resolve(key.blockId(), key.data());
            markdown.append("- `").append(symbol).append("`: legacy `")
                    .append(key.blockId()).append(":").append(key.data())
                    .append("`, block `").append(legacyBlock.known() ? legacyBlock.resourceLocation() : "unknown")
                    .append("`, count `").append(entry.getValue()).append("`");
            if (!legacyBlock.note().isBlank()) {
                markdown.append(", note: ").append(legacyBlock.note());
            }
            if (symbol == '?') {
                markdown.append(", overflow palette entry");
            }
            markdown.append("\n");
        }
        return markdown.toString();
    }

    private static void appendBuildingLayerRow(StringBuilder markdown, SchemReader.Schematic schematic, Map<BuildingBlockKey, Character> symbols, int y, int z) {
        for (int x = 0; x < schematic.width(); x++) {
            var block = schematic.blockAt(x, y, z);
            if (block.blockId() == 0) {
                markdown.append('.');
            } else {
                markdown.append(symbols.getOrDefault(new BuildingBlockKey(block.blockId(), block.data()), '?'));
            }
        }
        markdown.append('\n');
    }

    private static String formatNullableVec(Integer x, Integer y, Integer z) {
        return formatNullableInt(x) + "," + formatNullableInt(y) + "," + formatNullableInt(z);
    }

    private static String formatNullableInt(Integer value) {
        return value == null ? "?" : value.toString();
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
    private static void err400(HTTPResponse response, RErrorCode errcode,String reason) {
        writeJson(response, 400, RMcpResponse.error(errcode.id(),reason));
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

    private record HotbarSelectRequest(Integer slot, boolean dryRun) {
    }

    private record MenuDropRequest(Integer slot, Integer count, boolean dryRun) {
    }

    private record CraftRequest(Map<String, Integer> slots, String shape, Integer outputSlot, Integer times,
                                boolean dryRun) {
    }

    private record BlockActionRequest(int x, int y, int z, String face) {
    }

    private record PlayerMoveRequest(Double x, Double y, Double z) {
    }

    private record ItemPickupRequest(List<String> ids, Double radius, Integer limit) {
    }

    private record ItemPickupParams(List<UUID> ids, double radius, int limit, RErrorCode error) {
        private static ItemPickupParams error(RErrorCode error) {
            return new ItemPickupParams(List.of(), 64.0D, 256, error);
        }
    }

    private record BlockMapRequest(Integer x, Integer y, Integer z, int radius) {
    }

    private record TerrainProfileRequest(String axis, Integer x, Integer y, Integer z, int length, int verticalRadius) {
    }

    private record BlockBatchActionRequest(List<RMcpBlockPosData> positions) {
    }

    private record BlockStateBatchRequest(List<RMcpBlockPosData> positions) {
    }

    private record BlockBoxActionRequest(RMcpBlockPosData from, RMcpBlockPosData to) {
    }

    private record ContainerMoveRequest(ContainerEndpointRequest from, ContainerEndpointRequest to, int count,
                                        boolean dryRun) {
    }

    private record ContainerPutRequest(Integer fromInventorySlot, ContainerEndpointRequest to, int count,
                                       boolean dryRun) {
    }

    private record ContainerTakeRequest(ContainerEndpointRequest from, Integer toInventorySlot, int count,
                                        boolean dryRun) {
    }

    private record ContainerEndpointRequest(String pos, String side, Integer slot) {
    }

    private record BuildingBlockKey(int blockId, int data) {
    }
}
