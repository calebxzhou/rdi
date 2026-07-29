package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.*
import calebxzau.rdi.client.ui.SimpleTooltip
import calebxzhou.rdi.client.service.rememberPlayerHeadState
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-14 19:53
 */
@Composable
fun HeadButton(
    uid: ObjectId,
    avatarSize: Dp = 24.dp,
    nameFontSize: TextUnit = 14.sp,
    showName: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val state = rememberPlayerHeadState(uid)
    HeadButton(
        name = state.name,
        skinImage = state.skinImage,
        avatarSize = avatarSize,
        nameFontSize = nameFontSize,
        showName = showName,
        onClick = onClick
    )
}

@Composable
fun HeadButton(
    name: String,
    skinImage: ImageBitmap?,
    avatarSize: Dp = 24.dp,
    nameFontSize: TextUnit = 14.sp,
    showName: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val paddingSize = 2.dp
    val spacerSize = 6.dp
    val baseTextStyle = MaterialTheme.typography.bodyMedium
    val textStyle = if (nameFontSize == TextUnit.Unspecified) {
        baseTextStyle
    } else {
        baseTextStyle.copy(fontSize = nameFontSize)
    }
    val row = @Composable {
        Row(
            modifier = Modifier
                .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
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
