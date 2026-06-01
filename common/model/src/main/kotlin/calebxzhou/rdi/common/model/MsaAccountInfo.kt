package calebxzhou.rdi.common.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MsaAccountInfo(
    val uuid: @Contextual UUID,
    val name: String,
    val token: String,
)
