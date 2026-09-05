package calebxzhou.rdi.master.service.modpack

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.types.ObjectId
import java.util.concurrent.ConcurrentHashMap

/** Serializes Legacy Modpack version mutations in this process. */
internal object ModpackVersionMutationLock {
    private data class Key(val modpackId: ObjectId, val versionName: String)

    private val locks = ConcurrentHashMap<Key, Mutex>()

    suspend fun <T> withLock(
        modpackId: ObjectId,
        versionName: String,
        block: suspend () -> T
    ): T = locks.computeIfAbsent(Key(modpackId, versionName)) { Mutex() }.withLock { block() }
}
