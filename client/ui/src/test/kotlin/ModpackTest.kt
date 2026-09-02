import calebxzhou.rdi.common.util.jarResource
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.CurseForgeService
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class ModpackTest {

	companion object {
		private const val TEST_USER = "123123"
		private const val TEST_PASSWORD = "123123"
		private val TEST_ACCOUNT_ID = ObjectId("68b314bbadaf52ddab96b5ed")

		@JvmStatic
		@BeforeAll
		fun setUpAccount() = runBlocking {
			System.setProperty("rdi.debug", "true")
			System.setProperty("rdi.modDir", "C:\\Users\\calebxzhou\\Documents\\RDI5sea-Ref\\.minecraft\\versions\\ATM10 To the Sky\\mods")
            System.setProperty("logging.level.root","DEBUG")
			val account = RAccount(TEST_ACCOUNT_ID, TEST_USER, TEST_PASSWORD, TEST_USER)
			account.jwt = PlayerService.getJwt(TEST_USER, TEST_PASSWORD)
			loggedAccount = account
		}
	}

	@Test
	@Disabled("Integration test that requires a running backend instance")
	fun rebuildModpackVersion(): Unit = runBlocking {
		server.makeRequest<Unit>(
			path = "modpack/69194653663d20f0e88fb268/version/1.9.2/rebuild",
			method = HttpMethod.Post
		)
	}
    private fun loadTestMods(): MutableList<Mod> {
        val json = jarResource("test_mod_list.json").use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
        return serdesJson.decodeFromString<List<Mod>>(json).toMutableList()
    }
	@Test
	@Disabled("Integration test that creates a modpack on the backend")
	fun createModpackVersion(): Unit = runBlocking {
		val modpack = server.makeRequest<Modpack>(
			path = "modpack/",
			method = HttpMethod.Post,
			params = mapOf("name" to "test1")
		).data!!

		val response = server.makeRequest<Unit>(
			path = "modpack/${modpack._id}/version/1.8.4",
			method = HttpMethod.Post
		).msg

		assertNotNull(response)
	}

	@Test
	fun loadCurseForgeModpack(): Unit = runBlocking {
		/*val zipPath = "C:\\Users\\calebxzhou\\Downloads\\ftb-skies-2-1.9.2.zip"
		val modpackData = CurseForgeService.loadModpack(zipPath).getOrThrow()
        print(modpackData.manifest.files.mapMods().json)
		assertNotNull(modpackData.manifest)*/
	}
    @Test
    fun cfModFile(){
        runBlocking {
            CurseForgeService.getModFileInfo(1150640,6743799)?.let { print(it.json)
			print(it.realDownloadUrl)}
        }
    }
    @Test
    fun install() : Unit = runBlocking {
		/*ModpackService.installVersion(
			McVersion.V211,
			ModLoader.NEOFORGE,
			ObjectId("695519fec0e579e94b5c40c5"),"1.9.2",loadTestMods()){ println(it) }
*/		 }
    @Test
    fun downloadClient() : Unit = runBlocking {
        /*ModpackService.downloadVersionClientPackLegacy(
            ObjectId("693bda0ed294de2450aa7caf"),"1.9.2",){ println(it) }*/
    }
}
