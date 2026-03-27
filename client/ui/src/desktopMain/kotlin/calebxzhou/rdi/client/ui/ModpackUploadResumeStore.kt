package calebxzhou.rdi.client.ui

import calebxzhou.rdi.client.service.UploadPayload
import calebxzhou.rdi.common.model.Mod

data class ModpackUploadResumeState(
    val payload: UploadPayload?,
    val selectedSourceName: String?,
    val parseProgress: String?,
    val errorText: String?,
    val pendingDownloadMods: List<Mod>,
    val modpackName: String,
    val versionName: String,
    val iconUrl: String,
    val sourceUrl: String,
    val infoText: String,
    val mcVersionText: String,
    val modloaderText: String,
    val mods: List<Mod>
)

object ModpackUploadResumeStore {
    var state: ModpackUploadResumeState? = null
}
