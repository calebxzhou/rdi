package calebxzhou.rdi.common.model

import kotlinx.serialization.Serializable

@Serializable
data class ProxyHostRoute(
    val status: HostStatus,
    val backendHost: String,
    val backendPort: Int
)
