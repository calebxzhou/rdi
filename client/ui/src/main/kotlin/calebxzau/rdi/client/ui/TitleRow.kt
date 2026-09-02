package calebxzau.rdi.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TitleRow(
    title: String,
    onBack: (() -> Unit)?=null,
    modifier: Modifier =  Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                BackButton(onClick = onBack)
                Box(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }

}

@Composable
private fun BackButton(onClick: () -> Unit) {
    CircleIconButton(
        icon = "\uF060",
        label = "返回",
        size = 36.dp,
        showText = false,
        bgColor = themeNow.surface,
        iconColor = themeNow.primary,
        onClick = onClick
    )
}
