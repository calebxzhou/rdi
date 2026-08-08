package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun CodeEditorVerticalScrollbar(
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
internal fun CodeEditorHorizontalScrollbar(
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
