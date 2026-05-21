package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable
import kotlin.collections.joinToString

/**
 * calebxzhou @ 2026-05-15 20:05
 */
enum class EntityCategory { ANIMAL, MONSTER; }
@Serializable
data class EntityFindQ(
    val radius: Double = 64.0,
    val limitPerType: Int = 8,
    val category: EntityCategory = EntityCategory.ANIMAL,
    //take entity resloca. if empty return all kinds
    val typeFilter: List<String> = emptyList(),
)

/*
total  ***x
64x minecraft:pig
82.3m(distance) xxxxxxxxxx-xxxx-xxxx-xxxxx(uuid)
72.3m(distance) xxxxxxxxxx-xxxx-xxxx-xxxxx(uuid)
62.3m(distance) xxxxxxxxxx-xxxx-xxxx-xxxxx(uuid)
42.3m(distance) xxxxxxxxxx-xxxx-xxxx-xxxxx(uuid)
20x minecraft:cow
72.3m(distance) xxxxxxxxxx-xxxx-xxxx-xxxxx(uuid)
62.3m(distance) xxxxxxxxxx-xxxx-xxxx-xxxxx(uuid)
42.3m(distance) xxxxxxxxxx-xxxx-xxxx-xxxxx(uuid)
.....
*/
@Serializable
data class EntityFindP(
    val totalCount: Int,
    val records: List<Group>,
) {
    override fun toString(): String {
        return buildString {
            appendLine("total $totalCount")
            append(records.joinToString("\n"))
        }.trimEnd()
    }

    @Serializable
    data class Group(
        val id: String,
        val count: Int,
        val entities: List<Sample>,
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("${count}x $id")
                append(entities.joinToString("\n"))
            }.trimEnd()
        }
    }

    @Serializable
    data class Sample(
        val uuid: String,
        val distance: Double
    ) {
        override fun toString(): String {
            return "${"%.1f".format(distance)}m $uuid"
        }
    }
}