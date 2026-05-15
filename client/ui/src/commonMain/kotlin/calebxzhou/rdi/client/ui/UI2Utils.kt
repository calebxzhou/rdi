package calebxzhou.rdi.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.IconFontFamily
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField

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

val space16
    get() = Arrangement.spacedBy(16.dp)
const val baseShapeRadius = 24
val roundShape get() = RoundedCornerShape(baseShapeRadius.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RThinTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "",
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    M3OutlinedTextField(
        state = state,
        enabled = enabled,
        lineLimits = lineLimits,
        label = { Text(label, color = Color.Gray) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(baseShapeRadius.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    M3OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        label = {Text(label, color = Color.Gray) },
        shape = RoundedCornerShape(baseShapeRadius.dp),
        leadingIcon=leadingIcon,
        trailingIcon = trailingIcon,
    )
}

@Composable
fun RSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.scale(0.8f)
    )
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
fun RRow(
    modifier: Modifier = Modifier,
    itemVerticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = space8,
    verticalArrangement: Arrangement.Vertical = space8,
    content: @Composable (FlowRowScope.() -> Unit) = {}
){
    FlowRow(
        modifier,
        itemVerticalAlignment = itemVerticalAlignment,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        content = content
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
inline fun RColumn(
    modifier: Modifier = Modifier.padding(24.dp),
    verticalArrangement: Arrangement.Vertical = space16,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier,
        verticalArrangement,
        horizontalAlignment,
        content,
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
            val titleStyle = MaterialTheme.typography.titleMedium
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
        containerColor = Color(0xFFF1ECF8),
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
    buttonSize: Int = 30,
    onSelect: (T) -> Unit
) {
    val containerShape = RoundedCornerShape(percent = 50)
    val tabShape = RoundedCornerShape(percent = 50)
    Surface(
        modifier = modifier.height((buttonSize +12).dp),
        shape = containerShape,
        color = Color(0xFFF6F0F7),
        contentColor = MaterialColor.GRAY_900.color,
        border = BorderStroke(1.dp, MaterialColor.GRAY_200.color),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = selected == item.value
                Surface(
                    onClick = { onSelect(item.value) },
                    modifier = Modifier
                        .widthIn(min = 112.dp)
                        .fillMaxHeight(),
                    shape = tabShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                ) {
                    RowV(
                        modifier = Modifier
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.icon.asIconText,
                            fontSize = TextUnit(buttonSize * 0.52f, TextUnitType.Sp),
                            color = LocalContentColor.current,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                        Space8w()
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
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
            val labelStyle = MaterialTheme.typography.bodyMedium
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
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = bgColor,
                            contentColor = iconColor,
                            disabledContainerColor = bgColor.copy(alpha = 0.38f),
                            disabledContentColor = iconColor.copy(alpha = 0.38f)
                        ),
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                        enabled = enabled,
                    ) {
                        RowV(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = icon.asIconText,
                                fontSize = TextUnit(size * 0.5f, TextUnitType.Sp),
                                color = LocalContentColor.current
                            )
                            Text(
                                text = inlineText,
                                style = labelStyle,
                                color = LocalContentColor.current,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                } else {
                    PureCircleIconButton(
                        icon = icon,
                        size = size,
                        bgColor = bgColor,
                        iconColor = iconColor,
                        enabled = enabled,
                        onClick = onClick
                    )
                }
            }
            drawButton()
        }
        return
    }

    IconButtonBase(
        tooltip = tooltip,
        tooltipAnchorPosition = tooltipAnchorPosition,
        size = size
    ) {
        PureCircleIconButton(
            icon = icon,
            size = size,
            bgColor = bgColor,
            iconColor = iconColor,
            enabled = enabled,
            onClick = onClick
        )
    }
}

@Composable
private fun PureCircleIconButton(
    icon: String,
    size: Int,
    bgColor: Color,
    iconColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) iconColor else iconColor.copy(alpha = 0.38f)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size.dp),
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) bgColor else bgColor.copy(alpha = 0.38f),
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon.asIconText,
                fontSize = TextUnit(size * 0.5f, TextUnitType.Sp),
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleRow(
    title: String,
    onBack: () -> Unit,
    content: @Composable (FlowRowScope.() -> Unit)
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
                style = MaterialTheme.typography.titleLarge
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
    content: @Composable (FlowRowScope.() -> Unit) = {}
) {
    platformBackHandler {
        onBack.invoke()
    }
    RRow (
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RRow {
            CircleIconButton(
                icon = "\uF060",
                tooltip = "返回",
                size = 32,
                showText = false
            ) { onBack.invoke() }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        }
        RRow(horizontalArrangement = space8, verticalArrangement = space8) {
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
fun BoxScope.BottomSnakebarM3(state: SnackbarHostState) {
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
    Text("\uF467 $text".asIconText, color = MaterialTheme.colorScheme.error)
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
        containerColor = MaterialTheme.colorScheme.surface,
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
            val labelStyle = MaterialTheme.typography.bodyMedium
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
                    Surface(
                        onClick = onClick,
                        modifier = Modifier.height(size.dp),
                        enabled = enabled,
                        shape = RoundedCornerShape(percent = 50),
                        color = bgColor,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        RowV(
                            modifier = Modifier.padding(start = 12.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
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
                    PureImageIconButton(
                        icon = icon,
                        contentDescription = inlineText,
                        size = size,
                        contentPadding = contentPadding,
                        bgColor = bgColor,
                        enabled = enabled,
                        onClick = onClick
                    )
                }
            }

            SimpleTooltip(inlineText, tooltipAnchorPosition) { drawButton() }
        }
        return
    }

    if (tooltip != null && !showText) {
        SimpleTooltip(tooltip, tooltipAnchorPosition) {
            PureImageIconButton(
                icon = icon,
                contentDescription = tooltip,
                size = size,
                contentPadding = contentPadding,
                bgColor = bgColor,
                enabled = enabled,
                onClick = onClick
            )
        }
    } else {
        PureImageIconButton(
            icon = icon,
            contentDescription = null,
            size = size,
            contentPadding = contentPadding,
            bgColor = bgColor,
            enabled = enabled,
            onClick = onClick
        )
    }
}

@Composable
private fun PureImageIconButton(
    icon: String,
    contentDescription: String?,
    size: Int,
    contentPadding: PaddingValues,
    bgColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size.dp),
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) bgColor else bgColor.copy(alpha = 0.38f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = iconBitmap(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size((size * 2 / 3).dp)
            )
        }
    }
}
