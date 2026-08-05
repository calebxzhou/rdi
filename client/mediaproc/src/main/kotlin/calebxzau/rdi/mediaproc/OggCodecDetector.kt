package calebxzau.rdi.mediaproc

import java.io.BufferedInputStream

enum class OggAudioCodec {
    OPUS,
    VORBIS,
    UNKNOWN
}

object OggCodecDetector {
    private const val MAX_OGG_PAGE_SIZE = 65_307

    fun detect(input: BufferedInputStream): Result<OggAudioCodec> = runCatching {
        input.mark(MAX_OGG_PAGE_SIZE)
        try {
            val header = input.readNBytes(27)
            if (header.size != 27 || !header.copyOfRange(0, 4).contentEquals("OggS".encodeToByteArray())) {
                return@runCatching OggAudioCodec.UNKNOWN
            }

            val segmentCount = header[26].toInt() and 0xFF
            if (input.readNBytes(segmentCount).size != segmentCount) {
                return@runCatching OggAudioCodec.UNKNOWN
            }

            val identification = input.readNBytes(8)
            when {
                identification.contentEquals("OpusHead".encodeToByteArray()) -> OggAudioCodec.OPUS
                identification.size >= 7 &&
                    identification[0] == 1.toByte() &&
                    identification.copyOfRange(1, 7).contentEquals("vorbis".encodeToByteArray()) -> OggAudioCodec.VORBIS
                else -> OggAudioCodec.UNKNOWN
            }
        } finally {
            input.reset()
        }
    }
}
