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
import calebxzhou.rdi.client.service.playermodel.DEFAULT_ORBIT_YAW_DEG
import calebxzhou.rdi.client.service.playermodel.PlayerModelRenderService
import calebxzhou.rdi.client.service.playermodel.TemporalAaState
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
    taaLowResThreshold: Int = 1440,
    noControl: Boolean = false
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

    val skinData = remember(skin) { PlayerModelRenderService.buildSkinData(skin) }
    val slim = remember(skinData, isSlim) { isSlim ?: (skinData?.detectSlim() ?: false) }
    val capeData = remember(cape) { PlayerModelRenderService.buildSkinData(cape) }
    val renderer = remember(skinData, capeData, slim, showOuterLayer) {
        PlayerModelRenderService.buildRenderer(skinData, capeData, slim, showOuterLayer)
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
                    imageBitmapFromArgb(
                        PlayerModelRenderService.renderArgb(
                            renderer = renderer,
                            taaState = taaState,
                            width = renderWidth,
                            height = renderHeight,
                            orbitYawDeg = yawDeg,
                            orbitPitchDeg = pitchDeg,
                            walkPhaseDeg = walkDeg,
                            enableTaa = enableTaa,
                            taaLowResThreshold = taaLowResThreshold
                        ),
                        renderWidth,
                        renderHeight
                    )
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
            if (!noControl) {
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
    taaLowResThreshold: Int = 640,
    noControl: Boolean = false
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
        taaLowResThreshold = taaLowResThreshold,
        noControl = noControl
    )
}
