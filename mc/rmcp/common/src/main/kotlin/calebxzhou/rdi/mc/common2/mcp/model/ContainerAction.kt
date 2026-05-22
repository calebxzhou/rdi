package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

/**
 * calebxzhou @ 2026-05-18 11:44
 */



@Serializable
data class ContainerRef(
    val pos: RBlockPos? = null,
) {
    val isPlayerInventory get() = pos == null
}

@Serializable
data class ContainerSlotRef(
    val pos: RBlockPos? = null,
    // as target, -1 means auto find
    val slotId: Int,
) {
    val isPlayerInventory get() = pos == null
    val targetAutoFind get() = slotId < 0
}


@Serializable
data class ContainerSlot(
    val id: Int,
    val itemId: String,
    val count: Int,
) {
    override fun toString() = "$id $count $itemId"
    val isEmpty = count == 0 && itemId == EMPTY_ITEM_ID
    companion object {
        const val EMPTY_ITEM_ID = "air"
        fun empty(id: Int) = ContainerSlot(id, EMPTY_ITEM_ID, 0)
    }
}
fun Iterable<ContainerSlot>.text(showEmpty: Boolean = true) = buildString {
    val slots = this@text
    val nonEmptySlots = slots.filterNot { it.isEmpty }
    appendLine("slotID count itemID")
    append(nonEmptySlots.joinToString("\n"))
    if (showEmpty) {
        val emptySlotIds = slots.filter { it.isEmpty }.map { it.id }.sorted()
        if (emptySlotIds.isNotEmpty()) {
            if (nonEmptySlots.isNotEmpty()) appendLine()
            appendLine("empty ${emptySlotIds.toRangeText()}")
        }
    }
}

private fun List<Int>.toRangeText(): String {
    if (isEmpty()) return ""
    val ranges = mutableListOf<String>()
    val sortedIds = distinct().sorted()
    var start = sortedIds.first()
    var prev = start
    for (id in sortedIds.drop(1)) {
        if (id == prev + 1) {
            prev = id
        } else {
            ranges += if (start == prev) "$start" else "$start..$prev"
            start = id
            prev = id
        }
    }
    ranges += if (start == prev) "$start" else "$start..$prev"
    return ranges.joinToString(" ")
}

@Serializable
data class ContainerSlotListQ(
    val poses: List<RBlockPos>,
)

@Serializable
data class ContainerSlotListP(
    val groups: List<Group>,
    val failures: Map<ActionResult.Err, List<RBlockPos>>,
) {
    override fun toString() = buildString {
        appendLine("containers ${groups.size}")
        appendLine()
        groups.forEach { group ->
            appendLine(group)
            appendLine()
        }
        appendLine("failures ${failures.values.sumOf { it.size }}")
        if (failures.isNotEmpty()) {
            failures.forEach { (failure, poses) ->
                appendLine("${failure.reason}: ${poses.text}")
            }
        }
    }

    @Serializable
    data class Group(
        val pos: RBlockPos,
        val blockId: String,
        val slots: List<ContainerSlot>,
    ) {
        override fun toString() = buildString {
            appendLine("$pos $blockId")
            append(slots.text())
        }
    }
}



@Serializable
data class ContainerMoveQ(
    val groups: List<Group>,
    val test: Boolean = false,
) {
    @Serializable
    data class Group(
        val from: ContainerRef,
        val to: ContainerRef,
        val moves: List<Move>,
    )

    @Serializable
    data class Move(
        val fromSlotId: Int,
        val toSlotId: Int? = null,
        val count: Int? = null,
    ) {
        val targetAutoFind get() = toSlotId == null
        val countAll get() = count == null
    }
}

@Serializable
data class ContainerMoveP(
    val count: Int,
    val failures: Map<ActionResult.Err, List<Failure>>,
    val test: Boolean = false,
) {
    override fun toString() = buildString {
        appendLine("moved $count")
        appendLine("test $test")
        appendLine("failures ${failures.values.sumOf { it.size }}")
        failures.forEach { (failure, moves) ->
            appendLine("${failure.reason}: ${moves.joinToString(", ")}")
        }
    }

    @Serializable
    data class Failure(
        val groupId: Int,
        val moveId: Int,
        val fromSlotId: Int,
        val toSlotId: Int?,
        val count: Int?,
    ) {
        override fun toString() = "group${groupId}move${moveId} slot$fromSlotId->${toSlotId ?: "auto"} count ${count ?: "all"}"
    }
}

enum class InventoryCompart{ INV,ARMOR,OFFHAND }

@Serializable
data class InventorySlotQ(val compart: InventoryCompart,val slotId: Int)

@Serializable
data class InventoryListP(
    //count + item ids eg. 64x minecraft:dirt
    val inv: List<ContainerSlot>,
    val armor: List<ContainerSlot>,
    val offhand: List<ContainerSlot>,
){
    override fun toString() = buildString {
        appendLine("inventory")
        append(inv.text())
        appendLine()
        appendLine("armor")
        append(armor.text())
        appendLine()
        appendLine("offhand")
        append(offhand.text())
    }
}

@Serializable
data class InventoryMoveQ(
    val fromInvSlot: Int,
    val toInvSlot: Int,
    val swap: Boolean,
)
