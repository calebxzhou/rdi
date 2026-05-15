package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpSignTextReadData(
    val pos: RBlockPos,
    val waxed: Boolean,
    val front: Side,
    val back: Side?
) {
    @JvmRecord
    data class Side(
        val color: String,
        val glowing: Boolean,
        val lines: List<String>,
        val hasText: Boolean
    )
}
