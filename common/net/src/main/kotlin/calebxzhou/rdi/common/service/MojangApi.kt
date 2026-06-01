package calebxzhou.rdi.common.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.decodeBase64
import calebxzhou.rdi.common.model.MojangPlayerProfile
import calebxzhou.rdi.common.model.MojangProfileResponse
import calebxzhou.rdi.common.model.MojangTexturesPayload
import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.util.ok
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

object MojangApi {
    private val lgr by Loggers
    val UUID.dashless get() = this.toString().replace("-", "")
    suspend fun getUuidFromName(name: String): Result<String?> = runCatching {
        val resp = ktorClient.request { url("https://api.mojang.com/users/profiles/minecraft/${name}") }

        @Serializable
        data class IdName(val name: String, val id: String)
        if (resp.status.value == 404) return ok(null)
        val body = resp.body<IdName>()
        return ok(body.id)
    }

    suspend fun getProfile(uuid: UUID): Result<MojangProfileResponse> = getProfile(uuid.dashless)
    suspend fun getProfile(uuidNoDash: String): Result<MojangProfileResponse> = runCatching {
        val resp = ktorClient.request { url("https://sessionserver.mojang.com/session/minecraft/profile/$uuidNoDash") }
        val profile = resp.body<MojangProfileResponse>()
        profile
    }

    val MojangProfileResponse.textures
        get() = properties
            .firstOrNull { it.name.equals("textures", ignoreCase = true) }
            ?.value?.decodeBase64?.let { Json.decodeFromString<MojangTexturesPayload>(it) }?.textures ?: emptyMap()

    suspend fun getProfileByToken(accessToken: String): Result<MojangPlayerProfile> = runCatching {
        val resp = ktorClient.get {
            url("https://api.minecraftservices.com/minecraft/profile")
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.AcceptEncoding, "identity")
        }
        resp.body<MojangPlayerProfile>()
    }

}