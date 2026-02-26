package calebxzhou.rdi.client.ui.comp

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.decodeImageBitmap
import calebxzhou.rdi.client.ui.imageBitmapFromArgb
import calebxzhou.rdi.common.net.httpRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * calebxzhou @ 2026-02-25 18:04
 *
 * Software player model renderer (skinview-like).
 * Input: a standard minecraft skin (64x64 or 64x32).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerModel(
    skin: ImageBitmap?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    autoRotate: Boolean = true,
    animateWalk: Boolean = true,
    showOuterLayer: Boolean = true,
    isSlim: Boolean? = null,
    maxRenderSide: Int = 256
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var orbitYawDeg by remember(skin) { mutableStateOf(0f) }
    var orbitPitchDeg by remember(skin) { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(autoRotate, skin) {
        val speedDegPerSecond = 30f
        var lastFrameNanos = withFrameNanos { it }
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val deltaSec = (nowNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = nowNanos
            if (autoRotate && !dragging) {
                orbitYawDeg += speedDegPerSecond * deltaSec
            }
        }
    }
    val walkPhase = if (animateWalk) {
        rememberInfiniteTransition(label = "player-model-walk").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "player-model-walk-phase"
        ).value
    } else {
        0f
    }

    val skinData = remember(skin) { skin?.let(::SkinData) }
    val slim = remember(skinData, isSlim) { isSlim ?: (skinData?.detectSlim() ?: false) }
    val renderer = remember(skinData, slim, showOuterLayer) {
        skinData?.let { PlayerModelRenderer(it, slim, showOuterLayer) }
    }

    val rendered by produceState<ImageBitmap?>(
        initialValue = null,
        renderer,
        viewport,
        orbitYawDeg,
        orbitPitchDeg,
        walkPhase,
        maxRenderSide
    ) {
        if (renderer == null || viewport.width <= 2 || viewport.height <= 2) {
            value = null
            return@produceState
        }
        val renderCap = max(2, maxRenderSide)
        val renderWidth = viewport.width.coerceIn(2, renderCap)
        val renderHeight = viewport.height.coerceIn(2, renderCap)
        value = withContext(Dispatchers.Default) {
            val pixels = renderer.render(
                width = renderWidth,
                height = renderHeight,
                orbitYawDeg = orbitYawDeg,
                orbitPitchDeg = orbitPitchDeg,
                walkPhaseDeg = walkPhase
            )
            imageBitmapFromArgb(pixels, renderWidth, renderHeight)
        }
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .onSizeChanged { viewport = it }
            .pointerInput(renderer) {
                if (renderer == null) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    orbitYawDeg += dragAmount.x * 0.65f
                    orbitPitchDeg = (orbitPitchDeg - dragAmount.y * 0.35f).coerceIn(-80f, 80f)
                }
            }
    ) {
        if (renderer == null) {
            Text(
                text = "皮肤加载中...",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (rendered != null) {
            Image(
                bitmap = rendered!!,
                contentDescription = "Player Model",
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                CircleIconButton(
                    icon = "\uDB81\uDC50",
                    tooltip = "重置视角",
                    bgColor = MaterialColor.BLUE_900.color,
                    size = 30
                ) {
                    orbitYawDeg = 0f
                    orbitPitchDeg = 0f
                    dragging = false
                }
            }
        }
    }
}

@Composable
fun PlayerModel(
    skinUrl: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    autoRotate: Boolean = true,
    animateWalk: Boolean = true,
    showOuterLayer: Boolean = true,
    isSlim: Boolean? = null,
    maxRenderSide: Int = 256
) {
    val skin = produceState<ImageBitmap?>(initialValue = null, skinUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val response = httpRequest { url(skinUrl) }
                if (!response.status.isSuccess()) return@runCatching null
                decodeImageBitmap(response.bodyAsBytes())
            }.getOrNull()
        }
    }.value
    PlayerModel(
        skin = skin,
        modifier = modifier,
        backgroundColor = backgroundColor,
        autoRotate = autoRotate,
        animateWalk = animateWalk,
        showOuterLayer = showOuterLayer,
        isSlim = isSlim,
        maxRenderSide = maxRenderSide
    )
}

private class SkinData(image: ImageBitmap) {
    val width: Int = image.width
    val height: Int = image.height
    private val argb: IntArray = IntArray(width * height)
    val legacy32: Boolean = height <= 32

    init {
        val pm = image.toPixelMap()
        for (y in 0 until height) {
            for (x in 0 until width) {
                argb[y * width + x] = pm[x, y].toArgb()
            }
        }
    }

    fun detectSlim(): Boolean {
        if (legacy32 || width < 64 || height < 64) return false
        var transparent = 0
        var total = 0
        for (y in 20..31) {
            total++
            if (((pixel(54, y) ushr 24) and 0xFF) < 16) transparent++
        }
        for (y in 52..63) {
            total++
            if (((pixel(46, y) ushr 24) and 0xFF) < 16) transparent++
        }
        return total > 0 && transparent >= total * 0.75f
    }

    fun sample(u: Float, v: Float): Int {
        val x = floor(u).toInt()
        val y = floor(v).toInt()
        return pixel(x, y)
    }

    private fun pixel(x: Int, y: Int): Int {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0
        return argb[y * width + x]
    }
}

private class PlayerModelRenderer(
    private val skin: SkinData,
    isSlim: Boolean,
    showOuterLayer: Boolean
) {
    private val faces: List<Face> = buildFaces(skin.legacy32, isSlim, showOuterLayer)
    private val armPivotX = if (isSlim) 5.5f else 6f

    fun render(
        width: Int,
        height: Int,
        orbitYawDeg: Float,
        orbitPitchDeg: Float,
        walkPhaseDeg: Float
    ): IntArray {
        val out = IntArray(width * height)
        val depth = FloatArray(width * height) { Float.NEGATIVE_INFINITY }
        val camera = OrbitCamera.fromOrbit(
            target = Vec3(0f, 16f, 0f),
            radius = 42f,
            yawDeg = orbitYawDeg,
            pitchDeg = orbitPitchDeg
        )
        val fit = computeProjectionFit(camera, width, height)
        val focal = fit.focal
        val cx = fit.cx
        val cy = fit.cy
        val light = Vec3(0.35f, 0.85f, 0.75f).normalized()
        val walkPhaseRad = Math.toRadians(walkPhaseDeg.toDouble()).toFloat()

        faces.forEach { face ->
            val a0 = animateWalkVertex(face.v0, face.part, armPivotX, walkPhaseRad)
            val a1 = animateWalkVertex(face.v1, face.part, armPivotX, walkPhaseRad)
            val a2 = animateWalkVertex(face.v2, face.part, armPivotX, walkPhaseRad)
            val a3 = animateWalkVertex(face.v3, face.part, armPivotX, walkPhaseRad)

            val p0 = camera.worldToCamera(a0)
            val p1 = camera.worldToCamera(a1)
            val p2 = camera.worldToCamera(a2)
            val p3 = camera.worldToCamera(a3)
            val normal = animateWalkDirection(face.normal, face.part, walkPhaseRad).normalized()

            val shade = (face.baseShade * (0.7f + max(0f, normal.dot(light)) * 0.35f))
                .coerceIn(0.2f, 1.25f)

            val uv = face.uv
            val u0 = if (uv.flipX) uv.u + uv.w else uv.u
            val u1 = if (uv.flipX) uv.u else uv.u + uv.w
            val v0 = if (uv.flipY) uv.v + uv.h else uv.v
            val v1 = if (uv.flipY) uv.v else uv.v + uv.h

            val a = project(p0, u0, v1, focal, cx, cy) ?: return@forEach
            val b = project(p1, u1, v1, focal, cx, cy) ?: return@forEach
            val c = project(p2, u1, v0, focal, cx, cy) ?: return@forEach
            val d = project(p3, u0, v0, focal, cx, cy) ?: return@forEach

            rasterizeTriangle(a, b, c, shade, skin, out, depth, width, height)
            rasterizeTriangle(a, c, d, shade, skin, out, depth, width, height)
        }
        return out
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z
    fun cross(other: Vec3): Vec3 = Vec3(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x
    )
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float): Vec3 = Vec3(x * scale, y * scale, z * scale)
    fun normalized(): Vec3 {
        val len = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        if (len < 1e-6f) return this
        return Vec3(x / len, y / len, z / len)
    }
}

private data class OrbitCamera(
    val position: Vec3,
    val right: Vec3,
    val up: Vec3,
    val forward: Vec3
) {
    fun worldToCamera(world: Vec3): Vec3 {
        val rel = world - position
        return Vec3(
            x = rel.dot(right),
            y = rel.dot(up),
            z = rel.dot(forward)
        )
    }

    companion object {
        fun fromOrbit(target: Vec3, radius: Float, yawDeg: Float, pitchDeg: Float): OrbitCamera {
            val pitch = Math.toRadians(pitchDeg.coerceIn(-85f, 85f).toDouble()).toFloat()
            val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
            val cosPitch = cos(pitch)
            val orbitOffset = Vec3(
                x = sin(yaw) * cosPitch * radius,
                y = sin(pitch) * radius,
                z = -cos(yaw) * cosPitch * radius
            )
            val position = target + orbitOffset
            val forward = (target - position).normalized()
            val worldUp = Vec3(0f, 1f, 0f)
            var right = worldUp.cross(forward)
            if (right.dot(right) < 1e-6f) right = Vec3(1f, 0f, 0f)
            right = right.normalized()
            val up = forward.cross(right).normalized()
            return OrbitCamera(position, right, up, forward)
        }
    }
}

private data class ProjectionFit(
    val focal: Float,
    val cx: Float,
    val cy: Float
)

private fun computeProjectionFit(camera: OrbitCamera, width: Int, height: Int): ProjectionFit {
    val modelBounds = arrayOf(
        Vec3(-8.75f, -0.75f, -4.75f),
        Vec3(-8.75f, -0.75f, 4.75f),
        Vec3(-8.75f, 32.75f, -4.75f),
        Vec3(-8.75f, 32.75f, 4.75f),
        Vec3(8.75f, -0.75f, -4.75f),
        Vec3(8.75f, -0.75f, 4.75f),
        Vec3(8.75f, 32.75f, -4.75f),
        Vec3(8.75f, 32.75f, 4.75f)
    )
    var minNormX = Float.POSITIVE_INFINITY
    var maxNormX = Float.NEGATIVE_INFINITY
    var minNormY = Float.POSITIVE_INFINITY
    var maxNormY = Float.NEGATIVE_INFINITY

    for (corner in modelBounds) {
        val p = camera.worldToCamera(corner)
        if (p.z <= 0.05f) continue
        val normX = p.x / p.z
        val normY = -p.y / p.z
        minNormX = min(minNormX, normX)
        maxNormX = max(maxNormX, normX)
        minNormY = min(minNormY, normY)
        maxNormY = max(maxNormY, normY)
    }

    if (!minNormX.isFinite() || !minNormY.isFinite()) {
        return ProjectionFit(
            focal = min(width, height) * 1.4f,
            cx = width * 0.5f,
            cy = height * 0.5f
        )
    }

    val spanX = (maxNormX - minNormX).coerceAtLeast(1e-4f)
    val spanY = (maxNormY - minNormY).coerceAtLeast(1e-4f)
    val fitRatio = 0.96f
    val focalX = width * fitRatio / spanX
    val focalY = height * fitRatio / spanY
    val focal = min(focalX, focalY)
    val centerNormX = (minNormX + maxNormX) * 0.5f
    val centerNormY = (minNormY + maxNormY) * 0.5f
    val cx = width * 0.5f - focal * centerNormX
    val cy = height * 0.5f - focal * centerNormY
    return ProjectionFit(focal, cx, cy)
}

private fun rotateAroundXAxis(v: Vec3, pivot: Vec3, radians: Float): Vec3 {
    val c = cos(radians)
    val s = sin(radians)
    val dy = v.y - pivot.y
    val dz = v.z - pivot.z
    return Vec3(
        x = v.x,
        y = pivot.y + dy * c - dz * s,
        z = pivot.z + dy * s + dz * c
    )
}

private fun animateWalkVertex(v: Vec3, part: ModelPart, armPivotX: Float, phaseRad: Float): Vec3 {
    val swingRad = sin(phaseRad) * Math.toRadians(20.0).toFloat()
    return when (part) {
        ModelPart.LEFT_ARM -> rotateAroundXAxis(v, Vec3(-armPivotX, 24f, 0f), swingRad)
        ModelPart.RIGHT_ARM -> rotateAroundXAxis(v, Vec3(armPivotX, 24f, 0f), -swingRad)
        ModelPart.LEFT_LEG -> rotateAroundXAxis(v, Vec3(-1.9f, 12f, -0.1f), -swingRad)
        ModelPart.RIGHT_LEG -> rotateAroundXAxis(v, Vec3(1.9f, 12f, -0.1f), swingRad)
        else -> v
    }
}

private fun animateWalkDirection(v: Vec3, part: ModelPart, phaseRad: Float): Vec3 {
    val swingRad = sin(phaseRad) * Math.toRadians(20.0).toFloat()
    return when (part) {
        ModelPart.LEFT_ARM -> rotateAroundXAxis(v, Vec3(0f, 0f, 0f), swingRad)
        ModelPart.RIGHT_ARM -> rotateAroundXAxis(v, Vec3(0f, 0f, 0f), -swingRad)
        ModelPart.LEFT_LEG -> rotateAroundXAxis(v, Vec3(0f, 0f, 0f), -swingRad)
        ModelPart.RIGHT_LEG -> rotateAroundXAxis(v, Vec3(0f, 0f, 0f), swingRad)
        else -> v
    }
}

private data class UvRect(
    val u: Float,
    val v: Float,
    val w: Float,
    val h: Float,
    val flipX: Boolean = false,
    val flipY: Boolean = false
)

private enum class ModelPart {
    HEAD,
    BODY,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_LEG,
    RIGHT_LEG
}

private data class Face(
    val v0: Vec3,
    val v1: Vec3,
    val v2: Vec3,
    val v3: Vec3,
    val normal: Vec3,
    val uv: UvRect,
    val baseShade: Float,
    val part: ModelPart
)

private data class CuboidUv(
    val top: UvRect,
    val bottom: UvRect,
    val left: UvRect,
    val front: UvRect,
    val right: UvRect,
    val back: UvRect
)

private data class ProjVertex(
    val x: Float,
    val y: Float,
    val invZ: Float,
    val uOverZ: Float,
    val vOverZ: Float
)

private fun project(
    p: Vec3,
    u: Float,
    v: Float,
    focal: Float,
    cx: Float,
    cy: Float
): ProjVertex? {
    val depth = p.z
    if (depth <= 0.1f) return null
    val invZ = 1f / depth
    return ProjVertex(
        x = cx + p.x * focal * invZ,
        y = cy - p.y * focal * invZ,
        invZ = invZ,
        uOverZ = u * invZ,
        vOverZ = v * invZ
    )
}

private fun rasterizeTriangle(
    a: ProjVertex,
    b: ProjVertex,
    c: ProjVertex,
    shade: Float,
    skin: SkinData,
    out: IntArray,
    zBuf: FloatArray,
    width: Int,
    height: Int
) {
    val minX = floor(min(a.x, min(b.x, c.x))).toInt().coerceIn(0, width - 1)
    val maxX = ceil(max(a.x, max(b.x, c.x))).toInt().coerceIn(0, width - 1)
    val minY = floor(min(a.y, min(b.y, c.y))).toInt().coerceIn(0, height - 1)
    val maxY = ceil(max(a.y, max(b.y, c.y))).toInt().coerceIn(0, height - 1)
    if (minX > maxX || minY > maxY) return

    val area = edge(a.x, a.y, b.x, b.y, c.x, c.y)
    if (abs(area) < 1e-6f) return
    val invArea = 1f / area

    for (y in minY..maxY) {
        val py = y + 0.5f
        for (x in minX..maxX) {
            val px = x + 0.5f
            val w0 = edge(b.x, b.y, c.x, c.y, px, py) * invArea
            val w1 = edge(c.x, c.y, a.x, a.y, px, py) * invArea
            val w2 = 1f - w0 - w1
            if (w0 < 0f || w1 < 0f || w2 < 0f) continue

            val invZ = w0 * a.invZ + w1 * b.invZ + w2 * c.invZ
            if (invZ <= 0f) continue

            val idx = y * width + x
            if (invZ <= zBuf[idx]) continue

            val u = (w0 * a.uOverZ + w1 * b.uOverZ + w2 * c.uOverZ) / invZ
            val v = (w0 * a.vOverZ + w1 * b.vOverZ + w2 * c.vOverZ) / invZ
            val argb = skin.sample(u, v)
            val alpha = (argb ushr 24) and 0xFF
            if (alpha < 8) continue

            out[idx] = shadeArgb(argb, shade)
            zBuf[idx] = invZ
        }
    }
}

private fun edge(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float =
    (px - ax) * (by - ay) - (py - ay) * (bx - ax)

private fun shadeArgb(color: Int, shade: Float): Int {
    val a = (color ushr 24) and 0xFF
    val r = (((color ushr 16) and 0xFF) * shade).roundToInt().coerceIn(0, 255)
    val g = (((color ushr 8) and 0xFF) * shade).roundToInt().coerceIn(0, 255)
    val b = ((color and 0xFF) * shade).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun buildFaces(
    legacy32: Boolean,
    isSlim: Boolean,
    showOuterLayer: Boolean
): List<Face> {
    val faces = ArrayList<Face>(96)
    val armWidth = if (isSlim) 3 else 4
    val armWidthF = armWidth.toFloat()
    val armX = if (isSlim) 5.5f else 6f

    // Same texture boxes as skinview3d/src/model.ts::setSkinUVs(...)
    addCuboid(
        faces,
        center = Vec3(0f, 28f, 0f),
        size = Vec3(8f, 8f, 8f),
        uv = cubeSkinUv(0, 0, 8, 8, 8),
        part = ModelPart.HEAD
    )
    addCuboid(
        faces,
        center = Vec3(0f, 18f, 0f),
        size = Vec3(8f, 12f, 4f),
        uv = cubeSkinUv(16, 16, 8, 12, 4),
        part = ModelPart.BODY
    )
    addCuboid(
        faces,
        center = Vec3(-armX, 18f, 0f),
        size = Vec3(armWidthF, 12f, 4f),
        uv = cubeSkinUv(40, 16, armWidth, 12, 4),
        part = ModelPart.LEFT_ARM
    )
    addCuboid(
        faces,
        center = Vec3(armX, 18f, 0f),
        size = Vec3(armWidthF, 12f, 4f),
        uv = if (legacy32) cubeSkinUv(40, 16, armWidth, 12, 4) else cubeSkinUv(32, 48, armWidth, 12, 4),
        part = ModelPart.RIGHT_ARM
    )
    addCuboid(
        faces,
        center = Vec3(-1.9f, 6f, -0.1f),
        size = Vec3(4f, 12f, 4f),
        uv = cubeSkinUv(0, 16, 4, 12, 4),
        part = ModelPart.LEFT_LEG
    )
    addCuboid(
        faces,
        center = Vec3(1.9f, 6f, -0.1f),
        size = Vec3(4f, 12f, 4f),
        uv = if (legacy32) cubeSkinUv(0, 16, 4, 12, 4) else cubeSkinUv(16, 48, 4, 12, 4),
        part = ModelPart.RIGHT_LEG
    )

    if (showOuterLayer) {
        addCuboid(
            faces,
            center = Vec3(0f, 28f, 0f),
            size = Vec3(8f, 8f, 8f),
            inflate = 0.5f,
            uv = cubeSkinUv(32, 0, 8, 8, 8),
            part = ModelPart.HEAD
        )
        if (!legacy32) {
            addCuboid(
                faces,
                center = Vec3(0f, 18f, 0f),
                size = Vec3(8f, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(16, 32, 8, 12, 4),
                part = ModelPart.BODY
            )
            addCuboid(
                faces,
                center = Vec3(-armX, 18f, 0f),
                size = Vec3(armWidthF, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(40, 32, armWidth, 12, 4),
                part = ModelPart.LEFT_ARM
            )
            addCuboid(
                faces,
                center = Vec3(armX, 18f, 0f),
                size = Vec3(armWidthF, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(48, 48, armWidth, 12, 4),
                part = ModelPart.RIGHT_ARM
            )
            addCuboid(
                faces,
                center = Vec3(-1.9f, 6f, -0.1f),
                size = Vec3(4f, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(0, 32, 4, 12, 4),
                part = ModelPart.LEFT_LEG
            )
            addCuboid(
                faces,
                center = Vec3(1.9f, 6f, -0.1f),
                size = Vec3(4f, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(0, 48, 4, 12, 4),
                part = ModelPart.RIGHT_LEG
            )
        }
    }
    return faces
}

private fun cubeSkinUv(
    u: Int,
    v: Int,
    width: Int,
    height: Int,
    depth: Int
): CuboidUv {
    return CuboidUv(
        top = uv(u + depth, v, width, depth),
        bottom = uv(u + width + depth, v, width, depth),
        left = uv(u, v + depth, depth, height),
        front = uv(u + depth, v + depth, width, height),
        right = uv(u + width + depth, v + depth, depth, height),
        back = uv(u + width + depth * 2, v + depth, width, height, flipX = true)
    )
}

private fun uv(
    u: Int,
    v: Int,
    w: Int,
    h: Int,
    flipX: Boolean = false,
    flipY: Boolean = false
) = UvRect(u.toFloat(), v.toFloat(), w.toFloat(), h.toFloat(), flipX, flipY)

private fun addCuboid(
    out: MutableList<Face>,
    center: Vec3,
    size: Vec3,
    uv: CuboidUv,
    part: ModelPart,
    inflate: Float = 0f
) {
    val hx = size.x * 0.5f + inflate
    val hy = size.y * 0.5f + inflate
    val hz = size.z * 0.5f + inflate
    val cx = center.x
    val cy = center.y
    val cz = center.z

    val l = cx - hx
    val r = cx + hx
    val d = cy - hy
    val u = cy + hy
    val b = cz - hz
    val f = cz + hz

    out += Face(
        v0 = Vec3(l, d, f), v1 = Vec3(r, d, f), v2 = Vec3(r, u, f), v3 = Vec3(l, u, f),
        normal = Vec3(0f, 0f, 1f), uv = uv.front, baseShade = 1.0f, part = part
    )
    out += Face(
        v0 = Vec3(r, d, b), v1 = Vec3(l, d, b), v2 = Vec3(l, u, b), v3 = Vec3(r, u, b),
        normal = Vec3(0f, 0f, -1f), uv = uv.back, baseShade = 0.82f, part = part
    )
    out += Face(
        v0 = Vec3(l, d, b), v1 = Vec3(l, d, f), v2 = Vec3(l, u, f), v3 = Vec3(l, u, b),
        normal = Vec3(-1f, 0f, 0f), uv = uv.left, baseShade = 0.9f, part = part
    )
    out += Face(
        v0 = Vec3(r, d, f), v1 = Vec3(r, d, b), v2 = Vec3(r, u, b), v3 = Vec3(r, u, f),
        normal = Vec3(1f, 0f, 0f), uv = uv.right, baseShade = 0.78f, part = part
    )
    out += Face(
        v0 = Vec3(l, u, b), v1 = Vec3(r, u, b), v2 = Vec3(r, u, f), v3 = Vec3(l, u, f),
        normal = Vec3(0f, 1f, 0f), uv = uv.top, baseShade = 1.08f, part = part
    )
    out += Face(
        v0 = Vec3(l, d, f), v1 = Vec3(r, d, f), v2 = Vec3(r, d, b), v3 = Vec3(l, d, b),
        normal = Vec3(0f, -1f, 0f), uv = uv.bottom, baseShade = 0.62f, part = part
    )
}
