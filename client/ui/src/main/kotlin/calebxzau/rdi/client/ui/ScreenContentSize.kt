package calebxzau.rdi.client.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ScreenContentSize {
    SMALL,
    MEDIUM,
    LARGE,
    FULL
}

fun BoxScope.screenContentSize(
    size: ScreenContentSize = ScreenContentSize.MEDIUM,
    modifier: Modifier = Modifier
): Modifier =
    modifier.align(Alignment.Center).then(
        when (size) {
            ScreenContentSize.SMALL -> Modifier
                .padding(16.dp)
                .size(width = 480.dp, height = 520.dp)

            ScreenContentSize.MEDIUM -> Modifier
                .padding(16.dp)
                .size(width = 880.dp, height = 540.dp)

            ScreenContentSize.LARGE -> Modifier.fillMaxSize(0.9f)
            ScreenContentSize.FULL -> Modifier.fillMaxSize()
        }
    )
