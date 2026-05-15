package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpContainerMoveBatchData(
    val action: String,
    val failedMoves: List<FailedMove>
) {
    @JvmRecord
    data class FailedMove(
        val index: Int,
        val code: String?
    )
}
