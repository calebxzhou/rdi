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
fun rememberPlayerInfoPrefetch(ids: Collection<ObjectId>) {
    val normalizedIds = ids.distinct()
    LaunchedEffect(normalizedIds) {
        playerInfoCache.prefetch(normalizedIds)
    }
}

@Composable
fun rememberPlayerHeadState(uid: ObjectId): PlayerHeadState {
    var state by remember(uid) {
        mutableStateOf(cachedPlayerHeadState(uid) ?: PlayerHeadState("载入中...", null))
    }

    LaunchedEffect(uid) {
        val info = playerInfoCache[uid]
        val cachedImage = HttpImageState.peek(info.cloth.skin)?.bitmap?.takeIf(ImageBitmap::isPlayerSkin)
        state = PlayerHeadState(info.name, cachedImage)
        if (cachedImage == null) {
            val skinImage = HttpImageState.fetch(info.cloth.skin).bitmap?.takeIf(ImageBitmap::isPlayerSkin)
            state = PlayerHeadState(info.name, skinImage)
        }
    }
    return state
}

private fun cachedPlayerHeadState(uid: ObjectId): PlayerHeadState? {
    val info = playerInfoCache.peek(uid) ?: return null
    return PlayerHeadState(
        name = info.name,
        skinImage = HttpImageState.peek(info.cloth.skin)?.bitmap?.takeIf(ImageBitmap::isPlayerSkin)
    )
}

private fun ImageBitmap.isPlayerSkin() = width >= 64 && height >= 32
