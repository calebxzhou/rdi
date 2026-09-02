package calebxzau.rdi.common.util

import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.common.util.toObjectId
import calebxzhou.rdi.common.util.toUUID
import org.bson.types.ObjectId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectIdUuidTest {
    @Test
    fun `object id mapping keeps the fixed byte layout`() {
        val objectId = ObjectId("00112233445566778899aabb")

        assertEquals(
            UUID.fromString("00112233-4455-6677-8899-aabb00000000"),
            objectId.toUUID()
        )
    }

    @Test
    fun `object id mapping round trips`() {
        val objectId = ObjectId("00112233445566778899aabb")

        assertEquals(objectId, objectId.toUUID().objectId)
        assertEquals(objectId, objectId.toUUID().toObjectId().getOrThrow())
    }

    @Test
    fun `ordinary uuid is rejected by strict reverse mapping`() {
        val ordinaryUuid = UUID.fromString("00112233-4455-6677-8899-aabb00000001")

        assertTrue(ordinaryUuid.toObjectId().isFailure)
    }
}
