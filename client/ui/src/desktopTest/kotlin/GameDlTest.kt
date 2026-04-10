import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.client.service.GameService.rewriteMirrorUrl
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GameDlTest {
    @Test
    fun urlReplace() {
        assertEquals(
            "https://bmclapi2.bangbang93.com/maven/net/neoforged/neoforge/21.1.216/neoforge-21.1.216-installer.jar",
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.216/neoforge-21.1.216-installer.jar".rewriteMirrorUrl
        )
    }

    @Test
    fun dl21(): Unit = runBlocking {

        //GameService.downloadVersionLegacy(McVersion.V211) { println(it) }
    }

    @Test
    fun start21(): Unit = runBlocking {
       // GameService.start(McVersion.V211, "neoforge-21.1.216") { println(it) }
    }
    /*@Test
    fun start20(): Unit = runBlocking {
        GameService.start(McVersion.V201, "1.20.1-forge-47.4.13") { println(it) }
    }*/
    @Test
    fun startModpack(): Unit = runBlocking {
      //  GameService.start(McVersion.V211, "693bda0ed294de2450aa7caf_1.9.2") { println(it) }
    }
    @Test
    fun installLoader21(): Unit = runBlocking {
       // GameService.downloadLoaderLegacy(McVersion.V211, ModLoader.neoforge) { println(it) }
    }

}
