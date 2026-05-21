package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.McpError
import calebxzhou.rdi.mc.common2.mcp.McpInternalError
import calebxzhou.rdi.mc.common2.mcp.McpMethodNotAllowedError
import calebxzhou.rdi.mc.common2.mcp.McpNotFoundError
import calebxzhou.rdi.mc.common2.mcp.McpServerMcpUnavailableError
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
        runCatching {
            val route = ROUTES[request.path] ?: throw McpNotFoundError()
            if (!request.method.`is`(route.method)) {
                throw McpMethodNotAllowedError()
            }
            route.handler.handle(McpHttpContext(request, response, game ?: throw McpServerMcpUnavailableError())).getOrThrow()
        }.onSuccess { result ->
            if (result == null) {
                response.writeText("ok")
            } else {
                response.writeText(result.toString())
            }
        }.onFailure { e ->
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

    private fun HTTPResponse.writeText(text: String) {
        this.status = 200
        contentType = "text/plain; charset=utf-8"
        getWriter().write(text)
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
        writeText("${e.javaClass.simpleName.removeSuffix("Error")} ${e.detail}")
    }
}
