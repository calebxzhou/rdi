package calebxzau.rdi.client.modcatalog

import calebxzhou.rdi.common.util.digestHex
import calebxzhou.rdi.common.util.sha1dig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

internal data class LocalFileHashes(
    val path: Path,
    val sha1: String,
    val curseForgeFingerprint: Long?
)

internal suspend fun hashLocalFile(path: Path, dispatcher: CoroutineDispatcher): LocalFileHashes =
    withContext(dispatcher) {
        val hashes = hashCatalogFile(path.toString()) { Files.newInputStream(path) }.getOrThrow()
        LocalFileHashes(
            path = path,
            sha1 = hashes.sha1,
            curseForgeFingerprint = hashes.curseForgeFingerprint
        )
    }

suspend fun hashCatalogFile(
    key: String,
    openStream: () -> java.io.InputStream
): Result<CatalogFileHashes> = try {
    val context = currentCoroutineContext()
    val sha1 = sha1dig()
    val fingerprintInput = ByteArrayOutputStream()
    openStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            context.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            sha1.update(buffer, 0, read)
            for (index in 0 until read) {
                val value = buffer[index].toInt() and 0xFF
                if (value != 9 && value != 10 && value != 13 && value != 32) {
                    fingerprintInput.write(value)
                }
            }
        }
    }
    Result.success(CatalogFileHashes(key, sha1.digestHex(), murmur2(fingerprintInput.toByteArray()).toUInt().toLong()))
} catch (cause: kotlinx.coroutines.CancellationException) {
    throw cause
} catch (cause: Throwable) {
    Result.failure(cause)
}

private fun murmur2(data: ByteArray): Int {
    val multiplier = 0x5bd1e995
    var hash = 1 xor data.size
    var index = 0
    var remaining = data.size
    while (remaining >= 4) {
        var value = (data[index].toInt() and 0xff) or
            ((data[index + 1].toInt() and 0xff) shl 8) or
            ((data[index + 2].toInt() and 0xff) shl 16) or
            ((data[index + 3].toInt() and 0xff) shl 24)
        value *= multiplier
        value = value xor (value ushr 24)
        value *= multiplier
        hash *= multiplier
        hash = hash xor value
        index += 4
        remaining -= 4
    }
    when (remaining) {
        3 -> {
            hash = hash xor ((data[index + 2].toInt() and 0xff) shl 16)
            hash = hash xor ((data[index + 1].toInt() and 0xff) shl 8)
            hash = hash xor (data[index].toInt() and 0xff)
            hash *= multiplier
        }

        2 -> {
            hash = hash xor ((data[index + 1].toInt() and 0xff) shl 8)
            hash = hash xor (data[index].toInt() and 0xff)
            hash *= multiplier
        }

        1 -> {
            hash = hash xor (data[index].toInt() and 0xff)
            hash *= multiplier
        }
    }
    hash = hash xor (hash ushr 13)
    hash *= multiplier
    return hash xor (hash ushr 15)
}

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
}
