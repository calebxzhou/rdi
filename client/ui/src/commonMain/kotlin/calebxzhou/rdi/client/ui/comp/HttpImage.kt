package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import calebxzhou.rdi.client.service.HttpImageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val state = produceState(initialValue = HttpImageState.loading(), imgUrl) {
        value = HttpImageState.loading()
        value = withContext(Dispatchers.IO) {
            HttpImageState.fetch(imgUrl)
        }
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
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

