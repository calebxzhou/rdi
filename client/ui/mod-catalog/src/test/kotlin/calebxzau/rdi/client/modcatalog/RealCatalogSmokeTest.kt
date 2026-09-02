package calebxzau.rdi.client.modcatalog

import calebxzau.rdi.client.modcatalog.CatalogSearchRequest
import calebxzau.rdi.client.modcatalog.CatalogTarget
import calebxzau.rdi.client.modcatalog.CatalogNetworkPolicy
import calebxzau.rdi.client.modcatalog.CurseForgeAdapter
import calebxzau.rdi.client.modcatalog.CurseForgeConfig
import calebxzau.rdi.client.modcatalog.createModCatalog
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Buffer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

private fun createPrintingHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            addInterceptor { chain ->
                val request = chain.request()
                val requestBody = request.body?.let { body ->
                    Buffer().apply { body.writeTo(this) }.readUtf8()
                } ?: "<empty>"
                println("HTTP request URL: ${request.url}")
                println("HTTP request body: $requestBody")

                val response = chain.proceed(request)
                println("HTTP response body: ${response.peekBody(Long.MAX_VALUE).string()}")
                response
            }
        }
    }
}

class RealCatalogSmokeTest {
    @Test
    fun `live search is opt in`() = runBlocking {
        if (System.getProperty("rdi.modCatalogLiveTest") != "true") return@runBlocking
        val materializationDir = Files.createTempDirectory("rdi-catalog-live")
        createPrintingHttpClient().use { client ->
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

    @Test
    fun `live curseforge project lookup is opt in`() = runBlocking {
        if (System.getProperty("rdi.modCatalogLiveTest") != "true") return@runBlocking
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        createPrintingHttpClient().use { client ->
            val adapter = CurseForgeAdapter(
                httpClient = client,
                config = CurseForgeConfig(),
                networkPolicy = CatalogNetworkPolicy(preferMirror = false),
                json = json
            )
            val result = adapter.getProjects(setOf("499980","60089"))
            println(result)
           // assertTrue("499980" in result.found)
        }
    }
}
