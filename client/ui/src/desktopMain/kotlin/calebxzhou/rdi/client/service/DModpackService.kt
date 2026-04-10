package calebxzhou.rdi.client.service

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

private const val PNG_COMPRESSION_THRESHOLD_BYTES = 50 * 1024
private const val PNG_COMPRESSION_JPEG_QUALITY = 0.5f

internal actual fun compressPngIfNeededPlatform(bytes: ByteArray): ByteArray {
    if (bytes.size <= PNG_COMPRESSION_THRESHOLD_BYTES) return bytes
    return try {
        val original = ImageIO.read(ByteArrayInputStream(bytes)) ?: return bytes
        val scaled = if (original.height > 720) {
            val targetHeight = 720
            val scale = targetHeight.toDouble() / original.height.toDouble()
            val targetWidth = (original.width * scale).toInt().coerceAtLeast(1)
            val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
            val g = resized.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, resized.width, resized.height)
            g.drawImage(original, 0, 0, targetWidth, targetHeight, null)
            g.dispose()
            resized
        } else {
            original
        }
        val rgbImage = if (scaled.type == BufferedImage.TYPE_INT_RGB) scaled else {
            val converted = BufferedImage(scaled.width, scaled.height, BufferedImage.TYPE_INT_RGB)
            val graphics = converted.createGraphics()
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, converted.width, converted.height)
            graphics.drawImage(scaled, 0, 0, null)
            graphics.dispose()
            converted
        }
        val writerIterator = ImageIO.getImageWritersByFormatName("jpg")
        if (!writerIterator.hasNext()) return bytes
        val writer = writerIterator.next()
        try {
            val params = writer.defaultWriteParam
            if (params.canWriteCompressed()) {
                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = PNG_COMPRESSION_JPEG_QUALITY
            }
            ByteArrayOutputStream().use { baos ->
                val imageOut = ImageIO.createImageOutputStream(baos) ?: return bytes
                imageOut.use { outputStream ->
                    writer.output = outputStream
                    writer.write(null, IIOImage(rgbImage, null, null), params)
                }
                baos.toByteArray()
            }
        } finally {
            writer.dispose()
        }
    } catch (_: Exception) {
        bytes
    }
}
