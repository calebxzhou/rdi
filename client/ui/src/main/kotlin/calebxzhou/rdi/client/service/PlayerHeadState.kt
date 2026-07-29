package calebxzhou.rdi.client.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.bson.types.ObjectId

data class PlayerHeadState(
    val name: String,
    val skinImage: ImageBitmap?
)

@Composable
fun rememberPlayerHeadState(uid: ObjectId): PlayerHeadState {
    val key = uid.toHexString()
    var state by remember(key) {
        mutableStateOf(cachedPlayerHeadState(key) ?: PlayerHeadState("载入中...", null))
    }

    LaunchedEffect(key) {
        val info = playerInfoCache[key]
        val cachedImage = HttpImageState.peek(info.cloth.skin)?.bitmap?.takeIf(ImageBitmap::isPlayerSkin)
        state = PlayerHeadState(info.name, cachedImage)
        if (cachedImage == null) {
            val skinImage = HttpImageState.fetch(info.cloth.skin).bitmap?.takeIf(ImageBitmap::isPlayerSkin)
            state = PlayerHeadState(info.name, skinImage)
        }
    }
    return state
}

private fun cachedPlayerHeadState(key: String): PlayerHeadState? {
    val info = playerInfoCache.peek(key) ?: return null
    return PlayerHeadState(
        name = info.name,
        skinImage = HttpImageState.peek(info.cloth.skin)?.bitmap?.takeIf(ImageBitmap::isPlayerSkin)
    )
}

private fun ImageBitmap.isPlayerSkin() = width >= 64 && height >= 32
