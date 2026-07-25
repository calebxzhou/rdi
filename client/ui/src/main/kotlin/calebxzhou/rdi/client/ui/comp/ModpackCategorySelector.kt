package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.FlowRowV
import calebxzhou.rdi.common.model.Modpack

@Composable
fun ModpackCategorySelector(
    selected: List<Modpack.Category>,
    onSelectedChange: (List<Modpack.Category>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selectedSet = selected.toSet()
    FlowRowV(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Modpack.Category.entries.forEach { category ->
            val isSelected = category in selectedSet
            val shape = RoundedCornerShape(16.dp)
            Surface(
                modifier = Modifier
                    .clip(shape)
                    .clickable(enabled = enabled) {
                        val next = if (isSelected) {
                            selected.filterNot { it == category }
                        } else {
                            Modpack.normalizeCategories(selected + category)
                        }
                        onSelectedChange(next)
                    },
                shape = shape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = category.label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ModpackCategoryChips(
    categories: List<Modpack.Category>,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
) {
    if (categories.isEmpty()) {
        if (!emptyText.isNullOrBlank()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    FlowRowV(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = category.label,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}
