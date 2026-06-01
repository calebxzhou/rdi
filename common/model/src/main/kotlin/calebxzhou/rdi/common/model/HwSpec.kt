package calebxzhou.rdi.common.model

import kotlinx.serialization.Serializable

@Serializable
data class HwSpec(
    val model: String,
    val os: String,
    val cpu: Cpu,
    val gpus: List<Gpu>,
    val mems: List<Memory>,
    val disk: List<Disk>,
    val display: List<Display>,
    val videoMode: List<String>
) {
    @Serializable
    data class Cpu(
        val name: String,
        val cores: Int,
        val threads: Int,
        val frequency: Long, // in Hz
    )

    @Serializable
    data class Gpu(
        val name: String,
        val vram: Long,
    )

    @Serializable
    data class Memory(
        val size: Long,
        val type: String, // e.g., DDR4, LPDDR5
        val speed: Long // in Hz
    )

    @Serializable
    data class Disk(
        val name: String,
        val size: Long,
        val model: String
    )

    @Serializable
    data class Display(
        val name: String,
        val height: Int,
        val width: Int, //cm
    )
    companion object{

    }
}