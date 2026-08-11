package calebxzhou.rdi.client.service

import androidx.compose.ui.graphics.ImageBitmap
import calebxzau.rdi.client.lgr
import calebxzau.rdi.client.ui.decodeImageBitmap
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Shared local-first icon resolution used by both rendering and color analysis. */
fun decodeLocalModIcon(iconData: ByteArray?): Result<ImageBitmap?> {
    if (iconData == null) return Result.success(null)
    return decodeImageBitmap(iconData)
}

fun normalizeModIconUrls(iconUrls: List<String>): List<String> = iconUrls
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

suspend fun resolveRemoteModIcon(iconUrls: List<String>): Result<ImageBitmap?> {
    return try {
        val candidates = normalizeModIconUrls(iconUrls)
        for (url in candidates) {
            val state = HttpImageState.peek(url) ?: HttpImageState.fetch(url)
            state.bitmap?.let { return Result.success(it) }
        }
        if (candidates.isEmpty()) {
            Result.success(null)
        } else {
            Result.failure(IOException("所有网络Mod图标加载失败"))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

suspend fun resolveLocalFirstModIcon(
    iconData: ByteArray?,
    iconUrls: List<String>
): Result<ImageBitmap?> {
    return try {
        val localBitmap = decodeLocalModIcon(iconData)
            .getOrElse { error ->
                lgr.warn(error) { "读取本地Mod图标失败，将尝试网络图标" }
                null
            }
        if (localBitmap != null) {
            Result.success(localBitmap)
        } else {
            resolveRemoteModIcon(iconUrls)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
