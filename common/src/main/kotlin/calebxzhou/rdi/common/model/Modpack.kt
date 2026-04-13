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
        LARGE("大型"),
        MEDIUM("中型"),
        SMALL("小型"),
        MAGIC("魔法"),
        HARDCORE("硬核"),
        MINIGAME("小游戏"),
        QUEST("任务"),
        SKYBLOCK("空岛"),
        EDUCATION("教育"),
        VANILLA("纯净"),
        TECH("科技"),
        STORY("剧情"),
        ADVENTURE("探险"),
        CASUAL("休闲"),
        MANAGE("经营"),
        NURTURE("养成"),
        SCENERY("风景"),
        APOCALYPSE("末日"),
        WAR("战争"),
        HEAVY_MOD("魔改"),
        RPG("RPG"),
        COMBAT("战斗"),
        LIGHT_MOD("轻量"),
        OPTIMIZE("优化"),
        OTHER("其他");

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
        val sourceUrl: String? = null,
        val icon: String? = null,
        val info: String? = null,
        val modloader: ModLoader,
        val mcVer: McVersion,
        val categories: List<Category> = emptyList(),
        val versions: List<Version> = arrayListOf(),
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
