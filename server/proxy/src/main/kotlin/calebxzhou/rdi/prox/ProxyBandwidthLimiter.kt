package calebxzhou.rdi.prox

import io.netty.handler.traffic.GlobalTrafficShapingHandler
import io.netty.util.concurrent.GlobalEventExecutor
import java.util.concurrent.ConcurrentHashMap

object ProxyBandwidthLimiter {
    private const val BANDWIDTH_LIM = 50L * 1000L * 1000L / 8L
    private val limiters = ConcurrentHashMap<Int, GlobalTrafficShapingHandler>()

    fun forHostPort(port: Int): GlobalTrafficShapingHandler =
        limiters.computeIfAbsent(port) {
            GlobalTrafficShapingHandler(
                GlobalEventExecutor.INSTANCE,
                BANDWIDTH_LIM,
                BANDWIDTH_LIM
            )
        }
}
