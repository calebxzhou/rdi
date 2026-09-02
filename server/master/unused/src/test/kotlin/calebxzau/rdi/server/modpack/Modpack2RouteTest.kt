package calebxzau.rdi.server.modpack

import calebxzhou.rdi.master.exception.ParamError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Modpack2RouteTest {
    @Test
    fun `page defaults and parses non-negative integers`() {
        assertEquals(0, parseModpack2Page(null))
        assertEquals(0, parseModpack2Page("0"))
        assertEquals(1, parseModpack2Page("1"))
        assertEquals(Int.MAX_VALUE, parseModpack2Page(Int.MAX_VALUE.toString()))
    }

    @Test
    fun `page rejects negative empty non-integer and overflowing values`() {
        listOf("-1", "", "not-a-number", "${Int.MAX_VALUE}0").forEach { value ->
            assertFailsWith<ParamError> { parseModpack2Page(value) }
        }
    }
}
