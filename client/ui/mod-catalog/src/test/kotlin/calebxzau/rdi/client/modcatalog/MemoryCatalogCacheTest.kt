package calebxzau.rdi.client.modcatalog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MemoryCatalogCacheTest {
    @Test
    fun `same key loads once and shares result`() = runBlocking<Unit> {
        withTimeout(5_000) {
            val cache = cache()
            val loadStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val loads = AtomicInteger()

            supervisorScope {
                val owner = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.getOrLoad("key") {
                        loads.incrementAndGet()
                        loadStarted.complete(Unit)
                        release.await()
                        "value"
                    }
                }
                loadStarted.await()
                val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.getOrLoad("key") { error("second load") }
                }
                release.complete(Unit)

                assertEquals("value", owner.await())
                assertEquals("value", waiter.await())
                assertEquals(1, loads.get())
            }
        }
    }

    @Test
    fun `failed load completes waiters and can retry`() = runBlocking<Unit> {
        withTimeout(5_000) {
            val cache = cache()
            val loadStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val failure = IllegalStateException("offline")
            val loads = AtomicInteger()

            supervisorScope {
                val owner = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.getOrLoad("key") {
                        loads.incrementAndGet()
                        loadStarted.complete(Unit)
                        release.await()
                        throw failure
                    }
                }
                loadStarted.await()
                val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.getOrLoad("key") { error("second load") }
                }
                release.complete(Unit)

                val ownerFailure = assertFailsWith<IllegalStateException> { owner.await() }
                val waiterFailure = assertFailsWith<IllegalStateException> { waiter.await() }
                assertEquals(failure.message, ownerFailure.message)
                assertEquals(failure.message, waiterFailure.message)
                assertTrue(ownerFailure === failure || ownerFailure.cause === failure)
                assertTrue(waiterFailure === failure || waiterFailure.cause === failure)
                assertEquals(1, loads.get())
            }

            assertEquals("retry", cache.getOrLoad("key") { "retry" })
        }
    }

    @Test
    fun `owner cancellation completes waiters and permits retry`() = runBlocking<Unit> {
        withTimeout(5_000) {
            val cache = cache()
            val loadStarted = CompletableDeferred<Unit>()

            supervisorScope {
                val owner = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.getOrLoad("key") {
                        loadStarted.complete(Unit)
                        CompletableDeferred<Unit>().await()
                        "never"
                    }
                }
                loadStarted.await()
                val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.getOrLoad("key") { "unexpected" }
                }
                owner.cancel()

                assertFailsWith<CancellationException> { owner.await() }
                assertFailsWith<CancellationException> { waiter.await() }
            }

            assertEquals("retry", cache.getOrLoad("key") { "retry" })
        }
    }

    @Test
    fun `owner cancellation cleans up after mutex contention`() = runBlocking<Unit> {
        withTimeout(10_000) {
            val clock = BlockingClock()
            val cache = MemoryCatalogCache<String, String>(
                clock = clock,
                positiveTtl = Duration.ofMinutes(1),
                negativeTtl = Duration.ofMinutes(1)
            )
            val loadStarted = CompletableDeferred<Unit>()

            supervisorScope {
                var owner: Deferred<String?>? = null
                var waiter: Deferred<String?>? = null
                var competingPut: Deferred<Unit>? = null
                try {
                    owner = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.getOrLoad("key") {
                            loadStarted.complete(Unit)
                            CompletableDeferred<Unit>().await()
                            "never"
                        }
                    }
                    loadStarted.await()
                    waiter = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.getOrLoad("key") { "unexpected" }
                    }
                    clock.blockNextInstant()
                    competingPut = async(Dispatchers.Default) { cache.put("other", "value") }
                    check(clock.entered.await(5, TimeUnit.SECONDS)) {
                        "mutex contention was not established"
                    }

                    owner.cancel()
                    yield()
                    assertTrue(!owner.isCompleted)
                    clock.release()
                    assertFailsWith<CancellationException> { owner.await() }
                    assertFailsWith<CancellationException> { waiter.await() }
                    competingPut.await()
                } finally {
                    clock.release()
                    owner?.cancel()
                    waiter?.cancel()
                    competingPut?.cancel()
                    withTimeout(5_000) {
                        listOfNotNull(owner, waiter, competingPut).joinAll()
                    }
                }

            }

            assertEquals("retry", cache.getOrLoad("key") { "retry" })
        }
    }

    @Test
    fun `cancelling one waiter leaves owner active`() = runBlocking<Unit> {
        withTimeout(5_000) {
            val cache = cache()
            val loadStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            supervisorScope {
                val owner = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.getOrLoad("key") {
                        loadStarted.complete(Unit)
                        release.await()
                        "value"
                    }
                }
                loadStarted.await()
                val canceledWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.getOrLoad("key") { error("canceled waiter loaded") }
                }
                val survivingWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.getOrLoad("key") { error("surviving waiter loaded") }
                }
                canceledWaiter.cancel()
                release.complete(Unit)

                assertEquals("value", owner.await())
                assertEquals("value", survivingWaiter.await())
                assertFailsWith<CancellationException> { canceledWaiter.await() }
            }
        }
    }

    @Test
    fun `positive and null values are cached`() = runBlocking<Unit> {
        withTimeout(5_000) {
            val clock = TestClock()
            val cache = MemoryCatalogCache<String, String>(
                clock = clock,
                positiveTtl = Duration.ofSeconds(10),
                negativeTtl = Duration.ofSeconds(20)
            )
            val valueLoads = AtomicInteger()
            val missingLoads = AtomicInteger()

            assertEquals("loaded", cache.getOrLoad("value") {
                valueLoads.incrementAndGet()
                "loaded"
            })
            assertEquals("loaded", cache.getOrLoad("value") {
                valueLoads.incrementAndGet()
                "unexpected"
            })
            assertEquals(1, valueLoads.get())

            assertEquals(null, cache.getOrLoad("missing") {
                missingLoads.incrementAndGet()
                null
            })
            assertEquals(null, cache.getOrLoad("missing") {
                missingLoads.incrementAndGet()
                "unexpected"
            })
            assertEquals(1, missingLoads.get())

            clock.advance(Duration.ofSeconds(11))
            assertEquals("reloaded", cache.getOrLoad("value") {
                valueLoads.incrementAndGet()
                "reloaded"
            })
            assertEquals(2, valueLoads.get())

            clock.advance(Duration.ofSeconds(10))
            assertEquals(null, cache.getOrLoad("missing") {
                missingLoads.incrementAndGet()
                null
            })
            assertEquals(2, missingLoads.get())
        }
    }

    private fun cache() = MemoryCatalogCache<String, String>(
        clock = Clock.systemUTC(),
        positiveTtl = Duration.ofMinutes(1),
        negativeTtl = Duration.ofMinutes(1)
    )

    private class BlockingClock : Clock() {
        private val now = Instant.EPOCH
        private var shouldBlock = false
        private var releaseLatch = CountDownLatch(0)
        val entered = CountDownLatch(1)

        @Synchronized
        fun blockNextInstant() {
            shouldBlock = true
            releaseLatch = CountDownLatch(1)
        }

        fun release() {
            releaseLatch.countDown()
        }

        override fun instant(): Instant {
            val latch = synchronized(this) {
                if (!shouldBlock) return now
                shouldBlock = false
                entered.countDown()
                releaseLatch
            }
            check(latch.await(10, TimeUnit.SECONDS)) { "clock release timed out" }
            return now
        }

        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this
    }

    private class TestClock(private var now: Instant = Instant.EPOCH) : Clock() {
        override fun instant(): Instant = now

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this
    }
}
