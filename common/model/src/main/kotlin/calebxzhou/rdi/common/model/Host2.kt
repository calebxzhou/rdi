package calebxzhou.rdi.common.model

import calebxzau.rdi.common.model.ClientContentVo
import calebxzau.rdi.common.model.ContentInput
import calebxzau.rdi.common.model.ContentKey
import calebxzau.rdi.common.model.ContentVo
import calebxzhou.rdi.model.Role
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Immutable source from which a Host2 is installed. */
@Serializable
sealed class PackSource {
    abstract val versionId: UUID

    @Serializable
    @SerialName("modpack2")
    data class Modpack2(@Contextual override val versionId: UUID) : PackSource()
}

@Serializable
enum class Host2PackStatus {
    Busy,
    Ok,
    Fail,
}

@Serializable
data class Host2PackInfo(
    val name: String,
    val versionName: String,
    val mcVersion: McVersion,
    val modLoader: ModLoader,
    @Contextual val modpackId: UUID,
)

@Serializable
data class Host2(
    @Contextual val id: UUID,
    val name: String,
    val intro: String = "暂无简介",
    val iconUrl: String? = null,
    @Contextual val ownerId: UUID,
    val packSource: PackSource,
    val packStatus: Host2PackStatus,
    val activeContentRevision: Long = 0,
    val pendingContentRevision: Long? = null,
    val pack: Host2PackInfo? = null,
    val port: Int,
    val whitelist: Boolean,
    val status: HostStatus,
) {
    @Serializable
    data class Member(@Contextual val playerId: UUID, val role: Host2MemberRole)

    @Serializable
    data class BriefVo(
        @Contextual val id: UUID,
        val name: String,
        val intro: String = "暂无简介",
        val iconUrl: String? = null,
        @Contextual val ownerId: UUID,
        val packSource: PackSource,
        val packStatus: Host2PackStatus,
        val activeContentRevision: Long = 0,
        val pendingContentRevision: Long? = null,
        val pack: Host2PackInfo? = null,
        val port: Int,
        val whitelist: Boolean,
        val status: HostStatus,
        val role: Role,
        val onlinePlayerIds: List<@Contextual UUID> = emptyList(),
    )

    @Serializable
    data class DetailVo(
        @Contextual val id: UUID,
        val name: String,
        val intro: String = "暂无简介",
        val iconUrl: String? = null,
        @Contextual val ownerId: UUID,
        val packSource: PackSource,
        val packStatus: Host2PackStatus,
        val activeContentRevision: Long = 0,
        val pendingContentRevision: Long? = null,
        val pack: Host2PackInfo? = null,
        val port: Int,
        val whitelist: Boolean,
        val status: HostStatus,
        val role: Role,
        val onlinePlayerIds: List<@Contextual UUID> = emptyList(),
        val members: List<Member> = emptyList(),
    )

    @Serializable
    data class CreateDto(
        val name: String,
        val packSource: PackSource,
        val intro: String = "暂无简介",
        val whitelist: Boolean,
    )

    @Serializable
    data class OptionsDto(
        val name: String? = null,
        val intro: String? = null,
        val iconUrl: String? = null,
        val whitelist: Boolean? = null,
    )

    @Serializable
    data class PackSourceDto(
        val packSource: PackSource,
        val expectedRevision: Long,
    )

    @Serializable
    data class ContentsVo(
        val activeRevision: Long,
        val pendingRevision: Long? = null,
        val active: List<ContentVo> = emptyList(),
        val pending: List<ContentVo>? = null,
    )

    @Serializable
    data class AddContentsDto(
        val expectedRevision: Long,
        val contents: List<ContentInput>,
    )

    @Serializable
    data class DeleteContentsDto(
        val expectedRevision: Long,
        val keys: List<ContentKey>,
    )

    @Serializable
    data class SetContentsEnabledDto(
        val expectedRevision: Long,
        val keys: List<ContentKey>,
        val enabled: Boolean,
    )

    @Serializable
    data class ApplyContentsDto(val expectedRevision: Long? = null)

    @Serializable
    data class ClientManifest(
        val packSource: PackSource,
        val activeContentRevision: Long,
        val mcVersion: McVersion? = null,
        val modLoader: ModLoader? = null,
        val contents: List<ClientContentVo> = emptyList(),
    )

    @Serializable
    data class ContentRevisionVo(
        val currentRevision: Long,
        val newRevision: Long,
    )

    @Serializable
    data class CommandDto(val command: String)

    @Serializable
    data class InviteMemberDto(val qq: String)

    @Serializable
    data class SetMemberRoleDto(val role: Host2MemberRole)

    @Serializable
    data class TransferOwnershipDto(@Contextual val playerId: UUID)

}

@Serializable
enum class Host2MemberRole {
    ADMIN,
    MEMBER,
}

fun isHost2Available(
    packStatus: Host2PackStatus,
    status: HostStatus,
    whitelist: Boolean,
    isMember: Boolean,
): Boolean = packStatus == Host2PackStatus.Ok &&
    status != HostStatus.UNKNOWN &&
    (!whitelist || isMember)
