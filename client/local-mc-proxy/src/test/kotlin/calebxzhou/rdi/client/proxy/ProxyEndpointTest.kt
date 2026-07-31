package calebxzhou.rdi.client.proxy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxyEndpointTest {
    @Test
    fun `parses plain endpoint`() {
        assertEquals(
            ProxyEndpoint("game.example.com", 25565),
            ProxyEndpoint.parse("game.example.com:25565").getOrThrow()
        )
    }

    @Test
    fun `strips transport prefix`() {
        assertEquals(
            ProxyEndpoint("127.0.0.1", 65230),
            ProxyEndpoint.parse("tcp://127.0.0.1:65230").getOrThrow()
        )
    }

    @Test
    fun `rejects invalid port`() {
        assertTrue(ProxyEndpoint.parse("game.example.com:not-a-port").isFailure)
    }
}
