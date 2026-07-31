package calebxzhou.rdi.client.proxy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMcProxyTest {
    @Test
    fun `start returns actual loopback address and is idempotent`() {
        val proxy = LocalMcProxy(
            endpointProvider = { ProxyEndpoint("127.0.0.1", 25565) },
            config = LocalMcProxyConfig(
                preferredBindPort = 0,
                compressionEnabled = false
            )
        )

        try {
            val firstAddress = proxy.start().getOrThrow()
            assertTrue(firstAddress.startsWith("127.0.0.1:"))
            assertTrue(firstAddress.substringAfterLast(':').toInt() > 0)
            assertEquals(firstAddress, proxy.start().getOrThrow())
        } finally {
            proxy.stop().getOrThrow()
        }
    }
}
