package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

@Serializable
data class McpC2SNetPacket(
    val reqId: String,
    val className: String,
    val reqJson: String,
){}

@Serializable
data class McpS2CNetPacket(
    val reqId: String,
    val text: String
){
}
