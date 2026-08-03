package calebxzau.rdi.client.modcatalog

import calebxzau.rdi.client.modcatalog.CatalogSearchRequest
import calebxzau.rdi.client.modcatalog.CatalogTarget
import calebxzau.rdi.client.modcatalog.createModCatalog
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class RealCatalogSmokeTest {
    @Test
    fun `live search is opt in`() = runBlocking {
        if (System.getProperty("rdi.modCatalogLiveTest") != "true") return@runBlocking
        val materializationDir = Files.createTempDirectory("rdi-catalog-live")
        HttpClient(OkHttp).use { client ->
            createModCatalog(client, materializationDir).use { catalog ->
                val result = catalog.search(
                    CatalogSearchRequest(
                        "jei",
                        CatalogTarget(McVersion.V211, ModLoader.neoforge)
                    )
                ).getOrThrow()
                assertTrue(result.value.items.isNotEmpty())
            }
        }
    }
}
