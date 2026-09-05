package calebxzhou.rdi.master.service

import calebxzhou.rdi.master.service.modpack.ModpackVersionMutationLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.bson.types.ObjectId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModpackServiceVersionLockTest {
    @Test
    fun `version admission lock remains held until host insertion completes`() = runTest {
        val modpackId = ObjectId()
        val inserted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val admission = async {
            ModpackVersionMutationLock.withLock(modpackId, "1.0") {
                inserted.complete(Unit)
                release.await()
            }
        }
        inserted.await()
        var secondEntered = false
        val deletion = async {
            ModpackVersionMutationLock.withLock(modpackId, "1.0") {
                secondEntered = true
            }
        }
        kotlinx.coroutines.yield()
        assertFalse(secondEntered)
        release.complete(Unit)
        admission.await()
        deletion.await()
        assertTrue(secondEntered)
    }
}
