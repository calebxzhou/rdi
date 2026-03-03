package calebxzhou.rdi.client.ui.comp

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import calebxzhou.rdi.client.graphics.crossed
import calebxzhou.rdi.client.graphics.minus
import calebxzhou.rdi.client.graphics.normalized
import calebxzhou.rdi.client.graphics.plus
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.decodeImageBitmap
import calebxzhou.rdi.client.ui.imageBitmapFromArgb
import calebxzhou.rdi.common.net.httpRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.*

/**
 * calebxzhou @ 2026-02-25 18:04
 *
 * Software player model renderer (skinview-like).
 * Input: a standard minecraft skin (64x64 or 64x32) and optional cape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerModel(
    skin: ImageBitmap?,
    cape: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    autoRotate: Boolean = true,
    animateWalk: Boolean = true,
    showOuterLayer: Boolean = true,
    isSlim: Boolean? = null,
    maxRenderSide: Int = 256,
    enableTaa: Boolean = true,
    taaLowResThreshold: Int = 1440
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var orbitYawDeg by remember(skin, cape) { mutableStateOf(DEFAULT_ORBIT_YAW_DEG) }
    var orbitPitchDeg by remember(skin, cape) { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var autoRotateEnabled by remember(skin, cape, autoRotate) { mutableStateOf(autoRotate) }
    var walkEnabled by remember(skin, cape, animateWalk) { mutableStateOf(animateWalk) }

    LaunchedEffect(autoRotateEnabled, skin, cape) {
        val speedDegPerSecond = 30f
        var lastFrameNanos = withFrameNanos { it }
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val deltaSec = (nowNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = nowNanos
            if (autoRotateEnabled && !dragging) {
                orbitYawDeg += speedDegPerSecond * deltaSec
            }
        }
    }
    val walkPhaseState: State<Float> = if (walkEnabled) {
        rememberInfiniteTransition(label = "player-model-walk").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "player-model-walk-phase"
        )
    } else {
        rememberUpdatedState(0f)
    }

    val skinData = remember(skin) { skin?.let(::SkinData) }
    val slim = remember(skinData, isSlim) { isSlim ?: (skinData?.detectSlim() ?: false) }
    val capeData = remember(cape) { cape?.let(::SkinData) }
    val renderer = remember(skinData, capeData, slim, showOuterLayer) {
        skinData?.let { PlayerModelRenderer(it, capeData, slim, showOuterLayer) }
    }
    val taaState = remember(renderer) { TemporalAaState() }
    var rendered by remember(renderer) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(
        renderer,
        viewport,
        walkEnabled,
        maxRenderSide,
        enableTaa,
        taaLowResThreshold
    ) {
        if (renderer == null || viewport.width <= 2 || viewport.height <= 2) {
            taaState.reset()
            rendered = null
            return@LaunchedEffect
        }
        val renderCap = max(2, maxRenderSide)
        val renderWidth = viewport.width.coerceIn(2, renderCap)
        val renderHeight = viewport.height.coerceIn(2, renderCap)
        snapshotFlow { Triple(orbitYawDeg, orbitPitchDeg, walkPhaseState.value) }
            .conflate()
            .collect { (yawDeg, pitchDeg, walkDeg) ->
                val bitmap = withContext(Dispatchers.Default) {
                    val taaEnabledNow = enableTaa && min(renderWidth, renderHeight) <= max(64, taaLowResThreshold)
                    val passes = if (taaEnabledNow) 2 else 1
                    var resolved: IntArray? = null
                    repeat(passes) {
                        val jitter = if (taaEnabledNow) taaState.nextJitter() else Vector2f(0f, 0f)
                        val current = renderer.render(
                            width = renderWidth,
                            height = renderHeight,
                            orbitYawDeg = yawDeg,
                            orbitPitchDeg = pitchDeg,
                            walkPhaseDeg = walkDeg,
                            jitterX = jitter.x,
                            jitterY = jitter.y
                        )
                        resolved = if (taaEnabledNow) {
                            taaState.resolve(
                                current = current,
                                width = renderWidth,
                                height = renderHeight,
                                yawDeg = yawDeg,
                                pitchDeg = pitchDeg,
                                walkPhaseDeg = walkDeg
                            )
                        } else {
                            current
                        }
                    }
                    if (!taaEnabledNow) taaState.reset()
                    imageBitmapFromArgb(resolved!!, renderWidth, renderHeight)
                }
                rendered = bitmap
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleIconButton(
                        icon = "\uDB83\uDD98",
                        tooltip = if (autoRotateEnabled) "自动旋转：开" else "自动旋转：关",
                        bgColor = if (autoRotateEnabled) MaterialColor.GREEN_700.color else MaterialColor.GRAY_500.color,
                        size = 30,
                        showText = false
                    ) {
                        autoRotateEnabled = !autoRotateEnabled
                    }

                    CircleIconButton(
                        icon = "\uEE1D",
                        tooltip = if (walkEnabled) "走路动画：开" else "走路动画：关",
                        bgColor = if (walkEnabled) MaterialColor.GREEN_700.color else MaterialColor.GRAY_500.color,
                        size = 30,
                        showText = false
                    ) {
                        walkEnabled = !walkEnabled
                    }
                    CircleIconButton(
                        icon = "\uDB81\uDC50",
                        tooltip = "重置视角",
                        bgColor = MaterialColor.BLUE_900.color,
                        size = 30,
                        showText = false
                    ) {
                        orbitYawDeg = DEFAULT_ORBIT_YAW_DEG
                        orbitPitchDeg = 0f
                        dragging = false
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerModel(
    skinUrl: String,
    capeUrl: String? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    autoRotate: Boolean = true,
    animateWalk: Boolean = true,
    showOuterLayer: Boolean = true,
    isSlim: Boolean? = null,
    maxRenderSide: Int = 256,
    enableTaa: Boolean = true,
    taaLowResThreshold: Int = 640
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
    val cape = produceState<ImageBitmap?>(initialValue = null, capeUrl) {
        if (capeUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val response = httpRequest { url(capeUrl) }
                if (!response.status.isSuccess()) return@runCatching null
                decodeImageBitmap(response.bodyAsBytes())
            }.getOrNull()
        }
    }.value
    PlayerModel(
        skin = skin,
        cape = cape,
        modifier = modifier,
        backgroundColor = backgroundColor,
        autoRotate = autoRotate,
        animateWalk = animateWalk,
        showOuterLayer = showOuterLayer,
        isSlim = isSlim,
        maxRenderSide = maxRenderSide,
        enableTaa = enableTaa,
        taaLowResThreshold = taaLowResThreshold
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
    private val cape: SkinData?,
    isSlim: Boolean,
    showOuterLayer: Boolean
) {
    private val faces: List<Face> = buildFaces(skin.legacy32, isSlim, showOuterLayer, cape != null)
    private val armPivotX = if (isSlim) 5.5f else 6f
    private var zBuf: FloatArray = FloatArray(0)

    fun render(
        width: Int,
        height: Int,
        orbitYawDeg: Float,
        orbitPitchDeg: Float,
        walkPhaseDeg: Float,
        jitterX: Float = 0f,
        jitterY: Float = 0f
    ): IntArray {
        val out = IntArray(width * height)
        val depth = acquireDepthBuffer(width * height)
        val camera = OrbitCamera.fromOrbit(
            target = Vector3f(0f, 16f, 0f),
            radius = 42f,
            yawDeg = orbitYawDeg,
            pitchDeg = orbitPitchDeg
        )
        val fit = computeProjectionFit(camera, width, height)
        val focal = fit.focal
        val cx = fit.cx + jitterX
        val cy = fit.cy + jitterY
        val light = Vector3f(0.35f, 0.85f, 0.75f).normalized()
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
            val texture = when (face.texture) {
                TextureSource.SKIN -> skin
                TextureSource.CAPE -> cape ?: return@forEach
            }

            val uv = face.uv
            val u0 = if (uv.flipX) uv.u + uv.w else uv.u
            val u1 = if (uv.flipX) uv.u else uv.u + uv.w
            val v0 = if (uv.flipY) uv.v + uv.h else uv.v
            val v1 = if (uv.flipY) uv.v else uv.v + uv.h

            val a = project(p0, u0, v1, focal, cx, cy) ?: return@forEach
            val b = project(p1, u1, v1, focal, cx, cy) ?: return@forEach
            val c = project(p2, u1, v0, focal, cx, cy) ?: return@forEach
            val d = project(p3, u0, v0, focal, cx, cy) ?: return@forEach

            rasterizeTriangle(a, b, c, shade, texture, out, depth, width, height)
            rasterizeTriangle(a, c, d, shade, texture, out, depth, width, height)
        }
        return out
    }

    private fun acquireDepthBuffer(size: Int): FloatArray {
        if (zBuf.size < size) zBuf = FloatArray(size)
        zBuf.fill(Float.NEGATIVE_INFINITY, 0, size)
        return zBuf
    }
}

private const val DEFAULT_ORBIT_YAW_DEG = 180f

private class TemporalAaState {
    private var width: Int = 0
    private var height: Int = 0
    private var history: IntArray = IntArray(0)
    private var output: IntArray = IntArray(0)
    private var initialized = false
    private var jitterIndex = 0
    private var lastYaw = 0f
    private var lastPitch = 0f
    private var lastWalk = 0f

    fun reset() {
        width = 0
        height = 0
        history = IntArray(0)
        output = IntArray(0)
        initialized = false
        jitterIndex = 0
        lastYaw = 0f
        lastPitch = 0f
        lastWalk = 0f
    }

    fun nextJitter(): Vector2f {
        val jitter = TAA_JITTER_SEQUENCE[jitterIndex % TAA_JITTER_SEQUENCE.size]
        jitterIndex++
        return jitter
    }

    fun resolve(
        current: IntArray,
        width: Int,
        height: Int,
        yawDeg: Float,
        pitchDeg: Float,
        walkPhaseDeg: Float
    ): IntArray {
        ensureBuffers(width, height)
        val yawDelta = wrappedAngleDelta(yawDeg, lastYaw)
        val pitchDelta = abs(pitchDeg - lastPitch)
        val walkDelta = wrappedAngleDelta(walkPhaseDeg, lastWalk)
        val motion = yawDelta * 0.6f + pitchDelta * 0.8f + walkDelta * 0.05f
        val resetHistory = !initialized || motion > 2.5f
        val currentWeight = when {
            resetHistory -> 1f
            motion > 1.25f -> 0.45f
            motion > 0.35f -> 0.30f
            else -> 0.18f
        }
        for (i in current.indices) {
            val blended = if (resetHistory) {
                current[i]
            } else {
                blendArgb(history[i], current[i], currentWeight)
            }
            history[i] = blended
            output[i] = blended
        }
        initialized = true
        lastYaw = yawDeg
        lastPitch = pitchDeg
        lastWalk = walkPhaseDeg
        return output
    }

    private fun ensureBuffers(width: Int, height: Int) {
        if (this.width == width && this.height == height && history.size == width * height) return
        this.width = width
        this.height = height
        history = IntArray(width * height)
        output = IntArray(width * height)
        initialized = false
        jitterIndex = 0
    }
}

private val TAA_JITTER_SEQUENCE = arrayOf(
    Vector2f(0.0f, 0.0f),
    Vector2f(0.25f, -0.25f),
    Vector2f(-0.25f, 0.25f),
    Vector2f(0.375f, 0.125f),
    Vector2f(-0.125f, -0.375f),
    Vector2f(0.125f, 0.375f),
    Vector2f(-0.375f, -0.125f),
    Vector2f(0.5f, 0.5f)
)

private fun wrappedAngleDelta(a: Float, b: Float): Float {
    val diff = (a - b + 540f) % 360f - 180f
    return abs(diff)
}

private fun blendArgb(history: Int, current: Int, currentWeight: Float): Int {
    val t = currentWeight.coerceIn(0f, 1f)
    val inv = 1f - t
    val ha = (history ushr 24) and 0xFF
    val hr = (history ushr 16) and 0xFF
    val hg = (history ushr 8) and 0xFF
    val hb = history and 0xFF
    val ca = (current ushr 24) and 0xFF
    val cr = (current ushr 16) and 0xFF
    val cg = (current ushr 8) and 0xFF
    val cb = current and 0xFF
    val a = (ha * inv + ca * t).roundToInt().coerceIn(0, 255)
    val r = (hr * inv + cr * t).roundToInt().coerceIn(0, 255)
    val g = (hg * inv + cg * t).roundToInt().coerceIn(0, 255)
    val b = (hb * inv + cb * t).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private data class OrbitCamera(
    val position: Vector3f,
    val right: Vector3f,
    val up: Vector3f,
    val forward: Vector3f
) {
    fun worldToCamera(world: Vector3f): Vector3f {
        val rel = world - position
        return Vector3f(rel.dot(right), rel.dot(up), rel.dot(forward))
    }

    companion object {
        fun fromOrbit(target: Vector3f, radius: Float, yawDeg: Float, pitchDeg: Float): OrbitCamera {
            val pitch = Math.toRadians(pitchDeg.coerceIn(-85f, 85f).toDouble()).toFloat()
            val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
            val cosPitch = cos(pitch)
            val orbitOffset = Vector3f(
                sin(yaw) * cosPitch * radius,
                sin(pitch) * radius,
                -cos(yaw) * cosPitch * radius
            )
            val position = target + orbitOffset
            val forward = (target - position).normalized()
            val worldUp = Vector3f(0f, 1f, 0f)
            var right = worldUp.crossed(forward)
            if (right.dot(right) < 1e-6f) right = Vector3f(1f, 0f, 0f)
            right = right.normalized()
            val up = forward.crossed(right).normalized()
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
        Vector3f(-8.75f, -0.75f, -6.9f),
        Vector3f(-8.75f, -0.75f, 4.75f),
        Vector3f(-8.75f, 32.75f, -6.9f),
        Vector3f(-8.75f, 32.75f, 4.75f),
        Vector3f(8.75f, -0.75f, -6.9f),
        Vector3f(8.75f, -0.75f, 4.75f),
        Vector3f(8.75f, 32.75f, -6.9f),
        Vector3f(8.75f, 32.75f, 4.75f)
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

private fun rotateAroundXAxis(v: Vector3f, pivot: Vector3f, radians: Float): Vector3f {
    val c = cos(radians)
    val s = sin(radians)
    val dy = v.y - pivot.y
    val dz = v.z - pivot.z
    return Vector3f(v.x, pivot.y + dy * c - dz * s, pivot.z + dy * s + dz * c)
}

private fun animateWalkVertex(v: Vector3f, part: ModelPart, armPivotX: Float, phaseRad: Float): Vector3f {
    val swingRad = sin(phaseRad) * Math.toRadians(20.0).toFloat()
    val capeSwingRad = Math.toRadians(8.0).toFloat() + sin(phaseRad * 0.5f) * Math.toRadians(4.0).toFloat()
    return when (part) {
        ModelPart.LEFT_ARM -> rotateAroundXAxis(v, Vector3f(-armPivotX, 24f, 0f), swingRad)
        ModelPart.RIGHT_ARM -> rotateAroundXAxis(v, Vector3f(armPivotX, 24f, 0f), -swingRad)
        ModelPart.LEFT_LEG -> rotateAroundXAxis(v, Vector3f(-1.9f, 12f, -0.1f), -swingRad)
        ModelPart.RIGHT_LEG -> rotateAroundXAxis(v, Vector3f(1.9f, 12f, -0.1f), swingRad)
        ModelPart.CAPE -> rotateAroundXAxis(v, Vector3f(0f, 24f, -2.6f), capeSwingRad)
        else -> v
    }
}

private fun animateWalkDirection(v: Vector3f, part: ModelPart, phaseRad: Float): Vector3f {
    val swingRad = sin(phaseRad) * Math.toRadians(20.0).toFloat()
    val capeSwingRad = Math.toRadians(8.0).toFloat() + sin(phaseRad * 0.5f) * Math.toRadians(4.0).toFloat()
    return when (part) {
        ModelPart.LEFT_ARM -> rotateAroundXAxis(v, Vector3f(0f, 0f, 0f), swingRad)
        ModelPart.RIGHT_ARM -> rotateAroundXAxis(v, Vector3f(0f, 0f, 0f), -swingRad)
        ModelPart.LEFT_LEG -> rotateAroundXAxis(v, Vector3f(0f, 0f, 0f), -swingRad)
        ModelPart.RIGHT_LEG -> rotateAroundXAxis(v, Vector3f(0f, 0f, 0f), swingRad)
        ModelPart.CAPE -> rotateAroundXAxis(v, Vector3f(0f, 0f, 0f), capeSwingRad)
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
    RIGHT_LEG,
    CAPE
}

private enum class TextureSource {
    SKIN,
    CAPE
}

private data class Face(
    val v0: Vector3f,
    val v1: Vector3f,
    val v2: Vector3f,
    val v3: Vector3f,
    val normal: Vector3f,
    val uv: UvRect,
    val baseShade: Float,
    val part: ModelPart,
    val texture: TextureSource = TextureSource.SKIN
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
    p: Vector3f,
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
    texture: SkinData,
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
            val argb = texture.sample(u, v)
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
    showOuterLayer: Boolean,
    hasCape: Boolean
): List<Face> {
    val faces = ArrayList<Face>(96)
    val armWidth = if (isSlim) 3 else 4
    val armWidthF = armWidth.toFloat()
    val armX = if (isSlim) 5.5f else 6f

    // Same texture boxes as skinview3d/src/model.ts::setSkinUVs(...)
    addCuboid(
        faces,
        center = Vector3f(0f, 28f, 0f),
        size = Vector3f(8f, 8f, 8f),
        uv = cubeSkinUv(0, 0, 8, 8, 8),
        part = ModelPart.HEAD
    )
    addCuboid(
        faces,
        center = Vector3f(0f, 18f, 0f),
        size = Vector3f(8f, 12f, 4f),
        uv = cubeSkinUv(16, 16, 8, 12, 4),
        part = ModelPart.BODY
    )
    addCuboid(
        faces,
        center = Vector3f(-armX, 18f, 0f),
        size = Vector3f(armWidthF, 12f, 4f),
        uv = cubeSkinUv(40, 16, armWidth, 12, 4),
        part = ModelPart.LEFT_ARM
    )
    addCuboid(
        faces,
        center = Vector3f(armX, 18f, 0f),
        size = Vector3f(armWidthF, 12f, 4f),
        uv = if (legacy32) cubeSkinUv(40, 16, armWidth, 12, 4) else cubeSkinUv(32, 48, armWidth, 12, 4),
        part = ModelPart.RIGHT_ARM
    )
    addCuboid(
        faces,
        center = Vector3f(-1.9f, 6f, -0.1f),
        size = Vector3f(4f, 12f, 4f),
        uv = cubeSkinUv(0, 16, 4, 12, 4),
        part = ModelPart.LEFT_LEG
    )
    addCuboid(
        faces,
        center = Vector3f(1.9f, 6f, -0.1f),
        size = Vector3f(4f, 12f, 4f),
        uv = if (legacy32) cubeSkinUv(0, 16, 4, 12, 4) else cubeSkinUv(16, 48, 4, 12, 4),
        part = ModelPart.RIGHT_LEG
    )

    if (showOuterLayer) {
        addCuboid(
            faces,
            center = Vector3f(0f, 28f, 0f),
            size = Vector3f(8f, 8f, 8f),
            inflate = 0.5f,
            uv = cubeSkinUv(32, 0, 8, 8, 8),
            part = ModelPart.HEAD
        )
        if (!legacy32) {
            addCuboid(
                faces,
                center = Vector3f(0f, 18f, 0f),
                size = Vector3f(8f, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(16, 32, 8, 12, 4),
                part = ModelPart.BODY
            )
            addCuboid(
                faces,
                center = Vector3f(-armX, 18f, 0f),
                size = Vector3f(armWidthF, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(40, 32, armWidth, 12, 4),
                part = ModelPart.LEFT_ARM
            )
            addCuboid(
                faces,
                center = Vector3f(armX, 18f, 0f),
                size = Vector3f(armWidthF, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(48, 48, armWidth, 12, 4),
                part = ModelPart.RIGHT_ARM
            )
            addCuboid(
                faces,
                center = Vector3f(-1.9f, 6f, -0.1f),
                size = Vector3f(4f, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(0, 32, 4, 12, 4),
                part = ModelPart.LEFT_LEG
            )
            addCuboid(
                faces,
                center = Vector3f(1.9f, 6f, -0.1f),
                size = Vector3f(4f, 12f, 4f),
                inflate = 0.25f,
                uv = cubeSkinUv(0, 48, 4, 12, 4),
                part = ModelPart.RIGHT_LEG
            )
        }
    }
    if (hasCape) {
        addCuboid(
            faces,
            center = Vector3f(0f, 16f, -2.6f),
            size = Vector3f(10f, 16f, 1f),
            uv = cubeCapeUv(),
            part = ModelPart.CAPE,
            texture = TextureSource.CAPE
        )
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

private fun cubeCapeUv(): CuboidUv = cubeSkinUv(0, 0, 10, 16, 1)

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
    center: Vector3f,
    size: Vector3f,
    uv: CuboidUv,
    part: ModelPart,
    inflate: Float = 0f,
    texture: TextureSource = TextureSource.SKIN
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
        v0 = Vector3f(l, d, f), v1 = Vector3f(r, d, f), v2 = Vector3f(r, u, f), v3 = Vector3f(l, u, f),
        normal = Vector3f(0f, 0f, 1f), uv = uv.front, baseShade = 1.0f, part = part, texture = texture
    )
    out += Face(
        v0 = Vector3f(r, d, b), v1 = Vector3f(l, d, b), v2 = Vector3f(l, u, b), v3 = Vector3f(r, u, b),
        normal = Vector3f(0f, 0f, -1f), uv = uv.back, baseShade = 0.82f, part = part, texture = texture
    )
    out += Face(
        v0 = Vector3f(l, d, b), v1 = Vector3f(l, d, f), v2 = Vector3f(l, u, f), v3 = Vector3f(l, u, b),
        normal = Vector3f(-1f, 0f, 0f), uv = uv.left, baseShade = 0.9f, part = part, texture = texture
    )
    out += Face(
        v0 = Vector3f(r, d, f), v1 = Vector3f(r, d, b), v2 = Vector3f(r, u, b), v3 = Vector3f(r, u, f),
        normal = Vector3f(1f, 0f, 0f), uv = uv.right, baseShade = 0.78f, part = part, texture = texture
    )
    out += Face(
        v0 = Vector3f(l, u, b), v1 = Vector3f(r, u, b), v2 = Vector3f(r, u, f), v3 = Vector3f(l, u, f),
        normal = Vector3f(0f, 1f, 0f), uv = uv.top, baseShade = 1.08f, part = part, texture = texture
    )
    out += Face(
        v0 = Vector3f(l, d, f), v1 = Vector3f(r, d, f), v2 = Vector3f(r, d, b), v3 = Vector3f(l, d, b),
        normal = Vector3f(0f, -1f, 0f), uv = uv.bottom, baseShade = 0.62f, part = part, texture = texture
    )
}
