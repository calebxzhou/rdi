package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.McpError
import calebxzhou.rdi.mc.common2.mcp.McpInternalError
import calebxzhou.rdi.mc.common2.mcp.McpMethodNotAllowedError
import calebxzhou.rdi.mc.common2.mcp.McpNotFoundError
import io.fusionauth.http.HTTPMethod
import io.fusionauth.http.server.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

/*
RMCP API design rule
all response 200.
 */
object McpServer {
    private val lgr = LoggerFactory.getLogger("RMcpServer")
    private var server: HTTPServer? = null
    private var shutdownHookRegistered = false
    private var game: McpGameInterface? = null

    @Synchronized
    fun start(game: McpGameInterface, port: Int? = null): Result<Int> {
        this.game = game
        val port = port?:selectAvailablePort()
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
        File("rmcp_port.txt").writeText(port.toString())
        lgr.info("RMCP Server started on port {}", port)
        printRoutes()
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(Thread(::stop, "rdi-mcp-http-stop"))
            shutdownHookRegistered = true
        }
        return Result.success(port)
    }

    @Synchronized
    fun stop() {
        server?.close() ?: return
        server = null
        game = null
    }

    private fun handle(request: HTTPRequest, response: HTTPResponse) {
        var matchedRoute: McpRoute? = null
        runCatching {
            if (request.path == "/") {
                response.writeText(endpointListText())
                return
            }
            val route = ROUTES[request.path] ?: throw McpNotFoundError()
            matchedRoute = route
            if (!request.method.`is`(route.method)) {
                throw McpMethodNotAllowedError()
            }
            route.handler.handle(McpHttpContext(request, response, game ?: throw McpError("unavaliable local server"))).getOrThrow()
        }.onSuccess { result ->
            when (result) {
                null -> response.writeText("ok")
                is ByteArray -> response.writePng(result)
                else -> response.writeText(result.toString())
            }
        }.onFailure { e ->
            val route = matchedRoute
            if (e is McpBadRequestError && route != null) {
                response.writeBadRequestError(e, route)
                e.printStackTrace()
                return@onFailure
            }
            if(e is McpError){
                response.writeError(e)
                return@onFailure
            }
            e.printStackTrace()
            response.writeError(McpInternalError())
        }
    }

    private fun printRoutes() {
        lgr.info("RMCP routes: {}", ROUTES.map { (_,it) ->"${it.method} ${it.path}" })
    }

    private fun endpointListText(): String {
        return buildString {
            appendLine("""
                Use these local HTTP APIs to read live data from the running Minecraft client. 
                Do not guess game state when an API can read it directly.
                Read APIs use `GET`. Action APIs use `POST`.
                
            """.trimIndent())
            ROUTES.values
                .sortedBy { it.path }
                .forEach { appendLine("${it.method} ${it.path}") }
        }.trimEnd()
    }

    private fun HTTPResponse.writeText(text: String) {
        this.status = 200
        contentType = "text/plain; charset=utf-8"
        getWriter().write(text)
    }
    private fun HTTPResponse.writePng(data: ByteArray) {
        this.status = 200
        contentType = "image/png"
        setHeader("Cache-Control", "no-store")
        contentLength = data.size.toLong()
        outputStream.write(data)
    }

    private fun selectAvailablePort(): Int {
        try {
            ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { socket ->
                return socket.getLocalPort()
            }
        } catch (e: IOException) {
            throw McpInternalError()
        }
    }

    private fun HTTPResponse.writeError(e: McpError) {
        writeText("${e.javaClass.simpleName} ${e.detail}")
    }

    private fun HTTPResponse.writeBadRequestError(e: McpBadRequestError, route: McpRoute) {
        writeText("${e.javaClass.simpleName} ${e.detail}\nHELP DOC: ${route.handler.helpDoc}")
    }
}
