package calebxzhou.rdi.common.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerEntry(
    val api: String? = null,
    val useBackupNode: Boolean = false,
    val nodeName: String,
    val gameAddr: String,
)
