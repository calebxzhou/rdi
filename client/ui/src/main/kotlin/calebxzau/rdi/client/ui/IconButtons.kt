package calebxzau.rdi.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.ui.iconBitmapPng


@Composable
fun CircleIconButton(
    icon: String,
    label: String? = null,
    tooltip: String? = label,
    tooltipAnchorPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    size: Dp = 36.dp,
    iconFontScale: Float = 0.5f,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    bgColor: Color = MaterialTheme.colorScheme.primary,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true,
    showText: Boolean = true,
    onClick: () -> Unit
) {
    val inlineLabel = label?.takeIf { it.isNotBlank() }
    val iconFontSize = remember(size, iconFontScale) { (size.value * iconFontScale).sp }

    if (showText && inlineLabel != null) {
        BoxWithConstraints {
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val textMeasurer = rememberTextMeasurer()
            val labelStyle = MaterialTheme.typography.bodyMedium
            val labelWidthPx = remember(inlineLabel, labelStyle) {
                textMeasurer.measure(
                    text = AnnotatedString(inlineLabel),
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
            val iconBoxPx = with(density) { size.roundToPx() }
            val gapPx = with(density) { 8.dp.roundToPx() }
            val horizontalPaddingPx = with(density) {
                val horizontalPadding = contentPadding.calculateStartPadding(layoutDirection) +
                    contentPadding.calculateEndPadding(layoutDirection)
                horizontalPadding.roundToPx()
            }
            val requiredWidthPx = iconBoxPx + gapPx + labelWidthPx + horizontalPaddingPx
            val canShowInlineText = availableWidthPx >= requiredWidthPx

            if (canShowInlineText) {
                TextButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.height(size),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = bgColor,
                        contentColor = iconColor,
                        disabledContainerColor = bgColor.copy(alpha = 0.38f),
                        disabledContentColor = iconColor.copy(alpha = 0.38f)
                    ),
                    contentPadding = contentPadding,
                    enabled = enabled
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconText(icon = icon, fontSize = iconFontSize, color = LocalContentColor.current)
                        Text(
                            text = inlineLabel,
                            style = labelStyle,
                            color = LocalContentColor.current,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            } else {
                IconButtonBase(
                    tooltip = tooltip,
                    tooltipAnchorPosition = tooltipAnchorPosition,
                    size = size
                ) {
                    PureCircleIconButton(
                        icon = icon,
                        size = size,
                        iconFontSize = iconFontSize,
                        bgColor = bgColor,
                        iconColor = iconColor,
                        enabled = enabled,
                        onClick = onClick
                    )
                }
            }
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
            iconFontSize = iconFontSize,
            bgColor = bgColor,
            iconColor = iconColor,
            enabled = enabled,
            onClick = onClick
        )
    }
}


@Composable
private fun IconButtonBase(
    tooltip: String?,
    tooltipAnchorPosition: TooltipAnchorPosition,
    size: Dp,
    content: @Composable () -> Unit
) {
    val drawButton = @Composable {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { clip = false },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }

    if (tooltip.isNullOrBlank()) {
        drawButton()
    } else {
        SimpleTooltip(tooltip, tooltipAnchorPosition) { drawButton() }
    }
}

@Composable
private fun PureCircleIconButton(
    icon: String,
    size: Dp,
    iconFontSize: TextUnit,
    bgColor: Color,
    iconColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) iconColor else iconColor.copy(alpha = 0.38f)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) bgColor else bgColor.copy(alpha = 0.38f),
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            IconText(icon = icon, fontSize = iconFontSize, color = contentColor)
        }
    }
}

@Composable
private fun IconText(
    icon: String,
    fontSize: TextUnit,
    color: Color
) {
    Text(
        text = icon,
        fontFamily = IconFontFamily,
        fontSize = fontSize,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}

@Composable
fun ImageIconButton(
    icon: String,
    tooltip: String? = null,
    tooltipAnchorPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    size: Int = 36,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    bgColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                                bitmap = iconBitmapPng(icon),
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
                bitmap = iconBitmapPng(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size((size * 2 / 3).dp)
            )
        }
    }
}
