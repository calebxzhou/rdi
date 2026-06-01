package calebxzhou.rdi.common.model

import kotlinx.serialization.Serializable

//mod简要信息 从pcl 的 ModData取得 + mcmod目录爬取
@Serializable
data class ModBriefInfo(
    //页面id  /class/{id}.html
    val mcmodId: Int,
    val logoUrl: String,
    val name:String,
    val nameCn: String?=null,
    //一句话介绍
    val intro: String,
    // mod通名
    val curseforgeSlugs: List<String>,
    val modrinthSlugs: List<String>,
) {
}