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
@Serializable data class ResourceRoute(
    val tab: String = ResourceTab.All.name,
    val requiredMcVer: String? = null
)
@Serializable data class ModpackInfo(
    val modpackId: String,
    val fromHostId: String? = null,
    val fromAllHosts: Boolean = false
)
@Serializable data class ModpackVersionEdit(
    val modpackId: String,
    val verName: String,
    val fromHostId: String? = null,
    val fromAllHosts: Boolean = false
)
@Serializable object ModpackUpload
@Serializable object Login
@Serializable object Menu
@Serializable object Sponsor
@Serializable data class Register(val msa: Boolean)
@Serializable object Setting
@Serializable data class HostRoute(val tab: String = HostTab.MyHosts.name)
@Serializable data class HostInfo(val hostId: String, val fromAllHosts: Boolean = false)
@Serializable data class MailDetail(val mailId: String)
@Serializable data class WorldBirdView(val worldId: String)
@Serializable data class TaskList(val selectedRunId: String? = null)
@Serializable object McPlayView
