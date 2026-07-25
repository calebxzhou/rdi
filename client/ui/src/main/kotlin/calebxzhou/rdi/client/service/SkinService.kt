package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.BSSkin
import calebxzhou.rdi.client.model.BSSkinData
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.MojangApi
import calebxzhou.rdi.common.service.MojangApi.textures
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

object SkinService {
    suspend fun applyBlessingSkin(
        urlPrefix: String,
        skinData: BSSkinData
    ): Result<RAccount.Cloth> = runCatching {
        val response = httpRequest {
            url("$urlPrefix/texture/${skinData.tid}")
        }
        if (!response.status.isSuccess()) {
            throw RequestError("获取皮肤数据失败: ${response.bodyAsText()}")
        }
        val skin = serdesJson.decodeFromString<BSSkin>(response.bodyAsText())
        val newCloth = loggedAccount.cloth.copy()
        if (skinData.isCape) {
            newCloth.cape = "$urlPrefix/textures/${skin.hash}"
        } else {
            newCloth.isSlim = skinData.isSlim
            newCloth.skin = "$urlPrefix/textures/${skin.hash}"
        }
        PlayerService.setCloth(newCloth).getOrThrow()
        newCloth
    }

    suspend fun importMojangSkin(
        name: String,
        importSkin: Boolean,
        importCape: Boolean
    ): Result<RAccount.Cloth> = runCatching {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw RequestError("请输入玩家名")
        }
        if (!importSkin && !importCape) {
            throw RequestError("请选择皮肤或披风")
        }
        val uuid = MojangApi.getUuidFromName(trimmedName).getOrThrow() ?: run {
            throw RequestError("玩家${trimmedName}不存在")
        }
        val textures = MojangApi.getProfile(uuid).getOrThrow().textures
        val skin = textures["SKIN"] ?: run {
            throw RequestError("玩家${trimmedName}没有设置过皮肤")
        }
        val cape = textures["CAPE"]

        val cloth = RAccount.Cloth(
            isSlim = skin.metadata?.model.equals("slim", ignoreCase = true),
            skin = skin.url
        )
        if (importCape) {
            cape?.let { cloth.cape = it.url }
        }
        PlayerService.setCloth(cloth).getOrThrow()
        cloth
    }
}