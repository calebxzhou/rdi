package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.*
import io.fusionauth.http.HTTPMethod
import io.fusionauth.http.server.*
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.text.contains
import kotlin.text.isBlank
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.text.toBoolean
import kotlin.text.trim

object RMHttpServer {
    private const val BLOCK_BATCH_LIMIT = 512
    private const val CRAFT_PARALLEL_LIMIT = 64
    private const val ITEM_PICKUP_LIMIT = 2048
    private const val CONTAINER_BATCH_LIMIT = 64
    private const val NEARBY_RESOURCES_LIMIT = 256
    private val NEARBY_RESOURCE_CATEGORIES =
        mutableListOf("ore", "fluid", "water", "lava", "wood", "crop", "container", "block_entity", "spawner")
    private val GSON: Gson = GsonBuilder().create()
    private var server: HTTPServer? = null
    private var connector: RMcpGameConnector? = null
    private var boundPort = -1
    private var shutdownHookRegistered = false

    @Synchronized
    fun start(newConnector: RMcpGameConnector): Int {
        if (server != null) {
            connector = newConnector
            return boundPort
        }
        connector = newConnector
        val port = selectAvailablePort()
        server = HTTPServer()
            .withConfiguration(
                HTTPServerConfiguration()
                    .withCompressByDefault(false)
                    .withHandler(HTTPHandler { request: HTTPRequest, response: HTTPResponse ->
                        handle(request, response)
                    })
                    .withListener(HTTPListenerConfiguration(port))
            )
            .start()
        if (!waitUntilListening(port)) {
            stop()
            throw IllegalStateException("RMCP Server failed to listen on port " + port)
        }
        boundPort = port
        writePortFile(port)
        println("RMCP Server started on port " + port)
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(Thread(::stop, "rdi-mcp-http-stop"))
            shutdownHookRegistered = true
        }
        return port
    }

    @Synchronized
    fun stop() {
        server?.close() ?: return
        server = null
        boundPort = -1
    }

    private fun selectAvailablePort(): Int {
        try {
            ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { socket ->
                return socket.getLocalPort()
            }
        } catch (e: IOException) {
            throw IllegalStateException("Unable to select an available RMCP port", e)
        }
    }

    private fun waitUntilListening(port: Int): Boolean {
        val deadline = System.currentTimeMillis() + 1000
        while (System.currentTimeMillis() < deadline) {
            if (canConnect(InetAddress.getLoopbackAddress(), port) || canConnect("::1", port) || canConnect(
                    "127.0.0.1",
                    port
                )
            ) {
                return true
            }
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    private fun canConnect(address: InetAddress, port: Int): Boolean {
        try {
            Socket(address, port).use { socket ->
                return true
            }
        } catch (e: IOException) {
            return false
        }
    }

    private fun canConnect(host: String, port: Int): Boolean {
        try {
            return canConnect(InetAddress.getByName(host), port)
        } catch (e: IOException) {
            return false
        }
    }

    private fun writePortFile(port: Int) {
        try {
            Files.writeString(File("rmcp_port.txt").toPath(), port.toString(), StandardCharsets.UTF_8)
        } catch (e: IOException) {
            throw IllegalStateException("Unable to write rmcp_port.txt", e)
        }
    }

    private fun handle(request: HTTPRequest, response: HTTPResponse) {
        if (!request.getMethod().`is`(HTTPMethod.GET) && !request.getMethod().`is`(HTTPMethod.POST)) {
            err400(response, RErrorCode.METHOD_NOT_ALLOWED)
            return
        }
        for (route in Route.Companion.LIST) {
            if (route.matches(request)) {
                try {
                    route.handler(request, response)
                } catch (e: RMError) {
                    err400(response, e.errorCode())
                } catch (e: RMcpEndpointException) {
                    err400(response, e.code())
                } catch (e: Exception) {
                    if (e is ClassNotFoundException) {
                        err400(response, RErrorCode.MOD_CLASS_NOT_FOUND, e.message)
                    } else {
                        err400(response, RErrorCode.INTERNAL_ERROR)
                    }
                }
                return
            }
        }
        err404(response)
    }

    @Throws(RMError::class)
    private fun requirePlayerInWorld() {
        if (connector?.playerInWorld() != true) {
            throw RMError(RErrorCode.NO_PLAYER)
        }
    }

    @Throws(RMError::class)
    private fun handleMods(request: HTTPRequest, response: HTTPResponse) {
        var id = request.getURLParameter("id")
        if (id == null) {
            ok(response, connector!!.modIds())
            return
        }
        id = id.trim { it <= ' ' }
        if (id.isEmpty() || id.contains("/") || id.contains("\\") || id.contains(" ") || id.contains("`")) {
            throw RMError(RErrorCode.BAD_MOD_ID)
        }
        val mod = connector!!.modData(id)
        if (mod == null) {
            throw RMError(RErrorCode.NO_MOD)
        }
        ok(response, mod)
    }

    @Throws(RMError::class)
    private fun handleQuestChapterList(request: HTTPRequest, response: HTTPResponse) {
        requirePlayerInWorld()
        val data = connector!!.questChapterList()
        if (data == null) {
            throw RMError(RErrorCode.QUEST_DATA_NOT_LOADED)
        }
        ok(response, data)
    }

    @Throws(RMError::class)
    private fun handleReachableQuests(request: HTTPRequest, response: HTTPResponse) {
        requirePlayerInWorld()
        val data = connector!!.reachableQuests()
        if (data == null) {
            throw RMError(RErrorCode.QUEST_DATA_NOT_LOADED)
        }
        ok(response, data)
    }

    @Throws(RMError::class)
    private fun handleQuestDetail(request: HTTPRequest, response: HTTPResponse) {
        val prefix = "/quest/detail/"
        val path = request.getPath()
        if (path.length <= prefix.length) {
            throw RMError(RErrorCode.MISSING_QUEST_ID)
        }
        val id = URLDecoder.decode(path.substring(prefix.length), StandardCharsets.UTF_8).trim { it <= ' ' }
        if (id.isEmpty() || id.contains("/") || id.contains("\\") || id.contains(" ") || id.contains("`")) {
            throw RMError(RErrorCode.BAD_QUEST_ID)
        }
        requirePlayerInWorld()
        val data = connector!!.questDetail(id)
        if (data == null) {
            throw RMError(RErrorCode.NO_QUEST)
        }
        if ("full".equals(request.getURLParameter("view"), ignoreCase = true)) {
            ok(response, data)
            return
        }
        ok(response, questSummaryData(data))
    }

    @Throws(RMError::class)
    private fun handleQuestDetailRef(request: HTTPRequest, response: HTTPResponse) {
        val detail = questDetailRefData(request)
        ok(response, detail)
    }

    @Throws(RMError::class)
    private fun handleQuestDetailRefMarkdown(request: HTTPRequest, response: HTTPResponse) {
        val detail = questDetailRefData(request)
        writeMarkdown(response, questDetailMarkdown(detail))
    }

    @Throws(RMError::class)
    private fun questDetailRefData(request: HTTPRequest): RQuest {
        var id = request.getURLParameter("ref")
        if (id == null || id.isBlank()) {
            id = request.getURLParameter("id")
        }
        if (id == null || id.isBlank() || id.contains("/") || id.contains("\\") || id.contains(" ") || id.contains("`")) {
            throw RMError(RErrorCode.BAD_QUEST_ID)
        }
        requirePlayerInWorld()
        val data = connector!!.questDetail(id.trim { it <= ' ' })
        if (data == null) {
            throw RMError(RErrorCode.NO_QUEST)
        }
        return data
    }

    @Throws(RMError::class)
    private fun handleQuestChapter(request: HTTPRequest, response: HTTPResponse) {
        val prefix = "/quest/chapter/"
        val path = request.getPath()
        if (path.length <= prefix.length) {
            throw RMError(RErrorCode.MISSING_QUEST_CHAPTER_ID)
        }
        val id = URLDecoder.decode(path.substring(prefix.length), StandardCharsets.UTF_8).trim { it <= ' ' }
        if (id.isEmpty() || id.contains("/") || id.contains("\\") || id.contains(" ") || id.contains("`")) {
            throw RMError(RErrorCode.BAD_QUEST_CHAPTER_ID)
        }
        requirePlayerInWorld()
        val data = connector!!.questChapter(id)
        if (data == null) {
            throw RMError(RErrorCode.NO_QUEST_CHAPTER)
        }
        ok(response, data)
    }

    @Throws(RMError::class)
    private fun handlePrompts(request: HTTPRequest, response: HTTPResponse) {
        val doc = readApiDoc()
        if (doc == null) {
            throw RMError(RErrorCode.PROMPTS_NOT_FOUND)
        }
        writeMarkdown(response, doc)
    }

    @Throws(RMError::class)
    private fun handleErrCode(request: HTTPRequest, response: HTTPResponse) {
        val prefix = "/errcode/"
        val path = request.getPath()
        if (path.length <= prefix.length) {
            throw RMError(RErrorCode.MISSING_ERRCODE)
        }
        val code = URLDecoder.decode(path.substring(prefix.length), StandardCharsets.UTF_8).trim { it <= ' ' }
        if (code.isEmpty() || code.contains("/") || code.contains(" ") || code.contains("`")) {
            throw RMError(RErrorCode.BAD_ERRCODE)
        }
        val errcode = RErrorCode.get(code)
        if (errcode == null) {
            throw RMError(RErrorCode.UNKNOWN_ERRCODE)
        }
        ok(response, errcode)
    }

    @Throws(RMError::class)
    private fun handleApiDoc(request: HTTPRequest, response: HTTPResponse) {
        val prefix = "/apidoc/"
        val path = request.getPath()
        if (path.length <= prefix.length) {
            throw RMError(RErrorCode.MISSING_APIDOC)
        }
        val file = URLDecoder.decode(path.substring(prefix.length), StandardCharsets.UTF_8).trim { it <= ' ' }
        if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.contains("..") || !file.endsWith(".md")) {
            throw RMError(RErrorCode.BAD_APIDOC)
        }
        val doc = readResourceText("mcp/apidoc/" + file)
        if (doc == null) {
            throw RMError(RErrorCode.UNKNOWN_APIDOC)
        }
        writeMarkdown(response, doc)
    }

    private fun readApiDoc(): String? {
        return readResourceText("mcp/summary.md")
    }


    private fun readResourceText(path: String?): String? {
        try {
            RMHttpServer::class.java.getClassLoader().getResourceAsStream(path).use { input ->
                return input?.readAllBytes()?.toString(StandardCharsets.UTF_8)
            }
        } catch (e: IOException) {
            return null
        }
    }

    private fun writeMarkdown(response: HTTPResponse, markdown: String) {
        writeMarkdown(response, 200, markdown)
    }

    private fun writeMarkdown(response: HTTPResponse, status: Int, markdown: String) {
        try {
            response.setStatus(status)
            response.setContentType("text/markdown; charset=utf-8")
            response.getWriter().write(markdown)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(RMError::class)
    private fun handlePos(request: HTTPRequest, response: HTTPResponse) {
        val pos = connector!!.posData()
        if (pos == null) {
            throw RMError(RErrorCode.NO_PLAYER)
        }
        ok(response, pos)
    }

    @Throws(RMError::class)
    private fun handleMainHand(request: HTTPRequest, response: HTTPResponse) {
        requirePlayerInWorld()
        val data: MutableMap<String, Any?> = connector!!.mainHandItemData()
        if (isDetailRequest(request)) {
            okFull(response, data)
        } else {
            ok(response, compactMainHand(data))
        }
    }

    @Throws(RMError::class)
    private fun handleInventory(request: HTTPRequest, response: HTTPResponse) {
        requirePlayerInWorld()
        writeCsv(response, inventoryCsv(connector!!.inventoryData()))
    }

    @Throws(RMError::class)
    private fun handleInventoryDetail(request: HTTPRequest, response: HTTPResponse) {
        requirePlayerInWorld()
        val item = inventoryDetailItem(request, connector!!.inventoryData())
        okFull(response, item)
    }

    @Throws(RMError::class)
    private fun handleMenu(request: HTTPRequest, response: HTTPResponse) {
        requirePlayerInWorld()
        ok(response, connector!!.menuData())
    }

    @Throws(RMError::class)
    private fun handleMenuClose(request: HTTPRequest, response: HTTPResponse) {
        requirePlayerInWorld()
        ok(response, connector!!.closeMenu())
    }

    @Throws(RMError::class)
    private fun handleMenuDrop(request: HTTPRequest, response: HTTPResponse) {
        val dropRequest = readMenuDropRequest(request)
        if (dropRequest == null || dropRequest.slot == null || dropRequest.count == null || dropRequest.count <= 0) {
            throw RMError(RErrorCode.BAD_REQUEST)
        }
        requirePlayerInWorld()
        ok(response, connector!!.dropMenuItem(dropRequest.slot, dropRequest.count, dropRequest.dryRun))
    }

    private fun readMenuDropRequest(request: HTTPRequest): MenuDropRequest? {
        try {
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    return GSON.fromJson<MenuDropRequest?>(body, MenuDropRequest::class.java)
                }
            }
            return MenuDropRequest(
                request.getURLParameter("slot").toString().toInt(),
                request.getURLParameter("count").toString().toInt(),
                request.getURLParameter("dryRun").toString().toBoolean()
            )
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(RMError::class)
    private fun handleInventorySwap(request: HTTPRequest, response: HTTPResponse) {
        val swapRequest = readInventorySwapRequest(request)
        if (swapRequest == null || swapRequest.from == null || swapRequest.from.isBlank() || swapRequest.to == null || swapRequest.to.isBlank()) {
            throw RMError(RErrorCode.BAD_REQUEST)
        }
        requirePlayerInWorld()
        ok(
            response,
            connector!!.swapInventorySlots(
                swapRequest.from.trim { it <= ' ' },
                swapRequest.to.trim { it <= ' ' },
                swapRequest.dryRun
            )
        )
    }

    private fun readInventorySwapRequest(request: HTTPRequest): InventorySwapRequest? {
        try {
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    return GSON.fromJson<InventorySwapRequest?>(body, InventorySwapRequest::class.java)
                }
            }
            return InventorySwapRequest(
                request.getURLParameter("from"),
                request.getURLParameter("to"),
                request.getURLParameter("dryRun").toString().toBoolean()
            )
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(RMError::class)
    private fun handleInventoryMove(request: HTTPRequest, response: HTTPResponse) {
        val moveRequest = readInventoryMoveRequest(request)
        if (moveRequest == null || moveRequest.from == null || moveRequest.from.isBlank() || moveRequest.to == null || moveRequest.to.isBlank()) {
            throw RMError(RErrorCode.BAD_REQUEST)
        }
        requirePlayerInWorld()
        ok(
            response,
            connector!!.moveInventoryItems(
                moveRequest.from.trim { it <= ' ' },
                moveRequest.to.trim { it <= ' ' },
                moveRequest.count,
                moveRequest.dryRun
            )
        )
    }

    private fun readInventoryMoveRequest(request: HTTPRequest): InventoryMoveRequest? {
        try {
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    return GSON.fromJson<InventoryMoveRequest?>(body, InventoryMoveRequest::class.java)
                }
            }
            return InventoryMoveRequest(
                request.getURLParameter("from"),
                request.getURLParameter("to"),
                request.getURLParameter("count").toString().toInt(),
                request.getURLParameter("dryRun").toString().toBoolean()
            )
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(RMError::class)
    private fun handleHotbarSelect(request: HTTPRequest, response: HTTPResponse) {
        val selectRequest = readHotbarSelectRequest(request)
        if (selectRequest == null || selectRequest.slot == null) {
            throw RMError(RErrorCode.BAD_REQUEST)
        }
        requirePlayerInWorld()
        ok(response, connector!!.selectHotbarSlot(selectRequest.slot, selectRequest.dryRun))
    }

    private fun readHotbarSelectRequest(request: HTTPRequest): HotbarSelectRequest? {
        try {
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    return GSON.fromJson<HotbarSelectRequest?>(body, HotbarSelectRequest::class.java)
                }
            }
            return HotbarSelectRequest(
                request.getURLParameter("slot").toString().toInt(),
                request.getURLParameter("dryRun").toString().toBoolean()
            )
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(RMError::class)
    private fun handleCraft(request: HTTPRequest, response: HTTPResponse) {
        val craftRequest = readCraftRequest(request)
        if (craftRequest == null) {
            throw RMError(RErrorCode.BAD_REQUEST)
        }
        if (craftRequest.slots == null || craftRequest.slots.isEmpty() || craftRequest.shape == null || craftRequest.shape.isBlank()) {
            throw RMError(RErrorCode.BAD_SHAPE)
        }
        if (craftRequest.outputSlot == null) {
            throw RMError(RErrorCode.BAD_SLOT)
        }
        if (craftRequest.times!! <= 0 || craftRequest.times > 64) {
            throw RMError(RErrorCode.BAD_COUNT)
        }
        requirePlayerInWorld()
        ok(
            response,
            connector!!.craft(
                craftRequest.slots,
                craftRequest.shape.trim { it <= ' ' },
                craftRequest.outputSlot,
                craftRequest.times,
                craftRequest.dryRun
            )
        )
    }

    private fun readCraftRequest(request: HTTPRequest): CraftRequest? {
        try {
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    val parsed = GSON.fromJson<CraftRequest?>(body, CraftRequest::class.java)
                    if (parsed == null) {
                        return null
                    }
                    return CraftRequest(
                        parsed.slots,
                        parsed.shape,
                        parsed.outputSlot,
                        if (parsed.times == null) 1 else parsed.times,
                        parsed.dryRun
                    )
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(RMError::class)
    private fun handleCraftParallel(request: HTTPRequest, response: HTTPResponse) {
        val craftRequest = readCraftParallelRequest(request)
        if (craftRequest == null || craftRequest.crafts == null || craftRequest.crafts!!.isEmpty()) {
            throw RMError(RErrorCode.BAD_REQUEST)
        }
        if (craftRequest.crafts!!.size > CRAFT_PARALLEL_LIMIT) {
            throw RMError(RErrorCode.BAD_LIMIT)
        }
        requirePlayerInWorld()
        ok(response, connector!!.craftParallel(craftRequest))
    }

    private fun readCraftParallelRequest(request: HTTPRequest): RMcpCraftParallelRequest? {
        try {
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                return if (body.isBlank()) null else GSON.fromJson<RMcpCraftParallelRequest?>(
                    body,
                    RMcpCraftParallelRequest::class.java
                )
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(RMError::class)
    private fun handlePlace(request: HTTPRequest, response: HTTPResponse) {
        val pos = readBlockActionRequest(request)
        if (pos == null) {
            throw RMError(RErrorCode.BAD_POS)
        }
        requirePlayerInWorld()
        ok(response, connector!!.placeBlock(pos.x, pos.y, pos.z, pos.face!!))
    }

    @Throws(RMError::class)
    private fun handleMove(request: HTTPRequest, response: HTTPResponse) {
        val pos = readPlayerMoveRequest(request)
        if (pos == null) {
            throw RMError(RErrorCode.BAD_POS)
        }
        requirePlayerInWorld()
        ok(response, connector!!.movePlayer(pos.x, pos.y, pos.z))
    }

    @Throws(RMError::class)
    private fun handleRespawn(request: HTTPRequest, response: HTTPResponse) {
        requirePlayerInWorld()
        ok(response, connector!!.respawnPlayer())
    }

    @Throws(RMError::class)
    private fun handleEntityPickupItem(request: HTTPRequest, response: HTTPResponse) {
        val pickup = readItemPickupRequest(request)
        if (pickup.error != null) {
            throw RMError(pickup.error)
        }
        requirePlayerInWorld()
        ok(response, connector!!.pickupItemEntities(pickup.ids, pickup.radius, pickup.limit))
    }

    @Throws(RMError::class)
    private fun handleItemDrop(request: HTTPRequest, response: HTTPResponse) {
        val drop = readItemDropRequest(request)
        if (drop == null || drop.from == null || drop.from!!.isBlank() || drop.count == null || drop.count!! <= 0 || drop.pos == null) {
            throw RMError(RErrorCode.BAD_REQUEST)
        }
        val pos: RMcpItemDropRequest.Pos = drop.pos!!
        if (pos.x == null || pos.y == null || pos.z == null || !pos.x!!.isFinite() || !pos.y!!.isFinite() || !pos.z!!.isFinite()) {
            throw RMError(RErrorCode.BAD_POS)
        }
        if (drop.pickupDelay != null && (drop.pickupDelay!! < 0 || drop.pickupDelay!! > 32767)) {
            throw RMError(RErrorCode.BAD_REQUEST)
        }
        requirePlayerInWorld()
        ok(
            response, connector!!.dropInventoryItem(
                RMcpItemDropRequest(
                    drop.from!!.trim { it <= ' ' },
                    drop.count,
                    RMcpItemDropRequest.Pos(pos.x, pos.y, pos.z),
                    drop.pickupDelay,
                    drop.dryRun
                )
            )
        )
    }

    private fun readItemDropRequest(request: HTTPRequest): RMcpItemDropRequest? {
        try {
            var parsed: RMcpItemDropRequest? = null
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    parsed = GSON.fromJson<RMcpItemDropRequest?>(body, RMcpItemDropRequest::class.java)
                }
            }
            val from = firstNonBlank(request.getURLParameter("from"), if (parsed == null) null else parsed.from)
            val count = readOptionalInteger(
                request.getURLParameter("count"),
                (if (parsed == null) null else parsed.count)!!
            )
            val pickupDelay = readOptionalInteger(
                request.getURLParameter("pickupDelay"),
                (if (parsed == null) null else parsed.pickupDelay)!!
            )
            val dryRunText = request.getURLParameter("dryRun")
            val dryRun = if (dryRunText == null) parsed != null && parsed.dryRun else dryRunText.toBoolean()
            val parsedPos = if (parsed == null) null else parsed.pos
            val x = readOptionalDouble(
                request.getURLParameter("x"),
                (if (parsedPos == null) null else parsedPos.x)!!
            )
            val y = readOptionalDouble(
                request.getURLParameter("y"),
                (if (parsedPos == null) null else parsedPos.y)!!
            )
            val z = readOptionalDouble(
                request.getURLParameter("z"),
                (if (parsedPos == null) null else parsedPos.z)!!
            )
            return RMcpItemDropRequest(from, count, RMcpItemDropRequest.Pos(x, y, z), pickupDelay, dryRun)
        } catch (e: Exception) {
            return null
        }
    }

    private fun readOptionalInteger(text: String, fallback: Int): Int {
        return if (text == null || text.isBlank()) fallback else text.trim { it <= ' ' }.toInt()
    }

    private fun readOptionalDouble(text: String, fallback: Double): Double? {
        return if (text == null || text.isBlank()) fallback else text.trim { it <= ' ' }.toDouble()
    }

    private fun readItemPickupRequest(request: HTTPRequest): ItemPickupParams {
        try {
            var texts: MutableList<String>? = null
            var radius: Double? = null
            var limit: Int? = null
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    val parsed = GSON.fromJson<ItemPickupRequest?>(body, ItemPickupRequest::class.java)
                    if (parsed != null) {
                        texts = parsed.ids
                        radius = parsed.radius
                        limit = parsed.limit
                    }
                }
            }
            val idsText = request.getURLParameter("ids")
            if (idsText != null && !idsText.isBlank()) {
                texts = idsText.split(",").filter { it.isNotEmpty() }.toMutableList()
            }
            val radiusText = request.getURLParameter("radius")
            if (radiusText != null && !radiusText.isBlank()) {
                try {
                    radius = radiusText.trim { it <= ' ' }.toDouble()
                } catch (e: NumberFormatException) {
                    return ItemPickupParams.Companion.error(RErrorCode.BAD_RADIUS)
                }
            }
            val limitText = request.getURLParameter("limit")
            if (limitText != null && !limitText.isBlank()) {
                try {
                    limit = limitText.trim { it <= ' ' }.toInt()
                } catch (e: NumberFormatException) {
                    return ItemPickupParams.Companion.error(RErrorCode.BAD_LIMIT)
                }
            }
            radius = if (radius == null) 64.0 else radius
            limit = if (limit == null) 256 else limit
            if (!radius.isFinite() || radius < 1.0 || radius > 64.0) {
                return ItemPickupParams.Companion.error(RErrorCode.BAD_RADIUS)
            }
            if (limit < 0 || limit > ITEM_PICKUP_LIMIT) {
                return ItemPickupParams.Companion.error(RErrorCode.BAD_LIMIT)
            }
            val ids = ArrayList<UUID>()
            if (texts != null) {
                if (texts.size > ITEM_PICKUP_LIMIT) {
                    return ItemPickupParams.Companion.error(RErrorCode.BAD_IDS)
                }
                for (text in texts) {
                    if (text == null || text.isBlank()) {
                        return ItemPickupParams.Companion.error(RErrorCode.BAD_IDS)
                    }
                    val id = UUID.fromString(text.trim { it <= ' ' })
                    if (!ids.contains(id)) {
                        ids.add(id)
                    }
                }
            }
            return ItemPickupParams(ids.toMutableList(), radius, limit, null)
        } catch (e: Exception) {
            return ItemPickupParams.Companion.error(RErrorCode.BAD_IDS)
        }
    }

    private fun readPlayerMoveRequest(request: HTTPRequest): PlayerMoveRequest? {
        try {
            var move: PlayerMoveRequest? = null
            val xText = request.getURLParameter("x")
            val yText = request.getURLParameter("y")
            val zText = request.getURLParameter("z")
            if (xText != null || yText != null || zText != null) {
                move = PlayerMoveRequest(
                    xText.toString().toDouble(),
                    yText.toString().toDouble(),
                    zText.toString().toDouble()
                )
            } else if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    move = GSON.fromJson<PlayerMoveRequest?>(body, PlayerMoveRequest::class.java)
                }
            }
            if (move == null || move.x == null || move.y == null || move.z == null) {
                return null
            }
            val x = move.x
            val y = move.y
            val z = move.z
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
                return null
            }
            return PlayerMoveRequest(x, y, z)
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(RMError::class)
    private fun handleBreak(request: HTTPRequest, response: HTTPResponse) {
        val pos = readBlockActionRequest(request)
        if (pos == null) {
            throw RMError(RErrorCode.BAD_POS)
        }
        requirePlayerInWorld()
        ok(response, connector!!.breakBlock(pos.x, pos.y, pos.z))
    }

    @Throws(RMError::class)
    private fun handleItemUseOnBlock(request: HTTPRequest, response: HTTPResponse) {
        val use = readItemUseOnBlockRequest(request)
        if (use == null || use.pos == null || use.pos!!.isBlank()) {
            throw RMError(RErrorCode.BAD_POS)
        }
        if (use.times == null || use.times!! < 1 || use.times!! > 64) {
            throw RMError(RErrorCode.BAD_COUNT)
        }
        if (use.fromInventorySlot != null && (use.fromInventorySlot!! < 0 || use.fromInventorySlot!! > 35)) {
            throw RMError(RErrorCode.BAD_SLOT)
        }
        requirePlayerInWorld()
        ok(response, connector!!.useItemOnBlock(use))
    }

    private fun readItemUseOnBlockRequest(request: HTTPRequest): RMcpItemUseOnBlockRequest? {
        try {
            var parsed: RMcpItemUseOnBlockRequest? = null
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    parsed = GSON.fromJson<RMcpItemUseOnBlockRequest?>(body, RMcpItemUseOnBlockRequest::class.java)
                }
            }
            val pos = firstNonBlank(request.getURLParameter("pos"), if (parsed == null) null else parsed.pos)
            val face = firstNonBlank(request.getURLParameter("face"), if (parsed == null) null else parsed.face)
            val itemId = firstNonBlank(request.getURLParameter("itemId"), if (parsed == null) null else parsed.itemId)
            val hand = firstNonBlank(request.getURLParameter("hand"), if (parsed == null) null else parsed.hand)
            var slot = if (parsed == null) null else parsed.fromInventorySlot
            var times = if (parsed == null) null else parsed.times
            var dryRun = parsed != null && parsed.dryRun
            val slotText = request.getURLParameter("fromInventorySlot")
            if (slotText != null && !slotText.isBlank()) {
                slot = slotText.trim { it <= ' ' }.toInt()
            }
            val timesText = request.getURLParameter("times")
            if (timesText != null && !timesText.isBlank()) {
                times = timesText.trim { it <= ' ' }.toInt()
            }
            val dryRunText = request.getURLParameter("dryRun")
            if (dryRunText != null && !dryRunText.isBlank()) {
                dryRun = dryRunText.trim { it <= ' ' }.toBoolean()
            }
            return RMcpItemUseOnBlockRequest(pos, face, itemId, slot, hand, if (times == null) 1 else times, dryRun)
        } catch (e: Exception) {
            return null
        }
    }

    private fun firstNonBlank(first: String, second: String?): String? {
        if (first != null && !first.isBlank()) {
            return first.trim { it <= ' ' }
        }
        return if (second == null) null else second.trim { it <= ' ' }
    }

    private fun readBlockActionRequest(request: HTTPRequest): BlockActionRequest? {
        try {
            return BlockActionRequest(
                request.getURLParameter("x").toString().toInt(),
                request.getURLParameter("y").toString().toInt(),
                request.getURLParameter("z").toString().toInt(),
                request.getURLParameter("face")
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun handlePlaceBatch(request: HTTPRequest, response: HTTPResponse) {
        val batch = readBlockBatchActionRequest(request)
        if (batch == null || batch.positions == null || batch.positions.isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS)
            return
        }
        if (batch.positions.any { it == null }) {
            err400(response, RErrorCode.BAD_POSITIONS)
            return
        }
        if (batch.positions.size > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.placeBlocks(batch.positions))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handlePlaceDiscrete(request: HTTPRequest, response: HTTPResponse) {
        val discrete = readPlaceDiscreteRequest(request)
        if (discrete == null || discrete.targets == null || discrete.targets!!.isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS)
            return
        }
        if (discrete.blockId == null || discrete.blockId!!.isBlank()) {
            err400(response, RErrorCode.BAD_BLOCK_ID)
            return
        }
        if (discrete.targets!!.size > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.placeBlocksDiscrete(discrete))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readPlaceDiscreteRequest(request: HTTPRequest): RMcpPlaceDiscreteRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) {
                return null
            }
            return GSON.fromJson<RMcpPlaceDiscreteRequest?>(body, RMcpPlaceDiscreteRequest::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    private fun handlePlacePalette(request: HTTPRequest, response: HTTPResponse) {
        val palette = readPlacePaletteRequest(request)
        if (palette == null || palette.palette == null || palette.palette!!.isEmpty()) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        if (palette.targets == null || palette.targets!!.isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS)
            return
        }
        if (palette.targets!!.size > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.placeBlocksPalette(palette))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readPlacePaletteRequest(request: HTTPRequest): RMcpPlacePaletteRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) {
                return null
            }
            return GSON.fromJson<RMcpPlacePaletteRequest?>(body, RMcpPlacePaletteRequest::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    private fun handleBreakBatch(request: HTTPRequest, response: HTTPResponse) {
        val batch = readBlockBatchActionRequest(request)
        if (batch == null || batch.positions == null || batch.positions.isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS)
            return
        }
        if (batch.positions.size > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.breakBlocks(batch.positions))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readBlockBatchActionRequest(request: HTTPRequest): BlockBatchActionRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) {
                return null
            }
            return GSON.fromJson<BlockBatchActionRequest?>(body, BlockBatchActionRequest::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    private fun handlePlaceBox(request: HTTPRequest, response: HTTPResponse) {
        val box = readPlaceBoxRequest(request)
        if (box == null || box.startPos == null || box.endOffset == null) {
            err400(response, RErrorCode.BAD_BOX)
            return
        }
        if (box.blockId == null || box.blockId!!.isBlank()) {
            err400(response, RErrorCode.BAD_BLOCK_ID)
            return
        }
        if (offsetBoxBlockCount(box.endOffset!!) > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.placeBlockBox(box))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handlePlaceRing(request: HTTPRequest, response: HTTPResponse) {
        val ring = readPlaceRingRequest(request)
        if (ring == null || ring.startPos == null || ring.endOffset == null) {
            err400(response, RErrorCode.BAD_BOX)
            return
        }
        if (ring.blockId == null || ring.blockId!!.isBlank()) {
            err400(response, RErrorCode.BAD_BLOCK_ID)
            return
        }
        if (offsetRingBlockCount(ring.endOffset!!) > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.placeBlockRing(ring))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readPlaceBoxRequest(request: HTTPRequest): RMcpPlaceBoxRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) {
                return null
            }
            return GSON.fromJson<RMcpPlaceBoxRequest?>(body, RMcpPlaceBoxRequest::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    private fun readPlaceRingRequest(request: HTTPRequest): RMcpPlaceRingRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) {
                return null
            }
            return GSON.fromJson<RMcpPlaceRingRequest?>(body, RMcpPlaceRingRequest::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    private fun handleBreakBox(request: HTTPRequest, response: HTTPResponse) {
        val box = readBlockBoxActionRequest(request)
        if (box == null || box.from == null || box.to == null) {
            err400(response, RErrorCode.BAD_BOX)
            return
        }
        if (boxBlockCount(box.from, box.to) > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.breakBlockBox(box.from, box.to, box.dryRun))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readBlockBoxActionRequest(request: HTTPRequest): BlockBoxActionRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) {
                return null
            }
            return GSON.fromJson<BlockBoxActionRequest?>(body, BlockBoxActionRequest::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    private fun boxBlockCount(from: RBlockPos, to: RBlockPos): Long {
        return ((abs(from.x - to.x) + 1).toLong() * (abs(from.y - to.y) + 1)
                * (abs(from.z - to.z) + 1))
    }

    private fun offsetBoxBlockCount(endOffset: RBlockPos): Long {
        return ((abs(endOffset.x) + 1).toLong() * (abs(endOffset.y) + 1)
                * (abs(endOffset.z) + 1))
    }

    private fun offsetRingBlockCount(endOffset: RBlockPos): Long {
        val sizeX = abs(endOffset.x) + 1
        val sizeY = abs(endOffset.y) + 1
        val sizeZ = abs(endOffset.z) + 1
        val axes = (if (sizeX > 1) 1 else 0) + (if (sizeY > 1) 1 else 0) + (if (sizeZ > 1) 1 else 0)
        if (axes == 0) {
            return 1
        }
        if (axes == 1) {
            return max(sizeX, max(sizeY, sizeZ)).toLong()
        }
        if (axes == 2) {
            val a = ArrayList<Int>()
            if (sizeX > 1) {
                a.add(sizeX)
            }
            if (sizeY > 1) {
                a.add(sizeY)
            }
            if (sizeZ > 1) {
                a.add(sizeZ)
            }
            return 2L * a.get(0)!! + 2L * a.get(1)!! - 4L
        }
        return 4L * sizeX + 4L * sizeY + 4L * sizeZ - 16L
    }

    private fun handleContainer(request: HTTPRequest, response: HTTPResponse) {
        val pos = request.getURLParameter("pos")
        if (pos == null || pos.isBlank()) {
            err400(response, RErrorCode.MISSING_POS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val data = connector!!.containerData(pos.trim { it <= ' ' }, request.getURLParameter("side"))
            if ("full".equals(request.getURLParameter("view"), ignoreCase = true)) {
                ok(response, data)
                return
            }
            ok(response, containerSummaryData(data))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleContainerDetail(request: HTTPRequest, response: HTTPResponse) {
        val detail = containerDetailData(request, response)
        if (detail != null) {
            ok(response, detail)
        }
    }

    private fun handleContainerDetailMarkdown(request: HTTPRequest, response: HTTPResponse) {
        val detail = containerDetailData(request, response)
        if (detail != null) {
            writeMarkdown(response, containerDetailMarkdown(detail))
        }
    }

    private fun containerDetailData(request: HTTPRequest, response: HTTPResponse): RMcpContainerData? {
        val ref = request.getURLParameter("ref")
        val pos: String?
        val side: String?
        if (ref == null || ref.isBlank()) {
            pos = request.getURLParameter("pos")
            side = request.getURLParameter("side")
        } else {
            val parts: Array<String?> = ref.split("~".toRegex(), limit = 2).toTypedArray()
            pos = parts[0]
            side = if (parts.size > 1 && !parts[1]!!.isBlank()) parts[1] else null
        }
        if (pos == null || pos.isBlank()) {
            err400(response, RErrorCode.MISSING_POS)
            return null
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return null
            }
            return connector!!.containerData(pos.trim { it <= ' ' }, side!!)
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
            return null
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
            return null
        }
    }

    private fun handleContainerMove(request: HTTPRequest, response: HTTPResponse) {
        val moveRequest = readContainerMoveRequest(request)
        if (moveRequest == null || moveRequest.from == null || moveRequest.to == null) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        if (moveRequest.from.pos == null || moveRequest.from.pos.isBlank() || moveRequest.to.pos == null || moveRequest.to.pos.isBlank()) {
            err400(response, RErrorCode.MISSING_POS)
            return
        }
        if (moveRequest.from.slot == null) {
            err400(response, RErrorCode.BAD_SLOT)
            return
        }
        if (moveRequest.count <= 0) {
            err400(response, RErrorCode.BAD_COUNT)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(
                response, connector!!.moveContainerItems(
                    moveRequest.from.pos.trim { it <= ' ' },
                    moveRequest.from.side!!,
                    moveRequest.from.slot,
                    moveRequest.to.pos.trim { it <= ' ' },
                    moveRequest.to.side!!,
                    moveRequest.to.slot,
                    moveRequest.count,
                    moveRequest.dryRun
                )
            )
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleContainerPut(request: HTTPRequest, response: HTTPResponse) {
        val putRequest = readContainerPutRequest(request)
        if (putRequest == null || putRequest.to == null || putRequest.fromInventorySlot == null) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        if (putRequest.to.pos == null || putRequest.to.pos.isBlank()) {
            err400(response, RErrorCode.MISSING_POS)
            return
        }
        if (putRequest.count <= 0) {
            err400(response, RErrorCode.BAD_COUNT)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(
                response, connector!!.putInventoryItemIntoContainer(
                    putRequest.fromInventorySlot,
                    putRequest.to.pos.trim { it <= ' ' },
                    putRequest.to.side!!,
                    putRequest.to.slot,
                    putRequest.count,
                    putRequest.dryRun
                )
            )
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleContainerTake(request: HTTPRequest, response: HTTPResponse) {
        val takeRequest = readContainerTakeRequest(request)
        if (takeRequest == null || takeRequest.from == null || takeRequest.from.slot == null) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        if (takeRequest.from.pos == null || takeRequest.from.pos.isBlank()) {
            err400(response, RErrorCode.MISSING_POS)
            return
        }
        if (takeRequest.count <= 0) {
            err400(response, RErrorCode.BAD_COUNT)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(
                response, connector!!.takeContainerItemToInventory(
                    takeRequest.from.pos.trim { it <= ' ' },
                    takeRequest.from.side!!,
                    takeRequest.from.slot,
                    takeRequest.toInventorySlot,
                    takeRequest.count,
                    takeRequest.dryRun
                )
            )
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleContainerPutBatch(request: HTTPRequest, response: HTTPResponse) {
        val batchRequest = readContainerPutBatchRequest(request)
        if (batchRequest == null || batchRequest.moves == null || batchRequest.moves!!.isEmpty()) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        if (batchRequest.moves!!.size > CONTAINER_BATCH_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT)
            return
        }
        for (move in batchRequest.moves) {
            if (move == null || move.fromInventorySlot == null || move.to == null) {
                err400(response, RErrorCode.BAD_REQUEST)
                return
            }
            if (move.to!!.pos == null || move.to!!.pos!!.isBlank()) {
                err400(response, RErrorCode.MISSING_POS)
                return
            }
            if (move.count <= 0) {
                err400(response, RErrorCode.BAD_COUNT)
                return
            }
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.putInventoryItemsIntoContainerBatch(batchRequest))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleContainerTakeBatch(request: HTTPRequest, response: HTTPResponse) {
        val batchRequest = readContainerTakeBatchRequest(request)
        if (batchRequest == null || batchRequest.moves == null || batchRequest.moves!!.isEmpty()) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        if (batchRequest.moves!!.size > CONTAINER_BATCH_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT)
            return
        }
        for (move in batchRequest.moves) {
            if (move == null || move.from == null || move.from!!.slot == null) {
                err400(response, RErrorCode.BAD_REQUEST)
                return
            }
            if (move.from!!.pos == null || move.from!!.pos!!.isBlank()) {
                err400(response, RErrorCode.MISSING_POS)
                return
            }
            if (move.count <= 0) {
                err400(response, RErrorCode.BAD_COUNT)
                return
            }
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.takeContainerItemsToInventoryBatch(batchRequest))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleContainerMoveBatch(request: HTTPRequest, response: HTTPResponse) {
        val batchRequest = readContainerMoveBatchRequest(request)
        if (batchRequest == null || batchRequest.moves == null || batchRequest.moves!!.isEmpty()) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        if (batchRequest.moves!!.size > CONTAINER_BATCH_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT)
            return
        }
        for (move in batchRequest.moves) {
            if (move == null || move.from == null || move.to == null) {
                err400(response, RErrorCode.BAD_REQUEST)
                return
            }
            if (move.from!!.pos == null || move.from!!.pos!!.isBlank() || move.to!!.pos == null || move.to!!.pos!!.isBlank()) {
                err400(response, RErrorCode.MISSING_POS)
                return
            }
            if (move.from!!.slot == null) {
                err400(response, RErrorCode.BAD_SLOT)
                return
            }
            if (move.count <= 0) {
                err400(response, RErrorCode.BAD_COUNT)
                return
            }
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.moveContainerItemsBatch(batchRequest))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readContainerMoveRequest(request: HTTPRequest): ContainerMoveRequest? {
        try {
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    return GSON.fromJson<ContainerMoveRequest?>(body, ContainerMoveRequest::class.java)
                }
            }
            return ContainerMoveRequest(
                ContainerEndpointRequest(
                    request.getURLParameter("fromPos"),
                    request.getURLParameter("fromSide"),
                    request.getURLParameter("fromSlot").toString().toInt()
                ),
                ContainerEndpointRequest(
                    request.getURLParameter("toPos"),
                    request.getURLParameter("toSide"),
                    if (request.getURLParameter("toSlot") == null || request.getURLParameter("toSlot")
                            .isBlank()
                    ) null else request.getURLParameter("toSlot").trim { it <= ' ' }.toInt()
                ),
                request.getURLParameter("count").toString().toInt(),
                request.getURLParameter("dryRun").toString().toBoolean()
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun readContainerMoveBatchRequest(request: HTTPRequest): RMcpContainerMoveBatchRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            return if (body.isBlank()) null else GSON.fromJson<RMcpContainerMoveBatchRequest?>(
                body,
                RMcpContainerMoveBatchRequest::class.java
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun readContainerPutBatchRequest(request: HTTPRequest): RMcpContainerPutBatchRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            return if (body.isBlank()) null else GSON.fromJson<RMcpContainerPutBatchRequest?>(
                body,
                RMcpContainerPutBatchRequest::class.java
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun readContainerTakeBatchRequest(request: HTTPRequest): RMcpContainerTakeBatchRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            return if (body.isBlank()) null else GSON.fromJson<RMcpContainerTakeBatchRequest?>(
                body,
                RMcpContainerTakeBatchRequest::class.java
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun readContainerPutRequest(request: HTTPRequest): ContainerPutRequest? {
        try {
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    return GSON.fromJson<ContainerPutRequest?>(body, ContainerPutRequest::class.java)
                }
            }
            return ContainerPutRequest(
                request.getURLParameter("fromInventorySlot").toString().toInt(),
                ContainerEndpointRequest(
                    request.getURLParameter("toPos"),
                    request.getURLParameter("toSide"),
                    if (request.getURLParameter("toSlot") == null || request.getURLParameter("toSlot")
                            .isBlank()
                    ) null else request.getURLParameter("toSlot").trim { it <= ' ' }.toInt()
                ),
                request.getURLParameter("count").toString().toInt(),
                request.getURLParameter("dryRun").toString().toBoolean()
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun readContainerTakeRequest(request: HTTPRequest): ContainerTakeRequest? {
        try {
            if (request.hasBody()) {
                val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
                if (!body.isBlank()) {
                    return GSON.fromJson<ContainerTakeRequest?>(body, ContainerTakeRequest::class.java)
                }
            }
            return ContainerTakeRequest(
                ContainerEndpointRequest(
                    request.getURLParameter("fromPos"),
                    request.getURLParameter("fromSide"),
                    request.getURLParameter("fromSlot").toString().toInt()
                ),
                if (request.getURLParameter("toInventorySlot") == null || request.getURLParameter("toInventorySlot")
                        .isBlank()
                ) null else request.getURLParameter("toInventorySlot").trim { it <= ' ' }.toInt(),
                request.getURLParameter("count").toString().toInt(),
                request.getURLParameter("dryRun").toString().toBoolean()
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun handleSituation(request: HTTPRequest, response: HTTPResponse) {
        val entityRadius = readOptionalDoubleParam(request, response, "entityRadius", 32.0, RErrorCode.BAD_RADIUS)
        if (entityRadius == null) {
            return
        }
        if (!entityRadius.isFinite() || entityRadius < 0.0 || entityRadius > 128.0) {
            err400(response, RErrorCode.BAD_RADIUS)
            return
        }
        val resourceChunkRadius =
            readOptionalIntParam(request, response, "resourceChunkRadius", 1, RErrorCode.BAD_CHUNK_RADIUS)
        if (resourceChunkRadius == null) {
            return
        }
        if (resourceChunkRadius < 0 || resourceChunkRadius > 4) {
            err400(response, RErrorCode.BAD_CHUNK_RADIUS)
            return
        }
        val resourceSectionRadius =
            readOptionalIntParam(request, response, "resourceSectionRadius", 1, RErrorCode.BAD_SECTION_RADIUS)
        if (resourceSectionRadius == null) {
            return
        }
        if (resourceSectionRadius < 0 || resourceSectionRadius > 4) {
            err400(response, RErrorCode.BAD_SECTION_RADIUS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.situationData(entityRadius, resourceChunkRadius, resourceSectionRadius))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleScreenshot(request: HTTPRequest, response: HTTPResponse) {
        try {
            val data = connector!!.screenshotPngData().get(5, TimeUnit.SECONDS)
            response.setStatus(200)
            response.setContentType("image/png")
            response.setContentLength(data.size.toLong())
            response.setHeader("Cache-Control", "no-store")
            response.getOutputStream().write(data)
        } catch (e: TimeoutException) {
            err400(response, RErrorCode.SCREENSHOT_TIMEOUT)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            err400(response, RErrorCode.SCREENSHOT_FAILED)
        } catch (e: ExecutionException) {
            err400(response, RErrorCode.SCREENSHOT_FAILED)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun handleRecipe(request: HTTPRequest, response: HTTPResponse) {
        var itemId = request.getURLParameter("itemId")
        if (itemId == null || itemId.isBlank()) {
            err400(response, RErrorCode.MISSING_ITEM_ID)
            return
        }
        val limit = readOptionalIntQuery(request, response, "limit", 16, 1, 50)
        if (limit == null) {
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            itemId = itemId.trim { it <= ' ' }
            val recipes: MutableList<RMcpRecipeData> = connector!!.recipeData(itemId)
            if (recipes == null) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            if ("full".equals(request.getURLParameter("view"), ignoreCase = true)) {
                ok(response, recipes)
                return
            }
            val kind = request.getURLParameter("kind")
            val includeHidden = "true".equals(request.getURLParameter("includeHidden"), ignoreCase = true)
                    || "all".equals(request.getURLParameter("include"), ignoreCase = true)
            ok(response, recipeSummaryData(itemId, recipes, limit, kind, includeHidden))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleRecipeDetail(request: HTTPRequest, response: HTTPResponse) {
        val detail = recipeDetailData(request, response)
        if (detail != null) {
            ok(response, detail)
        }
    }

    private fun handleRecipeDetailMarkdown(request: HTTPRequest, response: HTTPResponse) {
        val detail = recipeDetailData(request, response)
        if (detail != null) {
            writeMarkdown(response, recipeDetailMarkdown(detail))
        }
    }

    private fun recipeDetailData(request: HTTPRequest, response: HTTPResponse): RMcpRecipeDetailData? {
        var itemId = request.getURLParameter("itemId")
        if (itemId == null || itemId.isBlank()) {
            err400(response, RErrorCode.MISSING_ITEM_ID)
            return null
        }
        val ref = request.getURLParameter("ref")
        if (ref == null || ref.isBlank()) {
            err400(response, RErrorCode.MISSING_RECIPE_REF)
            return null
        }
        if (!isValidRecipeRef(ref)) {
            err400(response, RErrorCode.BAD_RECIPE_REF)
            return null
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return null
            }
            itemId = itemId.trim { it <= ' ' }
            val recipes: MutableList<RMcpRecipeData> = connector!!.recipeData(itemId)
            if (recipes == null) {
                err400(response, RErrorCode.NO_PLAYER)
                return null
            }
            for (i in recipes.indices) {
                val recipe = recipes.get(i)
                if (ref == recipeRef(recipe, i)) {
                    return RMcpRecipeDetailData(itemId, ref, recipe)
                }
            }
            err400(response, RErrorCode.BAD_RECIPE_REF)
            return null
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
            return null
        }
    }

    private fun recipeSummaryData(
        itemId: String,
        recipes: MutableList<RMcpRecipeData>,
        limit: Int,
        kindFilter: String,
        includeHidden: Boolean
    ): RMcpRecipeSummaryData {
        val normalizedKindFilter =
            if (kindFilter == null || kindFilter.isBlank()) null else kindFilter.trim { it <= ' ' }.lowercase()
        val entries = ArrayList<RMcpRecipeSummaryData.Entry>()
        val seen = HashSet<String?>()
        var hiddenCount = 0
        for (i in recipes.indices) {
            val recipe = recipes.get(i)
            val kind = recipeKind(recipe)
            if (normalizedKindFilter != null && normalizedKindFilter != kind) {
                hiddenCount++
                continue
            }
            if (!includeHidden && hiddenRecipeKind(kind)) {
                hiddenCount++
                continue
            }
            val summaryKey = recipeSummaryKey(recipe, kind)
            if (!seen.add(summaryKey)) {
                hiddenCount++
                continue
            }
            entries.add(recipeSummaryEntry(itemId, recipe, i, kind))
            if (entries.size >= limit) {
                hiddenCount += recipes.size - i - 1
                break
            }
        }
        if (entries.isEmpty() && !recipes.isEmpty() && !includeHidden && normalizedKindFilter == null) {
            return recipeSummaryData(itemId, recipes, limit, null, true)
        }
        return RMcpRecipeSummaryData(
            itemId,
            recipes.size,
            entries.size,
            hiddenCount,
            entries.toMutableList()
        )
    }

    private fun recipeSummaryEntry(
        itemId: String,
        recipe: RMcpRecipeData,
        index: Int,
        kind: String?
    ): RMcpRecipeSummaryData.Entry {
        val ref = recipeRef(recipe, index)
        return RMcpRecipeSummaryData.Entry(
            ref,
            recipe.id,
            recipe.source,
            recipe.type,
            kind,
            recipe.category,
            recipe.title,
            RMHttpServer.summaryItems(recipe.inputs),
            RMHttpServer.summaryFluids(recipe.inputs),
            RMHttpServer.summaryTags(recipe.inputs),
            RMHttpServer.summaryItems(recipe.outputs),
            RMHttpServer.summaryFluids(recipe.outputs),
            RMHttpServer.summaryItems(recipe.catalysts),
            "/recipe/detail?itemId=" + itemId + "&ref=" + ref,
            "/recipe/detail.json?itemId=" + itemId + "&ref=" + ref
        )
    }

    private fun recipeRef(recipe: RMcpRecipeData, index: Int): String {
        return (sanitizeRecipeRefPart(recipe.source)
                + "~" + sanitizeRecipeRefPart(recipe.type)
                + "~" + index
                + "~" + recipeHash(recipeCanonical(recipe)))
    }

    private fun isValidRecipeRef(ref: String): Boolean {
        for (i in 0..<ref.length) {
            val ch = ref.get(i)
            if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == ',' || ch == '.' || ch == '~')) {
                return false
            }
        }
        return ref.split("~".toRegex()).toTypedArray().size == 4
    }

    private fun sanitizeRecipeRefPart(text: String?): String {
        if (text == null || text.isBlank()) {
            return "none"
        }
        val normalized = text.trim { it <= ' ' }.lowercase()
        val builder = StringBuilder()
        for (i in 0..<normalized.length) {
            val ch = normalized.get(i)
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == ',' || ch == '.') {
                builder.append(ch)
            } else {
                builder.append('_')
            }
        }
        return if (builder.length == 0) "none" else builder.toString()
    }

    private fun recipeHash(canonical: String): String {
        val crc = CRC32()
        crc.update(canonical.toByteArray(StandardCharsets.UTF_8))
        return String.format(Locale.ROOT, "%08x", crc.getValue())
    }

    private fun recipeCanonical(recipe: RMcpRecipeData): String {
        return (safe(recipe.source) + "|"
                + safe(recipe.type) + "|"
                + safe(recipe.id) + "|"
                + safe(recipe.category) + "|"
                + safe(recipe.title) + "|"
                + RMHttpServer.ingredientCanonical(recipe.inputs) + "|"
                + RMHttpServer.ingredientCanonical(recipe.outputs) + "|"
                + RMHttpServer.ingredientCanonical(recipe.catalysts) + "|"
                + RMHttpServer.ingredientCanonical(recipe.renderOnly) + "|"
                + GSON.toJson(recipe.extra))
    }

    private fun recipeSummaryKey(recipe: RMcpRecipeData, kind: String?): String {
        return (kind + "|"
                + safe(recipe.id) + "|"
                + RMHttpServer.ingredientCanonical(recipe.inputs) + "|"
                + RMHttpServer.ingredientCanonical(recipe.outputs) + "|"
                + RMHttpServer.ingredientCanonical(recipe.catalysts))
    }

    private fun ingredientCanonical(slots: List<RMcpRecipeData.IngredientSlot>?): String {
        return summaryItems(slots).toString() + "|" + summaryFluids(slots) + "|" + summaryTags(slots)
    }

    private fun recipeKind(recipe: RMcpRecipeData): String {
        val type = safe(recipe.type).lowercase()
        val category = safe(recipe.category).lowercase()
        val title = safe(recipe.title).lowercase()
        if (type.contains("loot") || category.contains("loot") || title.contains("loot") || category.contains("战利品") || title.contains(
                "战利品"
            ) || category.contains("掉落") || title.contains("掉落")
        ) {
            return "loot"
        }
        if (type.contains("chipped") || type.contains("rechiseled") || type.contains("chiseling") || category.contains("chiseling") || title.contains(
                "chiseling"
            ) || category.contains("雕刻") || title.contains("雕刻")
        ) {
            return "decorative"
        }
        if (type.contains("crafting") || type.contains("smelting") || type.contains("blasting") || type.contains("smoking") || type.contains(
                "campfire"
            )
        ) {
            return "crafting_or_smelting"
        }
        if (type.contains("stonecutting") || type.contains("cutting") || type.contains("sawing") || type.contains("compress") || type.contains(
                "decompress"
            )
        ) {
            return "conversion"
        }
        return "machine"
    }

    private fun hiddenRecipeKind(kind: String?): Boolean {
        return "loot" == kind || "decorative" == kind
    }

    private fun summaryItems(slots: List<RMcpRecipeData.IngredientSlot>?): MutableList<RMcpRecipeSummaryData.Item> {
        val map = LinkedHashMap<String, RMcpRecipeSummaryData.Item>()
        if (slots == null) {
            return mutableListOf<RMcpRecipeSummaryData.Item>()
        }
        for (slot in slots) {
            if (slot.items == null) {
                continue
            }
            for (item in slot.items) {
                if (item == null || item.id == null || item.id!!.isBlank()) {
                    continue
                }
                val key = item.id + "|" + safe(item.langKey)
                val existing = map.get(key)
                val count = max(1, item.count)
                map.put(
                    key, RMcpRecipeSummaryData.Item(
                        item.id,
                        item.langKey,
                        if (existing == null) count else existing.count + count
                    )
                )
            }
        }
        return map.values.toMutableList()
    }

    private fun summaryFluids(slots: List<RMcpRecipeData.IngredientSlot>?): MutableList<RMcpRecipeSummaryData.Fluid> {
        val map = LinkedHashMap<String, RMcpRecipeSummaryData.Fluid>()
        if (slots == null) {
            return mutableListOf<RMcpRecipeSummaryData.Fluid>()
        }
        for (slot in slots) {
            if (slot.fluids == null) {
                continue
            }
            for (fluid in slot.fluids) {
                if (fluid == null || fluid.id == null || fluid.id!!.isBlank()) {
                    continue
                }
                val key = fluid.id + "|" + safe(fluid.name)
                val existing = map.get(key)
                map.put(
                    key, RMcpRecipeSummaryData.Fluid(
                        fluid.id,
                        fluid.name,
                        if (existing == null) fluid.amount else existing.amount + fluid.amount
                    )
                )
            }
        }
        return map.values.toMutableList()
    }

    private fun summaryTags(slots: List<RMcpRecipeData.IngredientSlot>?): MutableList<RMcpRecipeSummaryData.Tag> {
        val map = LinkedHashMap<String, RMcpRecipeSummaryData.Tag>()
        if (slots == null) {
            return mutableListOf<RMcpRecipeSummaryData.Tag>()
        }
        for (slot in slots) {
            if (slot.tags == null) {
                continue
            }
            for (tag in slot.tags) {
                if (tag == null || tag.id == null || tag.id!!.isBlank()) {
                    continue
                }
                val existing = map.get(tag.id)
                map.put(
                    tag.id, RMcpRecipeSummaryData.Tag(
                        tag.id,
                        if (existing == null) tag.count else existing.count + tag.count,
                        max(tag.candidateCount, if (existing == null) 0 else existing.candidateCount)
                    )
                )
            }
        }
        return map.values.toMutableList()
    }

    private fun recipeDetailMarkdown(detail: RMcpRecipeDetailData): String {
        val recipe = detail.recipe
        val markdown = StringBuilder()
        markdown.append("# Recipe Detail\n\n")
        markdown.append("- itemId: `").append(escapeMarkdownCode(detail.itemId)).append("`\n")
        markdown.append("- ref: `").append(escapeMarkdownCode(detail.ref)).append("`\n")
        markdown.append("- id: `").append(escapeMarkdownCode(recipe!!.id)).append("`\n")
        markdown.append("- source: `").append(escapeMarkdownCode(recipe.source)).append("`\n")
        markdown.append("- type: `").append(escapeMarkdownCode(recipe.type)).append("`\n")
        markdown.append("- kind: `").append(recipeKind(recipe)).append("`\n")
        markdown.append("- category: `").append(escapeMarkdownCode(recipe.category)).append("`\n")
        markdown.append("- title: `").append(escapeMarkdownCode(recipe.title)).append("`\n")
        RMHttpServer.appendSummarySection(markdown, "Inputs", recipe.inputs)
        RMHttpServer.appendSummarySection(markdown, "Outputs", recipe.outputs)
        RMHttpServer.appendSummarySection(markdown, "Catalysts", recipe.catalysts)
        RMHttpServer.appendSummarySection(markdown, "Render Only", recipe.renderOnly)
        if (recipe.extra != null && !recipe.extra!!.isEmpty()) {
            markdown.append("\n## Extra\n\n```json\n")
                .append(GSON.toJson(recipe.extra))
                .append("\n```\n")
        }
        return markdown.toString()
    }

    private fun appendSummarySection(markdown: StringBuilder, title: String, slots: List<RMcpRecipeData.IngredientSlot>?) {
        val items = summaryItems(slots)
        val fluids = summaryFluids(slots)
        val tags = summaryTags(slots)
        if (items.isEmpty() && fluids.isEmpty() && tags.isEmpty()) {
            return
        }
        markdown.append("\n## ").append(title).append("\n\n")
        for (item in items) {
            markdown.append("- item `").append(escapeMarkdownCode(item.id)).append("` x").append(item.count)
            if (item.name != null && !item.name!!.isBlank()) {
                markdown.append(" (").append(escapeMarkdownText(item.name)).append(")")
            }
            markdown.append('\n')
        }
        for (fluid in fluids) {
            markdown.append("- fluid `").append(escapeMarkdownCode(fluid.id)).append("` ").append(fluid.amount)
                .append("mB")
            if (fluid.name != null && !fluid.name!!.isBlank()) {
                markdown.append(" (").append(escapeMarkdownText(fluid.name)).append(")")
            }
            markdown.append('\n')
        }
        for (tag in tags) {
            markdown.append("- tag `").append(escapeMarkdownCode(tag.id)).append("` x").append(tag.count)
                .append(", candidates ").append(tag.candidateCount).append('\n')
        }
    }

    private fun escapeMarkdownCode(text: String?): String {
        return if (text == null) "" else text.replace("`", "'")
    }

    private fun escapeMarkdownText(text: String?): String {
        return if (text == null) "" else text.replace("\n", " ").replace("\r", " ")
    }

    private fun safe(text: String?): String {
        return if (text == null) "" else text
    }

    private fun readOptionalIntQuery(
        request: HTTPRequest,
        response: HTTPResponse,
        name: String,
        defaultValue: Int,
        min: Int,
        max: Int
    ): Int? {
        val text = request.getURLParameter(name)
        if (text == null || text.isBlank()) {
            return defaultValue
        }
        try {
            val value = text.trim { it <= ' ' }.toInt()
            if (value < min || value > max) {
                err400(response, RErrorCode.BAD_LIMIT)
                return null
            }
            return value
        } catch (e: NumberFormatException) {
            err400(response, RErrorCode.BAD_LIMIT)
            return null
        }
    }

    private fun handleInventoryTag(request: HTTPRequest, response: HTTPResponse) {
        val tag = request.getURLParameter("tag")
        if (tag == null || tag.isBlank()) {
            err400(response, RErrorCode.MISSING_ITEM_ID)
            return
        }
        var scope = request.getURLParameter("scope")
        if (scope == null || scope.isBlank()) {
            scope = "all"
        }
        val limit: Int
        try {
            val limitText = request.getURLParameter("limit")
            limit = if (limitText == null || limitText.isBlank()) 64 else limitText.trim { it <= ' ' }.toInt()
        } catch (e: Exception) {
            err400(response, RErrorCode.BAD_SLOT)
            return
        }
        if (limit < 1 || limit > 512) {
            err400(response, RErrorCode.BAD_SLOT)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.inventoryTagMatchData(tag.trim { it <= ' ' }, scope.trim { it <= ' ' }, limit))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleBlockMapSlice(request: HTTPRequest, response: HTTPResponse) {
        val mapRequest = readBlockMapRequest(request, response)
        if (mapRequest == null) {
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.blockMapSliceData(mapRequest.x, mapRequest.y, mapRequest.z, mapRequest.radius))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readBlockMapRequest(request: HTTPRequest, response: HTTPResponse): BlockMapRequest? {
        val radius = readOptionalIntParam(request, response, "radius", 8, RErrorCode.BAD_BLOCKMAP_RADIUS)
        if (radius == null) {
            return null
        }
        if (radius < 0 || radius > 16) {
            err400(response, RErrorCode.BAD_BLOCKMAP_RADIUS)
            return null
        }
        val xText = request.getURLParameter("x")
        val yText = request.getURLParameter("y")
        val zText = request.getURLParameter("z")
        val anyPos =
            (xText != null && !xText.isBlank()) || (yText != null && !yText.isBlank()) || (zText != null && !zText.isBlank())
        if (!anyPos) {
            return BlockMapRequest(null, null, null, radius)
        }
        if (xText == null || xText.isBlank() || yText == null || yText.isBlank() || zText == null || zText.isBlank()) {
            err400(response, RErrorCode.BAD_POS)
            return null
        }
        try {
            return BlockMapRequest(
                xText.trim { it <= ' ' }.toInt(),
                yText.trim { it <= ' ' }.toInt(),
                zText.trim { it <= ' ' }.toInt(),
                radius
            )
        } catch (e: NumberFormatException) {
            err400(response, RErrorCode.BAD_POS)
            return null
        }
    }

    private fun handleBlocksFind(request: HTTPRequest, response: HTTPResponse) {
        val findRequest = readBlocksFindRequest(request)
        if (findRequest == null) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        val ids = normalizedBlockIds(findRequest.id, findRequest.ids)
        if (ids == null) {
            err400(response, RErrorCode.BAD_BLOCK_IDS)
            return
        }
        val chunkRadius: Int = (if (findRequest.chunkRadius == null) 2 else findRequest.chunkRadius)!!
        if (chunkRadius < 0 || chunkRadius > 4) {
            err400(response, RErrorCode.BAD_CHUNK_RADIUS)
            return
        }
        val scanMode =
            if (findRequest.scanMode == null || findRequest.scanMode!!.isBlank()) "nearby_sections" else findRequest.scanMode!!.trim { it <= ' ' }
        if ("nearby_sections" != scanMode && "chunk" != scanMode) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        val sectionRadius =
            if ("chunk" == scanMode) null else if (findRequest.sectionRadius == null) 1 else findRequest.sectionRadius
        if (sectionRadius != null && (sectionRadius < 0 || sectionRadius > 4)) {
            err400(response, RErrorCode.BAD_SECTION_RADIUS)
            return
        }
        val limit: Int = (if (findRequest.limit == null) 64 else findRequest.limit)!!
        if (limit < 1 || limit > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(
                response,
                connector!!.blocksFindData(
                    RMcpBlocksFindRequest(
                        null,
                        ids,
                        chunkRadius,
                        sectionRadius,
                        scanMode,
                        limit,
                        findRequest.includeState
                    )
                )
            )
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readBlocksFindRequest(request: HTTPRequest): RMcpBlocksFindRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) {
                return null
            }
            return GSON.fromJson<RMcpBlocksFindRequest?>(body, RMcpBlocksFindRequest::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    private fun normalizedBlockIds(id: String, ids: MutableList<String>?): MutableList<String>? {
        val normalized = ArrayList<String>()
        if (id != null && !id.isBlank()) {
            val text = id.trim { it <= ' ' }
            if (!isValidResourceId(text)) {
                return null
            }
            normalized.add(text)
        }
        if (ids == null) {
            return normalized.ifEmpty { return null }
        }
        for (_id in ids) {
            if (_id == null) {
                return null
            }
            val text = _id.trim { it <= ' ' }
            if (!isValidResourceId(text)) {
                return null
            }
            if (!normalized.contains(text)) {
                normalized.add(text)
                if (normalized.size > 16) {
                    return null
                }
            }
        }
        return normalized.ifEmpty { return null }
    }

    private fun isValidResourceId(id: String): Boolean {
        val separator = id.indexOf(':')
        if (separator <= 0 || separator == id.length - 1 || id.indexOf(':', separator + 1) >= 0) {
            return false
        }
        for (i in 0..<separator) {
            if (!isValidNamespaceChar(id.get(i))) {
                return false
            }
        }
        for (i in separator + 1..<id.length) {
            if (!isValidPathChar(id.get(i))) {
                return false
            }
        }
        return true
    }

    private fun isValidNamespaceChar(ch: Char): Boolean {
        return ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '_' || ch == '-' || ch == '.'
    }

    private fun isValidPathChar(ch: Char): Boolean {
        return isValidNamespaceChar(ch) || ch == '/'
    }

    private fun readIntParam(
        request: HTTPRequest,
        response: HTTPResponse,
        name: String,
        missingCode: RErrorCode,
        badCode: RErrorCode
    ): Int? {
        val text = request.getURLParameter(name)
        if (text == null || text.isBlank()) {
            err400(response, missingCode)
            return null
        }
        try {
            return text.trim { it <= ' ' }.toInt()
        } catch (e: NumberFormatException) {
            err400(response, badCode)
            return null
        }
    }

    private fun readOptionalIntParam(
        request: HTTPRequest,
        response: HTTPResponse,
        name: String,
        defaultValue: Int,
        badCode: RErrorCode
    ): Int? {
        val text = request.getURLParameter(name)
        if (text == null || text.isBlank()) {
            return defaultValue
        }
        try {
            return text.trim { it <= ' ' }.toInt()
        } catch (e: NumberFormatException) {
            err400(response, badCode)
            return null
        }
    }

    private fun readOptionalDoubleParam(
        request: HTTPRequest,
        response: HTTPResponse,
        name: String,
        defaultValue: Double,
        badCode: RErrorCode
    ): Double? {
        val text = request.getURLParameter(name)
        if (text == null || text.isBlank()) {
            return defaultValue
        }
        try {
            return text.trim { it <= ' ' }.toDouble()
        } catch (e: NumberFormatException) {
            err400(response, badCode)
            return null
        }
    }

    private fun handleNearbyResources(request: HTTPRequest, response: HTTPResponse) {
        val posText = request.getURLParameter("pos")
        var pos = readOptionalPos(request, response)
        if (posText != null && !posText.isBlank() && pos == null) {
            return
        }
        val chunkRadius = readOptionalIntParam(request, response, "chunkRadius", 2, RErrorCode.BAD_CHUNK_RADIUS)
        if (chunkRadius == null) {
            return
        }
        val sectionRadius = readOptionalIntParam(request, response, "sectionRadius", 1, RErrorCode.BAD_SECTION_RADIUS)
        if (sectionRadius == null) {
            return
        }
        if (chunkRadius < 0 || chunkRadius > 4) {
            err400(response, RErrorCode.BAD_CHUNK_RADIUS)
            return
        }
        if (sectionRadius < 0 || sectionRadius > 4) {
            err400(response, RErrorCode.BAD_SECTION_RADIUS)
            return
        }
        val categories = readNearbyResourceCategories(request, response)
        if (categories == null) {
            return
        }
        val ids = readNearbyResourceIds(request, response)
        if (ids == null) {
            return
        }
        val limit = readOptionalIntParam(request, response, "limit", 64, RErrorCode.BAD_LIMIT)
        if (limit == null) {
            return
        }
        if (limit < 1 || limit > NEARBY_RESOURCES_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val playerPos = connector!!.posData()
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            if (pos == null) {
                pos = ParsedPos(
                    floor(playerPos.x).toInt(),
                    floor(playerPos.y).toInt(),
                    floor(playerPos.z).toInt()
                )
            }
            val data =
                connector!!.nearbyResourcesData(pos.x, pos.y, pos.z, chunkRadius, sectionRadius, categories, ids, limit)
            if ("full".equals(request.getURLParameter("view"), ignoreCase = true)) {
                ok(response, data)
                return
            }
            ok(response, nearbyResourcesSummaryData(data))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleNearbyResourcesDetail(request: HTTPRequest, response: HTTPResponse) {
        val detail = nearbyResourcesDetailData(request, response)
        if (detail != null) {
            ok(response, detail)
        }
    }

    private fun handleNearbyResourcesDetailMarkdown(request: HTTPRequest, response: HTTPResponse) {
        val detail = nearbyResourcesDetailData(request, response)
        if (detail != null) {
            writeMarkdown(response, nearbyResourcesDetailMarkdown(detail))
        }
    }

    private fun nearbyResourcesDetailData(request: HTTPRequest, response: HTTPResponse): Any? {
        val ref = request.getURLParameter("ref")
        if (ref == null || ref.isBlank()) {
            err400(response, RErrorCode.BAD_REQUEST)
            return null
        }
        val posText = request.getURLParameter("pos")
        var pos = readOptionalPos(request, response)
        if (posText != null && !posText.isBlank() && pos == null) {
            return null
        }
        val chunkRadius = readOptionalIntParam(request, response, "chunkRadius", 2, RErrorCode.BAD_CHUNK_RADIUS)
        if (chunkRadius == null) {
            return null
        }
        val sectionRadius = readOptionalIntParam(request, response, "sectionRadius", 1, RErrorCode.BAD_SECTION_RADIUS)
        if (sectionRadius == null) {
            return null
        }
        if (chunkRadius < 0 || chunkRadius > 4) {
            err400(response, RErrorCode.BAD_CHUNK_RADIUS)
            return null
        }
        if (sectionRadius < 0 || sectionRadius > 4) {
            err400(response, RErrorCode.BAD_SECTION_RADIUS)
            return null
        }
        val categories = readNearbyResourceCategories(request, response)
        if (categories == null) {
            return null
        }
        val ids = readNearbyResourceIds(request, response)
        if (ids == null) {
            return null
        }
        val limit = readOptionalIntParam(request, response, "limit", 64, RErrorCode.BAD_LIMIT)
        if (limit == null) {
            return null
        }
        if (limit < 1 || limit > NEARBY_RESOURCES_LIMIT) {
            err400(response, RErrorCode.BAD_LIMIT)
            return null
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return null
            }
            val playerPos = connector!!.posData()
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER)
                return null
            }
            if (pos == null) {
                pos = ParsedPos(floor(playerPos.x).toInt(), floor(playerPos.y).toInt(), floor(playerPos.z).toInt())
            }
            val data =
                connector!!.nearbyResourcesData(pos.x, pos.y, pos.z, chunkRadius, sectionRadius, categories, ids, limit)
            for (resource in data.resources!!) {
                if (ref == resource!!.id) {
                    return linkedMap(
                        "ref",
                        ref,
                        "dim",
                        data.dim,
                        "center",
                        data.center,
                        "range",
                        data.range,
                        "filter",
                        data.filter,
                        "scan",
                        data.scan,
                        "resource",
                        resource
                    )
                }
            }
            err400(response, RErrorCode.BAD_REQUEST)
            return null
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
            return null
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
            return null
        }
    }

    private fun handleNearbyEntities(request: HTTPRequest, response: HTTPResponse) {
        val posText = request.getURLParameter("pos")
        var pos = readOptionalPos(request, response)
        if (posText != null && !posText.isBlank() && pos == null) {
            return
        }
        val radius = readOptionalDoubleParam(request, response, "radius", 64.0, RErrorCode.BAD_RADIUS)
        if (radius == null) {
            return
        }
        if (!radius.isFinite() || radius < 0.0 || radius > 128.0) {
            err400(response, RErrorCode.BAD_RADIUS)
            return
        }
        val limit = readOptionalIntParam(request, response, "limit", 64, RErrorCode.BAD_LIMIT)
        if (limit == null) {
            return
        }
        if (limit < 1 || limit > 1024) {
            err400(response, RErrorCode.BAD_LIMIT)
            return
        }
        val categories = readCategories(request.getURLParameter("category"))
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val playerPos = connector!!.posData()
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            if (pos == null) {
                pos = ParsedPos(
                    floor(playerPos.x).toInt(),
                    floor(playerPos.y).toInt(),
                    floor(playerPos.z).toInt()
                )
            }
            ok(response, connector!!.nearbyEntitiesData(pos.x, pos.y, pos.z, radius, categories, limit))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readCategories(text: String?): MutableList<String> {
        if (text == null || text.isBlank()) {
            return mutableListOf<String>("monster", "animal")
        }
        val categories = ArrayList<String>()
        for (part in text.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val category = part.trim { it <= ' ' }.lowercase(Locale.getDefault())
            if (!category.isEmpty() && !categories.contains(category)) {
                categories.add(category)
            }
        }
        return if (categories.isEmpty()) mutableListOf("monster", "animal") else categories
    }

    private fun readNearbyResourceCategories(request: HTTPRequest, response: HTTPResponse): MutableList<String>? {
        val text = request.getURLParameter("category")
        if (text == null || text.isBlank()) {
            return mutableListOf<String>()
        }
        val categories = ArrayList<String>()
        for (part in text.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val category = part.trim { it <= ' ' }.lowercase()
            if (category.isEmpty()) {
                continue
            }
            if (!NEARBY_RESOURCE_CATEGORIES.contains(category)) {
                err400(response, RErrorCode.BAD_REQUEST)
                return null
            }
            if (!categories.contains(category)) {
                categories.add(category)
            }
        }
        return categories
    }

    private fun readNearbyResourceIds(request: HTTPRequest, response: HTTPResponse): MutableList<String>? {
        val text = request.getURLParameter("ids")
        if (text == null || text.isBlank()) {
            return mutableListOf<String>()
        }
        val ids = ArrayList<String>()
        for (part in text.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val id = part.trim { it <= ' ' }
            if (id.isEmpty()) {
                continue
            }
            if (!isValidResourceId(id)) {
                err400(response, RErrorCode.BAD_REQUEST)
                return null
            }
            if (!ids.contains(id)) {
                ids.add(id)
            }
        }
        return ids
    }

    private fun handleStaringBlock(request: HTTPRequest, response: HTTPResponse) {
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val block = connector!!.staringBlockData("true" == request.getURLParameter("fluid"))
            if (block == null) {
                err400(response, RErrorCode.NO_BLOCK)
                return
            }
            ok(response, block)
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleBlockState(request: HTTPRequest, response: HTTPResponse) {
        val pos = readXyzRequest(request, response)
        if (pos == null) {
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.blockStateData(pos.x, pos.y, pos.z))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleBlockStateBatch(request: HTTPRequest, response: HTTPResponse) {
        val batch = readBlockStateBatchRequest(request)
        if (batch == null || batch.positions == null || batch.positions.isEmpty()) {
            err400(response, RErrorCode.BAD_POSITIONS)
            return
        }
        if (batch.positions.any { it == null }) {
            err400(response, RErrorCode.BAD_POSITIONS)
            return
        }
        if (batch.positions.size > BLOCK_BATCH_LIMIT) {
            err400(response, RErrorCode.TOO_MANY_BLOCKS)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.blockStateBatchData(batch.positions))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readBlockStateBatchRequest(request: HTTPRequest): BlockStateBatchRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) {
                return null
            }
            return GSON.fromJson<BlockStateBatchRequest?>(body, BlockStateBatchRequest::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    private fun readXyzRequest(request: HTTPRequest, response: HTTPResponse): BlockActionRequest? {
        val xText = request.getURLParameter("x")
        val yText = request.getURLParameter("y")
        val zText = request.getURLParameter("z")
        if (xText == null || xText.isBlank() || yText == null || yText.isBlank() || zText == null || zText.isBlank()) {
            err400(response, RErrorCode.MISSING_POS)
            return null
        }
        try {
            return BlockActionRequest(
                xText.trim { it <= ' ' }.toInt(),
                yText.trim { it <= ' ' }.toInt(),
                zText.trim { it <= ' ' }.toInt(),
                null
            )
        } catch (e: NumberFormatException) {
            err400(response, RErrorCode.BAD_POS)
            return null
        }
    }

    private fun readPos(request: HTTPRequest, response: HTTPResponse): ParsedPos? {
        val posText = request.getURLParameter("pos")
        if (posText == null || posText.isBlank()) {
            err400(response, RErrorCode.MISSING_POS)
            return null
        }
        val parts: Array<String?> = posText.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size != 3) {
            err400(response, RErrorCode.BAD_POS)
            return null
        }
        val x: Int
        val y: Int
        val z: Int
        try {
            x = parts[0]!!.trim { it <= ' ' }.toInt()
            y = parts[1]!!.trim { it <= ' ' }.toInt()
            z = parts[2]!!.trim { it <= ' ' }.toInt()
        } catch (e: NumberFormatException) {
            err400(response, RErrorCode.BAD_POS)
            return null
        }
        return ParsedPos(x, y, z)
    }

    private fun readOptionalPos(request: HTTPRequest, response: HTTPResponse): ParsedPos? {
        val posText = request.getURLParameter("pos")
        if (posText == null || posText.isBlank()) {
            return null
        }
        val parts: Array<String?> = posText.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size != 3) {
            err400(response, RErrorCode.BAD_POS)
            return null
        }
        val x: Int
        val y: Int
        val z: Int
        try {
            x = parts[0]!!.trim { it <= ' ' }.toInt()
            y = parts[1]!!.trim { it <= ' ' }.toInt()
            z = parts[2]!!.trim { it <= ' ' }.toInt()
        } catch (e: NumberFormatException) {
            err400(response, RErrorCode.BAD_POS)
            return null
        }
        return ParsedPos(x, y, z)
    }

    private fun handleBlockEntity(request: HTTPRequest, response: HTTPResponse) {
        val pos = readPos(request, response)
        if (pos == null) {
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val playerPos = connector!!.posData()
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val blockEntity = connector!!.blockEntityData(pos.x, pos.y, pos.z)
            if (blockEntity == null) {
                err400(response, RErrorCode.NO_BLOCK_ENTITY)
                return
            }
            if ("full".equals(request.getURLParameter("view"), ignoreCase = true)) {
                ok(response, blockEntity)
                return
            }
            ok(response, blockEntitySummaryData(blockEntity))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleBlockEntityDetail(request: HTTPRequest, response: HTTPResponse) {
        val detail = blockEntityDetailData(request, response)
        if (detail != null) {
            ok(response, detail)
        }
    }

    private fun handleBlockEntityDetailMarkdown(request: HTTPRequest, response: HTTPResponse) {
        val detail = blockEntityDetailData(request, response)
        if (detail != null) {
            writeMarkdown(response, blockEntityDetailMarkdown(detail))
        }
    }

    private fun blockEntityDetailData(request: HTTPRequest, response: HTTPResponse): RMcpBlockEntityData? {
        val ref = request.getURLParameter("ref")
        val pos = if (ref == null || ref.isBlank()) readPos(request, response) else parseDetailPosRef(ref, response)
        if (pos == null) {
            return null
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return null
            }
            val playerPos = connector!!.posData()
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER)
                return null
            }
            val blockEntity = connector!!.blockEntityData(pos.x, pos.y, pos.z)
            if (blockEntity == null) {
                err400(response, RErrorCode.NO_BLOCK_ENTITY)
                return null
            }
            return blockEntity
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
            return null
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
            return null
        }
    }

    private fun handleSignText(request: HTTPRequest, response: HTTPResponse) {
        val signRequest = readSignTextRequest(request)
        if (signRequest == null || signRequest.pos == null) {
            err400(response, RErrorCode.BAD_REQUEST)
            return
        }
        if (signRequest.text == null || signRequest.text!!.isBlank()) {
            err400(response, RErrorCode.BAD_SIGN_TEXT)
            return
        }
        if (signRequest.text!!.replace("\r", "").split("\n".toRegex()).toTypedArray().size > 4) {
            err400(response, RErrorCode.BAD_SIGN_TEXT)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.setSignText(signRequest))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleGetSignText(request: HTTPRequest, response: HTTPResponse) {
        val pos = readXyzRequest(request, response)
        if (pos == null) {
            return
        }
        val side = request.getURLParameter("side")
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            ok(response, connector!!.signTextData(pos.x, pos.y, pos.z, side))
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readSignTextRequest(request: HTTPRequest): RMcpSignTextRequest? {
        try {
            if (!request.hasBody()) {
                return null
            }
            val body = String(request.getBodyBytes(), StandardCharsets.UTF_8)
            return if (body.isBlank()) null else GSON.fromJson<RMcpSignTextRequest?>(
                body,
                RMcpSignTextRequest::class.java
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun handleHarvestTool(request: HTTPRequest, response: HTTPResponse) {
        var blockId = request.getURLParameter("blockId")
        if (blockId != null) {
            blockId = blockId.trim { it <= ' ' }
        }
        val posText = request.getURLParameter("pos")
        if ((blockId == null || blockId.isBlank()) && (posText == null || posText.isBlank())) {
            err400(response, RErrorCode.MISSING_BLOCK_ID)
            return
        }
        val pos = readOptionalPos(request, response)
        if (posText != null && !posText.isBlank() && pos == null) {
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val playerPos = connector!!.posData()
            if (playerPos == null) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val data = connector!!.harvestToolData(
                (if (blockId == null || blockId.isBlank()) null else blockId)!!,
                if (pos == null) null else pos.x,
                if (pos == null) null else pos.y,
                if (pos == null) null else pos.z
            )
            ok(response, data)
        } catch (e: RMcpEndpointException) {
            err400(response, e.code())
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleStaringEntity(request: HTTPRequest, response: HTTPResponse) {
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val entity = connector!!.staringEntityData()
            if (entity == null) {
                err400(response, RErrorCode.NO_ENTITY)
                return
            }
            if ("full".equals(request.getURLParameter("view"), ignoreCase = true)) {
                ok(response, entity)
                return
            }
            ok(response, entityBriefSummaryData(entity))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleEntityDetail(request: HTTPRequest, response: HTTPResponse) {
        val detail = entityDetailData(request, response)
        if (detail != null) {
            ok(response, detail)
        }
    }

    private fun handleEntityDetailMarkdown(request: HTTPRequest, response: HTTPResponse) {
        val detail = entityDetailData(request, response)
        if (detail != null) {
            writeMarkdown(response, entityDetailMarkdown(detail))
        }
    }

    private fun entityDetailData(request: HTTPRequest, response: HTTPResponse): RMcpEntityDetailData? {
        val ref = request.getURLParameter("ref")
        val uuidText = if (ref == null || ref.isBlank()) request.getURLParameter("uuid") else ref
        if (uuidText == null || uuidText.isBlank()) {
            err400(response, RErrorCode.MISSING_UUID)
            return null
        }
        val uuid: UUID?
        try {
            uuid = UUID.fromString(uuidText.trim { it <= ' ' })
        } catch (e: IllegalArgumentException) {
            err400(response, RErrorCode.BAD_UUID)
            return null
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return null
            }
            val entity = connector!!.entityData(uuid)
            if (entity == null) {
                err400(response, RErrorCode.NO_ENTITY)
                return null
            }
            return entity
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
            return null
        }
    }

    private fun handleEntity(request: HTTPRequest, response: HTTPResponse) {
        val uuidText = request.getURLParameter("uuid")
        if (uuidText == null || uuidText.isBlank()) {
            err400(response, RErrorCode.MISSING_UUID)
            return
        }
        val uuid: UUID?
        try {
            uuid = UUID.fromString(uuidText.trim { it <= ' ' })
        } catch (e: IllegalArgumentException) {
            err400(response, RErrorCode.BAD_UUID)
            return
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            val entity = connector!!.entityData(uuid)
            if (entity == null) {
                err400(response, RErrorCode.NO_ENTITY)
                return
            }
            if ("full".equals(request.getURLParameter("view"), ignoreCase = true)) {
                ok(response, entity)
                return
            }
            ok(response, entitySummaryData(entity))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handlePlayer(request: HTTPRequest, response: HTTPResponse) {
        val uuidText = request.getURLParameter("uuid")
        var uuid: UUID? = null
        if (uuidText != null && !uuidText.isBlank()) {
            try {
                uuid = UUID.fromString(uuidText.trim { it <= ' ' })
            } catch (e: IllegalArgumentException) {
                err400(response, RErrorCode.BAD_UUID)
                return
            }
        }
        try {
            if (!connector!!.playerInWorld()) {
                err400(response, RErrorCode.NO_PLAYER)
                return
            }
            if ("true" == request.getURLParameter("detail")) {
                val player = connector!!.playerDetailData(uuid!!)
                if (player == null) {
                    err400(response, RErrorCode.NO_PLAYER_ENTITY)
                    return
                }
                ok(response, player)
                return
            }
            val player = connector!!.playerData(uuid!!)
            if (player == null) {
                err400(response, RErrorCode.NO_PLAYER_ENTITY)
                return
            }
            ok(response, player)
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleLangKeySearch(request: HTTPRequest, response: HTTPResponse) {
        val text = request.getURLParameter("text")
        if (text == null || text.isBlank()) {
            err400(response, RErrorCode.MISSING_TEXT)
            return
        }
        try {
            ok(response, connector!!.langKeyIndex().search(text))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleItemSearch(request: HTTPRequest, response: HTTPResponse) {
        val search = readResolveSearchRequest(request, response)
        if (search == null) {
            return
        }
        try {
            ok(response, connector!!.itemSearchData(search.text!!, search.modId!!, search.limit))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleBlockSearch(request: HTTPRequest, response: HTTPResponse) {
        val search = readResolveSearchRequest(request, response)
        if (search == null) {
            return
        }
        try {
            ok(response, connector!!.blockSearchData(search.text!!, search.modId!!, search.limit))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleEntityTypeSearch(request: HTTPRequest, response: HTTPResponse) {
        val search = readResolveSearchRequest(request, response)
        if (search == null) {
            return
        }
        try {
            ok(response, connector!!.entityTypeSearchData(search.text!!, search.modId!!, search.limit))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleFluidSearch(request: HTTPRequest, response: HTTPResponse) {
        val search = readResolveSearchRequest(request, response)
        if (search == null) {
            return
        }
        try {
            ok(response, connector!!.fluidSearchData(search.text!!, search.modId!!, search.limit))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun handleTagSearch(request: HTTPRequest, response: HTTPResponse) {
        val search = readResolveSearchRequest(request, response)
        if (search == null) {
            return
        }
        try {
            ok(response, connector!!.tagSearchData(search.text!!, search.modId!!, search.limit))
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun readResolveSearchRequest(request: HTTPRequest, response: HTTPResponse): ResolveSearchRequest? {
        val text = request.getURLParameter("text")
        if (text == null || text.isBlank()) {
            err400(response, RErrorCode.MISSING_TEXT)
            return null
        }
        var limit = 10
        val limitText = request.getURLParameter("limit")
        if (limitText != null && !limitText.isBlank()) {
            try {
                limit = limitText.trim { it <= ' ' }.toInt()
            } catch (e: NumberFormatException) {
                err400(response, RErrorCode.BAD_LIMIT)
                return null
            }
        }
        if (limit < 1 || limit > 50) {
            err400(response, RErrorCode.BAD_LIMIT)
            return null
        }
        val modId = request.getURLParameter("modId")
        return ResolveSearchRequest(
            text.trim { it <= ' ' },
            if (modId == null || modId.isBlank()) null else modId.trim { it <= ' ' },
            limit
        )
    }

    private fun handleLangKey(request: HTTPRequest, response: HTTPResponse) {
        val key = request.getURLParameter("key")
        if (key == null || key.isBlank()) {
            err400(response, RErrorCode.MISSING_KEY)
            return
        }
        try {
            val data = connector!!.langKeyIndex().langKey(key.trim { it <= ' ' })
            if (data == null) {
                err400(response, RErrorCode.NO_LANGKEY)
                return
            }
            ok(response, data)
        } catch (e: Exception) {
            err400(response, RErrorCode.INTERNAL_ERROR)
        }
    }

    private fun ok(response: HTTPResponse, data: Any?) {
        writeJson(response, 200, RMcpResponse.Companion.ok(compactInventorySnbt(data)))
    }

    private fun okFull(response: HTTPResponse, data: Any?) {
        writeJson(response, 200, RMcpResponse.ok(data))
    }

    private fun writeCsv(response: HTTPResponse, csv: String) {
        try {
            response.setStatus(200)
            response.setContentType("text/csv; charset=utf-8")
            response.getWriter().write(csv)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun isDetailRequest(request: HTTPRequest): Boolean {
        return "true".equals(request.getURLParameter("detail"), ignoreCase = true)
                || "full".equals(request.getURLParameter("view"), ignoreCase = true)
    }

    private fun compactInventorySnbt(data: Any?): JsonElement? {
        val element = GSON.toJsonTree(data)
        stripInventoryItemSnbt(element)
        return element
    }

    private fun stripInventoryItemSnbt(element: JsonElement?) {
        if (element == null || element.isJsonNull()) {
            return
        }
        if (element.isJsonArray()) {
            for (item in element.getAsJsonArray()) {
                stripInventoryItemSnbt(item)
            }
            return
        }
        if (!element.isJsonObject()) {
            return
        }
        val `object` = element.getAsJsonObject()
        if (isInventoryItemObject(`object`)) {
            `object`.remove("snbt")
        }
        for (entry in `object`.entrySet()) {
            stripInventoryItemSnbt(entry.value)
        }
    }

    private fun isInventoryItemObject(`object`: JsonObject): Boolean {
        return `object`.has("snbt")
                && `object`.has("section")
                && `object`.has("slot")
                && `object`.has("id")
                && `object`.has("count")
    }

    private fun compactMainHand(data: Any?): Any? {
        if (data !is MutableMap<*, *> || !data.containsKey("snbt")) {
            return data
        }
        val compact = LinkedHashMap<String, Any?>()
        for (entry in data.entries) {
            if ("snbt" != entry.key) {
                compact.put(entry.key.toString(), entry.value)
            }
        }
        return compact
    }

    private fun inventoryCsv(inventory: RMcpInventoryData): String {
        val csv = StringBuilder("section,slot,id,count\n")
        appendInventoryCsvRows(csv, "hotbar", 0, 9, inventory.hotbar!!)
        appendInventoryCsvRows(csv, "inventory", 9, 36, inventory.items!!)
        appendInventoryCsvRows(csv, "armor", 0, 4, inventory.armor!!)
        appendInventoryCsvRows(csv, "offhand", 0, 1, inventory.offhand!!)
        return csv.toString()
    }

    private fun appendInventoryCsvRows(
        csv: StringBuilder,
        section: String,
        from: Int,
        to: Int,
        items: List<RMcpInventoryData.Item>
    ) {
        val bySlot = HashMap<Int, RMcpInventoryData.Item?>()
        for (item in items) {
            bySlot.put(item!!.slot, item)
        }
        for (slot in from..<to) {
            val item = bySlot.get(slot)
            if (item == null) {
                csv.append(csvCell(section)).append(',')
                    .append(slot).append(',')
                    .append("minecraft:air,0\n")
            } else {
                csv.append(csvCell(item.section)).append(',')
                    .append(slot).append(',')
                    .append(csvCell(item.id)).append(',')
                    .append(item.count).append('\n')
            }
        }
    }

    private fun csvCell(value: String?): String {
        if (value == null) {
            return ""
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value
        }
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    @Throws(RMError::class)
    private fun inventoryDetailItem(request: HTTPRequest, inventory: RMcpInventoryData): RMcpInventoryData.Item? {
        val slotText = request.getURLParameter("slot")
        if (slotText == null || slotText.isBlank()) {
            throw RMError(RErrorCode.BAD_SLOT)
        }
        var section = request.getURLParameter("section")
        val slot = parseInventoryDetailSlot(slotText)
        if (slot == null) {
            throw RMError(RErrorCode.BAD_SLOT)
        }
        if (section == null || section.isBlank()) {
            if (slot < 0 || slot > 35) {
                throw RMError(RErrorCode.BAD_SLOT)
            }
            section = if (slot < 9) "hotbar" else "inventory"
        }
        return findInventoryItem(inventory, section.trim { it <= ' ' }, slot)
    }

    private fun parseInventoryDetailSlot(text: String): Int? {
        try {
            return text.trim { it <= ' ' }.toInt()
        } catch (e: NumberFormatException) {
            return null
        }
    }

    @Throws(RMError::class)
    private fun findInventoryItem(inventory: RMcpInventoryData, section: String, slot: Int): RMcpInventoryData.Item? {
        val items = when (section.lowercase()) {
            "hotbar" -> inventory.hotbar
            "inventory", "main" -> inventory.items
            "armor" -> inventory.armor
            "offhand" -> inventory.offhand
            else -> throw RMError(RErrorCode.BAD_SLOT)
        }
        for (item in items!!) {
            if (item!!.slot == slot) {
                return item
            }
        }
        return null
    }

    private fun questSummaryData(quest: RQuest): MutableMap<String, Any?> {
        return linkedMap(
            "ref", quest.id,
            "id", quest.id,
            "title", quest.title,
            "subtitle", quest.subtitle,
            "chapterId", quest.chapterId,
            "chapterTitle", quest.chapterTitle,
            "state", quest.state,
            "rules", quest.rules,
            "dependencyCount", if (quest.dependencies == null) 0 else quest.dependencies!!.size,
            "taskCount", if (quest.tasks == null) 0 else quest.tasks!!.size,
            "rewardCount", if (quest.rewards == null) 0 else quest.rewards!!.size,
            "tasks", if (quest.tasks == null) mutableListOf<Any>() else quest.tasks,
            "rewards", if (quest.rewards == null) mutableListOf<Any>() else quest.rewards,
            "detail", "/quest/detail/detail?ref=" + url(quest.id!!),
            "detailJson", "/quest/detail/detail.json?ref=" + url(quest.id!!),
            "full", "/quest/detail/" + url(quest.id!!) + "?view=full"
        )
    }

    private fun nearbyResourcesSummaryData(data: RMcpNearbyResourcesData): MutableMap<String, Any?> {
        val query = nearbyResourcesQuery(data)
        val resources = data.resources.orEmpty()
            .mapTo(ArrayList<MutableMap<String, Any?>?>()) { resource ->
                linkedMap(
                    "ref", resource!!.id,
                    "id", resource.id,
                    "category", resource.category,
                    "count", resource.count,
                    "nearest", resource.nearest,
                    "sectionCount", resource.sections!!.size,
                    "detail", "/nearby-resources/detail?" + query + "&ref=" + url(resource.id!!),
                    "detailJson", "/nearby-resources/detail.json?" + query + "&ref=" + url(resource.id!!)
                )
            }
        return linkedMap(
            "dim", data.dim,
            "center", data.center,
            "range", data.range,
            "filter", data.filter,
            "scan", data.scan,
            "features", data.features,
            "resourceCount", data.resources!!.size,
            "resources", resources,
            "topBlocks", data.topBlocks,
            "full", "/nearby-resources?" + query + "&view=full"
        )
    }

    private fun nearbyResourcesQuery(data: RMcpNearbyResourcesData): String {
        val params = ArrayList<String>()
        params.add("pos=" + url(data.center!!.block!!.x.toString() + "," + data.center!!.block!!.y + "," + data.center!!.block!!.z))
        params.add("chunkRadius=" + data.range!!.chunkRadius)
        params.add("sectionRadius=" + data.range!!.sectionRadius)
        if (!data.filter!!.categories!!.isEmpty()) {
            params.add("category=" + url(String.join(",", data.filter!!.categories)))
        }
        if (!data.filter!!.ids!!.isEmpty()) {
            params.add("ids=" + url(String.join(",", data.filter!!.ids)))
        }
        params.add("limit=" + data.filter!!.limit)
        return String.join("&", params)
    }

    private fun blockEntitySummaryData(data: RMcpBlockEntityData): MutableMap<String, Any?> {
        val ref = data.pos!!.x.toString() + "," + data.pos!!.y + "," + data.pos!!.z
        return linkedMap(
            "ref", ref,
            "dim", data.dim,
            "pos", data.pos,
            "blockId", data.blockId,
            "blockState", data.blockState,
            "type", data.type,
            "runtimeClass", data.runtimeClass,
            "hasSnbt", data.snbt != null && !data.snbt!!.isBlank(),
            "detail", "/blockentity/detail?ref=" + url(ref),
            "detailJson", "/blockentity/detail.json?ref=" + url(ref),
            "full", "/blockentity?pos=" + url(ref) + "&view=full"
        )
    }

    private fun containerSummaryData(data: RMcpContainerData): MutableMap<String, Any?> {
        val ref =
            data.pos!!.x.toString() + "," + data.pos!!.y + "," + data.pos!!.z + (if (data.side == null) "" else "~" + data.side)
        val counts = LinkedHashMap<String, Int?>()
        for (item in data.items!!) {
            counts.merge(item!!.id, item.count) { a: Int, b: Int? -> Integer.sum(a!!, b!!) }
        }
        val topItems = counts.entries
            .sortedWith { left, right ->
                val countCompare = right.value!!.compareTo(left.value!!)
                if (countCompare != 0) countCompare else left.key!!.compareTo(right.key!!)
            }
            .take(16)
            .mapTo(ArrayList<MutableMap<String, Any?>?>()) { entry ->
                linkedMap(
                    "id",
                    entry.key,
                    "count",
                    entry.value
                )
            }
        val items = data.items.orEmpty()
            .mapTo(ArrayList<MutableMap<String, Any?>?>()) { item ->
                linkedMap(
                    "slot",
                    item!!.slot,
                    "id",
                    item.id,
                    "count",
                    item.count,
                    "limit",
                    item.limit,
                    "canInsert",
                    item.canInsert
                )
            }
        return linkedMap(
            "ref",
            ref,
            "dim",
            data.dim,
            "pos",
            data.pos,
            "side",
            data.side,
            "slots",
            data.slots,
            "occupiedSlots",
            data.items!!.size,
            "topItems",
            topItems,
            "items",
            items,
            "detail",
            "/container/detail?ref=" + url(ref),
            "detailJson",
            "/container/detail.json?ref=" + url(ref),
            "full",
            "/container?pos=" + url(data.pos!!.x.toString() + "," + data.pos!!.y + "," + data.pos!!.z) + (if (data.side == null) "" else "&side=" + url(
                data.side!!
            )) + "&view=full"
        )
    }

    private fun entitySummaryData(data: RMcpEntityDetailData): MutableMap<String, Any?> {
        return linkedMap(
            "ref",
            data.uuid,
            "dim",
            data.dim,
            "uuid",
            data.uuid,
            "type",
            data.type,
            "name",
            data.name,
            "pos",
            data.pos,
            "runtime",
            data.runtime,
            "nbtKeys",
            data.nbt()?.keySet()?.sorted()?.toMutableList() ?: mutableListOf<Any>(),
            "hasSnbt",
            data.snbt != null && !data.snbt!!.isBlank(),
            "detail",
            "/entity/detail?ref=" + url(data.uuid!!),
            "detailJson",
            "/entity/detail.json?ref=" + url(data.uuid!!),
            "full",
            "/entity?uuid=" + url(data.uuid!!) + "&view=full"
        )
    }

    private fun entityBriefSummaryData(data: RMcpEntityData): MutableMap<String, Any?> {
        return linkedMap(
            "ref", data.uuid,
            "dim", data.dim,
            "uuid", data.uuid,
            "type", data.type,
            "name", data.name,
            "pos", data.pos,
            "distance", data.distance,
            "detail", "/entity/detail?ref=" + url(data.uuid!!),
            "detailJson", "/entity/detail.json?ref=" + url(data.uuid!!)
        )
    }

    private fun questDetailMarkdown(quest: RQuest): String {
        val text = StringBuilder()
        text.append("### Quest detail\n\n")
        text.append("- ID: `").append(quest.id).append("`\n")
        text.append("- Title: ").append(quest.title).append("\n")
        text.append("- Chapter: ").append(quest.chapterTitle).append("\n")
        text.append("- Completed: ").append(quest.state != null && quest.state!!.completed).append("\n\n")
        if (quest.description != null && !quest.description!!.isEmpty()) {
            text.append("Description:\n")
            for (line in quest.description) {
                text.append("- ").append(line).append("\n")
            }
            text.append("\n")
        }
        text.append("```json\n").append(GSON.toJson(quest)).append("\n```\n")
        return text.toString()
    }

    private fun nearbyResourcesDetailMarkdown(detail: Any?): String {
        return "### Nearby resource detail\n\n```json\n" + GSON.toJson(detail) + "\n```\n"
    }

    private fun blockEntityDetailMarkdown(detail: RMcpBlockEntityData): String {
        return ("### Block entity detail\n\n- Ref: `" + detail.pos!!.x + "," + detail.pos!!.y + "," + detail.pos!!.z + "`\n"
                + "- Block: `" + detail.blockId + "`\n"
                + "- Type: `" + detail.type + "`\n\n```snbt\n" + (if (detail.snbt == null) "" else detail.snbt) + "\n```\n")
    }

    private fun containerDetailMarkdown(detail: RMcpContainerData): String {
        val text = StringBuilder()
        text.append("### Container detail\n\n")
        text.append("- Pos: `").append(detail.pos!!.x).append(",").append(detail.pos!!.y).append(",")
            .append(detail.pos!!.z).append("`\n")
        text.append("- Side: `").append(detail.side).append("`\n")
        text.append("- Slots: ").append(detail.slots).append("\n\n")
        for (item in detail.items!!) {
            text.append("- slot ").append(item!!.slot).append(": `").append(item.id).append("` x")
                .append(item.count).append("\n")
        }
        return text.toString()
    }

    private fun entityDetailMarkdown(detail: RMcpEntityDetailData): String {
        return "### Entity detail\n\n- UUID: `" + detail.uuid + "`\n- Type: `" + detail.type + "`\n- Name: `" + detail.name + "`\n\n```snbt\n" + (if (detail.snbt == null) "" else detail.snbt) + "\n```\n"
    }

    private fun parseDetailPosRef(ref: String, response: HTTPResponse): ParsedPos? {
        val parts: Array<String?> = ref.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size != 3) {
            err400(response, RErrorCode.BAD_POS)
            return null
        }
        try {
            return ParsedPos(
                parts[0]!!.trim { it <= ' ' }.toInt(),
                parts[1]!!.trim { it <= ' ' }.toInt(),
                parts[2]!!.trim { it <= ' ' }.toInt()
            )
        } catch (e: NumberFormatException) {
            err400(response, RErrorCode.BAD_POS)
            return null
        }
    }

    private fun linkedMap(vararg entries: Any?): MutableMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        var i = 0
        while (i + 1 < entries.size) {
            map.put(entries[i].toString(), entries[i + 1])
            i += 2
        }
        return map
    }

    private fun url(text: String): String {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")
    }

    private fun err404(response: HTTPResponse) {
        writeMarkdown(response, 404, "err_code: " + RErrorCode.NOT_FOUND.id)
    }

    private fun err400(response: HTTPResponse, errcode: RErrorCode) {
        writeMarkdown(response, 400, "err_code: " + errcode.id)
    }

    private fun err400(response: HTTPResponse, errcode: RErrorCode, reason: String?) {
        writeMarkdown(response, 400, "err_code: " + errcode.id + ", detail:" + reason)
    }

    private fun err400(response: HTTPResponse, errcode: String?) {
        val known = RErrorCode.get(errcode)
        writeMarkdown(response, 400, "err_code: " + (if (known == null) RErrorCode.INTERNAL_ERROR.id else known.id))
    }

    private fun writeJson(response: HTTPResponse, status: Int, data: RMcpResponse<*>?) {
        try {
            response.setStatus(status)
            response.setContentType("application/json; charset=utf-8")
            response.getWriter().write(GSON.toJson(data))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @JvmRecord
    private data class Route(
        val method: HTTPMethod,
        val endpoint: String,
        val prefix: Boolean,
        val handler: (HTTPRequest, HTTPResponse) -> Unit
    ) {
        fun matches(request: HTTPRequest): Boolean {
            return request.getMethod().`is`(method) && if (prefix) {
                request.getPath().startsWith(endpoint)
            } else {
                endpoint == request.getPath()
            }
        }

        companion object {
            val LIST = mutableListOf(
                    get(
                        "/",
                        RMHttpServer::handlePrompts),
                    get(
                        "/prompts",
                        RMHttpServer::handlePrompts),
                    getPrefix(
                        "/apidoc/",
                        RMHttpServer::handleApiDoc),
                    getPrefix(
                        "/errcode/",
                        RMHttpServer::handleErrCode),
                    get(
                        "/mods",
                        RMHttpServer::handleMods),
                    get(
                        "/quest/chapter-list",
                        RMHttpServer::handleQuestChapterList),
                    get(
                        "/quest/reachable",
                        RMHttpServer::handleReachableQuests),
                    get(
                        "/quest/detail/detail",
                        RMHttpServer::handleQuestDetailRefMarkdown),
                    get(
                        "/quest/detail/detail.md",
                        RMHttpServer::handleQuestDetailRefMarkdown),
                    get(
                        "/quest/detail/detail.json",
                        RMHttpServer::handleQuestDetailRef),
                    getPrefix(
                        "/quest/detail/",
                        RMHttpServer::handleQuestDetail),
                    getPrefix(
                        "/quest/chapter/",
                        RMHttpServer::handleQuestChapter),
                    get(
                        "/pos",
                        RMHttpServer::handlePos),
                    get(
                        "/inventory",
                        RMHttpServer::handleInventory),
                    get(
                        "/inventory/detail",
                        RMHttpServer::handleInventoryDetail),
                    get(
                        "/inventory/detail.json",
                        RMHttpServer::handleInventoryDetail),
                    get(
                        "/inventory/tag",
                        RMHttpServer::handleInventoryTag),
                    get(
                        "/menu",
                        RMHttpServer::handleMenu),
                    get(
                        "/situation",
                        RMHttpServer::handleSituation),
                    get(
                        "/mainhand",
                        RMHttpServer::handleMainHand),
                    get(
                        "/screenshot",
                        RMHttpServer::handleScreenshot),
                    get(
                        "/recipe",
                        RMHttpServer::handleRecipe),
                    get(
                        "/recipe/detail",
                        RMHttpServer::handleRecipeDetailMarkdown),
                    get(
                        "/recipe/detail.md",
                        RMHttpServer::handleRecipeDetailMarkdown),
                    get(
                        "/recipe/detail.json",
                        RMHttpServer::handleRecipeDetail),
                    get(
                        "/blockmap/slice",
                        RMHttpServer::handleBlockMapSlice),
                    post(
                        "/blocks/find",
                        RMHttpServer::handleBlocksFind),
                    get(
                        "/nearby-resources",
                        RMHttpServer::handleNearbyResources),
                    get(
                        "/nearby-resources/detail",
                        RMHttpServer::handleNearbyResourcesDetailMarkdown),
                    get(
                        "/nearby-resources/detail.md",
                        RMHttpServer::handleNearbyResourcesDetailMarkdown),
                    get(
                        "/nearby-resources/detail.json",
                        RMHttpServer::handleNearbyResourcesDetail),
                    get(
                        "/nearby-entities",
                        RMHttpServer::handleNearbyEntities),
                    get(
                        "/staring-block",
                        RMHttpServer::handleStaringBlock),
                    get(
                        "/blockstate",
                        RMHttpServer::handleBlockState),
                    post(
                        "/blockstate/batch",
                        RMHttpServer::handleBlockStateBatch),
                    get(
                        "/blockentity",
                        RMHttpServer::handleBlockEntity),
                    get(
                        "/blockentity/detail",
                        RMHttpServer::handleBlockEntityDetailMarkdown),
                    get(
                        "/blockentity/detail.md",
                        RMHttpServer::handleBlockEntityDetailMarkdown),
                    get(
                        "/blockentity/detail.json",
                        RMHttpServer::handleBlockEntityDetail),
                    get(
                        "/sign/text",
                        RMHttpServer::handleGetSignText),
                    post(
                        "/sign/text",
                        RMHttpServer::handleSignText),
                    get(
                        "/container",
                        RMHttpServer::handleContainer),
                    get(
                        "/container/detail",
                        RMHttpServer::handleContainerDetailMarkdown),
                    get(
                        "/container/detail.md",
                        RMHttpServer::handleContainerDetailMarkdown),
                    get(
                        "/container/detail.json",
                        RMHttpServer::handleContainerDetail),
                    get(
                        "/harvest-tool",
                        RMHttpServer::handleHarvestTool),
                    get(
                        "/staring-entity",
                        RMHttpServer::handleStaringEntity),
                    get(
                        "/entity",
                        RMHttpServer::handleEntity),
                    get(
                        "/entity/detail",
                        RMHttpServer::handleEntityDetailMarkdown),
                    get(
                        "/entity/detail.md",
                        RMHttpServer::handleEntityDetailMarkdown),
                    get(
                        "/entity/detail.json",
                        RMHttpServer::handleEntityDetail),
                    get(
                        "/player",
                        RMHttpServer::handlePlayer),
                    get(
                        "/langkey",
                        RMHttpServer::handleLangKey),
                    get(
                        "/langkey-search",
                        RMHttpServer::handleLangKeySearch),
                    get(
                        "/item-search",
                        RMHttpServer::handleItemSearch),
                    get(
                        "/block-search",
                        RMHttpServer::handleBlockSearch),
                    get(
                        "/entity-type-search",
                        RMHttpServer::handleEntityTypeSearch),
                    get(
                        "/fluid-search",
                        RMHttpServer::handleFluidSearch),
                    get(
                        "/tag-search",
                        RMHttpServer::handleTagSearch),
                    post(
                        "/inventory/swap",
                        RMHttpServer::handleInventorySwap),
                    post(
                        "/inventory/move",
                        RMHttpServer::handleInventoryMove),
                    post(
                        "/hotbar/select",
                        RMHttpServer::handleHotbarSelect),
                    post(
                        "/menu/close",
                        RMHttpServer::handleMenuClose),
                    post(
                        "/menu/drop",
                        RMHttpServer::handleMenuDrop),
                    post(
                        "/craft",
                        RMHttpServer::handleCraft),
                    post(
                        "/craft/parallel",
                        RMHttpServer::handleCraftParallel),
                    post(
                        "/container/put",
                        RMHttpServer::handleContainerPut),
                    post(
                        "/container/put/batch",
                        RMHttpServer::handleContainerPutBatch),
                    post(
                        "/container/take",
                        RMHttpServer::handleContainerTake),
                    post(
                        "/container/take/batch",
                        RMHttpServer::handleContainerTakeBatch),
                    post(
                        "/container/move",
                        RMHttpServer::handleContainerMove),
                    post(
                        "/container/move/batch",
                        RMHttpServer::handleContainerMoveBatch),
                    post(
                        "/move",
                        RMHttpServer::handleMove),
                    post(
                        "/respawn",
                        RMHttpServer::handleRespawn),
                    post(
                        "/entity/pickup-item",
                        RMHttpServer::handleEntityPickupItem),
                    post(
                        "/item/drop",
                        RMHttpServer::handleItemDrop),
                    post(
                        "/item/use-on-block",
                        RMHttpServer::handleItemUseOnBlock),
                    post(
                        "/place",
                        RMHttpServer::handlePlace),
                    post(
                        "/break",
                        RMHttpServer::handleBreak),
                    post(
                        "/place/batch",
                        RMHttpServer::handlePlaceBatch),
                    post(
                        "/place/discrete",
                        RMHttpServer::handlePlaceDiscrete),
                    post(
                        "/place/palette",
                        RMHttpServer::handlePlacePalette),
                    post(
                        "/break/batch",
                        RMHttpServer::handleBreakBatch),
                    post(
                        "/place/box",
                        RMHttpServer::handlePlaceBox),
                    post(
                        "/place/ring",
                        RMHttpServer::handlePlaceRing),
                    post(
                        "/break/box",
                        RMHttpServer::handleBreakBox)
            )

            private fun get(endpoint: String, handler: (HTTPRequest, HTTPResponse) -> Unit): Route {
                return Route(HTTPMethod.GET, endpoint, false, handler)
            }

            private fun getPrefix(endpoint: String, handler: (HTTPRequest, HTTPResponse) -> Unit): Route {
                return Route(HTTPMethod.GET, endpoint, true, handler)
            }

            private fun post(endpoint: String, handler: (HTTPRequest, HTTPResponse) -> Unit): Route {
                return Route(HTTPMethod.POST, endpoint, false, handler)
            }
        }
    }

    @JvmRecord
    private data class ParsedPos(val x: Int, val y: Int, val z: Int)

    @JvmRecord
    private data class InventorySwapRequest(val from: String, val to: String, val dryRun: Boolean)

    @JvmRecord
    private data class InventoryMoveRequest(
        val from: String,
        val to: String,
        val count: Int,
        val dryRun: Boolean
    )

    @JvmRecord
    private data class HotbarSelectRequest(val slot: Int, val dryRun: Boolean)

    @JvmRecord
    private data class MenuDropRequest(val slot: Int, val count: Int, val dryRun: Boolean)

    @JvmRecord
    private data class ResolveSearchRequest(val text: String, val modId: String, val limit: Int)

    @JvmRecord
    private data class CraftRequest(
        val slots: MutableMap<String, Int?>, val shape: String, val outputSlot: Int, val times: Int,
        val dryRun: Boolean
    )

    @JvmRecord
    private data class BlockActionRequest(val x: Int, val y: Int, val z: Int, val face: String?)

    @JvmRecord
    private data class PlayerMoveRequest(val x: Double, val y: Double, val z: Double)

    @JvmRecord
    private data class ItemPickupRequest(
        val ids: MutableList<String>,
        val radius: Double,
        val limit: Int?
    )

    @JvmRecord
    private data class ItemPickupParams(
        val ids: MutableList<UUID>,
        val radius: Double,
        val limit: Int,
        val error: RErrorCode?
    ) {
        companion object {
            private fun error(error: RErrorCode?): ItemPickupParams {
                return ItemPickupParams(mutableListOf<UUID>(), 64.0, 256, error)
            }
        }
    }

    @JvmRecord
    private data class BlockMapRequest(val x: Int, val y: Int, val z: Int, val radius: Int)

    @JvmRecord
    private data class BlockBatchActionRequest(val positions: MutableList<RBlockPos>?)

    @JvmRecord
    private data class BlockStateBatchRequest(val positions: MutableList<RBlockPos>?)

    @JvmRecord
    private data class BlockBoxActionRequest(
        val from: RBlockPos,
        val to: RBlockPos,
        val dryRun: Boolean
    )

    @JvmRecord
    private data class ContainerMoveRequest(
        val from: ContainerEndpointRequest, val to: ContainerEndpointRequest, val count: Int,
        val dryRun: Boolean
    )

    @JvmRecord
    private data class ContainerPutRequest(
        val fromInventorySlot: Int, val to: ContainerEndpointRequest, val count: Int,
        val dryRun: Boolean
    )

    @JvmRecord
    private data class ContainerTakeRequest(
        val from: ContainerEndpointRequest, val toInventorySlot: Int, val count: Int,
        val dryRun: Boolean
    )

    @JvmRecord
    private data class ContainerEndpointRequest(val pos: String, val side: String, val slot: Int?)
}
