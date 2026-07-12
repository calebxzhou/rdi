package calebxzhou.rdi.proxy2.http

import calebxzhou.rdi.proxy2.Proxy2Config
import calebxzhou.rdi.proxy2.metrics.Proxy2Metrics
import calebxzhou.rdi.proxy2.relay.RelaySessionManager
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

class Proxy2HttpServer(
    private val config: Proxy2Config,
    private val metrics: Proxy2Metrics,
    private val sessions: RelaySessionManager,
) {
    private var server: ApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, host = config.bindHost, port = config.httpPort) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/health") {
                    call.respond(mapOf("ok" to true))
                }
                get("/status") {
                    call.respond(
                        Proxy2StatusVo(
                            ok = true,
                            stunPort = config.stunPort,
                            udpRelayPort = config.udpRelayPort,
                            tcpRelayPort = config.tcpRelayPort,
                            activeRelaySessions = sessions.activeSessionCount(),
                            metrics = metrics.snapshot(),
                        )
                    )
                }
                get("/") {
                    call.respondText("proxy2 ok", ContentType.Text.Plain)
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
    }
}

@Serializable
data class Proxy2StatusVo(
    val ok: Boolean,
    val stunPort: Int,
    val udpRelayPort: Int,
    val tcpRelayPort: Int,
    val activeRelaySessions: Int,
    val metrics: Map<String, Long>,
)
