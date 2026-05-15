package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpNearbyEntitiesData(
    val dim: String,
    val center: Center,
    val radius: Double,
    val summary: Summary,
    val entities: List<Entity>
) {
    @JvmRecord
    data class Center(val block: RBlockPos, val chunkX: Int, val chunkZ: Int, val sectionY: Int)

    @JvmRecord
    data class EntityRef(val type: String, val distance: Double)

    @JvmRecord
    data class Summary(
        val total: Int,
        val monsters: Int,
        val animals: Int,
        val items: Int,
        val nearestMonster: EntityRef,
        val nearestAnimal: EntityRef,
        val nearestItem: EntityRef?
    )

    @JvmRecord
    data class Item(val snbt: String?)

    @JvmRecord
    data class Entity(
        val dim: String,
        val uuid: String,
        val type: String,
        val name: String,
        val category: String,
        val pos: REntityPosData,
        val distance: Double,
        val health: Float,
        val maxHealth: Float,
        val hostile: Boolean,
        val baby: Boolean,
        val item: Item?
    )
}
