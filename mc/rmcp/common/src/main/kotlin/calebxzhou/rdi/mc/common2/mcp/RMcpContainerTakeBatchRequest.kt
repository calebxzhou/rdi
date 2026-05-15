package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpContainerTakeBatchRequest(
    val moves: List<Move>,
    val dryRun: Boolean,
    val stopOnError: Boolean
) {
    @JvmRecord
    data class Move(
        val from: RMcpContainerMoveBatchRequest.Endpoint,
        val toInventorySlot: Int,
        val count: Int
    )
}
