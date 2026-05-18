package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.ui.asIconText

@Composable
fun ToggleButton(
    icon: String,
    checked: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val bg = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(999.dp))
            .then(clickModifier),
        color = bg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = icon.asIconText,
                color = fg,
                fontSize = 11.sp
            )
        }
    }
}
