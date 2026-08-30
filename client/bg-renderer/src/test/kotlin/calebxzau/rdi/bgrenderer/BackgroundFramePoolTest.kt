package calebxzau.rdi.bgrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackgroundFramePoolTest {
    private class Payload : AutoCloseable {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    private fun pool(created: MutableList<Payload> = mutableListOf()) =
        FrameSlotPool(3, { Payload().also(created::add) }) { _, _ -> }

    private fun publish(pool: FrameSlotPool<Payload>): BackgroundFramePainter<Payload> {
        val write = assertNotNull(pool.write())
        return assertNotNull(write.publish())
    }

    @Test
    fun createsExactlyThreeReusableSlots() {
        val created = mutableListOf<Payload>()
        val pool = pool(created)
        publish(pool)
        assertEquals(3, created.size)
        repeat(20) { publish(pool) }
        assertEquals(3, created.size)
        pool.close()
    }

    @Test
    fun oldReadyIsReturnedToFree() {
        val pool = pool()
        publish(pool)
        val first = pool.snapshots()
        publish(pool)
        val second = pool.snapshots()
        assertEquals(1, first.count { it.state == FrameSlotState.READY })
        assertEquals(1, second.count { it.state == FrameSlotState.READY })
        assertEquals(2, second.count { it.state == FrameSlotState.FREE })
        pool.close()
    }

    @Test
    fun displayedSlotRemainsReservedAfterDraw() {
        val pool = pool()
        publish(pool)
        val lease = assertNotNull(pool.drawLease())
        lease.close()
        assertEquals(1, pool.snapshots().count { it.state == FrameSlotState.DISPLAYED })
        assertEquals(2, pool.snapshots().count { it.state == FrameSlotState.FREE })
        pool.close()
    }

    @Test
    fun activeReadersDelayOldDisplayedReuse() {
        val pool = pool()
        publish(pool)
        val oldLease = assertNotNull(pool.drawLease())
        publish(pool)
        val newLease = assertNotNull(pool.drawLease())
        assertEquals(2, pool.snapshots().count { it.state == FrameSlotState.DISPLAYED })
        oldLease.close()
        assertEquals(1, pool.snapshots().count { it.state == FrameSlotState.DISPLAYED })
        assertEquals(2, pool.snapshots().count { it.state == FrameSlotState.FREE })
        newLease.close()
        pool.close()
    }

    @Test
    fun noFreeSlotDropsFrameWithoutOverwriting() {
        val created = mutableListOf<Payload>()
        val pool = pool(created)
        val leases = buildList {
            repeat(3) {
                publish(pool)
                add(assertNotNull(pool.drawLease()))
            }
        }
        assertNull(pool.write())
        pool.close()
        leases.forEach { it.close() }
        assertTrue(created.all(Payload::closed))
    }

    @Test
    fun payloadFactoryFailureRollsBackAlreadyCreatedSlots() {
        val created = mutableListOf<Payload>()
        var attempts = 0
        val pool = FrameSlotPool<Payload>(3, {
            if (++attempts == 2) error("payload allocation failed")
            Payload().also(created::add)
        }) { _, _ -> }

        assertFailsWith<IllegalStateException> { pool.write() }
        assertEquals(1, created.size)
        assertTrue(created.single().closed)
        assertEquals(0, pool.createdSlotCount)
        pool.close()
    }

    @Test
    fun closeRejectsWritesAndDefersActiveReaderPayload() {
        val created = mutableListOf<Payload>()
        val pool = pool(created)
        publish(pool)
        val lease = assertNotNull(pool.drawLease())
        pool.close()
        assertTrue(pool.isClosed())
        assertNull(pool.write())
        assertFalse(created[0].closed)
        lease.close()
        assertTrue(created.all(Payload::closed))
    }

    @Test
    fun closeSeesThreadPublishedBeforeStartReturns() {
        val startEntered = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        val state = RendererState(
            threadFactory = { runnable, name ->
                object : Thread(runnable, name) {
                    override fun start() {
                        startEntered.countDown()
                        releaseStart.await(5, TimeUnit.SECONDS)
                        super.start()
                    }
                }
            },
            renderLoopOverride = {}
        )
        val ensureThread = Thread { state.ensureThread() }
        ensureThread.start()
        assertTrue(startEntered.await(5, TimeUnit.SECONDS))
        val closeThread = Thread {
            state.close()
            closeReturned.countDown()
        }
        closeThread.start()
        assertFalse(closeReturned.await(100, TimeUnit.MILLISECONDS))
        releaseStart.countDown()
        assertTrue(closeReturned.await(5, TimeUnit.SECONDS))
        ensureThread.join(5_000)
        closeThread.join(5_000)
    }
}
