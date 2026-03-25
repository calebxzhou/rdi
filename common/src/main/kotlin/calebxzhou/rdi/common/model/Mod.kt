package calebxzhou.rdi.common.model

import calebxzhou.rdi.common.DL_MOD_DIR
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

@Serializable
data class Mod(
    val platform: String,//cf / mr
    val projectId: String,
    val slug: String,
    val fileId: String,
    val hash: String,
    var side: Side= Side.BOTH,
    val downloadUrls: List<String> = emptyList(),
) {
    val fileName
        get() = "${slug}_${platform}_${hash}.jar"
    val targetPath get() = DL_MOD_DIR.resolve(fileName).toPath()

    enum class Side(val text:String){
        CLIENT("客户端"),SERVER("服务端"),BOTH("客+服通用"),UNKNOWN("未知")
    }
    @Transient
    var vo: Mod.CardVo?=null
    @Transient
    var file: File?=null
    //展示modcard的信息
    @Serializable
    data class CardVo(
        val name: String,
        val nameCn: String?=null,
        val intro: String="",
        //jar里的icon 不一定有
        val iconData: ByteArray?=null,
        //curseforge的icon&mc百科的icon  哪个能用用哪个
        val iconUrls: List<String> =emptyList(),
        val side: Side = Side.BOTH,
    ) {
    }
}
val Mod.isPlatformCf get() = platform=="cf"
val Mod.isPlatformMr get() = platform=="mr"
val Mod.normalizedProjectId get() = projectId.trim()
val Mod.normalizedSlug get() = slug.trim().lowercase()
val Mod.displaySlugOrProject get() = slug.trim().ifBlank { normalizedProjectId }
