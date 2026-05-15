package calebxzhou.rdi.mc.common2.mcp

/*
GET /blocks
search blocks by given ids, in 256x256 player-centered area
64x limit for each given id
 */
data class GetBlocksQ(
    val ids: List<String>,
) {
}