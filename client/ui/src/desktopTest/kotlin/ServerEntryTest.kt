import calebxzhou.rdi.client.net.RServer
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.model.ServerEntry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerEntryTest {
    companion object {
        private const val ENABLED_PROP = "rdi.test.serverEntry.enabled"
        private const val BGP_URL = "bkrdi.calebxzhou.cn"

        // 1.1.9.0|1.1.63.255|中国|广东省|广州市|中国电信|CN
        private const val GUANGZHOU_TELECOM_IP = "1.1.9.1"

        // 27.39.0.0|27.39.31.255|中国|广东省|广州市|联通|CN
        private const val GUANGZHOU_UNICOM_IP = "27.39.0.1"

        // 58.240.16.0|58.240.52.3|中国|江苏省|南京市|联通|CN
        private const val NANJING_UNICOM_IP = "58.240.16.1"

        // 1.0.16.0|1.0.31.255|Japan|Tokyo|0|0|JP
        private const val TOKYO_IP = "1.0.16.1"

        @JvmStatic
        @BeforeAll
        fun setUp() {
            System.setProperty("rdi.debug", "true")
            System.setProperty("rdi.noHttps", "true")
            DEBUG = true
            RServer.DBG.noHttps = true
        }
    }

    @Test
    fun invalidIpShouldFail(): Unit = runBlocking {
        val response = server.makeRequest<ServerEntry>(
            path = "server-entry",
            params = mapOf("myIp" to "abc")
        )
        assertFalse(response.ok)
        assertTrue(response.msg.contains("myIp格式错误"), response.msg)
    }

    @Test
    fun telecomIpShouldUsePrimaryEntry(): Unit = runBlocking {
        val entry = requestEntry(GUANGZHOU_TELECOM_IP)
        println(entry)
        assertFalse(entry.useBackupNode)
        assertNull(entry.api)
    }

    @Test
    fun guangzhouUnicomIpShouldUseGuangzhouNode(): Unit = runBlocking {
        val entry = requestEntry(GUANGZHOU_UNICOM_IP)
        println(entry)
        assertTrue(entry.useBackupNode)
        assertEquals(BGP_URL, entry.api)
    }

    @Test
    fun nanjingUnicomIpShouldUseShanghaiNode(): Unit = runBlocking {
        val entry = requestEntry(NANJING_UNICOM_IP)
        println(entry)
        assertTrue(entry.useBackupNode)
        assertEquals(BGP_URL, entry.api)
    }

    @Test
    fun outsideChinaIpShouldUseInternationalNode(): Unit = runBlocking {
        val entry = requestEntry(TOKYO_IP)
        println(entry)
        assertTrue(entry.useBackupNode)
        assertEquals(BGP_URL, entry.api)
    }

    private suspend fun requestEntry(ip: String): ServerEntry {
        val response = server.makeRequest<ServerEntry>(
            path = "server-entry",
            params = mapOf("myIp" to ip)
        )
        assertTrue(response.ok, response.msg)
        return assertNotNull(response.data)
    }

}
