package calebxzau.rdi.server.service.baseworld

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-local coordination for operations that reference a published map template.
 * Mongo and PostgreSQL remain the durable sources of truth; this only closes races
 * between this process's version, host-admission, and deletion operations.
 */
object BaseWorldReferenceCoordinator {
    private val locks = Array(256) { Mutex() }
    private val pendingSnapshots = ConcurrentHashMap<UUID, AtomicInteger>()

    suspend fun <T> withLock(id: UUID, block: suspend () -> T): T =
        locks[(id.hashCode() and Int.MAX_VALUE) % locks.size].withLock { block() }

    suspend fun acquireSnapshotLease(id: UUID): SnapshotLease = withLock(id) {
        acquireSnapshotLeaseLocked(id)
    }

    internal fun acquireSnapshotLeaseLocked(id: UUID): SnapshotLease {
        pendingSnapshots.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
        return SnapshotLease(id)
    }

    suspend fun hasPendingSnapshot(id: UUID): Boolean = withLock(id) {
        hasPendingSnapshotLocked(id)
    }

    internal fun hasPendingSnapshotLocked(id: UUID): Boolean =
        pendingSnapshots[id]?.get()?.let { it > 0 } == true

    class SnapshotLease internal constructor(private val id: UUID) {
        private val released = AtomicBoolean(false)

        suspend fun release() {
            if (!released.compareAndSet(false, true)) return
            withLock(id) {
                pendingSnapshots.computeIfPresent(id) { _, count ->
                    if (count.decrementAndGet() <= 0) null else count
                }
            }
        }
    }
}
