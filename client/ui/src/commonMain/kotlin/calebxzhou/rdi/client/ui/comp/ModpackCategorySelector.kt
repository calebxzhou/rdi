package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.ui.FlowRowV
import calebxzhou.rdi.client.ui.MaterialColor
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Modpack.Category.entries.forEach { category ->
            val isSelected = category in selectedSet
            Surface(
                modifier = Modifier
                    .clickable(enabled = enabled) {
                        val next = if (isSelected) {
                            selected.filterNot { it == category }
                        } else {
                            Modpack.normalizeCategories(selected + category)
                        }
                        onSelectedChange(next)
                    },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialColor.BLUE_700.color else MaterialColor.GRAY_200.color
            ) {
                Text(
                    text = category.label,
                    color = if (isSelected) MaterialColor.WHITE.color else MaterialColor.GRAY_900.color,
                    style = MaterialTheme.typography.body2,
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
            Text(emptyText, color = MaterialColor.GRAY_600.color)
        }
        return
    }
    FlowRowV(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialColor.BLUE_50.color
            ) {
                Text(
                    text = category.label,
                    color = MaterialColor.BLUE_800.color,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}
