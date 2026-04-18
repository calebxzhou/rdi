package calebxzhou.rdi.common.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
class Modpack(
    @Contextual val _id: ObjectId = ObjectId(),
    val name: String,
    @Contextual
    val authorId: ObjectId,
    val iconUrl: String? = null,
    val info: String? = null,
    val modloader: ModLoader,
    val mcVer: McVersion,
    val sourceUrl: String? = null,
    val playCount: Int = 0,
    val categories: List<Category> = emptyList(),
    val versions: MutableList<Version> = arrayListOf(),
) {
    companion object {
        const val MAX_CATEGORY_COUNT = 4

        fun normalizeCategories(categories: List<Category>): List<Category> =
            categories.distinct().take(MAX_CATEGORY_COUNT)
    }

    @Serializable
    enum class Category(val label: String) {
        LARGE("\uDB84\uDFA7 大型"),
        MEDIUM("\uDB84\uDFA5 中型"),
        SMALL("\uDB84\uDFA4 小型"),
        MAGIC("\uDB86\uDC44 魔法"),
        HARDCORE("\uE646 硬核"),
        SKYBLOCK("\uDB84\uDC4F 空岛"),
        VANILLA("\uDB81\uDD8C 纯净"),
        TECH("\uE266 科技"),
        STORY("\uDB84\uDFAD 剧情"),
        ADVENTURE("\uE6A0 探险"),
        CASUAL("\uE2A2 休闲"),
        MANAGE("\uF157 经营"),
        APOCALYPSE("\uDB82\uDEC1 末日"),
        WAR("\uDB81\uDF03 战争"),
        HEAVY_MOD("\uDB80\uDEA2 魔改"),
        RPG("\uDB85\uDFDD RPG"),
        COMBAT("\uDB81\uDF87 战斗"),
        LIGHT_MOD("\uEDF7 轻量"),
        OPTIMIZE("\uF4BC 优化"),
        OTHER("\uDB82\uDC17 其他");

        companion object {
            val allLabels = entries.map(Category::label)
        }
    }
    @Serializable
    data class Version(
        val time: Long,
        @Contextual
        val modpackId: ObjectId,
        //1.0 1.1 1.2 etc
        val name: String,
        val changelog: String,
        //构建完成状态
        val totalSize: Long? = 0L,
        val status: Status,
        val mods: MutableList<Mod> = arrayListOf(),
    ) {
    }

    @Serializable
    class AddVersionDto(

    )

    @Serializable
    class CreateWithVersionDto(
        val name: String,
        val mcVer: McVersion,
        val modLoader: ModLoader,
        val verName: String,
        val iconUrl:String?=null,
        val sourceUrl: String? =null,
        val info: String? =null,
        val categories: List<Category> = emptyList(),
        val mods: MutableList<Mod>
    ) {

    }

    @Serializable
    data class BriefVo(
        @Contextual
        val id: ObjectId = ObjectId(),
        val name: String = "未知整合包",
        @Contextual
        val authorId: ObjectId = ObjectId(),
        val authorName: String = "",
        val mcVer: McVersion = McVersion.V211,
        val modloader: ModLoader = ModLoader.neoforge,
        val modCount: Int = 0,
        val fileSize: Long = 0L,
        val playCount: Int = 0,
        val lastUpdatedTime: Long = 0L,
        val icon: String? = null,
        val info: String? = null,
        val categories: List<Category> = emptyList(),
    )

    @Serializable
    data class DetailVo(
        @Contextual
        val _id: ObjectId,
        val name: String,
        @Contextual
        val authorId: ObjectId,
        val authorName: String = "",
        val modCount: Int,
        val playCount: Int = 0,
        val sourceUrl: String? = null,
        val icon: String? = null,
        val info: String? = null,
        val modloader: ModLoader,
        val mcVer: McVersion,
        val categories: List<Category> = emptyList(),
        val versions: List<Version> = arrayListOf(),
    )

    @Serializable
    enum class SearchSort {
        RELEVANCE,
        UPDATED,
        POPULAR,
        NAME,
    }

    @Serializable
    data class SearchResultVo(
        val items: List<BriefVo> = emptyList(),
        val total: Int = 0,
        val offset: Int = 0,
        val limit: Int = 24,
        val hasMore: Boolean = false,
    )

    @Serializable
    data class OptionsDto(
        val name: String? = null,
        val iconUrl: String? = null,
        val info: String? = null,
        val sourceUrl: String? = null,
        val categories: List<Category>? = null
    )

    enum class Status {
        FAIL, OK, BUILDING, WAIT,
    }


}
