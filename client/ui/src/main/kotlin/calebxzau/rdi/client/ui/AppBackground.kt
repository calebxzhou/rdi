package calebxzau.rdi.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import calebxzau.rdi.client.lgr

val LocalAppBackgroundPainter = staticCompositionLocalOf<Painter> {
    error("LocalAppBackgroundPainter is not provided")
}

@Composable
fun AppBackgroundProvider(content: @Composable () -> Unit) {
    val background: Painter = remember {
        loadImageBitmap("assets/bg.avif").fold(
            onSuccess = { BitmapPainter(it) },
            onFailure = {
                lgr.error(it) { "AVIF背景图解码失败，将使用纯白背景" }
                ColorPainter(Color.White)
            }
        )
    }

    CompositionLocalProvider(
        LocalAppBackgroundPainter provides background,
        content = content
    )
}
