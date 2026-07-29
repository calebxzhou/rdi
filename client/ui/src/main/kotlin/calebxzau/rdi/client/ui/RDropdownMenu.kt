package calebxzau.rdi.client.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val RDropdownMenuItemHeight = 30.dp
val OffsetFirstItemUnderCursor get() = DpOffset(
    x = (-16).dp,
    y = -(8.dp + RDropdownMenuItemHeight / 2f)
)
@Composable
fun RDropdownMenuItem(
    text: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = {
            Text(
                text = icon,
                fontSize = 15.sp,
                color = color
            )
        },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(RDropdownMenuItemHeight)
    )
}
