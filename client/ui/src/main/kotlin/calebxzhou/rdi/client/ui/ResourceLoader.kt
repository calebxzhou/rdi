package calebxzhou.rdi.client.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

fun iconBitmapPng(icon: String): ImageBitmap {
    val bytes = loadResourceBytes("assets/icons/$icon.png")
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

fun loadResourceBytes(path: String): ByteArray {
    return Thread.currentThread().contextClassLoader
        .getResourceAsStream(path)
        ?.use { it.readBytes() }
        ?: error("Resource not found: $path")
}

fun loadResourceBitmap(path: String): ImageBitmap {
    val bytes = loadResourceBytes(path)
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
