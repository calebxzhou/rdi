package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpPlaceDiscreteRequest(
    val blockId: String,
    val targets: List<Target>,
    val dryRun: Boolean
) {
    @JvmRecord
    data class Target(
        val pos: RBlockPos,
        val state: Map<String, String>?
    )
}
