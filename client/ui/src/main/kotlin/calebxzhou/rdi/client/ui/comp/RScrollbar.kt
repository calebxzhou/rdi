package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import calebxzau.rdi.client.ui.RHorizontalScrollbar as SharedRHorizontalScrollbar
import calebxzau.rdi.client.ui.RVerticalScrollbar as SharedRVerticalScrollbar

@Composable
fun RVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    SharedRVerticalScrollbar(listState, modifier)
}

@Composable
fun RVerticalScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    SharedRVerticalScrollbar(gridState, modifier)
}

@Composable
fun RVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    SharedRVerticalScrollbar(scrollState, modifier)
}

@Composable
fun RHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    SharedRHorizontalScrollbar(scrollState, modifier)
}
