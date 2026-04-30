package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import calebxzhou.rdi.client.ui.PlatformWebView

@Composable
fun WebPagePane(
    url: String,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        PlatformWebView(
            url = url,
            title = title,
            modifier = Modifier.fillMaxSize()
        )
    }
}
