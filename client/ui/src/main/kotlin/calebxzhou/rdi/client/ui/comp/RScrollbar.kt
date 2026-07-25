package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun RVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        style = defaultScrollbarStyle().copy(
            unhoverColor = Color(0xFFAAAAAA),
            hoverColor = Color(0xFFCCCCCC)
        ),
        modifier = modifier
    )
}

@Composable
fun RVerticalScrollbar(
    gridState: LazyGridState,
    modifier: Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(gridState),
        style = defaultScrollbarStyle().copy(
            unhoverColor = Color(0xFFAAAAAA),
            hoverColor = Color(0xFFCCCCCC)
        ),
        modifier = modifier
    )
}

@Composable
fun RVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        style = defaultScrollbarStyle().copy(
            unhoverColor = Color(0xFFAAAAAA),
            hoverColor = Color(0xFFCCCCCC)
        ),
        modifier = modifier
    )
}

@Composable
fun RHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) {
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        style = defaultScrollbarStyle().copy(
            unhoverColor = Color(0xFFAAAAAA),
            hoverColor = Color(0xFFCCCCCC)
        ),
        modifier = modifier
    )
}
