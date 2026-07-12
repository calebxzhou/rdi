package calebxzhou.rdi.proxy2

import calebxzhou.rdi.proxy2.http.Proxy2HttpServer
import calebxzhou.rdi.proxy2.metrics.Proxy2Metrics
import calebxzhou.rdi.proxy2.relay.RelaySessionManager
import calebxzhou.rdi.proxy2.relay.TcpRelayServer
import calebxzhou.rdi.proxy2.relay.UdpRelayServer
import calebxzhou.rdi.proxy2.stun.StunServer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private val lgr = KotlinLogging.logger {}

fun main() {
    val config = Proxy2Config.load()
    val metrics = Proxy2Metrics()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sessions = RelaySessionManager(config, metrics, scope)
    val httpServer = Proxy2HttpServer(config, metrics, sessions)
    val stunServer = StunServer(config, metrics)
    val udpRelayServer = UdpRelayServer(config, metrics, sessions)
    val tcpRelayServer = TcpRelayServer(config, metrics, sessions)

    fun stop() {
        lgr.info { "stopping proxy2" }
        tcpRelayServer.stop()
        udpRelayServer.stop()
        stunServer.stop()
        httpServer.stop()
        scope.cancel()
    }

    Runtime.getRuntime().addShutdownHook(Thread(::stop, "proxy2-shutdown"))

    lgr.info {
        "starting proxy2 http=${config.httpPort} stun=${config.stunPort} " +
            "udpRelay=${config.udpRelayPort} tcpRelay=${config.tcpRelayPort}"
    }
    httpServer.start()
    stunServer.start()
    udpRelayServer.start()
    tcpRelayServer.start()

    Thread.currentThread().join()
}
