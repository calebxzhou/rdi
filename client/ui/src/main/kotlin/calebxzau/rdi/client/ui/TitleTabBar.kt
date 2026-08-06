package calebxzau.rdi.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp

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
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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