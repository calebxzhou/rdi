package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpContainerPutBatchRequest(
    val moves: List<Move>,
    val dryRun: Boolean,
    val stopOnError: Boolean
) {
    @JvmRecord
    data class Move(
        val fromInventorySlot: Int,
        val to: RMcpContainerMoveBatchRequest.Endpoint,
        val count: Int
    )
}
