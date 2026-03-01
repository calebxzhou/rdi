import calebxzhou.rdi.common.anvilrw.format.AnvilReader
import net.benwoodworth.knbt.nbtCompound
import net.benwoodworth.knbt.nbtInt
import net.benwoodworth.knbt.nbtList
import java.io.File
import kotlin.test.Test

class WorldTest {
    @Test
    fun load(){
        val chunkData =
            AnvilReader(File("C:\\Users\\calebxzhou\\Documents\\RDI5sea-Ref\\.minecraft\\versions\\FTB Skies 2\\saves\\新的世界\\region\\r.0.0.mca"))
                .readRegion().chunks.filter { !it.isEmpty }.first().getNbtData()
        chunkData?.get("blending_data")?.nbtCompound?.get("min_section")?.nbtInt?.let { println(it) }
        chunkData?.get("sections")?.nbtList?.forEach { println(it.toString())
            println("---")}
    }
}