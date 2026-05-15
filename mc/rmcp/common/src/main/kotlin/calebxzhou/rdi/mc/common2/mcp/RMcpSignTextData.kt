package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpSignTextData(
    val dryRun: Boolean,
    val pos: RBlockPos,
    val side: String,
    val changed: Boolean,
    val lines: List<String>
)
