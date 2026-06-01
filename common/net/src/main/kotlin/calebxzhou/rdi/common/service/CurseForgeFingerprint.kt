package calebxzhou.rdi.common.service

import java.io.File
import java.nio.file.Path
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlin.io.path.exists
import kotlin.io.path.inputStream

val Path.murmur2: Long
    get() = runCatching { computeCurseForgeFingerprint(this) }
        .getOrElse { if (it is java.io.FileNotFoundException) 0L else throw it }

val File.murmur2: Long
    get() = runCatching { computeCurseForgeFingerprint(toPath()) }
        .getOrElse { if (it is java.io.FileNotFoundException) 0L else throw it }

private fun computeCurseForgeFingerprint(path: Path): Long {
    if (!path.exists()) return 0L
    val normalizedLength = countNormalizedBytes(path)
    if (normalizedLength == 0u) return 0L

    val multiplex = 1540483477u
    var hash = 1u xor normalizedLength
    var block = 0u
    var shift = 0
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    path.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            for (index in 0 until read) {
                val byte = buffer[index]
                if (byte.isCurseForgeWhitespace()) continue

                val value = (byte.toInt() and 0xFF).toUInt()
                block = block or (value shl shift)
                shift += 8

                if (shift == 32) {
                    val mixed = block * multiplex
                    val remixed = (mixed xor (mixed shr 24)) * multiplex
                    hash = hash * multiplex xor remixed
                    block = 0u
                    shift = 0
                }
            }
        }
    }

    if (shift > 0) {
        hash = (hash xor block) * multiplex
    }

    var remixed = (hash xor (hash shr 13)) * multiplex
    remixed = remixed xor (remixed shr 15)
    return remixed.toLong()
}

private fun countNormalizedBytes(path: Path): UInt {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var count = 0u
    path.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            for (index in 0 until read) {
                if (!buffer[index].isCurseForgeWhitespace()) {
                    count += 1u
                }
            }
        }
    }
    return count
}

private fun Byte.isCurseForgeWhitespace(): Boolean = when (toInt() and 0xFF) {
    9, 10, 13, 32 -> true
    else -> false
}
