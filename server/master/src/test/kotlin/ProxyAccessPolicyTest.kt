import calebxzhou.rdi.master.service.ProxyAccessPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyAccessPolicyTest {
    private val gameAddresses = listOf(
        "1.2.3.4:65230",
        "5.6.7.8:65230"
    )

    @Test
    fun `loopback can request proxy route`() {
        assertTrue(ProxyAccessPolicy.isAllowed("127.0.0.1", gameAddresses))
    }

    @Test
    fun `configured game node ip can request proxy route`() {
        assertTrue(ProxyAccessPolicy.isAllowed("5.6.7.8", gameAddresses))
    }

    @Test
    fun `unknown ip cannot request proxy route`() {
        assertFalse(ProxyAccessPolicy.isAllowed("9.10.11.12", gameAddresses))
    }
}
