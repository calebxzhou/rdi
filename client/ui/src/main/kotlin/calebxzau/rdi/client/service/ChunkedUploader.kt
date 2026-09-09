package calebxzau.rdi.client.service

import calebxzhou.rdi.common.util.sha1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicInteger

const val MAX_CHUNKED_UPLOAD_PART_SIZE = 4 * 1024 * 1024
const val DEFAULT_CHUNKED_UPLOAD_PARALLELISM = 8

data class ChunkedUploadDescriptor(
    val size: Long,
    val partSize: Int,
    val partCount: Int,
    val uploadedParts: List<Int> = emptyList(),
)

/**
 * Uploads the missing parts of a local file with bounded parallelism.
 *
 * The uploader owns only local file reading, retrying, and progress accounting.
 * The caller supplies the remote part operation and can keep its domain-specific
 * task presentation and cancellation checks outside this reusable component.
 */
class ChunkedUploader(
    private val file: Path,
    private val descriptor: ChunkedUploadDescriptor,
    private val maxPartRetries: Int = 3,
    private val retryDelayMillis: Long = 100L,
    parallelism: Int = DEFAULT_CHUNKED_UPLOAD_PARALLELISM,
    private val uploadPart: suspend (index: Int, bytes: ByteArray, sha1: String) -> Unit,
    private val ensureActive: () -> Unit = {},
    private val onProgress: (completedBytes: Long, completedParts: Int) -> Unit = { _, _ -> },
    private val isRetryable: (Throwable) -> Boolean = { cause ->
        cause !is CancellationException
    },
) {
    private val parallelism = parallelism.also {
        require(it in 1..DEFAULT_CHUNKED_UPLOAD_PARALLELISM) { "分片并发数必须在1到8之间" }
    }

    init {
        require(maxPartRetries >= 0) { "分片重试次数不能为负数" }
        require(retryDelayMillis >= 0) { "分片重试间隔不能为负数" }
    }

    suspend fun upload() {
        validateDescriptor()
        val acknowledged = descriptor.uploadedParts.toSet()
        val missing = (0 until descriptor.partCount).filterNot(acknowledged::contains)
        val acknowledgedBytes = acknowledged.sumOf(::partLength)
        val progressLock = Mutex()
        var completedBytes = acknowledgedBytes
        var completedParts = acknowledged.size

        onProgress(completedBytes, completedParts)
        if (missing.isEmpty()) return

        FileChannel.open(file, StandardOpenOption.READ).use { channel ->
            coroutineScope {
                val nextPart = AtomicInteger(0)
                val workerCount = minOf(parallelism, missing.size)
                val workers = buildList {
                    repeat(workerCount) {
                        add(async {
                            while (true) {
                                ensureCurrentCoroutineActive()
                                val position = nextPart.getAndIncrement()
                                if (position >= missing.size) return@async
                                val index = missing[position]
                                val length = partLength(index)
                                val bytes = readPart(channel, index, length)
                                val partSha1 = sha1(bytes)
                                uploadWithRetry(index, bytes, partSha1)
                                progressLock.withLock {
                                    completedBytes += length
                                    completedParts++
                                    onProgress(completedBytes, completedParts)
                                }
                            }
                        })
                    }
                }
                workers.awaitAll()
            }
        }
    }

    private fun validateDescriptor() {
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "分片上传文件无效"
        }
        val actualSize = Files.size(file)
        require(descriptor.size > 0 && descriptor.size == actualSize) {
            "服务器返回的上传文件大小不匹配"
        }
        require(descriptor.partSize in 1..MAX_CHUNKED_UPLOAD_PART_SIZE && descriptor.partCount > 0) {
            "服务器返回的上传分片信息无效"
        }
        val expectedPartCount = ((descriptor.size - 1) / descriptor.partSize + 1).toInt()
        require(descriptor.partCount == expectedPartCount) { "服务器返回的分片数量不匹配" }
        require(descriptor.uploadedParts.all { it in 0 until descriptor.partCount }) {
            "服务器返回的已上传分片序号无效"
        }
    }

    private fun partLength(index: Int): Long {
        val offset = index.toLong() * descriptor.partSize
        val length = minOf(descriptor.partSize.toLong(), descriptor.size - offset)
        require(length in 1..Int.MAX_VALUE) { "服务器返回的分片范围无效" }
        return length
    }

    private suspend fun readPart(channel: FileChannel, index: Int, length: Long): ByteArray {
        ensureCurrentCoroutineActive()
        val bytes = ByteArray(length.toInt())
        val buffer = ByteBuffer.wrap(bytes)
        val offset = index.toLong() * descriptor.partSize
        while (buffer.hasRemaining()) {
            ensureCurrentCoroutineActive()
            val count = channel.read(buffer, offset + buffer.position())
            require(count > 0) { "读取压缩包分片失败" }
        }
        return bytes
    }

    private suspend fun uploadWithRetry(index: Int, bytes: ByteArray, partSha1: String) {
        var attempt = 0
        while (true) {
            ensureCurrentCoroutineActive()
            try {
                uploadPart(index, bytes, partSha1)
                return
            } catch (cause: Throwable) {
                if (!isRetryable(cause) || attempt >= maxPartRetries) throw cause
                attempt++
                ensureCurrentCoroutineActive()
                delay(retryDelayMillis)
            }
        }
    }

    private suspend fun ensureCurrentCoroutineActive() {
        currentCoroutineContext().ensureActive()
        ensureActive()
    }

    private fun sha1(bytes: ByteArray): String =
        bytes.sha1
}
