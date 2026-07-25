package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import calebxzau.rdi.client.ui.WebView

@Composable
fun WebPagePane(
    url: String,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        WebView(
            url = url,
            title = title,
            modifier = Modifier.fillMaxSize()
        )
    }
}
