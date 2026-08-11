package calebxzhou.rdi.client.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModIconResolverTest {
    @Test
    fun `normalizes remote icon candidates without changing their priority`() {
        assertEquals(
            listOf("https://first", "https://second"),
            normalizeModIconUrls(
                listOf("  https://first ", "", "https://first", "https://second")
            )
        )
    }

    @Test
    fun `empty local icon resolves successfully as missing`() {
        assertNull(decodeLocalModIcon(null).getOrThrow())
    }
}
