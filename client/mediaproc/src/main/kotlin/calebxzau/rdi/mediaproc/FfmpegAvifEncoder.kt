package calebxzau.rdi.mediaproc

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_FLAG_GLOBAL_HEADER
import org.bytedeco.ffmpeg.global.avcodec.av_packet_rescale_ts
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_from_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avformat.AVFMT_GLOBALHEADER
import org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2
import org.bytedeco.ffmpeg.global.avformat.avformat_free_context
import org.bytedeco.ffmpeg.global.avformat.avformat_new_stream
import org.bytedeco.ffmpeg.global.avformat.avformat_write_header
import org.bytedeco.ffmpeg.global.avformat.av_interleaved_write_frame
import org.bytedeco.ffmpeg.global.avformat.av_write_trailer
import org.bytedeco.ffmpeg.global.avformat.avio_close_dyn_buf
import org.bytedeco.ffmpeg.global.avformat.avio_open_dyn_buf
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_GRAY10LE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P10LE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV444P10LE
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_JPEG
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_IEC61966_2_1
import org.bytedeco.ffmpeg.global.avutil.AV_OPT_SEARCH_CHILDREN
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_free
import org.bytedeco.ffmpeg.global.avutil.av_opt_set
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.PointerPointer
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal object FfmpegAvifEncoder {
    private const val MAX_HEIGHT = 720
    private const val MAX_PIXELS = 64L * 1024L * 1024L
    private const val COLOR_CRF = 30
    private const val ALPHA_CRF = 0
    private const val CPU_USED = 6
    private const val STILL_FRAME_RATE = 1
    private const val RGBA_CHANNELS = 4
    private const val MAX_8_BIT_SAMPLE = 255
    private const val MAX_10_BIT_SAMPLE = 1023
    private const val SAMPLE_ROUNDING = MAX_8_BIT_SAMPLE / 2

    fun encodePng(
        input: ByteArray,
        quality: AvifQuality = AvifQuality.HIGH,
    ): Result<ByteArray> = runCatching {
        FfmpegAvifNative.requireReady()
        decodePng(input).let { encodeRgba(it, quality) }
    }

    private fun decodePng(input: ByteArray): DecodedRgbaImage {
        val source = ImageIO.read(ByteArrayInputStream(input))
            ?: error("Input is not a decodable PNG image")
        val resized = if (source.height > MAX_HEIGHT) resize(source) else source
        val pixelCount = Math.multiplyExact(resized.width.toLong(), resized.height.toLong())
        require(pixelCount <= MAX_PIXELS) { "PNG dimensions exceed AVIF processing limit" }

        val argb = resized.getRGB(0, 0, resized.width, resized.height, null, 0, resized.width)
        val rgba = ByteArray(Math.multiplyExact(pixelCount.toInt(), RGBA_CHANNELS))
        argb.forEachIndexed { index, pixel ->
            val offset = index * RGBA_CHANNELS
            rgba[offset] = (pixel ushr 16).toByte()
            rgba[offset + 1] = (pixel ushr 8).toByte()
            rgba[offset + 2] = pixel.toByte()
            rgba[offset + 3] = (pixel ushr 24).toByte()
        }
        return DecodedRgbaImage(resized.width, resized.height, rgba)
    }

    private fun resize(source: BufferedImage): BufferedImage {
        val targetHeight = MAX_HEIGHT
        val targetWidth = (source.width.toDouble() * targetHeight / source.height)
            .toInt()
            .coerceAtLeast(1)
        val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB_PRE)
        val graphics = target.createGraphics()
        try {
            graphics.composite = AlphaComposite.Src
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        return target
    }

    private fun encodeRgba(image: DecodedRgbaImage, quality: AvifQuality): ByteArray {
        val hasAlpha = (3 until image.pixels.size step RGBA_CHANNELS)
            .any { image.pixels[it].toInt() and 0xFF != 0xFF }

        val contextPointer = PointerPointer<AVFormatContext>(1L)
        var formatContext: AVFormatContext? = null
        var outputIo: AVIOContext? = null
        var ioPointer: PointerPointer<AVIOContext>? = null
        var outputClosed = false
        var colorEncoder: AVCodecContext? = null
        var alphaEncoder: AVCodecContext? = null
        var outputPointer: PointerPointer<BytePointer>? = null
        var outputBuffer: BytePointer? = null
        try {
            val formatName = BytePointer("avif")
            try {
                FfmpegAvifNative.check(
                    avformat_alloc_output_context2(contextPointer, null, formatName, null),
                    "allocate AVIF output context",
                )
            } finally {
                formatName.close()
            }
            formatContext = contextPointer.get(AVFormatContext::class.java)
                ?: error("FFmpeg returned a null AVIF output context")

            val allocatedIoPointer = PointerPointer<AVIOContext>(1L)
            ioPointer = allocatedIoPointer
            FfmpegAvifNative.check(avio_open_dyn_buf(allocatedIoPointer), "open AVIF memory output")
            outputIo = allocatedIoPointer.get(AVIOContext::class.java)
                ?: error("FFmpeg returned a null AVIF output IO context")
            formatContext.pb(outputIo)

            val color = createEncoder(
                formatContext,
                image.width,
                image.height,
                quality.colorPixelFormat,
                COLOR_CRF,
            )
            colorEncoder = color.context

            val alpha = if (hasAlpha) {
                createEncoder(
                    formatContext,
                    image.width,
                    image.height,
                    AV_PIX_FMT_GRAY10LE,
                    ALPHA_CRF,
                ).also { alphaEncoder = it.context }
            } else {
                null
            }

            FfmpegAvifNative.check(
                avformat_write_header(formatContext, null as PointerPointer<AVDictionary>?),
                "write AVIF header",
            )

            encodeColor(image, color, quality.colorPixelFormat, formatContext)
            if (alpha != null) encodeAlpha(image, alpha, formatContext)

            FfmpegAvifNative.check(av_write_trailer(formatContext), "write AVIF trailer")

            val allocatedOutputIo = checkNotNull(outputIo) {
                "AVIF output IO context was closed unexpectedly"
            }
            val allocatedOutputPointer = PointerPointer<BytePointer>(1L)
            outputPointer = allocatedOutputPointer
            val outputSize = avio_close_dyn_buf(allocatedOutputIo, allocatedOutputPointer)
            outputClosed = true
            outputIo = null
            formatContext.pb(null)
            FfmpegAvifNative.check(outputSize, "close AVIF memory output")
            val output = allocatedOutputPointer.get(BytePointer::class.java)
                ?: error("FFmpeg returned a null AVIF output buffer")
            outputBuffer = output
            try {
                check(outputSize > 0) { "FFmpeg produced an empty AVIF image" }
                val bytes = ByteArray(outputSize)
                output.limit(outputSize.toLong()).get(bytes)
                return bytes
            } finally {
                av_free(outputBuffer)
                outputBuffer = null
            }
        } finally {
            if (!outputClosed && outputIo != null) {
                val discarded = PointerPointer<BytePointer>(1L)
                runCatching { avio_close_dyn_buf(outputIo, discarded) }
                    .onSuccess { discarded.get(BytePointer::class.java)?.let(::av_free) }
                discarded.close()
            }
            outputBuffer?.let { runCatching { av_free(it) } }
            outputPointer?.close()
            ioPointer?.close()
            contextPointer.close()
            colorEncoder?.let { runCatching { avcodec_free_context(it) } }
            alphaEncoder?.let { runCatching { avcodec_free_context(it) } }
            formatContext?.let { runCatching { avformat_free_context(it) } }
        }
    }

    private data class EncoderState(
        val context: AVCodecContext,
        val stream: AVStream,
    )

    private val AvifQuality.colorPixelFormat: Int
        get() = when (this) {
            AvifQuality.HIGH -> AV_PIX_FMT_YUV444P10LE
            AvifQuality.LOW -> AV_PIX_FMT_YUV420P10LE
        }

    private fun createEncoder(
        formatContext: AVFormatContext,
        width: Int,
        height: Int,
        pixelFormat: Int,
        crf: Int,
    ): EncoderState {
        val codec = avcodec_find_encoder_by_name("libaom-av1")
            ?: error("FFmpeg libaom-av1 encoder is unavailable")
        val context = avcodec_alloc_context3(codec)
            ?: error("Unable to allocate libaom-av1 encoder context")
        val timeBase = AVRational().num(STILL_FRAME_RATE).den(STILL_FRAME_RATE)
        try {
            context.width(width)
                .height(height)
                .pix_fmt(pixelFormat)
                .time_base(timeBase)
                .framerate(timeBase)
                .gop_size(1)
                .thread_count(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                .color_range(AVCOL_RANGE_JPEG)
                .color_primaries(AVCOL_PRI_BT709)
                .color_trc(AVCOL_TRC_IEC61966_2_1)
                .colorspace(AVCOL_SPC_BT709)

            setOption(context, "crf", crf.toString())
            setOption(context, "cpu-used", CPU_USED.toString())
            setOption(context, "still-picture", "1")
            setOption(context, "usage", "allintra")
            setOption(context, "row-mt", "1")
            if (formatContext.oformat().flags() and AVFMT_GLOBALHEADER != 0) {
                context.flags(context.flags() or AV_CODEC_FLAG_GLOBAL_HEADER)
            }
            FfmpegAvifNative.check(
                avcodec_open2(context, codec, null as PointerPointer<AVDictionary>?),
                "open libaom-av1 encoder",
            )

            val stream = avformat_new_stream(formatContext, codec)
                ?: error("Unable to allocate AVIF video stream")
            stream.time_base(timeBase)
            FfmpegAvifNative.check(
                avcodec_parameters_from_context(stream.codecpar(), context),
                "copy AV1 encoder parameters",
            )
            return EncoderState(context, stream)
        } catch (error: Throwable) {
            runCatching { avcodec_free_context(context) }
            throw error
        } finally {
            timeBase.close()
        }
    }

    private fun setOption(context: AVCodecContext, name: String, value: String) {
        FfmpegAvifNative.check(
            av_opt_set(context, name, value, AV_OPT_SEARCH_CHILDREN),
            "set libaom option $name",
        )
    }

    private fun encodeColor(
        image: DecodedRgbaImage,
        encoder: EncoderState,
        pixelFormat: Int,
        formatContext: AVFormatContext,
    ) {
        val source = allocateFrame(AV_PIX_FMT_RGBA, image.width, image.height)
        val converted = allocateFrame(pixelFormat, image.width, image.height)
        var scaler: SwsContext? = null
        try {
            copyPackedRows(source, image.pixels, image.width * RGBA_CHANNELS)
            scaler = sws_getContext(
                image.width,
                image.height,
                AV_PIX_FMT_RGBA,
                image.width,
                image.height,
                pixelFormat,
                SWS_BILINEAR,
                null as org.bytedeco.ffmpeg.swscale.SwsFilter?,
                null as org.bytedeco.ffmpeg.swscale.SwsFilter?,
                null as DoublePointer?,
            ) ?: error("Unable to allocate RGBA to 10-bit YUV converter")
            check(
                sws_scale(
                    scaler,
                    source.data(),
                    source.linesize(),
                    0,
                    image.height,
                    converted.data(),
                    converted.linesize(),
                ) == image.height,
            ) { "FFmpeg failed to convert RGBA to 10-bit YUV" }
            converted.pts(0)
            encodeFrame(encoder, converted, formatContext)
            flushEncoder(encoder, formatContext)
        } finally {
            scaler?.let { sws_freeContext(it) }
            av_frame_free(converted)
            av_frame_free(source)
        }
    }

    private fun encodeAlpha(
        image: DecodedRgbaImage,
        encoder: EncoderState,
        formatContext: AVFormatContext,
    ) {
        val alpha = allocateFrame(AV_PIX_FMT_GRAY10LE, image.width, image.height)
        try {
            repeat(image.height) { y ->
                val rowOffset = y.toLong() * alpha.linesize(0)
                repeat(image.width) { x ->
                    val alpha8 = image.pixels[(y * image.width + x) * RGBA_CHANNELS + 3].toInt() and
                        MAX_8_BIT_SAMPLE
                    val alpha10 = (alpha8 * MAX_10_BIT_SAMPLE + SAMPLE_ROUNDING) / MAX_8_BIT_SAMPLE
                    val sampleOffset = rowOffset + x * 2L
                    alpha.data(0).put(sampleOffset, (alpha10 and 0xFF).toByte())
                    alpha.data(0).put(sampleOffset + 1, (alpha10 ushr 8).toByte())
                }
            }
            alpha.pts(0)
            encodeFrame(encoder, alpha, formatContext)
            flushEncoder(encoder, formatContext)
        } finally {
            av_frame_free(alpha)
        }
    }

    private fun allocateFrame(pixelFormat: Int, width: Int, height: Int): AVFrame {
        val frame = av_frame_alloc() ?: error("Unable to allocate FFmpeg frame")
        try {
            frame.format(pixelFormat).width(width).height(height)
            FfmpegAvifNative.check(av_frame_get_buffer(frame, 32), "allocate FFmpeg frame buffer")
            return frame
        } catch (error: Throwable) {
            av_frame_free(frame)
            throw error
        }
    }

    private fun copyPackedRows(frame: AVFrame, pixels: ByteArray, rowBytes: Int) {
        repeat(frame.height()) { row ->
            frame.data(0).getPointer(row.toLong() * frame.linesize(0))
                .put(pixels, row * rowBytes, rowBytes)
        }
    }

    private fun encodeFrame(
        encoder: EncoderState,
        frame: AVFrame,
        formatContext: AVFormatContext,
    ) {
        FfmpegAvifNative.check(
            avcodec_send_frame(encoder.context, frame),
            "send AVIF frame",
        )
        drainPackets(encoder, formatContext)
    }

    private fun flushEncoder(encoder: EncoderState, formatContext: AVFormatContext) {
        FfmpegAvifNative.check(
            avcodec_send_frame(encoder.context, null),
            "flush AVIF encoder",
        )
        drainPackets(encoder, formatContext)
    }

    private fun drainPackets(encoder: EncoderState, formatContext: AVFormatContext) {
        val packet = AVPacket()
        try {
            while (true) {
                val result = avcodec_receive_packet(encoder.context, packet)
                if (FfmpegAvifNative.isAgain(result)) break
                if (result == org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF()) break
                FfmpegAvifNative.check(result, "receive AVIF packet")
                av_packet_rescale_ts(packet, encoder.context.time_base(), encoder.stream.time_base())
                packet.stream_index(encoder.stream.index())
                FfmpegAvifNative.check(
                    av_interleaved_write_frame(formatContext, packet),
                    "write AVIF packet",
                )
                av_packet_unref(packet)
            }
        } finally {
            av_packet_unref(packet)
            packet.close()
        }
    }
}
