package calebxzhou.rdi.master.model

import kotlinx.serialization.Serializable

@Serializable
data class RGlobalPlayerList(
    val generatedAt: Long,
    val hosts: List<HostEntry>,
) {
    @Serializable
    data class HostEntry(
        val hostId: String,
        val hostName: String,
        val modpackName: String,
        val packVer: String,
        val players: List<PlayerEntry>,
    )

    @Serializable
    data class PlayerEntry(
        val playerId: String,
        val playerName: String,
    )
}
