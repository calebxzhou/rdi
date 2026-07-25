package calebxzau.rdi.client.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RVerticalScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier = modifier.fillMaxHeight().padding(vertical = 2.dp),
        style = rScrollbarStyle()
    )
}

@Composable
fun RVerticalScrollbar(gridState: LazyGridState, modifier: Modifier = Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(gridState),
        modifier = modifier.fillMaxHeight().padding(vertical = 2.dp),
        style = rScrollbarStyle()
    )
}

@Composable
fun RVerticalScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier.fillMaxHeight().padding(vertical = 2.dp),
        style = rScrollbarStyle()
    )
}

@Composable
fun RHorizontalScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier) {
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp),
        style = rScrollbarStyle()
    )
}

@Composable
private fun rScrollbarStyle() = defaultScrollbarStyle().copy(
    unhoverColor = MaterialTheme.colorScheme.outlineVariant,
    hoverColor = MaterialTheme.colorScheme.outline
)
