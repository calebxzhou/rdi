package calebxzhou.rdi.mc.client.sound

import calebxzau.rdi.mediaproc.FfmpegPcmDecoder
import calebxzau.rdi.mediaproc.OggAudioCodec
import calebxzau.rdi.mediaproc.OggCodecDetector
import net.minecraft.client.sounds.FiniteAudioStream
import net.minecraft.client.sounds.JOrbisAudioStream
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

object RdiSoundStreamFactory {
    private val lgr = LoggerFactory.getLogger(RdiSoundStreamFactory::class.java)

    @JvmStatic
    @Throws(IOException::class)
    fun open(input: InputStream): FiniteAudioStream {
        val buffered = input as? BufferedInputStream ?: BufferedInputStream(input)
        val codec = OggCodecDetector.detect(buffered).getOrElse { error ->
            lgr.error("OGG格式识别失败", error)
            throw IOException("Failed to inspect OGG audio", error)
        }
        if (codec != OggAudioCodec.OPUS) return JOrbisAudioStream(buffered)

        return FfmpegAudioStream(
            FfmpegPcmDecoder.open(buffered).getOrElse { error ->
                lgr.error("Opus OGG初始化失败", error)
                throw IOException("Failed to open Opus OGG audio", error)
            }
        )
    }
}
