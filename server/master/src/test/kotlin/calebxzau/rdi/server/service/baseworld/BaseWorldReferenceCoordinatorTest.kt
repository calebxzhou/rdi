package calebxzau.rdi.server.service.baseworld

import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseWorldReferenceCoordinatorTest {
    @Test
    fun snapshotLeaseIsVisibleUntilReleasedAndReleaseIsIdempotent() = runBlocking {
        val worldId = UUID.randomUUID()
        val lease = BaseWorldReferenceCoordinator.acquireSnapshotLease(worldId)

        assertTrue(BaseWorldReferenceCoordinator.hasPendingSnapshot(worldId))

        lease.release()
        assertFalse(BaseWorldReferenceCoordinator.hasPendingSnapshot(worldId))

        lease.release()
        assertFalse(BaseWorldReferenceCoordinator.hasPendingSnapshot(worldId))
    }
}
