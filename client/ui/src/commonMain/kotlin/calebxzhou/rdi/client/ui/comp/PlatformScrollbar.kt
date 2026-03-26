package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific vertical scrollbar for LazyColumn.
 * Desktop: renders a VerticalScrollbar with custom styling.
 * Android: no-op (touch scrolling is native).
 */
@Composable
expect fun PlatformVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
)

@Composable
expect fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
)
