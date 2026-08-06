package calebxzau.rdi.client.packproc

import calebxzau.rdi.mediaproc.AvifCodec
import calebxzau.rdi.mediaproc.AvifQuality
import calebxzhou.mykotutils.log.Loggers

private const val PNG_COMPRESSION_THRESHOLD_BYTES = 50 * 1024
private val lgr by Loggers

internal fun processUploadPng(bytes: ByteArray, entryName: String): ByteArray {
    if (bytes.size <= PNG_COMPRESSION_THRESHOLD_BYTES) return bytes
    return AvifCodec.encodePng(bytes, AvifQuality.LOW).getOrElse { error ->
        lgr.error(error) { "PNG转AVIF失败，保留原PNG: $entryName" }
        bytes
    }
}
