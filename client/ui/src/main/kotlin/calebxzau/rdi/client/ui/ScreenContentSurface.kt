package calebxzau.rdi.client.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.ScreenContentSurface(
    size: ScreenContentSize = ScreenContentSize.MEDIUM,
    modifier: Modifier = Modifier,
    containerAlpha: Float = 0.96f,
    shadowElevation: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = screenContentSize(size, modifier),
        shape = baseRoundCornerShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha),
        shadowElevation = shadowElevation
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}
