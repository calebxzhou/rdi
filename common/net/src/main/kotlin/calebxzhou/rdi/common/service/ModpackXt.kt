package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.common.util.ok
import calebxzhou.rdi.common.util.validateHttpUrl
import calebxzhou.rdi.common.util.validateModpackName
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * calebxzhou @ 2026-03-04 17:01
 */

val List<Modpack.Version>.latest get() = maxBy { it.time }

private val MODPACK_ICON_ALLOWED_DOMAINS = setOf(
    "forgecdn.net",
    "curseforge.com",
    "modrinth.com",
    "xyeidc.com",
    "bbsmc.net",
    "mcmod.cn",
    "candycake.cloud",
    "hdslb.com",
    "bilibili.com",
)

fun Modpack.hasVer(verName: String): Boolean {
    return versions.any { it.name == verName }
}

fun validateIconUrlAddress(iconUrl: String?): Result<Unit> = runCatching {
    if (iconUrl.isNullOrBlank()) return@runCatching
    val uri = iconUrl.trim().validateHttpUrl().getOrThrow()
    val host = uri.host?.lowercase() ?: throw RequestError("图标链接无效")
    if (MODPACK_ICON_ALLOWED_DOMAINS.none { domain ->
            host == domain || host.endsWith(".$domain")
        }
    ) {
        throw RequestError("只接受整合包发布网页的图标链接")
    }
}

suspend fun validateIconUrl(iconUrl: String?): Result<Unit> {
    if (iconUrl.isNullOrBlank()) return ok()
    val trimmed = iconUrl.trim()
    return try {
        validateIconUrlAddress(trimmed).getOrThrow()

        fun isImageType(contentType: String?): Boolean {
            return contentType?.lowercase()?.startsWith("image/") == true
        }

        val contentType = runCatching {
            httpRequest {
                url(trimmed)
                method = HttpMethod.Head
                header(HttpHeaders.AcceptEncoding, "identity")
            }.contentType()?.toString()
        }.getOrElse { cause ->
            if (cause is CancellationException) throw cause
            null
        } ?: runCatching {
            httpRequest {
                url(trimmed)
                method = HttpMethod.Get
                header(HttpHeaders.AcceptEncoding, "identity")
                header(HttpHeaders.Range, "bytes=0-0")
            }.contentType()?.toString()
        }.getOrElse { cause ->
            if (cause is CancellationException) throw cause
            null
        }

        if (contentType == null) {
            throw RequestError("无法获取图标类型")
        }
        if (!isImageType(contentType)) {
            throw RequestError("图标链接不是图片")
        }
        ok()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (cause: Throwable) {
        Result.failure(cause)
    }
}
private fun validateModpackSourceUrl(sourceUrl: String?) : Result<Unit>{
    if (sourceUrl.isNullOrBlank()) return ok()
    val sourceUrl = sourceUrl.trim()

    return ok()
}
suspend fun Modpack.OptionsDto.validate(): Result<Unit>{
    name?.validateModpackName()?.getOrThrow()
    validateIconUrl(iconUrl).getOrThrow()
    validateModpackSourceUrl(sourceUrl)
    if ((categories?.distinct()?.size ?: 0) > Modpack.MAX_CATEGORY_COUNT) {
        throw RequestError("分类最多选择${Modpack.MAX_CATEGORY_COUNT}个")
    }
    return ok()
}
