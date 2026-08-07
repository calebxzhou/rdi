package calebxzhou.rdi.common.model

import calebxzhou.rdi.common.UNKNOWN_PLAYER_ID
import calebxzhou.rdi.common.UUIDSerializer
import calebxzhou.rdi.common.util.toUUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bson.types.ObjectId
import java.util.UUID

/*val account
    get() = RAccount.now ?: RAccount.DEFAULT.also { lgr.warn { "用户未登录 使用默认账号" } }*/
const val DEFAULT_SKIN_URL = "https://littleskin.cn/textures/526fe866ed25a7ee1cf894b81a2199aaa03f139803623a25a793f6ae57e22f02"
@Serializable
data class RAccount(
    @Contextual
    val _id: ObjectId,
    var name: String,
    var pwd: String,
    var qq: String,
    val score: Int = 0,
    @Serializable(with = UUIDSerializer::class)
    val msid: UUID? = null,
    @Contextual
    val inviter: ObjectId?=null,
    val allowUploadModpack: Boolean? = null,
    var cloth: Cloth = Cloth(),
) {
    @Transient
    var jwt: String? = null
    @Serializable
    data class RegisterDto(
        val name: String,
        val qq: String,
        val pwd: String,
        val msa: MsaAccountInfo?,
    )
    @Serializable
    data class ResetPasswordByMsaDto(
        val msa: MsaAccountInfo,
        val newPwd: String
    )
    @Serializable
    data class ResetPasswordByQqMailDto(
        val qq: String,
        val newPwd: String
    )

    @Serializable
    data class Cloth(
        var isSlim: Boolean = true,
        var skin: String = DEFAULT_SKIN_URL,
        var cape: String? = null
    )
    @Serializable
    data class Dto(
        @Contextual
        val id: ObjectId,
        val name: String,
        val cloth: Cloth
    )
    companion object {
        val DEFAULT = RAccount(UNKNOWN_PLAYER_ID, "未知", "123456", "12345", 0)
        val TESTS = listOf(
            RAccount(ObjectId("68b314bbadaf52ddab96b5ed"), "测试1", "123123", "123123", 0),
            RAccount(ObjectId("68c901f07c76a32fa7dc270a"), "测试2", "456456", "456456", 0)
        )



        /*@JvmStatic
        fun processSkullProfile(
            profileCache: GameProfileCache,
            profile: GameProfile?,
            profileConsumer: Consumer<GameProfile?>
        ) {
            if(profile != null && !profile.name.isNullOrBlank() && (!profile.isComplete || !profile.properties.containsKey("textures")) && profileCache != null){
                profileCache.getAsync(profile.name) {  }
            }
        }*/

    }

    val uuid get() = _id.toUUID()
    val dto get() = Dto(_id, name, cloth)
    val hasMsid get() = msid != null
}
val RAccount.isDav get() = name == "davickk"

