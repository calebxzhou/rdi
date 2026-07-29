package calebxzhou.rdi.client.ui.comp

import kotlin.test.Test
import kotlin.test.assertEquals

class ConsoleStateTest {
    @Test
    fun `append splits multiline text and preserves blank lines`() {
        val state = ConsoleState()

        state.append("first\n\nthird")

        assertEquals(listOf("first", "", "third"), state.snapshot())
    }

    @Test
    fun `appendAll appends all split lines`() {
        val state = ConsoleState()

        state.appendAll(listOf("first\nsecond", "third"))

        assertEquals(listOf("first", "second", "third"), state.snapshot())
    }

    @Test
    fun `default capacity keeps latest 5000 lines`() {
        val state = ConsoleState()

        state.appendAll((0..5000).map { it.toString() })

        val lines = state.snapshot()
        assertEquals(5000, lines.size)
        assertEquals("1", lines.first())
        assertEquals("5000", lines.last())
    }

    @Test
    fun `clear removes lines and resets removed count`() {
        val state = ConsoleState(2)
        state.appendAll(listOf("first", "second", "third"))

        state.clear()

        assertEquals(emptyList(), state.snapshot())
        assertEquals(0L, state.uiSnapshot().removedLineCount)
    }
}
