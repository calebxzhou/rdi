package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpBlockBatchActionData(
    val action: String,
    val dryRun: Boolean,
    val changed: Boolean,
    val failedPos: List<RBlockPos>,
)
