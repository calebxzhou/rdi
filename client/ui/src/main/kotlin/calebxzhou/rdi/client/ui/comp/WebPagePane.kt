package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import calebxzhou.rdi.client.webview2.WebView2

@Composable
fun WebPagePane(
    url: String,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        WebView2(
            url = url,
            title = title,
            modifier = Modifier.fillMaxSize()
        )
    }
}
