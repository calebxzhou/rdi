package calebxzhou.rdi.client.ui.screen

import kotlinx.serialization.Serializable


@Serializable
object Wardrobe
@Serializable data class SkinPreview(
    val tid: Int,
    val name: String,
    val type: String,
    val uploader: Int,
    val isPublic: Boolean,
    val likes: Int
)
@Serializable data class HostCreate(
    val hostId: String? = null,
    val fromAllHosts: Boolean = false,
)
@Serializable object ModpackList
@Serializable data class ModpackInfo(
    val modpackId: String,
    val fromHostId: String? = null,
    val fromAllHosts: Boolean = false
)
@Serializable object ModpackUpload
@Serializable object Login
@Serializable object Menu
@Serializable data class Register(val msa: Boolean)
@Serializable object Setting
@Serializable object ModpackLocalManage
@Serializable object HostList
@Serializable object HostAll
@Serializable data class HostInfo(val hostId: String, val fromAllHosts: Boolean = false)
@Serializable object Mail
@Serializable data class MailDetail(val mailId: String)
@Serializable object WorldList
@Serializable data class WorldBirdView(val worldId: String)
@Serializable object TaskView
@Serializable object McPlayView
@Serializable data class RMcVersion(val mcVer: String? = null)
