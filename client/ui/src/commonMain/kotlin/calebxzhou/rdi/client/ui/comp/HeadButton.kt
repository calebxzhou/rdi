package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import calebxzhou.rdi.client.ui.decodeImageBitmap
import androidx.compose.ui.unit.*
import calebxzhou.rdi.client.service.playerInfoCache
import calebxzhou.rdi.client.ui.SimpleTooltip
import calebxzhou.rdi.common.net.httpRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-14 19:53
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadButton(
    uid: ObjectId,
    avatarSize: Dp = 24.dp,
    nameFontSize: TextUnit = TextUnit.Unspecified,
    showName: Boolean = true,
    onClick: () -> Unit = {}
) {
    val paddingSize = 2.dp
    val spacerSize = 6.dp
    val baseTextStyle = MaterialTheme.typography.bodyMedium
    val textStyle = if (nameFontSize == TextUnit.Unspecified) {
        baseTextStyle
    } else {
        baseTextStyle.copy(fontSize = nameFontSize)
    }
    var name by remember { mutableStateOf("载入中...") }
    var skinImage by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uid) {
        withContext(Dispatchers.IO) {
            try {
                val info = playerInfoCache[uid.toHexString()]
                name = info.name
                val response = httpRequest { url(info.cloth.skin) }
                if (response.status.isSuccess()) {
                    val bytes = response.bodyAsBytes()
                    val bitmap = decodeImageBitmap(bytes)
                    if (bitmap.width >= 64 && bitmap.height >= 32) {
                        skinImage = bitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                name = "加载失败"
            }
        }
    }

    val row = @Composable {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = paddingSize, vertical = paddingSize),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(modifier = Modifier.size(avatarSize)) {
                val img = skinImage
                if (img != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width.toInt()
                        val h = size.height.toInt()
                        // Draw Head
                        drawImage(
                            image = img,
                            srcOffset = IntOffset(8, 8),
                            srcSize = IntSize(8, 8),
                            dstSize = IntSize(w, h),
                            filterQuality = FilterQuality.None
                        )
                        // Draw Hat (Overlay)
                        drawImage(
                            image = img,
                            srcOffset = IntOffset(40, 8),
                            srcSize = IntSize(8, 8),
                            dstSize = IntSize(w, h),
                            filterQuality = FilterQuality.None
                        )
                    }
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(color = Color(0xFFA0A0A0))
                    }
                }
            }

            if (showName) {
                Spacer(modifier = Modifier.width(spacerSize))
                Text(text = name, style = textStyle)
            }
        }
    }
    if(showName){
       row ()
    }else{
        SimpleTooltip(name){row()}
    }
}
