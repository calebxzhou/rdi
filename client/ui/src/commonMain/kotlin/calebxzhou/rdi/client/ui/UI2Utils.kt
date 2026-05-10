package calebxzhou.rdi.client.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.IconFontFamily
import kotlin.math.min

/**
 * calebxzhou @ 2026-02-15 21:10
 */
val DEFAULT_MODPACK_ICON by lazy { iconBitmap("modpack") }
val DEFAULT_HOST_ICON by lazy { iconBitmap("host") }
typealias M3MaterialTheme = androidx.compose.material3.MaterialTheme

data class TitleTabItem<T>(
    val value: T,
    val icon: String,
    val label: String
)

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

val space8
    get() = Arrangement.spacedBy(8.dp)

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
fun FlowRowV(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable (FlowRowScope.() -> Unit)
) {
    FlowRow(
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

@Composable
fun AlertOk(msg: String, onClose: (() -> Unit)? = null) {
    createAlertDialog(
        title = "成功",
        icon = "\uF058",
        msg = msg,
        accentColor = MaterialColor.GREEN_900.color,
        onClose = onClose
    )
}

@Composable
fun AlertWarn(msg: String, onClose: (() -> Unit)? = null) {
    createAlertDialog(
        title = "警告",
        icon = "\uEA6C",
        msg = msg,
        accentColor = Color(0xFFE0A800),
        onClose = onClose
    )
}

@Composable
fun AlertErr(msg: String, onClose: (() -> Unit)? = null) {
    createAlertDialog(
        title = "错误",
        icon = "\uEA87",
        msg = msg,
        accentColor = Color(0xFFD64545),
        onClose = onClose
    )
}

@Composable
private fun createAlertDialog(
    title: String,
    icon: String,
    msg: String,
    accentColor: Color,
    onClose: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(true) }
    if (!visible) return

    fun close() {
        visible = false
        onClose?.invoke()
    }

    AlertDialog(
        onDismissRequest = ::close,
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
            TextButton(onClick = ::close) {
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
    content: @Composable (Modifier) -> Unit
) {
    val drawButton = @Composable {
        Box(
            modifier = Modifier
                .size(size.dp)
                .graphicsLayer { clip = false },
            contentAlignment = Alignment.Center
        ) {
            content(Modifier.size(size.dp))
        }
    }

    tooltip?.let { tip ->
        SimpleTooltip(tip, tooltipAnchorPosition) { drawButton() }
    } ?: drawButton()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> TitleTabBar(
    items: List<TitleTabItem<T>>,
    selected: T,
    modifier: Modifier = Modifier,
    buttonSize: Int = 34,
    onSelect: (T) -> Unit
) {
    FlowRowV(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            CircleIconButton(
                icon = item.icon,
                tooltip = item.label,
                bgColor = if (selected == item.value) MaterialColor.BLUE_700.color else MaterialColor.GRAY_200.color,
                iconColor = if (selected == item.value) Color.White else MaterialColor.GRAY_900.color,
                size = buttonSize
            ) {
                onSelect(item.value)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleIconButton(
    icon: String,
    tooltip: String? = null,
    tooltipAnchorPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    size: Int = 36,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    bgColor: Color = M3MaterialTheme.colorScheme.primary,
    iconColor: Color = Color.White,
    enabled: Boolean = true,
    showText: Boolean = true,
    onClick: () -> Unit
) {
    val inlineText = tooltip?.takeIf { it.isNotBlank() }
    if (showText && inlineText != null) {
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
                        ScaledCircleIconText(
                            icon = icon,
                            size = size,
                            contentPadding = contentPadding,
                            color = iconColor
                        )
                    }
                }
            }
            if (!showText) {
                SimpleTooltip(inlineText, tooltipAnchorPosition) { drawButton() }
            } else {
                drawButton()
            }
        }
        return
    }

    IconButtonBase(
        tooltip = tooltip,
        tooltipAnchorPosition = tooltipAnchorPosition,
        size = size
    ) { modifier ->
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
            ScaledCircleIconText(
                icon = icon,
                size = size,
                contentPadding = contentPadding,
                color = iconColor
            )
        }
    }
}

@Composable
private fun ScaledCircleIconText(
    icon: String,
    size: Int,
    contentPadding: PaddingValues,
    color: Color
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()
    val annotatedIcon = icon.asIconText
    val baseFontSize = TextUnit(size * 0.5f, TextUnitType.Sp)
    val measured = remember(annotatedIcon, baseFontSize) {
        textMeasurer.measure(
            text = annotatedIcon,
            style = TextStyle(fontSize = baseFontSize),
            maxLines = 1,
            softWrap = false
        ).size
    }
    val availableWidthPx = with(density) {
        val horizontalPadding = contentPadding.calculateStartPadding(layoutDirection) +
                contentPadding.calculateEndPadding(layoutDirection)
        (size.dp - horizontalPadding).roundToPx()
    }.coerceAtLeast(with(density) { (size * 0.45f).dp.roundToPx() })
    val availableHeightPx = with(density) {
        val verticalPadding = contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()
        (size.dp - verticalPadding).roundToPx()
    }.coerceAtLeast(with(density) { (size * 0.45f).dp.roundToPx() })
    val scale = min(
        availableWidthPx / measured.width.toFloat().coerceAtLeast(1f),
        availableHeightPx / measured.height.toFloat().coerceAtLeast(1f)
    ).coerceAtMost(1f)
    val fontSize = TextUnit((size * 0.5f * scale).coerceAtLeast(8f), TextUnitType.Sp)
    Text(
        text = annotatedIcon,
        fontSize = fontSize,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleRow(
    title: String,
    onBack: () -> Unit,
    content: @Composable (RowScope.() -> Unit)
) {
    platformBackHandler {
        onBack.invoke()
    }
    FlowRowV(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FlowRowV {
            CircleIconButton(
                icon = "\uF060",
                tooltip = "返回",
                size = 32,
                showText = false
            ) { onBack.invoke() }

            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.h6
            )
        }
        FlowRowV {
            content()
        }
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleRow2(
    title: String,
    onBack: () -> Unit,
    content: @Composable (RowScope.() -> Unit)
) {
    platformBackHandler {
        onBack.invoke()
    }
    FlowRowV(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FlowRowV {
            CircleIconButton(
                icon = "\uF060",
                tooltip = "返回",
                size = 32,
                showText = false
            ) { onBack.invoke() }
            Space8w()
            Text(
                text = title,
                style = MaterialTheme.typography.h6
            )
        }
        FlowRowV(horizontalArrangement = space8, verticalArrangement = space8) {
            content()
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
fun BoxScope.BottomSnakebarM3(state: androidx.compose.material3.SnackbarHostState) {
    androidx.compose.material3.SnackbarHost(
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
    showText: Boolean = true,
    onClick: () -> Unit = {}
) {
    val inlineText = tooltip?.takeIf { it.isNotBlank() }
    if (showText && inlineText != null) {
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
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = bgColor),
                        enabled = enabled
                    ) {
                        RowV(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.foundation.Image(
                                bitmap = iconBitmap(icon),
                                contentDescription = inlineText,
                                modifier = Modifier.size((size * 2 / 3).dp)
                            )
                            Text(
                                text = inlineText,
                                style = labelStyle,
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
                        contentPadding = contentPadding,
                        colors = ButtonDefaults.buttonColors(backgroundColor = bgColor),
                        enabled = enabled
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = iconBitmap(icon),
                            contentDescription = inlineText,
                            modifier = Modifier.size((size * 2 / 3).dp)
                        )
                    }
                }
            }

            SimpleTooltip(inlineText, tooltipAnchorPosition) { drawButton() }
        }
        return
    }

    if (tooltip != null && !showText) {
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
