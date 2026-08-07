package calebxzhou.rdi.client.ui.screen

import kotlinx.serialization.Serializable


@Serializable
object Wardrobe
@Serializable data class SkinPreview(val textureId: Int)
@Serializable data class HostCreate(
    val hostId: String? = null,
    val fromAllHosts: Boolean = false,
)
@Serializable data class ResourceRoute(
    val tab: String = ResourceTab.Installed.name,
    val requiredMcVer: String? = null,
    val requiredLoader: String? = null,
    val fromHostId: String? = null,
    val fromHost2Id: String? = null,
    val fromAllHosts: Boolean = false,
    val fromHostMods: Boolean = false
)
@Serializable data class RemoteModInfoRoute(
    val platform: String,
    val projectId: String,
    val requiredMcVer: String? = null,
    val requiredLoader: String? = null,
    val targetLocalVersionId: String? = null,
    val targetHostId: String? = null,
    val targetHost2Id: String? = null,
    val fromAllHosts: Boolean = false,
    val fromHostMods: Boolean = false
)
@Serializable enum class ResourceInfoType {
    ResourcePack,
    Shader
}
@Serializable data class ResourceInfoRoute(
    val type: String,
    val projectId: String,
    val targetLocalVersionId: String? = null
)
@Serializable data class ModpackInfo(
    val modpackId: String,
    val fromHostId: String? = null,
    val fromAllHosts: Boolean = false
)
@Serializable data class ModpackOptions(
    val versionId: String,
    val modpackName: String = "",
    val versionName: String = "",
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
@Serializable object Mcmod
@Serializable object Sponsor
/*@Serializable data class AiChat(
    val mcpPort: Int? = null,
    val versionDir: String? = null,
    val chatId: String? = null
)*/
@Serializable data class Register(val msa: Boolean)
@Serializable object ResetPassword
@Serializable object PlayerInfo
@Serializable object Setting
@Serializable object Mailbox
@Serializable data class HostRoute(val tab: String = HostTab.MyHosts.name)
@Serializable data class HostInfo(val hostId: String, val fromAllHosts: Boolean = false)
@Serializable data class HostMembers(val hostId: String, val fromAllHosts: Boolean = false)
@Serializable data class HostMods(val hostId: String, val fromAllHosts: Boolean = false)
@Serializable data class HostFiles(val hostId: String, val fromAllHosts: Boolean = false)
@Serializable data class HostBackend(val hostId: String, val fromAllHosts: Boolean = false)
@Serializable object Host2Lobby
@Serializable object Host2Create
@Serializable data class Host2Info(val hostId: String)
@Serializable data class MailDetail(val mailId: String)
//@Serializable data class WorldBirdView(val worldId: String)
@Serializable data class TaskList(
    val selectedRunId: String? = null,
    val fromHostModsId: String? = null,
    val fromAllHosts: Boolean = false,
    val fromHostTab: String? = null
)
@Serializable object McPlayView
