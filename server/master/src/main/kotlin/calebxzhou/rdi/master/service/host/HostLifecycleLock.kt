package calebxzhou.rdi.master.service.host

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.types.ObjectId
import java.util.concurrent.ConcurrentHashMap

/** Serializes mutating operations for one active Legacy Host in this process. */
internal object HostLifecycleLock {
    private val locks = ConcurrentHashMap<ObjectId, Mutex>()

    suspend inline fun <T> withLock(hostId: ObjectId, crossinline block: suspend () -> T): T =
        locks.computeIfAbsent(hostId) { Mutex() }.withLock { block() }
}
