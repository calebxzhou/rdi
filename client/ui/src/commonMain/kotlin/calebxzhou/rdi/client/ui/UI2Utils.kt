package calebxzhou.rdi.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import calebxzhou.rdi.client.IconFontFamily
import kotlinx.coroutines.launch

/**
 * calebxzhou @ 2026-02-15 21:10
 */
val DEFAULT_MODPACK_ICON by lazy { iconBitmap("modpack") }
val DEFAULT_HOST_ICON by lazy { iconBitmap("host") }

val Int.wM: Modifier
    get() = Modifier.width(this.dp)

val Int.hM: Modifier
    get() = Modifier.height(this.dp)

@Composable
fun Space8w() {
    Spacer(modifier = Modifier.width(8.dp))
}

@Composable
fun Space24w() {
    Spacer(modifier = Modifier.width(24.dp))
}

@Composable
fun Space8h() {
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun Space24h() {
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
fun MainColumn(content: @Composable (ColumnScope.() -> Unit)) {
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)
    ) { content() }
}

@Composable
fun MainBox(content: @Composable (BoxScope.() -> Unit)) {
    Box(modifier = Modifier.fillMaxSize()) { content() }
}

@Composable
inline fun RowV(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable (RowScope.() -> Unit)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier, horizontalArrangement = horizontalArrangement, content = content
    )
}

@Composable
fun alertOk(msg: String) {
    createAlertDialog(title = "成功", icon = "\uF058", msg = msg, accentColor = MaterialColor.GREEN_900.color)
}

@Composable
fun alertWarn(msg: String) {
    createAlertDialog(title = "警告", icon = "\uEA6C", msg = msg, accentColor = Color(0xFFE0A800))
}

@Composable
fun alertErr(msg: String) {
    createAlertDialog(title = "错误", icon = "\uEA87", msg = msg, accentColor = Color(0xFFD64545))
}

@Composable
private fun createAlertDialog(
    title: String,
    icon: String,
    msg: String,
    accentColor: Color
) {
    var visible by remember { mutableStateOf(true) }
    if (!visible) return

    AlertDialog(
        onDismissRequest = { visible = false },
        title = {
            val titleStyle = MaterialTheme.typography.subtitle1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    color = Color.White,
                    style = titleStyle,
                    fontFamily = IconFontFamily,
                    modifier = Modifier
                        .background(accentColor, CircleShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                        .alignByBaseline()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alignByBaseline()
                )
            }
        },
        text = {
            Text(
                text = msg,
                textAlign = TextAlign.Left,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { visible = false }) {
                Text("明白", color = accentColor)
            }
        },
        backgroundColor = Color(0xFFF1ECF8),
        shape = RoundedCornerShape(28.dp)
    )
}

//pua codepoint识别并应用图标字体
val String.asIconText
    get() = buildAnnotatedString {
        val str = this@asIconText
        var i = 0
        while (i < str.length) {
            val codePoint = str.codePointAt(i)
            val isPua = (codePoint in 0xE000..0xF8FF) ||
                    (codePoint in 0xF0000..0xFFFFD) ||
                    (codePoint in 0x100000..0x10FFFD)

            val charCount = Character.charCount(codePoint)
            if (isPua) {
                withStyle(style = SpanStyle(fontFamily = IconFontFamily)) {
                    append(str.substring(i, i + charCount))
                }
            } else {
                append(str.substring(i, i + charCount))
            }
            i += charCount
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTooltip(
    text: String,
    position: TooltipAnchorPosition = TooltipAnchorPosition.Above,
    content: @Composable (() -> Unit)
) {
    val state = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            position,
            4.dp
        ),
        tooltip = {
            PlainTooltip(
                caretShape = null,
                containerColor = MaterialColor.GRAY_200.color
            ) {
                Text(text, fontSize = TextUnit(12f, TextUnitType.Sp), color = Color.Black)
            }
        },
        state = state
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconButtonBase(
    tooltip: String? = "",
    tooltipAnchorPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    size: Int = 36,
    bgColor: Color = MaterialTheme.colors.primary,
    enabled: Boolean = true,
    longPressDelay: Long = 0L,
    onClick: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var fired by remember { mutableStateOf(false) }
    val ringPadding = 4.dp
    val ringStroke = 3.dp
    val useLongPress = longPressDelay > 0L
    var isPressing by remember { mutableStateOf(false) }

    val pressModifier = if (useLongPress) {
        Modifier.pointerInput(longPressDelay, enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    fired = false
                    isPressing = true
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = longPressDelay.toInt(),
                                easing = LinearEasing
                            )
                        )
                        if (!fired) {
                            fired = true
                            onClick()
                        }
                    }
                    val released = tryAwaitRelease()
                    if (released) {
                        job.cancel()
                        scope.launch { progress.snapTo(0f) }
                    }
                    isPressing = false
                }
            )
        }
    } else {
        Modifier
    }

    val remainingSeconds = ((1f - progress.value) * longPressDelay / 1000f)
    val remainingText = String.format("\uE641 %.1fs", remainingSeconds).asIconText

    val popupAlignment = when (tooltipAnchorPosition) {
        TooltipAnchorPosition.Above -> Alignment.TopCenter
        else -> Alignment.BottomCenter
    }
    val popupOffset = when (tooltipAnchorPosition) {
        TooltipAnchorPosition.Above -> IntOffset(0, -8)
        TooltipAnchorPosition.Below -> IntOffset(0, 40)
        else -> IntOffset(0, 8)
    }

    val drawButton = @Composable {
        Box(
            modifier = Modifier
                .size(size.dp)
                .graphicsLayer { clip = false }
                .drawWithContent {
                    if (useLongPress && progress.value > 0f) {
                        val strokePx = ringStroke.toPx()
                        val ringPaddingPx = ringPadding.toPx()
                        val drawSize = this.size
                        val minDim = minOf(drawSize.width, drawSize.height)
                        val diameter = minDim + ringPaddingPx * 2 - strokePx
                        val arcOffset = -ringPaddingPx + strokePx / 2
                        drawArc(
                            color = bgColor,
                            startAngle = -90f,
                            sweepAngle = progress.value.coerceIn(0f, 1f) * 360f,
                            useCenter = false,
                            topLeft = Offset(arcOffset, arcOffset),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                    drawContent()
                }
                .then(pressModifier),
            contentAlignment = Alignment.Center
        ) {
            content(Modifier.size(size.dp))

            if (useLongPress && isPressing && progress.value > 0f) {
                Popup(
                    alignment = popupAlignment,
                    offset = popupOffset,
                    properties = PopupProperties(focusable = false)
                ) {
                    Surface(
                        color = MaterialColor.GRAY_200.color,
                        shape = RoundedCornerShape(4.dp),
                        elevation = 2.dp
                    ) {
                        Text(
                            text = remainingText,
                            fontSize = TextUnit(12f, TextUnitType.Sp),
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }

    tooltip?.let { tip ->
        SimpleTooltip(tip, tooltipAnchorPosition) { drawButton() }
    } ?: drawButton()
}
@Composable
fun SimpleTextButton(text:String, color: Color = MaterialTheme.colors.primary, onClick: () -> Unit){
    TextButton(onClick,colors = ButtonDefaults.buttonColors(backgroundColor = color)){Text(text)}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleIconButton(
    icon: String,
    tooltip: String? = null,
    tooltipAnchorPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    size: Int = 36,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    bgColor: Color = MaterialTheme.colors.primary,
    iconColor: Color = Color.White,
    enabled: Boolean = true,
    longPressDelay: Long = 0L,
    showText: Boolean = false,
    onClick: () -> Unit
) {
    val inlineText = tooltip?.takeIf { it.isNotBlank() }
    if (showText && longPressDelay <= 0L && inlineText != null) {
        BoxWithConstraints {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val labelStyle = MaterialTheme.typography.body2
            val labelWidthPx = remember(inlineText, labelStyle) {
                textMeasurer.measure(
                    text = AnnotatedString(inlineText),
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false
                ).size.width
            }
            val availableWidthPx = if (maxWidth == Dp.Infinity) {
                Int.MAX_VALUE
            } else {
                with(density) { maxWidth.roundToPx() }
            }
            val iconBoxPx = with(density) { size.dp.roundToPx() }
            val gapPx = with(density) { 8.dp.roundToPx() }
            val horizontalPaddingPx = with(density) { 28.dp.roundToPx() }
            val requiredWidthPx = iconBoxPx + gapPx + labelWidthPx + horizontalPaddingPx
            val canShowInlineText = availableWidthPx >= requiredWidthPx

            val drawButton = @Composable {
                if (canShowInlineText) {
                    TextButton(
                        onClick = onClick,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.height(size.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = bgColor,
                            contentColor = iconColor
                        ),
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                        enabled = enabled
                    ) {
                        RowV(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = icon.asIconText,
                                fontSize = TextUnit(size * 0.5f, TextUnitType.Sp),
                                color = iconColor
                            )
                            Text(
                                text = inlineText,
                                style = labelStyle,
                                color = iconColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                } else {
                    TextButton(
                        onClick = onClick,
                        shape = CircleShape,
                        modifier = Modifier.size(size.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = bgColor,
                            contentColor = iconColor
                        ),
                        contentPadding = contentPadding,
                        enabled = enabled
                    ) {
                        Text(text = icon.asIconText, fontSize = TextUnit(size * 0.5f, TextUnitType.Sp))
                    }
                }
            }

            SimpleTooltip(inlineText, tooltipAnchorPosition) { drawButton() }
        }
        return
    }

    IconButtonBase(
        tooltip = tooltip,
        tooltipAnchorPosition = tooltipAnchorPosition,
        size = size,
        bgColor = bgColor,
        enabled = enabled,
        longPressDelay = longPressDelay,
        onClick = onClick
    ) { modifier ->
        if (longPressDelay > 0L) {
            Box(
                modifier = modifier
                    .background(if (enabled) bgColor else MaterialColor.GRAY_200.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon.asIconText,
                    fontSize = TextUnit(size * 0.5f, TextUnitType.Sp),
                    color = iconColor
                )
            }
        } else {
            TextButton(
                onClick = onClick,
                shape = CircleShape,
                modifier = modifier,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = bgColor,
                    contentColor = iconColor
                ),
                contentPadding = contentPadding,
                enabled = enabled
            ) {
                Text(text = icon.asIconText, fontSize = TextUnit(size * 0.5f, TextUnitType.Sp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleRow(
    title: String,
    onBack: () -> Unit,
    longPressToBack: Boolean = false,
    content: @Composable (RowScope.() -> Unit)
) {
    platformBackHandler {
        onBack.invoke()
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        var leftWidthPx by remember { mutableStateOf(0f) }
        var rightWidthPx by remember { mutableStateOf(0f) }
        val spacingPx = with(density) { 12.dp.toPx() }
        val fitsOneLine = leftWidthPx <= 0f || rightWidthPx <= 0f || (leftWidthPx + spacingPx + rightWidthPx <= maxWidthPx)

        if (fitsOneLine) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onSizeChanged { leftWidthPx = it.width.toFloat() }
                ) {
                    CircleIconButton(
                        icon = "\uF060",
                        tooltip = if (longPressToBack) "长按返回" else "返回",
                        size = 32,
                        longPressDelay = if (longPressToBack) 3000L else 0L
                    ) { onBack.invoke() }

                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.h6
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onSizeChanged { rightWidthPx = it.width.toFloat() }
                ) {
                    content()
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.onSizeChanged { leftWidthPx = it.width.toFloat() }
                    ) {
                        CircleIconButton(
                            icon = "\uF060",
                            tooltip = if (longPressToBack) "长按返回" else "返回",
                            size = 32,
                            longPressDelay = if (longPressToBack) 3000L else 0L
                        ) { onBack.invoke() }

                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.h6
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.onSizeChanged { rightWidthPx = it.width.toFloat() }
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.BottomSnakebar(state: SnackbarHostState) {
    SnackbarHost(
        hostState = state,
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
    )
}

@Composable
fun RowScope.SpacerFullW() {
    Spacer(Modifier.weight(1f))
}

@Composable
fun ErrorText(text: String) {
    Text("\uF467 $text".asIconText, color = MaterialTheme.colors.error)
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定", color = MaterialColor.GREEN_900.color)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageIconButton(
    icon: String,
    tooltip: String? = null,
    tooltipAnchorPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    size: Int = 36,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    bgColor: Color = Color.White,
    enabled: Boolean = true,
    longPressDelay: Long = 0L,
    onClick: () -> Unit = {}
) {
    if (tooltip != null) {
        SimpleTooltip(tooltip, tooltipAnchorPosition) {
            TextButton(
                onClick = onClick,
                shape = CircleShape,
                modifier = Modifier.size(size.dp),
                contentPadding = contentPadding,
                colors = ButtonDefaults.buttonColors(backgroundColor = bgColor),
                enabled = enabled
            ) {
                androidx.compose.foundation.Image(
                    bitmap = iconBitmap(icon),
                    contentDescription = tooltip,
                    modifier = Modifier.size((size * 2 / 3).dp)
                )
            }
        }
    } else {
        TextButton(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier.size(size.dp),
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(backgroundColor = bgColor),
            enabled = enabled
        ) {
            androidx.compose.foundation.Image(
                bitmap = iconBitmap(icon),
                contentDescription = null,
                modifier = Modifier.size((size * 2 / 3).dp)
            )
        }
    }
}
