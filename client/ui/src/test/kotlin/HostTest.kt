import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.common.model.RAccount
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

class HostTest {
    companion object {
        private val TEST_HOST_ID = ObjectId("696312b0e61232912c744968")
        private const val TEST_USER = "123123"
        private const val TEST_PASSWORD = "123123"
        private val TEST_ACCOUNT_ID = ObjectId("68b314bbadaf52ddab96b5ed")

        @JvmStatic
        @BeforeAll
        fun setUpAccount() = runBlocking {
            System.setProperty("rdi.debug", "true")
            System.setProperty("logging.level.root","DEBUG")
            val account = RAccount(TEST_ACCOUNT_ID, TEST_USER, TEST_PASSWORD, TEST_USER)
            account.jwt = PlayerService.getJwt(TEST_USER, TEST_PASSWORD)
            loggedAccount = account
        }
    }
    @Test
    fun sendCommand(){
        runBlocking {
            val response = server.makeRequest<String>("host/${TEST_HOST_ID}/command", HttpMethod.Post,params = mapOf("command" to "stop"))
            println(response)
        }
    }
}