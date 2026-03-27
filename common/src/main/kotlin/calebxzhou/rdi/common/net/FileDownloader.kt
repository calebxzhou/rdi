package calebxzhou.rdi.common.net

import calebxzhou.mykotutils.log.Loggers
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * calebxzhou @ 2025-12-20 11:49
 * Simplified @ 2026-02-12
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Double,
) {
    val fraction: Float
        get() = if (totalBytes <= 0) -1f else (bytesDownloaded / totalBytes.toFloat()).coerceAtMost(1f)
}

private val lgr by Loggers
private const val DEFAULT_DOWNLOAD_ATTEMPTS = 2
private const val DEFAULT_MAX_RANGE_THREADS = 4
private const val MIN_MULTI_PART_DOWNLOAD_BYTES = 4L * 1024 * 1024
private const val MIN_BYTES_PER_RANGE = 2L * 1024 * 1024
private const val READ_TIMEOUT_MILLIS = 30_000L
private const val ZERO_READ_BACKOFF_MILLIS = 100L
private const val MAX_CONSECUTIVE_ZERO_READS = 100

val httpFileClient by lazy {
    HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                followRedirects(true)
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
                dispatcher(Dispatcher().apply {
                    maxRequests = 1024
                    maxRequestsPerHost = 1024
                })
                connectionPool(ConnectionPool(1024, 1, TimeUnit.MINUTES))
                proxySelector(DynamicProxySelector())
            }
        }
        BrowserUserAgent()
        install(HttpTimeout) {
            requestTimeoutMillis = Long.MAX_VALUE
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }
}

suspend fun Path.downloadFileFrom(
    url: String,
    headers: Map<String, String> = emptyMap(),
    knownSize: Long = 0L,
    maxAttempts: Int = DEFAULT_DOWNLOAD_ATTEMPTS,
    onProgress: (DownloadProgress) -> Unit
): Result<Path> {
    lgr.info { "Start download file: $url -> $this" }
    if (url.isBlank()) throw IllegalArgumentException("下载链接为空 无法下载到${this}")

    val targetPath = this

    // Ensure parent dir exists
    targetPath.parent?.let { parent ->
        withContext(Dispatchers.IO) {
            if (!Files.exists(parent)) Files.createDirectories(parent)
        }
    }

    return runCatching {
        var lastError: Throwable? = null
        val totalAttempts = maxAttempts.coerceAtLeast(1)
        repeat(totalAttempts) { attemptIndex ->
            val attemptNumber = attemptIndex + 1
            val result = runCatching {
                val tempPath = targetPath.createTempDownloadPath()
                try {
                    val strategy = resolveDownloadStrategy(url, headers, knownSize)
                    when (strategy) {
                        is DownloadStrategy.Single -> {
                            downloadSingleStream(url, tempPath, onProgress, strategy.totalBytesHint, headers)
                        }

                        is DownloadStrategy.MultiRange -> {
                            downloadByRanges(
                                url = url,
                                targetPath = tempPath,
                                totalBytes = strategy.totalBytes,
                                chunks = strategy.chunks,
                                headers = headers,
                                onProgress = onProgress,
                            )
                        }
                    }
                    moveDownloadedFile(tempPath, targetPath)
                    this
                } catch (cancel: CancellationException) {
                    deleteQuietly(tempPath)
                    throw cancel
                } catch (t: Throwable) {
                    deleteQuietly(tempPath)
                    throw t
                }
            }
            if (result.isSuccess) {
                return@runCatching result.getOrThrow()
            }

            lastError = result.exceptionOrNull()
            val shouldRetry = attemptNumber < totalAttempts && lastError.isRetryableDownloadFailure()
            if (shouldRetry) {
                lgr.warn { "Download attempt $attemptNumber/$maxAttempts failed for $url, retrying immediately: ${lastError?.message}" }
            } else {
                throw (lastError ?: IOException("下载失败: $url"))
            }
        }
        throw (lastError ?: IOException("下载失败: $url"))
    }
}

private sealed interface DownloadStrategy {
    data class Single(val totalBytesHint: Long) : DownloadStrategy
    data class MultiRange(
        val totalBytes: Long,
        val chunks: List<DownloadChunk>,
    ) : DownloadStrategy
}

private data class DownloadChunk(
    val index: Int,
    val startInclusive: Long,
    val endInclusive: Long,
) {
    val length: Long
        get() = endInclusive - startInclusive + 1
}

private data class ContentRangeInfo(
    val startInclusive: Long,
    val endInclusive: Long,
    val totalBytes: Long?,
)

private class ProgressReporter(
    private val totalBytes: Long,
    private val onProgress: (DownloadProgress) -> Unit,
) {
    private val downloadedBytes = AtomicLong(0L)
    private val lock = Any()
    private var lastReportTime = System.currentTimeMillis()
    private var lastReportBytes = 0L

    fun addBytes(delta: Int) {
        if (delta <= 0) return
        val total = downloadedBytes.addAndGet(delta.toLong())
        maybeEmit(total, force = false)
    }

    fun finish() {
        val total = downloadedBytes.get()
        synchronized(lock) {
            lastReportTime = System.currentTimeMillis()
            lastReportBytes = total
        }
        onProgress(DownloadProgress(total, totalBytes, 0.0))
    }

    private fun maybeEmit(total: Long, force: Boolean) {
        var progress: DownloadProgress? = null
        synchronized(lock) {
            val now = System.currentTimeMillis()
            if (!force && now - lastReportTime < 500L) return
            val timeDelta = (now - lastReportTime) / 1000.0
            val bytesDelta = total - lastReportBytes
            val speed = if (timeDelta > 0) bytesDelta / timeDelta else 0.0
            progress = DownloadProgress(total, totalBytes, speed)
            lastReportTime = now
            lastReportBytes = total
        }
        progress?.let(onProgress)
    }
}

private suspend fun resolveDownloadStrategy(
    url: String,
    headers: Map<String, String>,
    knownSize: Long,
): DownloadStrategy {
    val sizeHint = fetchContentLength(url, headers) ?: knownSize.takeIf { it > 0L } ?: -1L
    if (sizeHint < MIN_MULTI_PART_DOWNLOAD_BYTES) {
        return DownloadStrategy.Single(sizeHint)
    }

    val rangeProbe = probeRangeSupport(url, headers, sizeHint)
    if (!rangeProbe.supported || rangeProbe.totalBytes <= 0L) {
        return DownloadStrategy.Single(sizeHint)
    }

    return DownloadStrategy.MultiRange(
        totalBytes = rangeProbe.totalBytes,
        chunks = buildDownloadChunks(rangeProbe.totalBytes)
    )
}

private suspend fun fetchContentLength(
    url: String,
    headers: Map<String, String>,
): Long? {
    return try {
        val response = httpFileClient.request(url) {
            method = HttpMethod.Head
            headers.forEach { (key, value) -> header(key, value) }
            header(HttpHeaders.AcceptEncoding, "identity")
        }
        if (!response.status.isSuccess()) return null
        response.contentLength()?.takeIf { size -> size > 0L }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (t: Throwable) {
        lgr.debug(t) { "HEAD probe failed for $url" }
        null
    }
}

private suspend fun probeRangeSupport(
    url: String,
    headers: Map<String, String>,
    sizeHint: Long,
): RangeProbeResult {
    return try {
        httpFileClient.prepareGet(url) {
            headers.forEach { (key, value) -> header(key, value) }
            header(HttpHeaders.AcceptEncoding, "identity")
            header(HttpHeaders.Range, "bytes=0-0")
        }.execute { response ->
            if (response.status != HttpStatusCode.PartialContent) {
                return@execute RangeProbeResult(false, sizeHint)
            }
            val contentRange = parseContentRange(response.headers[HttpHeaders.ContentRange])
                ?: return@execute RangeProbeResult(false, sizeHint)
            if (contentRange.startInclusive != 0L || contentRange.endInclusive != 0L) {
                return@execute RangeProbeResult(false, sizeHint)
            }
            val totalBytes = contentRange.totalBytes ?: sizeHint
            RangeProbeResult(totalBytes > 0L, totalBytes)
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (t: Throwable) {
        lgr.debug(t) { "Range probe failed for $url" }
        RangeProbeResult(false, sizeHint)
    }
}

private data class RangeProbeResult(
    val supported: Boolean,
    val totalBytes: Long,
)

private fun buildDownloadChunks(totalBytes: Long): List<DownloadChunk> {
    val parallelism = ((totalBytes + MIN_BYTES_PER_RANGE - 1) / MIN_BYTES_PER_RANGE)
        .toInt()
        .coerceIn(1, DEFAULT_MAX_RANGE_THREADS)
    if (parallelism <= 1) {
        return listOf(DownloadChunk(0, 0L, totalBytes - 1))
    }

    val chunkSize = (totalBytes + parallelism - 1) / parallelism
    return buildList(parallelism) {
        var start = 0L
        var index = 0
        while (start < totalBytes) {
            val end = (start + chunkSize - 1).coerceAtMost(totalBytes - 1)
            add(DownloadChunk(index = index, startInclusive = start, endInclusive = end))
            start = end + 1
            index++
        }
    }
}

private suspend fun downloadSingleStream(
    url: String,
    targetPath: Path,
    onProgress: (DownloadProgress) -> Unit,
    knownSize: Long = -1L,
    headers: Map<String, String>
): Long {
    return httpFileClient.prepareGet(url) {
        headers.forEach { (key, value) -> header(key, value) }
        header(HttpHeaders.AcceptEncoding, "identity")
    }.execute { response ->
        if (!response.status.isSuccess()) {
            throw IOException("Download failed: ${response.status} for ${url}")
        }

        val totalBytes = response.contentLength() ?: knownSize.takeIf { it > 0L } ?: -1L
        val reporter = ProgressReporter(totalBytes, onProgress)
        val channel: ByteReadChannel = response.body()
        val buffer = ByteArray(8192)
        var bytesDownloaded = 0L

        withContext(Dispatchers.IO) {
            Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .use { outputStream ->
                    var consecutiveZeroReads = 0
                    while (!channel.isClosedForRead) {
                        val bytesRead = withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
                            channel.readAvailable(buffer, 0, buffer.size)
                        } ?: throw IOException("Read timeout - connection stalled")

                        if (bytesRead == -1) break

                        if (bytesRead == 0) {
                            consecutiveZeroReads++
                            if (consecutiveZeroReads >= MAX_CONSECUTIVE_ZERO_READS) {
                                throw IOException("Connection stalled - too many zero reads")
                            }
                            delay(ZERO_READ_BACKOFF_MILLIS)
                            continue
                        }
                        consecutiveZeroReads = 0

                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        reporter.addBytes(bytesRead)
                    }
                }
        }

        if (totalBytes > 0L && bytesDownloaded != totalBytes) {
            throw IOException("Download truncated: expected $totalBytes bytes, got $bytesDownloaded for $url")
        }
        reporter.finish()
        bytesDownloaded
    }
}

private suspend fun downloadByRanges(
    url: String,
    targetPath: Path,
    totalBytes: Long,
    chunks: List<DownloadChunk>,
    headers: Map<String, String>,
    onProgress: (DownloadProgress) -> Unit,
): Long = coroutineScope {
    if (totalBytes <= 0L) {
        throw IOException("Invalid ranged download size for $url: $totalBytes")
    }

    withContext(Dispatchers.IO) {
        RandomAccessFile(targetPath.toFile(), "rw").use { file ->
            file.setLength(totalBytes)
        }
    }

    val reporter = ProgressReporter(totalBytes, onProgress)
    val writtenByChunk = chunks.map { chunk ->
        async {
            downloadRangeChunk(
                url = url,
                targetPath = targetPath,
                expectedTotalBytes = totalBytes,
                chunk = chunk,
                headers = headers,
                reporter = reporter,
            )
        }
    }.awaitAll()

    val totalWritten = writtenByChunk.sum()
    if (totalWritten != totalBytes) {
        throw IOException("Download truncated: expected $totalBytes bytes, got $totalWritten for $url")
    }
    reporter.finish()
    totalWritten
}

private suspend fun downloadRangeChunk(
    url: String,
    targetPath: Path,
    expectedTotalBytes: Long,
    chunk: DownloadChunk,
    headers: Map<String, String>,
    reporter: ProgressReporter,
): Long {
    return httpFileClient.prepareGet(url) {
        headers.forEach { (key, value) -> header(key, value) }
        header(HttpHeaders.AcceptEncoding, "identity")
        header(HttpHeaders.Range, "bytes=${chunk.startInclusive}-${chunk.endInclusive}")
    }.execute { response ->
        if (response.status != HttpStatusCode.PartialContent) {
            throw IOException("Range download failed: expected 206, got ${response.status} for $url")
        }

        val contentRange = parseContentRange(response.headers[HttpHeaders.ContentRange])
            ?: throw IOException("Missing Content-Range for ranged download: $url")
        if (contentRange.startInclusive != chunk.startInclusive || contentRange.endInclusive != chunk.endInclusive) {
            throw IOException(
                "Unexpected Content-Range for $url: expected ${chunk.startInclusive}-${chunk.endInclusive}, " +
                        "got ${contentRange.startInclusive}-${contentRange.endInclusive}"
            )
        }
        if (contentRange.totalBytes != null && contentRange.totalBytes != expectedTotalBytes) {
            throw IOException(
                "Unexpected total size for $url: expected $expectedTotalBytes, got ${contentRange.totalBytes}"
            )
        }

        val channel: ByteReadChannel = response.body()
        val buffer = ByteArray(8192)
        var writePosition = chunk.startInclusive
        var bytesDownloaded = 0L
        var consecutiveZeroReads = 0

        withContext(Dispatchers.IO) {
            FileChannel.open(targetPath, StandardOpenOption.WRITE).use { outputChannel ->
                while (bytesDownloaded < chunk.length) {
                    val bytesRead = withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
                        channel.readAvailable(buffer, 0, buffer.size)
                    } ?: throw IOException("Read timeout - connection stalled")

                    if (bytesRead == -1) break

                    if (bytesRead == 0) {
                        consecutiveZeroReads++
                        if (consecutiveZeroReads >= MAX_CONSECUTIVE_ZERO_READS) {
                            throw IOException("Connection stalled - too many zero reads")
                        }
                        delay(ZERO_READ_BACKOFF_MILLIS)
                        continue
                    }
                    consecutiveZeroReads = 0

                    writeBufferFully(outputChannel, buffer, bytesRead, writePosition)
                    writePosition += bytesRead
                    bytesDownloaded += bytesRead
                    reporter.addBytes(bytesRead)
                }
            }
        }

        if (bytesDownloaded != chunk.length) {
            throw IOException(
                "Range download truncated for $url: expected ${chunk.length} bytes, got $bytesDownloaded on chunk ${chunk.index}"
            )
        }
        bytesDownloaded
    }
}

private fun writeBufferFully(
    channel: FileChannel,
    buffer: ByteArray,
    length: Int,
    startPosition: Long,
) {
    var position = startPosition
    val byteBuffer = ByteBuffer.wrap(buffer, 0, length)
    while (byteBuffer.hasRemaining()) {
        val written = channel.write(byteBuffer, position)
        if (written <= 0) {
            throw IOException("Failed to write downloaded bytes to disk")
        }
        position += written
    }
}

private fun parseContentRange(raw: String?): ContentRangeInfo? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
        .matchEntire(raw.trim())
        ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    val total = match.groupValues[3]
        .takeUnless { it == "*" }
        ?.toLongOrNull()
    return ContentRangeInfo(
        startInclusive = start,
        endInclusive = end,
        totalBytes = total,
    )
}

private fun Path.createTempDownloadPath(): Path {
    val absoluteTarget = toAbsolutePath()
    val parent = absoluteTarget.parent ?: throw IOException("无法确定下载目录: $absoluteTarget")
    val tempName = "${absoluteTarget.fileName}.downloading.${System.nanoTime()}"
    return parent.resolve(tempName)
}

private suspend fun moveDownloadedFile(source: Path, target: Path) {
    withContext(Dispatchers.IO) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

private suspend fun deleteQuietly(path: Path) {
    withContext(Dispatchers.IO) {
        runCatching { Files.deleteIfExists(path) }
    }
}

private fun Throwable?.isRetryableDownloadFailure(): Boolean {
    val error = this ?: return false
    if (error is CancellationException) return false
    return error is IOException
}
