package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import calebxzhou.rdi.client.service.HttpImageState

/**
 * calebxzhou @ 2026-01-12 23:34
 */
@Composable
fun HttpImage(
    imgUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    val state = produceState(
        initialValue = HttpImageState.peek(imgUrl) ?: HttpImageState.loading(),
        key1 = imgUrl
    ) {
        HttpImageState.peek(imgUrl)?.let { cached ->
            value = cached
            return@produceState
        }
        value = HttpImageState.loading()
        value = HttpImageState.fetch(imgUrl)
    }.value

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.wrapContentSize())
            }
            state.bitmap != null -> {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
            else -> {
                Text(
                    text = state.error ?: "图片加载失败",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

