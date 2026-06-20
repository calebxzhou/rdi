package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class McpParam(
    val description: String,
    val minimum: Int = Int.MIN_VALUE,
    val maximum: Int = Int.MAX_VALUE,
    val minItems: Int = Int.MIN_VALUE,
    val enumValues: Array<String> = [],
)
