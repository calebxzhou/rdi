/*
package calebxzhou.rdi.mc.firmsection

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class FirmSectionStateTest {
    private val playerA = UUID(0L, 1L)
    private val playerB = UUID(0L, 2L)
    private val key = FirmSectionKey("minecraft:overworld", 1, 2, 3)
    private val anotherKey = FirmSectionKey("minecraft:overworld", 1, 2, 4)

    @Test
    fun `same player setting same section returns already present`() {
        val state = FirmSectionState(maxTotal = 10)

        assertEquals(FirmSectionSetStatus.ADDED, state.set(playerA, key).status)
        assertEquals(FirmSectionSetStatus.ALREADY_PRESENT, state.set(playerA, key).status)
        assertEquals(listOf(key), state.sectionsOf(playerA))
        assertEquals(1, state.totalCount())
    }

    @Test
    fun `different player cannot set occupied section`() {
        val state = FirmSectionState(maxTotal = 10)

        assertEquals(FirmSectionSetStatus.ADDED, state.set(playerA, key).status)
        assertEquals(FirmSectionSetStatus.OCCUPIED_BY_OTHER, state.set(playerB, key).status)
        assertEquals(listOf(key), state.sectionsOf(playerA))
        assertEquals(emptyList(), state.sectionsOf(playerB))
        assertEquals(1, state.totalCount())
    }

    @Test
    fun `different player can set different section`() {
        val state = FirmSectionState(maxTotal = 10)

        assertEquals(FirmSectionSetStatus.ADDED, state.set(playerA, key).status)
        assertEquals(FirmSectionSetStatus.ADDED, state.set(playerB, anotherKey).status)
        assertEquals(listOf(key), state.sectionsOf(playerA))
        assertEquals(listOf(anotherKey), state.sectionsOf(playerB))
        assertEquals(2, state.totalCount())
    }

    @Test
    fun `loading duplicate historical sections keeps first owner`() {
        val state = FirmSectionState(maxTotal = 10)

        state.loadPlayer(playerA, listOf(key), autoSet = false)
        state.loadPlayer(playerB, listOf(key, anotherKey), autoSet = false)

        assertEquals(listOf(key), state.sectionsOf(playerA))
        assertEquals(listOf(anotherKey), state.sectionsOf(playerB))
        assertEquals(2, state.totalCount())
    }
}
*/
