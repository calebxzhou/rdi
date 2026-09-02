package calebxzau.rdi.common.logging

import kotlin.test.Test
import kotlin.test.assertNotNull

class LoggersTest {
    private class Demo {
        val logger by Loggers
    }

    @Test
    fun `delegate returns logger`() {
        assertNotNull(Demo().logger)
    }
}
