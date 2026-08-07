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
import calebxzau.rdi.client.lgr
import calebxzau.rdi.client.ui.SimpleTooltip
import calebxzau.rdi.client.ui.loadImageBitmap
import calebxzhou.rdi.client.service.rememberPlayerHeadState
import org.bson.types.ObjectId

private const val STEVE_SKIN_RESOURCE = "assets/skins/steve.png"
private val steveSkinImage: ImageBitmap? by lazy {
    loadImageBitmap(STEVE_SKIN_RESOURCE)
        .onFailure { lgr.warn(it) { "加载Steve皮肤占位图失败" } }
        .getOrNull()
}

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
                PlayerHead(
                    skinImage = skinImage,
                    modifier = Modifier.fillMaxSize()
                )
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

@Composable
fun PlayerHead(
    skinImage: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val image = skinImage ?: steveSkinImage
    Canvas(modifier = modifier) {
        if (image == null) {
            drawRect(color = Color(0xFFA0A0A0))
            return@Canvas
        }

        val dstSize = IntSize(size.width.toInt(), size.height.toInt())
        drawImage(
            image = image,
            srcOffset = IntOffset(8, 8),
            srcSize = IntSize(8, 8),
            dstSize = dstSize,
            filterQuality = FilterQuality.None
        )
        drawImage(
            image = image,
            srcOffset = IntOffset(40, 8),
            srcSize = IntSize(8, 8),
            dstSize = dstSize,
            filterQuality = FilterQuality.None
        )
    }
}
