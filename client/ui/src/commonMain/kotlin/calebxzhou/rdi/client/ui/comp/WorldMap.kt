package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import calebxzhou.rdi.client.ui.imageBitmapFromArgb
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One-draw-call world map renderer.
 *
 * `pixels` format: ARGB (0xAARRGGBB), row-major, length >= width * height.
 * Use `pixelsVersion` when you mutate the same IntArray instance in-place.
 */
@Composable
fun WorldMap(
    pixels: IntArray,
    mapWidth: Int,
    mapHeight: Int,
    modifier: Modifier = Modifier,
    pixelsVersion: Int = 0,
    minZoom: Float = 1f,
    maxZoom: Float = 64f,
    backgroundColor: Color = Color(0xFFE0E0E0),
    gesturesEnabled: Boolean = true
) {
    if (mapWidth <= 0 || mapHeight <= 0 || pixels.isEmpty()) {
        Box(modifier = modifier.background(backgroundColor))
        return
    }

    val bitmap = remember(mapWidth, mapHeight, pixelsVersion, pixels) {
        imageBitmapFromArgb(pixels, mapWidth, mapHeight)
    }

    var zoom by remember(mapWidth, mapHeight) { mutableStateOf(1f) }
    var pan by remember(mapWidth, mapHeight) { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .background(backgroundColor)
            .then(
                if (!gesturesEnabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(mapWidth, mapHeight, minZoom, maxZoom) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(minZoom, maxZoom)
                            pan += panChange
                        }
                    }
                }
            )
    ) {
        val fit = min(size.width / mapWidth.toFloat(), size.height / mapHeight.toFloat())
        if (fit <= 0f) return@Canvas

        val drawW = (mapWidth * fit * zoom).roundToInt().coerceAtLeast(1)
        val drawH = (mapHeight * fit * zoom).roundToInt().coerceAtLeast(1)
        val drawX = ((size.width - drawW) / 2f + pan.x).roundToInt()
        val drawY = ((size.height - drawH) / 2f + pan.y).roundToInt()

        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(drawX, drawY),
            dstSize = IntSize(drawW, drawH),
            filterQuality = FilterQuality.None
        )
    }
}

@Preview
@Composable
private fun WorldMapPreview() {
    val w = 160
    val h = 120
    val pixels = remember {
        IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            when {
                x % 20 == 0 || y % 20 == 0 -> 0xFF64B5F6.toInt()
                (x - w / 2) * (x - w / 2) + (y - h / 2) * (y - h / 2) < 900 -> 0xFF81C784.toInt()
                else -> 0xFFE0E0E0.toInt()
            }
        }
    }
    WorldMap(
        pixels = pixels,
        mapWidth = w,
        mapHeight = h,
        modifier = Modifier.fillMaxSize()
    )
}
