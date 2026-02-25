package calebxzhou.rdi.client.ui.screen

import kotlinx.serialization.Serializable


@Serializable
object Wardrobe
@Serializable data class HostCreate(
    val modpackId: String,
    val modpackName: String,
    val packVer: String,
    val skyblock: Boolean,
    val hostId: String? = null,
)
@Serializable object ModpackList
@Serializable data class ModpackInfo(val modpackId: String, val fromHostId: String? = null)
@Serializable object ModpackUpload
@Serializable object Login
@Serializable data class Register(val msa: Boolean)
@Serializable object Setting
@Serializable object HostList
@Serializable data class HostInfo(val hostId: String)
@Serializable object Mail
@Serializable data class MailDetail(val mailId: String)
@Serializable object WorldList
@Serializable data class WorldBirdView(val worldId: String)
@Serializable object TaskView
@Serializable object McPlayView
@Serializable data class RMcVersion(val mcVer: String? = null)
