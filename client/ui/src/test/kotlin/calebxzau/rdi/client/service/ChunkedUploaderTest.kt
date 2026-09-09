package calebxzau.rdi.client.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.util.sha1
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChunkedUploaderTest {
    @Test
    fun `uploads parts in parallel and reconstructs exact file including tail`() = runBlocking {
        val source = ByteArray(23) { (it * 7).toByte() }
        withTempFile(source) { file ->
            val uploaded = ConcurrentHashMap<Int, ByteArray>()
            val active = AtomicInteger(0)
            val maximum = AtomicInteger(0)
            val otherPartsDone = CompletableDeferred<Unit>()
            val otherParts = AtomicInteger(0)
            val progress = Collections.synchronizedList(mutableListOf<Pair<Long, Int>>())
            ChunkedUploader(
                file = file,
                descriptor = descriptor(source, partSize = 3),
                parallelism = 8,
                uploadPart = { index, bytes, sha1 ->
                    val current = active.incrementAndGet()
                    maximum.updateAndGet { old -> maxOf(old, current) }
                    try {
                        assertEquals(source.copyOfRange(index * 3, minOf(source.size, index * 3 + 3)).sha1, sha1)
                        uploaded[index] = bytes.copyOf()
                        if (index == 0) {
                            otherPartsDone.await()
                        } else if (otherParts.incrementAndGet() == 7) {
                            otherPartsDone.complete(Unit)
                        }
                    } finally {
                        active.decrementAndGet()
                    }
                },
                onProgress = { bytes, parts -> progress += bytes to parts },
            ).upload()

            val reconstructed = uploaded.toSortedMap().values.flatMap { it.asIterable() }.toByteArray()
            assertContentEquals(source, reconstructed)
            assertTrue(maximum.get() > 1)
            assertTrue(maximum.get() <= 8)
            assertEquals((source.size.toLong() to 8), progress.last())
            assertTrue(progress.zipWithNext().all { (before, after) -> after.first >= before.first && after.second >= before.second })
        }
    }

    @Test
    fun `does not start a ninth part while eight workers are occupied`() = runBlocking {
        val source = ByteArray(9) { it.toByte() }
        withTempFile(source) { file ->
            val eightStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val started = Collections.synchronizedList(mutableListOf<Int>())
            ChunkedUploader(
                file = file,
                descriptor = descriptor(source, partSize = 1),
                parallelism = 8,
                uploadPart = { index, _, _ ->
                    started += index
                    if (started.size == 8) eightStarted.complete(Unit)
                    if (started.size <= 8) release.await()
                },
            ).let { uploader ->
                val task = async { uploader.upload() }
                withTimeout(5_000) { eightStarted.await() }
                assertEquals(8, started.size)
                release.complete(Unit)
                task.await()
            }
        }
    }

    @Test
    fun `resumed parts are acknowledged by actual lengths and skipped`() = runBlocking {
        val source = ByteArray(5) { (it + 10).toByte() }
        withTempFile(source) { file ->
            val uploaded = Collections.synchronizedList(mutableListOf<Int>())
            val progress = Collections.synchronizedList(mutableListOf<Pair<Long, Int>>())
            ChunkedUploader(
                file = file,
                descriptor = descriptor(source, partSize = 2, uploadedParts = listOf(0, 0, 2)),
                parallelism = 8,
                uploadPart = { index, _, _ -> uploaded += index },
                onProgress = { bytes, parts -> progress += bytes to parts },
            ).upload()

            assertEquals(listOf(1), uploaded)
            assertEquals(listOf(3L to 2, 5L to 3), progress)
        }
    }

    @Test
    fun `retries a part with identical bytes and counts it once`() = runBlocking {
        val source = ByteArray(4) { (it + 1).toByte() }
        withTempFile(source) { file ->
            val calls = mutableListOf<ByteArray>()
            var attempts = 0
            val progress = mutableListOf<Pair<Long, Int>>()
            ChunkedUploader(
                file = file,
                descriptor = descriptor(source, partSize = 4),
                maxPartRetries = 1,
                retryDelayMillis = 0,
                parallelism = 1,
                uploadPart = { _, bytes, _ ->
                    calls += bytes.copyOf()
                    if (attempts++ == 0) throw IOException("transient")
                },
                onProgress = { bytes, parts -> progress += bytes to parts },
                isRetryable = { it is IOException },
            ).upload()

            assertEquals(2, calls.size)
            assertContentEquals(calls[0], calls[1])
            assertEquals(listOf(0L to 0, 4L to 1), progress)
        }
    }

    @Test
    fun `failure cancels and joins blocked sibling workers`() = runBlocking {
        val source = ByteArray(2)
        withTempFile(source) { file ->
            val siblingStarted = CompletableDeferred<Unit>()
            val siblingCancelled = CompletableDeferred<Unit>()
            val failure = assertFailsWith<IllegalStateException> {
                ChunkedUploader(
                    file = file,
                    descriptor = descriptor(source, partSize = 1),
                    parallelism = 2,
                    maxPartRetries = 0,
                    uploadPart = { index, _, _ ->
                        if (index == 0) {
                            siblingStarted.await()
                            throw IllegalStateException("part failed")
                        }
                        siblingStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            siblingCancelled.complete(Unit)
                        }
                    },
                ).upload()
            }
            assertEquals("part failed", failure.message)
            assertTrue(siblingCancelled.isCompleted)
        }
    }

    @Test
    fun `parent cancellation propagates while a part is blocked`() = runBlocking {
        val source = byteArrayOf(1)
        withTempFile(source) { file ->
            val started = CompletableDeferred<Unit>()
            val task: Job = async {
                ChunkedUploader(
                    file = file,
                    descriptor = descriptor(source, partSize = 1),
                    uploadPart = { _, _, _ ->
                        started.complete(Unit)
                        awaitCancellation()
                    },
                ).upload()
            }
            withTimeout(5_000) { started.await() }
            task.cancelAndJoin()
            assertTrue(task.isCancelled)
        }
    }

    @Test
    fun `shared HTTP client reaches eight loopback handlers concurrently`() = runBlocking {
        val source = ByteArray(8) { (it + 1).toByte() }
        withTempFile(source) { file ->
            val entered = CountDownLatch(8)
            val release = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(8)
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.executor = executor
            server.createContext("/") { exchange ->
                try {
                    exchange.requestBody.use { it.readBytes() }
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.use { }
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val task = async {
                ChunkedUploader(
                    file = file,
                    descriptor = descriptor(source, partSize = 1),
                    parallelism = 8,
                    uploadPart = { _, bytes, _ ->
                        ktorClient.put("http://127.0.0.1:${server.address.port}/part") {
                            contentType(ContentType.Application.OctetStream)
                            setBody(bytes)
                        }.bodyAsText()
                    },
                ).upload()
            }
            try {
                val allEntered = withContext(Dispatchers.IO) {
                    withTimeout(5_000) { entered.await(5, TimeUnit.SECONDS) }
                }
                assertTrue(allEntered)
                release.countDown()
                task.await()
            } finally {
                release.countDown()
                task.cancelAndJoin()
                server.stop(0)
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `part cancellation cancels and joins sibling workers`() = runBlocking {
        val source = ByteArray(2)
        withTempFile(source) { file ->
            val siblingStarted = CompletableDeferred<Unit>()
            val siblingCancelled = CompletableDeferred<Unit>()
            assertFailsWith<kotlinx.coroutines.CancellationException> {
                ChunkedUploader(
                    file = file,
                    descriptor = descriptor(source, partSize = 1),
                    parallelism = 2,
                    uploadPart = { index, _, _ ->
                        if (index == 0) {
                            siblingStarted.await()
                            throw kotlinx.coroutines.CancellationException("part canceled")
                        }
                        siblingStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            siblingCancelled.complete(Unit)
                        }
                    },
                ).upload()
            }
            assertTrue(siblingCancelled.isCompleted)
        }
    }

    private fun descriptor(
        source: ByteArray,
        partSize: Int,
        uploadedParts: List<Int> = emptyList(),
    ) = ChunkedUploadDescriptor(
        size = source.size.toLong(),
        partSize = partSize,
        partCount = (source.size + partSize - 1) / partSize,
        uploadedParts = uploadedParts,
    )

    private suspend fun withTempFile(bytes: ByteArray, block: suspend (Path) -> Unit) {
        val file = Files.createTempFile("chunked-upload-", ".bin")
        Files.write(file, bytes)
        try {
            block(file)
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
