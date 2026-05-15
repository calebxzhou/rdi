package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpContainerData(
    val dim: String,
    val pos: RBlockPos,
    val side: String,
    val slots: Int,
    val items: List<Slot>
) {
    @JvmRecord
    data class Slot(
        val slot: Int,
        val id: String,
        val count: Int,
        val limit: Int,
        val canInsert: Boolean,
        val snbt: String?
    )
}
