package calebxzhou.rdi.mc.server.world

import java.util.concurrent.Semaphore
import java.util.function.Supplier

object TerrainGenerationLimiter1710 {
    private const val MAX_THREADS = 2
    private val PERMITS = Semaphore(MAX_THREADS)
    private val DEPTH: ThreadLocal<Int> = ThreadLocal.withInitial(Supplier { 0 })

    @JvmStatic
    fun enter() {
        val depth = DEPTH.get()!!
        if (depth == 0) {
            PERMITS.acquireUninterruptibly()
        }
        DEPTH.set(depth + 1)
    }

    @JvmStatic
    fun exit() {
        val depth = DEPTH.get()!! - 1
        if (depth == 0) {
            DEPTH.remove()
            PERMITS.release()
            return
        }
        DEPTH.set(depth)
    }
}
