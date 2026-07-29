package calebxzhou.rdi.common.model

import calebxzhou.rdi.model.Role
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Host2(
    @Contextual
    val id: UUID,
    val name: String,
    val intro: String = "暂无简介",
    val iconUrl: String? = null,
    @Contextual
    val ownerId: UUID,
    val mcVersion: McVersion,
    val modLoader: ModLoader,
    val port: Int,
    val whitelist: Boolean,
    val setupStatus: Host2SetupStatus,
    val status: HostStatus
) {
    @Serializable
    data class Member(
        @Contextual
        val playerId: UUID,
        val role: Host2MemberRole
    )

    @Serializable
    data class BriefVo(
        @Contextual
        val id: UUID,
        val name: String,
        val intro: String = "暂无简介",
        val iconUrl: String? = null,
        @Contextual
        val ownerId: UUID,
        val mcVersion: McVersion,
        val modLoader: ModLoader,
        val port: Int,
        val whitelist: Boolean,
        val setupStatus: Host2SetupStatus,
        val status: HostStatus,
        val role: Role,
        val onlinePlayerIds: List<@Contextual UUID> = emptyList()
    )

    @Serializable
    data class DetailVo(
        @Contextual
        val id: UUID,
        val name: String,
        val intro: String = "暂无简介",
        val iconUrl: String? = null,
        @Contextual
        val ownerId: UUID,
        val mcVersion: McVersion,
        val modLoader: ModLoader,
        val port: Int,
        val whitelist: Boolean,
        val setupStatus: Host2SetupStatus,
        val status: HostStatus,
        val role: Role,
        val onlinePlayerIds: List<@Contextual UUID> = emptyList(),
        val members: List<Member> = emptyList(),
        val operation: Host2Operation? = null
    )

    @Serializable
    data class CreateDto(
        val name: String,
        val intro: String = "暂无简介",
        val mcVersion: McVersion,
        val modLoader: ModLoader,
        val whitelist: Boolean
    )

    @Serializable
    data class OptionsDto(
        val name: String? = null,
        val intro: String? = null,
        val iconUrl: String? = null,
        val whitelist: Boolean? = null,
        val mcVersion: McVersion? = null,
        val modLoader: ModLoader? = null
    )

    @Serializable
    data class ServerPackUploadDto(
        val mods: List<Mod>
    )

    @Serializable
    data class CommandDto(
        val command: String
    )

    @Serializable
    data class InviteMemberDto(
        val qq: String
    )

    @Serializable
    data class SetMemberRoleDto(
        val role: Host2MemberRole
    )

    @Serializable
    data class TransferOwnershipDto(
        @Contextual
        val playerId: UUID
    )

    @Serializable
    data class ModKey(
        val platform: String,
        val projectId: String
    )

    @Serializable
    data class SetModsEnabledDto(
        val mods: List<ModKey>,
        val enabled: Boolean
    )

    @Serializable
    data class ModVo(
        val mod: Mod,
        val enabled: Boolean
    )
}

@Serializable
enum class Host2SetupStatus {
    AWAITING_UPLOAD,
    PROCESSING,
    READY,
    FAILED
}

@Serializable
enum class Host2MemberRole {
    ADMIN,
    MEMBER
}

@Serializable
enum class Host2Operation {
    MOD_ADD,
    MOD_DEL,
    MOD_CHG
}
