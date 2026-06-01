package calebxzhou.rdi.common.model.world

import kotlinx.serialization.Serializable

@Serializable
data class RBlockState(
    //res loca
    val name: String,
    val props: Map<String, String> = emptyMap()
){

}