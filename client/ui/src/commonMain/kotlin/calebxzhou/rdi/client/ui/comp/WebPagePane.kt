package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.openUrl
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.ui.PlatformWebView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPagePane(
    url: String,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                CircleIconButton(
                    icon = "\uE89E",
                    tooltip = "浏览器打开",
                    showText = false,
                    onClick = { openUrl(url) }
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            PlatformWebView(
                url = url,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
