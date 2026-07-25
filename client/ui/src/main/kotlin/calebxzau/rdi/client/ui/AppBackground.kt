package calebxzau.rdi.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter

val LocalAppBackgroundPainter = staticCompositionLocalOf<Painter> {
    error("LocalAppBackgroundPainter is not provided")
}

@Composable
fun AppBackgroundProvider(content: @Composable () -> Unit) {
    val background = remember {
        BitmapPainter(loadImageBitmap("assets/bg1.webp"))
    }

    CompositionLocalProvider(
        LocalAppBackgroundPainter provides background,
        content = content
    )
}
