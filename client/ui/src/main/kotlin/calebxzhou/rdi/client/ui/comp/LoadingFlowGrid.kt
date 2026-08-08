package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.asIconText
import calebxzau.rdi.client.ui.RScrollableColumn


@Composable
fun <T> LoadingFlowGrid(
    loading: Boolean,
    items: List<T>,
    emptyText: String,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 5.dp,
    verticalSpacing: Dp = 5.dp,
    itemContent: @Composable (T) -> Unit
) {
    when {
        loading -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }

        items.isEmpty() -> Text(
            text = emptyText.asIconText,
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        else -> RScrollableColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
            ) {
                items.forEach { itemContent(it) }
            }
        }
    }
}
