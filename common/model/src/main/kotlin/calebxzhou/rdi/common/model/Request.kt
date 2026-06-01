package calebxzhou.rdi.common.model

import kotlinx.serialization.Serializable

@Serializable
data class Request<T>(
    val opr: String,
    val data: T
)  {
}