import calebxzhou.rdi.common.CommonConfig
import calebxzhou.rdi.common.ProxyConfig
import calebxzhou.rdi.common.net.DynamicProxySelector
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicProxySelectorTest {
    @AfterTest
    fun resetProxyConfig() {
        CommonConfig.updateProxyConfig(null)
    }

    @Test
    fun loopbackTargetsShouldBypassConfiguredProxy() {
        CommonConfig.updateProxyConfig(
            ProxyConfig(
                enabled = true,
                host = "127.0.0.1",
                port = 10808
            )
        )
        val selector = DynamicProxySelector()

        listOf(
            "http://localhost:65231",
            "http://127.0.0.1:65231",
            "http://127.0.0.2:65231",
            "http://[::1]:65231"
        ).forEach { url ->
            assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI(url)), url)
        }
    }

    @Test
    fun publicTargetShouldUseConfiguredProxy() {
        CommonConfig.updateProxyConfig(
            ProxyConfig(
                enabled = true,
                host = "127.0.0.1",
                port = 10808
            )
        )

        val proxy = DynamicProxySelector().select(URI("https://rdi.calebxzhou.cn")).single()

        assertEquals(Proxy.Type.HTTP, proxy.type())
        assertEquals(InetSocketAddress("127.0.0.1", 10808), proxy.address())
    }
}
