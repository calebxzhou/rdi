package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.common.util.ok
import calebxzhou.rdi.common.util.validateHttpUrl
import calebxzhou.rdi.common.util.validateName
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import java.net.URI

/**
 * calebxzhou @ 2026-03-04 17:01
 */

val List<Modpack.Version>.latest get() = maxBy { it.time }

fun Modpack.hasVer(verName: String): Boolean {
    return versions.any { it.name == verName }
}

private suspend fun validateModpackIconUrl(iconUrl: String?): Result<Unit> {
    if (iconUrl.isNullOrBlank()) return ok()
    val trimmed = iconUrl.trim()
    val uri = trimmed.validateHttpUrl().getOrThrow()
    val host = uri.host?.lowercase() ?: throw RequestError("图标链接无效")
    val allowedHosts = listOf(
        "forgecdn.net",
        "curseforge.com",
        "modrinth.com",
        "xyeidc.com",
        "bbsmc.net",
        "mcmod.cn"
    )
    val allowed = allowedHosts.any { domain ->
        host == domain || host.endsWith(".$domain")
    }
    if (!allowed) {
        throw RequestError("图标链接域名不受支持")
    }

    fun isImageType(contentType: String?): Boolean {
        return contentType?.lowercase()?.startsWith("image/") == true
    }

    val contentType = runCatching {
        httpRequest {
            url(trimmed)
            method = HttpMethod.Head
            header(HttpHeaders.AcceptEncoding, "identity")
        }.contentType()?.toString()
    }.getOrNull()
        ?: runCatching {
            httpRequest {
                url(trimmed)
                method = HttpMethod.Get
                header(HttpHeaders.AcceptEncoding, "identity")
                header(HttpHeaders.Range, "bytes=0-0")
            }.contentType()?.toString()
        }.getOrNull()

    if (contentType == null) {
        throw RequestError("无法获取图标类型")
    }
    if (!isImageType(contentType)) {
        throw RequestError("图标链接不是图片")
    }
    return ok()
}
private fun validateModpackSourceUrl(sourceUrl: String?) : Result<Unit>{
    if (sourceUrl.isNullOrBlank()) return ok()
    val sourceUrl = sourceUrl.trim()

    return ok()
}
suspend fun Modpack.OptionsDto.validate(): Result<Unit>{
    name?.validateName()
    validateModpackIconUrl(iconUrl)
    validateModpackSourceUrl(sourceUrl)
    if ((categories?.distinct()?.size ?: 0) > Modpack.MAX_CATEGORY_COUNT) {
        throw RequestError("分类最多选择${Modpack.MAX_CATEGORY_COUNT}个")
    }
    return ok()
}
