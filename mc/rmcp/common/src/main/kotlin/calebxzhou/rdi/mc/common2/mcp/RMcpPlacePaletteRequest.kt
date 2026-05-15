package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpPlacePaletteRequest(
    val palette: Map<String, Entry>,
    val targets: List<Target>,
    val dryRun: Boolean
) {
    @JvmRecord
    data class Entry(
        val blockId: String,
        val state: Map<String, String>?
    )

    @JvmRecord
    data class Target(
        val pos: RBlockPos,
        val key: String?
    )
}
