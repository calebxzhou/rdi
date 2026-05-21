package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface ActionResult<out T> {
    @Serializable
    data class Ok<out T>(val data: T) : ActionResult<T>
    @Serializable
    data class Err(val reason: String) : ActionResult<Nothing>
}
