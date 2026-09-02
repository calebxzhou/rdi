package calebxzhou.rdi.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurseForgeSideTest {
    @Test
    fun `game version tags map to mod sides`() {
        assertEquals(
            Mod.Side.BOTH,
            listOf("1.21.1", "Client", "Fabric", "Server").toCurseForgeModSide()
        )
        assertEquals(Mod.Side.CLIENT, listOf("Client", "Forge").toCurseForgeModSide())
        assertEquals(Mod.Side.SERVER, listOf("Server", "NeoForge").toCurseForgeModSide())
        assertNull(listOf("1.21.1", "Forge").toCurseForgeModSide())
    }

    @Test
    fun `game version side tags are case insensitive`() {
        assertEquals(Mod.Side.BOTH, listOf("client", "SERVER").toCurseForgeModSide())
    }
}
