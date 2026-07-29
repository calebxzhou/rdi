package calebxzau.rdi.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * calebxzhou @ 2026-07-29 15:37
 */
@Composable
fun CursorPositionBox(
    modifier: Modifier = Modifier,
    onSecondaryPress: (() -> Unit)? = null,
    cursorContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var cursorPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier.onPointerEvent(PointerEventType.Press) { event ->
            if (event.buttons.isPrimaryPressed || event.buttons.isSecondaryPressed) {
                cursorPosition = event.changes.first().position
            }
            if (event.buttons.isSecondaryPressed && onSecondaryPress != null) {
                onSecondaryPress()
                event.changes.forEach { it.consume() }
            }
        }
    ) {
        content()
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        cursorPosition.x.roundToInt(),
                        cursorPosition.y.roundToInt()
                    )
                }
                .size(1.dp),
            content = cursorContent
        )
    }
}
