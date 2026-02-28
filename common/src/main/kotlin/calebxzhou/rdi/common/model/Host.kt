package calebxzhou.rdi.common.model

import calebxzhou.rdi.model.Role
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId


@Serializable
data class Host(
    @Contextual
    val _id: ObjectId = ObjectId(),
    val name: String,
    val intro: String = "暂无简介",
    @Contextual
    val ownerId: ObjectId,
    @Contextual
    val modpackId: ObjectId,
    //版本可能会重新发布 此时id会变 所以不用packVerId
    var packVer: String = "latest",
    @Contextual
    val worldId: ObjectId? = null,
    var port: Int,
    val difficulty: Int,
    val gameMode: Int,
    val levelType: String,
    val gameRules: MutableMap<String, String> = mutableMapOf(),
    //白名单 只有成员才能进
    val whitelist: Boolean = false,
    //允许作弊（全op）
    val allowCheats: Boolean = false,
    val members: List<Member> = arrayListOf(),
    val banlist: List<@Contextual ObjectId> = arrayListOf(),
    //整合包外的附加mod
    var extraMods: List<Mod> = arrayListOf()
) {
    companion object {
        var portNow: Int = 0
    }
    //所有人都能玩 无论是否启动
    val isPublicTest get() = name.contains("公测")

    @Serializable
    data class Member(
        @Contextual
        val id: ObjectId,
        val role: Role
    )

    @Serializable
    data class BriefVo(
        @Contextual
        val _id: ObjectId = ObjectId(),
        val name: String,
        val intro: String = "暂无简介",
        val iconUrl: String? = null,
        @Contextual
        val ownerId: ObjectId = ObjectId(),
        val modpackName: String,
        val packVer: String,
        var port: Int,
        val playable: Boolean = true,
        val isMember: Boolean = false,
        val onlinePlayerIds: List<@Contextual ObjectId> = arrayListOf(),
    ) {
        companion object {
            val TEST = BriefVo(
                name = "啊实打实大苏打实打实的",
                intro = "都是大大实打实大苏打实打实的得分风格风格风格非官方",
                modpackName = "测试测试测试测试测试",
                packVer = "1.0.0",
                port = 55555,
                onlinePlayerIds = arrayListOf(
                    ObjectId(), ObjectId(), ObjectId(), ObjectId(), ObjectId(), ObjectId(), ObjectId(), ObjectId(),
                )
            )
        }
    }

    @Serializable
    data class DetailVo(
        @Contextual
        val _id: ObjectId = ObjectId(),
        val name: String,
        val intro: String = "暂无简介",
        val iconUrl: String? = null,
        @Contextual
        val ownerId: ObjectId = ObjectId(),
        val modpack: Modpack.BriefVo,
        val packVer: String,
        @Contextual
        val worldId: ObjectId? = null,
        var port: Int,
        val difficulty: Int,
        val gameMode: Int,
        val levelType: String,
        val gameRules: MutableMap<String, String> = mutableMapOf(),
        val whitelist: Boolean = false,
        val allowCheats: Boolean = false,
        val members: List<Member> = arrayListOf(),
        val extraMods: List<Mod> = arrayListOf(),
        val onlinePlayerIds: List<@Contextual ObjectId> = arrayListOf(),
    )

    @Serializable
    data class CreateDto(
        val name: String,
        @Contextual
        val modpackId: ObjectId,
        val packVer: String,
        //是否存档 false不保存任何数据
        val saveWorld: Boolean,
        //已有存档id 新建存档=null
        @Contextual
        val worldId: ObjectId?,
        val difficulty: Int,
        val gameMode: Int,
        val levelType: String,
        val allowCheats: Boolean,
        val whitelist: Boolean,
        val gameRules: MutableMap<String, String>
    )

    @Serializable
    data class OptionsDto(
        val name: String? = null,
        @Contextual
        val modpackId: ObjectId? = null,
        val packVer: String? = null,
        val difficulty: Int? = null,
        val gameMode: Int? = null,
        val levelType: String? = null,
        val whitelist: Boolean? = null,
        val allowCheats: Boolean? = null,
        val gameRules: Map<String, String>? = null
    )

}
